package exam.tests;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.wiring.FrameworkWiring;
import org.junit.Assert;

/**
 * @author cnoelle
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class FelixForkedTest {

	private static final Path osgiStorage = Paths.get("data/osgi-storage");
	private static final AtomicInteger bundleCount = new AtomicInteger(0);
	
	@Inject
	protected BundleContext ctx;
	
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
				CoreOptions.frameworkProperty(Constants.FRAMEWORK_STORAGE).value(osgiStorage.toString()), 
				CoreOptions.frameworkProperty(Constants.FRAMEWORK_STORAGE_CLEAN).value(Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT),
				CoreOptions.vmOption("-ea"), 
				CoreOptions.when(getJavaVersion() >= 9).useOptions(
					CoreOptions.vmOption("--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED")
				),				
				CoreOptions.junitBundles(),
				CoreOptions.mavenBundle("org.apache.felix", "org.apache.felix.scr", "2.1.2"),
				CoreOptions.mavenBundle("org.apache.felix", "org.apache.felix.configadmin", "1.9.4"),
				CoreOptions.mavenBundle("org.ops4j.pax.tinybundles", "tinybundles", "3.0.0"),
				CoreOptions.mavenBundle("biz.aQute.bnd", "biz.aQute.bndlib", "3.5.0")
			};
	}
	
	private Bundle createFragment() throws BundleException {
		final String id = "testFragment" + bundleCount.getAndIncrement();
		final InputStream in = TinyBundles.bundle()
				.set(Constants.BUNDLE_SYMBOLICNAME, id)
				.set(Constants.BUNDLE_VERSION, "0.0.1")
				.set(Constants.BUNDLE_NAME, "tiny test bundle")
				.set(Constants.BUNDLE_MANIFESTVERSION, "2")
				.set(Constants.FRAGMENT_HOST, Constants.SYSTEM_BUNDLE_SYMBOLICNAME 
						+ ";" + Constants.EXTENSION_DIRECTIVE + ":=" + Constants.EXTENSION_FRAMEWORK)
//				.set(Constants.REQUIRE_CAPABILITY, "osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\"")
			.build();
		final Bundle b = ctx.installBundle("test:" + id, in);
		Assert.assertTrue("Fragment bundle could not be resolved", 
				b.getState() == Bundle.RESOLVED || ctx.getBundle(0).adapt(FrameworkWiring.class).resolveBundles(Collections.singleton(b)));
		return b;
	}
	
	@Test
	public void startupWorks() {
	}
	
	@Test
	public void frameworkExtensionWorks() throws BundleException {
		final Bundle fragment = createFragment();
		fragment.uninstall();
	}
	
	@Test
	public void testClassWorks() {
		final Class<?> clazz = TestClass.class;
		System.out.println("Test class: " + clazz.getName());
	}
	
}
