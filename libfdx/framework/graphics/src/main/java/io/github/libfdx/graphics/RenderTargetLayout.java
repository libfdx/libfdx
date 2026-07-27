package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable attachment formats and sample count required by a render pipeline
 * or supplied by a render pass.
 */
public final class RenderTargetLayout {
    private final TextureFormat[] colorFormats;
    private final TextureFormat depthStencilFormat;
    private final int sampleCount;
    private final String structuralKey;

    private RenderTargetLayout(TextureFormat[] colorFormats,
            TextureFormat depthStencilFormat, int sampleCount) {
        this.colorFormats = colorFormats != null ? colorFormats.clone() : new TextureFormat[0];
        for (TextureFormat format : this.colorFormats) {
            if (format == null || !format.isColor()) {
                throw new FdxException("Render target color format must be a concrete color format");
            }
        }
        this.depthStencilFormat = depthStencilFormat != null
                ? depthStencilFormat : TextureFormat.UNKNOWN;
        if (this.depthStencilFormat != TextureFormat.UNKNOWN
                && !this.depthStencilFormat.isDepthStencil()) {
            throw new FdxException("Render target depth/stencil format is not a depth/stencil format");
        }
        if (this.colorFormats.length == 0 && this.depthStencilFormat == TextureFormat.UNKNOWN) {
            throw new FdxException("Render target layout must contain at least one attachment");
        }
        if (sampleCount <= 0 || (sampleCount & (sampleCount - 1)) != 0) {
            throw new FdxException("Render target sample count must be a positive power of two");
        }
        this.sampleCount = sampleCount;
        structuralKey = computeKey();
    }

    public static RenderTargetLayout color(TextureFormat format) {
        return of(new TextureFormat[] { format }, TextureFormat.UNKNOWN, 1);
    }

    public static RenderTargetLayout of(TextureFormat[] colorFormats,
            TextureFormat depthStencilFormat, int sampleCount) {
        return new RenderTargetLayout(colorFormats, depthStencilFormat, sampleCount);
    }

    public TextureFormat[] colorFormats() {
        return colorFormats.clone();
    }

    public int colorAttachmentCount() {
        return colorFormats.length;
    }

    public TextureFormat colorFormat(int index) {
        return colorFormats[index];
    }

    public TextureFormat depthStencilFormat() {
        return depthStencilFormat;
    }

    public boolean hasDepthStencil() {
        return depthStencilFormat != TextureFormat.UNKNOWN;
    }

    public int sampleCount() {
        return sampleCount;
    }

    public String structuralKey() {
        return structuralKey;
    }

    public void validate(GraphicsCapabilities capabilities) {
        if (capabilities == null) {
            throw new FdxException("Graphics capabilities cannot be null");
        }
        if (colorFormats.length > capabilities.limits().maxColorAttachments()) {
            throw new FdxException("Render target requires " + colorFormats.length
                    + " color attachments, provider limit is "
                    + capabilities.limits().maxColorAttachments());
        }
        if (colorFormats.length > 1) {
            capabilities.require(GraphicsFeature.MULTIPLE_COLOR_ATTACHMENTS);
        }
        for (TextureFormat format : colorFormats) {
            if (!capabilities.supportsColorFormat(format)) {
                throw new FdxException("Graphics device does not support color format " + format);
            }
            if (!capabilities.supportsSampleCount(format, sampleCount)) {
                throw new FdxException("Graphics device does not support sample count "
                        + sampleCount + " for color format " + format);
            }
        }
        if (hasDepthStencil()) {
            capabilities.require(GraphicsFeature.DEPTH_STENCIL_ATTACHMENTS);
            if (!capabilities.supportsDepthStencilFormat(depthStencilFormat)) {
                throw new FdxException("Graphics device does not support depth/stencil format "
                        + depthStencilFormat);
            }
            if (!capabilities.supportsSampleCount(depthStencilFormat, sampleCount)) {
                throw new FdxException("Graphics device does not support sample count "
                        + sampleCount + " for depth/stencil format " + depthStencilFormat);
            }
        }
        if (sampleCount > 1) {
            capabilities.require(GraphicsFeature.MULTISAMPLE);
        }
        if (!capabilities.supportsSampleCount(sampleCount)) {
            throw new FdxException("Graphics device does not support sample count " + sampleCount);
        }
    }

    private String computeKey() {
        PortableSha256 digest = new PortableSha256()
                .updateSizedUtf8("fdx-render-target-layout-v1")
                .updateInt(colorFormats.length);
        for (TextureFormat format : colorFormats) {
            digest.updateSizedUtf8(format.name());
        }
        return digest.updateSizedUtf8(depthStencilFormat.name())
                .updateInt(sampleCount).digestHex();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof RenderTargetLayout other
                && sampleCount == other.sampleCount
                && depthStencilFormat == other.depthStencilFormat
                && Arrays.equals(colorFormats, other.colorFormats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(colorFormats), depthStencilFormat, sampleCount);
    }
}
