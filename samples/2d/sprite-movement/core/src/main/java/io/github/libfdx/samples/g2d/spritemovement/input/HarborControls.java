package io.github.libfdx.samples.g2d.spritemovement.input;

import io.github.libfdx.input.*;

/** Samples one direction per frame. Drag distance and stick magnitude control speed. */
public final class HarborControls extends InputAdapter implements MovementInput {
    private final Input input;
    private boolean sailing, confirm, padHeld, mouseHeld;
    private int finger = -1, anchorX, anchorY;
    private float dragX, dragY, x, y;

    public HarborControls(Input input) {
        this.input = input;
    }

    public void sailing(boolean active) {
        sailing = active;
        finger = -1;
        mouseHeld = false;
        dragX = dragY = 0;
        confirm = false;
    }

    public void poll() {
        x = axis(Key.RIGHT, Key.D) - axis(Key.LEFT, Key.A);
        y = axis(Key.UP, Key.W) - axis(Key.DOWN, Key.S);
        boolean button = false;
        if (input.gamepads() != null) {
            var pads = input.gamepads().connected();
            if (!pads.isEmpty()) {
                Gamepad pad = pads.get(0);
                float sx = pad.axis(GamepadAxis.LEFT_X), sy = -pad.axis(GamepadAxis.LEFT_Y);
                if (sx * sx + sy * sy > .16f * .16f) { x += sx; y += sy; }
                if (pad.pressed(GamepadButton.DPAD_LEFT)) x--;
                if (pad.pressed(GamepadButton.DPAD_RIGHT)) x++;
                if (pad.pressed(GamepadButton.DPAD_DOWN)) y--;
                if (pad.pressed(GamepadButton.DPAD_UP)) y++;
                button = pad.pressed(GamepadButton.SOUTH) || pad.pressed(GamepadButton.START);
            }
        }
        if (!sailing && button && !padHeld) confirm = true;
        padHeld = button;
        if (finger >= 0 || mouseHeld) { x = dragX; y = dragY; }
    }

    private int axis(Key first, Key second) {
        return input.isKeyPressed(first) || input.isKeyPressed(second) ? 1 : 0;
    }

    public boolean takeConfirm() {
        boolean pressed = confirm;
        confirm = false;
        return pressed;
    }

    @Override
    public boolean keyDown(KeyEvent event) {
        if (!sailing && !event.repeat() && (event.key() == Key.ENTER || event.key() == Key.SPACE)) {
            confirm = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(TouchEvent event) {
        if (!sailing || finger >= 0) return false;
        finger = event.point().id();
        anchor(event.point().x(), event.point().y());
        return true;
    }

    @Override
    public boolean touchMoved(TouchEvent event) {
        if (finger != event.point().id()) return false;
        drag(event.point().x(), event.point().y());
        return true;
    }

    @Override
    public boolean touchUp(TouchEvent event) {
        if (finger != event.point().id()) return false;
        finger = -1;
        dragX = dragY = 0;
        return true;
    }

    @Override
    public boolean pointerDown(PointerEvent event) {
        if (!sailing || event.type() != PointerType.MOUSE || event.button() != MouseButton.LEFT) return false;
        mouseHeld = true;
        anchor(event.x(), event.y());
        return true;
    }

    @Override
    public boolean pointerMoved(PointerEvent event) {
        if (!mouseHeld) return false;
        drag(event.x(), event.y());
        return true;
    }

    @Override
    public boolean pointerUp(PointerEvent event) {
        if (!mouseHeld || event.button() != MouseButton.LEFT) return false;
        mouseHeld = false;
        dragX = dragY = 0;
        return true;
    }

    private void anchor(int px, int py) {
        anchorX = px;
        anchorY = py;
        dragX = dragY = 0;
    }

    private void drag(int px, int py) {
        dragX = (px - anchorX) / 52f;
        dragY = (anchorY - py) / 52f;
    }

    @Override
    public float horizontal() {
        return x;
    }

    @Override
    public float vertical() {
        return y;
    }

}
