package exam.tests;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ogema.core.administration.FrameworkClock;
import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * Example usage of the configurator service to provide an initial configuration.
 * 
 * @author cnoelle
 */
// FIXME configurator and config admin require patched versions for security, see 
// https://issues.apache.org/jira/browse/FELIX-5910
// https://issues.apache.org/jira/browse/FELIX-5911
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ConfigTest {

	private static final String SLF4J_VERSION = "1.7.25";
	private static final String OGEMA_VERSION = "2.2.0-alpha-20180724";
	private static final Path osgiStorage = Paths.get("data/osgi-storage");
	// FIXME it should be possible to provide the initial configuration in memory 
	private static final Path configFile = Paths.get("data/config.json");
	private static final String SIM_CLOCK_PID = "org.ogema.application.manager.impl.SimulationClock";
	private static final int SIMULATION_FACTOR = 5;
	
	// write config file to disk, so it can be picked up by configurator service later on
	public ConfigTest() {
		try {
			Files.createDirectories(configFile.getParent());
			final JSONObject config = new JSONObject(configPropertyMap());
			final InputStream in = new ByteArrayInputStream(config.toString().getBytes(StandardCharsets.UTF_8));
			Files.copy(in, configFile, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
	
	@Inject
	private BundleContext ctx;
	
	@Inject
	private FrameworkClock clock;
	
	private volatile ServiceRegistration<Application> appRef;
	private volatile CountDownLatch stopLatch;
	private volatile ApplicationManager appMan;
	
	@Before
	public void registerApp() throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		this.stopLatch = new CountDownLatch(1);
		final Application app = new Application() {
			
			@Override
			public void start(ApplicationManager appManager) {
				appMan = appManager;
				latch.countDown();
			}
			
			@Override
			public void stop(AppStopReason reason) {
				appMan = null;
				stopLatch.countDown();
			}
		};
		this.appRef = ctx.registerService(Application.class, app, null);
		Assert.assertTrue("App start timed out", latch.await(10, TimeUnit.SECONDS));
	}
	
	@After
	public void waitForStop() throws InterruptedException {
		final ServiceRegistration<Application> ref = this.appRef;
		if (ref != null) {
			final CountDownLatch stopLatch = this.stopLatch;
			this.appRef = null;
			this.appMan = null;
			ForkJoinPool.commonPool().submit(ref::unregister);
			Assert.assertTrue("App stop timed out", stopLatch.await(10, TimeUnit.SECONDS));
		}
	}
	
	private static Map<String, Object> configPropertyMap() {
		final Map<String, Object> properties = new HashMap<>();
		final Map<String, Object> simulationClockProperties = new HashMap<>(4);
		simulationClockProperties.put("simulationFactor", SIMULATION_FACTOR); // set simulation factor
		properties.put(SIM_CLOCK_PID, simulationClockProperties);
		// add more configurations if required
		properties.put(":configurator:version", "1");
		properties.put(":configurator:symbolic-name", "initConfig");
		return properties;
	}
	
	private static int getJavaVersion() {
		String version = System.getProperty("java.specification.version");
		final int idx = version.indexOf('.');
		if (idx > 0)
			version = version.substring(idx + 1);
		return Integer.parseInt(version); 
	}

	@Configuration
	public Option[] configuration() throws IOException {
		return new Option[] {
				CoreOptions.cleanCaches(),
				CoreOptions.frameworkProperty(Constants.FRAMEWORK_SECURITY).value(Constants.FRAMEWORK_SECURITY_OSGI),
				CoreOptions.frameworkProperty(Constants.FRAMEWORK_STORAGE).value(osgiStorage.toString()), 
				CoreOptions.frameworkProperty(Constants.FRAMEWORK_STORAGE_CLEAN).value(Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT),
				CoreOptions.frameworkProperty(Constants.FRAMEWORK_BSNVERSION).value(Constants.FRAMEWORK_BSNVERSION_MULTIPLE),
				// specifying the JSON directly does not work in tests... 
				// maybe pax exam tweaks the system and framework properties, removing "\""?
				CoreOptions.frameworkProperty("configurator.initial").value("file:" + configFile.toString()),
				CoreOptions.vmOption("-ea"), 
				// these four options are required with the forked launcher; otherwise they are in the surefire plugin
				CoreOptions.vmOption("-Djava.security.policy=config/all.policy"),
				CoreOptions.vmOption("-Dorg.ogema.security=on"),
				CoreOptions.when(getJavaVersion() >= 9).useOptions(
					CoreOptions.vmOption("--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED"),
					CoreOptions.vmOption("--add-modules=java.xml.bind,java.xml.ws.annotation")
				),
				//
				CoreOptions.junitBundles(),
				CoreOptions.mavenBundle("org.apache.felix", "org.apache.felix.framework.security", "2.6.0"),
				CoreOptions.mavenBundle("org.ogema.ref-impl", "permission-admin").version(OGEMA_VERSION).startLevel(1),
				CoreOptions.mavenBundle("org.apache.felix", "org.apache.felix.scr", "2.1.2"),
				CoreOptions.mavenBundle("org.apache.felix", "org.apache.felix.configadmin", "1.9.5-SNAPSHOT"),
				CoreOptions.mavenBundle("org.apache.felix", "org.apache.felix.configurator", "1.0.5-SNAPSHOT"),
				CoreOptions.mavenBundle("org.apache.felix", "org.apache.felix.useradmin.filestore", "1.0.2"),
				CoreOptions.mavenBundle("org.apache.felix", "org.apache.felix.useradmin", "1.0.3"),
				CoreOptions.mavenBundle("org.osgi", "org.osgi.service.useradmin", "1.1.0"),
				
				// Jetty
				CoreOptions.mavenBundle("org.eclipse.jetty", "jetty-servlets", "9.4.11.v20180605"),
				CoreOptions.mavenBundle("org.apache.felix", "org.apache.felix.http.servlet-api", "1.1.2"),
				CoreOptions.mavenBundle("org.apache.felix", "org.apache.felix.http.jetty", "4.0.4").start(),
				
				// slf4j
				CoreOptions.mavenBundle("org.slf4j", "slf4j-api", SLF4J_VERSION),
				CoreOptions.mavenBundle("org.slf4j", "osgi-over-slf4j", SLF4J_VERSION),
				CoreOptions.mavenBundle("org.slf4j", "slf4j-simple", SLF4J_VERSION).noStart(),
				
				// Jackson
				CoreOptions.mavenBundle("com.fasterxml.jackson.core", "jackson-core", "2.9.6"),
				CoreOptions.mavenBundle("com.fasterxml.jackson.core", "jackson-annotations", "2.9.6"),
				CoreOptions.mavenBundle("com.fasterxml.jackson.core", "jackson-databind", "2.9.6"),
				CoreOptions.mavenBundle("com.fasterxml.jackson.module", "jackson-module-jaxb-annotations", "2.9.6"),
				
				// commons
				CoreOptions.mavenBundle("commons-io", "commons-io", "2.6"),
				CoreOptions.mavenBundle("org.apache.commons", "commons-math3", "3.6.1"),
				CoreOptions.mavenBundle("commons-codec", "commons-codec", "1.11"),
				CoreOptions.mavenBundle("org.apache.commons", "commons-lang3", "3.7"),
				CoreOptions.mavenBundle("org.json", "json", "20170516"),
				CoreOptions.mavenBundle("com.google.guava", "guava", "23.0"),
				
				// OGEMA
				CoreOptions.mavenBundle("org.ogema.core", "api", OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.core", "models").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.core", "api").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.tools", "memory-timeseries").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.ref-impl", "administration").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.ref-impl", "internal-api").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.ref-impl", "non-secure-apploader").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.ref-impl", "app-manager").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.ref-impl", "resource-manager").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.ref-impl", "resource-access-advanced").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.ref-impl", "security").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.ref-impl", "persistence").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.ref-impl", "channel-manager").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.ref-impl", "hardware-manager").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.ref-impl", "recordeddata-slotsdb").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.ref-impl", "util").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.ref-impl", "rest").version(OGEMA_VERSION),
				CoreOptions.mavenBundle("org.ogema.tools", "resource-utils").version(OGEMA_VERSION),
				
				CoreOptions.mavenBundle("org.ops4j.pax.tinybundles", "tinybundles", "3.0.0"),
				CoreOptions.mavenBundle("biz.aQute.bnd", "biz.aQute.bndlib", "3.5.0")
				
			};
	}
	
	@Test
	public void configWorks0() throws InterruptedException {
		Assert.assertEquals("Unexpected simulation factor", SIMULATION_FACTOR, clock.getSimulationFactor(), 0.0001);
	}
	
	@Test
	public void configCanBeChanged() throws IOException, InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		clock.addClockChangeListener(event -> latch.countDown());
		final ServiceReference<ConfigurationAdmin> caRef = ctx.getServiceReference(ConfigurationAdmin.class);
		final ConfigurationAdmin ca = ctx.getService(caRef);
		try {
			final org.osgi.service.cm.Configuration cfg = ca.getConfiguration(SIM_CLOCK_PID);
			final Dictionary<String, Object> props = cfg.getProperties();
			final float factor = (float) props.get("simulationFactor");
			final float newFactor = 2 * factor;
			props.put("simulationFactor", newFactor);
			cfg.update(props);
			try {
				Assert.assertTrue("Clock change callback pending", latch.await(5, TimeUnit.SECONDS));
				Assert.assertEquals("Unexpected simulation factor", newFactor, clock.getSimulationFactor(), 0.0001);
			} finally {
				props.put("simulationFactor", factor);
				cfg.update(props);
			}
		} finally {
			ctx.ungetService(caRef);
		}
	}
	
}
