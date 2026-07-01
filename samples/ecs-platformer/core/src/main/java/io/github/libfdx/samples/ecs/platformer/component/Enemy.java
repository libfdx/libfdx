package io.github.libfdx.samples.ecs.platformer.component;

public final class Enemy {
    public final float startX;
    public final float startY;
    public final float minX;
    public final float maxX;
    public final float speed;
    public int direction = -1;

    public Enemy(float startX, float startY, float minX, float maxX, float speed) {
        this.startX = startX;
        this.startY = startY;
        this.minX = minX;
        this.maxX = maxX;
        this.speed = speed;
    }

    public void reset(Position position, Velocity velocity) {
        position.x = startX;
        position.y = startY;
        direction = -1;
        velocity.x = -speed;
        velocity.y = 0.0f;
    }
}
