package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.controller.OrbitCameraController3D;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment3D;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.MaterialAttributes;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.ModelBuilder;
import io.github.libfdx.graphics.g3d.ModelVertexUsage;
import io.github.libfdx.graphics.g3d.PbrAttributes;
import io.github.libfdx.graphics.g3d.ShadingModel;
import io.github.libfdx.math.Color;
import io.github.libfdx.tests.TestFpsLogger;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Compares full PBR, partially influenced PBR, and unlit shading in one
 * {@link ModelBatch}.
 *
 * <p>The spheres are intentionally identical except for their material
 * shading configuration. From left to right they use PBR with lighting
 * influence {@code 1.0}, PBR with lighting influence {@code 0.45}, and
 * unlit shading. The unlit material retains lighting influence {@code 1.0}
 * so the scenario also proves that its shading model takes precedence.</p>
 */
public final class ShadingModels3DTest extends ApplicationAdapter {
    private static final Color CLEAR_COLOR =
            new Color(0.035f, 0.045f, 0.065f, 1.0f);
    private static final float PARTIAL_LIGHTING_INFLUENCE = 0.45f;
    private static final float[] SPHERE_X = { -1.65f, 0.0f, 1.65f };

    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private GraphicsContext graphics;
    private ModelBatch batch;
    private Camera camera;
    private OrbitCameraController3D cameraInput;
    private Model floorModel;
    private Model[] sphereModels;
    private DefaultModelInstance floorInstance;
    private DefaultModelInstance[] sphereInstances;
    private boolean created;
    private String capturePath;
    private long captureFrame;
    private int captureEvery;
    private int capturedFrames;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates the mixed shading-model scenario.
     *
     * @param exitAfterFrames frame count before exiting, or zero to run
     */
    public ShadingModels3DTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "ShadingModels3DTest");

        Environment3D environment = new Environment3D()
                .ambientColor(new Color(0.025f, 0.030f, 0.040f, 1.0f))
                .add(new DirectionalLight()
                        .direction(-0.42f, -0.82f, -0.38f)
                        .color(new Color(1.0f, 0.92f, 0.78f, 1.0f))
                        .intensity(1.55f));
        batch = new ModelBatch(graphics).environment(environment);
        createScene();

        camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(58.0f)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 30.0f);
        cameraInput = new OrbitCameraController3D(fdx.input(), camera)
                .position(0.0f, 1.75f, 5.25f, 0.0f, 0.58f, -0.35f)
                .autoOrbit(TestCameraControllers.autoOrbitEnabled(), 0.75f,
                        exitAfterFrames,
                        TestCameraControllers.autoOrbitStartDegrees(),
                        TestCameraControllers.autoOrbitDegrees());
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureFrame = Math.max(0L, Long.parseLong(
                System.getProperty("libfdx.test.captureFrame", "6")));
        captureEvery = Integer.parseInt(
                System.getProperty("libfdx.test.captureEvery", "0"));

        created = true;
        logger.info("ShadingModels3DTest created for provider "
                + graphics.providerId()
                + "; left=PBR 1.0, center=PBR 0.45, right=unlit");
    }

    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        camera.viewport(framebufferWidth(), framebufferHeight());
        cameraInput.update(deltaSeconds);

        batch.begin(LoadOp.clear(CLEAR_COLOR.red(), CLEAR_COLOR.green(),
                CLEAR_COLOR.blue(), CLEAR_COLOR.alpha()), camera);
        batch.render(floorInstance);
        for (int i = 0; i < sphereInstances.length; i++) {
            batch.render(sphereInstances[i]);
        }
        batch.end();
        captureIfRequested();

        renderedFrames++;
        fpsLogger.frame(deltaSeconds, renderedFrames);
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    @Override
    public void dispose() {
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (floorModel != null) {
            floorModel.dispose();
            floorModel = null;
        }
        if (sphereModels != null) {
            for (int i = 0; i < sphereModels.length; i++) {
                if (sphereModels[i] != null) {
                    sphereModels[i].dispose();
                    sphereModels[i] = null;
                }
            }
            sphereModels = null;
        }
        if (!created) {
            throw new FdxException(
                    "ShadingModels3DTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("ShadingModels3DTest rendered "
                    + renderedFrames + " of " + exitAfterFrames
                    + " required frames");
        }
        if (capturePath != null && capturePath.length() > 0
                && !captured) {
            throw new FdxException(
                    "ShadingModels3DTest did not capture framebuffer to "
                            + capturePath);
        }
        logger.info("ShadingModels3DTest rendered " + renderedFrames
                + " frames");
    }

    private void createScene() {
        Material[] materials = {
                sphereMaterial("shading models full PBR",
                        ShadingModel.PBR, 1.0f),
                sphereMaterial("shading models partial PBR",
                        ShadingModel.PBR,
                        PARTIAL_LIGHTING_INFLUENCE),
                sphereMaterial("shading models unlit",
                        ShadingModel.UNLIT, 1.0f)
        };
        ModelBuilder builder = new ModelBuilder(graphics);
        sphereModels = new Model[materials.length];
        sphereInstances = new DefaultModelInstance[materials.length];
        for (int i = 0; i < materials.length; i++) {
            String id = materials[i].id();
            sphereModels[i] = builder.material(materials[i])
                    .sphere(id, 0.68f, 32, 20,
                            ModelVertexUsage.STANDARD_PBR);
            sphereInstances[i] = new DefaultModelInstance(sphereModels[i]);
            sphereInstances[i].transform().setToTranslation(
                    SPHERE_X[i], 0.68f, -0.35f);
        }

        Material floorMaterial = new Material("shading models floor material")
                .set(MaterialAttributes.baseColor(
                        0.22f, 0.235f, 0.26f, 1.0f))
                .set(MaterialAttributes.lightingInfluence(1.0f))
                .set(PbrAttributes.metallicFactor(0.0f))
                .set(PbrAttributes.roughnessFactor(0.92f));
        floorModel = builder.material(floorMaterial)
                .box("shading models floor", 6.2f, 0.08f, 3.5f,
                        ModelVertexUsage.STANDARD_PBR);
        floorInstance = new DefaultModelInstance(floorModel);
        floorInstance.transform().setToTranslation(0.0f, -0.04f, -0.35f);
    }

    private static Material sphereMaterial(String id,
            ShadingModel shadingModel, float lightingInfluence) {
        return new Material(id)
                .shadingModel(shadingModel)
                .set(MaterialAttributes.baseColor(
                        0.12f, 0.42f, 0.82f, 1.0f))
                .set(MaterialAttributes.lightingInfluence(
                        lightingInfluence))
                .set(PbrAttributes.metallicFactor(0.12f))
                .set(PbrAttributes.roughnessFactor(0.38f));
    }

    private void captureIfRequested() {
        if (capturePath == null || capturePath.length() == 0
                || renderedFrames < captureFrame) {
            return;
        }
        if (captureEvery > 0 && capturePath.indexOf('%') >= 0) {
            if ((renderedFrames - captureFrame) % captureEvery == 0) {
                captureFrame(String.format(Locale.ROOT,
                        capturePath, capturedFrames));
                capturedFrames++;
                captured = true;
            }
        }
        else if (!captured) {
            captureFrame(capturePath);
            captured = true;
        }
    }

    private void captureFrame(String path) {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            int width = framebufferWidth();
            int height = framebufferHeight();
            FramebufferCapture.validateSceneFrame(width, height, pixels);
            FramebufferCapture.writePpm(path, width, height, pixels);
            logger.info("ShadingModels3DTest captured framebuffer to "
                    + path);
        }
        catch (Exception exception) {
            throw new FdxException(
                    "Could not capture ShadingModels3DTest framebuffer",
                    exception);
        }
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0
                ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 800;
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0
                ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 600;
    }
}
