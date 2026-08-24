package io.github.libfdx.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector2;
import io.github.libfdx.math.Vector3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Provider-neutral buffered shape renderer for 2D and 3D geometry.
 *
 * <p>Two-dimensional overloads submit vertices at {@code z = 0}. With the
 * default identity projection those coordinates are normalized device
 * coordinates. Three-dimensional callers set a view-projection matrix and use
 * a depth-enabled recording scope. Line and filled geometry share one renderer
 * and are flushed through independent line-list and triangle-list pipelines.</p>
 */
public final class ShapeRenderer implements Disposable {
    /** Selects how shape primitives are emitted. */
    public enum ShapeType {
        Point,
        Line,
        Filled
    }

    private static final int SOURCE_FLOATS_PER_VERTEX = 7;
    private static final int GPU_FLOATS_PER_VERTEX = 8;
    private static final int GPU_BYTES_PER_VERTEX =
            GPU_FLOATS_PER_VERTEX * Float.BYTES;
    private static final int DEFAULT_VERTEX_CAPACITY = 8192;
    private static final int DEFAULT_CIRCLE_SEGMENTS = 48;
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(
            GPU_BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X4, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X4, 16));
    private static final String SHADER = """
            struct VertexInput {
                @location(0) clipPosition: vec4<f32>,
                @location(1) color: vec4<f32>,
            };

            struct VertexOutput {
                @builtin(position) position: vec4<f32>,
                @location(0) color: vec4<f32>,
            };

            @vertex
            fn vertexMain(input: VertexInput) -> VertexOutput {
                var output: VertexOutput;
                output.position = input.clipPosition;
                output.color = input.color;
                return output;
            }

            @fragment
            fn fragmentMain(input: VertexOutput) -> @location(0) vec4<f32> {
                return input.color;
            }
            """;

    private final GraphicsContext graphics;
    private final boolean heapUploadBuffers;
    private final ShaderModule shader;
    private final RenderPipeline overlayTrianglePipeline;
    private final RenderPipeline overlayLinePipeline;
    private final RenderPipeline depthTrianglePipeline;
    private final RenderPipeline depthLinePipeline;
    private final RenderPassDescriptor overlayPassDescriptor =
            new RenderPassDescriptor()
                    .label("shape renderer overlay pass")
                    .colorLoadOp(LoadOp.load())
                    .colorStoreOp(StoreOp.store())
                    .depthEnabled(false);
    private final RenderPassDescriptor depthPassDescriptor =
            new RenderPassDescriptor()
                    .label("shape renderer depth pass")
                    .colorLoadOp(LoadOp.load())
                    .colorStoreOp(StoreOp.store())
                    .depthEnabled(true);
    private final float[] projection = Matrix4.IDENTITY.values();
    private final float[] transform = Matrix4.IDENTITY.values();
    private final Matrix4 transformScratch = new Matrix4();

    private float[] triangleVertices;
    private float[] lineVertices;
    private int triangleFloatCount;
    private int lineFloatCount;
    private Buffer[] vertexBuffers;
    private int vertexBufferSlot;
    private ByteBuffer uploadBuffer;
    private RenderPass pass;
    private boolean ownsPass;
    private boolean depthEnabled;
    private boolean drawing;
    private boolean disposed;
    private ShapeType shapeType = ShapeType.Line;
    private float red = 1.0f;
    private float green = 1.0f;
    private float blue = 1.0f;
    private float alpha = 1.0f;

    public ShapeRenderer(GraphicsContext graphics) {
        this(graphics, DEFAULT_VERTEX_CAPACITY);
    }

    public ShapeRenderer(GraphicsContext graphics, int initialMaxVertices) {
        if(graphics == null) {
            throw new FdxException("ShapeRenderer graphics cannot be null");
        }
        if(initialMaxVertices <= 0) {
            throw new FdxException(
                    "ShapeRenderer initial vertex count must be greater than zero");
        }
        this.graphics = graphics;
        heapUploadBuffers = "psp".equals(graphics.providerId().value());
        triangleVertices = new float[initialMaxVertices
                * SOURCE_FLOATS_PER_VERTEX];
        lineVertices = new float[initialMaxVertices
                * SOURCE_FLOATS_PER_VERTEX];
        shader = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("shape renderer", SHADER));
        overlayTrianglePipeline = createPipeline(
                PrimitiveTopology.TRIANGLE_LIST, false,
                "shape renderer overlay triangles");
        overlayLinePipeline = createPipeline(PrimitiveTopology.LINE_LIST,
                false, "shape renderer overlay lines");
        depthTrianglePipeline = createPipeline(
                PrimitiveTopology.TRIANGLE_LIST, true,
                "shape renderer depth triangles");
        depthLinePipeline = createPipeline(PrimitiveTopology.LINE_LIST, true,
                "shape renderer depth lines");
        ensureVertexBuffer(0, initialMaxVertices * GPU_BYTES_PER_VERTEX);
    }

    private RenderPipeline createPipeline(PrimitiveTopology topology,
            boolean depthTest, String label) {
        return graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shader, graphics.surfaceFormat())
                .label(label)
                .primitiveTopology(topology)
                .vertexLayout(VERTEX_LAYOUT)
                .depthTestEnabled(depthTest)
                .depthWriteEnabled(false));
    }

    /** Begins an overlay pass using the current shape type. */
    public void begin() {
        begin(shapeType, LoadOp.load());
    }

    /** Begins an overlay pass using the current shape type and load operation. */
    public void begin(LoadOp loadOp) {
        begin(shapeType, loadOp);
    }

    /** Begins an overlay pass using the supplied shape type. */
    public void begin(ShapeType type) {
        begin(type, LoadOp.load());
    }

    /** Begins an overlay pass using the supplied shape type and load operation. */
    public void begin(ShapeType type, LoadOp loadOp) {
        beginOwned(type, false, loadOp);
    }

    /** Begins recording into an existing overlay render pass. */
    public void begin(RenderPass pass) {
        begin(pass, shapeType, false);
    }

    /** Begins recording into an existing pass with explicit depth behavior. */
    public void begin(RenderPass pass, ShapeType type, boolean depthTest) {
        ensureCanBegin();
        if(pass == null) {
            throw new FdxException("ShapeRenderer render pass cannot be null");
        }
        this.pass = pass;
        shapeType = type != null ? type : ShapeType.Line;
        depthEnabled = depthTest;
        ownsPass = false;
        drawing = true;
        vertexBufferSlot = 0;
    }

    /** Begins a depth-tested pass. */
    public void beginDepth(ShapeType type) {
        beginOwned(type, true, LoadOp.load());
    }

    /** Begins an overlay pass. */
    public void beginOverlay(ShapeType type) {
        beginOwned(type, false, LoadOp.load());
    }

    /** Begins a depth-tested alpha-blended pass. */
    public void beginDepthBlend(ShapeType type) {
        beginDepth(type);
    }

    private void beginOwned(ShapeType type, boolean depthTest, LoadOp loadOp) {
        ensureCanBegin();
        GraphicsFrame frame = graphics.currentFrame();
        RenderPassDescriptor descriptor = depthTest ? depthPassDescriptor
                : overlayPassDescriptor;
        pass = frame.commandEncoder().beginRenderPass(descriptor
                .colorAttachment(frame.colorAttachment())
                .colorLoadOp(loadOp != null ? loadOp : LoadOp.load())
                .colorStoreOp(StoreOp.store()));
        shapeType = type != null ? type : ShapeType.Line;
        depthEnabled = depthTest;
        ownsPass = true;
        drawing = true;
        vertexBufferSlot = 0;
    }

    /** Ends the current recording scope and clears submitted geometry. */
    public void end() {
        end(true);
    }

    /** Ends the current recording scope. */
    public void end(boolean reset) {
        ensureDrawing();
        try {
            flushQueued();
        }
        finally {
            if(ownsPass) {
                pass.end();
            }
            pass = null;
            ownsPass = false;
            drawing = false;
            if(reset) {
                reset();
            }
        }
    }

    public void endDepth() {
        end();
    }

    public void endOverlay() {
        end();
    }

    public void endDepthBlend() {
        end();
    }

    /** Flushes queued geometry into the active recording scope. */
    public void flush() {
        ensureDrawing();
        flushQueued();
        reset();
    }

    private void flushQueued() {
        flush(depthEnabled ? depthTrianglePipeline : overlayTrianglePipeline,
                triangleVertices, triangleFloatCount);
        flush(depthEnabled ? depthLinePipeline : overlayLinePipeline,
                lineVertices, lineFloatCount);
    }

    private void flush(RenderPipeline pipeline, float[] vertices,
            int floatCount) {
        if(floatCount == 0) {
            return;
        }
        int vertexCount = floatCount / SOURCE_FLOATS_PER_VERTEX;
        int byteCount = vertexCount * GPU_BYTES_PER_VERTEX;
        Buffer activeVertexBuffer = nextVertexBuffer(byteCount);
        ensureUploadBuffer(byteCount);
        uploadBuffer.clear();
        for(int source = 0; source < floatCount;
                source += SOURCE_FLOATS_PER_VERTEX) {
            float x = vertices[source];
            float y = vertices[source + 1];
            float z = vertices[source + 2];
            uploadBuffer.putFloat(projection[0] * x + projection[4] * y
                    + projection[8] * z + projection[12]);
            uploadBuffer.putFloat(projection[1] * x + projection[5] * y
                    + projection[9] * z + projection[13]);
            uploadBuffer.putFloat(projection[2] * x + projection[6] * y
                    + projection[10] * z + projection[14]);
            uploadBuffer.putFloat(projection[3] * x + projection[7] * y
                    + projection[11] * z + projection[15]);
            uploadBuffer.putFloat(vertices[source + 3]);
            uploadBuffer.putFloat(vertices[source + 4]);
            uploadBuffer.putFloat(vertices[source + 5]);
            uploadBuffer.putFloat(vertices[source + 6]);
        }
        uploadBuffer.flip();
        graphics.device().writeBuffer(activeVertexBuffer, uploadBuffer);
        pass.setPipeline(pipeline);
        pass.setVertexBuffer(activeVertexBuffer);
        pass.draw(vertexCount, 1, 0, 0);
    }

    public ShapeRenderer color(float red, float green, float blue,
            float alpha) {
        setColor(red, green, blue, alpha);
        return this;
    }

    public void setColor(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    public void setColor(Color color) {
        Color actual = color != null ? color : Color.WHITE;
        setColor(actual.red(), actual.green(), actual.blue(), actual.alpha());
    }

    public void setColor(int rgba) {
        setColor(((rgba >>> 24) & 0xff) / 255.0f,
                ((rgba >>> 16) & 0xff) / 255.0f,
                ((rgba >>> 8) & 0xff) / 255.0f,
                (rgba & 0xff) / 255.0f);
    }

    public Color getColor() {
        return Color.rgba(red, green, blue, alpha);
    }

    public void set(ShapeType type) {
        shapeType = type != null ? type : ShapeType.Line;
    }

    public ShapeType getCurrentType() {
        return shapeType;
    }

    public void setProjectionMatrix(Matrix4 matrix) {
        (matrix != null ? matrix : Matrix4.IDENTITY).copyValues(projection, 0);
    }

    /**
     * Copies the projection matrix into caller-owned storage.
     *
     * @param out the output matrix
     * @return the output matrix
     */
    public Matrix4 getProjectionMatrix(Matrix4 out) {
        if (out == null) {
            throw new FdxException("ShapeRenderer projection output cannot be null");
        }
        return out.set(projection);
    }

    public void setTransformMatrix(Matrix4 matrix) {
        (matrix != null ? matrix : Matrix4.IDENTITY).copyValues(transform, 0);
    }

    /**
     * Copies the transform matrix into caller-owned storage.
     *
     * @param out the output matrix
     * @return the output matrix
     */
    public Matrix4 getTransformMatrix(Matrix4 out) {
        if (out == null) {
            throw new FdxException("ShapeRenderer transform output cannot be null");
        }
        return out.set(transform);
    }

    public void identity() {
        Matrix4.IDENTITY.copyValues(transform, 0);
    }

    public void translate(float x, float y, float z) {
        transformScratch.set(transform).translate(x, y, z)
                .copyValues(transform, 0);
    }

    public void rotate(float axisX, float axisY, float axisZ, float degrees) {
        transformScratch.set(transform).rotate(axisX, axisY, axisZ,
                (float)Math.toRadians(degrees)).copyValues(transform, 0);
    }

    public void scale(float x, float y, float z) {
        transformScratch.set(transform).scale(x, y, z)
                .copyValues(transform, 0);
    }

    public void updateMatrices() {
        // Matrix values are copied when set and applied when vertices queue.
    }

    public void setAutoShapeType(boolean autoShapeType) {
        // Shape types may be changed at any point before a primitive queues.
    }

    public void point(float x, float y, float z) {
        float size = 0.01f;
        line(x - size, y, z, x + size, y, z);
        line(x, y - size, z, x, y + size, z);
        line(x, y, z - size, x, y, z + size);
    }

    public void line(float x1, float y1, float x2, float y2) {
        line(x1, y1, 0.0f, x2, y2, 0.0f);
    }

    public void line(float x1, float y1, float x2, float y2,
            float red, float green, float blue, float alpha) {
        addLine(x1, y1, 0.0f, x2, y2, 0.0f,
                red, green, blue, alpha,
                red, green, blue, alpha);
    }

    public void line(float x1, float y1, float z1, float x2, float y2,
            float z2) {
        addLine(x1, y1, z1, x2, y2, z2,
                red, green, blue, alpha,
                red, green, blue, alpha);
    }

    public void line(ShapeType type, float x1, float y1, float z1,
            float x2, float y2, float z2) {
        line(x1, y1, z1, x2, y2, z2);
    }

    public void line(Vector3 first, Vector3 second) {
        line(first.x(), first.y(), first.z(), second.x(), second.y(),
                second.z());
    }

    public void line(Vector2 first, Vector2 second) {
        line(first.x(), first.y(), second.x(), second.y());
    }

    public void line(float x1, float y1, float x2, float y2,
            Color firstColor, Color secondColor) {
        addLine(x1, y1, 0.0f, x2, y2, 0.0f,
                firstColor, secondColor);
    }

    public void line(float x1, float y1, float z1, float x2, float y2,
            float z2, Color firstColor, Color secondColor) {
        addLine(x1, y1, z1, x2, y2, z2, firstColor, secondColor);
    }

    public void triangle(Vector3 first, Vector3 second, Vector3 third) {
        triangle(first.x(), first.y(), first.z(), second.x(), second.y(),
                second.z(), third.x(), third.y(), third.z());
    }

    public void triangle(float x1, float y1, float x2, float y2,
            float x3, float y3) {
        triangle(x1, y1, 0.0f, x2, y2, 0.0f, x3, y3, 0.0f);
    }

    public void triangle(float x1, float y1, float z1, float x2, float y2,
            float z2, float x3, float y3, float z3) {
        triangle(shapeType, x1, y1, z1, x2, y2, z2, x3, y3, z3);
    }

    public void triangle(ShapeType type, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3) {
        if(type == ShapeType.Filled) {
            addTriangle(x1, y1, z1, red, green, blue, alpha,
                    x2, y2, z2, red, green, blue, alpha,
                    x3, y3, z3, red, green, blue, alpha);
        }
        else {
            line(x1, y1, z1, x2, y2, z2);
            line(x2, y2, z2, x3, y3, z3);
            line(x3, y3, z3, x1, y1, z1);
        }
    }

    public void filledTriangle(float x1, float y1, float x2, float y2,
            float x3, float y3) {
        filledTriangle(x1, y1, x2, y2, x3, y3,
                red, green, blue, alpha);
    }

    public void filledTriangle(float x1, float y1, float x2, float y2,
            float x3, float y3, float red, float green, float blue,
            float alpha) {
        addTriangle(x1, y1, 0.0f, red, green, blue, alpha,
                x2, y2, 0.0f, red, green, blue, alpha,
                x3, y3, 0.0f, red, green, blue, alpha);
    }

    public void rect(float x, float y, float width, float height) {
        rect(x, y, width, height, red, green, blue, alpha);
    }

    public void rect(float x, float y, float width, float height,
            float red, float green, float blue, float alpha) {
        float x2 = x + width;
        float y2 = y + height;
        addLine(x, y, 0.0f, x2, y, 0.0f, red, green, blue, alpha,
                red, green, blue, alpha);
        addLine(x2, y, 0.0f, x2, y2, 0.0f, red, green, blue, alpha,
                red, green, blue, alpha);
        addLine(x2, y2, 0.0f, x, y2, 0.0f, red, green, blue, alpha,
                red, green, blue, alpha);
        addLine(x, y2, 0.0f, x, y, 0.0f, red, green, blue, alpha,
                red, green, blue, alpha);
    }

    public void filledRect(float x, float y, float width, float height) {
        filledRect(x, y, width, height, red, green, blue, alpha);
    }

    public void filledRect(float x, float y, float width, float height,
            float red, float green, float blue, float alpha) {
        float x2 = x + width;
        float y2 = y + height;
        addTriangle(x, y, 0.0f, red, green, blue, alpha,
                x, y2, 0.0f, red, green, blue, alpha,
                x2, y2, 0.0f, red, green, blue, alpha);
        addTriangle(x, y, 0.0f, red, green, blue, alpha,
                x2, y2, 0.0f, red, green, blue, alpha,
                x2, y, 0.0f, red, green, blue, alpha);
    }

    public void box(float x, float y, float z, float width, float height,
            float depth) {
        boundingBox(x, y, z, x + width, y + height, z + depth);
    }

    public void boundingBox(BoundingBox bounds) {
        if(bounds != null) {
            boundingBox(bounds.min().x(), bounds.min().y(), bounds.min().z(),
                    bounds.max().x(), bounds.max().y(), bounds.max().z());
        }
    }

    public void boundingBox(Vector3 min, Vector3 max) {
        boundingBox(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    public void boundingBox(float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ) {
        if(shapeType == ShapeType.Filled) {
            filledBox(minX, minY, minZ, maxX, maxY, maxZ);
        }
        else {
            lineBox(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private void lineBox(float x0, float y0, float z0, float x1, float y1,
            float z1) {
        line(x0, y0, z0, x1, y0, z0);
        line(x1, y0, z0, x1, y1, z0);
        line(x1, y1, z0, x0, y1, z0);
        line(x0, y1, z0, x0, y0, z0);
        line(x0, y0, z1, x1, y0, z1);
        line(x1, y0, z1, x1, y1, z1);
        line(x1, y1, z1, x0, y1, z1);
        line(x0, y1, z1, x0, y0, z1);
        line(x0, y0, z0, x0, y0, z1);
        line(x1, y0, z0, x1, y0, z1);
        line(x1, y1, z0, x1, y1, z1);
        line(x0, y1, z0, x0, y1, z1);
    }

    private void filledBox(float x0, float y0, float z0, float x1, float y1,
            float z1) {
        filledQuad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        filledQuad(x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1);
        filledQuad(x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1);
        filledQuad(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
        filledQuad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        filledQuad(x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
    }

    private void filledQuad(float ax, float ay, float az,
            float bx, float by, float bz, float cx, float cy, float cz,
            float dx, float dy, float dz) {
        triangle(ShapeType.Filled, ax, ay, az, bx, by, bz, cx, cy, cz);
        triangle(ShapeType.Filled, cx, cy, cz, dx, dy, dz, ax, ay, az);
    }

    public void circle(float x, float y, float z, float radius) {
        circle(x, y, z, radius, DEFAULT_CIRCLE_SEGMENTS);
    }

    public void circle(float x, float y, float z, float radius,
            int segments) {
        ensureSegments(segments);
        float previousX = x + radius;
        float previousY = y;
        for(int i = 1; i <= segments; i++) {
            float angle = (float)(Math.PI * 2.0 * i / segments);
            float nextX = x + (float)Math.cos(angle) * radius;
            float nextY = y + (float)Math.sin(angle) * radius;
            if(shapeType == ShapeType.Filled) {
                triangle(ShapeType.Filled, x, y, z,
                        previousX, previousY, z, nextX, nextY, z);
            }
            else {
                line(previousX, previousY, z, nextX, nextY, z);
            }
            previousX = nextX;
            previousY = nextY;
        }
    }

    public void circle(float x, float y, float radius) {
        circle(x, y, 0.0f, radius, DEFAULT_CIRCLE_SEGMENTS);
    }

    public void circle(float x, float y, float radius, int segments,
            float red, float green, float blue, float alpha) {
        float oldRed = this.red;
        float oldGreen = this.green;
        float oldBlue = this.blue;
        float oldAlpha = this.alpha;
        setColor(red, green, blue, alpha);
        circle(x, y, 0.0f, radius, segments);
        setColor(oldRed, oldGreen, oldBlue, oldAlpha);
    }

    public void filledCircle(float x, float y, float radius) {
        filledCircle(x, y, radius, DEFAULT_CIRCLE_SEGMENTS,
                red, green, blue, alpha);
    }

    public void filledCircle(float x, float y, float radius, int segments,
            float red, float green, float blue, float alpha) {
        ensureSegments(segments);
        float previousX = x + radius;
        float previousY = y;
        for(int i = 1; i <= segments; i++) {
            float angle = (float)(Math.PI * 2.0 * i / segments);
            float nextX = x + (float)Math.cos(angle) * radius;
            float nextY = y + (float)Math.sin(angle) * radius;
            addTriangle(x, y, 0.0f, red, green, blue, alpha,
                    previousX, previousY, 0.0f, red, green, blue, alpha,
                    nextX, nextY, 0.0f, red, green, blue, alpha);
            previousX = nextX;
            previousY = nextY;
        }
    }

    public void reset() {
        triangleFloatCount = 0;
        lineFloatCount = 0;
    }

    public int getQueuedLineCount() {
        return lineFloatCount / (2 * SOURCE_FLOATS_PER_VERTEX);
    }

    public int getQueuedTriangleCount() {
        return triangleFloatCount / (3 * SOURCE_FLOATS_PER_VERTEX);
    }

    public boolean hasQueuedGeometry() {
        return triangleFloatCount > 0 || lineFloatCount > 0;
    }

    public boolean isDrawing() {
        return drawing;
    }

    private void addLine(float x1, float y1, float z1,
            float x2, float y2, float z2, Color first, Color second) {
        Color a = first != null ? first : Color.rgba(red, green, blue, alpha);
        Color b = second != null ? second : a;
        addLine(x1, y1, z1, x2, y2, z2,
                a.red(), a.green(), a.blue(), a.alpha(),
                b.red(), b.green(), b.blue(), b.alpha());
    }

    private void addLine(float x1, float y1, float z1,
            float x2, float y2, float z2,
            float r1, float g1, float b1, float a1,
            float r2, float g2, float b2, float a2) {
        lineVertices = ensureFloatCapacity(lineVertices,
                lineFloatCount + 2 * SOURCE_FLOATS_PER_VERTEX);
        lineFloatCount = appendVertex(lineVertices, lineFloatCount,
                x1, y1, z1, r1, g1, b1, a1);
        lineFloatCount = appendVertex(lineVertices, lineFloatCount,
                x2, y2, z2, r2, g2, b2, a2);
    }

    private void addTriangle(float x1, float y1, float z1,
            float r1, float g1, float b1, float a1,
            float x2, float y2, float z2,
            float r2, float g2, float b2, float a2,
            float x3, float y3, float z3,
            float r3, float g3, float b3, float a3) {
        triangleVertices = ensureFloatCapacity(triangleVertices,
                triangleFloatCount + 3 * SOURCE_FLOATS_PER_VERTEX);
        triangleFloatCount = appendVertex(triangleVertices,
                triangleFloatCount, x1, y1, z1, r1, g1, b1, a1);
        triangleFloatCount = appendVertex(triangleVertices,
                triangleFloatCount, x2, y2, z2, r2, g2, b2, a2);
        triangleFloatCount = appendVertex(triangleVertices,
                triangleFloatCount, x3, y3, z3, r3, g3, b3, a3);
    }

    private int appendVertex(float[] vertices, int index,
            float x, float y, float z, float red, float green, float blue,
            float alpha) {
        vertices[index++] = transform[0] * x + transform[4] * y
                + transform[8] * z + transform[12];
        vertices[index++] = transform[1] * x + transform[5] * y
                + transform[9] * z + transform[13];
        vertices[index++] = transform[2] * x + transform[6] * y
                + transform[10] * z + transform[14];
        vertices[index++] = red;
        vertices[index++] = green;
        vertices[index++] = blue;
        vertices[index++] = alpha;
        return index;
    }

    private float[] ensureFloatCapacity(float[] vertices, int required) {
        if(vertices.length >= required) {
            return vertices;
        }
        int next = vertices.length;
        while(next < required) {
            next *= 2;
        }
        float[] larger = new float[next];
        System.arraycopy(vertices, 0, larger, 0, vertices.length);
        return larger;
    }

    private Buffer nextVertexBuffer(int byteCount) {
        Buffer buffer = ensureVertexBuffer(vertexBufferSlot, byteCount);
        vertexBufferSlot++;
        return buffer;
    }

    private Buffer ensureVertexBuffer(int slot, int byteCount) {
        if(vertexBuffers == null || slot >= vertexBuffers.length) {
            int next = vertexBuffers != null ? vertexBuffers.length : 4;
            while(slot >= next) {
                next *= 2;
            }
            Buffer[] larger = new Buffer[next];
            if(vertexBuffers != null) {
                System.arraycopy(vertexBuffers, 0, larger, 0,
                        vertexBuffers.length);
            }
            vertexBuffers = larger;
        }
        if(vertexBuffers[slot] != null
                && vertexBuffers[slot].size() >= byteCount) {
            return vertexBuffers[slot];
        }
        if(vertexBuffers[slot] != null) {
            vertexBuffers[slot].dispose();
        }
        vertexBuffers[slot] = graphics.device().createBuffer(
                BufferDescriptor.vertex("shape renderer vertices",
                        byteCount));
        return vertexBuffers[slot];
    }

    private void ensureUploadBuffer(int byteCount) {
        if(uploadBuffer != null && uploadBuffer.capacity() >= byteCount) {
            return;
        }
        int next = uploadBuffer != null ? uploadBuffer.capacity()
                : GPU_BYTES_PER_VERTEX;
        while(next < byteCount) {
            next *= 2;
        }
        uploadBuffer = (heapUploadBuffers ? ByteBuffer.allocate(next)
                : ByteBuffer.allocateDirect(next)).order(ByteOrder.nativeOrder());
    }

    private void ensureSegments(int segments) {
        if(segments < 3) {
            throw new FdxException("ShapeRenderer requires at least 3 segments");
        }
    }

    private void ensureCanBegin() {
        ensureNotDisposed();
        if(drawing) {
            throw new FdxException("ShapeRenderer is already drawing");
        }
    }

    private void ensureDrawing() {
        ensureNotDisposed();
        if(!drawing || pass == null) {
            throw new FdxException(
                    "ShapeRenderer.begin() must be called before ending or flushing");
        }
    }

    private void ensureNotDisposed() {
        if(disposed) {
            throw new FdxException("ShapeRenderer has been disposed");
        }
    }

    @Override
    public void dispose() {
        if(disposed) {
            return;
        }
        disposed = true;
        if(vertexBuffers != null) {
            for(int i = 0; i < vertexBuffers.length; i++) {
                if(vertexBuffers[i] != null) {
                    vertexBuffers[i].dispose();
                    vertexBuffers[i] = null;
                }
            }
            vertexBuffers = null;
        }
        overlayTrianglePipeline.dispose();
        overlayLinePipeline.dispose();
        depthTrianglePipeline.dispose();
        depthLinePipeline.dispose();
        shader.dispose();
        uploadBuffer = null;
        triangleVertices = null;
        lineVertices = null;
        triangleFloatCount = 0;
        lineFloatCount = 0;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
