package io.github.libfdx.tests.desktopc;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktopc.DesktopCApplicationBackend;
import io.github.libfdx.backend.desktopc.DesktopCApplicationConfig;
import io.github.libfdx.backend.desktopc.DesktopCOpenGLProvider;
import io.github.libfdx.tests.TestSelector;

/**
 * Launches the desktop C OpenGL test entry point.
 *
 * @author xpenatan
 */
public final class DesktopCOpenGLTestLauncher {
    private DesktopCOpenGLTestLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        DesktopCTestLauncherArgs launcherArgs = DesktopCTestLauncherArgs.apply(args);
        String testName = launcherArgs.testName();
        long frames = launcherArgs.frames();
        boolean explicitSize = launcherArgs.hasProperty("libfdx.test.width")
                || launcherArgs.hasProperty("libfdx.test.height");
        boolean maximized = launcherArgs.maximized(explicitSize);
        int width = launcherArgs.width(testName);
        int height = launcherArgs.height(testName);

        DesktopCApplicationConfig config = new DesktopCApplicationConfig()
                .title("libfdx Test: " + testName + " - desktop_c OpenGL")
                .size(width, height)
                .maximized(maximized)
                .graphics(new DesktopCOpenGLProvider());

        ApplicationListener test = TestSelector.create(testName, frames);
        new DesktopCApplicationBackend().start(config, test);
    }
}
