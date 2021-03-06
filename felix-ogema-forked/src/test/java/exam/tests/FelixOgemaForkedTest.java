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
public class FelixOgemaForkedTest {

	private static final String SLF4J_VERSION = "1.7.26";
	private static final String OGEMA_VERSION = "2.2.0";
	private static final String MOXY_VERSION = "2.7.4";
	private static final String JACKSON_VERSION = "2.9.9";
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
	
	private static final boolean isJava9Or10() {
		final int v = getJavaVersion();
		return v == 9 || v == 10;
	}
	
	@Configuration
	public Option[] configuration() throws IOException {
		return new Option[] {
				CoreOptions.cleanCaches(),
				CoreOptions.frameworkProperty(Constants.FRAMEWORK_STORAGE).value(osgiStorage.toString()), 
				CoreOptions.frameworkProperty(Constants.FRAMEWORK_STORAGE_CLEAN).value(Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT),
				CoreOptions.frameworkProperty(Constants.FRAMEWORK_BSNVERSION).value(Constants.FRAMEWORK_BSNVERSION_MULTIPLE),
				CoreOptions.vmOption("-ea"), 
				CoreOptions.when(getJavaVersion() >= 9).useOptions(
						CoreOptions.vmOption("--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED")
				),
				CoreOptions.when(isJava9Or10()).useOptions(
						CoreOptions.vmOption("--add-modules=java.xml.bind,java.xml.ws.annotation")
				),
				CoreOptions.when(getJavaVersion() >= 11).useOptions(
						CoreOptions.mavenBundle("com.sun.activation", "javax.activation", "1.2.0"),
						CoreOptions.mavenBundle("javax.annotation", "javax.annotation-api", "1.3.2"),
						CoreOptions.mavenBundle("javax.xml.bind", "jaxb-api", "2.4.0-b180830.0359"),
						CoreOptions.mavenBundle("org.eclipse.persistence", "org.eclipse.persistence.asm", MOXY_VERSION),
						CoreOptions.mavenBundle("org.eclipse.persistence", "org.eclipse.persistence.core", MOXY_VERSION),
						CoreOptions.mavenBundle("org.eclipse.persistence", "org.eclipse.persistence.moxy", MOXY_VERSION),
						CoreOptions.vmOption("-Djavax.xml.bind.JAXBContextFactory=org.eclipse.persistence.jaxb.JAXBContextFactory")
				),
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
				CoreOptions.mavenBundle("com.fasterxml.jackson.core", "jackson-core", JACKSON_VERSION),
				CoreOptions.mavenBundle("com.fasterxml.jackson.core", "jackson-annotations", JACKSON_VERSION),
				CoreOptions.mavenBundle("com.fasterxml.jackson.core", "jackson-databind", JACKSON_VERSION),
				CoreOptions.mavenBundle("com.fasterxml.jackson.module", "jackson-module-jaxb-annotations", JACKSON_VERSION),
				
				// commons
				CoreOptions.mavenBundle("commons-io", "commons-io", "2.6"),
				CoreOptions.mavenBundle("org.apache.commons", "commons-math3", "3.6.1"),
				CoreOptions.mavenBundle("commons-codec", "commons-codec", "1.11"),
				CoreOptions.mavenBundle("org.apache.commons", "commons-lang3", "3.7"),
				CoreOptions.mavenBundle("org.json", "json", "20180813"),
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
				CoreOptions.mavenBundle("biz.aQute.bnd", "biz.aQute.bndlib", "3.5.0") // v >= 4.0.0 not supported by tinybundles yet
			};
	}
	
	@Test
	public void startupWorks() {
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
