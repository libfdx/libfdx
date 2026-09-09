package io.github.libfdx.tests.desktopc;

import io.github.libfdx.testsupport.desktopc.DesktopCTestLauncherArgs;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktopc.DesktopCApplicationBackend;
import io.github.libfdx.backend.desktopc.DesktopCApplicationConfig;
import io.github.libfdx.backend.desktopc.DesktopCVulkanProvider;
import io.github.libfdx.testsupport.TestSelector;

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
        DesktopCTestLauncherArgs launcherArgs = DesktopCTestLauncherArgs.apply(args);
        String testName = launcherArgs.testName();
        long frames = launcherArgs.frames();
        boolean explicitSize = launcherArgs.hasProperty("libfdx.test.width")
                || launcherArgs.hasProperty("libfdx.test.height");
        boolean maximized = launcherArgs.maximized(explicitSize);
        int width = launcherArgs.width(testName);
        int height = launcherArgs.height(testName);

        DesktopCApplicationConfig config = new DesktopCApplicationConfig()
                .title("libfdx Test: " + testName + " - desktop_c Vulkan")
                .size(width, height)
                .maximized(maximized)
                .graphics(new DesktopCVulkanProvider());

        ApplicationListener test = "DesktopCVulkanPreparationTest".equals(testName)
                ? new DesktopCVulkanPreparationTest(frames) : TestSelector.create(testName, frames);
        new DesktopCApplicationBackend().start(config, test);
        if (test instanceof DesktopCVulkanPreparationTest preparation) preparation.verifyShutdown();
    }
}
