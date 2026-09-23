package io.github.libfdx.backend.android;

import io.github.libfdx.math.ClipDepthRange;
import android.view.Surface;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.GraphicsProviderSupport;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsLimits;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptors;
import io.github.libfdx.graphics.shader.internal.ShaderCompilationTasks;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Sampler;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureOrigin;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.VertexStepMode;
import io.github.libfdx.graphics.internal.ShaderRenderBindings;
import io.github.libfdx.graphics.vulkan.VulkanConfiguration;
import io.github.libfdx.graphics.vulkan.VulkanProvider;
import io.github.libfdx.graphics.vulkan.internal.VulkanShaderLayoutValidator;
import io.github.libfdx.graphics.shader.runtime.ResolvedShaderPass;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationPhase;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationTrace;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import io.github.libfdx.graphics.shader.target.RuntimeShaderTargetCompiler;
import io.github.libfdx.graphics.shader.target.RuntimeWgslTargetVerifier;
import io.github.libfdx.graphics.shader.target.ShaderCompilerRegistry;
import io.github.libfdx.graphics.shader.target.ShaderTargetSupport;
import io.github.libfdx.graphics.shader.target.ShaderVerificationRequirement;
import io.github.libfdx.runtime.core.RuntimeCore;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Provides android vulkan services.
 *
 * @author xpenatan
 */
public final class AndroidVulkanProvider implements GraphicsAttachmentProvider, GraphicsProviderSupport {
    public static final ProviderId ID = VulkanProvider.ID;
    private static final int MAX_UNIFORM_BYTE_COUNT = 64 * 1024;
    private static final GraphicsCapabilities CAPABILITIES = GraphicsCapabilities.builder()
            .profile(ShaderProfile.PORTABLE_WEBGL2)
            .profile(ShaderProfile.PORTABLE_WEBGPU)
            .profile(ShaderProfile.NATIVE)
            .feature(GraphicsFeature.INDEXED_DRAW)
            .feature(GraphicsFeature.INSTANCED_DRAW)
            .feature(GraphicsFeature.DEPTH_STENCIL_ATTACHMENTS)
            .feature(GraphicsFeature.ALPHA_BLEND_CONTROL)
            .colorFormats(TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                    TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB)
            .depthStencilFormats(TextureFormat.DEPTH32_FLOAT)
            // Vulkan clips depth to 0..w.
            .clipDepthRange(ClipDepthRange.ZERO_TO_ONE)
            .renderedTextureOrigin(TextureOrigin.TOP_LEFT)
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

    private VulkanConfiguration configuration = new VulkanConfiguration().preparationWorkerLimit(2);

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
        return GraphicsAttachmentRequirements.vulkan();
    }

    /**
     * Creates a value.
     *
     * @param environment the environment
     * @return the created value
     */
    @Override
    public GraphicsAttachment create(GraphicsEnvironment environment) {
        if (environment == null) {
            throw new FdxException("GraphicsEnvironment cannot be null");
        }
        NativeWindow nativeWindow = environment.nativeWindow();
        if (nativeWindow == null || !(nativeWindow.objectHandle() instanceof Surface)) {
            throw new FdxException("Android Vulkan requires an Android Surface");
        }
        GraphicsContext sharedContext = environment.sharedContext();
        if (sharedContext != null) {
            if (!ID.equals(sharedContext.providerId())) {
                throw new FdxException("Cannot share a non-Vulkan graphics context with Android Vulkan");
            }
            throw new FdxException("Android Vulkan does not currently support shared graphics contexts");
        }
        String supportFailure = AndroidVulkanNative.instanceProbeFailure();
        if (supportFailure != null) {
            throw new FdxException(supportFailure);
        }
        return new AndroidVulkanGraphicsAttachment(configuration, (Surface) nativeWindow.objectHandle(),
                environment.display().framebufferWidth(), environment.display().framebufferHeight());
    }

    /**
     * Returns whether supported is enabled or true.
     *
     * @return true if supported is enabled or true; false otherwise
     */
    @Override
    public boolean isSupported() {
        return supportFailureReason() == null;
    }

    /**
     * Returns the support failure reason.
     *
     * @return the support failure reason
     */
    @Override
    public String supportFailureReason() {
        return AndroidVulkanNative.instanceProbeFailure();
    }

    /**
     * Returns the configuration.
     *
     * @return the configuration
     */
    public VulkanConfiguration configuration() {
        return configuration;
    }

    /**
     * Sets the configuration and returns this android vulkan provider.
     *
     * @param configuration the configuration
     * @return this android vulkan provider for chaining
     */
    public AndroidVulkanProvider configuration(VulkanConfiguration configuration) {
        this.configuration = configuration != null ? configuration : new VulkanConfiguration().preparationWorkerLimit(2);
        return this;
    }

    /**
     * Sets the v sync and returns this android vulkan provider.
     *
     * @param vSync the v sync
     * @return this android vulkan provider for chaining
     */
    public AndroidVulkanProvider vSync(boolean vSync) {
        configuration.vSync(vSync);
        return this;
    }

    /**
     * Sets the validation and returns this android vulkan provider.
     *
     * @param validation the validation
     * @return this android vulkan provider for chaining
     */
    public AndroidVulkanProvider validation(boolean validation) {
        configuration.validation(validation);
        return this;
    }

    /**
     * Sets the frames in flight and returns this android vulkan provider.
     *
     * @param framesInFlight the frames in flight
     * @return this android vulkan provider for chaining
     */
    public AndroidVulkanProvider framesInFlight(int framesInFlight) {
        configuration.framesInFlight(framesInFlight);
        return this;
    }

    /**
     * Represents an android vulkan graphics attachment.
     *
     * @author xpenatan
     */
    private static final class AndroidVulkanGraphicsAttachment implements GraphicsAttachment {
        private final long context;
        private final AndroidVulkanDeviceState deviceState;
        private final AndroidVulkanGraphicsDevice device = new AndroidVulkanGraphicsDevice(this);
        private final AndroidVulkanCommandEncoder commandEncoder = new AndroidVulkanCommandEncoder(this);
        private final AndroidVulkanTextureViewHandle colorAttachment = new AndroidVulkanTextureViewHandle(this);
        private final AndroidVulkanFrameBuffer frameBuffer = new AndroidVulkanFrameBuffer(this, colorAttachment);
        private final AndroidVulkanGraphicsFrame currentFrame = new AndroidVulkanGraphicsFrame(this,
                commandEncoder, frameBuffer, colorAttachment);
        private final TextureFormat surfaceFormat;
        private final int preparationWorkers;
        private final ShaderArtifactCache shaderCache;
        private int width;
        private int height;
        private int pendingResizeWidth;
        private int pendingResizeHeight;
        private boolean frameStarted;
        private boolean pendingResize;
        private volatile boolean disposed;

        AndroidVulkanGraphicsAttachment(VulkanConfiguration configuration, Surface surface, int width, int height) {
            VulkanConfiguration actualConfiguration = configuration != null ? configuration : new VulkanConfiguration();
            preparationWorkers = actualConfiguration.preparationWorkerLimit();
            shaderCache = actualConfiguration.shaderCache();
            this.width = width;
            this.height = height;
            context = AndroidVulkanNative.create(surface, width, height, actualConfiguration.vSync(),
                    actualConfiguration.preferMailboxPresentMode(), actualConfiguration.framesInFlight());
            deviceState = new AndroidVulkanDeviceState(() -> AndroidVulkanNative.isDeviceLost(context), device::closePreparation);
            surfaceFormat = toCommonFormat(AndroidVulkanNative.surfaceFormat(context));
        }

        /**
         * Handles a size change.
         *
         * @param framebufferWidth the framebuffer width
         * @param framebufferHeight the framebuffer height
         */
        @Override
        public void resize(int framebufferWidth, int framebufferHeight) {
            ensureNotDisposed("resize");
            int nextWidth = Math.max(1, framebufferWidth);
            int nextHeight = Math.max(1, framebufferHeight);
            AndroidVulkanNative.resize(context, nextWidth, nextHeight);
            if (frameStarted) {
                pendingResizeWidth = nextWidth;
                pendingResizeHeight = nextHeight;
                pendingResize = true;
            } else {
                width = nextWidth;
                height = nextHeight;
                pendingResize = false;
            }
        }

        /**
         * Runs the process events step.
         */
        @Override
        public void processEvents() {
            if (!disposed) deviceState.requireUsable();
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
            deviceState.requireUsable();
            if (frameStarted) {
                throw new FdxException("Android Vulkan frame is already started");
            }
            commandEncoder.beginFrame();
            frameStarted = AndroidVulkanNative.beginFrame(context);
            if (frameStarted) {
                applyPendingResizeDimensions();
            }
            return frameStarted;
        }

        /**
         * Ends frame.
         */
        @Override
        public void endFrame() {
            if (!frameStarted) {
                return;
            }
            try {
                deviceState.requireUsable();
                commandEncoder.ensurePassesEnded();
                AndroidVulkanNative.endFrame(context);
                applyPendingResizeDimensions();
            } finally {
                frameStarted = false;
            }
        }

        /**
         * Returns the device.
         *
         * @return the device
         */
        @Override
        public GraphicsDevice device() {
            ensureNotDisposed("access the graphics device");
            return device;
        }

        /**
         * Returns the surface format.
         *
         * @return the surface format
         */
        @Override
        public TextureFormat surfaceFormat() {
            ensureNotDisposed("access the surface format");
            return surfaceFormat;
        }

        /**
         * Returns the current frame.
         *
         * @return the current frame
         */
        @Override
        public GraphicsFrame currentFrame() {
            ensureFrameStarted("access the current frame");
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
            ensureFrameStarted("clear");
            AndroidVulkanNative.clear(context, red, green, blue, alpha);
        }

        ByteBuffer readPixelsRgba8() {
            ensureFrameStarted("read pixels");
            int byteCount = width * height * 4;
            ByteBuffer pixels = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
            try {
                AndroidVulkanNative.readPixelsRgba8(context, pixels, byteCount);
                applyPendingResizeDimensions();
            } finally {
                frameStarted = false;
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
                try { device.closePreparation(); }
                finally { AndroidVulkanNative.destroy(context); }
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

        private void applyPendingResizeDimensions() {
            if (!pendingResize) {
                return;
            }
            width = pendingResizeWidth;
            height = pendingResizeHeight;
            pendingResize = false;
        }

        private void ensureNotDisposed(String operation) {
            if (disposed) {
                throw new FdxException("Cannot " + operation + " after the Android Vulkan context is disposed");
            }
            deviceState.requireUsable();
        }

        private void ensureFrameStarted(String operation) {
            ensureNotDisposed(operation);
            if (!frameStarted) {
                throw new FdxException("Cannot " + operation + " outside an active Android Vulkan frame");
            }
        }
    }

    /**
     * Represents an android vulkan graphics device.
     *
     * @author xpenatan
     */
    private static final class AndroidVulkanGraphicsDevice implements GraphicsDevice {
        private final AndroidVulkanGraphicsAttachment attachment;
        private final Object closedDomain = new Object();
        private ShaderPreparationCapabilities preparationCapabilities;
        private volatile AndroidVulkanPreparationQueue preparation;

        AndroidVulkanGraphicsDevice(AndroidVulkanGraphicsAttachment attachment) {
            this.attachment = attachment;
        }

        @Override
        public Object resourceDomain() {
            return attachment.disposed || attachment.deviceState.poll() ? closedDomain : attachment;
        }
        @Override
        public ShaderPreparationCapabilities shaderPreparationCapabilities() {
            if (preparationCapabilities == null) preparationCapabilities = new ShaderPreparationCapabilities(
                    ShaderPreparationCapabilities.Execution.WORKERS, ShaderPreparationCapabilities.Execution.WORKERS,
                    true, attachment.preparationWorkers,
                    attachment.shaderCache != null && attachment.shaderCache.enabled(),
                    attachment.shaderCache != null && attachment.shaderCache.supportsAtomicUpdate());
            return preparationCapabilities;
        }
        @Override
        public ShaderPreparationOperation prepareRenderPipeline(ShaderPipelineRequest request) {
            attachment.ensureNotDisposed("prepare a pipeline");
            if (request == null) throw new FdxException("Shader pipeline request cannot be null");
            if (preparation == null) preparation = new AndroidVulkanPreparationQueue(attachment);
            return preparation.submit(request);
        }
        void closePreparation() { if (preparation != null) preparation.close(); }

        /**
         * Creates a buffer.
         *
         * @param descriptor the descriptor
         * @return the created value
         */
        @Override
        public Buffer createBuffer(BufferDescriptor descriptor) {
            attachment.ensureNotDisposed("create a buffer");
            if (descriptor == null) {
                throw new FdxException("BufferDescriptor cannot be null");
            }
            return new AndroidVulkanBufferHandle(attachment, AndroidVulkanNative.createBuffer(attachment.context,
                    descriptor.size(), toNativeBufferUsage(descriptor.usage())), descriptor.size(), descriptor.usage());
        }

        /**
         * Runs the write buffer step.
         *
         * @param buffer the buffer
         * @param data the data
         */
        @Override
        public void writeBuffer(Buffer buffer, ByteBuffer data) {
            attachment.ensureNotDisposed("write a buffer");
            if (data == null) {
                throw new FdxException("Buffer data cannot be null");
            }
            AndroidVulkanBufferHandle vulkanBuffer = AndroidVulkanResources.requireBuffer(buffer, attachment,
                    "Buffer");
            if (data.remaining() > vulkanBuffer.size()) {
                throw new FdxException("Buffer data is larger than the destination buffer");
            }
            ByteBuffer source = data.position() == 0 ? data : data.slice();
            AndroidVulkanNative.writeBuffer(vulkanBuffer.handle(), source, source.remaining());
        }

        /**
         * Creates a texture.
         *
         * @param descriptor the descriptor
         * @return the created value
         */
        @Override
        public Texture createTexture(TextureDescriptor descriptor) {
            attachment.ensureNotDisposed("create a texture");
            if (descriptor == null) {
                throw new FdxException("TextureDescriptor cannot be null");
            }
            descriptor.validate(capabilities());
            if (descriptor.format() != TextureFormat.RGBA8_UNORM
                    && descriptor.format() != TextureFormat.RGBA8_UNORM_SRGB) {
                throw new FdxException("Android Vulkan currently supports RGBA8 textures only");
            }
            if (!descriptor.usage().sampled() && !descriptor.usage().renderAttachment()) {
                throw new FdxException("Android Vulkan texture usage must allow sampling or render attachment binding");
            }
            return new AndroidVulkanTextureHandle(attachment, AndroidVulkanNative.createTexture(attachment.context,
                    descriptor.width(), descriptor.height(), toNativeTextureFormat(descriptor.format()),
                    toNativeWrap(descriptor.wrapS()), toNativeWrap(descriptor.wrapT()),
                    toNativeFilter(descriptor.filter()),
                    descriptor.usage().sampled(), descriptor.usage().renderAttachment()),
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
            attachment.ensureNotDisposed("write a texture");
            if (data == null) {
                throw new FdxException("Texture data cannot be null");
            }
            AndroidVulkanTextureHandle vulkanTexture = AndroidVulkanResources.requireTexture(texture, attachment,
                    "Texture");
            int byteCount = vulkanTexture.width() * vulkanTexture.height() * 4;
            if (data.remaining() != byteCount) {
                throw new FdxException("Android Vulkan texture upload expects " + byteCount + " RGBA bytes");
            }
            ByteBuffer source = data.position() == 0 ? data : data.slice();
            AndroidVulkanNative.writeTexture(vulkanTexture.handle(), source, source.remaining());
        }

        /**
         * Creates a shader module.
         *
         * @param descriptor the descriptor
         * @return the created value
         */
        @Override
        public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
            attachment.ensureNotDisposed("create a shader module");
            if (descriptor == null) {
                throw new FdxException("ShaderModuleDescriptor cannot be null");
            }
            descriptor = ShaderModuleDescriptors.requireTarget(descriptor, ShaderTarget.VULKAN_SPIRV,
                    "Android Vulkan");
            if (descriptor.targetArtifact() != null) {
                shaderTargetSupport().require(descriptor.targetArtifact());
                VulkanShaderLayoutValidator.requireArtifact(
                        descriptor.targetArtifact());
            }
            if (!descriptor.hasSource(ShaderLanguage.SPIRV)) {
                throw new FdxException("Android Vulkan requires SPIR-V shader modules");
            }
            return new AndroidVulkanShaderModuleHandle(attachment,
                    AndroidVulkanNative.createShaderModule(attachment.context,
                            descriptor.spirvVertexWords(), descriptor.spirvFragmentWords()), descriptor.reflection());
        }

        /**
         * Creates a render pipeline.
         *
         * @param descriptor the descriptor
         * @return the created value
         */
        @Override
        public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
            attachment.ensureNotDisposed("create a render pipeline");
            if (descriptor == null) {
                throw new FdxException("RenderPipelineDescriptor cannot be null");
            }
            AndroidVulkanShaderModuleHandle shaderModule = AndroidVulkanResources.requireShaderModule(
                    descriptor.shaderModule(), attachment, "Shader module");
            descriptor.validate(capabilities());
            if (descriptor.renderTargetLayout().colorAttachmentCount() != 1) {
                throw new FdxException("Android Vulkan currently requires exactly one color attachment");
            }
            ShaderRenderBindings resourceBindings = ShaderRenderBindings.from(descriptor);
            VulkanShaderLayoutValidator.requireRenderLayout(resourceBindings);
            boolean uniformBufferEnabled = resourceBindings.hasUniformBuffer();
            VertexLayout[] vertexLayouts = descriptor.vertexLayouts();
            return new AndroidVulkanRenderPipelineHandle(attachment,
                    AndroidVulkanNative.createRenderPipeline(attachment.context,
                    shaderModule.handle(), toNativeTextureFormat(descriptor.colorFormat()),
                    toNativeTopology(descriptor.primitiveTopology()), vertexStrides(vertexLayouts),
                    vertexStepModes(vertexLayouts), attributeBindings(vertexLayouts), attributeLocations(vertexLayouts),
                    attributeFormats(vertexLayouts), attributeOffsets(vertexLayouts),
                    descriptor.sampledTextureCount(), uniformBufferEnabled, descriptor.depthTestEnabled(),
                    descriptor.colorTargets()[0].blend() != null, descriptor.depthWriteEnabled(),
                    descriptor.vertexEntryPoint(), descriptor.fragmentEntryPoint(), false),
                    descriptor.primitiveTopology(), descriptor.sampledTextureCount(),
                    resourceBindings, resourceBindings.uniformSetIndex(),
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

    /** CPU/native creation uses retained device access and private inputs. Only finish publishes
     * an attachment-owned pipeline. Workers never access frames, surfaces or render-pass caches. */
    private static final class AndroidVulkanPreparationQueue implements AutoCloseable {
        private final AndroidVulkanGraphicsAttachment attachment;
        private final long nativeContext;
        private final AndroidShaderPreparationExecutor executor;
        private final ShaderCompilerRegistry compilers;
        private final ShaderCompilerRegistry refreshCompilers;
        private final AndroidVulkanPipelineCache pipelineCache;
        private final Set<Job> jobs = ConcurrentHashMap.newKeySet();
        private volatile boolean closed;

        AndroidVulkanPreparationQueue(AndroidVulkanGraphicsAttachment attachment) {
            this.attachment = attachment;
            nativeContext = attachment.context;
            executor = new AndroidShaderPreparationExecutor(attachment.preparationWorkers);
            pipelineCache = new AndroidVulkanPipelineCache(nativeContext, attachment.shaderCache, executor::execute, attachment.deviceState);
            var compiler = RuntimeCore.shaderCompiler();
            compilers = ShaderCompilerRegistry.builder().compiler(new RuntimeShaderTargetCompiler(compiler,
                            RuntimeShaderTargetCompiler.VERSION, attachment.shaderCache))
                    .verifier(new RuntimeWgslTargetVerifier(compiler)).build();
            refreshCompilers = attachment.shaderCache == null || !attachment.shaderCache.enabled() ? compilers
                    : ShaderCompilerRegistry.builder().compiler(new RuntimeShaderTargetCompiler(compiler,
                            RuntimeShaderTargetCompiler.VERSION, attachment.shaderCache.refreshing()))
                            .verifier(new RuntimeWgslTargetVerifier(compiler)).build();
        }

        ShaderPreparationOperation submit(ShaderPipelineRequest request) {
            if (closed) throw new FdxException("Android Vulkan preparation is closed");
            Job job = new Job(request);
            AndroidVulkanNative.retainPreparationDevice(nativeContext);
            try { jobs.add(job); executor.execute(job.trace.wrap(job)); }
            catch (RuntimeException | Error failure) {
                jobs.remove(job); AndroidVulkanNative.releasePreparationDevice(nativeContext); throw failure;
            }
            return job;
        }
        @Override
        public void close() {
            if (closed) return;
            closed = true;
            Throwable first = null;
            try {
                for (Job job : jobs) try { job.cancel(); }
                catch (RuntimeException | Error failure) {
                    if (first == null) first = failure; else first.addSuppressed(failure);
                }
            } finally { executor.dispose(); }
            if (first instanceof RuntimeException failure) throw failure;
            if (first instanceof Error failure) throw failure;
        }

        private FdxFuture<ShaderModuleDescriptor> translateAsync(ShaderModuleDescriptor source,
                ShaderCompilerRegistry registry, Consumer<Runnable> execute) {
            FdxFuture<ShaderModuleDescriptor> reflected = FdxFuture.completed(source);
            if (!source.reflection().complete()) {
                reflected = ShaderModuleDescriptors.requireTargetAsync(source, ShaderTarget.WGPU_WGSL,
                        registry, ShaderVerificationRequirement.REQUIRED, "Android Vulkan reflection", execute);
            }
            return ShaderCompilationTasks.then(reflected, execute, descriptor ->
                    ShaderModuleDescriptors.requireTargetAsync(descriptor.entryPoints(source.vertexEntryPoint(), source.fragmentEntryPoint()),
                            ShaderTarget.VULKAN_SPIRV, registry, ShaderVerificationRequirement.PROVIDER_PIPELINE,
                            "Android Vulkan", execute));
        }

        private final class Job implements ShaderPreparationOperation, Runnable {
            private final ShaderPipelineRequest request;
            private final AtomicReference<Prepared> output = new AtomicReference<>();
            private final AtomicBoolean completed = new AtomicBoolean();
            private volatile boolean done, cancelled;
            private final ShaderPreparationTrace trace = new ShaderPreparationTrace();
            private Throwable failure;
            private boolean finished, disposed;
            private ShaderModuleDescriptor source;
            private boolean refreshedTranslation;
            private final Consumer<Runnable> execute = work -> { requireActive(); executor.execute(trace.wrap(work)); };

            Job(ShaderPipelineRequest request) { this.request = request; }
            private void requireActive() {
                if (cancelled || closed) throw new CancellationException("Android Vulkan preparation cancelled");
                attachment.deviceState.requireUsable();
            }

            @Override
            public void run() {
                try {
                    requireActive(); trace.enter(ShaderPreparationPhase.SOURCE);
                    source = request.sourceDescriptor();
                    requireActive(); trace.enter(ShaderPreparationPhase.TRANSLATION);
                    beginTranslation(compilers);
                } catch (Throwable error) { fail(error); }
            }

            private void beginTranslation(ShaderCompilerRegistry registry) {
                ShaderCompilationTasks.then(translateAsync(source, registry, execute), execute, translated -> {
                    trace.enter(ShaderPreparationPhase.CACHE_LOOKUP);
                    return ShaderCompilationTasks.then(pipelineCache.initializeAsync(), execute, initialized -> {
                        prepareNative(translated);
                        return FdxFuture.completed(null);
                    });
                }).onFailure(this::fail);
            }

            private void fail(Throwable error) {
                if (!completed.compareAndSet(false, true)) return;
                attachment.deviceState.observe(error);
                failure = error;
                try { AndroidVulkanNative.releasePreparationDevice(nativeContext); }
                catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                trace.enter(ShaderPreparationPhase.PUBLICATION_WAIT); done = true;
                jobs.remove(this);
            }

            private void prepareNative(ShaderModuleDescriptor translated) {
                long module = 0, pipeline = 0;
                Prepared prepared = null;
                Throwable nativeFailure = null;
                boolean nativeStarted = false;
                try {
                    requireActive();
                    if (translated.targetArtifact() != null) {
                        ShaderTargetSupport.forProvider(ID).require(translated.targetArtifact());
                        VulkanShaderLayoutValidator.requireArtifact(translated.targetArtifact());
                    }
                    var descriptor = request.pipelineDescriptor(new PreparationModule(translated.reflection()));
                    descriptor.validate(CAPABILITIES);
                    if (descriptor.renderTargetLayout().colorAttachmentCount() != 1)
                        throw new FdxException("Android Vulkan requires exactly one color attachment");
                    ShaderRenderBindings bindings = ShaderRenderBindings.from(descriptor);
                    VulkanShaderLayoutValidator.requireRenderLayout(bindings);
                    requireActive(); trace.enter(ShaderPreparationPhase.COMPILATION);
                    module = AndroidVulkanNative.createShaderModule(nativeContext,
                            translated.spirvVertexWords(), translated.spirvFragmentWords());
                    requireActive(); trace.enter(ShaderPreparationPhase.PIPELINE);
                    VertexLayout[] layouts = descriptor.vertexLayouts();
                    pipelineCache.beginNative(); nativeStarted = true;
                    pipeline = AndroidVulkanNative.createRenderPipeline(nativeContext, module,
                            toNativeTextureFormat(descriptor.colorFormat()), toNativeTopology(descriptor.primitiveTopology()),
                            vertexStrides(layouts), vertexStepModes(layouts), attributeBindings(layouts), attributeLocations(layouts),
                            attributeFormats(layouts), attributeOffsets(layouts), descriptor.sampledTextureCount(),
                            bindings.hasUniformBuffer(), descriptor.depthTestEnabled(), descriptor.colorTargets()[0].blend() != null,
                            descriptor.depthWriteEnabled(), descriptor.vertexEntryPoint(), descriptor.fragmentEntryPoint(), true);
                    prepared = new Prepared(pipeline, descriptor, bindings);
                } catch (Throwable error) { nativeFailure = error; }
                finally {
                    try { if (module != 0) AndroidVulkanNative.destroyShaderModule(module); }
                    catch (Throwable error) {
                        if (nativeFailure == null) nativeFailure = error;
                        else nativeFailure.addSuppressed(error);
                    }
                    try { if (nativeStarted) pipelineCache.endNative(); }
                    catch (Throwable error) {
                        if (nativeFailure == null) nativeFailure = error;
                        else nativeFailure.addSuppressed(error);
                    }
                }
                if (nativeFailure != null) {
                    attachment.deviceState.observe(nativeFailure);
                    try { if (pipeline != 0) AndroidVulkanNative.discardPreparedPipeline(pipeline); }
                    catch (Throwable cleanup) { nativeFailure.addSuppressed(cleanup); }
                    if (!refreshedTranslation && compilers != refreshCompilers
                            && nativeFailure instanceof RuntimeException && !(nativeFailure instanceof CancellationException)
                            && !cancelled && !closed) {
                        refreshedTranslation = true; trace.enter(ShaderPreparationPhase.TRANSLATION);
                        beginTranslation(refreshCompilers);
                    } else fail(nativeFailure);
                    return;
                }
                output.set(prepared);
                completed.set(true); trace.enter(ShaderPreparationPhase.PUBLICATION_WAIT); done = true;
                if (cancelled || closed) discard();
                if (output.get() == null) jobs.remove(this);
            }
            private void discard() {
                Prepared prepared = output.getAndSet(null);
                if (prepared != null) try { AndroidVulkanNative.discardPreparedPipeline(prepared.pipeline()); }
                finally { AndroidVulkanNative.releasePreparationDevice(nativeContext); }
            }
            @Override
            public boolean isDone() { return done; }
            @Override
            public ShaderPreparationPhase phase() { return trace.phase(); }
            @Override
            public ShaderPreparationTrace trace() { return trace; }
            @Override
            public ShaderPreparedResult finish() {
                if (!done || finished) throw new FdxException("Android Vulkan preparation cannot be published now");
                finished = true; requireActive();
                attachment.ensureNotDisposed("publish a prepared pipeline");
                if (failure instanceof Error error) throw error;
                if (failure instanceof RuntimeException error) throw error;
                if (failure != null) throw new FdxException("Android Vulkan preparation failed", failure);
                Prepared prepared = output.getAndSet(null);
                if (prepared == null) throw new CancellationException("Android Vulkan result was retired");
                boolean published = false;
                try {
                    var descriptor = prepared.descriptor();
                    RenderPipeline pipeline = new AndroidVulkanRenderPipelineHandle(attachment, prepared.pipeline(),
                            descriptor.primitiveTopology(), descriptor.sampledTextureCount(), prepared.bindings(),
                            prepared.bindings().uniformSetIndex(), descriptor.renderTargetLayout());
                    ShaderPreparedResult result = new ShaderPreparedResult(ResolvedShaderPass.of(request.passId(), pipeline,
                            prepared.bindings().layout(), request.providerRevision()), pipeline);
                    published = true; return result;
                } finally {
                    try { if (!published) AndroidVulkanNative.discardPreparedPipeline(prepared.pipeline()); }
                    finally { AndroidVulkanNative.releasePreparationDevice(nativeContext); jobs.remove(this); }
                }
            }
            @Override
            public void cancel() { cancelled = true; if (done) { discard(); jobs.remove(this); } }
            @Override
            public void dispose() {
                if (disposed) return;
                if (!done) throw new FdxException("Android Vulkan preparation has not drained");
                disposed = true; discard(); jobs.remove(this);
            }
            @Override
            public boolean isDisposed() { return disposed; }
        }
        private record Prepared(long pipeline, RenderPipelineDescriptor descriptor, ShaderRenderBindings bindings) { }
        private record PreparationModule(ShaderReflection reflection) implements ShaderModule {
            @Override
            public ShaderLanguage language() { return ShaderLanguage.SPIRV; }
            @Override
            public ProviderId providerId() { return ID; }
            @Override
            public <T> T as() { throw new FdxException("Preparation metadata has no native module"); }
            @Override
            public void dispose() { }
            @Override
            public boolean isDisposed() { return false; }
        }
    }

    private static final class AndroidVulkanResources {
        private AndroidVulkanResources() {
        }

        static AndroidVulkanBufferHandle requireBuffer(Buffer value, AndroidVulkanGraphicsAttachment attachment,
                String name) {
            if (value == null) {
                throw new FdxException(name + " cannot be null");
            }
            if (!(value instanceof AndroidVulkanBufferHandle handle)) {
                throw new FdxException(name + " belongs to another graphics provider");
            }
            requireOwner(handle.attachment, attachment, name);
            if (handle.isDisposed()) {
                throw new FdxException(name + " has been disposed");
            }
            return handle;
        }

        static AndroidVulkanTextureHandle requireTexture(Texture value, AndroidVulkanGraphicsAttachment attachment,
                String name) {
            if (value == null) {
                throw new FdxException(name + " cannot be null");
            }
            if (!(value instanceof AndroidVulkanTextureHandle handle)) {
                throw new FdxException(name + " belongs to another graphics provider");
            }
            requireOwner(handle.attachment, attachment, name);
            if (handle.isDisposed()) {
                throw new FdxException(name + " has been disposed");
            }
            return handle;
        }

        static AndroidVulkanShaderModuleHandle requireShaderModule(ShaderModule value,
                AndroidVulkanGraphicsAttachment attachment, String name) {
            if (value == null) {
                throw new FdxException(name + " cannot be null");
            }
            if (!(value instanceof AndroidVulkanShaderModuleHandle handle)) {
                throw new FdxException(name + " belongs to another graphics provider");
            }
            requireOwner(handle.attachment, attachment, name);
            if (handle.isDisposed()) {
                throw new FdxException(name + " has been disposed");
            }
            return handle;
        }

        static AndroidVulkanRenderPipelineHandle requirePipeline(RenderPipeline value,
                AndroidVulkanGraphicsAttachment attachment, String name) {
            if (value == null) {
                throw new FdxException(name + " cannot be null");
            }
            if (!(value instanceof AndroidVulkanRenderPipelineHandle handle)) {
                throw new FdxException(name + " belongs to another graphics provider");
            }
            requireOwner(handle.attachment, attachment, name);
            if (handle.isDisposed()) {
                throw new FdxException(name + " has been disposed");
            }
            return handle;
        }

        static AndroidVulkanTextureViewHandle requireTextureView(TextureView value,
                AndroidVulkanGraphicsAttachment attachment, String name) {
            if (value == null) {
                throw new FdxException(name + " cannot be null");
            }
            if (!(value instanceof AndroidVulkanTextureViewHandle handle)) {
                throw new FdxException(name + " belongs to another graphics provider");
            }
            if (handle.texture != null) {
                AndroidVulkanTextureHandle texture = requireTexture(handle.texture, attachment, name + " texture");
                if (!texture.usage().renderAttachment()) {
                    throw new FdxException(name + " texture was not created for render attachment usage");
                }
            } else {
                requireOwner(handle.attachment, attachment, name);
                attachment.ensureFrameStarted("use " + name.toLowerCase());
            }
            return handle;
        }

        private static void requireOwner(AndroidVulkanGraphicsAttachment actual,
                AndroidVulkanGraphicsAttachment expected, String name) {
            if (actual != expected) {
                throw new FdxException(name + " belongs to another Android Vulkan context");
            }
            expected.ensureNotDisposed("use " + name.toLowerCase());
        }
    }

    /**
     * Represents an android vulkan command encoder.
     *
     * @author xpenatan
     */
    private static final class AndroidVulkanCommandEncoder implements CommandEncoder {
        private final AndroidVulkanGraphicsAttachment attachment;
        private AndroidVulkanRenderPass[] renderPasses = new AndroidVulkanRenderPass[4];
        private int renderPassCount;

        AndroidVulkanCommandEncoder(AndroidVulkanGraphicsAttachment attachment) {
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
            AndroidVulkanTextureViewHandle colorAttachment = AndroidVulkanResources.requireTextureView(
                    descriptor.colorAttachment(), attachment, "Color attachment");
            LoadOp loadOp = descriptor.colorLoadOp();
            StoreOp storeOp = descriptor.colorStoreOp();
            AndroidVulkanNative.beginRenderPass(attachment.context, colorAttachment.textureHandle(),
                    toNativeTextureFormat(colorAttachment.format()), colorAttachment.width(), colorAttachment.height(),
                    loadOp.isClear(), loadOp.red(), loadOp.green(), loadOp.blue(), loadOp.alpha(),
                    storeOp.isStore(), descriptor.depthClearEnabled(), descriptor.depthClearValue());
            AndroidVulkanRenderPass renderPass = nextRenderPass();
            renderPass.begin(colorAttachment, colorAttachment.height(),
                    RenderPassCompatibility.of(compatibility.targetLayout(),
                            colorAttachment.width(), colorAttachment.height()));
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
                    throw new FdxException("Android Vulkan render pass must be ended before ending the frame");
                }
            }
        }

        private void ensurePreviousPassEnded() {
            if (renderPassCount > 0 && !renderPasses[renderPassCount - 1].isEnded()) {
                throw new FdxException(
                        "Previous Android Vulkan render pass must be ended before beginning another pass");
            }
        }

        private AndroidVulkanRenderPass nextRenderPass() {
            if (renderPassCount == renderPasses.length) {
                AndroidVulkanRenderPass[] grown = new AndroidVulkanRenderPass[renderPasses.length * 2];
                System.arraycopy(renderPasses, 0, grown, 0, renderPasses.length);
                renderPasses = grown;
            }
            AndroidVulkanRenderPass renderPass = renderPasses[renderPassCount];
            if (renderPass == null) {
                renderPass = new AndroidVulkanRenderPass(attachment);
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
     * Represents an android vulkan render pass.
     *
     * @author xpenatan
     */
    private static final class AndroidVulkanRenderPass implements RenderPass {
        private final AndroidVulkanGraphicsAttachment attachment;
        private AndroidVulkanTextureViewHandle colorAttachment;
        private int renderTargetHeight;
        private final ByteBuffer uniformBytes = ByteBuffer.allocateDirect(MAX_UNIFORM_BYTE_COUNT)
                .order(ByteOrder.nativeOrder());
        private ShaderParameterBlock compatibilityUniformBlock;
        private AndroidVulkanRenderPipelineHandle pipeline;
        private RenderPassCompatibility compatibility;
        private AndroidVulkanBufferHandle indexBuffer;
        private AndroidVulkanBufferHandle[] vertexBuffers = new AndroidVulkanBufferHandle[0];
        private AndroidVulkanTextureHandle[] textures = new AndroidVulkanTextureHandle[0];
        private long[] textureHandles = new long[0];
        private boolean uniformDataDirty;
        private boolean hasUniformData;
        private boolean ended = true;

        AndroidVulkanRenderPass(AndroidVulkanGraphicsAttachment attachment) {
            this.attachment = attachment;
        }

        void begin(AndroidVulkanTextureViewHandle colorAttachment, int renderTargetHeight,
                RenderPassCompatibility compatibility) {
            if (!ended) {
                throw new FdxException("Cannot reuse an active Android Vulkan render pass");
            }
            this.colorAttachment = colorAttachment;
            this.renderTargetHeight = renderTargetHeight;
            this.compatibility = compatibility;
            pipeline = null;
            indexBuffer = null;
            for (int i = 0; i < vertexBuffers.length; i++) {
                vertexBuffers[i] = null;
            }
            for (int i = 0; i < textures.length; i++) {
                textures[i] = null;
                textureHandles[i] = 0L;
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
            this.pipeline = AndroidVulkanResources.requirePipeline(pipeline, attachment, "Render pipeline");
            if (!compatibility.isCompatible(this.pipeline.targetLayout())) {
                this.pipeline = null;
                throw new FdxException(
                        "Android Vulkan render pipeline target layout is incompatible with the active pass");
            }
            prepareTextureSlots(this.pipeline.sampledTextureCount());
            uniformDataDirty = true;
            hasUniformData = false;
            compatibilityUniformBlock = null;
            AndroidVulkanNative.setPipeline(attachment.context, this.pipeline.handle());
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
         * @param slot the vertex buffer slot
         * @param buffer the buffer
         */
        @Override
        public void setVertexBuffer(int slot, Buffer buffer) {
            ensureOpen();
            if (slot < 0) {
                throw new FdxException("Vertex buffer slot cannot be negative");
            }
            AndroidVulkanBufferHandle vulkanBuffer = AndroidVulkanResources.requireBuffer(buffer, attachment,
                    "Vertex buffer");
            if (vulkanBuffer.usage() != BufferUsage.VERTEX) {
                throw new FdxException("RenderPass.setVertexBuffer requires a vertex buffer");
            }
            rememberVertexBuffer(slot, vulkanBuffer);
            AndroidVulkanNative.setVertexBuffer(attachment.context, slot, vulkanBuffer.handle());
        }

        /**
         * Sets the index buffer.
         *
         * @param buffer the buffer
         */
        @Override
        public void setIndexBuffer(Buffer buffer) {
            ensureOpen();
            indexBuffer = AndroidVulkanResources.requireBuffer(buffer, attachment, "Index buffer");
            if (indexBuffer.usage() != BufferUsage.INDEX) {
                throw new FdxException("RenderPass.setIndexBuffer requires an index buffer");
            }
            AndroidVulkanNative.setIndexBuffer(attachment.context, indexBuffer.handle());
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
                throw new FdxException("Texture slot is not declared by the active Android Vulkan pipeline: " + slot);
            }
            AndroidVulkanTextureHandle vulkanTexture = AndroidVulkanResources.requireTexture(texture, attachment,
                    "Texture");
            if (!vulkanTexture.usage().sampled()) {
                throw new FdxException("RenderPass.setTexture requires a sampled texture");
            }
            textures[slot] = vulkanTexture;
        }

        @Override
        public void setTextureBinding(int group, int binding, Texture texture) {
            requirePipeline();
            int slot = pipeline.resourceBindings().textureSlot(group, binding);
            if (slot < 0) {
                throw new FdxException("Texture binding is not declared by the active Android Vulkan pipeline: "
                        + group + ':' + binding);
            }
            setTexture(slot, texture);
        }

        @Override
        public void setTextureSamplerBinding(int group, int binding, Texture texture) {
            requirePipeline();
            int slot = pipeline.resourceBindings().samplerSlot(group, binding);
            if (slot < 0) {
                throw new FdxException("Sampler binding is not declared by the active Android Vulkan pipeline: "
                        + group + ':' + binding);
            }
            setTexture(slot, texture);
        }

        @Override
        public void setSamplerBinding(int group, int binding, Sampler sampler) {
            throw new FdxException("Separate sampler objects are not supported by Android Vulkan");
        }

        @Override
        public void setParameterBlock(int group, int binding, ShaderParameterBlock block) {
            requirePipeline();
            pipeline.resourceBindings().requireParameterBlock(group, binding, block);
            block.copyTo(uniformBytes, 0);
            markUniformDirty();
        }

        /**
         * Sets the scissor.
         *
         * @param x the x coordinate
         * @param y the y coordinate
         * @param width the width in pixels
         * @param height the height in pixels
         */
        @Override
        public void setScissor(int x, int y, int width, int height) {
            ensureOpen();
            if (width <= 0 || height <= 0) {
                throw new FdxException("Scissor size must be greater than zero");
            }
            AndroidVulkanNative.setScissor(attachment.context, x, renderTargetHeight - y - height, width, height);
        }

        /**
         * Sets the viewport.
         *
         * @param x the lower-left x coordinate
         * @param y the lower-left y coordinate
         * @param width the width in pixels
         * @param height the height in pixels
         */
        @Override
        public void setViewport(int x, int y, int width, int height) {
            ensureOpen();
            if (width <= 0 || height <= 0) {
                throw new FdxException("Viewport size must be greater than zero");
            }
            AndroidVulkanNative.setViewport(attachment.context, x, y, width, height);
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
            ensureOpen();
            if (pipeline == null) {
                throw new FdxException("Render pipeline must be set before draw");
            }
            validateBoundResources(false);
            bindTextures();
            bindUniforms();
            AndroidVulkanNative.draw(attachment.context, vertexCount, instanceCount, firstVertex, firstInstance);
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
            ensureOpen();
            if (pipeline == null) {
                throw new FdxException("Render pipeline must be set before drawIndexed");
            }
            if (indexBuffer == null) {
                throw new FdxException("Index buffer must be set before drawIndexed");
            }
            validateBoundResources(true);
            bindTextures();
            bindUniforms();
            AndroidVulkanNative.drawIndexed(attachment.context, indexCount, instanceCount, firstIndex,
                    baseVertex, firstInstance);
        }

        /**
         * Ends the operation.
         */
        @Override
        public void end() {
            if (ended) {
                return;
            }
            ended = true;
            if (!attachment.disposed && !attachment.deviceState.poll()) {
                attachment.ensureFrameStarted("end a render pass");
                AndroidVulkanNative.endRenderPass(attachment.context);
            }
            pipeline = null;
            indexBuffer = null;
            colorAttachment = null;
            compatibility = null;
            for (int i = 0; i < vertexBuffers.length; i++) {
                vertexBuffers[i] = null;
            }
            for (int i = 0; i < textures.length; i++) {
                textures[i] = null;
                textureHandles[i] = 0L;
            }
        }

        private void ensureOpen() {
            attachment.ensureFrameStarted("use a render pass");
            if (ended) {
                throw new FdxException("Render pass has already ended");
            }
            AndroidVulkanResources.requireTextureView(colorAttachment, attachment, "Color attachment");
        }

        private void validateBoundResources(boolean indexed) {
            AndroidVulkanResources.requirePipeline(pipeline, attachment, "Render pipeline");
            for (int i = 0; i < vertexBuffers.length; i++) {
                if (vertexBuffers[i] != null) {
                    AndroidVulkanResources.requireBuffer(vertexBuffers[i], attachment,
                            "Vertex buffer at slot " + i);
                }
            }
            if (indexed) {
                AndroidVulkanResources.requireBuffer(indexBuffer, attachment, "Index buffer");
            }
        }

        private void bindTextures() {
            int sampledTextureCount = pipeline.sampledTextureCount();
            if (sampledTextureCount == 0) {
                return;
            }
            for (int i = 0; i < sampledTextureCount; i++) {
                if (textures[i] == null) {
                    throw new FdxException("Texture slot " + i
                            + " must be set before drawing with Android Vulkan pipeline");
                }
                textureHandles[i] = AndroidVulkanResources.requireTexture(textures[i], attachment,
                        "Texture at slot " + i).handle();
            }
            AndroidVulkanNative.bindTextures(attachment.context, pipeline.handle(), textureHandles, sampledTextureCount);
        }

        private void prepareTextureSlots(int sampledTextureCount) {
            if (textures.length < sampledTextureCount) {
                textures = new AndroidVulkanTextureHandle[sampledTextureCount];
                textureHandles = new long[sampledTextureCount];
            }
            for (int i = 0; i < textures.length; i++) {
                textures[i] = null;
                textureHandles[i] = 0L;
            }
        }

        private void rememberVertexBuffer(int slot, AndroidVulkanBufferHandle buffer) {
            if (slot >= vertexBuffers.length) {
                int nextLength = Math.max(slot + 1, Math.max(1, vertexBuffers.length * 2));
                AndroidVulkanBufferHandle[] grown = new AndroidVulkanBufferHandle[nextLength];
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
                throw new FdxException("Android Vulkan uniform parameter block must be bound before drawing");
            }
            if (uniformDataDirty) {
                AndroidVulkanNative.bindUniforms(attachment.context, pipeline.handle(), uniformBytes,
                        pipeline.resourceBindings().uniformByteCount());
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
            AndroidVulkanResources.requirePipeline(pipeline, attachment, "Render pipeline");
        }

        private ShaderParameterBlock compatibilityUniformBlock() {
            requirePipeline();
            if (!pipeline.resourceBindings().hasUniformBuffer()) {
                throw new FdxException("Active Android Vulkan pipeline has no reflected uniform buffer");
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
            return new FdxException("Android Vulkan named uniform '" + name
                    + "' is not portable; bind a reflected ShaderParameterBlock");
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
     * Represents an android vulkan buffer handle.
     *
     * @author xpenatan
     */
    private static final class AndroidVulkanBufferHandle implements Buffer {
        private final AndroidVulkanGraphicsAttachment attachment;
        private final long handle;
        private final int size;
        private final BufferUsage usage;
        private boolean disposed;

        AndroidVulkanBufferHandle(AndroidVulkanGraphicsAttachment attachment, long handle, int size,
                BufferUsage usage) {
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
                AndroidVulkanNative.destroyBuffer(handle);
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

    /**
     * Represents an android vulkan texture handle.
     *
     * @author xpenatan
     */
    private static final class AndroidVulkanTextureHandle implements Texture {
        private final AndroidVulkanGraphicsAttachment attachment;
        private final long handle;
        private final AndroidVulkanTextureViewHandle view;
        private final int width;
        private final int height;
        private final TextureFormat format;
        private final TextureUsage usage;
        private boolean disposed;

        AndroidVulkanTextureHandle(AndroidVulkanGraphicsAttachment attachment, long handle, int width, int height,
                TextureFormat format, TextureUsage usage) {
            this.attachment = attachment;
            this.handle = handle;
            this.view = new AndroidVulkanTextureViewHandle(this);
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
         * Returns the default texture view.
         *
         * @return the default texture view
         */
        @Override
        public TextureView view() {
            return view;
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
                AndroidVulkanNative.destroyTexture(handle);
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

    /**
     * Represents an android vulkan graphics frame.
     *
     * @author xpenatan
     */
    private static final class AndroidVulkanGraphicsFrame implements GraphicsFrame {
        private final AndroidVulkanGraphicsAttachment attachment;
        private final CommandEncoder commandEncoder;
        private final FrameBuffer frameBuffer;
        private final TextureView colorAttachment;

        AndroidVulkanGraphicsFrame(AndroidVulkanGraphicsAttachment attachment, CommandEncoder commandEncoder,
                FrameBuffer frameBuffer, TextureView colorAttachment) {
            this.attachment = attachment;
            this.commandEncoder = commandEncoder;
            this.frameBuffer = frameBuffer;
            this.colorAttachment = colorAttachment;
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
     * Represents an android vulkan frame buffer.
     *
     * @author xpenatan
     */
    private static final class AndroidVulkanFrameBuffer implements FrameBuffer {
        private final AndroidVulkanGraphicsAttachment attachment;
        private final TextureView colorAttachment;

        AndroidVulkanFrameBuffer(AndroidVulkanGraphicsAttachment attachment, TextureView colorAttachment) {
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
            return attachment.surfaceFormat;
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
         * Returns the read pixels RGBA8.
         *
         * @return the read pixels RGBA8
         */
        @Override
        public ByteBuffer readPixelsRgba8() {
            return attachment.readPixelsRgba8();
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
     * Represents an android vulkan texture view handle.
     *
     * @author xpenatan
     */
    private static final class AndroidVulkanTextureViewHandle implements TextureView {
        private final AndroidVulkanGraphicsAttachment attachment;
        private final AndroidVulkanTextureHandle texture;

        AndroidVulkanTextureViewHandle(AndroidVulkanGraphicsAttachment attachment) {
            this.attachment = attachment;
            this.texture = null;
        }

        AndroidVulkanTextureViewHandle(AndroidVulkanTextureHandle texture) {
            this.attachment = null;
            this.texture = texture;
        }

        long textureHandle() {
            return texture != null ? texture.handle() : 0L;
        }

        @Override
        public int width() {
            return texture != null ? texture.width() : attachment.width;
        }

        @Override
        public int height() {
            return texture != null ? texture.height() : attachment.height;
        }

        /**
         * Returns the format.
         *
         * @return the format
         */
        @Override
        public TextureFormat format() {
            return texture != null ? texture.format() : attachment.surfaceFormat;
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
     * Represents an android vulkan shader module handle.
     *
     * @author xpenatan
     */
    private static final class AndroidVulkanShaderModuleHandle implements ShaderModule {
        private final AndroidVulkanGraphicsAttachment attachment;
        private final long handle;
        private final ShaderReflection reflection;
        private boolean disposed;

        AndroidVulkanShaderModuleHandle(AndroidVulkanGraphicsAttachment attachment, long handle,
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
            return ShaderLanguage.SPIRV;
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
                AndroidVulkanNative.destroyShaderModule(handle);
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

    /**
     * Represents an android vulkan render pipeline handle.
     *
     * @author xpenatan
     */
    private static final class AndroidVulkanRenderPipelineHandle implements RenderPipeline {
        private final AndroidVulkanGraphicsAttachment attachment;
        private final long handle;
        private final PrimitiveTopology primitiveTopology;
        private final int sampledTextureCount;
        private final ShaderRenderBindings resourceBindings;
        private final int uniformDescriptorSetIndex;
        private final RenderTargetLayout targetLayout;
        private boolean disposed;

        AndroidVulkanRenderPipelineHandle(AndroidVulkanGraphicsAttachment attachment, long handle,
                PrimitiveTopology primitiveTopology, int sampledTextureCount,
                ShaderRenderBindings resourceBindings,
                int uniformDescriptorSetIndex, RenderTargetLayout targetLayout) {
            this.attachment = attachment;
            this.handle = handle;
            this.primitiveTopology = primitiveTopology;
            this.sampledTextureCount = sampledTextureCount;
            this.resourceBindings = resourceBindings;
            this.uniformDescriptorSetIndex = uniformDescriptorSetIndex;
            this.targetLayout = targetLayout;
        }

        long handle() {
            return handle;
        }

        PrimitiveTopology primitiveTopology() {
            return primitiveTopology;
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

        int uniformDescriptorSetIndex() {
            return uniformDescriptorSetIndex;
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
                AndroidVulkanNative.destroyRenderPipeline(handle);
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
        if (count == 0) {
            return new int[0];
        }
        int[] locations = new int[count];
        int out = 0;
        for (int i = 0; i < layouts.length; i++) {
            VertexAttribute[] attributes = layouts[i].attributes();
            for (int j = 0; j < attributes.length; j++) {
                locations[out++] = attributes[j].location();
            }
        }
        return locations;
    }

    private static int[] attributeFormats(VertexLayout[] layouts) {
        int count = attributeCount(layouts);
        if (count == 0) {
            return new int[0];
        }
        int[] formats = new int[count];
        int out = 0;
        for (int i = 0; i < layouts.length; i++) {
            VertexAttribute[] attributes = layouts[i].attributes();
            for (int j = 0; j < attributes.length; j++) {
                formats[out++] = toNativeFormat(attributes[j].format());
            }
        }
        return formats;
    }

    private static int[] attributeOffsets(VertexLayout[] layouts) {
        int count = attributeCount(layouts);
        if (count == 0) {
            return new int[0];
        }
        int[] offsets = new int[count];
        int out = 0;
        for (int i = 0; i < layouts.length; i++) {
            VertexAttribute[] attributes = layouts[i].attributes();
            for (int j = 0; j < attributes.length; j++) {
                offsets[out++] = attributes[j].offset();
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
                return 100;
            case FLOAT32X2:
                return 103;
            case FLOAT32X3:
                return 106;
            case UNORM8X4:
                return 37;
            case FLOAT32X4:
            default:
                return 109;
        }
    }

    private static int toNativeTextureFormat(TextureFormat format) {
        if (format == TextureFormat.RGBA8_UNORM) {
            return 37;
        }
        if (format == TextureFormat.RGBA8_UNORM_SRGB) {
            return 43;
        }
        if (format == TextureFormat.BGRA8_UNORM) {
            return 44;
        }
        if (format == TextureFormat.BGRA8_UNORM_SRGB) {
            return 50;
        }
        return 0;
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

    private static TextureFormat toCommonFormat(int format) {
        switch (format) {
            case 44:
                return TextureFormat.BGRA8_UNORM;
            case 50:
                return TextureFormat.BGRA8_UNORM_SRGB;
            case 37:
                return TextureFormat.RGBA8_UNORM;
            case 43:
                return TextureFormat.RGBA8_UNORM_SRGB;
            default:
                return TextureFormat.UNKNOWN;
        }
    }
}
