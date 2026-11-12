package io.github.libfdx.ui;

/** Immutable, opt-in button feedback. Compression and darkening apply to the button and its content. */
public final class UiPressAnimation {
    private static final UiPressAnimation DEFAULT = new UiPressAnimation(0.92f, 0.72f,
            Ui.animation().durationMillis(80).easing(UiEasing.outCubic()),
            Ui.animation().durationMillis(220).easing(UiEasing.outBack()));
    private final float scale;
    private final float brightness;
    private final UiAnimationSpec press;
    private final UiAnimationSpec release;

    private UiPressAnimation(float scale, float brightness, UiAnimationSpec press, UiAnimationSpec release) {
        this.scale = scale;
        this.brightness = brightness;
        this.press = press;
        this.release = release;
    }

    /** Returns the default 80 ms compression and 220 ms spring-back configuration. */
    public static UiPressAnimation create() {
        return DEFAULT;
    }

    /** Sets a positive, finite pressed scale. Layout and the button's hit area stay stable. */
    public UiPressAnimation scale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Press scale must be positive");
        return new UiPressAnimation(scale, brightness, press, release);
    }

    /** Sets pressed RGB brightness in the range 0..1. Opacity is preserved. */
    public UiPressAnimation brightness(float brightness) {
        if (!Float.isFinite(brightness) || brightness < 0 || brightness > 1) {
            throw new IllegalArgumentException("Press brightness must be between zero and one");
        }
        return new UiPressAnimation(scale, brightness, press, release);
    }

    /** Sets nonnegative press and release durations in milliseconds. */
    public UiPressAnimation durationMillis(int pressMillis, int releaseMillis) {
        if (pressMillis < 0 || releaseMillis < 0) throw new IllegalArgumentException("Durations must be nonnegative");
        return new UiPressAnimation(scale, brightness, press.durationMillis(pressMillis), release.durationMillis(releaseMillis));
    }

    float scale() {
        return scale;
    }

    float brightness() {
        return brightness;
    }

    UiAnimationSpec press() {
        return press;
    }

    UiAnimationSpec release() {
        return release;
    }

}
