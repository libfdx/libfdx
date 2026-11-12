package io.github.libfdx.ui;

/** Retains press progress through recomposition and completes a visible pulse for short clicks. */
final class UiPressAnimationRuntime {
    private final UiFloatAnimatable progress = new UiFloatAnimatable(0);
    private UiPressAnimation configuration;
    private boolean pressed;
    private float pressRemaining;

    void configure(UiPressAnimation configuration) {
        this.configuration = configuration;
    }

    void pressed(boolean pressed) {
        if (this.pressed == pressed) return;
        this.pressed = pressed;
        if (pressed) compress();
        else if (!progress.isRunning() || progress.target() != 1) progress.animateTo(0, configuration.release());
    }

    void pulse() {
        compress();
    }

    private void compress() {
        if (progress.isRunning() && progress.target() == 1) return;
        progress.animateTo(1, configuration.press());
        pressRemaining = Math.max(1, configuration.press().durationMillis()) / 1000.0f;
    }

    boolean update(float delta) {
        boolean running = progress.isRunning();
        float releaseDelta = 0;
        if (progress.isRunning() && progress.target() == 1) {
            float step = Math.min(delta, pressRemaining);
            progress.update(step);
            pressRemaining = Math.max(0, pressRemaining - step);
            if (pressRemaining == 0) progress.snapTo(1);
            releaseDelta = delta - step;
        } else {
            progress.update(delta);
        }
        if (!pressed && !progress.isRunning() && progress.target() == 1) {
            progress.animateTo(0, configuration.release());
            progress.update(releaseDelta);
        }
        return running || progress.isRunning();
    }

    float scale() {
        return 1 + (configuration.scale() - 1) * progress.get();
    }

    float brightness() {
        float amount = Math.max(0, Math.min(1, progress.get()));
        return 1 + (configuration.brightness() - 1) * amount;
    }

}
