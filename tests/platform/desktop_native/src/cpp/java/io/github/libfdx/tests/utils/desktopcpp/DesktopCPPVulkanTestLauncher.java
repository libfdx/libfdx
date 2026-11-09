package io.github.libfdx.tests.utils.desktopcpp;

import io.github.libfdx.backend.desktopcpp.DesktopCppVulkanProvider;
import io.github.libfdx.graphics.vulkan.VulkanConfiguration;
import io.github.libfdx.tests.utils.performance.PerformanceApplication;

/** Launches the shared Vulkan tests with jNative. */
public final class DesktopCPPVulkanTestLauncher {
    private DesktopCPPVulkanTestLauncher() {}

    public static void main(String[] args) {
        DesktopCppTestLauncherSupport.launch(
                "vulkan", args,
                () -> new DesktopCppVulkanProvider().configuration(
                        new VulkanConfiguration().vSync(!PerformanceApplication.enabled())));
    }
}
