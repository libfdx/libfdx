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
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;

import java.nio.ByteBuffer;

/**
 * Runs the depth-preserving multi-pass parity test.
 *
 * @author xpenatan
 */
public final class DepthPreserveTest extends GraphicsParityTest {
    private static final int TEXTURE_SIZE = 96;
    private static final int FLOATS_PER_VERTEX = 9;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 12),
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 20));
    private static final float[] DEPTH_MASK_VERTICES = {
            -0.36f, -0.50f, 0.24f, 0.0f, 1.0f, 0.16f, 0.10f, 0.08f, 1.0f,
            0.40f, -0.50f, 0.24f, 1.0f, 1.0f, 0.16f, 0.10f, 0.08f, 1.0f,
            0.40f, 0.36f, 0.24f, 1.0f, 0.0f, 0.20f, 0.12f, 0.10f, 1.0f,
            -0.36f, -0.50f, 0.24f, 0.0f, 1.0f, 0.16f, 0.10f, 0.08f, 1.0f,
            0.40f, 0.36f, 0.24f, 1.0f, 0.0f, 0.20f, 0.12f, 0.10f, 1.0f,
            -0.36f, 0.36f, 0.24f, 0.0f, 0.0f, 0.18f, 0.11f, 0.09f, 1.0f,

            0.40f, -0.50f, 0.24f, 0.0f, 1.0f, 0.16f, 0.10f, 0.08f, 1.0f,
            0.56f, -0.38f, 0.24f, 1.0f, 1.0f, 0.16f, 0.10f, 0.08f, 1.0f,
            0.56f, 0.50f, 0.24f, 1.0f, 0.0f, 0.20f, 0.12f, 0.10f, 1.0f,
            0.40f, -0.50f, 0.24f, 0.0f, 1.0f, 0.16f, 0.10f, 0.08f, 1.0f,
            0.56f, 0.50f, 0.24f, 1.0f, 0.0f, 0.20f, 0.12f, 0.10f, 1.0f,
            0.40f, 0.36f, 0.24f, 0.0f, 0.0f, 0.18f, 0.11f, 0.09f, 1.0f,

            -0.36f, 0.36f, 0.24f, 0.0f, 1.0f, 0.16f, 0.10f, 0.08f, 1.0f,
            0.40f, 0.36f, 0.24f, 1.0f, 1.0f, 0.16f, 0.10f, 0.08f, 1.0f,
            0.56f, 0.50f, 0.24f, 1.0f, 0.0f, 0.20f, 0.12f, 0.10f, 1.0f,
            -0.36f, 0.36f, 0.24f, 0.0f, 1.0f, 0.16f, 0.10f, 0.08f, 1.0f,
            0.56f, 0.50f, 0.24f, 1.0f, 0.0f, 0.20f, 0.12f, 0.10f, 1.0f,
            -0.22f, 0.50f, 0.24f, 0.0f, 0.0f, 0.18f, 0.11f, 0.09f, 1.0f
    };
    private static final float[] POSTER_VERTICES = {
            -0.88f, -0.80f, 0.78f, 0.0f, 1.0f, 0.94f, 0.94f, 0.94f, 1.0f,
            0.88f, -0.80f, 0.78f, 1.0f, 1.0f, 0.94f, 0.94f, 0.94f, 1.0f,
            0.88f, 0.80f, 0.78f, 1.0f, 0.0f, 0.94f, 0.94f, 0.94f, 1.0f,
            -0.88f, -0.80f, 0.78f, 0.0f, 1.0f, 0.94f, 0.94f, 0.94f, 1.0f,
            0.88f, 0.80f, 0.78f, 1.0f, 0.0f, 0.94f, 0.94f, 0.94f, 1.0f,
            -0.88f, 0.80f, 0.78f, 0.0f, 0.0f, 0.94f, 0.94f, 0.94f, 1.0f
    };
    private static final float[] CRATE_VERTICES = {
            -0.30f, -0.44f, 0.10f, 0.0f, 1.0f, 0.88f, 0.88f, 0.88f, 1.0f,
            0.34f, -0.44f, 0.10f, 1.0f, 1.0f, 0.88f, 0.88f, 0.88f, 1.0f,
            0.34f, 0.30f, 0.10f, 1.0f, 0.0f, 0.88f, 0.88f, 0.88f, 1.0f,
            -0.30f, -0.44f, 0.10f, 0.0f, 1.0f, 0.88f, 0.88f, 0.88f, 1.0f,
            0.34f, 0.30f, 0.10f, 1.0f, 0.0f, 0.88f, 0.88f, 0.88f, 1.0f,
            -0.30f, 0.30f, 0.10f, 0.0f, 0.0f, 0.88f, 0.88f, 0.88f, 1.0f,

            0.34f, -0.44f, 0.10f, 0.0f, 1.0f, 0.58f, 0.58f, 0.58f, 1.0f,
            0.50f, -0.32f, 0.18f, 1.0f, 1.0f, 0.58f, 0.58f, 0.58f, 1.0f,
            0.50f, 0.44f, 0.18f, 1.0f, 0.0f, 0.58f, 0.58f, 0.58f, 1.0f,
            0.34f, -0.44f, 0.10f, 0.0f, 1.0f, 0.58f, 0.58f, 0.58f, 1.0f,
            0.50f, 0.44f, 0.18f, 1.0f, 0.0f, 0.58f, 0.58f, 0.58f, 1.0f,
            0.34f, 0.30f, 0.10f, 0.0f, 0.0f, 0.58f, 0.58f, 0.58f, 1.0f,

            -0.30f, 0.30f, 0.10f, 0.0f, 1.0f, 1.00f, 1.00f, 1.00f, 1.0f,
            0.34f, 0.30f, 0.10f, 1.0f, 1.0f, 1.00f, 1.00f, 1.00f, 1.0f,
            0.50f, 0.44f, 0.18f, 1.0f, 0.0f, 1.00f, 1.00f, 1.00f, 1.0f,
            -0.30f, 0.30f, 0.10f, 0.0f, 1.0f, 1.00f, 1.00f, 1.00f, 1.0f,
            0.50f, 0.44f, 0.18f, 1.0f, 0.0f, 1.00f, 1.00f, 1.00f, 1.0f,
            -0.16f, 0.44f, 0.18f, 0.0f, 0.0f, 1.00f, 1.00f, 1.00f, 1.0f
    };
    private static final int DEPTH_MASK_VERTEX_COUNT = DEPTH_MASK_VERTICES.length / FLOATS_PER_VERTEX;
    private static final int POSTER_VERTEX_COUNT = POSTER_VERTICES.length / FLOATS_PER_VERTEX;
    private static final int CRATE_VERTEX_COUNT = CRATE_VERTICES.length / FLOATS_PER_VERTEX;
    private static final String SHADER_SOURCE = """
            struct VertexInput {
                @location(0) position : vec3f,
                @location(1) texCoord : vec2f,
                @location(2) tint : vec4f,
            };

            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) texCoord : vec2f,
                @location(1) tint : vec4f,
            };

            @group(0) @binding(0) var u_texture : texture_2d<f32>;
            @group(0) @binding(1) var u_sampler : sampler;

            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4f(input.position, 1.0);
                output.texCoord = input.texCoord;
                output.tint = input.tint;
                return output;
            }

            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                let texel = textureSample(u_texture, u_sampler, input.texCoord);
                return vec4f(texel.rgb * input.tint.rgb, texel.a * input.tint.a);
            }
            """;

    private final RenderPassDescriptor depthWritePass = new RenderPassDescriptor()
            .label("depth preserve crate depth pass")
            .colorLoadOp(LoadOp.clear(0.18f, 0.18f, 0.18f, 1.0f))
            .colorStoreOp(StoreOp.store())
            .depthClear(1.0f);
    private final RenderPassDescriptor preserveDepthPass = new RenderPassDescriptor()
            .label("depth preserve textured scene pass")
            .colorLoadOp(LoadOp.clear(0.12f, 0.13f, 0.14f, 1.0f))
            .colorStoreOp(StoreOp.store())
            .depthEnabled(true);
    private ShaderModule shaderModule;
    private RenderPipeline pipeline;
    private Buffer depthMaskBuffer;
    private Buffer posterBuffer;
    private Buffer crateBuffer;
    private Texture crateTexture;
    private Texture posterTexture;

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
        crateTexture = graphics.device().createTexture(TextureDescriptor.rgba8(
                "depth preserve crate texture", TEXTURE_SIZE, TEXTURE_SIZE));
        graphics.device().writeTexture(crateTexture, crateTexturePixels());
        posterTexture = graphics.device().createTexture(TextureDescriptor.rgba8(
                "depth preserve poster texture", TEXTURE_SIZE, TEXTURE_SIZE));
        graphics.device().writeTexture(posterTexture, posterTexturePixels());
        depthMaskBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                "depth preserve crate depth mask", DEPTH_MASK_VERTEX_COUNT * BYTES_PER_VERTEX));
        graphics.device().writeBuffer(depthMaskBuffer, floats(DEPTH_MASK_VERTICES));
        posterBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                "depth preserve poster", POSTER_VERTEX_COUNT * BYTES_PER_VERTEX));
        graphics.device().writeBuffer(posterBuffer, floats(POSTER_VERTICES));
        crateBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                "depth preserve textured crate", CRATE_VERTEX_COUNT * BYTES_PER_VERTEX));
        graphics.device().writeBuffer(crateBuffer, floats(CRATE_VERTICES));
        shaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("depth preserve textured shader", SHADER_SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shaderModule, graphics.surfaceFormat())
                .label("depth preserve textured pipeline")
                .vertexLayout(VERTEX_LAYOUT)
                .sampledTextureCount(1)
                .depthTestEnabled(true)
                .depthWriteEnabled(true));
        markCreated();
    }

    @Override
    public void render() {
        GraphicsFrame frame = graphics.currentFrame();
        depthWritePass.colorAttachment(frame.colorAttachment());
        RenderPass pass = frame.commandEncoder().beginRenderPass(depthWritePass);
        pass.setPipeline(pipeline);
        pass.setTexture(0, crateTexture);
        pass.setVertexBuffer(depthMaskBuffer);
        pass.draw(DEPTH_MASK_VERTEX_COUNT, 1, 0, 0);
        pass.end();

        preserveDepthPass.colorAttachment(frame.colorAttachment());
        pass = frame.commandEncoder().beginRenderPass(preserveDepthPass);
        pass.setPipeline(pipeline);
        pass.setTexture(0, posterTexture);
        pass.setVertexBuffer(posterBuffer);
        pass.draw(POSTER_VERTEX_COUNT, 1, 0, 0);
        pass.setTexture(0, crateTexture);
        pass.setVertexBuffer(crateBuffer);
        pass.draw(CRATE_VERTEX_COUNT, 1, 0, 0);
        pass.end();
        finishFrame();
    }

    @Override
    public void dispose() {
        dispose(pipeline);
        dispose(shaderModule);
        dispose(depthMaskBuffer);
        dispose(posterBuffer);
        dispose(crateBuffer);
        dispose(crateTexture);
        dispose(posterTexture);
        verifyDisposed();
    }

    private static ByteBuffer crateTexturePixels() {
        ByteBuffer pixels = rgba8(TEXTURE_SIZE, TEXTURE_SIZE);
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                int grain = ((x * 13 + y * 5) & 15) - 7;
                int value = 168 + grain;
                boolean border = x < 7 || y < 7 || x >= TEXTURE_SIZE - 7 || y >= TEXTURE_SIZE - 7;
                boolean diagonal = Math.abs(x - y) < 4 || Math.abs((TEXTURE_SIZE - 1 - x) - y) < 4;
                boolean band = x >= 42 && x <= 53;
                boolean plank = y % 18 <= 1;
                boolean label = crateLabelPixel(x, y);
                if (border || diagonal || band) {
                    value = 38;
                }
                else if (plank) {
                    value = 112;
                }
                if (label) {
                    value = 22;
                }
                putGray(pixels, value, 255);
            }
        }
        pixels.flip();
        return pixels;
    }

    private static ByteBuffer posterTexturePixels() {
        ByteBuffer pixels = rgba8(TEXTURE_SIZE, TEXTURE_SIZE);
        int center = TEXTURE_SIZE / 2;
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                int value = 214;
                boolean border = x < 3 || y < 3 || x >= TEXTURE_SIZE - 3 || y >= TEXTURE_SIZE - 3;
                boolean grid = x % 16 == 0 || y % 16 == 0;
                int dx = x - center;
                int dy = y - 54;
                int dist2 = dx * dx + dy * dy;
                boolean outerRing = Math.abs(dist2 - 28 * 28) < 64;
                boolean middleRing = Math.abs(dist2 - 18 * 18) < 46;
                boolean innerRing = Math.abs(dist2 - 8 * 8) < 28;
                boolean cross = Math.abs(dx) < 2 || Math.abs(dy) < 2;
                boolean label = farLabelPixel(x, y);
                if (grid) {
                    value = 182;
                }
                if (border || outerRing || middleRing || innerRing || cross || label) {
                    value = 46;
                }
                putGray(pixels, value, 255);
            }
        }
        pixels.flip();
        return pixels;
    }

    private static boolean crateLabelPixel(int x, int y) {
        if (y < 34 || y > 57) {
            return false;
        }
        boolean letterF = (x >= 18 && x <= 22)
                || (x >= 18 && x <= 36 && y <= 38)
                || (x >= 18 && x <= 32 && y >= 44 && y <= 48);
        boolean letterD = (x >= 42 && x <= 46)
                || (x >= 42 && x <= 58 && (y <= 38 || y >= 53))
                || (x >= 58 && x <= 62 && y >= 39 && y <= 52);
        boolean letterX = x >= 68 && x <= 82
                && (Math.abs((x - 75) - (y - 45)) <= 2 || Math.abs((x - 75) + (y - 45)) <= 2);
        return letterF || letterD || letterX;
    }

    private static boolean farLabelPixel(int x, int y) {
        if (y < 9 || y > 23) {
            return false;
        }
        boolean letterF = (x >= 24 && x <= 27)
                || (x >= 24 && x <= 38 && y <= 12)
                || (x >= 24 && x <= 35 && y >= 16 && y <= 19);
        boolean letterA = (x >= 44 && x <= 47)
                || (x >= 58 && x <= 61)
                || (x >= 44 && x <= 61 && (y <= 12 || (y >= 16 && y <= 19)));
        boolean letterR = (x >= 68 && x <= 71)
                || (x >= 68 && x <= 82 && (y <= 12 || (y >= 16 && y <= 19)))
                || (x >= 82 && x <= 85 && y >= 13 && y <= 18)
                || (x >= 78 && x <= 85 && y >= 20);
        return letterF || letterA || letterR;
    }

    private static void putGray(ByteBuffer pixels, int value, int alpha) {
        putRgba(pixels, value, value, value, alpha);
    }

    private static void putRgba(ByteBuffer pixels, int red, int green, int blue, int alpha) {
        pixels.put((byte)red);
        pixels.put((byte)green);
        pixels.put((byte)blue);
        pixels.put((byte)alpha);
    }
}
