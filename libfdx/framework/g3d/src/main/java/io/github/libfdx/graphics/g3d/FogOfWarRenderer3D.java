package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.camera.Camera;
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
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Renders a WGSL-authored world-space 3D fog-of-war overlay.
 *
 * @author xpenatan
 */
public final class FogOfWarRenderer3D implements Disposable {
    /**
     * Maximum reveal spheres submitted by one draw call.
     */
    public static final int MAX_LIGHTS = 4;

    private static final int LIGHT_FLOATS = 5;
    private static final int FLOATS_PER_VERTEX = 4 + 3 + 4 + MAX_LIGHTS * 4 + MAX_LIGHTS;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final int VERTICES_PER_QUAD = 6;
    private static final int DEFAULT_MAX_QUADS = 8;
    private static final VertexLayout FOG_VERTEX_LAYOUT = VertexLayout.of(
            BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X4, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X3, 16),
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 28),
            VertexAttribute.of(3, VertexFormat.FLOAT32X4, 44),
            VertexAttribute.of(4, VertexFormat.FLOAT32X4, 60),
            VertexAttribute.of(5, VertexFormat.FLOAT32X4, 76),
            VertexAttribute.of(6, VertexFormat.FLOAT32X4, 92),
            VertexAttribute.of(7, VertexFormat.FLOAT32X4, 108));
    private static final String FOG_SHADER_SOURCE = """
            struct VertexInput {
                @location(0) clipPosition : vec4f,
                @location(1) worldPosition : vec3f,
                @location(2) fogColor : vec4f,
                @location(3) light0 : vec4f,
                @location(4) light1 : vec4f,
                @location(5) light2 : vec4f,
                @location(6) light3 : vec4f,
                @location(7) lightSoftness : vec4f,
            };

            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) worldPosition : vec3f,
                @location(1) fogColor : vec4f,
                @location(2) light0 : vec4f,
                @location(3) light1 : vec4f,
                @location(4) light2 : vec4f,
                @location(5) light3 : vec4f,
                @location(6) lightSoftness : vec4f,
            };

            fn fogContribution(worldPosition : vec3f, light : vec4f, softnessValue : f32) -> f32 {
                if (light.w <= 0.0) {
                    return 1.0;
                }
                let softness = max(softnessValue, 0.0001);
                let innerRadius = max(light.w - softness, 0.0);
                let distanceToLight = distance(worldPosition, light.xyz);
                return smoothstep(innerRadius, light.w, distanceToLight);
            }

            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = input.clipPosition;
                output.worldPosition = input.worldPosition;
                output.fogColor = input.fogColor;
                output.light0 = input.light0;
                output.light1 = input.light1;
                output.light2 = input.light2;
                output.light3 = input.light3;
                output.lightSoftness = input.lightSoftness;
                return output;
            }

            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                var fogAlpha = 1.0;
                fogAlpha = min(fogAlpha, fogContribution(input.worldPosition, input.light0,
                        input.lightSoftness.x));
                fogAlpha = min(fogAlpha, fogContribution(input.worldPosition, input.light1,
                        input.lightSoftness.y));
                fogAlpha = min(fogAlpha, fogContribution(input.worldPosition, input.light2,
                        input.lightSoftness.z));
                fogAlpha = min(fogAlpha, fogContribution(input.worldPosition, input.light3,
                        input.lightSoftness.w));
                return vec4f(input.fogColor.rgb, input.fogColor.a * fogAlpha);
            }
            """;

    private final GraphicsContext graphics;
    private final boolean heapUploadBuffers;
    private final ShaderModule shader;
    private final RenderPipeline pipeline;
    private final RenderPassDescriptor renderPassDescriptor =
            new RenderPassDescriptor().label("fog of war renderer 3d pass");
    private final float[] lights = new float[MAX_LIGHTS * LIGHT_FLOATS];
    private final float[] combinedValues = new float[16];
    private float[] vertices;
    private int lightCount;
    private int floatCount;
    private int vertexCount;
    private int quadCount;
    private Buffer[] vertexBuffers;
    private ByteBuffer uploadBuffer;
    private FloatBuffer uploadFloats;
    private int vertexBufferSlot;
    private RenderPass pass;
    private boolean ownsPass;
    private boolean drawing;
    private boolean disposed;
    private float red = 0.0f;
    private float green = 0.02f;
    private float blue = 0.05f;
    private float alpha = 0.78f;

    /**
     * Creates a 3D fog-of-war renderer.
     *
     * @param graphicsSystem the graphics system
     */
    public FogOfWarRenderer3D(GraphicsContext graphicsSystem) {
        this(graphicsSystem, DEFAULT_MAX_QUADS);
    }

    /**
     * Creates a 3D fog-of-war renderer.
     *
     * @param graphicsSystem the graphics system
     * @param initialMaxQuads the initial max quads
     */
    public FogOfWarRenderer3D(GraphicsContext graphicsSystem, int initialMaxQuads) {
        if (graphicsSystem == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        if (initialMaxQuads <= 0) {
            throw new FdxException("FogOfWarRenderer3D initial quad count must be greater than zero");
        }
        graphics = graphicsSystem;
        heapUploadBuffers = usesHeapUploadBuffers(graphicsSystem);
        vertices = new float[initialMaxQuads * VERTICES_PER_QUAD * FLOATS_PER_VERTEX];
        shader = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("fog of war renderer 3d", FOG_SHADER_SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shader, graphics.surfaceFormat())
                .label("fog of war renderer 3d")
                .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                .vertexEntryPoint("vertexMain")
                .fragmentEntryPoint("fragmentMain")
                .vertexLayout(FOG_VERTEX_LAYOUT)
                .depthWriteEnabled(false));
        int initialByteCount = initialMaxQuads * VERTICES_PER_QUAD * BYTES_PER_VERTEX;
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
     * Sets the fog color and returns this renderer.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     * @return this renderer for chaining
     */
    public FogOfWarRenderer3D color(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        return this;
    }

    /**
     * Removes all reveal spheres and returns this renderer.
     *
     * @return this renderer for chaining
     */
    public FogOfWarRenderer3D clearLights() {
        lightCount = 0;
        return this;
    }

    /**
     * Adds one spherical reveal light and returns this renderer.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param radius the fully revealed radius edge
     * @param softness the fade distance near the edge
     * @return this renderer for chaining
     */
    public FogOfWarRenderer3D light(float x, float y, float z, float radius, float softness) {
        if (lightCount >= MAX_LIGHTS) {
            throw new FdxException("FogOfWarRenderer3D supports at most " + MAX_LIGHTS + " lights per draw");
        }
        if (radius < 0.0f) {
            throw new FdxException("Fog light radius cannot be negative");
        }
        if (softness < 0.0f) {
            throw new FdxException("Fog light softness cannot be negative");
        }
        int index = lightCount * LIGHT_FLOATS;
        lights[index] = x;
        lights[index + 1] = y;
        lights[index + 2] = z;
        lights[index + 3] = radius;
        lights[index + 4] = softness;
        lightCount++;
        return this;
    }

    /**
     * Draws fog over a horizontal world-space rectangle.
     *
     * @param camera the active camera
     * @param x the minimum x coordinate
     * @param z the minimum z coordinate
     * @param width the rectangle width
     * @param depth the rectangle depth
     * @param y the world-space height of the fog plane
     */
    public void draw(Camera camera, float x, float z, float width, float depth, float y) {
        ensureDrawing();
        if (camera == null) {
            throw new FdxException("Camera cannot be null");
        }
        if (width == 0.0f || depth == 0.0f) {
            return;
        }
        camera.combined().copyValues(combinedValues, 0);
        appendQuad(x, z, width, depth, y);
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

    private void appendQuad(float x, float z, float width, float depth, float y) {
        vertices = ensureFloatCapacity(vertices, floatCount + VERTICES_PER_QUAD * FLOATS_PER_VERTEX);
        float x1 = x;
        float z1 = z;
        float x2 = x;
        float z2 = z + depth;
        float x3 = x + width;
        float z3 = z + depth;
        float x4 = x + width;
        float z4 = z;
        float[] values = vertices;
        int index = floatCount;
        index = appendVertex(values, index, x1, y, z1);
        index = appendVertex(values, index, x2, y, z2);
        index = appendVertex(values, index, x3, y, z3);
        index = appendVertex(values, index, x1, y, z1);
        index = appendVertex(values, index, x3, y, z3);
        index = appendVertex(values, index, x4, y, z4);
        floatCount = index;
        vertexCount += VERTICES_PER_QUAD;
        quadCount++;
    }

    private int appendVertex(float[] values, int index, float x, float y, float z) {
        index = appendClipPosition(values, index, x, y, z);
        values[index++] = x;
        values[index++] = y;
        values[index++] = z;
        values[index++] = red;
        values[index++] = green;
        values[index++] = blue;
        values[index++] = alpha;
        for (int i = 0; i < MAX_LIGHTS; i++) {
            int lightIndex = i * LIGHT_FLOATS;
            if (i < lightCount) {
                values[index++] = lights[lightIndex];
                values[index++] = lights[lightIndex + 1];
                values[index++] = lights[lightIndex + 2];
                values[index++] = lights[lightIndex + 3];
            }
            else {
                values[index++] = 0.0f;
                values[index++] = 0.0f;
                values[index++] = 0.0f;
                values[index++] = 0.0f;
            }
        }
        for (int i = 0; i < MAX_LIGHTS; i++) {
            values[index++] = i < lightCount ? lights[i * LIGHT_FLOATS + 4] : 0.0f;
        }
        return index;
    }

    private int appendClipPosition(float[] values, int index, float x, float y, float z) {
        float[] matrix = combinedValues;
        values[index++] = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
        values[index++] = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
        values[index++] = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
        values[index++] = matrix[3] * x + matrix[7] * y + matrix[11] * z + matrix[15];
        return index;
    }

    private void flush() {
        if (quadCount == 0) {
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
        pass.setVertexBuffer(activeVertexBuffer);
        pass.draw(vertexCount, 1, 0, 0);
        floatCount = 0;
        vertexCount = 0;
        quadCount = 0;
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
                "fog of war renderer 3d vertices", byteCount));
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
            throw new FdxException("FogOfWarRenderer3D.begin() must be called before drawing");
        }
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException("FogOfWarRenderer3D has been disposed");
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
