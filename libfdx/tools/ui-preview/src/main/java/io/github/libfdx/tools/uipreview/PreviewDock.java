package io.github.libfdx.tools.uipreview;

import io.github.libfdx.Fdx;
import io.github.libfdx.display.Display;
import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.InputRouter;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.PointerEvent;
import io.github.libfdx.input.TextInputEvent;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiToolkit;
import java.util.List;
import java.util.function.IntConsumer;

/** An independent hierarchy root in a reserved portion of the preview window. */
final class PreviewDock extends InputAdapter implements AutoCloseable {
    private static final int WIDTH = 235;
    private final Display display;
    private final InputRouter canvasInput;
    private final InputRouter hierarchyInput = new InputRouter(1);
    private PreviewHierarchyView view;
    private final UiRoot root;
    private final PreviewDebugLines debug;
    private final PreviewRefreshStatus status;
    private UiRect canvasViewport;
    private int leftPixels;
    private float split;
    private InputRouter keyboard;
    private InputRouter pressed;

    PreviewDock(Fdx fdx, InputRouter canvasInput, List<PreviewHierarchy.Item> items, int active, IntConsumer select) {
        display = fdx.displays().main();
        this.canvasInput = canvasInput;
        keyboard = canvasInput;
        debug = new PreviewDebugLines(PreviewSessionFiles.debugLinesFile());
        var preference = PreviewSessionFiles.debugLinesFile();
        status = new PreviewRefreshStatus(preference == null ? null : preference.resolveSibling("status.properties"));
        view = new PreviewHierarchyView(items, active, select, debug.state(), status);
        root = new UiToolkit(fdx.files()).root(display, fdx.graphics().main());
        root.input(fdx.input(), hierarchyInput).setContent(view);
    }

    void layout(UiRoot canvas, int width, int height) {
        if (width < 2 || height < 1) return;
        int left = Math.max(1, Math.min(width / 2, Math.round(WIDTH * display.contentScale())));
        if (canvasViewport == null || left != leftPixels
                || width != canvasViewport.right() || height != canvasViewport.height()) {
            leftPixels = left;
            root.viewport(new UiRect(0, 0, left, height));
            canvasViewport = new UiRect(left, 0, width - left, height);
        }
        split = (float) leftPixels * display.width() / width;
        if (canvas.viewport() != canvasViewport) canvas.viewport(canvasViewport);
    }

    int leftPixels() {
        return leftPixels;
    }

    void items(List<PreviewHierarchy.Item> before, List<PreviewHierarchy.Item> after, int active,
            IntConsumer select) {
        boolean same = before.size() == after.size();
        for (int i = 0; same && i < before.size(); i++) {
            PreviewHierarchy.Item a = before.get(i);
            PreviewHierarchy.Item b = after.get(i);
            same = a.id().equals(b.id()) && a.label().equals(b.label()) && a.depth() == b.depth();
        }
        if (same) return;
        view = new PreviewHierarchyView(after, active, select, debug.state(), status);
        root.setContent(view);
    }

    int step(int index, boolean backward) {
        return view.step(index, backward);
    }

    void render(int active, float deltaSeconds) {
        status.update(System.nanoTime());
        view.active(active);
        debug.save();
        root.debugLines(debug.state().get());
        root.update(deltaSeconds);
        root.render();
    }

    void renderCanvas(UiRoot canvas) {
        debug.render(canvas);
    }

    private InputRouter at(PointerEvent event) {
        return event.x() < split ? hierarchyInput : canvasInput;
    }

    @Override
    public boolean keyDown(KeyEvent event) {
        return keyboard.keyDown(event);
    }

    @Override
    public boolean keyUp(KeyEvent event) {
        boolean hierarchy = hierarchyInput.keyUp(event);
        return canvasInput.keyUp(event) || hierarchy;
    }

    @Override
    public boolean textInput(TextInputEvent event) {
        return keyboard.textInput(event);
    }

    @Override
    public boolean pointerDown(PointerEvent event) {
        keyboard = at(event);
        if (!keyboard.pointerDown(event)) return false;
        pressed = keyboard;
        return true;
    }

    @Override
    public boolean pointerUp(PointerEvent event) {
        InputRouter target = pressed == null ? at(event) : pressed;
        pressed = null;
        return target.pointerUp(event);
    }

    @Override
    public boolean pointerMoved(PointerEvent event) {
        if (pressed != null) return pressed.pointerMoved(event);
        boolean hierarchy = hierarchyInput.pointerMoved(event);
        return canvasInput.pointerMoved(event) || hierarchy;
    }

    @Override
    public boolean scrolled(PointerEvent event) {
        return at(event).scrolled(event);
    }

    @Override
    public void close() {
        root.dispose();
    }

}
