package io.github.libfdx.testsupport.graphics.lodoptimizer;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.g3d.lod.ModelLodFallback;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.graphics.g3d.lod.ModelLodView;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.Key;
import io.github.libfdx.testsupport.graphics.FramebufferCapture;
import io.github.libfdx.testsupport.graphics.lod.ModelLodInputDriver;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.validation.scenario.*;
import io.github.libfdx.validation.scenario.ui.kit.UiScenarioActions;
import io.github.libfdx.validation.scenario.ui.kit.UiScenarioTargets;

/** Real UI input plus asynchronous generation, indexed GPU upload and rendered-frame checks. */
public final class LodOptimizerVerification {
    private final LodOptimizerControls controls;
    private final LodOptimizerSession session;
    private final LodOptimizerPreview preview;
    private final DefaultInput input;
    private final Logger logger;
    private final ScenarioHost host;
    private final Scenario[] phases;
    private final String captures=System.getProperty("libfdx.test.optimizerCaptures","").trim();
    private int phase,frames;
    private int layoutWait;
    private boolean applied;
    private final boolean animated=Boolean.getBoolean("libfdx.test.optimizerAnimated");

    public LodOptimizerVerification(LodOptimizerControls controls,LodOptimizerSession session,LodOptimizerPreview preview,DefaultInput input,Logger logger) {
        this.controls=controls; this.session=session; this.preview=preview; this.input=input; this.logger=logger;
        host=ScenarioHost.create().registerProbe(UiRoot.class,controls.root()).inputDriver(new ModelLodInputDriver(input))
                .frameDriver(context -> controls.update(1f/60));
        phases=animated?animatedScenarios():new Scenario[]{
                click("load","optimizer.load"),
                click("generate","optimizer.generate"),
                click("preview2","optimizer.preview2"),
                click("preview3","optimizer.preview3"),
                click("automatic-far","optimizer.preview-1").action(UiScenarioActions.dragSlider("optimizer.distance",.01f,.99f)),
                click("generate-four","optimizer.preset2").action(UiScenarioActions.click("optimizer.add")).action(UiScenarioActions.click("optimizer.generate")),
                click("preview4","optimizer.preview4"),
                replace("invalid-settings","optimizer.ratio1","100").action(UiScenarioActions.click("optimizer.generate")),
                click("remove-level","optimizer.remove"),
                click("rebuild","optimizer.preset1").action(UiScenarioActions.click("optimizer.generate")),
                replace("missing-model-rejected","optimizer.path","gltf-animation/missing.glb").action(UiScenarioActions.click("optimizer.load")),
                click("load-duck","optimizer.duck"),
                click("generate-duck","optimizer.generate"),
                click("duck-low","optimizer.preview3"),
                click("reload-helmet","optimizer.helmet"),
                click("regenerate-helmet","optimizer.generate").action(UiScenarioActions.click("optimizer.preview2"))
        };
    }
    private Scenario[] animatedScenarios() {
        Scenario[] result=new Scenario[21];
        String[] assets={"skin","morph","combined"};
        for (int i=0;i<assets.length;i++) {
            String name=assets[i];int at=i*7;
            result[at]=replace(name+"-load","optimizer.path","gltf-animation/lod/"+name+".gltf").action(UiScenarioActions.click("optimizer.load"));
            result[at+1]=click(name+"-generate","optimizer.generate");
            result[at+2]=click(name+"-half-pose","optimizer.preview2");
            result[at+3]=click(name+"-peak-pose","optimizer.preview3");
            result[at+4]=click(name+"-second-pose","optimizer.preview1");
            result[at+5]=click(name+"-far","optimizer.preview-1").action(UiScenarioActions.dragSlider("optimizer.distance",.01f,.99f));
            result[at+6]=click(name+"-rebuild","optimizer.generate");
        }
        return result;
    }
    private static Scenario click(String name,String id) { return Scenario.named(name).action(UiScenarioActions.click(id)); }
    private Scenario replace(String name,String id,String value) {
        return Scenario.named(name).action(UiScenarioActions.focus(id)).action(ScenarioActions.callback("select all",context -> {
            input.dispatchKeyDown(Key.CONTROL_LEFT); input.dispatchKeyDown(Key.A); input.dispatchKeyUp(Key.A); input.dispatchKeyUp(Key.CONTROL_LEFT);
        })).action(ScenarioActions.type(value)).action(ScenarioActions.key(Key.ENTER));
    }
    public void beforeFrame() {
        if (complete() || applied) return;
        if (animated) {
            String id=new String[]{"path","generate","preview2","preview3","preview1","preview-1","generate"}[phase%7];
            var node=UiScenarioTargets.id("optimizer."+id).resolve(controls.root());
            if (node==null || node.bounds().width()<=0 || node.bounds().height()<=0) {
                if (++layoutWait>30) fail("UI layout did not settle for "+id);
                return;
            }
            layoutWait=0;
        }
        // The last load removes preview buttons until the new chain is published.
        Scenario action=!animated && phase==15?click("regenerate-helmet","optimizer.generate"):phases[phase];
        ScenarioResult result=host.run(action);
        if (!result.passed()) throw new FdxException("Optimizer UI "+phases[phase].name()+": "+result.operationName()+": "+result.message());
        applied=true;
        if (animated) session.animationTime(new float[]{0,0,.5f,1,2,3,1}[phase%7]);
    }
    public boolean afterFrame(GraphicsContext graphics) {
        if (complete() || !applied || session.busy() || !preview.ready() || ++frames<8) return false;
        String name=phases[phase].name();
        if (animated) return afterAnimatedFrame(graphics,name);
        if (phase==0 && session.input()==null) fail("Model failed to load");
        if (phase==1) generation(1,3);
        if (phase==2) level(2);
        if (phase==3 || phase==4) level(3);
        if (phase==5) { generation(2,4); if (controls.levelCount()!=4) fail("Add level did not update settings"); }
        if (phase==6) { level(4); if (preview.pixels()<100) fail("Preview did not return to a close view"); }
        if (phase==7) { generation(2,4); if (!session.status().startsWith("Invalid settings:")) fail("Invalid ratio was accepted"); }
        if (phase==8 && controls.levelCount()!=3) fail("Remove last did not remove a setting");
        if (phase==9) generation(3,3);
        if (phase==10) { generation(3,3); if (!session.status().startsWith("Load rejected:")) fail("Missing model was not rejected safely"); }
        if (phase==11 || phase==14) { if (session.generated()!=null) fail("Replacing the source retained stale LODs"); }
        if (phase==12) generation(4,3);
        if (phase==13) level(3);
        if (phase==15) {
            if (frames==8) {
                generation(5,3);
                ScenarioResult result=host.run(click("final-preview","optimizer.preview2"));
                if (!result.passed()) fail(result.message());
                return false;
            }
            if (frames<16) return false;
            level(2);
        }
        boolean captured=!captures.isEmpty() && (phase==3 || phase==6 || phase==13 || phase==15);
        if (captured) capture(graphics,name);
        logger.info("ModelLodOptimizerTest "+name+" PASS generation="+session.generation()+" level="+preview.level()+" status="+session.status());
        phase++; frames=0; applied=false;
        return captured;
    }
    private boolean afterAnimatedFrame(GraphicsContext graphics,String name) {
        int step=phase%7,variant=phase/7;
        if (phase==phases.length-1) {
            if (frames==8) {
                ScenarioResult result=host.run(click("final-animated-preview","optimizer.preview2"));
                if (!result.passed()) fail(result.message());
                return false;
            }
            if (frames<16) return false;
            level(2);
        }
        if (step==0) {
            if (session.base()==null || session.generated()!=null || session.input().maxDeformationSamples()<6) fail("Animated source did not load");
        } else {
            generation(variant*2+(step==6?2:1),3);
            if (step==2) level(2);
            if (step==3 || step==5) level(3);
            if (step==4) level(1);
            if (step>=2) {
                DefaultModelInstance selected=(DefaultModelInstance)session.binding().selectLevel(new ModelLodView(),3);
                float[] a=new float[16],b=new float[16];
                session.base().copyNodeModelTransform("tip",new Matrix4()).copyValues(a,0);
                selected.copyNodeModelTransform("tip",new Matrix4()).copyValues(b,0);
                for (int k=0;k<16;k++) if (a[k]!=b[k]) fail("LOD skin pose diverged");
                int targets=variant==0?0:2;
                if (selected.morphTargetCount("surface")!=targets) fail("Morph target count changed");
                if (targets>0) {
                    session.base().copyMorphWeights("surface",a,0);selected.copyMorphWeights("surface",b,0);
                    for (int k=0;k<targets;k++) if (a[k]!=b[k]) fail("LOD morph weights diverged");
                }
                if (selected.model().animations().size()!=session.base().model().animations().size()) fail("Animation clips lost");
            }
        }
        boolean captured=step>=2 && step<=4;
        if (captured) capture(graphics,name);
        logger.info("ModelLodOptimizerTest "+name+" PASS time="+session.animationTime()+" generation="+session.generation()+" level="+preview.level());
        phase++;frames=0;applied=false;return captured;
    }
    private void generation(int count,int levels) {
        if (session.generation()!=count || session.generated()==null || session.generated().levelCount()!=levels) fail("Unexpected generated chain");
        int source=session.input().triangleCount();
        for (int i=1;i<=levels;i++) {
            var report=session.generated().report(i);
            if (report.triangles()<=0 || report.triangles()>source) fail("Invalid generated triangle count");
            logger.info("LOD_GENERATED level="+i+" triangles="+report.triangles()+" source="+source+" vertices="+report.vertices()+" error="+report.error()+" reached="+report.targetReached());
        }
        if (session.generated().report(levels).triangles()>=source) fail("Real model was not reduced");
    }
    private void level(int expected) {
        if (preview.level()!=expected || preview.fallback()!=ModelLodFallback.NONE) fail("Expected LOD "+expected+", got "+preview.level()+" / "+preview.fallback());
    }
    private void capture(GraphicsContext graphics,String name) {
        try {
            int width=graphics.currentFrame().width(),height=graphics.currentFrame().height();
            var pixels=FramebufferCapture.readPixelsRgba8(graphics);
            FramebufferCapture.validateSceneFrame(width,height,pixels);
            LodOptimizerFrameCheck.verify(pixels,width,height,controls,!animated && phase==13);
            if (animated) LodOptimizerFrameCheck.verifyAnimated(pixels,width,height,controls);
            if (!captures.isEmpty()) FramebufferCapture.writePpm(captures+"/"+graphics.providerId().value()+"/"+name+".ppm",width,height,pixels);
        } catch (Exception failure) { throw new FdxException("Optimizer capture "+name,failure); }
    }
    private void fail(String message) { throw new FdxException("Optimizer "+phases[phase].name()+": "+message+"; "+session.status()); }
    public boolean complete() { return phase==phases.length; }
    public void requireComplete() { if (!complete()) fail("Only "+phase+" / "+phases.length+" scenarios completed"); }
}
