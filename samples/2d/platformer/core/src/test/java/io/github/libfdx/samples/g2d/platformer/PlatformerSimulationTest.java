package io.github.libfdx.samples.g2d.platformer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.samples.g2d.platformer.input.TestPlatformerInput;
import io.github.libfdx.samples.g2d.platformer.render.PlatformerTextures;
import org.junit.jupiter.api.Test;

final class PlatformerSimulationTest {
    private static final float FRAME = 1.0f / 60.0f;

    @Test
    void rightInputMovesPlayer() {
        TestPlatformerInput input = new TestPlatformerInput();
        PlatformerGame game = PlatformerLevel.create(input);
        float startX = game.player().x();

        input.right = true;
        game.update(FRAME);

        assertTrue(game.player().x() > startX);
        assertTrue(game.playerVelocityX() > 0.0f);
    }

    @Test
    void cameraFollowsPlayerInsideLevelBounds() {
        PlatformerGame game = PlatformerLevel.create(new TestPlatformerInput());
        game.player().position(1.50f, game.player().y());

        game.update(1.0f);

        assertTrue(game.cameraX() > 0.0f);
        assertTrue(game.cameraX() <= PlatformerConstants.CAMERA_MAX_X);
    }

    @Test
    void jumpOnlyStartsFromGround() {
        TestPlatformerInput input = new TestPlatformerInput();
        PlatformerGame game = PlatformerLevel.create(input);
        input.jump = true;

        game.update(FRAME);

        assertFalse(game.playerOnGround());
        float firstVelocity = game.playerVelocityY();
        assertTrue(firstVelocity > 0.0f);

        game.update(FRAME);

        assertTrue(game.playerVelocityY() < firstVelocity);
    }

    @Test
    void zeroAndInvalidDeltaPreserveGroundedStateAndTheNextJumpEdge() {
        TestPlatformerInput input = new TestPlatformerInput();
        PlatformerGame game = PlatformerLevel.create(input);

        game.update(0.0f);
        game.update(Float.NaN);

        assertTrue(game.playerOnGround());
        assertEquals(PlatformerConstants.PLAYER_START_Y, game.player().y(), 0.001f);

        input.jump = true;
        game.update(FRAME);

        assertFalse(game.playerOnGround());
        assertTrue(game.playerVelocityY() > 0.0f);
    }

    @Test
    void fallingPlayerLandsOnSolidTiles() {
        PlatformerGame game = PlatformerLevel.create(new TestPlatformerInput());
        game.player().position(PlatformerConstants.PLAYER_START_X, -0.12f);

        for (int i = 0; i < 90; i++) {
            game.update(FRAME);
        }

        assertTrue(game.playerOnGround());
        assertEquals(PlatformerConstants.PLAYER_START_Y, game.player().y(), 0.001f);
        assertTrue(game.solidCount() > 0);
    }

    @Test
    void collectibleUpdatesProgressAndRestartRestoresIt() {
        TestPlatformerInput input = new TestPlatformerInput();
        PlatformerGame game = PlatformerLevel.create(input);
        PlatformerGame.Sprite collectible = game.collectibleAt(0);
        game.player().position(collectible.x(), collectible.y());

        game.update(FRAME);

        assertEquals(1, game.coinsCollected());
        assertTrue(collectible.collected());

        PlatformerGame.Sprite hazard = game.hazardAt(0);
        game.player().position(hazard.x(), hazard.y());
        game.update(FRAME);
        assertTrue(game.gameOver());

        input.restart = true;
        game.update(FRAME);

        assertFalse(game.gameOver());
        assertEquals(0, game.coinsCollected());
        assertFalse(collectible.collected());
    }

    @Test
    void hazardTriggersGameOverAndRestartResetsPlayerAndEnemies() {
        TestPlatformerInput input = new TestPlatformerInput();
        PlatformerGame game = PlatformerLevel.create(input);
        PlatformerGame.Sprite hazard = game.hazardAt(0);
        game.player().position(hazard.x(), hazard.y());

        game.update(FRAME);

        assertTrue(game.gameOver());
        PlatformerGame.Enemy enemy = game.enemyAt(0);
        enemy.sprite().position(enemy.sprite().x() + 0.20f, enemy.sprite().y());

        input.restart = true;
        game.update(FRAME);

        assertFalse(game.gameOver());
        assertEquals(PlatformerConstants.PLAYER_START_X, game.player().x(), 0.001f);
        assertEquals(enemy.startX(), enemy.sprite().x(), 0.001f);
    }

    @Test
    void goalCompletesLevel() {
        PlatformerGame game = PlatformerLevel.create(new TestPlatformerInput());
        PlatformerGame.Sprite goal = game.goalAt(0);
        game.player().position(goal.x(), goal.y());

        game.update(FRAME);

        assertTrue(game.completed());
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
}
