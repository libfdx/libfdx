package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.collections.ObjectIterable;
import io.github.libfdx.collections.ObjectIterator;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.camera.Camera;
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
import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * Renders a screen-space edge outline around selected 3D models.
 *
 * <p>The renderer first draws the selected geometry into a depth-independent,
 * single-channel mask. A second WGSL pass detects mask discontinuities and
 * alpha-composites the requested outline over the current frame. This produces
 * a stable pixel-width selection stroke without expanding geometry or relying
 * on provider-specific off-screen depth attachments.</p>
 *
 * <p>Both static position-based meshes and libFDX PBR skinned meshes are
 * supported. All shader sources remain canonical WGSL so every native backend
 * uses its normal Tint translation path.</p>
 *
 * @author xpenatan
 */
public final class EdgeDetectionOutlineRenderer3D implements Disposable {
    private static final int MAX_BONES = PbrShaderParameters.manifestMaxBones();
    private static final int POST_VERTEX_COUNT = 6;
    private static final int POST_FLOATS_PER_VERTEX = 12;
    private static final int POST_BYTES_PER_VERTEX = POST_FLOATS_PER_VERTEX * 4;
    private static final int POST_BUFFER_SIZE = POST_VERTEX_COUNT * POST_BYTES_PER_VERTEX;
    private static final VertexLayout POST_VERTEX_LAYOUT = VertexLayout.of(
            POST_BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8),
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 16),
            VertexAttribute.of(3, VertexFormat.FLOAT32X4, 32));
    private static final ShaderValueType MATRIX4 = ShaderValueType
            .matrix(ShaderScalarType.F32, 4, 4, 16)
            .named("mat4x4<f32>");
    private static final ShaderValueType FLOAT4 = ShaderValueType
            .vector(ShaderScalarType.F32, 4)
            .named("vec4<f32>");
    private static final ShaderValueType BONE_MATRIX_ARRAY = ShaderValueType
            .array(MATRIX4, MAX_BONES, 64)
            .named("array<mat4x4<f32>, " + MAX_BONES + ">");
    private static final ShaderParameterLayout STATIC_UNIFORM_LAYOUT =
            ShaderParameterLayout.of(128, 16,
                    parameter("model", MATRIX4, 0, 64),
                    parameter("viewProjection", MATRIX4, 64, 64));
    private static final ShaderParameterLayout SKINNED_UNIFORM_LAYOUT =
            ShaderParameterLayout.of(144L + MAX_BONES * 64L, 16,
                    parameter("model", MATRIX4, 0, 64),
                    parameter("viewProjection", MATRIX4, 64, 64),
                    parameter("skinningParams", FLOAT4, 128, 16),
                    parameter("boneMatrices", BONE_MATRIX_ARRAY, 144,
                            MAX_BONES * 64L));
    private static final ShaderParameterHandle STATIC_MODEL =
            STATIC_UNIFORM_LAYOUT.requireHandle("model");
    private static final ShaderParameterHandle STATIC_VIEW_PROJECTION =
            STATIC_UNIFORM_LAYOUT.requireHandle("viewProjection");
    private static final ShaderParameterHandle SKINNED_MODEL =
            SKINNED_UNIFORM_LAYOUT.requireHandle("model");
    private static final ShaderParameterHandle SKINNED_VIEW_PROJECTION =
            SKINNED_UNIFORM_LAYOUT.requireHandle("viewProjection");
    private static final ShaderParameterHandle SKINNING_PARAMS =
            SKINNED_UNIFORM_LAYOUT.requireHandle("skinningParams");
    private static final ShaderParameterHandle BONE_MATRICES =
            SKINNED_UNIFORM_LAYOUT.requireHandle("boneMatrices");
    private static final String STATIC_MASK_SOURCE = """
            struct VertexInput {
                @location(0) position : vec3f,
            };
            struct MaskUniforms {
                model : mat4x4<f32>,
                viewProjection : mat4x4<f32>,
            };
            @group(0) @binding(0) var<uniform> uniforms : MaskUniforms;

            @vertex
            fn vertexMain(input : VertexInput) -> @builtin(position) vec4f {
                return uniforms.viewProjection
                        * uniforms.model * vec4f(input.position, 1.0);
            }

            @fragment
            fn fragmentMain() -> @location(0) vec4f {
                return vec4f(1.0);
            }
            """;
    private static final String SKINNED_MASK_SOURCE = ("""
            struct VertexInput {
                @location(0) position : vec3f,
                @location(6) joints : vec4f,
                @location(7) weights : vec4f,
            };
            struct MaskUniforms {
                model : mat4x4<f32>,
                viewProjection : mat4x4<f32>,
                skinningParams : vec4f,
                boneMatrices : array<mat4x4<f32>, __MAX_BONES__>,
            };
            @group(0) @binding(0) var<uniform> uniforms : MaskUniforms;

            @vertex
            fn vertexMain(input : VertexInput) -> @builtin(position) vec4f {
                var localPosition = vec4f(input.position, 1.0);
                if (uniforms.skinningParams.x > 0.0) {
                    let joint0 = clamp(i32(input.joints.x), 0, __MAX_BONE_INDEX__);
                    let joint1 = clamp(i32(input.joints.y), 0, __MAX_BONE_INDEX__);
                    let joint2 = clamp(i32(input.joints.z), 0, __MAX_BONE_INDEX__);
                    let joint3 = clamp(i32(input.joints.w), 0, __MAX_BONE_INDEX__);
                    localPosition = uniforms.boneMatrices[joint0]
                                    * vec4f(input.position, 1.0) * input.weights.x
                            + uniforms.boneMatrices[joint1]
                                    * vec4f(input.position, 1.0) * input.weights.y
                            + uniforms.boneMatrices[joint2]
                                    * vec4f(input.position, 1.0) * input.weights.z
                            + uniforms.boneMatrices[joint3]
                                    * vec4f(input.position, 1.0) * input.weights.w;
                }
                return uniforms.viewProjection * uniforms.model * localPosition;
            }

            @fragment
            fn fragmentMain() -> @location(0) vec4f {
                return vec4f(1.0);
            }
            """)
            .replace("__MAX_BONES__", Integer.toString(MAX_BONES))
            .replace("__MAX_BONE_INDEX__", Integer.toString(MAX_BONES - 1));
    private static final String EDGE_SOURCE = """
            struct VertexInput {
                @location(0) position : vec2f,
                @location(1) texCoord : vec2f,
                @location(2) texelAndWidth : vec4f,
                @location(3) outlineColor : vec4f,
            };
            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) texCoord : vec2f,
                @location(1) texelAndWidth : vec4f,
                @location(2) outlineColor : vec4f,
            };
            @group(0) @binding(0) var maskTexture : texture_2d<f32>;
            @group(0) @binding(1) var maskSampler : sampler;

            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4f(input.position, 0.0, 1.0);
                output.texCoord = input.texCoord;
                output.texelAndWidth = input.texelAndWidth;
                output.outlineColor = input.outlineColor;
                return output;
            }

            fn maskAt(uv : vec2f) -> f32 {
                return textureSample(maskTexture, maskSampler,
                        clamp(uv, vec2f(0.0), vec2f(1.0))).r;
            }

            fn differs(first : f32, second : f32) -> f32 {
                return select(0.0, 1.0, abs(first - second) > 0.5);
            }

            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                let center = maskAt(input.texCoord);
                let sampleStep = input.texelAndWidth.xy
                        * max(input.texelAndWidth.z, 0.0);
                var edge = differs(center,
                        maskAt(input.texCoord + vec2f(-sampleStep.x, 0.0)));
                edge = max(edge, differs(center,
                        maskAt(input.texCoord + vec2f(sampleStep.x, 0.0))));
                edge = max(edge, differs(center,
                        maskAt(input.texCoord + vec2f(0.0, -sampleStep.y))));
                edge = max(edge, differs(center,
                        maskAt(input.texCoord + vec2f(0.0, sampleStep.y))));
                return vec4f(input.outlineColor.rgb,
                        input.outlineColor.a * edge);
            }
            """;
    private static final ShaderReflection STATIC_MASK_REFLECTION =
            maskReflection(STATIC_UNIFORM_LAYOUT, 128, false);
    private static final ShaderReflection SKINNED_MASK_REFLECTION =
            maskReflection(SKINNED_UNIFORM_LAYOUT,
                    144L + MAX_BONES * 64L, true);

    private final GraphicsContext graphics;
    private final DefaultRenderQueue3D queue = new DefaultRenderQueue3D();
    private final ShaderModule staticMaskShader;
    private final ShaderModule skinnedMaskShader;
    private final ShaderModule edgeShader;
    private final RenderPipeline edgePipeline;
    private final Buffer postVertexBuffer;
    private final ByteBuffer postUploadBuffer;
    private final FloatBuffer postUploadFloats;
    private final ShaderParameterBlock staticUniforms =
            ShaderParameterBlock.allocate(STATIC_UNIFORM_LAYOUT);
    private final ShaderParameterBlock skinnedUniforms =
            ShaderParameterBlock.allocate(SKINNED_UNIFORM_LAYOUT);
    private final ArrayList<MaskPipelineEntry> maskPipelines = new ArrayList<>();
    private final RenderPassDescriptor maskPassDescriptor =
            new RenderPassDescriptor()
                    .label("edge detection outline mask pass")
                    .colorLoadOp(LoadOp.clear(0.0f, 0.0f, 0.0f, 0.0f))
                    .colorStoreOp(StoreOp.store());
    private final RenderPassDescriptor edgePassDescriptor =
            new RenderPassDescriptor()
                    .label("edge detection outline composite pass")
                    .colorLoadOp(LoadOp.load())
                    .colorStoreOp(StoreOp.store());
    private final float[] modelMatrix = new float[Matrix4.VALUE_COUNT];
    private final float[] viewProjectionMatrix = new float[Matrix4.VALUE_COUNT];
    private final float[] boneMatrices = new float[MAX_BONES * Matrix4.VALUE_COUNT];
    private final float[] postVertices = new float[POST_VERTEX_COUNT * POST_FLOATS_PER_VERTEX];
    private Texture maskTexture;
    private RenderPass maskPass;
    private Camera camera;
    private int targetWidth;
    private int targetHeight;
    private float red = 1.0f;
    private float green = 0.82f;
    private float blue = 0.0f;
    private float alpha = 1.0f;
    private float outlineWidth = 2.0f;
    private boolean postVerticesDirty = true;
    private boolean drawing;
    private boolean disposed;

    /**
     * Creates an edge-detection outline renderer.
     *
     * @param graphics the graphics context
     */
    public EdgeDetectionOutlineRenderer3D(GraphicsContext graphics) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        this.graphics = graphics;
        ShaderModule createdStaticMaskShader = null;
        ShaderModule createdSkinnedMaskShader = null;
        ShaderModule createdEdgeShader = null;
        RenderPipeline createdEdgePipeline = null;
        Buffer createdPostVertexBuffer = null;
        try {
            createdStaticMaskShader = graphics.device().createShaderModule(
                    ShaderModuleDescriptor.wgsl(
                            "edge detection outline static mask",
                            STATIC_MASK_SOURCE));
            createdSkinnedMaskShader = graphics.device().createShaderModule(
                    ShaderModuleDescriptor.wgsl(
                            "edge detection outline skinned mask",
                            SKINNED_MASK_SOURCE));
            createdEdgeShader = graphics.device().createShaderModule(
                    ShaderModuleDescriptor.wgsl(
                            "edge detection outline composite", EDGE_SOURCE));
            createdEdgePipeline = graphics.device().createRenderPipeline(
                    RenderPipelineDescriptor.shader(createdEdgeShader,
                                    graphics.surfaceFormat())
                            .label("edge detection outline composite")
                            .colorTargets(ColorTargetState.alpha(
                                    graphics.surfaceFormat()))
                            .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                            .vertexLayout(POST_VERTEX_LAYOUT)
                            .sampledTextureCount(1)
                            .depthWriteEnabled(false));
            createdPostVertexBuffer = graphics.device().createBuffer(
                    BufferDescriptor.vertex(
                            "edge detection outline fullscreen vertices",
                            POST_BUFFER_SIZE));
        }
        catch (RuntimeException | Error failure) {
            disposeAfterFailedCreation(createdPostVertexBuffer, failure);
            disposeAfterFailedCreation(createdEdgePipeline, failure);
            disposeAfterFailedCreation(createdEdgeShader, failure);
            disposeAfterFailedCreation(createdSkinnedMaskShader, failure);
            disposeAfterFailedCreation(createdStaticMaskShader, failure);
            throw failure;
        }
        staticMaskShader = createdStaticMaskShader;
        skinnedMaskShader = createdSkinnedMaskShader;
        edgeShader = createdEdgeShader;
        edgePipeline = createdEdgePipeline;
        postVertexBuffer = createdPostVertexBuffer;
        postUploadBuffer = newUploadBuffer(graphics, POST_BUFFER_SIZE);
        postUploadFloats = postUploadBuffer.asFloatBuffer();
    }

    /**
     * Sets the composite outline color.
     *
     * @return this renderer for chaining
     */
    public EdgeDetectionOutlineRenderer3D outlineColor(
            float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        postVerticesDirty = true;
        return this;
    }

    /**
     * Sets the outline radius in framebuffer pixels.
     *
     * @param width pixel radius, zero to disable the stroke
     * @return this renderer for chaining
     */
    public EdgeDetectionOutlineRenderer3D outlineWidth(float width) {
        if (Float.isNaN(width) || width < 0.0f) {
            throw new FdxException(
                    "Edge-detection outline width cannot be negative");
        }
        outlineWidth = width;
        postVerticesDirty = true;
        return this;
    }

    /**
     * Begins collecting selected models and recording their silhouette mask.
     * The scene color must already have been rendered this frame.
     *
     * @param camera the scene camera
     */
    public void begin(Camera camera) {
        ensureNotDisposed();
        if (drawing) {
            throw new FdxException(
                    "EdgeDetectionOutlineRenderer3D is already drawing");
        }
        if (camera == null) {
            throw new FdxException("Camera cannot be null");
        }
        GraphicsFrame frame = graphics.currentFrame();
        if (frame.width() <= 0 || frame.height() <= 0) {
            throw new FdxException(
                    "Edge-detection outline requires a non-empty frame");
        }
        ensureMaskTarget(frame.width(), frame.height());
        this.camera = camera;
        camera.combined().copyValues(viewProjectionMatrix, 0);
        staticUniforms.setFloatMatrix(STATIC_VIEW_PROJECTION,
                viewProjectionMatrix, 0);
        skinnedUniforms.setFloatMatrix(SKINNED_VIEW_PROJECTION,
                viewProjectionMatrix, 0);
        queue.clear();
        maskPass = frame.commandEncoder().beginRenderPass(maskPassDescriptor
                .colorAttachment(maskTexture.view()));
        drawing = true;
    }

    /**
     * Adds a selected model instance to the mask.
     *
     * @param instance selected model instance
     */
    public void render(ModelInstance instance) {
        ensureDrawing();
        if (instance == null) {
            throw new FdxException("ModelInstance cannot be null");
        }
        instance.collectRenderables(queue);
    }

    /**
     * Adds selected model instances to the mask.
     *
     * @param instances selected model instances
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
     * Adds selected model instances to the mask.
     *
     * @param instances selected model instances
     */
    public void render(ObjectIterable<? extends ModelInstance> instances) {
        ensureDrawing();
        if (instances == null) {
            throw new FdxException("ModelInstance iterable cannot be null");
        }
        if (instances instanceof ArrayView<?>) {
            ArrayView<?> values = (ArrayView<?>)instances;
            for (int i = 0; i < values.size(); i++) {
                ModelInstance instance = (ModelInstance)values.get(i);
                if (instance != null) {
                    render(instance);
                }
            }
            return;
        }
        ObjectIterator<? extends ModelInstance> iterator = instances.iterator();
        while (iterator.hasNext()) {
            ModelInstance instance = iterator.next();
            if (instance != null) {
                render(instance);
            }
        }
    }

    /**
     * Adds one renderable to the selected mask.
     *
     * @param renderable selected renderable
     */
    public void render(Renderable3D renderable) {
        ensureDrawing();
        if (renderable == null) {
            throw new FdxException("Renderable3D cannot be null");
        }
        queue.add(renderable);
    }

    /**
     * Flushes queued selected geometry into the mask.
     */
    public void flush() {
        ensureDrawing();
        if (queue.size() == 0) {
            return;
        }
        queue.sort(camera);
        for (int i = 0; i < queue.size(); i++) {
            drawMask(queue.get(i));
        }
        queue.clear();
    }

    /**
     * Completes the mask and composites its detected edges over the frame.
     */
    public void end() {
        ensureDrawing();
        RenderPass activeMaskPass = maskPass;
        boolean hasContent = queue.size() > 0;
        try {
            flush();
        }
        finally {
            queue.clear();
            activeMaskPass.end();
            drawing = false;
            maskPass = null;
            camera = null;
        }
        if (hasContent && outlineWidth > 0.0f) {
            composite();
        }
    }

    private void drawMask(Renderable3D renderable) {
        MeshPart meshPart = renderable.meshPart();
        Mesh mesh = meshPart.mesh();
        boolean skinned = Mesh.PBR_SKINNED_LAYOUT.equals(mesh.vertexLayout());
        RenderPipeline pipeline = maskPipeline(mesh.vertexLayout(),
                meshPart.primitiveTopology(), skinned);
        maskPass.setPipeline(pipeline);
        maskPass.setVertexBuffer(mesh.vertexBuffer());
        renderable.worldTransform().copyValues(modelMatrix, 0);
        if (skinned) {
            skinnedUniforms.setFloatMatrix(SKINNED_MODEL, modelMatrix, 0);
            applySkinning(renderable);
            maskPass.setParameterBlock(0, 0, skinnedUniforms);
        }
        else {
            staticUniforms.setFloatMatrix(STATIC_MODEL, modelMatrix, 0);
            maskPass.setParameterBlock(0, 0, staticUniforms);
        }
        int indexCount = meshPart.indexCount() > 0
                ? meshPart.indexCount() : mesh.indexCount();
        if (indexCount > 0) {
            maskPass.setIndexBuffer(mesh.indexBuffer());
            maskPass.drawIndexed(indexCount, 1, meshPart.firstIndex(), 0, 0);
            return;
        }
        int vertexCount = meshPart.vertexCount() > 0
                ? meshPart.vertexCount() : mesh.vertexCount();
        maskPass.draw(vertexCount, 1, meshPart.firstVertex(), 0);
    }

    private void applySkinning(Renderable3D renderable) {
        SkinningPalette palette = renderable.skinningPalette();
        if (palette == null || palette.size() == 0) {
            skinnedUniforms.setFloat4(SKINNING_PARAMS,
                    0.0f, 0.0f, 0.0f, 0.0f);
            return;
        }
        if (palette.size() > MAX_BONES) {
            throw new FdxException("Selected model uses " + palette.size()
                    + " bones, but the edge outline supports " + MAX_BONES);
        }
        skinnedUniforms.setFloat4(SKINNING_PARAMS,
                palette.size(), 0.0f, 0.0f, 0.0f);
        palette.copyValues(boneMatrices);
        for (int i = 0; i < palette.size(); i++) {
            skinnedUniforms.setArrayElementFloatMatrix(BONE_MATRICES, i,
                    boneMatrices, i * Matrix4.VALUE_COUNT);
        }
    }

    private RenderPipeline maskPipeline(VertexLayout layout,
            PrimitiveTopology topology, boolean skinned) {
        validatePositionLayout(layout, skinned);
        PrimitiveTopology actualTopology = topology != null
                ? topology : PrimitiveTopology.TRIANGLE_LIST;
        for (int i = 0; i < maskPipelines.size(); i++) {
            MaskPipelineEntry entry = maskPipelines.get(i);
            if (entry.skinned == skinned && entry.topology == actualTopology
                    && entry.layout.equals(layout)) {
                return entry.pipeline;
            }
        }
        ShaderModule shader = skinned ? skinnedMaskShader : staticMaskShader;
        ShaderReflection reflection = skinned
                ? SKINNED_MASK_REFLECTION : STATIC_MASK_REFLECTION;
        RenderPipeline pipeline = graphics.device().createRenderPipeline(
                RenderPipelineDescriptor.shader(shader, TextureFormat.RGBA8_UNORM)
                        .label(skinned
                                ? "edge detection outline skinned mask"
                                : "edge detection outline static mask")
                        .shaderReflection(reflection)
                        .primitiveTopology(actualTopology)
                        .vertexLayout(layout)
                        .depthTestEnabled(false)
                        .depthWriteEnabled(false));
        maskPipelines.add(new MaskPipelineEntry(
                layout, actualTopology, skinned, pipeline));
        return pipeline;
    }

    private static void validatePositionLayout(VertexLayout layout,
            boolean skinned) {
        if (layout == null) {
            throw new FdxException(
                    "Edge-detection outline requires a vertex layout");
        }
        boolean hasPosition = false;
        for (int i = 0; i < layout.attributeCount(); i++) {
            VertexAttribute attribute = layout.attribute(i);
            if (attribute.location() == 0
                    && attribute.format() == VertexFormat.FLOAT32X3) {
                hasPosition = true;
                break;
            }
        }
        if (!hasPosition) {
            throw new FdxException(
                    "Edge-detection outline requires FLOAT32X3 position at location 0");
        }
        if (skinned && !Mesh.PBR_SKINNED_LAYOUT.equals(layout)) {
            throw new FdxException(
                    "Edge-detection outline only supports the standard PBR skinned layout");
        }
    }

    private void ensureMaskTarget(int width, int height) {
        if (maskTexture != null && targetWidth == width
                && targetHeight == height) {
            return;
        }
        if (maskTexture != null) {
            maskTexture.dispose();
        }
        maskTexture = graphics.device().createTexture(
                TextureDescriptor.rgba8RenderTarget(
                                "edge detection outline mask", width, height)
                        .filter(TextureFilter.NEAREST)
                        .wrap(TextureWrap.CLAMP_TO_EDGE));
        targetWidth = width;
        targetHeight = height;
        postVerticesDirty = true;
    }

    private void composite() {
        updatePostVertices();
        GraphicsFrame frame = graphics.currentFrame();
        RenderPass edgePass = frame.commandEncoder().beginRenderPass(
                edgePassDescriptor.colorAttachment(frame.colorAttachment()));
        edgePass.setPipeline(edgePipeline);
        edgePass.setTexture(0, maskTexture);
        edgePass.setVertexBuffer(postVertexBuffer);
        edgePass.draw(POST_VERTEX_COUNT, 1, 0, 0);
        edgePass.end();
    }

    private void updatePostVertices() {
        if (!postVerticesDirty) {
            return;
        }
        float texelX = 1.0f / targetWidth;
        float texelY = 1.0f / targetHeight;
        int index = 0;
        index = postVertex(index, -1.0f, -1.0f, 0.0f, 1.0f,
                texelX, texelY);
        index = postVertex(index, 1.0f, -1.0f, 1.0f, 1.0f,
                texelX, texelY);
        index = postVertex(index, 1.0f, 1.0f, 1.0f, 0.0f,
                texelX, texelY);
        index = postVertex(index, -1.0f, -1.0f, 0.0f, 1.0f,
                texelX, texelY);
        index = postVertex(index, 1.0f, 1.0f, 1.0f, 0.0f,
                texelX, texelY);
        postVertex(index, -1.0f, 1.0f, 0.0f, 0.0f,
                texelX, texelY);
        postUploadBuffer.clear();
        postUploadFloats.clear();
        postUploadFloats.put(postVertices);
        postUploadBuffer.limit(POST_BUFFER_SIZE);
        postUploadBuffer.position(0);
        graphics.device().writeBuffer(postVertexBuffer, postUploadBuffer);
        postUploadBuffer.clear();
        postVerticesDirty = false;
    }

    private int postVertex(int index, float x, float y, float u, float v,
            float texelX, float texelY) {
        postVertices[index++] = x;
        postVertices[index++] = y;
        postVertices[index++] = u;
        postVertices[index++] = v;
        postVertices[index++] = texelX;
        postVertices[index++] = texelY;
        postVertices[index++] = outlineWidth;
        postVertices[index++] = 0.0f;
        postVertices[index++] = red;
        postVertices[index++] = green;
        postVertices[index++] = blue;
        postVertices[index++] = alpha;
        return index;
    }

    private static ShaderParameter parameter(String name,
            ShaderValueType type, long offset, long size) {
        return ShaderParameter.of(name, type, offset, size, 16);
    }

    private static ShaderReflection maskReflection(
            ShaderParameterLayout layout, long uniformSize,
            boolean skinned) {
        ShaderStageVariable position = variable(
                "input.position", "position", 0,
                ShaderValueType.vector(ShaderScalarType.F32, 3));
        ShaderStageVariable[] inputs;
        if (skinned) {
            inputs = new ShaderStageVariable[] {
                    position,
                    variable("input.joints", "joints", 6,
                            ShaderValueType.vector(ShaderScalarType.F32, 4)),
                    variable("input.weights", "weights", 7,
                            ShaderValueType.vector(ShaderScalarType.F32, 4))
            };
        }
        else {
            inputs = new ShaderStageVariable[] { position };
        }
        ShaderBinding uniforms = ShaderBinding.builder(0, 0,
                        "uniforms", ShaderResourceKind.UNIFORM_BUFFER)
                .visibility(ShaderStageVisibility.VERTEX)
                .access(ShaderResourceAccess.READ)
                .buffer(uniformSize, uniformSize, 16, layout)
                .build();
        ShaderStageVariable color = variable(
                "<retval>", "", 0,
                ShaderValueType.vector(ShaderScalarType.F32, 4));
        return ShaderReflection.complete(ShaderProfile.PORTABLE_WEBGPU,
                new ShaderEntryPoint[] {
                        ShaderEntryPoint.builder("vertexMain",
                                        ShaderStage.VERTEX)
                                .builtins(ShaderBuiltinUsage.POSITION, -1)
                                .inputs(inputs)
                                .resources(ShaderResourceUse.of(
                                        0, 0, uniformSize))
                                .build(),
                        ShaderEntryPoint.builder("fragmentMain",
                                        ShaderStage.FRAGMENT)
                                .outputs(color)
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

    private static ByteBuffer newUploadBuffer(
            GraphicsContext graphics, int byteCount) {
        ByteBuffer buffer = "psp".equals(graphics.providerId().value())
                ? ByteBuffer.allocate(byteCount)
                : ByteBuffer.allocateDirect(byteCount);
        return buffer.order(ByteOrder.nativeOrder());
    }

    private void ensureDrawing() {
        ensureNotDisposed();
        if (!drawing || maskPass == null || camera == null) {
            throw new FdxException(
                    "EdgeDetectionOutlineRenderer3D.begin() must be called before rendering");
        }
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException(
                    "EdgeDetectionOutlineRenderer3D has been disposed");
        }
    }

    private static void disposeAfterFailedCreation(
            Disposable disposable, Throwable failure) {
        if (disposable == null) {
            return;
        }
        try {
            disposable.dispose();
        }
        catch (Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    /**
     * Releases mask, pipeline, shader, and upload resources.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        queue.clear();
        for (int i = 0; i < maskPipelines.size(); i++) {
            maskPipelines.get(i).pipeline.dispose();
        }
        maskPipelines.clear();
        if (maskTexture != null) {
            maskTexture.dispose();
            maskTexture = null;
        }
        postVertexBuffer.dispose();
        edgePipeline.dispose();
        edgeShader.dispose();
        skinnedMaskShader.dispose();
        staticMaskShader.dispose();
    }

    /**
     * Returns whether this renderer has been disposed.
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private static final class MaskPipelineEntry {
        private final VertexLayout layout;
        private final PrimitiveTopology topology;
        private final boolean skinned;
        private final RenderPipeline pipeline;

        MaskPipelineEntry(VertexLayout layout, PrimitiveTopology topology,
                boolean skinned, RenderPipeline pipeline) {
            this.layout = layout;
            this.topology = topology;
            this.skinned = skinned;
            this.pipeline = pipeline;
        }
    }
}
