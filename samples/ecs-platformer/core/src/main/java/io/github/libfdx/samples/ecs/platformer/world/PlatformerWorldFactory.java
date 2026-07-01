package io.github.libfdx.samples.ecs.platformer.world;

import io.github.libfdx.ecs.World;
import io.github.libfdx.samples.ecs.platformer.PlatformerConstants;
import io.github.libfdx.samples.ecs.platformer.component.Bounds;
import io.github.libfdx.samples.ecs.platformer.component.Collectible;
import io.github.libfdx.samples.ecs.platformer.component.Enemy;
import io.github.libfdx.samples.ecs.platformer.component.Goal;
import io.github.libfdx.samples.ecs.platformer.component.Hazard;
import io.github.libfdx.samples.ecs.platformer.component.InputState;
import io.github.libfdx.samples.ecs.platformer.component.LevelState;
import io.github.libfdx.samples.ecs.platformer.component.Player;
import io.github.libfdx.samples.ecs.platformer.component.Position;
import io.github.libfdx.samples.ecs.platformer.component.RenderSprite;
import io.github.libfdx.samples.ecs.platformer.component.Solid;
import io.github.libfdx.samples.ecs.platformer.component.Velocity;
import io.github.libfdx.samples.ecs.platformer.input.PlatformerInput;
import io.github.libfdx.samples.ecs.platformer.system.CameraSystem;
import io.github.libfdx.samples.ecs.platformer.system.EnemySystem;
import io.github.libfdx.samples.ecs.platformer.system.InputSystem;
import io.github.libfdx.samples.ecs.platformer.system.InteractionSystem;
import io.github.libfdx.samples.ecs.platformer.system.PlayerSystem;
import io.github.libfdx.samples.ecs.platformer.system.RenderSystem;
import io.github.libfdx.samples.ecs.platformer.system.RestartSystem;

public final class PlatformerWorldFactory {
    private static final float ITEM_HALF = 0.038f;
    private static final float ENEMY_HALF_WIDTH = 0.056f;
    private static final float ENEMY_HALF_HEIGHT = 0.058f;

    private PlatformerWorldFactory() {
    }

    public static World create(PlatformerInput input, RenderSystem renderSystem) {
        World world = new World();
        LevelState state = createState(world);
        createBackground(world);
        createLevel(world, state);
        createPlayer(world);
        world.addSystem(new InputSystem(input));
        world.addSystem(new RestartSystem());
        world.addSystem(new PlayerSystem());
        world.addSystem(new EnemySystem());
        world.addSystem(new InteractionSystem());
        world.addSystem(new CameraSystem());
        if (renderSystem != null) {
            world.addSystem(renderSystem);
        }
        world.flushCommands();
        return world;
    }

    private static LevelState createState(World world) {
        LevelState state = new LevelState();
        int entity = world.createEntity();
        world.add(entity, state);
        world.add(entity, new InputState());
        return state;
    }

    private static void createPlayer(World world) {
        int entity = world.createEntity();
        world.add(entity, new Position(PlatformerConstants.PLAYER_START_X, PlatformerConstants.PLAYER_START_Y));
        world.add(entity, new Velocity(0.0f, 0.0f));
        world.add(entity, new Bounds(PlatformerConstants.PLAYER_HALF_WIDTH, PlatformerConstants.PLAYER_HALF_HEIGHT));
        world.add(entity, new Player());
        world.add(entity, new RenderSprite(PlatformerConstants.REGION_PLAYER_IDLE,
                PlatformerConstants.LAYER_PLAYER));
    }

    private static void createBackground(World world) {
        createSprite(world, 0.0f, 0.0f, 2.08f, 2.08f, PlatformerConstants.REGION_BACKGROUND_SKY,
                PlatformerConstants.LAYER_BACKGROUND, 0.0f);
        createCloud(world, -0.70f, 0.58f, 0.16f);
        createCloud(world, -0.12f, 0.70f, 0.16f);
        createCloud(world, 0.68f, 0.52f, 0.16f);
        createSprite(world, 1.38f, 0.66f, 0.16f, 0.12f, PlatformerConstants.REGION_CLOUD_SMALL,
                PlatformerConstants.LAYER_BACKGROUND, 0.10f);
        createCloud(world, 2.18f, 0.60f, 0.16f);
    }

    private static void createLevel(World world, LevelState state) {
        createGrassSpan(world, PlatformerConstants.LEVEL_LEFT, PlatformerConstants.GROUND_TILE_Y, 14);
        createGrassSpan(world, 0.92f, PlatformerConstants.GROUND_TILE_Y, 7);
        createGrassSpan(world, 2.00f, PlatformerConstants.GROUND_TILE_Y, 13);

        createBridgeSpan(world, -0.56f, -0.55f, 4);
        createGrassSpan(world, 0.06f, -0.33f, 4);
        createGrassSpan(world, 0.76f, -0.09f, 5);
        createBridgeSpan(world, 1.48f, -0.35f, 4);
        createGrassSpan(world, 2.26f, -0.08f, 5);
        createGrassSpan(world, 2.86f, -0.45f, 4);

        createDecoration(world, -0.86f, PlatformerConstants.GROUND_TOP_Y + 0.06f,
                PlatformerConstants.REGION_BUSH, 0.13f, 0.10f);
        createDecoration(world, -0.46f, PlatformerConstants.GROUND_TOP_Y + 0.08f,
                PlatformerConstants.REGION_FLOWER, 0.10f, 0.10f);
        createDecoration(world, 0.28f, -0.21f, PlatformerConstants.REGION_ROCK, 0.10f, 0.10f);
        createDecoration(world, 1.08f, PlatformerConstants.GROUND_TOP_Y + 0.12f,
                PlatformerConstants.REGION_TREE, 0.16f, 0.22f);
        createDecoration(world, 2.18f, PlatformerConstants.GROUND_TOP_Y + 0.07f,
                PlatformerConstants.REGION_SIGN_RIGHT, 0.13f, 0.12f);

        createCollectible(world, state, -0.40f, -0.40f, PlatformerConstants.REGION_COIN);
        createCollectible(world, state, -0.26f, -0.40f, PlatformerConstants.REGION_COIN);
        createCollectible(world, state, 0.20f, -0.18f, PlatformerConstants.REGION_GEM);
        createCollectible(world, state, 0.92f, 0.06f, PlatformerConstants.REGION_COIN);
        createCollectible(world, state, 1.08f, 0.06f, PlatformerConstants.REGION_COIN);
        createCollectible(world, state, 1.62f, -0.20f, PlatformerConstants.REGION_KEY);
        createCollectible(world, state, 2.42f, 0.07f, PlatformerConstants.REGION_GEM);
        createCollectible(world, state, 2.58f, 0.07f, PlatformerConstants.REGION_COIN);

        createHazard(world, 0.54f, PlatformerConstants.GROUND_TOP_Y + 0.036f, PlatformerConstants.REGION_SPIKE);
        createHazard(world, 2.02f, PlatformerConstants.GROUND_TOP_Y + 0.036f, PlatformerConstants.REGION_SPIKE);
        createEnemy(world, 1.26f, PlatformerConstants.GROUND_TOP_Y + ENEMY_HALF_HEIGHT,
                1.04f, 1.62f, 0.30f, PlatformerConstants.REGION_ENEMY_WALKER);
        createEnemy(world, 2.66f, PlatformerConstants.GROUND_TOP_Y + ENEMY_HALF_HEIGHT,
                2.36f, 3.10f, 0.34f, PlatformerConstants.REGION_ENEMY_FLYER);
        createGoal(world, 3.22f, PlatformerConstants.GROUND_TOP_Y + 0.13f);
    }

    private static void createGrassSpan(World world, float startX, float y, int count) {
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
            createSolidTile(world, x, y, region);
            createTile(world, x, y - PlatformerConstants.TILE_SIZE, dirtRegion(count, i), false);
        }
    }

    private static void createBridgeSpan(World world, float startX, float y, int count) {
        for (int i = 0; i < count; i++) {
            int region = PlatformerConstants.REGION_BRIDGE_MIDDLE;
            if (i == 0) {
                region = PlatformerConstants.REGION_BRIDGE_LEFT;
            } else if (i == count - 1) {
                region = PlatformerConstants.REGION_BRIDGE_RIGHT;
            }
            float x = startX + PlatformerConstants.TILE_HALF + i * PlatformerConstants.TILE_SIZE;
            createSolidTile(world, x, y, region);
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

    private static void createSolidTile(World world, float x, float y, int region) {
        createTile(world, x, y, region, true);
    }

    private static void createTile(World world, float x, float y, int region, boolean solid) {
        int entity = world.createEntity();
        world.add(entity, new Position(x, y));
        world.add(entity, new Bounds(PlatformerConstants.TILE_HALF, PlatformerConstants.TILE_HALF));
        world.add(entity, new RenderSprite(region, PlatformerConstants.LAYER_PLATFORM));
        if (solid) {
            world.add(entity, new Solid());
        }
    }

    private static void createDecoration(World world, float x, float y, int region, float width, float height) {
        createSprite(world, x, y, width, height, region, PlatformerConstants.LAYER_DECORATION, 1.0f);
    }

    private static void createCloud(World world, float x, float y, float tileWidth) {
        createSprite(world, x, y, tileWidth, tileWidth, PlatformerConstants.REGION_CLOUD_LEFT,
                PlatformerConstants.LAYER_BACKGROUND, 0.10f);
        createSprite(world, x + tileWidth, y, tileWidth, tileWidth, PlatformerConstants.REGION_CLOUD_MIDDLE,
                PlatformerConstants.LAYER_BACKGROUND, 0.10f);
        createSprite(world, x + tileWidth * 2.0f, y, tileWidth, tileWidth, PlatformerConstants.REGION_CLOUD_RIGHT,
                PlatformerConstants.LAYER_BACKGROUND, 0.10f);
    }

    private static void createCollectible(World world, LevelState state, float x, float y, int region) {
        int entity = world.createEntity();
        world.add(entity, new Position(x, y));
        world.add(entity, new Bounds(ITEM_HALF, ITEM_HALF));
        world.add(entity, new Collectible(1));
        world.add(entity, new RenderSprite(region, PlatformerConstants.LAYER_ITEM));
        state.coinTotal++;
    }

    private static void createHazard(World world, float x, float y, int region) {
        int entity = world.createEntity();
        world.add(entity, new Position(x, y));
        world.add(entity, new Bounds(PlatformerConstants.TILE_HALF * 0.82f, PlatformerConstants.TILE_HALF * 0.62f));
        world.add(entity, new Hazard());
        world.add(entity, new RenderSprite(region, PlatformerConstants.LAYER_HAZARD));
    }

    private static void createEnemy(World world, float x, float y, float minX, float maxX, float speed, int region) {
        int entity = world.createEntity();
        world.add(entity, new Position(x, y));
        world.add(entity, new Velocity(-speed, 0.0f));
        world.add(entity, new Bounds(ENEMY_HALF_WIDTH, ENEMY_HALF_HEIGHT));
        world.add(entity, new Enemy(x, y, minX, maxX, speed));
        world.add(entity, new RenderSprite(region, PlatformerConstants.LAYER_ENEMY));
    }

    private static void createGoal(World world, float x, float y) {
        int entity = world.createEntity();
        world.add(entity, new Position(x, y));
        world.add(entity, new Bounds(0.075f, 0.13f));
        world.add(entity, new Goal());
        world.add(entity, new RenderSprite(PlatformerConstants.REGION_DOOR, PlatformerConstants.LAYER_GOAL));
    }

    private static void createSprite(World world, float x, float y, float width, float height, int region, int layer,
            float parallax) {
        int entity = world.createEntity();
        world.add(entity, new Position(x, y));
        world.add(entity, new Bounds(width * 0.5f, height * 0.5f));
        world.add(entity, new RenderSprite(region, layer).parallax(parallax));
    }
}
