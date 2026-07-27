package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Renders textured 2D sprites with a WGSL-authored alpha outline effect.
 *
 * @author xpenatan
 */
public final class SpriteOutlineRenderer2D implements Disposable {
    private static final int FLOATS_PER_VERTEX = 20;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final int VERTICES_PER_SPRITE = 6;
    private static final int DEFAULT_MAX_SPRITES = 128;
    private static final VertexLayout OUTLINE_VERTEX_LAYOUT = VertexLayout.of(
            BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8),
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 16),
            VertexAttribute.of(3, VertexFormat.FLOAT32X4, 32),
            VertexAttribute.of(4, VertexFormat.FLOAT32X4, 48),
            VertexAttribute.of(5, VertexFormat.FLOAT32X4, 64));
    private static final String OUTLINE_SHADER_SOURCE = """
            struct VertexInput {
                @location(0) position : vec2f,
                @location(1) texCoord : vec2f,
                @location(2) color : vec4f,
                @location(3) outlineColor : vec4f,
                @location(4) texelAndWidth : vec4f,
                @location(5) uvBounds : vec4f,
            };

            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) texCoord : vec2f,
                @location(1) color : vec4f,
                @location(2) outlineColor : vec4f,
                @location(3) texelAndWidth : vec4f,
                @location(4) uvBounds : vec4f,
            };

            @group(0) @binding(0) var u_texture : texture_2d<f32>;
            @group(0) @binding(1) var u_sampler : sampler;

            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4f(input.position, 0.0, 1.0);
                output.texCoord = input.texCoord;
                output.color = input.color;
                output.outlineColor = input.outlineColor;
                output.texelAndWidth = input.texelAndWidth;
                output.uvBounds = input.uvBounds;
                return output;
            }

            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                let base = textureSample(u_texture, u_sampler, input.texCoord) * input.color;
                let sampleStep = input.texelAndWidth.xy * input.texelAndWidth.z;
                let uvMin = input.uvBounds.xy;
                let uvMax = input.uvBounds.zw;
                var outlineAlpha = 0.0;
                outlineAlpha = max(outlineAlpha, textureSample(u_texture, u_sampler,
                    clamp(input.texCoord + vec2f(-sampleStep.x, 0.0), uvMin, uvMax)).a);
                outlineAlpha = max(outlineAlpha, textureSample(u_texture, u_sampler,
                    clamp(input.texCoord + vec2f(sampleStep.x, 0.0), uvMin, uvMax)).a);
                outlineAlpha = max(outlineAlpha, textureSample(u_texture, u_sampler,
                    clamp(input.texCoord + vec2f(0.0, -sampleStep.y), uvMin, uvMax)).a);
                outlineAlpha = max(outlineAlpha, textureSample(u_texture, u_sampler,
                    clamp(input.texCoord + vec2f(0.0, sampleStep.y), uvMin, uvMax)).a);
                outlineAlpha = max(outlineAlpha, textureSample(u_texture, u_sampler,
                    clamp(input.texCoord + vec2f(-sampleStep.x, -sampleStep.y), uvMin, uvMax)).a);
                outlineAlpha = max(outlineAlpha, textureSample(u_texture, u_sampler,
                    clamp(input.texCoord + vec2f(sampleStep.x, -sampleStep.y), uvMin, uvMax)).a);
                outlineAlpha = max(outlineAlpha, textureSample(u_texture, u_sampler,
                    clamp(input.texCoord + vec2f(-sampleStep.x, sampleStep.y), uvMin, uvMax)).a);
                outlineAlpha = max(outlineAlpha, textureSample(u_texture, u_sampler,
                    clamp(input.texCoord + vec2f(sampleStep.x, sampleStep.y), uvMin, uvMax)).a);
                let outlineOnly = vec4f(input.outlineColor.rgb,
                    max(outlineAlpha - base.a, 0.0) * input.outlineColor.a);
                if (base.a > 0.01) {
                    return base;
                }
                return outlineOnly;
            }
            """;

    private final GraphicsContext graphics;
    private final boolean heapUploadBuffers;
    private final ShaderModule shader;
    private final RenderPipeline pipeline;
    private final RenderPassDescriptor renderPassDescriptor =
            new RenderPassDescriptor().label("sprite outline renderer 2d pass");
    private float[] vertices;
    private int floatCount;
    private int vertexCount;
    private int spriteCount;
    private Buffer[] vertexBuffers;
    private ByteBuffer uploadBuffer;
    private FloatBuffer uploadFloats;
    private int vertexBufferSlot;
    private RenderPass pass;
    private Texture currentTexture;
    private boolean ownsPass;
    private boolean drawing;
    private boolean disposed;
    private float red = 1.0f;
    private float green = 1.0f;
    private float blue = 1.0f;
    private float alpha = 1.0f;
    private float outlineRed = 0.0f;
    private float outlineGreen = 0.85f;
    private float outlineBlue = 1.0f;
    private float outlineAlpha = 1.0f;
    private float outlineWidth = 2.0f;

    /**
     * Creates a sprite outline renderer.
     *
     * @param graphicsSystem the graphics system
     */
    public SpriteOutlineRenderer2D(GraphicsContext graphicsSystem) {
        this(graphicsSystem, DEFAULT_MAX_SPRITES);
    }

    /**
     * Creates a sprite outline renderer.
     *
     * @param graphicsSystem the graphics system
     * @param initialMaxSprites the initial max sprites
     */
    public SpriteOutlineRenderer2D(GraphicsContext graphicsSystem, int initialMaxSprites) {
        if (graphicsSystem == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        if (initialMaxSprites <= 0) {
            throw new FdxException("SpriteOutlineRenderer2D initial sprite count must be greater than zero");
        }
        graphics = graphicsSystem;
        heapUploadBuffers = usesHeapUploadBuffers(graphicsSystem);
        vertices = new float[initialMaxSprites * VERTICES_PER_SPRITE * FLOATS_PER_VERTEX];
        shader = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("sprite outline renderer 2d", OUTLINE_SHADER_SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shader, graphics.surfaceFormat())
                .label("sprite outline renderer 2d")
                .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                .vertexEntryPoint("vertexMain")
                .fragmentEntryPoint("fragmentMain")
                .vertexLayout(OUTLINE_VERTEX_LAYOUT)
                .sampledTextureCount(1)
                .depthWriteEnabled(false));
        int initialByteCount = initialMaxSprites * VERTICES_PER_SPRITE * BYTES_PER_VERTEX;
        ensureVertexBuffer(0, initialByteCount);
        ensureUploadBuffer(initialByteCount);
    }

    /**
     * Begins the operation.
     */
    public void begin() {
        begin(LoadOp.load());
    }

    /**
     * Begins the operation.
     *
     * @param loadOp the load op
     */
    public void begin(LoadOp loadOp) {
        ensureNotDisposed();
        GraphicsFrame frame = graphics.currentFrame();
        pass = frame.commandEncoder().beginRenderPass(renderPassDescriptor
                .colorAttachment(frame.colorAttachment())
                .colorLoadOp(loadOp != null ? loadOp : LoadOp.load())
                .colorStoreOp(StoreOp.store()));
        ownsPass = true;
        drawing = true;
        resetFlushBufferSlots();
    }

    /**
     * Begins the operation.
     *
     * @param pass the pass
     */
    public void begin(RenderPass pass) {
        ensureNotDisposed();
        if (pass == null) {
            throw new FdxException("RenderPass cannot be null");
        }
        this.pass = pass;
        resetFlushBufferSlots();
        ownsPass = false;
        drawing = true;
    }

    /**
     * Sets the sprite color and returns this renderer.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     * @return this renderer for chaining
     */
    public SpriteOutlineRenderer2D color(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        return this;
    }

    /**
     * Sets the outline color and returns this renderer.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     * @return this renderer for chaining
     */
    public SpriteOutlineRenderer2D outlineColor(float red, float green, float blue, float alpha) {
        outlineRed = red;
        outlineGreen = green;
        outlineBlue = blue;
        outlineAlpha = alpha;
        return this;
    }

    /**
     * Sets the outline width in source texture texels and returns this renderer.
     *
     * @param width the outline width in texels
     * @return this renderer for chaining
     */
    public SpriteOutlineRenderer2D outlineWidth(float width) {
        if (width < 0.0f) {
            throw new FdxException("Sprite outline width cannot be negative");
        }
        outlineWidth = width;
        return this;
    }

    /**
     * Draws a texture region with the current color and outline state.
     *
     * @param region the region
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width
     * @param height the height
     */
    public void draw(TextureRegion region, float x, float y, float width, float height) {
        ensureDrawing();
        if (region == null) {
            throw new FdxException("TextureRegion cannot be null");
        }
        if (width == 0.0f || height == 0.0f) {
            return;
        }
        if (currentTexture != null && currentTexture != region.texture()) {
            flush();
        }
        currentTexture = region.texture();
        appendQuad(region, x, y, width, height);
    }

    /**
     * Ends the operation.
     */
    public void end() {
        ensureDrawing();
        flush();
        drawing = false;
        if (ownsPass) {
            pass.end();
        }
        ownsPass = false;
        pass = null;
    }

    private void appendQuad(TextureRegion region, float x, float y, float width, float height) {
        vertices = ensureFloatCapacity(vertices, floatCount + VERTICES_PER_SPRITE * FLOATS_PER_VERTEX);
        float x1 = x;
        float y1 = y;
        float x2 = x;
        float y2 = y + height;
        float x3 = x + width;
        float y3 = y + height;
        float x4 = x + width;
        float y4 = y;
        float u = region.u();
        float v = region.v();
        float u2 = region.u2();
        float v2 = region.v2();
        float texelX = 1.0f / region.texture().width();
        float texelY = 1.0f / region.texture().height();
        float[] values = vertices;
        int index = floatCount;
        index = appendVertex(values, index, x1, y1, u, v2, texelX, texelY, u, v, u2, v2);
        index = appendVertex(values, index, x2, y2, u, v, texelX, texelY, u, v, u2, v2);
        index = appendVertex(values, index, x3, y3, u2, v, texelX, texelY, u, v, u2, v2);
        index = appendVertex(values, index, x1, y1, u, v2, texelX, texelY, u, v, u2, v2);
        index = appendVertex(values, index, x3, y3, u2, v, texelX, texelY, u, v, u2, v2);
        index = appendVertex(values, index, x4, y4, u2, v2, texelX, texelY, u, v, u2, v2);
        floatCount = index;
        vertexCount += VERTICES_PER_SPRITE;
        spriteCount++;
    }

    private int appendVertex(float[] values, int index, float x, float y, float u, float v,
            float texelX, float texelY, float uMin, float vMin, float uMax, float vMax) {
        values[index++] = x;
        values[index++] = y;
        values[index++] = u;
        values[index++] = v;
        values[index++] = red;
        values[index++] = green;
        values[index++] = blue;
        values[index++] = alpha;
        values[index++] = outlineRed;
        values[index++] = outlineGreen;
        values[index++] = outlineBlue;
        values[index++] = outlineAlpha;
        values[index++] = texelX;
        values[index++] = texelY;
        values[index++] = outlineWidth;
        values[index++] = 0.0f;
        values[index++] = uMin;
        values[index++] = vMin;
        values[index++] = uMax;
        values[index++] = vMax;
        return index;
    }

    private void flush() {
        if (spriteCount == 0) {
            return;
        }
        int byteCount = floatCount * 4;
        Buffer activeVertexBuffer = nextVertexBuffer(byteCount);
        ensureUploadBuffer(byteCount);
        uploadBuffer.clear();
        uploadFloats.clear();
        uploadFloats.put(vertices, 0, floatCount);
        uploadBuffer.limit(byteCount);
        uploadBuffer.position(0);
        graphics.device().writeBuffer(activeVertexBuffer, uploadBuffer);
        uploadBuffer.clear();
        pass.setPipeline(pipeline);
        pass.setTexture(0, currentTexture);
        pass.setVertexBuffer(activeVertexBuffer);
        pass.draw(vertexCount, 1, 0, 0);
        floatCount = 0;
        vertexCount = 0;
        spriteCount = 0;
    }

    private Buffer nextVertexBuffer(int byteCount) {
        Buffer buffer = ensureVertexBuffer(vertexBufferSlot, byteCount);
        vertexBufferSlot++;
        return buffer;
    }

    private Buffer ensureVertexBuffer(int slot, int byteCount) {
        vertexBuffers = ensureBufferSlots(vertexBuffers, slot);
        if (vertexBuffers[slot] != null && vertexBuffers[slot].size() >= byteCount) {
            return vertexBuffers[slot];
        }
        if (vertexBuffers[slot] != null) {
            vertexBuffers[slot].dispose();
        }
        vertexBuffers[slot] = graphics.device().createBuffer(BufferDescriptor.vertex(
                "sprite outline renderer 2d vertices", byteCount));
        return vertexBuffers[slot];
    }

    private Buffer[] ensureBufferSlots(Buffer[] buffers, int slot) {
        if (buffers != null && slot < buffers.length) {
            return buffers;
        }
        int next = buffers != null ? buffers.length : 4;
        while (slot >= next) {
            next *= 2;
        }
        Buffer[] larger = new Buffer[next];
        if (buffers != null) {
            System.arraycopy(buffers, 0, larger, 0, buffers.length);
        }
        return larger;
    }

    private void resetFlushBufferSlots() {
        vertexBufferSlot = 0;
    }

    private void ensureUploadBuffer(int byteCount) {
        if (uploadBuffer != null && uploadBuffer.capacity() >= byteCount) {
            return;
        }
        int next = uploadBuffer != null ? uploadBuffer.capacity() : BYTES_PER_VERTEX;
        while (next < byteCount) {
            next *= 2;
        }
        uploadBuffer = newUploadBuffer(next);
        uploadFloats = uploadBuffer.asFloatBuffer();
    }

    private ByteBuffer newUploadBuffer(int byteCount) {
        ByteBuffer buffer = heapUploadBuffers ? ByteBuffer.allocate(byteCount) : ByteBuffer.allocateDirect(byteCount);
        return buffer.order(ByteOrder.nativeOrder());
    }

    private static boolean usesHeapUploadBuffers(GraphicsContext graphics) {
        return "psp".equals(graphics.providerId().value());
    }

    private float[] ensureFloatCapacity(float[] values, int required) {
        if (values.length >= required) {
            return values;
        }
        int next = values.length;
        while (next < required) {
            next *= 2;
        }
        float[] larger = new float[next];
        System.arraycopy(values, 0, larger, 0, values.length);
        return larger;
    }

    private void ensureDrawing() {
        ensureNotDisposed();
        if (!drawing || pass == null) {
            throw new FdxException("SpriteOutlineRenderer2D.begin() must be called before drawing");
        }
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException("SpriteOutlineRenderer2D has been disposed");
        }
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
        if (vertexBuffers != null) {
            for (int i = 0; i < vertexBuffers.length; i++) {
                if (vertexBuffers[i] != null) {
                    vertexBuffers[i].dispose();
                    vertexBuffers[i] = null;
                }
            }
            vertexBuffers = null;
        }
        pipeline.dispose();
        shader.dispose();
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
