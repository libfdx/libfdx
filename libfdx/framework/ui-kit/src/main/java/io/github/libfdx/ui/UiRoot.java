package io.github.libfdx.ui;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.display.Display;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.input.Input;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import io.github.libfdx.input.PointerType;
import io.github.libfdx.input.TextInputEvent;
import io.github.libfdx.input.TextInputRequest;
import io.github.libfdx.input.TextInputType;
import io.github.libfdx.graphics.g2d.BitmapFont;
import io.github.libfdx.graphics.g2d.BitmapFontGlyph;
import io.github.libfdx.graphics.g2d.BitmapFontLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an ui root.
 *
 * @author xpenatan
 */
public final class UiRoot implements Disposable, UiStateListener {
    private static final UiModifier ROOT_MODIFIER = UiModifier.none().fill();
    private static final int WINDOW_POINTER_NONE = 0;
    private static final int WINDOW_POINTER_DRAG = 1;
    private static final int WINDOW_POINTER_RESIZE = 2;
    private static final int SCROLL_POINTER_NONE = 0;
    private static final int SCROLL_POINTER_VERTICAL = 1;
    private static final int SCROLL_POINTER_HORIZONTAL = 2;
    private static final int SCROLL_POINTER_BODY = 3;
    private static final float CHECKBOX_SIZE = 20.0f;
    private static final float CHECKBOX_LABEL_GAP = 8.0f;
    private static final float SWITCH_WIDTH = 48.0f;
    private static final float SWITCH_HEIGHT = 28.0f;
    private static final float RADIO_SIZE = 28.0f;
    private static final float COLLAPSE_HEADER_HEIGHT = 44.0f;
    private static final float WINDOW_TITLE_HEIGHT = 30.0f;
    private static final float WINDOW_RESIZE_HANDLE = 18.0f;
    private static final float SCROLLBAR_HIT_SIZE = 12.0f;
    private static final float SCROLLBAR_MIN_THUMB = 22.0f;
    private static final float SCROLL_BODY_DRAG_SLOP = 8.0f;
    private static final float TOUCH_TEXT_AREA_DRAG_SLOP = 8.0f;
    private static final float TEXT_TAP_SLOP = 6.0f;
    private static final long DOUBLE_TEXT_TAP_TIMEOUT_NANOS = 500_000_000L;
    private static final int RECT_WINDOW_TITLE = 3;
    private static final int RECT_WINDOW_RESIZE = 4;
    private static final int RECT_TEXT_INPUT = 5;
    private static final int RECT_TEXT_CARET = 6;
    private static final int RECT_SCROLL_VERTICAL_TRACK = 7;
    private static final int RECT_SCROLL_HORIZONTAL_TRACK = 8;
    private static final int RECT_SCROLL_VERTICAL_THUMB = 9;
    private static final int RECT_SCROLL_HORIZONTAL_THUMB = 10;
    private static final int RECT_PLATFORM_TEXT_INPUT = 11;
    private static final int RECT_TAB_BASE = 16;
    private static final int RECT_LAYOUT_AREA = 32;
    private static final int RECT_LAYOUT_TARGET = 33;
    private static final int RECT_LAYOUT_ANIMATED_SIZE = 34;
    private static final int RECT_LAYOUT_INNER = 35;
    private static final int RECT_LAYOUT_SCROLLED = 36;
    private static final int RECT_LAYOUT_CHILD_INPUT = 37;
    private static final int RECT_LAYOUT_WINDOW_AREA = 38;
    private static final int RECT_LAYOUT_WINDOW_TARGET = 39;
    private static final int RECT_LAYOUT_OVERLAY_TARGET = 40;
    private static final int RECT_LAYOUT_ROOT = 41;
    private static final int SIZE_PREFERRED_BASE = 0;
    private static final int SIZE_PREFERRED_RESULT = 1;
    private static final int SIZE_TEXT = 2;
    private static final int SIZE_TEXT_AREA = 3;
    private static final int SIZE_COLUMN = 4;
    private static final int SIZE_ROW = 5;
    private static final int SIZE_TABS = 6;
    private static final int SIZE_ANIMATION_TARGET = 7;
    private static final int KEY_CONTENT_SIZE_ANIMATION = 0;
    private static final int KEY_PLACEMENT_ANIMATION = 1;

    private final FileSystem files;
    private final Display display;
    private final GraphicsContext graphics;
    private final UiTextEngine textEngine;
    private final Map<String, UiNode> retainedNodes = new LinkedHashMap<String, UiNode>();
    private final List<UiNode> retainedNodeValues = new ArrayList<UiNode>();
    private final List<UiObservableState> observedStateValues = new ArrayList<UiObservableState>();
    private final Map<String, UiAnimatable<?>> animatables = new LinkedHashMap<String, UiAnimatable<?>>();
    private final List<String> animatableKeys = new ArrayList<String>();
    private final List<UiAnimatable<?>> animatableValues = new ArrayList<UiAnimatable<?>>();
    private final Map<String, UiFloatAnimatable> floatAnimatables = new LinkedHashMap<String, UiFloatAnimatable>();
    private final List<String> floatAnimatableKeys = new ArrayList<String>();
    private final List<UiFloatAnimatable> floatAnimatableValues = new ArrayList<UiFloatAnimatable>();
    private final Map<String, UiScrollState> scrollStates = new LinkedHashMap<String, UiScrollState>();
    private final Map<String, UiListState> listStates = new LinkedHashMap<String, UiListState>();
    private final Map<String, UiRect> tooltipAnchors = new LinkedHashMap<String, UiRect>();
    private final List<String> unusedNodeIdentities = new ArrayList<String>();
    private final List<UiNode> focusableNodes = new ArrayList<UiNode>();
    private UiScope[] compositionScopes = new UiScope[64];
    private ArrayList<?>[] compositionLists = new ArrayList<?>[8];
    private int compositionScopeCount;
    private int compositionListCount;
    private final UiInputHandler inputHandler = new UiInputHandler(this);
    private final HitResult hitResult = new HitResult();
    private final UiPointerEvent surfacePointerEvent = new UiPointerEvent();
    private UiTheme theme = UiTheme.dark();
    private UiRenderer renderer;
    private Input input;
    private UiContent content;
    private UiNode rootNode;
    private UiNode hoveredNode;
    private UiNode pressedNode;
    private UiNode focusedNode;
    private UiNode capturedSurfaceNode;
    private int capturedSurfacePointerId;
    private PointerType capturedSurfacePointerType = PointerType.MOUSE;
    private MouseButton capturedSurfaceButton = MouseButton.UNKNOWN;
    private long capturedSurfaceTimeNanos;
    private float capturedSurfaceX;
    private float capturedSurfaceY;
    private UiNode tooltipHoverNode;
    private float tooltipHoverStartSeconds;
    private float tooltipWakeSeconds = -1.0f;
    private UiInsets safeArea = UiInsets.ZERO;
    private float uiScale = 1.0f;
    private boolean autoUiScale = true;
    private boolean debugLines;
    private float animationScale = 1.0f;
    private UiWindowState activeWindowState;
    private UiNode activeWindowNode;
    private int activeWindowPointerMode = WINDOW_POINTER_NONE;
    private UiRect activeWindowArea = UiRect.ZERO;
    private float activeWindowStartPointerX;
    private float activeWindowStartPointerY;
    private float activeWindowStartX;
    private float activeWindowStartY;
    private float activeWindowStartWidth;
    private float activeWindowStartHeight;
    private UiNode activeScrollNode;
    private int activeScrollPointerMode = SCROLL_POINTER_NONE;
    private float activeScrollPointerOffset;
    private float activeScrollStartPointerX;
    private float activeScrollStartPointerY;
    private float activeScrollStartX;
    private float activeScrollStartY;
    private UiNode pendingScrollBodyNode;
    private float pendingScrollBodyStartX;
    private float pendingScrollBodyStartY;
    private UiNode activeTextNode;
    private int activeTextSelectionAnchor;
    private float activeTextSelectionStartX;
    private float activeTextSelectionStartY;
    private boolean activeTextSelectionMoved;
    private UiNode lastTextTapNode;
    private PointerType lastTextTapType;
    private int lastTextTapPointerId;
    private long lastTextTapTimeNanos = Long.MIN_VALUE;
    private float lastTextTapX;
    private float lastTextTapY;
    private UiNode pendingTouchTextAreaNode;
    private float pendingTouchTextAreaStartX;
    private float pendingTouchTextAreaStartY;
    private UiNode pendingTextInputTapNode;
    private float pendingTextInputTapStartX;
    private float pendingTextInputTapStartY;
    private int nextWindowZOrder = 1;
    private long childOrderRevision;
    private long compositionPass;
    private boolean dirty = true;
    private boolean disposed;
    private int width;
    private int height;
    private float elapsedSeconds;
    private int layoutPass;

    /**
     * Represents the result of a hit operation.
     *
     * @author xpenatan
     */
    private static final class HitResult {
        UiNode node;
        boolean blocked;

        void set(UiNode node, boolean blocked) {
            this.node = node;
            this.blocked = blocked;
        }

        void reset() {
            node = null;
            blocked = false;
        }

        boolean handled() {
            return node != null || blocked;
        }
    }

    UiRoot(FileSystem files, Display display, GraphicsContext graphics, UiTheme theme) {
        this.files = files;
        this.display = display;
        this.graphics = graphics;
        this.textEngine = new UiTextEngine(files, graphics);
        this.theme = theme != null ? theme : UiTheme.dark();
        this.width = display != null ? display.width() : 0;
        this.height = display != null ? display.height() : 0;
        this.renderer = graphics != null ? new UiG2DRenderer(graphics) : null;
    }

    /**
     * Sets the content.
     *
     * @param content the content
     */
    public void setContent(UiContent content) {
        this.content = content;
        requestCompose();
    }

    /**
     * Runs the request compose step.
     */
    public void requestCompose() {
        dirty = true;
    }

    /**
     * Updates this instance.
     *
     * @param deltaSeconds the delta seconds
     */
    public void update(float deltaSeconds) {
        ensureComposed();
        float previousElapsedSeconds = elapsedSeconds;
        elapsedSeconds += Math.max(0.0f, deltaSeconds);
        boolean animationRunning = false;
        for (int i = 0; i < animatableValues.size(); i++) {
            UiAnimatable<?> animatable = animatableValues.get(i);
            boolean wasRunning = animatable.isRunning();
            animatable.update(deltaSeconds * animationScale);
            animationRunning = animationRunning || wasRunning || animatable.isRunning();
        }
        for (int i = 0; i < floatAnimatableValues.size(); i++) {
            UiFloatAnimatable animatable = floatAnimatableValues.get(i);
            boolean wasRunning = animatable.isRunning();
            animatable.update(deltaSeconds * animationScale);
            animationRunning = animationRunning || wasRunning || animatable.isRunning();
        }
        boolean tooltipWakeDue = tooltipWakeDue(previousElapsedSeconds, elapsedSeconds);
        if (animationRunning || tooltipWakeDue) {
            requestCompose();
        }
        if (animationRunning || tooltipWakeDue) {
            ensureComposed();
        }
    }

    /**
     * Renders the current content.
     */
    public void render() {
        ensureComposed();
        if (renderer != null && rootNode != null) {
            renderer.render(this, rootNode);
        }
    }

    /**
     * Handles a size change.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    public void resize(int width, int height) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        layout();
    }

    /**
     * Returns the display.
     *
     * @return the display
     */
    public Display display() {
        return display;
    }

    /**
     * Returns the files.
     *
     * @return the files
     */
    public FileSystem files() {
        return files;
    }

    /**
     * Returns the graphics.
     *
     * @return the graphics
     */
    public GraphicsContext graphics() {
        return graphics;
    }

    /**
     * Returns the theme.
     *
     * @return the theme
     */
    public UiTheme theme() {
        return theme;
    }

    /**
     * Runs the theme step.
     *
     * @param theme the theme
     */
    public void theme(UiTheme theme) {
        this.theme = theme != null ? theme : UiTheme.dark();
        requestCompose();
    }

    /**
     * Returns the safe area.
     *
     * @return the safe area
     */
    public UiInsets safeArea() {
        return safeArea;
    }

    /**
     * Sets the safe area and returns this UI root.
     *
     * @param safeArea the safe area
     * @return this UI root for chaining
     */
    public UiRoot safeArea(UiInsets safeArea) {
        this.safeArea = safeArea != null ? safeArea : UiInsets.ZERO;
        layout();
        return this;
    }

    /**
     * Returns the UI scale.
     *
     * @return the UI scale
     */
    public float uiScale() {
        return uiScale;
    }

    /**
     * Sets the UI scale and returns this UI root.
     *
     * @param uiScale the UI scale
     * @return this UI root for chaining
     */
    public UiRoot uiScale(float uiScale) {
        this.uiScale = uiScale > 0.0f ? uiScale : 1.0f;
        layout();
        return this;
    }

    /**
     * Returns whether the root automatically applies {@link Display#contentScale()}.
     *
     * <p>Automatic display scaling is enabled by default.</p>
     *
     * @return true when display content scaling is applied
     */
    public boolean autoUiScale() {
        return autoUiScale;
    }

    /**
     * Sets whether the root automatically applies {@link Display#contentScale()} and returns this UI root.
     *
     * <p>Disable this only when an application already converts its UI units to display-scaled units.</p>
     *
     * @param autoUiScale true to apply display content scaling
     * @return this UI root for chaining
     */
    public UiRoot autoUiScale(boolean autoUiScale) {
        this.autoUiScale = autoUiScale;
        layout();
        return this;
    }

    /**
     * Returns the debug lines.
     *
     * @return true if debug lines succeeds or is active; false otherwise
     */
    public boolean debugLines() {
        return debugLines;
    }

    /**
     * Sets the debug lines and returns this UI root.
     *
     * @param debugLines the debug lines
     * @return this UI root for chaining
     */
    public UiRoot debugLines(boolean debugLines) {
        this.debugLines = debugLines;
        return this;
    }

    /**
     * Runs the display x step.
     *
     * @param uiX the UI x
     * @return the display x
     */
    public int displayX(float uiX) {
        return Math.round(uiX * effectiveUiScale());
    }

    /**
     * Runs the display y step.
     *
     * @param uiY the UI y
     * @return the display y
     */
    public int displayY(float uiY) {
        return Math.round(uiY * effectiveUiScale());
    }

    /**
     * Runs the UI x step.
     *
     * @param displayX the display x
     * @return the UI x
     */
    public float uiX(int displayX) {
        return displayX / effectiveUiScale();
    }

    /**
     * Runs the UI y step.
     *
     * @param displayY the display y
     * @return the UI y
     */
    public float uiY(int displayY) {
        return displayY / effectiveUiScale();
    }

    /**
     * Returns the resolved line height used to render and hit-test the supplied text node.
     *
     * @param node the text node
     * @return the resolved logical line height
     */
    public float textLineHeight(UiNode node) {
        return textLineHeight(textStyleFor(node));
    }

    /**
     * Returns the animation scale.
     *
     * @return the animation scale
     */
    public float animationScale() {
        return animationScale;
    }

    /**
     * Sets the animation scale and returns this UI root.
     *
     * @param animationScale the animation scale
     * @return this UI root for chaining
     */
    public UiRoot animationScale(float animationScale) {
        this.animationScale = Math.max(0.0f, animationScale);
        return this;
    }

    /**
     * Returns the root node.
     *
     * @return the root node
     */
    public UiNode rootNode() {
        ensureComposed();
        return rootNode;
    }

    /**
     * Returns the renderer.
     *
     * @return the renderer
     */
    public UiRenderer renderer() {
        return renderer;
    }

    /**
     * Renders er.
     *
     * @param renderer the renderer
     */
    public void renderer(UiRenderer renderer) {
        if (this.renderer != null && this.renderer != renderer) {
            this.renderer.dispose();
        }
        this.renderer = renderer;
    }

    /**
     * Sets the input and returns this UI root.
     *
     * @param input the input
     * @return this UI root for chaining
     */
    public UiRoot input(Input input) {
        if (this.input == input) {
            return this;
        }
        if (this.input != null) {
            hidePlatformTextInput(this.input);
            this.input.removeProcessor(inputHandler);
        }
        this.input = input;
        if (this.input != null) {
            this.input.addProcessor(inputHandler);
            requestPlatformTextInput(focusedNode);
        }
        return this;
    }

    /**
     * Returns the input.
     *
     * @return the input
     */
    public Input input() {
        return input;
    }

    <T> UiAnimatable<T> animatable(String key, T value) {
        String id = key != null ? key : "animatable-" + animatables.size();
        UiAnimatable<?> current = animatables.get(id);
        if (current == null) {
            current = new UiAnimatable<T>(value);
            animatables.put(id, current);
            animatableKeys.add(id);
            animatableValues.add(current);
        }
        @SuppressWarnings("unchecked")
        UiAnimatable<T> typed = (UiAnimatable<T>) current;
        return typed;
    }

    UiFloatAnimatable floatAnimatable(String key, float value) {
        String id = key != null ? key : "float-animatable-" + floatAnimatables.size();
        UiFloatAnimatable current = floatAnimatables.get(id);
        if (current == null) {
            current = new UiFloatAnimatable(value);
            floatAnimatables.put(id, current);
            floatAnimatableKeys.add(id);
            floatAnimatableValues.add(current);
        }
        return current;
    }

    UiScrollState scrollState(String key) {
        String id = key != null ? key : "scroll-" + scrollStates.size();
        UiScrollState state = scrollStates.get(id);
        if (state == null) {
            state = new UiScrollState();
            scrollStates.put(id, state);
        }
        return state;
    }

    UiListState listState(String key) {
        String id = key != null ? key : "list-" + listStates.size();
        UiListState state = listStates.get(id);
        if (state == null) {
            state = new UiListState();
            listStates.put(id, state);
        }
        return state;
    }

    void observe(UiObservableState state) {
        if (state != null && !observedStateValues.contains(state)) {
            observedStateValues.add(state);
            state.addListener(this);
        }
    }

    UiNode retainNode(String identity, UiNodeType type, String key, UiModifier modifier) {
        UiNode node = retainedNodes.get(identity);
        if (node == null || node.isDisposed() || node.type() != type) {
            int retainedIndex = node != null ? retainedNodeValues.indexOf(node) : -1;
            if (node != null) {
                node.dispose();
            }
            node = new UiNode(type, identity);
            retainedNodes.put(identity, node);
            if (retainedIndex >= 0) {
                retainedNodeValues.set(retainedIndex, node);
            } else {
                retainedNodeValues.add(node);
            }
        }
        node.begin(key, modifier);
        node.usedInComposition(compositionPass);
        return node;
    }

    /**
     * Runs the state changed step.
     *
     * @param state the state
     */
    @Override
    public void stateChanged(UiObservableState state) {
        requestCompose();
        updatePlatformTextInput(focusedNode);
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        cancelSurfaceCapture();
        notifySurfaceFocus(focusedNode, false);
        for (int i = 0; i < observedStateValues.size(); i++) {
            observedStateValues.get(i).removeListener(this);
        }
        observedStateValues.clear();
        for (int i = 0; i < retainedNodeValues.size(); i++) {
            retainedNodeValues.get(i).dispose();
        }
        retainedNodes.clear();
        retainedNodeValues.clear();
        animatables.clear();
        animatableKeys.clear();
        animatableValues.clear();
        floatAnimatables.clear();
        floatAnimatableKeys.clear();
        floatAnimatableValues.clear();
        scrollStates.clear();
        listStates.clear();
        tooltipAnchors.clear();
        if (input != null) {
            hidePlatformTextInput(input);
            input.removeProcessor(inputHandler);
            input = null;
        }
        if (renderer != null) {
            renderer.dispose();
            renderer = null;
        }
        textEngine.dispose();
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private void ensureComposed() {
        if (!dirty || disposed) {
            return;
        }
        for (int i = 0; i < observedStateValues.size(); i++) {
            observedStateValues.get(i).removeListener(this);
        }
        observedStateValues.clear();
        compositionPass++;
        tooltipWakeSeconds = -1.0f;

        UiRoot previous = UiComposition.CURRENT_ROOT.get();
        UiComposition.CURRENT_ROOT.set(this);
        beginCompositionScratch();
        try {
            rootNode = retainNode("root", UiNodeType.ROOT, "root", ROOT_MODIFIER);
            if (content != null) {
                content.build(obtainScope(rootNode, "root"));
            }
        } finally {
            releaseCompositionScratch();
            UiComposition.CURRENT_ROOT.set(previous);
        }
        disposeUnusedNodes();
        dirty = false;
        layout();
    }

    UiScope obtainScope(UiNode parent, String path) {
        if (compositionScopeCount >= compositionScopes.length) {
            compositionScopes = Arrays.copyOf(compositionScopes, compositionScopes.length * 2);
        }
        UiScope scope = compositionScopes[compositionScopeCount];
        if (scope == null) {
            scope = new UiScope(this);
            compositionScopes[compositionScopeCount] = scope;
        }
        compositionScopeCount++;
        return scope.reset(parent, path);
    }

    @SuppressWarnings("unchecked")
    <T> List<T> materialize(Iterable<T> items) {
        if (items instanceof List<?>) {
            return (List<T>)items;
        }
        if (compositionListCount >= compositionLists.length) {
            compositionLists = Arrays.copyOf(compositionLists, compositionLists.length * 2);
        }
        ArrayList<Object> values = (ArrayList<Object>)compositionLists[compositionListCount];
        if (values == null) {
            values = new ArrayList<Object>();
            compositionLists[compositionListCount] = values;
        }
        compositionListCount++;
        values.clear();
        for (T item : items) {
            values.add(item);
        }
        return (List<T>)(List<?>)values;
    }

    private void beginCompositionScratch() {
        releaseCompositionScratch();
    }

    private void releaseCompositionScratch() {
        for (int i = 0; i < compositionScopeCount; i++) {
            compositionScopes[i].clear();
        }
        for (int i = 0; i < compositionListCount; i++) {
            compositionLists[i].clear();
        }
        compositionScopeCount = 0;
        compositionListCount = 0;
    }



    private void disposeUnusedNodes() {
        unusedNodeIdentities.clear();
        try {
            for (int i = 0; i < retainedNodeValues.size(); i++) {
                UiNode node = retainedNodeValues.get(i);
                if (!node.wasUsedInComposition(compositionPass)) {
                    unusedNodeIdentities.add(node.identity());
                }
            }
            for (int i = 0; i < unusedNodeIdentities.size(); i++) {
                String identity = unusedNodeIdentities.get(i);
                UiNode node = retainedNodes.remove(identity);
                retainedNodeValues.remove(node);
                clearReferencesToNode(node);
                removeAnimationsForNode(identity);
                if (node != null) {
                    node.dispose();
                }
            }
        }
        finally {
            unusedNodeIdentities.clear();
        }
    }

    private void clearReferencesToNode(UiNode node) {
        if (node == null) {
            return;
        }
        if (hoveredNode == node) {
            hoveredNode = null;
        }
        if (pressedNode == node) {
            pressedNode = null;
        }
        if (capturedSurfaceNode == node) {
            cancelSurfaceCapture();
        }
        if (focusedNode == node) {
            if (input != null && requestsPlatformTextInput(focusedNode)) {
                input.hideTextInput();
            }
            notifySurfaceFocus(focusedNode, false);
            focusedNode.focused(false);
            focusedNode = null;
        }
        if (tooltipHoverNode == node) {
            tooltipHoverNode = null;
            tooltipWakeSeconds = -1.0f;
        }
        if (activeScrollNode == node) {
            activeScrollNode = null;
            activeScrollPointerMode = SCROLL_POINTER_NONE;
            activeScrollPointerOffset = 0.0f;
        }
        if (activeTextNode == node) {
            activeTextNode = null;
            activeTextSelectionAnchor = 0;
            activeTextSelectionStartX = 0.0f;
            activeTextSelectionStartY = 0.0f;
            activeTextSelectionMoved = false;
        }
        if (lastTextTapNode == node) {
            clearLastTextTap();
        }
    }

    private void removeAnimationsForNode(String identity) {
        removeObjectAnimationsForNode(identity);
        removeFloatAnimationsForNode(identity);
    }

    private void removeObjectAnimationsForNode(String identity) {
        String prefix = identity + ":";
        for (int i = animatableKeys.size() - 1; i >= 0; i--) {
            String key = animatableKeys.get(i);
            if (key.equals(identity) || key.startsWith(prefix)) {
                animatables.remove(key);
                animatableKeys.remove(i);
                animatableValues.remove(i);
            }
        }
    }

    private void removeFloatAnimationsForNode(String identity) {
        String prefix = identity + ":";
        for (int i = floatAnimatableKeys.size() - 1; i >= 0; i--) {
            String key = floatAnimatableKeys.get(i);
            if (key.equals(identity) || key.startsWith(prefix)) {
                floatAnimatables.remove(key);
                floatAnimatableKeys.remove(i);
                floatAnimatableValues.remove(i);
            }
        }
    }


    float renderWidth() {
        int value = display != null ? display.width() : width;
        return value > 0 ? value : width;
    }

    float renderHeight() {
        int value = display != null ? display.height() : height;
        return value > 0 ? value : height;
    }

    float effectiveUiScale() {
        float scale = uiScale > 0.0f ? uiScale : 1.0f;
        if (autoUiScale && display != null) {
            scale *= display.contentScale();
        }
        return Math.max(0.25f, Math.min(4.0f, scale));
    }

    float elapsedSeconds() {
        return elapsedSeconds;
    }

    boolean tooltipActive(UiTooltip tooltip) {
        if (tooltip == null || hoveredNode == null || hoveredNode != tooltipHoverNode) {
            return false;
        }
        String target = tooltip.text();
        if (target == null || target.length() == 0 || !target.equals(tooltipTarget(hoveredNode))) {
            return false;
        }
        float wakeSeconds = tooltipHoverStartSeconds + tooltip.delayMillis() / 1000.0f;
        if (elapsedSeconds < wakeSeconds) {
            scheduleTooltipWake(wakeSeconds);
            return false;
        }
        return true;
    }

    UiRect tooltipAnchorBounds(UiTooltip tooltip) {
        if (tooltip == null || tooltip.text() == null) {
            return null;
        }
        if (tooltipActive(tooltip)) {
            UiRect bounds = hoveredNode.bounds();
            tooltipAnchors.put(tooltip.text(), bounds);
            return bounds;
        }
        return tooltipAnchors.get(tooltip.text());
    }

    boolean handlePointerMoved(PointerEvent event) {
        ensureComposed();
        float x = uiX(event.x());
        float y = uiY(event.y());
        if (ownsSurfaceCapture(event)) {
            HitResult hit = hitTestResult(x, y);
            setHovered(hit.node);
            UiPointerResult result = dispatchSurfacePointer(capturedSurfaceNode, UiPointerPhase.MOVE, event, x, y);
            applySurfacePointerResult(capturedSurfaceNode, event, x, y, result);
            return true;
        }
        if (activeWindowState != null) {
            updateActiveWindowPointer(x, y);
            return true;
        }
        if (activeScrollNode != null) {
            updateActiveScrollPointer(x, y);
            return true;
        }
        if (pendingTouchTextAreaNode != null) {
            if (updatePendingTouchTextAreaGesture(x, y)) {
                setHovered(hitTest(x, y));
                return true;
            }
            if (pendingTouchTextAreaNode != null) {
                setHovered(hitTest(x, y));
                return true;
            }
        }
        if (updatePendingScrollBodyGesture(x, y)) {
            setHovered(hitTest(x, y));
            return true;
        }
        if (updatePendingTextInputTapGesture(x, y)) {
            setHovered(hitTest(x, y));
            return true;
        }
        if (activeTextNode != null) {
            updateActiveTextSelection(x, y);
            setHovered(hitTest(x, y));
            return true;
        }
        if (isSlider(pressedNode)) {
            updateSliderFromPointer(pressedNode, x);
            setHovered(hitTest(x, y));
            return true;
        }
        HitResult hit = hitTestResult(x, y);
        setHovered(hit.node);
        if (surfaceInput(hit.node) != null) {
            UiPointerResult result = dispatchSurfacePointer(hit.node, UiPointerPhase.MOVE, event, x, y);
            applySurfacePointerResult(hit.node, event, x, y, result);
            return true;
        }
        return hit.handled();
    }

    boolean handlePointerDown(PointerEvent event) {
        ensureComposed();
        float x = uiX(event.x());
        float y = uiY(event.y());
        HitResult hit = hitTestResult(x, y);
        if (!isTextInput(hit.node)) {
            clearLastTextTap();
        }
        setHovered(hit.node);
        clearPendingScrollBodyGesture();
        clearPendingTextInputTapGesture();
        if (capturedSurfaceNode != null) {
            cancelSurfaceCapture();
        }
        if (surfaceInput(hit.node) != null) {
            pressedNode = hit.node;
            bringWindowToFront(windowState(findAncestorOrSelf(pressedNode, UiNodeType.WINDOW)));
            pressedNode.pressed(true);
            if (isFocusable(pressedNode)) {
                setFocused(pressedNode);
            }
            UiPointerResult result = dispatchSurfacePointer(pressedNode, UiPointerPhase.DOWN, event, x, y);
            applySurfacePointerResult(pressedNode, event, x, y, result);
            if (result != UiPointerResult.IGNORED) {
                return true;
            }
            pressedNode.pressed(false);
            pressedNode = null;
        }
        if (hit.node != null && hit.node.type() == UiNodeType.TEXT_AREA
                && beginScrollPointer(hit.node, x, y, false)) {
            pressedNode = null;
            return true;
        }
        UiNode scroll = findAncestorOrSelf(hit.node, UiNodeType.SCROLL);
        if (scroll != null && beginScrollPointer(scroll, x, y, hit.node == scroll)) {
            setFocused(null);
            pressedNode = null;
            return true;
        }
        pressedNode = hit.node;
        if (pressedNode != null) {
            bringWindowToFront(windowState(findAncestorOrSelf(pressedNode, UiNodeType.WINDOW)));
            pressedNode.pressed(true);
            boolean focusedTextInputTap = isTextInput(pressedNode) && focusedNode == pressedNode;
            boolean deferTextInputTap = event.type() == PointerType.TOUCH && isTextInput(pressedNode);
            if (isFocusable(pressedNode) && !deferTextInputTap) {
                setFocused(pressedNode);
            }
            if (beginWindowPointer(pressedNode, x, y)) {
                return true;
            }
            if (pressedNode.type() == UiNodeType.TEXT_AREA && beginScrollPointer(pressedNode, x, y, false)) {
                return true;
            }
            if (isTextInput(pressedNode)) {
                if (deferTextInputTap) {
                    beginPendingTextInputTapGesture(pressedNode, x, y);
                    if (pressedNode.type() == UiNodeType.TEXT_AREA) {
                        beginTouchTextAreaGesture(pressedNode, x, y);
                    }
                    beginPendingScrollBodyGesture(scroll, pressedNode, x, y);
                    return true;
                }
                if (pressedNode.type() == UiNodeType.TEXT_AREA && event.type() == PointerType.TOUCH) {
                    beginTouchTextAreaGesture(pressedNode, x, y);
                } else {
                    beginTextSelection(pressedNode, x, y);
                }
                if (focusedTextInputTap) {
                    requestPlatformTextInput(pressedNode);
                }
                return true;
            }
            if (!isSlider(pressedNode)) {
                beginPendingScrollBodyGesture(scroll, pressedNode, x, y);
            }
            updateSliderFromPointer(pressedNode, x);
            return true;
        }
        setFocused(null);
        return hit.blocked;
    }

    boolean handlePointerUp(PointerEvent event) {
        ensureComposed();
        float x = uiX(event.x());
        float y = uiY(event.y());
        HitResult hit = hitTestResult(x, y);
        if (ownsSurfaceCapture(event)) {
            UiNode captured = capturedSurfaceNode;
            dispatchSurfacePointer(captured, UiPointerPhase.UP, event, x, y);
            releaseSurfaceCapture(captured);
            if (pressedNode != null) {
                pressedNode.pressed(false);
            }
            pressedNode = null;
            setHovered(hit.node);
            return true;
        }
        boolean handled = false;
        UiNode completedTextTapNode = null;
        if (activeScrollNode != null) {
            activeScrollNode = null;
            activeScrollPointerMode = SCROLL_POINTER_NONE;
            activeScrollPointerOffset = 0.0f;
            handled = true;
        }
        if (activeTextNode != null) {
            updateActiveTextSelection(x, y);
            if (!activeTextSelectionMoved && activeTextNode == hit.node) {
                completedTextTapNode = activeTextNode;
            } else {
                clearLastTextTap();
            }
            activeTextNode = null;
            activeTextSelectionAnchor = 0;
            activeTextSelectionStartX = 0.0f;
            activeTextSelectionStartY = 0.0f;
            activeTextSelectionMoved = false;
            handled = true;
        }
        if (pendingTouchTextAreaNode != null) {
            pendingTouchTextAreaNode = null;
        }
        clearPendingScrollBodyGesture();
        UiNode pendingTextInputTap = pendingTextInputTapNode;
        clearPendingTextInputTapGesture();
        if (pressedNode != null) {
            pressedNode.pressed(false);
            if (activeWindowState != null) {
                updateActiveWindowPointer(x, y);
                activeWindowState = null;
                activeWindowNode = null;
                activeWindowPointerMode = WINDOW_POINTER_NONE;
                activeWindowArea = UiRect.ZERO;
                handled = true;
            }
            if (isSlider(pressedNode)) {
                updateSliderFromPointer(pressedNode, x);
                handled = true;
            } else if (pressedNode == hit.node) {
                updateSliderFromPointer(pressedNode, x);
                if (!handled && surfaceInput(pressedNode) != null) {
                    UiPointerResult result = dispatchSurfacePointer(
                            pressedNode, UiPointerPhase.UP, event, x, y);
                    applySurfacePointerResult(pressedNode, event, x, y, result);
                    releaseSurfaceCapture(pressedNode);
                    handled = true;
                } else if (!handled && isTabs(pressedNode)) {
                    selectTabFromPointer(pressedNode, x, y);
                    handled = true;
                } else if (!handled && pressedNode.activatable()) {
                    pressedNode.activate();
                    handled = true;
                } else if (!handled && isTextInput(pressedNode)) {
                    if (pendingTextInputTap == pressedNode) {
                        activateTextInputTap(pressedNode, x, y);
                        completedTextTapNode = pressedNode;
                    }
                    handled = true;
                }
            }
        }
        if (completedTextTapNode != null) {
            completeTextTap(completedTextTapNode, event, x, y);
        }
        pressedNode = null;
        setHovered(hit.node);
        return handled || hit.handled();
    }

    boolean handleScrolled(PointerEvent event) {
        ensureComposed();
        float x = uiX(event.x());
        float y = uiY(event.y());
        HitResult hit = hitTestResult(x, y);
        if (surfaceInput(hit.node) != null) {
            UiPointerResult result = dispatchSurfacePointer(hit.node, UiPointerPhase.SCROLL, event, x, y);
            applySurfacePointerResult(hit.node, event, x, y, result);
            if (result != UiPointerResult.IGNORED) {
                return true;
            }
        }
        UiNode scroll = scrollTarget(hit.node);
        if (scroll != null && scroll.scrollState() != null) {
            scroll.scrollState().scrollBy(event.scrollX() * 24.0f, event.scrollY() * 24.0f);
            requestCompose();
            return true;
        }
        return hit.handled();
    }

    boolean handleKeyDown(KeyEvent event) {
        ensureComposed();
        if (focusedNode == null) {
            if (event.key() == Key.TAB || event.key() == Key.DOWN || event.key() == Key.RIGHT) {
                return focusNext(1);
            }
            if (event.key() == Key.UP || event.key() == Key.LEFT) {
                return focusNext(-1);
            }
            return false;
        }
        UiSurfaceInput focusedSurfaceInput = surfaceInput(focusedNode);
        if (focusedSurfaceInput != null && focusedSurfaceInput.keyDown(event)) {
            return true;
        }
        if (handleTabsKey(focusedNode, event.key())) {
            return true;
        }
        if (handleRadioKey(focusedNode, event.key())) {
            return true;
        }
        if (event.key() == Key.TAB || (!isTextInput(focusedNode) && event.key() == Key.DOWN)
                || (!isTextInput(focusedNode) && event.key() == Key.RIGHT)) {
            return focusNext(1);
        }
        if ((!isTextInput(focusedNode) && event.key() == Key.UP)
                || (!isTextInput(focusedNode) && event.key() == Key.LEFT)) {
            return focusNext(-1);
        }
        if (isTextInput(focusedNode) && focusedNode.descriptor() instanceof UiTextFieldModel) {
            UiTextFieldModel model = (UiTextFieldModel) focusedNode.descriptor();
            if (handleTextShortcut(model, event.key())) {
                ensureTextCursorVisible(focusedNode);
                updatePlatformTextInput(focusedNode);
                return true;
            }
            if (event.key() == Key.BACKSPACE) {
                model.backspace();
                ensureTextCursorVisible(focusedNode);
                updatePlatformTextInput(focusedNode);
                return true;
            }
            if (event.key() == Key.DELETE) {
                model.delete();
                ensureTextCursorVisible(focusedNode);
                updatePlatformTextInput(focusedNode);
                return true;
            }
            if (event.key() == Key.LEFT) {
                model.moveCursor(model.previousCursor(), isShiftDown());
                ensureTextCursorVisible(focusedNode);
                updatePlatformTextInput(focusedNode);
                return true;
            }
            if (event.key() == Key.RIGHT) {
                model.moveCursor(model.nextCursor(), isShiftDown());
                ensureTextCursorVisible(focusedNode);
                updatePlatformTextInput(focusedNode);
                return true;
            }
            if (event.key() == Key.HOME) {
                model.moveCursor(0, isShiftDown());
                ensureTextCursorVisible(focusedNode);
                updatePlatformTextInput(focusedNode);
                return true;
            }
            if (event.key() == Key.END) {
                model.moveCursor(model.value().length(), isShiftDown());
                ensureTextCursorVisible(focusedNode);
                updatePlatformTextInput(focusedNode);
                return true;
            }
            if (event.key() == Key.ENTER) {
                if (model.multiline()) {
                    model.insert("\n");
                    ensureTextCursorVisible(focusedNode);
                    updatePlatformTextInput(focusedNode);
                    return true;
                }
                if (model.submit()) {
                    updatePlatformTextInput(focusedNode);
                    return true;
                }
            }
        }
        if ((event.key() == Key.ENTER || event.key() == Key.SPACE) && focusedNode.activatable()) {
            focusedNode.activate();
            return true;
        }
        return false;
    }

    private boolean handleRadioKey(UiNode node, Key key) {
        if (node == null || node.type() != UiNodeType.RADIO_BUTTON
                || !(node.descriptor() instanceof UiRadioModel)) {
            return false;
        }
        int direction;
        if (key == Key.RIGHT || key == Key.DOWN) {
            direction = 1;
        } else if (key == Key.LEFT || key == Key.UP) {
            direction = -1;
        } else {
            return false;
        }
        UiRadioModel group = (UiRadioModel) node.descriptor();
        focusableNodes.clear();
        try {
            collectRadioGroup(rootNode, group, focusableNodes);
            if (focusableNodes.isEmpty()) {
                return false;
            }
            int current = focusableNodes.indexOf(node);
            int next = current + direction;
            if (next < 0) {
                next = focusableNodes.size() - 1;
            } else if (next >= focusableNodes.size()) {
                next = 0;
            }
            UiNode choice = focusableNodes.get(next);
            choice.activate();
            setFocused(choice);
            return true;
        }
        finally {
            focusableNodes.clear();
        }
    }

    private void collectRadioGroup(UiNode node, UiRadioModel group, List<UiNode> result) {
        if (node == null || !node.visible()) {
            return;
        }
        if (node.type() == UiNodeType.RADIO_BUTTON && node.modifier().enabled()
                && node.descriptor() instanceof UiRadioModel
                && group.sameGroup((UiRadioModel) node.descriptor())) {
            result.add(node);
        }
        List<UiNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            collectRadioGroup(children.get(i), group, result);
        }
    }

    private boolean focusNext(int direction) {
        focusableNodes.clear();
        try {
            collectFocusable(rootNode, focusableNodes);
            if (focusableNodes.isEmpty()) {
                setFocused(null);
                return false;
            }
            int current = focusedNode != null ? focusableNodes.indexOf(focusedNode) : -1;
            int next = current + (direction >= 0 ? 1 : -1);
            if (next < 0) {
                next = focusableNodes.size() - 1;
            }
            if (next >= focusableNodes.size()) {
                next = 0;
            }
            setFocused(focusableNodes.get(next));
            return true;
        }
        finally {
            focusableNodes.clear();
        }
    }

    private void collectFocusable(UiNode node, List<UiNode> result) {
        if (node == null || !node.visible()) {
            return;
        }
        if (isFocusable(node)) {
            if (node.type() == UiNodeType.RADIO_BUTTON && node.descriptor() instanceof UiRadioModel) {
                UiRadioModel group = (UiRadioModel) node.descriptor();
                int existing = radioGroupIndex(result, group);
                if (existing < 0) {
                    result.add(node);
                } else if (node.checked() && !result.get(existing).checked()) {
                    result.set(existing, node);
                }
            } else {
                result.add(node);
            }
        }
        List<UiNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            collectFocusable(children.get(i), result);
        }
    }

    private int radioGroupIndex(List<UiNode> nodes, UiRadioModel group) {
        for (int i = 0; i < nodes.size(); i++) {
            UiNode node = nodes.get(i);
            if (node.descriptor() instanceof UiRadioModel
                    && group.sameGroup((UiRadioModel) node.descriptor())) {
                return i;
            }
        }
        return -1;
    }

    boolean handleTextInput(TextInputEvent event) {
        ensureComposed();
        UiSurfaceInput focusedSurfaceInput = surfaceInput(focusedNode);
        if (focusedSurfaceInput != null && focusedSurfaceInput.textInput(event)) {
            return true;
        }
        if (focusedNode != null && isTextInput(focusedNode)
                && focusedNode.descriptor() instanceof UiTextFieldModel) {
            ((UiTextFieldModel) focusedNode.descriptor()).insert(event.text());
            ensureTextCursorVisible(focusedNode);
            updatePlatformTextInput(focusedNode);
            return true;
        }
        return false;
    }

    UiNode hitTest(float x, float y) {
        return hitTestResult(x, y).node;
    }

    private HitResult hitTestResult(float x, float y) {
        hitResult.reset();
        if (rootNode == null || !rootNode.visible()) {
            return hitResult;
        }
        hitTest(rootNode, x, y, hitResult);
        return hitResult;
    }

    List<UiNode> renderChildren(UiNode node) {
        return orderedChildren(node, false);
    }

    boolean isOverlayNode(UiNode node) {
        return isOverlay(node);
    }

    UiInsets effectivePadding(UiNode node) {
        UiInsets modifierPadding = node.modifier().padding();
        UiStyle style = styleFor(node);
        UiInsets stylePadding = style != null ? style.padding() : UiInsets.ZERO;
        return node.effectivePadding(modifierPadding, stylePadding);
    }

    UiStyle styleFor(UiNode node) {
        UiStyle inline = node.modifier().inlineStyle();
        if (inline != null) {
            return inline;
        }
        String styleName = node.modifier().style();
        if (styleName == null) {
            styleName = defaultStyleName(node.type());
        }
        return styleName != null ? theme.style(styleName) : null;
    }

    BitmapFontLayout textLayout(String text, UiTextStyle style, float maxWidth) {
        return textEngine.layout(text, style, maxWidth, effectiveUiScale());
    }

    BitmapFontLayout textLayout(UiNode node, String text, UiTextStyle style, float maxWidth) {
        if (node == null) {
            return textLayout(text, style, maxWidth);
        }
        BitmapFontLayout cached = node.cachedTextLayout(layoutPass, text, style, maxWidth);
        if (cached != null) {
            return cached;
        }
        BitmapFontLayout layout = textLayout(text, style, maxWidth);
        node.cacheTextLayout(layoutPass, text, style, maxWidth, layout);
        return layout;
    }

    io.github.libfdx.graphics.g2d.BitmapFont textFont(UiTextStyle style) {
        UiTextStyle actualStyle = style != null ? style : UiTextStyle.text();
        return textEngine.resolve(actualStyle.font(), effectiveUiScale());
    }

    private void layout() {
        if (rootNode == null) {
            return;
        }
        float scale = effectiveUiScale();
        int layoutWidth = (int) Math.ceil(renderWidth() / scale);
        int layoutHeight = (int) Math.ceil(renderHeight() / scale);
        if (layoutWidth <= 0) {
            layoutWidth = width;
        }
        if (layoutHeight <= 0) {
            layoutHeight = height;
        }
        if (layoutWidth <= 0 || layoutHeight <= 0) {
            return;
        }
        UiRect rootBounds = rootNode.rendererRect(RECT_LAYOUT_ROOT, safeArea.left(), safeArea.top(),
                layoutWidth - safeArea.horizontal(), layoutHeight - safeArea.vertical());
        if (rootBounds.width() <= 0.0f || rootBounds.height() <= 0.0f) {
            return;
        }
        rootNode.bounds(rootBounds);
        layoutPass++;
        layoutChildren(rootNode, rootBounds);
    }

    private void layoutNode(UiNode node, UiRect bounds) {
        layoutNode(node, bounds, false, false);
    }

    private void layoutNode(UiNode node, UiRect bounds, boolean allowWidthOverflow, boolean allowHeightOverflow) {
        UiModifier modifier = node.modifier();
        UiInsets margin = modifier.margin();
        UiRect area = node.rendererRect(RECT_LAYOUT_AREA, bounds.x() + margin.left(), bounds.y() + margin.top(),
                bounds.width() - margin.horizontal(), bounds.height() - margin.vertical());
        UiSize preferred = preferredSize(node, area.width(), area.height());
        float widthValue = chooseDimension(modifier.width(), modifier.isFillWidth(), preferred.width(), area.width(),
                minimumWidth(node), modifier.maxWidth(), allowWidthOverflow);
        float heightValue = chooseDimension(modifier.height(), modifier.isFillHeight(), preferred.height(), area.height(),
                minimumHeight(node), modifier.maxHeight(), allowHeightOverflow);
        float x = alignX(area, widthValue, modifier.align()) + modifier.offsetX();
        float y = area.y() + modifier.offsetY();
        UiRect targetBounds = node.rendererRect(RECT_LAYOUT_TARGET, x, y, widthValue, heightValue);
        UiRect actualBounds = animatedBounds(node, targetBounds);
        node.bounds(actualBounds);
        updateTextAreaMetrics(node);
        layoutChildren(node, actualBounds);
    }

    private UiRect animatedBounds(UiNode node, UiRect targetBounds) {
        UiModifier modifier = node.modifier();
        UiRect bounds = targetBounds;
        if (modifier.contentSizeAnimation() != null) {
            UiSize targetSize = node.layoutSize(SIZE_ANIMATION_TARGET,
                    targetBounds.width(), targetBounds.height());
            UiAnimatable<UiSize> size = animatable(node.derivedKey(KEY_CONTENT_SIZE_ANIMATION, ":content-size"),
                    targetSize);
            size.animateTo(targetSize, modifier.contentSizeAnimation());
            UiSize animated = size.get();
            bounds = node.rendererRect(RECT_LAYOUT_ANIMATED_SIZE, targetBounds.x(), targetBounds.y(),
                    animated.width(), animated.height());
        }
        if (modifier.placementAnimation() != null) {
            UiAnimatable<UiRect> placement = animatable(node.derivedKey(KEY_PLACEMENT_ANIMATION, ":placement"),
                    bounds);
            placement.animateTo(bounds, modifier.placementAnimation());
            bounds = placement.get();
        }
        return bounds;
    }

    private void layoutChildren(UiNode node, UiRect bounds) {
        if (!node.visible() || node.children().isEmpty()) {
            return;
        }
        UiInsets padding = effectivePadding(node);
        UiRect inner = node.rendererRect(RECT_LAYOUT_INNER, bounds.x() + padding.left(), bounds.y() + padding.top(),
                Math.max(0.0f, bounds.width() - padding.horizontal()),
                Math.max(0.0f, bounds.height() - padding.vertical()));
        if (node.type() == UiNodeType.COLLAPSE_BAR) {
            float headerHeight = Math.min(COLLAPSE_HEADER_HEIGHT, inner.height());
            inner = node.rendererRect(RECT_LAYOUT_INNER, inner.x(), inner.y() + headerHeight,
                    inner.width(), Math.max(0.0f, inner.height() - headerHeight));
        }
        if (node.type() == UiNodeType.SCROLL && node.scrollState() != null) {
            layoutScroll(node, inner);
            layoutOverlayChildren(node, bounds);
            return;
        }
        layoutContainerChildren(node, inner);
        layoutOverlayChildren(node, bounds);
    }

    private void layoutContainerChildren(UiNode node, UiRect inner) {
        if (node.type() == UiNodeType.MODAL || node.type() == UiNodeType.POPUP
                || node.type() == UiNodeType.TOOLTIP) {
            layoutOverlay(node, inner);
        } else if (isColumnContainer(node.type())) {
            layoutColumn(node, inner);
        } else if (node.type() == UiNodeType.ROW) {
            layoutRow(node, inner);
        } else if (node.type() == UiNodeType.GRID) {
            layoutGrid(node, inner);
        } else {
            List<UiNode> children = node.children();
            for (int i = 0; i < children.size(); i++) {
                layoutNode(children.get(i), inner);
            }
        }
    }

    private void layoutScroll(UiNode node, UiRect viewport) {
        UiScrollState state = node.scrollState();
        UiRect scrolled = scrolledViewport(node, viewport, state);
        layoutContainerChildren(node, scrolled);
        if (updateScrollMetrics(node, viewport, scrolled)) {
            scrolled = scrolledViewport(node, viewport, state);
            layoutContainerChildren(node, scrolled);
            updateScrollMetrics(node, viewport, scrolled);
        }
    }

    private UiRect scrolledViewport(UiNode node, UiRect viewport, UiScrollState state) {
        return node.rendererRect(RECT_LAYOUT_SCROLLED,
                viewport.x() - state.x(), viewport.y() - state.y(), viewport.width(), viewport.height());
    }

    private boolean updateScrollMetrics(UiNode node, UiRect viewport, UiRect scrolled) {
        float contentRight = scrolled.x() + viewport.width();
        float contentBottom = scrolled.y() + viewport.height();
        List<UiNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            UiNode child = children.get(i);
            if (!isLayoutChild(child)) {
                continue;
            }
            UiRect childBounds = child.bounds();
            contentRight = Math.max(contentRight, childBounds.right());
            contentBottom = Math.max(contentBottom, childBounds.bottom());
        }
        float contentWidth = Math.max(viewport.width(), contentRight - scrolled.x());
        float contentHeight = Math.max(viewport.height(), contentBottom - scrolled.y());
        return node.scrollState().updateMetrics(viewport.width(), viewport.height(), contentWidth, contentHeight);
    }

    private void layoutColumn(UiNode node, UiRect inner) {
        List<UiNode> children = node.children();
        int childCount = layoutChildCount(children);
        float gap = node.modifier().gap();
        float availableHeight = Math.max(0.0f, inner.height() - gap * Math.max(0, childCount - 1));
        boolean allowOverflow = node.type() == UiNodeType.SCROLL;
        float fixedPreferred = 0.0f;
        float fixedMinimum = 0.0f;
        float weightedMinimum = 0.0f;
        float weight = 0.0f;
        for (int i = 0; i < children.size(); i++) {
            UiNode child = children.get(i);
            if (!isLayoutChild(child)) {
                continue;
            }
            if (child.modifier().weight() > 0.0f) {
                weight += child.modifier().weight();
                weightedMinimum += minimumHeight(child);
            } else if (child.modifier().isFillHeight()) {
                weight += 1.0f;
                weightedMinimum += minimumHeight(child);
            } else {
                fixedPreferred += preferredSize(child, inner.width(), availableHeight).height();
                fixedMinimum += minimumHeight(child);
            }
        }
        float minimumTotal = fixedMinimum + weightedMinimum;
        float minimumScale = minimumTotal > 0.0f
                ? Math.min(1.0f, availableHeight / minimumTotal)
                : 1.0f;
        float fixedAllocated = fixedPreferred;
        if (!allowOverflow && minimumScale >= 1.0f && fixedPreferred > fixedMinimum) {
            float fixedRoom = Math.max(fixedMinimum, availableHeight - weightedMinimum);
            float fixedRatio = clamp((fixedRoom - fixedMinimum) / (fixedPreferred - fixedMinimum), 0.0f, 1.0f);
            fixedAllocated = fixedMinimum + (fixedPreferred - fixedMinimum) * fixedRatio;
        } else if (!allowOverflow && minimumScale < 1.0f) {
            fixedAllocated = fixedMinimum * minimumScale;
        }
        float weightedAllocated = allowOverflow
                ? Math.max(0.0f, availableHeight - fixedPreferred)
                : Math.max(0.0f, availableHeight - fixedAllocated);
        float y = inner.y();
        for (int i = 0; i < children.size(); i++) {
            UiNode child = children.get(i);
            if (!isLayoutChild(child)) {
                continue;
            }
            float heightValue;
            float childWeight = child.modifier().weight() > 0.0f
                    ? child.modifier().weight()
                    : child.modifier().isFillHeight() ? 1.0f : 0.0f;
            if (childWeight > 0.0f && weight > 0.0f) {
                if (allowOverflow) {
                    heightValue = weightedAllocated * childWeight / weight;
                } else if (minimumScale < 1.0f) {
                    heightValue = minimumHeight(child) * minimumScale;
                } else {
                    float minimum = minimumHeight(child);
                    float extra = Math.max(0.0f, weightedAllocated - weightedMinimum);
                    heightValue = minimum + extra * childWeight / weight;
                }
            } else {
                float preferred = preferredSize(child, inner.width(), availableHeight).height();
                if (allowOverflow) {
                    heightValue = preferred;
                } else if (minimumScale < 1.0f) {
                    heightValue = minimumHeight(child) * minimumScale;
                } else if (fixedPreferred > fixedMinimum) {
                    float ratio = (fixedAllocated - fixedMinimum) / (fixedPreferred - fixedMinimum);
                    heightValue = minimumHeight(child)
                            + Math.max(0.0f, preferred - minimumHeight(child)) * ratio;
                } else {
                    heightValue = minimumHeight(child);
                }
            }
            layoutNode(child, child.rendererRect(RECT_LAYOUT_CHILD_INPUT,
                    inner.x(), y, inner.width(), heightValue), allowOverflow, allowOverflow);
            y += heightValue + gap;
        }
    }

    private void layoutRow(UiNode node, UiRect inner) {
        List<UiNode> children = node.children();
        int childCount = layoutChildCount(children);
        float gap = node.modifier().gap();
        float availableWidth = Math.max(0.0f, inner.width() - gap * Math.max(0, childCount - 1));
        float fixedPreferred = 0.0f;
        float fixedMinimum = 0.0f;
        float weightedMinimum = 0.0f;
        float weight = 0.0f;
        for (int i = 0; i < children.size(); i++) {
            UiNode child = children.get(i);
            if (!isLayoutChild(child)) {
                continue;
            }
            if (child.modifier().weight() > 0.0f) {
                weight += child.modifier().weight();
                weightedMinimum += minimumWidth(child);
            } else if (child.modifier().isFillWidth()) {
                weight += 1.0f;
                weightedMinimum += minimumWidth(child);
            } else {
                fixedPreferred += preferredSize(child, availableWidth, inner.height()).width();
                fixedMinimum += minimumWidth(child);
            }
        }
        float minimumTotal = fixedMinimum + weightedMinimum;
        float minimumScale = minimumTotal > 0.0f
                ? Math.min(1.0f, availableWidth / minimumTotal)
                : 1.0f;
        float fixedAllocated = fixedPreferred;
        if (minimumScale >= 1.0f && fixedPreferred > fixedMinimum) {
            float fixedRoom = Math.max(fixedMinimum, availableWidth - weightedMinimum);
            float fixedRatio = clamp((fixedRoom - fixedMinimum) / (fixedPreferred - fixedMinimum), 0.0f, 1.0f);
            fixedAllocated = fixedMinimum + (fixedPreferred - fixedMinimum) * fixedRatio;
        } else if (minimumScale < 1.0f) {
            fixedAllocated = fixedMinimum * minimumScale;
        }
        float weightedAllocated = Math.max(0.0f, availableWidth - fixedAllocated);
        float x = inner.x();
        for (int i = 0; i < children.size(); i++) {
            UiNode child = children.get(i);
            if (!isLayoutChild(child)) {
                continue;
            }
            UiSize preferred = preferredSize(child, availableWidth, inner.height());
            float widthValue;
            float childWeight = child.modifier().weight() > 0.0f
                    ? child.modifier().weight()
                    : child.modifier().isFillWidth() ? 1.0f : 0.0f;
            if (childWeight > 0.0f && weight > 0.0f) {
                if (minimumScale < 1.0f) {
                    widthValue = minimumWidth(child) * minimumScale;
                } else {
                    float minimum = minimumWidth(child);
                    float extra = Math.max(0.0f, weightedAllocated - weightedMinimum);
                    widthValue = minimum + extra * childWeight / weight;
                }
            } else {
                if (minimumScale < 1.0f) {
                    widthValue = minimumWidth(child) * minimumScale;
                } else if (fixedPreferred > fixedMinimum) {
                    float ratio = (fixedAllocated - fixedMinimum) / (fixedPreferred - fixedMinimum);
                    widthValue = minimumWidth(child)
                            + Math.max(0.0f, preferred.width() - minimumWidth(child)) * ratio;
                } else {
                    widthValue = minimumWidth(child);
                }
            }
            UiModifier childModifier = child.modifier();
            float heightValue = chooseDimension(childModifier.height(), childModifier.isFillHeight(),
                    preferred.height(), inner.height(), minimumHeight(child), childModifier.maxHeight(), false);
            float y = inner.y() + Math.max(0.0f, inner.height() - heightValue) * 0.5f;
            layoutNode(child, child.rendererRect(RECT_LAYOUT_CHILD_INPUT, x, y, widthValue, heightValue));
            x += widthValue + gap;
        }
    }

    private void layoutGrid(UiNode node, UiRect inner) {
        List<UiNode> children = node.children();
        int columns = Math.max(1, node.intValue());
        float gap = node.modifier().gap();
        float cellWidth = Math.max(0.0f,
                (inner.width() - gap * Math.max(0, columns - 1)) / columns);
        float y = inner.y();
        float rowHeight = 0.0f;
        int layoutIndex = 0;
        for (int i = 0; i < children.size(); i++) {
            UiNode child = children.get(i);
            if (!isLayoutChild(child)) {
                continue;
            }
            int column = layoutIndex % columns;
            if (column == 0 && layoutIndex > 0) {
                y += rowHeight + gap;
                rowHeight = 0.0f;
            }
            UiSize preferred = preferredSize(child, cellWidth, inner.height());
            float availableHeight = Math.max(0.0f, inner.bottom() - y);
            float childHeight = Math.min(preferred.height(), availableHeight);
            rowHeight = Math.max(rowHeight, childHeight);
            float x = inner.x() + column * (cellWidth + gap);
            layoutNode(child, child.rendererRect(RECT_LAYOUT_CHILD_INPUT, x, y, cellWidth, childHeight));
            layoutIndex++;
        }
    }

    private int layoutChildCount(List<UiNode> children) {
        int count = 0;
        for (int i = 0; i < children.size(); i++) {
            if (isLayoutChild(children.get(i))) {
                count++;
            }
        }
        return count;
    }

    private boolean isLayoutChild(UiNode child) {
        return child != null && child.visible() && !isOverlay(child);
    }

    private void layoutOverlayChildren(UiNode node, UiRect bounds) {
        List<UiNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            UiNode child = children.get(i);
            if (child.visible() && isOverlay(child)) {
                if (child.type() == UiNodeType.WINDOW) {
                    layoutWindow(child, bounds);
                } else {
                    child.bounds(bounds);
                    layoutChildren(child, bounds);
                }
            }
        }
    }

    private void layoutWindow(UiNode node, UiRect bounds) {
        if (!(node.descriptor() instanceof UiWindowModel)) {
            return;
        }
        UiRect area = visibleWindowArea(node, bounds);
        UiWindowModel model = (UiWindowModel) node.descriptor();
        UiWindowState state = model.state();
        ensureWindowZOrder(state);
        model.layoutArea(area);
        state.clamp(area);
        UiRect target = node.rendererRect(RECT_LAYOUT_WINDOW_TARGET,
                state.x(), state.y(), state.width(), state.height());
        UiRect actualBounds = animatedBounds(node, target);
        node.bounds(actualBounds);
        layoutChildren(node, actualBounds);
    }

    private UiRect visibleWindowArea(UiNode node, UiRect bounds) {
        UiRect rootBounds = rootNode != null ? rootNode.bounds() : bounds;
        float x = Math.max(bounds.x(), rootBounds.x());
        float y = Math.max(bounds.y(), rootBounds.y());
        float right = Math.min(bounds.right(), rootBounds.right());
        float bottom = Math.min(bounds.bottom(), rootBounds.bottom());
        return node.rendererRect(RECT_LAYOUT_WINDOW_AREA, x, y, right - x, bottom - y);
    }

    private void layoutOverlay(UiNode node, UiRect inner) {
        UiTooltip tooltip = node.descriptor() instanceof UiTooltip ? (UiTooltip) node.descriptor() : null;
        UiRect tooltipAnchor = tooltip != null ? tooltipAnchorBounds(tooltip) : null;
        List<UiNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            UiNode child = children.get(i);
            if (!isLayoutChild(child)) {
                continue;
            }
            UiSize preferred = preferredSize(child, inner.width(), inner.height());
            float widthValue = chooseDimension(child.modifier().width(), child.modifier().isFillWidth(),
                    preferred.width(), inner.width(), minimumWidth(child), child.modifier().maxWidth(), false);
            float heightValue = chooseDimension(child.modifier().height(), child.modifier().isFillHeight(),
                    preferred.height(), inner.height(), minimumHeight(child), child.modifier().maxHeight(), false);
            float x;
            float y;
            if (tooltip != null) {
                if (tooltipAnchor == null) {
                    layoutChildren(child, child.bounds());
                    continue;
                }
                UiRect anchor = tooltipAnchor;
                x = clamp(anchor.x(), inner.x(), inner.right() - widthValue) + child.modifier().offsetX();
                float aboveY = anchor.y() - heightValue - 8.0f + child.modifier().offsetY();
                float belowY = anchor.bottom() + 8.0f + child.modifier().offsetY();
                y = belowY;
                if (y + heightValue > inner.bottom() && aboveY >= inner.y()) {
                    y = aboveY;
                }
                y = clamp(y, inner.y(), inner.bottom() - heightValue);
            } else {
                x = alignOverlayX(inner, widthValue, overlayHorizontalAlign(node)) + child.modifier().offsetX();
                y = alignOverlayY(inner, heightValue, overlayVerticalAlign(node)) + child.modifier().offsetY();
            }
            UiRect target = child.rendererRect(RECT_LAYOUT_OVERLAY_TARGET, x, y, widthValue, heightValue);
            UiRect childBounds = animatedBounds(child, target);
            child.bounds(childBounds);
            layoutChildren(child, childBounds);
        }
    }

    private UiAlign overlayHorizontalAlign(UiNode node) {
        if (node.descriptor() instanceof UiPopup) {
            return ((UiPopup) node.descriptor()).horizontalAlign();
        }
        if (node.descriptor() instanceof UiTooltip) {
            return ((UiTooltip) node.descriptor()).align();
        }
        return UiAlign.CENTER;
    }

    private UiAlign overlayVerticalAlign(UiNode node) {
        if (node.descriptor() instanceof UiPopup) {
            return ((UiPopup) node.descriptor()).verticalAlign();
        }
        if (node.descriptor() instanceof UiTooltip) {
            return ((UiTooltip) node.descriptor()).align();
        }
        return UiAlign.CENTER;
    }

    private float alignOverlayX(UiRect area, float widthValue, UiAlign align) {
        if (align == UiAlign.START) {
            return area.x();
        }
        if (align == UiAlign.END) {
            return area.right() - widthValue;
        }
        return area.x() + (area.width() - widthValue) * 0.5f;
    }

    private float alignOverlayY(UiRect area, float heightValue, UiAlign align) {
        if (align == UiAlign.START) {
            return area.y();
        }
        if (align == UiAlign.END) {
            return area.bottom() - heightValue;
        }
        return area.y() + (area.height() - heightValue) * 0.5f;
    }

    private boolean isOverlay(UiNode node) {
        return node.type() == UiNodeType.MODAL || node.type() == UiNodeType.POPUP || node.type() == UiNodeType.TOOLTIP
                || node.type() == UiNodeType.WINDOW;
    }

    private UiSize preferredSize(UiNode node, float availableWidth, float availableHeight) {
        UiSize cached = node.cachedPreferredSize(layoutPass, availableWidth, availableHeight);
        if (cached != null) {
            return cached;
        }
        UiModifier modifier = node.modifier();
        if (!Float.isNaN(modifier.width()) && !Float.isNaN(modifier.height())) {
            UiSize explicit = node.layoutSize(SIZE_PREFERRED_RESULT,
                    Math.max(minimumWidth(node), modifier.width()),
                    Math.max(minimumHeight(node), modifier.height()));
            node.cachePreferredSize(layoutPass, availableWidth, availableHeight, explicit);
            return explicit;
        }
        UiInsets padding = effectivePadding(node);
        UiSize size;
        if (node.type() == UiNodeType.TEXT) {
            size = textSize(node, node.text(), padding, availableWidth);
        } else if (node.type() == UiNodeType.BUTTON) {
            UiSize text = textSize(node, node.text(), padding, availableWidth);
            size = node.layoutSize(SIZE_PREFERRED_BASE,
                    Math.max(72.0f, text.width() + 16.0f), Math.max(32.0f, text.height() + 6.0f));
        } else if (node.type() == UiNodeType.CHECKBOX) {
            if (!node.checkboxLabel()) {
                size = node.layoutSize(SIZE_PREFERRED_BASE,
                        CHECKBOX_SIZE + padding.horizontal(), CHECKBOX_SIZE + padding.vertical());
            } else {
                UiSize text = textSize(node, node.text(), padding, availableWidth);
                size = node.layoutSize(SIZE_PREFERRED_BASE,
                        text.width() + CHECKBOX_SIZE + CHECKBOX_LABEL_GAP,
                        Math.max(CHECKBOX_SIZE + padding.vertical(), text.height()));
            }
        } else if (node.type() == UiNodeType.SWITCH) {
            if (!node.checkboxLabel()) {
                size = node.layoutSize(SIZE_PREFERRED_BASE,
                        SWITCH_WIDTH + padding.horizontal(), SWITCH_HEIGHT + padding.vertical());
            } else {
                UiSize text = textSize(node, node.text(), padding, availableWidth);
                size = node.layoutSize(SIZE_PREFERRED_BASE,
                        text.width() + SWITCH_WIDTH + CHECKBOX_LABEL_GAP,
                        Math.max(SWITCH_HEIGHT + padding.vertical(), text.height()));
            }
        } else if (node.type() == UiNodeType.RADIO_BUTTON) {
            UiSize text = textSize(node, node.text(), padding, availableWidth);
            size = node.layoutSize(SIZE_PREFERRED_BASE,
                    node.checkboxLabel()
                            ? text.width() + RADIO_SIZE + CHECKBOX_LABEL_GAP
                            : RADIO_SIZE + padding.horizontal(),
                    Math.max(RADIO_SIZE + padding.vertical(), text.height()));
        } else if (node.type() == UiNodeType.SLIDER) {
            size = node.layoutSize(SIZE_PREFERRED_BASE,
                    160.0f + padding.horizontal(), 24.0f + padding.vertical());
        } else if (node.type() == UiNodeType.PROGRESS_BAR) {
            size = node.layoutSize(SIZE_PREFERRED_BASE,
                    160.0f + padding.horizontal(), 16.0f + padding.vertical());
        } else if (node.type() == UiNodeType.LOADING_BAR) {
            size = node.layoutSize(SIZE_PREFERRED_BASE,
                    160.0f + padding.horizontal(), 12.0f + padding.vertical());
        } else if (node.type() == UiNodeType.LOADING_SPINNER) {
            size = node.layoutSize(SIZE_PREFERRED_BASE,
                    28.0f + padding.horizontal(), 28.0f + padding.vertical());
        } else if (node.type() == UiNodeType.DIVIDER) {
            size = node.layoutSize(SIZE_PREFERRED_BASE,
                    64.0f + padding.horizontal(), 1.0f + padding.vertical());
        } else if (node.type() == UiNodeType.COLLAPSE_BAR) {
            UiSize text = textSize(node, node.text(), padding, availableWidth);
            UiSize content = preferredColumnSize(node, availableWidth, availableHeight, padding);
            size = node.layoutSize(SIZE_PREFERRED_BASE,
                    Math.max(text.width() + 56.0f, content.width()),
                    COLLAPSE_HEADER_HEIGHT + content.height());
        } else if (node.type() == UiNodeType.TABS) {
            size = tabsSize(node, padding);
        } else if (node.type() == UiNodeType.TEXT_FIELD) {
            UiSize text = textSize(node, nodeTextValue(node), padding, availableWidth);
            size = node.layoutSize(SIZE_PREFERRED_BASE,
                    Math.max(180.0f + padding.horizontal(), text.width() + 12.0f),
                    Math.max(28.0f, text.height() + 2.0f));
        } else if (node.type() == UiNodeType.TEXT_AREA) {
            size = textAreaSize(node, padding, availableWidth);
        } else if (node.type() == UiNodeType.IMAGE && node.image() != null) {
            size = node.layoutSize(SIZE_PREFERRED_BASE,
                    node.image().width() + padding.horizontal(), node.image().height() + padding.vertical());
        } else if (node.type() == UiNodeType.CUSTOM && node.customContext() != null
                && node.customContext().measureFunction() != null) {
            size = node.customContext().measureFunction().measure(
                    node.layoutConstraints(0.0f, 0.0f, availableWidth, availableHeight));
        } else if (node.children().isEmpty()) {
            size = node.layoutSize(SIZE_PREFERRED_BASE, padding.horizontal(), padding.vertical());
        } else if (node.type() == UiNodeType.ROW) {
            size = preferredRowSize(node, availableWidth, availableHeight, padding);
        } else if (node.type() == UiNodeType.GRID) {
            size = preferredGridSize(node, availableWidth, availableHeight, padding);
        } else {
            size = preferredColumnSize(node, availableWidth, availableHeight, padding);
        }
        UiSize result = applyExplicitPreferredSize(node, modifier, size);
        node.cachePreferredSize(layoutPass, availableWidth, availableHeight, result);
        return result;
    }

    private UiSize applyExplicitPreferredSize(UiNode node, UiModifier modifier, UiSize size) {
        float widthValue = !Float.isNaN(modifier.width()) ? modifier.width() : size.width();
        float heightValue = !Float.isNaN(modifier.height()) ? modifier.height() : size.height();
        return node.layoutSize(SIZE_PREFERRED_RESULT,
                Math.max(minimumWidth(node), widthValue),
                Math.max(minimumHeight(node), heightValue));
    }

    private boolean isColumnContainer(UiNodeType type) {
        return type == UiNodeType.COLUMN
                || type == UiNodeType.ROOT
                || type == UiNodeType.PANEL
                || type == UiNodeType.SCROLL
                || type == UiNodeType.ITEM
                || type == UiNodeType.ANIMATED_VISIBILITY
                || type == UiNodeType.COLLAPSE_BAR
                || type == UiNodeType.WINDOW
                || type == UiNodeType.MODAL
                || type == UiNodeType.POPUP
                || type == UiNodeType.TOOLTIP;
    }

    private UiSize preferredColumnSize(UiNode node, float availableWidth, float availableHeight, UiInsets padding) {
        float widthValue = padding.horizontal();
        float heightValue = padding.vertical();
        float gap = node.modifier().gap();
        int count = 0;
        List<UiNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            UiNode child = children.get(i);
            if (!isLayoutChild(child)) {
                continue;
            }
            UiSize childSize = preferredSize(child, availableWidth, availableHeight);
            widthValue = Math.max(widthValue, childSize.width() + padding.horizontal());
            heightValue += childSize.height();
            count++;
        }
        heightValue += gap * Math.max(0, count - 1);
        return node.layoutSize(SIZE_COLUMN, widthValue, heightValue);
    }

    private UiSize preferredRowSize(UiNode node, float availableWidth, float availableHeight, UiInsets padding) {
        float widthValue = padding.horizontal();
        float heightValue = padding.vertical();
        float gap = node.modifier().gap();
        int count = 0;
        List<UiNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            UiNode child = children.get(i);
            if (!isLayoutChild(child)) {
                continue;
            }
            UiSize childSize = preferredSize(child, availableWidth, availableHeight);
            widthValue += childSize.width();
            heightValue = Math.max(heightValue, childSize.height() + padding.vertical());
            count++;
        }
        widthValue += gap * Math.max(0, count - 1);
        return node.layoutSize(SIZE_ROW, widthValue, heightValue);
    }

    private UiSize preferredGridSize(UiNode node, float availableWidth, float availableHeight, UiInsets padding) {
        int columns = Math.max(1, node.intValue());
        float gap = node.modifier().gap();
        float innerAvailableWidth = Math.max(0.0f, availableWidth - padding.horizontal());
        float cellAvailableWidth = Math.max(0.0f,
                (innerAvailableWidth - gap * Math.max(0, columns - 1)) / columns);
        float maxCellWidth = 0.0f;
        float rowHeight = 0.0f;
        float rowsHeight = 0.0f;
        int count = 0;
        List<UiNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            UiNode child = children.get(i);
            if (!isLayoutChild(child)) {
                continue;
            }
            UiSize childSize = preferredSize(child, cellAvailableWidth, availableHeight);
            maxCellWidth = Math.max(maxCellWidth, childSize.width());
            rowHeight = Math.max(rowHeight, childSize.height());
            count++;
            if (count % columns == 0) {
                rowsHeight += rowHeight;
                rowHeight = 0.0f;
            }
        }
        if (count % columns != 0) {
            rowsHeight += rowHeight;
        }
        int rows = count == 0 ? 0 : (count + columns - 1) / columns;
        int usedColumns = Math.min(columns, count);
        float widthValue = padding.horizontal() + maxCellWidth * usedColumns
                + gap * Math.max(0, usedColumns - 1);
        float heightValue = padding.vertical() + rowsHeight + gap * Math.max(0, rows - 1);
        return node.layoutSize(SIZE_COLUMN, widthValue, heightValue);
    }

    private UiSize textSize(UiNode node, String text, UiInsets padding, float availableWidth) {
        int fallbackLength = text != null ? text.codePointCount(0, text.length()) : 0;
        UiStyle style = styleFor(node);
        UiTextStyle textStyle = style != null ? style.textStyle() : UiTextStyle.text();
        float maxWidth = textStyle.wrap() || textStyle.ellipsis() ? availableWidth : 0.0f;
        BitmapFontLayout layout = textLayout(text, textStyle, maxWidth);
        node.cacheTextLayout(layoutPass, text, textStyle, maxWidth, layout);
        if (layout != null) {
            return node.layoutSize(SIZE_TEXT,
                    layout.width() + padding.horizontal(), layout.height() + padding.vertical());
        }
        return node.layoutSize(SIZE_TEXT,
                fallbackLength * 8.0f + padding.horizontal(), 20.0f + padding.vertical());
    }

    private UiSize textAreaSize(UiNode node, UiInsets padding, float availableWidth) {
        UiTextFieldModel model = textModel(node);
        UiTextAreaOptions options = model != null ? model.textAreaOptions() : UiTextAreaOptions.defaults();
        UiSize text = textSize(node, nodeTextValue(node), padding, Math.max(0.0f, availableWidth - padding.horizontal()));
        float height = Math.max(options.minHeight(), text.height() + 2.0f);
        if (options.autoGrow() && !Float.isNaN(options.maxHeight())) {
            height = Math.min(height, options.maxHeight());
        } else if (!options.autoGrow()) {
            height = options.minHeight();
        }
        return node.layoutSize(SIZE_TEXT_AREA,
                Math.max(220.0f + padding.horizontal(), Math.min(availableWidth, text.width() + 12.0f)), height);
    }

    private UiSize tabsSize(UiNode node, UiInsets padding) {
        UiTabsModel model = tabsModel(node);
        int count = model != null ? model.count() : 0;
        UiTextStyle style = textStyleFor(node);
        float width = padding.horizontal();
        float height = Math.max(32.0f, textLineHeight(style) + 12.0f) + padding.vertical();
        for (int i = 0; i < count; i++) {
            width += Math.max(68.0f, textWidth(model.label(i), style) + 28.0f);
        }
        return node.layoutSize(SIZE_TABS, width, height);
    }

    private void updateTextAreaMetrics(UiNode node) {
        if (node == null || node.type() != UiNodeType.TEXT_AREA || !(node.descriptor() instanceof UiTextFieldModel)) {
            return;
        }
        UiTextFieldModel model = (UiTextFieldModel) node.descriptor();
        UiScrollState state = model.scrollState();
        if (state == null) {
            return;
        }
        UiRect viewport = textInputBounds(node);
        UiTextStyle style = textStyleFor(node);
        float lineHeight = textLineHeight(style);
        String value = model.value();
        int lineCount = textLineCount(value);
        float contentHeight = Math.max(viewport.height(), lineCount * lineHeight);
        float contentWidth = Math.max(viewport.width(), maxTextLineWidth(value, style));
        state.updateMetrics(viewport.width(), viewport.height(), contentWidth, contentHeight);
    }

    private String nodeTextValue(UiNode node) {
        if (isTextInput(node) && node.descriptor() instanceof UiTextFieldModel) {
            return ((UiTextFieldModel) node.descriptor()).value();
        }
        return node.text();
    }

    private String defaultStyleName(UiNodeType type) {
        if (type == UiNodeType.BUTTON) {
            return "button";
        }
        if (type == UiNodeType.PANEL) {
            return "panel";
        }
        if (type == UiNodeType.CHECKBOX) {
            return "checkbox";
        }
        if (type == UiNodeType.SWITCH) {
            return "switch";
        }
        if (type == UiNodeType.RADIO_BUTTON) {
            return "radio-button";
        }
        if (type == UiNodeType.SLIDER) {
            return "slider";
        }
        if (type == UiNodeType.PROGRESS_BAR) {
            return "progress-bar";
        }
        if (type == UiNodeType.LOADING_BAR || type == UiNodeType.LOADING_SPINNER) {
            return "loading-indicator";
        }
        if (type == UiNodeType.DIVIDER) {
            return "divider";
        }
        if (type == UiNodeType.COLLAPSE_BAR) {
            return "collapse-bar";
        }
        if (type == UiNodeType.TEXT_FIELD) {
            return "text-field";
        }
        if (type == UiNodeType.TEXT_AREA) {
            return "text-area";
        }
        if (type == UiNodeType.TABS) {
            return "tabs";
        }
        if (type == UiNodeType.WINDOW) {
            return "window";
        }
        if (type == UiNodeType.TEXT) {
            return "text";
        }
        return null;
    }

    private float chooseDimension(float explicit, boolean fill, float preferred, float available, float min, float max,
            boolean allowOverflow) {
        float value = !Float.isNaN(explicit) ? explicit : fill ? available : preferred;
        if (!Float.isNaN(min)) {
            value = Math.max(value, min);
        }
        if (!Float.isNaN(max)) {
            value = Math.min(value, max);
        }
        if (!allowOverflow) {
            value = Math.min(value, Math.max(0.0f, available));
        }
        return Math.max(0.0f, value);
    }

    private float minimumWidth(UiNode node) {
        float modifierMinimum = node != null ? node.modifier().minWidth() : Float.NaN;
        UiStyle style = node != null ? styleFor(node) : null;
        float styleMinimum = style != null ? style.minimumSize().width() : 0.0f;
        return Math.max(styleMinimum, Float.isNaN(modifierMinimum) ? 0.0f : modifierMinimum);
    }

    private float minimumHeight(UiNode node) {
        float modifierMinimum = node != null ? node.modifier().minHeight() : Float.NaN;
        UiStyle style = node != null ? styleFor(node) : null;
        float styleMinimum = style != null ? style.minimumSize().height() : 0.0f;
        return Math.max(styleMinimum, Float.isNaN(modifierMinimum) ? 0.0f : modifierMinimum);
    }

    private float alignX(UiRect area, float width, UiAlign align) {
        if (align == UiAlign.CENTER) {
            return area.x() + (area.width() - width) * 0.5f;
        }
        if (align == UiAlign.END) {
            return area.right() - width;
        }
        return area.x();
    }

    private boolean hitTest(UiNode node, float x, float y, HitResult result) {
        if (!node.visible()) {
            return false;
        }
        boolean contains = node.bounds().contains(x, y);
        if (!contains && clipsHitToBounds(node)) {
            return false;
        }
        if (node.type() == UiNodeType.WINDOW
                && (windowTitleBar(node).contains(x, y) || windowResizeHandle(node).contains(x, y))) {
            result.set(node, false);
            return true;
        }
        List<UiNode> children = node.children();
        if (children.size() > 1 && hasLayeredChildren(children)) {
            List<UiNode> ordered = orderedChildren(node, true);
            for (int i = 0; i < ordered.size(); i++) {
                if (hitTest(ordered.get(i), x, y, result)) {
                    return true;
                }
            }
        } else {
            for (int i = children.size() - 1; i >= 0; i--) {
                if (hitTest(children.get(i), x, y, result)) {
                    return true;
                }
            }
        }
        if (!contains) {
            return false;
        }
        if (acceptsInput(node)) {
            result.set(node, false);
            return true;
        }
        if (blocksInput(node)) {
            result.set(null, true);
            return true;
        }
        return false;
    }

    private boolean clipsHitToBounds(UiNode node) {
        UiNodeType type = node.type();
        return node.modifier().clipsToBounds()
                || type == UiNodeType.ROOT
                || type == UiNodeType.SCROLL
                || type == UiNodeType.TEXT_AREA
                || type == UiNodeType.WINDOW
                || type == UiNodeType.MODAL
                || type == UiNodeType.POPUP
                || type == UiNodeType.TOOLTIP;
    }

    private List<UiNode> orderedChildren(UiNode node, boolean frontToBack) {
        List<UiNode> children = node.children();
        if (children.size() < 2 || !hasLayeredChildren(children)) {
            return children;
        }
        if (!node.hasChildOrderRevision(childOrderRevision)) {
            ArrayList<UiNode> ordered = node.mutableOrderedChildren();
            ordered.clear();
            for (int i = 0; i < children.size(); i++) {
                ordered.add(children.get(i));
            }
            sortLayeredChildren(ordered);

            ArrayList<UiNode> reverse = node.mutableReverseOrderedChildren();
            reverse.clear();
            for (int i = ordered.size() - 1; i >= 0; i--) {
                reverse.add(ordered.get(i));
            }
            node.childOrderRevision(childOrderRevision);
        }
        return node.orderedChildren(frontToBack);
    }

    private void sortLayeredChildren(ArrayList<UiNode> children) {
        for (int i = 1; i < children.size(); i++) {
            UiNode value = children.get(i);
            int insertion = i;
            while (insertion > 0 && compareLayeredChildren(children.get(insertion - 1), value) > 0) {
                children.set(insertion, children.get(insertion - 1));
                insertion--;
            }
            children.set(insertion, value);
        }
    }

    private int compareLayeredChildren(UiNode left, UiNode right) {
        int leftLayer = layerRank(left);
        int rightLayer = layerRank(right);
        if (leftLayer != rightLayer) {
            return leftLayer < rightLayer ? -1 : 1;
        }
        if (left.type() == UiNodeType.WINDOW && right.type() == UiNodeType.WINDOW) {
            int leftZ = windowZOrder(left);
            int rightZ = windowZOrder(right);
            if (leftZ != rightZ) {
                return leftZ < rightZ ? -1 : 1;
            }
        }
        return 0;
    }

    private boolean hasLayeredChildren(List<UiNode> children) {
        for (int i = 0; i < children.size(); i++) {
            if (layerRank(children.get(i)) > 0) {
                return true;
            }
        }
        return false;
    }

    private int layerRank(UiNode node) {
        if (node.type() == UiNodeType.WINDOW) {
            return 1;
        }
        if (node.type() == UiNodeType.POPUP || node.type() == UiNodeType.TOOLTIP) {
            return 2;
        }
        if (node.type() == UiNodeType.MODAL) {
            return 3;
        }
        return 0;
    }

    private int windowZOrder(UiNode node) {
        UiWindowState state = windowState(node);
        return state != null ? state.zOrder() : 0;
    }

    private boolean acceptsInput(UiNode node) {
        if (!node.modifier().enabled()) {
            return false;
        }
        return node.activatable() || node.modifier().focusable() || node.type() == UiNodeType.SCROLL
                || node.type() == UiNodeType.SLIDER || node.type() == UiNodeType.TABS || isTextInput(node)
                || node.type() == UiNodeType.WINDOW || hasTooltipTarget(node) || surfaceInput(node) != null;
    }

    private boolean blocksInput(UiNode node) {
        if (node == null) {
            return false;
        }
        if (node.type() == UiNodeType.MODAL) {
            return true;
        }
        return node.type() == UiNodeType.POPUP && node.descriptor() instanceof UiPopup
                && ((UiPopup) node.descriptor()).blockingInput();
    }

    private boolean isFocusable(UiNode node) {
        return node != null && node.modifier().enabled() && node.modifier().focusable();
    }

    private UiSurfaceInput surfaceInput(UiNode node) {
        if (node == null || node.type() != UiNodeType.CUSTOM || node.customContext() == null) {
            return null;
        }
        return node.customContext().surfaceInput();
    }

    private UiPointerResult dispatchSurfacePointer(UiNode node, UiPointerPhase phase, PointerEvent event,
            float x, float y) {
        UiSurfaceInput surface = surfaceInput(node);
        if (surface == null || event == null) {
            return UiPointerResult.IGNORED;
        }
        boolean captured = capturedSurfaceNode == node
                && capturedSurfacePointerId == event.pointerId()
                && capturedSurfacePointerType == event.type();
        UiPointerResult result = surface.pointer(surfacePointerEvent.configure(
                phase, event.timeNanos(), event.pointerId(), event.type(), event.button(),
                x, y, event.scrollX(), event.scrollY(), node.bounds(), captured, node.focused()));
        if (captured) {
            rememberCapturedSurfaceEvent(event, x, y);
        }
        return result != null ? result : UiPointerResult.IGNORED;
    }

    private void applySurfacePointerResult(UiNode node, PointerEvent event, float x, float y,
            UiPointerResult result) {
        UiPointerResult actual = result != null ? result : UiPointerResult.IGNORED;
        if (actual == UiPointerResult.CAPTURE) {
            captureSurfacePointer(node, event, x, y);
        } else if (actual == UiPointerResult.RELEASE) {
            releaseSurfaceCapture(node);
        }
    }

    private void captureSurfacePointer(UiNode node, PointerEvent event, float x, float y) {
        if (node == null || event == null || surfaceInput(node) == null) {
            return;
        }
        if (capturedSurfaceNode != null && capturedSurfaceNode != node) {
            cancelSurfaceCapture();
        }
        capturedSurfaceNode = node;
        capturedSurfacePointerId = event.pointerId();
        capturedSurfacePointerType = event.type();
        capturedSurfaceButton = event.button();
        rememberCapturedSurfaceEvent(event, x, y);
    }

    private void rememberCapturedSurfaceEvent(PointerEvent event, float x, float y) {
        capturedSurfaceTimeNanos = event.timeNanos();
        capturedSurfaceButton = event.button();
        capturedSurfaceX = x;
        capturedSurfaceY = y;
    }

    private boolean ownsSurfaceCapture(PointerEvent event) {
        if (capturedSurfaceNode == null || event == null) {
            return false;
        }
        if (surfaceInput(capturedSurfaceNode) == null || !capturedSurfaceNode.modifier().enabled()) {
            cancelSurfaceCapture();
            return false;
        }
        return capturedSurfacePointerId == event.pointerId() && capturedSurfacePointerType == event.type();
    }

    private void releaseSurfaceCapture(UiNode node) {
        if (capturedSurfaceNode != node) {
            return;
        }
        capturedSurfaceNode = null;
        capturedSurfacePointerId = 0;
        capturedSurfacePointerType = PointerType.MOUSE;
        capturedSurfaceButton = MouseButton.UNKNOWN;
        capturedSurfaceTimeNanos = 0L;
        capturedSurfaceX = 0.0f;
        capturedSurfaceY = 0.0f;
    }

    private void cancelSurfaceCapture() {
        UiNode captured = capturedSurfaceNode;
        if (captured == null) {
            return;
        }
        UiSurfaceInput surface = surfaceInput(captured);
        if (surface != null) {
            surface.pointer(surfacePointerEvent.configure(
                    UiPointerPhase.CANCEL, capturedSurfaceTimeNanos, capturedSurfacePointerId,
                    capturedSurfacePointerType, capturedSurfaceButton, capturedSurfaceX, capturedSurfaceY,
                    0.0f, 0.0f, captured.bounds(), true, captured.focused()));
        }
        if (pressedNode == captured) {
            captured.pressed(false);
            pressedNode = null;
        }
        releaseSurfaceCapture(captured);
    }

    private void notifySurfaceFocus(UiNode node, boolean focused) {
        UiSurfaceInput surface = surfaceInput(node);
        if (surface != null) {
            surface.focusChanged(focused);
        }
    }

    private boolean isSlider(UiNode node) {
        return node != null && node.type() == UiNodeType.SLIDER && node.modifier().enabled();
    }

    private boolean isTabs(UiNode node) {
        return node != null && node.type() == UiNodeType.TABS && node.modifier().enabled();
    }

    UiTabsModel tabsModel(UiNode node) {
        return node != null && node.type() == UiNodeType.TABS && node.descriptor() instanceof UiTabsModel
                ? (UiTabsModel) node.descriptor()
                : null;
    }

    int tabCount(UiNode node) {
        UiTabsModel model = tabsModel(node);
        return model != null ? model.count() : 0;
    }

    String tabLabel(UiNode node, int index) {
        UiTabsModel model = tabsModel(node);
        return model != null ? model.label(index) : "";
    }

    int tabActiveIndex(UiNode node) {
        UiTabsModel model = tabsModel(node);
        return model != null ? model.clamp(node.intValue()) : -1;
    }

    UiRect tabBounds(UiNode node, int index) {
        int count = tabCount(node);
        if (count <= 0 || index < 0 || index >= count) {
            return UiRect.ZERO;
        }
        UiRect bounds = node.bounds();
        UiInsets padding = effectivePadding(node);
        float contentX = bounds.x() + padding.left();
        float contentY = bounds.y() + padding.top();
        float contentWidth = Math.max(0.0f, bounds.width() - padding.horizontal());
        float contentHeight = Math.max(0.0f, bounds.height() - padding.vertical());
        float tabWidth = contentWidth / count;
        float x = contentX + tabWidth * index;
        float width = index == count - 1 ? Math.max(0.0f, contentX + contentWidth - x) : tabWidth;
        return node.rendererRect(RECT_TAB_BASE + index, x, contentY, width, contentHeight);
    }

    private int tabIndexAt(UiNode node, float x, float y) {
        int count = tabCount(node);
        if (count <= 0) {
            return -1;
        }
        UiRect bounds = node.bounds();
        UiInsets padding = effectivePadding(node);
        float contentX = bounds.x() + padding.left();
        float contentY = bounds.y() + padding.top();
        float contentWidth = Math.max(0.0f, bounds.width() - padding.horizontal());
        float contentHeight = Math.max(0.0f, bounds.height() - padding.vertical());
        if (x < contentX || y < contentY || x > contentX + contentWidth || y > contentY + contentHeight
                || contentWidth <= 0.0f) {
            return -1;
        }
        int index = (int) ((x - contentX) / Math.max(1.0f, contentWidth / count));
        return Math.max(0, Math.min(count - 1, index));
    }

    private void selectTabFromPointer(UiNode node, float x, float y) {
        int index = tabIndexAt(node, x, y);
        if (index >= 0) {
            selectTab(node, index);
        }
    }

    private void selectTab(UiNode node, int index) {
        UiTabsModel model = tabsModel(node);
        if (model == null) {
            return;
        }
        int selected = model.clamp(index);
        if (selected < 0) {
            return;
        }
        model.select(selected);
        node.intValue(selected);
        requestCompose();
    }

    private boolean handleTabsKey(UiNode node, Key key) {
        if (!isTabs(node)) {
            return false;
        }
        int count = tabCount(node);
        if (count <= 0) {
            return false;
        }
        int active = tabActiveIndex(node);
        if (key == Key.LEFT) {
            selectTab(node, active <= 0 ? count - 1 : active - 1);
            return true;
        }
        if (key == Key.RIGHT) {
            selectTab(node, active >= count - 1 ? 0 : active + 1);
            return true;
        }
        if (key == Key.HOME) {
            selectTab(node, 0);
            return true;
        }
        if (key == Key.END) {
            selectTab(node, count - 1);
            return true;
        }
        return false;
    }

    private void setHovered(UiNode node) {
        if (hoveredNode == node) {
            return;
        }
        if (hoveredNode != null) {
            hoveredNode.hovered(false);
        }
        hoveredNode = node;
        tooltipHoverNode = node;
        tooltipHoverStartSeconds = elapsedSeconds;
        tooltipWakeSeconds = -1.0f;
        if (hoveredNode != null) {
            hoveredNode.hovered(true);
        }
        requestCompose();
    }

    private void scheduleTooltipWake(float wakeSeconds) {
        if (wakeSeconds < 0.0f) {
            return;
        }
        if (tooltipWakeSeconds < 0.0f || wakeSeconds < tooltipWakeSeconds) {
            tooltipWakeSeconds = wakeSeconds;
        }
    }

    private boolean tooltipWakeDue(float previousElapsedSeconds, float currentElapsedSeconds) {
        return tooltipWakeSeconds >= 0.0f
                && previousElapsedSeconds < tooltipWakeSeconds
                && currentElapsedSeconds >= tooltipWakeSeconds;
    }

    private String nodeLabel(UiNode node) {
        if (node == null) {
            return null;
        }
        String text = node.text();
        if (hasVisibleText(text)) {
            return text;
        }
        String semanticLabel = node.modifier().semanticLabel();
        return semanticLabel != null && semanticLabel.length() > 0 ? semanticLabel : text;
    }

    private boolean hasVisibleText(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTooltipTarget(UiNode node) {
        return node != null && node.modifier() != null
                && node.modifier().tooltipTarget() != null
                && node.modifier().tooltipTarget().length() > 0;
    }

    private String tooltipTarget(UiNode node) {
        if (hasTooltipTarget(node)) {
            return node.modifier().tooltipTarget();
        }
        return nodeLabel(node);
    }

    private void setFocused(UiNode node) {
        if (focusedNode == node) {
            return;
        }
        UiNode previous = focusedNode;
        if (focusedNode != null) {
            notifySurfaceFocus(focusedNode, false);
            focusedNode.focused(false);
        }
        focusedNode = node;
        if (focusedNode != null) {
            focusedNode.focused(true);
            notifySurfaceFocus(focusedNode, true);
        }
        updatePlatformTextInput(previous, focusedNode);
    }

    private void updatePlatformTextInput(UiNode previous, UiNode next) {
        if (input == null) {
            return;
        }
        if (requestsPlatformTextInput(next)) {
            input.showTextInput(textInputRequest(next));
        } else if (requestsPlatformTextInput(previous)) {
            input.hideTextInput();
        }
    }

    private void requestPlatformTextInput(UiNode node) {
        if (input != null && requestsPlatformTextInput(node)) {
            input.showTextInput(textInputRequest(node));
        }
    }

    private void updatePlatformTextInput(UiNode node) {
        if (input != null && requestsPlatformTextInput(node)) {
            input.updateTextInput(textInputRequest(node));
        }
    }

    private void hidePlatformTextInput(Input targetInput) {
        if (targetInput != null && requestsPlatformTextInput(focusedNode)) {
            targetInput.hideTextInput();
        }
    }

    private boolean requestsPlatformTextInput(UiNode node) {
        UiTextFieldModel model = textModel(node);
        return model != null && !model.readOnly();
    }

    private TextInputRequest textInputRequest(UiNode node) {
        UiTextFieldModel model = textModel(node);
        if (model == null) {
            return TextInputRequest.builder().build();
        }
        UiRect bounds = platformTextInputBounds(node, model);
        float scale = effectiveUiScale();
        return TextInputRequest.builder()
                .text(model.value())
                .selection(model.selectionStart(), model.selectionEnd())
                .multiline(model.multiline())
                .password(model.password())
                .readOnly(model.readOnly())
                .type(textInputType(model.inputFilter()))
                .bounds(Math.round(bounds.x() * scale), Math.round(bounds.y() * scale),
                        Math.max(1, Math.round(bounds.width() * scale)),
                        Math.max(1, Math.round(bounds.height() * scale)))
                .build();
    }

    private TextInputType textInputType(UiTextInputFilter inputFilter) {
        if (inputFilter == UiTextInputFilter.INTEGER) {
            return TextInputType.INTEGER;
        }
        if (inputFilter == UiTextInputFilter.FLOAT) {
            return TextInputType.DECIMAL;
        }
        return TextInputType.TEXT;
    }

    private boolean isTextInput(UiNode node) {
        return node != null && (node.type() == UiNodeType.TEXT_FIELD || node.type() == UiNodeType.TEXT_AREA);
    }

    private UiTextFieldModel textModel(UiNode node) {
        return isTextInput(node) && node.descriptor() instanceof UiTextFieldModel
                ? (UiTextFieldModel) node.descriptor()
                : null;
    }

    private UiTextStyle textStyleFor(UiNode node) {
        UiStyle style = styleFor(node);
        return style != null ? style.textStyle() : UiTextStyle.text();
    }

    private UiRect textInputBounds(UiNode node) {
        UiRect bounds = node.bounds();
        UiInsets padding = effectivePadding(node);
        return node.rendererRect(RECT_TEXT_INPUT, bounds.x() + padding.left(), bounds.y() + padding.top(),
                bounds.width() - padding.horizontal(), bounds.height() - padding.vertical());
    }

    private UiRect platformTextInputBounds(UiNode node, UiTextFieldModel model) {
        UiRect bounds = textInputBounds(node);
        if (node == null || model == null || node.type() != UiNodeType.TEXT_AREA) {
            return bounds;
        }
        float lineHeight = textLineHeight(textStyleFor(node));
        int line = lineIndexForOffset(model.value(), model.cursor());
        float scrollY = model.scrollState() != null ? model.scrollState().y() : 0.0f;
        return node.rendererRect(RECT_PLATFORM_TEXT_INPUT, bounds.x(),
                bounds.y() + line * lineHeight - scrollY, bounds.width(), lineHeight);
    }

    private float textLineHeight(UiTextStyle style) {
        UiTextStyle actual = style != null ? style : UiTextStyle.text();
        BitmapFontLayout layout = textLayout("M", actual, 0.0f);
        if (layout != null) {
            return Math.max(actual.lineHeight(), layout.lineHeight());
        }
        return Math.max(10.0f, actual.lineHeight());
    }

    private int textLineCount(String value) {
        String text = value != null ? value : "";
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private float maxTextLineWidth(String value, UiTextStyle style) {
        String text = value != null ? value : "";
        float width = 0.0f;
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                width = Math.max(width, textWidth(text, start, i, style));
                start = i + 1;
            }
        }
        return width;
    }

    private void beginTextSelection(UiNode node, float x, float y) {
        UiTextFieldModel model = textModel(node);
        if (model == null) {
            return;
        }
        int cursor = textIndexAtPointer(node, x, y);
        if (isShiftDown() && model.hasSelection()) {
            model.select(model.selectionStart(), cursor);
        } else {
            model.cursor(cursor);
        }
        activeTextNode = node;
        activeTextSelectionAnchor = model.selectionStart();
        activeTextSelectionStartX = x;
        activeTextSelectionStartY = y;
        activeTextSelectionMoved = false;
        ensureTextCursorVisible(node);
        updatePlatformTextInput(node);
        requestCompose();
    }

    private void updateActiveTextSelection(float x, float y) {
        UiTextFieldModel model = textModel(activeTextNode);
        if (model == null) {
            return;
        }
        float deltaX = x - activeTextSelectionStartX;
        float deltaY = y - activeTextSelectionStartY;
        float slop = TEXT_TAP_SLOP;
        if (deltaX * deltaX + deltaY * deltaY >= slop * slop) {
            activeTextSelectionMoved = true;
        }
        model.select(activeTextSelectionAnchor, textIndexAtPointer(activeTextNode, x, y));
        ensureTextCursorVisible(activeTextNode);
        updatePlatformTextInput(activeTextNode);
        requestCompose();
    }

    private void beginTouchTextAreaGesture(UiNode node, float x, float y) {
        UiTextFieldModel model = textModel(node);
        if (model == null) {
            return;
        }
        pendingTouchTextAreaNode = node;
        pendingTouchTextAreaStartX = x;
        pendingTouchTextAreaStartY = y;
    }

    private boolean updatePendingTouchTextAreaGesture(float x, float y) {
        UiNode node = pendingTouchTextAreaNode;
        if (node == null) {
            return false;
        }
        float deltaX = x - pendingTouchTextAreaStartX;
        float deltaY = y - pendingTouchTextAreaStartY;
        float slop = TOUCH_TEXT_AREA_DRAG_SLOP;
        if (deltaX * deltaX + deltaY * deltaY < slop * slop) {
            return false;
        }
        if (beginScrollBodyPointer(node, pendingTouchTextAreaStartX, pendingTouchTextAreaStartY)) {
            pendingTouchTextAreaNode = null;
            cancelPendingTextInputTapGesture();
            clearPendingScrollBodyGesture();
            updateActiveScrollPointer(x, y);
            return true;
        } else {
            pendingTouchTextAreaNode = null;
        }
        return false;
    }

    private int textIndexAtPointer(UiNode node, float x, float y) {
        UiTextFieldModel model = textModel(node);
        if (model == null) {
            return 0;
        }
        String value = model.value();
        UiTextStyle style = textStyleFor(node);
        UiRect bounds = textInputBounds(node);
        float localX = x - bounds.x();
        float localY = y - bounds.y();
        if (node.type() == UiNodeType.TEXT_AREA && model.scrollState() != null) {
            localX += model.scrollState().x();
            localY += model.scrollState().y();
        }
        int line = node.type() == UiNodeType.TEXT_AREA
                ? Math.max(0, Math.min(textLineCount(value) - 1, (int) (localY / Math.max(1.0f, textLineHeight(style)))))
                : 0;
        int lineStart = lineStart(value, line);
        int lineEnd = lineEnd(value, lineStart);
        return textIndexForX(value, lineStart, lineEnd, style, Math.max(0.0f, localX));
    }

    private int textCharacterAtPointer(UiNode node, float x, float y) {
        UiTextFieldModel model = textModel(node);
        if (model == null) {
            return -1;
        }
        String value = model.value();
        UiTextStyle style = textStyleFor(node);
        UiRect bounds = textInputBounds(node);
        float localX = x - bounds.x();
        float localY = y - bounds.y();
        if (node.type() == UiNodeType.TEXT_AREA && model.scrollState() != null) {
            localX += model.scrollState().x();
            localY += model.scrollState().y();
        }
        int line = node.type() == UiNodeType.TEXT_AREA
                ? Math.max(0, Math.min(textLineCount(value) - 1,
                        (int) (localY / Math.max(1.0f, textLineHeight(style)))))
                : 0;
        int start = lineStart(value, line);
        int end = lineEnd(value, start);
        if (start >= end || localX < 0.0f) {
            return -1;
        }
        UiTextStyle actual = style != null ? style : UiTextStyle.text();
        BitmapFont font = textFont(actual);
        float scale = font != null ? font.scale(actual.size()) : 1.0f;
        float cursor = 0.0f;
        int previous = -1;
        for (int i = start; i < end;) {
            int codePoint = value.codePointAt(i);
            BitmapFontGlyph glyph = font != null ? font.glyph(codePoint) : null;
            float next = cursor;
            if (glyph != null) {
                if (previous >= 0) {
                    next += font.kerning(previous, codePoint) * scale;
                }
                next += glyph.xAdvance() * scale;
                previous = codePoint;
            } else {
                next += font != null ? actual.size() * 0.5f : 8.0f;
                previous = -1;
            }
            if (localX < next) {
                return i;
            }
            cursor = next;
            i += Character.charCount(codePoint);
        }
        return -1;
    }

    private void completeTextTap(UiNode node, PointerEvent event, float x, float y) {
        if (node == null || event == null || !isPrimaryTextTap(event)) {
            clearLastTextTap();
            return;
        }
        long elapsedNanos = event.timeNanos() - lastTextTapTimeNanos;
        float deltaX = x - lastTextTapX;
        float deltaY = y - lastTextTapY;
        float slop = TEXT_TAP_SLOP;
        boolean doubleTap = node == lastTextTapNode
                && event.type() == lastTextTapType
                && event.pointerId() == lastTextTapPointerId
                && elapsedNanos >= 0L
                && elapsedNanos <= DOUBLE_TEXT_TAP_TIMEOUT_NANOS
                && deltaX * deltaX + deltaY * deltaY <= slop * slop;
        if (doubleTap) {
            selectTextUnitAtPointer(node, x, y);
            clearLastTextTap();
            return;
        }
        lastTextTapNode = node;
        lastTextTapType = event.type();
        lastTextTapPointerId = event.pointerId();
        lastTextTapTimeNanos = event.timeNanos();
        lastTextTapX = x;
        lastTextTapY = y;
    }

    private boolean isPrimaryTextTap(PointerEvent event) {
        return event.type() == PointerType.TOUCH || event.button() == MouseButton.LEFT;
    }

    private void clearLastTextTap() {
        lastTextTapNode = null;
        lastTextTapType = null;
        lastTextTapPointerId = 0;
        lastTextTapTimeNanos = Long.MIN_VALUE;
        lastTextTapX = 0.0f;
        lastTextTapY = 0.0f;
    }

    private void selectTextUnitAtPointer(UiNode node, float x, float y) {
        UiTextFieldModel model = textModel(node);
        if (model == null) {
            return;
        }
        String value = model.value();
        int characterIndex = textCharacterAtPointer(node, x, y);
        if (characterIndex < 0 || characterIndex >= value.length()) {
            model.cursor(textIndexAtPointer(node, x, y));
        } else {
            int codePoint = value.codePointAt(characterIndex);
            int category = textSelectionCategory(codePoint);
            int start = characterIndex;
            int end = characterIndex + Character.charCount(codePoint);
            if (category != 2) {
                while (start > 0) {
                    int previous = value.codePointBefore(start);
                    if (previous == '\n' || textSelectionCategory(previous) != category) {
                        break;
                    }
                    start -= Character.charCount(previous);
                }
                while (end < value.length()) {
                    int next = value.codePointAt(end);
                    if (next == '\n' || textSelectionCategory(next) != category) {
                        break;
                    }
                    end += Character.charCount(next);
                }
            }
            model.select(start, end);
        }
        ensureTextCursorVisible(node);
        updatePlatformTextInput(node);
        requestCompose();
    }

    private int textSelectionCategory(int codePoint) {
        int type = Character.getType(codePoint);
        if (Character.isLetterOrDigit(codePoint)
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || type == Character.CONNECTOR_PUNCTUATION) {
            return 0;
        }
        if (Character.isWhitespace(codePoint)) {
            return 1;
        }
        return 2;
    }

    private int lineStart(String value, int targetLine) {
        int line = 0;
        String text = value != null ? value : "";
        for (int i = 0; i < text.length(); i++) {
            if (line == targetLine) {
                return i;
            }
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return text.length();
    }

    private int lineEnd(String value, int start) {
        String text = value != null ? value : "";
        int index = Math.max(0, Math.min(start, text.length()));
        while (index < text.length() && text.charAt(index) != '\n') {
            index++;
        }
        return index;
    }

    private int lineIndexForOffset(String value, int offset) {
        String text = value != null ? value : "";
        int clamped = Math.max(0, Math.min(offset, text.length()));
        int line = 0;
        for (int i = 0; i < clamped; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private int textIndexForX(String text, int start, int end, UiTextStyle style, float x) {
        if (text == null || start >= end || x <= 0.0f) {
            return Math.max(0, start);
        }
        UiTextStyle actual = style != null ? style : UiTextStyle.text();
        float cursor = 0.0f;
        int safeStart = Math.max(0, Math.min(start, text.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, text.length()));
        BitmapFont font = textFont(actual);
        float scale = font != null ? font.scale(actual.size()) : 1.0f;
        int previous = -1;
        for (int i = safeStart; i < safeEnd;) {
            int codePoint = text.codePointAt(i);
            BitmapFontGlyph glyph = font != null ? font.glyph(codePoint) : null;
            float next = cursor;
            if (glyph != null) {
                if (previous >= 0) {
                    next += font.kerning(previous, codePoint) * scale;
                }
                next += glyph.xAdvance() * scale;
                previous = codePoint;
            } else {
                next += font != null ? actual.size() * 0.5f : 8.0f;
                previous = -1;
            }
            if (x < (cursor + next) * 0.5f) {
                return i;
            }
            cursor = next;
            i += Character.charCount(codePoint);
        }
        return safeEnd;
    }

    /**
     * Returns the rendered caret bounds for a text offset.
     *
     * @param node the text field or text area node
     * @param offset the UTF-16 text offset
     * @return the caret bounds, or {@code null} when the node is not a text input
     */
    public UiRect textCaretBounds(UiNode node, int offset) {
        UiTextFieldModel model = textModel(node);
        if (model == null) {
            return null;
        }
        String text = model.value();
        int cursor = codePointBoundary(text, offset);
        UiTextStyle style = textStyleFor(node);
        UiRect bounds = textInputBounds(node);
        float lineHeight = Math.min(bounds.height(), Math.max(12.0f, textLineHeight(style)));
        if (node.type() == UiNodeType.TEXT_AREA) {
            int line = lineIndexForOffset(text, cursor);
            int start = lineStart(text, line);
            int end = Math.max(start, Math.min(cursor, lineEnd(text, start)));
            float scrollX = model.scrollState() != null ? model.scrollState().x() : 0.0f;
            float x = bounds.x() + textWidth(text, start, end, style) - scrollX;
            float y = bounds.y() + line * textLineHeight(style)
                    - (model.scrollState() != null ? model.scrollState().y() : 0.0f);
            return node.rendererRect(RECT_TEXT_CARET, x, y, 1.5f, textLineHeight(style));
        }
        float cursorX = bounds.x() + textWidth(text, 0, cursor, style);
        float y = bounds.y() + Math.max(0.0f, (bounds.height() - lineHeight) * 0.5f);
        return node.rendererRect(RECT_TEXT_CARET, cursorX, y, 1.5f, lineHeight);
    }

    private float textWidth(String text, UiTextStyle style) {
        return textWidth(text, 0, text != null ? text.length() : 0, style);
    }

    private float textWidth(String text, int start, int end, UiTextStyle style) {
        UiTextStyle actual = style != null ? style : UiTextStyle.text();
        int safeStart = text != null ? Math.max(0, Math.min(start, text.length())) : 0;
        int safeEnd = text != null ? Math.max(safeStart, Math.min(end, text.length())) : 0;
        BitmapFont font = textFont(actual);
        if (font != null) {
            float scale = font.scale(actual.size());
            float width = 0.0f;
            int previous = -1;
            for (int i = safeStart; i < safeEnd;) {
                int codePoint = text.codePointAt(i);
                BitmapFontGlyph glyph = font.glyph(codePoint);
                if (glyph == null) {
                    width += actual.size() * 0.5f;
                    previous = -1;
                    i += Character.charCount(codePoint);
                    continue;
                }
                if (previous >= 0) {
                    width += font.kerning(previous, codePoint) * scale;
                }
                width += glyph.xAdvance() * scale;
                previous = codePoint;
                i += Character.charCount(codePoint);
            }
            return width;
        }
        return text.codePointCount(safeStart, safeEnd) * 8.0f;
    }

    private int codePointBoundary(String text, int offset) {
        int length = text != null ? text.length() : 0;
        int value = Math.max(0, Math.min(length, offset));
        if (value > 0 && value < length
                && Character.isLowSurrogate(text.charAt(value))
                && Character.isHighSurrogate(text.charAt(value - 1))) {
            value--;
        }
        return value;
    }

    private boolean handleTextShortcut(UiTextFieldModel model, Key key) {
        if (!isShortcutDown()) {
            return false;
        }
        if (key == Key.A) {
            model.selectAll();
            return true;
        }
        if (key == Key.C) {
            copySelection(model);
            return true;
        }
        if (key == Key.X) {
            copySelection(model);
            model.deleteSelection();
            return true;
        }
        if (key == Key.V) {
            model.insert(clipboardText());
            return true;
        }
        return false;
    }

    private void copySelection(UiTextFieldModel model) {
        String selected = model.selectedText();
        if (selected.length() > 0 && input != null && input.clipboard() != null) {
            input.clipboard().setText(selected);
        }
    }

    private String clipboardText() {
        if (input == null || input.clipboard() == null) {
            return "";
        }
        String text = input.clipboard().getText();
        return text != null ? text : "";
    }

    private boolean isShortcutDown() {
        return input != null && (input.isKeyPressed(Key.CONTROL_LEFT) || input.isKeyPressed(Key.CONTROL_RIGHT));
    }

    private boolean isShiftDown() {
        return input != null && (input.isKeyPressed(Key.SHIFT_LEFT) || input.isKeyPressed(Key.SHIFT_RIGHT));
    }

    private void ensureTextCursorVisible(UiNode node) {
        UiTextFieldModel model = textModel(node);
        if (model == null || node.type() != UiNodeType.TEXT_AREA || model.scrollState() == null) {
            return;
        }
        UiScrollState state = model.scrollState();
        UiRect viewport = textInputBounds(node);
        UiTextStyle style = textStyleFor(node);
        float lineHeight = textLineHeight(style);
        String value = model.value();
        int cursor = model.cursor();
        int line = lineIndexForOffset(value, cursor);
        int start = lineStart(value, line);
        int end = Math.max(start, Math.min(cursor, lineEnd(value, start)));
        float cursorLeft = textWidth(value, start, end, style);
        float cursorRight = cursorLeft + 1.5f;
        float cursorTop = line * lineHeight;
        float cursorBottom = cursorTop + lineHeight;
        float x = state.x();
        float y = state.y();
        if (cursorLeft < x) {
            x = cursorLeft;
        } else if (cursorRight > x + viewport.width()) {
            x = cursorRight - viewport.width();
        }
        if (cursorTop < y) {
            y = cursorTop;
        } else if (cursorBottom > y + viewport.height()) {
            y = cursorBottom - viewport.height();
        }
        state.scrollTo(x, y);
    }

    private UiNode scrollTarget(UiNode node) {
        if (node == null) {
            return null;
        }
        if (node.type() == UiNodeType.TEXT_AREA && node.scrollState() != null) {
            return node;
        }
        return findAncestorOrSelf(node, UiNodeType.SCROLL);
    }

    private boolean isScrollable(UiNode node) {
        return node != null && (node.type() == UiNodeType.SCROLL || node.type() == UiNodeType.TEXT_AREA);
    }


    private UiNode findAncestorOrSelf(UiNode node, UiNodeType type) {
        UiNode current = node;
        while (current != null) {
            if (current.type() == type) {
                return current;
            }
            current = current.parent();
        }
        return null;
    }

    private boolean beginWindowPointer(UiNode node, float x, float y) {
        if (node == null || node.type() != UiNodeType.WINDOW || !(node.descriptor() instanceof UiWindowModel)) {
            return false;
        }
        UiWindowModel model = (UiWindowModel) node.descriptor();
        UiWindowState state = model.state();
        bringWindowToFront(state);
        activeWindowState = state;
        activeWindowNode = node;
        activeWindowArea = model.layoutArea();
        if (activeWindowArea.width() <= 0.0f || activeWindowArea.height() <= 0.0f) {
            activeWindowArea = rootNode != null ? rootNode.bounds() : UiRect.ZERO;
        }
        activeWindowStartPointerX = x;
        activeWindowStartPointerY = y;
        activeWindowStartX = state.x();
        activeWindowStartY = state.y();
        activeWindowStartWidth = state.width();
        activeWindowStartHeight = state.height();
        if (windowResizeHandle(node).contains(x, y)) {
            activeWindowPointerMode = WINDOW_POINTER_RESIZE;
        } else {
            activeWindowPointerMode = WINDOW_POINTER_DRAG;
        }
        return true;
    }

    private boolean beginScrollPointer(UiNode node, float pointerX, float pointerY, boolean allowBodyDrag) {
        if (node == null || !node.modifier().enabled() || !isScrollable(node) || node.scrollState() == null) {
            return false;
        }
        UiScrollState state = node.scrollState();
        UiRect verticalTrack = scrollVerticalTrack(node);
        UiRect verticalThumb = scrollVerticalThumb(node, state);
        if (state.canScrollY() && verticalTrack != null && verticalTrack.contains(pointerX, pointerY)) {
            activeScrollNode = node;
            activeScrollPointerMode = SCROLL_POINTER_VERTICAL;
            if (verticalThumb != null && verticalThumb.contains(pointerX, pointerY)) {
                activeScrollPointerOffset = pointerY - verticalThumb.y();
            } else {
                state.scrollTo(state.x(), scrollYFromPointer(node, state, pointerY));
                requestCompose();
                UiRect updatedThumb = scrollVerticalThumb(node, state);
                if (updatedThumb != null) {
                    activeScrollPointerOffset = pointerY - updatedThumb.y();
                } else {
                    activeScrollPointerOffset = SCROLLBAR_MIN_THUMB * 0.5f;
                }
            }
            return true;
        }
        UiRect horizontalTrack = scrollHorizontalTrack(node);
        UiRect horizontalThumb = scrollHorizontalThumb(node, state);
        if (state.canScrollX() && horizontalTrack != null && horizontalTrack.contains(pointerX, pointerY)) {
            activeScrollNode = node;
            activeScrollPointerMode = SCROLL_POINTER_HORIZONTAL;
            if (horizontalThumb != null && horizontalThumb.contains(pointerX, pointerY)) {
                activeScrollPointerOffset = pointerX - horizontalThumb.x();
            } else {
                state.scrollTo(scrollXFromPointer(node, state, pointerX), state.y());
                requestCompose();
                UiRect updatedThumb = scrollHorizontalThumb(node, state);
                if (updatedThumb != null) {
                    activeScrollPointerOffset = pointerX - updatedThumb.x();
                } else {
                    activeScrollPointerOffset = SCROLLBAR_MIN_THUMB * 0.5f;
                }
            }
            return true;
        }
        UiRect body = node.bounds().inset(effectivePadding(node));
        if (allowBodyDrag && body.contains(pointerX, pointerY) && (state.canScrollX() || state.canScrollY())) {
            return beginScrollBodyPointer(node, pointerX, pointerY);
        }
        return false;
    }

    private boolean beginScrollBodyPointer(UiNode node, float pointerX, float pointerY) {
        if (node == null || !node.modifier().enabled() || !isScrollable(node) || node.scrollState() == null) {
            return false;
        }
        UiScrollState state = node.scrollState();
        if (!state.canScrollX() && !state.canScrollY()) {
            return false;
        }
        activeScrollNode = node;
        activeScrollPointerMode = SCROLL_POINTER_BODY;
        activeScrollStartPointerX = pointerX;
        activeScrollStartPointerY = pointerY;
        activeScrollStartX = state.x();
        activeScrollStartY = state.y();
        return true;
    }

    private void beginPendingScrollBodyGesture(UiNode scroll, UiNode pressed, float pointerX, float pointerY) {
        if (scroll == null || pressed == null || scroll == pressed || isSlider(pressed)) {
            return;
        }
        if (!scroll.modifier().enabled() || !isScrollable(scroll) || scroll.scrollState() == null) {
            return;
        }
        UiScrollState state = scroll.scrollState();
        if (!state.canScrollX() && !state.canScrollY()) {
            return;
        }
        UiRect body = scroll.bounds().inset(effectivePadding(scroll));
        if (!body.contains(pointerX, pointerY)) {
            return;
        }
        pendingScrollBodyNode = scroll;
        pendingScrollBodyStartX = pointerX;
        pendingScrollBodyStartY = pointerY;
    }

    private boolean updatePendingScrollBodyGesture(float pointerX, float pointerY) {
        if (pendingScrollBodyNode == null) {
            return false;
        }
        float deltaX = pointerX - pendingScrollBodyStartX;
        float deltaY = pointerY - pendingScrollBodyStartY;
        float slop = SCROLL_BODY_DRAG_SLOP;
        if (deltaX * deltaX + deltaY * deltaY < slop * slop) {
            return false;
        }
        UiNode scroll = pendingScrollBodyNode;
        float startX = pendingScrollBodyStartX;
        float startY = pendingScrollBodyStartY;
        clearPendingScrollBodyGesture();
        if (!beginScrollBodyPointer(scroll, startX, startY)) {
            return false;
        }
        if (pressedNode != null) {
            pressedNode.pressed(false);
            pressedNode = null;
        }
        clearPendingTextInputTapGesture();
        setFocused(null);
        updateActiveScrollPointer(pointerX, pointerY);
        return true;
    }

    private void clearPendingScrollBodyGesture() {
        pendingScrollBodyNode = null;
        pendingScrollBodyStartX = 0.0f;
        pendingScrollBodyStartY = 0.0f;
    }

    private void beginPendingTextInputTapGesture(UiNode node, float pointerX, float pointerY) {
        if (!isTextInput(node)) {
            return;
        }
        pendingTextInputTapNode = node;
        pendingTextInputTapStartX = pointerX;
        pendingTextInputTapStartY = pointerY;
    }

    private boolean updatePendingTextInputTapGesture(float pointerX, float pointerY) {
        if (pendingTextInputTapNode == null) {
            return false;
        }
        float deltaX = pointerX - pendingTextInputTapStartX;
        float deltaY = pointerY - pendingTextInputTapStartY;
        float slop = SCROLL_BODY_DRAG_SLOP;
        if (deltaX * deltaX + deltaY * deltaY < slop * slop) {
            return false;
        }
        cancelPendingTextInputTapGesture();
        return true;
    }

    private void cancelPendingTextInputTapGesture() {
        clearPendingTextInputTapGesture();
        clearLastTextTap();
        if (pressedNode != null && isTextInput(pressedNode)) {
            pressedNode.pressed(false);
            pressedNode = null;
        }
    }

    private void clearPendingTextInputTapGesture() {
        pendingTextInputTapNode = null;
        pendingTextInputTapStartX = 0.0f;
        pendingTextInputTapStartY = 0.0f;
    }

    private void activateTextInputTap(UiNode node, float x, float y) {
        UiTextFieldModel model = textModel(node);
        if (model == null) {
            return;
        }
        model.cursor(textIndexAtPointer(node, x, y));
        ensureTextCursorVisible(node);
        boolean alreadyFocused = focusedNode == node;
        setFocused(node);
        if (alreadyFocused) {
            requestPlatformTextInput(node);
        }
        updatePlatformTextInput(node);
        requestCompose();
    }

    private void updateActiveScrollPointer(float pointerX, float pointerY) {
        if (activeScrollNode == null || activeScrollNode.scrollState() == null) {
            return;
        }
        UiScrollState state = activeScrollNode.scrollState();
        if (activeScrollPointerMode == SCROLL_POINTER_BODY) {
            float deltaX = pointerX - activeScrollStartPointerX;
            float deltaY = pointerY - activeScrollStartPointerY;
            state.scrollTo(activeScrollStartX - deltaX, activeScrollStartY - deltaY);
            requestCompose();
            return;
        }
        if (activeScrollPointerMode == SCROLL_POINTER_VERTICAL) {
            UiRect track = scrollVerticalTrack(activeScrollNode);
            UiRect thumb = scrollVerticalThumb(activeScrollNode, state);
            if (track == null || thumb == null || state.maxY() <= 0.0f) {
                state.scrollTo(state.x(), 0.0f);
                return;
            }
            float travel = Math.max(0.0f, track.height() - thumb.height());
            if (travel <= 0.0f) {
                state.scrollTo(state.x(), 0.0f);
                return;
            }
            float target = (pointerY - track.y() - activeScrollPointerOffset) / travel * state.maxY();
            state.scrollTo(state.x(), target);
            requestCompose();
            return;
        }
        if (activeScrollPointerMode == SCROLL_POINTER_HORIZONTAL) {
            UiRect track = scrollHorizontalTrack(activeScrollNode);
            UiRect thumb = scrollHorizontalThumb(activeScrollNode, state);
            if (track == null || thumb == null || state.maxX() <= 0.0f) {
                state.scrollTo(0.0f, state.y());
                return;
            }
            float travel = Math.max(0.0f, track.width() - thumb.width());
            if (travel <= 0.0f) {
                state.scrollTo(0.0f, state.y());
                return;
            }
            float target = (pointerX - track.x() - activeScrollPointerOffset) / travel * state.maxX();
            state.scrollTo(target, state.y());
            requestCompose();
        }
    }

    private UiRect scrollVerticalTrack(UiNode node) {
        if (node == null) {
            return null;
        }
        UiRect bounds = node.bounds();
        UiInsets padding = effectivePadding(node);
        float x = bounds.x() + padding.left();
        float y = bounds.y() + padding.top();
        float width = Math.max(0.0f, bounds.width() - padding.horizontal());
        float height = Math.max(0.0f, bounds.height() - padding.vertical());
        if (!isScrollable(node) || width <= 0.0f || height <= 0.0f) {
            return null;
        }
        float contentRight = x + width;
        float hitWidth = Math.min(bounds.width(), SCROLLBAR_HIT_SIZE + padding.right());
        return node.rendererRect(RECT_SCROLL_VERTICAL_TRACK, contentRight - SCROLLBAR_HIT_SIZE, y,
                hitWidth, height);
    }

    private UiRect scrollHorizontalTrack(UiNode node) {
        if (node == null) {
            return null;
        }
        UiRect bounds = node.bounds();
        UiInsets padding = effectivePadding(node);
        float x = bounds.x() + padding.left();
        float y = bounds.y() + padding.top();
        float width = Math.max(0.0f, bounds.width() - padding.horizontal());
        float height = Math.max(0.0f, bounds.height() - padding.vertical());
        if (!isScrollable(node) || width <= 0.0f || height <= 0.0f) {
            return null;
        }
        float contentBottom = y + height;
        float hitHeight = Math.min(bounds.height(), SCROLLBAR_HIT_SIZE + padding.bottom());
        return node.rendererRect(RECT_SCROLL_HORIZONTAL_TRACK, x, contentBottom - SCROLLBAR_HIT_SIZE,
                width, hitHeight);
    }

    private UiRect scrollVerticalThumb(UiNode node, UiScrollState state) {
        UiRect track = scrollVerticalTrack(node);
        if (track == null || state == null || !state.canScrollY() || state.contentHeight() <= 0.0f) {
            return null;
        }
        float thumbHeight = Math.max(SCROLLBAR_MIN_THUMB, track.height() * state.viewportHeight() / state.contentHeight());
        thumbHeight = Math.min(track.height(), thumbHeight);
        float travel = Math.max(0.0f, track.height() - thumbHeight);
        if (travel <= 0.0f) {
            return node.rendererRect(RECT_SCROLL_VERTICAL_THUMB,
                    track.x(), track.y(), track.width(), track.height());
        }
        float thumbY = track.y() + travel * state.y() / Math.max(1.0f, state.maxY());
        return node.rendererRect(RECT_SCROLL_VERTICAL_THUMB,
                track.x(), thumbY, track.width(), thumbHeight);
    }

    private UiRect scrollHorizontalThumb(UiNode node, UiScrollState state) {
        UiRect track = scrollHorizontalTrack(node);
        if (track == null || state == null || !state.canScrollX() || state.contentWidth() <= 0.0f) {
            return null;
        }
        float thumbWidth = Math.max(SCROLLBAR_MIN_THUMB, track.width() * state.viewportWidth() / state.contentWidth());
        thumbWidth = Math.min(track.width(), thumbWidth);
        float travel = Math.max(0.0f, track.width() - thumbWidth);
        if (travel <= 0.0f) {
            return node.rendererRect(RECT_SCROLL_HORIZONTAL_THUMB,
                    track.x(), track.y(), track.width(), track.height());
        }
        float thumbX = track.x() + travel * state.x() / Math.max(1.0f, state.maxX());
        return node.rendererRect(RECT_SCROLL_HORIZONTAL_THUMB,
                thumbX, track.y(), thumbWidth, track.height());
    }

    private float scrollYFromPointer(UiNode node, UiScrollState state, float pointerY) {
        UiRect track = scrollVerticalTrack(node);
        UiRect thumb = scrollVerticalThumb(node, state);
        if (track == null || thumb == null || state == null || state.maxY() <= 0.0f) {
            return 0.0f;
        }
        float halfThumb = thumb.height() * 0.5f;
        float clamped = clamp(pointerY - track.y() - halfThumb, 0.0f, track.height() - thumb.height());
        return clamped * state.maxY() / Math.max(1.0f, track.height() - thumb.height());
    }

    private float scrollXFromPointer(UiNode node, UiScrollState state, float pointerX) {
        UiRect track = scrollHorizontalTrack(node);
        UiRect thumb = scrollHorizontalThumb(node, state);
        if (track == null || thumb == null || state == null || state.maxX() <= 0.0f) {
            return 0.0f;
        }
        float halfThumb = thumb.width() * 0.5f;
        float clamped = clamp(pointerX - track.x() - halfThumb, 0.0f, track.width() - thumb.width());
        return clamped * state.maxX() / Math.max(1.0f, track.width() - thumb.width());
    }

    private UiWindowState windowState(UiNode node) {
        if (node == null || !(node.descriptor() instanceof UiWindowModel)) {
            return null;
        }
        return ((UiWindowModel) node.descriptor()).state();
    }

    void ensureWindowZOrder(UiWindowState state) {
        if (state == null) {
            return;
        }
        if (state.zOrder() <= 0) {
            state.zOrder(nextWindowZOrder++);
            childOrderRevision++;
        } else if (state.zOrder() >= nextWindowZOrder) {
            nextWindowZOrder = state.zOrder() + 1;
        }
    }

    void bringWindowToFront(UiWindowState state) {
        if (state == null) {
            return;
        }
        ensureWindowZOrder(state);
        if (state.zOrder() < nextWindowZOrder - 1) {
            state.zOrder(nextWindowZOrder++);
            childOrderRevision++;
        }
    }

    private void updateActiveWindowPointer(float x, float y) {
        if (activeWindowState == null) {
            return;
        }
        float deltaX = x - activeWindowStartPointerX;
        float deltaY = y - activeWindowStartPointerY;
        if (activeWindowPointerMode == WINDOW_POINTER_DRAG) {
            float maxX = Math.max(activeWindowArea.x(), activeWindowArea.right() - activeWindowStartWidth);
            float maxY = Math.max(activeWindowArea.y(), activeWindowArea.bottom() - activeWindowStartHeight);
            float nextX = clamp(activeWindowStartX + deltaX, activeWindowArea.x(), maxX);
            float nextY = clamp(activeWindowStartY + deltaY, activeWindowArea.y(), maxY);
            activeWindowState.position(nextX, nextY);
            moveActiveWindowNode(nextX, nextY);
        } else if (activeWindowPointerMode == WINDOW_POINTER_RESIZE) {
            float maxWidth = Math.max(activeWindowState.minWidth(), activeWindowArea.right() - activeWindowStartX);
            float maxHeight = Math.max(activeWindowState.minHeight(), activeWindowArea.bottom() - activeWindowStartY);
            activeWindowState.position(activeWindowStartX, activeWindowStartY)
                    .size(clamp(activeWindowStartWidth + deltaX, activeWindowState.minWidth(), maxWidth),
                            clamp(activeWindowStartHeight + deltaY, activeWindowState.minHeight(), maxHeight));
            resizeActiveWindowNode();
        }
    }

    private void moveActiveWindowNode(float nextX, float nextY) {
        if (activeWindowNode == null) {
            return;
        }
        UiRect bounds = activeWindowNode.bounds();
        float deltaX = nextX - bounds.x();
        float deltaY = nextY - bounds.y();
        if (deltaX == 0.0f && deltaY == 0.0f) {
            return;
        }
        translateNode(activeWindowNode, deltaX, deltaY);
    }

    private void resizeActiveWindowNode() {
        if (activeWindowNode == null || activeWindowState == null) {
            return;
        }
        activeWindowNode.bounds(activeWindowState.x(), activeWindowState.y(),
                activeWindowState.width(), activeWindowState.height());
        layoutChildren(activeWindowNode, activeWindowNode.bounds());
    }

    private void translateNode(UiNode node, float deltaX, float deltaY) {
        UiRect bounds = node.bounds();
        node.bounds(bounds.x() + deltaX, bounds.y() + deltaY, bounds.width(), bounds.height());
        List<UiNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            translateNode(children.get(i), deltaX, deltaY);
        }
    }

    private float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    UiRect windowTitleBar(UiNode node) {
        if (node == null) {
            return UiRect.ZERO;
        }
        UiRect bounds = node.bounds();
        return node.rendererRect(RECT_WINDOW_TITLE, bounds.x(), bounds.y(), bounds.width(),
                Math.min(WINDOW_TITLE_HEIGHT, bounds.height()));
    }

    UiRect windowResizeHandle(UiNode node) {
        if (node == null) {
            return UiRect.ZERO;
        }
        UiRect bounds = node.bounds();
        float size = Math.min(WINDOW_RESIZE_HANDLE, Math.min(bounds.width(), bounds.height()));
        return node.rendererRect(RECT_WINDOW_RESIZE,
                bounds.right() - size, bounds.bottom() - size, size, size);
    }

    private void updateSliderFromPointer(UiNode node, float x) {
        if (node == null || node.type() != UiNodeType.SLIDER || !(node.descriptor() instanceof UiSliderModel)) {
            return;
        }
        UiSliderModel model = (UiSliderModel) node.descriptor();
        float trackX = node.bounds().x() + 8.0f;
        float trackWidth = Math.max(1.0f, node.bounds().width() - 16.0f);
        float progress = Math.max(0.0f, Math.min(1.0f, (x - trackX) / trackWidth));
        UiRange range = model.range();
        float value = range.minimum() + (range.maximum() - range.minimum()) * progress;
        float clamped = range.clamp(value);
        if (model.state() != null) {
            model.state().set(clamped);
        }
        node.floatValue(clamped);
    }
}
