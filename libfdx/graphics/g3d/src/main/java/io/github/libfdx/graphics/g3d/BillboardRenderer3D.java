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
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Renders WGSL-authored camera-facing textured quads in 3D space.
 *
 * @author xpenatan
 */
public final class BillboardRenderer3D implements Disposable {
    private static final int FLOATS_PER_VERTEX = 10;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final int VERTICES_PER_BILLBOARD = 6;
    private static final int DEFAULT_MAX_BILLBOARDS = 64;
    private static final float AXIS_EPSILON = 0.000001f;
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X4, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 16),
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 24));
    private static final String SOURCE = """
            @group(0) @binding(0) var u_texture : texture_2d<f32>;
            @group(0) @binding(1) var u_sampler : sampler;

            struct VertexInput {
                @location(0) clipPosition : vec4f,
                @location(1) texCoord : vec2f,
                @location(2) color : vec4f,
            };

            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) texCoord : vec2f,
                @location(1) color : vec4f,
            };

            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = input.clipPosition;
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
    private final boolean heapUploadBuffers;
    private final ShaderModule shader;
    private final RenderPipeline pipeline;
    private final RenderPassDescriptor renderPassDescriptor =
            new RenderPassDescriptor().label("billboard renderer 3d pass");
    private final float[] combinedValues = new float[16];
    private float[] vertices;
    private Buffer[] vertexBuffers;
    private ByteBuffer uploadBuffer;
    private FloatBuffer uploadFloats;
    private Texture currentTexture;
    private RenderPass pass;
    private RenderPass flushSlotPass;
    private int vertexBufferSlot;
    private int floatCount;
    private int vertexCount;
    private int billboardCount;
    private boolean ownsPass;
    private boolean drawing;
    private boolean disposed;
    private float red = 1.0f;
    private float green = 1.0f;
    private float blue = 1.0f;
    private float alpha = 1.0f;

    /**
     * Creates a billboard renderer.
     *
     * @param graphics the graphics context
     */
    public BillboardRenderer3D(GraphicsContext graphics) {
        this(graphics, DEFAULT_MAX_BILLBOARDS);
    }

    /**
     * Creates a billboard renderer.
     *
     * @param graphics the graphics context
     * @param initialMaxBillboards the initial max billboards
     */
    public BillboardRenderer3D(GraphicsContext graphics, int initialMaxBillboards) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        if (initialMaxBillboards <= 0) {
            throw new FdxException("BillboardRenderer3D initial billboard count must be greater than zero");
        }
        this.graphics = graphics;
        heapUploadBuffers = usesHeapUploadBuffers(graphics);
        vertices = new float[initialMaxBillboards * VERTICES_PER_BILLBOARD * FLOATS_PER_VERTEX];
        shader = graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl("billboard renderer 3d", SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shader, graphics.surfaceFormat())
                .label("billboard renderer 3d")
                .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                .vertexLayout(VERTEX_LAYOUT)
                .sampledTextureCount(1)
                .depthTestEnabled(true)
                .depthWriteEnabled(false));
        int initialByteCount = initialMaxBillboards * VERTICES_PER_BILLBOARD * BYTES_PER_VERTEX;
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
     * @param loadOp the load operation for the color attachment
     */
    public void begin(LoadOp loadOp) {
        ensureNotDisposed();
        GraphicsFrame frame = graphics.currentFrame();
        pass = frame.commandEncoder().beginRenderPass(renderPassDescriptor
                .colorAttachment(frame.colorAttachment())
                .colorLoadOp(loadOp != null ? loadOp : LoadOp.load())
                .colorStoreOp(StoreOp.store())
                .depthClear(1.0f));
        ownsPass = true;
        drawing = true;
        resetBatch();
        resetFlushBufferSlots();
        flushSlotPass = pass;
    }

    /**
     * Begins the operation.
     *
     * @param pass the render pass
     */
    public void begin(RenderPass pass) {
        ensureNotDisposed();
        if (pass == null) {
            throw new FdxException("RenderPass cannot be null");
        }
        this.pass = pass;
        ownsPass = false;
        drawing = true;
        resetBatch();
        if (flushSlotPass != pass) {
            resetFlushBufferSlots();
            flushSlotPass = pass;
        }
    }

    /**
     * Sets the tint color and returns this renderer.
     *
     * @param red the red component
     * @param green the green component
     * @param blue the blue component
     * @param alpha the alpha component
     * @return this renderer for chaining
     */
    public BillboardRenderer3D color(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        return this;
    }

    /**
     * Draws a full-texture billboard centered at the supplied world position.
     *
     * @param texture the texture to sample
     * @param camera the active camera
     * @param centerX the world-space center x
     * @param centerY the world-space center y
     * @param centerZ the world-space center z
     * @param width the billboard width in world units
     * @param height the billboard height in world units
     */
    public void draw(Texture texture, Camera camera, float centerX, float centerY, float centerZ,
            float width, float height) {
        draw(texture, camera, centerX, centerY, centerZ, width, height, 0.0f);
    }

    /**
     * Draws a rotated full-texture billboard centered at the supplied world position.
     *
     * @param texture the texture to sample
     * @param camera the active camera
     * @param centerX the world-space center x
     * @param centerY the world-space center y
     * @param centerZ the world-space center z
     * @param width the billboard width in world units
     * @param height the billboard height in world units
     * @param rotationDegrees clockwise rotation in the billboard plane
     */
    public void draw(Texture texture, Camera camera, float centerX, float centerY, float centerZ,
            float width, float height, float rotationDegrees) {
        draw(texture, camera, centerX, centerY, centerZ, width, height, rotationDegrees,
                0.0f, 0.0f, 1.0f, 1.0f);
    }

    /**
     * Draws a billboard using the supplied normalized texture coordinates.
     *
     * @param texture the texture to sample
     * @param camera the active camera
     * @param centerX the world-space center x
     * @param centerY the world-space center y
     * @param centerZ the world-space center z
     * @param width the billboard width in world units
     * @param height the billboard height in world units
     * @param rotationDegrees clockwise rotation in the billboard plane
     * @param u the minimum u coordinate
     * @param v the minimum v coordinate
     * @param u2 the maximum u coordinate
     * @param v2 the maximum v coordinate
     */
    public void draw(Texture texture, Camera camera, float centerX, float centerY, float centerZ,
            float width, float height, float rotationDegrees, float u, float v, float u2, float v2) {
        ensureDrawing();
        if (texture == null) {
            throw new FdxException("Billboard texture cannot be null");
        }
        if (camera == null) {
            throw new FdxException("Camera cannot be null");
        }
        if (width == 0.0f || height == 0.0f || alpha == 0.0f) {
            return;
        }
        if (currentTexture != null && currentTexture != texture) {
            flush();
        }
        currentTexture = texture;
        camera.combined().copyValues(combinedValues, 0);
        appendBillboard(camera, centerX, centerY, centerZ, width, height, rotationDegrees, u, v, u2, v2);
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
            flushSlotPass = null;
        }
        ownsPass = false;
        pass = null;
        currentTexture = null;
    }

    private void appendBillboard(Camera camera, float centerX, float centerY, float centerZ,
            float width, float height, float rotationDegrees, float u, float v, float u2, float v2) {
        vertices = ensureFloatCapacity(vertices, floatCount + VERTICES_PER_BILLBOARD * FLOATS_PER_VERTEX);
        float directionX = camera.direction().x();
        float directionY = camera.direction().y();
        float directionZ = camera.direction().z();
        float upX = camera.up().x();
        float upY = camera.up().y();
        float upZ = camera.up().z();

        float rightX = directionY * upZ - directionZ * upY;
        float rightY = directionZ * upX - directionX * upZ;
        float rightZ = directionX * upY - directionY * upX;
        float rightLen = (float)Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        if (rightLen <= AXIS_EPSILON) {
            rightX = 1.0f;
            rightY = 0.0f;
            rightZ = 0.0f;
        }
        else {
            float invRightLen = 1.0f / rightLen;
            rightX *= invRightLen;
            rightY *= invRightLen;
            rightZ *= invRightLen;
        }

        float planeUpX = rightY * directionZ - rightZ * directionY;
        float planeUpY = rightZ * directionX - rightX * directionZ;
        float planeUpZ = rightX * directionY - rightY * directionX;
        float planeUpLen = (float)Math.sqrt(planeUpX * planeUpX + planeUpY * planeUpY + planeUpZ * planeUpZ);
        if (planeUpLen <= AXIS_EPSILON) {
            planeUpX = 0.0f;
            planeUpY = 1.0f;
            planeUpZ = 0.0f;
        }
        else {
            float invPlaneUpLen = 1.0f / planeUpLen;
            planeUpX *= invPlaneUpLen;
            planeUpY *= invPlaneUpLen;
            planeUpZ *= invPlaneUpLen;
        }

        float radians = (float)Math.toRadians(rotationDegrees);
        float cos = (float)Math.cos(radians);
        float sin = (float)Math.sin(radians);
        float halfWidth = width * 0.5f;
        float halfHeight = height * 0.5f;

        float x1 = -halfWidth;
        float y1 = -halfHeight;
        float x2 = -halfWidth;
        float y2 = halfHeight;
        float x3 = halfWidth;
        float y3 = halfHeight;
        float x4 = halfWidth;
        float y4 = -halfHeight;

        float[] values = vertices;
        int index = floatCount;
        index = appendVertex(values, index, centerX, centerY, centerZ, rightX, rightY, rightZ,
                planeUpX, planeUpY, planeUpZ, x1, y1, cos, sin, u, v2);
        index = appendVertex(values, index, centerX, centerY, centerZ, rightX, rightY, rightZ,
                planeUpX, planeUpY, planeUpZ, x2, y2, cos, sin, u, v);
        index = appendVertex(values, index, centerX, centerY, centerZ, rightX, rightY, rightZ,
                planeUpX, planeUpY, planeUpZ, x3, y3, cos, sin, u2, v);
        index = appendVertex(values, index, centerX, centerY, centerZ, rightX, rightY, rightZ,
                planeUpX, planeUpY, planeUpZ, x1, y1, cos, sin, u, v2);
        index = appendVertex(values, index, centerX, centerY, centerZ, rightX, rightY, rightZ,
                planeUpX, planeUpY, planeUpZ, x3, y3, cos, sin, u2, v);
        index = appendVertex(values, index, centerX, centerY, centerZ, rightX, rightY, rightZ,
                planeUpX, planeUpY, planeUpZ, x4, y4, cos, sin, u2, v2);
        floatCount = index;
        vertexCount += VERTICES_PER_BILLBOARD;
        billboardCount++;
    }

    private int appendVertex(float[] values, int index, float centerX, float centerY, float centerZ,
            float rightX, float rightY, float rightZ, float upX, float upY, float upZ,
            float localX, float localY, float cos, float sin, float u, float v) {
        float rotatedX = localX * cos - localY * sin;
        float rotatedY = localX * sin + localY * cos;
        float worldX = centerX + rightX * rotatedX + upX * rotatedY;
        float worldY = centerY + rightY * rotatedX + upY * rotatedY;
        float worldZ = centerZ + rightZ * rotatedX + upZ * rotatedY;
        index = appendClipPosition(values, index, worldX, worldY, worldZ);
        values[index++] = u;
        values[index++] = v;
        values[index++] = red;
        values[index++] = green;
        values[index++] = blue;
        values[index++] = alpha;
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
        if (billboardCount == 0) {
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
        resetBatch();
    }

    private void resetBatch() {
        floatCount = 0;
        vertexCount = 0;
        billboardCount = 0;
        currentTexture = null;
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
                "billboard renderer 3d vertices", byteCount));
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
            throw new FdxException("BillboardRenderer3D.begin() must be called before drawing");
        }
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException("BillboardRenderer3D has been disposed");
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
