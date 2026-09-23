package io.github.libfdx.maps.tiled;

import io.github.libfdx.assets.*;
import io.github.libfdx.core.*;
import io.github.libfdx.files.*;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.graphics.g2d.TileMapAsset;
import io.github.libfdx.json.*;
import io.github.libfdx.maps.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

final class TiledMapLoaderTest {
    private static final String MAP = "tiled/level.tmj";
    private static final String NEXT = "tiled/next-level.tmj";
    private static final String ATLAS = "tiled/tilesets/terrain.tsj";
    private static final String IMAGE = "tiled/images/terrain.png";
    private static final String COLLECTION_MAP = "tiled/collection.tmj";
    private static final String COLLECTION = "tiled/tilesets/collection.tsj";
    private static final String COLLECTION_A = "tiled/images/collection-a.png";
    private static final String COLLECTION_B = "tiled/images/collection-b.png";

    @Test
    void sparseCollectionsLoadDependenciesBeforeBindingAndUseNativeImageCrops() {
        Fixture fixture = new Fixture(64); // The 100-million-wide ID span must not consume the tile/cell budget.
        var cpu = fixture.manager.acquire(AssetDescriptor.of(COLLECTION_MAP, TileMap.class)); fixture.drain();
        assertEquals(2, fixture.readCounts.size()); assertEquals(0, fixture.uploads);
        TileAtlas atlas = cpu.asset().atlas(0);
        assertTrue(atlas.isImageCollection()); assertEquals(3, atlas.tileCount());
        assertEquals(100_000_001, atlas.localIdLimit()); assertEquals(7, atlas.localId(1));
        assertEquals("cropped", atlas.tileClass(100_000_000));
        assertEquals(-1, cpu.asset().findAtlas(2)); assertEquals(1, cpu.asset().findAtlas(100_000_002));
        fixture.graphics(); var delayed = fixture.delay(COLLECTION_B);
        var first = fixture.manager.acquire(AssetDescriptor.of(COLLECTION_MAP, TileMapAsset.class));
        var second = fixture.manager.acquire(AssetDescriptor.of(COLLECTION_MAP, TileMapAsset.class));
        fixture.advance(200); assertFalse(first.isLoaded()); assertEquals(2, fixture.uploads);
        delayed.complete(bytes(COLLECTION_B)); fixture.drain();
        var tiles = first.asset().tiles();
        assertSame(first.asset(), second.asset()); assertEquals(5, tiles.size());
        assertEquals(12, tiles.region(1).width()); assertEquals(20, tiles.region(1).height());
        assertEquals(8, tiles.region(100_000_001).width()); assertEquals(8, tiles.region(100_000_001).height());
        assertSame(tiles.region(1).texture(), tiles.region(100_000_001).texture());
        assertSame(tiles.region(1).texture(), tiles.findImage(COLLECTION_A).texture());
        tiles.animationTime(100); assertSame(tiles.region(8), tiles.region(1));
        assertEquals(20, tiles.region(1).width()); assertEquals(12, tiles.region(1).height());
        assertEquals(1, fixture.readCounts.get(COLLECTION_A)); assertEquals(3, fixture.uploads);
        Texture texture = tiles.region(8).texture(); first.dispose(); assertFalse(texture.isDisposed());
        second.dispose(); assertTrue(texture.isDisposed()); assertEquals(3, fixture.disposals);
        cpu.dispose(); fixture.close();
    }

    @Test
    void sparseHolesInvalidCropsAndOverlappingRangesFailBeforeTextureLoading() {
        rejectCollection(map -> {
            JsonValue data = map.require("layers").require(0).require("data"), changed = JsonValue.array().add(2);
            for (int i = 1; i < data.size(); i++) { changed.add(data.require(i)); }
            map.require("layers").require(0).put("data", changed);
        }, null, "unmapped");
        rejectCollection(map -> map.require("layers").require(1).require("objects").require(0).put("gid", 2), null, "object 1");
        rejectCollection(null, atlas -> atlas.require("tiles").require(1).require("animation").require(0).put("tileid", 1), "local tile");
        rejectCollection(null, atlas -> atlas.require("tiles").require(2).put("id", 0), "duplicate");
        rejectCollection(null, atlas -> atlas.require("tiles").require(0).put("x", 10), "image");
        rejectCollection(null, atlas -> atlas.put("tilecount", 4), "tilecount");
        rejectCollection(map -> map.require("tilesets").require(1).put("firstgid", 10), null, "overlapping");
        rejectCollection(map -> map.require("tilesets").require(1).put("firstgid", 0x0fffffff), null, "range");
        rejectCollection(null, atlas -> atlas.require("tiles").require(0).put("image", "../../../escape.png"), "root");
    }

    @Test
    void collectionImageFailuresReleaseAllPreparedTextures() {
        for (boolean wrongSize : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(); fixture.graphics();
            if (wrongSize) {
                JsonValue atlas = new JsonReader().parse(bytes(COLLECTION));
                atlas.require("tiles").require(2).put("imagewidth", 19);
                fixture.overrides.put(COLLECTION, encode(atlas));
            }
            var delayed = fixture.delay(COLLECTION_B);
            var lease = fixture.manager.acquire(AssetDescriptor.of(COLLECTION_MAP, TileMapAsset.class));
            fixture.advance(200); assertEquals(2, fixture.uploads);
            if (wrongSize) { delayed.complete(bytes(COLLECTION_B)); }
            else { delayed.completeExceptionally(new FdxException("Missing collection image")); }
            fixture.drain(); assertTrue(lease.future().isFailed());
            assertTrue(failure(lease).contains(wrongSize ? "tile 7" : "Missing collection image"));
            assertEquals(fixture.uploads, fixture.disposals); fixture.close();
        }
    }

    private static void rejectCollection(Consumer<JsonValue> editMap, Consumer<JsonValue> editAtlas, String message) {
        Fixture fixture = new Fixture();
        try {
            JsonValue map = new JsonReader().parse(bytes(COLLECTION_MAP)), atlas = new JsonReader().parse(bytes(COLLECTION));
            if (editMap != null) { editMap.accept(map); }
            if (editAtlas != null) { editAtlas.accept(atlas); }
            fixture.overrides.put(COLLECTION_MAP, encode(map)); fixture.overrides.put(COLLECTION, encode(atlas));
            var lease = fixture.manager.acquire(AssetDescriptor.of(COLLECTION_MAP, TileMap.class)); fixture.drain();
            assertTrue(lease.future().isFailed(), message); assertTrue(failure(lease).contains(message), failure(lease));
            assertEquals(0, fixture.uploads); assertFalse(fixture.readCounts.containsKey(COLLECTION_A));
        } finally { fixture.close(); }
    }

    @Test
    void imageLayersLoadOnlyGraphicsDependenciesAndSharePagesAcrossScopes() {
        Fixture fixture=new Fixture();
        JsonValue map=root();
        JsonValue image=JsonValue.object().put("id",12).put("type","imagelayer").put("image","images/terrain.png")
                .put("imagewidth",100).put("imageheight",67).put("offsetx",3).put("offsety",5)
                .put("repeatx",true).put("tintcolor","#8080ff40");
        JsonValue group=JsonValue.object().put("id",11).put("type","group").put("tintcolor","#ff8040")
                .put("parallaxx",.5).put("layers",JsonValue.array().add(image));
        map.put("tilesets",JsonValue.array()).put("layers",JsonValue.array().add(group));
        fixture.overrides.put(MAP,encode(map));
        var cpu=fixture.manager.acquire(AssetDescriptor.of(MAP,TileMap.class)); fixture.drain();
        assertEquals(1,fixture.readCounts.size()); assertEquals(0,fixture.uploads);
        GroupLayer loadedGroup=(GroupLayer)cpu.asset().mapLayer(0);
        ImageLayer loaded=(ImageLayer)loadedGroup.layer(0);
        assertEquals(0xff8040ff,loadedGroup.tintRgba()); assertEquals(0x80ff4080,loaded.tintRgba());
        assertEquals(IMAGE,loaded.imagePath()); assertEquals(cpu.asset().worldHeight(),loaded.y());
        assertEquals(-5,loaded.offsetY()); assertTrue(loaded.repeatX()); assertFalse(loaded.repeatY());
        fixture.graphics(); FdxFuture<byte[]> page=fixture.delay(IMAGE);
        var first=fixture.manager.acquire(AssetDescriptor.of(MAP,TileMapAsset.class));
        var second=fixture.manager.acquire(AssetDescriptor.of(MAP,TileMapAsset.class));
        fixture.advance(100); assertFalse(first.future().isDone()); assertEquals(0,fixture.uploads);
        page.complete(bytes(IMAGE)); fixture.drain();
        assertSame(first.asset(),second.asset()); Texture texture=second.asset().tiles().findImage(IMAGE).texture();
        assertEquals(1,fixture.uploads); first.dispose(); assertFalse(texture.isDisposed());
        second.dispose(); assertTrue(texture.isDisposed()); assertEquals(1,fixture.disposals);
        cpu.dispose(); fixture.close();
    }
    @Test
    void imageLayerDimensionsAndPathsFailClearlyAndMissingDimensionsResolveAtBinding() {
        for(boolean omitDimensions:new boolean[]{true,false}) {
            Fixture fixture=new Fixture(); fixture.graphics();
            JsonValue image=JsonValue.object().put("id",1).put("type","imagelayer").put("image","images/terrain.png");
            if(!omitDimensions) image.put("imagewidth",99).put("imageheight",67);
            JsonValue map=root().put("tilesets",JsonValue.array()).put("layers",JsonValue.array().add(image));
            fixture.overrides.put(MAP,encode(map));
            var asset=fixture.manager.acquire(AssetDescriptor.of(MAP,TileMapAsset.class)); fixture.drain();
            if(omitDimensions) assertEquals(100,asset.asset().tiles().findImage(IMAGE).width());
            else { assertTrue(asset.future().isFailed()); assertTrue(failure(asset).contains("layer 1")); assertEquals(1,fixture.disposals); }
            fixture.close();
        }
        reject(map -> map.put("layers",JsonValue.array().add(JsonValue.object().put("id",1).put("type","imagelayer")
                .put("image","../../escape.png"))),"root");
        reject(map -> map.put("layers",JsonValue.array().add(JsonValue.object().put("id",1).put("type","imagelayer")
                .put("image","images/terrain.png").put("imagewidth",100))),"dimensions");
        reject(map -> map.put("layers",JsonValue.array().add(JsonValue.object().put("id",1).put("type","imagelayer")
                .put("image","images/terrain.png").put("transparentcolor","#00ff00"))),"transparentcolor");
    }

    @Test
    void groupsAnimationsAndParallaxSurviveBudgetedCpuAndGraphicsLoading() {
        Fixture fixture = new Fixture(); fixture.graphics();
        AssetLease<TileMapAsset> lease = fixture.manager.acquire(AssetDescriptor.of("tiled/motion.tmj", TileMapAsset.class));
        fixture.drain();
        TileMapAsset asset = lease.asset(); TileMap map = asset.map();
        assertEquals(1,map.mapLayerCount()); assertEquals(0,map.layerCount());
        GroupLayer outer = (GroupLayer)map.mapLayer(0), nested = (GroupLayer)outer.layer(0);
        assertEquals(4,outer.offsetX()); assertEquals(4,outer.offsetY()); assertEquals(.5,outer.parallaxX());
        assertEquals(.5,nested.parallaxX()); assertFalse(outer.layer(1).isVisible());
        assertEquals(32,map.parallaxOriginX()); assertEquals(48,map.parallaxOriginY());
        assertEquals("marker",map.findObject(1).name());
        TileAtlas atlas = map.atlas(0); assertEquals(2,atlas.offsetX()); assertEquals(1,atlas.offsetY());
        assertEquals(300,atlas.animation(0).totalDurationMillis());
        var first = asset.tiles().region(1); var second = asset.tiles().region(2);
        assertNotSame(first,second); asset.tiles().animationTime(100); assertSame(second,asset.tiles().region(1));
        asset.tiles().animationTime(300); assertSame(first,asset.tiles().region(1));
        Texture image = first.texture(); lease.dispose(); assertTrue(image.isDisposed()); fixture.close();
    }

    @Test
    void nestedReferencesIdsAndAnimationBoundsAreValidated() {
        reject(map -> {
            JsonValue layers=map.require("layers");
            JsonValue original=layers.require(0).require("data"), changed=JsonValue.array().add(1000);
            for(int i=1;i<original.size();i++) { changed.add(original.require(i)); }
            layers.require(0).put("data",changed);
            map.put("layers",JsonValue.array().add(JsonValue.object().put("id",10).put("type","group").put("layers",layers)));
        },"unmapped");
        reject(map -> map.put("layers",JsonValue.array().add(JsonValue.object().put("id",1).put("type","group")
                .put("layers",map.require("layers")))),"duplicate");
        reject(map -> {
            JsonValue layers=map.require("layers");
            for(int i=0;i<33;i++) { layers=JsonValue.array().add(JsonValue.object().put("id",10+i).put("type","group").put("layers",layers)); }
            map.put("layers",layers);
        },"depth");
        for (int id : new int[] {-1,2}) {
            reject(map -> map.require("tilesets").require(1).put("tiles",JsonValue.array().add(JsonValue.object().put("id",0)
                    .put("animation",JsonValue.array().add(JsonValue.object().put("tileid",id).put("duration",100))))),"tile");
        }
        reject(map -> map.require("tilesets").require(1).put("tiles",JsonValue.array().add(JsonValue.object().put("id",0)
                .put("animation",JsonValue.array().add(JsonValue.object().put("tileid",0).put("duration",0))))),"duration");
    }

    @Test
    void loadsCpuMapWithExternalAtlasAllFlagsSparseMetadataAndObjects() {
        Fixture fixture = new Fixture();
        AssetLease<TileMap> lease = fixture.manager.acquire(AssetDescriptor.of(MAP, TileMap.class));
        fixture.drain();
        TileMap map = lease.future().get();
        assertEquals(12, map.width()); assertEquals(8, map.height());
        assertEquals(3, map.mapLayerCount()); assertEquals(2, map.layerCount());
        assertEquals(2, map.atlasCount()); assertEquals(17, map.firstId(1));
        assertTrue(map.reverseY()); assertFalse(map.reverseX());
        for (int flags = 0; flags < 8; flags++) {
            assertEquals(1, map.layer(1).tile(flags + 1, 6));
            assertEquals(flags, map.layer(1).transform(flags + 1, 6));
        }
        assertEquals(6, map.layer(1).tile(8, 1)); // Clears the fourth high flag.
        assertEquals(0, map.layer(1).transform(8, 1));
        assertEquals(34, map.atlas(0).sourceX(4)); assertEquals(34, map.atlas(0).sourceY(4));
        assertTrue(map.atlas(0).tileProperties(5).get("solid").booleanValue());
        assertEquals("wall", map.atlas(0).tileClass(5)); assertNull(map.atlas(0).tileProperties(1));
        assertEquals(2, fixture.readCounts.size()); // CPU loading never requests images.
        assertEquals(48, map.findObject(1).x()); assertEquals(48, map.findObject(1).y());
        assertEquals(2, map.findObject(1).properties().get("target").longValue());
        MapObject rectangle = map.findObject(2);
        assertEquals(76, rectangle.y()); assertEquals(44, rectangle.worldY(2));
        MapObject ramp = map.findObject(3);
        assertEquals(192, ramp.worldX(2)); assertEquals(80, ramp.worldY(2));
        MapObject rotated = map.findObject(6);
        assertEquals(-90, rotated.rotationDegrees()); assertEquals(180, rotated.worldX(1), .001);
        assertEquals(80, rotated.worldY(1), .001);
        assertEquals(32, map.findObject(5).y()); assertEquals(48, map.findObject(5).height());
        assertEquals("tiled/level.js", map.properties().get("script").stringValue());
        assertEquals(0x4488cc80, map.properties().get("ambient").rgba());
        assertEquals(9.8, map.properties().get("gravity").doubleValue(), .00001);
        assertFalse(map.properties().get("night").booleanValue());
        assertEquals(3, map.properties().get("lives").longValue());
        lease.dispose(); assertNull(fixture.manager.find(ATLAS, TileAtlas.class));
        fixture.close();
    }

    @Test
    void pendingExternalAtlasPreventsMapPublicationWithinOneStepBudgets() {
        Fixture fixture = new Fixture();
        FdxFuture<byte[]> read = fixture.delay(ATLAS);
        AssetLease<TileMap> lease = fixture.manager.acquire(AssetDescriptor.of(MAP, TileMap.class));
        fixture.advance(80);
        assertFalse(lease.future().isDone()); assertEquals(1, fixture.readCounts.get(ATLAS));
        read.complete(bytes(ATLAS));
        fixture.drain(); assertTrue(lease.isLoaded()); fixture.close();
    }

    @Test
    void twoDifferentMapScopesShareAtlasAndGpuImagesUntilFinalRelease() {
        Fixture fixture = new Fixture(); fixture.graphics();
        FdxFuture<byte[]> image = fixture.delay(IMAGE);
        AssetScope a = fixture.manager.createScope(), b = fixture.manager.createScope();
        AssetLease<TileMapAsset> first = a.load(AssetDescriptor.of(MAP, TileMapAsset.class));
        AssetLease<TileMapAsset> second = b.load(AssetDescriptor.of(NEXT, TileMapAsset.class));
        fixture.advance(160);
        assertFalse(first.future().isDone()); assertFalse(second.future().isDone());
        image.complete(bytes(IMAGE)); fixture.drain();
        assertNotSame(first.asset(), second.asset());
        assertSame(first.asset().map().atlas(0), second.asset().map().atlas(0));
        Texture texture = first.asset().tiles().region(1).texture();
        assertSame(texture, second.asset().tiles().region(1).texture());
        assertEquals(1, fixture.readCounts.get(IMAGE)); assertEquals(1, fixture.readCounts.get(ATLAS));
        assertEquals(2, fixture.uploads);
        TileMapAsset old = first.asset();
        a.dispose(); fixture.manager.unload(IMAGE);
        assertTrue(old.isDisposed()); assertFalse(texture.isDisposed());
        assertThrows(FdxException.class, old::tiles);
        assertTrue(second.isLoaded());
        b.dispose();
        assertTrue(texture.isDisposed()); assertEquals(2, fixture.disposals);
        assertNull(fixture.manager.find(ATLAS, TileAtlas.class)); fixture.close();
        assertEquals(2, fixture.disposals);
    }

    @Test
    void cancellingOnePendingMapKeepsOtherMapsDependenciesAlive() {
        Fixture fixture = new Fixture(); fixture.graphics();
        FdxFuture<byte[]> image = fixture.delay(IMAGE);
        AssetScope a = fixture.manager.createScope(), b = fixture.manager.createScope();
        AssetLease<TileMapAsset> first = a.load(AssetDescriptor.of(MAP, TileMapAsset.class));
        AssetLease<TileMapAsset> second = b.load(AssetDescriptor.of(NEXT, TileMapAsset.class));
        fixture.advance(160); a.dispose();
        assertTrue(first.future().isFailed()); assertFalse(second.future().isDone());
        image.complete(bytes(IMAGE)); fixture.drain();
        assertTrue(second.isLoaded()); assertEquals(2, fixture.uploads);
        assertNull(fixture.manager.find(MAP, TileMapAsset.class));
        fixture.close(); assertEquals(2, fixture.disposals);
    }

    @Test
    void failedExternalReadPropagatesAndRetriedMapCanLoad() {
        Fixture fixture = new Fixture();
        FdxFuture<byte[]> atlas = fixture.delay(ATLAS);
        AssetLease<TileMap> first = fixture.manager.acquire(AssetDescriptor.of(MAP, TileMap.class));
        fixture.advance(80); atlas.completeExceptionally(new FdxException("fixture I/O failure"));
        fixture.drain(); assertTrue(first.future().isFailed()); first.dispose();
        fixture.pending.remove(ATLAS);
        AssetLease<TileMap> retry = fixture.manager.acquire(AssetDescriptor.of(MAP, TileMap.class));
        fixture.drain(); assertTrue(retry.isLoaded()); fixture.close();
    }

    @Test
    void cancelledMapAttemptCannotPublishOverItsRetry() {
        Fixture fixture = new Fixture(); fixture.graphics();
        FdxFuture<byte[]> atlas = fixture.delay(ATLAS);
        AssetLease<TileMapAsset> old = fixture.manager.acquire(AssetDescriptor.of(MAP, TileMapAsset.class));
        fixture.advance(80); old.dispose();
        AssetLease<TileMapAsset> retry = fixture.manager.acquire(AssetDescriptor.of(MAP, TileMapAsset.class));
        fixture.advance(80); atlas.complete(bytes(ATLAS)); fixture.drain();
        assertEquals(AssetStatus.UNLOADED, old.status()); assertTrue(old.future().isFailed());
        assertTrue(retry.isLoaded()); assertEquals(2, fixture.readCounts.get(ATLAS));
        assertEquals(2, fixture.uploads); fixture.close(); assertEquals(2, fixture.disposals);
    }

    @Test
    void failedBindingReleasesUploadedImagesWithoutPartiallyPublishingMap() {
        Fixture fixture = new Fixture(); fixture.graphics(); fixture.wrongImageSize = true;
        AssetLease<TileMapAsset> lease = fixture.manager.acquire(AssetDescriptor.of(MAP, TileMapAsset.class));
        fixture.drain();
        assertTrue(lease.future().isFailed()); assertNull(lease.asset());
        assertEquals(2, fixture.uploads); assertEquals(2, fixture.disposals);
        assertNull(fixture.manager.find(IMAGE, Texture.class)); fixture.close();
    }

    @Test
    void invalidGidFailsBeforeImagesAreRequested() {
        Fixture fixture = new Fixture(); fixture.graphics();
        JsonValue json = root();
        JsonValue data = JsonValue.array();
        for (int i = 0; i < 96; i++) { data.add(i == 0 ? 8 : 0); }
        json.require("layers").require(1).put("data", data);
        fixture.overrides.put(MAP, encode(json));
        AssetLease<TileMapAsset> lease = fixture.manager.acquire(AssetDescriptor.of(MAP, TileMapAsset.class));
        fixture.drain(); assertTrue(lease.future().isFailed()); assertEquals(0, fixture.uploads);
        assertEquals(2, fixture.readCounts.size());
        assertTrue(failure(lease).contains("layer 2")); fixture.close();
    }

    @Test
    void malformedCountsRangesAndUnsignedIdsFailWithSourceContext() {
        reject(map -> map.put("width", 0), "width");
        reject(map -> map.put("width", 99999999), "limit");
        reject(map -> map.require("layers").require(0).put("data", JsonValue.array().add(1)), "count");
        reject(map -> map.require("layers").require(0).put("width", 2), "dimensions");
        reject(map -> map.require("layers").require(0).put("id", 2), "duplicate");
        for (String gid : new String[] {"-1", "4294967296", "1.5", "18446744073709551616"}) {
            reject(map -> {
                JsonValue data = map.require("layers").require(0).require("data");
                JsonValue changed = JsonValue.array().add(new JsonReader().parse(gid));
                for (int i = 1; i < data.size(); i++) { changed.add(data.require(i)); }
                map.require("layers").require(0).put("data", changed);
            }, "layer");
        }
        reject(map -> map.require("tilesets").require(1).put("firstgid", 4), "overlapping");
        reject(map -> map.require("tilesets").require(1).put("tilecount", 100), "atlas");
        reject(map -> map.require("layers").require(2).require("objects").require(1).put("id", 1), "object 1");
        reject(map -> map.require("layers").require(2).require("objects").require(4).put("width", 0), "object 5");
    }

    @Test
    void unsupportedRuntimeFeaturesFailInsteadOfSilentlyChangingTheMap() {
        reject(map -> map.put("orientation", "isometric"), "orientation");
        reject(map -> map.put("infinite", true), "infinite");
        reject(map -> map.require("layers").require(0).put("type", "group"), "layer");
        reject(map -> map.require("layers").require(0).put("encoding", "base64"), "array");
        reject(map -> map.require("layers").require(0).put("data", "AQAAAA=="), "array");
        reject(map -> map.require("layers").require(0).put("mode", "multiply"), "blend");
        reject(map -> map.require("layers").require(0).put("tintcolor", "#oops"), "color");
        reject(map -> map.require("tilesets").require(1).put("objectalignment", "center"), "alignment");
        reject(map -> map.require("tilesets").require(1).put("tiles",
                JsonValue.array().add(JsonValue.object().put("id", 0).put("animation", JsonValue.array()))), "animation");
        reject(map -> map.require("layers").require(2).require("objects").require(1).put("ellipse", true), "object 2");
        reject(map -> map.require("properties").require(0).put("type", "class"), "property");
    }

    @Test
    void propertyTypesAreStrictAndRelativePathsStayPortable() {
        reject(map -> map.require("properties").require(0).put("value", 7), "string");
        reject(map -> map.require("properties").require(1).put("value", "9.8"), "number");
        reject(map -> map.require("properties").require(3).put("value", "#oops"), "color");
        reject(map -> map.require("properties").require(4).put("value", "../../escape"), "root");
        assertEquals("tiled/images/terrain.png", TiledReader.resolve("tiled/tilesets/terrain.tsj", "..\\images/./terrain.png"));
        assertThrows(FdxException.class, () -> TiledReader.resolve(MAP, "/absolute.png"));
        assertThrows(FdxException.class, () -> TiledReader.resolve(MAP, "https://example.com/image.png"));
    }

    @Test
    void cellLimitIncludesAllLayersAndLayerOffsetsVisibilityAndOpacityAreRetained() {
        Fixture fixture = new Fixture(100); // One 96-cell layer fits; the second does not.
        AssetLease<TileMap> failed = fixture.manager.acquire(AssetDescriptor.of(MAP, TileMap.class));
        fixture.drain(); assertTrue(failed.future().isFailed()); fixture.close();
        fixture = new Fixture();
        JsonValue json = root();
        json.require("layers").require(1).put("offsetx", 4.5).put("offsety", -3).put("opacity", .5).put("visible", false);
        json.put("renderorder", "left-up");
        fixture.overrides.put(MAP, encode(json));
        AssetLease<TileMap> lease = fixture.manager.acquire(AssetDescriptor.of(MAP, TileMap.class));
        fixture.drain();
        TileMap map = lease.future().get();
        assertTrue(map.reverseX()); assertFalse(map.reverseY());
        assertEquals(4.5, map.layer(1).offsetX()); assertEquals(3, map.layer(1).offsetY());
        assertEquals(.5, map.layer(1).opacity()); assertFalse(map.layer(1).isVisible());
        fixture.close();
    }

    private static void reject(Consumer<JsonValue> edit, String message) {
        Fixture fixture = new Fixture();
        try {
            JsonValue json = root(); edit.accept(json); fixture.overrides.put(MAP, encode(json));
            AssetLease<TileMap> lease = fixture.manager.acquire(AssetDescriptor.of(MAP, TileMap.class));
            fixture.drain(); assertTrue(lease.future().isFailed(), "Expected failure containing " + message);
            String failure = failure(lease);
            assertTrue(failure.contains(MAP), failure);
            assertTrue(failure.toLowerCase().contains(message.toLowerCase()), failure);
        } finally { fixture.close(); }
    }
    private static String failure(AssetLease<?> lease) {
        Throwable error = assertThrows(Throwable.class, () -> lease.future().get());
        StringBuilder message = new StringBuilder();
        while (error != null) { message.append(error.getMessage()).append('\n'); error = error.getCause(); }
        return message.toString();
    }
    private static JsonValue root() { return new JsonReader().parse(bytes(MAP)); }
    private static byte[] encode(JsonValue value) { return value.toJson().getBytes(StandardCharsets.UTF_8); }
    private static byte[] bytes(String path) {
        try (var stream = TiledMapLoaderTest.class.getResourceAsStream("/" + path)) {
            if (stream == null) { throw new AssertionError("Missing fixture " + path); }
            return stream.readAllBytes();
        } catch (IOException error) { throw new AssertionError(error); }
    }
    private static final class Fixture {
        final Map<String, Integer> readCounts = new HashMap<>();
        final Map<String, FdxFuture<byte[]>> pending = new HashMap<>();
        final Map<String, byte[]> overrides = new HashMap<>();
        final DefaultAssetManager manager;
        int uploads, disposals;
        boolean wrongImageSize;
        Fixture() { this(TiledMapLoader.DEFAULT_MAX_CELLS); }
        Fixture(int limit) {
            FileSystem files = proxy(FileSystem.class, (name, args) -> {
                if (!name.equals("internal")) { throw new UnsupportedOperationException(name); }
                String path = (String)args[0];
                return proxy(FileHandle.class, (method, values) -> {
                    if (method.equals("path")) { return path; }
                    if (!method.equals("readBytes")) { throw new UnsupportedOperationException(method); }
                    readCounts.merge(path, 1, Integer::sum);
                    return pending.containsKey(path) ? pending.get(path)
                            : FdxFuture.completed(overrides.containsKey(path) ? overrides.get(path) : bytes(path));
                });
            });
            manager = new DefaultAssetManager(files); TiledMapLoader.register(manager, limit);
        }
        void graphics() {
            Thread app = Thread.currentThread();
            GraphicsDevice device = proxy(GraphicsDevice.class, (name, args) -> {
                assertSame(app, Thread.currentThread());
                if (name.equals("createTexture")) {
                    TextureDescriptor descriptor = (TextureDescriptor)args[0];
                    boolean[] disposed = {false};
                    return proxy(Texture.class, (method, values) -> switch (method) {
                        case "width" -> descriptor.width() + (wrongImageSize ? 1 : 0);
                        case "height" -> descriptor.height();
                        case "isDisposed" -> disposed[0];
                        case "dispose" -> { assertFalse(disposed[0]); disposed[0] = true; disposals++; yield null; }
                        default -> throw new UnsupportedOperationException(method);
                    });
                }
                if (name.equals("writeTexture")) { uploads++; return null; }
                throw new UnsupportedOperationException(name);
            });
            GraphicsContext graphics = proxy(GraphicsContext.class, (name, args) -> device);
            G2DAssetLoaders.register(manager, graphics);
        }
        FdxFuture<byte[]> delay(String path) { FdxFuture<byte[]> value = FdxFuture.pending(); pending.put(path, value); return value; }
        void advance(int steps) {
            for (int i = 0; i < steps; i++) { manager.update(1, Long.MAX_VALUE); assertTrue(manager.lastUpdateTaskCount() <= 1); }
        }
        void drain() {
            for (int i = 0; i < 2000; i++) {
                boolean complete = manager.update(1, Long.MAX_VALUE);
                assertTrue(manager.lastUpdateTaskCount() <= 1);
                if (complete) { return; }
            }
            fail("Tiled load did not settle");
        }
        void close() { manager.dispose(); }
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation call) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (instance, method, args) -> call.call(method.getName(), args));
    }
    private interface Invocation { Object call(String name, Object[] args); }
}
