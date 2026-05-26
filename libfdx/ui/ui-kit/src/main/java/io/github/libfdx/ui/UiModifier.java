package io.github.libfdx.ui;

public final class UiModifier {
    private static final UiModifier NONE = new UiModifier(false, false, Float.NaN, Float.NaN, Float.NaN, Float.NaN,
            Float.NaN, Float.NaN, UiInsets.ZERO, UiInsets.ZERO, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f,
            0.0f, UiAlign.START, true, false, null, null, null, null, null, null, null, null);

    private final boolean fillWidth;
    private final boolean fillHeight;
    private final float width;
    private final float height;
    private final float minWidth;
    private final float minHeight;
    private final float maxWidth;
    private final float maxHeight;
    private final UiInsets padding;
    private final UiInsets margin;
    private final float gap;
    private final float weight;
    private final float alpha;
    private final float offsetX;
    private final float offsetY;
    private final float scaleX;
    private final float scaleY;
    private final float rotation;
    private final UiAlign align;
    private final boolean enabled;
    private final boolean focusable;
    private final String style;
    private final String transitionState;
    private final UiTransition transition;
    private final String semanticLabel;
    private final String validationId;
    private final String tooltipTarget;
    private final UiAnimationSpec contentSizeAnimation;
    private final UiAnimationSpec placementAnimation;

    private UiModifier(boolean fillWidth, boolean fillHeight, float width, float height, float minWidth,
            float minHeight, float maxWidth, float maxHeight, UiInsets padding, UiInsets margin, float gap,
            float weight, float alpha, float offsetX, float offsetY, float scaleX, float scaleY, float rotation,
            UiAlign align, boolean enabled, boolean focusable, String style, String transitionState,
            UiTransition transition, String semanticLabel, String validationId, UiAnimationSpec contentSizeAnimation,
            UiAnimationSpec placementAnimation, String tooltipTarget) {
        this.fillWidth = fillWidth;
        this.fillHeight = fillHeight;
        this.width = width;
        this.height = height;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.padding = padding != null ? padding : UiInsets.ZERO;
        this.margin = margin != null ? margin : UiInsets.ZERO;
        this.gap = gap;
        this.weight = weight;
        this.alpha = alpha;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.rotation = rotation;
        this.align = align != null ? align : UiAlign.START;
        this.enabled = enabled;
        this.focusable = focusable;
        this.style = style;
        this.transitionState = transitionState;
        this.transition = transition;
        this.semanticLabel = semanticLabel;
        this.validationId = validationId;
        this.tooltipTarget = tooltipTarget;
        this.contentSizeAnimation = contentSizeAnimation;
        this.placementAnimation = placementAnimation;
    }

    public static UiModifier none() {
        return NONE;
    }

    public UiModifier fill() {
        return copy(true, true, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin, gap, weight,
                alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style, transitionState,
                transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation, tooltipTarget);
    }

    public UiModifier fillWidth() {
        return copy(true, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin, gap,
                weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation,
                tooltipTarget);
    }

    public UiModifier fillHeight() {
        return copy(fillWidth, true, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin, gap,
                weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation,
                tooltipTarget);
    }

    public UiModifier width(float width) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier height(float height) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier size(float width, float height) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier minWidth(float minWidth) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier minHeight(float minHeight) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier maxWidth(float maxWidth) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier maxHeight(float maxHeight) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier padding(float all) {
        return padding(UiInsets.of(all));
    }

    public UiModifier padding(float horizontal, float vertical) {
        return padding(UiInsets.of(horizontal, vertical));
    }

    public UiModifier padding(float left, float top, float right, float bottom) {
        return padding(UiInsets.of(left, top, right, bottom));
    }

    public UiModifier padding(UiInsets padding) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier margin(float all) {
        return margin(UiInsets.of(all));
    }

    public UiModifier margin(UiInsets margin) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier gap(float gap) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier weight(float weight) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier alpha(float alpha) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier offset(float offsetX, float offsetY) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier scale(float scale) {
        return scale(scale, scale);
    }

    public UiModifier scale(float scaleX, float scaleY) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier rotation(float rotation) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier align(UiAlign align) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier enabled(boolean enabled) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier focusable(boolean focusable) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier style(String style) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier transition(String state, UiTransition transition) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                state, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier semanticLabel(String semanticLabel) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier validationId(String validationId) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    public UiModifier tooltipTarget(String tooltipTarget) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation,
                tooltipTarget);
    }

    public UiModifier animateContentSize() {
        return animateContentSize(UiAnimationSpec.defaultSpec());
    }

    public UiModifier animateContentSize(UiAnimationSpec animation) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, animation != null ? animation : UiAnimationSpec.defaultSpec(),
                placementAnimation);
    }

    public UiModifier animateItemPlacement() {
        return animateItemPlacement(UiAnimationSpec.defaultSpec());
    }

    public UiModifier animateItemPlacement(UiAnimationSpec animation) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation,
                animation != null ? animation : UiAnimationSpec.defaultSpec());
    }

    public boolean isFillWidth() {
        return fillWidth;
    }

    public boolean isFillHeight() {
        return fillHeight;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float minWidth() {
        return minWidth;
    }

    public float minHeight() {
        return minHeight;
    }

    public float maxWidth() {
        return maxWidth;
    }

    public float maxHeight() {
        return maxHeight;
    }

    public UiInsets padding() {
        return padding;
    }

    public UiInsets margin() {
        return margin;
    }

    public float gap() {
        return gap;
    }

    public float weight() {
        return weight;
    }

    public float alpha() {
        return alpha;
    }

    public float offsetX() {
        return offsetX;
    }

    public float offsetY() {
        return offsetY;
    }

    public float scaleX() {
        return scaleX;
    }

    public float scaleY() {
        return scaleY;
    }

    public float rotation() {
        return rotation;
    }

    public UiAlign align() {
        return align;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean focusable() {
        return focusable;
    }

    public String style() {
        return style;
    }

    public String transitionState() {
        return transitionState;
    }

    public UiTransition transition() {
        return transition;
    }

    public String semanticLabel() {
        return semanticLabel;
    }

    public String validationId() {
        return validationId;
    }

    public String tooltipTarget() {
        return tooltipTarget;
    }

    public UiAnimationSpec contentSizeAnimation() {
        return contentSizeAnimation;
    }

    public UiAnimationSpec placementAnimation() {
        return placementAnimation;
    }

    private UiModifier copy(boolean fillWidth, boolean fillHeight, float width, float height, float minWidth,
            float minHeight, float maxWidth, float maxHeight, UiInsets padding, UiInsets margin, float gap,
            float weight, float alpha, float offsetX, float offsetY, float scaleX, float scaleY, float rotation,
            UiAlign align, boolean enabled, boolean focusable, String style, String transitionState,
            UiTransition transition, String semanticLabel, String validationId, UiAnimationSpec contentSizeAnimation,
            UiAnimationSpec placementAnimation) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation,
                tooltipTarget);
    }

    private UiModifier copy(boolean fillWidth, boolean fillHeight, float width, float height, float minWidth,
            float minHeight, float maxWidth, float maxHeight, UiInsets padding, UiInsets margin, float gap,
            float weight, float alpha, float offsetX, float offsetY, float scaleX, float scaleY, float rotation,
            UiAlign align, boolean enabled, boolean focusable, String style, String transitionState,
            UiTransition transition, String semanticLabel, String validationId, UiAnimationSpec contentSizeAnimation,
            UiAnimationSpec placementAnimation, String tooltipTarget) {
        return new UiModifier(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight,
                padding, margin, gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled,
                focusable, style, transitionState, transition, semanticLabel, validationId, contentSizeAnimation,
                placementAnimation, tooltipTarget);
    }
}
