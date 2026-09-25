package io.github.libfdx.graphics.meshoptimizer;

import java.util.List;

/** Conservatively locks matching positions between primitives in the same rigid coordinate frame. */
final class LodSharedVerticesTask {
    private final MeshLodData[] meshes;
    private final List<int[]> groups;
    private final boolean[][] locks;
    private int[] owner,vertex;
    private boolean[] shared;
    private int group,part,cursor,phase,capacity;
    private boolean done;
    LodSharedVerticesTask(MeshLodData[] meshes,List<int[]> groups) {
        this.meshes=meshes;this.groups=groups;locks=new boolean[meshes.length][];
    }
    boolean step(int budget) {
        if(budget<1) throw new IllegalArgumentException("Positive work budget required");
        while(budget-- > 0 && !done) {
            if(group==groups.size()) {
                if(cursor<meshes.length) { if(locks[cursor]!=null) meshes[cursor]=meshes[cursor].withLocks(locks[cursor]);cursor++; }
                else done=true;
                continue;
            }
            int[] members=groups.get(group);
            switch(phase) {
                case 0 -> {
                    long total=0;for(int member:members) total+=meshes[member].vertexCount();
                    capacity=16;while(capacity<total*2) { if(capacity>=1<<29) throw new IllegalArgumentException("Shared mesh group exceeds capacity");capacity<<=1; }
                    owner=new int[capacity];phase++;return false;
                }
                case 1 -> { vertex=new int[capacity];phase++;return false; }
                case 2 -> { shared=new boolean[capacity];phase++;return false; }
                case 3,4 -> {
                    if(part==members.length) {
                        part=cursor=0;
                        if(phase==3) phase=4;
                        else { owner=null;vertex=null;shared=null;phase=0;group++; }
                        continue;
                    }
                    int m=members[part];MeshLodData mesh=meshes[m];
                    if(cursor==mesh.vertexCount()) { part++;cursor=0;continue; }
                    int slot=slot(m,cursor);
                    if(phase==3) {
                        if(vertex[slot]==0) { owner[slot]=m;vertex[slot]=cursor+1; }
                        else if(owner[slot]!=m) shared[slot]=true;
                    } else if(shared[slot]) {
                        if(locks[m]==null) { locks[m]=mesh.locked==null?new boolean[mesh.vertexCount()]:mesh.locked.clone();return false; }
                        locks[m][cursor]=true;
                    }
                    cursor++;
                }
                default -> throw new IllegalStateException("Unexpected boundary phase");
            }
        }
        return done;
    }
    private int slot(int mesh,int v) {
        float[] p=meshes[mesh].channel(0);int hash=0x811c9dc5;
        for(int k=0;k<3;k++) { float n=p[v*3+k];hash=(hash^(n==0?0:Float.floatToIntBits(n)))*0x01000193; }
        int slot=(hash^(hash>>>16))&(capacity-1);
        while(vertex[slot]!=0) {
            float[] q=meshes[owner[slot]].channel(0);int at=(vertex[slot]-1)*3;
            if(p[v*3]==q[at] && p[v*3+1]==q[at+1] && p[v*3+2]==q[at+2]) break;
            slot=(slot+1)&(capacity-1);
        }
        return slot;
    }
}
