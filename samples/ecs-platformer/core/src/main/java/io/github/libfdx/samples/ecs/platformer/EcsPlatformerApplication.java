package io.github.libfdx.samples.ecs.platformer;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.ecs.World;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.samples.ecs.platformer.input.BackendPlatformerInput;
import io.github.libfdx.samples.ecs.platformer.render.EcsPlatformerFramebufferCapture;
import io.github.libfdx.samples.ecs.platformer.render.PlatformerTextures;
import io.github.libfdx.samples.ecs.platformer.system.RenderSystem;
import io.github.libfdx.samples.ecs.platformer.world.PlatformerWorldFactory;

/**
 * Runs the ECS platformer example.
 *
 * @author xpenatan
 */
public final class EcsPlatformerApplication extends ApplicationAdapter {
    private final long exitAfterFrames;
    private Application application;
    private GraphicsContext graphics;
    private Logger logger;
    private SpriteBatch batch;
    private PlatformerTextures textures;
    private World world;
    private String capturePath;
    private long captureFrame;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates an ECS platformer application.
     */
    public EcsPlatformerApplication() {
        this(0L);
    }

    /**
     * Creates an ECS platformer application.
     *
     * @param exitAfterFrames the exit after frames
     */
    public EcsPlatformerApplication(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        logger = fdx.logger();
        graphics = fdx.graphics().main();
        batch = new SpriteBatch(graphics);
        textures = PlatformerTextures.load(fdx, graphics);
        TextureRegion[] regions = textures.regions();
        world = PlatformerWorldFactory.create(new BackendPlatformerInput(fdx.input()),
                new RenderSystem(batch, regions));
        capturePath = System.getProperty("libfdx.sample.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.sample.captureFrame", "2"));
        logger.info("ECS platformer example created with graphics provider " + graphics.providerId().value());
    }

    @Override
    public void render() {
        if (application == null || world == null) {
            return;
        }
        world.update(application.deltaTime());
        if (capturePath != null && capturePath.length() > 0 && !captured && renderedFrames >= captureFrame) {
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
        if (world != null) {
            world.clear();
            world.flushCommands();
            world = null;
        }
        if (textures != null) {
            textures.dispose();
            textures = null;
        }
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (logger != null) {
            logger.info("ECS platformer example disposed");
        }
        if (capturePath != null && capturePath.length() > 0 && !captured) {
            throw new FdxException("ECS platformer did not capture framebuffer to " + capturePath);
        }
    }

    private void captureFrame(String path) {
        try {
            EcsPlatformerFramebufferCapture.writePpm(path, graphics.currentFrame().frameBuffer().width(),
                    graphics.currentFrame().frameBuffer().height(),
                    graphics.currentFrame().frameBuffer().readPixelsRgba8());
            logger.info("ECS platformer captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture ECS platformer framebuffer", e);
        }
    }
}
