package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.collections.KeyComparison;
import io.github.libfdx.collections.ObjectIterable;
import io.github.libfdx.collections.ObjectIterator;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.graphics.shader.reflection.ShaderInterpolation;
import io.github.libfdx.graphics.shader.reflection.ShaderInterpolationSampling;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
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
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.math.Matrix4;

import io.github.libfdx.math.Vector3;

/**
 * Renders a directional-light shadow map into a sampled texture.
 *
 * @author xpenatan
 */
public final class DirectionalShadowMap3D implements Disposable {
    private static final int PRIMITIVE_TOPOLOGY_COUNT = PrimitiveTopology.values().length;
    private final GraphicsContext graphics;
    private final Texture texture;
    private final DefaultRenderTarget3D target;
    private final ShadowDepthShaderProvider shaderProvider;
    private final ModelBatch batch;
    private final Camera camera = new Camera();
    private final Matrix4 lightViewProjection = new Matrix4();
    private float centerX;
    private float centerY;
    private float centerZ;
    private float halfSize = 4.5f;
    private float near = 0.1f;
    private float far = 18.0f;
    private float bias = 0.022f;
    private float strength = 0.62f;
    private boolean disposed;

    /**
     * Creates a directional shadow map.
     *
     * @param graphics the graphics context
     * @param width the width in pixels
     * @param height the height in pixels
     */
    public DirectionalShadowMap3D(GraphicsContext graphics, int width, int height) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        if (width <= 0 || height <= 0) {
            throw new FdxException("Shadow map dimensions must be greater than zero");
        }
        this.graphics = graphics;
        texture = graphics.device().createTexture(TextureDescriptor
                .rgba8RenderTarget("directional shadow map", width, height)
                .filter(TextureFilter.NEAREST));
        target = new DefaultRenderTarget3D(width, height, texture.view());
        shaderProvider = new ShadowDepthShaderProvider(graphics);
        batch = new ModelBatch(graphics, new ModelBatchConfig().shaderProvider(shaderProvider));
    }

    /**
     * Sets the light-space bounds and returns this shadow map.
     *
     * @param centerX the center x coordinate
     * @param centerY the center y coordinate
     * @param centerZ the center z coordinate
     * @param halfSize the half size of the orthographic light area
     * @param near the near distance
     * @param far the far distance
     * @return this shadow map for chaining
     */
    public DirectionalShadowMap3D bounds(float centerX, float centerY, float centerZ,
            float halfSize, float near, float far) {
        if (halfSize <= 0.0f) {
            throw new FdxException("Shadow map half size must be greater than zero");
        }
        if (near <= 0.0f || far <= near) {
            throw new FdxException("Shadow map near/far range is invalid");
        }
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.halfSize = halfSize;
        this.near = near;
        this.far = far;
        return this;
    }

    /**
     * Sets the depth comparison bias and returns this shadow map.
     *
     * @param bias the depth comparison bias
     * @return this shadow map for chaining
     */
    public DirectionalShadowMap3D bias(float bias) {
        this.bias = Math.max(0.0f, bias);
        return this;
    }

    /**
     * Sets the shadow strength and returns this shadow map.
     *
     * @param strength the shadow strength from 0 to 1
     * @return this shadow map for chaining
     */
    public DirectionalShadowMap3D strength(float strength) {
        this.strength = Math.max(0.0f, Math.min(1.0f, strength));
        return this;
    }

    /**
     * Renders model instances into this shadow map.
     *
     * @param light the directional light
     * @param instances the model instances
     */
    public void render(DirectionalLight light, ModelInstance[] instances) {
        ensureNotDisposed();
        if (instances == null) {
            throw new FdxException("ModelInstance array cannot be null");
        }
        RenderPass pass = beginPass(light);
        try {
            batch.begin(pass, camera);
            for (int i = 0; i < instances.length; i++) {
                if (instances[i] != null) {
                    batch.render(instances[i]);
                }
            }
            batch.end();
        }
        finally {
            pass.end();
        }
    }

    /**
     * Renders model instances into this shadow map.
     *
     * @param light the directional light
     * @param instances the model instances
     */
    public void render(DirectionalLight light, ObjectIterable<? extends ModelInstance> instances) {
        ensureNotDisposed();
        if (instances == null) {
            throw new FdxException("ModelInstance iterable cannot be null");
        }
        RenderPass pass = beginPass(light);
        try {
            batch.begin(pass, camera);
            if (instances instanceof ArrayView<?>) {
                ArrayView<?> values = (ArrayView<?>)instances;
                for (int i = 0; i < values.size(); i++) {
                    ModelInstance instance = (ModelInstance) values.get(i);
                    if (instance != null) {
                        batch.render(instance);
                    }
                }
            }
            else {
                ObjectIterator<? extends ModelInstance> iterator = instances.iterator();
                while (iterator.hasNext()) {
                    ModelInstance instance = iterator.next();
                    if (instance != null) {
                        batch.render(instance);
                    }
                }
            }
            batch.end();
        }
        finally {
            pass.end();
        }
    }

    /**
     * Returns the shadow texture.
     *
     * @return the shadow texture
     */
    public Texture texture() {
        return texture;
    }

    /**
     * Returns the light view projection matrix.
     *
     * @return the light view projection matrix
     */
    public Matrix4 lightViewProjection() {
        return lightViewProjection;
    }

    /**
     * Returns the depth comparison bias.
     *
     * @return the depth comparison bias
     */
    public float bias() {
        return bias;
    }

    /**
     * Returns the shadow strength.
     *
     * @return the shadow strength
     */
    public float strength() {
        return strength;
    }

    private RenderPass beginPass(DirectionalLight light) {
        updateLightCamera(light);
        GraphicsFrame frame = graphics.currentFrame();
        return frame.commandEncoder().beginRenderPass(RenderPassDescriptor
                .color(target.colorAttachment(0), LoadOp.clear(1.0f, 1.0f, 1.0f, 1.0f), StoreOp.store())
                .depthClear(1.0f)
                .label("directional shadow map pass"));
    }

    private void updateLightCamera(DirectionalLight light) {
        if (light == null) {
            throw new FdxException("DirectionalLight cannot be null");
        }
        Vector3 direction = light.direction();
        float directionX = direction.x();
        float directionY = direction.y();
        float directionZ = direction.z();
        float length = (float)Math.sqrt(directionX * directionX + directionY * directionY
                + directionZ * directionZ);
        if (length <= 0.000001f) {
            directionX = 0.0f;
            directionY = -1.0f;
            directionZ = 0.0f;
        }
        else {
            float invLength = 1.0f / length;
            directionX *= invLength;
            directionY *= invLength;
            directionZ *= invLength;
        }

        float distance = (near + far) * 0.5f;
        float eyeX = centerX - directionX * distance;
        float eyeY = centerY - directionY * distance;
        float eyeZ = centerZ - directionZ * distance;
        camera.projection(CameraProjection.ORTHOGRAPHIC)
                .viewport(halfSize * 2.0f, halfSize * 2.0f)
                .zoom(1.0f)
                .nearFar(near, far)
                .position(eyeX, eyeY, eyeZ)
                .lookAt(centerX, centerY, centerZ);
        setStableLightUp(directionX, directionY, directionZ);
        lightViewProjection.set(camera.combined());
    }

    private void setStableLightUp(float directionX, float directionY, float directionZ) {
        float upX = -directionX * directionY;
        float upY = 1.0f - directionY * directionY;
        float upZ = -directionZ * directionY;
        float length = (float)Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        if (length <= 0.0001f) {
            upX = -directionX * directionZ;
            upY = -directionY * directionZ;
            upZ = 1.0f - directionZ * directionZ;
            length = (float)Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        }
        if (length <= 0.0001f) {
            camera.up(1.0f, 0.0f, 0.0f);
            return;
        }
        float invLength = 1.0f / length;
        camera.up(upX * invLength, upY * invLength, upZ * invLength);
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException("DirectionalShadowMap3D has been disposed");
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
        batch.dispose();
        shaderProvider.dispose();
        texture.dispose();
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

    private static final class ShadowDepthShaderProvider implements ShaderProvider3D, Disposable {
        private final ShadowDepthShader shader;

        ShadowDepthShaderProvider(GraphicsContext graphics) {
            shader = new ShadowDepthShader(graphics);
        }

        @Override
        public Shader3D shader(Renderable3D renderable, RenderContext3D context) {
            if (!shader.canRender(renderable)) {
                throw new FdxException("Shadow shader requires meshes with a position attribute at location 0");
            }
            return shader;
        }

        @Override
        public void dispose() {
            shader.dispose();
        }

        @Override
        public boolean isDisposed() {
            return shader.isDisposed();
        }
    }

    private static final class ShadowDepthShader implements Shader3D {
        private static final String SOURCE = """
                struct VertexInput {
                    @location(0) position : vec3f,
                };
                struct VertexOutput {
                    @builtin(position) position : vec4f,
                    @location(0) depth : f32,
                };
                struct Uniforms {
                    model : mat4x4<f32>,
                    viewProjection : mat4x4<f32>,
                };
                @group(0) @binding(0) var<uniform> uniforms : Uniforms;
                @vertex
                fn vertexMain(input : VertexInput) -> VertexOutput {
                    var output : VertexOutput;
                    let clip = uniforms.viewProjection * uniforms.model * vec4f(input.position, 1.0);
                    output.position = clip;
                    // libFDX cameras use the portable OpenGL-style -w..w
                    // clip-depth convention. WGSL/WebGPU clips z to 0..w,
                    // which discarded the near half of an orthographic shadow
                    // volume. Remap only the raster position; the separately
                    // encoded depth below remains in the same 0..1 space used
                    // by PBR shadow sampling on every provider.
                    output.position.z = clip.z * 0.5 + clip.w * 0.5;
                    output.depth = clamp((clip.z / clip.w) * 0.5 + 0.5, 0.0, 1.0);
                    return output;
                }
                @fragment
                fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                    let depth = min(input.depth, 0.999999);
                    let raw = fract(depth * vec2f(1.0, 255.0));
                    return vec4f(
                            raw.x - raw.y / 255.0,
                            raw.y,
                            0.0,
                            1.0);
                }
                """;
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
        private final GraphicsContext graphics;
        private final ShaderModule shaderModule;
        private final ObjectMap<VertexLayout, RenderPipeline[]> pipelines =
                new ObjectMap<VertexLayout, RenderPipeline[]>(KeyComparison.IDENTITY);
        private final ShaderParameterBlock uniformBlock =
                ShaderParameterBlock.allocate(UNIFORM_LAYOUT);
        private final float[] modelMatrix = new float[Matrix4.VALUE_COUNT];
        private final float[] viewProjectionMatrix = new float[Matrix4.VALUE_COUNT];
        private RenderContext3D context;
        private boolean disposed;

        ShadowDepthShader(GraphicsContext graphics) {
            this.graphics = graphics;
            shaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl(
                    "directional shadow depth", SOURCE));
        }

        @Override
        public boolean canRender(Renderable3D renderable) {
            if (renderable == null || renderable.meshPart() == null) {
                return false;
            }
            VertexLayout layout = renderable.meshPart().mesh().vertexLayout();
            if (layout.attributeCount() == 0) {
                return false;
            }
            VertexAttribute position = layout.attribute(0);
            return position.location() == 0
                    && position.format() == VertexFormat.FLOAT32X3;
        }

        @Override
        public void begin(RenderContext3D context) {
            if (disposed) {
                throw new FdxException("Shadow depth shader has been disposed");
            }
            this.context = context;
        }

        @Override
        public void render(Renderable3D renderable) {
            if (context == null) {
                throw new FdxException("Shader3D.begin() must be called before render");
            }
            MeshPart meshPart = renderable.meshPart();
            Mesh mesh = meshPart.mesh();
            RenderPass pass = context.pass();
            pass.setPipeline(pipeline(mesh.vertexLayout(), meshPart.primitiveTopology()));
            pass.setVertexBuffer(mesh.vertexBuffer());
            renderable.worldTransform().copyValues(modelMatrix, 0);
            context.camera().combined().copyValues(viewProjectionMatrix, 0);
            uniformBlock.setFloatMatrix(MODEL, modelMatrix, 0);
            uniformBlock.setFloatMatrix(VIEW_PROJECTION,
                    viewProjectionMatrix, 0);
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

        private static ShaderReflection reflection() {
            ShaderValueType f32 =
                    ShaderValueType.scalar(ShaderScalarType.F32);
            ShaderValueType float3 =
                    ShaderValueType.vector(ShaderScalarType.F32, 3);
            ShaderValueType float4 =
                    ShaderValueType.vector(ShaderScalarType.F32, 4);
            ShaderStageVariable vertexPosition = ShaderStageVariable.of(
                    "input.position", "position", 0, -1, -1, float3,
                    io.github.libfdx.graphics.shader.reflection.ShaderInterpolation.PERSPECTIVE,
                    io.github.libfdx.graphics.shader.reflection.ShaderInterpolationSampling.CENTER);
            ShaderStageVariable vertexDepth = ShaderStageVariable.of(
                    "<retval>.depth", "depth", 0, -1, -1, f32,
                    io.github.libfdx.graphics.shader.reflection.ShaderInterpolation.PERSPECTIVE,
                    io.github.libfdx.graphics.shader.reflection.ShaderInterpolationSampling.CENTER);
            ShaderStageVariable fragmentDepth = ShaderStageVariable.of(
                    "input.depth", "depth", 0, -1, -1, f32,
                    io.github.libfdx.graphics.shader.reflection.ShaderInterpolation.PERSPECTIVE,
                    io.github.libfdx.graphics.shader.reflection.ShaderInterpolationSampling.CENTER);
            ShaderStageVariable fragmentColor = ShaderStageVariable.of(
                    "<retval>", "", 0, -1, -1, float4,
                    io.github.libfdx.graphics.shader.reflection.ShaderInterpolation.PERSPECTIVE,
                    io.github.libfdx.graphics.shader.reflection.ShaderInterpolationSampling.CENTER);
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
                                    .inputs(vertexPosition)
                                    .outputs(vertexDepth)
                                    .resources(ShaderResourceUse.of(0, 0, 128))
                                    .build(),
                            ShaderEntryPoint.builder("fragmentMain",
                                            ShaderStage.FRAGMENT)
                                    .builtins(ShaderBuiltinUsage.POSITION, -1)
                                    .inputs(fragmentDepth)
                                    .outputs(fragmentColor)
                                    .build()
                    },
                    new ShaderBinding[] { uniforms }, new String[0]);
        }

        @Override
        public void end() {
            context = null;
        }

        private RenderPipeline pipeline(VertexLayout vertexLayout, PrimitiveTopology topology) {
            PrimitiveTopology actualTopology = topology != null ? topology : PrimitiveTopology.TRIANGLE_LIST;
            RenderPipeline[] variants = pipelines.get(vertexLayout);
            if (variants == null) {
                variants = new RenderPipeline[PRIMITIVE_TOPOLOGY_COUNT];
                pipelines.put(vertexLayout, variants);
            }
            int slot = actualTopology.ordinal();
            RenderPipeline pipeline = variants[slot];
            if (pipeline == null) {
                pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                        .shader(shaderModule, TextureFormat.RGBA8_UNORM)
                        .label("directional shadow depth")
                        .shaderReflection(REFLECTION)
                        .primitiveTopology(actualTopology)
                        .depthTestEnabled(true)
                        .depthWriteEnabled(true)
                        .vertexLayout(vertexLayout));
                variants[slot] = pipeline;
            }
            return pipeline;
        }

        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            ObjectIterator<RenderPipeline[]> iterator = pipelines.values().iterator();
            while (iterator.hasNext()) {
                RenderPipeline[] variants = iterator.next();
                for (int i = 0; i < variants.length; i++) {
                    if (variants[i] != null) {
                        variants[i].dispose();
                    }
                }
            }
            pipelines.clear();
            shaderModule.dispose();
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

}
