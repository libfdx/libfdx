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
import java.nio.ByteOrder;

/**
 * Runs the per-frame writeTexture parity test.
 *
 * @author xpenatan
 */
public final class DynamicTextureTest extends GraphicsParityTest {
    private static final int TEXTURE_WIDTH = 320;
    private static final int TEXTURE_HEIGHT = 180;
    private static final int FLOATS_PER_VERTEX = 4;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final int VERTEX_COUNT = 6;
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8));
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
    private ByteBuffer quadVertices;
    private long frameIndex;
    private int lastFrameWidth;
    private int lastFrameHeight;

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
                "dynamic texture diagnostic", TEXTURE_WIDTH, TEXTURE_HEIGHT));
        fillPixels(0L);
        graphics.device().writeTexture(texture, pixels);
        vertexBuffer = graphics.device().createBuffer(BufferDescriptor.vertex(
                "dynamic texture quad", VERTEX_COUNT * BYTES_PER_VERTEX));
        quadVertices = ByteBuffer.allocateDirect(VERTEX_COUNT * BYTES_PER_VERTEX)
                .order(ByteOrder.nativeOrder());
        shaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("dynamic texture shader", SHADER_SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shaderModule, graphics.surfaceFormat())
                .label("dynamic texture pipeline")
                .vertexLayout(VERTEX_LAYOUT)
                .sampledTextureCount(1)
                .depthWriteEnabled(false));
        markCreated();
        logger.info("DynamicTextureTest uploads a " + texture.width() + "x" + texture.height()
                + " generated diagnostic texture every frame");
    }

    @Override
    public void render() {
        fillPixels(frameIndex);
        graphics.device().writeTexture(texture, pixels);

        GraphicsFrame frame = graphics.currentFrame();
        int width = frame.width() > 0 ? frame.width() : framebufferWidth();
        int height = frame.height() > 0 ? frame.height() : framebufferHeight();
        updateQuadVertices(width, height);

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

    private void updateQuadVertices(int frameWidth, int frameHeight) {
        if (frameWidth == lastFrameWidth && frameHeight == lastFrameHeight) {
            return;
        }
        lastFrameWidth = frameWidth;
        lastFrameHeight = frameHeight;

        float displayAspect = frameHeight > 0 ? frameWidth / (float)frameHeight : 4.0f / 3.0f;
        float textureAspect = TEXTURE_WIDTH / (float)TEXTURE_HEIGHT;
        float halfWidth = 0.90f;
        float halfHeight = halfWidth * displayAspect / textureAspect;
        if (halfHeight > 0.76f) {
            halfHeight = 0.76f;
            halfWidth = halfHeight * textureAspect / displayAspect;
        }

        quadVertices.clear();
        putVertex(quadVertices, -halfWidth, -halfHeight, 0.0f, 1.0f);
        putVertex(quadVertices, halfWidth, -halfHeight, 1.0f, 1.0f);
        putVertex(quadVertices, halfWidth, halfHeight, 1.0f, 0.0f);
        putVertex(quadVertices, -halfWidth, -halfHeight, 0.0f, 1.0f);
        putVertex(quadVertices, halfWidth, halfHeight, 1.0f, 0.0f);
        putVertex(quadVertices, -halfWidth, halfHeight, 0.0f, 0.0f);
        quadVertices.flip();
        graphics.device().writeBuffer(vertexBuffer, quadVertices);
    }

    private static void putVertex(ByteBuffer vertices, float x, float y, float u, float v) {
        vertices.putFloat(x);
        vertices.putFloat(y);
        vertices.putFloat(u);
        vertices.putFloat(v);
    }

    private void fillPixels(long frame) {
        int slowFrame = (int)(frame / 6L);
        int scanX = 44 + slowFrame % 232;
        int progress = 2 + slowFrame % 228;
        int frameValue = (int)(frame % 10000L);

        pixels.clear();
        for (int y = 0; y < TEXTURE_HEIGHT; y++) {
            for (int x = 0; x < TEXTURE_WIDTH; x++) {
                int red = 22;
                int green = 29;
                int blue = 36;
                if (x % 40 == 0 || y % 40 == 0) {
                    red = 58;
                    green = 68;
                    blue = 78;
                }
                else if (x % 20 == 0 || y % 20 == 0) {
                    red = 36;
                    green = 45;
                    blue = 54;
                }
                putPixel(pixels, x, y, red, green, blue, 255);
            }
        }

        drawRect(pixels, 5, 5, TEXTURE_WIDTH - 10, TEXTURE_HEIGHT - 10, 226, 232, 238, 2);
        fillRect(pixels, 10, TEXTURE_HEIGHT - 38, TEXTURE_WIDTH - 20, 28, 36, 48, 62);
        drawText(pixels, "DYNAMIC TEXTURE", 24, TEXTURE_HEIGHT - 30, 3, 238, 242, 232);

        fillRect(pixels, 14, 116, 136, 20, 40, 52, 66);
        drawText(pixels, "FRAME", 20, 121, 2, 166, 214, 255);
        drawNumber4(pixels, frameValue, 92, 121, 2, 255, 232, 112);

        drawText(pixels, "RGB UPLOAD", 20, 95, 2, 210, 218, 226);
        drawChannelRow(pixels, "R", 20, 78, 236, 76, 88);
        drawChannelRow(pixels, "G", 20, 58, 86, 218, 132);
        drawChannelRow(pixels, "B", 20, 38, 80, 150, 246);

        drawText(pixels, "TOP ROW", 190, 121, 2, 160, 236, 190);
        drawText(pixels, "BOT ROW", 190, 14, 2, 255, 214, 120);

        drawRect(pixels, 44, 22, 232, 9, 196, 204, 214, 1);
        fillRect(pixels, 46, 24, progress, 5, 255, 224, 96);
        fillRect(pixels, scanX - 1, 34, 5, 70, 24, 28, 32);
        fillRect(pixels, scanX, 35, 3, 68, 255, 224, 96);
        drawText(pixels, "SCAN", 20, 15, 1, 255, 224, 96);

        pixels.position(0);
        pixels.limit(pixels.capacity());
    }

    private static void drawChannelRow(ByteBuffer pixels, String label, int x, int y,
            int red, int green, int blue) {
        drawText(pixels, label, x, y, 2, red, green, blue);
        fillRect(pixels, x + 24, y + 2, 228, 9, red, green, blue);
        fillRect(pixels, x + 24, y + 12, 228, 2, 232, 236, 240);
    }

    private static void drawNumber4(ByteBuffer pixels, int value, int x, int y, int scale,
            int red, int green, int blue) {
        int cursorX = x;
        int divisor = 1000;
        while (divisor > 0) {
            int digit = value / divisor;
            drawGlyph(pixels, (char)('0' + digit), cursorX, y, scale, red, green, blue);
            value -= digit * divisor;
            divisor /= 10;
            cursorX += scale * 6;
        }
    }

    private static void drawRect(ByteBuffer pixels, int x, int y, int width, int height,
            int red, int green, int blue, int thickness) {
        fillRect(pixels, x, y, width, thickness, red, green, blue);
        fillRect(pixels, x, y + height - thickness, width, thickness, red, green, blue);
        fillRect(pixels, x, y, thickness, height, red, green, blue);
        fillRect(pixels, x + width - thickness, y, thickness, height, red, green, blue);
    }

    private static void drawText(ByteBuffer pixels, String text, int x, int y, int scale,
            int red, int green, int blue) {
        int cursorX = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                cursorX += scale * 4;
            }
            else {
                drawGlyph(pixels, c, cursorX, y, scale, red, green, blue);
                cursorX += scale * 6;
            }
        }
    }

    private static void drawGlyph(ByteBuffer pixels, char c, int x, int y, int scale,
            int red, int green, int blue) {
        for (int row = 0; row < 7; row++) {
            int bits = glyphRow(c, row);
            for (int col = 0; col < 5; col++) {
                if ((bits & (1 << (4 - col))) != 0) {
                    fillRect(pixels, x + col * scale, y + (6 - row) * scale,
                            scale, scale, red, green, blue);
                }
            }
        }
    }

    private static int glyphRow(char c, int row) {
        long glyph = switch (c) {
            case 'A' -> 0b01110_10001_10001_11111_10001_10001_10001L;
            case 'B' -> 0b11110_10001_10001_11110_10001_10001_11110L;
            case 'C' -> 0b01111_10000_10000_10000_10000_10000_01111L;
            case 'D' -> 0b11110_10001_10001_10001_10001_10001_11110L;
            case 'E' -> 0b11111_10000_10000_11110_10000_10000_11111L;
            case 'F' -> 0b11111_10000_10000_11110_10000_10000_10000L;
            case 'G' -> 0b01111_10000_10000_10111_10001_10001_01111L;
            case 'I' -> 0b11111_00100_00100_00100_00100_00100_11111L;
            case 'L' -> 0b10000_10000_10000_10000_10000_10000_11111L;
            case 'M' -> 0b10001_11011_10101_10101_10001_10001_10001L;
            case 'N' -> 0b10001_11001_10101_10011_10001_10001_10001L;
            case 'O', '0' -> 0b01110_10001_10011_10101_11001_10001_01110L;
            case 'P' -> 0b11110_10001_10001_11110_10000_10000_10000L;
            case 'R' -> 0b11110_10001_10001_11110_10100_10010_10001L;
            case 'S' -> 0b01111_10000_10000_01110_00001_00001_11110L;
            case 'T' -> 0b11111_00100_00100_00100_00100_00100_00100L;
            case 'U' -> 0b10001_10001_10001_10001_10001_10001_01110L;
            case 'W' -> 0b10001_10001_10001_10101_10101_11011_10001L;
            case 'X' -> 0b10001_10001_01010_00100_01010_10001_10001L;
            case 'Y' -> 0b10001_10001_01010_00100_00100_00100_00100L;
            case '1' -> 0b00100_01100_00100_00100_00100_00100_01110L;
            case '2' -> 0b01110_10001_00001_00010_00100_01000_11111L;
            case '3' -> 0b11110_00001_00001_01110_00001_00001_11110L;
            case '4' -> 0b10010_10010_10010_11111_00010_00010_00010L;
            case '5' -> 0b11111_10000_10000_11110_00001_00001_11110L;
            case '6' -> 0b01110_10000_10000_11110_10001_10001_01110L;
            case '7' -> 0b11111_00001_00010_00100_01000_01000_01000L;
            case '8' -> 0b01110_10001_10001_01110_10001_10001_01110L;
            case '9' -> 0b01110_10001_10001_01111_00001_00001_01110L;
            default -> 0b11111_10001_00010_00100_00100_00000_00100L;
        };
        return (int)((glyph >> ((6 - row) * 5)) & 0b11111L);
    }

    private static void fillRect(ByteBuffer pixels, int x, int y, int width, int height,
            int red, int green, int blue) {
        int minX = Math.max(0, x);
        int minY = Math.max(0, y);
        int maxX = Math.min(TEXTURE_WIDTH, x + width);
        int maxY = Math.min(TEXTURE_HEIGHT, y + height);
        for (int py = minY; py < maxY; py++) {
            for (int px = minX; px < maxX; px++) {
                putPixel(pixels, px, py, red, green, blue, 255);
            }
        }
    }

    private static void putPixel(ByteBuffer pixels, int x, int y, int red, int green, int blue, int alpha) {
        int row = TEXTURE_HEIGHT - 1 - y;
        int index = (row * TEXTURE_WIDTH + x) * 4;
        pixels.put(index, (byte)red);
        pixels.put(index + 1, (byte)green);
        pixels.put(index + 2, (byte)blue);
        pixels.put(index + 3, (byte)alpha);
    }
}
