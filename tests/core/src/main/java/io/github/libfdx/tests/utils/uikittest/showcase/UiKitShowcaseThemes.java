package io.github.libfdx.tests.utils.uikittest.showcase;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiFont;
import io.github.libfdx.ui.UiNinePatch;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTextStyle;
import io.github.libfdx.ui.UiTheme;

/** Defines the showcase palettes and verifies their text contrast. */
public final class UiKitShowcaseThemes {
    private static final String FREETYPE_FONT_ASSET = "font/freetype/lsans.ttf";
    static final String[] THEME_NAMES = new String[] {
            "Graphite",
            "Porcelain",
            "Cobalt",
            "Aubergine",
            "High contrast"
    };

    private UiKitShowcaseThemes() {
    }

    public static int count() {
        return THEME_NAMES.length;
    }

    public static String name(int index) {
        return THEME_NAMES[index];
    }

    static UiTheme build(int index, TextureRegion panelPatchRegion) {
        int textColor = 0xe8eaedff;
        int mutedColor = 0xa6abb4ff;
        int titleColor = 0xf7f8faff;
        int accentTextColor = 0xffffffff;
        int buttonColor = 0x343840ff;
        int buttonHoverColor = 0x424751ff;
        int buttonPressedColor = 0x5b6472ff;
        int buttonDisabledColor = 0x26292fff;
        int disabledTextColor = 0x797e87ff;
        int panelColor = 0x202329ff;
        int sectionPanelColor = 0x1b1e23ff;
        int navColor = 0x15171bff;
        int fieldColor = 0x111317ff;
        int fieldFocusColor = 0x262a31ff;
        int tileColor = 0x1a1d22ff;
        int stageColor = 0x111318ff;
        int popupColor = 0x292d34ff;
        int tooltipColor = 0x0d0f12f2;
        int accentColor = 0x667085ff;
        int accentHoverColor = 0x6c7685ff;
        int accentPressedColor = 0x555d69ff;
        int controlColor = 0x343941ff;
        int progressColor = 0x9aa3afff;
        int dividerColor = 0x454a53ff;
        boolean light = false;
        boolean useNinePatch = index == 0;

        if (index == 1) {
            textColor = 0x22252aff;
            mutedColor = 0x69707aff;
            titleColor = 0x15171aff;
            accentTextColor = 0xffffffff;
            buttonColor = 0xe4e7ebff;
            buttonHoverColor = 0xd8dde3ff;
            buttonPressedColor = 0xb9c2ceff;
            buttonDisabledColor = 0xf1f2f4ff;
            disabledTextColor = 0x8b929cff;
            panelColor = 0xf7f8faff;
            sectionPanelColor = 0xf0f2f5ff;
            navColor = 0xe9ecf0ff;
            fieldColor = 0xffffffff;
            fieldFocusColor = 0xe9eff8ff;
            tileColor = 0xffffffff;
            stageColor = 0xedeff2ff;
            popupColor = 0xffffffff;
            tooltipColor = 0x1b1d21f2;
            accentColor = 0x315c9dff;
            accentHoverColor = 0x3e6cb0ff;
            accentPressedColor = 0x274c83ff;
            controlColor = 0xc9cfd7ff;
            progressColor = 0x5a73a5ff;
            dividerColor = 0xb8bec7ff;
            light = true;
        } else if (index == 2) {
            textColor = 0xe9edfbff;
            mutedColor = 0xa3acc4ff;
            titleColor = 0xf8faffff;
            accentTextColor = 0xffffffff;
            buttonColor = 0x2c3345ff;
            buttonHoverColor = 0x39445bff;
            buttonPressedColor = 0x4169b2ff;
            buttonDisabledColor = 0x222736ff;
            disabledTextColor = 0x747d95ff;
            panelColor = 0x1b2130ff;
            sectionPanelColor = 0x171c29ff;
            navColor = 0x111620ff;
            fieldColor = 0x0d111aff;
            fieldFocusColor = 0x1e2a42ff;
            tileColor = 0x171d2bff;
            stageColor = 0x0d121cff;
            popupColor = 0x252d42ff;
            tooltipColor = 0x0a0e16f2;
            accentColor = 0x4668b5ff;
            accentHoverColor = 0x4c6fbeff;
            accentPressedColor = 0x3a5798ff;
            controlColor = 0x30394eff;
            progressColor = 0x7d9be5ff;
            dividerColor = 0x424d66ff;
        } else if (index == 3) {
            textColor = 0xf3edf5ff;
            mutedColor = 0xbdafc1ff;
            titleColor = 0xfffaffff;
            accentTextColor = 0xffffffff;
            buttonColor = 0x443746ff;
            buttonHoverColor = 0x59465bff;
            buttonPressedColor = 0x744b78ff;
            buttonDisabledColor = 0x302833ff;
            disabledTextColor = 0x827585ff;
            panelColor = 0x2b222dff;
            sectionPanelColor = 0x251d27ff;
            navColor = 0x1c171eff;
            fieldColor = 0x161117ff;
            fieldFocusColor = 0x332538ff;
            tileColor = 0x241c26ff;
            stageColor = 0x171218ff;
            popupColor = 0x3a2d3dff;
            tooltipColor = 0x130f14f2;
            accentColor = 0x81558dff;
            accentHoverColor = 0x8e609aff;
            accentPressedColor = 0x6b4475ff;
            controlColor = 0x4a3a4dff;
            progressColor = 0xb987c5ff;
            dividerColor = 0x5e4b62ff;
        } else if (index == 4) {
            textColor = 0xffffffff;
            mutedColor = 0xe6e6e6ff;
            titleColor = 0xffffffff;
            accentTextColor = 0x000000ff;
            buttonColor = 0x242424ff;
            buttonHoverColor = 0x3d3d3dff;
            buttonPressedColor = 0x005fccff;
            buttonDisabledColor = 0x171717ff;
            disabledTextColor = 0x999999ff;
            panelColor = 0x080808ff;
            sectionPanelColor = 0x000000ff;
            navColor = 0x0d0d0dff;
            fieldColor = 0x000000ff;
            fieldFocusColor = 0x292300ff;
            tileColor = 0x121212ff;
            stageColor = 0x000000ff;
            popupColor = 0x1a1a1aff;
            tooltipColor = 0x000000f5;
            accentColor = 0xffd400ff;
            accentHoverColor = 0xffe45cff;
            accentPressedColor = 0xd6b300ff;
            controlColor = 0x333333ff;
            progressColor = 0x00afffff;
            dividerColor = 0xffffffff;
        }

        validatePaletteContrast(index,
                textColor, mutedColor, titleColor, accentTextColor,
                panelColor, navColor,
                buttonColor, buttonHoverColor, buttonPressedColor,
                accentColor, accentHoverColor, accentPressedColor);

        UiTextStyle text = textStyle(16.0f, textColor).lineHeight(20.0f);
        UiTextStyle muted = textStyle(13.0f, mutedColor).lineHeight(17.0f);
        UiTextStyle title = textStyle(22.0f, titleColor).lineHeight(26.0f);
        UiTextStyle buttonText = textStyle(15.0f, titleColor).lineHeight(19.0f)
                .wrap(false)
                .ellipsis(true);
        UiTextStyle disabledButtonText = textStyle(15.0f, disabledTextColor).lineHeight(19.0f)
                .wrap(false)
                .ellipsis(true);
        UiTextStyle activeButtonText = textStyle(15.0f, accentTextColor).lineHeight(19.0f)
                .wrap(false)
                .ellipsis(true);
        UiStyle button = UiStyle.button()
                .text(buttonText)
                .background(UiDrawable.color(UiColor.rgba8888(buttonColor)))
                .hover(UiStyle.button().text(buttonText)
                        .background(UiDrawable.color(UiColor.rgba8888(buttonHoverColor))))
                .pressed(UiStyle.button().text(buttonText)
                        .background(UiDrawable.color(UiColor.rgba8888(buttonPressedColor))))
                .disabled(UiStyle.button()
                        .text(disabledButtonText)
                        .background(UiDrawable.color(UiColor.rgba8888(buttonDisabledColor))));
        UiStyle textField = UiStyle.style()
                .padding(10.0f, 6.0f)
                .text(text.wrap(false).ellipsis(true))
                .background(UiDrawable.color(UiColor.rgba8888(fieldColor)));
        UiStyle textArea = UiStyle.style()
                .padding(10.0f, 8.0f)
                .text(text.wrap(false).ellipsis(false))
                .background(UiDrawable.color(UiColor.rgba8888(fieldColor)));
        UiStyle ninePanel = UiStyle.style()
                .padding(12.0f)
                .text(text)
                .background(useNinePatch
                        ? UiDrawable.ninePatch(UiNinePatch.region(panelPatchRegion,
                                Ui.insets(5.0f), Ui.insets(7.0f)))
                        : UiDrawable.color(UiColor.rgba8888(panelColor)));
        UiStyle window = UiStyle.style()
                .padding(Ui.insets(14.0f, 42.0f, 14.0f, 14.0f))
                .text(text)
                .background(UiDrawable.color(UiColor.rgba8888(panelColor)));
        UiStyle control = UiStyle.style()
                .background(UiDrawable.color(UiColor.rgba8888(controlColor)))
                .foreground(UiDrawable.color(UiColor.rgba8888(accentColor)))
                .text(text.wrap(false).ellipsis(true));
        UiStyle progress = UiStyle.style()
                .background(UiDrawable.color(UiColor.rgba8888(controlColor)))
                .foreground(UiDrawable.color(UiColor.rgba8888(progressColor)))
                .text(text);

        return (light ? Ui.lightTheme() : Ui.darkTheme())
                .colors(UiColor.rgba8888(navColor), UiColor.rgba8888(textColor))
                .text(UiStyle.style().text(text))
                .button(button)
                .checkbox(control)
                .toggleSwitch(control)
                .radioButton(control)
                .slider(control)
                .progressBar(progress)
                .loadingIndicator(control)
                .divider(UiStyle.style()
                        .foreground(UiDrawable.color(UiColor.rgba8888(dividerColor))))
                .collapseBar(control.padding(8.0f))
                .panel(UiStyle.style().padding(12.0f).text(text)
                        .background(UiDrawable.color(UiColor.rgba8888(panelColor))))
                .window(window)
                .tabs(UiStyle.style().padding(4.0f).text(textStyle(14.0f, textColor).lineHeight(18.0f))
                        .background(UiDrawable.color(UiColor.rgba8888(navColor)))
                        .foreground(UiDrawable.color(UiColor.rgba8888(accentColor))))
                .textField(textField.focused(textField
                        .background(UiDrawable.color(UiColor.rgba8888(fieldFocusColor)))))
                .style("text-area", textArea.focused(textArea
                        .background(UiDrawable.color(UiColor.rgba8888(fieldFocusColor)))))
                .style("title", UiStyle.style().text(title))
                .style("section", UiStyle.style().text(textStyle(15.0f, titleColor).lineHeight(19.0f)))
                .style("muted", UiStyle.style().text(muted))
                .style("small", UiStyle.style().text(textStyle(12.0f, mutedColor).lineHeight(16.0f)))
                .style("metric", UiStyle.style().text(textStyle(16.0f, accentColor).lineHeight(20.0f)))
                .style("nine-panel", ninePanel)
                .style("section-panel", UiStyle.style().padding(12.0f).text(text)
                        .background(UiDrawable.color(UiColor.rgba8888(sectionPanelColor))))
                .style("nav-panel", UiStyle.style().padding(10.0f).text(text)
                        .background(UiDrawable.color(UiColor.rgba8888(navColor))))
                .style("nav-active", UiStyle.button()
                        .text(activeButtonText)
                        .background(UiDrawable.color(UiColor.rgba8888(accentColor)))
                        .hover(UiStyle.button().text(activeButtonText)
                                .background(UiDrawable.color(UiColor.rgba8888(accentHoverColor))))
                        .pressed(UiStyle.button().text(activeButtonText)
                                .background(UiDrawable.color(UiColor.rgba8888(accentPressedColor)))))
                .style("tile", UiStyle.style().padding(10.0f).text(text)
                        .background(UiDrawable.color(UiColor.rgba8888(tileColor))))
                .style("stage", UiStyle.style().background(UiDrawable.color(UiColor.rgba8888(stageColor))))
                .style("scroll", UiStyle.style().background(UiDrawable.color(UiColor.rgba8888(stageColor))))
                .style("popup-panel", UiStyle.style().padding(10.0f).text(text)
                        .background(UiDrawable.color(UiColor.rgba8888(popupColor))))
                .style("tooltip-panel", UiStyle.style().padding(8.0f).text(muted)
                        .background(UiDrawable.color(UiColor.rgba8888(tooltipColor))));
    }

    public static UiTextStyle textStyle(float size, int rgba) {
        return UiTextStyle.text()
                .font(UiFont.freeType(FREETYPE_FONT_ASSET, size))
                .size(size)
                .lineHeight(size + 3.0f)
                .color(UiColor.rgba8888(rgba));
    }

    private static void validatePaletteContrast(int index,
            int textColor, int mutedColor, int titleColor, int accentTextColor,
            int panelColor, int navColor,
            int buttonColor, int buttonHoverColor, int buttonPressedColor,
            int accentColor, int accentHoverColor, int accentPressedColor) {
        String name = THEME_NAMES[index];
        assertContrast(name + " text on panel", textColor, panelColor);
        assertContrast(name + " muted text on panel", mutedColor, panelColor);
        assertContrast(name + " navigation text", textColor, navColor);
        assertContrast(name + " button text", titleColor, buttonColor);
        assertContrast(name + " hovered button text", titleColor, buttonHoverColor);
        assertContrast(name + " pressed button text", titleColor, buttonPressedColor);
        assertContrast(name + " selected text", accentTextColor, accentColor);
        assertContrast(name + " hovered selected text", accentTextColor, accentHoverColor);
        assertContrast(name + " pressed selected text", accentTextColor, accentPressedColor);
    }

    private static void assertContrast(String label, int foreground, int background) {
        double foregroundLuminance = relativeLuminance(foreground);
        double backgroundLuminance = relativeLuminance(background);
        double lighter = Math.max(foregroundLuminance, backgroundLuminance);
        double darker = Math.min(foregroundLuminance, backgroundLuminance);
        double ratio = (lighter + 0.05) / (darker + 0.05);
        if (ratio < 4.5) {
            throw new FdxException("UiKitTest " + label + " contrast is " + ratio + ":1; expected at least 4.5:1");
        }
    }

    private static double relativeLuminance(int rgba) {
        double red = linearColorComponent((rgba >>> 24) & 0xff);
        double green = linearColorComponent((rgba >>> 16) & 0xff);
        double blue = linearColorComponent((rgba >>> 8) & 0xff);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linearColorComponent(int component) {
        double value = component / 255.0;
        if (value <= 0.04045) {
            return value / 12.92;
        }
        return Math.pow((value + 0.055) / 1.055, 2.4);
    }

}
