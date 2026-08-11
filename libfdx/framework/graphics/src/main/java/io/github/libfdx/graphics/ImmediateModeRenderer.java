package io.github.libfdx.graphics;

import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderBuiltinUsage;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.reflection.ShaderInterpolation;
import io.github.libfdx.graphics.shader.reflection.ShaderInterpolationSampling;
import io.github.libfdx.graphics.shader.reflection.ShaderParameter;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceAccess;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceKind;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceUse;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderStageVariable;
import io.github.libfdx.graphics.shader.reflection.ShaderStageVisibility;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Immediate-mode line renderer for simple 2D and 3D diagnostics.
 *
 * @author xpenatan
 */
public final class ImmediateModeRenderer implements Disposable {
    private static final int FLOATS_PER_VERTEX = 7;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * Float.BYTES;
    private static final int DEFAULT_MAX_VERTICES = 512;
    private static final float[] IDENTITY_MATRIX = {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
    };
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X4, 12));
    private static final ShaderValueType MATRIX4 = ShaderValueType
            .matrix(ShaderScalarType.F32, 4, 4, 16)
            .named("mat4x4<f32>");
    private static final ShaderParameterLayout UNIFORM_LAYOUT =
            ShaderParameterLayout.of(128, 16,
                    ShaderParameter.of("model", MATRIX4, 0, 64, 16),
                    ShaderParameter.of("viewProjection", MATRIX4, 64, 64, 16));
    private static final ShaderParameterHandle MODEL =
            UNIFORM_LAYOUT.requireHandle("model");
    private static final ShaderParameterHandle VIEW_PROJECTION =
            UNIFORM_LAYOUT.requireHandle("viewProjection");
    private static final ShaderReflection REFLECTION = reflection();
    private static final String SHADER = ""
            + "struct CameraUniforms {\n"
            + "    model: mat4x4<f32>,\n"
            + "    viewProjection: mat4x4<f32>,\n"
            + "};\n"
            + "@group(0) @binding(0) var<uniform> uniforms: CameraUniforms;\n"
            + "struct VertexInput {\n"
            + "    @location(0) position: vec3<f32>,\n"
            + "    @location(1) color: vec4<f32>,\n"
            + "};\n"
            + "struct VertexOutput {\n"
            + "    @builtin(position) position: vec4<f32>,\n"
            + "    @location(0) color: vec4<f32>,\n"
            + "};\n"
            + "@vertex fn vertexMain(input: VertexInput) -> VertexOutput {\n"
            + "    var output: VertexOutput;\n"
            + "    let worldPosition = uniforms.model * vec4<f32>(input.position, 1.0);\n"
            + "    output.position = uniforms.viewProjection * worldPosition;\n"
            + "    output.color = input.color;\n"
            + "    return output;\n"
            + "}\n"
            + "@fragment fn fragmentMain(input: VertexOutput) -> @location(0) vec4<f32> {\n"
            + "    return input.color;\n"
            + "}\n";
    private final GraphicsContext graphics;
    private final RenderPassDescriptor line2DRenderPassDescriptor = new RenderPassDescriptor()
            .label("immediate renderer 2d line pass")
            .colorLoadOp(LoadOp.load())
            .colorStoreOp(StoreOp.store())
            .depthEnabled(false);
    private final RenderPassDescriptor line3DRenderPassDescriptor = new RenderPassDescriptor()
            .label("immediate renderer 3d line pass")
            .colorLoadOp(LoadOp.load())
            .colorStoreOp(StoreOp.store())
            .depthEnabled(true);
    private final ShaderParameterBlock uniformBlock =
            ShaderParameterBlock.allocate(UNIFORM_LAYOUT);
    private ShaderModule shader;
    private RenderPipeline line2DPipeline;
    private RenderPipeline line3DPipeline;
    private Buffer line2DVertexBuffer;
    private Buffer line3DVertexBuffer;
    private ByteBuffer uploadBuffer;
    private float[] line2DVertices;
    private float[] line3DVertices;
    private int line2DFloatCount;
    private int line3DFloatCount;
    private boolean disposed;

    /**
     * Creates an immediate-mode renderer.
     *
     * @param graphics the graphics context
     */
    public ImmediateModeRenderer(GraphicsContext graphics) {
        if (graphics == null) {
            throw new FdxException("ImmediateModeRenderer graphics cannot be null");
        }
        this.graphics = graphics;
        line2DVertices = new float[DEFAULT_MAX_VERTICES * FLOATS_PER_VERTEX];
        line3DVertices = new float[DEFAULT_MAX_VERTICES * FLOATS_PER_VERTEX];
        shader = graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl(
                "immediate mode renderer", SHADER));
        line2DPipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shader, graphics.surfaceFormat())
                .label("immediate renderer 2d lines")
                .primitiveTopology(PrimitiveTopology.LINE_LIST)
                .vertexLayout(VERTEX_LAYOUT)
                .shaderReflection(REFLECTION)
                .depthTestEnabled(false)
                .depthWriteEnabled(false));
        line3DPipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shader, graphics.surfaceFormat())
                .label("immediate renderer 3d lines")
                .primitiveTopology(PrimitiveTopology.LINE_LIST)
                .vertexLayout(VERTEX_LAYOUT)
                .shaderReflection(REFLECTION)
                .depthTestEnabled(true)
                .depthWriteEnabled(false));
        line2DVertexBuffer = graphics.device().createBuffer(BufferDescriptor.vertex("immediate renderer 2d vertices",
                DEFAULT_MAX_VERTICES * BYTES_PER_VERTEX));
        line3DVertexBuffer = graphics.device().createBuffer(BufferDescriptor.vertex("immediate renderer 3d vertices",
                DEFAULT_MAX_VERTICES * BYTES_PER_VERTEX));
    }

    /**
     * Clears queued 2D and 3D lines.
     */
    public void clear() {
        line2DFloatCount = 0;
        line3DFloatCount = 0;
    }

    /**
     * Clears queued 2D lines.
     */
    public void clear2D() {
        line2DFloatCount = 0;
    }

    /**
     * Clears queued 3D lines.
     */
    public void clear3D() {
        line3DFloatCount = 0;
    }

    /**
     * Adds a normalized-device-coordinate 2D line segment.
     *
     * @param x1 the first x in the -1..1 range
     * @param y1 the first y in the -1..1 range
     * @param x2 the second x in the -1..1 range
     * @param y2 the second y in the -1..1 range
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    public void line2D(float x1, float y1, float x2, float y2,
            float red, float green, float blue, float alpha) {
        ensureNotDisposed();
        ensureLine2DVertexCapacity(2);
        line2DFloatCount = appendVertex(line2DVertices, line2DFloatCount, x1, y1, 0.0f, red, green, blue, alpha);
        line2DFloatCount = appendVertex(line2DVertices, line2DFloatCount, x2, y2, 0.0f, red, green, blue, alpha);
    }

    /**
     * Adds a world-space 3D line segment.
     *
     * @param x1 the first x
     * @param y1 the first y
     * @param z1 the first z
     * @param x2 the second x
     * @param y2 the second y
     * @param z2 the second z
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    public void line3D(float x1, float y1, float z1, float x2, float y2, float z2,
            float red, float green, float blue, float alpha) {
        ensureNotDisposed();
        ensureLine3DVertexCapacity(2);
        line3DFloatCount = appendVertex(line3DVertices, line3DFloatCount, x1, y1, z1, red, green, blue, alpha);
        line3DFloatCount = appendVertex(line3DVertices, line3DFloatCount, x2, y2, z2, red, green, blue, alpha);
    }

    /**
     * Renders queued 2D lines.
     */
    public void render2D() {
        renderLines(line2DVertices, line2DFloatCount, true, null, 0, 0, 0, 0);
    }

    /**
     * Renders queued 2D lines in a viewport.
     *
     * @param x the viewport x coordinate
     * @param y the viewport y coordinate
     * @param width the viewport width, or 0 to keep the current full pass viewport
     * @param height the viewport height, or 0 to keep the current full pass viewport
     */
    public void render2D(int x, int y, int width, int height) {
        renderLines(line2DVertices, line2DFloatCount, true, null, x, y, width, height);
    }

    /**
     * Renders queued 3D lines.
     *
     * @param viewProjection the view-projection matrix values
     */
    public void render3D(float[] viewProjection) {
        render3D(viewProjection, 0, 0, 0, 0);
    }

    /**
     * Renders queued 3D lines in a viewport.
     *
     * @param viewProjection the view-projection matrix values
     * @param x the viewport x coordinate
     * @param y the viewport y coordinate
     * @param width the viewport width, or 0 to keep the current full pass viewport
     * @param height the viewport height, or 0 to keep the current full pass viewport
     */
    public void render3D(float[] viewProjection, int x, int y, int width, int height) {
        renderLines(line3DVertices, line3DFloatCount, false, viewProjection, x, y, width, height);
    }

    private void renderLines(float[] vertices, int floatCount, boolean line2D, float[] viewProjection,
            int x, int y, int width, int height) {
        ensureNotDisposed();
        if (floatCount == 0) {
            return;
        }
        if (!line2D && (viewProjection == null || viewProjection.length < 16)) {
            throw new FdxException("ImmediateModeRenderer 3D rendering requires a 4x4 view-projection matrix");
        }
        int vertexCount = floatCount / FLOATS_PER_VERTEX;
        int byteCount = vertexCount * BYTES_PER_VERTEX;
        if (line2D) {
            ensureLine2DVertexBuffer(byteCount);
        }
        else {
            ensureLine3DVertexBuffer(byteCount);
        }
        ensureUploadBuffer(byteCount);
        uploadBuffer.clear();
        for (int i = 0; i < floatCount; i++) {
            uploadBuffer.putFloat(vertices[i]);
        }
        uploadBuffer.flip();
        Buffer vertexBuffer = line2D ? line2DVertexBuffer : line3DVertexBuffer;
        graphics.device().writeBuffer(vertexBuffer, uploadBuffer);
        GraphicsFrame frame = graphics.currentFrame();
        RenderPassDescriptor descriptor = line2D ? line2DRenderPassDescriptor : line3DRenderPassDescriptor;
        RenderPass pass = frame.commandEncoder().beginRenderPass(descriptor
                .colorAttachment(frame.colorAttachment()));
        try {
            if (width > 0 && height > 0) {
                pass.setViewport(x, y, width, height);
                pass.setScissor(x, y, width, height);
            }
            pass.setPipeline(line2D ? line2DPipeline : line3DPipeline);
            uniformBlock.setFloatMatrix(MODEL, IDENTITY_MATRIX, 0);
            uniformBlock.setFloatMatrix(VIEW_PROJECTION,
                    line2D ? IDENTITY_MATRIX : viewProjection, 0);
            pass.setParameterBlock(0, 0, uniformBlock);
            pass.setVertexBuffer(vertexBuffer);
            pass.draw(vertexCount, 1, 0, 0);
        }
        finally {
            pass.end();
        }
    }

    private static ShaderReflection reflection() {
        ShaderValueType float3 =
                ShaderValueType.vector(ShaderScalarType.F32, 3);
        ShaderValueType float4 =
                ShaderValueType.vector(ShaderScalarType.F32, 4);
        ShaderStageVariable vertexPosition = ShaderStageVariable.of(
                "input.position", "position", 0, -1, -1, float3,
                ShaderInterpolation.PERSPECTIVE,
                ShaderInterpolationSampling.CENTER);
        ShaderStageVariable vertexColor = ShaderStageVariable.of(
                "input.color", "color", 1, -1, -1, float4,
                ShaderInterpolation.PERSPECTIVE,
                ShaderInterpolationSampling.CENTER);
        ShaderStageVariable outputColor = ShaderStageVariable.of(
                "<retval>.color", "color", 0, -1, -1, float4,
                ShaderInterpolation.PERSPECTIVE,
                ShaderInterpolationSampling.CENTER);
        ShaderStageVariable fragmentColor = ShaderStageVariable.of(
                "input.color", "color", 0, -1, -1, float4,
                ShaderInterpolation.PERSPECTIVE,
                ShaderInterpolationSampling.CENTER);
        ShaderStageVariable renderTarget = ShaderStageVariable.of(
                "<retval>", "", 0, -1, -1, float4,
                ShaderInterpolation.PERSPECTIVE,
                ShaderInterpolationSampling.CENTER);
        ShaderBinding uniforms = ShaderBinding.builder(0, 0,
                        "uniforms", ShaderResourceKind.UNIFORM_BUFFER)
                .visibility(ShaderStageVisibility.VERTEX)
                .access(ShaderResourceAccess.READ)
                .buffer(128, 128, 16, UNIFORM_LAYOUT)
                .build();
        return ShaderReflection.complete(ShaderProfile.PORTABLE_WEBGPU,
                new ShaderEntryPoint[] {
                        ShaderEntryPoint.builder("vertexMain",
                                        ShaderStage.VERTEX)
                                .builtins(ShaderBuiltinUsage.POSITION, -1)
                                .inputs(vertexPosition, vertexColor)
                                .outputs(outputColor)
                                .resources(ShaderResourceUse.of(0, 0, 128))
                                .build(),
                        ShaderEntryPoint.builder("fragmentMain",
                                        ShaderStage.FRAGMENT)
                                .builtins(ShaderBuiltinUsage.POSITION, -1)
                                .inputs(fragmentColor)
                                .outputs(renderTarget)
                                .build()
                },
                new ShaderBinding[] { uniforms }, new String[0]);
    }

    /**
     * Releases resources held by this renderer.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (line2DVertexBuffer != null) {
            line2DVertexBuffer.dispose();
            line2DVertexBuffer = null;
        }
        if (line3DVertexBuffer != null) {
            line3DVertexBuffer.dispose();
            line3DVertexBuffer = null;
        }
        if (line2DPipeline != null) {
            line2DPipeline.dispose();
            line2DPipeline = null;
        }
        if (line3DPipeline != null) {
            line3DPipeline.dispose();
            line3DPipeline = null;
        }
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
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

    private static int appendVertex(float[] vertices, int floatCount,
            float x, float y, float z, float red, float green, float blue, float alpha) {
        vertices[floatCount++] = x;
        vertices[floatCount++] = y;
        vertices[floatCount++] = z;
        vertices[floatCount++] = red;
        vertices[floatCount++] = green;
        vertices[floatCount++] = blue;
        vertices[floatCount++] = alpha;
        return floatCount;
    }

    private void ensureLine2DVertexCapacity(int addedVertices) {
        int required = line2DFloatCount + addedVertices * FLOATS_PER_VERTEX;
        if (required <= line2DVertices.length) {
            return;
        }
        int newCapacity = line2DVertices.length;
        while (newCapacity < required) {
            newCapacity *= 2;
        }
        float[] grown = new float[newCapacity];
        System.arraycopy(line2DVertices, 0, grown, 0, line2DVertices.length);
        line2DVertices = grown;
    }

    private void ensureLine3DVertexCapacity(int addedVertices) {
        int required = line3DFloatCount + addedVertices * FLOATS_PER_VERTEX;
        if (required <= line3DVertices.length) {
            return;
        }
        int newCapacity = line3DVertices.length;
        while (newCapacity < required) {
            newCapacity *= 2;
        }
        float[] grown = new float[newCapacity];
        System.arraycopy(line3DVertices, 0, grown, 0, line3DVertices.length);
        line3DVertices = grown;
    }

    private void ensureLine2DVertexBuffer(int byteCount) {
        if (line2DVertexBuffer != null && line2DVertexBuffer.size() >= byteCount) {
            return;
        }
        if (line2DVertexBuffer != null) {
            line2DVertexBuffer.dispose();
        }
        line2DVertexBuffer = graphics.device().createBuffer(BufferDescriptor.vertex("immediate renderer 2d vertices",
                byteCount));
    }

    private void ensureLine3DVertexBuffer(int byteCount) {
        if (line3DVertexBuffer != null && line3DVertexBuffer.size() >= byteCount) {
            return;
        }
        if (line3DVertexBuffer != null) {
            line3DVertexBuffer.dispose();
        }
        line3DVertexBuffer = graphics.device().createBuffer(BufferDescriptor.vertex("immediate renderer 3d vertices",
                byteCount));
    }

    private void ensureUploadBuffer(int byteCount) {
        if (uploadBuffer != null && uploadBuffer.capacity() >= byteCount) {
            return;
        }
        uploadBuffer = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException("ImmediateModeRenderer has been disposed");
        }
    }
}
