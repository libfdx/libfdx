package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.math.Matrix4;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class AnimationPlaybackTest {
    private static DefaultModelInstance instance() {
        ModelNode root=new ModelNode("root"),arm=new ModelNode("arm");
        root.localTransform().setToTranslation(1,0,0); arm.localTransform().setToTranslation(0,1,0); root.addChild(arm);
        Array<ModelNode> nodes=new Array<>(); nodes.add(root);
        return new DefaultModelInstance(new DefaultModel(nodes,new Array<Material>(),new Array<AnimationClip>(),new Array<Mesh>()));
    }
    private static AnimationClip move(String id,float start,float end) {
        return new AnimationClip(id,2,new AnimationClip.NodeTransformChannel[] {AnimationClip.nodeTransform("arm",
                AnimationClip.keyframe(0,0,start,0),AnimationClip.keyframe(2,0,end,0))});
    }
    private static float y(DefaultModelInstance instance) { return instance.copyNodeTransform("arm",new Matrix4()).values()[13]; }
    private static AnimationClip marked() {
        return new AnimationClip("events",1,null,new AnimationClip.Event[] {
                new AnimationClip.Event(0,"zero"),new AnimationClip.Event(.5f,"middle"),new AnimationClip.Event(1,"end")});
    }

    @Test void crossfadeAdvancesBothClipsAndInterruptedFadePreservesTheVisiblePose() {
        DefaultModelInstance instance=instance();
        AnimationController controller=new AnimationController(instance).play(move("a",0,4),false).time(.5f);
        controller.crossFade(move("b",10,14),false,1);
        assertEquals(1,y(instance),1e-6);
        controller.update(.25f);
        assertEquals(3.75f,y(instance),1e-6); assertEquals(.25f,controller.blendProgress());
        controller.crossFade(move("c",20,20),false,2);
        assertEquals(3.75f,y(instance),1e-6);
        controller.update(1);
        assertEquals(11.875f,y(instance),1e-6);
        controller.update(1);
        assertEquals(20,y(instance),1e-6); assertFalse(controller.isBlending());
        controller.crossFade(new AnimationClip("rest",2),false,1).update(.5f);
        assertEquals(10.5f,y(instance),1e-6);
        controller.update(.5f);
        assertEquals(1,y(instance),1e-6);
        controller.play(move("d",5,9),false).time(.5f).stop();
        assertEquals(6,y(instance),1e-6);
        controller.crossFade(move("e",10,10),false,1);
        assertEquals(6,y(instance),1e-6);
    }

    @Test void signedAndZeroScaleSurviveTrsSamplingAndQuaternionBlending() {
        DefaultModelInstance instance=instance();
        var a=AnimationClip.keyframe(0,0,1,0,0,0,0,1,-2,0,3);
        var b=AnimationClip.keyframe(0,0,1,0,0,1,0,0,-4,0,1);
        AnimationClip left=new AnimationClip("left",1,new AnimationClip.NodeTransformChannel[] {
                AnimationClip.sampledTransform("arm",a,null,null,null)});
        AnimationClip right=new AnimationClip("right",1,new AnimationClip.NodeTransformChannel[] {
                AnimationClip.sampledTransform("arm",b,null,null,null)});
        new AnimationController(instance).play(left,false).crossFade(right,false,1).update(.5f);
        float[] actual=instance.copyNodeTransform("arm",new Matrix4()).values();
        float q=(float)Math.sqrt(.5);
        float[] expected=new Matrix4().setToTrs(0,1,0,0,q,0,q,-3,0,2).values();
        assertArrayEquals(expected,actual,1e-5f);
        float[] output=new float[14]; java.util.Arrays.fill(output,91);
        right.nodeTransformChannels()[0].sampleTrs(.5f,output,2);
        assertEquals(91,output[0]); assertEquals(-4,output[9]); assertEquals(0,output[10]); assertEquals(1,output[11]); assertEquals(91,output[13]);
    }

    @Test void firstCrossfadePreservesAnExistingManualPose() {
        DefaultModelInstance instance=instance();
        instance.nodeTransform("arm",new Matrix4().setToTranslation(0,8,0));
        AnimationController controller=new AnimationController(instance).crossFade(move("a",10,10),false,1);
        assertEquals(8,y(instance),1e-5);
        controller.update(.5f); assertEquals(9,y(instance),1e-5);
        controller.update(.5f); assertEquals(10,y(instance),1e-5);
    }

    @Test void eventsCrossEveryLoopInOrderAndOverflowLeavesTimeUnchanged() {
        AnimationController controller=new AnimationController(instance());
        List<String> log=new ArrayList<>();
        controller.listener(new AnimationController.Listener() {
            @Override public void onEvent(AnimationController c,AnimationClip clip,AnimationClip.Event e) { log.add(e.id()); }
            @Override public void onLoop(AnimationController c,AnimationClip clip,long loops) { log.add("loops="+loops); }
        }).maxEventsPerUpdate(5).play(marked(),true);
        assertTrue(log.isEmpty());
        assertThrows(FdxException.class,()->controller.update(2.25f));
        assertEquals(0,controller.timeSeconds()); assertTrue(log.isEmpty());
        controller.maxEventsPerUpdate(8).update(2.25f);
        assertEquals(List.of("middle","end","zero","middle","end","zero","loops=2"),log);
        assertEquals(.25f,controller.timeSeconds());
        log.clear(); controller.time(.75f).update(-.5f).update(0);
        assertTrue(log.isEmpty()); assertEquals(.25f,controller.timeSeconds());
        controller.update(.75f);
        assertEquals(List.of("middle","end","zero","loops=1"),log);
    }

    @Test void nonloopingCompletionAndCallbackPlaybackChangesDoNotRepeatOldEvents() {
        AnimationController controller=new AnimationController(instance());
        List<String> log=new ArrayList<>();
        controller.listener(new AnimationController.Listener() {
            @Override public void onEvent(AnimationController c,AnimationClip clip,AnimationClip.Event e) { log.add(e.id()); }
            @Override public void onComplete(AnimationController c,AnimationClip clip) { log.add("complete"); }
        }).play(marked(),false).update(2).update(2);
        assertEquals(List.of("middle","end","complete"),log); assertTrue(controller.isComplete());
        log.clear();
        controller.listener((c,clip,event)-> { log.add(event.id()); c.play(new AnimationClip("replacement",5),false); })
                .play(marked(),true).update(2.25f);
        assertEquals(List.of("middle"),log); assertEquals("replacement",controller.clip().id()); assertEquals(0,controller.timeSeconds());
        controller.listener((c,clip,event)->assertThrows(FdxException.class,()->c.update(.1f))).play(marked(),true).update(.5f);
        assertEquals(.5f,controller.timeSeconds());
        controller.listener((c,clip,event)-> { throw new IllegalStateException("event failure"); }).play(marked(),false);
        assertThrows(IllegalStateException.class,()->controller.update(.75f));
        assertEquals(.75f,controller.timeSeconds());
        controller.listener(null).update(.25f); assertTrue(controller.isComplete());
    }

    @Test void transitionOnlyEmitsIncomingEventsAndRejectsNonfiniteControlInputs() {
        AnimationController controller=new AnimationController(instance());
        List<String> log=new ArrayList<>();
        AnimationClip incoming=new AnimationClip("incoming",1,null,new AnimationClip.Event[] {new AnimationClip.Event(.25f,"new")});
        controller.listener((c,clip,event)->log.add(clip.id()+":"+event.id())).play(marked(),true).crossFade(incoming,false,1).update(.75f);
        assertEquals(List.of("incoming:new"),log);
        assertThrows(FdxException.class,()->controller.time(Float.POSITIVE_INFINITY));
        assertThrows(FdxException.class,()->controller.update(Float.NaN));
        assertThrows(FdxException.class,()->controller.crossFade(incoming,true,Float.NaN));
        assertThrows(FdxException.class,()->new AnimationClip.Event(Float.NaN,"bad"));
        assertThrows(FdxException.class,()->new AnimationClip("bad",1,null,new AnimationClip.Event[] {new AnimationClip.Event(2,"late")}));
    }

    @Test void sampledCurveFailurePreservesThePreviousPoseAndPlaybackTime() {
        DefaultModelInstance instance=instance();
        var curve=new AnimationSampler(true,AnimationSampler.Interpolation.CUBICSPLINE,new float[] {0,1},
                new float[] {0,0,0,0, 0,0,0,1, 0,0,0,0, 0,0,0,0, 0,0,0,-1, 0,0,0,0});
        AnimationClip clip=new AnimationClip("zero-crossing",1,new AnimationClip.NodeTransformChannel[] {
                AnimationClip.nodeTransform("root",AnimationClip.keyframe(0,1,0,0),AnimationClip.keyframe(1,5,0,0)),
                AnimationClip.sampledTransform("arm",AnimationClip.keyframe(0,0,1,0),null,curve,null)});
        AnimationController controller=new AnimationController(instance).play(clip,false).time(.25f);
        float[] before=instance.copyNodeTransform("root",new Matrix4()).values();
        assertThrows(FdxException.class,()->controller.update(.25f));
        assertArrayEquals(before,instance.copyNodeTransform("root",new Matrix4()).values());
        assertEquals(.25f,controller.timeSeconds());
    }

    @Test void crossfadeUpdatesDoNotAllocateAfterWarmup() {
        AnimationController controller=new AnimationController(instance()).play(move("a",0,4),true)
                .crossFade(move("b",10,14),true,100_000);
        for (int i=0;i<2000;i++) controller.update(.001f);
        var platform=ManagementFactory.getThreadMXBean(); assumeTrue(platform instanceof ThreadMXBean);
        ThreadMXBean bean=(ThreadMXBean)platform; assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        long id=Thread.currentThread().threadId(),minimum=Long.MAX_VALUE;
        for (int attempt=0;attempt<5;attempt++) {
            long before=bean.getThreadAllocatedBytes(id);
            for (int i=0;i<2000;i++) controller.update(.001f);
            minimum=Math.min(minimum,bean.getThreadAllocatedBytes(id)-before);
        }
        assertTrue(minimum <= 1024,"Animation update allocated "+minimum+" bytes for 2000 operations");
    }

    @Test void programmaticMatrixDefaultsRoundTripEveryZeroAndSignedScaleCombination() {
        float[] trs=new float[10],scratch=new float[16];
        for (int mask=0;mask<8;mask++) for (int signs=0;signs<8;signs++) {
            float sx=(mask&1)==0 ? 0 : (signs&1)==0 ? 2 : -2;
            float sy=(mask&2)==0 ? 0 : (signs&2)==0 ? 3 : -3;
            float sz=(mask&4)==0 ? 0 : (signs&4)==0 ? 4 : -4;
            Matrix4 input=new Matrix4().setToTrs(1,2,3,.2f,.4f,.1f,(float)Math.sqrt(.79),sx,sy,sz);
            AnimationTransforms.decompose(input,trs,0,scratch);
            assertArrayEquals(input.values(),AnimationTransforms.matrix(trs,0,new Matrix4()).values(),1e-5f);
        }
    }
}
