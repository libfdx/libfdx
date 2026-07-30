package io.github.libfdx.samples.g2d.platformer;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.samples.g2d.platformer.input.BackendPlatformerInput;
import io.github.libfdx.samples.g2d.platformer.render.PlatformerFramebufferCapture;
import io.github.libfdx.samples.g2d.platformer.render.PlatformerRenderer;
import io.github.libfdx.samples.g2d.platformer.render.PlatformerTextures;

/**
 * Runs the platformer example.
 *
 * @author xpenatan
 */
public final class PlatformerApplication extends ApplicationAdapter {
    private final long exitAfterFrames;
    private Application application;
    private GraphicsContext graphics;
    private Logger logger;
    private SpriteBatch batch;
    private PlatformerTextures textures;
    private PlatformerGame game;
    private PlatformerRenderer renderer;
    private String capturePath;
    private long captureFrame;
    private boolean captureAttempted;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates a platformer application.
     */
    public PlatformerApplication() {
        this(0L);
    }

    /**
     * Creates a platformer application.
     *
     * @param exitAfterFrames the exit after frames
     */
    public PlatformerApplication(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        if (fdx == null) {
            throw new IllegalArgumentException("fdx cannot be null.");
        }
        String nextCapturePath = System.getProperty("libfdx.sample.capture", "");
        long nextCaptureFrame = Long.parseLong(System.getProperty("libfdx.sample.captureFrame", "2"));
        Application nextApplication = fdx.app();
        Logger nextLogger = fdx.logger();
        GraphicsContext nextGraphics = fdx.graphics().main();
        PlatformerGame nextGame = PlatformerLevel.create(new BackendPlatformerInput(fdx.input()));
        SpriteBatch nextBatch = new SpriteBatch(nextGraphics);
        PlatformerTextures nextTextures = null;
        try {
            nextTextures = PlatformerTextures.load(fdx.files(), nextGraphics);
            PlatformerRenderer nextRenderer = new PlatformerRenderer(nextBatch, nextTextures.regions());
            nextLogger.info("Platformer example created with graphics provider "
                    + nextGraphics.providerId().value());

            application = nextApplication;
            logger = nextLogger;
            graphics = nextGraphics;
            batch = nextBatch;
            textures = nextTextures;
            game = nextGame;
            renderer = nextRenderer;
            capturePath = nextCapturePath;
            captureFrame = nextCaptureFrame;
            captureAttempted = false;
            captured = false;
            renderedFrames = 0L;
        } catch (RuntimeException | Error failure) {
            if (nextTextures != null) {
                try {
                    nextTextures.dispose();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (failure != cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            try {
                nextBatch.dispose();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure != cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    @Override
    public void render() {
        if (application == null || game == null || renderer == null) {
            return;
        }
        game.update(application.deltaTime());
        GraphicsFrame frame = graphics.currentFrame();
        renderer.render(frame, frame.colorAttachment(), frame.width(), frame.height(), game);
        if (capturePath != null && capturePath.length() > 0 && !captured && renderedFrames >= captureFrame) {
            captureAttempted = true;
            captureFrame(capturePath);
            captured = true;
        }
        renderedFrames++;
        if (exitAfterFrames > 0L && application.frameId() >= exitAfterFrames) {
            application.requestExit();
        }
    }

    @Override
    public void dispose() {
        Throwable failure = null;
        if (capturePath != null && capturePath.length() > 0 && !captured && !captureAttempted) {
            failure = new FdxException("Platformer did not capture framebuffer to " + capturePath);
        }
        renderer = null;
        game = null;
        if (textures != null) {
            PlatformerTextures disposedTextures = textures;
            textures = null;
            try {
                disposedTextures.dispose();
            } catch (RuntimeException | Error disposeFailure) {
                failure = aggregateFailure(failure, disposeFailure);
            }
        }
        if (batch != null) {
            SpriteBatch disposedBatch = batch;
            batch = null;
            try {
                disposedBatch.dispose();
            } catch (RuntimeException | Error disposeFailure) {
                failure = aggregateFailure(failure, disposeFailure);
            }
        }
        if (logger != null) {
            try {
                logger.info("Platformer example disposed");
            } catch (RuntimeException | Error logFailure) {
                failure = aggregateFailure(failure, logFailure);
            }
        }
        application = null;
        graphics = null;
        logger = null;
        capturePath = null;
        captureFrame = 0L;
        captureAttempted = false;
        captured = false;
        renderedFrames = 0L;
        rethrowFailure(failure);
    }

    private void captureFrame(String path) {
        try {
            PlatformerFramebufferCapture.writePpm(path, graphics.currentFrame().frameBuffer().width(),
                    graphics.currentFrame().frameBuffer().height(),
                    graphics.currentFrame().frameBuffer().readPixelsRgba8());
            logger.info("Platformer captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture platformer framebuffer", e);
        }
    }

    private static Throwable aggregateFailure(Throwable failure, Throwable next) {
        if (failure == null) {
            return next;
        }
        if (failure != next) {
            failure.addSuppressed(next);
        }
        return failure;
    }

    private static void rethrowFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
