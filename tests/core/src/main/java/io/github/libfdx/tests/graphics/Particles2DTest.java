package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.g2d.Batch2D;
import io.github.libfdx.graphics.g2d.ParticleEmitter2D;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.tests.TestFpsLogger;

import java.nio.ByteBuffer;

/**
 * Runs the 2D particle test scenario.
 *
 * @author xpenatan
 */
public final class Particles2DTest extends ApplicationAdapter {
    private static final int PARTICLE_TEXTURE_SIZE = 32;
    private static final float FIXED_DELTA_SECONDS = 1.0f / 60.0f;

    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private Batch2D batch;
    private ParticleEmitter2D emitter;
    private Texture particleTexture;
    private TextureRegion particleRegion;
    private String capturePath;
    private long captureFrame;
    private boolean created;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates a particles test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public Particles2DTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    /**
     * Initializes the application with the libFDX runtime root.
     *
     * @param fdx the libFDX runtime root
     */
    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "Particles2DTest");
        batch = new SpriteBatch(graphics);
        particleTexture = createParticleTexture();
        particleRegion = new TextureRegion(particleTexture);
        emitter = new ParticleEmitter2D(160)
                .seed(0x5EED1234)
                .position(-0.05f, -0.55f)
                .emissionRate(95.0f)
                .lifetime(1.0f, 1.65f)
                .speed(0.62f, 1.15f)
                .direction(92.0f, 54.0f)
                .gravity(0.0f, -0.72f)
                .size(0.115f, 0.17f, 0.0f, 0.02f)
                .color(1.0f, 0.78f, 0.22f, 0.92f, 0.22f, 0.52f, 1.0f, 0.0f)
                .rotation(-35.0f, 35.0f, -90.0f, 90.0f);
        emitter.emit(36);
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "28"));
        created = true;
        logger.info("Particles2DTest created fixed-capacity particle emitter for provider "
                + graphics.providerId().value());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        emitter.update(FIXED_DELTA_SECONDS);
        batch.begin(LoadOp.clear(0.015f, 0.018f, 0.035f, 1.0f));
        drawEmitterBase();
        emitter.render(particleRegion, batch);
        batch.end();

        if (capturePath != null && capturePath.length() > 0 && !captured && renderedFrames >= captureFrame) {
            captureFrame(capturePath);
            captured = true;
        }
        renderedFrames++;
        fpsLogger.frame(deltaSeconds, renderedFrames);
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (particleTexture != null) {
            particleTexture.dispose();
            particleTexture = null;
        }
        if (!created) {
            throw new FdxException("Particles2DTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("Particles2DTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        if (capturePath != null && capturePath.length() > 0 && !captured) {
            throw new FdxException("Particles2DTest did not capture framebuffer to " + capturePath);
        }
        logger.info("Particles2DTest rendered " + renderedFrames + " frames");
    }

    private void drawEmitterBase() {
        batch.color(0.12f, 0.18f, 0.32f, 1.0f);
        batch.draw(particleRegion, -0.18f, -0.68f, 0.26f, 0.06f);
        batch.color(1.0f, 0.62f, 0.18f, 0.85f);
        batch.draw(particleRegion, -0.09f, -0.62f, 0.08f, 0.08f);
        batch.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private Texture createParticleTexture() {
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8("particle sprite",
                PARTICLE_TEXTURE_SIZE, PARTICLE_TEXTURE_SIZE));
        graphics.device().writeTexture(texture, particlePixels());
        return texture;
    }

    private ByteBuffer particlePixels() {
        ByteBuffer pixels = ByteBuffer.allocateDirect(PARTICLE_TEXTURE_SIZE * PARTICLE_TEXTURE_SIZE * 4);
        float center = (PARTICLE_TEXTURE_SIZE - 1) * 0.5f;
        for (int y = 0; y < PARTICLE_TEXTURE_SIZE; y++) {
            for (int x = 0; x < PARTICLE_TEXTURE_SIZE; x++) {
                float dx = (x - center) / center;
                float dy = (y - center) / center;
                float distance = (float)Math.sqrt(dx * dx + dy * dy);
                float alpha = clamp(1.0f - distance);
                alpha = alpha * alpha * (3.0f - 2.0f * alpha);
                pixels.put((byte)255);
                pixels.put((byte)255);
                pixels.put((byte)255);
                pixels.put((byte)(int)(alpha * 255.0f));
            }
        }
        pixels.flip();
        return pixels;
    }

    private void captureFrame(String path) {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            FramebufferCapture.writePpm(path, framebufferWidth(), framebufferHeight(), pixels);
            logger.info("Particles2DTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture Particles2DTest framebuffer", e);
        }
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 640;
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 480;
    }

    private float clamp(float value) {
        if (value <= 0.0f) {
            return 0.0f;
        }
        return value >= 1.0f ? 1.0f : value;
    }
}
