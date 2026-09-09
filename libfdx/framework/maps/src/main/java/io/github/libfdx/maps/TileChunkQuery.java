package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;
import java.util.Arrays;

/** Application-owned, reusable fixed-capacity query results. Chunks are borrowed, sorted by x then y.
 * Reuse invalidates prior results; clear releases retained references. No allocation during queries.
 * Do not mutate a layer while traversing its results. Capacity overflow fails and clears the result. */
public final class TileChunkQuery {
    private final TileChunk[] values;
    private int size;
    public TileChunkQuery(int capacity) {
        if(capacity<1)throw new FdxException("Chunk query capacity must be positive");
        values=new TileChunk[capacity];
    }
    public int size(){return size;}
    public int capacity(){return values.length;}
    public TileChunk get(int index){if(index<0||index>=size)throw new IndexOutOfBoundsException(index);return values[index];}
    public void clear(){Arrays.fill(values,0,size,null);size=0;}
    void add(TileChunk chunk) {
        if(size==values.length){clear();throw new FdxException("Visible chunks exceed query capacity "+values.length);}
        values[size++]=chunk;
    }
    // In-place heap sort avoids temporary storage even when thousands of chunks are visible.
    void sort() {
        for(int i=size/2-1;i>=0;i--)sift(i,size);
        for(int end=size-1;end>0;end--){swap(0,end);sift(0,end);}
    }
    private void sift(int parent,int end) {
        while(parent<end/2) {
            int child=parent*2+1;
            if(child+1<end&&compare(values[child],values[child+1])<0)child++;
            if(compare(values[parent],values[child])>=0)return;
            swap(parent,child);parent=child;
        }
    }
    private void swap(int a,int b){TileChunk value=values[a];values[a]=values[b];values[b]=value;}
    private static int compare(TileChunk a,TileChunk b){int x=Integer.compare(a.x(),b.x());return x!=0?x:Integer.compare(a.y(),b.y());}
}
