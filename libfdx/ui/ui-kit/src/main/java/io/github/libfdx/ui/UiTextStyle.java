package io.github.libfdx.ui;

/**
 * Stores style values for an ui text.
 *
 * @author xpenatan
 */
public final class UiTextStyle {
    private static final UiTextStyle DEFAULT = new UiTextStyle(UiFont.family("default", 16.0f), 16.0f,
            UiColor.WHITE, 20.0f, UiTextAlign.START, true, false, UiColor.TRANSPARENT, 0.0f,
            UiColor.TRANSPARENT, 0.0f);

    private final UiFont font;
    private final float size;
    private final UiColor color;
    private final float lineHeight;
    private final UiTextAlign align;
    private final boolean wrap;
    private final boolean ellipsis;
    private final UiColor shadowColor;
    private final float shadowOffset;
    private final UiColor outlineColor;
    private final float outlineWidth;

    private UiTextStyle(UiFont font, float size, UiColor color, float lineHeight, UiTextAlign align, boolean wrap,
            boolean ellipsis, UiColor shadowColor, float shadowOffset, UiColor outlineColor, float outlineWidth) {
        this.font = font != null ? font : UiFont.family("default", size);
        this.size = size;
        this.color = color != null ? color : UiColor.WHITE;
        this.lineHeight = lineHeight;
        this.align = align != null ? align : UiTextAlign.START;
        this.wrap = wrap;
        this.ellipsis = ellipsis;
        this.shadowColor = shadowColor != null ? shadowColor : UiColor.TRANSPARENT;
        this.shadowOffset = shadowOffset;
        this.outlineColor = outlineColor != null ? outlineColor : UiColor.TRANSPARENT;
        this.outlineWidth = outlineWidth;
    }

    /**
     * Creates an UI text style.
     *
     * @return a new UI text style
     */
    public static UiTextStyle text() {
        return DEFAULT;
    }

    /**
     * Sets the font and returns this UI text style.
     *
     * @param font the font
     * @return this UI text style for chaining
     */
    public UiTextStyle font(UiFont font) {
        return new UiTextStyle(font, size, color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                outlineColor, outlineWidth);
    }

    /**
     * Sets the size and returns this UI text style.
     *
     * @param size the size
     * @return this UI text style for chaining
     */
    public UiTextStyle size(float size) {
        return new UiTextStyle(font, size, color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                outlineColor, outlineWidth);
    }

    /**
     * Sets the color and returns this UI text style.
     *
     * @param color the color
     * @return this UI text style for chaining
     */
    public UiTextStyle color(UiColor color) {
        return new UiTextStyle(font, size, color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                outlineColor, outlineWidth);
    }

    /**
     * Sets the line height and returns this UI text style.
     *
     * @param lineHeight the line height
     * @return this UI text style for chaining
     */
    public UiTextStyle lineHeight(float lineHeight) {
        return new UiTextStyle(font, size, color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                outlineColor, outlineWidth);
    }

    /**
     * Sets the align and returns this UI text style.
     *
     * @param align the align
     * @return this UI text style for chaining
     */
    public UiTextStyle align(UiTextAlign align) {
        return new UiTextStyle(font, size, color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                outlineColor, outlineWidth);
    }

    /**
     * Sets the wrap and returns this UI text style.
     *
     * @param wrap the wrap
     * @return this UI text style for chaining
     */
    public UiTextStyle wrap(boolean wrap) {
        return new UiTextStyle(font, size, color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                outlineColor, outlineWidth);
    }

    /**
     * Sets the ellipsis and returns this UI text style.
     *
     * @param ellipsis the ellipsis
     * @return this UI text style for chaining
     */
    public UiTextStyle ellipsis(boolean ellipsis) {
        return new UiTextStyle(font, size, color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                outlineColor, outlineWidth);
    }

    /**
     * Sets the shadow and returns this UI text style.
     *
     * @param color the color
     * @param offset the offset
     * @return this UI text style for chaining
     */
    public UiTextStyle shadow(UiColor color, float offset) {
        return new UiTextStyle(font, size, this.color, lineHeight, align, wrap, ellipsis, color, offset, outlineColor,
                outlineWidth);
    }

    /**
     * Sets the outline and returns this UI text style.
     *
     * @param color the color
     * @param width the width in pixels
     * @return this UI text style for chaining
     */
    public UiTextStyle outline(UiColor color, float width) {
        return new UiTextStyle(font, size, this.color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                color, width);
    }

    /**
     * Returns the font.
     *
     * @return the font
     */
    public UiFont font() {
        return font;
    }

    /**
     * Returns the size.
     *
     * @return the size
     */
    public float size() {
        return size;
    }

    /**
     * Returns the color.
     *
     * @return the color
     */
    public UiColor color() {
        return color;
    }

    /**
     * Returns the line height.
     *
     * @return the line height
     */
    public float lineHeight() {
        return lineHeight;
    }

    /**
     * Returns the align.
     *
     * @return the align
     */
    public UiTextAlign align() {
        return align;
    }

    /**
     * Returns the wrap.
     *
     * @return true if wrap succeeds or is active; false otherwise
     */
    public boolean wrap() {
        return wrap;
    }

    /**
     * Returns the ellipsis.
     *
     * @return true if ellipsis succeeds or is active; false otherwise
     */
    public boolean ellipsis() {
        return ellipsis;
    }

    /**
     * Returns the shadow color.
     *
     * @return the shadow color
     */
    public UiColor shadowColor() {
        return shadowColor;
    }

    /**
     * Returns the shadow offset.
     *
     * @return the shadow offset
     */
    public float shadowOffset() {
        return shadowOffset;
    }

    /**
     * Returns the outline color.
     *
     * @return the outline color
     */
    public UiColor outlineColor() {
        return outlineColor;
    }

    /**
     * Returns the outline width.
     *
     * @return the outline width
     */
    public float outlineWidth() {
        return outlineWidth;
    }
}
