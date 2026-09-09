package io.github.libfdx.maps;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.LongMap;
import io.github.libfdx.core.FdxException;

/**
 * Mutable, bounded resident chunks at signed tile coordinates. Uses a spatial hash; empty coordinate gaps allocate nothing.
 * Chunks may have different sizes but cannot overlap. The layer retains borrowed chunk references until removal/clear.
 * Single-thread confined. Coordinate queries and cell lookups allocate nothing; structural changes may allocate.
 * Resident limits cover this layer, not references held by an application, query result or source document.
 */
public final class ChunkedTileLayer extends MapLayer {
    private static final int BUCKET_SIZE=64;
    private static final long MIN=Integer.MIN_VALUE, MAX=(long)Integer.MAX_VALUE+1;
    private final int maxChunks;
    private final long maxCells;
    private final Array<Entry> entries=new Array<>(0);
    private final LongMap<Entry> origins=new LongMap<>(0);
    private final LongMap<Array<Entry>> buckets=new LongMap<>(0);
    private long residentCells,revision,queryStamp;
    private int lastQueryCandidates;

    public ChunkedTileLayer(int maxChunks,long maxCells) {
        if(maxChunks<1||maxCells<1||maxCells>Long.MAX_VALUE/5)throw new FdxException("Resident chunk/cell limits must be positive and fit the byte estimate");
        this.maxChunks=maxChunks;this.maxCells=maxCells;
    }
    public int maxChunks(){return maxChunks;}
    public long maxCells(){return maxCells;}
    public int chunkCount(){return entries.size();}
    public long residentCells(){return residentCells;}
    /** IDs (four bytes) plus transforms (one byte), excluding Java objects, index and retained external references. */
    public long estimatedCellBytes(){return residentCells*5;}
    /** Structural revision only. Cell edits have their own TileChunk.revision and do not rebuild the spatial index. */
    public long revision(){return revision;}
    public int indexBucketCount(){return buckets.size();}
    /** Unique candidate chunks examined by the last query, before exact intersection filtering. */
    public int lastQueryCandidates(){return lastQueryCandidates;}
    /** Borrowed resident chunk; enumeration order is not a drawing order and may change after structural edits. */
    public TileChunk chunk(int index){return entries.get(index).chunk;}
    /** Returns the chunk at this exact origin, or null; not a containment lookup. */
    public TileChunk findChunk(int x,int y){Entry value=origins.get(key(x,y));return value==null?null:value.chunk;}

    /** Adds or replaces the chunk at its origin. Normal validation happens before mutation.
     * Returns the previous borrowed chunk or null. Same-instance insertion is a no-op. */
    public TileChunk put(TileChunk chunk) {
        if(chunk==null)throw new FdxException("Chunk cannot be null");
        Entry previous=origins.get(key(chunk.x(),chunk.y()));
        if(previous!=null&&previous.chunk==chunk)return chunk;
        long nextCells=residentCells+chunk.cellCount()-(previous==null?0:previous.chunk.cellCount());
        if(nextCells>maxCells||previous==null&&entries.size()==maxChunks)throw new FdxException("Resident chunk/cell budget exceeded");
        if(overlap(chunk,previous))throw new FdxException("Chunk overlaps existing cells at "+chunk.x()+","+chunk.y());
        TileChunk old=previous==null?null:previous.chunk;
        if(previous!=null)removeEntry(previous);
        Entry next=new Entry(chunk);
        entries.add(next);origins.put(key(chunk.x(),chunk.y()),next);index(next,true);
        residentCells+=chunk.cellCount();revision++;
        return old;
    }
    /** Removes and returns a chunk by exact origin, or null. No asset lease or external reference is released here. */
    public TileChunk remove(int x,int y) {
        Entry entry=origins.get(key(x,y));if(entry==null)return null;
        removeEntry(entry);revision++;return entry.chunk;
    }
    public void clear() {
        if(entries.isEmpty())return;
        entries.clear();origins.clear();buckets.clear();residentCells=0;revision++;
    }
    /** Cell ID at a world tile coordinate; holes return EMPTY_TILE. */
    public int tile(int x,int y) {
        TileChunk chunk=containing(x,y);return chunk==null?TileTransform.EMPTY_TILE:chunk.tile(x-chunk.x(),y-chunk.y());
    }
    public int transform(int x,int y) {
        TileChunk chunk=containing(x,y);return chunk==null?0:chunk.transform(x-chunk.x(),y-chunk.y());
    }
    /** Edits a resident cell; a hole fails instead of implicitly allocating a chunk. */
    public ChunkedTileLayer tile(int x,int y,int id,int transform) {
        TileChunk chunk=containing(x,y);
        if(chunk==null)throw new FdxException("Cannot edit a nonresident tile at "+x+","+y);
        chunk.tile(x-chunk.x(),y-chunk.y(),id,transform);return this;
    }

    /** Intersects a half-open tile rectangle, clipped to the signed coordinate domain.
     * Clears and fills the caller-owned result in x/y order. Large empty ranges scan resident chunks,
     * never every coordinate bucket. Bounds can include Integer.MAX_VALUE+1 without overflow. */
    public void query(long left,long bottom,long right,long top,TileChunkQuery result) {
        if(result==null)throw new FdxException("Chunk query result cannot be null");
        result.clear();lastQueryCandidates=0;
        left=Math.max(MIN,left);bottom=Math.max(MIN,bottom);right=Math.min(MAX,right);top=Math.min(MAX,top);
        if(left>=right||bottom>=top)return;
        if(queryStamp==Long.MAX_VALUE) {for(int i=0;i<entries.size();i++)entries.get(i).stamp=0;queryStamp=0;}
        long stamp=++queryStamp;
        if(useBuckets(left,bottom,right,top)) {
            int l=bucket(left),b=bucket(bottom),r=bucket(right-1),t=bucket(top-1);
            for(int y=b;y<=t;y++)for(int x=l;x<=r;x++) {
                Array<Entry> values=buckets.get(key(x,y));
                if(values!=null)for(int i=0;i<values.size();i++)collect(values.get(i),stamp,left,bottom,right,top,result);
            }
        } else {
            for(int i=0;i<entries.size();i++)collect(entries.get(i),stamp,left,bottom,right,top,result);
        }
        result.sort();
    }
    private void collect(Entry entry,long stamp,long l,long b,long r,long t,TileChunkQuery result) {
        if(entry.stamp==stamp)return;
        entry.stamp=stamp;lastQueryCandidates++;
        if(intersects(entry.chunk,l,b,r,t))result.add(entry.chunk);
    }
    private TileChunk containing(int x,int y) {
        Array<Entry> values=buckets.get(key(bucket(x),bucket(y)));
        if(values!=null)for(int i=0;i<values.size();i++) {
            TileChunk chunk=values.get(i).chunk;
            if(intersects(chunk,x,y,(long)x+1,(long)y+1))return chunk;
        }
        return null;
    }
    private boolean overlap(TileChunk value,Entry ignored) {
        if(useBuckets(value.x(),value.y(),value.right(),value.top())) {
            for(int y=bucket(value.y());y<=bucket(value.top()-1);y++) {
                for(int x=bucket(value.x());x<=bucket(value.right()-1);x++) {
                    Array<Entry> values=buckets.get(key(x,y));
                    if(values!=null)for(int i=0;i<values.size();i++) {
                        Entry e=values.get(i);
                        if(e!=ignored&&intersects(e.chunk,value.x(),value.y(),value.right(),value.top()))return true;
                    }
                }
            }
        } else {
            for(int i=0;i<entries.size();i++) {
                Entry e=entries.get(i);
                if(e!=ignored&&intersects(e.chunk,value.x(),value.y(),value.right(),value.top()))return true;
            }
        }
        return false;
    }
    private boolean useBuckets(long l,long b,long r,long t) {
        long area=((long)bucket(r-1)-bucket(l)+1)*((long)bucket(t-1)-bucket(b)+1);
        return area<=Math.max(1,entries.size());
    }
    private void removeEntry(Entry entry) {
        index(entry,false);origins.remove(key(entry.chunk.x(),entry.chunk.y()));entries.removeValue(entry,true);
        residentCells-=entry.chunk.cellCount();
    }
    private void index(Entry entry,boolean add) {
        TileChunk chunk=entry.chunk;
        for(int y=bucket(chunk.y());y<=bucket(chunk.top()-1);y++) {
            for(int x=bucket(chunk.x());x<=bucket(chunk.right()-1);x++) {
                long key=key(x,y);Array<Entry> values=buckets.get(key);
                if(add) {
                    if(values==null){values=new Array<>(1);buckets.put(key,values);}
                    values.add(entry);
                } else {
                    values.removeValue(entry,true);if(values.isEmpty())buckets.remove(key);
                }
            }
        }
    }
    private static boolean intersects(TileChunk c,long l,long b,long r,long t){return c.x()<r&&c.y()<t&&c.right()>l&&c.top()>b;}
    private static int bucket(long coordinate){return (int)Math.floorDiv(coordinate,BUCKET_SIZE);}
    private static long key(int x,int y){return ((long)x<<32)|(y&0xffffffffL);}
    private static final class Entry {
        final TileChunk chunk;long stamp;
        Entry(TileChunk chunk){this.chunk=chunk;}
    }
}
