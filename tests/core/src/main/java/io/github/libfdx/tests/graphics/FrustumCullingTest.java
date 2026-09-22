package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.GraphicsParityTest;

import io.github.libfdx.Fdx;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.controller.CameraInputBindings3D;
import io.github.libfdx.graphics.camera.controller.OrbitCameraController3D;
import io.github.libfdx.graphics.g2d.TextureBlitter;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.math.Color;
import io.github.libfdx.ui.*;

/** Interactive comparison of identical scenes with and without CPU frustum culling. */
public final class FrustumCullingTest extends GraphicsParityTest {
    private static final int HEADER_HEIGHT = 154;
    private static final int FOOTER_HEIGHT = 116;
    private static final int GAP = 8;
    private final Model[] models = new Model[10];
    private final DefaultModelInstance[] instances = new DefaultModelInstance[512];
    private final DefaultModelInstance[] transparent = new DefaultModelInstance[2];
    private final ModelBatch[] batches = new ModelBatch[2];
    private final OffscreenTarget[] targets = new OffscreenTarget[2];
    private final Camera camera = new Camera().projection(CameraProjection.PERSPECTIVE)
            .fieldOfView(55).nearFar(.1f, 180);
    private final UiBooleanState largeScene = Ui.state(true);
    private final UiFloatState fieldOfView = Ui.state(55f);
    private final UiIntState visibleCount = Ui.state(0);
    private final UiIntState culledCount = Ui.state(0);
    private final RenderPassDescriptor descriptor = new RenderPassDescriptor().label("Frustum culling comparison")
            .colorLoadOp(LoadOp.clear(.025f, .04f, .065f, 1)).colorStoreOp(StoreOp.store());
    private OrbitCameraController3D cameraInput;
    private TextureBlitter blitter;
    private UiRoot controls;
    private int frames;
    private DefaultModelInstance ground;
    private final DefaultModelInstance[] smallCasters = new DefaultModelInstance[16];
    private final DirectionalLight sunlight = new DirectionalLight().direction(-.55f, -.85f, -.35f)
            .color(new Color(1f, .88f, .70f, 1)).intensity(2.4f);
    private DirectionalShadowMap3D shadows;
    private boolean shadowsReady;
    private boolean shadowLargeScene;

    public FrustumCullingTest(long frames) {
        super(frames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "FrustumCullingTest");
        createScene();
        shadows = new DirectionalShadowMap3D(graphics, 2048, 2048)
                .bounds(0, 0, 0, 52, .1f, 160).autoBias(true).strength(.8f);
        Environment environment = new Environment().ambientColor(new Color(.15f, .19f, .24f, 1))
                .add(sunlight)
                .add(new DirectionalLight().direction(.6f, -.3f, .7f)
                        .color(new Color(.55f, .72f, 1, 1)).intensity(.55f))
                .directionalShadowMap(shadows).neutralToneMapping(1);
        for (int i = 0; i < batches.length; i++) {
            batches[i] = new ModelBatch(graphics).environment(environment).frustumCulling(i == 1);
            targets[i] = new OffscreenTarget(graphics.device(), true).clearColor(.045f, .07f, .10f, 1);
        }
        blitter = new TextureBlitter(graphics.device());
        createControls(fdx);
        cameraInput = new OrbitCameraController3D(fdx.input(), camera)
                .inputBindings(CameraInputBindings3D.defaults().lookButton(MouseButton.LEFT))
                .radiusRange(4, 100)
                .pointerRegion((x, y) -> x >= 0 && x < display.width()
                        && y >= HEADER_HEIGHT && y < display.height() - FOOTER_HEIGHT);
        resetCamera();
        markCreated();
    }

    private ModelBuilder material(String name, float r, float g, float b, float roughness, float metal) {
        return new ModelBuilder(graphics).material(new Material(name,
                MaterialAttributes.baseColor(r, g, b, 1), PbrAttributes.roughnessFactor(roughness),
                PbrAttributes.metallicFactor(metal)));
    }

    private DefaultModelInstance place(Model model, float x, float y, float z) {
        DefaultModelInstance instance = new DefaultModelInstance(model);
        instance.transform().setToTranslation(x, y, z);
        return instance;
    }

    private void createScene() {
        long usage = ModelVertexUsage.STANDARD_PBR;
        models[0] = material("sandstone paving", .48f, .44f, .35f, .9f, 0)
                .box("paver", 3.6f, .18f, 3.6f, usage);
        models[1] = material("slate plinth", .16f, .22f, .25f, .7f, .05f)
                .cylinder(1.08f, .48f, 32, usage);
        models[2] = material("brushed brass trim", .65f, .43f, .17f, .34f, .65f)
                .cylinder(1.12f, .08f, 32, usage);
        models[3] = material("jade ceramic", .055f, .48f, .39f, .26f, .12f)
                .sphere(.85f, 32, usage);
        models[4] = material("warm bronze", .68f, .32f, .10f, .3f, .55f)
                .torus(.67f, .23f, 40, usage);
        models[5] = material("ivory porcelain", .82f, .78f, .65f, .24f, .05f)
                .capsule(.55f, 1.7f, 32, usage);
        models[6] = material("blue enamel", .10f, .27f, .48f, .32f, .2f)
                .box("enamel monolith", 1.15f, 1.65f, 1.15f, usage);
        models[9] = material("garden floor", .105f, .15f, .14f, .95f, 0)
                .box("garden foundation", 112, .3f, 72, usage);
        ground = place(models[9], 0, -.24f, 0);

        // Four central exhibits form the small scene. The outer garden adds 124 exhibits.
        int grid = 0;
        for (int exhibit = 0; exhibit < 128; exhibit++) {
            float x, z;
            if (exhibit < 4) {
                x = (exhibit % 2 - .5f) * 5;
                z = (exhibit / 2 - .5f) * 5;
            } else {
                do {
                    x = (grid % 16 - 7.5f) * 5;
                    z = (grid / 16 - 3.5f) * 5;
                    grid++;
                } while (Math.abs(x) < 5 && Math.abs(z) < 5);
            }
            int index = exhibit * 4;
            instances[index] = place(models[0], x, 0, z);
            instances[index + 1] = place(models[1], x, .33f, z);
            instances[index + 2] = place(models[2], x, .61f, z);
            instances[index + 3] = place(models[3 + exhibit % 4], x, 1.52f, z);
            instances[index + 3].transform().rotateY(.35f + exhibit * .21f);
            if (exhibit % 4 == 1) instances[index + 3].transform().rotateX((float) Math.PI * .5f);
        }
        System.arraycopy(instances, 0, smallCasters, 0, smallCasters.length);
        // Overlapping tinted screens retain transparent sorting coverage in both views.
        for (int i = 0; i < transparent.length; i++) {
            Material glass = new Material("tinted screen " + i,
                    MaterialAttributes.baseColor(i == 0 ? .25f : .6f, .65f, i == 0 ? .7f : .3f, .26f),
                    PbrAttributes.roughnessFactor(.2f)).alphaMode(MaterialAlphaMode.BLEND).doubleSided(true);
            models[7 + i] = new ModelBuilder(graphics).material(glass)
                    .plane("glass screen " + i, 2.2f, 2.5f, usage);
            transparent[i] = place(models[7 + i], i == 0 ? -.45f : .45f, 1.4f, i == 0 ? -1 : -1.7f);
            transparent[i].transform().rotateX((float) Math.PI * .5f);
        }
    }
    private void createControls(Fdx fdx) {
        UiTextStyle text = UiTextStyle.text().font(UiFont.freeType("font/freetype/lsans.ttf", 18))
                .size(18).lineHeight(24).color(UiColor.rgba8888(0xe9f2ffff));
        controls = new UiToolkit(fdx.files()).theme(Ui.darkTheme().text(UiStyle.style().text(text)))
                .root(display, graphics).input(fdx.input());
        controls.setContent(ui -> ui.column(Ui.modifier().fill(), page -> {
            page.column(Ui.modifier().fillWidth().height(HEADER_HEIGHT).padding(12).gap(4), header -> {
                header.text("Frustum Culling 3D / Sculpture Garden");
                header.text("Culling skips objects outside the view. Compare the submission counts below.",
                        Ui.modifier().fillWidth());
                header.text("Drag either view: orbit   |   Wheel: zoom   |   A/D: pan   |   Q/E: down/up",
                        Ui.modifier().fillWidth());
                header.row(Ui.modifier().fillWidth().height(34).gap(8), row -> {
                    row.button("Small scene", Ui.modifier().width(138), () -> largeScene.set(false));
                    row.button("Large scene", Ui.modifier().width(138), () -> largeScene.set(true));
                    row.button("Reset camera", Ui.modifier().width(148), this::resetCamera);
                });
            });
            page.spacer(Ui.modifier().weight(1));
            page.column(Ui.modifier().fillWidth().height(FOOTER_HEIGHT).padding(12).gap(8), footer -> {
                footer.row(Ui.modifier().fillWidth().height(24).gap(GAP), row -> {
                    row.text("LEFT / CULLING OFF", Ui.modifier().weight(1));
                    row.text("RIGHT / CULLING ON", Ui.modifier().weight(1));
                });
                footer.row(Ui.modifier().fillWidth().height(24).gap(GAP), row -> {
                    row.text((largeScene.get() ? 515 : 19) + " submitted", Ui.modifier().weight(1));
                    row.text(visibleCount.get() + " submitted / " + culledCount.get() + " skipped",
                            Ui.modifier().weight(1));
                });
                footer.row(Ui.modifier().fillWidth().height(24).gap(12), row -> {
                    row.text("Field of view", Ui.modifier().width(114));
                    row.slider(Ui.modifier().fillWidth().weight(1).semanticLabel("Camera field of view"), fieldOfView, 25, 100);
                    row.text(Math.round(fieldOfView.get()) + " degrees", Ui.modifier().width(108));
                });
            });
        }));
    }

    private void resetCamera() {
        fieldOfView.set(55f);
        if (cameraInput != null) cameraInput.position(12, 11, 19, 0, 1, 0);
    }

    @Override
    public void render() {
        float delta = Math.min(application.deltaTime(), .1f);
        controls.update(delta);
        cameraInput.update(delta);
        int width = framebufferWidth();
        int height = framebufferHeight();
        float scale = height / (float) Math.max(1, display.height());
        int top = Math.round(HEADER_HEIGHT * scale);
        int bottom = Math.round(FOOTER_HEIGHT * scale);
        int gap = Math.max(1, Math.round(GAP * scale));
        int viewWidth = Math.max(1, (width - gap) / 2);
        int viewHeight = Math.max(1, height - top - bottom);
        camera.viewport(viewWidth, viewHeight).fieldOfView(fieldOfView.get());
        int count = largeScene.get() ? instances.length : 16;
        // Static lighting is shared by both comparison views and only rebuilt when exhibits change.
        if (!shadowsReady || shadowLargeScene != largeScene.get()) {
            shadows.render(sunlight, largeScene.get() ? instances : smallCasters);
            shadowsReady = true;
            shadowLargeScene = largeScene.get();
        }
        GraphicsFrame frame = graphics.currentFrame();
        for (int j = 0; j < batches.length; j++) {
            int method = (j + (frames & 1)) % 2;
            ModelBatch batch = batches[method];
            // Both targets use identical dimensions and raster coordinates for a fair visual comparison.
            targets[method].resize(viewWidth, viewHeight);
            RenderPass pass = targets[method].begin(frame, true);
            batch.begin(pass, camera);
            batch.render(ground);
            for (int i = 0; i < count; i++) batch.render(instances[i]);
            for (DefaultModelInstance instance : transparent) batch.render(instance);
            batch.end();
            pass.end();
        }
        int visible = batches[1].lastFlushVisibleCount();
        int culled = batches[1].lastFlushCulledCount();
        // Looking away from the entire scene is a valid interaction, including zero visible objects.
        if (visible + culled != count + transparent.length + 1
                || batches[0].lastFlushVisibleCount() != count + transparent.length + 1) {
            throw new FdxException("Unexpected frustum submission counts");
        }
        visibleCount.set(visible);
        culledCount.set(culled);
        RenderPass pass = frame.commandEncoder().beginRenderPass(descriptor.colorAttachment(frame.colorAttachment()));
        for (int i = 0; i < targets.length; i++) {
            pass.setViewport(i * (viewWidth + gap), bottom, viewWidth, viewHeight);
            pass.setScissor(i * (viewWidth + gap), bottom, viewWidth, viewHeight);
            blitter.draw(pass, targets[i].color(), targets[i].origin(), false);
        }
        pass.end();
        controls.render();
        frames++;
        finishFrame();
    }

    @Override
    public void resize(int width, int height) {
        if (controls != null) controls.resize(width, height);
    }

    @Override
    public void dispose() {
        dispose(cameraInput);
        dispose(controls);
        for (ModelBatch batch : batches) dispose(batch);
        for (Model model : models) dispose(model);
        dispose(blitter);
        dispose(shadows);
        for (OffscreenTarget target : targets) dispose(target);
        verifyDisposed();
    }
}
