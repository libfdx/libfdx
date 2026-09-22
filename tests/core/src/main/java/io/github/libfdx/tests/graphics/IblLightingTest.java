package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.controller.OrbitCameraController3D;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.math.Color;
import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.TestCameraControllers;
import io.github.libfdx.ui.*;

/**
 * Perspective material studio lit only by a prepared HDR environment.
 * The back row is copper, the front row is blue dielectric; roughness increases
 * from left to right. Orbit to inspect view-dependent reflections, rotate the
 * probe to move its highlights, or disable IBL to verify the lighting source.
 */
public final class IblLightingTest extends GraphicsParityTest {
    private static final String PROBE = "ibl/studio.fdxibl";
    private final Camera camera = new Camera().projection(CameraProjection.PERSPECTIVE)
            .fieldOfView(48).nearFar(.1f, 100);
    private final Environment environment = new Environment().ambientColor(Color.BLACK)
            .neutralToneMapping(1);
    private final Model[] models = new Model[12];
    private final DefaultModelInstance[] instances = new DefaultModelInstance[12];
    private DefaultAssetManager assets;
    private ModelBatch batch;
    private OrbitCameraController3D cameraInput;
    private UiRoot root;
    private ImageBasedLighting3D probe;
    private boolean ready;
    private boolean enabled = true;
    private float intensity = 1;
    private float rotation;
    private int logicalWidth, logicalHeight;

    public IblLightingTest(long frames) {
        super(frames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, getClass().getSimpleName());
        if (!ImageBasedLighting3D.isSupported(graphics)) {
            throw new FdxException("IBL is unavailable on this device");
        }
        assets = new DefaultAssetManager(fdx.files());
        G3DAssetLoaders.register(assets, graphics);
        assets.load(AssetDescriptor.of(PROBE, ImageBasedLighting3D.class));
        batch = new ModelBatch(graphics).environment(environment);
        createScene();
        cameraInput = new OrbitCameraController3D(fdx.input(), camera)
                .position(6, 6, 12, 0, .8f, 0).radiusRange(5, 45)
                .pointerRegion((x, y) -> y > 156)
                .autoOrbit(TestCameraControllers.autoOrbitEnabled(), .3f, exitAfterFrames,
                        TestCameraControllers.autoOrbitStartDegrees(), TestCameraControllers.autoOrbitDegrees());
        root = new UiToolkit(fdx.files()).root(display, graphics).input(fdx.input());
        root.setContent(this::buildUi);
        markCreated();
    }

    private void createScene() {
        ModelBuilder builder = new ModelBuilder(graphics);
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 5; column++) {
                int index = row * 5 + column;
                Material material = new Material(row == 0 ? "copper" : "blue ceramic",
                        MaterialAttributes.baseColor(row == 0 ? .95f : .045f,
                                row == 0 ? .52f : .24f, row == 0 ? .24f : .65f, 1),
                        PbrAttributes.metallicFactor(row == 0 ? 1 : 0),
                        PbrAttributes.roughnessFactor(.05f + column * .225f));
                models[index] = builder.material(material).sphere("roughness " + column,
                        .9f, 48, 32, ModelVertexUsage.STANDARD_PBR);
                instances[index] = new DefaultModelInstance(models[index]);
                instances[index].transform().setToTranslation((column - 2) * 2.3f, 1.15f, row == 0 ? -2 : 1);
            }
        }
        models[10] = builder.material(new Material("matte studio floor",
                MaterialAttributes.baseColor(.22f, .25f, .3f, 1),
                PbrAttributes.metallicFactor(0), PbrAttributes.roughnessFactor(.85f)))
                .plane("floor", 200, 200, ModelVertexUsage.STANDARD_PBR);
        instances[10] = new DefaultModelInstance(models[10]);
        models[11] = builder.material(new Material("graphite plinth",
                MaterialAttributes.baseColor(.07f, .08f, .1f, 1),
                PbrAttributes.metallicFactor(.6f), PbrAttributes.roughnessFactor(.3f)))
                .box("display plinth", 12, .25f, 6, ModelVertexUsage.STANDARD_PBR);
        instances[11] = new DefaultModelInstance(models[11]);
        instances[11].transform().setToTranslation(0, .125f, -.5f);
    }

    private void buildUi(UiScope ui) {
        ui.column(Ui.modifier().fillWidth().padding(12).gap(4), panel -> {
            panel.text("HDR image-based lighting", Ui.modifier().height(28).style("title"));
            panel.text("Back: copper / Front: ceramic", Ui.modifier().height(22));
            panel.text("Left to right: smooth to rough", Ui.modifier().height(22));
            panel.row(Ui.modifier().fillWidth().height(32).gap(6), row -> {
                row.button(enabled ? "IBL: on" : "IBL: off", () -> {
                    enabled = !enabled;
                    root.requestCompose();
                });
                row.button("Rotate probe", () -> rotation += (float)Math.PI / 4);
                row.button("Intensity: " + intensity, () -> {
                    intensity = intensity == 1 ? 2 : intensity == 2 ? .5f : 1;
                    root.requestCompose();
                });
            });
            panel.text("Right-drag: orbit / Scroll: zoom", Ui.modifier().height(22));
        });
    }

    @Override
    public void render() {
        if (!ready && assets.update(2, 1_000_000)) {
            probe = assets.get(PROBE, ImageBasedLighting3D.class);
            ready = true;
            logger.info("HDR studio ready: diffuse irradiance, roughness-filtered specular mips and BRDF LUT");
        }
        int width = Math.max(1, display.width()), height = Math.max(1, display.height());
        if (width != logicalWidth || height != logicalHeight) {
            logicalWidth = width;
            logicalHeight = height;
            root.resize(width, height);
        }
        camera.viewport(framebufferWidth(), framebufferHeight());
        // Preserve horizontal framing when the window becomes tall and narrow.
        float aspect = (float)framebufferWidth() / framebufferHeight();
        camera.fieldOfView((float)Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(24))
                * Math.max(1, 1.5f / aspect))));
        cameraInput.update(application.deltaTime());
        root.update(application.deltaTime());
        environment.imageBasedLighting(enabled ? probe : null).imageBasedLightingTransform(intensity, rotation);
        batch.begin(LoadOp.clear(.025f, .035f, .055f, 1), camera);
        if (ready) {
            for (DefaultModelInstance instance : instances) batch.render(instance);
        }
        batch.end();
        root.render();
        if (ready) finishFrame();
    }

    @Override
    public void dispose() {
        dispose(root);
        dispose(batch);
        for (Model model : models) dispose(model);
        dispose(assets);
        if (!ready && requiresCompletion()) throw new FdxException("IBL assets did not finish loading");
        verifyDisposed();
    }
}
