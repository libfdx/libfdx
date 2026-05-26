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

    @Override
    public void resize(int framebufferWidth, int framebufferHeight) {
        width = framebufferWidth;
        height = framebufferHeight;
    }

    @Override
    public void processEvents() {
    }

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

    @Override
    public void endFrame() {
        if (!frameStarted) {
            return;
        }
        frameStarted = false;
        surface.swapBuffers();
    }

    @Override
    public GraphicsDevice device() {
        return device;
    }

    @Override
    public TextureFormat surfaceFormat() {
        return surfaceFormat;
    }

    @Override
    public GraphicsFrame currentFrame() {
        if (!frameStarted) {
            throw new FdxException("No GL frame is active");
        }
        return currentFrame;
    }

    @Override
    public void clear(float red, float green, float blue, float alpha) {
        if (!frameStarted) {
            throw new FdxException("Cannot clear before beginFrame()");
        }
        gl.clearColor(red, green, blue, alpha);
        gl.clearColorBuffer();
    }

    public ByteBuffer readPixelsRgba8() {
        return frameBuffer.readPixelsRgba8();
    }

    @Override
    public ProviderId providerId() {
        return providerId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

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

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private final class GLCommandEncoder implements CommandEncoder {
        @Override
        public RenderPass beginRenderPass(RenderPassDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("RenderPassDescriptor cannot be null");
            }
            if (!frameStarted) {
                throw new FdxException("Cannot begin render pass outside a frame");
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
            return new GLRenderPass(providerId, gl);
        }

        @Override
        public ProviderId providerId() {
            return providerId;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }
    }

    private final class GLGraphicsFrame implements GraphicsFrame {
        @Override
        public CommandEncoder commandEncoder() {
            return commandEncoder;
        }

        @Override
        public FrameBuffer frameBuffer() {
            return frameBuffer;
        }

        @Override
        public TextureView colorAttachment() {
            return colorAttachment;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public ProviderId providerId() {
            return providerId;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }
    }

    private final class GLFrameBuffer implements FrameBuffer {
        @Override
        public TextureView colorAttachment() {
            return colorAttachment;
        }

        @Override
        public TextureFormat format() {
            return surfaceFormat;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

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

        @Override
        public ProviderId providerId() {
            return providerId;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }
    }
}
