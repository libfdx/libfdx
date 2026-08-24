package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.controller.OrbitCameraController3D;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment3D;
import io.github.libfdx.graphics.g3d.G3DAssetLoaders;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.SkyboxRenderer3D;
import io.github.libfdx.math.Color;
import io.github.libfdx.tests.TestFpsLogger;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Runs a 3D procedural skybox test scenario.
 *
 * @author xpenatan
 */
public final class Skybox3DTest extends ApplicationAdapter {
    private static final String MODEL_ASSET = ModelBatchTest.DEFAULT_GLTF_ASSET;

    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private AssetManager assets;
    private GraphicsContext graphics;
    private SkyboxRenderer3D skybox;
    private ModelBatch batch;
    private Camera camera;
    private OrbitCameraController3D cameraInput;
    private Model model;
    private DefaultModelInstance instance;
    private boolean created;
    private String capturePath;
    private long captureFrame;
    private int captureEvery;
    private int capturedFrames;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates a 3D skybox test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public Skybox3DTest(long exitAfterFrames) {
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
        fpsLogger = TestFpsLogger.create(logger, "Skybox3DTest");
        assets = new DefaultAssetManager(fdx.files());
        G3DAssetLoaders.register(assets, graphics);

        skybox = new SkyboxRenderer3D(graphics)
                .zenithColor(0.045f, 0.15f, 0.38f)
                .horizonColor(0.92f, 0.62f, 0.33f)
                .nadirColor(0.02f, 0.025f, 0.04f)
                .sunColor(1.0f, 0.86f, 0.52f, 0.9f)
                .sunPosition(0.36f, 0.72f)
                .sunSize(0.12f);
        batch = new ModelBatch(graphics).environment(new Environment3D()
                .ambientColor(new Color(0.21f, 0.22f, 0.25f, 1.0f))
                .add(new DirectionalLight()
                        .direction(-0.42f, -0.68f, -0.6f)
                        .color(new Color(1.0f, 0.88f, 0.72f, 1.0f))
                        .intensity(1.6f)));
        assets.load(AssetDescriptor.of(MODEL_ASSET, Model.class));
        assets.finishLoading();
        model = assets.get(MODEL_ASSET, Model.class);
        instance = new DefaultModelInstance(model);
        camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(64.0f)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 40.0f);
        cameraInput = new OrbitCameraController3D(fdx.input(), camera)
                .position(0.0f, 0.22f, 3.15f, 0.0f, 0.02f, 0.0f)
                .autoOrbit(TestCameraControllers.autoOrbitEnabled(), 0.75f, exitAfterFrames,
                        TestCameraControllers.autoOrbitStartDegrees(), TestCameraControllers.autoOrbitDegrees());
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "30"));
        captureEvery = Integer.parseInt(System.getProperty("libfdx.test.captureEvery", "0"));

        created = true;
        logger.info("Skybox3DTest created procedural WGSL skybox for provider "
                + graphics.providerId().value());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        assets.update();
        camera.viewport(framebufferWidth(), framebufferHeight());
        float seconds = renderedFrames / 60.0f;
        cameraInput.update(deltaSeconds);
        instance.transform().setToRotationY(seconds * 0.35f);

        skybox.begin(LoadOp.clear(0.0f, 0.0f, 0.0f, 1.0f));
        skybox.draw(camera);
        skybox.end();

        batch.begin(LoadOp.load(), camera);
        batch.render(instance);
        batch.end();

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
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (skybox != null) {
            skybox.dispose();
            skybox = null;
        }
        if (assets != null) {
            assets.dispose();
            assets = null;
        }
        if (model != null) {
            model.dispose();
            model = null;
        }
        if (!created) {
            throw new FdxException("Skybox3DTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("Skybox3DTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        if (capturePath != null && capturePath.length() > 0 && !captured && captureEvery <= 0) {
            throw new FdxException("Skybox3DTest did not capture framebuffer to " + capturePath);
        }
        logger.info("Skybox3DTest rendered " + renderedFrames + " frames");
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 640;
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 480;
    }

    private void captureFrame(String path) {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            FramebufferCapture.writePpm(path, framebufferWidth(), framebufferHeight(), pixels);
            logger.info("Skybox3DTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture Skybox3DTest framebuffer", e);
        }
    }
}
