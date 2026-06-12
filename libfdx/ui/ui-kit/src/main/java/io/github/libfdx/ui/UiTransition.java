package io.github.libfdx.ui;

/**
 * Represents an ui transition.
 *
 * @author xpenatan
 */
public final class UiTransition {
    private final UiAnimationSpec spec;
    private final float alphaFrom;
    private final float alphaTo;
    private final float scaleFrom;
    private final float scaleTo;

    private UiTransition(UiAnimationSpec spec, float alphaFrom, float alphaTo, float scaleFrom, float scaleTo) {
        this.spec = spec != null ? spec : UiAnimationSpec.defaultSpec();
        this.alphaFrom = alphaFrom;
        this.alphaTo = alphaTo;
        this.scaleFrom = scaleFrom;
        this.scaleTo = scaleTo;
    }

    /**
     * Creates an UI transition.
     *
     * @return a new UI transition
     */
    public static UiTransition create() {
        return new UiTransition(UiAnimationSpec.defaultSpec(), Float.NaN, Float.NaN, Float.NaN, Float.NaN);
    }

    /**
     * Sets the duration millis and returns this UI transition.
     *
     * @param durationMillis the duration millis
     * @return this UI transition for chaining
     */
    public UiTransition durationMillis(int durationMillis) {
        return new UiTransition(spec.durationMillis(durationMillis), alphaFrom, alphaTo, scaleFrom, scaleTo);
    }

    /**
     * Sets the easing and returns this UI transition.
     *
     * @param easing the easing
     * @return this UI transition for chaining
     */
    public UiTransition easing(UiEasing easing) {
        return new UiTransition(spec.easing(easing), alphaFrom, alphaTo, scaleFrom, scaleTo);
    }

    /**
     * Sets the alpha and returns this UI transition.
     *
     * @param from the from
     * @param to the to
     * @return this UI transition for chaining
     */
    public UiTransition alpha(float from, float to) {
        return new UiTransition(spec, from, to, scaleFrom, scaleTo);
    }

    /**
     * Sets the scale and returns this UI transition.
     *
     * @param from the from
     * @param to the to
     * @return this UI transition for chaining
     */
    public UiTransition scale(float from, float to) {
        return new UiTransition(spec, alphaFrom, alphaTo, from, to);
    }

    /**
     * Returns the spec.
     *
     * @return the spec
     */
    public UiAnimationSpec spec() {
        return spec;
    }

    /**
     * Returns the alpha from.
     *
     * @return the alpha from
     */
    public float alphaFrom() {
        return alphaFrom;
    }

    /**
     * Returns the alpha to.
     *
     * @return the alpha to
     */
    public float alphaTo() {
        return alphaTo;
    }

    /**
     * Returns the scale from.
     *
     * @return the scale from
     */
    public float scaleFrom() {
        return scaleFrom;
    }

    /**
     * Returns the scale to.
     *
     * @return the scale to
     */
    public float scaleTo() {
        return scaleTo;
    }
}
