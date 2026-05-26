package io.github.libfdx.ui;

public final class UiFloatAnimatable {
    private float value;
    private float start;
    private float target;
    private UiAnimationSpec spec = UiAnimationSpec.defaultSpec();
    private float elapsedMillis;
    private boolean running;
    private boolean reverseDirection;

    UiFloatAnimatable(float value) {
        this.value = value;
        this.start = value;
        this.target = value;
    }

    public float get() {
        return value;
    }

    public float target() {
        return target;
    }

    public void snapTo(float value) {
        this.value = value;
        this.start = value;
        this.target = value;
        this.elapsedMillis = 0.0f;
        this.running = false;
        this.reverseDirection = false;
    }

    public void animateTo(float target) {
        animateTo(target, UiAnimationSpec.defaultSpec());
    }

    public void animateTo(float target, UiAnimationSpec spec) {
        if (running && sameFloat(this.target, target)) {
            return;
        }
        if (!running && sameFloat(value, target)) {
            this.target = target;
            return;
        }
        this.start = value;
        this.target = target;
        this.spec = spec != null ? spec : UiAnimationSpec.defaultSpec();
        this.elapsedMillis = 0.0f;
        this.running = true;
        this.reverseDirection = false;
    }

    boolean isRunning() {
        return running;
    }

    void update(float deltaSeconds) {
        if (!running) {
            return;
        }
        elapsedMillis += Math.max(0.0f, deltaSeconds) * 1000.0f;
        if (elapsedMillis < spec.delayMillis()) {
            return;
        }
        int duration = Math.max(1, spec.durationMillis());
        float local = (elapsedMillis - spec.delayMillis()) / duration;
        if (local >= 1.0f) {
            if (spec.isRepeat()) {
                elapsedMillis = spec.delayMillis();
                if (spec.isRepeatReverse()) {
                    reverseDirection = !reverseDirection;
                }
                local = 1.0f;
            } else {
                value = target;
                running = false;
                return;
            }
        }
        float progress = spec.easing().apply(local);
        if (reverseDirection) {
            progress = 1.0f - progress;
        }
        value = start + (target - start) * progress;
    }

    private boolean sameFloat(float a, float b) {
        return Math.abs(a - b) <= 0.0001f;
    }
}
