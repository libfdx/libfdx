package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TileModelTest {
    @Test
    void transformsMatchAllEightIndependentCornerPermutations() {
        int[][] expected = {{0,1,2,3}, {3,2,1,0}, {1,0,3,2}, {2,3,0,1},
                {2,1,0,3}, {1,2,3,0}, {3,0,1,2}, {0,3,2,1}};
        for (int flags = 0; flags < 8; flags++) {
            for (int corner = 0; corner < 4; corner++) {
                assertEquals(expected[flags][corner], TileTransform.corner(corner, flags));
            }
        }
        assertThrows(FdxException.class, () -> TileTransform.check(8));
    }
    @Test
    void cellEditsAndEmptyCellsClearOldTransforms() {
        TileLayer layer = new TileLayer(2, 1);
        layer.tile(0, 0, 1, 7); assertEquals(7, layer.transform(0, 0));
        layer.tile(0, 0, 2); assertEquals(0, layer.transform(0, 0));
        layer.tile(0, 0, 0, 7); assertEquals(0, layer.transform(0, 0));
        layer.tile(1, 0, 3, 4).fill(2); assertEquals(0, layer.transform(1, 0));
        assertThrows(FdxException.class, () -> layer.tile(0, 0, -1, 0));
        assertThrows(FdxException.class, () -> layer.tile(0, 0, 1, 8));
        assertThrows(FdxException.class, () -> new TileLayer(Integer.MAX_VALUE, 2));
    }
    @Test
    void mixedLayerOrderAndLegacyTileIndicesUseTheSameObjects() {
        TileMap map = new TileMap(2, 2, 16, 16);
        TileLayer first = map.addLayer();
        ObjectLayer objects = new ObjectLayer(new MapObject[0], false);
        map.addObjectLayer(objects); TileLayer last = map.addLayer();
        assertSame(first, map.mapLayer(0)); assertSame(objects, map.mapLayer(1)); assertSame(last, map.mapLayer(2));
        assertSame(last, map.layer(1)); assertEquals(2, map.layerCount());
        assertSame(first, map.removeLayer(0)); assertSame(objects, map.mapLayer(0));
        assertSame(last, map.layer(0)); map.clearLayers(); assertEquals(0, map.mapLayerCount());
    }
    @Test
    void dimensionsAndLayerSettingsRejectNonfiniteValues() {
        assertThrows(FdxException.class, () -> new TileMap(1, 1, Float.NaN, 1));
        assertThrows(FdxException.class, () -> new TileMap(2, 1, Float.MAX_VALUE, 1));
        TileLayer layer = new TileLayer(1, 1);
        assertThrows(FdxException.class, () -> layer.opacity(-.1f));
        assertThrows(FdxException.class, () -> layer.opacity(Float.NaN));
        assertThrows(FdxException.class, () -> layer.offset(0, Float.POSITIVE_INFINITY));
    }
    @Test
    void objectSortingKeepsEditorOrderAndIsStableForEqualDepth() {
        MapObject a = object(1, 12), b = object(2, 25), c = object(3, 25);
        ObjectLayer layer = new ObjectLayer(new MapObject[] {a, b, c}, true);
        assertSame(a, layer.object(0)); assertSame(b, layer.drawObject(0)); assertSame(c, layer.drawObject(1));
        assertSame(a, layer.drawObject(2));
        TileMap map = new TileMap(1, 1, 32, 32).addObjectLayer(layer);
        assertSame(c, map.findObject(3)); assertNull(map.findObject(99));
    }
    @Test
    void propertiesKeepTypesAndRejectWrongAccessors() {
        MapProperties properties = new MapProperties().put(new MapProperty("target", MapProperty.Type.OBJECT, 8L));
        assertEquals(8, properties.get("target").longValue());
        assertThrows(FdxException.class, () -> properties.get("target").stringValue());
        assertThrows(FdxException.class, () -> new MapProperty("target", MapProperty.Type.OBJECT, 8));
        assertThrows(FdxException.class, () -> properties.get("missing")); assertNull(properties.find("missing"));
        properties.put(new MapProperty("target", MapProperty.Type.OBJECT, 9L));
        assertEquals(1, properties.size()); assertEquals(9, properties.get("target").longValue());
    }
    private static MapObject object(int id, float y) {
        return new MapObject(id, "", "", MapObject.Shape.POINT, 0, y, 0, 0, 0, true, 1,
                0, 0, new float[] {0,0}, new MapProperties());
    }
}
