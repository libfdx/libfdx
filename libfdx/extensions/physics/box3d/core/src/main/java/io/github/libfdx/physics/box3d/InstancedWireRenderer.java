package io.github.libfdx.physics.box3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
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
import io.github.libfdx.math.Matrix4;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** GPU-instanced, depth-tested line renderer used for repeated debug shapes. */
final class InstancedWireRenderer implements Disposable {
    private static final int INSTANCE_FLOATS = 20;
    private static final int INSTANCE_BYTES = INSTANCE_FLOATS * Float.BYTES;
    private static final int INITIAL_INSTANCE_CAPACITY = 128;
    private static final VertexLayout POSITION_LAYOUT = VertexLayout.of(3 * Float.BYTES,
            VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0));
    private static final VertexLayout INSTANCE_LAYOUT = VertexLayout.instance(INSTANCE_BYTES,
            VertexAttribute.of(1, VertexFormat.FLOAT32X4, 0),
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 16),
            VertexAttribute.of(3, VertexFormat.FLOAT32X4, 32),
            VertexAttribute.of(4, VertexFormat.FLOAT32X4, 48),
            VertexAttribute.of(5, VertexFormat.FLOAT32X4, 64));
    private static final ShaderValueType MATRIX4 = ShaderValueType
            .matrix(ShaderScalarType.F32, 4, 4, 16)
            .named("mat4x4<f32>");
    private static final ShaderParameterLayout UNIFORM_LAYOUT = ShaderParameterLayout.of(64, 16,
            ShaderParameter.of("viewProjection", MATRIX4, 0, 64, 16));
    private static final ShaderParameterHandle VIEW_PROJECTION =
            UNIFORM_LAYOUT.requireHandle("viewProjection");
    private static final ShaderReflection REFLECTION = reflection();
    private static final String SHADER = ""
            + "struct CameraUniforms {\n"
            + "    viewProjection: mat4x4<f32>,\n"
            + "};\n"
            + "@group(0) @binding(0) var<uniform> uniforms: CameraUniforms;\n"
            + "struct VertexInput {\n"
            + "    @location(0) position: vec3<f32>,\n"
            + "    @location(1) model0: vec4<f32>,\n"
            + "    @location(2) model1: vec4<f32>,\n"
            + "    @location(3) model2: vec4<f32>,\n"
            + "    @location(4) model3: vec4<f32>,\n"
            + "    @location(5) color: vec4<f32>,\n"
            + "};\n"
            + "struct VertexOutput {\n"
            + "    @builtin(position) position: vec4<f32>,\n"
            + "    @location(0) color: vec4<f32>,\n"
            + "};\n"
            + "@vertex fn vertexMain(input: VertexInput) -> VertexOutput {\n"
            + "    var output: VertexOutput;\n"
            + "    let model = mat4x4<f32>(input.model0, input.model1, input.model2, input.model3);\n"
            + "    output.position = uniforms.viewProjection * model * vec4<f32>(input.position, 1.0);\n"
            + "    output.position.z -= 0.0005 * output.position.w;\n"
            + "    output.color = input.color;\n"
            + "    return output;\n"
            + "}\n"
            + "@fragment fn fragmentMain(input: VertexOutput) -> @location(0) vec4<f32> {\n"
            + "    return input.color;\n"
            + "}\n";

    private final GraphicsContext graphics;
    private final Array<Geometry> geometries = new Array<Geometry>();
    private final RenderPassDescriptor passDescriptor = new RenderPassDescriptor()
            .label("box3d instanced wire pass")
            .colorLoadOp(LoadOp.load())
            .colorStoreOp(StoreOp.store())
            .depthEnabled(true);
    private final ShaderParameterBlock uniformBlock = ShaderParameterBlock.allocate(UNIFORM_LAYOUT);
    private ShaderModule shader;
    private RenderPipeline pipeline;
    private ByteBuffer uploadBuffer;
    private int drawCallCount;
    private boolean disposed;

    InstancedWireRenderer(GraphicsContext graphics) {
        if(graphics == null) {
            throw new FdxException("InstancedWireRenderer graphics cannot be null");
        }
        this.graphics = graphics;
        if(!graphics.device().capabilities().supports(GraphicsFeature.INSTANCED_DRAW)) {
            return;
        }
        shader = graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl(
                "box3d instanced wires", SHADER));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shader, graphics.surfaceFormat())
                .label("box3d instanced wires")
                .primitiveTopology(PrimitiveTopology.LINE_LIST)
                .vertexLayouts(POSITION_LAYOUT, INSTANCE_LAYOUT)
                .shaderReflection(REFLECTION)
                .depthTestEnabled(true)
                .depthWriteEnabled(false));
    }

    boolean supported() {
        return pipeline != null;
    }

    Geometry createGeometry(String id, float[] linePositions) {
        ensureNotDisposed();
        if(!supported() || linePositions == null || linePositions.length == 0) {
            return null;
        }
        Geometry geometry = new Geometry(id, linePositions);
        geometries.add(geometry);
        return geometry;
    }

    void beginFrame() {
        drawCallCount = 0;
        for(int i = 0; i < geometries.size(); i++) {
            geometries.get(i).beginFrame();
        }
    }

    void render(float[] viewProjection) {
        ensureNotDisposed();
        if(!supported() || !hasInstances()) {
            return;
        }
        if(viewProjection == null || viewProjection.length < Matrix4.VALUE_COUNT) {
            throw new FdxException("Instanced wire rendering requires a 4x4 view-projection matrix");
        }
        GraphicsFrame frame = graphics.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(passDescriptor
                .colorAttachment(frame.colorAttachment()));
        try {
            pass.setPipeline(pipeline);
            uniformBlock.setFloatMatrix(VIEW_PROJECTION, viewProjection, 0);
            pass.setParameterBlock(0, 0, uniformBlock);
            for(int i = 0; i < geometries.size(); i++) {
                Geometry geometry = geometries.get(i);
                if(geometry.render(pass)) {
                    drawCallCount++;
                }
            }
        }
        finally {
            pass.end();
        }
    }

    int drawCallCount() {
        return drawCallCount;
    }

    private boolean hasInstances() {
        for(int i = 0; i < geometries.size(); i++) {
            if(geometries.get(i).instanceCount > 0) {
                return true;
            }
        }
        return false;
    }

    private ByteBuffer uploadBuffer(int byteCount) {
        if(uploadBuffer == null || uploadBuffer.capacity() < byteCount) {
            uploadBuffer = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
        }
        uploadBuffer.clear();
        return uploadBuffer;
    }

    @Override
    public void dispose() {
        if(disposed) {
            return;
        }
        disposed = true;
        while(!geometries.isEmpty()) {
            geometries.get(geometries.size() - 1).dispose();
        }
        if(pipeline != null) {
            pipeline.dispose();
            pipeline = null;
        }
        if(shader != null) {
            shader.dispose();
            shader = null;
        }
        uploadBuffer = null;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private void ensureNotDisposed() {
        if(disposed) {
            throw new FdxException("InstancedWireRenderer has been disposed");
        }
    }

    final class Geometry implements Disposable {
        private final String id;
        private Buffer vertexBuffer;
        private Buffer instanceBuffer;
        private final int vertexCount;
        private float[] instances = new float[INITIAL_INSTANCE_CAPACITY * INSTANCE_FLOATS];
        private int instanceFloatCount;
        private int instanceCount;
        private boolean geometryDisposed;

        private Geometry(String id, float[] linePositions) {
            this.id = id != null ? id : "box3d-wire";
            if(linePositions.length % 6 != 0) {
                throw new FdxException("Instanced wire positions must contain complete line pairs");
            }
            vertexCount = linePositions.length / 3;
            int byteCount = linePositions.length * Float.BYTES;
            vertexBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                    this.id + " vertices", byteCount));
            ByteBuffer vertices = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
            vertices.asFloatBuffer().put(linePositions);
            vertices.limit(byteCount);
            vertices.position(0);
            graphics.device().writeBuffer(vertexBuffer, vertices);
            instanceBuffer = graphics.device().createBuffer(BufferDescriptor.vertex(
                    this.id + " instances", instances.length * Float.BYTES));
        }

        void append(Matrix4 transform, int color) {
            if(geometryDisposed) {
                return;
            }
            ensureInstanceCapacity(instanceFloatCount + INSTANCE_FLOATS);
            transform.copyValues(instances, instanceFloatCount);
            instanceFloatCount += Matrix4.VALUE_COUNT;
            instances[instanceFloatCount++] = ((color >>> 16) & 0xFF) / 255.0f;
            instances[instanceFloatCount++] = ((color >>> 8) & 0xFF) / 255.0f;
            instances[instanceFloatCount++] = (color & 0xFF) / 255.0f;
            instances[instanceFloatCount++] = 1.0f;
            instanceCount++;
        }

        private void beginFrame() {
            instanceFloatCount = 0;
            instanceCount = 0;
        }

        private boolean render(RenderPass pass) {
            if(geometryDisposed || instanceCount == 0) {
                return false;
            }
            int byteCount = instanceFloatCount * Float.BYTES;
            ensureInstanceBuffer(byteCount);
            ByteBuffer upload = uploadBuffer(byteCount);
            upload.asFloatBuffer().put(instances, 0, instanceFloatCount);
            upload.limit(byteCount);
            upload.position(0);
            graphics.device().writeBuffer(instanceBuffer, upload);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.setVertexBuffer(1, instanceBuffer);
            pass.draw(vertexCount, instanceCount, 0, 0);
            return true;
        }

        private void ensureInstanceCapacity(int requiredFloats) {
            if(requiredFloats <= instances.length) {
                return;
            }
            int newCapacity = instances.length;
            while(newCapacity < requiredFloats) {
                newCapacity *= 2;
            }
            float[] grown = new float[newCapacity];
            System.arraycopy(instances, 0, grown, 0, instanceFloatCount);
            instances = grown;
        }

        private void ensureInstanceBuffer(int byteCount) {
            if(instanceBuffer != null && instanceBuffer.size() >= byteCount) {
                return;
            }
            if(instanceBuffer != null) {
                instanceBuffer.dispose();
            }
            instanceBuffer = graphics.device().createBuffer(BufferDescriptor.vertex(
                    id + " instances", byteCount));
        }

        @Override
        public void dispose() {
            if(geometryDisposed) {
                return;
            }
            geometryDisposed = true;
            if(instanceBuffer != null) {
                instanceBuffer.dispose();
                instanceBuffer = null;
            }
            if(vertexBuffer != null) {
                vertexBuffer.dispose();
                vertexBuffer = null;
            }
            instances = new float[0];
            instanceFloatCount = 0;
            instanceCount = 0;
            geometries.removeValue(this, true);
        }

        @Override
        public boolean isDisposed() {
            return geometryDisposed;
        }
    }

    private static ShaderReflection reflection() {
        ShaderValueType float3 = ShaderValueType.vector(ShaderScalarType.F32, 3);
        ShaderValueType float4 = ShaderValueType.vector(ShaderScalarType.F32, 4);
        ShaderStageVariable position = input("input.position", "position", 0, float3);
        ShaderStageVariable model0 = input("input.model0", "model0", 1, float4);
        ShaderStageVariable model1 = input("input.model1", "model1", 2, float4);
        ShaderStageVariable model2 = input("input.model2", "model2", 3, float4);
        ShaderStageVariable model3 = input("input.model3", "model3", 4, float4);
        ShaderStageVariable color = input("input.color", "color", 5, float4);
        ShaderStageVariable outputColor = input("<retval>.color", "color", 0, float4);
        ShaderStageVariable fragmentColor = input("input.color", "color", 0, float4);
        ShaderStageVariable renderTarget = input("<retval>", "", 0, float4);
        ShaderBinding uniforms = ShaderBinding.builder(0, 0,
                        "uniforms", ShaderResourceKind.UNIFORM_BUFFER)
                .visibility(ShaderStageVisibility.VERTEX)
                .access(ShaderResourceAccess.READ)
                .buffer(64, 64, 16, UNIFORM_LAYOUT)
                .build();
        return ShaderReflection.complete(ShaderProfile.PORTABLE_WEBGPU,
                new ShaderEntryPoint[] {
                        ShaderEntryPoint.builder("vertexMain", ShaderStage.VERTEX)
                                .builtins(ShaderBuiltinUsage.POSITION, -1)
                                .inputs(position, model0, model1, model2, model3, color)
                                .outputs(outputColor)
                                .resources(ShaderResourceUse.of(0, 0, 64))
                                .build(),
                        ShaderEntryPoint.builder("fragmentMain", ShaderStage.FRAGMENT)
                                .builtins(ShaderBuiltinUsage.POSITION, -1)
                                .inputs(fragmentColor)
                                .outputs(renderTarget)
                                .build()
                },
                new ShaderBinding[] { uniforms }, new String[0]);
    }

    private static ShaderStageVariable input(String path, String name, int location,
            ShaderValueType type) {
        return ShaderStageVariable.of(path, name, location, -1, -1, type,
                ShaderInterpolation.PERSPECTIVE,
                ShaderInterpolationSampling.CENTER);
    }
}
