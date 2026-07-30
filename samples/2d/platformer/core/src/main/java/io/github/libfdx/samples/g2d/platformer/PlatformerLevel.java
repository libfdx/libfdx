package io.github.libfdx.samples.g2d.platformer;

import io.github.libfdx.samples.g2d.platformer.input.PlatformerInput;

/**
 * Builds the fixed sample level into ordinary application-owned game state.
 */
public final class PlatformerLevel {
    private static final float ITEM_HALF = 0.038f;
    private static final float ENEMY_HALF_WIDTH = 0.056f;
    private static final float ENEMY_HALF_HEIGHT = 0.058f;

    private PlatformerLevel() {
    }

    /**
     * Creates the complete platformer game state.
     *
     * @param input the input source
     * @return the game
     */
    public static PlatformerGame create(PlatformerInput input) {
        PlatformerGame game = new PlatformerGame(input);
        createBackground(game);
        createLevel(game);
        createPlayer(game);
        return game;
    }

    private static void createPlayer(PlatformerGame game) {
        game.player(game.addSprite(
                PlatformerConstants.PLAYER_START_X,
                PlatformerConstants.PLAYER_START_Y,
                PlatformerConstants.PLAYER_HALF_WIDTH,
                PlatformerConstants.PLAYER_HALF_HEIGHT,
                PlatformerConstants.REGION_PLAYER_IDLE,
                PlatformerConstants.LAYER_PLAYER,
                1.0f));
    }

    private static void createBackground(PlatformerGame game) {
        createSprite(game, 0.0f, 0.0f, 2.08f, 2.08f, PlatformerConstants.REGION_BACKGROUND_SKY,
                PlatformerConstants.LAYER_BACKGROUND, 0.0f);
        createCloud(game, -0.70f, 0.58f, 0.16f);
        createCloud(game, -0.12f, 0.70f, 0.16f);
        createCloud(game, 0.68f, 0.52f, 0.16f);
        createSprite(game, 1.38f, 0.66f, 0.16f, 0.12f, PlatformerConstants.REGION_CLOUD_SMALL,
                PlatformerConstants.LAYER_BACKGROUND, 0.10f);
        createCloud(game, 2.18f, 0.60f, 0.16f);
    }

    private static void createLevel(PlatformerGame game) {
        createGrassSpan(game, PlatformerConstants.LEVEL_LEFT, PlatformerConstants.GROUND_TILE_Y, 14);
        createGrassSpan(game, 0.92f, PlatformerConstants.GROUND_TILE_Y, 7);
        createGrassSpan(game, 2.00f, PlatformerConstants.GROUND_TILE_Y, 13);

        createBridgeSpan(game, -0.56f, -0.55f, 4);
        createGrassSpan(game, 0.06f, -0.33f, 4);
        createGrassSpan(game, 0.76f, -0.09f, 5);
        createBridgeSpan(game, 1.48f, -0.35f, 4);
        createGrassSpan(game, 2.26f, -0.08f, 5);
        createGrassSpan(game, 2.86f, -0.45f, 4);

        createDecoration(game, -0.86f, PlatformerConstants.GROUND_TOP_Y + 0.06f,
                PlatformerConstants.REGION_BUSH, 0.13f, 0.10f);
        createDecoration(game, -0.46f, PlatformerConstants.GROUND_TOP_Y + 0.08f,
                PlatformerConstants.REGION_FLOWER, 0.10f, 0.10f);
        createDecoration(game, 0.28f, -0.21f, PlatformerConstants.REGION_ROCK, 0.10f, 0.10f);
        createDecoration(game, 1.08f, PlatformerConstants.GROUND_TOP_Y + 0.12f,
                PlatformerConstants.REGION_TREE, 0.16f, 0.22f);
        createDecoration(game, 2.18f, PlatformerConstants.GROUND_TOP_Y + 0.07f,
                PlatformerConstants.REGION_SIGN_RIGHT, 0.13f, 0.12f);

        createCollectible(game, -0.40f, -0.40f, PlatformerConstants.REGION_COIN);
        createCollectible(game, -0.26f, -0.40f, PlatformerConstants.REGION_COIN);
        createCollectible(game, 0.20f, -0.18f, PlatformerConstants.REGION_GEM);
        createCollectible(game, 0.92f, 0.06f, PlatformerConstants.REGION_COIN);
        createCollectible(game, 1.08f, 0.06f, PlatformerConstants.REGION_COIN);
        createCollectible(game, 1.62f, -0.20f, PlatformerConstants.REGION_KEY);
        createCollectible(game, 2.42f, 0.07f, PlatformerConstants.REGION_GEM);
        createCollectible(game, 2.58f, 0.07f, PlatformerConstants.REGION_COIN);

        createHazard(game, 0.54f, PlatformerConstants.GROUND_TOP_Y + 0.036f,
                PlatformerConstants.REGION_SPIKE);
        createHazard(game, 2.02f, PlatformerConstants.GROUND_TOP_Y + 0.036f,
                PlatformerConstants.REGION_SPIKE);
        createEnemy(game, 1.26f, PlatformerConstants.GROUND_TOP_Y + ENEMY_HALF_HEIGHT,
                1.04f, 1.62f, 0.30f, PlatformerConstants.REGION_ENEMY_WALKER);
        createEnemy(game, 2.66f, PlatformerConstants.GROUND_TOP_Y + ENEMY_HALF_HEIGHT,
                2.36f, 3.10f, 0.34f, PlatformerConstants.REGION_ENEMY_FLYER);
        createGoal(game, 3.22f, PlatformerConstants.GROUND_TOP_Y + 0.13f);
    }

    private static void createGrassSpan(PlatformerGame game, float startX, float y, int count) {
        for (int i = 0; i < count; i++) {
            int region = PlatformerConstants.REGION_GRASS_SINGLE;
            if (count > 1 && i == 0) {
                region = PlatformerConstants.REGION_GRASS_LEFT;
            } else if (count > 1 && i == count - 1) {
                region = PlatformerConstants.REGION_GRASS_RIGHT;
            } else if (count > 1) {
                region = PlatformerConstants.REGION_GRASS_MIDDLE;
            }
            float x = startX + PlatformerConstants.TILE_HALF + i * PlatformerConstants.TILE_SIZE;
            createTile(game, x, y, region, true);
            createTile(game, x, y - PlatformerConstants.TILE_SIZE, dirtRegion(count, i), false);
        }
    }

    private static void createBridgeSpan(PlatformerGame game, float startX, float y, int count) {
        for (int i = 0; i < count; i++) {
            int region = PlatformerConstants.REGION_BRIDGE_MIDDLE;
            if (i == 0) {
                region = PlatformerConstants.REGION_BRIDGE_LEFT;
            } else if (i == count - 1) {
                region = PlatformerConstants.REGION_BRIDGE_RIGHT;
            }
            float x = startX + PlatformerConstants.TILE_HALF + i * PlatformerConstants.TILE_SIZE;
            createTile(game, x, y, region, true);
        }
    }

    private static int dirtRegion(int count, int index) {
        if (count == 1) {
            return PlatformerConstants.REGION_DIRT_SINGLE;
        }
        if (index == 0) {
            return PlatformerConstants.REGION_DIRT_LEFT;
        }
        if (index == count - 1) {
            return PlatformerConstants.REGION_DIRT_RIGHT;
        }
        return PlatformerConstants.REGION_DIRT_MIDDLE;
    }

    private static void createTile(PlatformerGame game, float x, float y, int region, boolean solid) {
        PlatformerGame.Sprite sprite = game.addSprite(
                x,
                y,
                PlatformerConstants.TILE_HALF,
                PlatformerConstants.TILE_HALF,
                region,
                PlatformerConstants.LAYER_PLATFORM,
                1.0f);
        if (solid) {
            game.addSolid(sprite);
        }
    }

    private static void createDecoration(
            PlatformerGame game, float x, float y, int region, float width, float height) {
        createSprite(game, x, y, width, height, region, PlatformerConstants.LAYER_DECORATION, 1.0f);
    }

    private static void createCloud(PlatformerGame game, float x, float y, float tileWidth) {
        createSprite(game, x, y, tileWidth, tileWidth, PlatformerConstants.REGION_CLOUD_LEFT,
                PlatformerConstants.LAYER_BACKGROUND, 0.10f);
        createSprite(game, x + tileWidth, y, tileWidth, tileWidth,
                PlatformerConstants.REGION_CLOUD_MIDDLE,
                PlatformerConstants.LAYER_BACKGROUND, 0.10f);
        createSprite(game, x + tileWidth * 2.0f, y, tileWidth, tileWidth,
                PlatformerConstants.REGION_CLOUD_RIGHT,
                PlatformerConstants.LAYER_BACKGROUND, 0.10f);
    }

    private static void createCollectible(PlatformerGame game, float x, float y, int region) {
        PlatformerGame.Sprite sprite = game.addSprite(
                x,
                y,
                ITEM_HALF,
                ITEM_HALF,
                region,
                PlatformerConstants.LAYER_ITEM,
                1.0f);
        game.addCollectible(sprite, 1);
    }

    private static void createHazard(PlatformerGame game, float x, float y, int region) {
        PlatformerGame.Sprite sprite = game.addSprite(
                x,
                y,
                PlatformerConstants.TILE_HALF * 0.82f,
                PlatformerConstants.TILE_HALF * 0.62f,
                region,
                PlatformerConstants.LAYER_HAZARD,
                1.0f);
        game.addHazard(sprite);
    }

    private static void createEnemy(
            PlatformerGame game, float x, float y, float minX, float maxX, float speed, int region) {
        PlatformerGame.Sprite sprite = game.addSprite(
                x,
                y,
                ENEMY_HALF_WIDTH,
                ENEMY_HALF_HEIGHT,
                region,
                PlatformerConstants.LAYER_ENEMY,
                1.0f);
        game.addEnemy(sprite, minX, maxX, speed);
    }

    private static void createGoal(PlatformerGame game, float x, float y) {
        PlatformerGame.Sprite sprite = game.addSprite(
                x,
                y,
                0.075f,
                0.13f,
                PlatformerConstants.REGION_DOOR,
                PlatformerConstants.LAYER_GOAL,
                1.0f);
        game.addGoal(sprite);
    }

    private static void createSprite(
            PlatformerGame game, float x, float y, float width, float height, int region, int layer,
            float parallax) {
        game.addSprite(x, y, width * 0.5f, height * 0.5f, region, layer, parallax);
    }
}
