package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

public final class RenderPassDescriptor {
    private String label = "";
    private TextureView colorAttachment;
    private LoadOp colorLoadOp = LoadOp.load();
    private StoreOp colorStoreOp = StoreOp.store();
    private boolean depthEnabled;
    private boolean depthClearEnabled;
    private float depthClearValue = 1.0f;

    public static RenderPassDescriptor color(TextureView colorAttachment, LoadOp loadOp, StoreOp storeOp) {
        return new RenderPassDescriptor()
                .colorAttachment(colorAttachment)
                .colorLoadOp(loadOp)
                .colorStoreOp(storeOp);
    }

    public String label() {
        return label;
    }

    public RenderPassDescriptor label(String label) {
        this.label = label != null ? label : "";
        return this;
    }

    public TextureView colorAttachment() {
        return colorAttachment;
    }

    public RenderPassDescriptor colorAttachment(TextureView colorAttachment) {
        if (colorAttachment == null) {
            throw new FdxException("Render pass color attachment cannot be null");
        }
        this.colorAttachment = colorAttachment;
        return this;
    }

    public LoadOp colorLoadOp() {
        return colorLoadOp;
    }

    public RenderPassDescriptor colorLoadOp(LoadOp colorLoadOp) {
        this.colorLoadOp = colorLoadOp != null ? colorLoadOp : LoadOp.load();
        return this;
    }

    public StoreOp colorStoreOp() {
        return colorStoreOp;
    }

    public RenderPassDescriptor colorStoreOp(StoreOp colorStoreOp) {
        this.colorStoreOp = colorStoreOp != null ? colorStoreOp : StoreOp.store();
        return this;
    }

    public boolean depthEnabled() {
        return depthEnabled;
    }

    public RenderPassDescriptor depthEnabled(boolean depthEnabled) {
        this.depthEnabled = depthEnabled;
        return this;
    }

    public boolean depthClearEnabled() {
        return depthClearEnabled;
    }

    public float depthClearValue() {
        return depthClearValue;
    }

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
