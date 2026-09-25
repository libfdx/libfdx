package io.github.libfdx.testsupport.graphics.lod;

import io.github.libfdx.Fdx;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.input.Input;
import io.github.libfdx.ui.*;

/** User-owned controls; changing these never creates model geometry. */
public final class ModelLodControls implements Disposable {
    private static final float MINIMUM_CAMERA_DISTANCE = 3.5f;
    public final UiFloatState distance = Ui.state(5f);
    public final UiFloatState mediumPixels = Ui.state(220f), lowPixels = Ui.state(80f), hysteresis = Ui.state(.1f);
    public final UiBooleanState tour = Ui.state(false), orthographic = Ui.state(false), missing = Ui.state(false), spin = Ui.state(false);
    public final UiIntState mode = Ui.state(-1);
    private final UiRoot root;
    private final ModelLodLayout layout;
    private final ModelLodScene scene;
    private float tourTime, distanceScale = 1;
    private int shownLevel = -1, shownPixels = -1;
    private String shownFallback = "";

    public ModelLodControls(Fdx fdx, Input input, ModelLodLayout layout, ModelLodScene scene) {
        this.layout = layout; this.scene = scene;
        UiTextStyle text = UiTextStyle.text().font(UiFont.freeType("font/freetype/lsans.ttf", 17))
                .size(17).lineHeight(22).color(UiColor.rgba8888(0xe9f2ffff));
        root = new UiToolkit(fdx.files()).theme(Ui.darkTheme().text(UiStyle.style().text(text))
                .style("active", UiStyle.button().background(UiDrawable.color(UiColor.rgba8888(0x287f91ff)))))
                .root(fdx.displays().main(), fdx.graphics().main()).input(input);
        root.setContent(this::buildUi);
    }

    private void buildUi(UiScope ui) {
        ui.column(Ui.modifier().fill().padding(ModelLodLayout.MARGIN).gap(ModelLodLayout.GAP), page -> {
            page.column(Ui.modifier().fillWidth().height(ModelLodLayout.HEADER).gap(6), header -> {
                header.text("Model LOD / geometry comparison", Ui.modifier().fillWidth().height(28).style("title"));
                header.text("Move away to reduce triangles. Preview a level up close to inspect its silhouette.", Ui.modifier().fillWidth().height(24));
                header.row(Ui.modifier().fillWidth().height(32).gap(8), row -> {
                    modeButton(row, "Automatic", -1, "lod.auto");
                    modeButton(row, "Full detail", 0, "lod.full");
                    modeButton(row, "Preview LOD 1", 1, "lod.preview1");
                    modeButton(row, "Preview LOD 2", 2, "lod.preview2");
                });
                header.row(Ui.modifier().fillWidth().height(32).gap(8), row -> {
                    row.button("Near", Ui.modifier().width(64).validationId("lod.near"), () -> moveTo(5));
                    row.button("Middle", Ui.modifier().width(72).validationId("lod.middle"), () -> moveTo(14));
                    row.button("Far", Ui.modifier().width(64).validationId("lod.far"), () -> moveTo(50));
                    row.button(tour.get() ? "Pause tour" : "Distance tour", Ui.modifier().width(138).validationId("lod.tour"), () -> {
                        tourTime = 0; tour.set(!tour.get());
                    });
                    row.checkbox(Ui.modifier().semanticLabel("Orthographic projection").validationId("lod.ortho"), orthographic);
                    row.text("Orthographic", Ui.modifier().width(110));
                    row.button("Reset", Ui.modifier().width(68).validationId("lod.reset"), this::reset);
                });
                header.row(Ui.modifier().fillWidth().height(32).gap(8), row -> {
                    row.text(orthographic.get() ? "View scale" : "Distance", Ui.modifier().width(90));
                    row.slider(Ui.modifier().fillWidth().weight(1).enabled(!tour.get())
                            .semanticLabel("Camera distance or orthographic scale").validationId("lod.distance"), distance, minimumDistance(), maximumDistance());
                    row.text(decimal(distance.get()), Ui.modifier().width(66));
                });
            });
            page.row(Ui.modifier().fillWidth().height(layout.contentHeight).gap(ModelLodLayout.GAP), body -> {
                comparisonLabel(body, "Full-detail reference", scene.referenceTriangles() + " triangles / always LOD 0");
                comparisonLabel(body, "Selected LOD " + scene.renderedLevel(), scene.triangles() + " triangles / "
                        + Math.round(scene.projectedPixels()) + " px / " + scene.fallback());
            });
            page.column(Ui.modifier().fillWidth().height(ModelLodLayout.FOOTER).gap(6), footer -> {
                footer.row(Ui.modifier().fillWidth().height(32).gap(8), row -> {
                    row.text("LOD 1 below", Ui.modifier().width(108));
                    row.slider(Ui.modifier().fillWidth().weight(1).semanticLabel("LOD 1 pixel threshold").validationId("lod.threshold1"), mediumPixels, 150, 360);
                    row.text(Math.round(mediumPixels.get()) + " px", Ui.modifier().width(66));
                    row.text("LOD 2 below", Ui.modifier().width(108));
                    row.slider(Ui.modifier().fillWidth().weight(1).semanticLabel("LOD 2 pixel threshold").validationId("lod.threshold2"), lowPixels, 40, 120);
                    row.text(Math.round(lowPixels.get()) + " px", Ui.modifier().width(66));
                });
                footer.row(Ui.modifier().fillWidth().height(32).gap(8), row -> {
                    row.text("Hysteresis", Ui.modifier().width(108));
                    row.slider(Ui.modifier().fillWidth().weight(1).semanticLabel("LOD hysteresis").validationId("lod.hysteresis"), hysteresis, 0, .3f);
                    row.text(Math.round(hysteresis.get() * 100) + "%", Ui.modifier().width(66));
                    row.checkbox(Ui.modifier().semanticLabel("Simulate missing LOD 2").validationId("lod.missing"), missing);
                    row.text("Missing LOD 2", Ui.modifier().width(128));
                    row.checkbox(Ui.modifier().semanticLabel("Rotate both models").validationId("lod.spin"), spin);
                    row.text("Spin", Ui.modifier().width(44));
                });
                footer.text("Same material and lighting. Missing levels keep higher detail. Three meshes are built once.", Ui.modifier().fillWidth().height(24));
            });
        });
    }

    private void modeButton(UiScope row, String label, int value, String id) {
        row.button(label, Ui.modifier().fillWidth().weight(1).validationId(id).style(mode.get() == value ? "active" : "button"), () -> {
            mode.set(value);
            if (value > 0) moveTo(5);
        });
    }

    private void comparisonLabel(UiScope body, String title, String detail) {
        body.column(Ui.modifier().width(layout.panelWidth).height(layout.contentHeight).gap(4), panel -> {
            panel.text(title, Ui.modifier().fillWidth().height(24).style("title"));
            panel.text(detail, Ui.modifier().fillWidth().height(24));
            panel.spacer(Ui.modifier().weight(1));
        });
    }

    public float minimumDistance() { return Math.min(maximumDistance(), Math.max(MINIMUM_CAMERA_DISTANCE, 5 * distanceScale)); }
    public float maximumDistance() { return 50 * distanceScale; }
    public void moveTo(float value) { tour.set(false); distance.set(Math.max(minimumDistance(), value * distanceScale)); }
    public void reset() {
        moveTo(5); mode.set(-1); orthographic.set(false); missing.set(false); spin.set(false);
        mediumPixels.set(220); lowPixels.set(80); hysteresis.set(.1f);
    }
    public void update(float delta) {
        root.update(delta);
        if (tour.get()) {
            tourTime += Math.min(.1f, delta);
            float progress = .5f - .5f * (float)Math.cos(tourTime * .5f);
            distance.set(minimumDistance() + (maximumDistance() - minimumDistance()) * progress);
        }
    }
    public void render() {
        int pixels = Math.round(scene.projectedPixels());
        String fallback = scene.fallback().name();
        if (shownLevel != scene.renderedLevel() || shownPixels != pixels || !shownFallback.equals(fallback)) {
            shownLevel = scene.renderedLevel(); shownPixels = pixels; shownFallback = fallback;
            root.requestCompose();
        }
        root.render();
    }
    public void resize(int width, int height) {
        // Keep all three levels reachable with the presets on different sizes and DPI scales.
        float scale = layout.viewHeight / 372f;
        float resizedDistance = distance.get() * scale / distanceScale;
        distanceScale = scale;
        // A short preview must not zoom through the ring while preserving its pixel size.
        distance.set(Math.max(minimumDistance(), resizedDistance));
        root.resize(width, height); root.requestCompose();
    }
    public UiRoot root() { return root; }
    private static String decimal(float value) { return Float.toString(Math.round(value * 10) / 10f); }
    @Override public boolean isDisposed() { return root.isDisposed(); }
    @Override public void dispose() { root.dispose(); }
}
