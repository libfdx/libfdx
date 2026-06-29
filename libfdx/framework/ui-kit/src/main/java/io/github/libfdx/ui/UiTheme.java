package io.github.libfdx.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an ui theme.
 *
 * @author xpenatan
 */
public final class UiTheme {
    private final Map<String, UiStyle> styles;
    private final Map<String, UiDrawable> drawables;
    private final Map<String, UiFont> fonts;
    private final UiColor backgroundColor;
    private final UiColor textColor;

    private UiTheme(Map<String, UiStyle> styles, Map<String, UiDrawable> drawables, Map<String, UiFont> fonts,
            UiColor backgroundColor, UiColor textColor) {
        this.styles = Collections.unmodifiableMap(new LinkedHashMap<String, UiStyle>(styles));
        this.drawables = Collections.unmodifiableMap(new LinkedHashMap<String, UiDrawable>(drawables));
        this.fonts = Collections.unmodifiableMap(new LinkedHashMap<String, UiFont>(fonts));
        this.backgroundColor = backgroundColor != null ? backgroundColor : UiColor.TRANSPARENT;
        this.textColor = textColor != null ? textColor : UiColor.WHITE;
    }

    /**
     * Creates an UI theme.
     *
     * @return a new UI theme
     */
    public static UiTheme light() {
        Map<String, UiStyle> styles = new LinkedHashMap<String, UiStyle>();
        styles.put("text", UiStyle.style().text(UiTextStyle.text().color(UiColor.rgba8888(0x20242cff))));
        styles.put("button", UiStyle.button().background(UiDrawable.color(UiColor.rgba8888(0xe8ebf0ff))));
        styles.put("panel", UiStyle.style().padding(12.0f).background(UiDrawable.color(UiColor.rgba8888(0xf7f8faff))));
        styles.put("window", UiStyle.style().padding(UiInsets.of(12.0f, 38.0f, 12.0f, 12.0f))
                .background(UiDrawable.color(UiColor.rgba8888(0xf7f8faff))));
        styles.put("text-field", UiStyle.style().padding(8.0f, 4.0f).background(UiDrawable.color(UiColor.WHITE)));
        styles.put("text-area", UiStyle.style().padding(8.0f, 6.0f).background(UiDrawable.color(UiColor.WHITE)));
        styles.put("tabs", UiStyle.style().padding(4.0f).background(UiDrawable.color(UiColor.rgba8888(0xe8ebf0ff))));
        return new UiTheme(styles, new LinkedHashMap<String, UiDrawable>(), new LinkedHashMap<String, UiFont>(),
                UiColor.WHITE, UiColor.rgba8888(0x20242cff));
    }

    /**
     * Creates an UI theme.
     *
     * @return a new UI theme
     */
    public static UiTheme dark() {
        Map<String, UiStyle> styles = new LinkedHashMap<String, UiStyle>();
        styles.put("text", UiStyle.style().text(UiTextStyle.text().color(UiColor.rgba8888(0xf2f4f8ff))));
        styles.put("button", UiStyle.button().background(UiDrawable.color(UiColor.rgba8888(0x343b46ff))));
        styles.put("panel", UiStyle.style().padding(12.0f).background(UiDrawable.color(UiColor.rgba8888(0x20242cff))));
        styles.put("window", UiStyle.style().padding(UiInsets.of(12.0f, 38.0f, 12.0f, 12.0f))
                .background(UiDrawable.color(UiColor.rgba8888(0x20242cff))));
        styles.put("text-field", UiStyle.style().padding(8.0f, 4.0f).background(UiDrawable.color(UiColor.rgba8888(0x10141bff))));
        styles.put("text-area", UiStyle.style().padding(8.0f, 6.0f).background(UiDrawable.color(UiColor.rgba8888(0x10141bff))));
        styles.put("tabs", UiStyle.style().padding(4.0f).background(UiDrawable.color(UiColor.rgba8888(0x10141bff))));
        return new UiTheme(styles, new LinkedHashMap<String, UiDrawable>(), new LinkedHashMap<String, UiFont>(),
                UiColor.rgba8888(0x10141bff), UiColor.rgba8888(0xf2f4f8ff));
    }

    /**
     * Sets the style and returns this UI theme.
     *
     * @param name the name
     * @param style the style
     * @return this UI theme for chaining
     */
    public UiTheme style(String name, UiStyle style) {
        Map<String, UiStyle> next = new LinkedHashMap<String, UiStyle>(styles);
        next.put(name, style);
        return new UiTheme(next, drawables, fonts, backgroundColor, textColor);
    }

    /**
     * Sets the button and returns this UI theme.
     *
     * @param style the style
     * @return this UI theme for chaining
     */
    public UiTheme button(UiStyle style) {
        return style("button", style);
    }

    /**
     * Sets the panel and returns this UI theme.
     *
     * @param style the style
     * @return this UI theme for chaining
     */
    public UiTheme panel(UiStyle style) {
        return style("panel", style);
    }

    /**
     * Sets the window and returns this UI theme.
     *
     * @param style the style
     * @return this UI theme for chaining
     */
    public UiTheme window(UiStyle style) {
        return style("window", style);
    }

    /**
     * Sets the text and returns this UI theme.
     *
     * @param style the style
     * @return this UI theme for chaining
     */
    public UiTheme text(UiStyle style) {
        return style("text", style);
    }

    /**
     * Sets the text field and returns this UI theme.
     *
     * @param style the style
     * @return this UI theme for chaining
     */
    public UiTheme textField(UiStyle style) {
        return style("text-field", style);
    }

    /**
     * Sets the text area and returns this UI theme.
     *
     * @param style the style
     * @return this UI theme for chaining
     */
    public UiTheme textArea(UiStyle style) {
        return style("text-area", style);
    }

    /**
     * Sets the tabs and returns this UI theme.
     *
     * @param style the style
     * @return this UI theme for chaining
     */
    public UiTheme tabs(UiStyle style) {
        return style("tabs", style);
    }

    /**
     * Sets the drawable and returns this UI theme.
     *
     * @param name the name
     * @param drawable the drawable
     * @return this UI theme for chaining
     */
    public UiTheme drawable(String name, UiDrawable drawable) {
        Map<String, UiDrawable> next = new LinkedHashMap<String, UiDrawable>(drawables);
        next.put(name, drawable);
        return new UiTheme(styles, next, fonts, backgroundColor, textColor);
    }

    /**
     * Sets the font and returns this UI theme.
     *
     * @param name the name
     * @param font the font
     * @return this UI theme for chaining
     */
    public UiTheme font(String name, UiFont font) {
        Map<String, UiFont> next = new LinkedHashMap<String, UiFont>(fonts);
        next.put(name, font);
        return new UiTheme(styles, drawables, next, backgroundColor, textColor);
    }

    /**
     * Sets the colors and returns this UI theme.
     *
     * @param backgroundColor the background color
     * @param textColor the text color
     * @return this UI theme for chaining
     */
    public UiTheme colors(UiColor backgroundColor, UiColor textColor) {
        return new UiTheme(styles, drawables, fonts, backgroundColor, textColor);
    }

    /**
     * Runs the style step.
     *
     * @param name the name
     * @return the style
     */
    public UiStyle style(String name) {
        return styles.get(name);
    }

    /**
     * Draws able.
     *
     * @param name the name
     * @return the drawable
     */
    public UiDrawable drawable(String name) {
        return drawables.get(name);
    }

    /**
     * Runs the font step.
     *
     * @param name the name
     * @return the font
     */
    public UiFont font(String name) {
        return fonts.get(name);
    }

    /**
     * Returns the styles.
     *
     * @return the styles
     */
    public Map<String, UiStyle> styles() {
        return styles;
    }

    /**
     * Returns the background color.
     *
     * @return the background color
     */
    public UiColor backgroundColor() {
        return backgroundColor;
    }

    /**
     * Returns the text color.
     *
     * @return the text color
     */
    public UiColor textColor() {
        return textColor;
    }
}
