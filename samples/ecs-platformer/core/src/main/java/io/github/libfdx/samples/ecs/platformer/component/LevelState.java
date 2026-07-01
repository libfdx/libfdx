package io.github.libfdx.samples.ecs.platformer.component;

public final class LevelState {
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
