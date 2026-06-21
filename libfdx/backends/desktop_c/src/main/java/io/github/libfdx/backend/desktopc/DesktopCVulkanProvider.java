package io.github.libfdx.backend.desktopc;

import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.GraphicsProviderSupport;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.graphics.NativeWindowPlatform;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.ShaderBinding;
import io.github.libfdx.graphics.ShaderBindingType;
import io.github.libfdx.graphics.ShaderLanguage;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.ShaderModuleDescriptors;
import io.github.libfdx.graphics.ShaderTarget;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.VertexStepMode;
import io.github.libfdx.graphics.vulkan.VulkanConfiguration;
import io.github.libfdx.graphics.vulkan.VulkanProvider;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Provides desktop C vulkan services.
 *
 * @author xpenatan
 */
public final class DesktopCVulkanProvider implements GraphicsAttachmentProvider, GraphicsProviderSupport {
    public static final ProviderId ID = VulkanProvider.ID;

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
        if (nativeWindow == null || nativeWindow.platform() != NativeWindowPlatform.GLFW
                || nativeWindow.backendHandle() == 0L) {
            throw new FdxException("desktop C Vulkan requires a GLFW native window");
        }
        String supportFailure = DesktopCVulkan.supportFailureReason();
        if (supportFailure != null) {
            throw new FdxException(supportFailure);
        }
        return new DesktopCVulkanGraphicsAttachment(configuration, nativeWindow.backendHandle(),
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
    private static final class DesktopCVulkanGraphicsAttachment implements GraphicsAttachment {
        private final long context;
        private final DesktopCVulkanGraphicsDevice device = new DesktopCVulkanGraphicsDevice(this);
        private final DesktopCVulkanCommandEncoder commandEncoder = new DesktopCVulkanCommandEncoder(this);
        private final DesktopCVulkanTextureViewHandle colorAttachment = new DesktopCVulkanTextureViewHandle(this);
        private final DesktopCVulkanFrameBuffer frameBuffer = new DesktopCVulkanFrameBuffer(this, colorAttachment);
        private final DesktopCVulkanGraphicsFrame currentFrame = new DesktopCVulkanGraphicsFrame(this,
                commandEncoder, frameBuffer, colorAttachment);
        private final TextureFormat surfaceFormat;
        private int width;
        private int height;
        private int pendingResizeWidth;
        private int pendingResizeHeight;
        private boolean frameStarted;
        private boolean pendingResize;
        private boolean disposed;

        DesktopCVulkanGraphicsAttachment(VulkanConfiguration configuration, long windowHandle, int width, int height) {
            VulkanConfiguration actualConfiguration = configuration != null ? configuration : new VulkanConfiguration();
            this.width = width;
            this.height = height;
            context = DesktopCVulkan.create(windowHandle, width, height, actualConfiguration.vSync(),
                    actualConfiguration.preferMailboxPresentMode(), actualConfiguration.framesInFlight());
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
                throw new FdxException("desktop C Vulkan frame is already started");
            }
            frameStarted = DesktopCVulkan.beginFrame(context);
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
                throw new FdxException("No desktop C Vulkan frame is active");
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
            DesktopCVulkan.clear(context, red, green, blue, alpha);
        }

        ByteBuffer readPixelsRgba8() {
            if (!frameStarted) {
                throw new FdxException("Cannot read pixels before beginFrame()");
            }
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

        /**
         * Releases resources held by this instance.
         */
        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            DesktopCVulkan.destroy(context);
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
    }

    /**
     * Represents a desktop C vulkan graphics device.
     *
     * @author xpenatan
     */
    private static final class DesktopCVulkanGraphicsDevice implements GraphicsDevice {
        private final DesktopCVulkanGraphicsAttachment attachment;

        DesktopCVulkanGraphicsDevice(DesktopCVulkanGraphicsAttachment attachment) {
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
            if (descriptor == null) {
                throw new FdxException("BufferDescriptor cannot be null");
            }
            return new DesktopCVulkanBufferHandle(DesktopCVulkan.createBuffer(attachment.context,
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
            if (buffer == null) {
                throw new FdxException("Buffer cannot be null");
            }
            if (data == null) {
                throw new FdxException("Buffer data cannot be null");
            }
            DesktopCVulkanBufferHandle vulkanBuffer = buffer.as();
            if (data.remaining() > vulkanBuffer.size()) {
                throw new FdxException("Buffer data is larger than the destination buffer");
            }
            ByteBuffer source = data.position() == 0 ? data : data.slice();
            DesktopCVulkan.writeBuffer(vulkanBuffer.handle(), source, source.remaining());
        }

        /**
         * Creates a texture.
         *
         * @param descriptor the descriptor
         * @return the created value
         */
        @Override
        public Texture createTexture(TextureDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("TextureDescriptor cannot be null");
            }
            if (descriptor.format() != TextureFormat.RGBA8_UNORM) {
                throw new FdxException("desktop C Vulkan currently supports RGBA8_UNORM sampled textures only");
            }
            if (descriptor.usage() != TextureUsage.SAMPLED) {
                throw new FdxException("desktop C Vulkan currently supports sampled textures only");
            }
            return new DesktopCVulkanTextureHandle(DesktopCVulkan.createTexture(attachment.context,
                    descriptor.width(), descriptor.height(), toNativeTextureFormat(descriptor.format()),
                    toNativeWrap(descriptor.wrapS()), toNativeWrap(descriptor.wrapT())),
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
            if (texture == null) {
                throw new FdxException("Texture cannot be null");
            }
            if (data == null) {
                throw new FdxException("Texture data cannot be null");
            }
            DesktopCVulkanTextureHandle vulkanTexture = texture.as();
            int byteCount = vulkanTexture.width() * vulkanTexture.height() * 4;
            if (data.remaining() != byteCount) {
                throw new FdxException("desktop C Vulkan texture upload expects " + byteCount + " RGBA bytes");
            }
            ByteBuffer source = data.position() == 0 ? data : data.slice();
            DesktopCVulkan.writeTexture(vulkanTexture.handle(), source, source.remaining());
        }

        /**
         * Creates a shader module.
         *
         * @param descriptor the descriptor
         * @return the created value
         */
        @Override
        public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("ShaderModuleDescriptor cannot be null");
            }
            descriptor = ShaderModuleDescriptors.requireTarget(descriptor, ShaderTarget.VULKAN_SPIRV,
                    "desktop C Vulkan");
            if (!descriptor.hasSource(ShaderLanguage.SPIRV)) {
                throw new FdxException("desktop C Vulkan requires SPIR-V shader modules");
            }
            return new DesktopCVulkanShaderModuleHandle(DesktopCVulkan.createShaderModule(attachment.context,
                    descriptor.spirvVertexWords(), descriptor.spirvFragmentWords()));
        }

        /**
         * Creates a render pipeline.
         *
         * @param descriptor the descriptor
         * @return the created value
         */
        @Override
        public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("RenderPipelineDescriptor cannot be null");
            }
            if (descriptor.colorFormat() != attachment.surfaceFormat()) {
                throw new FdxException("desktop C Vulkan render pipeline color format must match the surface format");
            }
            DesktopCVulkanShaderModuleHandle shaderModule = descriptor.shaderModule().as();
            boolean pbrUniformsEnabled = usesPbrUniformBlock(descriptor);
            VertexLayout[] vertexLayouts = descriptor.vertexLayouts();
            return new DesktopCVulkanRenderPipelineHandle(DesktopCVulkan.createRenderPipeline(attachment.context,
                    shaderModule.handle(), toNativeTopology(descriptor.primitiveTopology()),
                    vertexStrides(vertexLayouts), vertexStepModes(vertexLayouts), attributeBindings(vertexLayouts),
                    attributeLocations(vertexLayouts), attributeFormats(vertexLayouts),
                    attributeOffsets(vertexLayouts), descriptor.sampledTextureCount(), pbrUniformsEnabled,
                    descriptor.depthTestEnabled(), descriptor.depthWriteEnabled()),
                    descriptor.primitiveTopology(), descriptor.sampledTextureCount(),
                    pbrUniformsEnabled, descriptor.sampledTextureCount() > 0 ? 1 : 0);
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

    private static boolean usesPbrUniformBlock(RenderPipelineDescriptor descriptor) {
        ShaderBinding[] bindings = descriptor.shaderReflection().bindings();
        for (int i = 0; i < bindings.length; i++) {
            ShaderBinding binding = bindings[i];
            if ((binding.group() == 0 || binding.group() == 1)
                    && binding.binding() == 0
                    && binding.type() == ShaderBindingType.UNIFORM_BUFFER
                    && "uniforms".equals(binding.name())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Represents a desktop C vulkan command encoder.
     *
     * @author xpenatan
     */
    private static final class DesktopCVulkanCommandEncoder implements CommandEncoder {
        private final DesktopCVulkanGraphicsAttachment attachment;

        DesktopCVulkanCommandEncoder(DesktopCVulkanGraphicsAttachment attachment) {
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
            LoadOp loadOp = descriptor.colorLoadOp();
            StoreOp storeOp = descriptor.colorStoreOp();
            DesktopCVulkan.beginRenderPass(attachment.context, loadOp.isClear(), loadOp.red(), loadOp.green(),
                    loadOp.blue(), loadOp.alpha(), storeOp.isStore(), descriptor.depthClearEnabled(),
                    descriptor.depthClearValue());
            return new DesktopCVulkanRenderPass(attachment);
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
    private static final class DesktopCVulkanRenderPass implements RenderPass {
        private static final int PBR_UNIFORM_BYTE_COUNT = 5232;
        private static final int MATRIX_FLOAT_COUNT = 16;
        private static final int MODEL_OFFSET = 0;
        private static final int VIEW_PROJECTION_OFFSET = 16;
        private static final int CAMERA_POSITION_OFFSET = 32;
        private static final int CAMERA_DIRECTION_OFFSET = 36;
        private static final int AMBIENT_COLOR_OFFSET = 40;
        private static final int LIGHT_DIRECTION_OFFSET = 44;
        private static final int LIGHT_COLOR_INTENSITY_OFFSET = 48;
        private static final int TEXTURE_FLAGS_OFFSET = 52;
        private static final int EMISSIVE_FLAGS_OFFSET = 56;
        private static final int FOG_COLOR_OFFSET = 60;
        private static final int FOG_PARAMS_OFFSET = 64;
        private static final int SKY_ZENITH_COLOR_OFFSET = 68;
        private static final int SKY_HORIZON_COLOR_OFFSET = 72;
        private static final int SKY_NADIR_COLOR_OFFSET = 76;
        private static final int SKY_SUN_COLOR_OFFSET = 80;
        private static final int SKY_SUN_DIRECTION_OFFSET = 84;
        private static final int SKY_PARAMS_OFFSET = 88;
        private static final int MAX_POINT_LIGHTS = 4;
        private static final int POINT_LIGHT_COUNT_OFFSET = 92;
        private static final int POINT_LIGHT_POSITIONS_OFFSET = POINT_LIGHT_COUNT_OFFSET + 4;
        private static final int POINT_LIGHT_COLORS_OFFSET = POINT_LIGHT_POSITIONS_OFFSET + MAX_POINT_LIGHTS * 4;
        private static final int MAX_SPOT_LIGHTS = 4;
        private static final int SPOT_LIGHT_COUNT_OFFSET = POINT_LIGHT_COLORS_OFFSET + MAX_POINT_LIGHTS * 4;
        private static final int SPOT_LIGHT_POSITIONS_OFFSET = SPOT_LIGHT_COUNT_OFFSET + 4;
        private static final int SPOT_LIGHT_DIRECTIONS_OFFSET = SPOT_LIGHT_POSITIONS_OFFSET + MAX_SPOT_LIGHTS * 4;
        private static final int SPOT_LIGHT_COLORS_OFFSET = SPOT_LIGHT_DIRECTIONS_OFFSET + MAX_SPOT_LIGHTS * 4;
        private static final int SPOT_LIGHT_CONES_OFFSET = SPOT_LIGHT_COLORS_OFFSET + MAX_SPOT_LIGHTS * 4;
        private static final int MAX_SHADOW_CASCADES = 4;
        private static final int SHADOW_VIEW_PROJECTIONS_OFFSET = SPOT_LIGHT_CONES_OFFSET + MAX_SPOT_LIGHTS * 4;
        private static final int SHADOW_PARAMS_OFFSET = SHADOW_VIEW_PROJECTIONS_OFFSET
                + MAX_SHADOW_CASCADES * MATRIX_FLOAT_COUNT;
        private static final int SHADOW_CASCADE_SPLITS_OFFSET = SHADOW_PARAMS_OFFSET + 4;
        private static final int SHADOW_BIASES_OFFSET = SHADOW_CASCADE_SPLITS_OFFSET + 4;
        private static final int SHADOW_CAMERA_POSITION_OFFSET = SHADOW_BIASES_OFFSET + 4;
        private static final int SHADOW_CAMERA_DIRECTION_OFFSET = SHADOW_CAMERA_POSITION_OFFSET + 4;
        private static final int SHADOW_CAMERA_UP_OFFSET = SHADOW_CAMERA_DIRECTION_OFFSET + 4;
        private static final int SHADOW_CAMERA_PARAMS_OFFSET = SHADOW_CAMERA_UP_OFFSET + 4;
        private static final int SKINNING_PARAMS_OFFSET = SHADOW_CAMERA_PARAMS_OFFSET + 4;
        private static final int MAX_BONES = 64;
        private static final int BONE_MATRICES_OFFSET = SKINNING_PARAMS_OFFSET + 4;

        private final DesktopCVulkanGraphicsAttachment attachment;
        private final ByteBuffer uniformBytes = ByteBuffer.allocateDirect(PBR_UNIFORM_BYTE_COUNT)
                .order(ByteOrder.nativeOrder());
        private final FloatBuffer uniformFloats = uniformBytes.asFloatBuffer();
        private DesktopCVulkanRenderPipelineHandle pipeline;
        private DesktopCVulkanBufferHandle indexBuffer;
        private DesktopCVulkanTextureHandle[] textures = new DesktopCVulkanTextureHandle[0];
        private long[] textureHandles = new long[0];
        private boolean uniformDataDirty = true;
        private boolean hasUniformData;
        private boolean ended;

        DesktopCVulkanRenderPass(DesktopCVulkanGraphicsAttachment attachment) {
            this.attachment = attachment;
            resetUniformData();
        }

        /**
         * Sets the pipeline.
         *
         * @param pipeline the pipeline
         */
        @Override
        public void setPipeline(RenderPipeline pipeline) {
            ensureOpen();
            this.pipeline = pipeline.as();
            prepareTextureSlots(this.pipeline.sampledTextureCount());
            uniformDataDirty = true;
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
            if (buffer == null) {
                throw new FdxException("Vertex buffer cannot be null");
            }
            if (slot < 0) {
                throw new FdxException("Vertex buffer slot cannot be negative");
            }
            DesktopCVulkanBufferHandle vulkanBuffer = buffer.as();
            if (vulkanBuffer.usage() != BufferUsage.VERTEX) {
                throw new FdxException("RenderPass.setVertexBuffer requires a vertex buffer");
            }
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
            if (buffer == null) {
                throw new FdxException("Index buffer cannot be null");
            }
            indexBuffer = buffer.as();
            if (indexBuffer.usage() != BufferUsage.INDEX) {
                throw new FdxException("RenderPass.setIndexBuffer requires an index buffer");
            }
            DesktopCVulkan.setIndexBuffer(attachment.context, indexBuffer.handle());
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
            if (texture == null) {
                throw new FdxException("Texture cannot be null");
            }
            if (slot < 0 || slot >= pipeline.sampledTextureCount()) {
                throw new FdxException("Texture slot is not declared by the active desktop C Vulkan pipeline: " + slot);
            }
            textures[slot] = texture.as();
        }

        /**
         * Sets the uniform1i.
         *
         * @param name the name
         * @param value the value
         */
        @Override
        public void setUniform1i(String name, int value) {
            if ("u_hasBaseColorTexture".equals(name)) {
                setUniformFloat(TEXTURE_FLAGS_OFFSET, value);
            }
            else if ("u_hasMetallicRoughnessTexture".equals(name)) {
                setUniformFloat(TEXTURE_FLAGS_OFFSET + 1, value);
            }
            else if ("u_hasNormalTexture".equals(name)) {
                setUniformFloat(TEXTURE_FLAGS_OFFSET + 2, value);
            }
            else if ("u_hasOcclusionTexture".equals(name)) {
                setUniformFloat(TEXTURE_FLAGS_OFFSET + 3, value);
            }
            else if ("u_hasEmissiveTexture".equals(name)) {
                setUniformFloat(EMISSIVE_FLAGS_OFFSET, value);
            }
        }

        /**
         * Sets the uniform1f.
         *
         * @param name the name
         * @param value the value
         */
        @Override
        public void setUniform1f(String name, float value) {
            if ("u_lightIntensity".equals(name)) {
                setUniformFloat(LIGHT_COLOR_INTENSITY_OFFSET + 3, value);
            }
            else if ("u_pointLightCount".equals(name)) {
                setUniformFloat(POINT_LIGHT_COUNT_OFFSET, value);
            }
            else if ("u_spotLightCount".equals(name)) {
                setUniformFloat(SPOT_LIGHT_COUNT_OFFSET, value);
            }
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
            if ("u_cameraPosition".equals(name)) {
                setUniform4f(CAMERA_POSITION_OFFSET, x, y, z, 1.0f);
            }
            else if ("u_cameraDirection".equals(name)) {
                setUniform4f(CAMERA_DIRECTION_OFFSET, x, y, z, 0.0f);
            }
            else if ("u_ambientColor".equals(name)) {
                setUniform4f(AMBIENT_COLOR_OFFSET, x, y, z, 1.0f);
            }
            else if ("u_lightDirection".equals(name)) {
                setUniform4f(LIGHT_DIRECTION_OFFSET, x, y, z, 0.0f);
            }
            else if ("u_lightColor".equals(name)) {
                setUniform4f(LIGHT_COLOR_INTENSITY_OFFSET, x, y, z,
                        uniformFloats.get(LIGHT_COLOR_INTENSITY_OFFSET + 3));
            }
            else if ("u_fogColor".equals(name)) {
                setUniform4f(FOG_COLOR_OFFSET, x, y, z, 1.0f);
            }
            else if ("u_skyZenithColor".equals(name)) {
                setUniform4f(SKY_ZENITH_COLOR_OFFSET, x, y, z, 1.0f);
            }
            else if ("u_skyHorizonColor".equals(name)) {
                setUniform4f(SKY_HORIZON_COLOR_OFFSET, x, y, z, 1.0f);
            }
            else if ("u_skyNadirColor".equals(name)) {
                setUniform4f(SKY_NADIR_COLOR_OFFSET, x, y, z, 1.0f);
            }
            else if ("u_skySunColor".equals(name)) {
                setUniform4f(SKY_SUN_COLOR_OFFSET, x, y, z,
                        uniformFloats.get(SKY_SUN_COLOR_OFFSET + 3));
            }
            else if ("u_skySunDirection".equals(name)) {
                setUniform4f(SKY_SUN_DIRECTION_OFFSET, x, y, z, 0.0f);
            }
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
            if ("u_cameraPosition".equals(name)) {
                setUniform4f(CAMERA_POSITION_OFFSET, x, y, z, w);
            }
            else if ("u_cameraDirection".equals(name)) {
                setUniform4f(CAMERA_DIRECTION_OFFSET, x, y, z, w);
            }
            else if ("u_ambientColor".equals(name)) {
                setUniform4f(AMBIENT_COLOR_OFFSET, x, y, z, w);
            }
            else if ("u_lightDirection".equals(name)) {
                setUniform4f(LIGHT_DIRECTION_OFFSET, x, y, z, w);
            }
            else if ("u_lightColor".equals(name)) {
                setUniform4f(LIGHT_COLOR_INTENSITY_OFFSET, x, y, z, w);
            }
            else if ("u_fogColor".equals(name)) {
                setUniform4f(FOG_COLOR_OFFSET, x, y, z, w);
            }
            else if ("u_fogParams".equals(name)) {
                setUniform4f(FOG_PARAMS_OFFSET, x, y, z, w);
            }
            else if ("u_skyZenithColor".equals(name)) {
                setUniform4f(SKY_ZENITH_COLOR_OFFSET, x, y, z, w);
            }
            else if ("u_skyHorizonColor".equals(name)) {
                setUniform4f(SKY_HORIZON_COLOR_OFFSET, x, y, z, w);
            }
            else if ("u_skyNadirColor".equals(name)) {
                setUniform4f(SKY_NADIR_COLOR_OFFSET, x, y, z, w);
            }
            else if ("u_skySunColor".equals(name)) {
                setUniform4f(SKY_SUN_COLOR_OFFSET, x, y, z, w);
            }
            else if ("u_skySunDirection".equals(name)) {
                setUniform4f(SKY_SUN_DIRECTION_OFFSET, x, y, z, w);
            }
            else if ("u_skyParams".equals(name)) {
                setUniform4f(SKY_PARAMS_OFFSET, x, y, z, w);
            }
            else {
                int positionIndex = pointLightIndex(name, "PositionRange");
                if (positionIndex >= 0) {
                    setUniform4f(POINT_LIGHT_POSITIONS_OFFSET + positionIndex * 4, x, y, z, w);
                    return;
                }
                int colorIndex = pointLightIndex(name, "ColorIntensity");
                if (colorIndex >= 0) {
                    setUniform4f(POINT_LIGHT_COLORS_OFFSET + colorIndex * 4, x, y, z, w);
                    return;
                }
                int spotPositionIndex = spotLightIndex(name, "PositionRange");
                if (spotPositionIndex >= 0) {
                    setUniform4f(SPOT_LIGHT_POSITIONS_OFFSET + spotPositionIndex * 4, x, y, z, w);
                    return;
                }
                int spotDirectionIndex = spotLightIndex(name, "DirectionInner");
                if (spotDirectionIndex >= 0) {
                    setUniform4f(SPOT_LIGHT_DIRECTIONS_OFFSET + spotDirectionIndex * 4, x, y, z, w);
                    return;
                }
                int spotColorIndex = spotLightIndex(name, "ColorIntensity");
                if (spotColorIndex >= 0) {
                    setUniform4f(SPOT_LIGHT_COLORS_OFFSET + spotColorIndex * 4, x, y, z, w);
                    return;
                }
                int spotConeIndex = spotLightIndex(name, "Cone");
                if (spotConeIndex >= 0) {
                    setUniform4f(SPOT_LIGHT_CONES_OFFSET + spotConeIndex * 4, x, y, z, w);
                    return;
                }
                if ("u_shadowParams".equals(name)) {
                    setUniform4f(SHADOW_PARAMS_OFFSET, x, y, z, w);
                }
                else if ("u_shadowCascadeSplits".equals(name)) {
                    setUniform4f(SHADOW_CASCADE_SPLITS_OFFSET, x, y, z, w);
                }
                else if ("u_shadowBiases".equals(name)) {
                    setUniform4f(SHADOW_BIASES_OFFSET, x, y, z, w);
                }
                else if ("u_shadowCameraPosition".equals(name)) {
                    setUniform4f(SHADOW_CAMERA_POSITION_OFFSET, x, y, z, w);
                }
                else if ("u_shadowCameraDirection".equals(name)) {
                    setUniform4f(SHADOW_CAMERA_DIRECTION_OFFSET, x, y, z, w);
                }
                else if ("u_shadowCameraUp".equals(name)) {
                    setUniform4f(SHADOW_CAMERA_UP_OFFSET, x, y, z, w);
                }
                else if ("u_shadowCameraParams".equals(name)) {
                    setUniform4f(SHADOW_CAMERA_PARAMS_OFFSET, x, y, z, w);
                }
                else if ("u_skinningParams".equals(name)) {
                    setUniform4f(SKINNING_PARAMS_OFFSET, x, y, z, w);
                }
            }
        }

        /**
         * Sets the uniform matrix4.
         *
         * @param name the name
         * @param values the values
         */
        @Override
        public void setUniformMatrix4(String name, float[] values) {
            ensureOpen();
            if (values == null || values.length < MATRIX_FLOAT_COUNT) {
                throw new FdxException("Matrix uniform requires 16 float values");
            }
            if ("u_model".equals(name)) {
                setUniformMatrix(MODEL_OFFSET, values);
            }
            else if ("u_viewProjection".equals(name)) {
                setUniformMatrix(VIEW_PROJECTION_OFFSET, values);
            }
            else {
                int shadowIndex = shadowViewProjectionIndex(name);
                if (shadowIndex >= 0) {
                    setUniformMatrix(SHADOW_VIEW_PROJECTIONS_OFFSET + shadowIndex * MATRIX_FLOAT_COUNT, values);
                    return;
                }
                int boneIndex = boneMatrixIndex(name);
                if (boneIndex >= 0) {
                    setUniformMatrix(BONE_MATRICES_OFFSET + boneIndex * MATRIX_FLOAT_COUNT, values);
                }
            }
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
            bindTextures();
            bindUniforms();
            DesktopCVulkan.draw(attachment.context, vertexCount, instanceCount, firstVertex, firstInstance);
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
            bindTextures();
            bindUniforms();
            DesktopCVulkan.drawIndexed(attachment.context, indexCount, instanceCount, firstIndex,
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
            DesktopCVulkan.endRenderPass(attachment.context);
        }

        private void ensureOpen() {
            if (ended) {
                throw new FdxException("Render pass has already ended");
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
                            + " must be set before drawing with desktop C Vulkan pipeline");
                }
                textureHandles[i] = textures[i].handle();
            }
            DesktopCVulkan.bindTextures(attachment.context, pipeline.handle(), textureHandles, sampledTextureCount);
        }

        private void prepareTextureSlots(int sampledTextureCount) {
            if (sampledTextureCount == 0) {
                return;
            }
            if (textures.length != sampledTextureCount) {
                textures = new DesktopCVulkanTextureHandle[sampledTextureCount];
                textureHandles = new long[sampledTextureCount];
                return;
            }
            for (int i = 0; i < textures.length; i++) {
                textures[i] = null;
                textureHandles[i] = 0L;
            }
        }

        private void bindUniforms() {
            if (!pipeline.uniformBufferEnabled()) {
                return;
            }
            if (!hasUniformData) {
                throw new FdxException("desktop C Vulkan PBR uniforms must be set before drawing");
            }
            if (uniformDataDirty) {
                DesktopCVulkan.bindUniforms(attachment.context, pipeline.handle(), uniformBytes,
                        PBR_UNIFORM_BYTE_COUNT);
                uniformDataDirty = false;
            }
        }

        private void setUniformMatrix(int offset, float[] values) {
            ensureOpen();
            for (int i = 0; i < MATRIX_FLOAT_COUNT; i++) {
                uniformFloats.put(offset + i, values[i]);
            }
            markUniformDirty();
        }

        private void setUniform4f(int offset, float x, float y, float z, float w) {
            ensureOpen();
            uniformFloats.put(offset, x);
            uniformFloats.put(offset + 1, y);
            uniformFloats.put(offset + 2, z);
            uniformFloats.put(offset + 3, w);
            markUniformDirty();
        }

        private void setUniformFloat(int offset, float value) {
            ensureOpen();
            uniformFloats.put(offset, value);
            markUniformDirty();
        }

        private int pointLightIndex(String name, String suffix) {
            return lightIndex(name, "u_pointLight", suffix, MAX_POINT_LIGHTS);
        }

        private int spotLightIndex(String name, String suffix) {
            return lightIndex(name, "u_spotLight", suffix, MAX_SPOT_LIGHTS);
        }

        private int boneMatrixIndex(String name) {
            if (name == null || !name.startsWith("u_bone")) {
                return -1;
            }
            int index = 0;
            for (int i = 6; i < name.length(); i++) {
                char ch = name.charAt(i);
                if (ch < '0' || ch > '9') {
                    return -1;
                }
                index = index * 10 + ch - '0';
            }
            return index >= 0 && index < MAX_BONES ? index : -1;
        }

        private int shadowViewProjectionIndex(String name) {
            if ("u_shadowViewProjection".equals(name)) {
                return 0;
            }
            if (name == null || !name.startsWith("u_shadowViewProjection")) {
                return -1;
            }
            int suffixOffset = "u_shadowViewProjection".length();
            if (name.length() != suffixOffset + 1) {
                return -1;
            }
            int index = name.charAt(suffixOffset) - '0';
            return index >= 0 && index < MAX_SHADOW_CASCADES ? index : -1;
        }

        private int lightIndex(String name, String prefix, String suffix, int maxLights) {
            if (name == null || suffix == null || !name.startsWith(prefix) || !name.endsWith(suffix)) {
                return -1;
            }
            int digitOffset = prefix.length();
            int digitEnd = name.length() - suffix.length();
            if (digitEnd != digitOffset + 1) {
                return -1;
            }
            int index = name.charAt(digitOffset) - '0';
            return index >= 0 && index < maxLights ? index : -1;
        }

        private void markUniformDirty() {
            hasUniformData = true;
            uniformDataDirty = true;
        }

        private void resetUniformData() {
            for (int i = 0; i < PBR_UNIFORM_BYTE_COUNT / 4; i++) {
                uniformFloats.put(i, 0.0f);
            }
            uniformFloats.put(MODEL_OFFSET, 1.0f);
            uniformFloats.put(MODEL_OFFSET + 5, 1.0f);
            uniformFloats.put(MODEL_OFFSET + 10, 1.0f);
            uniformFloats.put(MODEL_OFFSET + 15, 1.0f);
            uniformFloats.put(VIEW_PROJECTION_OFFSET, 1.0f);
            uniformFloats.put(VIEW_PROJECTION_OFFSET + 5, 1.0f);
            uniformFloats.put(VIEW_PROJECTION_OFFSET + 10, 1.0f);
            uniformFloats.put(VIEW_PROJECTION_OFFSET + 15, 1.0f);
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
    private static final class DesktopCVulkanBufferHandle implements Buffer {
        private final long handle;
        private final int size;
        private final BufferUsage usage;
        private boolean disposed;

        DesktopCVulkanBufferHandle(long handle, int size, BufferUsage usage) {
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
            disposed = true;
            DesktopCVulkan.destroyBuffer(handle);
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
    private static final class DesktopCVulkanTextureHandle implements Texture {
        private final long handle;
        private final int width;
        private final int height;
        private final TextureFormat format;
        private final TextureUsage usage;
        private boolean disposed;

        DesktopCVulkanTextureHandle(long handle, int width, int height, TextureFormat format, TextureUsage usage) {
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
            disposed = true;
            DesktopCVulkan.destroyTexture(handle);
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
    private static final class DesktopCVulkanGraphicsFrame implements GraphicsFrame {
        private final DesktopCVulkanGraphicsAttachment attachment;
        private final CommandEncoder commandEncoder;
        private final FrameBuffer frameBuffer;
        private final TextureView colorAttachment;

        DesktopCVulkanGraphicsFrame(DesktopCVulkanGraphicsAttachment attachment, CommandEncoder commandEncoder,
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
     * Represents a desktop C vulkan frame buffer.
     *
     * @author xpenatan
     */
    private static final class DesktopCVulkanFrameBuffer implements FrameBuffer {
        private final DesktopCVulkanGraphicsAttachment attachment;
        private final TextureView colorAttachment;

        DesktopCVulkanFrameBuffer(DesktopCVulkanGraphicsAttachment attachment, TextureView colorAttachment) {
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
    private static final class DesktopCVulkanTextureViewHandle implements TextureView {
        private final DesktopCVulkanGraphicsAttachment attachment;

        DesktopCVulkanTextureViewHandle(DesktopCVulkanGraphicsAttachment attachment) {
            this.attachment = attachment;
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
    private static final class DesktopCVulkanShaderModuleHandle implements ShaderModule {
        private final long handle;
        private boolean disposed;

        DesktopCVulkanShaderModuleHandle(long handle) {
            this.handle = handle;
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
            DesktopCVulkan.destroyShaderModule(handle);
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
    private static final class DesktopCVulkanRenderPipelineHandle implements RenderPipeline {
        private final long handle;
        private final PrimitiveTopology primitiveTopology;
        private final int sampledTextureCount;
        private final boolean uniformBufferEnabled;
        private final int uniformDescriptorSetIndex;
        private boolean disposed;

        DesktopCVulkanRenderPipelineHandle(long handle, PrimitiveTopology primitiveTopology, int sampledTextureCount,
                boolean uniformBufferEnabled, int uniformDescriptorSetIndex) {
            this.handle = handle;
            this.primitiveTopology = primitiveTopology;
            this.sampledTextureCount = sampledTextureCount;
            this.uniformBufferEnabled = uniformBufferEnabled;
            this.uniformDescriptorSetIndex = uniformDescriptorSetIndex;
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
            return uniformBufferEnabled;
        }

        int uniformDescriptorSetIndex() {
            return uniformDescriptorSetIndex;
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
            DesktopCVulkan.destroyRenderPipeline(handle);
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
