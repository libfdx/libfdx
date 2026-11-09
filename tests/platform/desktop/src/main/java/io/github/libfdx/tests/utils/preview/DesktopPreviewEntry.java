package io.github.libfdx.tests.utils.preview;

import io.github.libfdx.backend.desktop.DesktopVulkanProvider;
import io.github.libfdx.graphics.d3d12.D3D12Provider;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.tools.uipreview.UiPreviewConfig;
import io.github.libfdx.tools.uipreview.UiPreviewEntry;

/** Shared window and graphics defaults for the desktop test previews. */
public abstract class DesktopPreviewEntry implements UiPreviewEntry {
    @Override
    public UiPreviewConfig previewConfig() {
        UiPreviewConfig config = new UiPreviewConfig(1440, 1000).title("UI Kit Preview");
        String graphics = System.getProperty("libfdx.uiPreview.graphics", "gl");
        switch (graphics) {
            case "gl" -> { }
            case "vulkan" -> config.graphics(new DesktopVulkanProvider());
            case "d3d12" -> config.graphics(new D3D12Provider());
            case "wgpu" -> config.graphics(new WGPUProvider());
            default -> throw new IllegalArgumentException("Unknown preview graphics provider: " + graphics);
        }
        return config;
    }

}
