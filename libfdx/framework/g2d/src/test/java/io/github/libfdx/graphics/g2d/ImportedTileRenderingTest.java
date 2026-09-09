package io.github.libfdx.graphics.g2d;

import com.sun.management.ThreadMXBean;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.maps.MapObject;
import io.github.libfdx.maps.MapProperties;
import io.github.libfdx.maps.ObjectLayer;
import io.github.libfdx.maps.GroupLayer;
import io.github.libfdx.maps.TileAnimation;
import io.github.libfdx.maps.ImageLayer;
import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class ImportedTileRenderingTest {
    @Test void sparseRowsMatchDensePainterOrderAcrossUnequalChunksAndOverhangs() {
        var dense=new io.github.libfdx.maps.TileMap(4,4,8,8);var cells=dense.addLayer();
        var sparse=io.github.libfdx.maps.TileMap.infinite(8,8);
        var layer=new io.github.libfdx.maps.ChunkedTileLayer(3,16);sparse.addChunkedLayer(layer);
        layer.put(new io.github.libfdx.maps.TileChunk(-2,-2,3,2));
        layer.put(new io.github.libfdx.maps.TileChunk(1,-2,1,4));
        layer.put(new io.github.libfdx.maps.TileChunk(-2,0,3,2));
        for(int y=0;y<4;y++)for(int x=0;x<4;x++){cells.tile(x,y,1,(x+y*4)%8);layer.tile(x-2,y-2,1,(x+y*4)%8);}
        var tiles=new TileSet().atlasRegion(1,region(23,19),-5,-4);
        var renderer=new TileMapRenderer().chunkLimits(3,16);
        assertThrows(FdxException.class,()->renderer.render(sparse,tiles,new RecordingBatch(),0,0));
        for(int order=0;order<4;order++) {
            dense.renderOrder((order&1)!=0,(order&2)!=0);sparse.renderOrder(dense.reverseX(),dense.reverseY());
            var a=new RecordingBatch();var b=new RecordingBatch();
            renderer.render(dense,tiles,a,-16,-16,-18,-18,42,40);
            renderer.render(sparse,tiles,b,0,0,-18,-18,42,40);
            assertEquals(16,a.count);assertEquals(a.count,b.count);
            for(int i=0;i<a.count;i++) {
                assertEquals(a.x[i],b.x[i]);assertEquals(a.y[i],b.y[i]);assertEquals(a.width[i],b.width[i]);
                assertEquals(a.height[i],b.height[i]);assertEquals(a.flags[i],b.flags[i]);
            }
        }
        var limited=new TileMapRenderer().chunkLimits(3,15);var batch=new RecordingBatch();
        assertThrows(FdxException.class,()->limited.render(sparse,tiles,batch,0,0,-18,-18,42,40));assertEquals(0,batch.count);
        // A huge offscreen gap cannot turn into a row-by-row scan across the coordinate span.
        layer.remove(1,-2);layer.put(new io.github.libfdx.maps.TileChunk(1_000_000_000,1_000_000_000,1,1).fill(1));
        batch.count=0;
        assertEquals(1,renderer.render(sparse,tiles,batch,-8_000_000_000f,-8_000_000_000f,-8,-8,40,40));
        assertEquals(-5,batch.x[0]);assertEquals(-4,batch.y[0]);
        layer.offset(2,3);batch.count=0;
        assertEquals(1,renderer.render(sparse,tiles,batch,-8_000_000_000f,-8_000_000_000f,-8,-8,40,40));
        assertEquals(-3,batch.x[0]);assertEquals(-1,batch.y[0]);
    }

    @Test void sparseCameraQueriesAndCellEditsReuseFrameStorage() {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean);
        var bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        var map=io.github.libfdx.maps.TileMap.infinite(16,16);
        var chunks=new io.github.libfdx.maps.ChunkedTileLayer(2,16);
        chunks.put(new io.github.libfdx.maps.TileChunk(-4,-2,4,2).fill(1));
        chunks.put(new io.github.libfdx.maps.TileChunk(0,-2,4,2).fill(1));map.addChunkedLayer(chunks);
        var tiles=new TileSet().region(1,region(16,16));var renderer=new TileMapRenderer().chunkLimits(2,16);
        var batch=new RecordingBatch();
        for(int i=0;i<12000;i++){batch.count=0;chunks.tile(-4,-2,1,i%8);renderer.render(map,tiles,batch,0,0,-66+i%4,-34,130,40);}
        long thread=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(thread);
        for(int i=0;i<2000;i++){batch.count=0;chunks.tile(-4,-2,1,i%8);renderer.render(map,tiles,batch,0,0,-66+i%4,-34,130,40);}
        long allocated=bean.getThreadAllocatedBytes(thread)-before;
        assertTrue(allocated<=512,"Sparse renderer allocated "+allocated+" bytes");
    }

    @Test void imageTintParallaxAndTopLeftAnchorComposeThroughGroups() {
        var map=new io.github.libfdx.maps.TileMap(2,2,16,16).parallaxOrigin(10,20);
        ImageLayer image=new ImageLayer("sky.png",8,4,2,20,false,false);
        image.offset(3,4).parallax(.5f,2).opacity(.5f).tint(0xff804080);
        GroupLayer group=new GroupLayer(image); group.offset(5,6).parallax(.5f,.25f).opacity(.5f).tint(0x80ff8080);
        map.addGroupLayer(group); TileSet tiles=new TileSet().imageRegion("sky.png",region(8,4));
        TileMapRenderer renderer=new TileMapRenderer().camera(150,100); RecordingBatch batch=new RecordingBatch();
        assertEquals(1,renderer.render(map,tiles,batch,100,40,140,86,8,4));
        assertEquals(140,batch.x[0]); assertEquals(86,batch.y[0]);
        assertEquals(128/255f,batch.red[0]); assertEquals(128/255f,batch.green[0]);
        assertEquals((128/255f)*(64/255f),batch.blue[0]);
        assertEquals(.25f*(128/255f)*(128/255f),batch.alpha[0]);
        assertEquals(1,batch.currentAlpha); assertEquals(1,batch.currentRed);
        batch.count=0;
        assertEquals(0,renderer.render(map,tiles,batch,100,40,148,86,1,4));
        image.tint(0xffffff00); assertEquals(0,renderer.render(map,tiles,batch,100,40));
        tiles.clear(); assertNull(tiles.findImage("sky.png"));
    }
    @Test void repeatedImagesCoverNegativeViewCoordinatesWithBoundedCopyCount() {
        var map=new io.github.libfdx.maps.TileMap(4,3,16,16);
        ImageLayer image=new ImageLayer("repeat.png",0,0,3,19,true,true); map.addImageLayer(image);
        TileSet tiles=new TileSet().imageRegion("repeat.png",region(8,4));
        TileMapRenderer renderer=new TileMapRenderer().imageRepeatLimit(10); RecordingBatch batch=new RecordingBatch();
        assertEquals(9,renderer.render(map,tiles,batch,0,0,-10,-3,20,10));
        assertEquals(-13,batch.x[0]); assertEquals(-5,batch.y[0]);
        assertEquals(3,batch.x[8]); assertEquals(3,batch.y[8]);
        batch.count=0;
        assertThrows(FdxException.class,() -> renderer.render(map,tiles,batch,0,0));
        assertEquals(0,batch.count);
        image.visible(false); assertEquals(0,renderer.render(map,tiles,batch,0,0));
        assertThrows(FdxException.class,() -> renderer.imageRepeatLimit(0));
    }
    @Test void groupedParallaxUsesWorldCameraCenterAndCullsAtShiftedBounds() {
        var map = new io.github.libfdx.maps.TileMap(1,1,16,16).parallaxOrigin(10,20);
        var layer = new io.github.libfdx.maps.TileLayer(1,1).fill(1);
        layer.offset(3,4).parallax(.5f, 2).opacity(.5f);
        GroupLayer group = new GroupLayer(layer);
        group.offset(5,6).parallax(.5f, .25f).opacity(.5f);
        map.addGroupLayer(group);
        TileSet tiles = new TileSet().region(1, region(16,16));
        TileMapRenderer renderer = new TileMapRenderer().camera(150,100);
        RecordingBatch batch = new RecordingBatch();
        // Map placed at (100,40): reference delta=(40,40), composed factors=(.25,.5).
        assertEquals(1, renderer.render(map,tiles,batch,100,40,138,70,16,16));
        assertEquals(138,batch.x[0]); assertEquals(70,batch.y[0]); assertEquals(.25,batch.alpha[0]);
        assertEquals(1,batch.currentAlpha);
        batch.count=0;
        assertEquals(0, renderer.render(map,tiles,batch,100,40,108,50,16,16));
        group.visible(false); assertEquals(0,renderer.render(map,tiles,batch,100,40));
        group.visible(true); renderer.clearCamera(); batch.count=0;
        assertEquals(1,renderer.render(map,tiles,batch,100,40));
        assertEquals(108,batch.x[0]); assertEquals(50,batch.y[0]);
        // A zero factor follows the camera in world space; negative factors reverse scrolling.
        group.parallax(0,-1); layer.parallax(1,1); renderer.camera(150,100); batch.count=0;
        renderer.render(map,tiles,batch,100,40);
        assertEquals(148,batch.x[0]); assertEquals(130,batch.y[0]);
    }

    @Test void animationSharesPhaseAndUsesBaseImagesWithoutRecursiveReferences() {
        TextureRegion a=region(16,16), b=region(16,32), replacement=region(20,40);
        TileSet tiles=new TileSet().atlasRegion(10,a,0,0).atlasRegion(11,b,0,0);
        TileAnimation sequence=new TileAnimation(new int[] {0,1},new int[] {100,200});
        tiles.animation(10,sequence,10).animation(11,sequence,10);
        assertSame(a,tiles.region(10)); assertSame(a,tiles.region(11));
        tiles.animationTime(100); assertSame(b,tiles.region(10)); assertSame(b,tiles.region(11));
        assertThrows(FdxException.class, () -> tiles.animation(10,sequence,11));
        assertSame(b,tiles.region(10));
        tiles.atlasRegion(11,replacement,-2,0); assertSame(replacement,tiles.region(10));
        var map=new io.github.libfdx.maps.TileMap(1,1,16,16); map.addLayer().fill(10);
        RecordingBatch batch=new RecordingBatch();
        assertEquals(1,new TileMapRenderer().render(map,tiles,batch,0,0,-2,35,16,4));
        assertEquals(40,batch.height[0]);
        tiles.remove(11); assertNull(tiles.region(10));
        tiles.animationTime(300); assertSame(a,tiles.region(10));
        tiles.clear(); assertEquals(0,tiles.size()); assertNull(tiles.region(10));
    }
    @Test void nativeSizedTilesRemainVisibleOutsideTheirCellsAndRespectOffsets() {
        var map = new io.github.libfdx.maps.TileMap(2, 2, 16, 16);
        var layer = map.addLayer().tile(0, 0, 1, 4);
        layer.offset(3, 5);
        TileSet tiles = new TileSet().atlasRegion(1, region(16, 40), -4, 0);
        RecordingBatch batch = new RecordingBatch();
        TileMapRenderer renderer = new TileMapRenderer();
        assertEquals(1, renderer.render(map, tiles, batch, 0, 0, -1, 36, 16, 5));
        assertEquals(-1, batch.x[0]); assertEquals(5, batch.y[0]);
        assertEquals(16, batch.width[0]); assertEquals(40, batch.height[0]); assertEquals(4, batch.flags[0]);
        batch.count = 0;
        assertEquals(0, renderer.render(map, tiles, batch, 0, 0, 15, 36, 1, 5));
    }
    @Test void renderOrderInterleavesObjectsAndLayersAndRestoresOpacity() {
        var map = new io.github.libfdx.maps.TileMap(2, 1, 16, 16).renderOrder(true, true);
        map.addLayer().fill(1).opacity(.5f);
        MapObject object = new MapObject(1, "", "", MapObject.Shape.TILE, 7, 8, 12, 24, -30, true, .5f,
                2, 5, new float[] {0,0, 0,24, 12,24, 12,0}, new MapProperties());
        ObjectLayer objects = new ObjectLayer(new MapObject[] {object}, false);
        objects.offset(2, 3); map.addObjectLayer(objects);
        map.addLayer().tile(0, 0, 2);
        TileSet tiles = new TileSet().region(1, region(16, 16)).region(2, region(16, 24));
        RecordingBatch batch = new RecordingBatch();
        assertEquals(4, new TileMapRenderer().render(map, tiles, batch, 0, 0));
        assertEquals(16, batch.x[0]); assertEquals(0, batch.x[1]); assertEquals(9, batch.x[2]);
        assertEquals(11, batch.y[2]); assertEquals(-30, batch.rotation[2]); assertEquals(5, batch.flags[2]);
        assertEquals(.5, batch.alpha[0]); assertEquals(.5, batch.alpha[2]); assertEquals(1, batch.alpha[3]);
        assertEquals(1, batch.currentAlpha);
    }
    @Test void rotatedObjectCullingUsesItsTransformedBounds() {
        var map = new io.github.libfdx.maps.TileMap(1, 1, 16, 16);
        MapObject object = new MapObject(1, "", "", MapObject.Shape.TILE, 0, 0, 10, 30, 90, true, 1,
                1, 0, new float[] {0,0, 0,30, 10,30, 10,0}, new MapProperties());
        map.addObjectLayer(new ObjectLayer(new MapObject[] {object}, false));
        TileSet tiles = new TileSet().region(1, region(10, 30));
        assertEquals(1, new TileMapRenderer().render(map, tiles, new RecordingBatch(), 0, 0, -25, 1, 5, 5));
        assertEquals(0, new TileMapRenderer().render(map, tiles, new RecordingBatch(), 0, 0, 1, 15, 5, 5));
    }
    @Test void compatibilityTypesRetainFluentReturnsAndCanonicalStorage() {
        TileMap map = new TileMap(1, 1, 16, 16);
        TileLayer layer = map.addLayer().tile(0, 0, 1).visible(true);
        assertSame(layer, ((io.github.libfdx.maps.TileMap)map).layer(0));
        assertThrows(FdxException.class, () -> map.addLayer(new io.github.libfdx.maps.TileLayer(1,1)));
        assertSame(layer, map.removeLayer(0));
    }
    @Test void warmedCulledTraversalDoesNotAllocate() {
        var platform = ManagementFactory.getThreadMXBean();
        assumeTrue(platform instanceof ThreadMXBean);
        ThreadMXBean bean = (ThreadMXBean)platform;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        var map = new io.github.libfdx.maps.TileMap(2, 2, 16, 16);
        GroupLayer group=new GroupLayer(new io.github.libfdx.maps.TileLayer(2,2).fill(1).tile(0,0,1,7),
                new ImageLayer("repeat.png",0,0,0,16,true,true));
        group.tint(0x8040ffff); map.addGroupLayer(group);
        TileSet tiles = new TileSet().region(1, region(16,16)).region(2,region(16,16))
                .animation(1,new TileAnimation(new int[] {0,1},new int[] {100,250}),1);
        tiles.imageRegion("repeat.png",region(16,16));
        TileMapRenderer renderer = new TileMapRenderer(); RecordingBatch batch = new RecordingBatch();
        // Warm the same call site that is measured, including the allocation counter.
        // Separate inline loops can enter different JIT tiers during the measurement.
        long thread = Thread.currentThread().threadId();
        for (int i = 0; i < 20; i++) {
            bean.getThreadAllocatedBytes(thread);
            renderCulledFrames(map, tiles, renderer, batch);
        }
        long before = bean.getThreadAllocatedBytes(thread);
        renderCulledFrames(map, tiles, renderer, batch);
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        assertTrue(allocated <= 512, "Allocated " + allocated + " bytes");
    }

    private static void renderCulledFrames(io.github.libfdx.maps.TileMap map, TileSet tiles,
            TileMapRenderer renderer, RecordingBatch batch) {
        for (int i = 0; i < 2000; i++) {
            batch.count = 0;
            tiles.animationTime(i * 17);
            renderer.render(map, tiles, batch, 0, 0, 0, 0, 30, 30);
        }
    }
    private static TextureRegion region(int width, int height) {
        Texture texture = (Texture)Proxy.newProxyInstance(Texture.class.getClassLoader(), new Class<?>[] {Texture.class},
                (p,m,a) -> switch (m.getName()) {
                    case "width" -> width; case "height" -> height; case "isDisposed" -> false;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        return new TextureRegion(texture);
    }
    private static final class RecordingBatch implements Batch2D {
        final float[] x = new float[16], y = new float[16], width = new float[16], height = new float[16],
                rotation = new float[16], alpha = new float[16], red=new float[16],green=new float[16],blue=new float[16];
        final int[] flags = new int[16]; int count; float currentAlpha = 1,currentRed=1,currentGreen=1,currentBlue=1;
        @Override public void begin() { }
        @Override public void begin(LoadOp op) { }
        @Override public void begin(RenderPass pass) { }
        @Override public Batch2D color(float r,float g,float b,float a) { currentAlpha = a; currentRed=r;currentGreen=g;currentBlue=b;return this; }
        @Override public Batch2D viewport(int w,int h) { return this; }
        @Override public void draw(Texture t,float x,float y,float w,float h) { }
        @Override public void draw(Texture t,float x,float y,float w,float h,float ox,float oy,float r) { }
        @Override public void draw(Texture t,int sx,int sy,int sw,int sh,float x,float y,float w,float h) { }
        @Override public void draw(TextureRegion t,float x,float y,float w,float h) { draw(t,x,y,w,h,0,0,0,0); }
        @Override public void draw(TextureRegion t,float x,float y,float w,float h,float ox,float oy,float r) { draw(t,x,y,w,h,ox,oy,r,0); }
        @Override public void draw(TextureRegion t,float x,float y,float w,float h,float ox,float oy,float r,int f) {
            this.x[count]=x; this.y[count]=y; width[count]=w; height[count]=h; rotation[count]=r; flags[count]=f;
            alpha[count]=currentAlpha; red[count]=currentRed;green[count]=currentGreen;blue[count]=currentBlue;count++;
        }
        @Override public void draw(TextureRegion t,float[] x,float[] y,int n,float w,float h,float ox,float oy,float r) { }
        @Override public void end() { }
        @Override public void dispose() { }
        @Override public boolean isDisposed() { return false; }
    }
}
