package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.graphics.shader.*;
import io.github.libfdx.graphics.shader.reflection.*;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.math.ClipDepthRange;

/** Records the nearest opaque surface's world XZ, including canopy and wall faces. */
public final class FogSurfacePositions implements Disposable {
    private static final String SOURCE = """
            struct Input { @location(0) position: vec3f };
            struct Output { @builtin(position) position: vec4f, @location(0) worldXZ: vec2f };
            struct Uniforms { model: mat4x4<f32>, viewProjection: mat4x4<f32> };
            @group(0) @binding(0) var<uniform> uniforms: Uniforms;
            @vertex fn vertexMain(input: Input) -> Output {
                var output: Output;
                let world = uniforms.model * vec4f(input.position, 1.0);
                output.position = uniforms.viewProjection * world;
                output.worldXZ = world.xz;
                return output;
            }
            @fragment fn fragmentMain(input: Output) -> @location(0) vec4f {
                // Two 16-bit coordinates fit in the RGBA8 format supported by every provider.
                let uv = clamp(input.worldXZ / vec2f(WORLD_WIDTH, WORLD_DEPTH) + 0.5, vec2f(0.0), vec2f(1.0));
                let value = floor(uv * 65535.0 + 0.5);
                let high = floor(value / 256.0);
                let low = value - high * 256.0;
                return vec4f(high.x, low.x, high.y, low.y) / 255.0;
            }
            """.replace("WORLD_WIDTH", Float.toString(FogExploration.WIDTH))
               .replace("WORLD_DEPTH", Float.toString(FogExploration.DEPTH));
    private final OffscreenTarget target;
    private final PositionShader shader;
    private final ModelBatch batch;
    private final RenderPassDescriptor passDescriptor = new RenderPassDescriptor()
            .label("courtyard surface positions").colorLoadOp(LoadOp.clear(0,0,0,0));
    private boolean disposed;

    public FogSurfacePositions(GraphicsContext graphics) {
        target = new OffscreenTarget(graphics.device(),TextureFormat.RGBA8_UNORM,null,1,TextureFilter.NEAREST);
        shader = new PositionShader(graphics);
        batch = new ModelBatch(graphics,new ModelBatchConfig().shaderProvider(
                (ShaderProvider3D)(renderable,context)->shader)).frustumCulling(true);
    }

    public void render(GraphicsFrame frame, Camera camera, ModelInstance ground, ModelInstance[] instances) {
        if(target.resize(frame.width(),frame.height()))passDescriptor.colorAttachment(target.color().view());
        RenderPass pass=frame.commandEncoder().beginRenderPass(
                passDescriptor.depthClear(ClipDepthRange.getDefault().depthClearValue()));
        batch.begin(pass,camera);
        if(ground!=null)batch.render(ground);
        for(ModelInstance instance:instances)if(instance!=null)batch.render(instance);
        batch.end(); pass.end();
    }

    public Texture texture() { return target.color(); }
    @Override
    public boolean isDisposed() { return disposed; }
    @Override
    public void dispose() {
        if(disposed)return;
        disposed=true; batch.dispose(); shader.dispose(); target.dispose();
    }

    private static final class PositionShader implements Shader3D {
        private final ShaderParameterBlock parameters;
        private final ShaderParameterHandle model, projection;
        private final float[] matrix = new float[16];
        private final ShaderModule module;
        private final RenderPipeline pipeline;
        private RenderPass pass;

        PositionShader(GraphicsContext graphics) {
            module=graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl("fog surface positions",SOURCE));
            ShaderParameterLayout layout=module.reflection().requireBinding(0,0).bufferLayout();
            parameters=ShaderParameterBlock.allocate(layout);
            model=layout.requireHandle("model"); projection=layout.requireHandle("viewProjection");
            pipeline=graphics.device().createRenderPipeline(RenderPipelineDescriptor.shader(module,TextureFormat.RGBA8_UNORM)
                    .colorTargets(ColorTargetState.opaque(TextureFormat.RGBA8_UNORM))
                    .vertexLayout(Mesh.PBR_LAYOUT).depthTestEnabled(true).depthWriteEnabled(true));
        }
        @Override
        public boolean canRender(Renderable3D renderable) { return true; }
        @Override
        public void begin(RenderContext3D context) {
            pass=context.pass();
            context.camera().combined().copyValues(matrix,0);
            parameters.setFloatMatrix(projection,matrix,0);
        }
        @Override
        public void render(Renderable3D renderable) {
            MeshPart part=renderable.meshPart(); Mesh mesh=part.mesh();
            renderable.worldTransform().copyValues(matrix,0);
            parameters.setFloatMatrix(model,matrix,0);
            pass.setPipeline(pipeline); pass.setVertexBuffer(mesh.vertexBuffer());
            pass.setParameterBlock(0,0,parameters);
            int indices=part.indexCount()>0?part.indexCount():mesh.indexCount();
            if(indices>0) {
                pass.setIndexBuffer(mesh.indexBuffer());
                pass.drawIndexed(indices,1,part.firstIndex(),0,0);
            } else pass.draw(part.vertexCount()>0?part.vertexCount():mesh.vertexCount(),1,part.firstVertex(),0);
        }
        @Override
        public void end() { pass=null; }
        @Override
        public boolean isDisposed() { return module.isDisposed(); }
        @Override
        public void dispose() { pipeline.dispose(); module.dispose(); }
    }
}
