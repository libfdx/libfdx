package io.github.libfdx.graphics.g2d;

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
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.VertexStepMode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Represents a sprite batch.
 *
 * @author xpenatan
 */
public final class SpriteBatch implements Batch2D {
    private static final int FLOATS_PER_VERTEX = 8;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final int VERTICES_PER_SPRITE = 6;
    private static final int INDEXED_VERTICES_PER_SPRITE = 4;
    private static final int INDICES_PER_SPRITE = 6;
    private static final int BYTES_PER_INDEX = 2;
    private static final int MAX_INDEXED_SPRITES = 65535 / INDEXED_VERTICES_PER_SPRITE;
    private static final int DEFAULT_MAX_SPRITES = 1024;
    private static final VertexLayout SPRITE_VERTEX_LAYOUT = VertexLayout.of(
            BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8),
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 16));
    private static final int WHITE_FLOATS_PER_VERTEX = 4;
    private static final int WHITE_BYTES_PER_VERTEX = WHITE_FLOATS_PER_VERTEX * 4;
    private static final VertexLayout WHITE_SPRITE_VERTEX_LAYOUT = VertexLayout.of(
            WHITE_BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8));
    private static final int INSTANCED_INDEXED_QUAD_VERTICES = 4;
    private static final int INSTANCED_NON_INDEXED_QUAD_VERTICES = 6;
    private static final int INSTANCED_INDICES = 6;
    private static final int INSTANCED_QUAD_FLOATS_PER_VERTEX = 8;
    private static final int INSTANCED_QUAD_BYTES_PER_VERTEX = INSTANCED_QUAD_FLOATS_PER_VERTEX * 4;
    private static final int INSTANCED_CENTER_FLOATS_PER_SPRITE = 2;
    private static final int INSTANCED_CENTER_BYTES_PER_SPRITE = INSTANCED_CENTER_FLOATS_PER_SPRITE * 4;
    private static final int INSTANCE_FLOATS_PER_SPRITE = 14;
    private static final int INSTANCE_BYTES_PER_SPRITE = INSTANCE_FLOATS_PER_SPRITE * 4;
    private static final VertexLayout INSTANCED_QUAD_VERTEX_LAYOUT = VertexLayout.of(
            INSTANCED_QUAD_BYTES_PER_VERTEX,
            VertexStepMode.VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8),
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 16));
    private static final VertexLayout INSTANCED_CENTER_VERTEX_LAYOUT = VertexLayout.instance(
            INSTANCED_CENTER_BYTES_PER_SPRITE,
            VertexAttribute.of(3, VertexFormat.FLOAT32X2, 0));
    private static final VertexLayout INSTANCED_SPRITE_VERTEX_LAYOUT = VertexLayout.instance(
            INSTANCE_BYTES_PER_SPRITE,
            VertexAttribute.of(0, VertexFormat.FLOAT32X4, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X4, 16),
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 32),
            VertexAttribute.of(3, VertexFormat.FLOAT32X2, 48));
    private static final String SPRITE_SHADER_SOURCE = """
            struct VertexInput {
                @location(0) position : vec2f,
                @location(1) texCoord : vec2f,
                @location(2) color : vec4f,
            };
            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) texCoord : vec2f,
                @location(1) color : vec4f,
            };
            @group(0) @binding(0) var u_texture : texture_2d<f32>;
            @group(0) @binding(1) var u_sampler : sampler;
            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4f(input.position, 0.0, 1.0);
                output.texCoord = input.texCoord;
                output.color = input.color;
                return output;
            }
            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                return textureSample(u_texture, u_sampler, input.texCoord) * input.color;
            }
            """;
    private static final String WHITE_SPRITE_SHADER_SOURCE = """
            struct VertexInput {
                @location(0) position : vec2f,
                @location(1) texCoord : vec2f,
            };
            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) texCoord : vec2f,
            };
            @group(0) @binding(0) var u_texture : texture_2d<f32>;
            @group(0) @binding(1) var u_sampler : sampler;
            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4f(input.position, 0.0, 1.0);
                output.texCoord = input.texCoord;
                return output;
            }
            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                return textureSample(u_texture, u_sampler, input.texCoord);
            }
            """;
    private static final String INSTANCED_SPRITE_SHADER_SOURCE = """
            struct VertexInput {
                @location(0) baseAndEdgeX : vec4f,
                @location(1) edgeYAndUvBase : vec4f,
                @location(2) uvSizeAndColorRG : vec4f,
                @location(3) colorBA : vec2f,
            };
            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) texCoord : vec2f,
                @location(1) color : vec4f,
            };
            @group(0) @binding(0) var u_texture : texture_2d<f32>;
            @group(0) @binding(1) var u_sampler : sampler;
            @vertex
            fn vertexMain(@builtin(vertex_index) vertexIndex : u32, input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                let cornerIndex = vertexIndex;
                let corner = vec2f(
                    select(0.0, 1.0, cornerIndex == 2u || cornerIndex == 4u || cornerIndex == 5u),
                    select(0.0, 1.0, cornerIndex == 1u || cornerIndex == 2u || cornerIndex == 4u));
                let basePosition = vec2f(input.baseAndEdgeX.x, input.baseAndEdgeX.y);
                let edgeX = vec2f(input.baseAndEdgeX.z, input.baseAndEdgeX.w);
                let edgeY = vec2f(input.edgeYAndUvBase.x, input.edgeYAndUvBase.y);
                let uvBase = vec2f(input.edgeYAndUvBase.z, input.edgeYAndUvBase.w);
                let uvSize = vec2f(input.uvSizeAndColorRG.x, input.uvSizeAndColorRG.y);
                let color = vec4f(input.uvSizeAndColorRG.z, input.uvSizeAndColorRG.w,
                    input.colorBA.x, input.colorBA.y);
                let position = basePosition + edgeX * corner.x + edgeY * corner.y;
                output.position = vec4f(position, 0.0, 1.0);
                output.texCoord = uvBase + uvSize * corner;
                output.color = color;
                return output;
            }
            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                return textureSample(u_texture, u_sampler, input.texCoord) * input.color;
            }
            """;
    private static final String COMPACT_INSTANCED_SPRITE_SHADER_SOURCE = """
            struct VertexInput {
                @location(0) localPosition : vec2f,
                @location(1) texCoord : vec2f,
                @location(2) color : vec4f,
                @location(3) center : vec2f,
            };
            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) texCoord : vec2f,
                @location(1) color : vec4f,
            };
            @group(0) @binding(0) var u_texture : texture_2d<f32>;
            @group(0) @binding(1) var u_sampler : sampler;
            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4f(input.center + input.localPosition, 0.0, 1.0);
                output.texCoord = input.texCoord;
                output.color = input.color;
                return output;
            }
            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                return textureSample(u_texture, u_sampler, input.texCoord) * input.color;
            }
            """;

    private final GraphicsContext graphics;
    private final ShaderModule shader;
    private final RenderPipeline pipeline;
    private final ShaderModule whiteShader;
    private final RenderPipeline whitePipeline;
    private final boolean heapUploadBuffers;
    private final boolean indexed;
    private final ShaderModule instancedShader;
    private final RenderPipeline instancedPipeline;
    private final ShaderModule compactInstancedShader;
    private final RenderPipeline compactInstancedPipeline;
    private final boolean instanced;
    private final boolean instancedIndexed;
    private final int instancedQuadVertexCount;
    private final RenderPassDescriptor renderPassDescriptor =
            new RenderPassDescriptor().label("sprite batch pass");
    private float[] vertices;
    private float[] instances;
    private float[] compactInstances;
    private int floatCount;
    private int instanceFloatCount;
    private int compactInstanceFloatCount;
    private Buffer[] vertexBuffers;
    private Buffer indexBuffer;
    private Buffer[] instancedQuadBuffers;
    private Buffer[] instanceBuffers;
    private Buffer[] compactInstanceBuffers;
    private Buffer[] retiredBuffers;
    private Buffer instancedIndexBuffer;
    private ByteBuffer uploadBuffer;
    private ByteBuffer indexUploadBuffer;
    private ByteBuffer instancedQuadUploadBuffer;
    private ByteBuffer instanceUploadBuffer;
    private ByteBuffer compactInstanceUploadBuffer;
    private ByteBuffer instancedIndexUploadBuffer;
    private FloatBuffer uploadFloats;
    private FloatBuffer instancedQuadUploadFloats;
    private FloatBuffer instanceUploadFloats;
    private FloatBuffer compactInstanceUploadFloats;
    private int vertexCount;
    private int indexCount;
    private int spriteCount;
    private int instanceCount;
    private int compactInstanceCount;
    private int indexBufferSpriteCapacity;
    private int vertexBufferSlot;
    private int instancedQuadBufferSlot;
    private int instanceBufferSlot;
    private int compactInstanceBufferSlot;
    private int retiredBufferCount;
    private RenderPass pass;
    private RenderPass flushSlotPass;
    private Texture currentTexture;
    private boolean ownsPass;
    private boolean drawing;
    private boolean disposed;
    private boolean batchUsesColor = true;
    private float red = 1.0f;
    private float green = 1.0f;
    private float blue = 1.0f;
    private float alpha = 1.0f;
    private int viewportWidth;
    private int viewportHeight;
    private boolean hasTransformCache;
    private float cachedWidth;
    private float cachedHeight;
    private float cachedOriginX;
    private float cachedOriginY;
    private float cachedRotationDegrees;
    private float cachedViewportScaleX;
    private float cachedViewportScaleY;
    private float cachedX1;
    private float cachedY1;
    private float cachedX2;
    private float cachedY2;
    private float cachedX3;
    private float cachedY3;
    private float cachedX4;
    private float cachedY4;
    private boolean compactBatchStateSet;
    private float compactX1;
    private float compactY1;
    private float compactX2;
    private float compactY2;
    private float compactX3;
    private float compactY3;
    private float compactX4;
    private float compactY4;
    private float compactU;
    private float compactV;
    private float compactU2;
    private float compactV2;
    private float compactRed;
    private float compactGreen;
    private float compactBlue;
    private float compactAlpha;

    /**
     * Creates a sprite batch.
     *
     * @param graphicsSystem the graphics system
     */
    public SpriteBatch(GraphicsContext graphicsSystem) {
        this(graphicsSystem, DEFAULT_MAX_SPRITES);
    }

    /**
     * Creates a sprite batch.
     *
     * @param graphicsSystem the graphics system
     * @param initialMaxSprites the initial max sprites
     */
    public SpriteBatch(GraphicsContext graphicsSystem, int initialMaxSprites) {
        if (graphicsSystem == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        if (initialMaxSprites <= 0) {
            throw new FdxException("SpriteBatch initial sprite count must be greater than zero");
        }
        graphics = graphicsSystem;
        heapUploadBuffers = usesHeapUploadBuffers(graphicsSystem);
        indexed = supportsIndexedSprites(graphics);
        instanced = supportsInstancedSprites(graphics);
        instancedIndexed = instanced && supportsIndexedInstancedSprites(graphics);
        instancedQuadVertexCount = instancedIndexed ? INSTANCED_INDEXED_QUAD_VERTICES : INSTANCED_NON_INDEXED_QUAD_VERTICES;
        vertices = new float[initialMaxSprites * VERTICES_PER_SPRITE * FLOATS_PER_VERTEX];
        instances = new float[initialMaxSprites * INSTANCE_FLOATS_PER_SPRITE];
        compactInstances = new float[initialMaxSprites * INSTANCED_CENTER_FLOATS_PER_SPRITE];
        shader = graphics.device().createShaderModule(shaderDescriptor("sprite batch", SPRITE_SHADER_SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shader, graphics.surfaceFormat())
                .label("sprite batch")
                .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                .vertexEntryPoint("vertexMain")
                .fragmentEntryPoint("fragmentMain")
                .vertexLayout(SPRITE_VERTEX_LAYOUT)
                .sampledTextureCount(1));
        if (supportsWhitePipeline(graphics)) {
            whiteShader = graphics.device().createShaderModule(shaderDescriptor("white sprite batch",
                    WHITE_SPRITE_SHADER_SOURCE));
            whitePipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                    .shader(whiteShader, graphics.surfaceFormat())
                    .label("white sprite batch")
                    .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                    .vertexEntryPoint("vertexMain")
                    .fragmentEntryPoint("fragmentMain")
                    .vertexLayout(WHITE_SPRITE_VERTEX_LAYOUT)
                    .sampledTextureCount(1));
        } else {
            whiteShader = null;
            whitePipeline = null;
        }
        if (instanced) {
            instancedShader = graphics.device().createShaderModule(shaderDescriptor("instanced sprite batch",
                    INSTANCED_SPRITE_SHADER_SOURCE));
            instancedPipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                    .shader(instancedShader, graphics.surfaceFormat())
                    .label("instanced sprite batch")
                    .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                    .vertexEntryPoint("vertexMain")
                    .fragmentEntryPoint("fragmentMain")
                    .vertexLayout(INSTANCED_SPRITE_VERTEX_LAYOUT)
                    .sampledTextureCount(1));
            instanceBuffers = ensureBufferSlots(instanceBuffers, 0);
            instanceBuffers[0] = ensureBuffer(instanceBuffers[0],
                    initialMaxSprites * INSTANCE_BYTES_PER_SPRITE, "sprite batch instances");
            compactInstancedShader = graphics.device().createShaderModule(shaderDescriptor("compact instanced sprite "
                    + "batch", COMPACT_INSTANCED_SPRITE_SHADER_SOURCE));
            compactInstancedPipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                    .shader(compactInstancedShader, graphics.surfaceFormat())
                    .label("compact instanced sprite batch")
                    .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                    .vertexEntryPoint("vertexMain")
                    .fragmentEntryPoint("fragmentMain")
                    .vertexLayouts(INSTANCED_QUAD_VERTEX_LAYOUT, INSTANCED_CENTER_VERTEX_LAYOUT)
                    .sampledTextureCount(1));
            compactInstanceBuffers = ensureBufferSlots(compactInstanceBuffers, 0);
            compactInstanceBuffers[0] = ensureBuffer(compactInstanceBuffers[0],
                    initialMaxSprites * INSTANCED_CENTER_BYTES_PER_SPRITE, "sprite batch compact instances");
            if (instancedIndexed) {
                ensureInstancedIndexBuffer();
            }
        } else {
            instancedShader = null;
            instancedPipeline = null;
            compactInstancedShader = null;
            compactInstancedPipeline = null;
        }
        int initialByteCount = initialMaxSprites * VERTICES_PER_SPRITE * BYTES_PER_VERTEX;
        ensureVertexBuffer(0, initialByteCount);
        ensureUploadBuffer(initialByteCount);
        if (indexed) {
            ensureIndexBuffer(Math.min(initialMaxSprites, MAX_INDEXED_SPRITES));
        }
    }

    /**
     * Begins the operation.
     */
    @Override
    public void begin() {
        begin(LoadOp.load());
    }

    /**
     * Begins the operation.
     *
     * @param loadOp the load op
     */
    @Override
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
        flushSlotPass = pass;
    }

    /**
     * Begins the operation.
     *
     * @param pass the pass
     */
    @Override
    public void begin(RenderPass pass) {
        ensureNotDisposed();
        if (pass == null) {
            throw new FdxException("RenderPass cannot be null");
        }
        this.pass = pass;
        if (flushSlotPass != pass) {
            resetFlushBufferSlots();
            flushSlotPass = pass;
        }
        ownsPass = false;
        drawing = true;
    }

    /**
     * Sets the color and returns this sprite batch.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     * @return this sprite batch for chaining
     */
    @Override
    public SpriteBatch color(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        return this;
    }

    /**
     * Sets the viewport and returns this sprite batch.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return this sprite batch for chaining
     */
    @Override
    public SpriteBatch viewport(int width, int height) {
        viewportWidth = Math.max(0, width);
        viewportHeight = Math.max(0, height);
        return this;
    }

    /**
     * Draws the current content.
     *
     * @param texture the texture
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void draw(Texture texture, float x, float y, float width, float height) {
        draw(new TextureRegion(texture), x, y, width, height);
    }

    /**
     * Draws the current content.
     *
     * @param texture the texture
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     * @param originX the origin x
     * @param originY the origin y
     * @param rotationDegrees the rotation degrees
     */
    @Override
    public void draw(Texture texture, float x, float y, float width, float height,
            float originX, float originY, float rotationDegrees) {
        draw(new TextureRegion(texture), x, y, width, height, originX, originY, rotationDegrees);
    }

    /**
     * Draws the current content.
     *
     * @param region the region
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void draw(TextureRegion region, float x, float y, float width, float height) {
        draw(region, x, y, width, height, 0.0f, 0.0f, 0.0f);
    }

    /**
     * Draws the current content.
     *
     * @param region the region
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     * @param originX the origin x
     * @param originY the origin y
     * @param rotationDegrees the rotation degrees
     */
    @Override
    public void draw(TextureRegion region, float x, float y, float width, float height,
            float originX, float originY, float rotationDegrees) {
        ensureDrawing();
        if (region == null) {
            throw new FdxException("TextureRegion cannot be null");
        }
        if (currentTexture != null && currentTexture != region.texture()) {
            flush();
        }
        currentTexture = region.texture();
        float worldOriginX = x + originX;
        float worldOriginY = y + originY;
        float scaleX = viewportWidth > 0 ? viewportWidth * 0.5f : 1.0f;
        float scaleY = viewportHeight > 0 ? viewportHeight * 0.5f : 1.0f;
        updateTransformCache(width, height, originX, originY, rotationDegrees, scaleX, scaleY);

        float u = region.u();
        float v = region.v();
        float u2 = region.u2();
        float v2 = region.v2();
        float x1 = cachedX1 + worldOriginX;
        float y1 = cachedY1 + worldOriginY;
        float x2 = cachedX2 + worldOriginX;
        float y2 = cachedY2 + worldOriginY;
        float x3 = cachedX3 + worldOriginX;
        float y3 = cachedY3 + worldOriginY;
        float x4 = cachedX4 + worldOriginX;
        float y4 = cachedY4 + worldOriginY;

        if (instanced) {
            if (vertexCount > 0 || compactInstanceCount > 0) {
                flush();
            }
            appendInstance(x1, y1, x2, y2, x4, y4, u, v, u2, v2);
            return;
        }

        prepareBatchForCurrentColor();
        if (instanceCount > 0 || compactInstanceCount > 0) {
            flush();
        }

        appendQuad(x1, y1, x2, y2, x3, y3, x4, y4, u, v, u2, v2);
    }

    /**
     * Draws the current content.
     *
     * @param region the region
     * @param centerX the center x
     * @param centerY the center y
     * @param count the count
     * @param width the width in pixels
     * @param height the height in pixels
     * @param originX the origin x
     * @param originY the origin y
     * @param rotationDegrees the rotation degrees
     */
    @Override
    public void draw(TextureRegion region, float[] centerX, float[] centerY, int count, float width, float height,
            float originX, float originY, float rotationDegrees) {
        ensureDrawing();
        if (region == null) {
            throw new FdxException("TextureRegion cannot be null");
        }
        if (centerX == null || centerY == null) {
            throw new FdxException("Sprite center arrays cannot be null");
        }
        if (count < 0 || count > centerX.length || count > centerY.length) {
            throw new FdxException("Sprite center count is outside the provided arrays");
        }
        if (count == 0) {
            return;
        }
        if (!instanced) {
            for (int i = 0; i < count; i++) {
                draw(region, centerX[i] - originX, centerY[i] - originY, width, height, originX, originY,
                        rotationDegrees);
            }
            return;
        }
        if (currentTexture != null && currentTexture != region.texture()) {
            flush();
        }
        currentTexture = region.texture();
        if (vertexCount > 0 || instanceCount > 0) {
            flush();
        }
        float scaleX = viewportWidth > 0 ? viewportWidth * 0.5f : 1.0f;
        float scaleY = viewportHeight > 0 ? viewportHeight * 0.5f : 1.0f;
        updateTransformCache(width, height, originX, originY, rotationDegrees, scaleX, scaleY);
        prepareCompactBatch(region.u(), region.v(), region.u2(), region.v2());
        appendCompactInstances(centerX, centerY, count);
    }

    /**
     * Ends the operation.
     */
    @Override
    public void end() {
        ensureDrawing();
        flush();
        drawing = false;
        if (ownsPass) {
            pass.end();
            flushSlotPass = null;
        }
        ownsPass = false;
        pass = null;
        currentTexture = null;
    }

    private void appendQuad(float x1, float y1, float x2, float y2, float x3, float y3,
            float x4, float y4, float u, float v, float u2, float v2) {
        if (indexed && spriteCount == MAX_INDEXED_SPRITES) {
            flush();
        }
        int floatsPerVertex = batchUsesColor ? FLOATS_PER_VERTEX : WHITE_FLOATS_PER_VERTEX;
        int verticesPerSprite = indexed ? INDEXED_VERTICES_PER_SPRITE : VERTICES_PER_SPRITE;
        vertices = ensureFloatCapacity(vertices, floatCount + verticesPerSprite * floatsPerVertex);
        float r = red;
        float g = green;
        float b = blue;
        float a = alpha;
        float[] values = vertices;
        int index = floatCount;

        values[index++] = x1;
        values[index++] = y1;
        values[index++] = u;
        values[index++] = v2;
        if (batchUsesColor) {
            values[index++] = r;
            values[index++] = g;
            values[index++] = b;
            values[index++] = a;
        }

        values[index++] = x2;
        values[index++] = y2;
        values[index++] = u;
        values[index++] = v;
        if (batchUsesColor) {
            values[index++] = r;
            values[index++] = g;
            values[index++] = b;
            values[index++] = a;
        }

        values[index++] = x3;
        values[index++] = y3;
        values[index++] = u2;
        values[index++] = v;
        if (batchUsesColor) {
            values[index++] = r;
            values[index++] = g;
            values[index++] = b;
            values[index++] = a;
        }

        if (!indexed) {
            values[index++] = x1;
            values[index++] = y1;
            values[index++] = u;
            values[index++] = v2;
            if (batchUsesColor) {
                values[index++] = r;
                values[index++] = g;
                values[index++] = b;
                values[index++] = a;
            }

            values[index++] = x3;
            values[index++] = y3;
            values[index++] = u2;
            values[index++] = v;
            if (batchUsesColor) {
                values[index++] = r;
                values[index++] = g;
                values[index++] = b;
                values[index++] = a;
            }
        }

        values[index++] = x4;
        values[index++] = y4;
        values[index++] = u2;
        values[index++] = v2;
        if (batchUsesColor) {
            values[index++] = r;
            values[index++] = g;
            values[index++] = b;
            values[index++] = a;
        }

        floatCount = index;
        vertexCount += verticesPerSprite;
        if (indexed) {
            indexCount += INDICES_PER_SPRITE;
        }
        spriteCount++;
    }

    private void appendInstance(float x1, float y1, float x2, float y2, float x4, float y4,
            float u, float v, float u2, float v2) {
        instances = ensureFloatCapacity(instances, instanceFloatCount + INSTANCE_FLOATS_PER_SPRITE);
        float[] values = instances;
        int index = instanceFloatCount;
        values[index++] = x1;
        values[index++] = y1;
        values[index++] = x4 - x1;
        values[index++] = y4 - y1;
        values[index++] = x2 - x1;
        values[index++] = y2 - y1;
        values[index++] = u;
        values[index++] = v2;
        values[index++] = u2 - u;
        values[index++] = v - v2;
        values[index++] = red;
        values[index++] = green;
        values[index++] = blue;
        values[index++] = alpha;
        instanceFloatCount = index;
        instanceCount++;
    }

    private void prepareCompactBatch(float u, float v, float u2, float v2) {
        if (compactBatchStateSet && compactInstanceCount > 0 && !matchesCompactBatch(u, v, u2, v2)) {
            flush();
        }
        if (!compactBatchStateSet || !matchesCompactBatch(u, v, u2, v2)) {
            compactBatchStateSet = true;
            compactX1 = cachedX1;
            compactY1 = cachedY1;
            compactX2 = cachedX2;
            compactY2 = cachedY2;
            compactX3 = cachedX3;
            compactY3 = cachedY3;
            compactX4 = cachedX4;
            compactY4 = cachedY4;
            compactU = u;
            compactV = v;
            compactU2 = u2;
            compactV2 = v2;
            compactRed = red;
            compactGreen = green;
            compactBlue = blue;
            compactAlpha = alpha;
        }
    }

    private boolean matchesCompactBatch(float u, float v, float u2, float v2) {
        return compactX1 == cachedX1
                && compactY1 == cachedY1
                && compactX2 == cachedX2
                && compactY2 == cachedY2
                && compactX3 == cachedX3
                && compactY3 == cachedY3
                && compactX4 == cachedX4
                && compactY4 == cachedY4
                && compactU == u
                && compactV == v
                && compactU2 == u2
                && compactV2 == v2
                && compactRed == red
                && compactGreen == green
                && compactBlue == blue
                && compactAlpha == alpha;
    }

    private void appendCompactInstances(float[] centerX, float[] centerY, int count) {
        compactInstances = ensureFloatCapacity(compactInstances,
                compactInstanceFloatCount + count * INSTANCED_CENTER_FLOATS_PER_SPRITE);
        float[] values = compactInstances;
        int index = compactInstanceFloatCount;
        for (int i = 0; i < count; i++) {
            values[index++] = centerX[i];
            values[index++] = centerY[i];
        }
        compactInstanceFloatCount = index;
        compactInstanceCount += count;
    }

    private void prepareBatchForCurrentColor() {
        boolean usesColor = whitePipeline == null || !isWhite();
        if (vertexCount > 0 && batchUsesColor != usesColor) {
            flush();
        }
        batchUsesColor = usesColor;
    }

    private boolean isWhite() {
        return red == 1.0f && green == 1.0f && blue == 1.0f && alpha == 1.0f;
    }

    private void updateTransformCache(float width, float height, float originX, float originY,
            float rotationDegrees, float scaleX, float scaleY) {
        if (hasTransformCache
                && cachedWidth == width
                && cachedHeight == height
                && cachedOriginX == originX
                && cachedOriginY == originY
                && cachedRotationDegrees == rotationDegrees
                && cachedViewportScaleX == scaleX
                && cachedViewportScaleY == scaleY) {
            return;
        }
        hasTransformCache = true;
        cachedWidth = width;
        cachedHeight = height;
        cachedOriginX = originX;
        cachedOriginY = originY;
        cachedRotationDegrees = rotationDegrees;
        cachedViewportScaleX = scaleX;
        cachedViewportScaleY = scaleY;

        float localX = -originX;
        float localY = -originY;
        float localX2 = width - originX;
        float localY2 = height - originY;
        float radians = (float) Math.toRadians(rotationDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);

        cachedX1 = rotateX(localX, localY, cos, sin, scaleX, scaleY);
        cachedY1 = rotateY(localX, localY, cos, sin, scaleX, scaleY);
        cachedX2 = rotateX(localX, localY2, cos, sin, scaleX, scaleY);
        cachedY2 = rotateY(localX, localY2, cos, sin, scaleX, scaleY);
        cachedX3 = rotateX(localX2, localY2, cos, sin, scaleX, scaleY);
        cachedY3 = rotateY(localX2, localY2, cos, sin, scaleX, scaleY);
        cachedX4 = rotateX(localX2, localY, cos, sin, scaleX, scaleY);
        cachedY4 = rotateY(localX2, localY, cos, sin, scaleX, scaleY);
    }

    private float rotateX(float x, float y, float cos, float sin, float scaleX, float scaleY) {
        float pixelX = x * scaleX;
        float pixelY = y * scaleY;
        return (pixelX * cos - pixelY * sin) / scaleX;
    }

    private float rotateY(float x, float y, float cos, float sin, float scaleX, float scaleY) {
        float pixelX = x * scaleX;
        float pixelY = y * scaleY;
        return (pixelX * sin + pixelY * cos) / scaleY;
    }

    private void flush() {
        if (vertexCount == 0 && instanceCount == 0 && compactInstanceCount == 0) {
            return;
        }
        if (compactInstanceCount > 0) {
            flushCompactInstances();
        }
        if (instanceCount > 0) {
            flushInstances();
        }
        if (vertexCount == 0) {
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
        pass.setPipeline(batchUsesColor ? pipeline : whitePipeline);
        pass.setTexture(0, currentTexture);
        pass.setVertexBuffer(activeVertexBuffer);
        if (indexed) {
            ensureIndexBuffer(spriteCount);
            pass.setIndexBuffer(indexBuffer);
            pass.drawIndexed(indexCount, 1, 0, 0, 0);
        } else {
            pass.draw(vertexCount, 1, 0, 0);
        }
        floatCount = 0;
        vertexCount = 0;
        indexCount = 0;
        spriteCount = 0;
    }

    private void flushInstances() {
        int instanceByteCount = instanceFloatCount * 4;
        Buffer activeInstanceBuffer = nextInstanceBuffer(instanceCount);
        ensureInstanceUploadBuffer(instanceByteCount);
        instanceUploadBuffer.clear();
        instanceUploadFloats.clear();
        instanceUploadFloats.put(instances, 0, instanceFloatCount);
        instanceUploadBuffer.limit(instanceByteCount);
        instanceUploadBuffer.position(0);
        graphics.device().writeBuffer(activeInstanceBuffer, instanceUploadBuffer);
        instanceUploadBuffer.clear();

        pass.setPipeline(instancedPipeline);
        pass.setTexture(0, currentTexture);
        pass.setVertexBuffer(activeInstanceBuffer);
        pass.draw(VERTICES_PER_SPRITE, instanceCount, 0, 0);
        instanceFloatCount = 0;
        instanceCount = 0;
    }

    private void flushCompactInstances() {
        int instanceByteCount = compactInstanceFloatCount * 4;
        Buffer activeInstancedQuadBuffer = nextInstancedQuadBuffer();
        Buffer activeCompactInstanceBuffer = nextCompactInstanceBuffer(compactInstanceCount);
        ensureCompactInstanceUploadBuffer(instanceByteCount);
        uploadInstancedQuadBuffer(activeInstancedQuadBuffer);
        compactInstanceUploadBuffer.clear();
        compactInstanceUploadFloats.clear();
        compactInstanceUploadFloats.put(compactInstances, 0, compactInstanceFloatCount);
        compactInstanceUploadBuffer.limit(instanceByteCount);
        compactInstanceUploadBuffer.position(0);
        graphics.device().writeBuffer(activeCompactInstanceBuffer, compactInstanceUploadBuffer);
        compactInstanceUploadBuffer.clear();

        pass.setPipeline(compactInstancedPipeline);
        pass.setTexture(0, currentTexture);
        pass.setVertexBuffer(0, activeInstancedQuadBuffer);
        pass.setVertexBuffer(1, activeCompactInstanceBuffer);
        if (instancedIndexed) {
            ensureInstancedIndexBuffer();
            pass.setIndexBuffer(instancedIndexBuffer);
            pass.drawIndexed(INSTANCED_INDICES, compactInstanceCount, 0, 0, 0);
        } else {
            pass.draw(instancedQuadVertexCount, compactInstanceCount, 0, 0);
        }
        compactInstanceFloatCount = 0;
        compactInstanceCount = 0;
        compactBatchStateSet = false;
    }

    private Buffer nextVertexBuffer(int byteCount) {
        Buffer buffer = ensureVertexBuffer(vertexBufferSlot, byteCount);
        vertexBufferSlot++;
        return buffer;
    }

    private Buffer ensureVertexBuffer(int slot, int byteCount) {
        vertexBuffers = ensureBufferSlots(vertexBuffers, slot);
        vertexBuffers[slot] = ensureBuffer(vertexBuffers[slot], byteCount, "sprite batch vertices");
        return vertexBuffers[slot];
    }

    private void ensureIndexBuffer(int sprites) {
        if (sprites <= 0 || indexBufferSpriteCapacity >= sprites) {
            return;
        }
        int actualSprites = Math.min(sprites, MAX_INDEXED_SPRITES);
        int byteCount = actualSprites * INDICES_PER_SPRITE * BYTES_PER_INDEX;
        if (indexBuffer != null) {
            retireBuffer(indexBuffer);
        }
        indexBuffer = graphics.device().createBuffer(BufferDescriptor.staticIndex("sprite batch indices", byteCount));
        if (indexUploadBuffer == null || indexUploadBuffer.capacity() < byteCount) {
            indexUploadBuffer = newUploadBuffer(byteCount);
        }
        indexUploadBuffer.clear();
        for (int i = 0; i < actualSprites; i++) {
            int vertex = i * INDEXED_VERTICES_PER_SPRITE;
            indexUploadBuffer.putShort((short) vertex);
            indexUploadBuffer.putShort((short) (vertex + 1));
            indexUploadBuffer.putShort((short) (vertex + 2));
            indexUploadBuffer.putShort((short) vertex);
            indexUploadBuffer.putShort((short) (vertex + 2));
            indexUploadBuffer.putShort((short) (vertex + 3));
        }
        indexUploadBuffer.flip();
        graphics.device().writeBuffer(indexBuffer, indexUploadBuffer);
        indexBufferSpriteCapacity = actualSprites;
    }

    private void uploadInstancedQuadBuffer(Buffer activeInstancedQuadBuffer) {
        int byteCount = instancedQuadVertexCount * INSTANCED_QUAD_BYTES_PER_VERTEX;
        ensureInstancedQuadUploadBuffer(byteCount);
        instancedQuadUploadBuffer.clear();
        instancedQuadUploadFloats.clear();
        putInstancedQuadVertex(compactX1, compactY1, compactU, compactV2);
        putInstancedQuadVertex(compactX2, compactY2, compactU, compactV);
        putInstancedQuadVertex(compactX3, compactY3, compactU2, compactV);
        if (!instancedIndexed) {
            putInstancedQuadVertex(compactX1, compactY1, compactU, compactV2);
            putInstancedQuadVertex(compactX3, compactY3, compactU2, compactV);
        }
        putInstancedQuadVertex(compactX4, compactY4, compactU2, compactV2);
        instancedQuadUploadBuffer.limit(byteCount);
        instancedQuadUploadBuffer.position(0);
        graphics.device().writeBuffer(activeInstancedQuadBuffer, instancedQuadUploadBuffer);
        instancedQuadUploadBuffer.clear();
    }

    private void putInstancedQuadVertex(float x, float y, float u, float v) {
        instancedQuadUploadFloats.put(x).put(y);
        instancedQuadUploadFloats.put(u).put(v);
        instancedQuadUploadFloats.put(compactRed).put(compactGreen).put(compactBlue).put(compactAlpha);
    }

    private Buffer nextInstancedQuadBuffer() {
        int byteCount = instancedQuadVertexCount * INSTANCED_QUAD_BYTES_PER_VERTEX;
        instancedQuadBuffers = ensureBufferSlots(instancedQuadBuffers, instancedQuadBufferSlot);
        instancedQuadBuffers[instancedQuadBufferSlot] = ensureBuffer(instancedQuadBuffers[instancedQuadBufferSlot],
                byteCount, "sprite batch instanced quad");
        Buffer buffer = instancedQuadBuffers[instancedQuadBufferSlot];
        instancedQuadBufferSlot++;
        return buffer;
    }

    private Buffer nextInstanceBuffer(int sprites) {
        int byteCount = sprites * INSTANCE_BYTES_PER_SPRITE;
        instanceBuffers = ensureBufferSlots(instanceBuffers, instanceBufferSlot);
        instanceBuffers[instanceBufferSlot] = ensureBuffer(instanceBuffers[instanceBufferSlot], byteCount,
                "sprite batch instances");
        Buffer buffer = instanceBuffers[instanceBufferSlot];
        instanceBufferSlot++;
        return buffer;
    }

    private Buffer nextCompactInstanceBuffer(int sprites) {
        int byteCount = sprites * INSTANCED_CENTER_BYTES_PER_SPRITE;
        compactInstanceBuffers = ensureBufferSlots(compactInstanceBuffers, compactInstanceBufferSlot);
        compactInstanceBuffers[compactInstanceBufferSlot] = ensureBuffer(
                compactInstanceBuffers[compactInstanceBufferSlot], byteCount, "sprite batch compact instances");
        Buffer buffer = compactInstanceBuffers[compactInstanceBufferSlot];
        compactInstanceBufferSlot++;
        return buffer;
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

    private Buffer ensureBuffer(Buffer buffer, int byteCount, String label) {
        if (buffer != null && buffer.size() >= byteCount) {
            return buffer;
        }
        if (buffer != null) {
            retireBuffer(buffer);
        }
        return graphics.device().createBuffer(BufferDescriptor.vertex(label, byteCount));
    }

    private void retireBuffer(Buffer buffer) {
        if (buffer == null) {
            return;
        }
        if (retiredBuffers == null) {
            retiredBuffers = new Buffer[4];
        } else if (retiredBufferCount == retiredBuffers.length) {
            Buffer[] larger = new Buffer[retiredBuffers.length * 2];
            System.arraycopy(retiredBuffers, 0, larger, 0, retiredBuffers.length);
            retiredBuffers = larger;
        }
        retiredBuffers[retiredBufferCount++] = buffer;
    }

    private void resetFlushBufferSlots() {
        vertexBufferSlot = 0;
        instancedQuadBufferSlot = 0;
        instanceBufferSlot = 0;
        compactInstanceBufferSlot = 0;
    }

    private void disposeBuffers(Buffer[] buffers) {
        if (buffers == null) {
            return;
        }
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] != null) {
                buffers[i].dispose();
                buffers[i] = null;
            }
        }
    }

    private void ensureInstancedIndexBuffer() {
        if (instancedIndexBuffer != null) {
            return;
        }
        instancedIndexBuffer = graphics.device().createBuffer(BufferDescriptor.staticIndex("sprite batch instanced indices",
                INSTANCED_INDICES * BYTES_PER_INDEX));
        if (instancedIndexUploadBuffer == null) {
            instancedIndexUploadBuffer = newUploadBuffer(INSTANCED_INDICES * BYTES_PER_INDEX);
        }
        instancedIndexUploadBuffer.clear();
        instancedIndexUploadBuffer.putShort((short) 0);
        instancedIndexUploadBuffer.putShort((short) 1);
        instancedIndexUploadBuffer.putShort((short) 2);
        instancedIndexUploadBuffer.putShort((short) 0);
        instancedIndexUploadBuffer.putShort((short) 2);
        instancedIndexUploadBuffer.putShort((short) 3);
        instancedIndexUploadBuffer.flip();
        graphics.device().writeBuffer(instancedIndexBuffer, instancedIndexUploadBuffer);
    }

    private static ShaderModuleDescriptor shaderDescriptor(String label, String source) {
        return ShaderModuleDescriptor.wgsl(label, source);
    }

    private boolean supportsIndexedSprites(GraphicsContext graphics) {
        String provider = graphics.providerId().value();
        return "gl".equals(provider) || "vulkan".equals(provider) || "wgpu".equals(provider)
                || "webgl".equals(provider) || "metal".equals(provider);
    }

    private boolean supportsInstancedSprites(GraphicsContext graphics) {
        String provider = graphics.providerId().value();
        return "gl".equals(provider) || "vulkan".equals(provider) || "wgpu".equals(provider);
    }

    private boolean supportsIndexedInstancedSprites(GraphicsContext graphics) {
        String provider = graphics.providerId().value();
        return "gl".equals(provider) || "vulkan".equals(provider) || "wgpu".equals(provider);
    }

    private boolean supportsWhitePipeline(GraphicsContext graphics) {
        return !"vulkan".equals(graphics.providerId().value());
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

    private void ensureInstancedQuadUploadBuffer(int byteCount) {
        if (instancedQuadUploadBuffer != null && instancedQuadUploadBuffer.capacity() >= byteCount) {
            return;
        }
        instancedQuadUploadBuffer = newUploadBuffer(byteCount);
        instancedQuadUploadFloats = instancedQuadUploadBuffer.asFloatBuffer();
    }

    private void ensureInstanceUploadBuffer(int byteCount) {
        if (instanceUploadBuffer != null && instanceUploadBuffer.capacity() >= byteCount) {
            return;
        }
        int next = instanceUploadBuffer != null
                ? instanceUploadBuffer.capacity()
                : INSTANCE_BYTES_PER_SPRITE;
        while (next < byteCount) {
            next *= 2;
        }
        instanceUploadBuffer = newUploadBuffer(next);
        instanceUploadFloats = instanceUploadBuffer.asFloatBuffer();
    }

    private void ensureCompactInstanceUploadBuffer(int byteCount) {
        if (compactInstanceUploadBuffer != null && compactInstanceUploadBuffer.capacity() >= byteCount) {
            return;
        }
        int next = compactInstanceUploadBuffer != null
                ? compactInstanceUploadBuffer.capacity()
                : INSTANCED_CENTER_BYTES_PER_SPRITE;
        while (next < byteCount) {
            next *= 2;
        }
        compactInstanceUploadBuffer = newUploadBuffer(next);
        compactInstanceUploadFloats = compactInstanceUploadBuffer.asFloatBuffer();
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
        if (!drawing) {
            throw new FdxException("SpriteBatch.begin must be called before drawing");
        }
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException("SpriteBatch is disposed");
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
        disposeBuffers(vertexBuffers);
        vertexBuffers = null;
        if (indexBuffer != null) {
            indexBuffer.dispose();
            indexBuffer = null;
        }
        disposeBuffers(instancedQuadBuffers);
        instancedQuadBuffers = null;
        disposeBuffers(instanceBuffers);
        instanceBuffers = null;
        disposeBuffers(compactInstanceBuffers);
        compactInstanceBuffers = null;
        disposeBuffers(retiredBuffers);
        retiredBuffers = null;
        retiredBufferCount = 0;
        if (instancedIndexBuffer != null) {
            instancedIndexBuffer.dispose();
            instancedIndexBuffer = null;
        }
        if (compactInstancedPipeline != null) {
            compactInstancedPipeline.dispose();
        }
        if (compactInstancedShader != null) {
            compactInstancedShader.dispose();
        }
        if (instancedPipeline != null) {
            instancedPipeline.dispose();
        }
        if (instancedShader != null) {
            instancedShader.dispose();
        }
        if (whitePipeline != null) {
            whitePipeline.dispose();
        }
        if (whiteShader != null) {
            whiteShader.dispose();
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
