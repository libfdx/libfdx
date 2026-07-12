package io.github.libfdx.ui;

/**
 * Represents an ui modifier.
 *
 * @author xpenatan
 */
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

    /**
     * Creates an UI modifier.
     *
     * @return a new UI modifier
     */
    public static UiModifier none() {
        return NONE;
    }

    /**
     * Returns the fill.
     *
     * @return this UI modifier for chaining
     */
    public UiModifier fill() {
        return copy(true, true, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin, gap, weight,
                alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style, transitionState,
                transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation, tooltipTarget);
    }

    /**
     * Returns the fill width.
     *
     * @return this UI modifier for chaining
     */
    public UiModifier fillWidth() {
        return copy(true, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin, gap,
                weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation,
                tooltipTarget);
    }

    /**
     * Returns the fill height.
     *
     * @return this UI modifier for chaining
     */
    public UiModifier fillHeight() {
        return copy(fillWidth, true, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin, gap,
                weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation,
                tooltipTarget);
    }

    /**
     * Sets the width and returns this UI modifier.
     *
     * @param width the width in pixels
     * @return this UI modifier for chaining
     */
    public UiModifier width(float width) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the height and returns this UI modifier.
     *
     * @param height the height in pixels
     * @return this UI modifier for chaining
     */
    public UiModifier height(float height) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the size and returns this UI modifier.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return this UI modifier for chaining
     */
    public UiModifier size(float width, float height) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the min width and returns this UI modifier.
     *
     * @param minWidth the min width
     * @return this UI modifier for chaining
     */
    public UiModifier minWidth(float minWidth) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the min height and returns this UI modifier.
     *
     * @param minHeight the min height
     * @return this UI modifier for chaining
     */
    public UiModifier minHeight(float minHeight) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the max width and returns this UI modifier.
     *
     * @param maxWidth the max width
     * @return this UI modifier for chaining
     */
    public UiModifier maxWidth(float maxWidth) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the max height and returns this UI modifier.
     *
     * @param maxHeight the max height
     * @return this UI modifier for chaining
     */
    public UiModifier maxHeight(float maxHeight) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the padding and returns this UI modifier.
     *
     * @param all the all
     * @return this UI modifier for chaining
     */
    public UiModifier padding(float all) {
        return padding(UiInsets.of(all));
    }

    /**
     * Sets the padding and returns this UI modifier.
     *
     * @param horizontal the horizontal
     * @param vertical the vertical
     * @return this UI modifier for chaining
     */
    public UiModifier padding(float horizontal, float vertical) {
        return padding(UiInsets.of(horizontal, vertical));
    }

    /**
     * Sets the padding and returns this UI modifier.
     *
     * @param left the left
     * @param top the top
     * @param right the right
     * @param bottom the bottom
     * @return this UI modifier for chaining
     */
    public UiModifier padding(float left, float top, float right, float bottom) {
        return padding(UiInsets.of(left, top, right, bottom));
    }

    /**
     * Sets the padding and returns this UI modifier.
     *
     * @param padding the padding
     * @return this UI modifier for chaining
     */
    public UiModifier padding(UiInsets padding) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the margin and returns this UI modifier.
     *
     * @param all the all
     * @return this UI modifier for chaining
     */
    public UiModifier margin(float all) {
        return margin(UiInsets.of(all));
    }

    /**
     * Sets the margin and returns this UI modifier.
     *
     * @param margin the margin
     * @return this UI modifier for chaining
     */
    public UiModifier margin(UiInsets margin) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the gap and returns this UI modifier.
     *
     * @param gap the gap
     * @return this UI modifier for chaining
     */
    public UiModifier gap(float gap) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the weight and returns this UI modifier.
     *
     * @param weight the weight
     * @return this UI modifier for chaining
     */
    public UiModifier weight(float weight) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the alpha and returns this UI modifier.
     *
     * @param alpha the alpha
     * @return this UI modifier for chaining
     */
    public UiModifier alpha(float alpha) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the offset and returns this UI modifier.
     *
     * @param offsetX the offset x
     * @param offsetY the offset y
     * @return this UI modifier for chaining
     */
    public UiModifier offset(float offsetX, float offsetY) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the scale and returns this UI modifier.
     *
     * @param scale the scale
     * @return this UI modifier for chaining
     */
    public UiModifier scale(float scale) {
        return scale(scale, scale);
    }

    /**
     * Sets the scale and returns this UI modifier.
     *
     * @param scaleX the scale x
     * @param scaleY the scale y
     * @return this UI modifier for chaining
     */
    public UiModifier scale(float scaleX, float scaleY) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the rotation and returns this UI modifier.
     *
     * @param rotation the rotation
     * @return this UI modifier for chaining
     */
    public UiModifier rotation(float rotation) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the align and returns this UI modifier.
     *
     * @param align the align
     * @return this UI modifier for chaining
     */
    public UiModifier align(UiAlign align) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the enabled and returns this UI modifier.
     *
     * @param enabled the enabled
     * @return this UI modifier for chaining
     */
    public UiModifier enabled(boolean enabled) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the focusable and returns this UI modifier.
     *
     * @param focusable the focusable
     * @return this UI modifier for chaining
     */
    public UiModifier focusable(boolean focusable) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the style and returns this UI modifier.
     *
     * @param style the style
     * @return this UI modifier for chaining
     */
    public UiModifier style(String style) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the transition and returns this UI modifier.
     *
     * @param state the state
     * @param transition the transition
     * @return this UI modifier for chaining
     */
    public UiModifier transition(String state, UiTransition transition) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                state, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the semantic label and returns this UI modifier.
     *
     * @param semanticLabel the semantic label
     * @return this UI modifier for chaining
     */
    public UiModifier semanticLabel(String semanticLabel) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the validation ID and returns this UI modifier.
     *
     * @param validationId the validation ID
     * @return this UI modifier for chaining
     */
    public UiModifier validationId(String validationId) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation);
    }

    /**
     * Sets the tooltip target and returns this UI modifier.
     *
     * @param tooltipTarget the tooltip target
     * @return this UI modifier for chaining
     */
    public UiModifier tooltipTarget(String tooltipTarget) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation, placementAnimation,
                tooltipTarget);
    }

    /**
     * Returns the animate content size.
     *
     * @return this UI modifier for chaining
     */
    public UiModifier animateContentSize() {
        return animateContentSize(UiAnimationSpec.defaultSpec());
    }

    /**
     * Sets the animate content size and returns this UI modifier.
     *
     * @param animation the animation
     * @return this UI modifier for chaining
     */
    public UiModifier animateContentSize(UiAnimationSpec animation) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, animation != null ? animation : UiAnimationSpec.defaultSpec(),
                placementAnimation);
    }

    /**
     * Returns the animate item placement.
     *
     * @return this UI modifier for chaining
     */
    public UiModifier animateItemPlacement() {
        return animateItemPlacement(UiAnimationSpec.defaultSpec());
    }

    /**
     * Sets the animate item placement and returns this UI modifier.
     *
     * @param animation the animation
     * @return this UI modifier for chaining
     */
    public UiModifier animateItemPlacement(UiAnimationSpec animation) {
        return copy(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight, padding, margin,
                gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled, focusable, style,
                transitionState, transition, semanticLabel, validationId, contentSizeAnimation,
                animation != null ? animation : UiAnimationSpec.defaultSpec());
    }

    /**
     * Returns whether fill width is enabled or true.
     *
     * @return true if fill width is enabled or true; false otherwise
     */
    public boolean isFillWidth() {
        return fillWidth;
    }

    /**
     * Returns whether fill height is enabled or true.
     *
     * @return true if fill height is enabled or true; false otherwise
     */
    public boolean isFillHeight() {
        return fillHeight;
    }

    /**
     * Returns the width.
     *
     * @return the width
     */
    public float width() {
        return width;
    }

    /**
     * Returns the height.
     *
     * @return the height
     */
    public float height() {
        return height;
    }

    /**
     * Returns the min width.
     *
     * @return the min width
     */
    public float minWidth() {
        return minWidth;
    }

    /**
     * Returns the min height.
     *
     * @return the min height
     */
    public float minHeight() {
        return minHeight;
    }

    /**
     * Returns the max width.
     *
     * @return the max width
     */
    public float maxWidth() {
        return maxWidth;
    }

    /**
     * Returns the max height.
     *
     * @return the max height
     */
    public float maxHeight() {
        return maxHeight;
    }

    /**
     * Returns the padding.
     *
     * @return the padding
     */
    public UiInsets padding() {
        return padding;
    }

    /**
     * Returns the margin.
     *
     * @return the margin
     */
    public UiInsets margin() {
        return margin;
    }

    /**
     * Returns the gap.
     *
     * @return the gap
     */
    public float gap() {
        return gap;
    }

    /**
     * Returns the weight.
     *
     * @return the weight
     */
    public float weight() {
        return weight;
    }

    /**
     * Returns the alpha.
     *
     * @return the alpha
     */
    public float alpha() {
        return alpha;
    }

    /**
     * Returns the offset x.
     *
     * @return the offset x
     */
    public float offsetX() {
        return offsetX;
    }

    /**
     * Returns the offset y.
     *
     * @return the offset y
     */
    public float offsetY() {
        return offsetY;
    }

    /**
     * Returns the scale x.
     *
     * @return the scale x
     */
    public float scaleX() {
        return scaleX;
    }

    /**
     * Returns the scale y.
     *
     * @return the scale y
     */
    public float scaleY() {
        return scaleY;
    }

    /**
     * Returns the rotation.
     *
     * @return the rotation
     */
    public float rotation() {
        return rotation;
    }

    /**
     * Returns the align.
     *
     * @return the align
     */
    public UiAlign align() {
        return align;
    }

    /**
     * Returns the enabled.
     *
     * @return true if enabled succeeds or is active; false otherwise
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Returns the focusable.
     *
     * @return true if focusable succeeds or is active; false otherwise
     */
    public boolean focusable() {
        return focusable;
    }

    /**
     * Returns the style.
     *
     * @return the style
     */
    public String style() {
        return style;
    }

    /**
     * Returns the transition state.
     *
     * @return the transition state
     */
    public String transitionState() {
        return transitionState;
    }

    /**
     * Returns the transition.
     *
     * @return the transition
     */
    public UiTransition transition() {
        return transition;
    }

    /**
     * Returns the semantic label.
     *
     * @return the semantic label
     */
    public String semanticLabel() {
        return semanticLabel;
    }

    /**
     * Returns the validation ID.
     *
     * @return the validation ID
     */
    public String validationId() {
        return validationId;
    }

    /**
     * Returns the tooltip target.
     *
     * @return the tooltip target
     */
    public String tooltipTarget() {
        return tooltipTarget;
    }

    /**
     * Returns the content size animation.
     *
     * @return the content size animation
     */
    public UiAnimationSpec contentSizeAnimation() {
        return contentSizeAnimation;
    }

    /**
     * Returns the placement animation.
     *
     * @return the placement animation
     */
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
        if (this.fillWidth == fillWidth
                && this.fillHeight == fillHeight
                && Float.compare(this.width, width) == 0
                && Float.compare(this.height, height) == 0
                && Float.compare(this.minWidth, minWidth) == 0
                && Float.compare(this.minHeight, minHeight) == 0
                && Float.compare(this.maxWidth, maxWidth) == 0
                && Float.compare(this.maxHeight, maxHeight) == 0
                && this.padding == padding
                && this.margin == margin
                && Float.compare(this.gap, gap) == 0
                && Float.compare(this.weight, weight) == 0
                && Float.compare(this.alpha, alpha) == 0
                && Float.compare(this.offsetX, offsetX) == 0
                && Float.compare(this.offsetY, offsetY) == 0
                && Float.compare(this.scaleX, scaleX) == 0
                && Float.compare(this.scaleY, scaleY) == 0
                && Float.compare(this.rotation, rotation) == 0
                && this.align == align
                && this.enabled == enabled
                && this.focusable == focusable
                && this.style == style
                && this.transitionState == transitionState
                && this.transition == transition
                && this.semanticLabel == semanticLabel
                && this.validationId == validationId
                && this.contentSizeAnimation == contentSizeAnimation
                && this.placementAnimation == placementAnimation
                && this.tooltipTarget == tooltipTarget) {
            return this;
        }
        return new UiModifier(fillWidth, fillHeight, width, height, minWidth, minHeight, maxWidth, maxHeight,
                padding, margin, gap, weight, alpha, offsetX, offsetY, scaleX, scaleY, rotation, align, enabled,
                focusable, style, transitionState, transition, semanticLabel, validationId, contentSizeAnimation,
                placementAnimation, tooltipTarget);
    }
}
