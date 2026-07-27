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
 * Runs the instance-step vertex layout parity test.
 *
 * @author xpenatan
 */
public final class InstancingBasicTest extends GraphicsParityTest {
    private static final int QUAD_VERTEX_COUNT = 4;
    private static final int QUAD_INDEX_COUNT = 6;
    private static final int INSTANCE_COUNT = 12;
    private static final int BYTES_PER_QUAD_VERTEX = 2 * 4;
    private static final int FLOATS_PER_INSTANCE = 6;
    private static final int BYTES_PER_INSTANCE = FLOATS_PER_INSTANCE * 4;
    private static final VertexLayout QUAD_LAYOUT = VertexLayout.of(BYTES_PER_QUAD_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0));
    private static final VertexLayout INSTANCE_LAYOUT = VertexLayout.instance(BYTES_PER_INSTANCE,
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 8));
    private static final float[] QUAD_VERTICES = {
            -0.10f, -0.10f,
            0.10f, -0.10f,
            0.10f, 0.10f,
            -0.10f, 0.10f
    };
    private static final short[] QUAD_INDICES = { 0, 1, 2, 0, 2, 3 };
    private static final float[] INSTANCE_DATA = {
            -0.66f, 0.42f, 0.98f, 0.24f, 0.24f, 1.0f,
            -0.22f, 0.42f, 0.95f, 0.72f, 0.18f, 1.0f,
            0.22f, 0.42f, 0.18f, 0.75f, 0.98f, 1.0f,
            0.66f, 0.42f, 0.30f, 0.96f, 0.46f, 1.0f,
            -0.66f, 0.00f, 0.96f, 0.36f, 0.84f, 1.0f,
            -0.22f, 0.00f, 0.32f, 0.90f, 0.88f, 1.0f,
            0.22f, 0.00f, 0.98f, 0.88f, 0.24f, 1.0f,
            0.66f, 0.00f, 0.48f, 0.54f, 1.0f, 1.0f,
            -0.66f, -0.42f, 0.86f, 0.26f, 0.30f, 1.0f,
            -0.22f, -0.42f, 0.22f, 0.82f, 0.42f, 1.0f,
            0.22f, -0.42f, 0.92f, 0.54f, 0.18f, 1.0f,
            0.66f, -0.42f, 0.18f, 0.62f, 0.96f, 1.0f
    };
    private static final String SHADER_SOURCE = """
            struct VertexInput {
                @location(0) localPosition : vec2f,
                @location(1) offset : vec2f,
                @location(2) color : vec4f,
            };

            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) color : vec4f,
            };

            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4f(input.localPosition + input.offset, 0.0, 1.0);
                output.color = input.color;
                return output;
            }

            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                return input.color;
            }
            """;

    private final RenderPassDescriptor passDescriptor = new RenderPassDescriptor()
            .label("instancing basic pass")
            .colorLoadOp(LoadOp.clear(0.02f, 0.025f, 0.035f, 1.0f))
            .colorStoreOp(StoreOp.store());
    private ShaderModule shaderModule;
    private RenderPipeline pipeline;
    private Buffer quadVertexBuffer;
    private Buffer indexBuffer;
    private Buffer instanceBuffer;

    /**
     * Creates an instance-step vertex layout parity test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public InstancingBasicTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "InstancingBasicTest");
        quadVertexBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                "instancing basic quad vertices", QUAD_VERTEX_COUNT * BYTES_PER_QUAD_VERTEX));
        graphics.device().writeBuffer(quadVertexBuffer, floats(QUAD_VERTICES));
        indexBuffer = graphics.device().createBuffer(BufferDescriptor.staticIndex(
                "instancing basic quad indices", QUAD_INDEX_COUNT * 2));
        graphics.device().writeBuffer(indexBuffer, shorts(QUAD_INDICES));
        instanceBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                "instancing basic instance data", INSTANCE_COUNT * BYTES_PER_INSTANCE));
        graphics.device().writeBuffer(instanceBuffer, floats(INSTANCE_DATA));
        shaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("instancing basic shader", SHADER_SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shaderModule, graphics.surfaceFormat())
                .label("instancing basic pipeline")
                .vertexLayouts(QUAD_LAYOUT, INSTANCE_LAYOUT)
                .depthWriteEnabled(false));
        markCreated();
    }

    @Override
    public void render() {
        GraphicsFrame frame = graphics.currentFrame();
        passDescriptor.colorAttachment(frame.colorAttachment());
        RenderPass pass = frame.commandEncoder().beginRenderPass(passDescriptor);
        pass.setPipeline(pipeline);
        pass.setVertexBuffer(0, quadVertexBuffer);
        pass.setVertexBuffer(1, instanceBuffer);
        pass.setIndexBuffer(indexBuffer);
        pass.drawIndexed(QUAD_INDEX_COUNT, INSTANCE_COUNT, 0, 0, 0);
        pass.end();
        finishFrame();
    }

    @Override
    public void dispose() {
        dispose(pipeline);
        dispose(shaderModule);
        dispose(quadVertexBuffer);
        dispose(indexBuffer);
        dispose(instanceBuffer);
        verifyDisposed();
    }
}
