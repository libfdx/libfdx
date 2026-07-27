package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

/**
 * Describes the values used to create or identify a render pass.
 *
 * @author xpenatan
 */
public final class RenderPassDescriptor {
    private String label = "";
    private TextureView colorAttachment;
    private LoadOp colorLoadOp = LoadOp.load();
    private StoreOp colorStoreOp = StoreOp.store();
    private RenderPassColorAttachment[] colorAttachments = new RenderPassColorAttachment[0];
    private RenderPassDepthStencilAttachment depthStencilAttachment;
    private RenderPassCompatibility compatibility;
    private boolean depthEnabled;
    private boolean depthClearEnabled;
    private float depthClearValue = 1.0f;

    /**
     * Creates a render pass descriptor.
     *
     * @param colorAttachment the color attachment
     * @param loadOp the load op
     * @param storeOp the store op
     * @return a new render pass descriptor
     */
    public static RenderPassDescriptor color(TextureView colorAttachment, LoadOp loadOp, StoreOp storeOp) {
        return new RenderPassDescriptor()
                .colorAttachment(colorAttachment)
                .colorLoadOp(loadOp)
                .colorStoreOp(storeOp);
    }

    /**
     * Returns the label.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * Sets the label and returns this render pass descriptor.
     *
     * @param label the debug label
     * @return this render pass descriptor for chaining
     */
    public RenderPassDescriptor label(String label) {
        this.label = label != null ? label : "";
        return this;
    }

    /**
     * Returns the color attachment.
     *
     * @return the color attachment
     */
    public TextureView colorAttachment() {
        return colorAttachments.length > 0 ? colorAttachments[0].view() : colorAttachment;
    }

    /**
     * Sets the color attachment and returns this render pass descriptor.
     *
     * @param colorAttachment the color attachment
     * @return this render pass descriptor for chaining
     */
    public RenderPassDescriptor colorAttachment(TextureView colorAttachment) {
        if (colorAttachment == null) {
            throw new FdxException("Render pass color attachment cannot be null");
        }
        this.colorAttachment = colorAttachment;
        this.colorAttachments = new RenderPassColorAttachment[0];
        return this;
    }

    /**
     * Returns the color load op.
     *
     * @return the color load op
     */
    public LoadOp colorLoadOp() {
        return colorLoadOp;
    }

    /**
     * Sets the color load op and returns this render pass descriptor.
     *
     * @param colorLoadOp the color load op
     * @return this render pass descriptor for chaining
     */
    public RenderPassDescriptor colorLoadOp(LoadOp colorLoadOp) {
        this.colorLoadOp = colorLoadOp != null ? colorLoadOp : LoadOp.load();
        rebuildFirstColorAttachment();
        return this;
    }

    /**
     * Returns the color store op.
     *
     * @return the color store op
     */
    public StoreOp colorStoreOp() {
        return colorStoreOp;
    }

    /**
     * Sets the color store op and returns this render pass descriptor.
     *
     * @param colorStoreOp the color store op
     * @return this render pass descriptor for chaining
     */
    public RenderPassDescriptor colorStoreOp(StoreOp colorStoreOp) {
        this.colorStoreOp = colorStoreOp != null ? colorStoreOp : StoreOp.store();
        rebuildFirstColorAttachment();
        return this;
    }

    /**
     * Returns all explicit color attachments.
     *
     * @return a defensive copy of the attachments
     */
    public RenderPassColorAttachment[] colorAttachments() {
        if (colorAttachments.length > 0) {
            return colorAttachments.clone();
        }
        if (colorAttachment == null) {
            return new RenderPassColorAttachment[0];
        }
        return new RenderPassColorAttachment[] {
                RenderPassColorAttachment.of(colorAttachment, colorLoadOp, colorStoreOp)
        };
    }

    /**
     * Sets one or more color/resolve attachments.
     *
     * @param attachments explicit attachments
     * @return this descriptor
     */
    public RenderPassDescriptor colorAttachments(RenderPassColorAttachment... attachments) {
        if (attachments == null || attachments.length == 0) {
            throw new FdxException("Render pass requires at least one color attachment");
        }
        for (RenderPassColorAttachment attachment : attachments) {
            if (attachment == null) {
                throw new FdxException("Render pass color attachment cannot be null");
            }
        }
        colorAttachments = attachments.clone();
        colorAttachment = colorAttachments[0].view();
        colorLoadOp = colorAttachments[0].loadOp();
        colorStoreOp = colorAttachments[0].storeOp();
        return this;
    }

    /**
     * Returns the explicit depth/stencil attachment, or {@code null}.
     *
     * @return depth/stencil attachment
     */
    public RenderPassDepthStencilAttachment depthStencilAttachment() {
        return depthStencilAttachment;
    }

    /**
     * Sets an explicit depth/stencil attachment.
     *
     * @param attachment attachment
     * @return this descriptor
     */
    public RenderPassDescriptor depthStencilAttachment(
            RenderPassDepthStencilAttachment attachment) {
        if (attachment == null) {
            throw new FdxException("Render pass depth/stencil attachment cannot be null");
        }
        depthStencilAttachment = attachment;
        depthEnabled = true;
        return this;
    }

    /**
     * Returns exact externally supplied compatibility metadata, deriving it
     * from attachment views when it was not supplied explicitly.
     *
     * @return compatibility metadata
     */
    public RenderPassCompatibility compatibility() {
        RenderPassCompatibility derived = deriveCompatibility();
        if (compatibility != null && derived != null
                && !compatibility.targetLayout().equals(derived.targetLayout())) {
            throw new FdxException("Render pass compatibility metadata does not match its attachments");
        }
        if (compatibility != null && compatibility.hasDimensions()
                && derived != null && derived.hasDimensions()
                && (compatibility.width() != derived.width()
                || compatibility.height() != derived.height())) {
            throw new FdxException("Render pass compatibility dimensions do not match its attachments");
        }
        return compatibility != null ? compatibility : derived;
    }

    /**
     * Supplies exact compatibility metadata for an externally created pass.
     *
     * @param value compatibility metadata
     * @return this descriptor
     */
    public RenderPassDescriptor compatibility(RenderPassCompatibility value) {
        if (value == null) {
            throw new FdxException("Render pass compatibility cannot be null");
        }
        compatibility = value;
        return this;
    }

    /**
     * Returns the depth enabled.
     *
     * @return true if depth enabled succeeds or is active; false otherwise
     */
    public boolean depthEnabled() {
        return depthEnabled;
    }

    /**
     * Sets the depth enabled and returns this render pass descriptor.
     *
     * @param depthEnabled the depth enabled
     * @return this render pass descriptor for chaining
     */
    public RenderPassDescriptor depthEnabled(boolean depthEnabled) {
        this.depthEnabled = depthEnabled;
        return this;
    }

    /**
     * Returns the depth clear enabled.
     *
     * @return true if depth clear enabled succeeds or is active; false otherwise
     */
    public boolean depthClearEnabled() {
        return depthClearEnabled;
    }

    /**
     * Returns the depth clear value.
     *
     * @return the depth clear value
     */
    public float depthClearValue() {
        return depthClearValue;
    }

    /**
     * Sets the depth clear and returns this render pass descriptor.
     *
     * @param depthClearValue the depth clear value
     * @return this render pass descriptor for chaining
     */
    public RenderPassDescriptor depthClear(float depthClearValue) {
        if (Float.isNaN(depthClearValue) || depthClearValue < 0.0f || depthClearValue > 1.0f) {
            throw new FdxException("Depth clear value must be between 0 and 1");
        }
        this.depthEnabled = true;
        this.depthClearEnabled = true;
        this.depthClearValue = depthClearValue;
        return this;
    }

    /**
     * Validates attachment features, formats, sample counts, and explicit
     * compatibility before a provider begins native recording.
     *
     * @param capabilities device capabilities
     * @return exact compatibility for the pass
     */
    public RenderPassCompatibility validate(GraphicsCapabilities capabilities) {
        if (capabilities == null) {
            throw new FdxException("Graphics capabilities cannot be null");
        }
        RenderPassColorAttachment[] colors = colorAttachments();
        if (colors.length == 0 && depthStencilAttachment == null) {
            throw new FdxException("Render pass requires at least one attachment");
        }
        RenderPassCompatibility result = compatibility();
        if (result == null) {
            throw new FdxException("Render pass compatibility could not be derived");
        }
        result.targetLayout().validate(capabilities);
        for (RenderPassColorAttachment color : colors) {
            if (color.resolveView() != null) {
                capabilities.require(GraphicsFeature.RESOLVE_ATTACHMENTS);
                if (!capabilities.supportsResolveFormat(color.view().format())) {
                    throw new FdxException("Graphics device cannot resolve color format "
                            + color.view().format());
                }
            }
        }
        if (depthStencilAttachment != null) {
            capabilities.require(GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS);
        }
        return result;
    }

    private void rebuildFirstColorAttachment() {
        if (colorAttachments.length == 0) {
            return;
        }
        RenderPassColorAttachment first = colorAttachments[0];
        colorAttachments[0] = first.resolveView() != null
                ? RenderPassColorAttachment.resolve(first.view(), first.resolveView(),
                        colorLoadOp, colorStoreOp)
                : RenderPassColorAttachment.of(first.view(), colorLoadOp, colorStoreOp);
    }

    private RenderPassCompatibility deriveCompatibility() {
        RenderPassColorAttachment[] colors = colorAttachments();
        if (colors.length == 0 && depthStencilAttachment == null) {
            return null;
        }
        TextureFormat[] colorFormats = new TextureFormat[colors.length];
        int sampleCount = depthStencilAttachment != null
                ? depthStencilAttachment.view().sampleCount() : colors[0].view().sampleCount();
        int width = depthStencilAttachment != null
                ? depthStencilAttachment.view().width() : colors[0].view().width();
        int height = depthStencilAttachment != null
                ? depthStencilAttachment.view().height() : colors[0].view().height();
        for (int i = 0; i < colors.length; i++) {
            colorFormats[i] = colors[i].view().format();
            if (colors[i].view().sampleCount() != sampleCount) {
                throw new FdxException("Render pass attachments have different sample counts");
            }
            requireMatchingDimensions(width, height, colors[i].view(), "color");
            if (colors[i].resolveView() != null) {
                TextureView resolve = colors[i].resolveView();
                if (resolve.sampleCount() != 1) {
                    throw new FdxException("Render pass resolve attachments must be single-sampled");
                }
                if (resolve.format() != colors[i].view().format()) {
                    throw new FdxException("Render pass resolve format does not match its color attachment");
                }
                requireMatchingDimensions(width, height, resolve, "resolve");
            }
        }
        if (depthStencilAttachment != null
                && depthStencilAttachment.view().sampleCount() != sampleCount) {
            throw new FdxException("Render pass depth and color attachments have different sample counts");
        }
        TextureFormat depthFormat = depthStencilAttachment != null
                ? depthStencilAttachment.view().format()
                : depthEnabled ? TextureFormat.DEPTH32_FLOAT : TextureFormat.UNKNOWN;
        RenderTargetLayout layout = RenderTargetLayout.of(
                colorFormats, depthFormat, sampleCount);
        return width > 0 && height > 0
                ? RenderPassCompatibility.of(layout, width, height)
                : RenderPassCompatibility.layout(layout);
    }

    private static void requireMatchingDimensions(int width, int height,
            TextureView view, String role) {
        int viewWidth = view.width();
        int viewHeight = view.height();
        if ((viewWidth == 0) != (viewHeight == 0)) {
            throw new FdxException("Render pass " + role
                    + " attachment dimensions must both be known or unknown");
        }
        if (width > 0 && viewWidth > 0
                && (width != viewWidth || height != viewHeight)) {
            throw new FdxException("Render pass attachments have different dimensions");
        }
    }
}
