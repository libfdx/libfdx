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
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;

/**
 * Runs the raw mesh rendering parity test.
 *
 * @author xpenatan
 */
public final class MeshBasicTest extends GraphicsParityTest {
    private static final int FLOATS_PER_VERTEX = 6;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final int TRIANGLE_VERTEX_COUNT = 3;
    private static final int QUAD_VERTEX_COUNT = 4;
    private static final int QUAD_INDEX_COUNT = 6;
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X4, 8));
    private static final float[] TRIANGLE_VERTICES = {
            -0.72f, 0.62f, 0.96f, 0.18f, 0.26f, 1.0f,
            -0.92f, -0.58f, 0.22f, 0.78f, 0.48f, 1.0f,
            -0.34f, -0.46f, 0.18f, 0.44f, 1.0f, 1.0f
    };
    private static final float[] QUAD_VERTICES = {
            0.18f, 0.58f, 1.0f, 0.86f, 0.22f, 1.0f,
            0.86f, 0.58f, 0.22f, 0.78f, 1.0f, 1.0f,
            0.86f, -0.52f, 0.30f, 0.96f, 0.42f, 1.0f,
            0.18f, -0.52f, 1.0f, 0.28f, 0.64f, 1.0f
    };
    private static final short[] QUAD_INDICES = { 0, 1, 2, 0, 2, 3 };
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
            .label("mesh basic pass")
            .colorLoadOp(LoadOp.clear(0.025f, 0.03f, 0.04f, 1.0f))
            .colorStoreOp(StoreOp.store());
    private ShaderModule shaderModule;
    private RenderPipeline pipeline;
    private Buffer triangleVertexBuffer;
    private Buffer quadVertexBuffer;
    private Buffer quadIndexBuffer;

    /**
     * Creates a raw mesh rendering parity test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public MeshBasicTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "MeshBasicTest");
        triangleVertexBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                "mesh basic triangle vertices", TRIANGLE_VERTEX_COUNT * BYTES_PER_VERTEX));
        graphics.device().writeBuffer(triangleVertexBuffer, floats(TRIANGLE_VERTICES));
        quadVertexBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                "mesh basic quad vertices", QUAD_VERTEX_COUNT * BYTES_PER_VERTEX));
        graphics.device().writeBuffer(quadVertexBuffer, floats(QUAD_VERTICES));
        quadIndexBuffer = graphics.device().createBuffer(BufferDescriptor.staticIndex(
                "mesh basic quad indices", QUAD_INDEX_COUNT * 2));
        graphics.device().writeBuffer(quadIndexBuffer, shorts(QUAD_INDICES));
        shaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("mesh basic shader", SHADER_SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shaderModule, graphics.surfaceFormat())
                .label("mesh basic pipeline")
                .vertexLayout(VERTEX_LAYOUT)
                .depthWriteEnabled(false));
        markCreated();
    }

    @Override
    public void render() {
        GraphicsFrame frame = graphics.currentFrame();
        passDescriptor.colorAttachment(frame.colorAttachment());
        RenderPass pass = frame.commandEncoder().beginRenderPass(passDescriptor);
        pass.setPipeline(pipeline);
        pass.setVertexBuffer(triangleVertexBuffer);
        pass.draw(TRIANGLE_VERTEX_COUNT, 1, 0, 0);
        pass.setVertexBuffer(quadVertexBuffer);
        pass.setIndexBuffer(quadIndexBuffer);
        pass.drawIndexed(QUAD_INDEX_COUNT, 1, 0, 0, 0);
        pass.end();
        finishFrame();
    }

    @Override
    public void dispose() {
        dispose(pipeline);
        dispose(shaderModule);
        dispose(triangleVertexBuffer);
        dispose(quadVertexBuffer);
        dispose(quadIndexBuffer);
        verifyDisposed();
    }
}
