package io.github.libfdx.samples.ecs.platformer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.ecs.entity.EntityList;
import io.github.libfdx.samples.ecs.platformer.component.BoundsComponent;
import io.github.libfdx.samples.ecs.platformer.component.CollectibleComponent;
import io.github.libfdx.samples.ecs.platformer.component.EnemyComponent;
import io.github.libfdx.samples.ecs.platformer.component.GoalComponent;
import io.github.libfdx.samples.ecs.platformer.component.HazardComponent;
import io.github.libfdx.samples.ecs.platformer.component.LevelStateComponent;
import io.github.libfdx.samples.ecs.platformer.component.PlayerComponent;
import io.github.libfdx.samples.ecs.platformer.component.PositionComponent;
import io.github.libfdx.samples.ecs.platformer.component.SolidComponent;
import io.github.libfdx.samples.ecs.platformer.component.VelocityComponent;
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
        float startX = world.require(player, PositionComponent.class).x;

        input.right = true;
        world.update(FRAME);

        assertTrue(world.require(player, PositionComponent.class).x > startX);
        assertTrue(world.require(player, VelocityComponent.class).x > 0.0f);
    }

    @Test
    void cameraFollowsPlayerInsideLevelBounds() {
        World world = PlatformerWorldFactory.create(new TestPlatformerInput(), null);
        LevelStateComponent state = world.mapper(LevelStateComponent.class).componentAt(0);
        int player = playerEntity(world);
        world.require(player, PositionComponent.class).x = 1.50f;

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

        assertFalse(world.require(player, PlayerComponent.class).onGround);
        float firstVelocity = world.require(player, VelocityComponent.class).y;
        assertTrue(firstVelocity > 0.0f);

        world.update(FRAME);

        assertTrue(world.require(player, VelocityComponent.class).y < firstVelocity);
    }

    @Test
    void fallingPlayerLandsOnSolidTiles() {
        World world = PlatformerWorldFactory.create(new TestPlatformerInput(), null);
        int player = playerEntity(world);
        PositionComponent position = world.require(player, PositionComponent.class);
        VelocityComponent velocity = world.require(player, VelocityComponent.class);
        PlayerComponent playerComponent = world.require(player, PlayerComponent.class);
        position.x = PlatformerConstants.PLAYER_START_X;
        position.y = -0.12f;
        velocity.y = -0.2f;
        playerComponent.onGround = false;

        for (int i = 0; i < 90; i++) {
            world.update(FRAME);
        }

        assertTrue(playerComponent.onGround);
        assertEquals(PlatformerConstants.PLAYER_START_Y, position.y, 0.001f);
        assertTrue(world.entities(world.matcher().all(SolidComponent.class)).size() > 0);
    }

    @Test
    void collectibleUpdatesProgressAndRestartRestoresIt() {
        TestPlatformerInput input = new TestPlatformerInput();
        World world = PlatformerWorldFactory.create(input, null);
        LevelStateComponent state = world.mapper(LevelStateComponent.class).componentAt(0);
        int player = playerEntity(world);
        EntityList collectibles = world.entities(world.matcher().all(CollectibleComponent.class, PositionComponent.class, BoundsComponent.class));
        int collectible = collectibles.entityAt(0);
        PositionComponent collectiblePosition = world.require(collectible, PositionComponent.class);
        PositionComponent playerPosition = world.require(player, PositionComponent.class);
        playerPosition.x = collectiblePosition.x;
        playerPosition.y = collectiblePosition.y;

        world.update(FRAME);

        assertEquals(1, state.coinsCollected);
        assertTrue(world.require(collectible, CollectibleComponent.class).collected);

        state.gameOver = true;
        input.restart = true;
        world.update(FRAME);

        assertFalse(state.gameOver);
        assertEquals(0, state.coinsCollected);
        assertFalse(world.require(collectible, CollectibleComponent.class).collected);
    }

    @Test
    void hazardTriggersGameOverAndRestartResetsPlayerAndEnemies() {
        TestPlatformerInput input = new TestPlatformerInput();
        World world = PlatformerWorldFactory.create(input, null);
        LevelStateComponent state = world.mapper(LevelStateComponent.class).componentAt(0);
        int player = playerEntity(world);
        EntityList hazards = world.entities(world.matcher().all(HazardComponent.class, PositionComponent.class, BoundsComponent.class));
        int hazard = hazards.entityAt(0);
        PositionComponent hazardPosition = world.require(hazard, PositionComponent.class);
        PositionComponent playerPosition = world.require(player, PositionComponent.class);
        playerPosition.x = hazardPosition.x;
        playerPosition.y = hazardPosition.y;

        world.update(FRAME);

        assertTrue(state.gameOver);
        EntityList enemies = world.entities(world.matcher().all(EnemyComponent.class, PositionComponent.class, VelocityComponent.class));
        int enemy = enemies.entityAt(0);
        PositionComponent enemyPosition = world.require(enemy, PositionComponent.class);
        enemyPosition.x += 0.20f;

        input.restart = true;
        world.update(FRAME);

        assertFalse(state.gameOver);
        assertEquals(PlatformerConstants.PLAYER_START_X, playerPosition.x, 0.001f);
        assertEquals(world.require(enemy, EnemyComponent.class).startX, enemyPosition.x, 0.001f);
    }

    @Test
    void goalCompletesLevel() {
        World world = PlatformerWorldFactory.create(new TestPlatformerInput(), null);
        LevelStateComponent state = world.mapper(LevelStateComponent.class).componentAt(0);
        int player = playerEntity(world);
        EntityList goals = world.entities(world.matcher().all(GoalComponent.class, PositionComponent.class, BoundsComponent.class));
        PositionComponent goalPosition = world.require(goals.entityAt(0), PositionComponent.class);
        PositionComponent playerPosition = world.require(player, PositionComponent.class);
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
        ComponentMapper<PlayerComponent> players = world.mapper(PlayerComponent.class);
        return players.entityAt(0);
    }
}
