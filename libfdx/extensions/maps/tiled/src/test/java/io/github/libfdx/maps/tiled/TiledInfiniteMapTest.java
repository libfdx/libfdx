package io.github.libfdx.maps.tiled;

import io.github.libfdx.collections.Array;
import io.github.libfdx.core.*;
import io.github.libfdx.json.*;
import io.github.libfdx.maps.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

final class TiledInfiniteMapTest {
    private static final String DOCUMENT="""
            {"type":"map","orientation":"orthogonal","infinite":true,"width":0,"height":0,
             "tilewidth":16,"tileheight":16,"parallaxoriginx":2,"parallaxoriginy":3,
             "tilesets":[{"firstgid":1,"name":"tiles","image":"tiles.png","imagewidth":64,"imageheight":16,
               "tilewidth":16,"tileheight":16,"tilecount":4,"columns":4}],
             "layers":[{"id":1,"type":"group","offsetx":3,"offsety":-4,"layers":[
               {"id":2,"type":"tilelayer","width":0,"height":0,"chunks":[
                 {"x":-4,"y":-1,"width":2,"height":2,"data":[1,2147483650,3,4]},
                 {"x":100000000,"y":-2,"width":1,"height":1,"data":[3758096385]},
                 {"x":-100000000,"y":2,"width":1,"height":1,"data":[1]}]}]},
               {"id":3,"type":"objectgroup","objects":[{"id":1,"point":true,"x":-7,"y":-9}]},
               {"id":4,"type":"imagelayer","image":"background.png"}]}
            """;
    @Test
    void arbitrarySignedChunksGroupsObjectsAndParallaxUseTheUnboundedOrigin() {
        TileMap map=parse(json(),64);
        assertTrue(map.isInfinite());assertEquals(0,map.width());assertEquals(0,map.height());
        assertEquals(2,map.parallaxOriginX());assertEquals(-3,map.parallaxOriginY());
        GroupLayer group=(GroupLayer)map.mapLayer(0);assertEquals(3,group.offsetX());assertEquals(4,group.offsetY());
        ChunkedTileLayer layer=(ChunkedTileLayer)group.layer(0);
        assertEquals(3,layer.chunkCount());assertEquals(6,layer.residentCells());
        TileChunk first=layer.findChunk(-4,-1);
        assertEquals(1,first.tile(0,1));assertEquals(2,first.tile(1,1));assertEquals(1,first.transform(1,1));
        assertEquals(3,first.tile(0,0));assertEquals(4,first.tile(1,0));
        assertEquals(7,layer.transform(100000000,1));assertEquals(1,layer.tile(-100000000,-3));
        assertEquals(-7,map.findObject(1).x());assertEquals(9,map.findObject(1).y());
        assertEquals(0,((ImageLayer)map.mapLayer(2)).y());
    }
    @Test
    void malformedOverlapOverflowAndUnmappedIdsFailWithSourceAndLayerContext() {
        reject(root->chunks(root).add(chunks(root).require(0)),"duplicate chunk");
        reject(root->chunks(root).add(chunk(-3,-1,1,1,1)),"overlap");
        reject(root->chunks(root).require(0).put("width",3),"count");
        reject(root->chunks(root).require(0).put("data",JsonValue.array().add(1)),"count");
        reject(root->chunks(root).require(0).put("data",JsonValue.array().add(9).add(2).add(3).add(4)),"unmapped");
        reject(root->chunks(root).require(0).put("y",Integer.MAX_VALUE),"conversion");
        reject(root->chunks(root).require(0).put("x",Integer.MAX_VALUE),"coordinate");
        reject(root->chunks(root).require(0).put("data","AAAA"),"array");
        reject(root->tileLayer(root).put("encoding","base64"),"array");
        reject(root->tileLayer(root).put("data",JsonValue.array()),"infinite");
        assertThrows(FdxException.class,()->parse(json(),5));
    }
    @Test
    void standaloneChunksRetainUnsignedFlagsAndValidateBothSignedBoundaries() {
        var reader=new TiledReader("world/chunk.json",4);
        var result=reader.readChunk(bytes(chunk(Integer.MAX_VALUE,Integer.MIN_VALUE,1,1,3758096385L)));
        assertEquals(Integer.MAX_VALUE,result.x());assertEquals(Integer.MAX_VALUE,result.y());
        assertEquals(1,result.tile(0,0));assertEquals(7,result.transform(0,0));
        assertThrows(FdxException.class,()->new TiledReader("world/chunk.json",1).readChunk(bytes(chunk(0,0,2,1,1))));
    }
    private static JsonValue json(){return new JsonReader().parse(DOCUMENT);}
    private static JsonValue tileLayer(JsonValue root){return root.require("layers").require(0).require("layers").require(0);}
    private static JsonValue chunks(JsonValue root){return tileLayer(root).require("chunks");}
    private static JsonValue chunk(int x,int y,int w,int h,long id) {
        JsonValue data=JsonValue.array();for(int i=0;i<w*h;i++)data.add(id);
        return JsonValue.object().put("x",x).put("y",y).put("width",w).put("height",h).put("data",data);
    }
    private static byte[] bytes(JsonValue json){return json.toJson().getBytes(StandardCharsets.UTF_8);}
    private static TileMap parse(JsonValue json,int limit) {
        var document=new TiledReader("world/infinite.tmj",limit).readMap(bytes(json));
        var atlases=new Array<FdxFuture<TileAtlas>>();
        for(var reference:document.references)atlases.add(FdxFuture.completed(reference.embedded));
        return document.assemble(atlases);
    }
    private static void reject(Consumer<JsonValue> edit,String message) {
        var root=json();edit.accept(root);
        FdxException failure=assertThrows(FdxException.class,()->parse(root,64));
        assertTrue(failure.getMessage().contains("world/infinite.tmj"),failure.getMessage());
        assertTrue(failure.getMessage().contains("layer 2"),failure.getMessage());
        assertTrue(failure.getMessage().contains(message),failure.getMessage());
    }
}
