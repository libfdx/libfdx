package io.github.libfdx.graphics.meshoptimizer;

/** Exact output comparison, yielding before large attribute/index arrays can monopolize a turn. */
final class LodMeshEqualityTask {
    private final MeshLodData a,b;
    private int channel,cursor;
    private boolean done,equal=true;
    LodMeshEqualityTask(MeshLodData a,MeshLodData b) {
        this.a=a;this.b=b;
        if(a==null || b==null || a.vertexCount()!=b.vertexCount() || a.indices().length!=b.indices().length) { equal=false;done=true; }
        else if (a.deformation()!=b.deformation()) { equal=false;done=true; }
    }
    boolean step(int budget) {
        while(budget-- > 0 && !done) {
            if(channel<MeshLodData.CHANNEL_COUNT) {
                float[] left=a.channel(channel),right=b.channel(channel);
                if((left==null)!=(right==null)) { equal=false;done=true; }
                else if(left==null || cursor==left.length) { channel++;cursor=0; }
                else if(Float.floatToIntBits(left[cursor])!=Float.floatToIntBits(right[cursor++])) { equal=false;done=true; }
            } else if (channel==MeshLodData.CHANNEL_COUNT) {
                if (cursor==a.indices().length) { channel++;cursor=0; }
                else if(a.indices()[cursor]!=b.indices()[cursor++]) { equal=false;done=true; }
            } else if (a.deformation()==null || cursor==a.vertexCount()) done=true;
            else if (a.sourceVertex(cursor)!=b.sourceVertex(cursor++)) { equal=false;done=true; }
        }
        return done;
    }
    boolean equal() { if(!done) throw new IllegalStateException("Comparison incomplete");return equal; }
}
