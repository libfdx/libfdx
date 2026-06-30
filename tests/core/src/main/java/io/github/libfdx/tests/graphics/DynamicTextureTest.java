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
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;

import java.nio.ByteBuffer;

/**
 * Runs the per-frame writeTexture parity test.
 *
 * @author xpenatan
 */
public final class DynamicTextureTest extends GraphicsParityTest {
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 64;
    private static final int FLOATS_PER_VERTEX = 4;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final int VERTEX_COUNT = 6;
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8));
    private static final float[] QUAD_VERTICES = {
            -0.82f, -0.82f, 0.0f, 1.0f,
            0.82f, -0.82f, 1.0f, 1.0f,
            0.82f, 0.82f, 1.0f, 0.0f,
            -0.82f, -0.82f, 0.0f, 1.0f,
            0.82f, 0.82f, 1.0f, 0.0f,
            -0.82f, 0.82f, 0.0f, 0.0f
    };
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

    private final RenderPassDescriptor passDescriptor = new RenderPassDescriptor()
            .label("dynamic texture pass")
            .colorLoadOp(LoadOp.clear(0.02f, 0.025f, 0.03f, 1.0f))
            .colorStoreOp(StoreOp.store());
    private ShaderModule shaderModule;
    private RenderPipeline pipeline;
    private Buffer vertexBuffer;
    private Texture texture;
    private ByteBuffer pixels;
    private long frameIndex;

    /**
     * Creates a per-frame writeTexture parity test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public DynamicTextureTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "DynamicTextureTest");
        pixels = rgba8(TEXTURE_WIDTH, TEXTURE_HEIGHT);
        texture = graphics.device().createTexture(TextureDescriptor.rgba8(
                "dynamic texture", TEXTURE_WIDTH, TEXTURE_HEIGHT));
        fillPixels(0L);
        graphics.device().writeTexture(texture, pixels);
        vertexBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                "dynamic texture quad", VERTEX_COUNT * BYTES_PER_VERTEX));
        graphics.device().writeBuffer(vertexBuffer, floats(QUAD_VERTICES));
        shaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("dynamic texture shader", SHADER_SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shaderModule, graphics.surfaceFormat())
                .label("dynamic texture pipeline")
                .vertexLayout(VERTEX_LAYOUT)
                .sampledTextureCount(1)
                .depthWriteEnabled(false));
        markCreated();
    }

    @Override
    public void render() {
        fillPixels(frameIndex);
        graphics.device().writeTexture(texture, pixels);

        GraphicsFrame frame = graphics.currentFrame();
        passDescriptor.colorAttachment(frame.colorAttachment());
        RenderPass pass = frame.commandEncoder().beginRenderPass(passDescriptor);
        pass.setPipeline(pipeline);
        pass.setTexture(0, texture);
        pass.setVertexBuffer(vertexBuffer);
        pass.draw(VERTEX_COUNT, 1, 0, 0);
        pass.end();
        frameIndex++;
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

    private void fillPixels(long frame) {
        int time = (int)(frame & 0xffL);
        pixels.clear();
        for (int y = 0; y < TEXTURE_HEIGHT; y++) {
            for (int x = 0; x < TEXTURE_WIDTH; x++) {
                int stripe = ((x + time) / 8 + y / 8) & 1;
                int red = stripe == 0 ? 32 + ((x * 3 + time) & 0x7f) : 235;
                int green = stripe == 0 ? 220 : 42 + ((y * 3 + time * 2) & 0x7f);
                int blue = 60 + ((x * 5 + y * 3 + time * 4) & 0xbf);
                pixels.put((byte)red);
                pixels.put((byte)green);
                pixels.put((byte)blue);
                pixels.put((byte)255);
            }
        }
        pixels.flip();
    }
}
