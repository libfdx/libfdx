package io.github.libfdx.samples.g2d.platformer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.samples.g2d.platformer.input.TestPlatformerInput;
import org.junit.jupiter.api.Test;

final class PlatformerSimulationTest {
    @Test
    void tapBetweenFixedStepsTriggersOneJumpAndRebindingWorks() {
        var input = new io.github.libfdx.input.DefaultInput();
        var controls = new io.github.libfdx.samples.g2d.platformer.input.BackendPlatformerInput(input);
        PlatformerGame game = PlatformerLevel.create(controls);
        input.dispatchKeyDown(io.github.libfdx.input.Key.SPACE);
        input.dispatchKeyUp(io.github.libfdx.input.Key.SPACE);
        for(int frame=0;frame<3;frame++) controls.update(640,480);
        game.update(FRAME); assertTrue(game.playerVelocityY()>0);
        float velocity=game.playerVelocityY(); game.update(FRAME); assertTrue(game.playerVelocityY()<velocity);
        var jump=controls.actions().find("jump");
        controls.actions().clearBindings(jump).bind(jump,io.github.libfdx.input.InputBinding.key(io.github.libfdx.input.Key.J));
        input.dispatchKeyDown(io.github.libfdx.input.Key.SPACE); assertFalse(controls.consumeJumpPress());
        input.dispatchKeyDown(io.github.libfdx.input.Key.J); assertTrue(controls.consumeJumpPress());
        controls.dispose(); assertTrue(controls.isDisposed());
    }
    private static final float FRAME = 1.0f / 60.0f;

    @Test
    void fixedStepClockKeepsHeldInputSimulationEqualAtDifferentRenderRates() {
        PlatformerGame slow = fixedCadence(30), fast = fixedCadence(144);
        assertEquals(slow.player().x(), fast.player().x(), 1e-7);
        assertEquals(slow.player().y(), fast.player().y(), 1e-7);
        assertEquals(slow.cameraX(), fast.cameraX(), 1e-7);
        assertEquals(slow.coinsCollected(), fast.coinsCollected());
    }

    private PlatformerGame fixedCadence(int fps) {
        TestPlatformerInput input = new TestPlatformerInput(); input.right = true;
        PlatformerGame game = PlatformerLevel.create(input);
        var clock = new io.github.libfdx.application.FixedStepClock(1.0 / 60, 8);
        for (int frame = 0; frame < fps * 2; frame++) {
            int steps = clock.advance(1.0 / fps);
            for (int step = 0; step < steps; step++) game.update((float) clock.stepSeconds());
        }
        return game;
    }

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

        assertFalse(game.completed());
        for (int i = 0; i < 30; i++) game.update(FRAME);

        assertTrue(game.playerOnGround());
        assertTrue(game.completed());
    }

    @Test
    void landingOnAnEnemyBouncesAndSideContactHasARecoveryWindow() {
        var game = new PlatformerGame(new TestPlatformerInput());
        var player = game.addSprite(0, .17f, .05f, .07f);
        game.player(player);
        var beetle = game.addSprite(0, 0, .09f, .06f);
        game.addEnemy(beetle, -.1f, .1f, .01f);
        for (int i = 0; i < 30 && !game.enemyAt(0).defeated(); i++) game.update(FRAME);
        assertTrue(game.enemyAt(0).defeated());
        assertTrue(game.playerVelocityY() > 0);
        assertEquals(PlatformerGame.MAX_HEALTH, game.health());
        assertTrue((game.consumeEvents() & PlatformerGame.STOMP) != 0);

        game.restart();
        player.position(0, 0);
        game.update(FRAME);
        assertFalse(game.enemyAt(0).defeated());
        assertEquals(PlatformerGame.MAX_HEALTH - 1, game.health());
        game.update(FRAME);
        assertEquals(PlatformerGame.MAX_HEALTH - 1, game.health());
    }

    @Test
    void warnedShotCanBeJumpedAndClearsOnRetry() {
        var input = new TestPlatformerInput();
        var game = shootingRange(input, RangedAttack.Kind.SEED);
        var shot = game.enemyAt(0).attack();
        game.addSite("Lantern", game.player().x(), game.player().y());
        int warnings = 0;
        for (int i = 0; i < 180 && !shot.flying(); i++) {
            game.update(FRAME);
            if (shot.charge() > 0) warnings++;
        }
        assertTrue(warnings >= 30);
        assertTrue(shot.flying());
        assertFalse(shot.facesLeft());
        for (int i = 0; i < 90 && shot.x() < game.player().x() - .25f; i++) game.update(FRAME);
        assertTrue(shot.flying());
        input.jump = true;
        game.update(FRAME);
        input.jump = false;
        for (int i = 0; i < 30; i++) game.update(FRAME);
        assertTrue(shot.x() > game.player().x());
        assertEquals(PlatformerGame.MAX_HEALTH, game.health());
        game.retry();
        assertFalse(shot.flying());
        assertEquals(0, shot.charge());
        for (int i = 0; i < 180; i++) game.update(FRAME);
        assertEquals(PlatformerGame.MAX_HEALTH - 1, game.health());
    }

    @Test
    void terrainBlocksShardsAndPearlsBounceOnce() {
        var game = shootingRange(new TestPlatformerInput(), RangedAttack.Kind.SHARD);
        game.addSolid(game.addSprite(.3f, .06f, .02f, .20f));
        for (int i = 0; i < 200; i++) game.update(FRAME);
        assertEquals(PlatformerGame.MAX_HEALTH, game.health());
        assertFalse(game.enemyAt(0).attack().flying());

        game = shootingRange(new TestPlatformerInput(), RangedAttack.Kind.PEARL);
        var shot = game.enemyAt(0).attack();
        boolean descending = false;
        int bounces = 0;
        for (int i = 0; i < 220; i++) {
            float previous = shot.y();
            boolean inFlight = shot.flying();
            game.update(FRAME);
            if (shot.flying()) game.player().position(-.55f, .07f);
            if (inFlight && shot.flying()) {
                if (descending && shot.y() > previous) bounces++;
                descending = shot.y() < previous;
            }
        }
        assertEquals(1, bounces);
        assertFalse(shot.flying());
    }

    @Test
    void stompingInterruptsAChargedShot() {
        var game = shootingRange(new TestPlatformerInput(), RangedAttack.Kind.EMBER);
        var enemy = game.enemyAt(0);
        for (int i = 0; i < 64; i++) game.update(FRAME);
        assertTrue(enemy.attack().charge() > 0);
        game.player().position(enemy.sprite().x(), .22f);
        for (int i = 0; i < 120; i++) {
            game.update(FRAME);
            assertFalse(enemy.attack().flying());
        }
        assertTrue(enemy.defeated());
        assertEquals(0, enemy.attack().charge());
    }

    private static PlatformerGame shootingRange(TestPlatformerInput input, RangedAttack.Kind kind) {
        var game = new PlatformerGame(input);
        game.addSolid(game.addSprite(0, -.1f, 3, .1f));
        game.player(game.addSprite(.60f, .07f, .05f, .07f));
        game.addEnemy(game.addSprite(0, .06f, .08f, .06f), -.01f, .01f, .01f, PlatformerGame.EnemyKind.CRAWLER, kind);
        return game;
    }

    @Test
    void movingLiftCarriesItsRiderAndRetryUsesTheDiscoveredLantern() {
        var input = new TestPlatformerInput();
        var game = new PlatformerGame(input);
        var lift = game.addSprite(0, 0, .3f, .02f);
        lift.lift = lift.bridge = true;
        game.addSolid(lift);
        var player = game.addSprite(0, .09f, .05f, .07f);
        game.player(player);
        for (int i = 0; i < 120; i++) {
            game.update(FRAME);
            assertTrue(game.playerOnGround());
            assertEquals(lift.y() + lift.halfHeight() + player.halfHeight(), player.y(), .0001);
        }
        game.addSite("Millrace", player.x(), player.y());
        game.update(FRAME);
        assertEquals(1, game.visitedCount());
        player.position(.5f, -2);
        game.update(FRAME);
        assertTrue(game.gameOver());
        game.retry();
        assertFalse(game.gameOver());
        assertEquals(1, game.visitedCount());
        assertEquals(game.siteX(0), player.x());
        assertEquals(game.siteY(0), player.y());
    }

}
