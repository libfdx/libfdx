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
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;

import java.nio.ByteBuffer;

/**
 * Proves that writes after command recording do not overwrite already-recorded resource allocations.
 *
 * @author xpenatan
 */
public final class RecordedResourceRewriteTest extends GraphicsParityTest {
    private static final int TEXTURE_SIZE = 2;
    private static final int VERTEX_COUNT = 6;
    private static final int BYTES_PER_VERTEX = 4 * 4;
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8));
    private static final float[] LEFT_VERTICES = rectangle(-0.9f, -0.65f, -0.1f, 0.65f);
    private static final float[] RIGHT_VERTICES = rectangle(0.1f, -0.65f, 0.9f, 0.65f);
    private static final String SHADER_SOURCE = """
            struct VertexInput {
                @location(0) position : vec2f,
                @location(1) texCoord : vec2f,
            };

            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) texCoord : vec2f,
            };

            @group(0) @binding(0) var u_texture : texture_2d<f32>;
            @group(0) @binding(1) var u_sampler : sampler;

            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4f(input.position, 0.0, 1.0);
                output.texCoord = input.texCoord;
                return output;
            }

            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                return textureSample(u_texture, u_sampler, input.texCoord);
            }
            """;

    private final RenderPassDescriptor firstPassDescriptor = new RenderPassDescriptor()
            .label("recorded rewrite old allocation pass")
            .colorLoadOp(LoadOp.clear(0.015f, 0.02f, 0.025f, 1.0f))
            .colorStoreOp(StoreOp.store());
    private final RenderPassDescriptor secondPassDescriptor = new RenderPassDescriptor()
            .label("recorded rewrite new allocation pass")
            .colorLoadOp(LoadOp.load())
            .colorStoreOp(StoreOp.store());
    private Buffer vertexBuffer;
    private Texture texture;
    private ShaderModule shaderModule;
    private RenderPipeline pipeline;
    private ByteBuffer leftVertices;
    private ByteBuffer rightVertices;
    private ByteBuffer redPixels;
    private ByteBuffer bluePixels;

    /**
     * Creates the recorded-resource rewrite test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public RecordedResourceRewriteTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "RecordedResourceRewriteTest");
        leftVertices = floats(LEFT_VERTICES);
        rightVertices = floats(RIGHT_VERTICES);
        redPixels = solidPixels(232, 48, 58);
        bluePixels = solidPixels(46, 112, 232);
        vertexBuffer = graphics.device().createBuffer(BufferDescriptor.vertex(
                "recorded rewrite vertices", VERTEX_COUNT * BYTES_PER_VERTEX));
        texture = graphics.device().createTexture(TextureDescriptor
                .rgba8("recorded rewrite texture", TEXTURE_SIZE, TEXTURE_SIZE)
                .filter(TextureFilter.NEAREST));
        shaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("recorded rewrite shader", SHADER_SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shaderModule, graphics.surfaceFormat())
                .label("recorded rewrite pipeline")
                .vertexLayout(VERTEX_LAYOUT)
                .sampledTextureCount(1)
                .depthWriteEnabled(false));
        markCreated();
        logger.info("RecordedResourceRewriteTest expects red on the left and blue on the right");
    }

    @Override
    public void render() {
        write(vertexBuffer, leftVertices);
        write(texture, redPixels);

        GraphicsFrame frame = graphics.currentFrame();
        firstPassDescriptor.colorAttachment(frame.colorAttachment());
        draw(frame, firstPassDescriptor);

        write(vertexBuffer, rightVertices);
        write(texture, bluePixels);

        secondPassDescriptor.colorAttachment(frame.colorAttachment());
        draw(frame, secondPassDescriptor);
        finishFrame();
    }

    @Override
    public void dispose() {
        dispose(pipeline);
        dispose(shaderModule);
        dispose(vertexBuffer);
        dispose(texture);
        verifyDisposed();
    }

    private void draw(GraphicsFrame frame, RenderPassDescriptor descriptor) {
        RenderPass pass = frame.commandEncoder().beginRenderPass(descriptor);
        pass.setPipeline(pipeline);
        pass.setTexture(0, texture);
        pass.setVertexBuffer(vertexBuffer);
        pass.draw(VERTEX_COUNT, 1, 0, 0);
        pass.end();
    }

    private void write(Buffer buffer, ByteBuffer data) {
        data.position(0);
        data.limit(data.capacity());
        graphics.device().writeBuffer(buffer, data);
    }

    private void write(Texture texture, ByteBuffer data) {
        data.position(0);
        data.limit(data.capacity());
        graphics.device().writeTexture(texture, data);
    }

    private static ByteBuffer solidPixels(int red, int green, int blue) {
        ByteBuffer pixels = rgba8(TEXTURE_SIZE, TEXTURE_SIZE);
        for (int i = 0; i < TEXTURE_SIZE * TEXTURE_SIZE; i++) {
            pixels.put((byte) red);
            pixels.put((byte) green);
            pixels.put((byte) blue);
            pixels.put((byte) 255);
        }
        pixels.flip();
        return pixels;
    }

    private static float[] rectangle(float left, float bottom, float right, float top) {
        return new float[] {
                left, top, 0.0f, 1.0f,
                left, bottom, 0.0f, 0.0f,
                right, bottom, 1.0f, 0.0f,
                left, top, 0.0f, 1.0f,
                right, bottom, 1.0f, 0.0f,
                right, top, 1.0f, 1.0f
        };
    }
}
