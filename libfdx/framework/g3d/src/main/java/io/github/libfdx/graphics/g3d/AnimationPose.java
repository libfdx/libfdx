package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import java.util.Arrays;

/** Controller-owned pose storage. Updates sample first, then commit the hierarchy once. */
final class AnimationPose {
    private final DefaultModelInstance instance;
    private final AnimationClip.NodeTransformChannel[] active, outgoing, pending;
    private final float[] defaults, current, candidate, frozen;
    private final float[] scratch = new float[16], other = new float[10];
    private final io.github.libfdx.math.Matrix4 defaultMatrix=new io.github.libfdx.math.Matrix4();
    private final float[] defaultValues=new float[16];
    private final boolean[] initialized;
    private final int[] controlled;
    private int count;
    private boolean sampleOutgoing;

    AnimationPose(DefaultModelInstance instance) {
        this.instance=instance;
        int nodes=instance.animationNodeCount();
        active=new AnimationClip.NodeTransformChannel[nodes]; outgoing=active.clone(); pending=active.clone();
        defaults=new float[Math.multiplyExact(nodes,10)]; current=defaults.clone(); candidate=defaults.clone(); frozen=defaults.clone();
        initialized=new boolean[nodes]; controlled=new int[nodes];
    }
    void bind(AnimationClip clip,boolean fade,boolean interrupted) {
        Arrays.fill(pending,null);
        for (AnimationClip.NodeTransformChannel channel : clip.nodeTransformChannelsUnsafe()) {
            int node=instance.animationNodeIndex(channel.nodeId());
            if (pending[node] != null) throw new FdxException("Animation repeats node " + channel.nodeId());
            pending[node]=channel;
            if (!initialized[node] && !channel.copyDefaults(candidate,node*10))
                AnimationTransforms.decompose(instance.animationDefault(node),candidate,node*10,scratch);
            if (fade && !initialized[node]) {
                AnimationTransforms.matrix(candidate,node*10,defaultMatrix).copyValues(defaultValues,0);
                instance.animationLocal(node).copyValues(scratch,0);
                boolean same=true;
                for (int i=0;i<16;i++) if (Math.abs(defaultValues[i]-scratch[i])>1e-5f*Math.max(1,Math.abs(scratch[i]))) { same=false; break; }
                if (same) System.arraycopy(candidate,node*10,frozen,node*10,10);
                else AnimationTransforms.decompose(instance.animationLocal(node),frozen,node*10,scratch);
            }
        }
        // Validate names/defaults before changing active bindings or controlled nodes.
        for (int node=0;node<pending.length;node++) if (pending[node] != null && !initialized[node]) {
            initialized[node]=true; controlled[count++]=node;
            System.arraycopy(candidate,node*10,defaults,node*10,10);
            System.arraycopy(fade ? frozen : candidate,node*10,current,node*10,10);
        }
        if (fade) {
            System.arraycopy(active,0,outgoing,0,active.length);
            System.arraycopy(current,0,frozen,0,current.length);
        } else Arrays.fill(outgoing,null);
        sampleOutgoing=fade && !interrupted;
        System.arraycopy(pending,0,active,0,active.length);
    }
    void apply(double time,double outgoingTime,double alpha) {
        for (int i=0;i<count;i++) {
            int node=controlled[i],offset=node*10;
            sample(active[node],time,defaults,offset,candidate,offset);
            if (alpha < 1) {
                if (sampleOutgoing && outgoing[node] != null) sample(outgoing[node],outgoingTime,defaults,offset,other,0);
                else System.arraycopy(frozen,offset,other,0,10);
                AnimationTransforms.blend(other,0,candidate,offset,alpha,candidate,offset);
            }
        }
        // Failed sampling leaves every instance transform untouched.
        for (int i=0;i<count;i++) {
            int node=controlled[i]; AnimationTransforms.matrix(candidate,node*10,instance.animationLocal(node));
        }
        if (count != 0) instance.applyAnimationTransforms();
        for (int i=0;i<count;i++) {
            int offset=controlled[i]*10; System.arraycopy(candidate,offset,current,offset,10);
        }
    }
    void clearOutgoing() { Arrays.fill(outgoing,null); sampleOutgoing=false; }
    private static void sample(AnimationClip.NodeTransformChannel channel,double time,float[] base,int offset,float[] out,int outOffset) {
        if (channel != null) channel.sampleTrs((float)time,out,outOffset);
        else System.arraycopy(base,offset,out,outOffset,10);
    }
}
