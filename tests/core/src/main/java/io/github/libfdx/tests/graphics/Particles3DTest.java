package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.FramebufferCapture;
import io.github.libfdx.testsupport.graphics.ParticleAnchorFixture;
import io.github.libfdx.testsupport.graphics.ParticleSolidOcclusionFixture;
import io.github.libfdx.testsupport.graphics.ParticleTestTiming;
import io.github.libfdx.testsupport.graphics.TestCameraControllers;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.controller.OrbitCameraController3D;
import io.github.libfdx.graphics.g3d.ParticleEmitter3D;
import io.github.libfdx.graphics.g3d.ParticlePresets3D;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.graphics.particles.ParticleVolume;
import io.github.libfdx.graphics.particles.ParticleSolidRenderer;
import io.github.libfdx.graphics.particles.ParticleVolumeRenderer;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.ClipDepthRange;
import io.github.libfdx.testsupport.TestFpsLogger;

/** 3D particles deposited into a density field and integrated without sprite artwork. */
public final class Particles3DTest extends ApplicationAdapter {
    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private ParticleEmitter3D fire, smoke, sparks, snow;
    private ParticleVolume volume, smokeVolume;
    private ParticleSolidRenderer solids;
    private ParticleVolumeRenderer renderer;
    private Camera camera;
    private OrbitCameraController3D controller;
    private ModelBatch models;
    private Model floor;
    private DefaultModelInstance floorInstance;
    private final Matrix4 inverseViewProjection = new Matrix4();
    private final ParticleTestTiming timing = new ParticleTestTiming();
    private float time = 3;
    private long frames;
    private String capture;
    private String occlusionFixture;
    private String solidOcclusion;
    private boolean frozen;
    private boolean anchorFixture;
    private long captureFrame;
    private boolean captured, created;

    public Particles3DTest(long exitAfterFrames) { this.exitAfterFrames = exitAfterFrames; }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app(); display = fdx.displays().main(); graphics = fdx.graphics().main(); logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "Particles3DTest");
        occlusionFixture = System.getProperty("libfdx.test.particleOcclusion", "");
        solidOcclusion = System.getProperty("libfdx.test.particleSolidOcclusion", "");
        frozen = Boolean.getBoolean("libfdx.test.particlesFrozen");
        anchorFixture = Boolean.getBoolean("libfdx.test.particleAnchors");
        volume = new ParticleVolume(96, 128, 64).bounds(-2.15f, -0.55f, -2.05f, 1.8f, 2.5f, 1.8f);
        smokeVolume = new ParticleVolume(64, 96, 48).bounds(-1.15f, -0.55f, -2.15f, 2.3f, 3.1f, 2.2f);
        if (!occlusionFixture.isEmpty()) {
            volume.bounds(-0.8f, -0.8f, -0.8f, 1.6f, 1.6f, 1.6f);
            smokeVolume.bounds(-0.8f, -0.8f, 0.2f, 1.6f, 1.6f, 1.6f);
        }
        if (!solidOcclusion.isEmpty()) {
            volume.bounds(-3.3f,-0.8f,-1.2f,6.6f,1.6f,2.4f);
            smokeVolume.bounds(-3.3f,-0.8f,-1.2f,6.6f,1.6f,2.4f);
        }
        renderer = occlusionFixture.endsWith("swapped")
                ? new ParticleVolumeRenderer(graphics, smokeVolume, volume).steps(160).density(5)
                : new ParticleVolumeRenderer(graphics, volume, smokeVolume).steps(160).density(5);
        solids = new ParticleSolidRenderer(graphics, solidOcclusion.startsWith("stack") ? 1 : 512, anchorFixture ? null : renderer);
        fire = ParticlePresets3D.volumetricFire(800, 1).seed(0x5EED).position(-1.25f, -0.34f, -1.18f);
        smoke = ParticlePresets3D.smoke(240, 1).seed(0x1234).position(-0.25f, -0.34f, -1.18f)
                .emissionRate(65).turbulence(0.22f, 4);
        sparks = ParticlePresets3D.sparks(100, 1).seed(0x5678).position(0.7f, -0.34f, -1.18f);
        snow = ParticlePresets3D.snow(160, 1).seed(0xABCD).position(1.65f, 1.7f, -1.18f)
                .spawnArea(0.6f, 0, 0.6f);
        for (int i = 0; i < 360; i++) simulate(1f / 120);
        camera = new Camera().projection(CameraProjection.PERSPECTIVE)
                .position(0, 0.75f, 4).direction(0, 0, -1).nearFar(0.1f, 20);
        controller = new OrbitCameraController3D(fdx.input(), camera)
                .position(0, 1.4f, 2.5f, 0, 0.45f, -1.18f)
                .autoOrbit(TestCameraControllers.autoOrbitEnabled(), 0.75f, exitAfterFrames,
                        TestCameraControllers.autoOrbitStartDegrees(), TestCameraControllers.autoOrbitDegrees());
        if ("top".equals(System.getProperty("libfdx.test.particlesView"))) {
            controller.position(0, 5.5f, -1.17f, 0, 0, -1.18f);
        }
        if ("side".equals(System.getProperty("libfdx.test.particlesView"))) {
            controller.position(4, 1.4f, -1.18f, 0, 0.45f, -1.18f);
        }
        if ("left".equals(System.getProperty("libfdx.test.particlesView"))) {
            controller.position(-4, 1.4f, -1.18f, 0, 0.45f, -1.18f);
        }
        if ("back".equals(System.getProperty("libfdx.test.particlesView"))) {
            controller.position(0, 1.4f, -5, 0, 0.45f, -1.18f);
        }
        if ("translated".equals(System.getProperty("libfdx.test.particlesView"))) {
            controller.position(1, 2, 3.5f, 1, 0.8f, -1.18f);
        }
        if (!occlusionFixture.isEmpty()) {
            controller.position(0, 0, occlusionFixture.startsWith("back") ? -4 : 4, 0, 0, 0);
        }
        if (!solidOcclusion.isEmpty()) {
            camera.projection(CameraProjection.ORTHOGRAPHIC);
            controller.position(0,0,solidOcclusion.contains("reverse") ? -4 : 4,0,0,0);
        }
        models = new ModelBatch(graphics);
        floor = new ModelBuilder(graphics).material(new Material("particle ground")
                .set(MaterialAttributes.baseColor(0.10f, 0.12f, 0.15f, 1)))
                .box("particle ground", 5, 0.06f, 3.5f);
        floorInstance = new DefaultModelInstance(floor);
        floorInstance.transform().setToTranslation(0, -0.65f, -1.2f);
        capture = System.getProperty("libfdx.test.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "90"));
        created = true;
        logger.info("Particles3DTest created world-space particle volume for " + graphics.providerId().value());
    }

    private void simulate(float delta) { fire.update(delta); smoke.update(delta); sparks.update(delta); snow.update(delta); }

    @Override
    public void render() {
        float elapsed = application.deltaTime();
        float delta = frozen || anchorFixture || !solidOcclusion.isEmpty() ? 0 : timing.advance(elapsed);
        time += delta;
        int steps = Math.max(1, (int)Math.ceil(delta * 120));
        for (int i = 0; i < steps; i++) simulate(delta / steps);
        camera.viewport(width(), height());
        if (!solidOcclusion.isEmpty()) camera.viewport(6.6f,6.6f*height()/width());
        controller.update(Math.min(elapsed, 0.1f));
        GraphicsFrame frame = graphics.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(RenderPassDescriptor
                .color(frame.colorAttachment(), LoadOp.clear(0.008f, 0.011f, 0.018f, 1), StoreOp.store())
                .depthClear(ClipDepthRange.getDefault().depthClearValue()));
        if (occlusionFixture.isEmpty() && solidOcclusion.isEmpty()) {
            models.begin(pass, camera); models.render(floorInstance); models.end();
        }
        volume.clear(); smokeVolume.clear();
        if (!solidOcclusion.isEmpty()) {
            ParticleSolidOcclusionFixture.media(solidOcclusion,volume,smokeVolume);
        } else if (occlusionFixture.isEmpty()) {
            fire.deposit(volume, ParticleVolume.Medium.FIRE);
            smoke.deposit(smokeVolume, ParticleVolume.Medium.SMOKE);
        } else {
            // Same world-space particles in every fixture; only camera/grid binding order changes.
            volume.add(0, 0, 0, 0.6f, 4, 1, ParticleVolume.Medium.FIRE);
            if (!occlusionFixture.startsWith("clear")) {
                smokeVolume.add(0, 0, 1, 0.65f, 8, 0, ParticleVolume.Medium.SMOKE);
            }
        }
        inverseViewProjection.setToMul(camera.inverseViewMatrix(), camera.inverseProjectionMatrix());
        if (!anchorFixture) renderer.draw(pass, camera.combined(), inverseViewProjection, camera.clipDepthRange(), occlusionFixture.isEmpty() && solidOcclusion.isEmpty() ? time : 0);
        solids.begin(pass, camera.combined(), camera.clipDepthRange());
        if (!solidOcclusion.isEmpty()) {
            ParticleSolidOcclusionFixture.solids(solidOcclusion,solids);
        } else if (anchorFixture) {
            ParticleAnchorFixture.draw(solids);
        } else if (occlusionFixture.isEmpty()) { drawSolids(sparks, 0.23f); drawSolids(snow, 0.18f); }
        solids.end();
        pass.end();
        if (!capture.isEmpty() && !captured && frames >= captureFrame) {
            try {
                java.nio.ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
                FramebufferCapture.writePpm(capture, width(), height(), pixels);
                if (anchorFixture) {
                    ParticleAnchorFixture.validate(camera, width(), height(), pixels);
                    logger.info("Particle anchors PASS: world projection within 2 pixels and floor occlusion");
                }
                captured = true;
                logger.info("Particles3DTest captured " + capture);
            } catch (Exception error) { throw new FdxException("Particle capture failed", error); }
        }
        frames++;
        fpsLogger.frame(elapsed, frames);
        if (exitAfterFrames > 0 && frames >= exitAfterFrames) application.requestExit();
    }

    private void drawSolids(ParticleEmitter3D emitter, float sizeFactor) {
        for (int i = 0; i < emitter.activeCount(); i++) {
            float size = emitter.size(i) * sizeFactor;
            if (size > 0) solids.add(emitter.x(i), emitter.y(i), emitter.z(i), size,
                    emitter.red(i), emitter.green(i), emitter.blue(i), emitter.alpha(i));
        }
    }

    @Override
    public void dispose() {
        if (solids != null) { solids.dispose(); solids = null; }
        if (renderer != null) { renderer.dispose(); renderer = null; }
        if (models != null) { models.dispose(); models = null; }
        if (floor != null) { floor.dispose(); floor = null; }
        if (!created || (exitAfterFrames > 0 && frames < exitAfterFrames) || (!capture.isEmpty() && !captured))
            throw new FdxException("Particle scenario did not complete");
        logger.info(timing.report());
        logger.info("Particles3DTest rendered " + frames + " frames");
    }

    private int width() { return Math.max(1, display.framebufferWidth()); }
    private int height() { return Math.max(1, display.framebufferHeight()); }
}
