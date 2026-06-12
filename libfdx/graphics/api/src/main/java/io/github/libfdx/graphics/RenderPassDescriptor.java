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
        return colorAttachment;
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
}
