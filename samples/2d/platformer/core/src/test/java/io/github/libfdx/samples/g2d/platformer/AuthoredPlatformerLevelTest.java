package io.github.libfdx.samples.g2d.platformer;

import io.github.libfdx.assets.*;
import io.github.libfdx.files.DefaultFileSystem;
import io.github.libfdx.maps.*;
import io.github.libfdx.maps.tiled.TiledMapLoader;
import io.github.libfdx.samples.g2d.platformer.input.TestPlatformerInput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AuthoredPlatformerLevelTest {
    @Test
    void bothAuthoredLevelsHaveCollisionSpawnsAndRestartWithoutMutatingCachedData() {
        for (String name : new String[]{"meadow", "moonrise"}) {
            DefaultAssetManager assets = new DefaultAssetManager(new DefaultFileSystem().classpathResourceResolver(
                    path -> AuthoredPlatformerLevelTest.class.getResourceAsStream("/"+path)));
            try {
                TiledMapLoader.register(assets);
                var lease = assets.acquire(AssetDescriptor.of("levels/"+name+".tmj", TileMap.class));
                for (int frame = 0; frame < 100 && !lease.future().isDone(); frame++) assets.update(1, Long.MAX_VALUE);
                assertTrue(lease.future().isDone());
                TileMap map = lease.future().get(); TestPlatformerInput input = new TestPlatformerInput();
                PlatformerGame game = PlatformerLevel.create(input, map);
                assertEquals(9, game.solidCount()); assertEquals(12, game.collectibleCount());
                assertEquals(1, game.enemyCount()); assertEquals(1, game.goalCount());
                float spawnX = game.player().x(), spawnY = game.player().y();
                assertEquals(36, PlatformerLevel.pixel(spawnX), .0001);
                assertEquals(48, PlatformerLevel.pixel(spawnY), .0001);
                for (int i = 0; i < 120; i++) { game.update(1f/60); }
                assertTrue(game.playerOnGround()); assertFalse(game.gameOver());
                input.right = true;
                for (int i = 0; i < 12; i++) { game.update(1f/60); }
                assertEquals(spawnX + 12 * PlatformerConstants.PLAYER_MOVE_SPEED / 60,
                        game.player().x(), .00001, name + ": ground contact must not become a wall");
                assertTrue(game.playerOnGround());
                input.right = false; input.left = true;
                for (int i = 0; i < 12; i++) { game.update(1f/60); }
                assertEquals(spawnX, game.player().x(), .00001, name + ": walking left stays on the floor");
                input.left = false;
                input.right = true;
                for (int i = 0; i < 90; i++) { game.update(1f/60); }
                int firstPlatformX = name.equals("meadow") ? 108 : 72;
                assertEquals(firstPlatformX - 8, PlatformerLevel.pixel(game.player().x()), .001,
                        "The raised platform still blocks horizontal movement");
                input.jump = true;
                for (int i = 0; i < 24; i++) { game.update(1f/60); }
                input.jump = input.right = false;
                for (int i = 0; i < 90; i++) { game.update(1f/60); }
                assertTrue(game.playerOnGround());
                assertEquals(84, PlatformerLevel.pixel(game.player().y()), .001,
                        "A normal jump can land on the authored platform");
                float platformX = game.player().x();
                input.right = true;
                for (int i = 0; i < 5; i++) { game.update(1f/60); }
                assertEquals(platformX + 5 * PlatformerConstants.PLAYER_MOVE_SPEED / 60,
                        game.player().x(), .00001, "Walking on a raised platform does not snap sideways");
                input.right = false;
                game.consumeEvents();
                game.restart(); assertEquals(spawnX, game.player().x()); assertEquals(spawnY, game.player().y());
                assertEquals(PlatformerGame.RESTART, game.consumeEvents()); assertEquals(0, game.consumeEvents());
                assertEquals(36, map.findObject(100).x()); // Authored data remains borrowed/unchanged.
                assertEquals("audio/"+name+".wav", map.properties().get("music").stringValue());
            } finally { assets.dispose(); }
        }
    }
}
