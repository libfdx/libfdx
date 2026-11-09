package io.github.libfdx.tools.uipreview;

import io.github.libfdx.input.Input;
import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.InputRouter;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.PointerEvent;
import io.github.libfdx.input.TextInputEvent;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiAlign;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiScrollState;
import java.util.List;
import java.util.function.IntConsumer;

/** The host's interactive overlay is routed before the target's root. */
final class PreviewClassPicker extends InputAdapter implements AutoCloseable {
    private final UiRoot root;
    private final List<String> labels;
    private final IntConsumer select;
    private final InputRouter input = new InputRouter(1);
    private final UiScrollState scroll = new UiScrollState();
    private boolean open;
    private int active;

    PreviewClassPicker(UiRoot root, Input source, List<String> labels, int active, IntConsumer select) {
        this.root = root;
        this.labels = labels;
        this.active = active;
        this.select = select;
        root.input(source, input).setContent(this::build);
    }

    private void build(UiScope ui) {
        ui.column(Ui.modifier().fill().padding(12), dock -> {
            dock.spacer(Ui.modifier().weight(1));
            dock.button("Choose class", Ui.modifier().align(UiAlign.END), this::openMenu);
        });
        if (!open) return;
        ui.modal(Ui.modal("preview-class-picker").dismissOnEscape(false), modal ->
                modal.panel(Ui.modifier().width(380).maxHeight(430).gap(8), panel -> {
                    panel.text("Choose a UI class");
                    panel.scroll(Ui.modifier().fillWidth().height(Math.min(320, 40.0f * labels.size())), scroll, list -> {
                        for (int i = 0; i < labels.size(); i++) {
                            int index = i;
                            String label = labels.get(i) + (i == active ? " (current)" : "");
                            list.button(label, Ui.modifier().fillWidth().height(40), () -> {
                                closeMenu();
                                select.accept(index);
                            });
                        }
                    });
                    panel.button("Close", Ui.modifier().align(UiAlign.END), this::closeMenu);
                }));
    }

    private void openMenu() {
        open = true;
        root.requestCompose();
    }

    void closeMenu() {
        if (!open) return;
        open = false;
        root.requestCompose();
    }

    void render(int active) {
        if (this.active != active) {
            this.active = active;
            root.requestCompose();
        }
        root.update(0);
        root.render();
    }

    void resize(int width, int height) {
        root.resize(width, height);
    }

    @Override
    public boolean keyDown(KeyEvent event) {
        if (!open) return false;
        if (event.key() == Key.ESCAPE) closeMenu();
        else input.keyDown(event);
        return true;
    }

    @Override
    public boolean textInput(TextInputEvent event) {
        return open;
    }

    @Override
    public boolean pointerDown(PointerEvent event) {
        return input.pointerDown(event);
    }

    @Override
    public boolean pointerUp(PointerEvent event) {
        return input.pointerUp(event);
    }

    @Override
    public boolean pointerMoved(PointerEvent event) {
        return input.pointerMoved(event);
    }

    @Override
    public boolean scrolled(PointerEvent event) {
        return input.scrolled(event);
    }

    @Override
    public void close() {
        root.dispose();
    }

}
