package io.github.libfdx.ui;

public final class UiAnimationSpec {
    private static final UiAnimationSpec DEFAULT = new UiAnimationSpec(150, 0, false, false,
            UiEasing.outCubic(), false, 0.0f, 0.0f);

    private final int durationMillis;
    private final int delayMillis;
    private final boolean repeat;
    private final boolean repeatReverse;
    private final UiEasing easing;
    private final boolean fade;
    private final float slideX;
    private final float slideY;

    private UiAnimationSpec(int durationMillis, int delayMillis, boolean repeat, boolean repeatReverse,
            UiEasing easing, boolean fade, float slideX, float slideY) {
        this.durationMillis = Math.max(0, durationMillis);
        this.delayMillis = Math.max(0, delayMillis);
        this.repeat = repeat;
        this.repeatReverse = repeatReverse;
        this.easing = easing != null ? easing : UiEasing.linear();
        this.fade = fade;
        this.slideX = slideX;
        this.slideY = slideY;
    }

    public static UiAnimationSpec defaultSpec() {
        return DEFAULT;
    }

    public UiAnimationSpec durationMillis(int durationMillis) {
        return new UiAnimationSpec(durationMillis, delayMillis, repeat, repeatReverse, easing, fade, slideX, slideY);
    }

    public UiAnimationSpec delayMillis(int delayMillis) {
        return new UiAnimationSpec(durationMillis, delayMillis, repeat, repeatReverse, easing, fade, slideX, slideY);
    }

    public UiAnimationSpec easing(UiEasing easing) {
        return new UiAnimationSpec(durationMillis, delayMillis, repeat, repeatReverse, easing, fade, slideX, slideY);
    }

    public UiAnimationSpec repeat() {
        return new UiAnimationSpec(durationMillis, delayMillis, true, false, easing, fade, slideX, slideY);
    }

    public UiAnimationSpec repeatReverse() {
        return new UiAnimationSpec(durationMillis, delayMillis, true, true, easing, fade, slideX, slideY);
    }

    public UiAnimationSpec fade() {
        return new UiAnimationSpec(durationMillis, delayMillis, repeat, repeatReverse, easing, true, slideX, slideY);
    }

    public UiAnimationSpec slideX(float slideX) {
        return new UiAnimationSpec(durationMillis, delayMillis, repeat, repeatReverse, easing, fade, slideX, slideY);
    }

    public UiAnimationSpec slideY(float slideY) {
        return new UiAnimationSpec(durationMillis, delayMillis, repeat, repeatReverse, easing, fade, slideX, slideY);
    }

    public int durationMillis() {
        return durationMillis;
    }

    public int delayMillis() {
        return delayMillis;
    }

    public boolean isRepeat() {
        return repeat;
    }

    public boolean isRepeatReverse() {
        return repeatReverse;
    }

    public UiEasing easing() {
        return easing;
    }

    public boolean isFade() {
        return fade;
    }

    public float slideX() {
        return slideX;
    }

    public float slideY() {
        return slideY;
    }
}
