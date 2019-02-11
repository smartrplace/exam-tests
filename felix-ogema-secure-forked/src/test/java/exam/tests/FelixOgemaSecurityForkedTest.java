package exam.tests;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.model.Resource;
import org.ogema.core.model.simple.StringResource;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;

/**
 * @author cnoelle
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class FelixOgemaSecurityForkedTest {

	private static final String SLF4J_VERSION = "1.7.25";
	private static final String OGEMA_VERSION = "2.2.0";
	private static final Path osgiStorage = Paths.get("data/osgi-storage");
	
	@Inject
	protected BundleContext ctx;
	
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
				CoreOptions.mavenBundle("org.apache.felix", "org.apache.felix.framework.security", "2.6.1"),
				CoreOptions.mavenBundle("org.ogema.ref-impl", "permission-admin").version(OGEMA_VERSION).startLevel(1),
				CoreOptions.mavenBundle("org.apache.felix", "org.apache.felix.scr", "2.1.14"),
				CoreOptions.mavenBundle("org.apache.felix", "org.apache.felix.configadmin", "1.9.10"),
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
				CoreOptions.mavenBundle("org.apache.commons", "commons-lang3", "3.8.1"),
				CoreOptions.mavenBundle("org.json", "json", "20170516"),
				CoreOptions.mavenBundle("com.google.guava", "guava", "27.0-jre"),
				CoreOptions.mavenBundle("org.ow2.asm", "asm", "7.0"),
				
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
	public void startupWorks() {
		Assert.assertNotNull("Security manager is null", System.getSecurityManager());
		Assert.assertNotNull("App manager is null", appMan);
	}
	
	@Test
	public void resourceCreationWorks() {
		final Resource resource = appMan.getResourceManagement().createResource("test", StringResource.class);
		try {
			System.out.println("   Resource created: " + resource);
			Assert.assertNotNull(resource);
		} finally {
			resource.delete();
		}
	}

}
