package io.github.libfdx.graphics;

/**
 * Lists the supported texture usage values.
 *
 * @author xpenatan
 */
public enum TextureUsage {
    /**
     * The texture can be sampled by shaders.
     */
    SAMPLED(true, false, false),

    /**
     * The texture can be used as a render-pass attachment.
     */
    RENDER_ATTACHMENT(false, true, false),

    /**
     * The texture can be rendered into and later sampled by shaders.
     */
    SAMPLED_RENDER_ATTACHMENT(true, true, false),

    /**
     * The texture can be accessed through a storage-texture binding.
     */
    STORAGE(false, false, true),

    /**
     * The texture can be sampled and accessed through a storage binding.
     */
    SAMPLED_STORAGE(true, false, true),

    /**
     * The texture can be used as both a storage and render attachment.
     */
    STORAGE_RENDER_ATTACHMENT(false, true, true),

    /**
     * The texture supports sampling, storage access, and render attachment use.
     */
    SAMPLED_STORAGE_RENDER_ATTACHMENT(true, true, true);

    private final boolean sampled;
    private final boolean renderAttachment;
    private final boolean storage;

    TextureUsage(boolean sampled, boolean renderAttachment, boolean storage) {
        this.sampled = sampled;
        this.renderAttachment = renderAttachment;
        this.storage = storage;
    }

    /**
     * Returns whether this usage allows shader sampling.
     *
     * @return true when shader sampling is allowed
     */
    public boolean sampled() {
        return sampled;
    }

    /**
     * Returns whether this usage allows render-pass attachment binding.
     *
     * @return true when render-pass attachment binding is allowed
     */
    public boolean renderAttachment() {
        return renderAttachment;
    }

    /**
     * Returns whether this usage allows storage-texture binding.
     *
     * @return true when storage binding is allowed
     */
    public boolean storage() {
        return storage;
    }
}
