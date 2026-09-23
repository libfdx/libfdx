package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

final class ChunkedTileLayerTest {
    @Test
    void hugeSignedGapsUseOnlyResidentCellsAndQueriesStayLocal() {
        var layer=new ChunkedTileLayer(1024,10000);
        for(int i=0;i<1000;i++)layer.put(new TileChunk(-1_000_000_000+i*1_000_000,-70,2,2).fill(1));
        var edge=new TileChunk(Integer.MAX_VALUE,Integer.MIN_VALUE,1,1).fill(7);layer.put(edge);
        assertEquals(4001,layer.residentCells());assertEquals(20005,layer.estimatedCellBytes());
        assertEquals(7,layer.tile(Integer.MAX_VALUE,Integer.MIN_VALUE));
        assertEquals(0,layer.tile(0,0));
        var query=new TileChunkQuery(8);
        layer.query(-1_000_000_001,-71,-999_999_997,-67,query);
        assertEquals(1,query.size());assertTrue(layer.lastQueryCandidates()<4);
        layer.query(Integer.MAX_VALUE,Integer.MIN_VALUE,(long)Integer.MAX_VALUE+1,(long)Integer.MIN_VALUE+1,query);
        assertSame(edge,query.get(0));
        assertThrows(FdxException.class,()->new TileChunk(Integer.MAX_VALUE,0,2,1));
        query.clear();assertEquals(0,query.size());
    }
    @Test
    void randomRectangleQueriesMatchBruteForceAndDeduplicateBucketCrossings() {
        var layer=new ChunkedTileLayer(400,5_000_000);
        Random random=new Random(72591);
        for(int y=-8;y<8;y++)for(int x=-12;x<12;x++) {
            layer.put(new TileChunk(x*200,y*200,1+random.nextInt(130),1+random.nextInt(130)));
        }
        var query=new TileChunkQuery(400);
        for(int attempt=0;attempt<500;attempt++) {
            int l=random.nextInt(7000)-3500,b=random.nextInt(5000)-2500;
            long r=(long)l+random.nextInt(2000)+1,t=(long)b+random.nextInt(2000)+1;
            layer.query(l,b,r,t,query);
            int expected=0;
            for(int i=0;i<layer.chunkCount();i++) {
                TileChunk c=layer.chunk(i);
                if(c.x()<r&&c.y()<t&&c.right()>l&&c.top()>b) {
                    expected++;int found=0;
                    for(int j=0;j<query.size();j++)if(query.get(j)==c)found++;
                    assertEquals(1,found);
                }
            }
            assertEquals(expected,query.size());
            for(int i=1;i<query.size();i++) {
                TileChunk a=query.get(i-1),c=query.get(i);
                assertTrue(a.x()<c.x()||a.x()==c.x()&&a.y()<c.y());
            }
        }
        layer.query(Long.MIN_VALUE,Long.MIN_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,query);
        assertEquals(384,query.size());assertEquals(384,layer.lastQueryCandidates());
        var tooSmall=new TileChunkQuery(1);
        assertThrows(FdxException.class,()->layer.query(Long.MIN_VALUE,Long.MIN_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,tooSmall));
        assertEquals(0,tooSmall.size());
    }
    @Test
    void editsOnlyChangeTheirChunkAndValidationPreservesPriorMembership() {
        var layer=new ChunkedTileLayer(2,8);
        var a=new TileChunk(-2,-2,2,2).fill(1);var b=new TileChunk(0,-2,2,2).fill(2);
        layer.put(a);layer.put(b);long structure=layer.revision(),original=a.revision();
        a.tile(0,0,1);assertEquals(original,a.revision());
        layer.tile(-2,-2,1,7);assertEquals(original+1,a.revision());assertEquals(1,b.revision());
        assertEquals(structure,layer.revision());
        layer.tile(-2,-2,0,7);assertEquals(0,layer.transform(-2,-2));
        assertThrows(FdxException.class,()->layer.tile(80,80,1,0));
        assertThrows(FdxException.class,()->layer.put(new TileChunk(-1,-2,2,2)));
        assertThrows(FdxException.class,()->layer.put(new TileChunk(-2,-2,3,3)));
        assertSame(a,layer.findChunk(-2,-2));assertSame(b,layer.findChunk(0,-2));assertEquals(structure,layer.revision());
        var replacement=new TileChunk(-2,-2,2,2).fill(3);
        assertSame(a,layer.put(replacement));assertSame(b,layer.findChunk(0,-2));
        assertSame(b,layer.remove(0,-2));assertEquals(4,layer.residentCells());assertNull(layer.remove(0,-2));
        layer.clear();assertEquals(0,layer.chunkCount());assertEquals(0,layer.indexBucketCount());assertEquals(0,layer.residentCells());
    }
    @Test
    void infiniteExtentIsExplicitAndFiniteMembershipRulesRemainIntact() {
        TileMap map=TileMap.infinite(16,24);
        assertTrue(map.isInfinite());assertEquals(0,map.worldWidth());assertEquals(0,map.worldHeight());
        assertThrows(FdxException.class,map::addLayer);
        assertThrows(FdxException.class,()->map.addLayer(new TileLayer(1,1)));
        var chunks=new ChunkedTileLayer(1,4);map.addGroupLayer(new GroupLayer(chunks));
        assertEquals(1,map.mapLayerCount());assertEquals(0,map.layerCount());
        assertThrows(FdxException.class,()->new TileMap(1,1,16,16).addChunkedLayer(chunks));
        assertThrows(FdxException.class,()->new TileMap(1,1,16,16).addGroupLayer(new GroupLayer(chunks)));
        assertThrows(FdxException.class,()->new TileMap(0,0,16,16));
    }
}
