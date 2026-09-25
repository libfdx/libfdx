package io.github.libfdx.testsupport.graphics.lodoptimizer;

import io.github.libfdx.Fdx;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.meshoptimizer.*;
import io.github.libfdx.input.Input;
import io.github.libfdx.ui.*;
import java.util.ArrayList;

/** Editable settings are separate from the last successfully generated LOD chain. */
public final class LodOptimizerControls implements Disposable {
    public final UiState<String> path = Ui.state(LodOptimizerSession.HELMET);
    public final UiFloatState distance = Ui.state(4f), yaw = Ui.state(.35f), hysteresis = Ui.state(.1f);
    public final UiFloatState normalWeight = Ui.state(.5f), uvWeight = Ui.state(10f), colorWeight = Ui.state(1f);
    public final UiBooleanState borders = Ui.state(false), cache = Ui.state(true), fetch = Ui.state(true);
    public final UiIntState previewLevel = Ui.state(1);
    private final ArrayList<LodOptimizerLevelFields> levels = new ArrayList<>();
    private final UiRoot root;
    private final LodOptimizerSession session;
    private final LodOptimizerPreview preview;
    private final UiNode[] images = new UiNode[2];
    private int imageHeight = 350, revision = -1, shownLevel = -1, shownPixels = -1;

    public LodOptimizerControls(Fdx fdx, Input input, LodOptimizerSession session, LodOptimizerPreview preview) {
        this.session = session; this.preview = preview;
        UiTextStyle text = UiTextStyle.text().font(UiFont.freeType("font/freetype/lsans.ttf",16))
                .size(16).lineHeight(21).color(UiColor.rgba8888(0xe9f2ffff));
        root = new UiToolkit(fdx.files()).theme(Ui.darkTheme().text(UiStyle.style().text(text))
                .style("active",UiStyle.button().background(UiDrawable.color(UiColor.rgba8888(0x287f91ff)))))
                .root(fdx.displays().main(),fdx.graphics().main()).input(input);
        preset(1);
        root.setContent(this::build);
    }

    private void build(UiScope ui) {
        ui.column(Ui.modifier().fill().padding(16).gap(10), page -> {
            page.text("Model LOD optimizer",Ui.modifier().height(28).style("title"));
            page.text("Load a model, tune the levels, then generate and compare. Animations stay synchronized.",Ui.modifier().height(24));
            page.row(Ui.modifier().fillWidth().height(34).gap(8), row -> {
                row.textField(Ui.modifier().fillWidth().weight(1).validationId("optimizer.path").semanticLabel("glTF or GLB asset path"),path);
                row.button("Load",Ui.modifier().width(80).validationId("optimizer.load").enabled(!session.busy() && session.supported()),() -> session.load(path.get()));
                assetButton(row,"Helmet",LodOptimizerSession.HELMET,"helmet");
                assetButton(row,"Duck",LodOptimizerSession.DUCK,"duck");
                assetButton(row,"Dragon",LodOptimizerSession.DRAGON,"dragon");
                assetButton(row,"Bend",LodOptimizerSession.ANIMATED,"animated");
            });
            page.row(Ui.modifier().fillWidth().weight(1).gap(14), body -> {
                body.column(Ui.modifier().width(360).fillHeight().gap(8), settings -> {
                    settings.row(Ui.modifier().fillWidth().height(32).gap(5), row -> {
                        presetButton(row,"Quality",0); presetButton(row,"Balanced",1); presetButton(row,"Small",2);
                    });
                    settings.row(Ui.modifier().fillWidth().height(32).gap(8), row -> {
                        row.button("Add LOD",Ui.modifier().fillWidth().weight(1).validationId("optimizer.add").enabled(levels.size()<8),this::addLevel);
                        row.button("Remove last",Ui.modifier().fillWidth().weight(1).validationId("optimizer.remove").enabled(levels.size()>1),() -> { levels.remove(levels.size()-1); root.requestCompose(); });
                    });
                    settings.scroll(Ui.modifier().fillWidth().weight(1), scroll -> scroll.column(Ui.modifier().fillWidth().gap(8), form -> {
                        form.text("Level / triangles % / error % / switch px",Ui.modifier().height(23));
                        for (int i=0;i<levels.size();i++) levelRow(form,i);
                        form.text("Errors are relative to each primitive's extent.",Ui.modifier().height(24));
                        form.text("Ratios and pixel thresholds must decrease.",Ui.modifier().height(24));
                        checkbox(form,"Lock open boundaries",borders,"borders");
                        form.text("Attribute seams are protected.",Ui.modifier().height(24));
                        slider(form,"Normal weight",normalWeight,0,5,"normal");
                        slider(form,"UV weight",uvWeight,0,100,"uv");
                        slider(form,"Color weight",colorWeight,0,5,"color");
                        slider(form,"Hysteresis",hysteresis,0,.3f,"hysteresis");
                        checkbox(form,"Optimize vertex cache",cache,"cache");
                        checkbox(form,"Optimize vertex fetch",fetch,"fetch");
                        form.text("Higher weights protect surface attributes.",Ui.modifier().height(24));
                        form.text("Strict quality may prevent a target ratio.",Ui.modifier().height(24));
                    }));
                    settings.button(session.busy()?"Working...":"Generate LODs",Ui.modifier().fillWidth().height(40)
                            .validationId("optimizer.generate").enabled(!session.busy() && session.base()!=null && session.supported()),this::generate);
                });
                body.column(Ui.modifier().fillWidth().weight(1).fillHeight().gap(8), comparison -> {
                    comparison.row(Ui.modifier().fillWidth().height(imageHeight+30).gap(10), row -> {
                        image(row,0,"Original - " + (session.input()==null?0:session.input().triangleCount()) + " triangles");
                        image(row,1,"LOD " + preview.level() + " - " + selectedTriangles() + " triangles");
                    });
                    comparison.row(Ui.modifier().fillWidth().height(32).gap(5), row -> {
                        mode(row,"Auto",-1); mode(row,"Original",0);
                        if (session.generated()!=null) for (int i=1;i<=session.generated().levelCount();i++) mode(row,""+i,i);
                    });
                    slider(comparison,"Distance",distance,3.5f,80,"distance");
                    slider(comparison,"Orbit",yaw,-3.14f,3.14f,"orbit");
                    comparison.text("Projected size: " + Math.round(preview.pixels()) + " px. Preview levels up close to inspect detail.",Ui.modifier().height(24));
                    comparison.scroll(Ui.modifier().fillWidth().weight(1), scroll -> scroll.column(Ui.modifier().fillWidth().gap(3), reports -> {
                        if (session.generated()!=null) for (int i=1;i<=session.generated().levelCount();i++) {
                            ModelLodReport report = session.generated().report(i);
                            reports.text("LOD " + i + ": " + report.triangles() + " tris / " + report.vertices() + " vertices / error "
                                    + number(report.error()*100) + "%" + (report.targetReached()?"":" / quality limited"),Ui.modifier().height(24));
                        }
                    }));
                });
            });
            page.text(session.status(),Ui.modifier().fillWidth().height(42));
        });
    }
    private void image(UiScope row,int side,String title) {
        row.column(Ui.modifier().fillWidth().weight(1).gap(6), panel -> {
            panel.text(title,Ui.modifier().fillWidth().height(24));
            images[side]=panel.image(preview.image(side),Ui.modifier().fillWidth().height(imageHeight));
        });
    }
    private int selectedTriangles() {
        if (session.input()==null) return 0;
        if (session.generated()==null || preview.level()==0) return session.input().triangleCount();
        return session.generated().report(Math.min(preview.level(),session.generated().levelCount())).triangles();
    }
    private void levelRow(UiScope form,int i) {
        LodOptimizerLevelFields fields = levels.get(i);
        form.row(Ui.modifier().fillWidth().height(32).gap(6), row -> {
            row.text("LOD " + (i+1),Ui.modifier().width(50));
            field(row,fields.ratio,"ratio",i); field(row,fields.error,"error",i); field(row,fields.pixels,"pixels",i);
        });
    }
    private void field(UiScope row,UiState<String> state,String name,int i) {
        row.floatField(Ui.modifier().fillWidth().weight(1).validationId("optimizer."+name+(i+1)).semanticLabel("LOD "+(i+1)+" "+name),state);
    }
    private void assetButton(UiScope row,String title,String asset,String id) {
        row.button(title,Ui.modifier().width(80).validationId("optimizer."+id).enabled(!session.busy() && session.supported()),() -> { path.set(asset); session.load(asset); });
    }
    private void presetButton(UiScope row,String title,int preset) {
        row.button(title,Ui.modifier().fillWidth().weight(1).validationId("optimizer.preset"+preset),() -> preset(preset));
    }
    private void mode(UiScope row,String title,int mode) {
        row.button(title,Ui.modifier().fillWidth().weight(1).validationId("optimizer.preview"+mode)
                .style(previewLevel.get()==mode?"active":"button"),() -> { previewLevel.set(mode); if (mode>=0) distance.set(4); });
    }
    private void checkbox(UiScope form,String title,UiBooleanState state,String id) {
        form.row(Ui.modifier().fillWidth().height(28).gap(7), row -> {
            row.checkbox(Ui.modifier().validationId("optimizer."+id).semanticLabel(title),state); row.text(title);
        });
    }
    private void slider(UiScope form,String title,UiFloatState state,float min,float max,String id) {
        form.row(Ui.modifier().fillWidth().height(30).gap(7), row -> {
            row.text(title,Ui.modifier().width(110));
            row.slider(Ui.modifier().fillWidth().weight(1).validationId("optimizer."+id).semanticLabel(title),state,min,max);
            row.text(number(state.get()),Ui.modifier().width(42));
        });
    }
    public void preset(int value) {
        levels.clear();
        float[] ratios = value==0?new float[]{70,40,20}:value==2?new float[]{35,10,3}:new float[]{50,20,7};
        float[] errors = value==0?new float[]{.3f,1,3}:value==2?new float[]{3,8,15}:new float[]{1,3,8};
        for (int i=0;i<3;i++) levels.add(new LodOptimizerLevelFields(ratios[i],errors[i],new float[]{240,100,40}[i]));
        borders.set(false); normalWeight.set(.5f); uvWeight.set(10); colorWeight.set(1);
        cache.set(true); fetch.set(true); hysteresis.set(.1f); root.requestCompose();
    }
    private void addLevel() {
        try {
            LodOptimizerLevelFields last = levels.get(levels.size()-1);
            levels.add(new LodOptimizerLevelFields(Float.parseFloat(last.ratio.get())*.5f,
                    Math.min(100,Float.parseFloat(last.error.get())*1.5f),Float.parseFloat(last.pixels.get())*.5f));
            root.requestCompose();
        } catch (RuntimeException failure) { session.message("Enter valid numbers before adding another level."); }
    }
    public ModelLodSettings settings() {
        ModelLodTarget[] targets = new ModelLodTarget[levels.size()];
        for (int i=0;i<targets.length;i++) {
            LodOptimizerLevelFields fields = levels.get(i);
            targets[i] = new ModelLodTarget(Float.parseFloat(fields.ratio.get())/100,
                    Float.parseFloat(fields.error.get())/100,Float.parseFloat(fields.pixels.get()));
        }
        return new ModelLodSettings(targets,hysteresis.get(),borders.get(),normalWeight.get(),uvWeight.get(),colorWeight.get(),cache.get(),fetch.get());
    }
    public void generate() {
        try { session.generate(settings()); }
        catch (IllegalArgumentException failure) { session.message("Invalid settings: " + failure.getMessage()); }
    }
    public int levelCount() { return levels.size(); }
    public void update(float delta) { root.update(delta); }
    public void render() {
        int pixels = Math.round(preview.pixels());
        if (revision!=session.revision() || shownLevel!=preview.level() || shownPixels!=pixels) {
            revision=session.revision(); shownLevel=preview.level(); shownPixels=pixels; root.requestCompose();
        }
        root.render();
    }
    public void resize(int width,int height,int pixelWidth,int pixelHeight) {
        root.resize(width,height);
        float uiWidth=root.uiX(width),uiHeight=root.uiY(height);
        imageHeight = Math.max(150,Math.round(uiHeight-450));
        preview.resize(Math.max(1,Math.round((uiWidth-416)/2f*pixelWidth/uiWidth)),Math.max(1,Math.round(imageHeight*pixelHeight/uiHeight)));
        root.requestCompose();
    }
    public UiRoot root() { return root; }
    public UiRect imageBounds(int side) { return images[side].bounds(); }
    private static String number(float value) { return Float.toString(Math.round(value*100)/100f); }
    @Override public boolean isDisposed() { return root.isDisposed(); }
    @Override public void dispose() { root.dispose(); }
}
