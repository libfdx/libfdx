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
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;

/**
 * Runs the render target chaining parity test.
 *
 * @author xpenatan
 */
public final class RenderTargetChainTest extends GraphicsParityTest {
    private static final int TARGET_SIZE = 256;
    private static final int COLOR_FLOATS_PER_VERTEX = 6;
    private static final int COLOR_BYTES_PER_VERTEX = COLOR_FLOATS_PER_VERTEX * 4;
    private static final int TEXTURE_FLOATS_PER_VERTEX = 4;
    private static final int TEXTURE_BYTES_PER_VERTEX = TEXTURE_FLOATS_PER_VERTEX * 4;
    private static final int VERTEX_COUNT = 6;
    private static final VertexLayout COLOR_LAYOUT = VertexLayout.of(COLOR_BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X4, 8));
    private static final VertexLayout TEXTURE_LAYOUT = VertexLayout.of(TEXTURE_BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8));
    private static final float[] TARGET_PATTERN_VERTICES = {
            -1.0f, -1.0f, 0.90f, 0.20f, 0.18f, 1.0f,
            1.0f, -1.0f, 0.16f, 0.62f, 0.96f, 1.0f,
            1.0f, 1.0f, 0.26f, 0.92f, 0.34f, 1.0f,
            -1.0f, -1.0f, 0.90f, 0.20f, 0.18f, 1.0f,
            1.0f, 1.0f, 0.26f, 0.92f, 0.34f, 1.0f,
            -1.0f, 1.0f, 0.98f, 0.86f, 0.18f, 1.0f
    };
    private static final float[] TARGET_QUAD_VERTICES = {
            -1.0f, -1.0f, 0.0f, 1.0f,
            1.0f, -1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 0.0f,
            -1.0f, 1.0f, 0.0f, 0.0f
    };
    private static final float[] SCREEN_QUAD_VERTICES = {
            -0.82f, -0.82f, 0.0f, 1.0f,
            0.82f, -0.82f, 1.0f, 1.0f,
            0.82f, 0.82f, 1.0f, 0.0f,
            -0.82f, -0.82f, 0.0f, 1.0f,
            0.82f, 0.82f, 1.0f, 0.0f,
            -0.82f, 0.82f, 0.0f, 0.0f
    };
    private static final String COLOR_SHADER_SOURCE = """
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
    private static final String TEXTURE_SHADER_SOURCE = """
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
                let sampled = textureSample(u_texture, u_sampler, input.texCoord);
                return vec4f(sampled.rgb * vec3f(0.88, 0.94, 1.0), 1.0);
            }
            """;

    private final RenderPassDescriptor targetAPass = new RenderPassDescriptor()
            .label("render target chain first pass")
            .colorLoadOp(LoadOp.clear(0.0f, 0.0f, 0.0f, 1.0f))
            .colorStoreOp(StoreOp.store());
    private final RenderPassDescriptor targetBPass = new RenderPassDescriptor()
            .label("render target chain second pass")
            .colorLoadOp(LoadOp.clear(0.03f, 0.04f, 0.08f, 1.0f))
            .colorStoreOp(StoreOp.store());
    private final RenderPassDescriptor screenPass = new RenderPassDescriptor()
            .label("render target chain present pass")
            .colorLoadOp(LoadOp.clear(0.015f, 0.018f, 0.025f, 1.0f))
            .colorStoreOp(StoreOp.store());
    private ShaderModule colorShaderModule;
    private ShaderModule textureShaderModule;
    private RenderPipeline colorTargetPipeline;
    private RenderPipeline textureTargetPipeline;
    private RenderPipeline screenPipeline;
    private Buffer targetPatternBuffer;
    private Buffer targetQuadBuffer;
    private Buffer screenQuadBuffer;
    private Texture targetA;
    private Texture targetB;
    private TextureView targetAView;
    private TextureView targetBView;

    /**
     * Creates a render target chaining parity test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public RenderTargetChainTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "RenderTargetChainTest");
        targetA = graphics.device().createTexture(TextureDescriptor.rgba8RenderTarget(
                "render target chain a", TARGET_SIZE, TARGET_SIZE));
        targetB = graphics.device().createTexture(TextureDescriptor.rgba8RenderTarget(
                "render target chain b", TARGET_SIZE, TARGET_SIZE));
        targetAView = targetA.view();
        targetBView = targetB.view();
        targetPatternBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                "render target chain pattern vertices", VERTEX_COUNT * COLOR_BYTES_PER_VERTEX));
        graphics.device().writeBuffer(targetPatternBuffer, floats(TARGET_PATTERN_VERTICES));
        targetQuadBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                "render target chain target quad", VERTEX_COUNT * TEXTURE_BYTES_PER_VERTEX));
        graphics.device().writeBuffer(targetQuadBuffer, floats(TARGET_QUAD_VERTICES));
        screenQuadBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                "render target chain screen quad", VERTEX_COUNT * TEXTURE_BYTES_PER_VERTEX));
        graphics.device().writeBuffer(screenQuadBuffer, floats(SCREEN_QUAD_VERTICES));
        colorShaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("render target chain color shader", COLOR_SHADER_SOURCE));
        textureShaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("render target chain texture shader", TEXTURE_SHADER_SOURCE));
        colorTargetPipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(colorShaderModule, TextureFormat.RGBA8_UNORM)
                .label("render target chain color pipeline")
                .vertexLayout(COLOR_LAYOUT)
                .depthWriteEnabled(false));
        textureTargetPipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(textureShaderModule, TextureFormat.RGBA8_UNORM)
                .label("render target chain texture target pipeline")
                .vertexLayout(TEXTURE_LAYOUT)
                .sampledTextureCount(1)
                .depthWriteEnabled(false));
        screenPipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(textureShaderModule, graphics.surfaceFormat())
                .label("render target chain screen pipeline")
                .vertexLayout(TEXTURE_LAYOUT)
                .sampledTextureCount(1)
                .depthWriteEnabled(false));
        markCreated();
    }

    @Override
    public void render() {
        GraphicsFrame frame = graphics.currentFrame();

        targetAPass.colorAttachment(targetAView);
        RenderPass pass = frame.commandEncoder().beginRenderPass(targetAPass);
        pass.setPipeline(colorTargetPipeline);
        pass.setVertexBuffer(targetPatternBuffer);
        pass.draw(VERTEX_COUNT, 1, 0, 0);
        pass.end();

        targetBPass.colorAttachment(targetBView);
        pass = frame.commandEncoder().beginRenderPass(targetBPass);
        pass.setPipeline(textureTargetPipeline);
        pass.setTexture(0, targetA);
        pass.setVertexBuffer(targetQuadBuffer);
        pass.draw(VERTEX_COUNT, 1, 0, 0);
        pass.end();

        screenPass.colorAttachment(frame.colorAttachment());
        pass = frame.commandEncoder().beginRenderPass(screenPass);
        pass.setPipeline(screenPipeline);
        pass.setTexture(0, targetB);
        pass.setVertexBuffer(screenQuadBuffer);
        pass.draw(VERTEX_COUNT, 1, 0, 0);
        pass.end();
        finishFrame();
    }

    @Override
    public void dispose() {
        dispose(screenPipeline);
        dispose(textureTargetPipeline);
        dispose(colorTargetPipeline);
        dispose(textureShaderModule);
        dispose(colorShaderModule);
        dispose(targetPatternBuffer);
        dispose(targetQuadBuffer);
        dispose(screenQuadBuffer);
        dispose(targetA);
        dispose(targetB);
        verifyDisposed();
    }
}
