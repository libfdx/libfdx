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
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.g2d.ShapeRenderer2D;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g3d.CascadedShadowMap3D;
import io.github.libfdx.graphics.camera.controller.FreeCameraController3D;
import io.github.libfdx.graphics.ImmediateModeRenderer;
import io.github.libfdx.graphics.g3d.DefaultModel;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.DirectionalShadowMap3D;
import io.github.libfdx.graphics.g3d.Environment3D;
import io.github.libfdx.graphics.g3d.G3DAssetLoaders;
import io.github.libfdx.graphics.g3d.MeshPart;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.PbrMaterial;
import io.github.libfdx.graphics.g3d.SkyEnvironment3D;
import io.github.libfdx.graphics.g3d.SkyboxRenderer3D;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector3;
import io.github.libfdx.tests.TestFpsLogger;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiFloatState;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTheme;
import io.github.libfdx.ui.UiToolkit;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Runs the 3D directional shadow-map test scenario.
 *
 * @author xpenatan
 */
public final class ShadowMap3DTest extends ApplicationAdapter {
    private static final String DUCK_ASSET = "data/g3d/gltf/Ducky/ducky.gltf";
    private static final String DRAGON_ASSET = "data/g3d/gltf/StanfordDragon/stanfordDragon.gltf";
    private static final int ROTATING_INSTANCE_LIMIT = 15;
    private static final int ROTATING_DRAGON_COUNT = 3;
    private static final float ROTATION_DEGREES_PER_SECOND = 12.0f;
    private static final Color CLEAR_COLOR = new Color(0.42f, 0.55f, 0.76f, 1.0f);
    private static final float CAMERA_TARGET_X = 0.0f;
    private static final float CAMERA_TARGET_Y = 0.0f;
    private static final float CAMERA_TARGET_Z = -20.0f;
    private static final float CAMERA_HEIGHT = 8.0f;
    private static final float CAMERA_ORBIT_RADIUS = 40.0f;
    private static final float SHADOW_CENTER_X = 0.0f;
    private static final float SHADOW_CENTER_Y = 0.25f;
    private static final float SHADOW_CENTER_Z = -44.0f;
    private static final float SHADOW_NEAR = 0.1f;
    private static final float SHADOW_FAR = 190.0f;
    private static final float DEFAULT_LIGHT_YAW_DEGREES = 210.0f;
    private static final float DEFAULT_LIGHT_PITCH_DEGREES = -60.0f;
    private static final int[] SHADOW_RESOLUTIONS = { 1024, 2048, 4096 };
    private static final float[][] CASCADE_DEBUG_COLORS = {
            { 1.0f, 0.02f, 0.02f, 0.96f },
            { 0.02f, 0.90f, 0.12f, 0.96f },
            { 0.05f, 0.15f, 1.0f, 0.96f },
            { 0.95f, 0.00f, 1.0f, 0.96f }
    };

    private final long exitAfterFrames;
    private final boolean cascaded;
    private Application application;
    private Display display;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private AssetManager assets;
    private GraphicsContext graphics;
    private Environment3D environment;
    private ModelBatch batch;
    private SkyboxRenderer3D skybox;
    private SkyEnvironment3D skyEnvironment;
    private Camera camera;
    private Camera editorCamera;
    private FreeCameraController3D cameraController;
    private FreeCameraController3D editorCameraController;
    private DirectionalLight sun;
    private DirectionalShadowMap3D shadowMap;
    private CascadedShadowMap3D cascadedShadowMap;
    private SpriteBatch spriteBatch;
    private ShapeRenderer2D overlayShapes;
    private ImmediateModeRenderer debugLineRenderer;
    private UiRoot root;
    private UiFloatState biasState;
    private UiFloatState strengthState;
    private UiFloatState shadowHalfSizeState;
    private UiFloatState cascadeDistanceState;
    private UiFloatState cascadeLambdaState;
    private UiFloatState minTexelBiasState;
    private UiFloatState lightYawState;
    private UiFloatState lightPitchState;
    private UiBooleanState shadowPreviewState;
    private UiBooleanState frustumLinesState;
    private UiBooleanState editorCameraState;
    private UiIntState shadowResolutionIndexState;
    private Model floorModel;
    private Model duckModel;
    private Model dragonModel;
    private Model[] sphereModels;
    private Model pillarModel;
    private DefaultModelInstance[] instances;
    private RotatingInstance[] rotatingInstances;
    private final Matrix4 animatedTransform = new Matrix4();
    private final Matrix4 animatedRotation = new Matrix4();
    private final Matrix4 animatedScale = new Matrix4();
    private boolean created;
    private String capturePath;
    private int captureEvery;
    private int capturedFrames;
    private boolean captured;
    private boolean uiVisible;
    private long renderedFrames;
    private float animationSeconds;
    private int activeShadowResolution;
    private float appliedLightYawDegrees = Float.NaN;
    private float appliedLightPitchDegrees = Float.NaN;

    /**
     * Creates a 3D shadow-map test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public ShadowMap3DTest(long exitAfterFrames) {
        this(exitAfterFrames, false);
    }

    /**
     * Creates a 3D shadow-map test.
     *
     * @param exitAfterFrames the exit after frames
     * @param cascaded true to use cascaded shadow maps
     */
    public ShadowMap3DTest(long exitAfterFrames, boolean cascaded) {
        this.exitAfterFrames = exitAfterFrames;
        this.cascaded = cascaded;
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
        fpsLogger = TestFpsLogger.create(logger, "ShadowMap3DTest");
        assets = new DefaultAssetManager(fdx.files());
        G3DAssetLoaders.register(assets, graphics);

        sun = new DirectionalLight()
                .direction(-0.43f, -0.87f, -0.25f)
                .color(new Color(1.0f, 0.90f, 0.72f, 1.0f))
                .intensity(2.18f);
        biasState = Ui.state(floatProperty("libfdx.test.shadowBias", cascaded ? 0.34f : 0.026f));
        strengthState = Ui.state(floatProperty("libfdx.test.shadowStrength", 0.88f));
        shadowHalfSizeState = Ui.state(floatProperty("libfdx.test.shadowHalfSize", 74.0f));
        cascadeDistanceState = Ui.state(floatProperty("libfdx.test.shadowCascadeDistance", 170.0f));
        cascadeLambdaState = Ui.state(floatProperty("libfdx.test.shadowCascadeLambda", 0.75f));
        minTexelBiasState = Ui.state(floatProperty("libfdx.test.shadowMinTexelBias", 2.5f));
        lightYawState = Ui.state(floatProperty("libfdx.test.shadowLightYaw", DEFAULT_LIGHT_YAW_DEGREES));
        lightPitchState = Ui.state(floatProperty("libfdx.test.shadowLightPitch", DEFAULT_LIGHT_PITCH_DEGREES));
        shadowPreviewState = Ui.state(Boolean.parseBoolean(System.getProperty("libfdx.test.shadowPreview", "false")));
        frustumLinesState = Ui.state(Boolean.parseBoolean(System.getProperty("libfdx.test.shadowFrustum", "true")));
        editorCameraState = Ui.state(Boolean.parseBoolean(System.getProperty("libfdx.test.shadowEditorCamera", "false")));
        shadowResolutionIndexState = Ui.state(initialShadowResolutionIndex());
        skyEnvironment = new SkyEnvironment3D()
                .zenithColor(0.20f, 0.38f, 0.66f)
                .horizonColor(0.68f, 0.80f, 0.93f)
                .nadirColor(0.36f, 0.34f, 0.32f)
                .sunColor(1.0f, 0.80f, 0.42f)
                .sunDirection(0.43f, 0.87f, 0.25f)
                .intensity(0.82f, 0.72f)
                .sunIntensity(0.46f)
                .horizonBlend(0.42f);
        skybox = new SkyboxRenderer3D(graphics)
                .zenithColor(0.20f, 0.38f, 0.66f)
                .horizonColor(0.68f, 0.80f, 0.93f)
                .nadirColor(0.36f, 0.34f, 0.32f)
                .sunColor(1.0f, 0.80f, 0.42f, 0.70f)
                .sunDirection(0.43f, 0.87f, 0.25f)
                .sunSize(0.16f);
        environment = new Environment3D()
                .ambientColor(new Color(0.035f, 0.040f, 0.050f, 1.0f))
                .skyEnvironment(skyEnvironment)
                .fog(0.50f, 0.62f, 0.80f, 0.42f, 52.0f, 150.0f)
                .add(sun);
        recreateShadowResources(selectedShadowResolution());
        batch = new ModelBatch(graphics).environment(environment);
        assets.load(AssetDescriptor.of(DUCK_ASSET, Model.class));
        assets.load(AssetDescriptor.of(DRAGON_ASSET, Model.class));
        assets.finishLoading();
        duckModel = assets.get(DUCK_ASSET, Model.class);
        dragonModel = assets.get(DRAGON_ASSET, Model.class);
        tuneSceneMaterials(duckModel, dragonModel);
        floorModel = createFloorModel(graphics);
        sphereModels = createSphereModels(graphics);
        pillarModel = createCylinderModel(graphics, "shadow-map-3d pillar", 0.75f, 10.0f,
                0.63f, 0.61f, 0.58f);
        ArrayList<RotatingInstance> rotating = new ArrayList<RotatingInstance>();
        instances = createInstances(floorModel, duckModel, dragonModel, sphereModels, pillarModel, rotating);
        rotatingInstances = rotating.toArray(new RotatingInstance[rotating.size()]);
        spriteBatch = new SpriteBatch(graphics);
        overlayShapes = new ShapeRenderer2D(graphics);
        debugLineRenderer = new ImmediateModeRenderer(graphics);
        camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(67.0f)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 190.0f)
                .position(CAMERA_TARGET_X, CAMERA_HEIGHT, CAMERA_TARGET_Z + CAMERA_ORBIT_RADIUS)
                .lookAt(CAMERA_TARGET_X, CAMERA_TARGET_Y, CAMERA_TARGET_Z)
                .update();
        float editorCameraX = floatProperty("libfdx.test.shadowEditorCameraX", 58.0f);
        float editorCameraY = floatProperty("libfdx.test.shadowEditorCameraY", 68.0f);
        float editorCameraZ = floatProperty("libfdx.test.shadowEditorCameraZ", 52.0f);
        float editorTargetX = floatProperty("libfdx.test.shadowEditorTargetX", SHADOW_CENTER_X);
        float editorTargetY = floatProperty("libfdx.test.shadowEditorTargetY", 1.0f);
        float editorTargetZ = floatProperty("libfdx.test.shadowEditorTargetZ", SHADOW_CENTER_Z);
        editorCamera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(58.0f)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 220.0f)
                .position(editorCameraX, editorCameraY, editorCameraZ)
                .lookAt(editorTargetX, editorTargetY, editorTargetZ)
                .update();
        if (cascaded) {
            int width = framebufferWidth();
            int height = framebufferHeight();
            int leftWidth = Math.max(1, width / 2);
            camera.viewport(leftWidth, height).update();
            editorCamera.viewport(Math.max(1, width - leftWidth), height).update();
            cascadedShadowMap.update(camera);
        }
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureEvery = Integer.parseInt(System.getProperty("libfdx.test.captureEvery", "0"));
        uiVisible = Boolean.parseBoolean(System.getProperty("libfdx.test.shadowUi", "true"));
        cameraController = new FreeCameraController3D(fdx.input(), camera)
                .speedRange(0.001f, camera.far())
                .pointerRegion((x, y) -> !cascaded || x < splitViewportX())
                .activationListener(() -> editorCameraState.set(false));
        editorCameraController = new FreeCameraController3D(fdx.input(), editorCamera)
                .speedRange(0.001f, editorCamera.far())
                .pointerRegion((x, y) -> !cascaded || x >= splitViewportX())
                .activationListener(() -> editorCameraState.set(true))
                .enabled(false);
        root = new UiToolkit(fdx.files())
                .theme(theme())
                .root(display, graphics)
                .input(fdx.input());
        root.setContent(this::buildUi);

        created = true;
        logger.info("ShadowMap3DTest created CSM reference-style scene with "
                + (cascaded ? "cascaded directional shadows" : "directional shadows") + " for provider "
                + graphics.providerId());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        int width = framebufferWidth();
        int height = framebufferHeight();
        int leftWidth = cascaded ? Math.max(1, width / 2) : width;
        int rightWidth = Math.max(1, width - leftWidth);
        camera.viewport(leftWidth, height);
        editorCamera.viewport(cascaded ? rightWidth : width, height);
        if (uiVisible) {
            root.update(deltaSeconds);
        }
        Camera renderCamera = activeRenderCamera();
        applyControllerEnabledState(renderCamera);
        cameraController.update(deltaSeconds);
        editorCameraController.update(deltaSeconds);
        updateAnimatedScene(deltaSeconds);
        applyShadowSettings();
        if (cascaded) {
            cascadedShadowMap.render(sun, camera, instances);
        }
        else {
            shadowMap.render(sun, instances);
        }
        if (cascaded) {
            renderSceneViewport(camera, 0, 0, leftWidth, height,
                    LoadOp.clear(CLEAR_COLOR.red(), CLEAR_COLOR.green(), CLEAR_COLOR.blue(), 1.0f));
            renderSceneViewport(editorCamera, leftWidth, 0, rightWidth, height, LoadOp.load());
            renderLightArrow(camera, 0, 0, leftWidth, height);
            if (frustumLinesState.get()) {
                renderDebugLines(editorCamera, leftWidth, 0, rightWidth, height);
            }
        }
        else {
            renderSceneViewport(renderCamera, 0, 0, width, height,
                    LoadOp.clear(CLEAR_COLOR.red(), CLEAR_COLOR.green(), CLEAR_COLOR.blue(), 1.0f));
        }
        if (shadowPreviewState.get()) {
            renderShadowPreview();
        }
        if (uiVisible) {
            root.render();
        }
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

    private void renderSceneViewport(Camera renderCamera, int x, int y, int width, int height, LoadOp loadOp) {
        RenderPass pass = graphics.currentFrame().commandEncoder().beginRenderPass(RenderPassDescriptor
                .color(graphics.currentFrame().colorAttachment(), loadOp, StoreOp.store())
                .depthClear(1.0f)
                .label("shadow-map-3d split viewport"));
        try {
            pass.setViewport(x, y, width, height);
            pass.setScissor(x, y, width, height);
            skybox.begin(pass);
            skybox.draw(renderCamera);
            skybox.end();
            batch.begin(pass, renderCamera);
            for (int i = 0; i < instances.length; i++) {
                batch.render(instances[i]);
            }
            batch.end();
        }
        finally {
            pass.end();
        }
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (root != null) {
            root.dispose();
            root = null;
        }
        if (cameraController != null) {
            cameraController.dispose();
            cameraController = null;
        }
        if (editorCameraController != null) {
            editorCameraController.dispose();
            editorCameraController = null;
        }
        if (debugLineRenderer != null) {
            debugLineRenderer.dispose();
            debugLineRenderer = null;
        }
        if (spriteBatch != null) {
            spriteBatch.dispose();
            spriteBatch = null;
        }
        if (overlayShapes != null) {
            overlayShapes.dispose();
            overlayShapes = null;
        }
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (skybox != null) {
            skybox.dispose();
            skybox = null;
        }
        if (shadowMap != null) {
            shadowMap.dispose();
            shadowMap = null;
        }
        if (cascadedShadowMap != null) {
            cascadedShadowMap.dispose();
            cascadedShadowMap = null;
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
        if (pillarModel != null) {
            pillarModel.dispose();
            pillarModel = null;
        }
        if (assets != null) {
            assets.dispose();
            assets = null;
        }
        if (!created) {
            throw new FdxException("ShadowMap3DTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("ShadowMap3DTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        logger.info("ShadowMap3DTest rendered " + renderedFrames + " frames");
    }

    /**
     * Handles a size change.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void resize(int width, int height) {
        if (root != null) {
            root.resize(width, height);
        }
    }

    private DefaultModelInstance[] createInstances(Model floor, Model duck, Model dragon, Model[] spheres,
            Model pillar, ArrayList<RotatingInstance> rotating) {
        ArrayList<DefaultModelInstance> result = new ArrayList<DefaultModelInstance>();
        result.add(new DefaultModelInstance(floor));

        float[][] dragonPositions = {
                { 0.0f, 0.0f, -5.0f, 22.0f },
                { -10.0f, 0.0f, -30.0f, 146.0f },
                { 10.0f, 0.0f, -50.0f, -42.0f },
                { 0.0f, 0.0f, -70.0f, 208.0f },
                { -8.0f, 0.0f, -90.0f, 80.0f },
                { 8.0f, 0.0f, -110.0f, -124.0f }
        };

        float minDragonDistance = 10.0f;
        for (int z = 0; z >= -120; z -= 12) {
            for (int x = -16; x <= 16; x += 8) {
                if (nearDragon(x, z, dragonPositions, minDragonDistance)) {
                    continue;
                }
                float rotation = ((x + 32) * 19 + (z + 132) * 7) % 360;
                DefaultModelInstance instance = new DefaultModelInstance(duck)
                        .transform(sceneTransform(x, 0.0f, z, rotation, 1.5f, 1.5f, 1.5f));
                result.add(instance);
                addRotatingInstance(rotating, result.size() - 1, instance, x, 0.0f, z, rotation,
                        1.5f, 1.5f, 1.5f);
            }
        }

        for (int i = 0; i < dragonPositions.length; i++) {
            float[] position = dragonPositions[i];
            DefaultModelInstance instance = new DefaultModelInstance(dragon)
                    .transform(sceneTransform(position[0], position[1], position[2], position[3],
                            3.0f, 3.0f, 3.0f));
            result.add(instance);
            if (i < ROTATING_DRAGON_COUNT) {
                addRotatingInstance(rotating, instance, position[0], position[1], position[2], position[3],
                        3.0f, 3.0f, 3.0f);
            }
        }

        for (int i = 0; i < spheres.length; i++) {
            result.add(new DefaultModelInstance(spheres[i])
                    .transform(Matrix4.translation(-8.0f + 4.0f * i, 1.0f, 8.0f)));
        }

        float[][] pillarPositions = {
                { -20.0f, 5.0f, -15.0f }, { 20.0f, 5.0f, -15.0f },
                { -20.0f, 5.0f, -45.0f }, { 20.0f, 5.0f, -45.0f },
                { -20.0f, 5.0f, -75.0f }, { 20.0f, 5.0f, -75.0f },
                { -20.0f, 5.0f, -105.0f }, { 20.0f, 5.0f, -105.0f }
        };
        for (int i = 0; i < pillarPositions.length; i++) {
            float[] position = pillarPositions[i];
            result.add(new DefaultModelInstance(pillar)
                    .transform(Matrix4.translation(position[0], position[1], position[2])));
        }

        return result.toArray(new DefaultModelInstance[result.size()]);
    }

    private static void addRotatingInstance(ArrayList<RotatingInstance> rotating, int instanceIndex,
            DefaultModelInstance instance, float x, float y, float z, float rotationDegrees,
            float scaleX, float scaleY, float scaleZ) {
        if (instanceIndex <= 0 || instanceIndex >= ROTATING_INSTANCE_LIMIT) {
            return;
        }
        addRotatingInstance(rotating, instance, x, y, z, rotationDegrees, scaleX, scaleY, scaleZ);
    }

    private static void addRotatingInstance(ArrayList<RotatingInstance> rotating, DefaultModelInstance instance,
            float x, float y, float z, float rotationDegrees, float scaleX, float scaleY, float scaleZ) {
        rotating.add(new RotatingInstance(instance, x, y, z, rotationDegrees, scaleX, scaleY, scaleZ));
    }

    private void updateAnimatedScene(float deltaSeconds) {
        if (rotatingInstances == null || rotatingInstances.length == 0) {
            return;
        }
        animationSeconds += deltaSeconds;
        float rotationOffset = animationSeconds * ROTATION_DEGREES_PER_SECOND;
        for (int i = 0; i < rotatingInstances.length; i++) {
            RotatingInstance rotating = rotatingInstances[i];
            animatedTransform.setToTranslation(rotating.x, rotating.y, rotating.z)
                    .mul(animatedRotation.setToRotationY((float)Math.toRadians(rotating.rotationDegrees
                            + rotationOffset)))
                    .mul(animatedScale.setToScale(rotating.scaleX, rotating.scaleY, rotating.scaleZ));
            rotating.instance.transform(animatedTransform);
        }
    }

    private static boolean nearDragon(float x, float z, float[][] dragonPositions, float minimumDistance) {
        float minimumDistanceSquared = minimumDistance * minimumDistance;
        for (int i = 0; i < dragonPositions.length; i++) {
            float dx = x - dragonPositions[i][0];
            float dz = z - dragonPositions[i][2];
            if (dx * dx + dz * dz < minimumDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private static Matrix4 sceneTransform(float x, float y, float z, float rotationDegrees,
            float scaleX, float scaleY, float scaleZ) {
        return Matrix4.translation(x, y, z)
                .multiply(Matrix4.rotationY((float)Math.toRadians(rotationDegrees)))
                .multiply(Matrix4.scale(scaleX, scaleY, scaleZ));
    }

    private static final class RotatingInstance {
        private final DefaultModelInstance instance;
        private final float x;
        private final float y;
        private final float z;
        private final float rotationDegrees;
        private final float scaleX;
        private final float scaleY;
        private final float scaleZ;

        RotatingInstance(DefaultModelInstance instance, float x, float y, float z, float rotationDegrees,
                float scaleX, float scaleY, float scaleZ) {
            this.instance = instance;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotationDegrees = rotationDegrees;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
        }
    }

    private void buildUi(UiScope ui) {
        if (cascaded) {
            ui.row(Ui.modifier().fill().padding(8.0f).gap(8.0f), page -> {
                page.column(Ui.modifier().width(272.0f).fillHeight(), column -> {
                    column.spacer(Ui.modifier().weight(1.0f));
                    buildCsmDebugPanel(column);
                });
                page.spacer(Ui.modifier().weight(1.0f));
                page.column(Ui.modifier().width(260.0f).fillHeight(), column -> {
                    column.spacer(Ui.modifier().weight(1.0f));
                    buildGraphicsPanel(column);
                });
            });
            return;
        }
        ui.panel(Ui.modifier().width(342.0f).padding(10.0f).gap(7.0f), panel -> {
            panel.text(cascaded ? "Cascade shadow map" : "Shadow map", Ui.modifier().style("title"));
            panel.text("Camera: " + (editorCameraState.get() ? "editor" : "main"), Ui.modifier().style("small"));
            panel.row(Ui.modifier().fillWidth().gap(6.0f), row -> {
                row.button("Main", Ui.modifier().fillWidth().weight(1.0f).style(buttonStyle(!editorCameraState.get())),
                        () -> selectEditorCamera(false));
                row.button("Editor", Ui.modifier().fillWidth().weight(1.0f).style(buttonStyle(editorCameraState.get())),
                        () -> selectEditorCamera(true));
            });
            panel.row(Ui.modifier().fillWidth().gap(6.0f), row -> {
                row.text("Resolution", Ui.modifier().width(72.0f).style("muted"));
                for (int i = 0; i < SHADOW_RESOLUTIONS.length; i++) {
                    final int index = i;
                    row.button(String.valueOf(SHADOW_RESOLUTIONS[i]),
                            Ui.modifier().fillWidth().weight(1.0f)
                                    .style(buttonStyle(shadowResolutionIndexState.get() == index)),
                            () -> shadowResolutionIndexState.set(index));
                }
            });
            sliderRow(panel, cascaded ? "World bias" : "Bias", biasState, 0.0f, cascaded ? 0.80f : 0.08f);
            sliderRow(panel, "Strength", strengthState, 0.0f, 1.0f);
            sliderRow(panel, "Sun yaw", lightYawState, -180.0f, 180.0f);
            sliderRow(panel, "Sun pitch", lightPitchState, -88.0f, -8.0f);
            if (cascaded) {
                sliderRow(panel, "Texel bias", minTexelBiasState, 0.0f, 5.0f);
                sliderRow(panel, "Distance", cascadeDistanceState, 30.0f, 180.0f);
                sliderRow(panel, "Split", cascadeLambdaState, 0.05f, 0.95f);
            }
            else {
                sliderRow(panel, "Size", shadowHalfSizeState, 20.0f, 120.0f);
            }
            panel.row(Ui.modifier().fillWidth().gap(10.0f), row -> {
                row.checkbox("Shadow preview", Ui.modifier().fillWidth().weight(1.0f), shadowPreviewState);
                if (editorCameraState.get()) {
                    row.checkbox("Frustum lines", Ui.modifier().fillWidth().weight(1.0f), frustumLinesState);
                }
            });
        });
    }

    private void buildCsmDebugPanel(UiScope parent) {
        parent.panel(Ui.modifier().width(272.0f).padding(6.0f).gap(4.0f), panel -> {
            panel.text("CSM Debug", Ui.modifier().style("section"));
            panel.text("Cascades: " + cascadedShadowMap.cascadeCount(), Ui.modifier().style("small"));
            panel.text("Shadow map: " + activeShadowResolution + "px", Ui.modifier().style("small"));
            panel.row(Ui.modifier().fillWidth().gap(6.0f), row -> {
                row.checkbox("Frustum", Ui.modifier().fillWidth().weight(1.0f), frustumLinesState);
            });
            panel.text("--- Shadow Bias ---", Ui.modifier().style("muted"));
            compactSliderRow(panel, "Base bias", biasState, 0.0f, 0.80f);
            compactSliderRow(panel, "Min texel", minTexelBiasState, 0.0f, 5.0f);
            compactSliderRow(panel, "Lambda", cascadeLambdaState, 0.05f, 0.95f);
            panel.text("Distance: " + Math.round(cascadeDistanceState.get()), Ui.modifier().style("small"));
            for (int i = 0; i < cascadedShadowMap.cascadeCount(); i++) {
                float near = i == 0 ? camera.near() : cascadedShadowMap.splitDistance(i - 1);
                float far = cascadedShadowMap.splitDistance(i);
                panel.text("Cascade " + i + ": " + Math.round(near) + "-" + Math.round(far)
                        + "  " + Math.round(cascadedShadowMap.cascadeHalfSize(i) * 2.0f) + "x"
                        + Math.round(cascadedShadowMap.cascadeHalfSize(i) * 2.0f),
                        Ui.modifier().style("small"));
            }
        });
    }

    private void buildGraphicsPanel(UiScope parent) {
        parent.panel(Ui.modifier().width(260.0f).padding(6.0f).gap(4.0f), panel -> {
            panel.text("Graphics", Ui.modifier().style("section"));
            panel.text("--- Light Direction ---", Ui.modifier().style("muted"));
            compactSliderRow(panel, "Azimuth", lightYawState, -180.0f, 180.0f);
            compactSliderRow(panel, "Elevation", lightPitchState, -88.0f, -8.0f);
            compactSliderRow(panel, "Intensity", strengthState, 0.0f, 1.0f);
            panel.text("--- Ambient & IBL ---", Ui.modifier().style("muted"));
            panel.text("IBL gradient sky enabled", Ui.modifier().style("small"));
            panel.text("0.15 0.15 0.15", Ui.modifier().style("small"));
            panel.text("--- CSM Tuning ---", Ui.modifier().style("muted"));
            compactSliderRow(panel, "Distance", cascadeDistanceState, 30.0f, 180.0f);
            compactSliderRow(panel, "Texel", minTexelBiasState, 0.0f, 5.0f);
        });
    }

    private static String buttonStyle(boolean selected) {
        return selected ? "selected-button" : "button";
    }

    private void selectEditorCamera(boolean editor) {
        editorCameraState.set(editor);
    }

    private void sliderRow(UiScope panel, String label, UiFloatState state, float minimum, float maximum) {
        panel.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.text(label, Ui.modifier().width(68.0f).style("muted"));
            row.slider(Ui.modifier().fillWidth().weight(1.0f), state, minimum, maximum);
            row.text(formatValue(state.get()), Ui.modifier().width(46.0f).style("metric"));
        });
    }

    private void compactSliderRow(UiScope panel, String label, UiFloatState state, float minimum, float maximum) {
        panel.row(Ui.modifier().fillWidth().gap(6.0f), row -> {
            row.text(label, Ui.modifier().width(66.0f).style("muted"));
            row.slider(Ui.modifier().fillWidth().weight(1.0f), state, minimum, maximum);
            row.text(String.format(Locale.ROOT, "%.2f", state.get()), Ui.modifier().width(38.0f).style("metric"));
        });
    }

    private void applyControllerEnabledState(Camera renderCamera) {
        if (cascaded) {
            cameraController.enabled(true);
            editorCameraController.enabled(true);
            cameraController.keyboardEnabled(!editorCameraState.get());
            editorCameraController.keyboardEnabled(editorCameraState.get());
            return;
        }
        boolean editorActive = renderCamera == editorCamera;
        cameraController.enabled(!editorActive);
        editorCameraController.enabled(editorActive);
        cameraController.keyboardEnabled(!editorActive);
        editorCameraController.keyboardEnabled(editorActive);
    }

    private Camera activeRenderCamera() {
        if (cascaded) {
            return editorCameraState.get() ? editorCamera : camera;
        }
        return editorCameraState.get() ? editorCamera : camera;
    }

    private void applyShadowSettings() {
        applyLightDirection();
        int resolution = selectedShadowResolution();
        if (resolution != activeShadowResolution) {
            recreateShadowResources(resolution);
        }
        if (cascaded) {
            cascadedShadowMap
                    .bias(biasState.get())
                    .minTexelBias(minTexelBiasState.get())
                    .strength(strengthState.get())
                    .maxDistance(cascadeDistanceState.get())
                    .splitLambda(cascadeLambdaState.get());
        }
        else {
            shadowMap
                    .bounds(SHADOW_CENTER_X, SHADOW_CENTER_Y, SHADOW_CENTER_Z,
                            shadowHalfSizeState.get(), SHADOW_NEAR, SHADOW_FAR)
                    .bias(biasState.get())
                    .strength(strengthState.get());
        }
    }

    private void applyLightDirection() {
        float yawDegrees = lightYawState.get();
        float pitchDegrees = lightPitchState.get();
        if (yawDegrees == appliedLightYawDegrees && pitchDegrees == appliedLightPitchDegrees) {
            return;
        }
        float yawRadians = (float)Math.toRadians(yawDegrees);
        float pitchRadians = (float)Math.toRadians(pitchDegrees);
        float horizontal = (float)Math.cos(pitchRadians);
        sun.direction((float)Math.sin(yawRadians) * horizontal,
                (float)Math.sin(pitchRadians),
                (float)Math.cos(yawRadians) * horizontal);
        if (skybox != null) {
            skybox.sunDirection(-sun.direction().x(), -sun.direction().y(), -sun.direction().z());
        }
        if (skyEnvironment != null) {
            skyEnvironment.sunDirection(-sun.direction().x(), -sun.direction().y(), -sun.direction().z());
        }
        appliedLightYawDegrees = yawDegrees;
        appliedLightPitchDegrees = pitchDegrees;
    }

    private static void tuneSceneMaterials(Model duck, Model dragon) {
        tunePbrMaterials(duck, 0.0f, 0.52f, 1.08f, 1.02f, 0.86f);
        tunePbrMaterials(dragon, 0.04f, 0.62f, 1.00f, 0.80f, 0.58f);
    }

    private static void tunePbrMaterials(Model model, float metallic, float roughness,
            float redTint, float greenTint, float blueTint) {
        if (model == null) {
            return;
        }
        for (int i = 0; i < model.materials().size(); i++) {
            Material material = model.materials().get(i);
            if (material instanceof PbrMaterial) {
                PbrMaterial pbr = (PbrMaterial)material;
                Color baseColor = pbr.baseColor();
                pbr.metallicFactor(metallic)
                        .roughnessFactor(roughness)
                        .baseColor(baseColor.red() * redTint, baseColor.green() * greenTint,
                                baseColor.blue() * blueTint, baseColor.alpha());
            }
        }
    }

    private void recreateShadowResources(int resolution) {
        DirectionalShadowMap3D oldShadowMap = shadowMap;
        CascadedShadowMap3D oldCascadedShadowMap = cascadedShadowMap;
        if (cascaded) {
            shadowMap = null;
            cascadedShadowMap = new CascadedShadowMap3D(graphics, 3, resolution, resolution)
                    .maxDistance(cascadeDistanceState.get())
                    .splitLambda(cascadeLambdaState.get())
                    .bias(biasState.get())
                    .minTexelBias(minTexelBiasState.get())
                    .strength(strengthState.get());
            environment.clearDirectionalShadowMap().cascadedShadowMap(cascadedShadowMap);
        }
        else {
            cascadedShadowMap = null;
            shadowMap = new DirectionalShadowMap3D(graphics, resolution, resolution)
                    .bounds(SHADOW_CENTER_X, SHADOW_CENTER_Y, SHADOW_CENTER_Z,
                            shadowHalfSizeState.get(), SHADOW_NEAR, SHADOW_FAR)
                    .bias(biasState.get())
                    .strength(strengthState.get());
            environment.clearCascadedShadowMap().directionalShadowMap(shadowMap);
        }
        activeShadowResolution = resolution;
        if (oldShadowMap != null) {
            oldShadowMap.dispose();
        }
        if (oldCascadedShadowMap != null) {
            oldCascadedShadowMap.dispose();
        }
    }

    private void renderShadowPreview() {
        Texture texture = cascaded ? cascadedShadowMap.activeShadowMap().texture() : shadowMap.texture();
        float x = 0.57f;
        float y = 0.48f;
        float size = 0.38f;
        overlayShapes.begin(LoadOp.load());
        overlayShapes.filledRect(x - 0.025f, y - 0.025f, size + 0.05f, size + 0.05f,
                0.025f, 0.030f, 0.040f, 0.88f);
        overlayShapes.rect(x - 0.025f, y - 0.025f, size + 0.05f, size + 0.05f,
                0.42f, 0.72f, 1.0f, 1.0f);
        overlayShapes.end();
        spriteBatch.color(1.0f, 0.96f, 0.86f, 1.0f);
        spriteBatch.begin(LoadOp.load());
        spriteBatch.draw(texture, x, y + size, size, -size);
        spriteBatch.end();
    }

    private void renderDebugLines(Camera renderCamera, int x, int y, int width, int height) {
        debugLineRenderer.clear();
        if (cascaded) {
            for (int i = 0; i < cascadedShadowMap.cascadeCount(); i++) {
                float[] color = CASCADE_DEBUG_COLORS[i % CASCADE_DEBUG_COLORS.length];
                float near = i == 0 ? camera.near() : cascadedShadowMap.splitDistance(i - 1);
                float far = cascadedShadowMap.splitDistance(i);
                addCameraFrustum(camera, near, far, color[0], color[1], color[2], color[3]);
            }
        }
        else {
            addBox(SHADOW_CENTER_X, SHADOW_CENTER_Y, SHADOW_CENTER_Z, shadowHalfSizeState.get(),
                    1.0f, 0.82f, 0.20f, 0.95f);
        }
        addCameraFrustum(camera);
        debugLineRenderer.render3D(renderCamera.combined().values(), x, y, width, height);
    }

    private void renderLightArrow(Camera renderCamera, int x, int y, int width, int height) {
        debugLineRenderer.clear();
        float dx = sun.direction().x();
        float dy = sun.direction().y();
        float dz = sun.direction().z();
        float length = length(dx, dy, dz);
        if (length <= 0.0001f) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        float endX = CAMERA_TARGET_X + 2.0f;
        float endY = CAMERA_TARGET_Y + 3.2f;
        float endZ = CAMERA_TARGET_Z + 2.5f;
        float arrowLength = 13.0f;
        float startX = endX - dx * arrowLength;
        float startY = endY - dy * arrowLength;
        float startZ = endZ - dz * arrowLength;
        debugLineRenderer.line3D(startX, startY, startZ, endX, endY, endZ, 1.0f, 0.92f, 0.0f, 1.0f);

        float rightX = dz;
        float rightZ = -dx;
        float rightLength = length(rightX, 0.0f, rightZ);
        if (rightLength <= 0.0001f) {
            rightX = 1.0f;
            rightZ = 0.0f;
            rightLength = 1.0f;
        }
        rightX /= rightLength;
        rightZ /= rightLength;
        float headLength = 2.2f;
        float wing = 0.95f;
        float backX = endX - dx * headLength;
        float backY = endY - dy * headLength;
        float backZ = endZ - dz * headLength;
        debugLineRenderer.line3D(endX, endY, endZ, backX + rightX * wing, backY, backZ + rightZ * wing,
                1.0f, 0.92f, 0.0f, 1.0f);
        debugLineRenderer.line3D(endX, endY, endZ, backX - rightX * wing, backY, backZ - rightZ * wing,
                1.0f, 0.92f, 0.0f, 1.0f);
        debugLineRenderer.render3D(renderCamera.combined().values(), x, y, width, height);
    }

    private void addCameraFrustum(Camera sourceCamera) {
        addCameraFrustum(sourceCamera, sourceCamera.near(), sourceCamera.far(), 0.88f, 0.94f, 1.0f, 0.95f);
    }

    private void addCameraFrustum(Camera sourceCamera, float near, float far, float red, float green, float blue,
            float alpha) {
        float aspect = sourceCamera.viewportHeight() > 0.0f
                ? sourceCamera.viewportWidth() / sourceCamera.viewportHeight() : 1.0f;
        float tan = (float)Math.tan(Math.toRadians(sourceCamera.fieldOfView()) * 0.5f);
        float nearHeight = near * tan;
        float nearWidth = nearHeight * aspect;
        float farHeight = far * tan;
        float farWidth = farHeight * aspect;
        float px = sourceCamera.position().x();
        float py = sourceCamera.position().y();
        float pz = sourceCamera.position().z();
        float fx = sourceCamera.direction().x();
        float fy = sourceCamera.direction().y();
        float fz = sourceCamera.direction().z();
        float directionLength = length(fx, fy, fz);
        if (directionLength <= 0.0001f) {
            fx = 0.0f;
            fy = 0.0f;
            fz = -1.0f;
            directionLength = 1.0f;
        }
        fx /= directionLength;
        fy /= directionLength;
        fz /= directionLength;
        float sourceUpX = sourceCamera.up().x();
        float sourceUpY = sourceCamera.up().y();
        float sourceUpZ = sourceCamera.up().z();
        float rightX = fy * sourceUpZ - fz * sourceUpY;
        float rightY = fz * sourceUpX - fx * sourceUpZ;
        float rightZ = fx * sourceUpY - fy * sourceUpX;
        float rightLength = length(rightX, rightY, rightZ);
        if (rightLength <= 0.0001f) {
            rightX = 1.0f;
            rightY = 0.0f;
            rightZ = 0.0f;
            rightLength = 1.0f;
        }
        rightX /= rightLength;
        rightY /= rightLength;
        rightZ /= rightLength;
        float upX = rightY * fz - rightZ * fy;
        float upY = rightZ * fx - rightX * fz;
        float upZ = rightX * fy - rightY * fx;
        float upLength = length(upX, upY, upZ);
        if (upLength > 0.0001f) {
            upX /= upLength;
            upY /= upLength;
            upZ /= upLength;
        }
        addFrustumPlane(px + fx * near, py + fy * near, pz + fz * near, rightX, rightY, rightZ,
                upX, upY, upZ, nearWidth, nearHeight, red, green, blue, alpha);
        addFrustumPlane(px + fx * far, py + fy * far, pz + fz * far, rightX, rightY, rightZ,
                upX, upY, upZ, farWidth, farHeight, red, green, blue, alpha);
        connectFrustumCorner(px, py, pz, fx, fy, fz, rightX, rightY, rightZ, upX, upY, upZ,
                near, far, nearWidth, nearHeight, farWidth, farHeight, -1.0f, -1.0f, red, green, blue, alpha);
        connectFrustumCorner(px, py, pz, fx, fy, fz, rightX, rightY, rightZ, upX, upY, upZ,
                near, far, nearWidth, nearHeight, farWidth, farHeight, 1.0f, -1.0f, red, green, blue, alpha);
        connectFrustumCorner(px, py, pz, fx, fy, fz, rightX, rightY, rightZ, upX, upY, upZ,
                near, far, nearWidth, nearHeight, farWidth, farHeight, 1.0f, 1.0f, red, green, blue, alpha);
        connectFrustumCorner(px, py, pz, fx, fy, fz, rightX, rightY, rightZ, upX, upY, upZ,
                near, far, nearWidth, nearHeight, farWidth, farHeight, -1.0f, 1.0f, red, green, blue, alpha);
    }

    private void addFrustumPlane(float cx, float cy, float cz, float rightX, float rightY, float rightZ,
            float upX, float upY, float upZ, float halfWidth, float halfHeight,
            float red, float green, float blue, float alpha) {
        float x0 = cx - rightX * halfWidth - upX * halfHeight;
        float y0 = cy - rightY * halfWidth - upY * halfHeight;
        float z0 = cz - rightZ * halfWidth - upZ * halfHeight;
        float x1 = cx + rightX * halfWidth - upX * halfHeight;
        float y1 = cy + rightY * halfWidth - upY * halfHeight;
        float z1 = cz + rightZ * halfWidth - upZ * halfHeight;
        float x2 = cx + rightX * halfWidth + upX * halfHeight;
        float y2 = cy + rightY * halfWidth + upY * halfHeight;
        float z2 = cz + rightZ * halfWidth + upZ * halfHeight;
        float x3 = cx - rightX * halfWidth + upX * halfHeight;
        float y3 = cy - rightY * halfWidth + upY * halfHeight;
        float z3 = cz - rightZ * halfWidth + upZ * halfHeight;
        debugLineRenderer.line3D(x0, y0, z0, x1, y1, z1, red, green, blue, alpha);
        debugLineRenderer.line3D(x1, y1, z1, x2, y2, z2, red, green, blue, alpha);
        debugLineRenderer.line3D(x2, y2, z2, x3, y3, z3, red, green, blue, alpha);
        debugLineRenderer.line3D(x3, y3, z3, x0, y0, z0, red, green, blue, alpha);
    }

    private void connectFrustumCorner(float px, float py, float pz, float fx, float fy, float fz,
            float rightX, float rightY, float rightZ, float upX, float upY, float upZ, float near, float far,
            float nearWidth, float nearHeight, float farWidth, float farHeight, float side, float vertical,
            float red, float green, float blue, float alpha) {
        float nearX = px + fx * near + rightX * nearWidth * side + upX * nearHeight * vertical;
        float nearY = py + fy * near + rightY * nearWidth * side + upY * nearHeight * vertical;
        float nearZ = pz + fz * near + rightZ * nearWidth * side + upZ * nearHeight * vertical;
        float farX = px + fx * far + rightX * farWidth * side + upX * farHeight * vertical;
        float farY = py + fy * far + rightY * farWidth * side + upY * farHeight * vertical;
        float farZ = pz + fz * far + rightZ * farWidth * side + upZ * farHeight * vertical;
        debugLineRenderer.line3D(nearX, nearY, nearZ, farX, farY, farZ, red, green, blue, alpha);
    }

    private static float length(float x, float y, float z) {
        return (float)Math.sqrt(x * x + y * y + z * z);
    }

    private void addBox(float centerX, float centerY, float centerZ, float halfSize,
            float red, float green, float blue, float alpha) {
        float x0 = centerX - halfSize;
        float x1 = centerX + halfSize;
        float y0 = centerY - halfSize;
        float y1 = centerY + halfSize;
        float z0 = centerZ - halfSize;
        float z1 = centerZ + halfSize;
        debugLineRenderer.line3D(x0, y0, z0, x1, y0, z0, red, green, blue, alpha);
        debugLineRenderer.line3D(x1, y0, z0, x1, y0, z1, red, green, blue, alpha);
        debugLineRenderer.line3D(x1, y0, z1, x0, y0, z1, red, green, blue, alpha);
        debugLineRenderer.line3D(x0, y0, z1, x0, y0, z0, red, green, blue, alpha);
        debugLineRenderer.line3D(x0, y1, z0, x1, y1, z0, red, green, blue, alpha);
        debugLineRenderer.line3D(x1, y1, z0, x1, y1, z1, red, green, blue, alpha);
        debugLineRenderer.line3D(x1, y1, z1, x0, y1, z1, red, green, blue, alpha);
        debugLineRenderer.line3D(x0, y1, z1, x0, y1, z0, red, green, blue, alpha);
        debugLineRenderer.line3D(x0, y0, z0, x0, y1, z0, red, green, blue, alpha);
        debugLineRenderer.line3D(x1, y0, z0, x1, y1, z0, red, green, blue, alpha);
        debugLineRenderer.line3D(x1, y0, z1, x1, y1, z1, red, green, blue, alpha);
        debugLineRenderer.line3D(x0, y0, z1, x0, y1, z1, red, green, blue, alpha);
    }

    private static String formatValue(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private int selectedShadowResolution() {
        int index = shadowResolutionIndexState.get();
        if (index < 0 || index >= SHADOW_RESOLUTIONS.length) {
            return SHADOW_RESOLUTIONS[0];
        }
        return SHADOW_RESOLUTIONS[index];
    }

    private static int initialShadowResolutionIndex() {
        int requested = intProperty("libfdx.test.shadowResolution", 1024);
        int closestIndex = 0;
        int closestDistance = Math.abs(SHADOW_RESOLUTIONS[0] - requested);
        for (int i = 1; i < SHADOW_RESOLUTIONS.length; i++) {
            int distance = Math.abs(SHADOW_RESOLUTIONS[i] - requested);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestIndex = i;
            }
        }
        return closestIndex;
    }

    private static int intProperty(String name, int defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.trim().length() == 0) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    private static float floatProperty(String name, float defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.trim().length() == 0) {
            return defaultValue;
        }
        return Float.parseFloat(value.trim());
    }

    private static UiTheme theme() {
        UiStyle selectedButton = UiStyle.button()
                .background(UiDrawable.color(UiColor.rgba8888(0x3f6fb6ff)))
                .hover(UiStyle.button().background(UiDrawable.color(UiColor.rgba8888(0x4b7fd0ff))))
                .pressed(UiStyle.button().background(UiDrawable.color(UiColor.rgba8888(0x31598fff))));
        return Ui.darkTheme()
                .style("selected-button", selectedButton);
    }

    private static Model createFloorModel(GraphicsContext graphics) {
        float y = -0.05f;
        int columns = 16;
        int rows = 16;
        int vertexCount = columns * rows * 6;
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] texCoords = new float[vertexCount * 2];
        float[] colors = new float[vertexCount * 4];
        float[] pbr = new float[vertexCount * 3];
        float[] emissive = new float[vertexCount * 3];
        int vertex = 0;
        for (int row = 0; row < rows; row++) {
            float z0 = 60.0f + (-140.0f - 60.0f) * row / rows;
            float z1 = 60.0f + (-140.0f - 60.0f) * (row + 1) / rows;
            for (int column = 0; column < columns; column++) {
                float x0 = -100.0f + 200.0f * column / columns;
                float x1 = -100.0f + 200.0f * (column + 1) / columns;
                float depth = row / (float)Math.max(1, rows - 1);
                float lateral = Math.abs((column + 0.5f) / columns - 0.5f);
                float red = 0.53f - depth * 0.020f - lateral * 0.018f;
                float green = 0.51f - depth * 0.016f - lateral * 0.016f;
                float blue = 0.48f + depth * 0.020f - lateral * 0.010f;
                vertex = addFloorVertex(positions, normals, texCoords, colors, vertex, x0, y, z0,
                        column, row, columns, rows, red, green, blue);
                vertex = addFloorVertex(positions, normals, texCoords, colors, vertex, x1, y, z0,
                        column + 1, row, columns, rows, red, green, blue);
                vertex = addFloorVertex(positions, normals, texCoords, colors, vertex, x1, y, z1,
                        column + 1, row + 1, columns, rows, red, green, blue);
                vertex = addFloorVertex(positions, normals, texCoords, colors, vertex, x0, y, z0,
                        column, row, columns, rows, red, green, blue);
                vertex = addFloorVertex(positions, normals, texCoords, colors, vertex, x1, y, z1,
                        column + 1, row + 1, columns, rows, red, green, blue);
                vertex = addFloorVertex(positions, normals, texCoords, colors, vertex, x0, y, z1,
                        column, row + 1, columns, rows, red, green, blue);
            }
        }
        for (int i = 0; i < vertexCount; i++) {
            int pbrOffset = i * 3;
            pbr[pbrOffset] = 1.0f;
            pbr[pbrOffset + 1] = 0.0f;
            pbr[pbrOffset + 2] = 0.95f;
        }
        Mesh mesh = Mesh.positionColor3D(graphics, "shadow-map-3d floor", positions, colors, normals, texCoords,
                pbr, emissive, bounds(positions));
        MeshPart meshPart = new MeshPart("shadow-map-3d floor part", mesh, null, 0, mesh.vertexCount());
        PbrMaterial material = new PbrMaterial("shadow-map-3d floor material")
                .roughnessFactor(0.95f)
                .metallicFactor(0.0f);
        return DefaultModel.singleNode("shadow-map-3d floor", meshPart, material);
    }

    private static int addFloorVertex(float[] positions, float[] normals, float[] texCoords, int vertex,
            float x, float y, float z, int column, int row, int columns, int rows) {
        return addFloorVertex(positions, normals, texCoords, null, vertex, x, y, z, column, row, columns, rows,
                0.78f, 0.72f, 0.60f);
    }

    private static int addFloorVertex(float[] positions, float[] normals, float[] texCoords, float[] colors,
            int vertex, float x, float y, float z, int column, int row, int columns, int rows,
            float red, float green, float blue) {
        int positionOffset = vertex * 3;
        positions[positionOffset] = x;
        positions[positionOffset + 1] = y;
        positions[positionOffset + 2] = z;
        normals[positionOffset] = 0.0f;
        normals[positionOffset + 1] = 1.0f;
        normals[positionOffset + 2] = 0.0f;
        int uvOffset = vertex * 2;
        texCoords[uvOffset] = column / (float)columns;
        texCoords[uvOffset + 1] = row / (float)rows;
        if (colors != null) {
            int colorOffset = vertex * 4;
            colors[colorOffset] = red;
            colors[colorOffset + 1] = green;
            colors[colorOffset + 2] = blue;
            colors[colorOffset + 3] = 1.0f;
        }
        return vertex + 1;
    }

    private static Model[] createSphereModels(GraphicsContext graphics) {
        return new Model[] {
                createSphereModel(graphics, "red", 0.90f, 0.16f, 0.20f, 0.00f, 0.18f),
                createSphereModel(graphics, "green", 0.10f, 0.70f, 0.22f, 0.25f, 0.30f),
                createSphereModel(graphics, "blue", 0.12f, 0.32f, 0.95f, 0.50f, 0.44f),
                createSphereModel(graphics, "gold", 0.95f, 0.78f, 0.16f, 0.75f, 0.58f),
                createSphereModel(graphics, "silver", 0.76f, 0.78f, 0.80f, 1.00f, 0.72f)
        };
    }

    private static Model createSphereModel(GraphicsContext graphics, String name, float red, float green, float blue,
            float metallic, float roughness) {
        ArrayList<Float> positions = new ArrayList<Float>();
        ArrayList<Float> normals = new ArrayList<Float>();
        ArrayList<Float> texCoords = new ArrayList<Float>();
        ArrayList<Float> colors = new ArrayList<Float>();
        ArrayList<Float> pbr = new ArrayList<Float>();
        ArrayList<Float> emissive = new ArrayList<Float>();
        int segments = 24;
        int rings = 14;
        float radius = 1.0f;
        for (int ring = 0; ring < rings; ring++) {
            float v0 = ring / (float)rings;
            float v1 = (ring + 1) / (float)rings;
            float theta0 = (float)(Math.PI * v0);
            float theta1 = (float)(Math.PI * v1);
            for (int segment = 0; segment < segments; segment++) {
                float u0 = segment / (float)segments;
                float u1 = (segment + 1) / (float)segments;
                float phi0 = (float)(Math.PI * 2.0 * u0);
                float phi1 = (float)(Math.PI * 2.0 * u1);
                addSphereTriangle(positions, normals, texCoords, colors, pbr, emissive, radius,
                        theta0, phi0, theta1, phi0, theta1, phi1, red, green, blue, metallic, roughness);
                addSphereTriangle(positions, normals, texCoords, colors, pbr, emissive, radius,
                        theta0, phi0, theta1, phi1, theta0, phi1, red, green, blue, metallic, roughness);
            }
        }
        return modelFromLists(graphics, "shadow-map-3d " + name + " sphere", positions, normals, texCoords, colors,
                pbr, emissive, roughness, metallic);
    }

    private static void addSphereTriangle(ArrayList<Float> positions, ArrayList<Float> normals,
            ArrayList<Float> texCoords, ArrayList<Float> colors, ArrayList<Float> pbr,
            ArrayList<Float> emissive, float radius, float theta0, float phi0, float theta1, float phi1,
            float theta2, float phi2, float red, float green, float blue, float metallic, float roughness) {
        addSphereVertex(positions, normals, texCoords, colors, pbr, emissive, radius, theta0, phi0,
                red, green, blue, metallic, roughness);
        addSphereVertex(positions, normals, texCoords, colors, pbr, emissive, radius, theta1, phi1,
                red, green, blue, metallic, roughness);
        addSphereVertex(positions, normals, texCoords, colors, pbr, emissive, radius, theta2, phi2,
                red, green, blue, metallic, roughness);
    }

    private static void addSphereVertex(ArrayList<Float> positions, ArrayList<Float> normals,
            ArrayList<Float> texCoords, ArrayList<Float> colors, ArrayList<Float> pbr,
            ArrayList<Float> emissive, float radius, float theta, float phi, float red, float green, float blue,
            float metallic, float roughness) {
        float sinTheta = (float)Math.sin(theta);
        float nx = (float)Math.cos(phi) * sinTheta;
        float ny = (float)Math.cos(theta);
        float nz = (float)Math.sin(phi) * sinTheta;
        addVertex(positions, normals, texCoords, colors, pbr, emissive, nx * radius, ny * radius, nz * radius,
                nx, ny, nz, phi / ((float)Math.PI * 2.0f), theta / (float)Math.PI, red, green, blue, metallic,
                roughness);
    }

    private static Model createCylinderModel(GraphicsContext graphics, String name, float radius, float height,
            float red, float green, float blue) {
        ArrayList<Float> positions = new ArrayList<Float>();
        ArrayList<Float> normals = new ArrayList<Float>();
        ArrayList<Float> texCoords = new ArrayList<Float>();
        ArrayList<Float> colors = new ArrayList<Float>();
        ArrayList<Float> pbr = new ArrayList<Float>();
        ArrayList<Float> emissive = new ArrayList<Float>();
        int segments = 24;
        float halfHeight = height * 0.5f;
        for (int i = 0; i < segments; i++) {
            float a0 = (float)(Math.PI * 2.0 * i / segments);
            float a1 = (float)(Math.PI * 2.0 * (i + 1) / segments);
            float x0 = (float)Math.cos(a0) * radius;
            float z0 = (float)Math.sin(a0) * radius;
            float x1 = (float)Math.cos(a1) * radius;
            float z1 = (float)Math.sin(a1) * radius;
            float nx0 = (float)Math.cos(a0);
            float nz0 = (float)Math.sin(a0);
            float nx1 = (float)Math.cos(a1);
            float nz1 = (float)Math.sin(a1);

            addVertex(positions, normals, texCoords, colors, pbr, emissive,
                    x0, -halfHeight, z0, nx0, 0.0f, nz0, 0.0f, 1.0f, red, green, blue, 0.0f, 0.72f);
            addVertex(positions, normals, texCoords, colors, pbr, emissive,
                    x1, -halfHeight, z1, nx1, 0.0f, nz1, 1.0f, 1.0f, red, green, blue, 0.0f, 0.72f);
            addVertex(positions, normals, texCoords, colors, pbr, emissive,
                    x1, halfHeight, z1, nx1, 0.0f, nz1, 1.0f, 0.0f, red, green, blue, 0.0f, 0.72f);
            addVertex(positions, normals, texCoords, colors, pbr, emissive,
                    x0, -halfHeight, z0, nx0, 0.0f, nz0, 0.0f, 1.0f, red, green, blue, 0.0f, 0.72f);
            addVertex(positions, normals, texCoords, colors, pbr, emissive,
                    x1, halfHeight, z1, nx1, 0.0f, nz1, 1.0f, 0.0f, red, green, blue, 0.0f, 0.72f);
            addVertex(positions, normals, texCoords, colors, pbr, emissive,
                    x0, halfHeight, z0, nx0, 0.0f, nz0, 0.0f, 0.0f, red, green, blue, 0.0f, 0.72f);

            addCylinderCap(positions, normals, texCoords, colors, pbr, emissive, false, x0, z0, x1, z1,
                    halfHeight, red, green, blue);
            addCylinderCap(positions, normals, texCoords, colors, pbr, emissive, true, x1, z1, x0, z0,
                    halfHeight, red, green, blue);
        }
        return modelFromLists(graphics, name, positions, normals, texCoords, colors, pbr, emissive, 0.72f, 0.0f);
    }

    private static void addCylinderCap(ArrayList<Float> positions, ArrayList<Float> normals,
            ArrayList<Float> texCoords, ArrayList<Float> colors, ArrayList<Float> pbr,
            ArrayList<Float> emissive, boolean top, float x0, float z0, float x1, float z1, float halfHeight,
            float red, float green, float blue) {
        float y = top ? halfHeight : -halfHeight;
        float ny = top ? 1.0f : -1.0f;
        addVertex(positions, normals, texCoords, colors, pbr, emissive, 0.0f, y, 0.0f,
                0.0f, ny, 0.0f, 0.5f, 0.5f, red, green, blue, 0.0f, 0.72f);
        addVertex(positions, normals, texCoords, colors, pbr, emissive, x0, y, z0,
                0.0f, ny, 0.0f, 0.0f, 0.0f, red, green, blue, 0.0f, 0.72f);
        addVertex(positions, normals, texCoords, colors, pbr, emissive, x1, y, z1,
                0.0f, ny, 0.0f, 1.0f, 0.0f, red, green, blue, 0.0f, 0.72f);
    }

    private static Model modelFromLists(GraphicsContext graphics, String name, ArrayList<Float> positions,
            ArrayList<Float> normals, ArrayList<Float> texCoords, ArrayList<Float> colors,
            ArrayList<Float> pbr, ArrayList<Float> emissive, float roughness, float metallic) {
        float[] sourcePositions = toFloatArray(positions);
        Mesh mesh = Mesh.positionColor3D(graphics, name, sourcePositions, toFloatArray(colors),
                toFloatArray(normals), toFloatArray(texCoords), toFloatArray(pbr), toFloatArray(emissive),
                bounds(sourcePositions));
        MeshPart meshPart = new MeshPart(name + " part", mesh, null, 0, mesh.vertexCount());
        PbrMaterial material = new PbrMaterial(name + " material")
                .roughnessFactor(roughness)
                .metallicFactor(metallic);
        return DefaultModel.singleNode(name, meshPart, material);
    }

    private static void addVertex(ArrayList<Float> positions, ArrayList<Float> normals,
            ArrayList<Float> texCoords, ArrayList<Float> colors, ArrayList<Float> pbr,
            ArrayList<Float> emissive, float x, float y, float z, float nx, float ny, float nz,
            float u, float v, float red, float green, float blue) {
        addVertex(positions, normals, texCoords, colors, pbr, emissive, x, y, z, nx, ny, nz, u, v,
                red, green, blue, 0.0f, 0.74f);
    }

    private static void addVertex(ArrayList<Float> positions, ArrayList<Float> normals,
            ArrayList<Float> texCoords, ArrayList<Float> colors, ArrayList<Float> pbr,
            ArrayList<Float> emissive, float x, float y, float z, float nx, float ny, float nz,
            float u, float v, float red, float green, float blue, float metallic, float roughness) {
        positions.add(x);
        positions.add(y);
        positions.add(z);
        normals.add(nx);
        normals.add(ny);
        normals.add(nz);
        texCoords.add(u);
        texCoords.add(v);
        colors.add(red);
        colors.add(green);
        colors.add(blue);
        colors.add(1.0f);
        pbr.add(1.0f);
        pbr.add(metallic);
        pbr.add(roughness);
        emissive.add(0.0f);
        emissive.add(0.0f);
        emissive.add(0.0f);
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 640;
    }

    private int splitViewportX() {
        return cascaded ? Math.max(1, framebufferWidth() / 2) : framebufferWidth();
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 480;
    }

    private void captureFrame(String path) {
        try {
            FrameBuffer frameBuffer = graphics.currentFrame().frameBuffer();
            ByteBuffer pixels = frameBuffer.readPixelsRgba8();
            FramebufferCapture.validateSceneFrame(frameBuffer.width(), frameBuffer.height(), pixels);
            FramebufferCapture.writePpm(path, frameBuffer.width(), frameBuffer.height(), pixels);
            logger.info("ShadowMap3DTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture ShadowMap3DTest framebuffer", e);
        }
    }

    private static BoundingBox bounds(float[] positions) {
        float minX = positions[0];
        float minY = positions[1];
        float minZ = positions[2];
        float maxX = minX;
        float maxY = minY;
        float maxZ = minZ;
        for (int i = 3; i < positions.length; i += 3) {
            minX = Math.min(minX, positions[i]);
            minY = Math.min(minY, positions[i + 1]);
            minZ = Math.min(minZ, positions[i + 2]);
            maxX = Math.max(maxX, positions[i]);
            maxY = Math.max(maxY, positions[i + 1]);
            maxZ = Math.max(maxZ, positions[i + 2]);
        }
        return BoundingBox.of(new Vector3(minX, minY, minZ), new Vector3(maxX, maxY, maxZ));
    }

    private static float[] toFloatArray(ArrayList<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
