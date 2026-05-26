package io.github.libfdx.ui;

public final class UiStyle {
    private static final UiStyle DEFAULT = new UiStyle(UiDrawable.none(), UiDrawable.none(),
            UiTextStyle.text(), UiInsets.ZERO, UiInsets.ZERO, new UiSize(0.0f, 0.0f), null, null, null, null);

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

    private UiStyle(UiDrawable background, UiDrawable foreground, UiTextStyle textStyle, UiInsets padding,
            UiInsets margin, UiSize minimumSize, UiStyle hover, UiStyle pressed, UiStyle focused, UiStyle disabled) {
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
    }

    public static UiStyle style() {
        return DEFAULT;
    }

    public static UiStyle button() {
        return DEFAULT.padding(10.0f, 6.0f);
    }

    public UiStyle background(UiDrawable background) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    public UiStyle foreground(UiDrawable foreground) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    public UiStyle text(UiTextStyle textStyle) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    public UiStyle padding(float all) {
        return padding(UiInsets.of(all));
    }

    public UiStyle padding(float horizontal, float vertical) {
        return padding(UiInsets.of(horizontal, vertical));
    }

    public UiStyle padding(UiInsets padding) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    public UiStyle margin(UiInsets margin) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    public UiStyle minimumSize(float width, float height) {
        return copy(background, foreground, textStyle, padding, margin, new UiSize(width, height), hover, pressed,
                focused, disabled);
    }

    public UiStyle hover(UiStyle hover) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    public UiStyle pressed(UiStyle pressed) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    public UiStyle focused(UiStyle focused) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    public UiStyle disabled(UiStyle disabled) {
        return copy(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }

    public UiDrawable background() {
        return background;
    }

    public UiDrawable foreground() {
        return foreground;
    }

    public UiTextStyle textStyle() {
        return textStyle;
    }

    public UiInsets padding() {
        return padding;
    }

    public UiInsets margin() {
        return margin;
    }

    public UiSize minimumSize() {
        return minimumSize;
    }

    public UiStyle hover() {
        return hover;
    }

    public UiStyle pressed() {
        return pressed;
    }

    public UiStyle focused() {
        return focused;
    }

    public UiStyle disabled() {
        return disabled;
    }

    private UiStyle copy(UiDrawable background, UiDrawable foreground, UiTextStyle textStyle, UiInsets padding,
            UiInsets margin, UiSize minimumSize, UiStyle hover, UiStyle pressed, UiStyle focused, UiStyle disabled) {
        return new UiStyle(background, foreground, textStyle, padding, margin, minimumSize, hover, pressed, focused,
                disabled);
    }
}
