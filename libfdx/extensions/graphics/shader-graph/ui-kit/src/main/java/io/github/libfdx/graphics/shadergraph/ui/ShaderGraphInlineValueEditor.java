package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorNode;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiCustomContext;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiPointerEvent;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTapSequence;

/** UI Kit fields anchored over a node's value row. */
final class ShaderGraphInlineValueEditor {
    static final float INSET = 10, BOTTOM = 21, HEIGHT = 16;
    private static final UiStyle PANEL = UiStyle.style().background(UiDrawable.color(ShaderGraphEditorTheme.NODE));
    private final ShaderGraphEditorSession session;
    private final Runnable onCommit;
    private final UiBooleanState open = Ui.state(false);
    private final UiTapSequence taps = new UiTapSequence();
    private final ShaderGraphEditorValueFields fields = new ShaderGraphEditorValueFields();
    private ShaderGraphEditorNodeValue pressed, editing;
    private ShaderGraphEditorNode placement;
    private ShaderGraphEditorLayout openedLayout;
    private long revision;
    private float pressX, pressY;

    ShaderGraphInlineValueEditor(ShaderGraphEditorSession session, Runnable onCommit) {
        this.session = session;
        this.onCommit = onCommit;
    }

    void press(UiPointerEvent event, ShaderGraphEditorNode node, ShaderGraphEditorNodeValue value, float graphX, float graphY) {
        if (event.button() != MouseButton.LEFT || node == null || value == null || !value.editableInline()
                || graphX < node.x() + INSET || graphX > node.x() + node.width() - INSET
                || graphY < node.y() + node.height() - BOTTOM || graphY > node.y() + node.height() - BOTTOM + HEIGHT) {
            cancel();
            return;
        }
        if (editing != null) cancel();
        pressed = value;
        placement = node;
        pressX = event.x();
        pressY = event.y();
    }

    /** A small motion between press and release should still count as a click, without moving the card. */
    boolean pendingClick(UiPointerEvent event) {
        if (pressed == null) return false;
        if (UiTapSequence.withinSlop(pressX, pressY, event.x(), event.y())) return true;
        cancel();
        return false;
    }

    void release(UiPointerEvent event) {
        if (pressed == null) return;
        var value = pressed;
        pressed = null;
        if (!event.inside() || !UiTapSequence.withinSlop(pressX, pressY, event.x(), event.y())) {
            cancel();
            return;
        }
        if (taps.complete(value.node(), event.pointerType(), event.pointerId(), event.timeNanos(), event.x(), event.y()) == 2) {
            taps.clear();
            editing = value;
            open.set(true);
            openedLayout = session.layout();
            revision = session.semanticRevision();
            fields.reset(value.literal());
        }
    }

    void cancel() {
        taps.clear();
        pressed = null;
        editing = null;
        open.set(false);
        placement = null;
        openedLayout = null;
    }

    private boolean isOpen() {
        if (editing != null && (openedLayout != session.layout() || revision != session.semanticRevision()
                || !editing.node().id().value().equals(session.selectedNodeId()))) cancel();
        return open.get();
    }

    void build(UiScope ui, UiCustomContext context) {
        if (!isOpen()) return;
        var view = openedLayout.activeGraph();
        float zoom = view.zoom();
        float x = view.panX() + (placement.x() + INSET) * zoom - 4;
        float y = view.panY() + (placement.y() + placement.height() - BOTTOM + HEIGHT / 2) * zoom - 20;
        var literal = editing.literal();
        int count = ShaderGraphEditorNodeValue.editableComponents(literal.type());
        float width = Math.max(100 + 64 * count, (placement.width() - 2 * INSET) * zoom);
        ui.popup(Ui.popup("inline-node-value").at(x, y).onDismiss(this::cancel),
                popup -> popup.panel(Ui.modifier().width(width).padding(3).style(PANEL), panel -> {
                    panel.row(Ui.modifier().fillWidth().gap(4), row -> {
                        row.column(Ui.modifier().weight(1), column ->
                                fields.build(column, literal, "Node value", this::submit, context));
                        row.button("Apply", this::submit);
                    });
                    fields.showError(panel);
                }));
    }

    boolean key(Key key) {
        if (!isOpen()) return false;
        if (key == Key.ESCAPE) cancel();
        else if (key == Key.ENTER) submit();
        else return false;
        return true;
    }

    private void submit() {
        if (!isOpen()) return;
        var value = editing;
        if (fields.submit(value.literal(), literal -> value.apply(session, literal))) {
            cancel();
            if (onCommit != null) onCommit.run();
        }
    }

}
