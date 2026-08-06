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
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment3D;
import io.github.libfdx.graphics.g3d.FogOfWarRenderer3D;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.ModelBuilder;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.PbrAttributes;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.tests.TestFpsLogger;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Runs the 3D fog-of-war shader test scenario.
 *
 * @author xpenatan
 */
public final class FogOfWar3DTest extends ApplicationAdapter {
    private static final Color CLEAR_COLOR = new Color(0.030f, 0.036f, 0.050f, 1.0f);
    private static final float CAMERA_TARGET_X = 0.0f;
    private static final float CAMERA_TARGET_Y = -0.32f;
    private static final float CAMERA_TARGET_Z = -2.70f;
    private static final float CAMERA_HEIGHT = 3.35f;
    private static final float CAMERA_ORBIT_RADIUS = 7.75f;

    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private GraphicsContext graphics;
    private ModelBatch batch;
    private FogOfWarRenderer3D fogRenderer;
    private Camera camera;
    private OrbitCameraController3D cameraInput;
    private Model floorModel;
    private Model cubeModel;
    private DefaultModelInstance[] instances;
    private boolean created;
    private String capturePath;
    private int captureEvery;
    private int capturedFrames;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates a 3D fog-of-war test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public FogOfWar3DTest(long exitAfterFrames) {
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
        fpsLogger = TestFpsLogger.create(logger, "FogOfWar3DTest");

        Environment3D environment = new Environment3D()
                .ambientColor(new Color(0.20f, 0.21f, 0.24f, 1.0f))
                .add(new DirectionalLight()
                        .direction(-0.28f, -0.82f, -0.42f)
                        .color(new Color(1.0f, 0.94f, 0.84f, 1.0f))
                        .intensity(1.2f));
        batch = new ModelBatch(graphics).environment(environment);
        fogRenderer = new FogOfWarRenderer3D(graphics)
                .color(0.0f, 0.018f, 0.050f, 0.82f);
        ModelBuilder builder = new ModelBuilder(graphics)
                .material(new Material("fog-of-war-3d material")
                        .set(PbrAttributes.roughnessFactor(0.78f))
                        .set(PbrAttributes.metallicFactor(0.0f)));
        floorModel = builder.box("fog-of-war-3d floor", 8.0f, 0.04f, 7.2f);
        cubeModel = builder.box("fog-of-war-3d cube", 0.76f, 0.92f, 0.76f);
        instances = createInstances(floorModel, cubeModel);
        camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(60.0f)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 38.0f);
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureEvery = Integer.parseInt(System.getProperty("libfdx.test.captureEvery", "0"));
        cameraInput = new OrbitCameraController3D(fdx.input(), camera)
                .position(CAMERA_TARGET_X, CAMERA_HEIGHT, CAMERA_TARGET_Z + CAMERA_ORBIT_RADIUS,
                        CAMERA_TARGET_X, CAMERA_TARGET_Y, CAMERA_TARGET_Z)
                .autoOrbit(TestCameraControllers.autoOrbitEnabled(), 0.75f, exitAfterFrames,
                        TestCameraControllers.autoOrbitStartDegrees(), TestCameraControllers.autoOrbitDegrees());

        created = true;
        logger.info("FogOfWar3DTest created WGSL world-space fog-of-war renderer for provider "
                + graphics.providerId());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        camera.viewport(framebufferWidth(), framebufferHeight());
        cameraInput.update(deltaSeconds);
        batch.begin(LoadOp.clear(CLEAR_COLOR.red(), CLEAR_COLOR.green(), CLEAR_COLOR.blue(), 1.0f), camera);
        for (int i = 0; i < instances.length; i++) {
            batch.render(instances[i]);
        }
        batch.end();

        fogRenderer.clearLights()
                .light(-1.55f, -0.40f, -1.35f, 1.55f, 0.58f)
                .light(1.15f, -0.40f, -2.90f, 1.72f, 0.62f)
                .light(0.10f, -0.40f, -4.65f, 1.25f, 0.54f);
        fogRenderer.begin(LoadOp.load());
        fogRenderer.draw(camera, -4.0f, -6.2f, 8.0f, 7.0f, -0.40f);
        fogRenderer.end();

        if (capturePath != null && capturePath.length() > 0) {
            if (captureEvery > 0 && capturePath.indexOf('%') >= 0) {
                if (renderedFrames % captureEvery == 0) {
                    captureFrame(String.format(Locale.ROOT, capturePath, capturedFrames));
                    capturedFrames++;
                }
            }
            else if (!captured && renderedFrames >= 10) {
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
        if (fogRenderer != null) {
            fogRenderer.dispose();
            fogRenderer = null;
        }
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (floorModel != null) {
            floorModel.dispose();
            floorModel = null;
        }
        if (cubeModel != null) {
            cubeModel.dispose();
            cubeModel = null;
        }
        if (!created) {
            throw new FdxException("FogOfWar3DTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("FogOfWar3DTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        logger.info("FogOfWar3DTest rendered " + renderedFrames + " frames");
    }

    private DefaultModelInstance[] createInstances(Model floor, Model cube) {
        DefaultModelInstance[] result = new DefaultModelInstance[6];
        result[0] = new DefaultModelInstance(floor)
                .transform(Matrix4.translation(0.0f, -0.46f, -2.78f));
        float[] x = { -1.65f, -0.42f, 1.18f, 1.72f, -1.05f };
        float[] y = { -0.03f, 0.24f, -0.02f, 0.35f, 0.16f };
        float[] z = { -1.18f, -2.18f, -2.92f, -4.14f, -4.98f };
        float[] yaw = { -0.34f, 0.22f, -0.42f, 0.55f, 0.36f };
        for (int i = 0; i < x.length; i++) {
            result[i + 1] = new DefaultModelInstance(cube)
                    .transform(Matrix4.translation(x[i], y[i], z[i]).multiply(Matrix4.rotationY(yaw[i])));
        }
        return result;
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
            logger.info("FogOfWar3DTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture FogOfWar3DTest framebuffer", e);
        }
    }
}
