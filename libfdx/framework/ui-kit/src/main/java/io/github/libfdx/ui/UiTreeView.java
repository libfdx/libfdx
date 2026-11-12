package io.github.libfdx.ui;

import io.github.libfdx.collections.OrderedMap;
import java.util.function.Consumer;

/** Composes the tree from keyed rows, controls and custom branch guides. */
final class UiTreeView implements UiContent {
    static final float ROW_HEIGHT = 26;
    private static final float INDENT = 16;
    private static final String LABEL = "tree-row";
    private static final String ANCESTOR = "tree-ancestor";
    private static final String SELECTED = "tree-selected";
    private static final String DISCLOSURE = "tree-disclosure";
    private static final UiModifier ROW = Ui.modifier().fillWidth().height(ROW_HEIGHT).animateItemPlacement();
    private final UiTreeState state;
    private final Consumer<String> select;
    private final UiColor guide;
    private final UiColor icon;
    private final UiItemContent<UiTreeItem> content;

    UiTreeView(UiTreeState state, Consumer<String> select, UiTheme theme, UiItemContent<UiTreeItem> content) {
        this.state = state;
        this.select = select != null ? select : state::select;
        guide = foreground(theme, LABEL);
        icon = foreground(theme, DISCLOSURE);
        this.content = content;
    }

    @Override
    public void build(UiScope ui) {
        ui.items(ROW, state.visibleRows(), row -> row.compositionKey, this::buildRow);
    }

    private void buildRow(UiScope scope, UiTreeState.Row entry) {
        scope.stack(Ui.modifier().fill(), row -> {
            row.row(Ui.modifier().fill(), controls -> buildControls(controls, entry));
            row.custom("tree-guides", Ui.modifier().fill(), guides ->
                    guides.draw((draw, bounds) -> drawGuides(draw, bounds, entry)));
        });
    }

    private void buildControls(UiScope row, UiTreeState.Row entry) {
        String key = entry.item.key();
        String label = entry.item.label();
        if (entry.depth > 0) row.spacer(Ui.modifier().width(entry.depth * INDENT));
        if (entry.branch()) {
            row.button("", Ui.modifier().width(INDENT).height(ROW_HEIGHT).style(DISCLOSURE)
                    .semanticLabel((entry.expanded ? "Collapse " : "Expand ") + label),
                    () -> state.setExpanded(key, !entry.expanded));
        } else {
            row.spacer(Ui.modifier().width(INDENT));
        }
        UiTreeState.Row selected = state.selectedRow();
        String style = entry == selected ? SELECTED : LABEL;
        if (!entry.expanded && entry.contains(selected)) style = ANCESTOR;
        UiModifier modifier = Ui.modifier().fillWidth().weight(1).height(ROW_HEIGHT).style(style)
                .semanticLabel(label);
        if (content == null) {
            row.button(label, modifier, () -> select.accept(key));
        } else {
            row.button(modifier.gap(6), () -> select.accept(key), item -> content.build(item, entry.item));
        }
    }

    private void drawGuides(UiDrawContext draw, UiRect row, UiTreeState.Row entry) {
        float center = guideX(row, entry.depth);
        float middle = row.y() + row.height() * 0.5f;
        for (UiTreeState.Row ancestor = entry.parent; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor.depth > 0 && state.hasNextSibling(ancestor)) {
                float x = guideX(row, ancestor.depth - 1);
                draw.line(x, row.y(), x, row.bottom(), 1, guide);
            }
        }
        if (entry.depth > 0) {
            float x = guideX(row, entry.depth - 1);
            draw.line(x, row.y(), x, state.hasNextSibling(entry) ? row.bottom() : middle, 1, guide);
            draw.line(x, middle, center - 5, middle, 1, guide);
        }
        if (entry.expanded) draw.line(center, middle + 6, center, row.bottom(), 1, guide);
        drawIcon(draw, center, middle, entry);
    }

    private static float guideX(UiRect row, int depth) {
        return row.x() + (depth + 0.5f) * INDENT;
    }

    private void drawIcon(UiDrawContext draw, float x, float y, UiTreeState.Row entry) {
        if (!entry.branch()) {
            draw.line(x - 1, y, x + 1, y, 2, guide);
        } else if (entry.expanded) {
            draw.line(x - 3, y - 1.5f, x, y + 1.5f, 1.35f, icon);
            draw.line(x, y + 1.5f, x + 3, y - 1.5f, 1.35f, icon);
        } else {
            draw.line(x - 1.5f, y - 3, x + 1.5f, y, 1.35f, icon);
            draw.line(x + 1.5f, y, x - 1.5f, y + 3, 1.35f, icon);
        }
    }

    private static UiColor foreground(UiTheme theme, String role) {
        UiStyle style = theme.style(role);
        UiColor color = style == null ? null : style.foreground().color();
        return color != null ? color : theme.textColor();
    }

    static void addStyles(OrderedMap<String, UiStyle> styles, boolean light) {
        int text = light ? 0x20242cff : 0xdce5efff;
        int guide = light ? 0x8291a1ff : 0x414b59ff;
        int icon = light ? 0x52657bff : 0xaab7c8ff;
        int hover = light ? 0xe8edf5ff : 0x252e3bff;
        int focus = light ? 0xd5e1f1ff : 0x303f52ff;
        UiStyle label = control(0x00000000, hover, focus, text, guide, 6);
        styles.put(LABEL, label);
        styles.put(DISCLOSURE, control(0x00000000, hover, focus, text, icon, 0));
        styles.put(ANCESTOR, control(light ? 0xe5ecf7ff : 0x202d3cff, hover, focus, text, guide, 6));
        styles.put(SELECTED, control(light ? 0xc6dbf5ff : 0x2c4666ff,
                light ? 0xb9d4f1ff : 0x365679ff, light ? 0xaac8ecff : 0x3b5f87ff, text, guide, 6));
    }

    private static UiStyle control(int fill, int hover, int focus, int text, int foreground, float padding) {
        UiStyle base = UiStyle.style().background(UiDrawable.color(UiColor.rgba8888(fill)))
                .foreground(UiDrawable.color(UiColor.rgba8888(foreground)))
                .padding(padding, 0).text(UiTextStyle.text()
                        .size(14).color(UiColor.rgba8888(text)).wrap(false).ellipsis(true));
        UiStyle highlighted = base.background(UiDrawable.color(UiColor.rgba8888(focus)));
        return base.hover(base.background(UiDrawable.color(UiColor.rgba8888(hover))))
                .focused(highlighted).pressed(highlighted);
    }

}
