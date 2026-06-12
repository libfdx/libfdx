package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentReadiness;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.TextureFormat;

/**
 * Represents a WGPU graphics attachment.
 *
 * @author xpenatan
 */
final class WGPUGraphicsAttachment implements GraphicsAttachment, GraphicsAttachmentReadiness {
    private final WGPUContext context;

    WGPUGraphicsAttachment(WGPUContext context) {
        this.context = context;
    }

    /**
     * Handles a size change.
     *
     * @param framebufferWidth the framebuffer width
     * @param framebufferHeight the framebuffer height
     */
    @Override
    public void resize(int framebufferWidth, int framebufferHeight) {
        context.resize(framebufferWidth, framebufferHeight);
    }

    /**
     * Runs the process events step.
     */
    @Override
    public void processEvents() {
        context.processEvents();
    }

    /**
     * Returns the begin frame.
     *
     * @return true if begin frame succeeds or is active; false otherwise
     */
    @Override
    public boolean beginFrame() {
        return context.beginFrame();
    }

    /**
     * Returns whether ready is enabled or true.
     *
     * @return true if ready is enabled or true; false otherwise
     */
    public boolean isReady() {
        return context.isReady();
    }

    /**
     * Ends frame.
     */
    @Override
    public void endFrame() {
        context.endFrame();
    }

    /**
     * Returns the device.
     *
     * @return the device
     */
    @Override
    public GraphicsDevice device() {
        return context.device();
    }

    /**
     * Returns the surface format.
     *
     * @return the surface format
     */
    @Override
    public TextureFormat surfaceFormat() {
        return context.surfaceFormat();
    }

    /**
     * Returns the current frame.
     *
     * @return the current frame
     */
    @Override
    public GraphicsFrame currentFrame() {
        return context.currentFrame();
    }

    /**
     * Runs the clear step.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    @Override
    public void clear(float red, float green, float blue, float alpha) {
        context.clear(red, green, blue, alpha);
    }

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return context.providerId();
    }

    /**
     * Returns the provider-specific representation requested by the caller.
     *
     * @param <T> the value type
     * @return the as
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) context;
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        context.dispose();
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return context.isDisposed();
    }
}
