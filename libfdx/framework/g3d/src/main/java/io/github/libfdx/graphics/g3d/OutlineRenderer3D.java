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
import io.github.libfdx.graphics.ShaderAttribute;
import io.github.libfdx.graphics.ShaderBinding;
import io.github.libfdx.graphics.ShaderBindingType;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.ShaderReflection;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.math.Matrix4;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a WGSL-authored shell outline for 3D PBR meshes.
 *
 * @author xpenatan
 */
public final class OutlineRenderer3D implements Disposable {
    private static final int PRIMITIVE_TOPOLOGY_COUNT = PrimitiveTopology.values().length;
    private static final String SOURCE = """
            struct VertexInput {
                @location(0) position : vec3f,
                @location(1) normal : vec3f,
                @location(2) uv : vec2f,
                @location(3) color : vec4f,
                @location(4) pbr : vec3f,
                @location(5) emissive : vec3f,
            };
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
    private static final ShaderReflection REFLECTION = ShaderReflection.of(new ShaderBinding[] {
            ShaderBinding.of(0, 0, "uniforms", ShaderBindingType.UNIFORM_BUFFER)
    }, new ShaderAttribute[] {
            ShaderAttribute.of(0, "position", VertexFormat.FLOAT32X3),
            ShaderAttribute.of(1, "normal", VertexFormat.FLOAT32X3),
            ShaderAttribute.of(2, "uv", VertexFormat.FLOAT32X2),
            ShaderAttribute.of(3, "color", VertexFormat.FLOAT32X4),
            ShaderAttribute.of(4, "pbr", VertexFormat.FLOAT32X3),
            ShaderAttribute.of(5, "emissive", VertexFormat.FLOAT32X3)
    });

    private final GraphicsContext graphics;
    private final DefaultRenderQueue3D queue = new DefaultRenderQueue3D();
    private final ShaderModule shader;
    private final Map<VertexLayout, RenderPipeline[]> pipelines =
            new IdentityHashMap<VertexLayout, RenderPipeline[]>();
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
        shader = graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl("outline renderer 3d", SOURCE));
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
        flush();
        drawing = false;
        camera = null;
        if (ownsPass) {
            pass.end();
        }
        pass = null;
        ownsPass = false;
    }

    private void draw(Renderable3D renderable) {
        MeshPart meshPart = renderable.meshPart();
        Mesh mesh = meshPart.mesh();
        if (mesh.vertexLayout() != Mesh.PBR_LAYOUT) {
            throw new FdxException("OutlineRenderer3D requires Mesh.PBR_LAYOUT meshes with normals");
        }
        pass.setPipeline(pipeline(mesh.vertexLayout(), meshPart.primitiveTopology()));
        pass.setVertexBuffer(mesh.vertexBuffer());
        renderable.worldTransform().copyValues(modelMatrix, 0);
        camera.combined().copyValues(viewProjectionMatrix, 0);
        pass.setUniformMatrix4("u_model", modelMatrix);
        pass.setUniformMatrix4("u_viewProjection", viewProjectionMatrix);
        pass.setUniform4f("u_ambientColor", red, green, blue, alpha);
        pass.setUniform4f("u_fogParams", outlineWidth, 0.0f, 0.0f, 0.0f);
        int indexCount = meshPart.indexCount() > 0 ? meshPart.indexCount() : mesh.indexCount();
        if (indexCount > 0) {
            pass.setIndexBuffer(mesh.indexBuffer());
            pass.drawIndexed(indexCount, 1, meshPart.firstIndex(), 0, 0);
            return;
        }
        int vertexCount = meshPart.vertexCount() > 0 ? meshPart.vertexCount() : mesh.vertexCount();
        pass.draw(vertexCount, 1, meshPart.firstVertex(), 0);
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
                    .shader(shader, graphics.surfaceFormat())
                    .label("outline renderer 3d")
                    .shaderReflection(REFLECTION)
                    .primitiveTopology(actualTopology)
                    .depthTestEnabled(false)
                    .depthWriteEnabled(false)
                    .vertexLayout(vertexLayout));
            variants[slot] = pipeline;
        }
        return pipeline;
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
        for (RenderPipeline[] variants : pipelines.values()) {
            for (int i = 0; i < variants.length; i++) {
                if (variants[i] != null) {
                    variants[i].dispose();
                }
            }
        }
        pipelines.clear();
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
