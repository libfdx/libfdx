package io.github.libfdx.ui;

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

    public static UiTransition create() {
        return new UiTransition(UiAnimationSpec.defaultSpec(), Float.NaN, Float.NaN, Float.NaN, Float.NaN);
    }

    public UiTransition durationMillis(int durationMillis) {
        return new UiTransition(spec.durationMillis(durationMillis), alphaFrom, alphaTo, scaleFrom, scaleTo);
    }

    public UiTransition easing(UiEasing easing) {
        return new UiTransition(spec.easing(easing), alphaFrom, alphaTo, scaleFrom, scaleTo);
    }

    public UiTransition alpha(float from, float to) {
        return new UiTransition(spec, from, to, scaleFrom, scaleTo);
    }

    public UiTransition scale(float from, float to) {
        return new UiTransition(spec, alphaFrom, alphaTo, from, to);
    }

    public UiAnimationSpec spec() {
        return spec;
    }

    public float alphaFrom() {
        return alphaFrom;
    }

    public float alphaTo() {
        return alphaTo;
    }

    public float scaleFrom() {
        return scaleFrom;
    }

    public float scaleTo() {
        return scaleTo;
    }
}
