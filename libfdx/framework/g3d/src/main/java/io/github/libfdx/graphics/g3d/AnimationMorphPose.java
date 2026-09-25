package io.github.libfdx.graphics.g3d;

import java.util.Arrays;

/** Controller-owned morph crossfade buffers; sampling validates before committing instance state. */
final class AnimationMorphPose {
    private final DefaultModelInstance instance;
    private final NodeMorphChannel[] active, outgoing, pending;
    private final float[][] current, candidate, frozen, other;
    private final boolean[] controlled;
    private boolean sampleOutgoing;

    AnimationMorphPose(DefaultModelInstance instance) {
        this.instance=instance; int count=instance.animationNodeCount();
        active=new NodeMorphChannel[count]; outgoing=active.clone(); pending=active.clone();
        controlled=new boolean[count]; current=new float[count][]; candidate=new float[count][];
        frozen=new float[count][]; other=new float[count][];
        for (int i=0;i<count;i++) {
            current[i]=instance.animationMorphWeights(i).clone(); candidate[i]=current[i].clone();
            frozen[i]=current[i].clone(); other[i]=current[i].clone();
        }
    }
    void bind(AnimationClip clip, boolean fade, boolean interrupted) {
        Arrays.fill(pending,null);
        for (NodeMorphChannel channel : clip.morphChannelsUnsafe()) {
            int node=instance.animationNodeIndex(channel.nodeId());
            if (pending[node] != null || channel.sampler().components() != current[node].length)
                throw new IllegalArgumentException("Morph animation target/count mismatch: " + channel.nodeId());
            pending[node]=channel;
        }
        for (int node=0;node<pending.length;node++) if (pending[node] != null && !controlled[node]) {
            System.arraycopy(instance.animationMorphWeights(node),0,current[node],0,current[node].length);
            controlled[node]=true;
        }
        if (fade) {
            System.arraycopy(active,0,outgoing,0,active.length);
            for (int node=0;node<current.length;node++) System.arraycopy(current[node],0,frozen[node],0,current[node].length);
        } else Arrays.fill(outgoing,null);
        sampleOutgoing=fade && !interrupted; System.arraycopy(pending,0,active,0,active.length);
    }
    void sample(double time, double outgoingTime, double alpha) {
        for (int node=0;node<active.length;node++) if (controlled[node]) {
            sample(active[node],time,instance.animationMorphDefaults(node),candidate[node]);
            if (alpha < 1) {
                if (sampleOutgoing && outgoing[node] != null) sample(outgoing[node],outgoingTime,frozen[node],other[node]);
                else System.arraycopy(frozen[node],0,other[node],0,other[node].length);
                for (int i=0;i<candidate[node].length;i++) {
                    float value=(float)((1-alpha)*other[node][i]+alpha*candidate[node][i]);
                    if (!Float.isFinite(value)) throw new IllegalArgumentException("Morph crossfade overflow");
                    candidate[node][i]=value;
                }
            }
        }
    }
    void commit() {
        for (int node=0;node<active.length;node++) if (controlled[node]) {
            System.arraycopy(candidate[node],0,instance.animationMorphWeights(node),0,candidate[node].length);
            System.arraycopy(candidate[node],0,current[node],0,candidate[node].length);
        }
    }
    void clearOutgoing() { Arrays.fill(outgoing,null); sampleOutgoing=false; }
    private static void sample(NodeMorphChannel channel,double time,float[] defaults,float[] out) {
        if (channel == null) System.arraycopy(defaults,0,out,0,out.length);
        else channel.sampler().sample((float)time,out,0);
    }
}
