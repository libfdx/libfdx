package io.github.libfdx.backend.psp;

import io.github.libfdx.backend.psp.natives.PSPMemory;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.ShaderLanguage;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.VertexLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.teavm.interop.Address;

import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_ADD;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_BLEND;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_CLAMP;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_COLOR_8888;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_COLOR_BUFFER_BIT;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_CULL_FACE;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_DEPTH_BUFFER_BIT;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_DEPTH_TEST;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_LINES;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_NEAREST;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_ONE_MINUS_SRC_ALPHA;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_PSM_8888;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_REPEAT;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_SRC_ALPHA;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_TCC_RGBA;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_TEXTURE_2D;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_TEXTURE_32BITF;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_TFX_MODULATE;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_TRANSFORM_2D;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_TRIANGLES;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_VERTEX_32BITF;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.copyTextureData;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.dcacheWritebackInvalidate;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuBlendFunc;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuClear;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuClearColor;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuClearDepth;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuDisable;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuDrawArray;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuEnable;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuTexFilter;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuTexFlush;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuTexFunc;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuTexImage;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuTexMode;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuTexOffset;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuTexScale;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuTexWrap;

public final class PspGraphicsContext implements GraphicsContext {
    public static final ProviderId ID = ProviderId.of("psp");
    public static final int SCREEN_WIDTH = 480;
    public static final int SCREEN_HEIGHT = 272;

    private final PspGraphicsDevice device = new PspGraphicsDevice();
    private final PspGraphicsFrame frame = new PspGraphicsFrame();

    @Override
    public GraphicsDevice device() {
        return device;
    }

    @Override
    public TextureFormat surfaceFormat() {
        return TextureFormat.RGBA8_UNORM;
    }

    @Override
    public GraphicsFrame currentFrame() {
        return frame;
    }

    @Override
    public void clear(float red, float green, float blue, float alpha) {
        sceGuClearColor(color8888(red, green, blue, alpha));
        sceGuClearDepth(0);
        sceGuClear(GU_COLOR_BUFFER_BIT | GU_DEPTH_BUFFER_BIT);
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

    void dispose() {
        frame.dispose();
    }

    static int color8888(float red, float green, float blue, float alpha) {
        int r = channel(red);
        int g = channel(green);
        int b = channel(blue);
        int a = channel(alpha);
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int channel(float value) {
        if (value <= 0.0f) {
            return 0;
        }
        if (value >= 1.0f) {
            return 255;
        }
        return (int) (value * 255.0f + 0.5f);
    }

    private static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    private final class PspGraphicsFrame implements GraphicsFrame, FrameBuffer, TextureView {
        private final PspCommandEncoder commandEncoder = new PspCommandEncoder();

        @Override
        public CommandEncoder commandEncoder() {
            return commandEncoder;
        }

        @Override
        public FrameBuffer frameBuffer() {
            return this;
        }

        @Override
        public TextureView colorAttachment() {
            return this;
        }

        @Override
        public TextureFormat format() {
            return TextureFormat.RGBA8_UNORM;
        }

        @Override
        public int width() {
            return SCREEN_WIDTH;
        }

        @Override
        public int height() {
            return SCREEN_HEIGHT;
        }

        @Override
        public ByteBuffer readPixelsRgba8() {
            throw new FdxException("PSP framebuffer readback is not implemented yet");
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

        void dispose() {
            commandEncoder.dispose();
        }
    }

    private static final class PspGraphicsDevice implements GraphicsDevice {
        @Override
        public Buffer createBuffer(BufferDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("BufferDescriptor cannot be null");
            }
            return new PspBuffer(descriptor.size(), descriptor.usage());
        }

        @Override
        public void writeBuffer(Buffer buffer, ByteBuffer data) {
            PspBuffer pspBuffer = checked(buffer, PspBuffer.class, "buffer");
            if (data == null) {
                throw new FdxException("Buffer data cannot be null");
            }
            data.order(ByteOrder.LITTLE_ENDIAN);
            int position = data.position();
            int byteCount = data.remaining();
            if (byteCount > pspBuffer.size()) {
                throw new FdxException("Buffer data is larger than the destination PSP buffer");
            }
            if ((byteCount & 3) != 0) {
                throw new FdxException("PSP vertex buffers must contain 32-bit float data");
            }
            int floatCount = byteCount / Float.BYTES;
            for (int i = 0; i < floatCount; i++) {
                pspBuffer.floatData[i] = data.getFloat(position + i * Float.BYTES);
            }
            pspBuffer.writtenSize = byteCount;
        }

        @Override
        public Texture createTexture(TextureDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("TextureDescriptor cannot be null");
            }
            if (descriptor.format() != TextureFormat.RGBA8_UNORM) {
                throw new FdxException("PSP currently supports RGBA8_UNORM sampled textures only");
            }
            if (descriptor.usage() != TextureUsage.SAMPLED) {
                throw new FdxException("PSP currently supports sampled textures only");
            }
            if (!isPowerOfTwo(descriptor.width()) || !isPowerOfTwo(descriptor.height())) {
                throw new FdxException("PSP textures must be power-of-two in this first graphics slice");
            }
            if (descriptor.wrapS() == TextureWrap.MIRRORED_REPEAT || descriptor.wrapT() == TextureWrap.MIRRORED_REPEAT) {
                throw new FdxException("PSP mirrored texture wrap is not implemented yet");
            }
            return new PspTexture(descriptor.width(), descriptor.height(), descriptor.format(), descriptor.usage(),
                    descriptor.wrapS(), descriptor.wrapT());
        }

        @Override
        public void writeTexture(Texture texture, ByteBuffer data) {
            PspTexture pspTexture = checked(texture, PspTexture.class, "texture");
            if (data == null) {
                throw new FdxException("Texture data cannot be null");
            }
            int byteCount = pspTexture.byteCount;
            int position = data.position();
            if (data.remaining() != byteCount) {
                throw new FdxException("PSP texture upload expects " + byteCount + " RGBA bytes");
            }
            pspTexture.upload.clear();
            pspTexture.upload.put(data);
            data.position(position);
            pspTexture.upload.flip();
            copyTextureData(pspTexture.data, pspTexture.upload, byteCount);
        }

        @Override
        public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("ShaderModuleDescriptor cannot be null");
            }
            return new PspShaderModule(descriptor.label(), descriptor.language());
        }

        @Override
        public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("RenderPipelineDescriptor cannot be null");
            }
            PspShaderModule shader = checked(descriptor.shaderModule(), PspShaderModule.class, "shader module");
            if (descriptor.colorFormat() != TextureFormat.RGBA8_UNORM) {
                throw new FdxException("PSP render pipeline color format must be RGBA8_UNORM");
            }
            String label = descriptor.label();
            if ("sprite batch".equals(label)) {
                if (descriptor.primitiveTopology() != PrimitiveTopology.TRIANGLE_LIST) {
                    throw new FdxException("PSP SpriteBatch pipeline must use triangle-list topology");
                }
                validateSpriteLayout(descriptor, 32, 1);
                return new PspRenderPipeline(shader, PspPipelineKind.SPRITE, 32, true, GU_TRIANGLES);
            }
            if ("white sprite batch".equals(label)) {
                if (descriptor.primitiveTopology() != PrimitiveTopology.TRIANGLE_LIST) {
                    throw new FdxException("PSP SpriteBatch pipeline must use triangle-list topology");
                }
                validateSpriteLayout(descriptor, 16, 1);
                return new PspRenderPipeline(shader, PspPipelineKind.WHITE_SPRITE, 16, true, GU_TRIANGLES);
            }
            if ("shape renderer 2d triangles".equals(label)) {
                if (descriptor.primitiveTopology() != PrimitiveTopology.TRIANGLE_LIST) {
                    throw new FdxException("PSP shape triangle pipeline must use triangle-list topology");
                }
                validateShapeLayout(descriptor);
                return new PspRenderPipeline(shader, PspPipelineKind.SHAPE, 24, false, GU_TRIANGLES);
            }
            if ("shape renderer 2d lines".equals(label)) {
                if (descriptor.primitiveTopology() != PrimitiveTopology.LINE_LIST) {
                    throw new FdxException("PSP shape line pipeline must use line-list topology");
                }
                validateShapeLayout(descriptor);
                return new PspRenderPipeline(shader, PspPipelineKind.SHAPE, 24, false, GU_LINES);
            }
            throw new FdxException("Unsupported PSP render pipeline: " + label);
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

        private static void validateSpriteLayout(RenderPipelineDescriptor descriptor, int expectedStride,
                int expectedTextures) {
            VertexLayout layout = descriptor.vertexLayout();
            if (layout == null || layout.arrayStride() != expectedStride) {
                throw new FdxException("Unsupported PSP SpriteBatch vertex layout");
            }
            if (descriptor.sampledTextureCount() != expectedTextures) {
                throw new FdxException("Unsupported PSP SpriteBatch sampled texture count");
            }
        }

        private static void validateShapeLayout(RenderPipelineDescriptor descriptor) {
            VertexLayout layout = descriptor.vertexLayout();
            if (layout == null || layout.arrayStride() != 24) {
                throw new FdxException("Unsupported PSP ShapeRenderer2D vertex layout");
            }
            if (descriptor.sampledTextureCount() != 0) {
                throw new FdxException("PSP ShapeRenderer2D pipeline must not sample textures");
            }
        }
    }

    private static final class PspCommandEncoder implements CommandEncoder {
        private static final int RENDER_PASS_RING_SIZE = 4;
        private final PspRenderPass[] renderPasses = createRenderPassRing();
        private int renderPassIndex;

        @Override
        public RenderPass beginRenderPass(RenderPassDescriptor descriptor) {
            PspRenderPass renderPass = renderPasses[renderPassIndex];
            renderPassIndex = (renderPassIndex + 1) & (RENDER_PASS_RING_SIZE - 1);
            renderPass.begin(descriptor);
            return renderPass;
        }

        void dispose() {
            for (int i = 0; i < renderPasses.length; i++) {
                renderPasses[i].dispose();
            }
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

        private static PspRenderPass[] createRenderPassRing() {
            PspRenderPass[] passes = new PspRenderPass[RENDER_PASS_RING_SIZE];
            for (int i = 0; i < passes.length; i++) {
                passes[i] = new PspRenderPass();
            }
            return passes;
        }
    }

    private static final class PspRenderPass implements RenderPass {
        private PspRenderPipeline pipeline;
        private PspBuffer vertexBuffer;
        private PspTexture texture;
        private ByteBuffer convertedSpriteVertices;
        private ByteBuffer convertedShapeVertices;
        private boolean ended = true;

        void begin(RenderPassDescriptor descriptor) {
            if (descriptor == null) {
                throw new FdxException("RenderPassDescriptor cannot be null");
            }
            LoadOp loadOp = descriptor.colorLoadOp();
            if (loadOp != null && loadOp.isClear()) {
                sceGuClearColor(color8888(loadOp.red(), loadOp.green(), loadOp.blue(), loadOp.alpha()));
                sceGuClear(GU_COLOR_BUFFER_BIT);
            }
            if (descriptor.depthClearEnabled()) {
                sceGuClearDepth(depthClearValue(descriptor.depthClearValue()));
                sceGuClear(GU_DEPTH_BUFFER_BIT);
            }
            pipeline = null;
            vertexBuffer = null;
            texture = null;
            ended = false;
        }

        @Override
        public void setPipeline(RenderPipeline pipeline) {
            ensureOpen();
            this.pipeline = checked(pipeline, PspRenderPipeline.class, "render pipeline");
        }

        @Override
        public void setVertexBuffer(Buffer buffer) {
            ensureOpen();
            vertexBuffer = checked(buffer, PspBuffer.class, "vertex buffer");
            if (vertexBuffer.usage() != BufferUsage.VERTEX) {
                throw new FdxException("PSP RenderPass.setVertexBuffer requires a vertex buffer");
            }
        }

        @Override
        public void setTexture(int slot, Texture texture) {
            ensureOpen();
            if (slot != 0) {
                throw new FdxException("PSP SpriteBatch pipeline supports texture slot 0 only");
            }
            this.texture = checked(texture, PspTexture.class, "texture");
        }

        @Override
        public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
            ensureOpen();
            if (pipeline == null) {
                throw new FdxException("PSP render pipeline must be set before draw");
            }
            if (vertexBuffer == null) {
                throw new FdxException("PSP vertex buffer must be set before draw");
            }
            if (instanceCount != 1 || firstInstance != 0) {
                throw new FdxException("PSP render path does not support instanced draws");
            }
            if (pipeline.textured && texture == null) {
                throw new FdxException("PSP texture must be set before drawing textured pipeline");
            }
            if (pipeline.textured) {
                drawSpriteVertices(vertexCount, firstVertex);
            } else {
                drawShapeVertices(vertexCount, firstVertex);
            }
        }

        @Override
        public void end() {
            ensureOpen();
            ended = true;
            pipeline = null;
            vertexBuffer = null;
            texture = null;
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

        private void drawSpriteVertices(int vertexCount, int firstVertex) {
            int outputBytes = vertexCount * PspSpriteVertex.BYTES;
            ByteBuffer convertedVertices = spriteVertexBuffer(outputBytes);
            int stride = pipeline.stride;
            int sourceOffset = firstVertex * stride;
            if (sourceOffset + vertexCount * stride > vertexBuffer.writtenSize) {
                throw new FdxException("PSP draw range exceeds the written vertex buffer");
            }
            for (int i = 0; i < vertexCount; i++) {
                int offset = sourceOffset + i * stride;
                float x = vertexBuffer.floatAt(offset);
                float y = vertexBuffer.floatAt(offset + 4);
                float u = vertexBuffer.floatAt(offset + 8);
                float v = vertexBuffer.floatAt(offset + 12);
                int color = 0xffffffff;
                if (pipeline.kind == PspPipelineKind.SPRITE) {
                    color = color8888(vertexBuffer.floatAt(offset + 16), vertexBuffer.floatAt(offset + 20),
                            vertexBuffer.floatAt(offset + 24), vertexBuffer.floatAt(offset + 28));
                }
                int targetOffset = i * PspSpriteVertex.BYTES;
                convertedVertices.putFloat(targetOffset, u * texture.width());
                convertedVertices.putFloat(targetOffset + 4, v * texture.height());
                convertedVertices.putInt(targetOffset + 8, color);
                convertedVertices.putFloat(targetOffset + 12, (x + 1.0f) * 0.5f * SCREEN_WIDTH);
                convertedVertices.putFloat(targetOffset + 16, (1.0f - y) * 0.5f * SCREEN_HEIGHT);
                convertedVertices.putFloat(targetOffset + 20, 0.0f);
            }
            dcacheWritebackInvalidate(convertedVertices, outputBytes);
            bindSpriteState();
            sceGuDrawArray(GU_TRIANGLES,
                    GU_TEXTURE_32BITF | GU_COLOR_8888 | GU_VERTEX_32BITF | GU_TRANSFORM_2D,
                    vertexCount, (Address) null, convertedVertices);
        }

        private void drawShapeVertices(int vertexCount, int firstVertex) {
            int outputBytes = vertexCount * PspShapeVertex.BYTES;
            ByteBuffer convertedVertices = shapeVertexBuffer(outputBytes);
            int stride = pipeline.stride;
            int sourceOffset = firstVertex * stride;
            if (sourceOffset + vertexCount * stride > vertexBuffer.writtenSize) {
                throw new FdxException("PSP draw range exceeds the written vertex buffer");
            }
            for (int i = 0; i < vertexCount; i++) {
                int offset = sourceOffset + i * stride;
                float x = vertexBuffer.floatAt(offset);
                float y = vertexBuffer.floatAt(offset + 4);
                int color = color8888(vertexBuffer.floatAt(offset + 8), vertexBuffer.floatAt(offset + 12),
                        vertexBuffer.floatAt(offset + 16), vertexBuffer.floatAt(offset + 20));
                int targetOffset = i * PspShapeVertex.BYTES;
                convertedVertices.putInt(targetOffset, color);
                convertedVertices.putFloat(targetOffset + 4, (x + 1.0f) * 0.5f * SCREEN_WIDTH);
                convertedVertices.putFloat(targetOffset + 8, (1.0f - y) * 0.5f * SCREEN_HEIGHT);
                convertedVertices.putFloat(targetOffset + 12, 0.0f);
            }
            dcacheWritebackInvalidate(convertedVertices, outputBytes);
            bindShapeState();
            sceGuDrawArray(pipeline.guPrimitive,
                    GU_COLOR_8888 | GU_VERTEX_32BITF | GU_TRANSFORM_2D,
                    vertexCount, (Address) null, convertedVertices);
        }

        private void bindSpriteState() {
            sceGuDisable(GU_DEPTH_TEST);
            sceGuDisable(GU_CULL_FACE);
            sceGuEnable(GU_BLEND);
            sceGuBlendFunc(GU_ADD, GU_SRC_ALPHA, GU_ONE_MINUS_SRC_ALPHA, 0, 0);
            sceGuEnable(GU_TEXTURE_2D);
            sceGuTexMode(GU_PSM_8888, 0, 0, 0);
            sceGuTexImage(0, texture.width(), texture.height(), texture.width(), texture.data);
            sceGuTexFunc(GU_TFX_MODULATE, GU_TCC_RGBA);
            sceGuTexFilter(GU_NEAREST, GU_NEAREST);
            sceGuTexWrap(texture.wrapS == TextureWrap.REPEAT ? GU_REPEAT : GU_CLAMP,
                    texture.wrapT == TextureWrap.REPEAT ? GU_REPEAT : GU_CLAMP);
            sceGuTexScale(1.0f, 1.0f);
            sceGuTexOffset(0.0f, 0.0f);
            sceGuTexFlush();
        }

        private void bindShapeState() {
            sceGuDisable(GU_DEPTH_TEST);
            sceGuDisable(GU_CULL_FACE);
            sceGuEnable(GU_BLEND);
            sceGuBlendFunc(GU_ADD, GU_SRC_ALPHA, GU_ONE_MINUS_SRC_ALPHA, 0, 0);
            sceGuDisable(GU_TEXTURE_2D);
        }

        private void ensureOpen() {
            if (ended) {
                throw new FdxException("PSP render pass is not active");
            }
        }

        private ByteBuffer spriteVertexBuffer(int byteCount) {
            if (convertedSpriteVertices == null || convertedSpriteVertices.capacity() < byteCount) {
                convertedSpriteVertices = directBuffer(byteCount);
            }
            return convertedSpriteVertices;
        }

        private ByteBuffer shapeVertexBuffer(int byteCount) {
            if (convertedShapeVertices == null || convertedShapeVertices.capacity() < byteCount) {
                convertedShapeVertices = directBuffer(byteCount);
            }
            return convertedShapeVertices;
        }

        private static ByteBuffer directBuffer(int byteCount) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(byteCount);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            return buffer;
        }

        void dispose() {
        }

        private static int depthClearValue(float depth) {
            if (depth <= 0.0f) {
                return 65535;
            }
            if (depth >= 1.0f) {
                return 0;
            }
            return 65535 - (int) (depth * 65535.0f + 0.5f);
        }
    }

    private enum PspPipelineKind {
        SPRITE,
        WHITE_SPRITE,
        SHAPE
    }

    private static final class PspRenderPipeline implements RenderPipeline {
        private final PspShaderModule shader;
        private final PspPipelineKind kind;
        private final int stride;
        private final boolean textured;
        private final int guPrimitive;
        private boolean disposed;

        PspRenderPipeline(PspShaderModule shader, PspPipelineKind kind, int stride, boolean textured,
                int guPrimitive) {
            this.shader = shader;
            this.kind = kind;
            this.stride = stride;
            this.textured = textured;
            this.guPrimitive = guPrimitive;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed || shader.isDisposed();
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

    private static final class PspShaderModule implements ShaderModule {
        private final String label;
        private final ShaderLanguage language;
        private boolean disposed;

        PspShaderModule(String label, ShaderLanguage language) {
            this.label = label != null ? label : "";
            this.language = language != null ? language : ShaderLanguage.WGSL;
        }

        @Override
        public ShaderLanguage language() {
            return language;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
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
        public String toString() {
            return label;
        }
    }

    private static final class PspBuffer implements Buffer {
        private final int size;
        private final BufferUsage usage;
        private final float[] floatData;
        private int writtenSize;
        private boolean disposed;

        PspBuffer(int size, BufferUsage usage) {
            this.size = size;
            this.usage = usage != null ? usage : BufferUsage.VERTEX;
            floatData = new float[(size + Float.BYTES - 1) / Float.BYTES];
        }

        float floatAt(int byteOffset) {
            return floatData[byteOffset / Float.BYTES];
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
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
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

    private static final class PspTexture implements Texture {
        private final int width;
        private final int height;
        private final TextureFormat format;
        private final TextureUsage usage;
        private final TextureWrap wrapS;
        private final TextureWrap wrapT;
        private final int byteCount;
        private final Address data;
        private final ByteBuffer upload;
        private boolean disposed;

        PspTexture(int width, int height, TextureFormat format, TextureUsage usage, TextureWrap wrapS,
                TextureWrap wrapT) {
            this.width = width;
            this.height = height;
            this.format = format;
            this.usage = usage;
            this.wrapS = wrapS != null ? wrapS : TextureWrap.CLAMP_TO_EDGE;
            this.wrapT = wrapT != null ? wrapT : TextureWrap.CLAMP_TO_EDGE;
            byteCount = width * height * 4;
            data = PSPMemory.memalign(16, byteCount);
            if (data == null) {
                throw new FdxException("Failed to allocate PSP texture memory");
            }
            upload = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.LITTLE_ENDIAN);
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
        public void dispose() {
            if (!disposed) {
                PSPMemory.free(data);
                disposed = true;
            }
        }

        @Override
        public boolean isDisposed() {
            return disposed;
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

    private static final class PspSpriteVertex {
        private static final int BYTES = 2 * Float.BYTES + Integer.BYTES + 3 * Float.BYTES;

        private PspSpriteVertex() {
        }
    }

    private static final class PspShapeVertex {
        private static final int BYTES = Integer.BYTES + 3 * Float.BYTES;

        private PspShapeVertex() {
        }
    }

    private static <T> T checked(Object handle, Class<T> type, String name) {
        if (handle == null) {
            throw new FdxException("PSP " + name + " cannot be null");
        }
        if (!type.isInstance(handle)) {
            throw new FdxException("PSP " + name + " belongs to another provider");
        }
        return type.cast(handle);
    }
}
