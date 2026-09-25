package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.graphics.g3d.MorphTarget;

/** Resumable remapping of influences and morph deltas into one uploaded mesh chunk. */
final class LodDeformationPackingTask {
    private final MeshLodData source;
    private final MeshLodDeformation data;
    private final int[] references;
    private final int vertices;
    private final MorphTarget[] targets;
    private int[] joints;
    private float[] weights;
    private float[][] deltas;
    private int phase,cursor,target,channel;
    private boolean done;
    LodDeformationPackingTask(MeshLodData source,int[] references,int vertices) {
        this.source=source;data=source.deformation();this.references=references;this.vertices=vertices;
        targets=new MorphTarget[data.morphTargetCount()];
    }
    boolean step(int budget) {
        while (budget-- > 0 && !done) switch(phase) {
            case 0 -> { if (data.skinned()) joints=new int[vertices*4];phase++;return false; }
            case 1 -> { if (data.skinned()) weights=new float[vertices*4];phase++;return false; }
            case 2 -> {
                if (data.skinned() && cursor<vertices) {
                    int origin=origin(cursor);
                    for (int i=0;i<4;i++) { joints[cursor*4+i]=data.joint(origin,i);weights[cursor*4+i]=data.weight(origin,i); }
                    cursor++;
                } else { phase++;cursor=0; }
            }
            default -> {
                if (target==targets.length) { done=true;continue; }
                MorphTarget input=data.morphTarget(target);
                if (deltas==null) deltas=new float[3][];
                if (channel==3) {
                    targets[target++]=new MorphTarget(input.id(),input.weight(),deltas[0],deltas[1],deltas[2]);
                    deltas=null;channel=cursor=0;return false;
                }
                if (!input.hasAttribute(channel)) { channel++;continue; }
                if (deltas[channel]==null) { deltas[channel]=new float[vertices*3];return false; }
                if (cursor<vertices) {
                    int origin=origin(cursor);for (int c=0;c<3;c++) deltas[channel][cursor*3+c]=input.delta(channel,origin,c);
                    cursor++;
                } else { cursor=0;channel++; }
            }
        }
        return done;
    }
    private int origin(int vertex) { return source.sourceVertex(references==null ? vertex : references[vertex]); }
    int[] joints() { return joints; }
    float[] weights() { return weights; }
    MorphTarget[] targets() { if (!done) throw new IllegalStateException("Deformation packing incomplete");return targets; }
}
