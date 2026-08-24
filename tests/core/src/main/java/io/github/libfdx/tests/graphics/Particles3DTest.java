package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.controller.OrbitCameraController3D;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.g3d.BillboardRenderer3D;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment3D;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.ModelBuilder;
import io.github.libfdx.graphics.g3d.ParticleEmitter3D;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.MaterialAttributes;
import io.github.libfdx.graphics.g3d.PbrAttributes;
import io.github.libfdx.graphics.g3d.SkyboxRenderer3D;
import io.github.libfdx.math.Color;
import io.github.libfdx.tests.TestFpsLogger;

import java.nio.ByteBuffer;

/**
 * Runs the 3D particle emitter test scenario.
 *
 * @author xpenatan
 */
public final class Particles3DTest extends ApplicationAdapter {
    private static final int PARTICLE_TEXTURE_SIZE = 48;
    private static final float FIXED_DELTA_SECONDS = 1.0f / 60.0f;
    private static final Color CLEAR_COLOR = new Color(0.014f, 0.018f, 0.030f, 1.0f);

    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private SkyboxRenderer3D skybox;
    private ModelBatch batch;
    private BillboardRenderer3D billboards;
    private ParticleEmitter3D emitter;
    private Camera camera;
    private OrbitCameraController3D cameraInput;
    private Model floorModel;
    private Model emitterModel;
    private DefaultModelInstance floorInstance;
    private DefaultModelInstance emitterInstance;
    private Texture particleTexture;
    private String capturePath;
    private long captureFrame;
    private boolean created;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates a 3D particles test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public Particles3DTest(long exitAfterFrames) {
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
        fpsLogger = TestFpsLogger.create(logger, "Particles3DTest");
        skybox = new SkyboxRenderer3D(graphics)
                .zenithColor(0.04f, 0.08f, 0.22f)
                .horizonColor(0.58f, 0.42f, 0.28f)
                .nadirColor(0.014f, 0.018f, 0.030f)
                .sunColor(1.0f, 0.70f, 0.38f, 0.56f)
                .sunPosition(0.63f, 0.68f)
                .sunSize(0.12f);
        batch = new ModelBatch(graphics).environment(new Environment3D()
                .ambientColor(new Color(0.16f, 0.18f, 0.22f, 1.0f))
                .add(new DirectionalLight()
                        .direction(-0.45f, -0.78f, -0.38f)
                        .color(new Color(1.0f, 0.84f, 0.66f, 1.0f))
                        .intensity(1.35f)));
        billboards = new BillboardRenderer3D(graphics, 384);
        particleTexture = createParticleTexture();
        createModels();
        emitter = new ParticleEmitter3D(340)
                .seed(0x3D5EED)
                .position(0.0f, -0.34f, -1.18f)
                .emissionRate(170.0f)
                .lifetime(1.20f, 1.95f)
                .speed(0.60f, 1.34f)
                .direction(0.10f, 1.0f, -0.18f, 46.0f)
                .gravity(0.0f, -0.48f, 0.04f)
                .size(0.18f, 0.34f, 0.04f, 0.08f)
                .color(1.0f, 0.78f, 0.30f, 1.0f, 0.18f, 0.66f, 1.0f, 0.12f)
                .rotation(-30.0f, 30.0f, -115.0f, 115.0f);
        emitter.emit(112);
        camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(58.0f)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 32.0f);
        cameraInput = new OrbitCameraController3D(fdx.input(), camera)
                .position(3.45f, 2.18f, 4.05f, 0.0f, 0.26f, -1.16f)
                .autoOrbit(TestCameraControllers.autoOrbitEnabled(), 0.75f, exitAfterFrames,
                        TestCameraControllers.autoOrbitStartDegrees(), TestCameraControllers.autoOrbitDegrees());
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "44"));
        created = true;
        logger.info("Particles3DTest created fixed-capacity 3D particle emitter for provider "
                + graphics.providerId().value());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        emitter.update(FIXED_DELTA_SECONDS);
        camera.viewport(framebufferWidth(), framebufferHeight());
        cameraInput.update(deltaSeconds);

        GraphicsFrame frame = graphics.currentFrame();
        skybox.begin(LoadOp.clear(CLEAR_COLOR.red(), CLEAR_COLOR.green(), CLEAR_COLOR.blue(), CLEAR_COLOR.alpha()));
        skybox.draw(camera);
        skybox.end();

        RenderPass pass = frame.commandEncoder().beginRenderPass(RenderPassDescriptor
                .color(frame.colorAttachment(), LoadOp.load(), StoreOp.store())
                .depthClear(1.0f)
                .label("particles 3d test pass"));
        batch.begin(pass, camera);
        batch.render(floorInstance);
        batch.render(emitterInstance);
        batch.end();

        billboards.begin(pass);
        emitter.render(particleTexture, camera, billboards);
        billboards.end();
        pass.end();

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
        if (billboards != null) {
            billboards.dispose();
            billboards = null;
        }
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (skybox != null) {
            skybox.dispose();
            skybox = null;
        }
        if (floorModel != null) {
            floorModel.dispose();
            floorModel = null;
        }
        if (emitterModel != null) {
            emitterModel.dispose();
            emitterModel = null;
        }
        if (particleTexture != null) {
            particleTexture.dispose();
            particleTexture = null;
        }
        if (!created) {
            throw new FdxException("Particles3DTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("Particles3DTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        if (capturePath != null && capturePath.length() > 0 && !captured) {
            throw new FdxException("Particles3DTest did not capture framebuffer to " + capturePath);
        }
        logger.info("Particles3DTest rendered " + renderedFrames + " frames");
    }

    private void createModels() {
        ModelBuilder builder = new ModelBuilder(graphics);
        floorModel = builder
                .material(new Material("particles 3d floor material")
                        .set(MaterialAttributes.baseColor(
                                0.20f, 0.28f, 0.30f, 1.0f))
                        .set(PbrAttributes.roughnessFactor(0.94f)))
                .box("particles 3d floor", 5.0f, 0.08f, 4.4f);
        emitterModel = builder
                .material(new Material("particles 3d emitter material")
                        .set(MaterialAttributes.baseColor(
                                0.70f, 0.25f, 0.20f, 1.0f))
                        .set(PbrAttributes.roughnessFactor(0.78f)))
                .box("particles 3d emitter", 0.38f, 0.22f, 0.38f);
        floorInstance = new DefaultModelInstance(floorModel);
        floorInstance.transform().setToTranslation(0.0f, -0.70f, -1.25f);
        emitterInstance = new DefaultModelInstance(emitterModel);
        emitterInstance.transform().setToTranslation(0.0f, -0.48f, -1.18f)
                .rotateY(0.42f);
    }

    private Texture createParticleTexture() {
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8("3d particle sprite",
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
                float core = clamp(1.0f - distance * 1.65f);
                float halo = clamp(1.0f - distance);
                float alpha = halo * halo * (3.0f - 2.0f * halo) * 0.66f
                        + core * core * (3.0f - 2.0f * core) * 0.34f;
                pixels.put((byte)255);
                pixels.put((byte)255);
                pixels.put((byte)255);
                pixels.put((byte)(int)(clamp(alpha) * 255.0f));
            }
        }
        pixels.flip();
        return pixels;
    }

    private void captureFrame(String path) {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            FramebufferCapture.writePpm(path, framebufferWidth(), framebufferHeight(), pixels);
            logger.info("Particles3DTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture Particles3DTest framebuffer", e);
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
