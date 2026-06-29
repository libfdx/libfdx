package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TileMapTest {
    @Test
    void layerStoresTileIdsAndVisibility() {
        TileMap map = new TileMap(3, 2, 16.0f, 8.0f);
        TileLayer layer = map.addLayer();

        layer.tile(1, 0, 4);
        layer.tile(2, 1, 7);
        layer.visible(false);

        assertEquals(3, map.width());
        assertEquals(2, map.height());
        assertEquals(16.0f, map.tileWidth());
        assertEquals(8.0f, map.tileHeight());
        assertEquals(48.0f, map.worldWidth());
        assertEquals(16.0f, map.worldHeight());
        assertEquals(1, map.layerCount());
        assertSame(layer, map.layer(0));
        assertEquals(4, layer.tile(1, 0));
        assertEquals(7, layer.tile(2, 1));
        assertFalse(layer.isVisible());
        assertArrayEquals(new int[] {0, 4, 0, 0, 0, 7}, layer.tiles());
    }

    @Test
    void layerRejectsInvalidDimensionsCoordinatesAndIds() {
        TileLayer layer = new TileLayer(2, 2);

        assertThrows(FdxException.class, () -> new TileMap(0, 2, 16, 16));
        assertThrows(FdxException.class, () -> new TileMap(2, 2, 0, 16));
        assertThrows(FdxException.class, () -> new TileLayer(0, 2));
        assertThrows(FdxException.class, () -> layer.tile(0, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> layer.tile(2, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> layer.tile(0, 2, 1));
    }

    @Test
    void tileSetUsesOneBasedIdsAndEmptyZero() {
        TestTexture texture = new TestTexture(32, 16);
        TextureRegion[][] regions = TextureRegion.split(texture, 16, 16);

        TileSet tileSet = TileSet.from(regions);

        assertEquals(2, tileSet.size());
        assertNull(tileSet.region(TileSet.EMPTY_TILE));
        assertSame(regions[0][0], tileSet.region(1));
        assertSame(regions[0][1], tileSet.region(2));
        assertFalse(tileSet.contains(TileSet.EMPTY_TILE));
        assertNull(tileSet.remove(TileSet.EMPTY_TILE));
        assertSame(regions[0][1], tileSet.remove(2));
        assertNull(tileSet.region(2));
    }

    @Test
    void tileSetRejectsInvalidIdsAndNullRegions() {
        TestTexture texture = new TestTexture(16, 16);
        TextureRegion region = new TextureRegion(texture);
        TileSet tileSet = new TileSet();

        assertThrows(FdxException.class, () -> tileSet.region(0, region));
        assertThrows(FdxException.class, () -> tileSet.region(1, null));
        assertThrows(FdxException.class, () -> tileSet.region(-1));
        assertThrows(FdxException.class, () -> TileSet.from(null));
    }

    @Test
    void rendererDrawsVisibleMappedTilesOnly() {
        TestTexture texture = new TestTexture(32, 16);
        TextureRegion[][] regions = TextureRegion.split(texture, 16, 16);
        TileSet tileSet = TileSet.from(regions);
        TileMap map = new TileMap(3, 2, 10, 20);
        TileLayer base = map.addLayer();
        base.tile(0, 0, 1);
        base.tile(1, 0, 99);
        base.tile(2, 1, 2);
        map.addLayer().tile(1, 1, 1).visible(false);
        RecordingBatch batch = new RecordingBatch();

        int drawn = new TileMapRenderer().render(map, tileSet, batch, 5.0f, 7.0f);

        assertEquals(2, drawn);
        assertEquals(2, batch.drawCount);
        assertSame(regions[0][0], batch.regions[0]);
        assertEquals(5.0f, batch.x[0]);
        assertEquals(7.0f, batch.y[0]);
        assertEquals(10.0f, batch.width[0]);
        assertEquals(20.0f, batch.height[0]);
        assertSame(regions[0][1], batch.regions[1]);
        assertEquals(25.0f, batch.x[1]);
        assertEquals(27.0f, batch.y[1]);
    }

    @Test
    void rendererClampsToVisibleWorldRectangle() {
        TestTexture texture = new TestTexture(48, 16);
        TextureRegion[][] regions = TextureRegion.split(texture, 16, 16);
        TileSet tileSet = TileSet.from(regions);
        TileMap map = new TileMap(5, 4, 10, 20);
        TileLayer base = map.addLayer();
        int tileId = 1;
        for (int y = 0; y < map.height(); y++) {
            for (int x = 0; x < map.width(); x++) {
                base.tile(x, y, 1 + tileId++ % 3);
            }
        }
        RecordingBatch batch = new RecordingBatch();

        int drawn = new TileMapRenderer().render(map, tileSet, batch, 100.0f, 200.0f,
                109.0f, 219.0f, 21.0f, 21.0f);

        assertEquals(6, drawn);
        assertEquals(6, batch.drawCount);
        assertEquals(100.0f, batch.x[0]);
        assertEquals(200.0f, batch.y[0]);
        assertEquals(110.0f, batch.x[1]);
        assertEquals(200.0f, batch.y[1]);
        assertEquals(120.0f, batch.x[2]);
        assertEquals(200.0f, batch.y[2]);
        assertEquals(100.0f, batch.x[3]);
        assertEquals(220.0f, batch.y[3]);
        assertEquals(110.0f, batch.x[4]);
        assertEquals(220.0f, batch.y[4]);
        assertEquals(120.0f, batch.x[5]);
        assertEquals(220.0f, batch.y[5]);
    }

    @Test
    void rendererSkipsEmptyVisibleRectanglesAndRejectsNonFiniteBounds() {
        TestTexture texture = new TestTexture(16, 16);
        TileSet tileSet = TileSet.from(TextureRegion.split(texture, 16, 16));
        TileMap map = new TileMap(2, 2, 10, 10);
        map.addLayer().fill(1);
        RecordingBatch batch = new RecordingBatch();
        TileMapRenderer renderer = new TileMapRenderer();

        assertEquals(0, renderer.render(map, tileSet, batch, 0, 0, 0, 0, 0, 10));
        assertEquals(0, renderer.render(map, tileSet, batch, 0, 0, 50, 50, 5, 5));
        assertEquals(0, batch.drawCount);
        assertThrows(FdxException.class, () -> renderer.render(map, tileSet, batch, 0, 0,
                Float.NaN, 0, 1, 1));
        assertThrows(FdxException.class, () -> renderer.render(map, tileSet, batch, 0, 0,
                0, 0, Float.POSITIVE_INFINITY, 1));
        assertThrows(FdxException.class, () -> renderer.render(map, tileSet, batch,
                Float.NEGATIVE_INFINITY, 0));
    }

    private static final class RecordingBatch implements Batch2D {
        private final TextureRegion[] regions = new TextureRegion[8];
        private final float[] x = new float[8];
        private final float[] y = new float[8];
        private final float[] width = new float[8];
        private final float[] height = new float[8];
        private int drawCount;

        @Override
        public void begin() {
        }

        @Override
        public void begin(LoadOp loadOp) {
        }

        @Override
        public void begin(RenderPass pass) {
        }

        @Override
        public Batch2D color(float red, float green, float blue, float alpha) {
            return this;
        }

        @Override
        public Batch2D viewport(int width, int height) {
            return this;
        }

        @Override
        public void draw(Texture texture, float x, float y, float width, float height) {
        }

        @Override
        public void draw(Texture texture, float x, float y, float width, float height,
                float originX, float originY, float rotationDegrees) {
        }

        @Override
        public void draw(TextureRegion region, float x, float y, float width, float height) {
            regions[drawCount] = region;
            this.x[drawCount] = x;
            this.y[drawCount] = y;
            this.width[drawCount] = width;
            this.height[drawCount] = height;
            drawCount++;
        }

        @Override
        public void draw(TextureRegion region, float x, float y, float width, float height,
                float originX, float originY, float rotationDegrees) {
            draw(region, x, y, width, height);
        }

        @Override
        public void draw(TextureRegion region, float[] centerX, float[] centerY, int count,
                float width, float height, float originX, float originY, float rotationDegrees) {
        }

        @Override
        public void end() {
        }

        @Override
        public void dispose() {
        }

        @Override
        public boolean isDisposed() {
            return false;
        }
    }

    private static final class TestTexture implements Texture {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test");
        private final int width;
        private final int height;

        TestTexture(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public TextureFormat format() {
            return TextureFormat.RGBA8_UNORM;
        }

        @Override
        public TextureUsage usage() {
            return TextureUsage.SAMPLED;
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        public <T> T as() {
            return null;
        }

        @Override
        public void dispose() {
        }

        @Override
        public boolean isDisposed() {
            return false;
        }
    }
}
