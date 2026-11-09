package io.github.libfdx.tests.utils.uikittest.validation;

import io.github.libfdx.display.Display;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiNodeType;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.validation.scenario.ScenarioInputDriver;

/** Dispatches scenario input and locates rendered targets. */
public final class UiKitTestInput implements ScenarioInputDriver {
    private static final float VALIDATION_SETTLE_SECONDS = 0.25f;

    private final DefaultInput input;
    private final Display display;
    private final UiRoot root;
    private final UiIntState activeSection;

    UiKitTestInput(UiRoot root, DefaultInput input, Display display, UiIntState activeSection) {
        this.root = root;
        this.input = input;
        this.display = display;
        this.activeSection = activeSection;
    }

    /**
     * Runs the key down step.
     *
     * @param key the key
     */
    @Override
    public void keyDown(Key key) {
        input.dispatchKeyDown(key);
    }

    /**
     * Runs the key up step.
     *
     * @param key the key
     */
    @Override
    public void keyUp(Key key) {
        input.dispatchKeyUp(key);
    }

    /**
     * Runs the pointer move step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    @Override
    public void pointerMove(float x, float y) {
        input.dispatchPointerMoved(Math.round(x), Math.round(y));
    }

    /**
     * Runs the pointer down step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    @Override
    public void pointerDown(float x, float y) {
        input.dispatchPointerDown(MouseButton.LEFT, Math.round(x), Math.round(y));
    }

    /**
     * Runs the pointer up step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    @Override
    public void pointerUp(float x, float y) {
        input.dispatchPointerUp(MouseButton.LEFT, Math.round(x), Math.round(y));
    }

    /**
     * Runs the text step.
     *
     * @param text the text
     */
    @Override
    public void text(String text) {
        input.dispatchTextInput(text);
    }

    /**
     * Runs the scroll step.
     *
     * @param amountX the amount x
     * @param amountY the amount y
     */
    @Override
    public void scroll(float amountX, float amountY) {
        int x = display != null ? Math.max(0, display.width() / 2) : 0;
        int y = display != null ? Math.max(0, display.height() / 2) : 0;
        input.dispatchScrolled(x, y, amountX, amountY);
    }

    void settleValidationLayout() {
        if (root != null) {
            root.update(VALIDATION_SETTLE_SECONDS);
            root.rootNode();
        }
    }

    void showValidationSection(int section) {
        activeSection.set(section);
        settleValidationLayout();
    }

    void click(UiNode node) {
        pointerDown(node);
        pointerUp(node);
    }

    void pointerDown(UiNode node) {
        if (node == null) {
            return;
        }
        UiRect bounds = node.bounds();
        int x = root.displayX(bounds.x() + bounds.width() * 0.5f);
        int y = root.displayY(bounds.y() + bounds.height() * 0.5f);
        input.dispatchPointerMoved(x, y);
        input.dispatchPointerDown(MouseButton.LEFT, x, y);
    }

    void pointerUp(UiNode node) {
        if (node == null) {
            return;
        }
        UiRect bounds = node.bounds();
        int x = root.displayX(bounds.x() + bounds.width() * 0.5f);
        int y = root.displayY(bounds.y() + bounds.height() * 0.5f);
        input.dispatchPointerUp(MouseButton.LEFT, x, y);
    }

    void pressKey(Key key) {
        input.dispatchKeyDown(key);
        input.dispatchKeyUp(key);
        settleValidationLayout();
    }

    void pressShortcut(Key key) {
        input.dispatchKeyDown(Key.CONTROL_LEFT);
        input.dispatchKeyDown(key);
        input.dispatchKeyUp(key);
        input.dispatchKeyUp(Key.CONTROL_LEFT);
        settleValidationLayout();
    }

    static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    public static UiNode find(UiNode node, UiNodeType type, String text) {
        if (node == null || !node.visible()) {
            return null;
        }
        if (node.type() == type && (text == null || text.equals(nodeLabel(node)))) {
            return node;
        }
        for (int i = 0; i < node.children().size(); i++) {
            UiNode child = node.children().get(i);
            UiNode found = find(child, type, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String nodeLabel(UiNode node) {
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

    private static boolean hasVisibleText(String text) {
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

}
