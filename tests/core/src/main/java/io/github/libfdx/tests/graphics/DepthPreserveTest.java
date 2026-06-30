package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
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

/**
 * Runs the depth-preserving multi-pass parity test.
 *
 * @author xpenatan
 */
public final class DepthPreserveTest extends GraphicsParityTest {
    private static final int FLOATS_PER_VERTEX = 7;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final int VERTEX_COUNT = 6;
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X4, 12));
    private static final float[] CLOSE_OCCLUDER_VERTICES = {
            -0.30f, -0.62f, 0.25f, 0.92f, 0.18f, 0.24f, 1.0f,
            0.30f, -0.62f, 0.25f, 0.92f, 0.18f, 0.24f, 1.0f,
            0.30f, 0.62f, 0.25f, 0.92f, 0.18f, 0.24f, 1.0f,
            -0.30f, -0.62f, 0.25f, 0.92f, 0.18f, 0.24f, 1.0f,
            0.30f, 0.62f, 0.25f, 0.92f, 0.18f, 0.24f, 1.0f,
            -0.30f, 0.62f, 0.25f, 0.92f, 0.18f, 0.24f, 1.0f
    };
    private static final float[] FAR_BACKGROUND_VERTICES = {
            -0.86f, -0.78f, 0.75f, 0.12f, 0.72f, 0.96f, 1.0f,
            0.86f, -0.78f, 0.75f, 0.12f, 0.72f, 0.96f, 1.0f,
            0.86f, 0.78f, 0.75f, 0.90f, 0.82f, 0.20f, 1.0f,
            -0.86f, -0.78f, 0.75f, 0.12f, 0.72f, 0.96f, 1.0f,
            0.86f, 0.78f, 0.75f, 0.90f, 0.82f, 0.20f, 1.0f,
            -0.86f, 0.78f, 0.75f, 0.26f, 0.96f, 0.42f, 1.0f
    };
    private static final String SHADER_SOURCE = """
            struct VertexInput {
                @location(0) position : vec3f,
                @location(1) color : vec4f,
            };

            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) color : vec4f,
            };

            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4f(input.position, 1.0);
                output.color = input.color;
                return output;
            }

            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                return input.color;
            }
            """;

    private final RenderPassDescriptor depthClearPass = new RenderPassDescriptor()
            .label("depth preserve depth clear pass")
            .colorLoadOp(LoadOp.clear(0.30f, 0.07f, 0.09f, 1.0f))
            .colorStoreOp(StoreOp.store())
            .depthClear(1.0f);
    private final RenderPassDescriptor preserveDepthPass = new RenderPassDescriptor()
            .label("depth preserve color clear pass")
            .colorLoadOp(LoadOp.clear(0.025f, 0.03f, 0.045f, 1.0f))
            .colorStoreOp(StoreOp.store())
            .depthEnabled(true);
    private ShaderModule shaderModule;
    private RenderPipeline pipeline;
    private Buffer closeOccluderBuffer;
    private Buffer farBackgroundBuffer;

    /**
     * Creates a depth-preserving multi-pass parity test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public DepthPreserveTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "DepthPreserveTest");
        closeOccluderBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                "depth preserve close occluder", VERTEX_COUNT * BYTES_PER_VERTEX));
        graphics.device().writeBuffer(closeOccluderBuffer, floats(CLOSE_OCCLUDER_VERTICES));
        farBackgroundBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                "depth preserve far background", VERTEX_COUNT * BYTES_PER_VERTEX));
        graphics.device().writeBuffer(farBackgroundBuffer, floats(FAR_BACKGROUND_VERTICES));
        shaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("depth preserve shader", SHADER_SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shaderModule, graphics.surfaceFormat())
                .label("depth preserve pipeline")
                .vertexLayout(VERTEX_LAYOUT)
                .depthTestEnabled(true)
                .depthWriteEnabled(true));
        markCreated();
    }

    @Override
    public void render() {
        GraphicsFrame frame = graphics.currentFrame();
        depthClearPass.colorAttachment(frame.colorAttachment());
        RenderPass pass = frame.commandEncoder().beginRenderPass(depthClearPass);
        pass.setPipeline(pipeline);
        pass.setVertexBuffer(closeOccluderBuffer);
        pass.draw(VERTEX_COUNT, 1, 0, 0);
        pass.end();

        preserveDepthPass.colorAttachment(frame.colorAttachment());
        pass = frame.commandEncoder().beginRenderPass(preserveDepthPass);
        pass.setPipeline(pipeline);
        pass.setVertexBuffer(farBackgroundBuffer);
        pass.draw(VERTEX_COUNT, 1, 0, 0);
        pass.end();
        finishFrame();
    }

    @Override
    public void dispose() {
        dispose(pipeline);
        dispose(shaderModule);
        dispose(closeOccluderBuffer);
        dispose(farBackgroundBuffer);
        verifyDisposed();
    }
}
