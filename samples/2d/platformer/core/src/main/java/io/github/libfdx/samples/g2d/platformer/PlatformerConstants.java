package io.github.libfdx.samples.g2d.platformer;

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

    private PlatformerConstants() {
    }

}
