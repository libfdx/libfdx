package io.github.libfdx.samples.ecs.platformer.component;

import io.github.libfdx.ecs.component.Component;

public final class LevelState implements Component {
    public float cameraX;
    public int coinsCollected;
    public int coinTotal;
    public boolean gameOver;
    public boolean completed;
    public boolean restarting;

    public void reset() {
        cameraX = 0.0f;
        coinsCollected = 0;
        gameOver = false;
        completed = false;
        restarting = false;
    }
}
