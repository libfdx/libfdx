package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TileAnimationTest {
    @Test
    void copiedVariableDurationsWrapAtBoundariesAndAtExtremeClocks() {
        int[] ids = {4, 7, 2}, durations = {100, 250, 50};
        TileAnimation animation = new TileAnimation(ids, durations);
        ids[0] = 99; durations[0] = 99;
        assertEquals(3, animation.frameCount()); assertEquals(400, animation.totalDurationMillis());
        assertEquals(100, animation.durationMillis(0)); assertEquals(250, animation.durationMillis(1));
        for (long base : new long[] {-800, 0, 800, 400_000_000_000L}) {
            assertEquals(4, animation.tileAt(base)); assertEquals(4, animation.tileAt(base + 99));
            assertEquals(7, animation.tileAt(base + 100)); assertEquals(7, animation.tileAt(base + 349));
            assertEquals(2, animation.tileAt(base + 350)); assertEquals(2, animation.tileAt(base + 399));
        }
        assertEquals(animation.tileAt(Math.floorMod(Long.MIN_VALUE, 400)), animation.tileAt(Long.MIN_VALUE));
        assertEquals(animation.tileAt(Long.MAX_VALUE % 400), animation.tileAt(Long.MAX_VALUE));
        assertEquals(4_294_967_294L, new TileAnimation(new int[] {0,1},
                new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}).totalDurationMillis());
    }

    @Test
    void invalidFramesAndAtlasReferencesFailBeforeReplacingASequence() {
        assertThrows(FdxException.class, () -> new TileAnimation(new int[0], new int[0]));
        assertThrows(FdxException.class, () -> new TileAnimation(new int[] {0}, new int[] {0}));
        assertThrows(FdxException.class, () -> new TileAnimation(new int[] {-1}, new int[] {1}));
        TileAtlas atlas = new TileAtlas("test", "test.png", 32, 16, 16, 16, 2, 2, 0, 0, 0, 0, new MapProperties());
        TileAnimation valid = new TileAnimation(new int[] {0,1}, new int[] {100,100});
        atlas.animation(0, valid);
        assertThrows(FdxException.class, () -> atlas.animation(0, new TileAnimation(new int[] {2}, new int[] {1})));
        assertSame(valid, atlas.animation(0));
        atlas.animation(0, null); assertNull(atlas.animation(0));
    }

    @Test
    void groupsCopyMembershipLimitDepthAndValidateMapDimensions() {
        TileLayer tile = new TileLayer(1, 1);
        MapLayer[] children = {tile};
        GroupLayer group = new GroupLayer(children);
        children[0] = null; assertSame(tile, group.layer(0));
        for (int depth = 1; depth < 32; depth++) { group = new GroupLayer(group); }
        GroupLayer deepest = group;
        assertThrows(FdxException.class, () -> new GroupLayer(deepest));
        TileMap map = new TileMap(1,1,16,16); map.addGroupLayer(group);
        assertEquals(0, map.layerCount()); assertEquals(1, map.mapLayerCount());
        assertThrows(FdxException.class, () -> map.addGroupLayer(new GroupLayer(new TileLayer(2,1))));
    }
}
