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
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.controller.CameraAnchor2D;
import io.github.libfdx.graphics.camera.controller.CameraAnchor3D;
import io.github.libfdx.graphics.camera.controller.CameraInputBindings3D;
import io.github.libfdx.graphics.camera.controller.CinematicCameraController;
import io.github.libfdx.graphics.camera.controller.CinematicCameraPath3D;
import io.github.libfdx.graphics.camera.controller.FirstPersonCameraController3D;
import io.github.libfdx.graphics.camera.controller.FreeCameraController3D;
import io.github.libfdx.graphics.camera.controller.KeyframeCinematicCameraPath3D;
import io.github.libfdx.graphics.camera.controller.OrbitCameraController3D;
import io.github.libfdx.graphics.camera.controller.OrthographicCameraController3D;
import io.github.libfdx.graphics.camera.controller.ThirdPersonCameraController3D;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g3d.DefaultModel;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.DirectionalShadowMap3D;
import io.github.libfdx.graphics.g3d.Environment3D;
import io.github.libfdx.graphics.g3d.G3DAssetLoaders;
import io.github.libfdx.graphics.g3d.MeshPart;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.MaterialAttributes;
import io.github.libfdx.graphics.g3d.PbrAttributes;
import io.github.libfdx.graphics.g3d.SkyEnvironment3D;
import io.github.libfdx.graphics.g3d.SkyboxRenderer3D;
import io.github.libfdx.input.Input;
import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector2;
import io.github.libfdx.math.Vector3;
import io.github.libfdx.tests.TestFpsLogger;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiFont;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTextAlign;
import io.github.libfdx.ui.UiTextStyle;
import io.github.libfdx.ui.UiTheme;
import io.github.libfdx.ui.UiToolkit;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Shows the reusable camera controller families in one visual scene.
 *
 * @author xpenatan
 */
public final class CameraControllersShowcaseTest extends ApplicationAdapter {
    private static final String DUCK_ASSET = "data/g3d/gltf/Ducky/ducky.gltf";
    private static final String DRAGON_ASSET = "data/g3d/gltf/StanfordDragon/stanfordDragon.gltf";
    private static final String HELMET_ASSET = "data/g3d/gltf/DamagedHelmet/DamagedHelmet.gltf";
    private static final int CAMERA_EXAMPLE_COUNT = 6;
    private static final int EXAMPLE_COUNT = 7;
    private static final int CINEMATIC_3D_PANEL = 5;
    private static final int CINEMATIC_2D_PANEL = 6;
    private static final int PLAYER_INSTANCE_INDEX = 1;
    private static final int SELECTOR_HEIGHT = 92;
    private static final float FIRST_PERSON_X = 1.85f;
    private static final float FIRST_PERSON_Y = 0.0f;
    private static final float FIRST_PERSON_Z = 2.45f;
    private static final float PLAYER_SPEED = 3.6f;
    private static final float PLAYER_FAST_MULTIPLIER = 1.8f;
    private static final float SCENE_GROUND_Y = -0.02f;
    private static final String FREETYPE_FONT_ASSET = "font/freetype/lsans.ttf";
    private static final String[] PANEL_LABELS = {
            "FREE CAMERA",
            "FIRST PERSON",
            "THIRD PERSON",
            "ORBIT",
            "ORTHOGRAPHIC",
            "CINEMATIC 3D",
            "CINEMATIC 2D"
    };
    private static final float CINEMATIC_PATH_DURATION = 20.0f;
    private static final float CINEMATIC_PATH_SECONDS_PER_SECOND = 0.336f;
    private static final float CINEMATIC_2D_PATH_DURATION = 18.0f;
    private static final float CINEMATIC_2D_SECONDS_PER_SECOND = 0.55f;
    private static final float CINEMATIC_2D_VIEW_HALF_HEIGHT = 2.35f;
    private static final float CINEMATIC_2D_PLAYER_X = 0.65f;
    private static final float CINEMATIC_2D_PLAYER_Y = -0.16f;
    private static final float CINEMATIC_2D_PLAYER_FACING = 1.0f;
    private static final float[] CINEMATIC_CAMERA_POINTS = {
            -0.55f, 1.85f, 2.75f,
            0.35f, 1.70f, 2.85f,
            1.55f, 1.22f, 1.55f,
            2.30f, 1.05f, 1.25f,
            3.40f, 1.02f, 1.10f,
            4.40f, 1.18f, -0.60f,
            5.30f, 1.25f, -3.40f,
            4.20f, 1.25f, -5.90f,
            5.35f, 1.42f, -2.05f,
            4.20f, 1.65f, 2.20f,
            1.00f, 1.84f, 2.85f,
            -0.55f, 1.85f, 2.75f
    };
    private static final float[] CINEMATIC_LOOK_AT_POINTS = {
            -2.10f, 1.46f, -2.45f,
            -0.80f, 1.28f, -2.70f,
            1.90f, 1.05f, -2.35f,
            2.20f, 0.55f, -0.60f,
            3.05f, 0.52f, -0.95f,
            2.75f, 0.75f, -2.00f,
            2.45f, 0.90f, -4.25f,
            2.45f, 0.95f, -4.25f,
            2.45f, 0.90f, -4.25f,
            1.90f, 0.95f, -2.35f,
            -1.50f, 1.35f, -2.50f,
            -2.10f, 1.46f, -2.45f
    };
    private static final float[] CINEMATIC_2D_CAMERA_POINTS = {
            -3.90f, -0.20f,
            -2.25f, -0.42f,
            -0.70f, -0.08f,
            0.95f, 0.10f,
            2.72f, -0.18f,
            3.65f, 0.10f,
            1.90f, 0.45f,
            -0.10f, 0.26f,
            -2.15f, 0.12f,
            -3.90f, -0.20f
    };

    private final long exitAfterFrames;
    private final Camera[] cameras = new Camera[CAMERA_EXAMPLE_COUNT];
    private final int[] selectorX = new int[EXAMPLE_COUNT];
    private final int[] selectorWidth = new int[EXAMPLE_COUNT];
    private final MutableAnchor3D firstPersonAnchor =
            new MutableAnchor3D(FIRST_PERSON_X, FIRST_PERSON_Y, FIRST_PERSON_Z, 0.0f, 1.0f, 0.0f);
    private final MutableAnchor3D thirdPersonAnchor =
            new MutableAnchor3D(0.0f, 0.0f, 0.0f, 0.22f, 0.96f, 0.18f);
    private final MutableAnchor2D cinematic2DAnchor = new MutableAnchor2D(0.0f, 0.0f);
    private final Camera cinematic2DCamera = new Camera();

    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private Input input;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private AssetManager assets;
    private DirectionalLight sun;
    private DirectionalShadowMap3D shadowMap;
    private SkyboxRenderer3D skybox;
    private SkyEnvironment3D skyEnvironment;
    private Environment3D environment;
    private ModelBatch batch;
    private SpriteBatch spriteBatch;
    private UiRoot uiRoot;
    private Texture cinematic2DWhiteTexture;
    private Texture cinematic2DPlayerTexture;
    private Texture cinematic2DTreeTexture;
    private Texture cinematic2DHouseTexture;
    private Texture cinematic2DCloudTexture;
    private Model duckModel;
    private Model dragonModel;
    private Model helmetModel;
    private Model groundModel;
    private DefaultModelInstance[] sceneInstances;
    private DefaultModelInstance playerInstance;
    private final Matrix4 playerTransform = new Matrix4();
    private float playerX = 1.45f;
    private float playerZ = 0.45f;
    private float playerYaw;
    private FreeCameraController3D freeController;
    private FirstPersonCameraController3D firstPersonController;
    private ThirdPersonCameraController3D thirdPersonController;
    private OrbitCameraController3D orbitController;
    private OrthographicCameraController3D orthographicController;
    private CinematicCameraController cinematicController3D;
    private CinematicCameraController cinematicController2D;
    private CinematicCameraPath3D cinematicPath3D;
    private InputAdapter cinematicInput;
    private boolean cinematicDragging;
    private int cinematicDragPanel = -1;
    private int cinematicLastX;
    private int cinematicLastY;
    private int activePanel;
    private int layoutWidth = 1;
    private int layoutHeight = 1;
    private int inputWidth = 1;
    private int inputHeight = 1;
    private float cinematicPathTimeOffset;
    private float cinematic2DPathTimeOffset;
    private float cinematic2DCameraTargetX;
    private float cinematic2DCameraTargetY;
    private boolean created;
    private String capturePath;
    private int captureEvery;
    private int capturedFrames;
    private boolean captured;
    private boolean validationSweep;
    private boolean validationTurnInPlace;
    private boolean validationPlayerAutoMove;
    private long renderedFrames;

    /**
     * Creates a camera controllers showcase test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public CameraControllersShowcaseTest(long exitAfterFrames) {
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
        input = fdx.input();
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "CameraControllersShowcaseTest");
        assets = new DefaultAssetManager(fdx.files());
        G3DAssetLoaders.register(assets, graphics);

        sun = new DirectionalLight()
                .direction(-0.42f, -0.82f, -0.36f)
                .color(new Color(1.0f, 0.88f, 0.68f, 1.0f))
                .intensity(2.15f);
        shadowMap = new DirectionalShadowMap3D(graphics, 2048, 2048)
                .bounds(0.0f, 0.4f, -1.7f, 8.0f, 0.1f, 32.0f)
                .bias(0.026f)
                .strength(0.58f);
        skyEnvironment = new SkyEnvironment3D()
                .zenithColor(0.18f, 0.36f, 0.62f)
                .horizonColor(0.70f, 0.82f, 0.94f)
                .nadirColor(0.32f, 0.36f, 0.34f)
                .sunColor(1.0f, 0.78f, 0.42f)
                .sunDirection(0.42f, 0.82f, 0.36f)
                .intensity(0.72f, 0.58f)
                .sunIntensity(0.36f)
                .horizonBlend(0.44f);
        skybox = new SkyboxRenderer3D(graphics)
                .zenithColor(0.18f, 0.36f, 0.62f)
                .horizonColor(0.70f, 0.82f, 0.94f)
                .nadirColor(0.32f, 0.36f, 0.34f)
                .sunColor(1.0f, 0.78f, 0.42f, 0.62f)
                .sunDirection(0.42f, 0.82f, 0.36f)
                .sunSize(0.12f);
        environment = new Environment3D()
                .ambientColor(new Color(0.05f, 0.055f, 0.065f, 1.0f))
                .skyEnvironment(skyEnvironment)
                .directionalShadowMap(shadowMap)
                .fog(0.56f, 0.66f, 0.78f, 0.28f, 22.0f, 55.0f)
                .add(sun);
        batch = new ModelBatch(graphics).environment(environment);
        spriteBatch = new SpriteBatch(graphics, 256);
        createCinematic2DTextures();
        createSceneModel();
        createCamerasAndControllers(fdx);
        createCinematicInput();
        uiRoot = new UiToolkit(fdx.files())
                .theme(selectorTheme())
                .root(display, graphics)
                .input(input);
        uiRoot.setContent(this::buildSelectorUi);
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureEvery = Integer.parseInt(System.getProperty("libfdx.test.captureEvery", "0"));
        activePanel = initialPanel();
        validationSweep = Boolean.parseBoolean(System.getProperty(
                "libfdx.test.cameraControllers.validationSweep", "false"));
        validationTurnInPlace = Boolean.parseBoolean(System.getProperty(
                "libfdx.test.cameraControllers.validationTurnInPlace", "false"));
        validationPlayerAutoMove = Boolean.parseBoolean(System.getProperty(
                "libfdx.test.cameraControllers.autoMovePlayer", "false"));

        created = true;
        logger.info("CameraControllersShowcaseTest created with graphics provider " + graphics.providerId());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        int width = framebufferWidth();
        int height = framebufferHeight();
        updateLayout(width, height);
        updateControllerInputState();
        float seconds = renderedFrames / 60.0f;
        updatePlayer(deltaSeconds, seconds);
        updateAnchors(seconds);
        updateControllers(deltaSeconds);
        updateValidationSweepCamera(seconds);

        if (activePanel == CINEMATIC_2D_PANEL) {
            renderCinematic2DPanel(width, height);
        }
        else {
            shadowMap.render(sun, sceneInstances);
            renderPanel(activePanel, 0, 0, width, height, LoadOp.clear(0.55f, 0.64f, 0.73f, 1.0f));
        }
        renderOverlay(width, height);
        captureIfRequested();

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
        if (cinematicInput != null && input != null) {
            input.removeProcessor(cinematicInput);
            cinematicInput = null;
        }
        disposeControllers();
        if (uiRoot != null) {
            uiRoot.dispose();
            uiRoot = null;
        }
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (spriteBatch != null) {
            spriteBatch.dispose();
            spriteBatch = null;
        }
        disposeTexture(cinematic2DWhiteTexture);
        cinematic2DWhiteTexture = null;
        disposeTexture(cinematic2DPlayerTexture);
        cinematic2DPlayerTexture = null;
        disposeTexture(cinematic2DTreeTexture);
        cinematic2DTreeTexture = null;
        disposeTexture(cinematic2DHouseTexture);
        cinematic2DHouseTexture = null;
        disposeTexture(cinematic2DCloudTexture);
        cinematic2DCloudTexture = null;
        if (skybox != null) {
            skybox.dispose();
            skybox = null;
        }
        if (shadowMap != null) {
            shadowMap.dispose();
            shadowMap = null;
        }
        sceneInstances = null;
        playerInstance = null;
        if (groundModel != null) {
            groundModel.dispose();
            groundModel = null;
        }
        if (assets != null) {
            assets.dispose();
            assets = null;
        }
        duckModel = null;
        dragonModel = null;
        helmetModel = null;
        if (!created) {
            throw new FdxException("CameraControllersShowcaseTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("CameraControllersShowcaseTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        logger.info("CameraControllersShowcaseTest rendered " + renderedFrames + " frames");
    }

    @Override
    public void resize(int width, int height) {
        if (uiRoot != null) {
            uiRoot.resize(width, height);
        }
    }

    private void createSceneModel() {
        assets.load(AssetDescriptor.of(DUCK_ASSET, Model.class));
        assets.load(AssetDescriptor.of(DRAGON_ASSET, Model.class));
        assets.load(AssetDescriptor.of(HELMET_ASSET, Model.class));
        assets.finishLoading();
        duckModel = assets.get(DUCK_ASSET, Model.class);
        dragonModel = assets.get(DRAGON_ASSET, Model.class);
        helmetModel = assets.get(HELMET_ASSET, Model.class);

        groundModel = createTexturedGroundModel();
        DefaultModelInstance ground = new DefaultModelInstance(groundModel);
        ground.transform().setToTranslation(0.0f, SCENE_GROUND_Y, -1.2f);
        sceneInstances = new DefaultModelInstance[] {
                ground,
                sceneInstance(duckModel, 0.0f, 0.0f, 0.0f, 0.0f,
                        0.85f, 0.85f, 0.85f),
                sceneInstance(dragonModel, -2.10f, 0.0f, -2.45f, 28.0f,
                        1.45f, 1.45f, 1.45f),
                sceneInstance(helmetModel, 1.90f, 0.58f, -2.35f, -24.0f,
                        0.72f, 0.72f, 0.72f),
                sceneInstance(duckModel, 2.20f, 0.0f, -0.60f, 22.0f,
                        0.48f, 0.48f, 0.48f),
                sceneInstance(duckModel, 3.05f, 0.0f, -0.95f, -32.0f,
                        0.42f, 0.42f, 0.42f),
                sceneInstance(dragonModel, 2.45f, 0.0f, -4.25f, -38.0f,
                        0.62f, 0.62f, 0.62f),
                sceneInstance(helmetModel, -1.62f, 0.38f, -3.55f, 34.0f,
                        0.42f, 0.42f, 0.42f)
        };
        playerInstance = sceneInstances[PLAYER_INSTANCE_INDEX];
        updatePlayerTransform();
    }

    private Model createTexturedGroundModel() {
        Texture texture = graphics.device().createTexture(TextureDescriptor
                .rgba8("camera-controller-showcase-ground-texture", 128, 128)
                .wrap(TextureWrap.REPEAT));
        graphics.device().writeTexture(texture, checkerPixels(128, 128));
        float halfWidth = 7.0f;
        float halfDepth = 7.0f;
        float tile = 7.0f;
        float[] positions = {
                -halfWidth, 0.0f, -halfDepth,
                halfWidth, 0.0f, -halfDepth,
                halfWidth, 0.0f, halfDepth,
                -halfWidth, 0.0f, -halfDepth,
                halfWidth, 0.0f, halfDepth,
                -halfWidth, 0.0f, halfDepth
        };
        float[] normals = {
                0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f
        };
        float[] texCoords = {
                0.0f, tile,
                tile, tile,
                tile, 0.0f,
                0.0f, tile,
                tile, 0.0f,
                0.0f, 0.0f
        };
        float[] colors = repeated4(6, 1.0f, 1.0f, 1.0f, 1.0f);
        float[] pbr = repeated3(6, 1.0f, 0.0f, 0.86f);
        float[] emissive = repeated3(6, 0.0f, 0.0f, 0.0f);
        Mesh mesh = Mesh.positionColor3D(graphics, "camera-controller-showcase-ground mesh",
                positions, colors, normals, texCoords, pbr, emissive,
                io.github.libfdx.math.BoundingBox.of(new Vector3(-halfWidth, 0.0f, -halfDepth),
                        new Vector3(halfWidth, 0.0f, halfDepth)));
        MeshPart meshPart = new MeshPart("camera-controller-showcase-ground part", mesh, null, 0, mesh.vertexCount());
        Material material = new Material("camera-controller-showcase-ground material")
                .set(MaterialAttributes.baseColor(
                        0.92f, 0.90f, 0.82f, 1.0f))
                .set(MaterialAttributes.baseColorTexture(texture))
                .set(PbrAttributes.roughnessFactor(0.86f));
        return DefaultModel.singleNode(
                "camera-controller-showcase-ground", meshPart, material,
                texture);
    }

    private ByteBuffer checkerPixels(int width, int height) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean bright = (((x / 16) + (y / 16)) & 1) == 0;
                int red = bright ? 186 : 138;
                int green = bright ? 178 : 146;
                int blue = bright ? 132 : 104;
                pixels.put((byte)red);
                pixels.put((byte)green);
                pixels.put((byte)blue);
                pixels.put((byte)255);
            }
        }
        pixels.flip();
        return pixels;
    }

    private void createCinematic2DTextures() {
        cinematic2DWhiteTexture = createSolidTexture("camera-controller-cinematic-2d-white", 1, 1,
                255, 255, 255, 255);
        cinematic2DPlayerTexture = createCinematicPlayerTexture();
        cinematic2DTreeTexture = createCinematicTreeTexture();
        cinematic2DHouseTexture = createCinematicHouseTexture();
        cinematic2DCloudTexture = createCinematicCloudTexture();
    }

    private Texture createSolidTexture(String name, int width, int height, int red, int green, int blue, int alpha) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        for (int i = 0; i < width * height; i++) {
            putPixel(pixels, red, green, blue, alpha);
        }
        pixels.flip();
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8(name, width, height));
        graphics.device().writeTexture(texture, pixels);
        return texture;
    }

    private Texture createCinematicPlayerTexture() {
        int width = 48;
        int height = 64;
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = 0;
                int green = 0;
                int blue = 0;
                int alpha = 0;
                if (insideEllipse(x, y, 24.0f, 16.0f, 9.0f, 10.0f)) {
                    red = 246;
                    green = 188;
                    blue = 134;
                    alpha = 255;
                }
                else if (insideEllipse(x, y, 24.0f, 36.0f, 14.0f, 18.0f)) {
                    red = 58;
                    green = 114;
                    blue = 204;
                    alpha = 255;
                }
                else if ((x >= 13 && x <= 20 && y >= 50 && y <= 62)
                        || (x >= 28 && x <= 35 && y >= 50 && y <= 62)) {
                    red = 43;
                    green = 59;
                    blue = 91;
                    alpha = 255;
                }
                else if ((x >= 8 && x <= 14 && y >= 31 && y <= 46)
                        || (x >= 34 && x <= 40 && y >= 31 && y <= 46)) {
                    red = 246;
                    green = 188;
                    blue = 134;
                    alpha = 255;
                }
                if (alpha > 0 && x > 29 && y > 24 && y < 48) {
                    red = Math.min(255, red + 28);
                    green = Math.min(255, green + 28);
                    blue = Math.min(255, blue + 28);
                }
                putPixel(pixels, red, green, blue, alpha);
            }
        }
        pixels.flip();
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8(
                "camera-controller-cinematic-2d-player", width, height));
        graphics.device().writeTexture(texture, pixels);
        return texture;
    }

    private Texture createCinematicTreeTexture() {
        int width = 48;
        int height = 72;
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = 0;
                int green = 0;
                int blue = 0;
                int alpha = 0;
                if (x >= 20 && x <= 28 && y >= 36 && y <= 70) {
                    red = 118;
                    green = 78;
                    blue = 45;
                    alpha = 255;
                }
                if (insideEllipse(x, y, 16.0f, 31.0f, 14.0f, 18.0f)
                        || insideEllipse(x, y, 30.0f, 27.0f, 16.0f, 20.0f)
                        || insideEllipse(x, y, 23.0f, 14.0f, 18.0f, 18.0f)) {
                    red = 50;
                    green = 143;
                    blue = 92;
                    alpha = 255;
                }
                if (alpha > 0 && green > red && x > 26) {
                    red = Math.min(255, red + 22);
                    green = Math.min(255, green + 20);
                    blue = Math.min(255, blue + 14);
                }
                putPixel(pixels, red, green, blue, alpha);
            }
        }
        pixels.flip();
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8(
                "camera-controller-cinematic-2d-tree", width, height));
        graphics.device().writeTexture(texture, pixels);
        return texture;
    }

    private Texture createCinematicHouseTexture() {
        int width = 72;
        int height = 56;
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = 0;
                int green = 0;
                int blue = 0;
                int alpha = 0;
                if (x >= 10 && x <= 62 && y >= 24 && y <= 52) {
                    red = 226;
                    green = 206;
                    blue = 155;
                    alpha = 255;
                }
                int roofEdge = Math.abs(x - 36);
                if (y >= 8 && y < 28 && roofEdge < 32 - y / 2) {
                    red = 169;
                    green = 71;
                    blue = 58;
                    alpha = 255;
                }
                if (x >= 31 && x <= 41 && y >= 34 && y <= 52) {
                    red = 95;
                    green = 82;
                    blue = 70;
                    alpha = 255;
                }
                if ((x >= 17 && x <= 27 && y >= 31 && y <= 40)
                        || (x >= 46 && x <= 56 && y >= 31 && y <= 40)) {
                    red = 105;
                    green = 168;
                    blue = 202;
                    alpha = 255;
                }
                putPixel(pixels, red, green, blue, alpha);
            }
        }
        pixels.flip();
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8(
                "camera-controller-cinematic-2d-house", width, height));
        graphics.device().writeTexture(texture, pixels);
        return texture;
    }

    private Texture createCinematicCloudTexture() {
        int width = 64;
        int height = 32;
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = 0;
                if (insideEllipse(x, y, 18.0f, 18.0f, 16.0f, 10.0f)
                        || insideEllipse(x, y, 32.0f, 13.0f, 15.0f, 11.0f)
                        || insideEllipse(x, y, 45.0f, 18.0f, 16.0f, 9.0f)) {
                    alpha = 220;
                }
                putPixel(pixels, 255, 255, 255, alpha);
            }
        }
        pixels.flip();
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8(
                "camera-controller-cinematic-2d-cloud", width, height));
        graphics.device().writeTexture(texture, pixels);
        return texture;
    }

    private static boolean insideEllipse(int x, int y, float centerX, float centerY, float radiusX, float radiusY) {
        float dx = (x - centerX) / radiusX;
        float dy = (y - centerY) / radiusY;
        return dx * dx + dy * dy <= 1.0f;
    }

    private static void putPixel(ByteBuffer pixels, int red, int green, int blue, int alpha) {
        pixels.put((byte)red);
        pixels.put((byte)green);
        pixels.put((byte)blue);
        pixels.put((byte)alpha);
    }

    private float[] repeated4(int count, float x, float y, float z, float w) {
        float[] values = new float[count * 4];
        int index = 0;
        for (int i = 0; i < count; i++) {
            values[index++] = x;
            values[index++] = y;
            values[index++] = z;
            values[index++] = w;
        }
        return values;
    }

    private float[] repeated3(int count, float x, float y, float z) {
        float[] values = new float[count * 3];
        int index = 0;
        for (int i = 0; i < count; i++) {
            values[index++] = x;
            values[index++] = y;
            values[index++] = z;
        }
        return values;
    }

    private DefaultModelInstance sceneInstance(Model model, float x, float y,
            float z, float yawDegrees,
            float scaleX, float scaleY, float scaleZ) {
        float halfYaw = (float)Math.toRadians(yawDegrees) * 0.5f;
        DefaultModelInstance instance = new DefaultModelInstance(model);
        instance.transform().setToTrs(x, y, z, 0.0f,
                (float)Math.sin(halfYaw), 0.0f,
                (float)Math.cos(halfYaw), scaleX, scaleY, scaleZ);
        return instance;
    }

    private void createCamerasAndControllers(Fdx fdx) {
        for (int i = 0; i < cameras.length; i++) {
            cameras[i] = perspectiveCamera();
        }
        cameras[1].direction(-0.62f, -0.20f, -0.76f).update();
        cameras[4].projection(CameraProjection.ORTHOGRAPHIC).zoom(0.016f);

        CameraInputBindings3D bindings = CameraInputBindings3D.defaults()
                .lookButton(MouseButton.LEFT)
                .touchLookButton(MouseButton.LEFT);
        freeController = new FreeCameraController3D(input, cameras[0])
                .position(0.0f, 1.9f, 5.0f)
                .speedRange(0.001f, cameras[0].far())
                .inputBindings(bindings)
                .pointerRegion((x, y) -> activePanel == 0 && containsActiveView(x, y))
                .activationListener(() -> selectPanel(0));
        firstPersonController = new FirstPersonCameraController3D(input, cameras[1], firstPersonAnchor)
                .eyeOffset(0.0f, 1.45f, 0.0f)
                .inputBindings(bindings)
                .pointerRegion((x, y) -> activePanel == 1 && containsActiveView(x, y))
                .activationListener(() -> selectPanel(1));
        thirdPersonController = new ThirdPersonCameraController3D(input, cameras[2], thirdPersonAnchor)
                .distanceRange(1.5f, 7.0f)
                .distance(4.2f)
                .offsets(0.55f, 1.55f, 0.70f)
                .damping(7.0f)
                .inputBindings(bindings)
                .pointerRegion((x, y) -> activePanel == 2 && containsActiveView(x, y))
                .activationListener(() -> selectPanel(2));
        orbitController = new OrbitCameraController3D(input, cameras[3])
                .position(3.2f, 2.3f, 3.8f, 0.0f, 0.45f, 0.0f)
                .radiusRange(1.5f, 8.0f)
                .autoOrbit(true, 0.45f, exitAfterFrames, 0.0f, 90.0f)
                .inputBindings(bindings)
                .pointerRegion((x, y) -> activePanel == 3 && containsActiveView(x, y))
                .activationListener(() -> selectPanel(3));
        orthographicController = new OrthographicCameraController3D(input, cameras[4])
                .position(0.0f, 4.4f, 5.8f)
                .zoomRange(0.006f, 0.05f)
                .inputBindings(bindings)
                .pointerRegion((x, y) -> activePanel == 4 && containsActiveView(x, y))
                .keyboardEnabled(false)
                .activationListener(() -> selectPanel(4));
        cinematicPath3D = new KeyframeCinematicCameraPath3D(CINEMATIC_PATH_DURATION,
                CINEMATIC_CAMERA_POINTS, CINEMATIC_LOOK_AT_POINTS).loop(true);
        cinematicController3D = new CinematicCameraController(cameras[CINEMATIC_3D_PANEL])
                .path3D(cinematicPath3D)
                .pathPlaybackSpeed(0.0f)
                .damping(0.0f);
        cinematic2DCamera
                .projection(CameraProjection.ORTHOGRAPHIC)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 100.0f)
                .position(0.0f, 0.0f, 10.0f)
                .direction(0.0f, 0.0f, -1.0f)
                .up(0.0f, 1.0f, 0.0f)
                .zoom(0.76f)
                .update();
        cinematicController2D = new CinematicCameraController(cinematic2DCamera)
                .anchor(cinematic2DAnchor)
                .offset2D(0.0f, 0.0f)
                .zoom(0.76f)
                .damping(3.8f);
    }

    private void createCinematicInput() {
        if (input == null) {
            return;
        }
        cinematicInput = new InputAdapter() {
            @Override
            public boolean pointerDown(PointerEvent event) {
                if (event.button() != MouseButton.LEFT) {
                    return false;
                }
                int selector = selectorAt(event.x(), event.y());
                if (selector >= 0) {
                    selectPanel(selector);
                    return true;
                }
                if (containsActiveView(event.x(), event.y())
                        && (activePanel == CINEMATIC_3D_PANEL || activePanel == CINEMATIC_2D_PANEL)) {
                    cinematicDragging = true;
                    cinematicDragPanel = activePanel;
                    cinematicLastX = event.x();
                    cinematicLastY = event.y();
                }
                return false;
            }

            @Override
            public boolean keyDown(KeyEvent event) {
                int selected = exampleIndex(event.key());
                if (selected >= 0) {
                    selectPanel(selected);
                    return true;
                }
                return false;
            }

            @Override
            public boolean pointerUp(PointerEvent event) {
                if (cinematicDragging && event.button() == MouseButton.LEFT) {
                    dragCinematic(event.x(), event.y());
                    cinematicDragging = false;
                    cinematicDragPanel = -1;
                }
                return false;
            }

            @Override
            public boolean pointerMoved(PointerEvent event) {
                if (cinematicDragging) {
                    dragCinematic(event.x(), event.y());
                }
                return false;
            }

            @Override
            public boolean scrolled(PointerEvent event) {
                if (!containsActiveView(event.x(), event.y())) {
                    return false;
                }
                if (activePanel == CINEMATIC_3D_PANEL) {
                    cinematicPathTimeOffset += event.scrollY() * 0.75f;
                }
                else if (activePanel == CINEMATIC_2D_PANEL) {
                    cinematic2DPathTimeOffset += event.scrollY() * 0.65f;
                }
                return false;
            }
        };
        input.addProcessor(cinematicInput);
    }

    private Camera perspectiveCamera() {
        return new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(62.0f)
                .viewport(1.0f, 1.0f)
                .nearFar(0.1f, 60.0f)
                .position(0.0f, 1.7f, 5.0f)
                .direction(0.0f, -0.18f, -1.0f)
                .up(0.0f, 1.0f, 0.0f)
                .update();
    }

    private void updateAnchors(float seconds) {
        firstPersonAnchor.set(FIRST_PERSON_X, FIRST_PERSON_Y, FIRST_PERSON_Z);
        firstPersonAnchor.setUp(0.0f, 1.0f, 0.0f);

        thirdPersonAnchor.set(playerX, 0.0f, playerZ);
        thirdPersonAnchor.setUp(0.0f, 1.0f, 0.0f);

        if (cinematicController3D != null) {
            cinematicController3D.pathTime(seconds * CINEMATIC_PATH_SECONDS_PER_SECOND + cinematicPathTimeOffset);
        }
        updateCinematic2DAnchor(seconds);
    }

    private void updatePlayer(float deltaSeconds, float seconds) {
        float moveX = 0.0f;
        float moveZ = 0.0f;
        if (activePanel == 2 && input != null) {
            if (input.isKeyPressed(Key.W) || input.isKeyPressed(Key.UP)) {
                moveZ -= 1.0f;
            }
            if (input.isKeyPressed(Key.S) || input.isKeyPressed(Key.DOWN)) {
                moveZ += 1.0f;
            }
            if (input.isKeyPressed(Key.A) || input.isKeyPressed(Key.LEFT)) {
                moveX -= 1.0f;
            }
            if (input.isKeyPressed(Key.D) || input.isKeyPressed(Key.RIGHT)) {
                moveX += 1.0f;
            }
        }
        if (validationPlayerAutoMove && activePanel == 2) {
            moveX += (float)Math.sin(seconds * 0.70f) * 0.55f;
            moveZ -= 0.85f;
        }
        float length = (float)Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (length > 0.0001f) {
            float invLength = 1.0f / length;
            moveX *= invLength;
            moveZ *= invLength;
            float speed = PLAYER_SPEED;
            if (activePanel == 2 && input != null
                    && (input.isKeyPressed(Key.SHIFT_LEFT) || input.isKeyPressed(Key.SHIFT_RIGHT))) {
                speed *= PLAYER_FAST_MULTIPLIER;
            }
            playerX = clamp(playerX + moveX * speed * deltaSeconds, -4.8f, 4.8f);
            playerZ = clamp(playerZ + moveZ * speed * deltaSeconds, -5.8f, 4.2f);
            playerYaw = (float)Math.atan2(moveX, -moveZ);
            updatePlayerTransform();
        }
    }

    private void updateCinematic2DAnchor(float seconds) {
        float timeSeconds = seconds * CINEMATIC_2D_SECONDS_PER_SECOND + cinematic2DPathTimeOffset;
        sampleCinematic2DCameraTarget(timeSeconds);
        cinematic2DAnchor.set(cinematic2DCameraTargetX, cinematic2DCameraTargetY);
        if (cinematicController2D != null) {
            float zoom = 0.76f + (float)Math.sin(timeSeconds * 0.28f) * 0.04f;
            cinematicController2D
                    .zoom(zoom)
                    .rotation((float)Math.sin(timeSeconds * 0.16f) * 0.012f);
        }
    }

    private void sampleCinematic2DCameraTarget(float timeSeconds) {
        float normalized = timeSeconds / CINEMATIC_2D_PATH_DURATION;
        normalized = normalized - (float)Math.floor(normalized);
        int pointCount = CINEMATIC_2D_CAMERA_POINTS.length / 2;
        float scaled = normalized * (pointCount - 1);
        int left = Math.min((int)scaled, pointCount - 2);
        int right = left + 1;
        float t = smoothStep(scaled - left);
        int leftOffset = left * 2;
        int rightOffset = right * 2;
        float leftX = CINEMATIC_2D_CAMERA_POINTS[leftOffset];
        float leftY = CINEMATIC_2D_CAMERA_POINTS[leftOffset + 1];
        float rightX = CINEMATIC_2D_CAMERA_POINTS[rightOffset];
        float rightY = CINEMATIC_2D_CAMERA_POINTS[rightOffset + 1];
        cinematic2DCameraTargetX = lerp(leftX, rightX, t);
        cinematic2DCameraTargetY = lerp(leftY, rightY, t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothStep(float t) {
        t = clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private void updatePlayerTransform() {
        float halfYaw = playerYaw * 0.5f;
        playerInstance.transform(playerTransform.setToTrs(playerX, 0.0f, playerZ,
                0.0f, (float)Math.sin(halfYaw), 0.0f, (float)Math.cos(halfYaw),
                0.85f, 0.85f, 0.85f));
    }

    private void updateControllers(float deltaSeconds) {
        freeController.update(deltaSeconds);
        firstPersonController.update(deltaSeconds);
        thirdPersonController.update(deltaSeconds);
        orbitController.update(deltaSeconds);
        orthographicController.update(deltaSeconds);
        cinematicController3D.update(deltaSeconds);
        cinematicController2D.update(deltaSeconds);
    }

    private void updateValidationSweepCamera(float seconds) {
        if (!validationSweep || activePanel >= CAMERA_EXAMPLE_COUNT) {
            return;
        }
        Camera camera = cameras[activePanel];
        float yaw = -0.85f + renderedFrames * 0.035f;
        if (validationTurnInPlace) {
            float pitch = -0.10f + (float)Math.sin(seconds * 2.20f) * 0.42f;
            float pitchScale = (float)Math.cos(pitch);
            camera
                    .direction((float)Math.sin(yaw) * pitchScale, (float)Math.sin(pitch),
                            -(float)Math.cos(yaw) * pitchScale)
                    .up(0.0f, 1.0f, 0.0f)
                    .update();
            return;
        }
        float radius = activePanel == 2 ? 5.0f : 5.6f;
        float eyeX = playerX + (float)Math.sin(yaw) * radius;
        float eyeY = activePanel == 2 ? 2.2f : 2.1f + (float)Math.sin(seconds * 1.2f) * 0.25f;
        float eyeZ = playerZ + (float)Math.cos(yaw) * radius;
        camera
                .position(eyeX, eyeY, eyeZ)
                .lookAt(playerX, 0.65f, playerZ - 0.9f)
                .up(0.0f, 1.0f, 0.0f)
                .update();
    }

    private void updateControllerInputState() {
        freeController.keyboardEnabled(activePanel == 0);
        firstPersonController.keyboardEnabled(activePanel == 1);
        thirdPersonController.keyboardEnabled(activePanel == 2);
        orbitController.keyboardEnabled(activePanel == 3);
        orthographicController.keyboardEnabled(activePanel == 4);
    }

    private void renderPanel(int index, int x, int y, int width, int height, LoadOp loadOp) {
        Camera camera = cameras[index];
        camera.viewport(width, height);
        RenderPass pass = graphics.currentFrame().commandEncoder().beginRenderPass(RenderPassDescriptor
                .color(graphics.currentFrame().colorAttachment(), loadOp, StoreOp.store())
                .depthClear(1.0f)
                .label("camera-controllers panel"));
        pass.setViewport(x, y, width, height);
        pass.setScissor(x, y, width, height);
        skybox.begin(pass);
        skybox.draw(camera);
        skybox.end();
        batch.begin(pass, camera);
        for (int i = 0; i < sceneInstances.length; i++) {
            if (index == CINEMATIC_3D_PANEL && i == PLAYER_INSTANCE_INDEX) {
                continue;
            }
            batch.render(sceneInstances[i]);
        }
        batch.end();
        pass.end();
    }

    private void renderCinematic2DPanel(int width, int height) {
        cinematic2DCamera.viewport(width, height).update();
        spriteBatch.viewport(width, height);
        spriteBatch.begin(LoadOp.clear(0.44f, 0.68f, 0.92f, 1.0f));
        drawScreen(cinematic2DWhiteTexture, -1.0f, -1.0f, 2.0f, 2.0f,
                0.48f, 0.72f, 0.95f, 1.0f);
        drawWorld(cinematic2DCloudTexture, -5.6f, 1.78f, 1.15f, 0.50f, 1.0f, 1.0f, 1.0f, 0.82f,
                width, height);
        drawWorld(cinematic2DCloudTexture, -0.8f, 2.02f, 1.30f, 0.56f, 1.0f, 1.0f, 1.0f, 0.62f,
                width, height);
        drawWorld(cinematic2DCloudTexture, 4.2f, 1.70f, 1.05f, 0.46f, 1.0f, 1.0f, 1.0f, 0.72f,
                width, height);
        drawWorld(cinematic2DWhiteTexture, -12.0f, -4.20f, 24.0f, 5.05f, 0.31f, 0.62f, 0.37f, 1.0f,
                width, height);
        drawWorld(cinematic2DWhiteTexture, -12.0f, -0.84f, 24.0f, 0.42f, 0.77f, 0.67f, 0.46f, 1.0f,
                width, height);
        drawWorld(cinematic2DWhiteTexture, -11.5f, -0.73f, 23.0f, 0.08f, 0.90f, 0.82f, 0.58f, 1.0f,
                width, height);
        drawFence(width, height);
        drawWorld(cinematic2DHouseTexture, -5.20f, -0.56f, 1.58f, 1.22f, 1.0f, 1.0f, 1.0f, 1.0f,
                width, height);
        drawWorld(cinematic2DHouseTexture, 3.82f, -0.62f, 1.28f, 1.00f, 0.86f, 0.95f, 1.0f, 1.0f,
                width, height);
        drawWorld(cinematic2DTreeTexture, -3.55f, -0.82f, 0.70f, 1.25f, 1.0f, 1.0f, 1.0f, 1.0f,
                width, height);
        drawWorld(cinematic2DTreeTexture, -1.70f, -0.92f, 0.64f, 1.12f, 0.92f, 1.0f, 0.92f, 1.0f,
                width, height);
        drawWorld(cinematic2DTreeTexture, 1.98f, -0.88f, 0.74f, 1.30f, 0.90f, 1.0f, 0.96f, 1.0f,
                width, height);
        drawWorld(cinematic2DTreeTexture, 5.18f, -0.90f, 0.68f, 1.20f, 0.88f, 0.98f, 0.94f, 1.0f,
                width, height);
        drawWorld(cinematic2DWhiteTexture, CINEMATIC_2D_PLAYER_X - 0.25f, CINEMATIC_2D_PLAYER_Y + 0.48f,
                0.50f, 0.13f, 0.07f, 0.08f, 0.09f, 0.25f, width, height);
        float playerBob = (float)Math.sin(renderedFrames * 0.18f) * 0.018f;
        drawWorld(cinematic2DPlayerTexture, CINEMATIC_2D_PLAYER_X - 0.25f,
                CINEMATIC_2D_PLAYER_Y - 0.10f + playerBob,
                0.50f * CINEMATIC_2D_PLAYER_FACING, 0.74f, 1.0f, 1.0f, 1.0f, 1.0f, width, height);
        spriteBatch.color(1.0f, 1.0f, 1.0f, 1.0f);
        spriteBatch.end();
    }

    private void drawFence(int width, int height) {
        for (int i = 0; i < 9; i++) {
            float x = -4.7f + i * 1.10f;
            drawWorld(cinematic2DWhiteTexture, x, -0.52f, 0.06f, 0.46f, 0.86f, 0.78f, 0.58f, 1.0f,
                    width, height);
        }
        drawWorld(cinematic2DWhiteTexture, -4.85f, -0.36f, 9.25f, 0.05f, 0.92f, 0.84f, 0.62f, 1.0f,
                width, height);
    }

    private void drawScreen(Texture texture, float x, float y, float width, float height,
            float red, float green, float blue, float alpha) {
        spriteBatch.color(red, green, blue, alpha);
        spriteBatch.draw(texture, x, y, width, height);
    }

    private void drawWorld(Texture texture, float x, float y, float width, float height,
            float red, float green, float blue, float alpha, int framebufferWidth, int framebufferHeight) {
        float halfHeight = CINEMATIC_2D_VIEW_HALF_HEIGHT * cinematic2DCamera.zoom();
        float aspect = framebufferHeight > 0 ? framebufferWidth / (float)framebufferHeight : 16.0f / 9.0f;
        float halfWidth = halfHeight * aspect;
        float drawX = worldToCinematic2DX(x, halfWidth);
        float drawY = worldToCinematic2DY(y, halfHeight);
        float drawWidth = width / halfWidth;
        float drawHeight = height / halfHeight;
        if (drawWidth < 0.0f) {
            drawX -= drawWidth;
        }
        spriteBatch.color(red, green, blue, alpha);
        spriteBatch.draw(texture, drawX, drawY, drawWidth, drawHeight);
    }

    private float worldToCinematic2DX(float worldX, float halfWidth) {
        return (worldX - cinematic2DCamera.position().x()) / halfWidth;
    }

    private float worldToCinematic2DY(float worldY, float halfHeight) {
        return (worldY - cinematic2DCamera.position().y()) / halfHeight;
    }

    private void renderOverlay(int width, int height) {
        if (uiRoot != null) {
            uiRoot.update(application.deltaTime());
            uiRoot.render();
        }
    }

    private void buildSelectorUi(UiScope ui) {
        ui.column(Ui.modifier().fill().padding(4.0f, 8.0f).gap(0.0f), page -> {
            page.row(Ui.modifier().fillWidth().height(58.0f).gap(5.0f).style("selector-bar"), row -> {
                for (int i = 0; i < EXAMPLE_COUNT; i++) {
                    final int panel = i;
                    UiModifier modifier = Ui.modifier()
                            .fillWidth()
                            .height(44.0f)
                            .weight(1.0f)
                            .style(i == activePanel ? "selector-active" : "selector")
                            .semanticLabel(PANEL_LABELS[i]);
                    row.button(PANEL_LABELS[i], modifier, () -> selectPanel(panel));
                }
            });
            page.spacer(Ui.modifier().weight(1.0f));
        });
    }

    private static UiTheme selectorTheme() {
        UiTextStyle text = UiTextStyle.text()
                .font(UiFont.freeType(FREETYPE_FONT_ASSET, 11.0f))
                .size(11.0f)
                .lineHeight(15.0f)
                .align(UiTextAlign.CENTER)
                .wrap(false)
                .ellipsis(true)
                .color(UiColor.rgba8888(0xf5f7fbff));
        UiTextStyle activeText = text.color(UiColor.rgba8888(0xffffffff));
        UiStyle selector = UiStyle.button()
                .padding(4.0f, 5.0f)
                .text(text)
                .background(UiDrawable.color(UiColor.rgba8888(0x263442ee)))
                .hover(UiStyle.button()
                        .padding(4.0f, 5.0f)
                        .text(activeText)
                        .background(UiDrawable.color(UiColor.rgba8888(0x334659f2))))
                .pressed(UiStyle.button()
                        .padding(4.0f, 5.0f)
                        .text(activeText)
                        .background(UiDrawable.color(UiColor.rgba8888(0x1c2835ff))));
        UiStyle active = UiStyle.button()
                .padding(4.0f, 5.0f)
                .text(activeText)
                .background(UiDrawable.color(UiColor.rgba8888(0x2f7de1ff)))
                .hover(UiStyle.button()
                        .padding(4.0f, 5.0f)
                        .text(activeText)
                        .background(UiDrawable.color(UiColor.rgba8888(0x3d8df2ff))))
                .pressed(UiStyle.button()
                        .padding(4.0f, 5.0f)
                        .text(activeText)
                        .background(UiDrawable.color(UiColor.rgba8888(0x2468bdff))));
        return Ui.darkTheme()
                .style("selector-bar", UiStyle.style()
                        .padding(0.0f)
                        .background(UiDrawable.color(UiColor.rgba8888(0x101820f2))))
                .style("selector", selector)
                .style("selector-active", active)
                .button(selector)
                .text(UiStyle.style().text(text));
    }

    private void updateLayout(int width, int height) {
        layoutWidth = Math.max(1, width);
        layoutHeight = Math.max(1, height);
        inputWidth = Math.max(1, display.width());
        inputHeight = Math.max(1, display.height());
        int baseSelectorWidth = Math.max(1, width / EXAMPLE_COUNT);
        for (int i = 0; i < EXAMPLE_COUNT; i++) {
            int x = i * baseSelectorWidth;
            selectorX[i] = x;
            selectorWidth[i] = i == EXAMPLE_COUNT - 1 ? width - x : baseSelectorWidth;
        }
    }

    private boolean containsActiveView(int x, int y) {
        int framebufferY = pointerFramebufferBottomY(y);
        return framebufferY >= 0 && framebufferY < Math.max(1, layoutHeight - SELECTOR_HEIGHT);
    }

    private int selectorAt(int x, int y) {
        int framebufferX = pointerFramebufferX(x);
        int framebufferY = pointerFramebufferBottomY(y);
        if (framebufferY < layoutHeight - SELECTOR_HEIGHT || framebufferY >= layoutHeight) {
            return -1;
        }
        for (int i = 0; i < EXAMPLE_COUNT; i++) {
            if (framebufferX >= selectorX[i] && framebufferX < selectorX[i] + selectorWidth[i]) {
                return i;
            }
        }
        return -1;
    }

    private int pointerFramebufferX(int x) {
        return Math.round(x * (layoutWidth / (float) inputWidth));
    }

    private int pointerFramebufferBottomY(int y) {
        int topLeftY = Math.max(0, Math.min(inputHeight, y));
        return Math.round((inputHeight - topLeftY) * (layoutHeight / (float) inputHeight));
    }

    private void selectPanel(int panel) {
        if (panel >= 0 && panel < EXAMPLE_COUNT) {
            activePanel = panel;
            if (uiRoot != null) {
                uiRoot.requestCompose();
            }
        }
    }

    private int initialPanel() {
        String value = System.getProperty("libfdx.test.cameraControllers.panel", "");
        if (value.isBlank()) {
            return 0;
        }
        int panel = panelIndex(value);
        return panel < 0 ? 0 : panel;
    }

    private int panelIndex(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', ' ').replace('_', ' ');
        for (int i = 0; i < PANEL_LABELS.length; i++) {
            if (PANEL_LABELS[i].equals(normalized)) {
                return i;
            }
        }
        try {
            int parsed = Integer.parseInt(normalized);
            if (parsed >= 0 && parsed < EXAMPLE_COUNT) {
                return parsed;
            }
            if (parsed >= 1 && parsed <= EXAMPLE_COUNT) {
                return parsed - 1;
            }
        }
        catch (NumberFormatException ignored) {
            return -1;
        }
        return -1;
    }

    private int exampleIndex(Key key) {
        switch (key) {
            case NUM_1:
                return 0;
            case NUM_2:
                return 1;
            case NUM_3:
                return 2;
            case NUM_4:
                return 3;
            case NUM_5:
                return 4;
            case NUM_6:
                return 5;
            case NUM_7:
                return CINEMATIC_2D_PANEL;
            default:
                return -1;
        }
    }

    private void dragCinematic(int x, int y) {
        int dx = x - cinematicLastX;
        int dy = y - cinematicLastY;
        if (cinematicDragPanel == CINEMATIC_3D_PANEL) {
            cinematicPathTimeOffset += dx * 0.025f - dy * 0.012f;
        }
        else if (cinematicDragPanel == CINEMATIC_2D_PANEL) {
            cinematic2DPathTimeOffset += dx * 0.018f - dy * 0.008f;
        }
        cinematicLastX = x;
        cinematicLastY = y;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 640;
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 480;
    }

    private void captureIfRequested() {
        if (capturePath == null || capturePath.length() == 0) {
            return;
        }
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

    private void captureFrame(String path) {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            FramebufferCapture.writePpm(path, framebufferWidth(), framebufferHeight(), pixels);
            logger.info("CameraControllersShowcaseTest captured framebuffer to " + path);
        }
        catch (Exception e) {
            throw new FdxException("Could not capture CameraControllersShowcaseTest framebuffer", e);
        }
    }

    private void disposeControllers() {
        if (freeController != null) {
            freeController.dispose();
            freeController = null;
        }
        if (firstPersonController != null) {
            firstPersonController.dispose();
            firstPersonController = null;
        }
        if (thirdPersonController != null) {
            thirdPersonController.dispose();
            thirdPersonController = null;
        }
        if (orbitController != null) {
            orbitController.dispose();
            orbitController = null;
        }
        if (orthographicController != null) {
            orthographicController.dispose();
            orthographicController = null;
        }
    }

    private static void disposeTexture(Texture texture) {
        if (texture != null) {
            texture.dispose();
        }
    }

    private static final class MutableAnchor2D implements CameraAnchor2D {
        private final Vector2 position = new Vector2();

        MutableAnchor2D(float x, float y) {
            set(x, y);
        }

        void set(float x, float y) {
            position.set(x, y);
        }

        @Override
        public void position(Vector2 out) {
            out.set(position);
        }
    }

    private static final class MutableAnchor3D implements CameraAnchor3D {
        private final Vector3 position = new Vector3();
        private final Vector3 up = new Vector3(0.0f, 1.0f, 0.0f);

        MutableAnchor3D(float x, float y, float z, float upX, float upY, float upZ) {
            set(x, y, z);
            setUp(upX, upY, upZ);
        }

        void set(float x, float y, float z) {
            position.set(x, y, z);
        }

        void setUp(float x, float y, float z) {
            up.set(x, y, z);
        }

        @Override
        public void position(Vector3 out) {
            out.set(position);
        }

        @Override
        public void up(Vector3 out) {
            out.set(up);
        }
    }
}
