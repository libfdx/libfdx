package io.github.libfdx.ui;

public final class UiAnimatable<T> {
    private T value;
    private T start;
    private T target;
    private UiAnimationSpec spec = UiAnimationSpec.defaultSpec();
    private float elapsedMillis;
    private boolean running;
    private boolean reverseDirection;

    UiAnimatable(T value) {
        this.value = value;
        this.start = value;
        this.target = value;
    }

    public T get() {
        return value;
    }

    public T target() {
        return target;
    }

    public void snapTo(T value) {
        this.value = value;
        this.start = value;
        this.target = value;
        this.elapsedMillis = 0.0f;
        this.running = false;
        this.reverseDirection = false;
    }

    public void animateTo(T target) {
        animateTo(target, UiAnimationSpec.defaultSpec());
    }

    public void animateTo(T target, UiAnimationSpec spec) {
        if (running && sameValue(this.target, target)) {
            return;
        }
        if (!running && sameValue(value, target)) {
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
        value = interpolate(start, target, progress);
    }

    @SuppressWarnings("unchecked")
    private T interpolate(T start, T target, float progress) {
        if (start instanceof UiSize && target instanceof UiSize) {
            UiSize a = (UiSize) start;
            UiSize b = (UiSize) target;
            return (T) new UiSize(lerp(a.width(), b.width(), progress), lerp(a.height(), b.height(), progress));
        }
        if (start instanceof UiRect && target instanceof UiRect) {
            UiRect a = (UiRect) start;
            UiRect b = (UiRect) target;
            return (T) new UiRect(lerp(a.x(), b.x(), progress), lerp(a.y(), b.y(), progress),
                    lerp(a.width(), b.width(), progress), lerp(a.height(), b.height(), progress));
        }
        if (start instanceof UiColor && target instanceof UiColor) {
            UiColor a = (UiColor) start;
            UiColor b = (UiColor) target;
            return (T) UiColor.rgba(lerp(a.red(), b.red(), progress), lerp(a.green(), b.green(), progress),
                    lerp(a.blue(), b.blue(), progress), lerp(a.alpha(), b.alpha(), progress));
        }
        return progress >= 1.0f ? target : start;
    }

    private float lerp(float a, float b, float progress) {
        return a + (b - a) * progress;
    }

    private boolean sameValue(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof UiSize && b instanceof UiSize) {
            UiSize x = (UiSize) a;
            UiSize y = (UiSize) b;
            return sameFloat(x.width(), y.width()) && sameFloat(x.height(), y.height());
        }
        if (a instanceof UiRect && b instanceof UiRect) {
            UiRect x = (UiRect) a;
            UiRect y = (UiRect) b;
            return sameFloat(x.x(), y.x()) && sameFloat(x.y(), y.y())
                    && sameFloat(x.width(), y.width()) && sameFloat(x.height(), y.height());
        }
        if (a instanceof UiColor && b instanceof UiColor) {
            UiColor x = (UiColor) a;
            UiColor y = (UiColor) b;
            return sameFloat(x.red(), y.red()) && sameFloat(x.green(), y.green())
                    && sameFloat(x.blue(), y.blue()) && sameFloat(x.alpha(), y.alpha());
        }
        return a.equals(b);
    }

    private boolean sameFloat(float a, float b) {
        return Math.abs(a - b) <= 0.0001f;
    }
}
