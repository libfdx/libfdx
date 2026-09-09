package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.FramebufferCapture;
import io.github.libfdx.testsupport.graphics.ParticleTestTiming;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.g2d.ParticleEmitter2D;
import io.github.libfdx.graphics.g2d.ParticlePresets2D;
import io.github.libfdx.graphics.particles.ParticleVolume;
import io.github.libfdx.graphics.particles.ParticleSolidRenderer;
import io.github.libfdx.graphics.particles.ParticleVolumeRenderer;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.ClipDepthRange;
import io.github.libfdx.testsupport.TestFpsLogger;

/** 2D particles deposited into a density field and integrated without sprite artwork. */
public final class Particles2DTest extends ApplicationAdapter {
    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private ParticleEmitter2D fire, smoke, sparks, snow;
    private ParticleVolume volume, smokeVolume;
    private ParticleSolidRenderer solids;
    private ParticleVolumeRenderer renderer;
    private Camera camera;
    private final Matrix4 inverseViewProjection = new Matrix4();
    private final ParticleTestTiming timing = new ParticleTestTiming();
    private float time = 3;
    private long frames;
    private String capture;
    private long captureFrame;
    private boolean captured, created;

    public Particles2DTest(long exitAfterFrames) { this.exitAfterFrames = exitAfterFrames; }

    @Override public void create(Fdx fdx) {
        application = fdx.app(); display = fdx.displays().main(); graphics = fdx.graphics().main(); logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "Particles2DTest");
        volume = new ParticleVolume(96, 128, 64).bounds(-2.15f, -0.55f, -2.05f, 1.8f, 2.5f, 1.8f);
        smokeVolume = new ParticleVolume(64, 96, 48).bounds(-1.15f, -0.55f, -2.15f, 2.3f, 3.1f, 2.2f);
        renderer = new ParticleVolumeRenderer(graphics, volume, smokeVolume).steps(160).density(5);
        solids = new ParticleSolidRenderer(graphics, 512, renderer);
        fire = ParticlePresets2D.volumetricFire(800, 1).seed(0x5EED).position(-1.25f, -0.34f);
        smoke = ParticlePresets2D.smoke(240, 1).seed(0x1234).position(-0.25f, -0.34f)
                .emissionRate(65).turbulence(0.22f, 4);
        sparks = ParticlePresets2D.sparks(100, 1).seed(0x5678).position(0.7f, -0.34f);
        snow = ParticlePresets2D.snow(160, 1).seed(0xABCD).position(1.65f, 1.7f)
                .spawnArea(0.6f, 0);
        for (int i = 0; i < 360; i++) simulate(1f / 120);
        camera = new Camera().projection(CameraProjection.ORTHOGRAPHIC)
                .position(0, 0.75f, 4).direction(0, 0, -1).nearFar(0.1f, 20);
        capture = System.getProperty("libfdx.test.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "90"));
        created = true;
        logger.info("Particles2DTest created world-space particle volume for " + graphics.providerId().value());
    }

    private void simulate(float delta) { fire.update(delta); smoke.update(delta); sparks.update(delta); snow.update(delta); }

    @Override public void render() {
        float elapsed = application.deltaTime();
        float delta = timing.advance(elapsed);
        time += delta;
        int steps = Math.max(1, (int)Math.ceil(delta * 120));
        for (int i = 0; i < steps; i++) simulate(delta / steps);
        camera.viewport(4.8f, 4.8f * height() / width());

        GraphicsFrame frame = graphics.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(RenderPassDescriptor
                .color(frame.colorAttachment(), LoadOp.clear(0.008f, 0.011f, 0.018f, 1), StoreOp.store())
                .depthClear(ClipDepthRange.getDefault().depthClearValue()));

        volume.clear(); smokeVolume.clear();
        fire.deposit(volume, ParticleVolume.Medium.FIRE, -1.18f);
        smoke.deposit(smokeVolume, ParticleVolume.Medium.SMOKE, -1.18f);
        inverseViewProjection.setToMul(camera.inverseViewMatrix(), camera.inverseProjectionMatrix());
        renderer.draw(pass, camera.combined(), inverseViewProjection, camera.clipDepthRange(), time);
        solids.begin(pass, camera.combined(), camera.clipDepthRange());
        drawSolids(sparks, 0.23f); drawSolids(snow, 0.18f);
        solids.end();
        pass.end();
        if (!capture.isEmpty() && !captured && frames >= captureFrame) {
            try {
                FramebufferCapture.writePpm(capture, width(), height(), FramebufferCapture.readPixelsRgba8(graphics));
                captured = true;
                logger.info("Particles2DTest captured " + capture);
            } catch (Exception error) { throw new FdxException("Particle capture failed", error); }
        }
        frames++;
        fpsLogger.frame(elapsed, frames);
        if (exitAfterFrames > 0 && frames >= exitAfterFrames) application.requestExit();
    }

    private void drawSolids(ParticleEmitter2D emitter, float sizeFactor) {
        for (int i = 0; i < emitter.activeCount(); i++) {
            float size = emitter.size(i) * sizeFactor;
            if (size > 0) solids.add(emitter.x(i), emitter.y(i), -1.18f, size,
                    emitter.red(i), emitter.green(i), emitter.blue(i), emitter.alpha(i));
        }
    }

    @Override public void dispose() {
        if (solids != null) { solids.dispose(); solids = null; }
        if (renderer != null) { renderer.dispose(); renderer = null; }
        if (!created || (exitAfterFrames > 0 && frames < exitAfterFrames) || (!capture.isEmpty() && !captured))
            throw new FdxException("Particle scenario did not complete");
        logger.info(timing.report());
        logger.info("Particles2DTest rendered " + frames + " frames");
    }

    private int width() { return Math.max(1, display.framebufferWidth()); }
    private int height() { return Math.max(1, display.framebufferHeight()); }
}
