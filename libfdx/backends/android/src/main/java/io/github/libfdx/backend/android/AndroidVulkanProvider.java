package io.github.libfdx.backend.android;

import android.view.Surface;
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
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.ShaderLanguage;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
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
import io.github.libfdx.graphics.vulkan.VulkanConfiguration;
import io.github.libfdx.graphics.vulkan.VulkanProvider;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public final class AndroidVulkanProvider implements GraphicsAttachmentProvider, GraphicsProviderSupport {
    public static final ProviderId ID = VulkanProvider.ID;
    private static final int PBR_TEXTURE_DESCRIPTOR_COUNT = 5;

    private VulkanConfiguration configuration = new VulkanConfiguration();

    @Override
    public ProviderId providerId() {
        return ID;
    }

    @Override
    public GraphicsAttachmentRequirements requirements() {
        return GraphicsAttachmentRequirements.vulkan();
    }

    @Override
    public GraphicsAttachment create(GraphicsEnvironment environment) {
        if (environment == null) {
            throw new FdxException("GraphicsEnvironment cannot be null");
        }
        NativeWindow nativeWindow = environment.nativeWindow();
        if (nativeWindow == null || !(nativeWindow.objectHandle() instanceof Surface)) {
            throw new FdxException("Android Vulkan requires an Android Surface");
        }
        String supportFailure = AndroidVulkanNative.instanceProbeFailure();
        if (supportFailure != null) {
            throw new FdxException(supportFailure);
        }
        return new AndroidVulkanGraphicsAttachment(configuration, (Surface) nativeWindow.objectHandle(),
                environment.display().framebufferWidth(), environment.display().framebufferHeight());
    }

    @Override
    public boolean isSupported() {
        return supportFailureReason() == null;
    }

    @Override
    public String supportFailureReason() {
        return AndroidVulkanNative.instanceProbeFailure();
    }

    public VulkanConfiguration configuration() {
        return configuration;
    }

    public AndroidVulkanProvider configuration(VulkanConfiguration configuration) {
        this.configuration = configuration != null ? configuration : new VulkanConfiguration();
        return this;
    }

    public AndroidVulkanProvider vSync(boolean vSync) {
        configuration.vSync(vSync);
        return this;
    }

    public AndroidVulkanProvider validation(boolean validation) {
        configuration.validation(validation);
        return this;
    }

    public AndroidVulkanProvider framesInFlight(int framesInFlight) {
        configuration.framesInFlight(framesInFlight);
        return this;
    }

    private static final class AndroidVulkanGraphicsAttachment implements GraphicsAttachment {
        private final long context;
        private final AndroidVulkanGraphicsDevice device = new AndroidVulkanGraphicsDevice(this);
        private final AndroidVulkanCommandEncoder commandEncoder = new AndroidVulkanCommandEncoder(this);
        private final AndroidVulkanTextureViewHandle colorAttachment = new AndroidVulkanTextureViewHandle(this);
        private final AndroidVulkanFrameBuffer frameBuffer = new AndroidVulkanFrameBuffer(this, colorAttachment);
        private final AndroidVulkanGraphicsFrame currentFrame = new AndroidVulkanGraphicsFrame(this,
                commandEncoder, frameBuffer, colorAttachment);
        private final TextureFormat surfaceFormat;
        private int width;
        private int height;
        private int pendingResizeWidth;
        private int pendingResizeHeight;
        private boolean frameStarted;
        private boolean pendingResize;
        private boolean disposed;

        AndroidVulkanGraphicsAttachment(VulkanConfiguration configuration, Surface surface, int width, int height) {
            VulkanConfiguration actualConfiguration = configuration != null ? configuration : new VulkanConfiguration();
            this.width = width;
            this.height = height;
            context = AndroidVulkanNative.create(surface, width, height, actualConfiguration.vSync(),
                    actualConfiguration.preferMailboxPresentMode(), actualConfiguration.framesInFlight());
            surfaceFormat = toCommonFormat(AndroidVulkanNative.surfaceFormat(context));
        }

        @Override
        public void resize(int framebufferWidth, int framebufferHeight) {
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

        @Override
        public void processEvents() {
        }

        @Override
        public boolean beginFrame() {
            if (disposed || width <= 0 || height <= 0) {
                return false;
            }
            if (frameStarted) {
                throw new FdxException("Android Vulkan frame is already started");
            }
            frameStarted = AndroidVulkanNative.beginFrame(context);
            if (frameStarted) {
                applyPendingResizeDimensions();
            }
            return frameStarted;
        }

        @Override
        public void endFrame() {
            if (!frameStarted) {
                return;
            }
            try {
                AndroidVulkanNative.endFrame(context);
                applyPendingResizeDimensions();
            } finally {
                frameStarted = false;
            }
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
                throw new FdxException("No Android Vulkan frame is active");
            }
            return currentFrame;
        }

        @Override
        public void clear(float red, float green, float blue, float alpha) {
            if (!frameStarted) {
                throw new FdxException("Cannot clear before beginFrame()");
            }
            AndroidVulkanNative.clear(context, red, green, blue, alpha);
        }

        ByteBuffer readPixelsRgba8() {
            if (!frameStarted) {
                throw new FdxException("Cannot read pixels before beginFrame()");
            }
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
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            AndroidVulkanNative.destroy(context);
        }

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

    private static final class AndroidVulkanGraphicsDevice implements GraphicsDevice {
        private final AndroidVulkanGraphicsAttachment attachment;

        AndroidVulkanGraphicsDevice(AndroidVulkanGraphicsAttachment attachment) {
            this.attachment = attachment;
        }

        @Override
        public Buffer createBuffer(BufferDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("BufferDescriptor cannot be null");
            }
            return new AndroidVulkanBufferHandle(AndroidVulkanNative.createBuffer(attachment.context,
                    descriptor.size(), toNativeBufferUsage(descriptor.usage())), descriptor.size(), descriptor.usage());
        }

        @Override
        public void writeBuffer(Buffer buffer, ByteBuffer data) {
            if (buffer == null) {
                throw new FdxException("Buffer cannot be null");
            }
            if (data == null) {
                throw new FdxException("Buffer data cannot be null");
            }
            AndroidVulkanBufferHandle vulkanBuffer = buffer.as();
            if (data.remaining() > vulkanBuffer.size()) {
                throw new FdxException("Buffer data is larger than the destination buffer");
            }
            ByteBuffer source = data.position() == 0 ? data : data.slice();
            AndroidVulkanNative.writeBuffer(vulkanBuffer.handle(), source, source.remaining());
        }

        @Override
        public Texture createTexture(TextureDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("TextureDescriptor cannot be null");
            }
            if (descriptor.format() != TextureFormat.RGBA8_UNORM) {
                throw new FdxException("Android Vulkan currently supports RGBA8_UNORM sampled textures only");
            }
            if (descriptor.usage() != TextureUsage.SAMPLED) {
                throw new FdxException("Android Vulkan currently supports sampled textures only");
            }
            return new AndroidVulkanTextureHandle(AndroidVulkanNative.createTexture(attachment.context,
                    descriptor.width(), descriptor.height(), toNativeTextureFormat(descriptor.format()),
                    toNativeWrap(descriptor.wrapS()), toNativeWrap(descriptor.wrapT())),
                    descriptor.width(), descriptor.height(), descriptor.format(), descriptor.usage());
        }

        @Override
        public void writeTexture(Texture texture, ByteBuffer data) {
            if (texture == null) {
                throw new FdxException("Texture cannot be null");
            }
            if (data == null) {
                throw new FdxException("Texture data cannot be null");
            }
            AndroidVulkanTextureHandle vulkanTexture = texture.as();
            int byteCount = vulkanTexture.width() * vulkanTexture.height() * 4;
            if (data.remaining() != byteCount) {
                throw new FdxException("Android Vulkan texture upload expects " + byteCount + " RGBA bytes");
            }
            ByteBuffer source = data.position() == 0 ? data : data.slice();
            AndroidVulkanNative.writeTexture(vulkanTexture.handle(), source, source.remaining());
        }

        @Override
        public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("ShaderModuleDescriptor cannot be null");
            }
            if (!descriptor.hasSource(ShaderLanguage.SPIRV)) {
                throw new FdxException("Android Vulkan requires SPIR-V shader modules");
            }
            return new AndroidVulkanShaderModuleHandle(AndroidVulkanNative.createShaderModule(attachment.context,
                    descriptor.spirvVertexWords(), descriptor.spirvFragmentWords()));
        }

        @Override
        public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("RenderPipelineDescriptor cannot be null");
            }
            if (descriptor.colorFormat() != attachment.surfaceFormat()) {
                throw new FdxException("Android Vulkan render pipeline color format must match the surface format");
            }
            AndroidVulkanShaderModuleHandle shaderModule = descriptor.shaderModule().as();
            boolean pbrUniformsEnabled = usesPbrUniformBlock(descriptor);
            return new AndroidVulkanRenderPipelineHandle(AndroidVulkanNative.createRenderPipeline(attachment.context,
                    shaderModule.handle(), toNativeTopology(descriptor.primitiveTopology()),
                    vertexStride(descriptor.vertexLayout()), attributeLocations(descriptor.vertexLayout()),
                    attributeFormats(descriptor.vertexLayout()), attributeOffsets(descriptor.vertexLayout()),
                    descriptor.sampledTextureCount(), pbrUniformsEnabled, descriptor.depthTestEnabled(),
                    descriptor.depthWriteEnabled()),
                    descriptor.primitiveTopology(), descriptor.sampledTextureCount(),
                    pbrUniformsEnabled, descriptor.sampledTextureCount() > 0 ? 1 : 0);
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

    private static boolean usesPbrUniformBlock(RenderPipelineDescriptor descriptor) {
        // The built-in Vulkan ModelBatch PBR shader declares five sampled texture slots and one PBR uniform block.
        return descriptor.sampledTextureCount() == PBR_TEXTURE_DESCRIPTOR_COUNT;
    }

    private static final class AndroidVulkanCommandEncoder implements CommandEncoder {
        private final AndroidVulkanGraphicsAttachment attachment;

        AndroidVulkanCommandEncoder(AndroidVulkanGraphicsAttachment attachment) {
            this.attachment = attachment;
        }

        @Override
        public RenderPass beginRenderPass(RenderPassDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("RenderPassDescriptor cannot be null");
            }
            LoadOp loadOp = descriptor.colorLoadOp();
            StoreOp storeOp = descriptor.colorStoreOp();
            AndroidVulkanNative.beginRenderPass(attachment.context, loadOp.isClear(), loadOp.red(), loadOp.green(),
                    loadOp.blue(), loadOp.alpha(), storeOp.isStore(), descriptor.depthClearEnabled(),
                    descriptor.depthClearValue());
            return new AndroidVulkanRenderPass(attachment);
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

    private static final class AndroidVulkanRenderPass implements RenderPass {
        private static final int PBR_UNIFORM_BYTE_COUNT = 224;
        private static final int MATRIX_FLOAT_COUNT = 16;
        private static final int MODEL_OFFSET = 0;
        private static final int VIEW_PROJECTION_OFFSET = 16;
        private static final int CAMERA_POSITION_OFFSET = 32;
        private static final int AMBIENT_COLOR_OFFSET = 36;
        private static final int LIGHT_DIRECTION_OFFSET = 40;
        private static final int LIGHT_COLOR_INTENSITY_OFFSET = 44;
        private static final int TEXTURE_FLAGS_OFFSET = 48;
        private static final int EMISSIVE_FLAGS_OFFSET = 52;

        private final AndroidVulkanGraphicsAttachment attachment;
        private final ByteBuffer uniformBytes = ByteBuffer.allocateDirect(PBR_UNIFORM_BYTE_COUNT)
                .order(ByteOrder.nativeOrder());
        private final FloatBuffer uniformFloats = uniformBytes.asFloatBuffer();
        private AndroidVulkanRenderPipelineHandle pipeline;
        private AndroidVulkanBufferHandle indexBuffer;
        private AndroidVulkanTextureHandle[] textures = new AndroidVulkanTextureHandle[0];
        private long[] textureHandles = new long[0];
        private boolean uniformDataDirty = true;
        private boolean hasUniformData;
        private boolean ended;

        AndroidVulkanRenderPass(AndroidVulkanGraphicsAttachment attachment) {
            this.attachment = attachment;
            resetUniformData();
        }

        @Override
        public void setPipeline(RenderPipeline pipeline) {
            ensureOpen();
            this.pipeline = pipeline.as();
            prepareTextureSlots(this.pipeline.sampledTextureCount());
            uniformDataDirty = true;
            AndroidVulkanNative.setPipeline(attachment.context, this.pipeline.handle());
        }

        @Override
        public void setVertexBuffer(Buffer buffer) {
            ensureOpen();
            if (buffer == null) {
                throw new FdxException("Vertex buffer cannot be null");
            }
            AndroidVulkanBufferHandle vulkanBuffer = buffer.as();
            if (vulkanBuffer.usage() != BufferUsage.VERTEX) {
                throw new FdxException("RenderPass.setVertexBuffer requires a vertex buffer");
            }
            AndroidVulkanNative.setVertexBuffer(attachment.context, vulkanBuffer.handle());
        }

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
            AndroidVulkanNative.setIndexBuffer(attachment.context, indexBuffer.handle());
        }

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
                throw new FdxException("Texture slot is not declared by the active Android Vulkan pipeline: " + slot);
            }
            textures[slot] = texture.as();
        }

        @Override
        public void setScissor(int x, int y, int width, int height) {
            ensureOpen();
            if (width <= 0 || height <= 0) {
                throw new FdxException("Scissor size must be greater than zero");
            }
            AndroidVulkanNative.setScissor(attachment.context, x, y, width, height);
        }

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

        @Override
        public void setUniform1f(String name, float value) {
            if ("u_lightIntensity".equals(name)) {
                setUniformFloat(LIGHT_COLOR_INTENSITY_OFFSET + 3, value);
            }
        }

        @Override
        public void setUniform3f(String name, float x, float y, float z) {
            if ("u_cameraPosition".equals(name)) {
                setUniform4f(CAMERA_POSITION_OFFSET, x, y, z, 1.0f);
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
        }

        @Override
        public void setUniform4f(String name, float x, float y, float z, float w) {
            if ("u_cameraPosition".equals(name)) {
                setUniform4f(CAMERA_POSITION_OFFSET, x, y, z, w);
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
        }

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
        }

        @Override
        public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
            ensureOpen();
            if (pipeline == null) {
                throw new FdxException("Render pipeline must be set before draw");
            }
            bindTextures();
            bindUniforms();
            AndroidVulkanNative.draw(attachment.context, vertexCount, instanceCount, firstVertex, firstInstance);
        }

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
            AndroidVulkanNative.drawIndexed(attachment.context, indexCount, instanceCount, firstIndex,
                    baseVertex, firstInstance);
        }

        @Override
        public void end() {
            if (ended) {
                return;
            }
            ended = true;
            AndroidVulkanNative.endRenderPass(attachment.context);
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
                            + " must be set before drawing with Android Vulkan pipeline");
                }
                textureHandles[i] = textures[i].handle();
            }
            AndroidVulkanNative.bindTextures(attachment.context, pipeline.handle(), textureHandles, sampledTextureCount);
        }

        private void prepareTextureSlots(int sampledTextureCount) {
            if (sampledTextureCount == 0) {
                return;
            }
            if (textures.length != sampledTextureCount) {
                textures = new AndroidVulkanTextureHandle[sampledTextureCount];
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
                throw new FdxException("Android Vulkan PBR uniforms must be set before drawing");
            }
            if (uniformDataDirty) {
                AndroidVulkanNative.bindUniforms(attachment.context, pipeline.handle(), uniformBytes,
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

    private static final class AndroidVulkanBufferHandle implements Buffer {
        private final long handle;
        private final int size;
        private final BufferUsage usage;
        private boolean disposed;

        AndroidVulkanBufferHandle(long handle, int size, BufferUsage usage) {
            this.handle = handle;
            this.size = size;
            this.usage = usage != null ? usage : BufferUsage.VERTEX;
        }

        long handle() {
            return handle;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public BufferUsage usage() {
            return usage;
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
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            AndroidVulkanNative.destroyBuffer(handle);
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    private static final class AndroidVulkanTextureHandle implements Texture {
        private final long handle;
        private final int width;
        private final int height;
        private final TextureFormat format;
        private final TextureUsage usage;
        private boolean disposed;

        AndroidVulkanTextureHandle(long handle, int width, int height, TextureFormat format, TextureUsage usage) {
            this.handle = handle;
            this.width = width;
            this.height = height;
            this.format = format != null ? format : TextureFormat.RGBA8_UNORM;
            this.usage = usage != null ? usage : TextureUsage.SAMPLED;
        }

        long handle() {
            return handle;
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
        public TextureFormat format() {
            return format;
        }

        @Override
        public TextureUsage usage() {
            return usage;
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
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            AndroidVulkanNative.destroyTexture(handle);
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

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
            return attachment.width;
        }

        @Override
        public int height() {
            return attachment.height;
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

    private static final class AndroidVulkanFrameBuffer implements FrameBuffer {
        private final AndroidVulkanGraphicsAttachment attachment;
        private final TextureView colorAttachment;

        AndroidVulkanFrameBuffer(AndroidVulkanGraphicsAttachment attachment, TextureView colorAttachment) {
            this.attachment = attachment;
            this.colorAttachment = colorAttachment;
        }

        @Override
        public TextureView colorAttachment() {
            return colorAttachment;
        }

        @Override
        public TextureFormat format() {
            return attachment.surfaceFormat;
        }

        @Override
        public int width() {
            return attachment.width;
        }

        @Override
        public int height() {
            return attachment.height;
        }

        @Override
        public ByteBuffer readPixelsRgba8() {
            return attachment.readPixelsRgba8();
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

    private static final class AndroidVulkanTextureViewHandle implements TextureView {
        private final AndroidVulkanGraphicsAttachment attachment;

        AndroidVulkanTextureViewHandle(AndroidVulkanGraphicsAttachment attachment) {
            this.attachment = attachment;
        }

        @Override
        public TextureFormat format() {
            return attachment.surfaceFormat;
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

    private static final class AndroidVulkanShaderModuleHandle implements ShaderModule {
        private final long handle;
        private boolean disposed;

        AndroidVulkanShaderModuleHandle(long handle) {
            this.handle = handle;
        }

        long handle() {
            return handle;
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
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            AndroidVulkanNative.destroyShaderModule(handle);
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    private static final class AndroidVulkanRenderPipelineHandle implements RenderPipeline {
        private final long handle;
        private final PrimitiveTopology primitiveTopology;
        private final int sampledTextureCount;
        private final boolean uniformBufferEnabled;
        private final int uniformDescriptorSetIndex;
        private boolean disposed;

        AndroidVulkanRenderPipelineHandle(long handle, PrimitiveTopology primitiveTopology, int sampledTextureCount,
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
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            AndroidVulkanNative.destroyRenderPipeline(handle);
        }

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

    private static int vertexStride(VertexLayout layout) {
        return layout != null ? layout.arrayStride() : 0;
    }

    private static int[] attributeLocations(VertexLayout layout) {
        if (layout == null) {
            return new int[0];
        }
        VertexAttribute[] attributes = layout.attributes();
        int[] locations = new int[attributes.length];
        for (int i = 0; i < attributes.length; i++) {
            locations[i] = attributes[i].location();
        }
        return locations;
    }

    private static int[] attributeFormats(VertexLayout layout) {
        if (layout == null) {
            return new int[0];
        }
        VertexAttribute[] attributes = layout.attributes();
        int[] formats = new int[attributes.length];
        for (int i = 0; i < attributes.length; i++) {
            formats[i] = toNativeFormat(attributes[i].format());
        }
        return formats;
    }

    private static int[] attributeOffsets(VertexLayout layout) {
        if (layout == null) {
            return new int[0];
        }
        VertexAttribute[] attributes = layout.attributes();
        int[] offsets = new int[attributes.length];
        for (int i = 0; i < attributes.length; i++) {
            offsets[i] = attributes[i].offset();
        }
        return offsets;
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
