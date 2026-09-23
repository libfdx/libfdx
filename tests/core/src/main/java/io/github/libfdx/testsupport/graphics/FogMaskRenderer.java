package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Surface fog, with depth-aware alpha composition for transient foreground layers. */
public final class FogMaskRenderer implements Disposable {
    private static final String SHADER = """
            struct Input { @location(0) position: vec2f, @location(1) uv: vec2f };
            struct Output { @builtin(position) position: vec4f, @location(0) uv: vec2f };
            struct Settings { cameraAlpha: vec4f };
            @group(1) @binding(0) var<uniform> settings: Settings;
            @group(0) @binding(0) var scene: texture_2d<f32>;
            @group(0) @binding(1) var sceneSampler: sampler;
            @group(0) @binding(2) var positions: texture_2d<f32>;
            @group(0) @binding(3) var positionSampler: sampler;
            @group(0) @binding(4) var mask: texture_2d<f32>;
            @group(0) @binding(5) var maskSampler: sampler;
            @group(0) @binding(6) var backgroundPositions: texture_2d<f32>;
            @group(0) @binding(7) var backgroundSampler: sampler;
            fn decode(encoded: vec4f) -> vec2f {
                return vec2f(encoded.r * 256.0 + encoded.g,
                        encoded.b * 256.0 + encoded.a) / 65535.0;
            }
            @vertex fn vertexMain(input: Input) -> Output {
                var output: Output;
                output.position = vec4f(input.position, 0.0, 1.0);
                output.uv = input.uv;
                return output;
            }
            @fragment fn fragmentMain(input: Output) -> @location(0) vec4f {
                let encodedPosition = textureSample(positions, positionSampler, input.uv) * 255.0;
                let worldUv = decode(encodedPosition);
                if (settings.cameraAlpha.w >= 0.0) {
                    if (dot(encodedPosition, vec4f(1.0)) == 0.0) { discard; }
                    let background = textureSample(backgroundPositions, backgroundSampler, input.uv) * 255.0;
                    if (dot(background, vec4f(1.0)) > 0.0) {
                        // Points on one view ray have monotonically increasing XZ distance.
                        let here = (worldUv - 0.5) * vec2f(48.0, 40.0) - settings.cameraAlpha.xz;
                        let behind = (decode(background) - 0.5) * vec2f(48.0, 40.0) - settings.cameraAlpha.xz;
                        if (dot(here, here) > dot(behind, behind) + 0.001) { discard; }
                    }
                }
                let dimensions = vec2f(MASK_COLUMNS, MASK_ROWS);
                let maskUv = (0.5 + worldUv * (dimensions - 1.0)) / dimensions;
                let darkness = textureSample(mask, maskSampler, maskUv).r;
                let color = textureSample(scene, sceneSampler, input.uv).rgb;
                let fogColor = vec3f(0.025, 0.035, 0.05);
                let surface = mix(color, fogColor, darkness);
                // Protect each visible surface, not an entire model when one canopy tip is revealed.
                // Memory (50%) and cleared ground stay opaque, including when cameraAlpha is zero.
                let hidden = smoothstep(0.5, 1.0, darkness);
                let alpha = 1.0 - (1.0 - settings.cameraAlpha.w) * hidden;
                return vec4f(surface, select(alpha, 1.0, settings.cameraAlpha.w < 0.0));
            }
            """.replace("MASK_COLUMNS", Float.toString(FogExploration.COLUMNS))
               .replace("MASK_ROWS", Float.toString(FogExploration.ROWS));
    private final GraphicsContext graphics;
    private final ByteBuffer pixels = ByteBuffer.allocateDirect(FogExploration.COLUMNS * FogExploration.ROWS * 4);
    private final RenderPassDescriptor descriptor = new RenderPassDescriptor().label("exploration fog")
            .colorLoadOp(LoadOp.load()).colorStoreOp(StoreOp.store());
    private Buffer vertices;
    private Texture mask;
    private ShaderModule shader;
    private RenderPipeline pipeline, alphaPipeline;
    private ShaderParameterBlock settings;
    private ShaderParameterHandle cameraAlpha;
    private int uploadedRevision = -1;
    private boolean disposed;

    public FogMaskRenderer(GraphicsContext graphics) {
        this.graphics = graphics;
        try {
            ByteBuffer upload=ByteBuffer.allocateDirect(6*16).order(ByteOrder.nativeOrder());
            TextureOrigin origin=graphics.device().capabilities().renderedTextureOrigin();
            float[] xy={-1,-1,1,-1,1,1,-1,-1,1,1,-1,1};
            for(int i=0;i<xy.length;i+=2) {
                float x=xy[i],y=xy[i+1];
                upload.putFloat(x).putFloat(y).putFloat((x+1)*.5f).putFloat(origin.v((1-y)*.5f));
            }
            upload.flip();
            vertices = graphics.device().createBuffer(BufferDescriptor.staticVertex("fog composition", upload.capacity()));
            graphics.device().writeBuffer(vertices,upload);
            mask = graphics.device().createTexture(TextureDescriptor.rgba8("explored terrain",
                    FogExploration.COLUMNS, FogExploration.ROWS).filter(TextureFilter.LINEAR));
            shader = graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl("exploration fog", SHADER));
            ShaderParameterLayout settingsLayout = shader.reflection().requireBinding(1,0).bufferLayout();
            settings = ShaderParameterBlock.allocate(settingsLayout);
            cameraAlpha=settingsLayout.requireHandle("cameraAlpha");
            pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor.shader(shader, graphics.surfaceFormat())
                    .colorTargets(ColorTargetState.opaque(graphics.surfaceFormat()))
                    .vertexLayout(VertexLayout.of(16, VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
                            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8)))
                    .sampledTextureCount(4).depthWriteEnabled(false));
            alphaPipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor.shader(shader, graphics.surfaceFormat())
                    .colorTargets(ColorTargetState.alpha(graphics.surfaceFormat()))
                    .vertexLayout(VertexLayout.of(16, VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
                            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8)))
                    .sampledTextureCount(4).depthWriteEnabled(false));
        } catch (RuntimeException | Error failure) { dispose(); throw failure; }
    }

    public void render(FogExploration exploration, Texture scene, Texture positions) {
        if (uploadedRevision != exploration.revision) {
            pixels.clear();
            for (float opacity:exploration.opacity) {
                pixels.put((byte)Math.round(opacity*255)).put((byte)0).put((byte)0).put((byte)255);
            }
            pixels.flip(); graphics.device().writeTexture(mask, pixels);
            uploadedRevision = exploration.revision;
        }
        compose(scene,positions,positions,0,0,-1);
    }

    public void renderLayer(Texture scene,Texture positions,Texture background,float cameraX,float cameraZ,float alpha) {
        compose(scene,positions,background,cameraX,cameraZ,alpha);
    }

    private void compose(Texture scene,Texture positions,Texture background,float cameraX,float cameraZ,float alpha) {
        settings.setFloat4(cameraAlpha,cameraX,0,cameraZ,alpha);
        GraphicsFrame frame = graphics.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(descriptor.colorAttachment(frame.colorAttachment()));
        pass.setPipeline(alpha<0?pipeline:alphaPipeline); pass.setVertexBuffer(vertices);
        pass.setParameterBlock(1,0,settings);
        pass.setTexture(0, scene); pass.setTexture(1, positions); pass.setTexture(2, mask); pass.setTexture(3,background);
        pass.draw(6, 1, 0, 0); pass.end();
    }

    @Override
    public void dispose() {
        if (disposed) return; disposed = true;
        if (pipeline != null) pipeline.dispose();
        if (alphaPipeline != null) alphaPipeline.dispose();
        if (shader != null) shader.dispose();
        if (mask != null) mask.dispose();
        if (vertices != null) vertices.dispose();
    }
    @Override
    public boolean isDisposed() { return disposed; }
}
