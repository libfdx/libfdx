package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.CompareFunction;
import io.github.libfdx.graphics.CullMode;
import io.github.libfdx.graphics.DepthStencilState;
import io.github.libfdx.graphics.FrontFace;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.MultisampleState;
import io.github.libfdx.graphics.PrimitiveState;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassColorAttachment;
import io.github.libfdx.graphics.RenderPassDepthStencilAttachment;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;

/**
 * Exercises exact MRT, resolve, depth, sample-count, and pipeline-state
 * compatibility through the common render API.
 */
public final class RenderTargetCompatibilityTest extends GraphicsParityTest {
    private static final int TARGET_WIDTH = 96;
    private static final int TARGET_HEIGHT = 64;
    private static final String SOURCE = """
            struct VertexOutput {
                @builtin(position) position : vec4f,
            };

            @vertex
            fn vertexMain(@builtin(vertex_index) vertexIndex : u32) -> VertexOutput {
                var positions = array<vec2f, 3>(
                    vec2f(-0.8, -0.7),
                    vec2f(0.8, -0.7),
                    vec2f(0.0, 0.8)
                );
                var output : VertexOutput;
                output.position = vec4f(positions[vertexIndex], 0.25, 1.0);
                return output;
            }

            struct FragmentOutput {
                @location(0) color : vec4f,
                @location(1) value : f32,
            };

            @fragment
            fn fragmentMain() -> FragmentOutput {
                var output : FragmentOutput;
                output.color = vec4f(0.12, 0.72, 0.34, 1.0);
                output.value = 0.625;
                return output;
            }
            """;

    private ShaderModule shaderModule;
    private RenderPipeline pipeline;
    private Texture color0;
    private Texture color1;
    private Texture resolve0;
    private Texture depth;
    private RenderPassDescriptor offscreenPass;

    public RenderTargetCompatibilityTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "RenderTargetCompatibilityTest");
        graphics.device().capabilities().require(GraphicsFeature.MULTIPLE_COLOR_ATTACHMENTS);
        graphics.device().capabilities().require(GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS);
        graphics.device().capabilities().require(GraphicsFeature.MULTISAMPLE);
        graphics.device().capabilities().require(GraphicsFeature.RESOLVE_ATTACHMENTS);
        graphics.device().capabilities().require(GraphicsFeature.COMPLETE_RENDER_PIPELINE_STATE);

        shaderModule = graphics.device().createShaderModule(
                ShaderModuleDescriptor.wgsl("MRT compatibility shader", SOURCE));
        RenderTargetLayout layout = RenderTargetLayout.of(
                new TextureFormat[] { TextureFormat.RGBA8_UNORM, TextureFormat.R32_FLOAT },
                TextureFormat.DEPTH32_FLOAT, 4);
        pipeline = graphics.device().createRenderPipeline(
                RenderPipelineDescriptor.shader(shaderModule, TextureFormat.RGBA8_UNORM)
                        .label("MRT compatibility pipeline")
                        .renderTargetLayout(layout)
                        .colorTargets(
                                ColorTargetState.opaque(TextureFormat.RGBA8_UNORM),
                                ColorTargetState.opaque(TextureFormat.R32_FLOAT))
                        .primitiveState(PrimitiveState.of(PrimitiveTopology.TRIANGLE_LIST,
                                FrontFace.COUNTER_CLOCKWISE, CullMode.BACK))
                        .depthStencilState(DepthStencilState.builder(TextureFormat.DEPTH32_FLOAT)
                                .depthWriteEnabled(true)
                                .depthCompare(CompareFunction.LESS)
                                .build())
                        .multisampleState(MultisampleState.of(4, -1, false)));

        color0 = texture("MRT color 0", TextureFormat.RGBA8_UNORM, 4);
        color1 = texture("MRT color 1", TextureFormat.R32_FLOAT, 4);
        resolve0 = texture("MRT resolve 0", TextureFormat.RGBA8_UNORM, 1);
        depth = texture("MRT depth", TextureFormat.DEPTH32_FLOAT, 4);
        offscreenPass = new RenderPassDescriptor()
                .label("MRT compatibility pass")
                .colorAttachments(
                        RenderPassColorAttachment.resolve(color0.view(), resolve0.view(),
                                LoadOp.clear(0, 0, 0, 1), StoreOp.store()),
                        RenderPassColorAttachment.of(color1.view(),
                                LoadOp.clear(0, 0, 0, 0), StoreOp.discard()))
                .depthStencilAttachment(RenderPassDepthStencilAttachment.of(
                        depth.view(), LoadOp.clear(1, 0, 0, 0), StoreOp.store(),
                        LoadOp.load(), StoreOp.store()));
        markCreated();
    }

    @Override
    public void render() {
        GraphicsFrame frame = graphics.currentFrame();
        RenderPass offscreen = frame.commandEncoder().beginRenderPass(offscreenPass);
        if (offscreen.compatibility().width() != TARGET_WIDTH
                || offscreen.compatibility().height() != TARGET_HEIGHT
                || !offscreen.compatibility().isCompatible(pipeline.targetLayout())) {
            throw new FdxException("Active offscreen pass did not expose exact pipeline compatibility");
        }
        offscreen.setPipeline(pipeline);
        offscreen.draw(3, 1, 0, 0);
        offscreen.end();

        RenderPass status = frame.commandEncoder().beginRenderPass(
                RenderPassDescriptor.color(frame.colorAttachment(),
                        LoadOp.clear(0.04f, 0.42f, 0.16f, 1.0f), StoreOp.store())
                        .label("MRT status"));
        status.end();
        finishFrame();
    }

    @Override
    public void dispose() {
        dispose(depth);
        dispose(resolve0);
        dispose(color1);
        dispose(color0);
        dispose(pipeline);
        dispose(shaderModule);
        verifyDisposed();
    }

    private Texture texture(String label, TextureFormat format, int sampleCount) {
        return graphics.device().createTexture(new TextureDescriptor()
                .label(label)
                .size(TARGET_WIDTH, TARGET_HEIGHT)
                .format(format)
                .usage(TextureUsage.RENDER_ATTACHMENT)
                .sampleCount(sampleCount));
    }
}
