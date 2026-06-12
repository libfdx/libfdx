package io.github.libfdx.ui;

/**
 * Represents an ui animation spec.
 *
 * @author xpenatan
 */
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

    /**
     * Creates an UI animation spec.
     *
     * @return a new UI animation spec
     */
    public static UiAnimationSpec defaultSpec() {
        return DEFAULT;
    }

    /**
     * Sets the duration millis and returns this UI animation spec.
     *
     * @param durationMillis the duration millis
     * @return this UI animation spec for chaining
     */
    public UiAnimationSpec durationMillis(int durationMillis) {
        return new UiAnimationSpec(durationMillis, delayMillis, repeat, repeatReverse, easing, fade, slideX, slideY);
    }

    /**
     * Sets the delay millis and returns this UI animation spec.
     *
     * @param delayMillis the delay millis
     * @return this UI animation spec for chaining
     */
    public UiAnimationSpec delayMillis(int delayMillis) {
        return new UiAnimationSpec(durationMillis, delayMillis, repeat, repeatReverse, easing, fade, slideX, slideY);
    }

    /**
     * Sets the easing and returns this UI animation spec.
     *
     * @param easing the easing
     * @return this UI animation spec for chaining
     */
    public UiAnimationSpec easing(UiEasing easing) {
        return new UiAnimationSpec(durationMillis, delayMillis, repeat, repeatReverse, easing, fade, slideX, slideY);
    }

    /**
     * Returns the repeat.
     *
     * @return this UI animation spec for chaining
     */
    public UiAnimationSpec repeat() {
        return new UiAnimationSpec(durationMillis, delayMillis, true, false, easing, fade, slideX, slideY);
    }

    /**
     * Returns the repeat reverse.
     *
     * @return this UI animation spec for chaining
     */
    public UiAnimationSpec repeatReverse() {
        return new UiAnimationSpec(durationMillis, delayMillis, true, true, easing, fade, slideX, slideY);
    }

    /**
     * Returns the fade.
     *
     * @return this UI animation spec for chaining
     */
    public UiAnimationSpec fade() {
        return new UiAnimationSpec(durationMillis, delayMillis, repeat, repeatReverse, easing, true, slideX, slideY);
    }

    /**
     * Sets the slide x and returns this UI animation spec.
     *
     * @param slideX the slide x
     * @return this UI animation spec for chaining
     */
    public UiAnimationSpec slideX(float slideX) {
        return new UiAnimationSpec(durationMillis, delayMillis, repeat, repeatReverse, easing, fade, slideX, slideY);
    }

    /**
     * Sets the slide y and returns this UI animation spec.
     *
     * @param slideY the slide y
     * @return this UI animation spec for chaining
     */
    public UiAnimationSpec slideY(float slideY) {
        return new UiAnimationSpec(durationMillis, delayMillis, repeat, repeatReverse, easing, fade, slideX, slideY);
    }

    /**
     * Returns the duration millis.
     *
     * @return the duration millis
     */
    public int durationMillis() {
        return durationMillis;
    }

    /**
     * Returns the delay millis.
     *
     * @return the delay millis
     */
    public int delayMillis() {
        return delayMillis;
    }

    /**
     * Returns whether repeat is enabled or true.
     *
     * @return true if repeat is enabled or true; false otherwise
     */
    public boolean isRepeat() {
        return repeat;
    }

    /**
     * Returns whether repeat reverse is enabled or true.
     *
     * @return true if repeat reverse is enabled or true; false otherwise
     */
    public boolean isRepeatReverse() {
        return repeatReverse;
    }

    /**
     * Returns the easing.
     *
     * @return the easing
     */
    public UiEasing easing() {
        return easing;
    }

    /**
     * Returns whether fade is enabled or true.
     *
     * @return true if fade is enabled or true; false otherwise
     */
    public boolean isFade() {
        return fade;
    }

    /**
     * Returns the slide x.
     *
     * @return the slide x
     */
    public float slideX() {
        return slideX;
    }

    /**
     * Returns the slide y.
     *
     * @return the slide y
     */
    public float slideY() {
        return slideY;
    }
}
