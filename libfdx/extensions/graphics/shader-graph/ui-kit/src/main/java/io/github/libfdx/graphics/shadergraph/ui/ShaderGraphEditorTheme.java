package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiShape;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTextStyle;
import io.github.libfdx.ui.UiTheme;

/** Dark gray canvas and control colors for the shader graph editor. */
public final class ShaderGraphEditorTheme {
    static final UiColor CANVAS = gray(27);
    static final UiColor GRID = gray(42);
    static final UiColor NODE = gray(49);
    static final UiColor HEADER = gray(66);
    static final UiColor EDGE = gray(87);
    static final UiColor TEXT = gray(240);
    static final UiColor DETAIL = gray(197);
    static final UiColor TYPE_TEXT = gray(168);
    static final UiColor ACCENT = gray(205);
    static final UiColor WIRE = gray(145);
    static final String SCROLL_STYLE = "shader-graph-scroll";
    static final String HEADING_STYLE = "shader-graph-heading";

    private ShaderGraphEditorTheme() {
    }

    private static UiColor gray(int brightness) {
        float channel = brightness / 255.0f;
        return UiColor.rgb(channel, channel, channel);
    }

    /** Returns the UI Kit theme for hosts displaying the dark gray graph canvas. */
    public static UiTheme darkGray() {
        UiColor frame = gray(34);
        UiDrawable panel = UiDrawable.color(gray(40));
        UiDrawable hover = UiDrawable.color(gray(76));
        UiDrawable focus = UiDrawable.color(gray(94));
        UiTextStyle text = UiTextStyle.text().size(14).lineHeight(19).color(TEXT);
        UiShape corners = UiShape.rounded(4);
        UiStyle button = UiStyle.button().shape(corners).text(text.wrap(false).ellipsis(true))
                .background(UiDrawable.color(gray(58)));
        UiStyle field = UiStyle.style().shape(corners).padding(8, 4).text(text)
                .background(UiDrawable.color(CANVAS)).foreground(UiDrawable.color(gray(145)));
        field = field.focused(field.background(UiDrawable.color(gray(53))));
        UiStyle treeRow = UiStyle.style().padding(6, 0).text(text.wrap(false).ellipsis(true))
                .foreground(UiDrawable.color(DETAIL));
        UiStyle treeFocus = treeRow.background(focus);
        treeRow = treeRow.hover(treeRow.background(hover)).focused(treeFocus).pressed(treeFocus);
        return UiTheme.dark().colors(frame, TEXT)
                .text(UiStyle.style().text(text).foreground(UiDrawable.color(WIRE)))
                .style(HEADING_STYLE, UiStyle.style().text(text.size(16).lineHeight(21))
                        .foreground(UiDrawable.color(WIRE)))
                .panel(UiStyle.style().padding(12).background(panel).text(text))
                .radioButton(UiStyle.style().padding(4, 2).background(panel)
                        .foreground(UiDrawable.color(ACCENT)).text(text.wrap(false).ellipsis(true)))
                .button(button.hover(button.background(hover)).focused(button.background(focus))
                        .pressed(button.background(UiDrawable.color(EDGE)))
                        .disabled(button.background(panel).text(text.wrap(false).ellipsis(true).color(DETAIL))))
                .textField(field).textArea(field)
                .style("tree-row", treeRow)
                .style("tree-ancestor", treeRow.background(UiDrawable.color(HEADER)))
                .style("tree-selected", treeRow.background(focus))
                .style("tree-disclosure", treeRow.padding(0))
                .style(SCROLL_STYLE, UiStyle.style().background(panel)
                        .foreground(UiDrawable.color(gray(121))));
    }

}
