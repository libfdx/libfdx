package io.github.libfdx.ui;

/**
 * Represents an ui.
 *
 * @author xpenatan
 */
public final class Ui {
    private Ui() {
    }

    /**
     * Returns the modifier.
     *
     * @return the modifier
     */
    public static UiModifier modifier() {
        return UiModifier.none();
    }

    /**
     * Runs the state step.
     *
     * @param value the value
     * @return the state
     */
    public static UiBooleanState state(boolean value) {
        return new UiBooleanState(value);
    }

    /**
     * Runs the state step.
     *
     * @param value the value
     * @return the state
     */
    public static UiIntState state(int value) {
        return new UiIntState(value);
    }

    /**
     * Runs the state step.
     *
     * @param value the value
     * @return the state
     */
    public static UiFloatState state(float value) {
        return new UiFloatState(value);
    }

    /**
     * Runs the state step.
     *
     * @param value the value
     * @return the state
     */
    public static UiLongState state(long value) {
        return new UiLongState(value);
    }

    /**
     * Runs the state step.
     *
     * @param value the value
     * @return the state
     */
    public static UiDoubleState state(double value) {
        return new UiDoubleState(value);
    }

    /**
     * Runs the state step.
     *
     * @param <T> the value type
     * @param value the value
     * @return the state
     */
    public static <T> UiState<T> state(T value) {
        return new UiState<T>(value);
    }

    /**
     * Returns the animation.
     *
     * @return the animation
     */
    public static UiAnimationSpec animation() {
        return UiAnimationSpec.defaultSpec();
    }

    /**
     * Returns the transition.
     *
     * @return the transition
     */
    public static UiTransition transition() {
        return UiTransition.create();
    }

    /**
     * Runs the insets step.
     *
     * @param all the all
     * @return the insets
     */
    public static UiInsets insets(float all) {
        return UiInsets.of(all);
    }

    /**
     * Runs the insets step.
     *
     * @param horizontal the horizontal
     * @param vertical the vertical
     * @return the insets
     */
    public static UiInsets insets(float horizontal, float vertical) {
        return UiInsets.of(horizontal, vertical);
    }

    /**
     * Runs the insets step.
     *
     * @param left the left
     * @param top the top
     * @param right the right
     * @param bottom the bottom
     * @return the insets
     */
    public static UiInsets insets(float left, float top, float right, float bottom) {
        return UiInsets.of(left, top, right, bottom);
    }

    /**
     * Runs the size step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return the size
     */
    public static UiSize size(float width, float height) {
        return new UiSize(width, height);
    }

    /**
     * Runs the RGB step.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @return the RGB
     */
    public static UiColor rgb(float red, float green, float blue) {
        return UiColor.rgb(red, green, blue);
    }

    /**
     * Runs the RGBA step.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     * @return the RGBA
     */
    public static UiColor rgba(float red, float green, float blue, float alpha) {
        return UiColor.rgba(red, green, blue, alpha);
    }

    /**
     * Runs the rgba8888 step.
     *
     * @param rgba the RGBA
     * @return the rgba8888
     */
    public static UiColor rgba8888(int rgba) {
        return UiColor.rgba8888(rgba);
    }

    /**
     * Runs the range step.
     *
     * @param minimum the minimum
     * @param maximum the maximum
     * @return the range
     */
    public static UiRange range(float minimum, float maximum) {
        return new UiRange(minimum, maximum);
    }

    /**
     * Returns the dark theme.
     *
     * @return the dark theme
     */
    public static UiTheme darkTheme() {
        return UiTheme.dark();
    }

    /**
     * Returns the light theme.
     *
     * @return the light theme
     */
    public static UiTheme lightTheme() {
        return UiTheme.light();
    }

    /**
     * Returns the style.
     *
     * @return the style
     */
    public static UiStyle style() {
        return UiStyle.style();
    }

    /**
     * Returns the text style.
     *
     * @return the text style
     */
    public static UiTextStyle textStyle() {
        return UiTextStyle.text();
    }

    /**
     * Runs the tooltip step.
     *
     * @param text the text
     * @return the tooltip
     */
    public static UiTooltip tooltip(String text) {
        return UiTooltip.text(text);
    }

    /**
     * Runs the modal step.
     *
     * @param id the identifier
     * @return the modal
     */
    public static UiModal modal(String id) {
        return UiModal.modal(id);
    }

    /**
     * Runs the popup step.
     *
     * @param id the identifier
     * @return the popup
     */
    public static UiPopup popup(String id) {
        return UiPopup.popup(id);
    }
}
