package io.github.libfdx.ui;

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

    public static UiTextStyle text() {
        return DEFAULT;
    }

    public UiTextStyle font(UiFont font) {
        return new UiTextStyle(font, size, color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                outlineColor, outlineWidth);
    }

    public UiTextStyle size(float size) {
        return new UiTextStyle(font, size, color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                outlineColor, outlineWidth);
    }

    public UiTextStyle color(UiColor color) {
        return new UiTextStyle(font, size, color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                outlineColor, outlineWidth);
    }

    public UiTextStyle lineHeight(float lineHeight) {
        return new UiTextStyle(font, size, color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                outlineColor, outlineWidth);
    }

    public UiTextStyle align(UiTextAlign align) {
        return new UiTextStyle(font, size, color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                outlineColor, outlineWidth);
    }

    public UiTextStyle wrap(boolean wrap) {
        return new UiTextStyle(font, size, color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                outlineColor, outlineWidth);
    }

    public UiTextStyle ellipsis(boolean ellipsis) {
        return new UiTextStyle(font, size, color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                outlineColor, outlineWidth);
    }

    public UiTextStyle shadow(UiColor color, float offset) {
        return new UiTextStyle(font, size, this.color, lineHeight, align, wrap, ellipsis, color, offset, outlineColor,
                outlineWidth);
    }

    public UiTextStyle outline(UiColor color, float width) {
        return new UiTextStyle(font, size, this.color, lineHeight, align, wrap, ellipsis, shadowColor, shadowOffset,
                color, width);
    }

    public UiFont font() {
        return font;
    }

    public float size() {
        return size;
    }

    public UiColor color() {
        return color;
    }

    public float lineHeight() {
        return lineHeight;
    }

    public UiTextAlign align() {
        return align;
    }

    public boolean wrap() {
        return wrap;
    }

    public boolean ellipsis() {
        return ellipsis;
    }

    public UiColor shadowColor() {
        return shadowColor;
    }

    public float shadowOffset() {
        return shadowOffset;
    }

    public UiColor outlineColor() {
        return outlineColor;
    }

    public float outlineWidth() {
        return outlineWidth;
    }
}
