package io.github.libfdx.backend.desktopc;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.BlendComponent;
import io.github.libfdx.graphics.BlendFactor;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.ComputePass;
import io.github.libfdx.graphics.ComputePassDescriptor;
import io.github.libfdx.graphics.ComputePipeline;
import io.github.libfdx.graphics.ComputePipelineDescriptor;
import io.github.libfdx.graphics.DepthStencilState;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.FrontFace;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.GraphicsLimits;
import io.github.libfdx.graphics.GraphicsProviderSupport;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.graphics.NativeWindowPlatform;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassColorAttachment;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPassDepthStencilAttachment;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.Sampler;
import io.github.libfdx.graphics.StencilFaceState;
import io.github.libfdx.graphics.StencilOperation;
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
import io.github.libfdx.graphics.internal.TextureUploads;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptors;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
import io.github.libfdx.graphics.shader.runtime.ResolvedShaderPass;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationPhase;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceSet;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceValueKind;
import io.github.libfdx.graphics.shader.target.RuntimeShaderTargetCompiler;
import io.github.libfdx.graphics.shader.target.ShaderArtifactStage;
import io.github.libfdx.graphics.shader.target.ShaderCompilerRegistry;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.graphics.shader.target.ShaderTargetArtifact;
import io.github.libfdx.graphics.shader.target.ShaderVerificationRequirement;
import io.github.libfdx.graphics.vulkan.VulkanConfiguration;
import io.github.libfdx.graphics.vulkan.VulkanProvider;
import io.github.libfdx.graphics.vulkan.internal.VulkanShaderLayoutValidator;
import io.github.libfdx.math.ClipDepthRange;
import io.github.libfdx.runtime.core.RuntimeCore;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Provides native Vulkan rendering and bounded background shader preparation.
 *
 * @author xpenatan
 */
public final class DesktopCVulkanProvider
        implements GraphicsAttachmentProvider, GraphicsProviderSupport {
    public static final ProviderId ID = VulkanProvider.ID;
    private static final int MAX_UNIFORM_BYTE_COUNT = 64 * 1024;
    private static final GraphicsCapabilities CAPABILITIES =
            GraphicsCapabilities.builder()
                    .profile(ShaderProfile.PORTABLE_WEBGL2)
                    .profile(ShaderProfile.PORTABLE_WEBGPU)
                    .profile(ShaderProfile.NATIVE)
                    .feature(GraphicsFeature.INDEXED_DRAW)
                    .feature(GraphicsFeature.INSTANCED_DRAW)
                    .feature(GraphicsFeature.DEPTH_STENCIL_ATTACHMENTS)
                    .feature(GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS)
                    .feature(GraphicsFeature.TEXTURE_MIP_LEVELS)
                    .feature(GraphicsFeature.TEXTURE_MIN_MAG_FILTERS)
                    .feature(GraphicsFeature.ALPHA_BLEND_CONTROL)
                    .feature(GraphicsFeature.COMPLETE_RENDER_PIPELINE_STATE)
                    .feature(GraphicsFeature.MULTIPLE_COLOR_ATTACHMENTS)
                    .feature(GraphicsFeature.MULTISAMPLE)
                    .feature(GraphicsFeature.RESOLVE_ATTACHMENTS)
                    .feature(GraphicsFeature.COMPUTE)
                    .feature(GraphicsFeature.STORAGE_BUFFERS)
                    .feature(GraphicsFeature.STORAGE_TEXTURES)
                    .feature(GraphicsFeature.ATOMICS)
                    .colorFormats(
                            TextureFormat.RGBA8_UNORM,
                            TextureFormat.RGBA8_UNORM_SRGB,
                            TextureFormat.BGRA8_UNORM,
                            TextureFormat.BGRA8_UNORM_SRGB,
                            TextureFormat.RGBA16_FLOAT,
                            TextureFormat.R32_FLOAT)
                    .filterableColorFormats(
                            TextureFormat.RGBA8_UNORM,
                            TextureFormat.RGBA8_UNORM_SRGB,
                            TextureFormat.BGRA8_UNORM,
                            TextureFormat.BGRA8_UNORM_SRGB,
                            TextureFormat.RGBA16_FLOAT)
                    .blendableColorFormats(
                            TextureFormat.RGBA8_UNORM,
                            TextureFormat.RGBA8_UNORM_SRGB,
                            TextureFormat.BGRA8_UNORM,
                            TextureFormat.BGRA8_UNORM_SRGB,
                            TextureFormat.RGBA16_FLOAT)
                    .resolveFormats(
                            TextureFormat.RGBA8_UNORM,
                            TextureFormat.RGBA8_UNORM_SRGB,
                            TextureFormat.BGRA8_UNORM,
                            TextureFormat.BGRA8_UNORM_SRGB,
                            TextureFormat.RGBA16_FLOAT,
                            TextureFormat.R32_FLOAT)
                    .depthStencilFormats(
                            TextureFormat.DEPTH32_FLOAT, TextureFormat.DEPTH24_STENCIL8)
                    .renderedTextureOrigin(TextureOrigin.TOP_LEFT)
                    // Vulkan clips depth to 0..w.
                    .clipDepthRange(ClipDepthRange.ZERO_TO_ONE)
                    .sampleCounts(1, 4)
                    .limits(
                            GraphicsLimits.builder()
                                    .maxBindGroups(2)
                                    .maxStorageBuffersPerStage(4)
                                    .maxStorageTexturesPerStage(4)
                                    .maxStorageBufferBindingSize(128L * 1024 * 1024)
                                    .maxComputeWorkgroupsPerDimension(65535)
                                    .maxComputeWorkgroupSize(128, 128, 64)
                                    .maxComputeInvocationsPerWorkgroup(128)
                                    .maxComputeWorkgroupStorageSize(16384)
                                    .maxBindingsPerGroup(32)
                                    .maxUniformBuffersPerStage(1)
                                    .maxSampledTexturesPerStage(16)
                                    .maxSamplersPerStage(16)
                                    .maxColorAttachments(8)
                                    .maxVertexBuffers(4)
                                    .maxVertexAttributes(16)
                                    .maxUniformBufferBindingSize(MAX_UNIFORM_BYTE_COUNT)
                                    .build())
                    .build();

    private VulkanConfiguration configuration = new VulkanConfiguration();

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
        if (nativeWindow == null
                || nativeWindow.platform() != NativeWindowPlatform.GLFW
                || nativeWindow.backendHandle() == 0L) {
            throw new FdxException("desktop C Vulkan requires a GLFW native window");
        }
        GraphicsContext sharedContext = environment.sharedContext();
        if (sharedContext != null) {
            if (!ID.equals(sharedContext.providerId())) {
                throw new FdxException(
                        "Cannot share a non-Vulkan graphics context with desktop C Vulkan");
            }
            throw new FdxException(
                    "desktop C Vulkan does not currently support shared graphics contexts");
        }
        String supportFailure = DesktopCVulkan.supportFailureReason();
        if (supportFailure != null) {
            throw new FdxException(supportFailure);
        }
        return new Attachment(
                configuration,
                nativeWindow.backendHandle(),
                environment.display().framebufferWidth(),
                environment.display().framebufferHeight());
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
        return DesktopCVulkan.supportFailureReason();
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
     * Sets the configuration and returns this desktop C vulkan provider.
     *
     * @param configuration the configuration
     * @return this desktop C vulkan provider for chaining
     */
    public DesktopCVulkanProvider configuration(VulkanConfiguration configuration) {
        this.configuration = configuration != null ? configuration : new VulkanConfiguration();
        return this;
    }

    /**
     * Sets the v sync and returns this desktop C vulkan provider.
     *
     * @param vSync the v sync
     * @return this desktop C vulkan provider for chaining
     */
    public DesktopCVulkanProvider vSync(boolean vSync) {
        configuration.vSync(vSync);
        return this;
    }

    /**
     * Sets the validation and returns this desktop C vulkan provider.
     *
     * @param validation the validation
     * @return this desktop C vulkan provider for chaining
     */
    public DesktopCVulkanProvider validation(boolean validation) {
        configuration.validation(validation);
        return this;
    }

    /**
     * Sets the frames in flight and returns this desktop C vulkan provider.
     *
     * @param framesInFlight the frames in flight
     * @return this desktop C vulkan provider for chaining
     */
    public DesktopCVulkanProvider framesInFlight(int framesInFlight) {
        configuration.framesInFlight(framesInFlight);
        return this;
    }

    /**
     * Represents a desktop C vulkan graphics attachment.
     *
     * @author xpenatan
     */
    private static final class Attachment implements GraphicsAttachment {
        private final long context;
        private final Device device = new Device(this);
        private final Encoder commandEncoder = new Encoder(this);
        private final TextureViewHandle colorAttachment = new TextureViewHandle(this);
        private final SurfaceBuffer frameBuffer = new SurfaceBuffer(this, colorAttachment);
        private final Frame currentFrame =
                new Frame(this, commandEncoder, frameBuffer, colorAttachment);
        private final TextureFormat surfaceFormat;
        private int width;
        private int height;
        private int pendingResizeWidth;
        private int pendingResizeHeight;
        private boolean frameStarted;
        private boolean pendingResize;
        private volatile boolean disposed;

        Attachment(VulkanConfiguration configuration, long windowHandle, int width, int height) {
            VulkanConfiguration actualConfiguration =
                    configuration != null ? configuration : new VulkanConfiguration();
            this.width = width;
            this.height = height;
            context =
                    DesktopCVulkan.create(
                            windowHandle,
                            width,
                            height,
                            actualConfiguration.vSync(),
                            actualConfiguration.preferMailboxPresentMode(),
                            actualConfiguration.framesInFlight());
            surfaceFormat = toCommonFormat(DesktopCVulkan.surfaceFormat(context));
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
            DesktopCVulkan.resize(context, nextWidth, nextHeight);
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

        /** Runs the process events step. */
        @Override
        public void processEvents() {}

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
                throw new FdxException("desktop C Vulkan frame is already started");
            }
            commandEncoder.beginFrame();
            frameStarted = DesktopCVulkan.beginFrame(context);
            if (frameStarted) {
                applyPendingResizeDimensions();
            }
            return frameStarted;
        }

        /** Ends frame. */
        @Override
        public void endFrame() {
            if (!frameStarted) {
                return;
            }
            commandEncoder.ensurePassesEnded();
            try {
                DesktopCVulkan.endFrame(context);
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
            DesktopCVulkan.clear(context, red, green, blue, alpha);
        }

        ByteBuffer readPixelsRgba8() {
            ensureFrameStarted("read pixels");
            int byteCount = width * height * 4;
            ByteBuffer pixels = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
            try {
                DesktopCVulkan.readPixelsRgba8(context, pixels, byteCount);
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

        /** Releases resources held by this instance. */
        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            device.closePreparation();
            disposed = true;
            try {
                DesktopCVulkan.destroy(context);
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
                throw new FdxException(
                        "Cannot " + operation + " after the desktop C Vulkan context is disposed");
            }
        }

        private void ensureFrameStarted(String operation) {
            ensureNotDisposed(operation);
            if (!frameStarted) {
                throw new FdxException(
                        "Cannot " + operation + " outside an active desktop C Vulkan frame");
            }
        }
    }

    /**
     * Represents a desktop C vulkan graphics device.
     *
     * @author xpenatan
     */
    private static final class Device implements GraphicsDevice {
        private static final ShaderPreparationCapabilities PREPARATION_CAPABILITIES =
                new ShaderPreparationCapabilities(
                        ShaderPreparationCapabilities.Execution.WORKERS,
                        ShaderPreparationCapabilities.Execution.WORKERS,
                        true,
                        2,
                        false,
                        false);
        private final Attachment attachment;
        private final Thread owner = Thread.currentThread();
        private final ArrayList<Preparation> preparations = new ArrayList<>();
        private ShaderCompilerRegistry preparationCompilers;
        private DesktopCAssetExecutor preparationWorkers;
        private int activePreparations;

        Device(Attachment attachment) {
            this.attachment = attachment;
        }

        @Override
        public ShaderPreparationCapabilities shaderPreparationCapabilities() {
            return PREPARATION_CAPABILITIES;
        }

        @Override
        public ShaderPreparationOperation prepareRenderPipeline(ShaderPipelineRequest request) {
            requirePreparationOwner();
            attachment.ensureNotDisposed("prepare a pipeline");
            if (preparationWorkers == null) {
                preparationCompilers =
                        ShaderCompilerRegistry.builder()
                                .compiler(
                                        new RuntimeShaderTargetCompiler(
                                                RuntimeCore.shaderCompiler()))
                                .build();
                preparationWorkers = new DesktopCAssetExecutor(2, 256);
            }
            Preparation operation = new Preparation(Objects.requireNonNull(request, "request"));
            preparations.add(operation);
            DesktopCVulkan.retain(attachment.context);
            activePreparations++;
            try {
                if (!preparationWorkers.submit(operation::run))
                    throw new FdxException("Vulkan shader preparation queue is full");
            } catch (RuntimeException | Error failure) {
                preparations.remove(operation);
                DesktopCVulkan.destroy(attachment.context);
                activePreparations--;
                throw failure;
            }
            return operation;
        }

        private void requirePreparationOwner() {
            if (Thread.currentThread() != owner)
                throw new FdxException("desktop C preparation requires the owner thread");
        }

        private void closePreparation() {
            requirePreparationOwner();
            while (!preparations.isEmpty()) preparations.get(preparations.size() - 1).dispose();
            if (preparationWorkers != null) preparationWorkers.dispose();
            // Java workers share the C event-loop thread. Let accepted tasks release
            // their context before entering the native wait, which blocks that thread.
            while (activePreparations != 0) Thread.yield();
            // Final context disposal may wait; ordinary preparation cancellation never does.
            // The backend must retain the native window until all device workers have left.
            DesktopCVulkan.waitPreparations(attachment.context);
        }

        /** Workers prepare an unpublished pipeline; only its owner may publish it. */
        private final class Preparation implements ShaderPreparationOperation {
            private final ShaderPipelineRequest request;
            private volatile ShaderPreparationPhase phase = ShaderPreparationPhase.QUEUED;
            private PipelineHandle pipeline;
            private Throwable failure;
            private volatile boolean done, cancelled, disposed;
            private boolean finished;

            Preparation(ShaderPipelineRequest request) {
                this.request = request;
            }

            private void run() {
                ShaderModule module = null;
                try {
                    requireActive();
                    phase = ShaderPreparationPhase.SOURCE;
                    ShaderModuleDescriptor source = request.sourceDescriptor();
                    requireActive();
                    phase = ShaderPreparationPhase.TRANSLATION;
                    ShaderTarget target = ShaderTarget.VULKAN_SPIRV;
                    source =
                            ShaderModuleDescriptors.requireTarget(
                                    source,
                                    target.id(),
                                    target.format(),
                                    target.environment(),
                                    preparationCompilers,
                                    ShaderVerificationRequirement.PROVIDER_PIPELINE,
                                    "desktop C Vulkan");
                    requireActive();
                    phase = ShaderPreparationPhase.COMPILATION;
                    module = createShaderModule(source);
                    ((ShaderHandle) module).preparing = true;
                    phase = ShaderPreparationPhase.PIPELINE;
                    pipeline =
                            (PipelineHandle)
                                    createRenderPipeline(request.pipelineDescriptor(module));
                    pipeline.published = false;
                } catch (Throwable error) {
                    failure = error;
                } finally {
                    try {
                        if (module != null) module.dispose();
                    } catch (Throwable error) {
                        if (failure == null) failure = error;
                        else if (failure != error) failure.addSuppressed(error);
                    }
                    try {
                        synchronized (this) {
                            try {
                                if (failure != null || cancelled || disposed) discard();
                            } catch (Throwable error) {
                                if (failure == null) failure = error;
                                else if (failure != error) failure.addSuppressed(error);
                            } finally {
                                phase = ShaderPreparationPhase.PUBLICATION;
                                done = true;
                            }
                        }
                    } finally {
                        DesktopCVulkan.destroy(attachment.context);
                        activePreparations--;
                    }
                }
            }

            private void requireActive() {
                if (cancelled || disposed || attachment.isDisposed()) {
                    throw new CancellationException("desktop C Vulkan preparation cancelled");
                }
            }

            @Override
            public boolean isDone() {
                return done;
            }

            @Override
            public ShaderPreparationPhase phase() {
                return phase;
            }

            @Override
            public synchronized ShaderPreparedResult finish() {
                requirePreparationOwner();
                if (!done || finished)
                    throw new FdxException("desktop C Vulkan preparation cannot be published now");
                finished = true;
                try {
                    requireActive();
                    if (failure instanceof Error error) throw error;
                    if (failure instanceof RuntimeException error) throw error;
                    if (failure != null)
                        throw new FdxException("desktop C Vulkan preparation failed", failure);
                    ShaderPreparedResult result =
                            new ShaderPreparedResult(
                                    ResolvedShaderPass.of(
                                            request.passId(),
                                            pipeline,
                                            pipeline.resourceBindings().layout(),
                                            request.providerRevision()),
                                    pipeline);
                    pipeline.published = true;
                    pipeline = null;
                    return result;
                } finally {
                    discard();
                    preparations.remove(this);
                }
            }

            @Override
            public synchronized void cancel() {
                requirePreparationOwner();
                cancelled = true;
                if (done) discard();
            }

            private void discard() {
                if (pipeline != null) {
                    pipeline.dispose();
                    pipeline = null;
                }
            }

            @Override
            public synchronized void dispose() {
                requirePreparationOwner();
                if (disposed) return;
                cancel();
                disposed = true;
                preparations.remove(this);
            }

            @Override
            public boolean isDisposed() {
                return disposed;
            }
        }

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
            return new BufferHandle(
                    attachment,
                    DesktopCVulkan.createBuffer(
                            attachment.context,
                            descriptor.size(),
                            toNativeBufferUsage(descriptor.usage())),
                    descriptor.size(),
                    descriptor.usage());
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
            BufferHandle vulkanBuffer = Resources.requireBuffer(buffer, attachment, "Buffer");
            if (data.remaining() > vulkanBuffer.size()) {
                throw new FdxException("Buffer data is larger than the destination buffer");
            }
            ByteBuffer source = data.position() == 0 ? data : data.slice();
            DesktopCVulkan.writeBuffer(vulkanBuffer.handle(), source, source.remaining());
        }

        @Override
        public ByteBuffer readBuffer(Buffer buffer, int offset, int size) {
            BufferHandle source = Resources.requireBuffer(buffer, attachment, "Readback buffer");
            if (source.usage() != BufferUsage.READBACK
                    || offset < 0
                    || size < 0
                    || offset > source.size() - size) {
                throw new FdxException("Invalid Vulkan buffer readback range or usage");
            }
            ByteBuffer output = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
            if (size > 0) DesktopCVulkan.readBuffer(source.handle(), offset, output, size);
            return output;
        }

        @Override
        public ComputePipeline createComputePipeline(ComputePipelineDescriptor descriptor) {
            attachment.ensureNotDisposed("create a compute pipeline");
            if (descriptor == null) throw new FdxException("Compute descriptor cannot be null");
            descriptor.validate(capabilities());
            if (!(descriptor.shaderModule() instanceof ComputeModule module)
                    || module.attachment != attachment
                    || module.isDisposed()) {
                throw new FdxException("Invalid Vulkan compute shader module or device");
            }
            return new ComputePipelineHandle(attachment, module, descriptor);
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
            return new TextureHandle(
                    attachment,
                    DesktopCVulkan.createTexture(
                            attachment.context,
                            descriptor.width(),
                            descriptor.height(),
                            toNativeTextureFormat(descriptor.format()),
                            toNativeWrap(descriptor.wrapS()),
                            toNativeWrap(descriptor.wrapT()),
                            toNativeFilter(descriptor.minFilter()),
                            toNativeFilter(descriptor.magFilter()),
                            descriptor.mipmapFilter().ordinal(),
                            descriptor.mipLevelCount(),
                            descriptor.sampleCount(),
                            (descriptor.usage().sampled() ? 1 : 0)
                                    | (descriptor.usage().renderAttachment() ? 2 : 0)
                                    | (descriptor.usage().storage() ? 4 : 0)),
                    descriptor.width(),
                    descriptor.height(),
                    descriptor.format(),
                    descriptor.usage(),
                    descriptor.mipLevelCount(),
                    descriptor.sampleCount());
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
            TextureHandle vulkanTexture = Resources.requireTexture(texture, attachment, "Texture");
            int byteCount =
                    vulkanTexture.width()
                            * vulkanTexture.height()
                            * vulkanTexture.format().bytesPerPixel();
            if (data.remaining() != byteCount) {
                throw new FdxException(
                        "desktop C Vulkan texture upload expects " + byteCount + " bytes");
            }
            ByteBuffer source = data.position() == 0 ? data : data.slice();
            DesktopCVulkan.writeTexture(vulkanTexture.handle(), 0, source, source.remaining());
        }

        @Override
        public void writeTextureMipLevels(Texture texture, ByteBuffer... levels) {
            TextureHandle target = Resources.requireTexture(texture, attachment, "Texture");
            TextureUploads.validate(texture, levels);
            for (int level = 0; level < levels.length; level++) {
                ByteBuffer source = levels[level].slice();
                DesktopCVulkan.writeTexture(target.handle(), level, source, source.remaining());
            }
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
            if (descriptor.hasSource(ShaderLanguage.WGSL) && !descriptor.reflection().complete()) {
                descriptor =
                        ShaderModuleDescriptors.requireTarget(
                                        descriptor, ShaderTarget.WGPU_WGSL, "Vulkan reflection")
                                .entryPoints(
                                        descriptor.vertexEntryPoint(),
                                        descriptor.fragmentEntryPoint());
            }
            descriptor =
                    ShaderModuleDescriptors.requireTarget(
                            descriptor, ShaderTarget.VULKAN_SPIRV, "desktop C Vulkan");
            if (ShaderModuleDescriptors.computeOnly(descriptor.reflection())) {
                shaderTargetSupport().require(descriptor.targetArtifact());
                return new ComputeModule(attachment, descriptor);
            }
            if (descriptor.targetArtifact() != null) {
                shaderTargetSupport().require(descriptor.targetArtifact());
                VulkanShaderLayoutValidator.requireArtifact(descriptor.targetArtifact());
            }
            if (!descriptor.hasSource(ShaderLanguage.SPIRV)) {
                throw new FdxException("desktop C Vulkan requires SPIR-V shader modules");
            }
            return new ShaderHandle(
                    attachment,
                    DesktopCVulkan.createShaderModule(
                            attachment.context,
                            descriptor.spirvVertexWords(),
                            descriptor.spirvFragmentWords(),
                            descriptor.vertexEntryPoint(),
                            descriptor.fragmentEntryPoint()),
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
            attachment.ensureNotDisposed("create a render pipeline");
            if (descriptor == null) {
                throw new FdxException("RenderPipelineDescriptor cannot be null");
            }
            ShaderHandle shaderModule =
                    Resources.requireShaderModule(
                            descriptor.shaderModule(), attachment, "Shader module");
            descriptor.validate(capabilities());
            ShaderRenderBindings resourceBindings = ShaderRenderBindings.from(descriptor);
            VulkanShaderLayoutValidator.requireRenderLayout(resourceBindings);
            boolean uniformBufferEnabled = resourceBindings.hasUniformBuffer();
            VertexLayout[] vertexLayouts = descriptor.vertexLayouts();
            return new PipelineHandle(
                    attachment,
                    DesktopCVulkan.createRenderPipeline(
                            attachment.context,
                            shaderModule.handle(),
                            toNativeTopology(descriptor.primitiveTopology()),
                            vertexStrides(vertexLayouts),
                            vertexStepModes(vertexLayouts),
                            attributeBindings(vertexLayouts),
                            attributeLocations(vertexLayouts),
                            attributeFormats(vertexLayouts),
                            attributeOffsets(vertexLayouts),
                            descriptor.sampledTextureCount(),
                            uniformBufferEnabled,
                            pipelineState(descriptor)),
                    descriptor.primitiveTopology(),
                    descriptor.sampledTextureCount(),
                    resourceBindings,
                    resourceBindings.uniformSetIndex(),
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

    private static final class ComputeModule implements ShaderModule {
        final Attachment attachment;
        final ShaderTargetArtifact artifact;
        final ShaderReflection reflection;
        private boolean disposed;

        ComputeModule(Attachment attachment, ShaderModuleDescriptor descriptor) {
            this.attachment = attachment;
            artifact = descriptor.targetArtifact();
            reflection = descriptor.reflection();
            if (artifact == null)
                throw new FdxException("Vulkan compute requires translated stage artifacts");
        }

        @Override
        public ShaderReflection reflection() {
            return reflection;
        }

        @Override
        public ShaderLanguage language() {
            return ShaderLanguage.SPIRV;
        }

        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

        @Override
        public void dispose() {
            disposed = true;
        }
    }

    private static final class ComputePipelineHandle implements ComputePipeline {
        final Attachment attachment;
        final ShaderResourceLayout layout;
        final long handle;
        private boolean disposed;

        ComputePipelineHandle(
                Attachment attachment, ComputeModule module, ComputePipelineDescriptor descriptor) {
            this.attachment = attachment;
            layout = descriptor.resourceLayout();
            int[] bindings = new int[layout.bindingCount() * 3];
            for (int i = 0; i < layout.bindingCount(); i++) {
                ShaderBinding binding = layout.binding(i);
                var remap =
                        module.artifact
                                .translatedInterface()
                                .findBinding(
                                        ShaderArtifactStage.COMPUTE,
                                        descriptor.entryPoint(),
                                        binding.group(),
                                        binding.binding());
                if (remap == null || remap.targetCount() != 1)
                    throw new FdxException("Invalid Vulkan compute resource remap");
                bindings[i * 3] = remap.target(0).group();
                bindings[i * 3 + 1] = remap.target(0).binding();
                bindings[i * 3 + 2] =
                        switch (binding.resourceKind()) {
                            case STORAGE_BUFFER -> 7;
                            case UNIFORM_BUFFER -> 6;
                            case STORAGE_TEXTURE -> 3;
                            default ->
                                    throw new FdxException(
                                            "Unsupported Vulkan compute resource: "
                                                    + binding.resourceKind());
                        };
            }
            String entry = null;
            for (var remap : module.artifact.translatedInterface().entryPoints()) {
                if (remap.sourceName().equals(descriptor.entryPoint())) entry = remap.targetName();
            }
            var stage =
                    entry == null ? null : module.artifact.find(ShaderArtifactStage.COMPUTE, entry);
            if (stage == null)
                throw new FdxException(
                        "Missing Vulkan compute entry point " + descriptor.entryPoint());
            byte[] payload = stage.payload();
            int[] words = new int[payload.length / 4];
            ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(words);
            handle = DesktopCVulkan.createCompute(attachment.context, words, entry, bindings);
            if (handle == 0) throw new FdxException("Could not create Vulkan compute pipeline");
        }

        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

        @Override
        public void dispose() {
            if (disposed) return;
            disposed = true;
            if (!attachment.isDisposed()) DesktopCVulkan.destroyRenderPipeline(handle, true);
        }
    }

    private static final class ComputePassHandle implements ComputePass {
        private final Attachment attachment;
        private final ShaderResourceSet[] sets = new ShaderResourceSet[2];
        private final long[] resources = new long[64 * 3];
        private ComputePipelineHandle pipeline;
        private boolean ended = true;

        ComputePassHandle(Attachment attachment) {
            this.attachment = attachment;
        }

        void begin() {
            ended = false;
            pipeline = null;
            java.util.Arrays.fill(sets, null);
        }

        private void requireOpen() {
            attachment.ensureFrameStarted("record compute commands");
            if (ended) throw new FdxException("Vulkan compute pass has ended");
        }

        @Override
        public void setPipeline(ComputePipeline value) {
            requireOpen();
            if (!(value instanceof ComputePipelineHandle candidate)
                    || candidate.attachment != attachment
                    || candidate.isDisposed()) {
                throw new FdxException("Invalid Vulkan compute pipeline or device");
            }
            pipeline = candidate;
            java.util.Arrays.fill(sets, null);
        }

        @Override
        public void setResourceSet(ShaderResourceSet value) {
            requireOpen();
            if (pipeline == null
                    || value == null
                    || value.group() < 0
                    || value.group() >= sets.length
                    || !pipeline.layout.physicalHash().equals(value.layout().physicalHash())) {
                throw new FdxException("Mismatched Vulkan compute resources");
            }
            sets[value.group()] = value;
        }

        @Override
        public void dispatch(int x, int y, int z) {
            requireOpen();
            validateDispatch(x, y, z, attachment.device.capabilities().limits());
            if (pipeline == null || pipeline.isDisposed())
                throw new FdxException("No live Vulkan compute pipeline");
            for (int i = 0; i < pipeline.layout.bindingCount(); i++) {
                ShaderBinding binding = pipeline.layout.binding(i);
                ShaderResourceSet set = sets[binding.group()];
                if (set == null) throw new FdxException("Missing Vulkan compute resource group");
                var value = set.find(binding.binding());
                if (value == null)
                    throw new FdxException("Missing Vulkan compute resource binding");
                if (value.kind() == ShaderResourceValueKind.BUFFER) {
                    BufferHandle buffer =
                            Resources.requireBuffer(value.buffer(), attachment, "Compute buffer");
                    resources[i * 3] = buffer.handle();
                    resources[i * 3 + 1] = value.offset();
                    resources[i * 3 + 2] = value.size();
                } else if (value.kind() == ShaderResourceValueKind.TEXTURE) {
                    TextureHandle texture =
                            Resources.requireTexture(value.texture(), attachment, "Compute image");
                    if (!texture.usage().storage())
                        throw new FdxException("Compute image requires storage usage");
                    resources[i * 3] = texture.handle();
                    resources[i * 3 + 1] = 0;
                    resources[i * 3 + 2] = 0;
                } else {
                    throw new FdxException(
                            "Vulkan compute requires explicit buffer or texture resources");
                }
            }
            DesktopCVulkan.dispatchCompute(
                    attachment.context,
                    pipeline.handle,
                    resources,
                    pipeline.layout.bindingCount(),
                    x,
                    y,
                    z);
        }

        @Override
        public void end() {
            requireOpen();
            ended = true;
            pipeline = null;
            java.util.Arrays.fill(sets, null);
        }

        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }
    }

    private static final class Resources {
        private Resources() {}

        static BufferHandle requireBuffer(Buffer value, Attachment attachment, String name) {
            if (value == null) {
                throw new FdxException(name + " cannot be null");
            }
            if (!(value instanceof BufferHandle handle)) {
                throw new FdxException(name + " belongs to another graphics provider");
            }
            requireOwner(handle.attachment, attachment, name);
            if (handle.isDisposed()) {
                throw new FdxException(name + " has been disposed");
            }
            return handle;
        }

        static TextureHandle requireTexture(Texture value, Attachment attachment, String name) {
            if (value == null) {
                throw new FdxException(name + " cannot be null");
            }
            if (!(value instanceof TextureHandle handle)) {
                throw new FdxException(name + " belongs to another graphics provider");
            }
            requireOwner(handle.attachment, attachment, name);
            if (handle.isDisposed()) {
                throw new FdxException(name + " has been disposed");
            }
            return handle;
        }

        static ShaderHandle requireShaderModule(
                ShaderModule value, Attachment attachment, String name) {
            if (value == null) {
                throw new FdxException(name + " cannot be null");
            }
            if (!(value instanceof ShaderHandle handle)) {
                throw new FdxException(name + " belongs to another graphics provider");
            }
            requireOwner(handle.attachment, attachment, name);
            if (handle.isDisposed()) {
                throw new FdxException(name + " has been disposed");
            }
            return handle;
        }

        static PipelineHandle requirePipeline(
                RenderPipeline value, Attachment attachment, String name) {
            if (value == null) {
                throw new FdxException(name + " cannot be null");
            }
            if (!(value instanceof PipelineHandle handle)) {
                throw new FdxException(name + " belongs to another graphics provider");
            }
            requireOwner(handle.attachment, attachment, name);
            if (handle.isDisposed()) {
                throw new FdxException(name + " has been disposed");
            }
            return handle;
        }

        static TextureViewHandle requireTextureView(
                TextureView value, Attachment attachment, String name) {
            if (value == null) {
                throw new FdxException(name + " cannot be null");
            }
            if (!(value instanceof TextureViewHandle handle)) {
                throw new FdxException(name + " belongs to another graphics provider");
            }
            requireOwner(handle.attachment, attachment, name);
            if (handle.texture != null) requireTexture(handle.texture, attachment, name);
            else if (attachment.disposed || !attachment.frameStarted)
                attachment.ensureFrameStarted("use " + name.toLowerCase());
            return handle;
        }

        private static void requireOwner(Attachment actual, Attachment expected, String name) {
            if (actual != expected) {
                throw new FdxException(name + " belongs to another desktop C Vulkan context");
            }
            if (expected.disposed) expected.ensureNotDisposed("use " + name.toLowerCase());
        }
    }

    /**
     * Represents a desktop C vulkan command encoder.
     *
     * @author xpenatan
     */
    private static final class Encoder implements CommandEncoder {
        private final Attachment attachment;
        private Pass[] renderPasses = new Pass[4];
        private int renderPassCount;
        private final ArrayList<ComputePassHandle> computePasses = new ArrayList<>();
        private int computePassCount;
        private final int[] passState = new int[51];
        private final long[] passTextures = new long[17];
        private final float[] passClears = new float[33];

        Encoder(Attachment attachment) {
            this.attachment = attachment;
        }

        @Override
        public ComputePass beginComputePass(ComputePassDescriptor descriptor) {
            attachment.ensureFrameStarted("begin a compute pass");
            if (descriptor == null) throw new FdxException("Compute descriptor cannot be null");
            ensurePreviousPassEnded();
            if (computePassCount == computePasses.size())
                computePasses.add(new ComputePassHandle(attachment));
            ComputePassHandle pass = computePasses.get(computePassCount++);
            pass.begin();
            return pass;
        }

        @Override
        public void copyBufferToBuffer(
                Buffer source,
                int sourceOffset,
                Buffer destination,
                int destinationOffset,
                int size) {
            attachment.ensureFrameStarted("copy buffers");
            ensurePreviousPassEnded();
            BufferHandle from = Resources.requireBuffer(source, attachment, "Copy source");
            BufferHandle to = Resources.requireBuffer(destination, attachment, "Copy destination");
            if (size < 0
                    || sourceOffset < 0
                    || destinationOffset < 0
                    || sourceOffset > from.size() - size
                    || destinationOffset > to.size() - size
                    || (from == to
                            && sourceOffset < destinationOffset + size
                            && destinationOffset < sourceOffset + size)) {
                throw new FdxException("Invalid Vulkan buffer copy range");
            }
            if (size > 0)
                DesktopCVulkan.copyBuffer(
                        attachment.context,
                        from.handle(),
                        sourceOffset,
                        to.handle(),
                        destinationOffset,
                        size);
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
            RenderPassCompatibility compatibility =
                    descriptor.validate(attachment.device.capabilities());
            RenderTargetLayout targets = compatibility.targetLayout();
            passState[0] = targets.colorAttachmentCount();
            passState[1] = toNativeTextureFormat(targets.depthStencilFormat());
            passState[2] = targets.sampleCount();
            passState[3] = compatibility.width();
            passState[4] = compatibility.height();
            RenderPassColorAttachment[] colors = descriptor.colorAttachments();
            for (int i = 0; i < colors.length; i++) {
                TextureViewHandle view =
                        Resources.requireTextureView(
                                colors[i].view(), attachment, "Color attachment");
                TextureViewHandle resolve =
                        colors[i].resolveView() == null
                                ? null
                                : Resources.requireTextureView(
                                        colors[i].resolveView(), attachment, "Resolve attachment");
                passTextures[i] = view.handle();
                passTextures[8 + i] = resolve == null ? 0 : resolve.handle();
                int offset = 11 + i * 5;
                passState[offset] = toNativeTextureFormat(view.format());
                passState[offset + 1] = view.mipLevel();
                passState[offset + 2] = resolve == null ? 0 : resolve.mipLevel();
                passState[offset + 3] = colors[i].loadOp().isClear() ? 1 : 0;
                passState[offset + 4] = colors[i].storeOp().isStore() ? 0 : 1;
                LoadOp clear = colors[i].loadOp();
                passClears[i * 4] = clear.red();
                passClears[i * 4 + 1] = clear.green();
                passClears[i * 4 + 2] = clear.blue();
                passClears[i * 4 + 3] = clear.alpha();
            }
            RenderPassDepthStencilAttachment depth = descriptor.depthStencilAttachment();
            passTextures[16] = 0;
            passState[5] = 0;
            passState[6] = descriptor.depthClearEnabled() ? 1 : 0;
            passState[7] = 0;
            passState[8] = 2;
            passState[9] = 1;
            passState[10] = 0;
            passClears[32] = descriptor.depthClearValue();
            if (depth != null) {
                TextureViewHandle view =
                        Resources.requireTextureView(depth.view(), attachment, "Depth attachment");
                passTextures[16] = view.handle();
                passState[5] = view.mipLevel();
                passState[6] = depth.depthLoadOp().isClear() ? 1 : 0;
                passState[7] = depth.depthStoreOp().isStore() ? 0 : 1;
                passState[8] = depth.stencilLoadOp().isClear() ? 1 : 0;
                passState[9] = depth.stencilStoreOp().isStore() ? 0 : 1;
                passState[10] = (int) depth.stencilLoadOp().red();
                passClears[32] = depth.depthLoadOp().red();
            } else if (targets.hasDepthStencil()) {
                TextureViewHandle color = (TextureViewHandle) colors[0].view();
                passTextures[16] = color.implicitDepth();
            }
            DesktopCVulkan.beginRenderPass(
                    attachment.context, passState, passTextures, passClears);
            Pass renderPass = nextRenderPass();
            renderPass.begin(compatibility);
            renderPassCount++;
            return renderPass;
        }

        void beginFrame() {
            ensurePassesEnded();
            renderPassCount = 0;
            computePassCount = 0;
        }

        void ensurePassesEnded() {
            for (int i = 0; i < computePassCount; i++) {
                if (!computePasses.get(i).ended)
                    throw new FdxException("Vulkan compute pass must be ended");
            }
            for (int i = 0; i < renderPassCount; i++) {
                if (!renderPasses[i].isEnded()) {
                    throw new FdxException(
                            "desktop C Vulkan render pass must be ended before ending the frame");
                }
            }
        }

        private void ensurePreviousPassEnded() {
            if (computePassCount > 0 && !computePasses.get(computePassCount - 1).ended) {
                throw new FdxException("Previous Vulkan compute pass must be ended");
            }
            if (renderPassCount > 0 && !renderPasses[renderPassCount - 1].isEnded()) {
                throw new FdxException(
                        "Previous desktop C Vulkan render pass must be ended before beginning another"
                                + " pass");
            }
        }

        private Pass nextRenderPass() {
            if (renderPassCount == renderPasses.length) {
                Pass[] grown = new Pass[renderPasses.length * 2];
                System.arraycopy(renderPasses, 0, grown, 0, renderPasses.length);
                renderPasses = grown;
            }
            Pass renderPass = renderPasses[renderPassCount];
            if (renderPass == null) {
                renderPass = new Pass(attachment);
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
     * Represents a desktop C vulkan render pass.
     *
     * @author xpenatan
     */
    private static final class Pass implements RenderPass {
        private final Attachment attachment;
        private final ByteBuffer uniformBytes =
                ByteBuffer.allocateDirect(MAX_UNIFORM_BYTE_COUNT).order(ByteOrder.nativeOrder());
        private ShaderParameterBlock compatibilityUniformBlock;
        private PipelineHandle pipeline;
        private RenderPassCompatibility compatibility;
        private BufferHandle indexBuffer;
        private BufferHandle[] vertexBuffers = new BufferHandle[0];
        private TextureHandle[] textures = new TextureHandle[0];
        private long[] textureHandles = new long[0];
        private boolean uniformDataDirty;
        private boolean hasUniformData;
        private boolean ended = true;

        Pass(Attachment attachment) {
            this.attachment = attachment;
        }

        void begin(RenderPassCompatibility compatibility) {
            if (!ended) {
                throw new FdxException("Cannot reuse an active desktop C Vulkan render pass");
            }
            pipeline = null;
            this.compatibility = compatibility;
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
            this.pipeline = Resources.requirePipeline(pipeline, attachment, "Render pipeline");
            if (!compatibility.isCompatible(this.pipeline.targetLayout())) {
                this.pipeline = null;
                throw new FdxException(
                        "desktop C Vulkan render pipeline target layout is incompatible with the"
                                + " active pass");
            }
            prepareTextureSlots(this.pipeline.sampledTextureCount());
            uniformDataDirty = true;
            hasUniformData = false;
            compatibilityUniformBlock = null;
            DesktopCVulkan.setPipeline(attachment.context, this.pipeline.handle());
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
            BufferHandle vulkanBuffer =
                    Resources.requireBuffer(buffer, attachment, "Vertex buffer");
            if (vulkanBuffer.usage() != BufferUsage.VERTEX) {
                throw new FdxException("RenderPass.setVertexBuffer requires a vertex buffer");
            }
            rememberVertexBuffer(slot, vulkanBuffer);
            DesktopCVulkan.setVertexBuffer(attachment.context, slot, vulkanBuffer.handle());
        }

        /**
         * Sets the index buffer.
         *
         * @param buffer the buffer
         */
        @Override
        public void setIndexBuffer(Buffer buffer) {
            ensureOpen();
            indexBuffer = Resources.requireBuffer(buffer, attachment, "Index buffer");
            if (indexBuffer.usage() != BufferUsage.INDEX) {
                throw new FdxException("RenderPass.setIndexBuffer requires an index buffer");
            }
            DesktopCVulkan.setIndexBuffer(attachment.context, indexBuffer.handle());
        }

        /**
         * Sets the scissor.
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
            DesktopCVulkan.setScissor(
                    attachment.context, x, compatibility.height() - y - height, width, height);
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
            DesktopCVulkan.setViewport(
                    attachment.context, x, compatibility.height() - y - height, width, height);
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
                throw new FdxException(
                        "Texture slot is not declared by the active desktop C Vulkan pipeline: "
                                + slot);
            }
            textures[slot] = Resources.requireTexture(texture, attachment, "Texture");
        }

        @Override
        public void setTextureBinding(int group, int binding, Texture texture) {
            requirePipeline();
            int slot = pipeline.resourceBindings().textureSlot(group, binding);
            if (slot < 0) {
                throw new FdxException(
                        "Texture binding is not declared by the active desktop C Vulkan pipeline: "
                                + group
                                + ':'
                                + binding);
            }
            setTexture(slot, texture);
        }

        @Override
        public void setTextureSamplerBinding(int group, int binding, Texture texture) {
            requirePipeline();
            int slot = pipeline.resourceBindings().samplerSlot(group, binding);
            if (slot < 0) {
                throw new FdxException(
                        "Sampler binding is not declared by the active desktop C Vulkan pipeline: "
                                + group
                                + ':'
                                + binding);
            }
            setTexture(slot, texture);
        }

        @Override
        public void setSamplerBinding(int group, int binding, Sampler sampler) {
            throw new FdxException("Separate sampler objects are not supported by desktop C Vulkan");
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
                default ->
                        throw new FdxException(
                                "Uniform handle is not integer-compatible: " + parameter.path());
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
        public void setUniform4f(
                ShaderParameterHandle parameter, float x, float y, float z, float w) {
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
            DesktopCVulkan.draw(
                    attachment.context, vertexCount, instanceCount, firstVertex, firstInstance);
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
        public void drawIndexed(
                int indexCount,
                int instanceCount,
                int firstIndex,
                int baseVertex,
                int firstInstance) {
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
            DesktopCVulkan.drawIndexed(
                    attachment.context,
                    indexCount,
                    instanceCount,
                    firstIndex,
                    baseVertex,
                    firstInstance);
        }

        /** Ends the operation. */
        @Override
        public void end() {
            if (ended) {
                return;
            }
            attachment.ensureFrameStarted("end a render pass");
            ended = true;
            DesktopCVulkan.endRenderPass(attachment.context);
            pipeline = null;
            compatibility = null;
            indexBuffer = null;
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
        }

        private void validateBoundResources(boolean indexed) {
            Resources.requirePipeline(pipeline, attachment, "Render pipeline");
            for (int i = 0; i < vertexBuffers.length; i++) {
                if (vertexBuffers[i] != null) {
                    Resources.requireBuffer(
                            vertexBuffers[i], attachment, "Vertex buffer at slot " + i);
                }
            }
            if (indexed) {
                Resources.requireBuffer(indexBuffer, attachment, "Index buffer");
            }
        }

        private void bindTextures() {
            int sampledTextureCount = pipeline.sampledTextureCount();
            if (sampledTextureCount == 0) {
                return;
            }
            for (int i = 0; i < sampledTextureCount; i++) {
                if (textures[i] == null) {
                    throw new FdxException(
                            "Texture slot "
                                    + i
                                    + " must be set before drawing with desktop C Vulkan pipeline");
                }
                textureHandles[i] =
                        Resources.requireTexture(textures[i], attachment, "Texture at slot " + i)
                                .handle();
            }
            DesktopCVulkan.bindTextures(
                    attachment.context, pipeline.handle(), textureHandles, sampledTextureCount);
        }

        private void prepareTextureSlots(int sampledTextureCount) {
            if (textures.length < sampledTextureCount) {
                textures = new TextureHandle[sampledTextureCount];
                textureHandles = new long[sampledTextureCount];
            }
            for (int i = 0; i < textures.length; i++) {
                textures[i] = null;
                textureHandles[i] = 0L;
            }
        }

        private void rememberVertexBuffer(int slot, BufferHandle buffer) {
            if (slot >= vertexBuffers.length) {
                int nextLength = Math.max(slot + 1, Math.max(1, vertexBuffers.length * 2));
                BufferHandle[] grown = new BufferHandle[nextLength];
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
                throw new FdxException(
                        "desktop C Vulkan uniform parameter block must be bound before drawing");
            }
            if (uniformDataDirty) {
                DesktopCVulkan.bindUniforms(
                        attachment.context,
                        pipeline.handle(),
                        uniformBytes,
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
            Resources.requirePipeline(pipeline, attachment, "Render pipeline");
        }

        private ShaderParameterBlock compatibilityUniformBlock() {
            requirePipeline();
            if (!pipeline.resourceBindings().hasUniformBuffer()) {
                throw new FdxException(
                        "Active desktop C Vulkan pipeline has no reflected uniform buffer");
            }
            if (compatibilityUniformBlock == null) {
                compatibilityUniformBlock =
                        ShaderParameterBlock.allocate(
                                pipeline.resourceBindings().uniformBuffer().bufferLayout());
            }
            return compatibilityUniformBlock;
        }

        private void snapshotCompatibilityBlock() {
            setParameterBlock(
                    pipeline.resourceBindings().uniformGroup(),
                    pipeline.resourceBindings().uniformBinding(),
                    compatibilityUniformBlock);
        }

        private FdxException namedUniformUnsupported(String name) {
            ensureOpen();
            return new FdxException(
                    "desktop C Vulkan named uniform '"
                            + name
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
     * Represents a desktop C vulkan buffer handle.
     *
     * @author xpenatan
     */
    private static final class BufferHandle implements Buffer {
        private final Attachment attachment;
        private final long handle;
        private final int size;
        private final BufferUsage usage;
        private boolean disposed;

        BufferHandle(Attachment attachment, long handle, int size, BufferUsage usage) {
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

        /** Releases resources held by this instance. */
        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            if (!attachment.isDisposed()) {
                DesktopCVulkan.destroyBuffer(handle);
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
    }

    /**
     * Represents a desktop C vulkan texture handle.
     *
     * @author xpenatan
     */
    private static final class TextureHandle implements Texture {
        private final Attachment attachment;
        private final long handle;
        private final int width;
        private final int height;
        private final TextureFormat format;
        private final TextureUsage usage;
        private final int mipLevels, samples;
        private final TextureViewHandle[] views;
        private boolean disposed;

        TextureHandle(
                Attachment attachment,
                long handle,
                int width,
                int height,
                TextureFormat format,
                TextureUsage usage,
                int mipLevels,
                int samples) {
            this.attachment = attachment;
            this.handle = handle;
            this.width = width;
            this.height = height;
            this.format = format != null ? format : TextureFormat.RGBA8_UNORM;
            this.usage = usage != null ? usage : TextureUsage.SAMPLED;
            this.mipLevels = mipLevels;
            this.samples = samples;
            this.views = new TextureViewHandle[mipLevels];
        }

        @Override
        public int mipLevelCount() {
            return mipLevels;
        }

        @Override
        public int sampleCount() {
            return samples;
        }

        @Override
        public TextureView view() {
            return view(0);
        }

        @Override
        public TextureView view(int level) {
            Resources.requireTexture(this, attachment, "Texture view");
            if (!usage.renderAttachment() || level < 0 || level >= mipLevels)
                throw new FdxException(
                        "Texture view requires a render attachment and valid mip level");
            if (views[level] == null) views[level] = new TextureViewHandle(attachment, this, level);
            return views[level];
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

        /** Releases resources held by this instance. */
        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            if (!attachment.isDisposed()) {
                DesktopCVulkan.destroyTexture(handle);
            }
            for (TextureViewHandle view : views)
                if (view != null && view.depth != null) view.depth.dispose();
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
     * Represents a desktop C vulkan graphics frame.
     *
     * @author xpenatan
     */
    private static final class Frame implements GraphicsFrame {
        private final Attachment attachment;
        private final CommandEncoder commandEncoder;
        private final FrameBuffer frameBuffer;
        private final TextureView colorAttachment;

        Frame(
                Attachment attachment,
                CommandEncoder commandEncoder,
                FrameBuffer frameBuffer,
                TextureView colorAttachment) {
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
     * Represents a desktop C vulkan frame buffer.
     *
     * @author xpenatan
     */
    private static final class SurfaceBuffer implements FrameBuffer {
        private final Attachment attachment;
        private final TextureView colorAttachment;

        SurfaceBuffer(Attachment attachment, TextureView colorAttachment) {
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
     * Represents a desktop C vulkan texture view handle.
     *
     * @author xpenatan
     */
    private static final class TextureViewHandle implements TextureView {
        private final Attachment attachment;
        private final TextureHandle texture;
        private final int level;
        private TextureHandle depth;

        TextureViewHandle(Attachment attachment) {
            this(attachment, null, 0);
        }

        TextureViewHandle(Attachment attachment, TextureHandle texture, int level) {
            this.attachment = attachment;
            this.texture = texture;
            this.level = level;
        }

        long handle() {
            return texture == null ? 0 : texture.handle();
        }

        @Override
        public int mipLevel() {
            return level;
        }

        @Override
        public int sampleCount() {
            return texture == null ? 1 : texture.sampleCount();
        }

        long implicitDepth() {
            if (texture == null) return -1;
            if (depth == null)
                depth =
                        (TextureHandle)
                                attachment.device.createTexture(
                                        new TextureDescriptor()
                                                .size(width(), height())
                                                .format(TextureFormat.DEPTH32_FLOAT)
                                                .usage(TextureUsage.RENDER_ATTACHMENT)
                                                .sampleCount(sampleCount()));
            return depth.handle();
        }

        @Override
        public int width() {
            return texture == null ? attachment.width : texture.mipWidth(level);
        }

        @Override
        public int height() {
            return texture == null ? attachment.height : texture.mipHeight(level);
        }

        /**
         * Returns the format.
         *
         * @return the format
         */
        @Override
        public TextureFormat format() {
            return texture == null ? attachment.surfaceFormat : texture.format();
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
     * Represents a desktop C vulkan shader module handle.
     *
     * @author xpenatan
     */
    private static final class ShaderHandle implements ShaderModule {
        private boolean preparing;
        private final Attachment attachment;
        private final long handle;
        private final ShaderReflection reflection;
        private boolean disposed;

        ShaderHandle(Attachment attachment, long handle, ShaderReflection reflection) {
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

        /** Releases resources held by this instance. */
        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            if (preparing || !attachment.isDisposed()) {
                DesktopCVulkan.destroyShaderModule(handle);
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
    }

    /**
     * Represents a desktop C vulkan render pipeline handle.
     *
     * @author xpenatan
     */
    private static final class PipelineHandle implements RenderPipeline {
        private boolean published = true;
        private final Attachment attachment;
        private final long handle;
        private final PrimitiveTopology primitiveTopology;
        private final int sampledTextureCount;
        private final ShaderRenderBindings resourceBindings;
        private final int uniformDescriptorSetIndex;
        private final RenderTargetLayout targetLayout;
        private boolean disposed;

        PipelineHandle(
                Attachment attachment,
                long handle,
                PrimitiveTopology primitiveTopology,
                int sampledTextureCount,
                ShaderRenderBindings resourceBindings,
                int uniformDescriptorSetIndex,
                RenderTargetLayout targetLayout) {
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

        /** Releases resources held by this instance. */
        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            if (!published || !attachment.isDisposed()) {
                DesktopCVulkan.destroyRenderPipeline(handle, published);
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
    }

    private static int[] pipelineState(RenderPipelineDescriptor descriptor) {
        RenderTargetLayout targets = descriptor.renderTargetLayout();
        int[] state = new int[23 + targets.colorAttachmentCount() * 9];
        state[0] = targets.colorAttachmentCount();
        state[1] = toNativeTextureFormat(targets.depthStencilFormat());
        state[2] = targets.sampleCount();
        state[3] =
                switch (descriptor.primitiveState().cullMode()) {
                    case NONE -> 0;
                    case FRONT -> 1;
                    case BACK -> 2;
                };
        state[4] = descriptor.primitiveState().frontFace() == FrontFace.COUNTER_CLOCKWISE ? 0 : 1;
        state[5] = descriptor.multisampleState().mask();
        state[6] = descriptor.multisampleState().alphaToCoverageEnabled() ? 1 : 0;
        DepthStencilState depth = descriptor.depthStencilState();
        if (depth != null) {
            state[7] = 1;
            state[8] = depth.depthWriteEnabled() ? 1 : 0;
            state[9] = depth.depthCompare().ordinal();
            state[10] = depth.depthBias();
            state[11] = Float.floatToRawIntBits(depth.depthBiasSlopeScale());
            state[12] = Float.floatToRawIntBits(depth.depthBiasClamp());
            stencilState(state, 13, depth.stencilFront());
            stencilState(state, 17, depth.stencilBack());
            state[21] = depth.stencilReadMask();
            state[22] = depth.stencilWriteMask();
        }
        ColorTargetState[] colors = descriptor.colorTargets();
        for (int i = 0; i < colors.length; i++) {
            int offset = 23 + i * 9;
            state[offset] = toNativeTextureFormat(colors[i].format());
            state[offset + 1] = colors[i].blend() == null ? 0 : 1;
            state[offset + 2] = colors[i].writeMask();
            if (colors[i].blend() != null) {
                blendState(state, offset + 3, colors[i].blend().color());
                blendState(state, offset + 6, colors[i].blend().alpha());
            }
        }
        return state;
    }

    private static void stencilState(int[] state, int offset, StencilFaceState face) {
        state[offset] = face.compare().ordinal();
        state[offset + 1] = stencilOperation(face.fail());
        state[offset + 2] = stencilOperation(face.depthFail());
        state[offset + 3] = stencilOperation(face.pass());
    }

    private static int stencilOperation(StencilOperation operation) {
        return switch (operation) {
            case KEEP -> 0;
            case ZERO -> 1;
            case REPLACE -> 2;
            case INCREMENT_CLAMP -> 3;
            case DECREMENT_CLAMP -> 4;
            case INVERT -> 5;
            case INCREMENT_WRAP -> 6;
            case DECREMENT_WRAP -> 7;
        };
    }

    private static void blendState(int[] state, int offset, BlendComponent blend) {
        state[offset] = blendFactor(blend.sourceFactor());
        state[offset + 1] = blendFactor(blend.destinationFactor());
        state[offset + 2] = blend.operation().ordinal();
    }

    private static int blendFactor(BlendFactor factor) {
        return switch (factor) {
            case ZERO -> 0;
            case ONE -> 1;
            case SOURCE -> 2;
            case ONE_MINUS_SOURCE -> 3;
            case DESTINATION -> 4;
            case ONE_MINUS_DESTINATION -> 5;
            case SOURCE_ALPHA -> 6;
            case ONE_MINUS_SOURCE_ALPHA -> 7;
            case DESTINATION_ALPHA -> 8;
            case ONE_MINUS_DESTINATION_ALPHA -> 9;
            case CONSTANT -> 10;
            case ONE_MINUS_CONSTANT -> 11;
            case SOURCE_ALPHA_SATURATED -> 14;
        };
    }

    private static int toNativeBufferUsage(BufferUsage usage) {
        return usage == null ? 0 : usage.ordinal();
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
        if (format == TextureFormat.RGBA16_FLOAT) return 97;
        if (format == TextureFormat.R32_FLOAT) return 100;
        if (format == TextureFormat.DEPTH32_FLOAT) return 126;
        if (format == TextureFormat.DEPTH24_STENCIL8) return 129;
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
