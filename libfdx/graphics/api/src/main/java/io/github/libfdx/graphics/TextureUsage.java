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
    SAMPLED(true, false),

    /**
     * The texture can be used as a render-pass attachment.
     */
    RENDER_ATTACHMENT(false, true),

    /**
     * The texture can be rendered into and later sampled by shaders.
     */
    SAMPLED_RENDER_ATTACHMENT(true, true);

    private final boolean sampled;
    private final boolean renderAttachment;

    TextureUsage(boolean sampled, boolean renderAttachment) {
        this.sampled = sampled;
        this.renderAttachment = renderAttachment;
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
}
