package io.github.libfdx.samples.ecs.platformer.input;

public final class TestPlatformerInput implements PlatformerInput {
    public boolean left;
    public boolean right;
    public boolean jump;
    public boolean restart;

    @Override
    public boolean leftDown() {
        return left;
    }

    @Override
    public boolean rightDown() {
        return right;
    }

    @Override
    public boolean jumpDown() {
        return jump;
    }

    @Override
    public boolean restartDown() {
        return restart;
    }
}
