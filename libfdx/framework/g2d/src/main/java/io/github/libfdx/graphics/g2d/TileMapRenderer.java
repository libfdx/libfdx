package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.maps.MapLayer;
import io.github.libfdx.maps.GroupLayer;
import io.github.libfdx.maps.ImageLayer;
import io.github.libfdx.maps.MapObject;
import io.github.libfdx.maps.ObjectLayer;
import io.github.libfdx.maps.ChunkedTileLayer;
import io.github.libfdx.maps.TileChunk;
import io.github.libfdx.maps.TileChunkQuery;

/**
 * Draws canonical maps through a caller-owned, active batch. Coordinates and the
 * visible rectangle use map units (pixels for imported maps). Layer order, cell
 * order, visibility, offsets, opacity, and tile-object order are preserved.
 * Shape objects remain gameplay metadata. Tint/opacity multiply through groups;
 * changed color is restored to white at exit. Untinted opaque legacy maps retain
 * the caller's batch color. Repeating images use the visible rectangle, or the
 * finite map bounds for the overload without a rectangle. Culling does not clip
 * partial quads; the caller sets a scissor when clipping is required.
 * Tile UVs span the centers of their outermost texels, preventing neighboring
 * atlas tiles from bleeding into nearest/linear samples at fractional camera
 * positions. This applies to dense/chunked cells, tile objects and animation
 * frames, including flipped tiles. Source pixel dimensions and geometry are
 * unchanged; TileSet retains the original regions and caches sampling views at
 * binding time. Custom batches must honor the supplied region UVs.
 * The renderer borrows all inputs and allocates no per-cell/per-frame storage.
 */
public final class TileMapRenderer {
    private float opacity = 1;
    private float red=1,green=1,blue=1;
    private int imageRepeatLimit=65536;
    private boolean hasCamera;
    private float cameraX, cameraY;
    private TileChunkQuery chunkQuery;
    private int[] chunkHeap;
    private long[] chunkRows;
    private long chunkCellLimit=1_048_576;
    public TileMapRenderer() { }

    /** Allocates reusable sparse-query storage and sets a maximum intersecting-cell budget per layer.
     * Limits fail before that layer draws. Defaults initialize lazily to 4096 chunks and 1048576 cells. */
    public TileMapRenderer chunkLimits(int visibleChunks,long visitedCells) {
        if(visibleChunks<1||visitedCells<1)throw new FdxException("Chunk rendering limits must be positive");
        chunkQuery=new TileChunkQuery(visibleChunks);chunkHeap=new int[visibleChunks];chunkRows=new long[visibleChunks];
        chunkCellLimit=visitedCells;return this;
    }

    /** Maximum repeated copies per image layer per render; excess fails before that layer draws. */
    public TileMapRenderer imageRepeatLimit(int maximum) {
        if(maximum<1) throw new FdxException("Image repeat limit must be positive");
        imageRepeatLimit=maximum; return this;
    }

    /** Overrides the parallax reference with the camera center in world coordinates. */
    public TileMapRenderer camera(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) { throw new FdxException("Camera center must be finite"); }
        cameraX = x; cameraY = y; hasCamera = true; return this;
    }
    /** Uses the visible rectangle center, or the map parallax origin when rendering without culling. */
    public TileMapRenderer clearCamera() { hasCamera = false; return this; }

    /** Compatibility overload retaining the original declaration. */
    public int render(TileMap map, TileSet tiles, Batch2D batch, float x, float y) {
        return render((io.github.libfdx.maps.TileMap)map, tiles, batch, x, y);
    }
    /** Compatibility overload retaining the original declaration. */
    public int render(TileMap map, TileSet tiles, Batch2D batch, float x, float y,
            float visibleX, float visibleY, float visibleWidth, float visibleHeight) {
        return render((io.github.libfdx.maps.TileMap)map, tiles, batch, x, y, visibleX, visibleY, visibleWidth, visibleHeight);
    }
    public int render(io.github.libfdx.maps.TileMap map, TileSet tiles, Batch2D batch, float x, float y) {
        validate(map, tiles, batch, x, y);
        if(map.isInfinite())throw new FdxException("Infinite map rendering requires an explicit visible rectangle");
        return draw(map, tiles, batch, x, y, false, 0, 0, 0, 0);
    }
    public int render(io.github.libfdx.maps.TileMap map, TileSet tiles, Batch2D batch, float x, float y,
            float visibleX, float visibleY, float visibleWidth, float visibleHeight) {
        validate(map, tiles, batch, x, y);
        if (!Float.isFinite(visibleX) || !Float.isFinite(visibleY)
                || !Float.isFinite(visibleWidth) || !Float.isFinite(visibleHeight)) {
            throw new FdxException("Visible rectangle must be finite");
        }
        if (visibleWidth <= 0 || visibleHeight <= 0) { return 0; }
        if(!Float.isFinite(visibleX+visibleWidth) || !Float.isFinite(visibleY+visibleHeight)) throw new FdxException("Visible rectangle overflow");
        return draw(map, tiles, batch, x, y, true, visibleX, visibleY, visibleX + visibleWidth, visibleY + visibleHeight);
    }
    private int draw(io.github.libfdx.maps.TileMap map, TileSet tiles, Batch2D batch, float x, float y,
            boolean cull, float left, float bottom, float right, float top) {
        int drawn = 0;
        opacity = red = green = blue = 1;
        double referenceX = hasCamera ? (double)cameraX - x - map.parallaxOriginX()
                : cull ? left + (double)(right - left) * .5 - x - map.parallaxOriginX() : 0;
        double referenceY = hasCamera ? (double)cameraY - y - map.parallaxOriginY()
                : cull ? bottom + (double)(top - bottom) * .5 - y - map.parallaxOriginY() : 0;
        if(!cull) { left=x; bottom=y; right=x+map.worldWidth(); top=y+map.worldHeight(); }
        try {
            for (int i = 0; i < map.mapLayerCount(); i++) {
                drawn += drawLayer(map.mapLayer(i), map, tiles, batch, x, y, 1, 1, 1, 1, 1, 1,
                        referenceX, referenceY, cull, left, bottom, right, top);
            }
        } finally { color(batch,1,1,1,1); }
        return drawn;
    }
    private int drawLayer(MapLayer layer, io.github.libfdx.maps.TileMap map, TileSet tiles, Batch2D batch,
            double x, double y, float parentAlpha, float parentRed, float parentGreen, float parentBlue,
            float parentParallaxX, float parentParallaxY,
            double referenceX, double referenceY, boolean cull, float left, float bottom, float right, float top) {
        if (!layer.isVisible() || layer.opacity() == 0) { return 0; }
        int drawn = 0;
        int tint=layer.tintRgba();
        float alpha = parentAlpha * layer.opacity() * ((tint&255)/255f);
        if(alpha==0) return 0;
        float r=parentRed*((tint>>>24)/255f),g=parentGreen*(((tint>>>16)&255)/255f),b=parentBlue*(((tint>>>8)&255)/255f);
        float parallaxX = parentParallaxX * layer.parallaxX(), parallaxY = parentParallaxY * layer.parallaxY();
        double originX = x + layer.offsetX(), originY = y + layer.offsetY();
        if (layer instanceof GroupLayer group) {
            for (int i = 0; i < group.layerCount(); i++) {
                drawn += drawLayer(group.layer(i), map, tiles, batch, originX, originY, alpha, r, g, b, parallaxX, parallaxY,
                        referenceX, referenceY, cull, left, bottom, right, top);
            }
            return drawn;
        }
        originX += referenceX * (1 - parallaxX); originY += referenceY * (1 - parallaxY);
        if (!Double.isFinite(originX) || !Double.isFinite(originY)) { throw new FdxException("Layer transform overflow"); }
        if (layer instanceof io.github.libfdx.maps.TileLayer grid) {
            float tw = map.tileWidth(), th = map.tileHeight();
            int startX = 0, endX = grid.width(), startY = 0, endY = grid.height();
            if (cull) {
                startX = clamp((int)(Math.floor(((double)left - originX - Math.max(tw, tiles.maxRight)) / tw) + 1), grid.width());
                startY = clamp((int)(Math.floor(((double)bottom - originY - Math.max(th, tiles.maxTop)) / th) + 1), grid.height());
                endX = clamp((int)Math.ceil(((double)right - originX - tiles.minX) / tw), grid.width());
                endY = clamp((int)Math.ceil(((double)top - originY - tiles.minY) / th), grid.height());
            }
            color(batch,r,g,b,alpha);
            for (int row = startY; row < endY; row++) {
                int tileY = map.reverseY() ? endY - 1 - (row - startY) : row;
                for (int column = startX; column < endX; column++) {
                    int tileX = map.reverseX() ? endX - 1 - (column - startX) : column;
                    int id = grid.tile(tileX, tileY);
                    if (id == 0) { continue; }
                    TileSet.Entry entry = tiles.entry(id);
                    if (entry == null) { continue; }
                    float dx = (float)(originX + tileX * (double)tw + entry.offsetX);
                    float dy = (float)(originY + tileY * (double)th + entry.offsetY);
                    float width = entry.nativeSize ? entry.region.width() : tw;
                    float height = entry.nativeSize ? entry.region.height() : th;
                    if (cull && (dx >= right || dy >= top || dx + width <= left || dy + height <= bottom)) { continue; }
                    int transform = grid.transform(tileX, tileY);
                    if (transform == 0) { batch.draw(entry.samplingRegion, dx, dy, width, height); }
                    else { batch.draw(entry.samplingRegion, dx, dy, width, height, transform); }
                    drawn++;
                }
            }
        } else if (layer instanceof ChunkedTileLayer chunks) {
            if(!cull)throw new FdxException("Chunked layers require an explicit visible rectangle");
            color(batch,r,g,b,alpha);
            drawn+=drawChunks(chunks,map,tiles,batch,originX,originY,left,bottom,right,top);
        } else if (layer instanceof ObjectLayer objects) {
            for (int j = 0; j < objects.objectCount(); j++) {
                MapObject object = objects.drawObject(j);
                if (!object.isVisible() || object.opacity() == 0 || object.shape() != MapObject.Shape.TILE) { continue; }
                TileSet.Entry entry = tiles.entry(object.tileId());
                if (entry == null) { continue; }
                float offsetX = entry.offsetX * object.width() / entry.region.width();
                float offsetY = entry.offsetY * object.height() / entry.region.height();
                if (cull) {
                    double radians = Math.toRadians(object.rotationDegrees());
                    float cos = (float)Math.cos(radians), sin = (float)Math.sin(radians);
                    double shiftX = offsetX * cos - offsetY * sin + originX;
                    double shiftY = offsetX * sin + offsetY * cos + originY;
                    float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
                    float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
                    for (int k = 0; k < 4; k++) {
                        float px = (float)(object.worldX(k) + shiftX), py = (float)(object.worldY(k) + shiftY);
                        minX = Math.min(minX, px); maxX = Math.max(maxX, px);
                        minY = Math.min(minY, py); maxY = Math.max(maxY, py);
                    }
                    if (minX >= right || minY >= top || maxX <= left || maxY <= bottom) { continue; }
                }
                color(batch,r,g,b,alpha * object.opacity());
                batch.draw(entry.samplingRegion, (float)(originX + object.x() + offsetX), (float)(originY + object.y() + offsetY),
                        object.width(), object.height(), -offsetX, -offsetY, object.rotationDegrees(), object.transform());
                drawn++;
            }
        } else if(layer instanceof ImageLayer image) {
            color(batch,r,g,b,alpha);
            drawn+=drawImage(image,tiles,batch,originX,originY,cull,left,bottom,right,top);
        }
        return drawn;
    }
    private int drawChunks(ChunkedTileLayer layer,io.github.libfdx.maps.TileMap map,TileSet tiles,Batch2D batch,
            double originX,double originY,float left,float bottom,float right,float top) {
        if(chunkQuery==null)chunkLimits(4096,chunkCellLimit);
        double tw=map.tileWidth(),th=map.tileHeight();
        long l=tileBound(Math.floor(((double)left-originX-Math.max(tw,tiles.maxRight))/tw)+1);
        long b=tileBound(Math.floor(((double)bottom-originY-Math.max(th,tiles.maxTop))/th)+1);
        long r=tileBound(Math.ceil(((double)right-originX-tiles.minX)/tw));
        long t=tileBound(Math.ceil(((double)top-originY-tiles.minY)/th));
        try {
            layer.query(l,b,r,t,chunkQuery);
            long visited=0;int heapSize=chunkQuery.size(),drawn=0;
            for(int i=0;i<heapSize;i++) {
                TileChunk chunk=chunkQuery.get(i);
                long width=Math.min(r,chunk.right())-Math.max(l,chunk.x());
                long height=Math.min(t,chunk.top())-Math.max(b,chunk.y());
                visited+=width*height;
                if(visited>chunkCellLimit)throw new FdxException("Visible chunk cells exceed limit "+chunkCellLimit);
                chunkHeap[i]=i;
                chunkRows[i]=map.reverseY()?Math.min(t,chunk.top())-1:Math.max(b,chunk.y());
            }
            for(int i=heapSize/2-1;i>=0;i--)siftChunk(i,heapSize,map);
            // Merge rows globally. Drawing whole adjacent chunks would break painter order for overhanging/alpha tiles.
            while(heapSize>0) {
                int index=chunkHeap[0];TileChunk chunk=chunkQuery.get(index);long row=chunkRows[index];
                long startX=Math.max(l,chunk.x()),endX=Math.min(r,chunk.right());
                int localY=(int)(row-chunk.y());
                for(long cursor=startX;cursor<endX;cursor++) {
                    long column=map.reverseX()?endX-1-(cursor-startX):cursor;
                    int localX=(int)(column-chunk.x()),id=chunk.tile(localX,localY);
                    if(id==0)continue;
                    TileSet.Entry entry=tiles.entry(id);if(entry==null)continue;
                    // Subtract the application origin before narrowing, retaining nearby cells at large coordinates.
                    float dx=(float)((double)originX+column*tw+entry.offsetX),dy=(float)((double)originY+row*th+entry.offsetY);
                    float width=entry.nativeSize?entry.region.width():(float)tw,height=entry.nativeSize?entry.region.height():(float)th;
                    if(dx>=right||dy>=top||dx+width<=left||dy+height<=bottom)continue;
                    int transform=chunk.transform(localX,localY);
                    if(transform==0)batch.draw(entry.samplingRegion,dx,dy,width,height);
                    else batch.draw(entry.samplingRegion,dx,dy,width,height,transform);
                    drawn++;
                }
                chunkRows[index]+=map.reverseY()?-1:1;
                if(chunkRows[index]<Math.max(b,chunk.y())||chunkRows[index]>=Math.min(t,chunk.top())) {
                    chunkHeap[0]=chunkHeap[--heapSize];
                }
                if(heapSize>0)siftChunk(0,heapSize,map);
            }
            return drawn;
        } finally {chunkQuery.clear();}
    }
    private void siftChunk(int parent,int size,io.github.libfdx.maps.TileMap map) {
        int value=chunkHeap[parent];
        while(parent<size/2) {
            int child=parent*2+1;
            if(child+1<size&&beforeChunk(chunkHeap[child+1],chunkHeap[child],map))child++;
            if(!beforeChunk(chunkHeap[child],value,map))break;
            chunkHeap[parent]=chunkHeap[child];parent=child;
        }
        chunkHeap[parent]=value;
    }
    private boolean beforeChunk(int a,int b,io.github.libfdx.maps.TileMap map) {
        int y=Long.compare(chunkRows[a],chunkRows[b]);
        if(y!=0)return map.reverseY()?y>0:y<0;
        int x=Integer.compare(chunkQuery.get(a).x(),chunkQuery.get(b).x());
        return map.reverseX()?x>0:x<0;
    }
    private static long tileBound(double value){return (long)Math.max(Integer.MIN_VALUE,Math.min((long)Integer.MAX_VALUE+1,value));}

    private int drawImage(ImageLayer layer,TileSet tiles,Batch2D batch,double originX,double originY,
            boolean cull,float left,float bottom,float right,float top) {
        TextureRegion image=tiles.findImage(layer.imagePath());
        if(image==null) return 0;
        int width=image.width(),height=image.height();
        double x=(double)originX+layer.x(),y=(double)originY+layer.y()-height;
        boolean repeat=layer.repeatX() || layer.repeatY();
        if((cull || repeat) && ((!layer.repeatX() && (x>=right || x+width<=left))
                || (!layer.repeatY() && (y>=top || y+height<=bottom)))) return 0;
        double startX=layer.repeatX() ? Math.floor((left-x)/width) : 0;
        double startY=layer.repeatY() ? Math.floor((bottom-y)/height) : 0;
        double columns=layer.repeatX() ? Math.ceil((right-x)/width)-startX : 1;
        double rows=layer.repeatY() ? Math.ceil((top-y)/height)-startY : 1;
        if(columns==0 || rows==0) return 0;
        if(!Double.isFinite(columns) || !Double.isFinite(rows) || columns<0 || rows<0 || columns*rows>imageRepeatLimit) {
            throw new FdxException("Image layer "+layer.id()+" exceeds repeat limit "+imageRepeatLimit);
        }
        x+=startX*width; y+=startY*height;
        if(!Float.isFinite((float)x) || !Float.isFinite((float)y)) throw new FdxException("Image layer transform overflow");
        int count=0;
        for(int row=0;row<(int)rows;row++) for(int column=0;column<(int)columns;column++) {
            batch.draw(image,(float)(x+(double)column*width),(float)(y+(double)row*height),width,height); count++;
        }
        return count;
    }
    private void color(Batch2D batch,float r,float g,float b,float a) {
        if(red!=r || green!=g || blue!=b || opacity!=a) {
            batch.color(r,g,b,a); red=r; green=g; blue=b; opacity=a;
        }
    }
    private static void validate(io.github.libfdx.maps.TileMap map, TileSet tiles, Batch2D batch, float x, float y) {
        if (map == null || tiles == null || batch == null) { throw new FdxException("Map, tile bindings, and batch cannot be null"); }
        if (!Float.isFinite(x) || !Float.isFinite(y)) { throw new FdxException("Map position must be finite"); }
    }
    private static int clamp(int value, int maximum) { return Math.max(0, Math.min(maximum, value)); }
}
