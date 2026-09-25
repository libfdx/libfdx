package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.graphics.g3d.MorphTarget;

/** Immutable vertex-domain deformation data. Construction copies influences and sampled positions;
 * morph targets are immutable. Samples are XYZ positions after morphing/skinning in one common frame.
 * They constrain reduction but are not a proof over every possible future pose. */
public final class MeshLodDeformation {
    private final int vertices;
    private final int[] joints;
    private final float[] weights;
    private final MorphTarget[] targets;
    private final float[][] poses;

    public MeshLodDeformation(int vertices,int[] joints,float[] weights,MorphTarget[] targets,float[][] poses) {
        if (vertices < 1 || (joints == null) != (weights == null)
                || joints != null && (joints.length != (long)vertices*4 || weights.length != joints.length))
            throw new IllegalArgumentException("Deformation needs a valid vertex domain and four joint influences");
        this.vertices=vertices; this.joints=joints == null ? null : joints.clone(); this.weights=weights == null ? null : weights.clone();
        if (joints != null) for (int i=0;i<joints.length;i++)
            if (joints[i] < 0 || !Float.isFinite(weights[i]) || weights[i] < 0) throw new IllegalArgumentException("Invalid skin influence");
        this.targets=targets == null ? new MorphTarget[0] : targets.clone();
        for (MorphTarget target : this.targets)
            if (target == null || target.vertexCount() != 0 && target.vertexCount() != vertices) throw new IllegalArgumentException("Morph domain mismatch");
        this.poses=poses == null ? new float[0][] : new float[poses.length][];
        for (int p=0;p<this.poses.length;p++) {
            if (poses[p] == null || poses[p].length != (long)vertices*3) throw new IllegalArgumentException("Deformation sample domain mismatch");
            for (float value : poses[p]) if (!Float.isFinite(value)) throw new IllegalArgumentException("Deformation samples must be finite");
            this.poses[p]=poses[p].clone();
        }
        if ((joints != null || this.targets.length > 0) && this.poses.length == 0)
            throw new IllegalArgumentException("Deforming geometry requires explicit pose samples");
    }
    public int vertexCount() { return vertices; }
    public boolean skinned() { return joints != null; }
    public int joint(int vertex,int influence) { return joints[vertex*4+influence]; }
    public float weight(int vertex,int influence) { return weights[vertex*4+influence]; }
    public int morphTargetCount() { return targets.length; }
    public MorphTarget morphTarget(int index) { return targets[index]; }
    public int poseCount() { return poses.length; }
    public float position(int pose,int vertex,int axis) { return poses[pose][vertex*3+axis]; }
    int hash(int vertex,int hash) {
        if (joints != null) for (int i=0;i<4;i++) { hash=(hash^joint(vertex,i))*0x01000193; hash=hash(hash,weight(vertex,i)); }
        for (MorphTarget target : targets) for (int c=0;c<3;c++) if (target.hasAttribute(c))
            for (int k=0;k<3;k++) hash=hash(hash,target.delta(c,vertex,k));
        for (float[] pose : poses) for (int k=0;k<3;k++) hash=hash(hash,pose[vertex*3+k]);
        return hash;
    }
    private static int hash(int hash,float value) { return (hash^(value==0 ? 0 : Float.floatToIntBits(value)))*0x01000193; }
    boolean same(int a,int b) {
        if (joints != null) for (int i=0;i<4;i++) if (joint(a,i)!=joint(b,i) || weight(a,i)!=weight(b,i)) return false;
        for (MorphTarget target : targets) for (int c=0;c<3;c++) if (target.hasAttribute(c))
            for (int k=0;k<3;k++) if (target.delta(c,a,k)!=target.delta(c,b,k)) return false;
        for (float[] pose : poses) for (int k=0;k<3;k++) if (pose[a*3+k]!=pose[b*3+k]) return false;
        return true;
    }
}
