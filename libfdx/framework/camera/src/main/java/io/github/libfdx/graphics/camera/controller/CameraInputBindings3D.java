package io.github.libfdx.graphics.camera.controller;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;

/**
 * Configures common 3D camera input bindings.
 *
 * @author xpenatan
 */
public final class CameraInputBindings3D {
    private Key forwardKey = Key.W;
    private Key backwardKey = Key.S;
    private Key leftKey = Key.A;
    private Key rightKey = Key.D;
    private Key upKey = Key.E;
    private Key downKey = Key.Q;
    private Key alternateForwardKey = Key.UP;
    private Key alternateBackwardKey = Key.DOWN;
    private Key alternateLeftKey = Key.LEFT;
    private Key alternateRightKey = Key.RIGHT;
    private Key alternateUpKey = Key.PAGE_UP;
    private Key alternateDownKey = Key.PAGE_DOWN;
    private Key fastKey = Key.SHIFT_LEFT;
    private Key alternateFastKey = Key.SHIFT_RIGHT;
    private Key boostKey = Key.CONTROL_LEFT;
    private Key alternateBoostKey = Key.CONTROL_RIGHT;
    private MouseButton lookButton = MouseButton.RIGHT;
    private MouseButton touchLookButton = MouseButton.LEFT;

    /**
     * Creates default 3D camera bindings.
     *
     * @return the bindings
     */
    public static CameraInputBindings3D defaults() {
        return new CameraInputBindings3D();
    }

    public Key forwardKey() {
        return forwardKey;
    }

    public CameraInputBindings3D forwardKey(Key key) {
        forwardKey = checkedKey(key, "forward");
        return this;
    }

    public Key backwardKey() {
        return backwardKey;
    }

    public CameraInputBindings3D backwardKey(Key key) {
        backwardKey = checkedKey(key, "backward");
        return this;
    }

    public Key leftKey() {
        return leftKey;
    }

    public CameraInputBindings3D leftKey(Key key) {
        leftKey = checkedKey(key, "left");
        return this;
    }

    public Key rightKey() {
        return rightKey;
    }

    public CameraInputBindings3D rightKey(Key key) {
        rightKey = checkedKey(key, "right");
        return this;
    }

    public Key upKey() {
        return upKey;
    }

    public CameraInputBindings3D upKey(Key key) {
        upKey = checkedKey(key, "up");
        return this;
    }

    public Key downKey() {
        return downKey;
    }

    public CameraInputBindings3D downKey(Key key) {
        downKey = checkedKey(key, "down");
        return this;
    }

    public Key alternateForwardKey() {
        return alternateForwardKey;
    }

    public CameraInputBindings3D alternateForwardKey(Key key) {
        alternateForwardKey = checkedKey(key, "alternate forward");
        return this;
    }

    public Key alternateBackwardKey() {
        return alternateBackwardKey;
    }

    public CameraInputBindings3D alternateBackwardKey(Key key) {
        alternateBackwardKey = checkedKey(key, "alternate backward");
        return this;
    }

    public Key alternateLeftKey() {
        return alternateLeftKey;
    }

    public CameraInputBindings3D alternateLeftKey(Key key) {
        alternateLeftKey = checkedKey(key, "alternate left");
        return this;
    }

    public Key alternateRightKey() {
        return alternateRightKey;
    }

    public CameraInputBindings3D alternateRightKey(Key key) {
        alternateRightKey = checkedKey(key, "alternate right");
        return this;
    }

    public Key alternateUpKey() {
        return alternateUpKey;
    }

    public CameraInputBindings3D alternateUpKey(Key key) {
        alternateUpKey = checkedKey(key, "alternate up");
        return this;
    }

    public Key alternateDownKey() {
        return alternateDownKey;
    }

    public CameraInputBindings3D alternateDownKey(Key key) {
        alternateDownKey = checkedKey(key, "alternate down");
        return this;
    }

    public Key fastKey() {
        return fastKey;
    }

    public CameraInputBindings3D fastKey(Key key) {
        fastKey = checkedKey(key, "fast");
        return this;
    }

    public Key alternateFastKey() {
        return alternateFastKey;
    }

    public CameraInputBindings3D alternateFastKey(Key key) {
        alternateFastKey = checkedKey(key, "alternate fast");
        return this;
    }

    public Key boostKey() {
        return boostKey;
    }

    public CameraInputBindings3D boostKey(Key key) {
        boostKey = checkedKey(key, "boost");
        return this;
    }

    public Key alternateBoostKey() {
        return alternateBoostKey;
    }

    public CameraInputBindings3D alternateBoostKey(Key key) {
        alternateBoostKey = checkedKey(key, "alternate boost");
        return this;
    }

    public MouseButton lookButton() {
        return lookButton;
    }

    public CameraInputBindings3D lookButton(MouseButton button) {
        lookButton = checkedButton(button, "look");
        return this;
    }

    public MouseButton touchLookButton() {
        return touchLookButton;
    }

    public CameraInputBindings3D touchLookButton(MouseButton button) {
        touchLookButton = checkedButton(button, "touch look");
        return this;
    }

    private static Key checkedKey(Key key, String name) {
        if (key == null) {
            throw new FdxException("Camera input " + name + " key cannot be null");
        }
        return key;
    }

    private static MouseButton checkedButton(MouseButton button, String name) {
        if (button == null) {
            throw new FdxException("Camera input " + name + " button cannot be null");
        }
        return button;
    }
}
