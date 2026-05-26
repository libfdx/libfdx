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
    private static final String SHAPE_VERTEX_GLSL =
            "#version 330 core\n" +
            "layout(location = 0) in vec2 a_position;\n" +
            "layout(location = 1) in vec4 a_color;\n" +
            "out vec4 v_color;\n" +
            "\n" +
            "void main() {\n" +
            "    v_color = a_color;\n" +
            "    gl_Position = vec4(a_position, 0.0, 1.0);\n" +
            "}\n";
    private static final String SHAPE_FRAGMENT_GLSL =
            "#version 330 core\n" +
            "in vec4 v_color;\n" +
            "out vec4 fragColor;\n" +
            "\n" +
            "void main() {\n" +
            "    fragColor = v_color;\n" +
            "}\n";
    private static final int[] SHAPE_VERTEX_SPIRV = {
            0x07230203, 0x00010600, 0x00070000, 0x0000001f, 0x00000000, 0x00020011, 0x00000001, 0x0006000b,
            0x00000001, 0x4c534c47, 0x6474732e, 0x3035342e, 0x00000000, 0x0003000e, 0x00000000, 0x00000001,
            0x000a000f, 0x00000000, 0x00000002, 0x74726576, 0x614d7865, 0x00006e69, 0x00000003, 0x00000004,
            0x00000005, 0x00000006, 0x00030003, 0x00000002, 0x000001c2, 0x000a0004, 0x475f4c47, 0x4c474f4f,
            0x70635f45, 0x74735f70, 0x5f656c79, 0x656e696c, 0x7269645f, 0x69746365, 0x00006576, 0x00080004,
            0x475f4c47, 0x4c474f4f, 0x6e695f45, 0x64756c63, 0x69645f65, 0x74636572, 0x00657669, 0x00050005,
            0x00000002, 0x74726576, 0x614d7865, 0x00006e69, 0x00040005, 0x00000003, 0x6f635f76, 0x00726f6c,
            0x00040005, 0x00000004, 0x6f635f61, 0x00726f6c, 0x00060005, 0x00000007, 0x505f6c67, 0x65567265,
            0x78657472, 0x00000000, 0x00060006, 0x00000007, 0x00000000, 0x505f6c67, 0x7469736f, 0x006e6f69,
            0x00070006, 0x00000007, 0x00000001, 0x505f6c67, 0x746e696f, 0x657a6953, 0x00000000, 0x00070006,
            0x00000007, 0x00000002, 0x435f6c67, 0x4470696c, 0x61747369, 0x0065636e, 0x00070006, 0x00000007,
            0x00000003, 0x435f6c67, 0x446c6c75, 0x61747369, 0x0065636e, 0x00030005, 0x00000005, 0x00000000,
            0x00050005, 0x00000006, 0x6f705f61, 0x69746973, 0x00006e6f, 0x00040047, 0x00000003, 0x0000001e,
            0x00000000, 0x00040047, 0x00000004, 0x0000001e, 0x00000001, 0x00050048, 0x00000007, 0x00000000,
            0x0000000b, 0x00000000, 0x00050048, 0x00000007, 0x00000001, 0x0000000b, 0x00000001, 0x00050048,
            0x00000007, 0x00000002, 0x0000000b, 0x00000003, 0x00050048, 0x00000007, 0x00000003, 0x0000000b,
            0x00000004, 0x00030047, 0x00000007, 0x00000002, 0x00040047, 0x00000006, 0x0000001e, 0x00000000,
            0x00020013, 0x00000008, 0x00030021, 0x00000009, 0x00000008, 0x00030016, 0x0000000a, 0x00000020,
            0x00040017, 0x0000000b, 0x0000000a, 0x00000004, 0x00040020, 0x0000000c, 0x00000003, 0x0000000b,
            0x0004003b, 0x0000000c, 0x00000003, 0x00000003, 0x00040020, 0x0000000d, 0x00000001, 0x0000000b,
            0x0004003b, 0x0000000d, 0x00000004, 0x00000001, 0x00040015, 0x0000000e, 0x00000020, 0x00000000,
            0x0004002b, 0x0000000e, 0x0000000f, 0x00000001, 0x0004001c, 0x00000010, 0x0000000a, 0x0000000f,
            0x0006001e, 0x00000007, 0x0000000b, 0x0000000a, 0x00000010, 0x00000010, 0x00040020, 0x00000011,
            0x00000003, 0x00000007, 0x0004003b, 0x00000011, 0x00000005, 0x00000003, 0x00040015, 0x00000012,
            0x00000020, 0x00000001, 0x0004002b, 0x00000012, 0x00000013, 0x00000000, 0x00040017, 0x00000014,
            0x0000000a, 0x00000002, 0x00040020, 0x00000015, 0x00000001, 0x00000014, 0x0004003b, 0x00000015,
            0x00000006, 0x00000001, 0x0004002b, 0x0000000a, 0x00000016, 0x00000000, 0x0004002b, 0x0000000a,
            0x00000017, 0x3f800000, 0x00050036, 0x00000008, 0x00000002, 0x00000000, 0x00000009, 0x000200f8,
            0x00000018, 0x0004003d, 0x0000000b, 0x00000019, 0x00000004, 0x0003003e, 0x00000003, 0x00000019,
            0x0004003d, 0x00000014, 0x0000001a, 0x00000006, 0x00050051, 0x0000000a, 0x0000001b, 0x0000001a,
            0x00000000, 0x00050051, 0x0000000a, 0x0000001c, 0x0000001a, 0x00000001, 0x00070050, 0x0000000b,
            0x0000001d, 0x0000001b, 0x0000001c, 0x00000016, 0x00000017, 0x00050041, 0x0000000c, 0x0000001e,
            0x00000005, 0x00000013, 0x0003003e, 0x0000001e, 0x0000001d, 0x000100fd, 0x00010038
    };
    private static final int[] SHAPE_FRAGMENT_SPIRV = {
            0x07230203, 0x00010600, 0x00070000, 0x0000000d, 0x00000000, 0x00020011, 0x00000001, 0x0006000b,
            0x00000001, 0x4c534c47, 0x6474732e, 0x3035342e, 0x00000000, 0x0003000e, 0x00000000, 0x00000001,
            0x0009000f, 0x00000004, 0x00000002, 0x67617266, 0x746e656d, 0x6e69614d, 0x00000000, 0x00000003,
            0x00000004, 0x00030010, 0x00000002, 0x00000007, 0x00030003, 0x00000002, 0x000001c2, 0x000a0004,
            0x475f4c47, 0x4c474f4f, 0x70635f45, 0x74735f70, 0x5f656c79, 0x656e696c, 0x7269645f, 0x69746365,
            0x00006576, 0x00080004, 0x475f4c47, 0x4c474f4f, 0x6e695f45, 0x64756c63, 0x69645f65, 0x74636572,
            0x00657669, 0x00060005, 0x00000002, 0x67617266, 0x746e656d, 0x6e69614d, 0x00000000, 0x00050005,
            0x00000003, 0x67617266, 0x6f6c6f43, 0x00000072, 0x00040005, 0x00000004, 0x6f635f76, 0x00726f6c,
            0x00040047, 0x00000003, 0x0000001e, 0x00000000, 0x00040047, 0x00000004, 0x0000001e, 0x00000000,
            0x00020013, 0x00000005, 0x00030021, 0x00000006, 0x00000005, 0x00030016, 0x00000007, 0x00000020,
            0x00040017, 0x00000008, 0x00000007, 0x00000004, 0x00040020, 0x00000009, 0x00000003, 0x00000008,
            0x0004003b, 0x00000009, 0x00000003, 0x00000003, 0x00040020, 0x0000000a, 0x00000001, 0x00000008,
            0x0004003b, 0x0000000a, 0x00000004, 0x00000001, 0x00050036, 0x00000005, 0x00000002, 0x00000000,
            0x00000006, 0x000200f8, 0x0000000b, 0x0004003d, 0x00000008, 0x0000000c, 0x00000004, 0x0003003e,
            0x00000003, 0x0000000c, 0x000100fd, 0x00010038
    };

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
    private RenderPass flushSlotPass;
    private int vertexBufferSlot;
    private boolean ownsPass;
    private boolean drawing;
    private boolean disposed;
    private float red = 1.0f;
    private float green = 1.0f;
    private float blue = 1.0f;
    private float alpha = 1.0f;

    public ShapeRenderer2D(GraphicsContext graphicsSystem) {
        this(graphicsSystem, DEFAULT_MAX_VERTICES);
    }

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
        shader = graphicsSystem.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("shape renderer 2d", SHAPE_WGSL)
                .glsl(SHAPE_VERTEX_GLSL, SHAPE_FRAGMENT_GLSL)
                .spirv(SHAPE_VERTEX_SPIRV, SHAPE_FRAGMENT_SPIRV));
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

    public void begin() {
        begin(LoadOp.load());
    }

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

    public ShapeRenderer2D color(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        return this;
    }

    public void line(float x1, float y1, float x2, float y2) {
        line(x1, y1, x2, y2, red, green, blue, alpha);
    }

    public void line(float x1, float y1, float x2, float y2, float red, float green, float blue, float alpha) {
        ensureDrawing();
        appendLineVertex(x1, y1, red, green, blue, alpha);
        appendLineVertex(x2, y2, red, green, blue, alpha);
    }

    public void triangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        triangle(x1, y1, x2, y2, x3, y3, red, green, blue, alpha);
    }

    public void triangle(float x1, float y1, float x2, float y2, float x3, float y3,
            float red, float green, float blue, float alpha) {
        line(x1, y1, x2, y2, red, green, blue, alpha);
        line(x2, y2, x3, y3, red, green, blue, alpha);
        line(x3, y3, x1, y1, red, green, blue, alpha);
    }

    public void filledTriangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        filledTriangle(x1, y1, x2, y2, x3, y3, red, green, blue, alpha);
    }

    public void filledTriangle(float x1, float y1, float x2, float y2, float x3, float y3,
            float red, float green, float blue, float alpha) {
        ensureDrawing();
        appendTriangleVertex(x1, y1, red, green, blue, alpha);
        appendTriangleVertex(x2, y2, red, green, blue, alpha);
        appendTriangleVertex(x3, y3, red, green, blue, alpha);
    }

    public void rect(float x, float y, float width, float height) {
        rect(x, y, width, height, red, green, blue, alpha);
    }

    public void rect(float x, float y, float width, float height, float red, float green, float blue, float alpha) {
        float x2 = x + width;
        float y2 = y + height;
        line(x, y, x2, y, red, green, blue, alpha);
        line(x2, y, x2, y2, red, green, blue, alpha);
        line(x2, y2, x, y2, red, green, blue, alpha);
        line(x, y2, x, y, red, green, blue, alpha);
    }

    public void filledRect(float x, float y, float width, float height) {
        filledRect(x, y, width, height, red, green, blue, alpha);
    }

    public void filledRect(float x, float y, float width, float height, float red, float green, float blue,
            float alpha) {
        float x2 = x + width;
        float y2 = y + height;
        filledTriangle(x, y, x, y2, x2, y2, red, green, blue, alpha);
        filledTriangle(x, y, x2, y2, x2, y, red, green, blue, alpha);
    }

    public void circle(float x, float y, float radius) {
        circle(x, y, radius, DEFAULT_CIRCLE_SEGMENTS, red, green, blue, alpha);
    }

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

    public void filledCircle(float x, float y, float radius) {
        filledCircle(x, y, radius, DEFAULT_CIRCLE_SEGMENTS, red, green, blue, alpha);
    }

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

    public void end() {
        ensureDrawing();
        flush(trianglePipeline, triangleVertices, triangleFloatCount);
        flush(linePipeline, lineVertices, lineFloatCount);
        triangleFloatCount = 0;
        lineFloatCount = 0;
        drawing = false;
        if (ownsPass) {
            pass.end();
            flushSlotPass = null;
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

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
