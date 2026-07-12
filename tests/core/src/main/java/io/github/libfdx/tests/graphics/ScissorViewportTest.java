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
 * Runs the moving scissor viewport parity test.
 *
 * @author xpenatan
 */
public final class ScissorViewportTest extends GraphicsParityTest {
    private static final int TEXTURE_WIDTH = 800;
    private static final int TEXTURE_HEIGHT = 500;
    private static final int SCISSOR_REFERENCE_X = 100;
    private static final int SCISSOR_REFERENCE_Y = 100;
    private static final int SCISSOR_WIDTH = 300;
    private static final int SCISSOR_HEIGHT = 200;
    private static final int VIEWPORT_MARGIN_X = 40;
    private static final int VIEWPORT_MARGIN_Y = 30;
    private static final int FLOATS_PER_VERTEX = 8;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final int VERTEX_COUNT = 6;
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8),
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 16));
    private static final String SHADER_SOURCE = """
            struct VertexInput {
                @location(0) position : vec2f,
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
                output.position = vec4f(input.position, 0.0, 1.0);
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

    private final RenderPassDescriptor passDescriptor = new RenderPassDescriptor()
            .label("scissor viewport pass")
            .colorLoadOp(LoadOp.clear(0.02f, 0.035f, 0.06f, 1.0f))
            .colorStoreOp(StoreOp.store());
    private ShaderModule shaderModule;
    private RenderPipeline pipeline;
    private Buffer backgroundBuffer;
    private Texture background;
    private ByteBuffer backgroundVertices;
    private int scissorX = 100;
    private int scissorY = 100;
    private int scissorDx = 1;
    private int scissorDy = 1;
    private int lastFrameWidth;
    private int lastFrameHeight;

    /**
     * Creates a scissor parity test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public ScissorViewportTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "ScissorViewportTest");
        background = graphics.device().createTexture(TextureDescriptor.rgba8(
                "scissor viewport diagnostic texture", TEXTURE_WIDTH, TEXTURE_HEIGHT));
        graphics.device().writeTexture(background, diagnosticTexturePixels());
        backgroundBuffer = graphics.device().createBuffer(BufferDescriptor.vertex(
                "scissor viewport background vertices", VERTEX_COUNT * BYTES_PER_VERTEX));
        backgroundVertices = ByteBuffer.allocateDirect(VERTEX_COUNT * BYTES_PER_VERTEX)
                .order(ByteOrder.nativeOrder());
        shaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("scissor viewport textured shader", SHADER_SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shaderModule, graphics.surfaceFormat())
                .label("scissor viewport textured pipeline")
                .vertexLayout(VERTEX_LAYOUT)
                .sampledTextureCount(1)
                .depthWriteEnabled(false));
        markCreated();
        logger.info("ScissorViewportTest generated " + background.width() + "x" + background.height()
                + " diagnostic texture");
    }

    @Override
    public void render() {
        GraphicsFrame frame = graphics.currentFrame();
        int width = frame.width() > 0 ? frame.width() : framebufferWidth();
        int height = frame.height() > 0 ? frame.height() : framebufferHeight();
        updateBackgroundVertices(width, height);

        int scissorWidth = Math.min(SCISSOR_WIDTH, width);
        int scissorHeight = Math.min(SCISSOR_HEIGHT, height);
        clampScissor(width, height, scissorWidth, scissorHeight);

        passDescriptor.colorAttachment(frame.colorAttachment());
        RenderPass pass = frame.commandEncoder().beginRenderPass(passDescriptor);
        pass.setPipeline(pipeline);
        pass.setTexture(0, background);
        pass.setVertexBuffer(backgroundBuffer);
        int viewportX = Math.min(VIEWPORT_MARGIN_X, Math.max(0, width / 8));
        int viewportY = Math.min(VIEWPORT_MARGIN_Y, Math.max(0, height / 8));
        pass.setViewport(viewportX, viewportY, Math.max(1, width - viewportX * 2),
                Math.max(1, height - viewportY * 2));
        pass.setScissor(scissorX, scissorY, scissorWidth, scissorHeight);
        pass.draw(VERTEX_COUNT, 1, 0, 0);
        pass.end();

        moveScissor(width, height, scissorWidth, scissorHeight);
        finishFrame();
    }

    @Override
    public void dispose() {
        dispose(pipeline);
        dispose(shaderModule);
        dispose(backgroundBuffer);
        dispose(background);
        verifyDisposed();
    }

    private void updateBackgroundVertices(int frameWidth, int frameHeight) {
        if (frameWidth == lastFrameWidth && frameHeight == lastFrameHeight) {
            return;
        }
        lastFrameWidth = frameWidth;
        lastFrameHeight = frameHeight;
        float left = -1.0f;
        float bottom = -1.0f;
        float right = -1.0f + 2.0f * background.width() / frameWidth;
        float top = -1.0f + 2.0f * background.height() / frameHeight;
        backgroundVertices.clear();
        putVertex(backgroundVertices, left, bottom, 0.0f, 1.0f);
        putVertex(backgroundVertices, right, bottom, 1.0f, 1.0f);
        putVertex(backgroundVertices, right, top, 1.0f, 0.0f);
        putVertex(backgroundVertices, left, bottom, 0.0f, 1.0f);
        putVertex(backgroundVertices, right, top, 1.0f, 0.0f);
        putVertex(backgroundVertices, left, top, 0.0f, 0.0f);
        backgroundVertices.flip();
        graphics.device().writeBuffer(backgroundBuffer, backgroundVertices);
    }

    private void clampScissor(int frameWidth, int frameHeight, int scissorWidth, int scissorHeight) {
        int maxX = Math.max(0, frameWidth - scissorWidth);
        int maxY = Math.max(0, frameHeight - scissorHeight);
        if (scissorX < 0) {
            scissorX = 0;
            scissorDx = 1;
        }
        else if (scissorX > maxX) {
            scissorX = maxX;
            scissorDx = -1;
        }
        if (scissorY < 0) {
            scissorY = 0;
            scissorDy = 1;
        }
        else if (scissorY > maxY) {
            scissorY = maxY;
            scissorDy = -1;
        }
    }

    private void moveScissor(int frameWidth, int frameHeight, int scissorWidth, int scissorHeight) {
        scissorX += scissorDx;
        scissorY += scissorDy;
        if (scissorX == 0) {
            scissorDx = 1;
        }
        else if (scissorX + scissorWidth >= frameWidth) {
            scissorDx = -1;
        }
        if (scissorY == 0) {
            scissorDy = 1;
        }
        else if (scissorY + scissorHeight >= frameHeight) {
            scissorDy = -1;
        }
    }

    private static void putVertex(ByteBuffer vertices, float x, float y, float u, float v) {
        vertices.putFloat(x);
        vertices.putFloat(y);
        vertices.putFloat(u);
        vertices.putFloat(v);
        vertices.putFloat(1.0f);
        vertices.putFloat(1.0f);
        vertices.putFloat(1.0f);
        vertices.putFloat(1.0f);
    }

    private static ByteBuffer diagnosticTexturePixels() {
        ByteBuffer pixels = rgba8(TEXTURE_WIDTH, TEXTURE_HEIGHT);
        for (int y = 0; y < TEXTURE_HEIGHT; y++) {
            for (int x = 0; x < TEXTURE_WIDTH; x++) {
                int checker = ((x / 100) + (y / 100)) & 1;
                int band = ((x + y) / 80) & 3;
                int red = checker == 0 ? 220 : 196;
                int green = checker == 0 ? 225 : 205;
                int blue = checker == 0 ? 226 : 214;
                if (band == 1) {
                    red -= 14;
                    green += 8;
                }
                else if (band == 2) {
                    green -= 10;
                    blue += 12;
                }
                else if (band == 3) {
                    red += 8;
                    blue -= 10;
                }
                if (x % 100 == 0 || y % 100 == 0) {
                    red = 52;
                    green = 58;
                    blue = 64;
                }
                else if (x % 50 == 0 || y % 50 == 0) {
                    red = 156;
                    green = 164;
                    blue = 170;
                }
                putPixel(pixels, x, y, red, green, blue, 255);
            }
        }

        fillRect(pixels, 0, 0, 94, 94, 116, 35, 45);
        fillRect(pixels, TEXTURE_WIDTH - 94, 0, 94, 94, 24, 126, 74);
        fillRect(pixels, 0, TEXTURE_HEIGHT - 94, 94, 94, 212, 178, 36);
        fillRect(pixels, TEXTURE_WIDTH - 94, TEXTURE_HEIGHT - 94, 94, 94, 106, 78, 158);
        drawText(pixels, "LL", 18, 32, 5, 255, 255, 255);
        drawText(pixels, "LR", TEXTURE_WIDTH - 76, 32, 5, 255, 255, 255);
        drawText(pixels, "UL", 18, TEXTURE_HEIGHT - 62, 5, 18, 22, 26);
        drawText(pixels, "UR", TEXTURE_WIDTH - 76, TEXTURE_HEIGHT - 62, 5, 255, 255, 255);

        fillRect(pixels, SCISSOR_REFERENCE_X, 0, 5, TEXTURE_HEIGHT, 24, 28, 32);
        fillRect(pixels, 0, SCISSOR_REFERENCE_Y, TEXTURE_WIDTH, 5, 24, 28, 32);
        drawRect(pixels, SCISSOR_REFERENCE_X, SCISSOR_REFERENCE_Y, SCISSOR_WIDTH, SCISSOR_HEIGHT,
                250, 146, 36, 8);
        drawRect(pixels, SCISSOR_REFERENCE_X + 18, SCISSOR_REFERENCE_Y + 18,
                SCISSOR_WIDTH - 36, SCISSOR_HEIGHT - 36, 24, 28, 32, 5);
        fillRect(pixels, 120, 232, 252, 58, 238, 242, 232);
        fillRect(pixels, 120, 154, 252, 82, 238, 242, 232);
        drawText(pixels, "LIBFDX", 134, 250, 5, 24, 28, 32);
        drawText(pixels, "SCISSOR", 134, 198, 4, 24, 28, 32);
        drawText(pixels, "300X200", 134, 174, 4, 24, 28, 32);

        drawRect(pixels, 12, 12, 72, 72, 255, 255, 255, 4);
        drawText(pixels, "0 0", 18, 20, 3, 255, 255, 255);
        pixels.position(0);
        pixels.limit(pixels.capacity());
        return pixels;
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
                continue;
            }
            String[] glyph = glyph(c);
            for (int row = 0; row < glyph.length; row++) {
                String line = glyph[row];
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) == '1') {
                        fillRect(pixels, cursorX + col * scale, y + (glyph.length - 1 - row) * scale,
                                scale, scale, red, green, blue);
                    }
                }
            }
            cursorX += scale * 6;
        }
    }

    private static String[] glyph(char c) {
        return switch (c) {
            case 'B' -> new String[] {"11110", "10001", "10001", "11110", "10001", "10001", "11110"};
            case 'C' -> new String[] {"01111", "10000", "10000", "10000", "10000", "10000", "01111"};
            case 'D' -> new String[] {"11110", "10001", "10001", "10001", "10001", "10001", "11110"};
            case 'F' -> new String[] {"11111", "10000", "10000", "11110", "10000", "10000", "10000"};
            case 'I' -> new String[] {"11111", "00100", "00100", "00100", "00100", "00100", "11111"};
            case 'L' -> new String[] {"10000", "10000", "10000", "10000", "10000", "10000", "11111"};
            case 'O', '0' -> new String[] {"01110", "10001", "10011", "10101", "11001", "10001", "01110"};
            case 'R' -> new String[] {"11110", "10001", "10001", "11110", "10100", "10010", "10001"};
            case 'S' -> new String[] {"01111", "10000", "10000", "01110", "00001", "00001", "11110"};
            case 'U' -> new String[] {"10001", "10001", "10001", "10001", "10001", "10001", "01110"};
            case 'X' -> new String[] {"10001", "10001", "01010", "00100", "01010", "10001", "10001"};
            case '2' -> new String[] {"01110", "10001", "00001", "00010", "00100", "01000", "11111"};
            case '3' -> new String[] {"11110", "00001", "00001", "01110", "00001", "00001", "11110"};
            default -> new String[] {"11111", "10001", "00010", "00100", "00100", "00000", "00100"};
        };
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
