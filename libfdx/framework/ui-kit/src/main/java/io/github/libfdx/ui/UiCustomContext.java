package io.github.libfdx.ui;

import java.util.Objects;
import java.util.function.Function;

/**
 * Represents an ui custom context.
 *
 * @author xpenatan
 */
public final class UiCustomContext {
    private UiMeasureFunction measureFunction;
    private UiDrawFunction drawFunction;
    private UiSurfaceInput surfaceInput;
    private Function<UiScope, UiNode> overlayContent;
    private Function<UiRect, UiRect> overlayPlacement;
    private UiNode overlayNode;
    private UiContent children;
    private UiNode initialFocus;
    private boolean selectInitialText;

    /** Composes ordinary child widgets above the surface using the same layout as a stack. */
    public void content(UiContent children) {
        this.children = Objects.requireNonNull(children);
    }

    /** Sets the child that should receive focus when this surface acquires focus. */
    public void initialFocus(UiNode child) {
        initialFocus(child, false);
    }

    /** Optionally selects a text field's contents when focus transfers from the surface to the child. */
    public void initialFocus(UiNode child, boolean selectText) {
        initialFocus = Objects.requireNonNull(child);
        selectInitialText = selectText;
    }

    boolean selectInitialText() {
        return selectInitialText;
    }

    UiNode focusTarget() {
        return initialFocus != null ? initialFocus : overlayNode;
    }


    /**
     * Runs the measure step.
     *
     * @param measureFunction the measure function
     */
    public void measure(UiMeasureFunction measureFunction) {
        this.measureFunction = measureFunction;
    }

    /**
     * Draws the current content.
     *
     * @param drawFunction the draw function
     */
    public void draw(UiDrawFunction drawFunction) {
        this.drawFunction = drawFunction;
    }

    /**
     * Sets the input handler that makes this custom node an interactive surface.
     *
     * <p>Use a focusable modifier when the surface also needs key or text input.
     * Returning {@link UiPointerResult#CAPTURE} from the pointer callback keeps
     * drag operations routed to this surface outside its bounds.</p>
     *
     * @param surfaceInput the surface input handler
     */
    public void input(UiSurfaceInput surfaceInput) {
        this.surfaceInput = surfaceInput;
    }

    /**
     * Composes a regular widget above this surface. While the surface owns focus, the
     * widget receives text and pointer input normally. The surface can handle navigation
     * keys before the widget. Omitting the overlay on a later composition returns focus
     * to the surface if the overlay still had focus.
     *
     * @param placement maps the surface's content bounds to the widget's area in UI-root coordinates
     * @param content builds and returns one direct, focusable child of the supplied scope
     */
    public void focusedOverlay(Function<UiRect, UiRect> placement, Function<UiScope, UiNode> content) {
        overlayPlacement = Objects.requireNonNull(placement);
        overlayContent = Objects.requireNonNull(content);
    }

    void composeChildren(UiRoot root, UiNode host) {
        if (children != null) children.build(root.obtainScope(host, host.identity()));
        if (overlayContent != null) {
            int count = host.children().size();
            overlayNode = overlayContent.apply(root.obtainScope(host, host.identity() + "/focused-overlay"));
            if (overlayNode == null || overlayNode.parent() != host || host.children().size() != count + 1
                    || !overlayNode.modifier().focusable()) {
                throw new IllegalArgumentException("A focused overlay must build and return one focusable child");
            }
        }
        if (initialFocus != null) {
            UiNode parent = initialFocus.parent();
            while (parent != null && parent != host) parent = parent.parent();
            if (parent != host || !initialFocus.modifier().focusable()) {
                throw new IllegalArgumentException("Initial focus must be a focusable child of this surface");
            }
        }
    }

    UiNode overlayNode() {
        return overlayNode;
    }

    UiRect overlayBounds(UiRect surface) {
        return overlayPlacement.apply(surface);
    }

    UiMeasureFunction measureFunction() {
        return measureFunction;
    }

    UiDrawFunction drawFunction() {
        return drawFunction;
    }

    UiSurfaceInput surfaceInput() {
        return surfaceInput;
    }

    void reset() {
        measureFunction = null;
        drawFunction = null;
        surfaceInput = null;
        overlayContent = null;
        overlayPlacement = null;
        overlayNode = null;
        initialFocus = null;
        selectInitialText = false;
        children = null;
    }

}
