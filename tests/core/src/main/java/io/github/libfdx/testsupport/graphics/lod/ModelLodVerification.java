package io.github.libfdx.testsupport.graphics.lod;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.g3d.lod.ModelLodFallback;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.testsupport.graphics.FramebufferCapture;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.validation.scenario.Scenario;
import io.github.libfdx.validation.scenario.ScenarioHost;
import io.github.libfdx.validation.scenario.ScenarioResult;
import io.github.libfdx.validation.scenario.ui.kit.UiScenarioActions;

/** Automatic chooser coverage: real control input, then multiple rendered frames and assertions. */
public final class ModelLodVerification {
    private final ModelLodControls controls;
    private final ModelLodScene scene;
    private final ModelLodLayout layout;
    private final Logger logger;
    private final ScenarioHost host;
    private final String captures = System.getProperty("libfdx.test.lodCaptures", "").trim();
    private final Scenario[] phases = {
            Scenario.named("near").action(UiScenarioActions.click("lod.near")),
            Scenario.named("middle").action(UiScenarioActions.click("lod.middle")),
            Scenario.named("far").action(UiScenarioActions.click("lod.far")),
            Scenario.named("missing").action(UiScenarioActions.click("lod.missing")),
            Scenario.named("restored").action(UiScenarioActions.click("lod.missing")),
            Scenario.named("disabled").action(UiScenarioActions.click("lod.full")),
            Scenario.named("preview1").action(UiScenarioActions.click("lod.preview1")),
            Scenario.named("preview2").action(UiScenarioActions.click("lod.preview2")),
            Scenario.named("return").action(UiScenarioActions.click("lod.auto")).action(UiScenarioActions.click("lod.near")),
            Scenario.named("orthographic-far").action(UiScenarioActions.click("lod.ortho")).action(UiScenarioActions.click("lod.far")),
            Scenario.named("orthographic-near").action(UiScenarioActions.click("lod.near")),
            Scenario.named("sliders").action(UiScenarioActions.dragSlider("lod.threshold1",.33f,.75f))
                    .action(UiScenarioActions.dragSlider("lod.hysteresis",.33f,.75f)),
            Scenario.named("spin").action(UiScenarioActions.click("lod.spin")),
            Scenario.named("reset").action(UiScenarioActions.click("lod.reset")),
            Scenario.named("distance-slider").action(UiScenarioActions.dragSlider("lod.distance",0,.5f)),
            Scenario.named("tour-start").action(UiScenarioActions.click("lod.near")).action(UiScenarioActions.click("lod.tour")),
            Scenario.named("tour-pause").action(UiScenarioActions.click("lod.tour")),
            Scenario.named("final-reset").action(UiScenarioActions.click("lod.reset"))
    };
    private final int[] expected = {0,1,2,1,2,0,1,2,0,2,0,0,0,0,2,0,0,0};
    private int phase, readyFrames;
    private boolean applied;

    public ModelLodVerification(ModelLodControls controls, ModelLodScene scene, ModelLodLayout layout, DefaultInput input, Logger logger) {
        this.controls = controls; this.scene = scene; this.layout = layout; this.logger = logger;
        host = ScenarioHost.create().registerProbe(UiRoot.class,controls.root())
                .inputDriver(new ModelLodInputDriver(input)).frameDriver(context -> controls.update(1f/60f));
    }

    public void beforeFrame() {
        if (complete() || applied) return;
        ScenarioResult result = host.run(phases[phase]);
        if (!result.passed()) throw new FdxException("LOD controls failed: " + result.operationName() + ": " + result.message());
        applied = true;
    }

    public boolean afterFrame(GraphicsContext graphics) {
        if (complete() || !scene.ready() || ++readyFrames < (phase == 15 ? 2 : 12)) return false;
        String name = phases[phase].name();
        // The close-up limit in short panels can put the model below the edited LOD 1 threshold.
        int expectedLevel = (phase == 11 || phase == 12) && scene.projectedPixels() < controls.mediumPixels.get()
                ? 1 : expected[phase];
        if (scene.renderedLevel() != expectedLevel)
            throw new FdxException("LOD " + name + " expected " + expectedLevel + " but rendered "
                    + scene.renderedLevel() + " at " + scene.projectedPixels() + " px");
        ModelLodFallback fallback = phase == 3 ? ModelLodFallback.UNAVAILABLE : ModelLodFallback.NONE;
        if (scene.fallback() != fallback) throw new FdxException("LOD " + name + " unexpected fallback: " + scene.fallback());
        if (phase == 3 && scene.desiredLevel() != 2) throw new FdxException("Missing resource lost desired LOD");
        if (scene.renderedLevel() > 0 && scene.triangles() >= scene.referenceTriangles())
            throw new FdxException("Reduced LOD did not reduce geometry");
        if (phase == 11 && (controls.mediumPixels.get() < 290 || controls.hysteresis.get() < .2f))
            throw new FdxException("LOD sliders did not respond to pointer dragging");
        if (phase == 13 && (controls.mediumPixels.get() != 220 || controls.hysteresis.get() != .1f || controls.spin.get()))
            throw new FdxException("LOD reset did not restore settings");
        if (phase == 15 && (!controls.tour.get() || controls.distance.get() <= controls.minimumDistance()))
            throw new FdxException("LOD tour did not move the camera");
        if (phase == 16 && controls.tour.get()) throw new FdxException("LOD tour did not pause");
        if (!captures.isEmpty()) capture(graphics,name);
        logger.info("ModelLodTest " + name + " PASS lod=" + scene.renderedLevel() + " triangles=" + scene.triangles()
                + " reference=" + scene.referenceTriangles() + " pixels=" + scene.projectedPixels() + " fallback=" + scene.fallback());
        phase++; readyFrames = 0; applied = false;
        return !captures.isEmpty();
    }

    private void capture(GraphicsContext graphics, String name) {
        try {
            int width = graphics.currentFrame().width(), height = graphics.currentFrame().height();
            var pixels = FramebufferCapture.readPixelsRgba8(graphics);
            FramebufferCapture.validateSceneFrame(width,height,pixels);
            ModelLodFrameCheck.verify(pixels,width,layout);
            FramebufferCapture.writePpm(captures + "/" + graphics.providerId().value() + "/" + name + ".ppm",width,height,pixels);
        } catch (Exception error) { throw new FdxException("LOD capture failed: " + name,error); }
    }

    public boolean complete() { return phase == phases.length; }
    public void requireComplete() {
        if (!complete()) throw new FdxException("ModelLodTest completed " + phase + " of " + phases.length + " UI/render checks");
    }
}
