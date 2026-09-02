package io.github.libfdx.backend.iosc;

import io.github.libfdx.math.ClipDepthRange;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsLimits;
import io.github.libfdx.graphics.GraphicsProviderSupport;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderBindingType;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptors;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Sampler;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.VertexStepMode;
import io.github.libfdx.graphics.internal.ShaderRenderBindings;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Provides iOS C native Metal graphics services.
 *
 * @author xpenatan
 */
public final class IosCMetalProvider implements GraphicsAttachmentProvider, GraphicsProviderSupport {
    public static final ProviderId ID = ProviderId.of("metal");
    private static final int MAX_UNIFORM_BYTE_COUNT = 64 * 1024;
    private static final GraphicsCapabilities CAPABILITIES = GraphicsCapabilities.builder()
            .profile(ShaderProfile.PORTABLE_WEBGL2)
            .profile(ShaderProfile.PORTABLE_WEBGPU)
            .profile(ShaderProfile.NATIVE)
            .feature(GraphicsFeature.INDEXED_DRAW)
            .feature(GraphicsFeature.INSTANCED_DRAW)
            .feature(GraphicsFeature.DEPTH_STENCIL_ATTACHMENTS)
            .colorFormats(TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                    TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB)
            .depthStencilFormats(TextureFormat.DEPTH32_FLOAT)
            // Metal clips depth to 0..w.
            .clipDepthRange(ClipDepthRange.ZERO_TO_ONE)
            .sampleCounts(1)
            .limits(GraphicsLimits.builder()
                    .maxBindGroups(2)
                    .maxBindingsPerGroup(32)
                    .maxUniformBuffersPerStage(1)
                    .maxSampledTexturesPerStage(16)
                    .maxSamplersPerStage(16)
                    .maxColorAttachments(1)
                    .maxVertexBuffers(4)
                    .maxVertexAttributes(16)
                    .maxUniformBufferBindingSize(MAX_UNIFORM_BYTE_COUNT)
                    .build())
            .build();

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return ID;
    }

    /**
     * Returns the requirements.
     *
     * @return the requirements
     */
    @Override
    public GraphicsAttachmentRequirements requirements() {
        return GraphicsAttachmentRequirements.noApi();
    }

    /**
     * Returns whether supported is enabled or true.
     *
     * @return true if supported succeeds or is active; false otherwise
     */
    @Override
    public boolean isSupported() {
        return IosCMetal.supported();
    }

    /**
     * Returns the support failure reason.
     *
     * @return the support failure reason
     */
    @Override
    public String supportFailureReason() {
        return isSupported() ? null : "Native iOS Metal is not available or the Metal view was not installed";
    }

    /**
     * Creates a value.
     *
     * @param environment the environment
     * @return the created value
     */
    @Override
    public GraphicsAttachment create(GraphicsEnvironment environment) {
        if (environment == null || environment.display() == null) {
            throw new FdxException("iOS C Metal requires a display environment");
        }
        GraphicsContext sharedContext = environment.sharedContext();
        if (sharedContext != null) {
            if (!ID.equals(sharedContext.providerId())) {
                throw new FdxException("Cannot share a non-Metal graphics context with iOS C Metal");
            }
            throw new FdxException("iOS C Metal does not currently support shared graphics contexts");
        }
        return new IosCMetalGraphicsAttachment(environment.display().framebufferWidth(),
                environment.display().framebufferHeight());
    }

    /**
     * Represents an iOS C Metal graphics attachment.
     *
     * @author xpenatan
     */
    private static final class IosCMetalGraphicsAttachment implements GraphicsAttachment {
        private final long context;
        private final IosCMetalGraphicsDevice device;
        private final IosCMetalFrame frame;
        private int width;
        private int height;
        private boolean frameStarted;
        private boolean disposed;

        IosCMetalGraphicsAttachment(int width, int height) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            context = IosCMetal.create(this.width, this.height);
            device = new IosCMetalGraphicsDevice(this);
            frame = new IosCMetalFrame(this);
        }

        /**
         * Handles a size change.
         *
         * @param framebufferWidth the framebuffer width
         * @param framebufferHeight the framebuffer height
         */
        @Override
        public void resize(int framebufferWidth, int framebufferHeight) {
            ensureNotDisposed();
            width = Math.max(1, framebufferWidth);
            height = Math.max(1, framebufferHeight);
            IosCMetal.resize(context, width, height);
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
            ensureNotDisposed();
            if (frameStarted) {
                throw new FdxException("iOS C Metal frame is already started");
            }
            frame.beginFrame();
            frameStarted = IosCMetal.beginFrame(context);
            return frameStarted;
        }

        /**
         * Ends frame.
         */
        @Override
        public void endFrame() {
            if (!disposed && frameStarted) {
                frame.ensurePassesEnded();
                try {
                    IosCMetal.endFrame(context);
                } finally {
                    frameStarted = false;
                }
            }
        }

        /**
         * Returns the device.
         *
         * @return the device
         */
        @Override
        public GraphicsDevice device() {
            ensureNotDisposed();
            return device;
        }

        /**
         * Returns the surface format.
         *
         * @return the surface format
         */
        @Override
        public TextureFormat surfaceFormat() {
            ensureNotDisposed();
            return TextureFormat.BGRA8_UNORM;
        }

        /**
         * Returns the current frame.
         *
         * @return the current frame
         */
        @Override
        public GraphicsFrame currentFrame() {
            ensureFrameStarted("access the current frame");
            return frame;
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
            ensureFrameStarted("clear");
            IosCMetal.clear(context, red, green, blue, alpha);
        }

        /**
         * Returns the identifier of the provider backing this object.
         *
         * @return the provider ID
         */
        @Override
        public ProviderId providerId() {
            return ID;
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
            try {
                IosCMetal.destroy(context);
            } finally {
                frameStarted = false;
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

        private void ensureNotDisposed() {
            if (disposed) {
                throw new FdxException("iOS C Metal graphics attachment has been disposed");
            }
        }

        private void ensureFrameStarted(String operation) {
            ensureNotDisposed();
            if (!frameStarted) {
                throw new FdxException("Cannot " + operation + " outside an active iOS C Metal frame");
            }
        }
    }

    /**
     * Represents an iOS C Metal graphics frame.
     *
     * @author xpenatan
     */
    private static final class IosCMetalFrame implements GraphicsFrame {
        private final IosCMetalGraphicsAttachment attachment;
        private final IosCMetalCommandEncoder commandEncoder;
        private final IosCMetalFrameBuffer frameBuffer;
        private final IosCMetalTextureView colorAttachment;

        IosCMetalFrame(IosCMetalGraphicsAttachment attachment) {
            this.attachment = attachment;
            colorAttachment = new IosCMetalTextureView(attachment, TextureFormat.BGRA8_UNORM);
            commandEncoder = new IosCMetalCommandEncoder(attachment);
            frameBuffer = new IosCMetalFrameBuffer(attachment, colorAttachment);
        }

        void beginFrame() {
            commandEncoder.beginFrame();
        }

        void ensurePassesEnded() {
            commandEncoder.ensurePassesEnded();
        }

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
            return attachment.width;
        }

        /**
         * Returns the height.
         *
         * @return the height
         */
        @Override
        public int height() {
            return attachment.height;
        }

        /**
         * Returns the identifier of the provider backing this object.
         *
         * @return the provider ID
         */
        @Override
        public ProviderId providerId() {
            return ID;
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
     * Represents an iOS C Metal framebuffer.
     *
     * @author xpenatan
     */
    private static final class IosCMetalFrameBuffer implements FrameBuffer {
        private final IosCMetalGraphicsAttachment attachment;
        private final TextureView colorAttachment;

        IosCMetalFrameBuffer(IosCMetalGraphicsAttachment attachment, TextureView colorAttachment) {
            this.attachment = attachment;
            this.colorAttachment = colorAttachment;
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
         * Returns the format.
         *
         * @return the format
         */
        @Override
        public TextureFormat format() {
            return TextureFormat.BGRA8_UNORM;
        }

        /**
         * Returns the width.
         *
         * @return the width
         */
        @Override
        public int width() {
            return attachment.width;
        }

        /**
         * Returns the height.
         *
         * @return the height
         */
        @Override
        public int height() {
            return attachment.height;
        }

        /**
         * Captures the current drawable as tightly packed RGBA8 pixels.
         *
         * @return the captured pixels
         */
        @Override
        public ByteBuffer readPixelsRgba8() {
            attachment.ensureFrameStarted("read pixels");
            int byteCount = width() * height() * 4;
            ByteBuffer pixels = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
            try {
                IosCMetal.readPixelsRgba8(attachment.context, pixels, byteCount);
            } finally {
                attachment.frameStarted = false;
            }
            pixels.position(0);
            pixels.limit(byteCount);
            return pixels;
        }

        /**
         * Returns the identifier of the provider backing this object.
         *
         * @return the provider ID
         */
        @Override
        public ProviderId providerId() {
            return ID;
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
     * Represents an iOS C Metal graphics device.
     *
     * @author xpenatan
     */
    private static final class IosCMetalGraphicsDevice implements GraphicsDevice {
        private final IosCMetalGraphicsAttachment attachment;

        IosCMetalGraphicsDevice(IosCMetalGraphicsAttachment attachment) {
            this.attachment = attachment;
        }

        /**
         * Creates a buffer.
         *
         * @param descriptor the descriptor
         * @return the created value
         */
        @Override
        public Buffer createBuffer(BufferDescriptor descriptor) {
            attachment.ensureNotDisposed();
            if (descriptor == null) {
                throw new FdxException("BufferDescriptor cannot be null");
            }
            return new IosCMetalBufferHandle(attachment,
                    IosCMetal.createBuffer(attachment.context, descriptor.size(),
                    toNativeBufferUsage(descriptor.usage())), descriptor.size(), descriptor.usage());
        }

        /**
         * Runs the write buffer step.
         *
         * @param buffer the buffer
         * @param data the data
         */
        @Override
        public void writeBuffer(Buffer buffer, ByteBuffer data) {
            attachment.ensureNotDisposed();
            if (data == null) {
                throw new FdxException("Buffer data cannot be null");
            }
            IosCMetalBufferHandle metalBuffer = IosCMetalResources.requireBuffer(buffer, attachment, "Buffer");
            if (data.remaining() > metalBuffer.size()) {
                throw new FdxException("Buffer data is larger than the destination buffer");
            }
            ByteBuffer source = data.position() == 0 ? data : data.slice();
            IosCMetal.writeBuffer(metalBuffer.handle(), source, source.remaining());
        }

        /**
         * Creates a texture.
         *
         * @param descriptor the descriptor
         * @return the created value
         */
        @Override
        public Texture createTexture(TextureDescriptor descriptor) {
            attachment.ensureNotDisposed();
            if (descriptor == null) {
                throw new FdxException("TextureDescriptor cannot be null");
            }
            descriptor.validate(capabilities());
            if (descriptor.format() != TextureFormat.RGBA8_UNORM) {
                throw new FdxException("iOS C Metal currently supports RGBA8_UNORM sampled textures only");
            }
            if (descriptor.usage() != TextureUsage.SAMPLED) {
                throw new FdxException("iOS C Metal currently supports sampled textures only");
            }
            return new IosCMetalTextureHandle(attachment,
                    IosCMetal.createTexture(attachment.context, descriptor.width(),
                    descriptor.height(), toNativeWrap(descriptor.wrapS()), toNativeWrap(descriptor.wrapT()),
                    toNativeFilter(descriptor.filter())),
                    descriptor.width(), descriptor.height(), descriptor.format(), descriptor.usage());
        }

        /**
         * Runs the write texture step.
         *
         * @param texture the texture
         * @param data the data
         */
        @Override
        public void writeTexture(Texture texture, ByteBuffer data) {
            attachment.ensureNotDisposed();
            if (data == null) {
                throw new FdxException("Texture data cannot be null");
            }
            IosCMetalTextureHandle metalTexture = IosCMetalResources.requireTexture(texture, attachment, "Texture");
            int byteCount = metalTexture.width() * metalTexture.height() * 4;
            if (data.remaining() != byteCount) {
                throw new FdxException("iOS C Metal texture upload expects " + byteCount + " RGBA bytes");
            }
            ByteBuffer source = data.position() == 0 ? data : data.slice();
            IosCMetal.writeTexture(metalTexture.handle(), source, source.remaining());
        }

        /**
         * Creates a shader module.
         *
         * @param descriptor the descriptor
         * @return the created value
         */
        @Override
        public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
            attachment.ensureNotDisposed();
            if (descriptor == null) {
                throw new FdxException("ShaderModuleDescriptor cannot be null");
            }
            descriptor = ShaderModuleDescriptors.requireTarget(descriptor, ShaderTarget.METAL_MSL, "iOS C Metal");
            if (descriptor.targetArtifact() != null) {
                shaderTargetSupport().require(descriptor.targetArtifact());
            }
            if (!descriptor.hasSource(ShaderLanguage.MSL)) {
                throw new FdxException("iOS C Metal requires MSL shader modules");
            }
            return new IosCMetalShaderModuleHandle(attachment,
                    IosCMetal.createShaderModule(attachment.context, descriptor.mslSource()),
                    descriptor.reflection());
        }

        /**
         * Creates a render pipeline.
         *
         * @param descriptor the descriptor
         * @return the created value
         */
        @Override
        public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
            attachment.ensureNotDisposed();
            if (descriptor == null) {
                throw new FdxException("RenderPipelineDescriptor cannot be null");
            }
            if (descriptor.colorFormat() != attachment.surfaceFormat()) {
                throw new FdxException("iOS C Metal render pipeline color format must match the surface format");
            }
            IosCMetalShaderModuleHandle shaderModule = IosCMetalResources.requireShaderModule(
                    descriptor.shaderModule(), attachment, "Shader module");
            descriptor.validate(capabilities());
            if (descriptor.renderTargetLayout().colorAttachmentCount() != 1) {
                throw new FdxException("iOS C Metal currently requires exactly one color attachment");
            }
            ShaderRenderBindings resourceBindings = ShaderRenderBindings.from(descriptor);
            boolean uniformBufferEnabled = resourceBindings.hasUniformBuffer();
            VertexLayout[] vertexLayouts = descriptor.vertexLayouts();
            return new IosCMetalRenderPipelineHandle(attachment,
                    IosCMetal.createRenderPipeline(attachment.context,
                    shaderModule.handle(), toNativeTopology(descriptor.primitiveTopology()),
                    vertexStrides(vertexLayouts), vertexStepModes(vertexLayouts), attributeBindings(vertexLayouts),
                    attributeLocations(vertexLayouts), attributeFormats(vertexLayouts),
                    attributeOffsets(vertexLayouts), descriptor.sampledTextureCount(), uniformBufferEnabled,
                    descriptor.depthTestEnabled(), descriptor.depthWriteEnabled()),
                    descriptor.primitiveTopology(), descriptor.sampledTextureCount(), resourceBindings,
                    textureBindings(descriptor), samplerBindings(descriptor),
                    descriptor.renderTargetLayout());
        }

        /**
         * Returns the identifier of the provider backing this object.
         *
         * @return the provider ID
         */
        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        public GraphicsCapabilities capabilities() {
            return CAPABILITIES;
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

    private static final class IosCMetalResources {
        private IosCMetalResources() {
        }

        static IosCMetalBufferHandle requireBuffer(Buffer value, IosCMetalGraphicsAttachment attachment,
                String name) {
            if (value == null) {
                throw new FdxException(name + " cannot be null");
            }
            if (!(value instanceof IosCMetalBufferHandle handle)) {
                throw new FdxException(name + " belongs to another graphics provider");
            }
            requireOwner(handle.attachment, attachment, name);
            if (handle.isDisposed()) {
                throw new FdxException(name + " has been disposed");
            }
            return handle;
        }

        static IosCMetalTextureHandle requireTexture(Texture value, IosCMetalGraphicsAttachment attachment,
                String name) {
            if (value == null) {
                throw new FdxException(name + " cannot be null");
            }
            if (!(value instanceof IosCMetalTextureHandle handle)) {
                throw new FdxException(name + " belongs to another graphics provider");
            }
            requireOwner(handle.attachment, attachment, name);
            if (handle.isDisposed()) {
                throw new FdxException(name + " has been disposed");
            }
            return handle;
        }

        static IosCMetalShaderModuleHandle requireShaderModule(ShaderModule value,
                IosCMetalGraphicsAttachment attachment, String name) {
            if (value == null) {
                throw new FdxException(name + " cannot be null");
            }
            if (!(value instanceof IosCMetalShaderModuleHandle handle)) {
                throw new FdxException(name + " belongs to another graphics provider");
            }
            requireOwner(handle.attachment, attachment, name);
            if (handle.isDisposed()) {
                throw new FdxException(name + " has been disposed");
            }
            return handle;
        }

        static IosCMetalRenderPipelineHandle requirePipeline(RenderPipeline value,
                IosCMetalGraphicsAttachment attachment, String name) {
            if (value == null) {
                throw new FdxException(name + " cannot be null");
            }
            if (!(value instanceof IosCMetalRenderPipelineHandle handle)) {
                throw new FdxException(name + " belongs to another graphics provider");
            }
            requireOwner(handle.attachment, attachment, name);
            if (handle.isDisposed()) {
                throw new FdxException(name + " has been disposed");
            }
            return handle;
        }

        static IosCMetalTextureView requireTextureView(TextureView value,
                IosCMetalGraphicsAttachment attachment, String name) {
            if (value == null) {
                throw new FdxException(name + " cannot be null");
            }
            if (!(value instanceof IosCMetalTextureView handle)) {
                throw new FdxException(name + " belongs to another graphics provider");
            }
            requireOwner(handle.attachment, attachment, name);
            attachment.ensureFrameStarted("use " + name.toLowerCase());
            return handle;
        }

        private static void requireOwner(IosCMetalGraphicsAttachment actual,
                IosCMetalGraphicsAttachment expected, String name) {
            if (actual != expected) {
                throw new FdxException(name + " belongs to another iOS C Metal context");
            }
            expected.ensureNotDisposed();
        }
    }

    /**
     * Represents an iOS C Metal command encoder.
     *
     * @author xpenatan
     */
    private static final class IosCMetalCommandEncoder implements CommandEncoder {
        private final IosCMetalGraphicsAttachment attachment;
        private IosCMetalRenderPass[] renderPasses = new IosCMetalRenderPass[4];
        private int renderPassCount;

        IosCMetalCommandEncoder(IosCMetalGraphicsAttachment attachment) {
            this.attachment = attachment;
        }

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
            attachment.ensureFrameStarted("begin a render pass");
            ensurePreviousPassEnded();
            RenderPassCompatibility compatibility = descriptor.validate(
                    attachment.device.capabilities());
            IosCMetalResources.requireTextureView(descriptor.colorAttachment(), attachment, "Color attachment");
            LoadOp loadOp = descriptor.colorLoadOp();
            StoreOp storeOp = descriptor.colorStoreOp();
            IosCMetal.beginRenderPass(attachment.context, loadOp.isClear(), loadOp.red(), loadOp.green(),
                    loadOp.blue(), loadOp.alpha(), storeOp.isStore(), descriptor.depthEnabled(),
                    descriptor.depthClearEnabled(), descriptor.depthClearValue());
            IosCMetalRenderPass renderPass = nextRenderPass();
            renderPass.begin(RenderPassCompatibility.of(compatibility.targetLayout(),
                    attachment.width, attachment.height));
            renderPassCount++;
            return renderPass;
        }

        void beginFrame() {
            ensurePassesEnded();
            renderPassCount = 0;
        }

        void ensurePassesEnded() {
            for (int i = 0; i < renderPassCount; i++) {
                if (!renderPasses[i].isEnded()) {
                    throw new FdxException("iOS C Metal render pass must be ended before ending the frame");
                }
            }
        }

        private void ensurePreviousPassEnded() {
            if (renderPassCount > 0 && !renderPasses[renderPassCount - 1].isEnded()) {
                throw new FdxException(
                        "Previous iOS C Metal render pass must be ended before beginning another pass");
            }
        }

        private IosCMetalRenderPass nextRenderPass() {
            if (renderPassCount == renderPasses.length) {
                IosCMetalRenderPass[] grown = new IosCMetalRenderPass[renderPasses.length * 2];
                System.arraycopy(renderPasses, 0, grown, 0, renderPasses.length);
                renderPasses = grown;
            }
            IosCMetalRenderPass renderPass = renderPasses[renderPassCount];
            if (renderPass == null) {
                renderPass = new IosCMetalRenderPass(attachment);
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
            return ID;
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
     * Represents an iOS C Metal render pass.
     *
     * @author xpenatan
     */
    private static final class IosCMetalRenderPass implements RenderPass {
        private final IosCMetalGraphicsAttachment attachment;
        private final ByteBuffer uniformBytes = ByteBuffer.allocateDirect(MAX_UNIFORM_BYTE_COUNT)
                .order(ByteOrder.nativeOrder());
        private ShaderParameterBlock compatibilityUniformBlock;
        private IosCMetalRenderPipelineHandle pipeline;
        private RenderPassCompatibility compatibility;
        private IosCMetalBufferHandle indexBuffer;
        private IosCMetalBufferHandle[] vertexBuffers = new IosCMetalBufferHandle[0];
        private IosCMetalTextureHandle[] textures = new IosCMetalTextureHandle[0];
        private boolean uniformDataDirty;
        private boolean hasUniformData;
        private boolean ended = true;

        IosCMetalRenderPass(IosCMetalGraphicsAttachment attachment) {
            this.attachment = attachment;
        }

        void begin(RenderPassCompatibility compatibility) {
            if (!ended) {
                throw new FdxException("Cannot reuse an active iOS C Metal render pass");
            }
            pipeline = null;
            this.compatibility = compatibility;
            indexBuffer = null;
            for (int i = 0; i < vertexBuffers.length; i++) {
                vertexBuffers[i] = null;
            }
            for (int i = 0; i < textures.length; i++) {
                textures[i] = null;
            }
            uniformDataDirty = false;
            hasUniformData = false;
            compatibilityUniformBlock = null;
            ended = false;
        }

        boolean isEnded() {
            return ended;
        }

        @Override
        public RenderPassCompatibility compatibility() {
            ensureOpen();
            return compatibility;
        }

        /**
         * Sets the pipeline.
         *
         * @param pipeline the pipeline
         */
        @Override
        public void setPipeline(RenderPipeline pipeline) {
            ensureOpen();
            this.pipeline = IosCMetalResources.requirePipeline(pipeline, attachment, "Render pipeline");
            if (!compatibility.isCompatible(this.pipeline.targetLayout())) {
                this.pipeline = null;
                throw new FdxException(
                        "iOS C Metal render pipeline target layout is incompatible with the active pass");
            }
            prepareTextureSlots(this.pipeline.sampledTextureCount());
            uniformDataDirty = true;
            hasUniformData = false;
            compatibilityUniformBlock = null;
            IosCMetal.setPipeline(attachment.context, this.pipeline.handle());
        }

        /**
         * Sets the vertex buffer.
         *
         * @param buffer the buffer
         */
        @Override
        public void setVertexBuffer(Buffer buffer) {
            setVertexBuffer(0, buffer);
        }

        /**
         * Sets the vertex buffer.
         *
         * @param slot the slot
         * @param buffer the buffer
         */
        @Override
        public void setVertexBuffer(int slot, Buffer buffer) {
            ensureOpen();
            if (slot < 0) {
                throw new FdxException("Vertex buffer slot cannot be negative");
            }
            IosCMetalBufferHandle metalBuffer = IosCMetalResources.requireBuffer(buffer, attachment,
                    "Vertex buffer");
            if (metalBuffer.usage() != BufferUsage.VERTEX) {
                throw new FdxException("RenderPass.setVertexBuffer requires a vertex buffer");
            }
            rememberVertexBuffer(slot, metalBuffer);
            IosCMetal.setVertexBuffer(attachment.context, slot, metalBuffer.handle());
        }

        /**
         * Sets the index buffer.
         *
         * @param buffer the buffer
         */
        @Override
        public void setIndexBuffer(Buffer buffer) {
            ensureOpen();
            indexBuffer = IosCMetalResources.requireBuffer(buffer, attachment, "Index buffer");
            if (indexBuffer.usage() != BufferUsage.INDEX) {
                throw new FdxException("RenderPass.setIndexBuffer requires an index buffer");
            }
            IosCMetal.setIndexBuffer(attachment.context, indexBuffer.handle());
        }

        /**
         * Sets the scissor rectangle.
         *
         * @param x the lower-left x coordinate in framebuffer pixels
         * @param y the lower-left y coordinate in framebuffer pixels
         * @param width the width in pixels
         * @param height the height in pixels
         */
        @Override
        public void setScissor(int x, int y, int width, int height) {
            ensureOpen();
            if (width <= 0 || height <= 0) {
                throw new FdxException("Scissor size must be greater than zero");
            }
            IosCMetal.setScissor(attachment.context, x, attachment.height - y - height, width, height);
        }

        /**
         * Sets the viewport.
         *
         * @param x the lower-left x coordinate in framebuffer pixels
         * @param y the lower-left y coordinate in framebuffer pixels
         * @param width the width in pixels
         * @param height the height in pixels
         */
        @Override
        public void setViewport(int x, int y, int width, int height) {
            ensureOpen();
            if (width <= 0 || height <= 0) {
                throw new FdxException("Viewport size must be greater than zero");
            }
            IosCMetal.setViewport(attachment.context, x, attachment.height - y - height, width, height);
        }

        /**
         * Sets the texture.
         *
         * @param slot the slot
         * @param texture the texture
         */
        @Override
        public void setTexture(int slot, Texture texture) {
            ensureOpen();
            if (pipeline == null) {
                throw new FdxException("Render pipeline must be set before binding a texture");
            }
            if (slot < 0 || slot >= pipeline.sampledTextureCount()) {
                throw new FdxException("Texture slot is not declared by the active iOS C Metal pipeline: " + slot);
            }
            IosCMetalTextureHandle metalTexture = IosCMetalResources.requireTexture(texture, attachment, "Texture");
            textures[slot] = metalTexture;
            IosCMetal.setTexture(attachment.context, pipeline.textureBinding(slot), pipeline.samplerBinding(slot),
                    metalTexture.handle());
        }

        @Override
        public void setTextureBinding(int group, int binding, Texture texture) {
            requirePipeline();
            int slot = pipeline.resourceBindings().textureSlot(group, binding);
            if (slot < 0) {
                throw new FdxException("Texture binding is not declared by the active iOS C Metal pipeline: "
                        + group + ':' + binding);
            }
            setTexture(slot, texture);
        }

        @Override
        public void setTextureSamplerBinding(int group, int binding, Texture texture) {
            requirePipeline();
            int slot = pipeline.resourceBindings().samplerSlot(group, binding);
            if (slot < 0) {
                throw new FdxException("Sampler binding is not declared by the active iOS C Metal pipeline: "
                        + group + ':' + binding);
            }
            setTexture(slot, texture);
        }

        @Override
        public void setSamplerBinding(int group, int binding, Sampler sampler) {
            throw new FdxException("Separate sampler objects are not supported by this iOS C Metal path");
        }

        @Override
        public void setParameterBlock(int group, int binding, ShaderParameterBlock block) {
            requirePipeline();
            pipeline.resourceBindings().requireParameterBlock(group, binding, block);
            block.copyTo(uniformBytes, 0);
            markUniformDirty();
        }

        /**
         * Sets the uniform1i.
         *
         * @param name the name
         * @param value the value
         */
        @Override
        public void setUniform1i(String name, int value) {
            throw namedUniformUnsupported(name);
        }

        @Override
        public void setUniform1i(ShaderParameterHandle parameter, int value) {
            ShaderParameterBlock block = compatibilityUniformBlock();
            switch (parameter.valueType().scalarType()) {
                case F32 -> block.setFloat(parameter, value);
                case I32 -> block.setInt(parameter, value);
                case U32 -> block.setUnsignedInt(parameter, value);
                case BOOL -> block.setBoolean(parameter, value != 0);
                default -> throw new FdxException("Uniform handle is not integer-compatible: "
                        + parameter.path());
            }
            snapshotCompatibilityBlock();
        }

        /**
         * Sets the uniform1f.
         *
         * @param name the name
         * @param value the value
         */
        @Override
        public void setUniform1f(String name, float value) {
            throw namedUniformUnsupported(name);
        }

        @Override
        public void setUniform1f(ShaderParameterHandle parameter, float value) {
            compatibilityUniformBlock().setFloat(parameter, value);
            snapshotCompatibilityBlock();
        }

        /**
         * Sets the uniform3f.
         *
         * @param name the name
         * @param x the x coordinate
         * @param y the y coordinate
         * @param z the z coordinate
         */
        @Override
        public void setUniform3f(String name, float x, float y, float z) {
            throw namedUniformUnsupported(name);
        }

        @Override
        public void setUniform3f(ShaderParameterHandle parameter, float x, float y, float z) {
            ShaderParameterBlock block = compatibilityUniformBlock();
            block.setFloat(parameter.component(0), x);
            block.setFloat(parameter.component(1), y);
            block.setFloat(parameter.component(2), z);
            snapshotCompatibilityBlock();
        }

        /**
         * Sets the uniform4f.
         *
         * @param name the name
         * @param x the x coordinate
         * @param y the y coordinate
         * @param z the z coordinate
         * @param w the w
         */
        @Override
        public void setUniform4f(String name, float x, float y, float z, float w) {
            throw namedUniformUnsupported(name);
        }

        @Override
        public void setUniform4f(ShaderParameterHandle parameter, float x, float y, float z, float w) {
            compatibilityUniformBlock().setFloat4(parameter, x, y, z, w);
            snapshotCompatibilityBlock();
        }

        /**
         * Sets the uniform matrix4.
         *
         * @param name the name
         * @param values the values
         */
        @Override
        public void setUniformMatrix4(String name, float[] values) {
            throw namedUniformUnsupported(name);
        }

        @Override
        public void setUniformMatrix4(ShaderParameterHandle parameter, float[] values) {
            compatibilityUniformBlock().setFloatMatrix(parameter, values, 0);
            snapshotCompatibilityBlock();
        }

        /**
         * Draws the current content.
         *
         * @param vertexCount the vertex count
         * @param instanceCount the instance count
         * @param firstVertex the first vertex
         * @param firstInstance the first instance
         */
        @Override
        public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
            ensureReadyToDraw(false);
            bindUniforms();
            IosCMetal.draw(attachment.context, vertexCount, instanceCount, firstVertex, firstInstance);
        }

        /**
         * Draws indexed.
         *
         * @param indexCount the index count
         * @param instanceCount the instance count
         * @param firstIndex the first index
         * @param baseVertex the base vertex
         * @param firstInstance the first instance
         */
        @Override
        public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance) {
            ensureReadyToDraw(true);
            if (indexBuffer == null) {
                throw new FdxException("Index buffer must be set before indexed draws");
            }
            bindUniforms();
            IosCMetal.drawIndexed(attachment.context, indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
        }

        /**
         * Ends the operation.
         */
        @Override
        public void end() {
            if (ended) {
                return;
            }
            attachment.ensureFrameStarted("end a render pass");
            IosCMetal.endRenderPass(attachment.context);
            ended = true;
            pipeline = null;
            compatibility = null;
            indexBuffer = null;
            for (int i = 0; i < vertexBuffers.length; i++) {
                vertexBuffers[i] = null;
            }
            for (int i = 0; i < textures.length; i++) {
                textures[i] = null;
            }
        }

        /**
         * Returns the identifier of the provider backing this object.
         *
         * @return the provider ID
         */
        @Override
        public ProviderId providerId() {
            return ID;
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

        private void ensureReadyToDraw(boolean indexed) {
            ensureOpen();
            if (pipeline == null) {
                throw new FdxException("Render pipeline must be set before draw");
            }
            IosCMetalResources.requirePipeline(pipeline, attachment, "Render pipeline");
            for (int i = 0; i < vertexBuffers.length; i++) {
                if (vertexBuffers[i] != null) {
                    IosCMetalResources.requireBuffer(vertexBuffers[i], attachment, "Vertex buffer at slot " + i);
                }
            }
            if (indexed) {
                IosCMetalResources.requireBuffer(indexBuffer, attachment, "Index buffer");
            }
            for (int i = 0; i < pipeline.sampledTextureCount(); i++) {
                if (textures[i] == null) {
                    throw new FdxException("Texture slot " + i
                            + " must be set before drawing with the iOS C Metal pipeline");
                }
                IosCMetalResources.requireTexture(textures[i], attachment, "Texture at slot " + i);
            }
        }

        private void prepareTextureSlots(int sampledTextureCount) {
            if (textures.length < sampledTextureCount) {
                textures = new IosCMetalTextureHandle[sampledTextureCount];
            }
            for (int i = 0; i < textures.length; i++) {
                textures[i] = null;
            }
        }

        private void rememberVertexBuffer(int slot, IosCMetalBufferHandle buffer) {
            if (slot >= vertexBuffers.length) {
                int nextLength = Math.max(slot + 1, Math.max(1, vertexBuffers.length * 2));
                IosCMetalBufferHandle[] grown = new IosCMetalBufferHandle[nextLength];
                System.arraycopy(vertexBuffers, 0, grown, 0, vertexBuffers.length);
                vertexBuffers = grown;
            }
            vertexBuffers[slot] = buffer;
        }

        private void bindUniforms() {
            if (!pipeline.uniformBufferEnabled()) {
                return;
            }
            if (!hasUniformData) {
                throw new FdxException("iOS C Metal uniform parameter block must be bound before drawing");
            }
            if (uniformDataDirty) {
                int byteCount = pipeline.resourceBindings().uniformByteCount();
                uniformBytes.position(0);
                uniformBytes.limit(byteCount);
                IosCMetal.setUniformBuffer(attachment.context, uniformBytes, byteCount);
                uniformDataDirty = false;
            }
        }

        private void markUniformDirty() {
            hasUniformData = true;
            uniformDataDirty = true;
        }

        private void requirePipeline() {
            ensureOpen();
            if (pipeline == null) {
                throw new FdxException("Render pipeline must be set before binding resources");
            }
            IosCMetalResources.requirePipeline(pipeline, attachment, "Render pipeline");
        }

        private ShaderParameterBlock compatibilityUniformBlock() {
            requirePipeline();
            if (!pipeline.resourceBindings().hasUniformBuffer()) {
                throw new FdxException("Active iOS C Metal pipeline has no reflected uniform buffer");
            }
            if (compatibilityUniformBlock == null) {
                compatibilityUniformBlock = ShaderParameterBlock.allocate(
                        pipeline.resourceBindings().uniformBuffer().bufferLayout());
            }
            return compatibilityUniformBlock;
        }

        private void snapshotCompatibilityBlock() {
            setParameterBlock(pipeline.resourceBindings().uniformGroup(),
                    pipeline.resourceBindings().uniformBinding(), compatibilityUniformBlock);
        }

        private FdxException namedUniformUnsupported(String name) {
            ensureOpen();
            return new FdxException("iOS C Metal named uniform '" + name
                    + "' is not portable; bind a reflected ShaderParameterBlock");
        }

        private void ensureOpen() {
            attachment.ensureFrameStarted("use a render pass");
            if (ended) {
                throw new FdxException("Render pass has already ended");
            }
        }
    }

    private static final class IosCMetalBufferHandle implements Buffer {
        private final IosCMetalGraphicsAttachment attachment;
        private final long handle;
        private final int size;
        private final BufferUsage usage;
        private boolean disposed;

        IosCMetalBufferHandle(IosCMetalGraphicsAttachment attachment, long handle, int size, BufferUsage usage) {
            this.attachment = attachment;
            this.handle = handle;
            this.size = size;
            this.usage = usage != null ? usage : BufferUsage.VERTEX;
        }

        long handle() {
            return handle;
        }

        /**
         * Returns the size.
         *
         * @return the size
         */
        @Override
        public int size() {
            return size;
        }

        /**
         * Returns the usage.
         *
         * @return the usage
         */
        @Override
        public BufferUsage usage() {
            return usage;
        }

        /**
         * Returns the identifier of the provider backing this object.
         *
         * @return the provider ID
         */
        @Override
        public ProviderId providerId() {
            return ID;
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
            if (!attachment.isDisposed()) {
                IosCMetal.destroyBuffer(handle);
            }
            disposed = true;
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
    }

    private static final class IosCMetalTextureHandle implements Texture {
        private final IosCMetalGraphicsAttachment attachment;
        private final long handle;
        private final int width;
        private final int height;
        private final TextureFormat format;
        private final TextureUsage usage;
        private boolean disposed;

        IosCMetalTextureHandle(IosCMetalGraphicsAttachment attachment, long handle, int width, int height,
                TextureFormat format, TextureUsage usage) {
            this.attachment = attachment;
            this.handle = handle;
            this.width = width;
            this.height = height;
            this.format = format != null ? format : TextureFormat.RGBA8_UNORM;
            this.usage = usage != null ? usage : TextureUsage.SAMPLED;
        }

        long handle() {
            return handle;
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
         * Returns the format.
         *
         * @return the format
         */
        @Override
        public TextureFormat format() {
            return format;
        }

        /**
         * Returns the usage.
         *
         * @return the usage
         */
        @Override
        public TextureUsage usage() {
            return usage;
        }

        /**
         * Returns the identifier of the provider backing this object.
         *
         * @return the provider ID
         */
        @Override
        public ProviderId providerId() {
            return ID;
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
            if (!attachment.isDisposed()) {
                IosCMetal.destroyTexture(handle);
            }
            disposed = true;
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
    }

    private static final class IosCMetalTextureView implements TextureView {
        private final IosCMetalGraphicsAttachment attachment;
        private final TextureFormat format;

        IosCMetalTextureView(IosCMetalGraphicsAttachment attachment, TextureFormat format) {
            this.attachment = attachment;
            this.format = format != null ? format : TextureFormat.BGRA8_UNORM;
        }

        @Override
        public int width() {
            return attachment.width;
        }

        @Override
        public int height() {
            return attachment.height;
        }

        /**
         * Returns the format.
         *
         * @return the format
         */
        @Override
        public TextureFormat format() {
            return format;
        }

        /**
         * Returns the identifier of the provider backing this object.
         *
         * @return the provider ID
         */
        @Override
        public ProviderId providerId() {
            return ID;
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

    private static final class IosCMetalShaderModuleHandle implements ShaderModule {
        private final IosCMetalGraphicsAttachment attachment;
        private final long handle;
        private final ShaderReflection reflection;
        private boolean disposed;

        IosCMetalShaderModuleHandle(IosCMetalGraphicsAttachment attachment, long handle,
                ShaderReflection reflection) {
            this.attachment = attachment;
            this.handle = handle;
            this.reflection = reflection != null ? reflection : ShaderReflection.empty();
        }

        long handle() {
            return handle;
        }

        /**
         * Returns the language.
         *
         * @return the language
         */
        @Override
        public ShaderLanguage language() {
            return ShaderLanguage.MSL;
        }

        @Override
        public ShaderReflection reflection() {
            return reflection;
        }

        /**
         * Returns the identifier of the provider backing this object.
         *
         * @return the provider ID
         */
        @Override
        public ProviderId providerId() {
            return ID;
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
            if (!attachment.isDisposed()) {
                IosCMetal.destroyShaderModule(handle);
            }
            disposed = true;
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
    }

    private static final class IosCMetalRenderPipelineHandle implements RenderPipeline {
        private final IosCMetalGraphicsAttachment attachment;
        private final long handle;
        private final PrimitiveTopology primitiveTopology;
        private final int sampledTextureCount;
        private final ShaderRenderBindings resourceBindings;
        private final int[] textureBindings;
        private final int[] samplerBindings;
        private final RenderTargetLayout targetLayout;
        private boolean disposed;

        IosCMetalRenderPipelineHandle(IosCMetalGraphicsAttachment attachment, long handle,
                PrimitiveTopology primitiveTopology, int sampledTextureCount, ShaderRenderBindings resourceBindings,
                int[] textureBindings, int[] samplerBindings, RenderTargetLayout targetLayout) {
            this.attachment = attachment;
            this.handle = handle;
            this.primitiveTopology = primitiveTopology != null ? primitiveTopology : PrimitiveTopology.TRIANGLE_LIST;
            this.sampledTextureCount = sampledTextureCount;
            this.resourceBindings = resourceBindings;
            this.textureBindings = textureBindings != null ? textureBindings.clone() : sequentialBindings(
                    sampledTextureCount);
            this.samplerBindings = samplerBindings != null ? samplerBindings.clone() : sequentialBindings(
                    sampledTextureCount);
            this.targetLayout = targetLayout;
        }

        long handle() {
            return handle;
        }

        int sampledTextureCount() {
            return sampledTextureCount;
        }

        boolean uniformBufferEnabled() {
            return resourceBindings.hasUniformBuffer();
        }

        ShaderRenderBindings resourceBindings() {
            return resourceBindings;
        }

        int textureBinding(int slot) {
            return slot >= 0 && slot < textureBindings.length ? textureBindings[slot] : slot;
        }

        int samplerBinding(int slot) {
            return slot >= 0 && slot < samplerBindings.length ? samplerBindings[slot] : slot;
        }

        @Override
        public RenderTargetLayout targetLayout() {
            return targetLayout;
        }

        /**
         * Returns the identifier of the provider backing this object.
         *
         * @return the provider ID
         */
        @Override
        public ProviderId providerId() {
            return ID;
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
            if (!attachment.isDisposed()) {
                IosCMetal.destroyRenderPipeline(handle);
            }
            disposed = true;
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
    }

    private static int toNativeBufferUsage(BufferUsage usage) {
        return usage == BufferUsage.INDEX ? 1 : 0;
    }

    private static int toNativeTopology(PrimitiveTopology topology) {
        if (topology == PrimitiveTopology.LINE_LIST) {
            return 2;
        }
        if (topology == PrimitiveTopology.TRIANGLE_STRIP) {
            return 1;
        }
        return 0;
    }

    private static int[] textureBindings(RenderPipelineDescriptor descriptor) {
        return resourceBindings(descriptor, ShaderBindingType.TEXTURE);
    }

    private static int[] samplerBindings(RenderPipelineDescriptor descriptor) {
        return resourceBindings(descriptor, ShaderBindingType.SAMPLER);
    }

    private static int[] resourceBindings(RenderPipelineDescriptor descriptor, ShaderBindingType type) {
        int sampledTextureCount = descriptor.sampledTextureCount();
        if (sampledTextureCount <= 0) {
            return new int[0];
        }
        ShaderBinding[] bindings = descriptor.shaderReflection().bindings();
        int[] values = new int[sampledTextureCount];
        int out = 0;
        for (int i = 0; i < bindings.length && out < sampledTextureCount; i++) {
            ShaderBinding binding = bindings[i];
            if (binding.group() == 0 && binding.type() == type) {
                values[out++] = binding.binding();
            }
        }
        if (out == sampledTextureCount) {
            return values;
        }
        return sequentialBindings(sampledTextureCount);
    }

    private static int[] sequentialBindings(int count) {
        int[] values = new int[Math.max(0, count)];
        for (int i = 0; i < values.length; i++) {
            values[i] = i;
        }
        return values;
    }

    private static int[] vertexStrides(VertexLayout[] layouts) {
        if (layouts == null || layouts.length == 0) {
            return new int[0];
        }
        int[] strides = new int[layouts.length];
        for (int i = 0; i < layouts.length; i++) {
            strides[i] = layouts[i].arrayStride();
        }
        return strides;
    }

    private static int[] vertexStepModes(VertexLayout[] layouts) {
        if (layouts == null || layouts.length == 0) {
            return new int[0];
        }
        int[] stepModes = new int[layouts.length];
        for (int i = 0; i < layouts.length; i++) {
            stepModes[i] = layouts[i].stepMode() == VertexStepMode.INSTANCE ? 1 : 0;
        }
        return stepModes;
    }

    private static int[] attributeBindings(VertexLayout[] layouts) {
        int count = attributeCount(layouts);
        int[] bindings = new int[count];
        int out = 0;
        if (layouts != null) {
            for (int i = 0; i < layouts.length; i++) {
                VertexAttribute[] attributes = layouts[i].attributes();
                for (int j = 0; j < attributes.length; j++) {
                    bindings[out++] = i;
                }
            }
        }
        return bindings;
    }

    private static int[] attributeLocations(VertexLayout[] layouts) {
        int count = attributeCount(layouts);
        int[] locations = new int[count];
        int out = 0;
        if (layouts != null) {
            for (int i = 0; i < layouts.length; i++) {
                VertexAttribute[] attributes = layouts[i].attributes();
                for (int j = 0; j < attributes.length; j++) {
                    locations[out++] = attributes[j].location();
                }
            }
        }
        return locations;
    }

    private static int[] attributeFormats(VertexLayout[] layouts) {
        int count = attributeCount(layouts);
        int[] formats = new int[count];
        int out = 0;
        if (layouts != null) {
            for (int i = 0; i < layouts.length; i++) {
                VertexAttribute[] attributes = layouts[i].attributes();
                for (int j = 0; j < attributes.length; j++) {
                    formats[out++] = toNativeFormat(attributes[j].format());
                }
            }
        }
        return formats;
    }

    private static int[] attributeOffsets(VertexLayout[] layouts) {
        int count = attributeCount(layouts);
        int[] offsets = new int[count];
        int out = 0;
        if (layouts != null) {
            for (int i = 0; i < layouts.length; i++) {
                VertexAttribute[] attributes = layouts[i].attributes();
                for (int j = 0; j < attributes.length; j++) {
                    offsets[out++] = attributes[j].offset();
                }
            }
        }
        return offsets;
    }

    private static int attributeCount(VertexLayout[] layouts) {
        int count = 0;
        if (layouts != null) {
            for (int i = 0; i < layouts.length; i++) {
                count += layouts[i].attributes().length;
            }
        }
        return count;
    }

    private static int toNativeFormat(VertexFormat format) {
        switch (format) {
            case FLOAT32:
                return 0;
            case FLOAT32X2:
                return 1;
            case FLOAT32X3:
                return 2;
            case UNORM8X4:
                return 4;
            case FLOAT32X4:
            default:
                return 3;
        }
    }

    private static int toNativeWrap(TextureWrap wrap) {
        if (wrap == TextureWrap.REPEAT) {
            return 1;
        }
        if (wrap == TextureWrap.MIRRORED_REPEAT) {
            return 2;
        }
        return 0;
    }

    private static int toNativeFilter(TextureFilter filter) {
        return filter == TextureFilter.NEAREST ? 0 : 1;
    }
}
