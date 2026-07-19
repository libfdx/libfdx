package io.github.libfdx.samples.ecs.platformer.component;

import io.github.libfdx.ecs.component.Component;

public final class InputStateComponent implements Component {
    public boolean leftDown;
    public boolean rightDown;
    public boolean jumpDown;
    public boolean jumpPressed;
    public boolean restartDown;
    public boolean restartPressed;

    public void update(boolean nextLeftDown, boolean nextRightDown, boolean nextJumpDown, boolean nextRestartDown) {
        leftDown = nextLeftDown;
        rightDown = nextRightDown;
        jumpPressed = nextJumpDown && !jumpDown;
        restartPressed = nextRestartDown && !restartDown;
        jumpDown = nextJumpDown;
        restartDown = nextRestartDown;
    }
}
