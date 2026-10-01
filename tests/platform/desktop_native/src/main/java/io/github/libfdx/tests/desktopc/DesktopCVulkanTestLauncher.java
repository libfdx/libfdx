package io.github.libfdx.tests.desktopc;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktopc.DesktopCApplicationBackend;
import io.github.libfdx.backend.desktopc.DesktopCApplicationConfig;
import io.github.libfdx.backend.desktopc.DesktopCAudioProvider;
import io.github.libfdx.backend.desktopc.DesktopCVulkanProvider;
import io.github.libfdx.graphics.vulkan.VulkanConfiguration;
import io.github.libfdx.testsupport.PerformanceApplication;
import io.github.libfdx.testsupport.desktopc.DesktopCTestFactory;
import io.github.libfdx.testsupport.desktopc.DesktopCTestLauncherArgs;

/**
 * Launches the desktop C vulkan test entry point.
 *
 * @author xpenatan
 */
public final class DesktopCVulkanTestLauncher {
    private DesktopCVulkanTestLauncher() {}

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
        boolean explicitSize =
                launcherArgs.hasProperty("libfdx.test.width")
                        || launcherArgs.hasProperty("libfdx.test.height");
        boolean maximized = launcherArgs.maximized(explicitSize);
        int width = launcherArgs.width(testName);
        int height = launcherArgs.height(testName);

        DesktopCApplicationConfig config =
                new DesktopCApplicationConfig()
                        .audio(new DesktopCAudioProvider())
                        .title("libfdx Test: " + testName + " - desktop_c Vulkan")
                        .size(width, height)
                        .maximized(maximized)
                        .graphics(
                                new DesktopCVulkanProvider()
                                        .configuration(
                                                new VulkanConfiguration()
                                                        .vSync(!PerformanceApplication.enabled())));

        ApplicationListener test =
                "DesktopCVulkanPreparationTest".equals(testName)
                        ? new DesktopCVulkanPreparationTest(frames)
                        : DesktopCTestFactory.create(testName, frames);
        if (PerformanceApplication.enabled()) {
            config.vSync(false).foregroundFps(0);
        }
        new DesktopCApplicationBackend().start(config, PerformanceApplication.wrap(test, testName));
        if (test instanceof DesktopCVulkanPreparationTest preparation) preparation.verifyShutdown();
    }
}
