package io.github.libfdx.maps.streaming;

import io.github.libfdx.assets.*;
import io.github.libfdx.collections.LongMap;
import io.github.libfdx.core.*;
import io.github.libfdx.maps.*;

/**
 * Application-owned fixed-grid chunk residency using a borrowed AssetManager and resolver.
 * Owns a scope/leases and its resident layer; release this object before the manager. Confined to the
 * manager's application thread. Call manager.update with its own CPU/time budget, then update this object.
 * Published chunk assets are borrowed, and may be shared with other scopes. Do not edit shared source chunks.
 * The resident layer is exposed for rendering; do not structurally mutate it while this owner is active.
 * Source archives and external owners are outside the residency limit. No drawing or GPU allocation occurs here.
 */
public final class ChunkResidency implements Disposable {
    private static final long MIN=Integer.MIN_VALUE,MAX=(long)Integer.MAX_VALUE+1;
    private final AssetScope scope;
    private final TileChunkResolver resolver;
    private final ChunkedTileLayer layer;
    private final int chunkWidth,chunkHeight,capacity,maxPending;
    private final Slot[] slots;
    private final int[] free;
    private final LongMap<Slot> requests=new LongMap<>(0);
    private int freeCount,pending,failed,resident;
    private long left,bottom,right,top,visibleLeft,visibleBottom,visibleRight,visibleTop;
    private boolean window,disposed,updating;

    public ChunkResidency(AssetManager assets,TileChunkResolver resolver,int chunkWidth,int chunkHeight,int capacity,int maxPending) {
        long cells=(long)chunkWidth*chunkHeight;
        if(assets==null||resolver==null||chunkWidth<=0||chunkHeight<=0||cells>Integer.MAX_VALUE||capacity<1
                ||maxPending<1||maxPending>capacity||cells>Long.MAX_VALUE/5/capacity) {
            throw new FdxException("Invalid chunk residency dimensions, capacity or pending budget");
        }
        this.resolver=resolver;this.chunkWidth=chunkWidth;this.chunkHeight=chunkHeight;this.capacity=capacity;this.maxPending=maxPending;
        layer=new ChunkedTileLayer(capacity,cells*capacity);
        slots=new Slot[capacity];free=new int[capacity];freeCount=capacity;
        for(int i=0;i<capacity;i++){slots[i]=new Slot(i);free[i]=capacity-1-i;}
        scope=assets.createScope();
    }

    /** Sets a half-open visible rectangle in tile coordinates plus a whole-chunk prefetch margin.
     * The full wanted window must fit capacity and the signed tile domain; invalid changes preserve the old window.
     * Changing the window queues eviction; update controls how much eviction/request/publication work occurs.
     * Chunks within the visible rectangle are requested before prefetch-only chunks. */
    public void window(int minX,int minY,long maxX,long maxY,int prefetchChunks) {
        ensureMutable();
        if(maxX<=minX||maxY<=minY||maxX>MAX||maxY>MAX||prefetchChunks<0)throw new FdxException("Invalid visible tile rectangle or prefetch margin");
        long vl=Math.floorDiv((long)minX,chunkWidth),vb=Math.floorDiv((long)minY,chunkHeight);
        long vr=Math.floorDiv(maxX-1,chunkWidth)+1,vt=Math.floorDiv(maxY-1,chunkHeight)+1;
        long l=vl-prefetchChunks,b=vb-prefetchChunks,r=vr+prefetchChunks,t=vt+prefetchChunks;
        if(r-l>capacity||t-b>capacity||(r-l)*(t-b)>capacity)throw new FdxException("Visible/prefetch window exceeds chunk capacity "+capacity);
        if(l*chunkWidth<MIN||b*chunkHeight<MIN||r*chunkWidth>MAX||t*chunkHeight>MAX)throw new FdxException("Whole chunks exceed signed tile domain");
        visibleLeft=vl;visibleBottom=vb;visibleRight=vr;visibleTop=vt;
        left=l;bottom=b;right=r;top=t;window=true;
    }
    /** Performs at most the separate limits of starts, publications (success/failure) and evictions.
     * Zero limits skip that work. Does not drive the asset manager or block for a pending load.
     * Returns completed work units. Load/validation failures are retained until retryFailed or eviction. */
    public int update(int maxStarts,int maxPublications,int maxEvictions) {
        ensureMutable();
        if(maxStarts<0||maxPublications<0||maxEvictions<0)throw new FdxException("Chunk update budgets cannot be negative");
        if(!window)return 0;
        updating=true;
        try {return updateInternal(maxStarts,maxPublications,maxEvictions);}
        finally {updating=false;}
    }
    private int updateInternal(int maxStarts,int maxPublications,int maxEvictions) {
        int work=0;
        for(Slot slot:slots) {
            if(slot.active&&!wanted(slot.x,slot.y)&&maxEvictions>0) {
                release(slot);maxEvictions--;work++;
            }
        }
        for(Slot slot:slots) {
            if(!slot.active||!slot.pending||!wanted(slot.x,slot.y)||maxPublications==0)continue;
            AssetStatus status=slot.lease.status();
            if(status==AssetStatus.LOADED) {
                try {
                    TileChunk chunk=slot.lease.asset();
                    if(chunk==null||chunk.x()!=(long)slot.x*chunkWidth||chunk.y()!=(long)slot.y*chunkHeight
                            ||chunk.width()!=chunkWidth||chunk.height()!=chunkHeight) {
                        throw new FdxException("Loaded chunk does not match grid request "+slot.x+","+slot.y);
                    }
                    layer.put(chunk);slot.chunk=chunk;slot.pending=false;pending--;resident++;
                } catch(RuntimeException failure){fail(slot,failure);}
                maxPublications--;work++;
            } else if(status==AssetStatus.FAILED||status==AssetStatus.UNLOADED) {
                RuntimeException failure;
                try {slot.lease.future().get();failure=new FdxException("Chunk lease ended without a usable result");}
                catch(RuntimeException cause){failure=cause;}
                fail(slot,failure);maxPublications--;work++;
            }
        }
        for(int phase=0;phase<2&&maxStarts>0&&freeCount>0&&pending<maxPending;phase++) {
            for(long y=bottom;y<top&&maxStarts>0&&freeCount>0&&pending<maxPending;y++) {
                for(long x=left;x<right&&maxStarts>0&&freeCount>0&&pending<maxPending;x++) {
                    boolean visible=x>=visibleLeft&&x<visibleRight&&y>=visibleBottom&&y<visibleTop;
                    if(visible!=(phase==0)||requests.containsKey(key((int)x,(int)y)))continue;
                    Slot slot=slots[free[--freeCount]];
                    slot.active=true;slot.x=(int)x;slot.y=(int)y;
                    requests.put(key(slot.x,slot.y),slot);
                    try {
                        AssetDescriptor<TileChunk> descriptor=resolver.resolve(slot.x,slot.y);
                        if(descriptor==null)throw new FdxException("Chunk resolver returned null for "+slot.x+","+slot.y);
                        slot.lease=scope.load(descriptor);slot.pending=true;pending++;
                    } catch(RuntimeException failure){fail(slot,failure);}
                    maxStarts--;work++;
                }
            }
        }
        return work;
    }
    /** Clears failed requests so later budgeted updates can retry them. Successful/pending requests are untouched. */
    public int retryFailed() {
        ensureMutable();int count=0;
        for(Slot slot:slots)if(slot.active&&slot.failure!=null){release(slot);count++;}
        return count;
    }
    /** Borrowed resident layer. Attach it to TileMap.infinite for drawing with an explicit visible rectangle. */
    public ChunkedTileLayer layer(){ensureOpen();return layer;}
    public int capacity(){return capacity;}
    /** Queued/loading/completed-but-unpublished leases; bounded by maxPending. */
    public int pendingChunks(){return pending;}
    public int failedChunks(){return failed;}
    public int residentChunks(){return resident;}
    public int requestedChunks(){return requests.size();}
    /** Retained cause for this chunk index, or null when it has no recorded failure. */
    public Throwable failure(int chunkX,int chunkY) {
        Slot slot=requests.get(key(chunkX,chunkY));return slot==null?null:slot.failure;
    }
    /** Whether all visible chunks are resident; prefetch completion is not required. */
    public boolean visibleReady() {
        ensureOpen();if(!window)return false;
        for(long y=visibleBottom;y<visibleTop;y++)for(long x=visibleLeft;x<visibleRight;x++) {
            Slot slot=requests.get(key((int)x,(int)y));if(slot==null||slot.chunk==null)return false;
        }
        return true;
    }
    /** Potential chunk payload in the resident plus pending slots, excluding compressed/source data, index and external owners. */
    public long maxCellBytes(){return (long)capacity*chunkWidth*chunkHeight*5;}
    private boolean wanted(int x,int y){return x>=left&&x<right&&y>=bottom&&y<top;}
    private void fail(Slot slot,RuntimeException failure) {
        if(slot.pending){pending--;slot.pending=false;}
        if(slot.failure==null)failed++;
        slot.failure=failure;
        AssetLease<TileChunk> lease=slot.lease;slot.lease=null;
        try {if(lease!=null)lease.dispose();}
        catch(RuntimeException cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
    }
    private void release(Slot slot) {
        if(slot.chunk!=null){layer.remove(slot.chunk.x(),slot.chunk.y());resident--;}
        if(slot.pending)pending--;
        if(slot.failure!=null)failed--;
        AssetLease<TileChunk> lease=slot.lease;
        requests.remove(key(slot.x,slot.y));slot.clear();free[freeCount++]=slot.index;
        if(lease!=null)lease.dispose();
    }
    private void ensureOpen(){if(disposed||scope.isDisposed())throw new FdxException("ChunkResidency or its asset scope is disposed");}
    private void ensureMutable(){ensureOpen();if(updating)throw new FdxException("ChunkResidency cannot be mutated during update callbacks");}
    private static long key(int x,int y){return ((long)x<<32)|(y&0xffffffffL);}
    @Override
    public boolean isDisposed(){return disposed;}
    @Override
    public void dispose() {
        if(disposed)return;
        if(updating)throw new FdxException("ChunkResidency cannot be disposed during update callbacks");
        disposed=true;
        layer.clear();requests.clear();pending=failed=resident=0;
        try {scope.dispose();}
        finally {for(Slot slot:slots)slot.clear();}
    }
    private static final class Slot {
        final int index;int x,y;boolean active,pending;TileChunk chunk;AssetLease<TileChunk> lease;Throwable failure;
        Slot(int index){this.index=index;}
        void clear(){active=pending=false;chunk=null;lease=null;failure=null;}
    }
}
