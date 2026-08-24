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
import io.github.libfdx.input.Input;
import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.tests.TestFpsLogger;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTheme;
import io.github.libfdx.ui.UiToolkit;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Reproduces float camera movement quantization far from the origin.
 *
 * <p>Every camera and movement value remains a {@code float}. The absolute
 * camera is still updated so far-world camera matrices are exercised, while
 * the scene is rendered with an origin-relative camera to isolate position
 * quantization from model/view matrix cancellation.</p>
 */
public final class FarWorldCameraTest extends ApplicationAdapter {
    private static final float WORLD_OFFSET = 1_000_000.0f;
    private static final float FIXED_STEP = 1.0f / 240.0f;
    private static final float SPEED = 10.0f;
    private static final float DIAGONAL_COMPONENT =
            SPEED * FIXED_STEP * 0.70710677f;
    private static final float INITIAL_PITCH_RADIANS = 0.07982999f;
    private static final float MAX_PITCH_RADIANS = 1.553343f;
    private static final float LOOK_RADIANS_PER_PIXEL = 0.004363323f;
    private static final int MANUAL = 0;
    private static final int CARDINAL_PROBE = 1;
    private static final int DIAGONAL_PROBE = 2;
    private static final int ACTION_NONE = 0;
    private static final int ACTION_CARDINAL = 1;
    private static final int ACTION_DIAGONAL = 2;
    private static final int ACTION_TOGGLE_ORIGIN = 3;
    private static final int ACTION_RESET = 4;

    private final long exitAfterFrames;
    private final Matrix4 spinnerTransform = new Matrix4();
    private final Matrix4 spinnerRotation = new Matrix4();
    private Application application;
    private Display display;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private GraphicsContext graphics;
    private Input input;
    private InputAdapter inputProcessor;
    private ModelBatch batch;
    private Camera camera;
    private Camera renderCamera;
    private UiRoot root;
    private Model[] models;
    private DefaultModelInstance[] instances;
    private DefaultModelInstance spinner;
    private boolean created;
    private boolean dragging;
    private boolean probeCompletionReported;
    private MouseButton dragButton = MouseButton.UNKNOWN;
    private int dragX;
    private int dragY;
    private int requestedAction;
    private int probeMode;
    private int probeStepsRemaining;
    private float yawRadians;
    private float pitchRadians;
    private float sceneOrigin;
    private float accumulator;
    private float spinnerAngle;
    private float startX;
    private float startY;
    private float startZ;
    private float expectedX;
    private float expectedY;
    private float expectedZ;
    private String capturePath;
    private long captureFrame;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates the far-world camera comparison.
     *
     * @param exitAfterFrames frame count before exiting, or zero to run
     */
    public FarWorldCameraTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "FarWorldCameraTest");
        graphics = fdx.graphics().main();
        input = fdx.input();

        Environment3D environment = new Environment3D()
                .ambientColor(new Color(0.32f, 0.32f, 0.34f, 1.0f))
                .add(new DirectionalLight()
                        .direction(-1.0f, -0.8f, -0.2f)
                        .color(new Color(0.9f, 0.9f, 0.9f, 1.0f))
                        .intensity(1.35f));
        batch = new ModelBatch(graphics).environment(environment);
        createScene();

        camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(67.0f)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 60.0f);
        renderCamera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(67.0f)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 60.0f);
        sceneOrigin = Boolean.getBoolean(
                "libfdx.test.farWorldZeroOrigin") ? 0.0f : WORLD_OFFSET;
        startProbe(DIAGONAL_PROBE);
        installInputProcessor();

        root = new UiToolkit(fdx.files())
                .theme(hudTheme())
                .root(display, graphics)
                .input(input);
        root.setContent(this::buildHud);

        capturePath = System.getProperty("libfdx.test.capture", "");
        captureFrame = Math.max(0L, Long.parseLong(
                System.getProperty("libfdx.test.captureFrame", "90")));
        created = true;
        logger.info("FarWorldCameraTest created for provider "
                + graphics.providerId() + " at scene origin " + sceneOrigin);
    }

    @Override
    public void render() {
        float deltaSeconds = Math.min(application.deltaTime(), 0.1f);
        applyRequestedAction();
        applyLookDirection();
        accumulator += deltaSeconds;
        while (accumulator >= FIXED_STEP) {
            updateMovement();
            accumulator -= FIXED_STEP;
        }

        int width = framebufferWidth();
        int height = framebufferHeight();
        camera.viewport(width, height).update();
        updateRenderCamera(width, height);
        updateSpinner(deltaSeconds);

        root.requestCompose();
        root.update(deltaSeconds);
        batch.begin(LoadOp.clear(0.06f, 0.08f, 0.12f, 1.0f),
                renderCamera);
        for (int i = 0; i < instances.length; i++) {
            batch.render(instances[i]);
        }
        batch.end();
        root.render();
        captureIfRequested();

        renderedFrames++;
        fpsLogger.frame(deltaSeconds, renderedFrames);
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    @Override
    public void dispose() {
        if (input != null && inputProcessor != null) {
            input.removeProcessor(inputProcessor);
            inputProcessor = null;
        }
        if (root != null) {
            root.dispose();
            root = null;
        }
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (models != null) {
            for (int i = 0; i < models.length; i++) {
                if (models[i] != null) {
                    models[i].dispose();
                    models[i] = null;
                }
            }
            models = null;
        }
        if (!created) {
            throw new FdxException(
                    "FarWorldCameraTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("FarWorldCameraTest rendered "
                    + renderedFrames + " of " + exitAfterFrames
                    + " required frames");
        }
        if (capturePath != null && capturePath.length() > 0 && !captured) {
            throw new FdxException(
                    "FarWorldCameraTest did not capture framebuffer to "
                            + capturePath);
        }
        logger.info("FarWorldCameraTest rendered " + renderedFrames
                + " frames");
    }

    private void createScene() {
        ModelBuilder builder = new ModelBuilder(graphics);
        Model ground = builder.material(material("far-world ground",
                        0.13f, 0.15f, 0.18f))
                .box("far-world ground", 30.0f, 0.2f, 36.0f,
                        ModelVertexUsage.STANDARD_PBR);
        Model marker = builder.material(material("far-world marker",
                        0.95f, 0.38f, 0.08f))
                .box("far-world marker", 1.25f, 1.5f, 1.25f,
                        ModelVertexUsage.STANDARD_PBR);
        Model goal = builder.material(material("far-world goal",
                        0.10f, 0.78f, 0.28f))
                .sphere("far-world goal", 1.0f, 20, 14,
                        ModelVertexUsage.STANDARD_PBR);
        Model bar = builder.material(material("far-world spinner",
                        0.98f, 0.78f, 0.10f))
                .box("far-world spinner", 4.0f, 0.4f, 0.75f,
                        ModelVertexUsage.STANDARD_PBR);
        models = new Model[] { ground, marker, goal, bar };

        ArrayList<DefaultModelInstance> scene =
                new ArrayList<DefaultModelInstance>();
        scene.add(instance(ground, 0.0f, -0.1f, 0.0f));
        for (int z = -10; z <= 10; z += 4) {
            scene.add(instance(marker, -4.0f, 0.75f, z));
            scene.add(instance(marker, 4.0f, 0.75f, z));
        }
        scene.add(instance(goal, 0.0f, 1.0f, -10.0f));
        spinner = instance(bar, 0.0f, 2.5f, 0.0f);
        scene.add(spinner);
        instances = scene.toArray(
                new DefaultModelInstance[scene.size()]);
    }

    private static Material material(String id, float red, float green,
            float blue) {
        return new Material(id)
                .set(MaterialAttributes.baseColor(red, green, blue, 1.0f))
                .set(PbrAttributes.metallicFactor(0.0f))
                .set(PbrAttributes.roughnessFactor(0.76f));
    }

    private static DefaultModelInstance instance(Model model, float x,
            float y, float z) {
        DefaultModelInstance instance = new DefaultModelInstance(model);
        instance.transform().setToTranslation(x, y, z);
        return instance;
    }

    private void installInputProcessor() {
        inputProcessor = new InputAdapter() {
            @Override
            public boolean keyDown(KeyEvent event) {
                if (event.repeat()) {
                    return false;
                }
                Key key = event.key();
                if (key == Key.NUM_1) {
                    requestedAction = ACTION_CARDINAL;
                }
                else if (key == Key.NUM_2) {
                    requestedAction = ACTION_DIAGONAL;
                }
                else if (key == Key.O) {
                    requestedAction = ACTION_TOGGLE_ORIGIN;
                }
                else if (key == Key.R) {
                    requestedAction = ACTION_RESET;
                }
                return false;
            }

            @Override
            public boolean pointerDown(PointerEvent event) {
                if (event.button() == MouseButton.LEFT
                        || event.button() == MouseButton.RIGHT) {
                    dragging = true;
                    dragButton = event.button();
                    dragX = event.x();
                    dragY = event.y();
                }
                return false;
            }

            @Override
            public boolean pointerMoved(PointerEvent event) {
                if (dragging) {
                    dragLook(event.x(), event.y());
                }
                return false;
            }

            @Override
            public boolean pointerUp(PointerEvent event) {
                if (dragging && event.button() == dragButton) {
                    dragLook(event.x(), event.y());
                    dragging = false;
                    dragButton = MouseButton.UNKNOWN;
                }
                return false;
            }
        };
        input.addProcessor(inputProcessor);
    }

    private void dragLook(int x, int y) {
        yawRadians += (dragX - x) * LOOK_RADIANS_PER_PIXEL;
        pitchRadians += (y - dragY) * LOOK_RADIANS_PER_PIXEL;
        pitchRadians = Math.max(-MAX_PITCH_RADIANS,
                Math.min(MAX_PITCH_RADIANS, pitchRadians));
        dragX = x;
        dragY = y;
    }

    private void applyLookDirection() {
        float cosPitch = (float)Math.cos(pitchRadians);
        float directionX = -(float)Math.sin(yawRadians) * cosPitch;
        float directionY = -(float)Math.sin(pitchRadians);
        float directionZ = -(float)Math.cos(yawRadians) * cosPitch;
        camera.direction(directionX, directionY, directionZ)
                .up(0.0f, 1.0f, 0.0f);
    }

    private void applyRequestedAction() {
        int action = requestedAction;
        requestedAction = ACTION_NONE;
        if (action == ACTION_CARDINAL) {
            startProbe(CARDINAL_PROBE);
        }
        else if (action == ACTION_DIAGONAL) {
            startProbe(DIAGONAL_PROBE);
        }
        else if (action == ACTION_TOGGLE_ORIGIN) {
            toggleSceneOrigin();
        }
        else if (action == ACTION_RESET) {
            probeMode = MANUAL;
            resetCamera();
        }
    }

    private void resetCamera() {
        yawRadians = 0.0f;
        pitchRadians = INITIAL_PITCH_RADIANS;
        camera.position(sceneOrigin, 3.0f, sceneOrigin + 15.0f);
        applyLookDirection();
        camera.update();
        updateRenderCamera(framebufferWidth(), framebufferHeight());
        startX = camera.position().x();
        startY = camera.position().y();
        startZ = camera.position().z();
        expectedX = 0.0f;
        expectedY = 0.0f;
        expectedZ = 0.0f;
        accumulator = 0.0f;
        probeStepsRemaining = 0;
    }

    private void updateMovement() {
        float movementX = 0.0f;
        float movementZ = 0.0f;
        if (probeStepsRemaining > 0) {
            movementZ = -1.0f;
            if (probeMode == DIAGONAL_PROBE) {
                movementX = 1.0f;
            }
            probeStepsRemaining--;
        }
        else if (probeMode == MANUAL) {
            float forwardX = camera.direction().x();
            float forwardZ = camera.direction().z();
            float forwardLength = (float)Math.sqrt(
                    forwardX * forwardX + forwardZ * forwardZ);
            if (forwardLength <= 0.0001f) {
                forwardX = 0.0f;
                forwardZ = -1.0f;
            }
            else {
                float inverseForwardLength = 1.0f / forwardLength;
                forwardX *= inverseForwardLength;
                forwardZ *= inverseForwardLength;
            }
            float rightX = -forwardZ;
            float rightZ = forwardX;
            if (input.isKeyPressed(Key.W)) {
                movementX += forwardX;
                movementZ += forwardZ;
            }
            if (input.isKeyPressed(Key.S)) {
                movementX -= forwardX;
                movementZ -= forwardZ;
            }
            if (input.isKeyPressed(Key.A)) {
                movementX -= rightX;
                movementZ -= rightZ;
            }
            if (input.isKeyPressed(Key.D)) {
                movementX += rightX;
                movementZ += rightZ;
            }
        }

        float lengthSquared = movementX * movementX
                + movementZ * movementZ;
        if (lengthSquared > 0.0f) {
            float scale = SPEED * FIXED_STEP
                    / (float)Math.sqrt(lengthSquared);
            float stepX = movementX * scale;
            float stepZ = movementZ * scale;
            expectedX += stepX;
            expectedZ += stepZ;
            camera.position(camera.position().x() + stepX,
                    camera.position().y(), camera.position().z() + stepZ);
        }
        if (probeStepsRemaining == 0 && probeMode != MANUAL
                && !probeCompletionReported) {
            probeCompletionReported = true;
            logger.info("FarWorldCameraTest " + probeName()
                    + " at origin " + sceneOrigin + ": expected X/Z="
                    + expectedX + "/" + expectedZ + ", actual X/Z="
                    + (camera.position().x() - startX) + "/"
                    + (camera.position().z() - startZ));
        }
    }

    private void startProbe(int mode) {
        resetCamera();
        probeMode = mode;
        probeStepsRemaining = 240;
        probeCompletionReported = false;
    }

    private void toggleSceneOrigin() {
        sceneOrigin = sceneOrigin == 0.0f ? WORLD_OFFSET : 0.0f;
        logger.info("FarWorldCameraTest toggled scene origin to "
                + sceneOrigin);
        if (probeMode == CARDINAL_PROBE
                || probeMode == DIAGONAL_PROBE) {
            startProbe(probeMode);
        }
        else {
            resetCamera();
        }
    }

    private void updateRenderCamera(int width, int height) {
        renderCamera.viewport(width, height)
                .position(camera.position().x() - sceneOrigin,
                        camera.position().y(),
                        camera.position().z() - sceneOrigin)
                .direction(camera.direction().x(), camera.direction().y(),
                        camera.direction().z())
                .up(camera.up().x(), camera.up().y(), camera.up().z())
                .update();
    }

    private void updateSpinner(float deltaSeconds) {
        spinnerAngle = (spinnerAngle + deltaSeconds * 1.0471976f)
                % 6.2831855f;
        spinnerTransform.setToTranslation(0.0f, 2.5f, 0.0f)
                .mul(spinnerRotation.setToRotationY(spinnerAngle));
        spinner.transform(spinnerTransform);
    }

    private void buildHud(UiScope ui) {
        float actualX = camera.position().x() - startX;
        float actualY = camera.position().y() - startY;
        float actualZ = camera.position().z() - startZ;
        ui.panel(Ui.modifier().width(620.0f).padding(10.0f).gap(2.0f),
                panel -> {
                    panel.text("Float camera origin comparison");
                    panel.text("WASD: move   mouse button + drag: look"
                            + "   R: reset/manual");
                    panel.text("O: toggle origin   Scene origin X/Z: "
                            + sceneOrigin);
                    panel.text("1: cardinal probe   2: diagonal probe"
                            + "   Mode: " + probeName() + "   240 Hz");
                    panel.text("Camera X/Y/Z: " + camera.position().x()
                            + " / " + camera.position().y() + " / "
                            + camera.position().z());
                    panel.text("Direction X/Z: " + camera.direction().x()
                            + " / " + camera.direction().z());
                    panel.text("Expected X/Y/Z offset: " + expectedX
                            + " / " + expectedY + " / " + expectedZ);
                    panel.text("Actual X/Y/Z offset: " + actualX
                            + " / " + actualY + " / " + actualZ);
                    panel.text("Position ULP X/Z: "
                            + Math.ulp(camera.position().x()) + " / "
                            + Math.ulp(camera.position().z()));
                    panel.text("Cardinal step: " + SPEED * FIXED_STEP
                            + "   diagonal component: "
                            + DIAGONAL_COMPONENT);
                    panel.text("Far: diagonal components are lost."
                            + " Zero: they are preserved.");
                    panel.text("Models render origin-relative to isolate"
                            + " camera-position precision.");
                });
    }

    private String probeName() {
        if (probeMode == CARDINAL_PROBE) {
            return probeStepsRemaining == 0
                    ? "CARDINAL COMPLETE" : "CARDINAL";
        }
        if (probeMode == DIAGONAL_PROBE) {
            return probeStepsRemaining == 0
                    ? "DIAGONAL COMPLETE" : "DIAGONAL";
        }
        return "MANUAL";
    }

    private static UiTheme hudTheme() {
        return Ui.darkTheme().panel(UiStyle.style()
                .background(UiDrawable.color(
                        UiColor.rgba8888(0x101820e8))));
    }

    private void captureIfRequested() {
        if (capturePath == null || capturePath.length() == 0
                || captured || renderedFrames < captureFrame) {
            return;
        }
        captureFrame(capturePath);
        captured = true;
    }

    private void captureFrame(String path) {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            int width = framebufferWidth();
            int height = framebufferHeight();
            FramebufferCapture.validateSceneFrame(width, height, pixels);
            FramebufferCapture.writePpm(path, width, height, pixels);
            logger.info("FarWorldCameraTest captured framebuffer to "
                    + path);
        }
        catch (Exception exception) {
            throw new FdxException(
                    "Could not capture FarWorldCameraTest framebuffer",
                    exception);
        }
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0
                ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 900;
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0
                ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 650;
    }
}
