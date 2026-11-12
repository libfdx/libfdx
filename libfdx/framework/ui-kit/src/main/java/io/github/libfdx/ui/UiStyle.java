package io.github.libfdx.ui;

/**
 * Stores style values for an ui.
 *
 * @author xpenatan
 */
public final class UiStyle {
    private static final UiStyle DEFAULT = new UiStyle(UiDrawable.none(), UiDrawable.none(),
            UiTextStyle.text(), UiInsets.ZERO, UiInsets.ZERO, new UiSize(0.0f, 0.0f), null, null, null, null,
            UiShape.rectangle(), UiDrawable.none(), null);

    private final UiDrawable background;
    private final UiDrawable foreground;
    private final UiTextStyle textStyle;
    private final UiInsets padding;
    private final UiInsets margin;
    private final UiSize minimumSize;
    private final UiStyle hover;
    private final UiStyle pressed;
    private final UiStyle focused;
    private final UiStyle disabled;
    private final UiShape shape;
    private final UiDrawable thumb;
    private final UiPressAnimation pressAnimation;

    private UiStyle(UiDrawable background, UiDrawable foreground, UiTextStyle textStyle, UiInsets padding,
            UiInsets margin, UiSize minimumSize, UiStyle hover, UiStyle pressed, UiStyle focused, UiStyle disabled,
            UiShape shape, UiDrawable thumb, UiPressAnimation pressAnimation) {
        this.background = background != null ? background : UiDrawable.none();
        this.foreground = foreground != null ? foreground : UiDrawable.none();
        this.textStyle = textStyle != null ? textStyle : UiTextStyle.text();
        this.padding = padding != null ? padding : UiInsets.ZERO;
        this.margin = margin != null ? margin : UiInsets.ZERO;
        this.minimumSize = minimumSize != null ? minimumSize : new UiSize(0.0f, 0.0f);
        this.hover = hover;
        this.pressed = pressed;
        this.focused = focused;
        this.disabled = disabled;
        this.shape = shape;
        this.thumb = thumb;
        this.pressAnimation = pressAnimation;
    }

    /** Sets retained button press feedback, or null to disable it. Use on the button's base style. */
    public UiStyle pressAnimation(UiPressAnimation animation) {
        return new UiStyle(background, foreground, textStyle, padding, margin, minimumSize,
                hover, pressed, focused, disabled, shape, thumb, animation);
    }

    public UiPressAnimation pressAnimation() {
        return pressAnimation;
    }

    /**
     * Sets surface and hit-test geometry. The base style's shape also applies in interaction states.
     * The clip modifier additionally clips content to this shape.
     */
    public UiStyle shape(UiShape shape) {
        return new UiStyle(background, foreground, textStyle, padding, margin, minimumSize,
                hover, pressed, focused, disabled, shape != null ? shape : UiShape.rectangle(), thumb, pressAnimation);
    }

    public UiShape shape() {
        return shape;
    }

    /** Sets the slider or switch thumb. A null or NONE drawable keeps its default appearance. */
    public UiStyle thumb(UiDrawable thumb) {
        return new UiStyle(background, foreground, textStyle, padding, margin, minimumSize,
                hover, pressed, focused, disabled, shape, thumb != null ? thumb : UiDrawable.none(), pressAnimation);
    }

    public UiDrawable thumb() {
        return thumb;
    }

    /**
     * Creates an UI style.
     *
     * @return a new UI style
     */
    public static UiStyle style() {
        return DEFAULT;
    }

    /**
     * Creates an UI style.
     *
     * @return a new UI style
     */
    public static UiStyle button() {
        return DEFAULT.padding(10.0f, 6.0f)
                .text(UiTextStyle.text().wrap(false).ellipsis(true));
    }

    /**
     * Sets the background and returns this UI style.
     *
     * @param background the background
     * @return this UI style for chaining
     */
    public UiStyle background(UiDrawable background) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    /**
     * Sets the foreground and returns this UI style.
     *
     * @param foreground the foreground
     * @return this UI style for chaining
     */
    public UiStyle foreground(UiDrawable foreground) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    /**
     * Sets the text and returns this UI style.
     *
     * @param textStyle the text style
     * @return this UI style for chaining
     */
    public UiStyle text(UiTextStyle textStyle) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    /**
     * Sets the padding and returns this UI style.
     *
     * @param all the all
     * @return this UI style for chaining
     */
    public UiStyle padding(float all) {
        return padding(UiInsets.of(all));
    }

    /**
     * Sets the padding and returns this UI style.
     *
     * @param horizontal the horizontal
     * @param vertical the vertical
     * @return this UI style for chaining
     */
    public UiStyle padding(float horizontal, float vertical) {
        return padding(UiInsets.of(horizontal, vertical));
    }

    /**
     * Sets the padding and returns this UI style.
     *
     * @param padding the padding
     * @return this UI style for chaining
     */
    public UiStyle padding(UiInsets padding) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    /**
     * Sets the margin and returns this UI style.
     *
     * @param margin the margin
     * @return this UI style for chaining
     */
    public UiStyle margin(UiInsets margin) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    /**
     * Sets the minimum size and returns this UI style.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return this UI style for chaining
     */
    public UiStyle minimumSize(float width, float height) {
        return copy(background, foreground, textStyle, padding, margin, new UiSize(width, height), hover, pressed,
                focused, disabled);
    }

    /**
     * Sets the hover and returns this UI style.
     *
     * @param hover the hover
     * @return this UI style for chaining
     */
    public UiStyle hover(UiStyle hover) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    /**
     * Sets the pressed and returns this UI style.
     *
     * @param pressed the pressed
     * @return this UI style for chaining
     */
    public UiStyle pressed(UiStyle pressed) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    /**
     * Sets the focused and returns this UI style.
     *
     * @param focused the focused
     * @return this UI style for chaining
     */
    public UiStyle focused(UiStyle focused) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    /**
     * Sets the disabled and returns this UI style.
     *
     * @param disabled the disabled
     * @return this UI style for chaining
     */
    public UiStyle disabled(UiStyle disabled) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    /**
     * Returns the background.
     *
     * @return the background
     */
    public UiDrawable background() {
        return background;
    }

    /**
     * Returns the foreground.
     *
     * @return the foreground
     */
    public UiDrawable foreground() {
        return foreground;
    }

    /**
     * Returns the text style.
     *
     * @return the text style
     */
    public UiTextStyle textStyle() {
        return textStyle;
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
     * Returns the minimum size.
     *
     * @return the minimum size
     */
    public UiSize minimumSize() {
        return minimumSize;
    }

    /**
     * Returns the hover.
     *
     * @return this UI style for chaining
     */
    public UiStyle hover() {
        return hover;
    }

    /**
     * Returns the pressed.
     *
     * @return this UI style for chaining
     */
    public UiStyle pressed() {
        return pressed;
    }

    /**
     * Returns the focused.
     *
     * @return this UI style for chaining
     */
    public UiStyle focused() {
        return focused;
    }

    /**
     * Returns the disabled.
     *
     * @return this UI style for chaining
     */
    public UiStyle disabled() {
        return disabled;
    }

    private UiStyle copy(UiDrawable background, UiDrawable foreground, UiTextStyle textStyle, UiInsets padding,
            UiInsets margin, UiSize minimumSize, UiStyle hover, UiStyle pressed, UiStyle focused, UiStyle disabled) {
        return new UiStyle(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled, shape, thumb, pressAnimation);
    }
}
