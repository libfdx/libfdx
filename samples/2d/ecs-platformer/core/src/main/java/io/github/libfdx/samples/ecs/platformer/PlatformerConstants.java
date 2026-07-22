package io.github.libfdx.samples.ecs.platformer;

public final class PlatformerConstants {
    public static final float VIEW_LEFT = -1.0f;
    public static final float VIEW_RIGHT = 1.0f;
    public static final float VIEW_WIDTH = VIEW_RIGHT - VIEW_LEFT;
    public static final float LEVEL_LEFT = -1.0f;
    public static final float LEVEL_RIGHT = 3.52f;
    public static final float CAMERA_MIN_X = 0.0f;
    public static final float CAMERA_MAX_X = LEVEL_RIGHT - VIEW_RIGHT;
    public static final float TILE_SIZE = 0.12f;
    public static final float TILE_HALF = TILE_SIZE * 0.5f;
    public static final float GROUND_TILE_Y = -0.82f;
    public static final float GROUND_TOP_Y = GROUND_TILE_Y + TILE_HALF;
    public static final float PLAYER_HALF_WIDTH = 0.052f;
    public static final float PLAYER_HALF_HEIGHT = 0.086f;
    public static final float PLAYER_START_X = -0.78f;
    public static final float PLAYER_START_Y = GROUND_TOP_Y + PLAYER_HALF_HEIGHT;
    public static final float PLAYER_MOVE_SPEED = 0.86f;
    public static final float GRAVITY = -4.2f;
    public static final float JUMP_VELOCITY = 1.55f;
    public static final float TERMINAL_VELOCITY = -2.0f;
    public static final float FALL_Y = -1.08f;
    public static final float CAMERA_FOLLOW_SPEED = 8.0f;
    public static final float MAX_DELTA = 1.0f / 30.0f;

    public static final int TILE_COLUMNS = 20;
    public static final int TILE_ROWS = 9;
    public static final int TILE_COUNT = TILE_COLUMNS * TILE_ROWS;
    public static final int BACKGROUND_COLUMNS = 8;
    public static final int BACKGROUND_ROWS = 3;
    public static final int BACKGROUND_COUNT = BACKGROUND_COLUMNS * BACKGROUND_ROWS;
    public static final int CHARACTER_COLUMNS = 9;
    public static final int CHARACTER_ROWS = 3;
    public static final int CHARACTER_COUNT = CHARACTER_COLUMNS * CHARACTER_ROWS;
    public static final int REGION_TILES_START = 0;
    public static final int REGION_BACKGROUNDS_START = REGION_TILES_START + TILE_COUNT;
    public static final int REGION_CHARACTERS_START = REGION_BACKGROUNDS_START + BACKGROUND_COUNT;
    public static final int REGION_COUNT = REGION_CHARACTERS_START + CHARACTER_COUNT;

    public static final int REGION_GRASS_SINGLE = tile(0);
    public static final int REGION_GRASS_LEFT = tile(1);
    public static final int REGION_GRASS_MIDDLE = tile(2);
    public static final int REGION_GRASS_RIGHT = tile(3);
    public static final int REGION_DIRT_SINGLE = tile(20);
    public static final int REGION_DIRT_LEFT = tile(21);
    public static final int REGION_DIRT_MIDDLE = tile(22);
    public static final int REGION_DIRT_RIGHT = tile(23);
    public static final int REGION_CRATE = tile(26);
    public static final int REGION_KEY = tile(27);
    public static final int REGION_COIN = tile(151);
    public static final int REGION_GEM = tile(67);
    public static final int REGION_SPIKE = tile(64);
    public static final int REGION_SIGN_RIGHT = tile(88);
    public static final int REGION_BRIDGE_LEFT = tile(90);
    public static final int REGION_BRIDGE_MIDDLE = tile(91);
    public static final int REGION_BRIDGE_RIGHT = tile(92);
    public static final int REGION_DOOR = tile(94);
    public static final int REGION_TREE = tile(117);
    public static final int REGION_BUSH = tile(124);
    public static final int REGION_FLOWER = tile(128);
    public static final int REGION_ROCK = tile(145);
    public static final int REGION_CLOUD_LEFT = tile(153);
    public static final int REGION_CLOUD_MIDDLE = tile(154);
    public static final int REGION_CLOUD_RIGHT = tile(155);
    public static final int REGION_CLOUD_SMALL = tile(156);
    public static final int REGION_PLAYER_IDLE = character(0);
    public static final int REGION_PLAYER_WALK = character(1);
    public static final int REGION_ENEMY_WALKER = character(15);
    public static final int REGION_ENEMY_FLYER = character(24);
    public static final int REGION_BACKGROUND_SKY = background(0);

    public static final int LAYER_BACKGROUND = 0;
    public static final int LAYER_DECORATION = 1;
    public static final int LAYER_PLATFORM = 2;
    public static final int LAYER_ITEM = 3;
    public static final int LAYER_HAZARD = 4;
    public static final int LAYER_GOAL = 5;
    public static final int LAYER_ENEMY = 6;
    public static final int LAYER_PLAYER = 7;

    private PlatformerConstants() {
    }

    public static int tile(int tileId) {
        return REGION_TILES_START + tileId;
    }

    public static int background(int tileId) {
        return REGION_BACKGROUNDS_START + tileId;
    }

    public static int character(int tileId) {
        return REGION_CHARACTERS_START + tileId;
    }
}
