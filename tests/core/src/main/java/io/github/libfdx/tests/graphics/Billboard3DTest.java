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
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.MaterialAttributes;
import io.github.libfdx.graphics.g3d.PbrAttributes;
import io.github.libfdx.graphics.g3d.SkyboxRenderer3D;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.tests.TestFpsLogger;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Runs a 3D billboard renderer test scenario.
 *
 * @author xpenatan
 */
public final class Billboard3DTest extends ApplicationAdapter {
    private static final int BILLBOARD_TEXTURE_SIZE = 64;
    private static final Color CLEAR_COLOR = new Color(0.018f, 0.021f, 0.033f, 1.0f);

    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private SkyboxRenderer3D skybox;
    private ModelBatch batch;
    private BillboardRenderer3D billboards;
    private Camera camera;
    private OrbitCameraController3D cameraInput;
    private Model floorModel;
    private Model pillarModel;
    private DefaultModelInstance floorInstance;
    private DefaultModelInstance pillarInstance;
    private Texture billboardTexture;
    private String capturePath;
    private long captureFrame;
    private int captureEvery;
    private int capturedFrames;
    private boolean created;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates a 3D billboard test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public Billboard3DTest(long exitAfterFrames) {
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
        fpsLogger = TestFpsLogger.create(logger, "Billboard3DTest");
        skybox = new SkyboxRenderer3D(graphics)
                .zenithColor(0.035f, 0.10f, 0.28f)
                .horizonColor(0.55f, 0.44f, 0.32f)
                .nadirColor(0.018f, 0.021f, 0.033f)
                .sunColor(1.0f, 0.78f, 0.42f, 0.62f)
                .sunPosition(0.68f, 0.70f)
                .sunSize(0.10f);
        batch = new ModelBatch(graphics).environment(new Environment3D()
                .ambientColor(new Color(0.18f, 0.19f, 0.22f, 1.0f))
                .add(new DirectionalLight()
                        .direction(-0.52f, -0.74f, -0.42f)
                        .color(new Color(1.0f, 0.88f, 0.72f, 1.0f))
                        .intensity(1.45f)));
        billboards = new BillboardRenderer3D(graphics, 16);
        billboardTexture = createBillboardTexture();
        createModels();
        camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(58.0f)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 32.0f);
        cameraInput = new OrbitCameraController3D(fdx.input(), camera)
                .position(3.8f, 2.15f, 4.1f, 0.0f, 0.12f, -0.85f)
                .autoOrbit(TestCameraControllers.autoOrbitEnabled(), 0.75f, exitAfterFrames,
                        TestCameraControllers.autoOrbitStartDegrees(), TestCameraControllers.autoOrbitDegrees());
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "24"));
        captureEvery = Integer.parseInt(System.getProperty("libfdx.test.captureEvery", "0"));
        created = true;
        logger.info("Billboard3DTest created WGSL 3D billboards for provider "
                + graphics.providerId().value());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        camera.viewport(framebufferWidth(), framebufferHeight());
        float seconds = renderedFrames / 60.0f;
        cameraInput.update(deltaSeconds);

        GraphicsFrame frame = graphics.currentFrame();
        skybox.begin(LoadOp.clear(CLEAR_COLOR.red(), CLEAR_COLOR.green(), CLEAR_COLOR.blue(), CLEAR_COLOR.alpha()));
        skybox.draw(camera);
        skybox.end();

        RenderPass pass = frame.commandEncoder().beginRenderPass(RenderPassDescriptor
                .color(frame.colorAttachment(), LoadOp.load(), StoreOp.store())
                .depthClear(1.0f)
                .label("billboard 3d test pass"));
        batch.begin(pass, camera);
        batch.render(floorInstance);
        batch.render(pillarInstance);
        batch.end();

        billboards.begin(pass);
        billboards.color(0.20f, 0.70f, 1.0f, 0.88f);
        billboards.draw(billboardTexture, camera, -1.25f, 0.50f, -1.65f, 0.88f, 0.88f, 8.0f);
        billboards.color(1.0f, 0.56f, 0.24f, 0.86f);
        billboards.draw(billboardTexture, camera, 1.22f, 0.44f, -1.10f, 0.78f, 0.78f, -16.0f);
        billboards.color(0.72f, 0.96f, 0.32f, 0.82f);
        billboards.draw(billboardTexture, camera, 0.0f, 1.28f, -2.05f, 1.05f, 1.05f, seconds * 45.0f);
        billboards.color(1.0f, 0.86f, 0.38f, 0.90f);
        billboards.draw(billboardTexture, camera, -0.12f, -0.15f, 0.18f, 0.55f, 0.55f, -seconds * 38.0f);
        billboards.end();
        pass.end();

        if (capturePath != null && capturePath.length() > 0) {
            if (captureEvery > 0 && capturePath.indexOf('%') >= 0) {
                if (renderedFrames % captureEvery == 0) {
                    captureFrame(String.format(Locale.ROOT, capturePath, capturedFrames));
                    capturedFrames++;
                }
            }
            else if (!captured && renderedFrames >= captureFrame) {
                captureFrame(capturePath);
                captured = true;
            }
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
        if (pillarModel != null) {
            pillarModel.dispose();
            pillarModel = null;
        }
        if (billboardTexture != null) {
            billboardTexture.dispose();
            billboardTexture = null;
        }
        if (!created) {
            throw new FdxException("Billboard3DTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("Billboard3DTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        if (capturePath != null && capturePath.length() > 0 && !captured && captureEvery <= 0) {
            throw new FdxException("Billboard3DTest did not capture framebuffer to " + capturePath);
        }
        logger.info("Billboard3DTest rendered " + renderedFrames + " frames");
    }

    private void createModels() {
        ModelBuilder builder = new ModelBuilder(graphics);
        floorModel = builder
                .material(new Material("billboard floor material")
                        .set(MaterialAttributes.baseColor(
                                0.42f, 0.43f, 0.40f, 1.0f))
                        .set(PbrAttributes.roughnessFactor(0.9f)))
                .box("billboard floor", 4.8f, 0.08f, 4.2f);
        pillarModel = builder
                .material(new Material("billboard pillar material")
                        .set(MaterialAttributes.baseColor(
                                0.54f, 0.49f, 0.44f, 1.0f))
                        .set(PbrAttributes.roughnessFactor(0.82f)))
                .box("billboard pillar", 0.78f, 1.72f, 0.78f);
        floorInstance = new DefaultModelInstance(floorModel)
                .transform(Matrix4.translation(0.0f, -0.70f, -1.25f));
        pillarInstance = new DefaultModelInstance(pillarModel)
                .transform(Matrix4.translation(0.0f, 0.18f, -1.10f)
                        .multiply(Matrix4.rotationY(0.32f)));
    }

    private Texture createBillboardTexture() {
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8("billboard sprite",
                BILLBOARD_TEXTURE_SIZE, BILLBOARD_TEXTURE_SIZE));
        graphics.device().writeTexture(texture, billboardPixels());
        return texture;
    }

    private ByteBuffer billboardPixels() {
        ByteBuffer pixels = ByteBuffer.allocateDirect(BILLBOARD_TEXTURE_SIZE * BILLBOARD_TEXTURE_SIZE * 4);
        float center = (BILLBOARD_TEXTURE_SIZE - 1) * 0.5f;
        for (int y = 0; y < BILLBOARD_TEXTURE_SIZE; y++) {
            for (int x = 0; x < BILLBOARD_TEXTURE_SIZE; x++) {
                float dx = (x - center) / center;
                float dy = (y - center) / center;
                float distance = (float)Math.sqrt(dx * dx + dy * dy);
                float disc = smooth(1.0f - distance);
                float core = smooth(1.0f - distance * 2.65f);
                float cross = smooth(1.0f - Math.min(Math.abs(dx), Math.abs(dy)) * 8.0f)
                        * smooth(1.0f - distance * 1.25f);
                float alpha = clamp(disc * 0.40f + core * 0.58f + cross * 0.30f);
                int value = (int)(255.0f * clamp(0.78f + core * 0.22f));
                pixels.put((byte)value);
                pixels.put((byte)value);
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
            logger.info("Billboard3DTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture Billboard3DTest framebuffer", e);
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

    private float smooth(float value) {
        float clamped = clamp(value);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }

    private float clamp(float value) {
        if (value <= 0.0f) {
            return 0.0f;
        }
        return value >= 1.0f ? 1.0f : value;
    }
}
