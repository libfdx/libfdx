package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.ComputePass;
import io.github.libfdx.graphics.ComputePassDescriptor;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsContextLostException;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.GraphicsFrameMetrics;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Represents a GL graphics attachment.
 *
 * @author xpenatan
 */
public final class GLGraphicsAttachment implements GraphicsAttachment {
    private static final ThreadLocal<GLGraphicsAttachment> CURRENT_ATTACHMENT = new ThreadLocal<>();

    private final ProviderId providerId;
    private final GLApi gl;
    private final GLSurface surface;
    private final GLResourceDomain resourceDomain;
    private final GLGraphicsDevice device;
    private final GLMultipleTargets multipleTargets;
    private final GLCommandEncoder commandEncoder = new GLCommandEncoder();
    private final GLTextureViewHandle colorAttachment;
    private final GLFrameBuffer frameBuffer = new GLFrameBuffer();
    private final GLGraphicsFrame currentFrame = new GLGraphicsFrame();
    private final int vertexArray;
    private final TextureFormat surfaceFormat;
    private GLTextureHandle[] renderTargetTextures = new GLTextureHandle[8];
    private GLTextureHandle[] renderTargetDepthTextures = new GLTextureHandle[8];
    private int[] renderTargetFramebuffers = new int[8];
    private int[] renderTargetDepthBuffers = new int[8];
    private int[] renderTargetMipLevels = new int[8];
    private int width;
    private int height;
    private boolean frameStarted;
    private boolean disposed;
    private long submittedFrameId;

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
        this(providerId, gl, surface, width, height, surfaceFormat, null);
    }

    /**
     * Creates a GL graphics attachment in an existing attachment's native share group.
     *
     * @param providerId the provider ID
     * @param gl the GL API
     * @param surface the surface
     * @param width the width in pixels
     * @param height the height in pixels
     * @param surfaceFormat the surface format
     * @param sharedAttachment an explicitly shared attachment, or null for an independent resource domain
     */
    public GLGraphicsAttachment(ProviderId providerId, GLApi gl, GLSurface surface, int width, int height,
            TextureFormat surfaceFormat, GLGraphicsAttachment sharedAttachment) {
        this(providerId, gl, surface, width, height, surfaceFormat, sharedAttachment, null);
    }

    /** Creates an attachment with optional borrowed compiler-artifact storage.
     * Continuations run on adapter CPU workers or explicit loading advances when workers are unavailable.
     * The application keeps the store available until preparation has drained and owns its disposal. */
    public GLGraphicsAttachment(ProviderId providerId, GLApi gl, GLSurface surface, int width, int height,
            TextureFormat surfaceFormat, GLGraphicsAttachment sharedAttachment, ShaderArtifactCache shaderCache) {
        if (providerId == null) {
            throw new FdxException("GL provider ID cannot be null");
        }
        if (gl == null) {
            throw new FdxException("GL API cannot be null");
        }
        if (surface == null) {
            throw new FdxException("GL surface cannot be null");
        }
        if (sharedAttachment != null) {
            if (sharedAttachment.isDisposed()) {
                throw new FdxException("Cannot share a disposed GL graphics attachment");
            }
            if (!providerId.equals(sharedAttachment.providerId())) {
                throw new FdxException("Cannot share GL attachments from different providers");
            }
        }
        this.providerId = providerId;
        this.gl = gl;
        this.surface = surface;
        this.resourceDomain = sharedAttachment != null
                ? sharedAttachment.resourceDomain
                : new GLResourceDomain(providerId);
        this.width = width;
        this.height = height;
        this.surfaceFormat = surfaceFormat != null ? surfaceFormat : TextureFormat.RGBA8_UNORM;
        makeCurrent();
        device = new GLGraphicsDevice(providerId, gl, resourceDomain, this, shaderCache);
        multipleTargets = new GLMultipleTargets(gl, resourceDomain, this);
        colorAttachment = new GLTextureViewHandle(providerId, resourceDomain, this.surfaceFormat, this);
        vertexArray = gl.genVertexArray();
        resourceDomain.add(this);
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
        if (!disposed) {
            detectContextLoss();
            resourceDomain.requireUsable();
        }
    }

    /**
     * Returns the begin frame.
     *
     * @return true if begin frame succeeds or is active; false otherwise
     */
    @Override
    public boolean beginFrame() {
        if (disposed) return false;
        detectContextLoss();
        resourceDomain.requireUsable();
        if (width <= 0 || height <= 0) {
            return false;
        }
        if (frameStarted) {
            throw new FdxException("GL frame is already started");
        }
        makeCurrent();
        commandEncoder.beginFrame();
        gl.beginFrameMetrics(++submittedFrameId);
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
        if (detectContextLoss()) {
            commandEncoder.abandonFrame();
            frameStarted = false;
            resourceDomain.requireUsable();
        }
        makeCurrent();
        commandEncoder.ensurePassesEnded();
        gl.endFrameMetrics();
        frameStarted = false;
        try {
            surface.swapBuffers();
        } catch (GraphicsContextLostException lost) {
            resourceDomain.invalidate();
            throw lost;
        }
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
        resourceDomain.requireUsable();
        if (!frameStarted) {
            throw new FdxException("No GL frame is active");
        }
        return currentFrame;
    }

    @Override
    public GraphicsFrameMetrics frameMetrics() {
        return gl.frameMetrics();
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
        makeCurrent();
        gl.framebufferSrgb(surfaceFormat.isSrgb());
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
        boolean lost=detectContextLoss();
        if (!lost) makeCurrent();
        else commandEncoder.abandonFrame();
        device.closePreparation(lost);
        disposeRenderTargets();
        multipleTargets.dispose();
        commandEncoder.dispose();
        if (!lost) {
            gl.disposeFrameMetrics();
            gl.deleteVertexArray(vertexArray);
        }
        frameStarted=false;
        resourceDomain.remove(this);
        disposed = true;
        if (CURRENT_ATTACHMENT.get() == this) {
            surface.releaseCurrent();
            CURRENT_ATTACHMENT.remove();
        }
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

    void makeCurrent() {
        resourceDomain.requireUsable();
        if (disposed) {
            throw new FdxException("GL graphics attachment has been disposed");
        }
        makeContextCurrent();
        cleanupDisposedRenderTargets();
    }

    private void makeContextCurrent() {
        if (CURRENT_ATTACHMENT.get() != this) {
            try {
                surface.makeCurrent();
                CURRENT_ATTACHMENT.set(this);
            } catch (GraphicsContextLostException lost) {
                CURRENT_ATTACHMENT.remove();
                resourceDomain.invalidate();
                throw lost;
            }
        }
    }

    boolean detectContextLoss() {
        if (!disposed && !resourceDomain.isLost()) {
            try {
                // Reset status belongs to the current native context. Do not clean up
                // render targets until this query has established that it is usable.
                makeContextCurrent();
                if (gl.isContextLost()) resourceDomain.invalidate();
            } catch (GraphicsContextLostException lost) {
                resourceDomain.invalidate();
            }
        }
        return resourceDomain.isLost();
    }

    void contextLost() { device.closePreparation(true); }

    GLResourceDomain resourceDomain() {
        return resourceDomain;
    }

    private int framebuffer(GLTextureHandle texture, int level, GLTextureHandle depthTexture) {
        cleanupDisposedRenderTargets();
        int freeSlot = -1;
        for (int i = 0; i < renderTargetTextures.length; i++) {
            if (renderTargetTextures[i] == texture && renderTargetMipLevels[i] == level && renderTargetDepthTextures[i] == depthTexture) {
                return renderTargetFramebuffers[i];
            }
            if (freeSlot < 0 && renderTargetTextures[i] == null) {
                freeSlot = i;
            }
        }
        if (freeSlot < 0) {
            freeSlot = renderTargetTextures.length;
            int nextLength = renderTargetTextures.length * 2;
            renderTargetTextures = Arrays.copyOf(renderTargetTextures, nextLength);
            renderTargetDepthTextures = Arrays.copyOf(renderTargetDepthTextures, nextLength);
            renderTargetFramebuffers = Arrays.copyOf(renderTargetFramebuffers, nextLength);
            renderTargetDepthBuffers = Arrays.copyOf(renderTargetDepthBuffers, nextLength);
            renderTargetMipLevels = Arrays.copyOf(renderTargetMipLevels, nextLength);
        }

        int framebuffer = gl.genFramebuffer();
        int depthBuffer = 0;
        try {
        gl.bindFramebuffer(framebuffer);
        gl.framebufferTexture2D(texture.texture(), level);
        if (depthTexture == null) {
        depthBuffer = gl.genRenderbuffer();
        gl.bindRenderbuffer(depthBuffer);
        gl.renderbufferStorageDepth(texture.mipWidth(level), texture.mipHeight(level));
        gl.framebufferRenderbufferDepth(depthBuffer);
        gl.bindRenderbuffer(0);
        } else {
            gl.framebufferDepthTexture2D(depthTexture.texture());
        }
        if (!gl.framebufferComplete()) {
            throw new FdxException("Could not create complete GL framebuffer for texture view");
        }
        } catch (RuntimeException | Error failure) {
            gl.bindFramebuffer(0);
            if (depthBuffer != 0) gl.deleteRenderbuffer(depthBuffer);
            gl.deleteFramebuffer(framebuffer);
            throw failure;
        }
        renderTargetTextures[freeSlot] = texture;
        renderTargetDepthTextures[freeSlot] = depthTexture;
        renderTargetMipLevels[freeSlot] = level;
        renderTargetFramebuffers[freeSlot] = framebuffer;
        renderTargetDepthBuffers[freeSlot] = depthBuffer;
        return framebuffer;
    }

    private void cleanupDisposedRenderTargets() {
        for (int i = 0; i < renderTargetTextures.length; i++) {
            GLTextureHandle texture = renderTargetTextures[i];
            if (texture != null && (texture.isDisposed()
                    || renderTargetDepthTextures[i] != null && renderTargetDepthTextures[i].isDisposed())) {
                disposeRenderTarget(i);
            }
        }
    }

    private void disposeRenderTargets() {
        for (int i = 0; i < renderTargetTextures.length; i++) {
            if (renderTargetTextures[i] != null) {
                disposeRenderTarget(i);
            }
        }
    }

    private void disposeRenderTarget(int index) {
        int depthBuffer = renderTargetDepthBuffers[index];
        if (depthBuffer != 0 && !resourceDomain.isLost()) {
            gl.deleteRenderbuffer(depthBuffer);
        }
        int framebuffer = renderTargetFramebuffers[index];
        if (framebuffer != 0 && !resourceDomain.isLost()) {
            gl.deleteFramebuffer(framebuffer);
        }
        renderTargetTextures[index] = null;
        renderTargetDepthTextures[index] = null;
        renderTargetFramebuffers[index] = 0;
        renderTargetDepthBuffers[index] = 0;
    }

    /**
     * Represents a GL command encoder.
     *
     * @author xpenatan
     */
    private final class GLCommandEncoder implements CommandEncoder {
        private GLComputePass[] computePasses = new GLComputePass[4];
        private int computePassCount;

        @Override
        public ComputePass beginComputePass(
                ComputePassDescriptor descriptor) {
            resourceDomain.requireUsable();
            device.capabilities().require(GraphicsFeature.COMPUTE);
            if (descriptor == null || !frameStarted) throw new FdxException("GL compute requires a descriptor and active frame");
            ensurePreviousPassEnded();
            makeCurrent();
            if (computePassCount == computePasses.length) computePasses = Arrays.copyOf(computePasses, computePassCount * 2);
            GLComputePass pass = computePasses[computePassCount];
            if (pass == null) computePasses[computePassCount] = pass =
                    new GLComputePass(providerId, gl, resourceDomain, device.capabilities().limits());
            pass.begin();
            computePassCount++;
            return pass;
        }

        @Override
        public void copyBufferToBuffer(Buffer source, int sourceOffset,
                Buffer destination, int destinationOffset, int size) {
            if (!frameStarted) throw new FdxException("GL buffer copy requires an active frame");
            device.capabilities().require(GraphicsFeature.COMPUTE);
            ensurePreviousPassEnded();
            GLBufferHandle from = GLResources.requireBuffer(source, resourceDomain, "Copy source");
            GLBufferHandle to = GLResources.requireBuffer(destination, resourceDomain, "Copy destination");
            if (sourceOffset < 0 || destinationOffset < 0 || size < 0
                    || sourceOffset > from.size() - size || destinationOffset > to.size() - size
                    || from == to && sourceOffset < destinationOffset + size && destinationOffset < sourceOffset + size) {
                throw new FdxException("Invalid GL buffer copy range");
            }
            makeCurrent();
            gl.copyBuffer(from.buffer(), sourceOffset, to.buffer(), destinationOffset, size);
        }

        void dispose() { for (GLComputePass pass : computePasses) if (pass != null) pass.dispose(); }
        private GLRenderPass[] renderPasses = new GLRenderPass[4];
        private int renderPassCount;

        /**
         * Begins render pass.
         *
         * @param descriptor the descriptor
         * @return the begin render pass
         */
        @Override
        public RenderPass beginRenderPass(RenderPassDescriptor descriptor) {
            resourceDomain.requireUsable();
            if (descriptor == null) {
                throw new FdxException("RenderPassDescriptor cannot be null");
            }
            if (!frameStarted) {
                throw new FdxException("Cannot begin render pass outside a frame");
            }
            ensurePreviousPassEnded();
            RenderPassCompatibility validated = descriptor.validate(device.capabilities());
            if (validated.targetLayout().colorAttachmentCount() > 1 || validated.targetLayout().sampleCount() > 1) {
                makeCurrent();
                multipleTargets.begin(descriptor);
                GLRenderPass pass = nextRenderPass();
                pass.begin(null, null, true, width, height, validated);
                pass.multipleTargets(multipleTargets);
                renderPassCount++;
                return pass;
            }
            GLTextureViewHandle attachment = GLResources.requireTextureView(descriptor.colorAttachment(),
                    resourceDomain, GLGraphicsAttachment.this, "Color attachment");
            boolean textureBacked = attachment.textureBacked();
            var explicitDepth = descriptor.depthStencilAttachment();
            GLTextureHandle depthTexture = null;
            if (explicitDepth != null) {
                if (!textureBacked) throw new FdxException("GL explicit depth requires an offscreen color attachment");
                var depthView = GLResources.requireTextureView(explicitDepth.view(), resourceDomain,
                        GLGraphicsAttachment.this, "Depth attachment");
                if (!depthView.textureBacked() || !depthView.textureHandle().usage().renderAttachment()) {
                    throw new FdxException("GL depth attachment requires a render attachment texture");
                }
                depthTexture = depthView.textureHandle();
            }
            if (textureBacked) {
                if (!attachment.textureHandle().usage().renderAttachment()) {
                    throw new FdxException("Color attachment texture was not created for render attachment usage");
                }
            }
            makeCurrent();
            if (textureBacked) {
                gl.bindFramebuffer(framebuffer(attachment.textureHandle(), attachment.mipLevel(), depthTexture));
                gl.viewport(0, 0, attachment.width(), attachment.height());
            } else {
                gl.bindFramebuffer(0);
                gl.viewport(0, 0, width, height);
            }
            gl.framebufferSrgb(attachment.format().isSrgb());
            gl.resetAttachmentWriteMasks();
            if (descriptor.colorLoadOp().isClear()) {
                LoadOp clear = descriptor.colorLoadOp();
                gl.clearColor(clear.red(), clear.green(), clear.blue(), clear.alpha());
                gl.clearColorBuffer();
            }
            if (descriptor.depthEnabled()) {
                gl.enableDepthTest(true);
                gl.depthFuncLessEqual();
                gl.depthMask(true);
                if (explicitDepth != null ? explicitDepth.depthLoadOp().isClear() : descriptor.depthClearEnabled()) {
                    gl.clearDepth(descriptor.depthClearValue());
                    gl.clearDepthBuffer();
                }
            }
            GLRenderPass renderPass = nextRenderPass();
            int passWidth = textureBacked ? attachment.width() : width;
            int passHeight = textureBacked ? attachment.height() : height;
            renderPass.begin(textureBacked ? attachment.textureHandle() : null, depthTexture, textureBacked,
                    width, height, RenderPassCompatibility.of(
                            validated.targetLayout(), passWidth, passHeight));
            renderPassCount++;
            return renderPass;
        }

        void beginFrame() {
            ensurePassesEnded();
            renderPassCount = 0;
            computePassCount = 0;
        }

        void abandonFrame() {
            for (int i = 0; i < computePassCount; i++) computePasses[i].end();
            computePassCount = 0;
            for (int i=0;i<renderPassCount;i++) renderPasses[i].end();
            renderPassCount=0;
        }

        void ensurePassesEnded() {
            for (int i = 0; i < computePassCount; i++) {
                if (!computePasses[i].isEnded()) throw new FdxException("GL compute pass must end before the frame");
            }
            for (int i = 0; i < renderPassCount; i++) {
                if (!renderPasses[i].isEnded()) {
                    throw new FdxException("GL render pass must be ended before ending the frame");
                }
            }
        }

        private void ensurePreviousPassEnded() {
            if (computePassCount > 0 && !computePasses[computePassCount - 1].isEnded()) {
                throw new FdxException("Previous GL compute pass must end before another command scope");
            }
            if (renderPassCount > 0 && !renderPasses[renderPassCount - 1].isEnded()) {
                throw new FdxException("Previous GL render pass must be ended before beginning another pass");
            }
        }

        private GLRenderPass nextRenderPass() {
            if (renderPassCount == renderPasses.length) {
                renderPasses = Arrays.copyOf(renderPasses, renderPasses.length * 2);
            }
            GLRenderPass renderPass = renderPasses[renderPassCount];
            if (renderPass == null) {
                renderPass = new GLRenderPass(providerId, gl, resourceDomain);
                renderPasses[renderPassCount] = renderPass;
            }
            return renderPass;
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
            makeCurrent();
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
