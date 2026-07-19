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
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Immediate 2D shape renderer. The first slice uses normalized device coordinates:
 * x/y values are expected in the -1..1 range.
 *
 * @author xpenatan
 */
public final class ShapeRenderer2D implements Disposable {
    private static final int FLOATS_PER_VERTEX = 6;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final int DEFAULT_MAX_VERTICES = 8192;
    private static final int DEFAULT_CIRCLE_SEGMENTS = 48;
    private static final VertexLayout SHAPE_VERTEX_LAYOUT = VertexLayout.of(
            BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X4, 8));
    private static final String SHAPE_WGSL =
            "struct VertexInput {\n" +
            "    @location(0) position : vec2f,\n" +
            "    @location(1) color : vec4f,\n" +
            "};\n" +
            "\n" +
            "struct VertexOutput {\n" +
            "    @builtin(position) position : vec4f,\n" +
            "    @location(0) color : vec4f,\n" +
            "};\n" +
            "\n" +
            "@vertex\n" +
            "fn vertexMain(input : VertexInput) -> VertexOutput {\n" +
            "    var output : VertexOutput;\n" +
            "    output.position = vec4f(input.position, 0.0, 1.0);\n" +
            "    output.color = input.color;\n" +
            "    return output;\n" +
            "}\n" +
            "\n" +
            "@fragment\n" +
            "fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {\n" +
            "    return input.color;\n" +
            "}\n";
    private final GraphicsContext graphics;
    private final boolean heapUploadBuffers;
    private final ShaderModule shader;
    private final RenderPipeline trianglePipeline;
    private final RenderPipeline linePipeline;
    private final RenderPassDescriptor renderPassDescriptor =
            new RenderPassDescriptor().label("shape renderer 2d pass");
    private float[] triangleVertices;
    private float[] lineVertices;
    private int triangleFloatCount;
    private int lineFloatCount;
    private Buffer[] vertexBuffers;
    private ByteBuffer uploadBuffer;
    private RenderPass pass;
    private int vertexBufferSlot;
    private boolean ownsPass;
    private boolean drawing;
    private boolean disposed;
    private float red = 1.0f;
    private float green = 1.0f;
    private float blue = 1.0f;
    private float alpha = 1.0f;

    /**
     * Creates a shape renderer2 d.
     *
     * @param graphicsSystem the graphics system
     */
    public ShapeRenderer2D(GraphicsContext graphicsSystem) {
        this(graphicsSystem, DEFAULT_MAX_VERTICES);
    }

    /**
     * Creates a shape renderer2 d.
     *
     * @param graphicsSystem the graphics system
     * @param initialMaxVertices the initial max vertices
     */
    public ShapeRenderer2D(GraphicsContext graphicsSystem, int initialMaxVertices) {
        if (graphicsSystem == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        if (initialMaxVertices <= 0) {
            throw new FdxException("ShapeRenderer2D initial vertex count must be greater than zero");
        }
        this.graphics = graphicsSystem;
        heapUploadBuffers = usesHeapUploadBuffers(graphicsSystem);
        triangleVertices = new float[initialMaxVertices * FLOATS_PER_VERTEX];
        lineVertices = new float[initialMaxVertices * FLOATS_PER_VERTEX];
        shader = graphicsSystem.device().createShaderModule(ShaderModuleDescriptor.wgsl(
                "shape renderer 2d", SHAPE_WGSL));
        trianglePipeline = createPipeline(PrimitiveTopology.TRIANGLE_LIST, "shape renderer 2d triangles");
        linePipeline = createPipeline(PrimitiveTopology.LINE_LIST, "shape renderer 2d lines");
        ensureVertexBuffer(0, initialMaxVertices * BYTES_PER_VERTEX);
    }

    private RenderPipeline createPipeline(PrimitiveTopology topology, String label) {
        return graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shader, graphics.surfaceFormat())
                .label(label)
                .primitiveTopology(topology)
                .vertexEntryPoint("vertexMain")
                .fragmentEntryPoint("fragmentMain")
                .vertexLayout(SHAPE_VERTEX_LAYOUT));
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
     * Sets the color and returns this shape renderer2 d.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     * @return this shape renderer2 d for chaining
     */
    public ShapeRenderer2D color(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        return this;
    }

    /**
     * Runs the line step.
     *
     * @param x1 the x1
     * @param y1 the y1
     * @param x2 the x2
     * @param y2 the y2
     */
    public void line(float x1, float y1, float x2, float y2) {
        line(x1, y1, x2, y2, red, green, blue, alpha);
    }

    /**
     * Runs the line step.
     *
     * @param x1 the x1
     * @param y1 the y1
     * @param x2 the x2
     * @param y2 the y2
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    public void line(float x1, float y1, float x2, float y2, float red, float green, float blue, float alpha) {
        ensureDrawing();
        appendLineVertex(x1, y1, red, green, blue, alpha);
        appendLineVertex(x2, y2, red, green, blue, alpha);
    }

    /**
     * Runs the triangle step.
     *
     * @param x1 the x1
     * @param y1 the y1
     * @param x2 the x2
     * @param y2 the y2
     * @param x3 the x3
     * @param y3 the y3
     */
    public void triangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        triangle(x1, y1, x2, y2, x3, y3, red, green, blue, alpha);
    }

    /**
     * Runs the triangle step.
     *
     * @param x1 the x1
     * @param y1 the y1
     * @param x2 the x2
     * @param y2 the y2
     * @param x3 the x3
     * @param y3 the y3
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    public void triangle(float x1, float y1, float x2, float y2, float x3, float y3,
            float red, float green, float blue, float alpha) {
        line(x1, y1, x2, y2, red, green, blue, alpha);
        line(x2, y2, x3, y3, red, green, blue, alpha);
        line(x3, y3, x1, y1, red, green, blue, alpha);
    }

    /**
     * Runs the filled triangle step.
     *
     * @param x1 the x1
     * @param y1 the y1
     * @param x2 the x2
     * @param y2 the y2
     * @param x3 the x3
     * @param y3 the y3
     */
    public void filledTriangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        filledTriangle(x1, y1, x2, y2, x3, y3, red, green, blue, alpha);
    }

    /**
     * Runs the filled triangle step.
     *
     * @param x1 the x1
     * @param y1 the y1
     * @param x2 the x2
     * @param y2 the y2
     * @param x3 the x3
     * @param y3 the y3
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    public void filledTriangle(float x1, float y1, float x2, float y2, float x3, float y3,
            float red, float green, float blue, float alpha) {
        ensureDrawing();
        appendTriangleVertex(x1, y1, red, green, blue, alpha);
        appendTriangleVertex(x2, y2, red, green, blue, alpha);
        appendTriangleVertex(x3, y3, red, green, blue, alpha);
    }

    /**
     * Runs the rect step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    public void rect(float x, float y, float width, float height) {
        rect(x, y, width, height, red, green, blue, alpha);
    }

    /**
     * Runs the rect step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    public void rect(float x, float y, float width, float height, float red, float green, float blue, float alpha) {
        float x2 = x + width;
        float y2 = y + height;
        line(x, y, x2, y, red, green, blue, alpha);
        line(x2, y, x2, y2, red, green, blue, alpha);
        line(x2, y2, x, y2, red, green, blue, alpha);
        line(x, y2, x, y, red, green, blue, alpha);
    }

    /**
     * Runs the filled rect step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    public void filledRect(float x, float y, float width, float height) {
        filledRect(x, y, width, height, red, green, blue, alpha);
    }

    /**
     * Runs the filled rect step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    public void filledRect(float x, float y, float width, float height, float red, float green, float blue,
            float alpha) {
        float x2 = x + width;
        float y2 = y + height;
        filledTriangle(x, y, x, y2, x2, y2, red, green, blue, alpha);
        filledTriangle(x, y, x2, y2, x2, y, red, green, blue, alpha);
    }

    /**
     * Runs the circle step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param radius the radius
     */
    public void circle(float x, float y, float radius) {
        circle(x, y, radius, DEFAULT_CIRCLE_SEGMENTS, red, green, blue, alpha);
    }

    /**
     * Runs the circle step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param radius the radius
     * @param segments the segments
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    public void circle(float x, float y, float radius, int segments, float red, float green, float blue, float alpha) {
        ensureSegments(segments);
        float previousX = x + radius;
        float previousY = y;
        for (int i = 1; i <= segments; i++) {
            float angle = (float) (Math.PI * 2.0 * i / segments);
            float nextX = x + (float) Math.cos(angle) * radius;
            float nextY = y + (float) Math.sin(angle) * radius;
            line(previousX, previousY, nextX, nextY, red, green, blue, alpha);
            previousX = nextX;
            previousY = nextY;
        }
    }

    /**
     * Runs the filled circle step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param radius the radius
     */
    public void filledCircle(float x, float y, float radius) {
        filledCircle(x, y, radius, DEFAULT_CIRCLE_SEGMENTS, red, green, blue, alpha);
    }

    /**
     * Runs the filled circle step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param radius the radius
     * @param segments the segments
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    public void filledCircle(float x, float y, float radius, int segments, float red, float green, float blue,
            float alpha) {
        ensureSegments(segments);
        float previousX = x + radius;
        float previousY = y;
        for (int i = 1; i <= segments; i++) {
            float angle = (float) (Math.PI * 2.0 * i / segments);
            float nextX = x + (float) Math.cos(angle) * radius;
            float nextY = y + (float) Math.sin(angle) * radius;
            filledTriangle(x, y, previousX, previousY, nextX, nextY, red, green, blue, alpha);
            previousX = nextX;
            previousY = nextY;
        }
    }

    /**
     * Ends the operation.
     */
    public void end() {
        ensureDrawing();
        flush(trianglePipeline, triangleVertices, triangleFloatCount);
        flush(linePipeline, lineVertices, lineFloatCount);
        triangleFloatCount = 0;
        lineFloatCount = 0;
        drawing = false;
        if (ownsPass) {
            pass.end();
        }
        ownsPass = false;
        pass = null;
    }

    private void flush(RenderPipeline pipeline, float[] vertices, int floatCount) {
        if (floatCount == 0) {
            return;
        }
        int vertexCount = floatCount / FLOATS_PER_VERTEX;
        int byteCount = vertexCount * BYTES_PER_VERTEX;
        Buffer activeVertexBuffer = nextVertexBuffer(byteCount);
        ensureUploadBuffer(byteCount);
        uploadBuffer.clear();
        for (int i = 0; i < floatCount; i++) {
            uploadBuffer.putFloat(vertices[i]);
        }
        uploadBuffer.flip();
        graphics.device().writeBuffer(activeVertexBuffer, uploadBuffer);
        pass.setPipeline(pipeline);
        pass.setVertexBuffer(activeVertexBuffer);
        pass.draw(vertexCount, 1, 0, 0);
    }

    private void appendLineVertex(float x, float y, float red, float green, float blue, float alpha) {
        lineVertices = ensureFloatCapacity(lineVertices, lineFloatCount + FLOATS_PER_VERTEX);
        lineFloatCount = appendVertex(lineVertices, lineFloatCount, x, y, red, green, blue, alpha);
    }

    private void appendTriangleVertex(float x, float y, float red, float green, float blue, float alpha) {
        triangleVertices = ensureFloatCapacity(triangleVertices, triangleFloatCount + FLOATS_PER_VERTEX);
        triangleFloatCount = appendVertex(triangleVertices, triangleFloatCount, x, y, red, green, blue, alpha);
    }

    private int appendVertex(float[] vertices, int offset, float x, float y, float red, float green, float blue,
            float alpha) {
        vertices[offset++] = x;
        vertices[offset++] = y;
        vertices[offset++] = red;
        vertices[offset++] = green;
        vertices[offset++] = blue;
        vertices[offset++] = alpha;
        return offset;
    }

    private float[] ensureFloatCapacity(float[] vertices, int required) {
        if (required <= vertices.length) {
            return vertices;
        }
        int newCapacity = vertices.length;
        while (newCapacity < required) {
            newCapacity *= 2;
        }
        float[] grown = new float[newCapacity];
        System.arraycopy(vertices, 0, grown, 0, vertices.length);
        return grown;
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
        int capacity = Math.max(byteCount, DEFAULT_MAX_VERTICES * BYTES_PER_VERTEX);
        vertexBuffers[slot] = graphics.device().createBuffer(BufferDescriptor.vertex("shape renderer 2d vertices",
                capacity));
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
        uploadBuffer = newUploadBuffer(byteCount);
    }

    private ByteBuffer newUploadBuffer(int byteCount) {
        ByteBuffer buffer = heapUploadBuffers ? ByteBuffer.allocate(byteCount) : ByteBuffer.allocateDirect(byteCount);
        return buffer.order(ByteOrder.nativeOrder());
    }

    private static boolean usesHeapUploadBuffers(GraphicsContext graphics) {
        return "psp".equals(graphics.providerId().value());
    }

    private void ensureSegments(int segments) {
        if (segments < 3) {
            throw new FdxException("Circle segment count must be at least 3");
        }
    }

    private void ensureDrawing() {
        ensureNotDisposed();
        if (!drawing || pass == null) {
            throw new FdxException("ShapeRenderer2D.begin() must be called before drawing");
        }
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException("ShapeRenderer2D has been disposed");
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
        linePipeline.dispose();
        trianglePipeline.dispose();
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
