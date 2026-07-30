package io.github.libfdx.samples.g2d.spritemovement;

import io.github.libfdx.samples.g2d.spritemovement.input.MovementInput;

/** Mutable application-owned state for the fixed Sprite Movement level. */
final class SpriteMovementState {
    static final float PLAYER_WIDTH = 1.0f;
    static final float PLAYER_HEIGHT = 1.0f;

    private static final float PLAYER_SPEED = 3.0f;
    private static final float DIAGONAL = 0.70710677f;

    private final Wall[] walls = {
            new Wall(-3.0f, 0.0f, 0.5f, 4.0f),
            new Wall(3.0f, 0.0f, 0.5f, 4.0f),
            new Wall(0.0f, -2.0f, 6.5f, 0.5f),
            new Wall(0.0f, 2.0f, 6.5f, 0.5f)
    };
    private float playerX;
    private float playerY;

    void update(MovementInput input, float deltaTime) {
        float moveX = input.horizontal();
        float moveY = input.vertical();
        if (moveX == 0.0f && moveY == 0.0f) {
            return;
        }
        if (moveX != 0.0f && moveY != 0.0f) {
            moveX *= DIAGONAL;
            moveY *= DIAGONAL;
        }
        playerX += moveX * PLAYER_SPEED * deltaTime;
        playerY += moveY * PLAYER_SPEED * deltaTime;
    }

    void reset() {
        playerX = 0.0f;
        playerY = 0.0f;
    }

    float playerX() {
        return playerX;
    }

    float playerY() {
        return playerY;
    }

    int wallCount() {
        return walls.length;
    }

    Wall wallAt(int index) {
        return walls[index];
    }

    static final class Wall {
        final float x;
        final float y;
        final float width;
        final float height;

        Wall(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
