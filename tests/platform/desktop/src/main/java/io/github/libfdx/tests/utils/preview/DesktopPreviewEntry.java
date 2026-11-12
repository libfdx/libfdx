package io.github.libfdx.tests.utils.preview;

import io.github.libfdx.backend.desktop.DesktopVulkanProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.d3d12.D3D12Provider;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.tools.uipreview.UiPreviewConfig;
import io.github.libfdx.tools.uipreview.UiPreviewEntry;

/** Shared window and graphics defaults for the desktop test previews. */
public abstract class DesktopPreviewEntry implements UiPreviewEntry {
    private static final GraphicsAttachmentProvider GRAPHICS = previewGraphics();

    @Override
    public UiPreviewConfig previewConfig() {
        return new UiPreviewConfig(1440, 1000).title("UI Kit Preview").graphics(GRAPHICS);
    }

    private static GraphicsAttachmentProvider previewGraphics() {
        String graphics = System.getProperty("libfdx.uiPreview.graphics", "gl");
        return switch (graphics) {
            case "gl" -> null;
            case "vulkan" -> new DesktopVulkanProvider();
            case "d3d12" -> new D3D12Provider();
            case "wgpu" -> new WGPUProvider();
            default -> throw new IllegalArgumentException("Unknown preview graphics provider: " + graphics);
        };
    }

}
