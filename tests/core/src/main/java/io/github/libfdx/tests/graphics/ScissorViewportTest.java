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
 * Runs the scissor and viewport parity test.
 *
 * @author xpenatan
 */
public final class ScissorViewportTest extends GraphicsParityTest {
    private static final int FLOATS_PER_VERTEX = 6;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final int VERTICES_PER_QUAD = 6;
    private static final int VERTEX_COUNT = VERTICES_PER_QUAD * 3;
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X4, 8));
    private static final float[] VERTICES = {
            -1.0f, -1.0f, 0.18f, 0.08f, 0.12f, 1.0f,
            1.0f, -1.0f, 0.40f, 0.14f, 0.18f, 1.0f,
            1.0f, 1.0f, 0.24f, 0.26f, 0.42f, 1.0f,
            -1.0f, -1.0f, 0.18f, 0.08f, 0.12f, 1.0f,
            1.0f, 1.0f, 0.24f, 0.26f, 0.42f, 1.0f,
            -1.0f, 1.0f, 0.32f, 0.12f, 0.30f, 1.0f,

            -1.0f, -1.0f, 0.12f, 0.52f, 0.98f, 1.0f,
            1.0f, -1.0f, 0.12f, 0.52f, 0.98f, 1.0f,
            1.0f, 1.0f, 0.12f, 0.52f, 0.98f, 1.0f,
            -1.0f, -1.0f, 0.12f, 0.52f, 0.98f, 1.0f,
            1.0f, 1.0f, 0.12f, 0.52f, 0.98f, 1.0f,
            -1.0f, 1.0f, 0.12f, 0.52f, 0.98f, 1.0f,

            -1.0f, -1.0f, 0.28f, 0.96f, 0.30f, 1.0f,
            1.0f, -1.0f, 0.28f, 0.96f, 0.30f, 1.0f,
            1.0f, 1.0f, 0.28f, 0.96f, 0.30f, 1.0f,
            -1.0f, -1.0f, 0.28f, 0.96f, 0.30f, 1.0f,
            1.0f, 1.0f, 0.28f, 0.96f, 0.30f, 1.0f,
            -1.0f, 1.0f, 0.28f, 0.96f, 0.30f, 1.0f
    };
    private static final String SHADER_SOURCE = """
            struct VertexInput {
                @location(0) position : vec2f,
                @location(1) color : vec4f,
            };

            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) color : vec4f,
            };

            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4f(input.position, 0.0, 1.0);
                output.color = input.color;
                return output;
            }

            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                return input.color;
            }
            """;

    private final RenderPassDescriptor passDescriptor = new RenderPassDescriptor()
            .label("scissor viewport pass")
            .colorLoadOp(LoadOp.clear(0.0f, 0.0f, 0.0f, 1.0f))
            .colorStoreOp(StoreOp.store());
    private ShaderModule shaderModule;
    private RenderPipeline pipeline;
    private Buffer vertexBuffer;

    /**
     * Creates a scissor and viewport parity test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public ScissorViewportTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "ScissorViewportTest");
        vertexBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                "scissor viewport vertices", VERTEX_COUNT * BYTES_PER_VERTEX));
        graphics.device().writeBuffer(vertexBuffer, floats(VERTICES));
        shaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("scissor viewport shader", SHADER_SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shaderModule, graphics.surfaceFormat())
                .label("scissor viewport pipeline")
                .vertexLayout(VERTEX_LAYOUT)
                .depthWriteEnabled(false));
        markCreated();
    }

    @Override
    public void render() {
        GraphicsFrame frame = graphics.currentFrame();
        int width = frame.width() > 0 ? frame.width() : framebufferWidth();
        int height = frame.height() > 0 ? frame.height() : framebufferHeight();
        int viewportX = width / 8;
        int viewportY = height / 8;
        int viewportWidth = width * 3 / 4;
        int viewportHeight = height * 3 / 4;
        int scissorX = width / 3;
        int scissorY = height / 3;
        int scissorWidth = width / 3;
        int scissorHeight = height / 3;

        passDescriptor.colorAttachment(frame.colorAttachment());
        RenderPass pass = frame.commandEncoder().beginRenderPass(passDescriptor);
        pass.setPipeline(pipeline);
        pass.setVertexBuffer(vertexBuffer);
        pass.draw(VERTICES_PER_QUAD, 1, 0, 0);
        pass.setViewport(viewportX, viewportY, viewportWidth, viewportHeight);
        pass.draw(VERTICES_PER_QUAD, 1, VERTICES_PER_QUAD, 0);
        pass.setScissor(scissorX, scissorY, scissorWidth, scissorHeight);
        pass.draw(VERTICES_PER_QUAD, 1, VERTICES_PER_QUAD * 2, 0);
        pass.end();
        finishFrame();
    }

    @Override
    public void dispose() {
        dispose(pipeline);
        dispose(shaderModule);
        dispose(vertexBuffer);
        verifyDisposed();
    }
}
