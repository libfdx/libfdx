package io.github.libfdx.tests.desktopc;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktopc.DesktopCApplicationBackend;
import io.github.libfdx.backend.desktopc.DesktopCApplicationConfig;
import io.github.libfdx.backend.desktopc.DesktopCVulkanProvider;
import io.github.libfdx.tests.TestSelector;

/**
 * Launches the desktop C vulkan test entry point.
 *
 * @author xpenatan
 */
public final class DesktopCVulkanTestLauncher {
    private DesktopCVulkanTestLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        String testName = TestSelector.DEFAULT_TEST_NAME;
        int width = TestSelector.defaultWidth(testName);
        int height = TestSelector.defaultHeight(testName);

        DesktopCApplicationConfig config = new DesktopCApplicationConfig()
                .title("libfdx Test: " + testName + " - desktop_c Vulkan")
                .size(width, height)
                .graphics(new DesktopCVulkanProvider());

        ApplicationListener test = TestSelector.create(testName, 0L);
        new DesktopCApplicationBackend().start(config, test);
    }
}
