package io.github.libfdx.backend.desktop;

import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.TextureFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DesktopVulkanProviderTest {
    @Test
    void advertisesAlphaBlendControlWithoutCompletePipelineState() {
        assertTrue(DesktopVulkanProvider.CAPABILITIES.supports(
                GraphicsFeature.ALPHA_BLEND_CONTROL));
        assertFalse(DesktopVulkanProvider.CAPABILITIES.supports(
                GraphicsFeature.COMPLETE_RENDER_PIPELINE_STATE));
    }

    @Test
    void selectsBlendEnableFromTheColorTarget() {
        assertFalse(DesktopVulkanProvider.blendEnabled(
                ColorTargetState.opaque(TextureFormat.RGBA8_UNORM)));
        assertTrue(DesktopVulkanProvider.blendEnabled(
                ColorTargetState.alpha(TextureFormat.RGBA8_UNORM)));
    }
}
