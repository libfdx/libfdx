package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureView;

import java.nio.ByteBuffer;

/**
 * Represents a GL graphics attachment.
 *
 * @author xpenatan
 */
public final class GLGraphicsAttachment implements GraphicsAttachment {
    private final ProviderId providerId;
    private final GLApi gl;
    private final GLSurface surface;
    private final GLGraphicsDevice device;
    private final GLCommandEncoder commandEncoder = new GLCommandEncoder();
    private final GLTextureViewHandle colorAttachment;
    private final GLFrameBuffer frameBuffer = new GLFrameBuffer();
    private final GLGraphicsFrame currentFrame = new GLGraphicsFrame();
    private final int vertexArray;
    private final TextureFormat surfaceFormat;
    private int width;
    private int height;
    private boolean frameStarted;
    private boolean disposed;

    /**
     * Creates a GL graphics attachment.
     *
     * @param providerId the provider ID
     * @param gl the GL
     * @param surface the surface
     * @param width the width in pixels
     * @param height the height in pixels
     * @param surfaceFormat the surface format
     */
    public GLGraphicsAttachment(ProviderId providerId, GLApi gl, GLSurface surface, int width, int height,
            TextureFormat surfaceFormat) {
        if (providerId == null) {
            throw new FdxException("GL provider ID cannot be null");
        }
        if (gl == null) {
            throw new FdxException("GL API cannot be null");
        }
        if (surface == null) {
            throw new FdxException("GL surface cannot be null");
        }
        this.providerId = providerId;
        this.gl = gl;
        this.surface = surface;
        this.width = width;
        this.height = height;
        this.surfaceFormat = surfaceFormat != null ? surfaceFormat : TextureFormat.RGBA8_UNORM;
        device = new GLGraphicsDevice(providerId, gl);
        colorAttachment = new GLTextureViewHandle(providerId, this.surfaceFormat);
        vertexArray = gl.genVertexArray();
    }

    /**
     * Handles a size change.
     *
     * @param framebufferWidth the framebuffer width
     * @param framebufferHeight the framebuffer height
     */
    @Override
    public void resize(int framebufferWidth, int framebufferHeight) {
        width = framebufferWidth;
        height = framebufferHeight;
    }

    /**
     * Runs the process events step.
     */
    @Override
    public void processEvents() {
    }

    /**
     * Returns the begin frame.
     *
     * @return true if begin frame succeeds or is active; false otherwise
     */
    @Override
    public boolean beginFrame() {
        if (disposed || width <= 0 || height <= 0) {
            return false;
        }
        if (frameStarted) {
            throw new FdxException("GL frame is already started");
        }
        gl.bindVertexArray(vertexArray);
        gl.viewport(0, 0, width, height);
        frameStarted = true;
        return true;
    }

    /**
     * Ends frame.
     */
    @Override
    public void endFrame() {
        if (!frameStarted) {
            return;
        }
        frameStarted = false;
        surface.swapBuffers();
    }

    /**
     * Returns the device.
     *
     * @return the device
     */
    @Override
    public GraphicsDevice device() {
        return device;
    }

    /**
     * Returns the surface format.
     *
     * @return the surface format
     */
    @Override
    public TextureFormat surfaceFormat() {
        return surfaceFormat;
    }

    /**
     * Returns the current frame.
     *
     * @return the current frame
     */
    @Override
    public GraphicsFrame currentFrame() {
        if (!frameStarted) {
            throw new FdxException("No GL frame is active");
        }
        return currentFrame;
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
        if (!frameStarted) {
            throw new FdxException("Cannot clear before beginFrame()");
        }
        gl.clearColor(red, green, blue, alpha);
        gl.clearColorBuffer();
    }

    /**
     * Returns the read pixels RGBA8.
     *
     * @return the read pixels RGBA8
     */
    public ByteBuffer readPixelsRgba8() {
        return frameBuffer.readPixelsRgba8();
    }

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return providerId;
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
        return (T) this;
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        gl.deleteVertexArray(vertexArray);
        surface.releaseCurrent();
        if (surface instanceof Disposable) {
            ((Disposable) surface).dispose();
        }
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Represents a GL command encoder.
     *
     * @author xpenatan
     */
    private final class GLCommandEncoder implements CommandEncoder {
        /**
         * Begins render pass.
         *
         * @param descriptor the descriptor
         * @return the begin render pass
         */
        @Override
        public RenderPass beginRenderPass(RenderPassDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("RenderPassDescriptor cannot be null");
            }
            if (!frameStarted) {
                throw new FdxException("Cannot begin render pass outside a frame");
            }
            GLTextureViewHandle attachment = descriptor.colorAttachment().as();
            boolean textureBacked = attachment.textureBacked();
            if (textureBacked) {
                gl.bindFramebuffer(attachment.framebuffer(gl));
                gl.viewport(0, 0, attachment.width(), attachment.height());
            } else {
                gl.bindFramebuffer(0);
                gl.viewport(0, 0, width, height);
            }
            if (descriptor.colorLoadOp().isClear()) {
                LoadOp clear = descriptor.colorLoadOp();
                gl.clearColor(clear.red(), clear.green(), clear.blue(), clear.alpha());
                gl.clearColorBuffer();
            }
            if (descriptor.depthEnabled()) {
                gl.enableDepthTest(true);
                gl.depthFuncLessEqual();
                gl.depthMask(true);
                if (descriptor.depthClearEnabled()) {
                    gl.clearDepth(descriptor.depthClearValue());
                    gl.clearDepthBuffer();
                }
            }
            return new GLRenderPass(providerId, gl, textureBacked, width, height);
        }

        /**
         * Returns the identifier of the provider backing this object.
         *
         * @return the provider ID
         */
        @Override
        public ProviderId providerId() {
            return providerId;
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
            return (T) this;
        }
    }

    /**
     * Represents a GL graphics frame.
     *
     * @author xpenatan
     */
    private final class GLGraphicsFrame implements GraphicsFrame {
        /**
         * Returns the command encoder.
         *
         * @return the command encoder
         */
        @Override
        public CommandEncoder commandEncoder() {
            return commandEncoder;
        }

        /**
         * Returns the frame buffer.
         *
         * @return the frame buffer
         */
        @Override
        public FrameBuffer frameBuffer() {
            return frameBuffer;
        }

        /**
         * Returns the color attachment.
         *
         * @return the color attachment
         */
        @Override
        public TextureView colorAttachment() {
            return colorAttachment;
        }

        /**
         * Returns the width.
         *
         * @return the width
         */
        @Override
        public int width() {
            return width;
        }

        /**
         * Returns the height.
         *
         * @return the height
         */
        @Override
        public int height() {
            return height;
        }

        /**
         * Returns the identifier of the provider backing this object.
         *
         * @return the provider ID
         */
        @Override
        public ProviderId providerId() {
            return providerId;
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
            return (T) this;
        }
    }

    /**
     * Represents a GL frame buffer.
     *
     * @author xpenatan
     */
    private final class GLFrameBuffer implements FrameBuffer {
        /**
         * Returns the color attachment.
         *
         * @return the color attachment
         */
        @Override
        public TextureView colorAttachment() {
            return colorAttachment;
        }

        /**
         * Returns the format.
         *
         * @return the format
         */
        @Override
        public TextureFormat format() {
            return surfaceFormat;
        }

        /**
         * Returns the width.
         *
         * @return the width
         */
        @Override
        public int width() {
            return width;
        }

        /**
         * Returns the height.
         *
         * @return the height
         */
        @Override
        public int height() {
            return height;
        }

        /**
         * Returns the read pixels RGBA8.
         *
         * @return the read pixels RGBA8
         */
        @Override
        public ByteBuffer readPixelsRgba8() {
            if (!frameStarted) {
                throw new FdxException("Cannot read pixels before beginFrame()");
            }
            ByteBuffer pixels = gl.readPixelsRgba8(width, height);
            endFrame();
            pixels.position(0);
            return pixels;
        }

        /**
         * Returns the identifier of the provider backing this object.
         *
         * @return the provider ID
         */
        @Override
        public ProviderId providerId() {
            return providerId;
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
            return (T) this;
        }
    }
}
