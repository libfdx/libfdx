package io.github.libfdx.samples.g2d.platformer.render;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.files.DefaultFileSystem;
import io.github.libfdx.maps.MapObject;
import io.github.libfdx.maps.ObjectLayer;
import io.github.libfdx.maps.TileMap;
import io.github.libfdx.maps.tiled.TiledMapLoader;
import io.github.libfdx.samples.g2d.platformer.PlatformerLevel;
import io.github.libfdx.samples.g2d.platformer.Trail;
import io.github.libfdx.samples.g2d.platformer.input.TestPlatformerInput;
import java.util.ArrayList;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

final class PlatformerFootingTest {
    @ParameterizedTest
    @EnumSource(Trail.class)
    void authoredFootprintsHaveSolidSupport(Trail trail) {
        var assets = new DefaultAssetManager(new DefaultFileSystem().classpathResourceResolver(
                path -> getClass().getResourceAsStream("/" + path)));
        try {
            TiledMapLoader.register(assets);
            var lease = assets.acquire(AssetDescriptor.of(trail.mapPath(), TileMap.class));
            try {
                while (!lease.future().isDone()) assets.update(4, Long.MAX_VALUE);
                TileMap map = lease.future().get();
                var terrain = (ObjectLayer) map.mapLayer(0);
                var actors = (ObjectLayer) map.mapLayer(1);
                var occupied = new ArrayList<Footprint>();
                int siteIndex = 0;
                for (int i = 0; i < actors.objectCount(); i++) {
                    MapObject object = actors.object(i);
                    float x = object.x(), y = object.y();
                    if (object.className().equals("enemy") || object.className().equals("hopper")) {
                        seated(terrain, object,
                                object.properties().get("minX").doubleValue() - 20.25,
                                object.properties().get("maxX").doubleValue() + 20.25, y - 8, true);
                        continue;
                    }
                    Footprint art = switch (object.className()) {
                        case "grove" -> new Footprint(object, x - 28, x + 23, y, 64);
                        case "winch" -> new Footprint(object, x - 18, x + 19, y, 39);
                        case "mill" -> new Footprint(object, x - 35, x + 33, y, 44);
                        case "portal" -> new Footprint(object, x - 26, x + 26, y, 44);
                        case "site" -> {
                            int height = siteIndex < AuthoredPlatformerRenderer.HOUSE_SITE_INDEX ? 39 : AuthoredPlatformerRenderer.HOUSE_SIZE;
                            int right = AuthoredPlatformerRenderer.siteRight(siteIndex++);
                            yield new Footprint(object, x - 29, x + right, y - 12, height);
                        }
                        case "dial" -> new Footprint(object, x - 13, x + 13, y, 44);
                        case "bell-gate", "lamp-gate" -> new Footprint(object, x - 21, x + 21, y, 94);
                        case "hazard" -> new Footprint(object, x - 10, x + 10, y - 5, 12);
                        case "goal" -> new Footprint(object, x - 70, x + 70, y - 19, trail.isLast() ? 113 : 81);
                        default -> null;
                    };
                    if (art == null) continue;
                    boolean canStandOnDeck = object.className().equals("site") || object.className().equals("dial");
                    seated(terrain, object, art.left, art.right, art.feet, canStandOnDeck);
                    for (Footprint other : occupied) art.checkClear(other.left, other.right, other.object);
                    occupied.add(art);
                }
                checkPatrols(actors, terrain, occupied);

                var ground = new PlatformerGround(null);
                ground.prepare(PlatformerLevel.create(new TestPlatformerInput(), map));
                for (int i = 0; i < terrain.objectCount(); i++) {
                    MapObject deck = terrain.object(i);
                    if (deck.className().equals("solid")) continue;
                    float left = deck.x(), right = left + deck.width();
                    boolean lift = deck.className().equals("lift");
                    if (lift) {
                        float beam = deck.y() + 44;
                        float near = ground.liftMast(left, false, beam), far = ground.liftMast(right, true, beam);
                        seated(terrain, deck, near, near + 5, ground.pierTop(near, 5, beam), false);
                        seated(terrain, deck, far, far + 5, ground.pierTop(far, 5, beam), false);
                        left = Math.min(near, left);
                        right = Math.max(far + 5, right);
                    } else {
                        float width = switch (trail) {
                            case HOLLOW -> 26;
                            case SUMMIT -> 11;
                            case QUARRY, FORGE -> 9;
                            default -> 7;
                        };
                        seated(terrain, deck, left, left + 3 + width, ground.pierTop(left + 3, width, deck.y() - 5), false);
                        seated(terrain, deck, right - 3 - width, right, ground.pierTop(right - 3 - width, width, deck.y() - 5), false);
                    }
                    for (Footprint art : occupied) {
                        if (!lift && art.feet == deck.y() && art.left >= left && art.right <= right) continue;
                        art.checkClear(left, right, deck);
                    }
                    occupied.add(new Footprint(deck, left, right, Float.NaN, 0));
                }
            } finally {
                lease.dispose();
            }
        } finally {
            assets.dispose();
        }
    }

    private static void checkPatrols(ObjectLayer actors, ObjectLayer terrain, ArrayList<Footprint> landmarks) {
        for (int i = 0; i < actors.objectCount(); i++) {
            MapObject enemy = actors.object(i);
            boolean moth = enemy.className().equals("moth");
            boolean hopper = enemy.className().equals("hopper");
            if (!moth && !hopper && !enemy.className().equals("enemy")) continue;
            // Include bobbing wings, a hopper at its apex and the widened defeat animation.
            float bottom = enemy.y() - (moth ? 31 : 8.5f);
            float top = enemy.y() + (moth ? 31 : hopper ? 56 : 22);
            float halfWidth = moth ? 22.3f : 20.25f;
            float left = (float)enemy.properties().get("minX").doubleValue() - halfWidth;
            float right = (float)enemy.properties().get("maxX").doubleValue() + halfWidth;
            if (moth) {
                for (int j = 0; j < terrain.objectCount(); j++) {
                    MapObject deck = terrain.object(j);
                    if (deck.className().equals("bridge") && bottom < deck.y() + 3) {
                        var structure = new Footprint(deck, deck.x(), deck.x() + deck.width(), deck.y(), 0);
                        structure.checkClear(left, right, enemy);
                    }
                }
            }
            for (Footprint art : landmarks) {
                if (top > art.feet - 3 && bottom < art.feet + art.height + 3) {
                    art.checkClear(left, right, enemy);
                }
            }
        }
    }

    private record Footprint(MapObject object, float left, float right, float feet, int height) {
        void checkClear(float otherLeft, float otherRight, MapObject other) {
            assertTrue(right + 3 <= otherLeft || otherRight + 3 <= left,
                    () -> object.className() + " " + object.id() + " overlaps " + other.className() + " " + other.id());
        }

    }

    private static void seated(ObjectLayer terrain, MapObject object, double left, double right, float feet,
            boolean allowDeck) {
        for (int i = 0; i < terrain.objectCount(); i++) {
            MapObject surface = terrain.object(i);
            if (!surface.className().equals("solid") && !(allowDeck && surface.className().equals("bridge"))) continue;
            if (Math.abs(surface.y() - feet) < .001 && left >= surface.x() + PlatformerGround.EDGE_INSET - .001
                    && right <= surface.x() + surface.width() - PlatformerGround.EDGE_INSET + .001) return;
        }
        fail(object.className() + " " + object.id() + " needs support from " + left + " to " + right + " at " + feet);
    }

}
