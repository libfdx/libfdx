package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderBuiltinUsage;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.reflection.ShaderInterpolation;
import io.github.libfdx.graphics.shader.reflection.ShaderInterpolationSampling;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.reflection.ShaderParameter;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceAccess;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceKind;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceUse;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderStageVariable;
import io.github.libfdx.graphics.shader.reflection.ShaderStageVisibility;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.math.Matrix4;

import java.util.List;

/**
 * Renders a WGSL-authored shell outline for static 3D meshes with normals.
 *
 * @author xpenatan
 */
public final class OutlineRenderer3D implements Disposable {
    private static final int PRIMITIVE_TOPOLOGY_COUNT = PrimitiveTopology.values().length;
    private static final String SHADER_BODY = """
            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) color : vec4f,
            };
            struct PbrUniforms {
                model : mat4x4<f32>,
                viewProjection : mat4x4<f32>,
                cameraPosition : vec4f,
                cameraDirection : vec4f,
                ambientColor : vec4f,
                lightDirection : vec4f,
                lightColorIntensity : vec4f,
                textureFlags : vec4f,
                emissiveFlags : vec4f,
                fogColor : vec4f,
                fogParams : vec4f,
                pointLightCount : vec4f,
                pointLightPositions : array<vec4f, 4>,
                pointLightColors : array<vec4f, 4>,
                spotLightCount : vec4f,
                spotLightPositions : array<vec4f, 4>,
                spotLightDirections : array<vec4f, 4>,
                spotLightColors : array<vec4f, 4>,
                spotLightCones : array<vec4f, 4>,
                shadowViewProjection0 : mat4x4<f32>,
                shadowViewProjection1 : mat4x4<f32>,
                shadowViewProjection2 : mat4x4<f32>,
                shadowViewProjection3 : mat4x4<f32>,
                shadowParams : vec4f,
                shadowCascadeSplits : vec4f,
            };
            @group(0) @binding(0) var<uniform> uniforms : PbrUniforms;
            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                let worldPosition = uniforms.model * vec4f(input.position, 1.0);
                let normalWorldRaw = (uniforms.model * vec4f(input.normal, 0.0)).xyz;
                let normalLength = length(normalWorldRaw);
                var normalWorld = vec3f(0.0, 0.0, 1.0);
                if (normalLength > 0.000001) {
                    normalWorld = normalWorldRaw / normalLength;
                }
                let radialRaw = (uniforms.model * vec4f(input.position, 0.0)).xyz;
                let radialLength = length(radialRaw);
                var expandDirection = normalWorld;
                if (radialLength > 0.000001) {
                    expandDirection = radialRaw / radialLength;
                }
                let expanded = worldPosition.xyz + expandDirection * max(uniforms.fogParams.x, 0.0);
                output.position = uniforms.viewProjection * vec4f(expanded, 1.0);
                output.color = uniforms.ambientColor;
                return output;
            }
            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                return input.color;
            }
            """;
    private static final String PBR_SOURCE = """
            struct VertexInput {
                @location(0) position : vec3f,
                @location(1) normal : vec3f,
                @location(2) uv : vec2f,
                @location(3) color : vec4f,
                @location(4) pbr : vec3f,
                @location(5) emissive : vec3f,
            };
            """ + SHADER_BODY;
    private static final String POSITION_NORMAL_SOURCE = """
            struct VertexInput {
                @location(0) position : vec3f,
                @location(1) normal : vec3f,
            };
            """ + SHADER_BODY;
    private static final String POSITION_NORMAL_COLOR_SOURCE = """
            struct VertexInput {
                @location(0) position : vec3f,
                @location(1) normal : vec3f,
                @location(3) color : vec4f,
            };
            """ + SHADER_BODY;
    private static final ShaderValueType MATRIX4 = ShaderValueType
            .matrix(ShaderScalarType.F32, 4, 4, 16)
            .named("mat4x4<f32>");
    private static final ShaderValueType FLOAT4 = ShaderValueType
            .vector(ShaderScalarType.F32, 4)
            .named("vec4<f32>");
    private static final ShaderValueType FLOAT4_ARRAY = ShaderValueType
            .array(FLOAT4, 4, 16)
            .named("array<vec4<f32>, 4>");
    private static final ShaderParameterLayout UNIFORM_LAYOUT =
            ShaderParameterLayout.of(976, 16,
                    parameter("model", MATRIX4, 0, 64),
                    parameter("viewProjection", MATRIX4, 64, 64),
                    parameter("cameraPosition", FLOAT4, 128, 16),
                    parameter("cameraDirection", FLOAT4, 144, 16),
                    parameter("ambientColor", FLOAT4, 160, 16),
                    parameter("lightDirection", FLOAT4, 176, 16),
                    parameter("lightColorIntensity", FLOAT4, 192, 16),
                    parameter("textureFlags", FLOAT4, 208, 16),
                    parameter("emissiveFlags", FLOAT4, 224, 16),
                    parameter("fogColor", FLOAT4, 240, 16),
                    parameter("fogParams", FLOAT4, 256, 16),
                    parameter("pointLightCount", FLOAT4, 272, 16),
                    parameter("pointLightPositions", FLOAT4_ARRAY, 288, 64),
                    parameter("pointLightColors", FLOAT4_ARRAY, 352, 64),
                    parameter("spotLightCount", FLOAT4, 416, 16),
                    parameter("spotLightPositions", FLOAT4_ARRAY, 432, 64),
                    parameter("spotLightDirections", FLOAT4_ARRAY, 496, 64),
                    parameter("spotLightColors", FLOAT4_ARRAY, 560, 64),
                    parameter("spotLightCones", FLOAT4_ARRAY, 624, 64),
                    parameter("shadowViewProjection0", MATRIX4, 688, 64),
                    parameter("shadowViewProjection1", MATRIX4, 752, 64),
                    parameter("shadowViewProjection2", MATRIX4, 816, 64),
                    parameter("shadowViewProjection3", MATRIX4, 880, 64),
                    parameter("shadowParams", FLOAT4, 944, 16),
                    parameter("shadowCascadeSplits", FLOAT4, 960, 16));
    private static final ShaderParameterHandle MODEL =
            UNIFORM_LAYOUT.requireHandle("model");
    private static final ShaderParameterHandle VIEW_PROJECTION =
            UNIFORM_LAYOUT.requireHandle("viewProjection");
    private static final ShaderParameterHandle AMBIENT_COLOR =
            UNIFORM_LAYOUT.requireHandle("ambientColor");
    private static final ShaderParameterHandle FOG_PARAMS =
            UNIFORM_LAYOUT.requireHandle("fogParams");
    private static final ShaderReflection PBR_REFLECTION = reflection(
            variable("input.position", "position", 0,
                    ShaderValueType.vector(ShaderScalarType.F32, 3)),
            variable("input.normal", "normal", 1,
                    ShaderValueType.vector(ShaderScalarType.F32, 3)),
            variable("input.uv", "uv", 2,
                    ShaderValueType.vector(ShaderScalarType.F32, 2)),
            variable("input.color", "color", 3,
                    ShaderValueType.vector(ShaderScalarType.F32, 4)),
            variable("input.pbr", "pbr", 4,
                    ShaderValueType.vector(ShaderScalarType.F32, 3)),
            variable("input.emissive", "emissive", 5,
                    ShaderValueType.vector(ShaderScalarType.F32, 3)));
    private static final ShaderReflection POSITION_NORMAL_REFLECTION = reflection(
            variable("input.position", "position", 0,
                    ShaderValueType.vector(ShaderScalarType.F32, 3)),
            variable("input.normal", "normal", 1,
                    ShaderValueType.vector(ShaderScalarType.F32, 3)));
    private static final ShaderReflection POSITION_NORMAL_COLOR_REFLECTION = reflection(
            variable("input.position", "position", 0,
                    ShaderValueType.vector(ShaderScalarType.F32, 3)),
            variable("input.normal", "normal", 1,
                    ShaderValueType.vector(ShaderScalarType.F32, 3)),
            variable("input.color", "color", 3,
                    ShaderValueType.vector(ShaderScalarType.F32, 4)));

    private final GraphicsContext graphics;
    private final DefaultRenderQueue3D queue = new DefaultRenderQueue3D();
    private final OutlineShaderVariant pbrVariant;
    private final OutlineShaderVariant positionNormalVariant;
    private final OutlineShaderVariant positionNormalColorVariant;
    private final ShaderParameterBlock uniformBlock =
            ShaderParameterBlock.allocate(UNIFORM_LAYOUT);
    private final RenderPassDescriptor renderPassDescriptor =
            new RenderPassDescriptor().label("outline renderer 3d pass");
    private final float[] modelMatrix = new float[Matrix4.VALUE_COUNT];
    private final float[] viewProjectionMatrix = new float[Matrix4.VALUE_COUNT];
    private RenderPass pass;
    private Camera camera;
    private boolean ownsPass;
    private boolean drawing;
    private boolean disposed;
    private float red = 0.0f;
    private float green = 0.82f;
    private float blue = 1.0f;
    private float alpha = 1.0f;
    private float outlineWidth = 0.055f;

    /**
     * Creates a 3D outline renderer.
     *
     * @param graphics the graphics context
     */
    public OutlineRenderer3D(GraphicsContext graphics) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        this.graphics = graphics;
        OutlineShaderVariant createdPbr = null;
        OutlineShaderVariant createdPositionNormal = null;
        OutlineShaderVariant createdPositionNormalColor = null;
        try {
            createdPbr = variant("outline renderer 3d pbr", PBR_SOURCE,
                    PBR_REFLECTION);
            createdPositionNormal = variant(
                    "outline renderer 3d position normal",
                    POSITION_NORMAL_SOURCE, POSITION_NORMAL_REFLECTION);
            createdPositionNormalColor = variant(
                    "outline renderer 3d position normal color",
                    POSITION_NORMAL_COLOR_SOURCE,
                    POSITION_NORMAL_COLOR_REFLECTION);
        }
        catch (RuntimeException | Error failure) {
            disposeAfterFailedCreation(createdPositionNormalColor, failure);
            disposeAfterFailedCreation(createdPositionNormal, failure);
            disposeAfterFailedCreation(createdPbr, failure);
            throw failure;
        }
        pbrVariant = createdPbr;
        positionNormalVariant = createdPositionNormal;
        positionNormalColorVariant = createdPositionNormalColor;
    }

    /**
     * Begins the operation.
     *
     * @param camera the camera
     */
    public void begin(Camera camera) {
        begin(LoadOp.load(), camera);
    }

    /**
     * Begins the operation.
     *
     * @param loadOp the load op
     * @param camera the camera
     */
    public void begin(LoadOp loadOp, Camera camera) {
        ensureNotDisposed();
        ensureCamera(camera);
        GraphicsFrame frame = graphics.currentFrame();
        pass = frame.commandEncoder().beginRenderPass(renderPassDescriptor
                .colorAttachment(frame.colorAttachment())
                .colorLoadOp(loadOp != null ? loadOp : LoadOp.load())
                .colorStoreOp(StoreOp.store()));
        ownsPass = true;
        this.camera = camera;
        drawing = true;
        queue.clear();
    }

    /**
     * Begins the operation.
     *
     * @param pass the render pass
     * @param camera the camera
     */
    public void begin(RenderPass pass, Camera camera) {
        ensureNotDisposed();
        ensureCamera(camera);
        if (pass == null) {
            throw new FdxException("RenderPass cannot be null");
        }
        this.pass = pass;
        this.camera = camera;
        ownsPass = false;
        drawing = true;
        queue.clear();
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
    public OutlineRenderer3D outlineColor(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        return this;
    }

    /**
     * Sets the outline width in world units and returns this renderer.
     *
     * @param width the outline width
     * @return this renderer for chaining
     */
    public OutlineRenderer3D outlineWidth(float width) {
        if (Float.isNaN(width) || width < 0.0f) {
            throw new FdxException("3D outline width cannot be negative");
        }
        outlineWidth = width;
        return this;
    }

    /**
     * Renders the current content.
     *
     * @param instance the instance
     */
    public void render(ModelInstance instance) {
        ensureDrawing();
        if (instance == null) {
            throw new FdxException("ModelInstance cannot be null");
        }
        instance.collectRenderables(queue);
    }

    /**
     * Renders the current content.
     *
     * @param instances the instances
     */
    public void render(ModelInstance[] instances) {
        ensureDrawing();
        if (instances == null) {
            throw new FdxException("ModelInstance array cannot be null");
        }
        for (int i = 0; i < instances.length; i++) {
            if (instances[i] != null) {
                render(instances[i]);
            }
        }
    }

    /**
     * Renders the current content.
     *
     * @param instances the instances
     */
    public void render(Iterable<? extends ModelInstance> instances) {
        ensureDrawing();
        if (instances == null) {
            throw new FdxException("ModelInstance iterable cannot be null");
        }
        if (instances instanceof List<?>) {
            List<?> values = (List<?>) instances;
            for (int i = 0; i < values.size(); i++) {
                ModelInstance instance = (ModelInstance) values.get(i);
                if (instance != null) {
                    render(instance);
                }
            }
            return;
        }
        for (ModelInstance instance : instances) {
            if (instance != null) {
                render(instance);
            }
        }
    }

    /**
     * Renders the current content.
     *
     * @param renderable the renderable
     */
    public void render(Renderable3D renderable) {
        ensureDrawing();
        if (renderable == null) {
            throw new FdxException("Renderable3D cannot be null");
        }
        queue.add(renderable);
    }

    /**
     * Runs the flush step.
     */
    public void flush() {
        ensureDrawing();
        if (queue.size() == 0) {
            return;
        }
        queue.sort(camera);
        for (int i = 0; i < queue.size(); i++) {
            draw(queue.get(i));
        }
        queue.clear();
    }

    /**
     * Ends the operation.
     */
    public void end() {
        ensureDrawing();
        RenderPass activePass = pass;
        boolean endOwnedPass = ownsPass;
        try {
            flush();
        }
        finally {
            queue.clear();
            drawing = false;
            camera = null;
            pass = null;
            ownsPass = false;
            if (endOwnedPass) {
                activePass.end();
            }
        }
    }

    private void draw(Renderable3D renderable) {
        MeshPart meshPart = renderable.meshPart();
        Mesh mesh = meshPart.mesh();
        pass.setPipeline(pipeline(mesh.vertexLayout(), meshPart.primitiveTopology()));
        pass.setVertexBuffer(mesh.vertexBuffer());
        renderable.worldTransform().copyValues(modelMatrix, 0);
        camera.combined().copyValues(viewProjectionMatrix, 0);
        uniformBlock.setFloatMatrix(MODEL, modelMatrix, 0);
        uniformBlock.setFloatMatrix(VIEW_PROJECTION,
                viewProjectionMatrix, 0);
        uniformBlock.setFloat4(AMBIENT_COLOR, red, green, blue, alpha);
        uniformBlock.setFloat4(FOG_PARAMS, outlineWidth, 0.0f, 0.0f,
                0.0f);
        pass.setParameterBlock(0, 0, uniformBlock);
        int indexCount = meshPart.indexCount() > 0 ? meshPart.indexCount() : mesh.indexCount();
        if (indexCount > 0) {
            pass.setIndexBuffer(mesh.indexBuffer());
            pass.drawIndexed(indexCount, 1, meshPart.firstIndex(), 0, 0);
            return;
        }
        int vertexCount = meshPart.vertexCount() > 0 ? meshPart.vertexCount() : mesh.vertexCount();
        pass.draw(vertexCount, 1, meshPart.firstVertex(), 0);
    }

    private static ShaderParameter parameter(String name,
            ShaderValueType type, long offset, long size) {
        return ShaderParameter.of(name, type, offset, size, 16);
    }

    private static ShaderReflection reflection(ShaderStageVariable... vertexInputs) {
        ShaderValueType float4 =
                ShaderValueType.vector(ShaderScalarType.F32, 4);
        ShaderStageVariable outputColor = variable(
                "<retval>.color", "color", 0, float4);
        ShaderStageVariable fragmentColor = variable(
                "input.color", "color", 0, float4);
        ShaderBinding uniforms = ShaderBinding.builder(0, 0,
                        "uniforms", ShaderResourceKind.UNIFORM_BUFFER)
                .visibility(ShaderStageVisibility.VERTEX)
                .access(ShaderResourceAccess.READ)
                .buffer(976, 976, 16, UNIFORM_LAYOUT)
                .build();
        return ShaderReflection.complete(ShaderProfile.PORTABLE_WEBGPU,
                new ShaderEntryPoint[] {
                        ShaderEntryPoint.builder("vertexMain",
                                        ShaderStage.VERTEX)
                                .builtins(ShaderBuiltinUsage.POSITION, -1)
                                .inputs(vertexInputs)
                                .outputs(outputColor)
                                .resources(ShaderResourceUse.of(0, 0, 976))
                                .build(),
                        ShaderEntryPoint.builder("fragmentMain",
                                        ShaderStage.FRAGMENT)
                                .builtins(ShaderBuiltinUsage.POSITION, -1)
                                .inputs(fragmentColor)
                                .outputs(variable("<retval>", "", 0, float4))
                                .build()
                },
                new ShaderBinding[] { uniforms }, new String[0]);
    }

    private static ShaderStageVariable variable(String name,
            String variableName, int location, ShaderValueType type) {
        return ShaderStageVariable.of(name, variableName, location, -1, -1,
                type, ShaderInterpolation.PERSPECTIVE,
                ShaderInterpolationSampling.CENTER);
    }

    private RenderPipeline pipeline(VertexLayout vertexLayout, PrimitiveTopology topology) {
        PrimitiveTopology actualTopology = topology != null ? topology : PrimitiveTopology.TRIANGLE_LIST;
        OutlineShaderVariant variant = variant(vertexLayout);
        int slot = actualTopology.ordinal();
        RenderPipeline pipeline = variant.pipelines[slot];
        if (pipeline == null) {
            pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                    .shader(variant.shader, graphics.surfaceFormat())
                    .label(variant.label)
                    .shaderReflection(variant.reflection)
                    .primitiveTopology(actualTopology)
                    .depthTestEnabled(false)
                    .depthWriteEnabled(false)
                    .vertexLayout(vertexLayout));
            variant.pipelines[slot] = pipeline;
        }
        return pipeline;
    }

    private OutlineShaderVariant variant(String label, String source,
            ShaderReflection reflection) {
        return new OutlineShaderVariant(label,
                graphics.device().createShaderModule(
                        ShaderModuleDescriptor.wgsl(label, source)),
                reflection);
    }

    private OutlineShaderVariant variant(VertexLayout vertexLayout) {
        if (Mesh.PBR_LAYOUT.equals(vertexLayout)) {
            return pbrVariant;
        }
        if (Mesh.POSITION_NORMAL_LAYOUT.equals(vertexLayout)) {
            return positionNormalVariant;
        }
        if (Mesh.POSITION_NORMAL_COLOR_LAYOUT.equals(vertexLayout)) {
            return positionNormalColorVariant;
        }
        throw new FdxException("OutlineRenderer3D requires a static mesh layout with normals");
    }

    private static void disposeAfterFailedCreation(OutlineShaderVariant variant,
            Throwable failure) {
        if (variant == null) {
            return;
        }
        try {
            variant.dispose();
        }
        catch (Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void ensureCamera(Camera camera) {
        if (camera == null) {
            throw new FdxException("Camera cannot be null");
        }
    }

    private void ensureDrawing() {
        ensureNotDisposed();
        if (!drawing || pass == null || camera == null) {
            throw new FdxException("OutlineRenderer3D.begin() must be called before rendering");
        }
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException("OutlineRenderer3D has been disposed");
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
        pbrVariant.dispose();
        positionNormalVariant.dispose();
        positionNormalColorVariant.dispose();
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

    private static final class OutlineShaderVariant implements Disposable {
        private final String label;
        private final ShaderModule shader;
        private final ShaderReflection reflection;
        private final RenderPipeline[] pipelines =
                new RenderPipeline[PRIMITIVE_TOPOLOGY_COUNT];

        OutlineShaderVariant(String label, ShaderModule shader,
                ShaderReflection reflection) {
            this.label = label;
            this.shader = shader;
            this.reflection = reflection;
        }

        @Override
        public void dispose() {
            for (int i = 0; i < pipelines.length; i++) {
                if (pipelines[i] != null) {
                    pipelines[i].dispose();
                    pipelines[i] = null;
                }
            }
            shader.dispose();
        }

        @Override
        public boolean isDisposed() {
            return shader.isDisposed();
        }
    }

}
