package io.github.libfdx.graphics.g2d;

import io.github.libfdx.graphics.*;
import io.github.libfdx.maps.*;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TileSamplingTest {
    @Test
    void filterFootprintsStayInsideUnpaddedTilesIncludingSingleTexels() {
        Texture texture = texture(384, 96);
        for (int size : new int[] {1, 2, 32}) {
            TextureRegion original = new TextureRegion(texture, 64, 32, size, size);
            TextureRegion sample = original.tileSamplingRegion();
            assertSame(texture, sample.texture());
            assertEquals(original.x(), sample.x()); assertEquals(original.y(), sample.y());
            assertEquals(size, sample.width()); assertEquals(size, sample.height());
            assertEquals(64f / 384, original.u()); // Ordinary sprite UVs are untouched.
            for (int step = 0; step <= 1000; step++) {
                double t = step / 1000.0;
                double x = (sample.u() * (1-t) + sample.u2() * t) * 384;
                double y = (sample.v() * (1-t) + sample.v2() * t) * 96;
                // Linear footprints cannot reach a neighboring tile; nearest is contained too.
                assertTrue(x >= 64.5 - 1e-5 && x <= 64 + size - .5 + 1e-5);
                assertTrue(y >= 32.5 - 1e-5 && y <= 32 + size - .5 + 1e-5);
            }
            if (size == 1) {
                assertEquals(sample.u(), sample.u2()); assertEquals(sample.v(), sample.v2());
            }
        }
    }

    @Test
    void renderedTextureOriginsKeepTheirDirection() {
        Texture texture = texture(64, 32);
        TextureRegion top = TextureRegion.rendered(texture, TextureOrigin.TOP_LEFT).tileSamplingRegion();
        TextureRegion bottom = TextureRegion.rendered(texture, TextureOrigin.BOTTOM_LEFT).tileSamplingRegion();
        assertEquals(top.v(), bottom.v2()); assertEquals(top.v2(), bottom.v());
        assertEquals(.5f / 32, top.v()); assertEquals(31.5f / 32, top.v2());
    }

    @Test
    void denseChunksObjectsFlipsAndAnimationUseCachedSamplingRegions() {
        Texture texture = texture(96, 32);
        TextureRegion first = new TextureRegion(texture, 0, 0, 32, 32);
        TextureRegion second = new TextureRegion(texture, 32, 0, 32, 32);
        TileSet tiles = new TileSet().region(1, first).atlasRegion(2, second, 0, 0);
        tiles.animation(1, new TileAnimation(new int[] {0, 1}, new int[] {100, 100}), 1);
        var dense = new io.github.libfdx.maps.TileMap(8, 1, 32, 32);
        var cells = dense.addLayer();
        var sparse = io.github.libfdx.maps.TileMap.infinite(32, 32);
        var chunks = new ChunkedTileLayer(1, 8);
        var chunk = new TileChunk(0, 0, 8, 1); chunks.put(chunk); sparse.addChunkedLayer(chunks);
        var objects = new io.github.libfdx.maps.TileMap(8, 1, 32, 32);
        MapObject[] values = new MapObject[8];
        for (int i = 0; i < 8; i++) {
            cells.tile(i, 0, 1, i); chunk.tile(i, 0, 1, i);
            values[i] = new MapObject(i+1, "", "", MapObject.Shape.TILE, i*32, 0,
                    32, 32, 15, true, 1, 1, i, new float[] {0,0,0,32,32,32,32,0}, new MapProperties());
        }
        objects.addObjectLayer(new ObjectLayer(values, false));
        TextureRegion[] seen = new TextureRegion[8];
        int[] count = {0};
        Batch2D batch = (Batch2D) Proxy.newProxyInstance(Batch2D.class.getClassLoader(), new Class<?>[] {Batch2D.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("draw")) {
                        seen[count[0]++] = (TextureRegion) args[0];
                        assertEquals(32f, args[3]); assertEquals(32f, args[4]);
                    }
                    return method.getReturnType() == Batch2D.class ? proxy : null;
                });
        TileMapRenderer renderer = new TileMapRenderer();
        TextureRegion[] cached = new TextureRegion[2];
        for (int frame : new int[] {0, 1, 0, 1}) {
            tiles.animationTime(frame * 100);
            assertSame(frame == 0 ? first : second, tiles.region(1));
            for (var map : new io.github.libfdx.maps.TileMap[] {dense, sparse, objects}) {
                count[0] = 0;
                assertEquals(8, renderer.render(map, tiles, batch, .125f, -.375f, -64, -64, 512, 128));
                for (TextureRegion sample : seen) {
                    assertEquals((frame * 32 + .5f) / 96, sample.u(), 1e-7f);
                    assertEquals((frame * 32 + 31.5f) / 96, sample.u2(), 1e-7f);
                    if (cached[frame] == null) cached[frame] = sample;
                    assertSame(cached[frame], sample);
                }
            }
        }
    }

    private static Texture texture(int width, int height) {
        return (Texture) Proxy.newProxyInstance(Texture.class.getClassLoader(), new Class<?>[] {Texture.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "width" -> width; case "height" -> height; case "sampleCount" -> 1;
                    case "usage" -> TextureUsage.SAMPLED_RENDER_ATTACHMENT;
                    default -> throw new AssertionError(method);
                });
    }
}
