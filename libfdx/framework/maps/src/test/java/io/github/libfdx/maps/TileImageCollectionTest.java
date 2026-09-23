package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TileImageCollectionTest {
    @Test
    void sparseEnumerationAndRangesUseExistingIdsWithoutAllocatingTheGap() {
        TileImage[] images = {new TileImage(100_000_000, "b.png", 20, 12), new TileImage(0, "a.png", 12, 20)};
        TileAtlas atlas = TileAtlas.imageCollection("props", 20, 20, images, 0, 0, new MapProperties());
        images[0] = null;
        assertEquals(2, atlas.tileCount()); assertEquals(100_000_001, atlas.localIdLimit());
        assertEquals(0, atlas.localId(0)); assertEquals(100_000_000, atlas.localId(1));
        assertFalse(atlas.contains(1)); assertThrows(FdxException.class, () -> atlas.sourceX(1));
        assertThrows(FdxException.class, atlas::imagePath);
        assertEquals("b.png", atlas.imagePath(100_000_000));
        TileMap map = new TileMap(1, 1, 16, 16).addAtlas(1, atlas);
        assertEquals(0, map.findAtlas(100_000_001)); assertEquals(-1, map.findAtlas(2));
        assertThrows(FdxException.class, () -> map.addAtlas(50, atlas));
        assertThrows(FdxException.class, () -> atlas.animation(0, new TileAnimation(new int[]{1}, new int[]{1})));
    }
    @Test
    void validatesCropsIdsAndDimensionsBeforePublication() {
        assertThrows(FdxException.class, () -> new TileImage(0, "a", 10, 10, Integer.MAX_VALUE, 0, 1, 1));
        assertThrows(FdxException.class, () -> new TileImage(-1, "a", 10, 10));
        TileImage image = new TileImage(0, "a", 12, 20, 2, 4, 8, 8);
        TileAtlas atlas = TileAtlas.imageCollection("crop", 8, 8, new TileImage[]{image}, 0, 0, new MapProperties());
        assertEquals(2, atlas.sourceX(0)); assertEquals(4, atlas.sourceY(0));
        assertEquals(12, atlas.imageWidth(0)); assertEquals(8, atlas.regionWidth(0));
        assertThrows(FdxException.class, () -> TileAtlas.imageCollection("bad", 8, 8, new TileImage[]{image, image}, 0, 0, new MapProperties()));
        assertThrows(FdxException.class, () -> TileAtlas.imageCollection("bad", 7, 8, new TileImage[]{image}, 0, 0, new MapProperties()));
    }
}
