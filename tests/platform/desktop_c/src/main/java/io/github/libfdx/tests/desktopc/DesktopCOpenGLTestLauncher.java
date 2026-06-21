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
        String testName = TestSelector.DEFAULT_TEST_NAME;
        boolean explicitSize = hasProperty("libfdx.test.width") || hasProperty("libfdx.test.height");
        boolean maximized = Boolean.parseBoolean(System.getProperty("libfdx.test.maximized",
                explicitSize ? "false" : "true"));
        int width = intProperty("libfdx.test.width", TestSelector.defaultWidth(testName));
        int height = intProperty("libfdx.test.height", TestSelector.defaultHeight(testName));

        DesktopCApplicationConfig config = new DesktopCApplicationConfig()
                .title("libfdx Test: " + testName + " - desktop_c OpenGL")
                .size(width, height)
                .maximized(maximized)
                .graphics(new DesktopCOpenGLProvider());

        ApplicationListener test = TestSelector.create(testName, 0L);
        new DesktopCApplicationBackend().start(config, test);
    }

    private static int intProperty(String name, int fallback) {
        return Integer.parseInt(System.getProperty(name, String.valueOf(fallback)));
    }

    private static boolean hasProperty(String name) {
        String value = System.getProperty(name);
        return value != null && value.trim().length() > 0;
    }
}
