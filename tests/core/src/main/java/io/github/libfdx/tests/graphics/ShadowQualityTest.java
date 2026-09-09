package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.*;
import io.github.libfdx.graphics.g2d.TextureBlitter;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.math.Color;
import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.ShadowQualityLayout;
import io.github.libfdx.ui.*;
import java.util.Arrays;

/** Compares identical shadows with forced rendering and revision-aware reuse. */
public final class ShadowQualityTest extends GraphicsParityTest {
    private static final ShadowBudget3D[] BUDGETS = { ShadowBudget3D.LOW, ShadowBudget3D.BALANCED, ShadowBudget3D.HIGH };
    private static final String[] QUALITY_LABELS = {"Low", "Balanced", "High"};
    private static final String[] MOTION_LABELS = {"Still", "Camera", "Objects", "Auto"};
    private static final String[] EXPLANATIONS = {
            "Still scene: reuse keeps the shadows without redrawing them. Both images should match.",
            "Moving camera: shadow coverage changes, so cached maps may need to redraw.",
            "Moving objects: their shadows change, so every shadow map must redraw."
    };
    private final Camera camera = new Camera().projection(CameraProjection.PERSPECTIVE).fieldOfView(52).nearFar(.2f, 85);
    private final DirectionalLight light = new DirectionalLight().direction(-1, -.8f, -.45f).intensity(3);
    private final Model[] models = new Model[4];
    private final DefaultModelInstance[] instances = new DefaultModelInstance[17];
    private final CascadedShadowMap3D[] shadows = new CascadedShadowMap3D[2];
    private final Environment3D[] environments = new Environment3D[2];
    private final ModelBatch[] batches = new ModelBatch[2];
    private final OffscreenTarget[] targets = new OffscreenTarget[2];
    private final ShadowQualityLayout layout = new ShadowQualityLayout();
    private final RenderPassDescriptor screen = new RenderPassDescriptor().label("Shadow comparison")
            .colorLoadOp(LoadOp.clear(.035f, .05f, .07f, 1)).colorStoreOp(StoreOp.store());
    private final long[][][] timings = new long[3][2][512];
    private final long[][] passCounts = new long[3][2];
    private final int[] samples = new int[3];
    private final String[] passLabels = {"", ""};
    private final int[] lastPassCounts = {-1, -1};
    private TextureBlitter blitter;
    private UiRoot root;
    private ShadowBudget3D budget;
    private int quality = -1, requestedQuality = 1;
    private int selectedMotion, workload, previousWorkload = -1, frames;
    private int logicalWidth, logicalHeight, pixelWidth, pixelHeight;
    private long casterRevision;
    private float elapsed;

    public ShadowQualityTest(long frames) {
        super(frames);
        selectedMotion = frames == 0 ? 0 : 3;
    }

    @Override public void create(Fdx fdx) {
        initialize(fdx, getClass().getSimpleName());
        createScene();
        for (int i = 0; i < 2; i++) {
            environments[i] = new Environment3D().ambientColor(new Color(.16f, .18f, .22f, 1))
                    .add(light).neutralToneMapping(1);
            batches[i] = new ModelBatch(graphics).environment(environments[i]).frustumCulling(true);
            targets[i] = new OffscreenTarget(graphics.device(), true).clearColor(.36f, .47f, .59f, 1);
        }
        blitter = new TextureBlitter(graphics.device());
        root = new UiToolkit(fdx.files()).theme(Ui.darkTheme().style("active",
                UiStyle.button().background(UiDrawable.color(UiColor.rgba8888(0x316d91ff)))))
                .root(display, graphics).input(fdx.input());
        root.setContent(this::buildUi);
        updateLayout();
        updateQuality();
        markCreated();
    }

    private void createScene() {
        ModelBuilder builder = new ModelBuilder(graphics);
        Material ground = new Material("matte ground", MaterialAttributes.baseColor(.72f, .74f, .70f, 1),
                PbrAttributes.metallicFactor(0), PbrAttributes.roughnessFactor(.95f));
        models[0] = builder.material(ground).plane("shadow stage", 44, 52, ModelVertexUsage.STANDARD_PBR);
        instances[0] = new DefaultModelInstance(models[0]);
        instances[0].transform().setToTranslation(0, -.02f, -7);
        for (int i = 1; i < models.length; i++) {
            Material material = new Material("colored caster", MaterialAttributes.baseColor(
                    i == 1 ? .85f : .12f, i == 2 ? .65f : .22f, i == 3 ? .85f : .12f, 1),
                    PbrAttributes.metallicFactor(0), PbrAttributes.roughnessFactor(.65f));
            models[i] = i == 2
                    ? builder.material(material).sphere("round caster", 1.5f, 24, 16, ModelVertexUsage.STANDARD_PBR)
                    : builder.material(material).box("tall caster", 2.4f, 3.6f, 2.4f, ModelVertexUsage.STANDARD_PBR);
        }
        for (int i = 1; i < instances.length; i++) instances[i] = new DefaultModelInstance(models[1 + (i - 1) % 3]);
        positionObjects(0);
    }

    private void positionObjects(float time) {
        for (int i = 1; i < instances.length; i++) {
            float x = ((i - 1) % 4 - 1.5f) * 5.2f;
            float z = 4 - ((i - 1) / 4) * 6;
            float height = (i - 1) % 3 == 1 ? 1.5f : 1.8f;
            instances[i].transform().setToTranslation(x + (float)Math.sin(time) * 1.5f, height, z).rotateY(time * .6f);
        }
        casterRevision++;
    }

    private void updateLayout() {
        int width = Math.max(1, display.width()), height = Math.max(1, display.height());
        int pixelsX = framebufferWidth(), pixelsY = framebufferHeight();
        if (width == logicalWidth && height == logicalHeight && pixelsX == pixelWidth && pixelsY == pixelHeight) return;
        logicalWidth = width; logicalHeight = height; pixelWidth = pixelsX; pixelHeight = pixelsY;
        layout.update(width, height, pixelsX, pixelsY);
        for (OffscreenTarget target : targets) target.resize(layout.viewWidth, layout.viewHeight);
        camera.viewport(layout.viewWidth, layout.viewHeight);
        camera.fieldOfView(layout.stacked ? 36 : 52);
        root.resize(width, height);
        previousWorkload = -1;
    }

    private void updateQuality() {
        if (quality == requestedQuality) return;
        ShadowBudget3D next = BUDGETS[requestedQuality];
        CascadedShadowMap3D first = next.create(graphics).bias(.015f).minTexelBias(.6f).strength(.85f);
        CascadedShadowMap3D second;
        try {
            second = next.create(graphics).bias(.015f).minTexelBias(.6f).strength(.85f);
        } catch (RuntimeException | Error failure) {
            first.dispose();
            throw failure;
        }
        for (int i = 0; i < 2; i++) {
            CascadedShadowMap3D old = shadows[i];
            shadows[i] = i == 0 ? first : second;
            environments[i].cascadedShadowMap(shadows[i]);
            dispose(old);
            lastPassCounts[i] = -1;
        }
        budget = next; quality = requestedQuality; previousWorkload = -1;
        root.requestCompose();
        Arrays.fill(samples, 0);
        for (long[] counts : passCounts) Arrays.fill(counts, 0);
    }

    @Override public void render() {
        updateLayout();
        updateQuality();
        elapsed += exitAfterFrames == 0 ? Math.min(.1f, application.deltaTime()) : 1f / 60f;
        workload = selectedMotion == 3 ? (int)(elapsed / 4) % 3 : selectedMotion;
        if (workload != previousWorkload) root.requestCompose();
        float travel = workload == 1 ? (float)Math.sin(elapsed * .6f) * 6 : 0;
        camera.position(16 + travel, 18, 24).lookAt(0, 0, -5);
        if (workload == 2 || workload != previousWorkload) positionObjects(workload == 2 ? elapsed : 0);
        renderShadows();
        GraphicsFrame frame = graphics.currentFrame();
        for (int i = 0; i < 2; i++) {
            RenderPass pass = targets[i].begin(frame, true);
            batches[i].begin(pass, camera);
            for (DefaultModelInstance instance : instances) batches[i].render(instance);
            batches[i].end();
            pass.end();
        }
        RenderPass pass = frame.commandEncoder().beginRenderPass(screen.colorAttachment(frame.colorAttachment()));
        for (int i = 0; i < 2; i++) {
            pass.setViewport(layout.x[i], layout.y[i], layout.viewWidth, layout.viewHeight);
            pass.setScissor(layout.x[i], layout.y[i], layout.viewWidth, layout.viewHeight);
            blitter.draw(pass, targets[i].color(), targets[i].origin(), false);
        }
        pass.end();
        root.update(application.deltaTime());
        root.render();
        previousWorkload = workload;
        frames++;
        finishFrame();
    }

    private void renderShadows() {
        int cachedCount = -1;
        boolean measure = frames >= 300 && samples[workload] < 512;
        for (int j = 0; j < 2; j++) {
            int method = (j + (frames & 1)) % 2;
            long start = System.nanoTime();
            if (method == 0) shadows[method].render(light, camera, instances);
            else cachedCount = shadows[method].renderIfNeeded(light, camera, instances, casterRevision);
            int count = shadows[method].lastRenderedCascadeCount();
            if (measure) {
                timings[workload][method][samples[workload]] = System.nanoTime() - start;
                passCounts[workload][method] += count;
            }
            if (count != lastPassCounts[method]) {
                passLabels[method] = count + " of " + budget.cascadeCount() + " maps redrawn | "
                        + budget.resolution() + " px";
                lastPassCounts[method] = count;
                root.requestCompose();
            }
        }
        if (workload == 0 && previousWorkload == 0 && cachedCount != 0)
            throw new FdxException("Unchanged shadow maps were redrawn");
        if (workload == 2 && cachedCount != budget.cascadeCount())
            throw new FdxException("Changed casters failed to invalidate every shadow map");
        if (measure) samples[workload]++;
    }

    private void buildUi(UiScope ui) {
        ui.column(Ui.modifier().fill().padding(ShadowQualityLayout.MARGIN).gap(ShadowQualityLayout.MARGIN), page -> {
            page.column(Ui.modifier().fillWidth().height(ShadowQualityLayout.HEADER).gap(4), header -> {
                header.text("Shadow quality & reuse", Ui.modifier().fillWidth().height(28).style("title"));
                header.text("Same shadows. Fewer updates when nothing moves.", Ui.modifier().fillWidth().height(24));
                header.row(Ui.modifier().fillWidth().height(32).gap(8), row -> {
                    row.text("Quality", Ui.modifier().width(56));
                    for (int i = 0; i < QUALITY_LABELS.length; i++) {
                        final int choice = i;
                        row.button(QUALITY_LABELS[i], Ui.modifier().fillWidth().weight(1)
                                .style(quality == i ? "active" : "button"), () -> chooseQuality(choice));
                    }
                });
                header.row(Ui.modifier().fillWidth().height(32).gap(8), row -> {
                    row.text("Motion", Ui.modifier().width(56));
                    for (int i = 0; i < MOTION_LABELS.length; i++) {
                        final int choice = i;
                        row.button(MOTION_LABELS[i], Ui.modifier().fillWidth().weight(1)
                                .style(selectedMotion == i ? "active" : "button"), () -> chooseMotion(choice));
                    }
                });
            });
            if (layout.stacked) page.column(Ui.modifier().fillWidth().height(layout.contentHeight).gap(12), body -> {
                buildComparisonPanel(body, 0); buildComparisonPanel(body, 1);
            });
            else page.row(Ui.modifier().fillWidth().height(layout.contentHeight).gap(12), body -> {
                buildComparisonPanel(body, 0); buildComparisonPanel(body, 1);
            });
            page.text(EXPLANATIONS[workload], Ui.modifier().fillWidth().height(ShadowQualityLayout.FOOTER));
        });
    }

    private void buildComparisonPanel(UiScope parent, int method) {
        parent.column(Ui.modifier().width(layout.panelWidth).height(layout.panelHeight), panel -> {
            panel.column(Ui.modifier().fillWidth().height(ShadowQualityLayout.LABEL).gap(3), label -> {
                label.text(method == 0 ? "Always redraw" : "Reuse unchanged maps", Ui.modifier().fillWidth().height(24).style("title"));
                label.text(passLabels[method], Ui.modifier().fillWidth().height(22));
            });
            panel.spacer(Ui.modifier().weight(1));
        });
    }

    private void chooseQuality(int choice) {
        requestedQuality = choice;
    }

    private void chooseMotion(int choice) {
        selectedMotion = choice;
        root.requestCompose();
    }

    @Override public void dispose() {
        for (int work = 0; work < 3; work++) for (int method = 0; method < 2; method++) {
            int count = samples[work];
            if (count == 0) continue;
            long[] data = timings[work][method]; Arrays.sort(data, 0, count);
            logger.info("ShadowQualityTest CPU_RECORD workload=" + MOTION_LABELS[work]
                    + " method=" + (method == 0 ? "forced" : "cached") + " quality=" + QUALITY_LABELS[quality]
                    + " samples=" + count + " median_us=" + data[count / 2] / 1000
                    + " p95_us=" + data[(count - 1) * 95 / 100] / 1000
                    + " p99_us=" + data[(count - 1) * 99 / 100] / 1000 + " max_us=" + data[count - 1] / 1000
                    + " passes=" + passCounts[work][method] + " GPU_NOT_MEASURED");
        }
        dispose(root);
        for (ModelBatch batch : batches) dispose(batch);
        for (CascadedShadowMap3D map : shadows) dispose(map);
        for (Model model : models) dispose(model);
        for (OffscreenTarget target : targets) dispose(target);
        dispose(blitter);
        verifyDisposed();
    }
}
