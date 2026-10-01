package io.github.libfdx.tests.desktopc;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktopc.DesktopCApplicationBackend;
import io.github.libfdx.backend.desktopc.DesktopCApplicationConfig;
import io.github.libfdx.backend.desktopc.DesktopCAudioProvider;
import io.github.libfdx.backend.desktopc.DesktopCOpenGLProvider;
import io.github.libfdx.testsupport.PerformanceApplication;
import io.github.libfdx.testsupport.AutoTestApplication;
import io.github.libfdx.testsupport.TestChooserApplication;
import io.github.libfdx.testsupport.TestSelector;
import io.github.libfdx.testsupport.desktopc.DesktopCTestFactory;
import io.github.libfdx.testsupport.desktopc.DesktopCTestLauncherArgs;
import io.github.libfdx.testsupport.desktopc.DesktopCTestProcesses;
import io.github.libfdx.testsupport.nativeprocess.NativeTestLaunchHandler;

/**
 * Launches the desktop C OpenGL test entry point.
 *
 * @author xpenatan
 */
public final class DesktopCOpenGLTestLauncher {
    private DesktopCOpenGLTestLauncher() {}

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) throws Exception {
        DesktopCTestLauncherArgs launcherArgs = DesktopCTestLauncherArgs.apply(args);
        String testName = launcherArgs.testName();
        if ("DesktopCArrayCloneTest".equals(testName)) {
            DesktopCArrayCloneTest.main(args);
            return;
        }
        if ("DesktopCGarbageCollectionTest".equals(testName)) {
            DesktopCGarbageCollectionTest.main(args);
            return;
        }
        long frames = launcherArgs.frames();
        boolean chooser = TestSelector.SELECTOR_NAME.equals(testName);
        if (chooser) {
            System.setProperty("libfdx.test.frames", Long.toString(frames));
        }
        boolean explicitSize =
                launcherArgs.hasProperty("libfdx.test.width")
                        || launcherArgs.hasProperty("libfdx.test.height");
        boolean maximized = launcherArgs.maximized(explicitSize || chooser);
        int width = chooser && !launcherArgs.hasProperty("libfdx.test.width")
                ? 900 : launcherArgs.width(testName);
        int height = chooser && !launcherArgs.hasProperty("libfdx.test.height")
                ? 740 : launcherArgs.height(testName);

        DesktopCApplicationConfig config =
                new DesktopCApplicationConfig()
                        .audio(new DesktopCAudioProvider())
                        .title("libfdx Test: " + testName + " - desktop_c OpenGL")
                        .size(width, height)
                        .maximized(maximized)
                        .vSync(Boolean.parseBoolean(System.getProperty("libfdx.test.vsync", "true")))
                        .foregroundFps(Integer.parseInt(System.getProperty("libfdx.test.foregroundFps", "60")))
                        .graphics(new DesktopCOpenGLProvider());

        NativeTestLaunchHandler launches = chooser ? new NativeTestLaunchHandler("gl", new DesktopCTestProcesses()) : null;
        ApplicationListener test = chooser
                ? new TestChooserApplication(new String[] {"gl"}, "gl", launches, false, false,
                        DesktopCTestFactory::create)
                : TestSelector.AUTO_TEST_NAME.equals(testName)
                ? new AutoTestApplication(null, true, DesktopCTestFactory::create)
                : DesktopCTestFactory.create(testName, frames);
        if (PerformanceApplication.enabled()) {
            config.vSync(false).foregroundFps(0);
        }
        try {
            new DesktopCApplicationBackend().start(config, PerformanceApplication.wrap(test, testName));
        } finally {
            if (launches != null) launches.dispose();
        }
    }
}
