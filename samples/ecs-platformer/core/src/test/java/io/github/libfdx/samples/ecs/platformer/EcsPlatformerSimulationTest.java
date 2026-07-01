package io.github.libfdx.samples.ecs.platformer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.ecs.entity.EntityList;
import io.github.libfdx.samples.ecs.platformer.component.Bounds;
import io.github.libfdx.samples.ecs.platformer.component.Collectible;
import io.github.libfdx.samples.ecs.platformer.component.Enemy;
import io.github.libfdx.samples.ecs.platformer.component.Goal;
import io.github.libfdx.samples.ecs.platformer.component.Hazard;
import io.github.libfdx.samples.ecs.platformer.component.LevelState;
import io.github.libfdx.samples.ecs.platformer.component.Player;
import io.github.libfdx.samples.ecs.platformer.component.Position;
import io.github.libfdx.samples.ecs.platformer.component.Solid;
import io.github.libfdx.samples.ecs.platformer.component.Velocity;
import io.github.libfdx.samples.ecs.platformer.input.TestPlatformerInput;
import io.github.libfdx.samples.ecs.platformer.render.PlatformerTextures;
import io.github.libfdx.samples.ecs.platformer.world.PlatformerWorldFactory;
import org.junit.jupiter.api.Test;

final class EcsPlatformerSimulationTest {
    private static final float FRAME = 1.0f / 60.0f;

    @Test
    void rightInputMovesPlayerThroughEcsSystems() {
        TestPlatformerInput input = new TestPlatformerInput();
        World world = PlatformerWorldFactory.create(input, null);
        int player = playerEntity(world);
        float startX = world.require(player, Position.class).x;

        input.right = true;
        world.update(FRAME);

        assertTrue(world.require(player, Position.class).x > startX);
        assertTrue(world.require(player, Velocity.class).x > 0.0f);
    }

    @Test
    void cameraFollowsPlayerInsideLevelBounds() {
        World world = PlatformerWorldFactory.create(new TestPlatformerInput(), null);
        LevelState state = world.mapper(LevelState.class).componentAt(0);
        int player = playerEntity(world);
        world.require(player, Position.class).x = 1.50f;

        world.update(1.0f);

        assertTrue(state.cameraX > 0.0f);
        assertTrue(state.cameraX <= PlatformerConstants.CAMERA_MAX_X);
    }

    @Test
    void jumpOnlyStartsFromGround() {
        TestPlatformerInput input = new TestPlatformerInput();
        World world = PlatformerWorldFactory.create(input, null);
        int player = playerEntity(world);
        input.jump = true;

        world.update(FRAME);

        assertFalse(world.require(player, Player.class).onGround);
        float firstVelocity = world.require(player, Velocity.class).y;
        assertTrue(firstVelocity > 0.0f);

        world.update(FRAME);

        assertTrue(world.require(player, Velocity.class).y < firstVelocity);
    }

    @Test
    void fallingPlayerLandsOnSolidTiles() {
        World world = PlatformerWorldFactory.create(new TestPlatformerInput(), null);
        int player = playerEntity(world);
        Position position = world.require(player, Position.class);
        Velocity velocity = world.require(player, Velocity.class);
        Player playerComponent = world.require(player, Player.class);
        position.x = PlatformerConstants.PLAYER_START_X;
        position.y = -0.12f;
        velocity.y = -0.2f;
        playerComponent.onGround = false;

        for (int i = 0; i < 90; i++) {
            world.update(FRAME);
        }

        assertTrue(playerComponent.onGround);
        assertEquals(PlatformerConstants.PLAYER_START_Y, position.y, 0.001f);
        assertTrue(world.entities(world.matcher().all(Solid.class)).size() > 0);
    }

    @Test
    void collectibleUpdatesProgressAndRestartRestoresIt() {
        TestPlatformerInput input = new TestPlatformerInput();
        World world = PlatformerWorldFactory.create(input, null);
        LevelState state = world.mapper(LevelState.class).componentAt(0);
        int player = playerEntity(world);
        EntityList collectibles = world.entities(world.matcher().all(Collectible.class, Position.class, Bounds.class));
        int collectible = collectibles.entityAt(0);
        Position collectiblePosition = world.require(collectible, Position.class);
        Position playerPosition = world.require(player, Position.class);
        playerPosition.x = collectiblePosition.x;
        playerPosition.y = collectiblePosition.y;

        world.update(FRAME);

        assertEquals(1, state.coinsCollected);
        assertTrue(world.require(collectible, Collectible.class).collected);

        state.gameOver = true;
        input.restart = true;
        world.update(FRAME);

        assertFalse(state.gameOver);
        assertEquals(0, state.coinsCollected);
        assertFalse(world.require(collectible, Collectible.class).collected);
    }

    @Test
    void hazardTriggersGameOverAndRestartResetsPlayerAndEnemies() {
        TestPlatformerInput input = new TestPlatformerInput();
        World world = PlatformerWorldFactory.create(input, null);
        LevelState state = world.mapper(LevelState.class).componentAt(0);
        int player = playerEntity(world);
        EntityList hazards = world.entities(world.matcher().all(Hazard.class, Position.class, Bounds.class));
        int hazard = hazards.entityAt(0);
        Position hazardPosition = world.require(hazard, Position.class);
        Position playerPosition = world.require(player, Position.class);
        playerPosition.x = hazardPosition.x;
        playerPosition.y = hazardPosition.y;

        world.update(FRAME);

        assertTrue(state.gameOver);
        EntityList enemies = world.entities(world.matcher().all(Enemy.class, Position.class, Velocity.class));
        int enemy = enemies.entityAt(0);
        Position enemyPosition = world.require(enemy, Position.class);
        enemyPosition.x += 0.20f;

        input.restart = true;
        world.update(FRAME);

        assertFalse(state.gameOver);
        assertEquals(PlatformerConstants.PLAYER_START_X, playerPosition.x, 0.001f);
        assertEquals(world.require(enemy, Enemy.class).startX, enemyPosition.x, 0.001f);
    }

    @Test
    void goalCompletesLevel() {
        World world = PlatformerWorldFactory.create(new TestPlatformerInput(), null);
        LevelState state = world.mapper(LevelState.class).componentAt(0);
        int player = playerEntity(world);
        EntityList goals = world.entities(world.matcher().all(Goal.class, Position.class, Bounds.class));
        Position goalPosition = world.require(goals.entityAt(0), Position.class);
        Position playerPosition = world.require(player, Position.class);
        playerPosition.x = goalPosition.x;
        playerPosition.y = goalPosition.y;

        world.update(FRAME);

        assertTrue(state.completed);
    }

    @Test
    void textureRegionIdsStayInsideKenneySheets() {
        assertTrue(PlatformerTextures.validRegionId(PlatformerConstants.REGION_GRASS_SINGLE));
        assertTrue(PlatformerTextures.validRegionId(PlatformerConstants.REGION_COIN));
        assertTrue(PlatformerTextures.validRegionId(PlatformerConstants.REGION_GEM));
        assertTrue(PlatformerTextures.validRegionId(PlatformerConstants.REGION_SPIKE));
        assertTrue(PlatformerTextures.validRegionId(PlatformerConstants.REGION_DOOR));
        assertTrue(PlatformerTextures.validRegionId(PlatformerConstants.REGION_PLAYER_IDLE));
        assertTrue(PlatformerTextures.validRegionId(PlatformerConstants.REGION_ENEMY_WALKER));
        assertTrue(PlatformerTextures.validRegionId(PlatformerConstants.REGION_CLOUD_LEFT));
        assertTrue(PlatformerTextures.validRegionId(PlatformerConstants.REGION_BACKGROUND_SKY));
        assertFalse(PlatformerTextures.validRegionId(PlatformerConstants.REGION_COUNT));
    }

    private static int playerEntity(World world) {
        ComponentMapper<Player> players = world.mapper(Player.class);
        return players.entityAt(0);
    }
}
