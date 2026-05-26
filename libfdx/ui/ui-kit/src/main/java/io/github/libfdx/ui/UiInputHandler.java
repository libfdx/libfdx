package io.github.libfdx.ui;

import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import io.github.libfdx.input.PointerType;
import io.github.libfdx.input.TextInputEvent;
import io.github.libfdx.input.TouchEvent;
import io.github.libfdx.input.TouchPoint;

final class UiInputHandler extends InputAdapter {
    private final UiRoot root;

    UiInputHandler(UiRoot root) {
        this.root = root;
    }

    @Override
    public boolean keyDown(KeyEvent event) {
        return root.handleKeyDown(event);
    }

    @Override
    public boolean pointerDown(PointerEvent event) {
        return root.handlePointerDown(event);
    }

    @Override
    public boolean pointerUp(PointerEvent event) {
        return root.handlePointerUp(event);
    }

    @Override
    public boolean pointerMoved(PointerEvent event) {
        return root.handlePointerMoved(event);
    }

    @Override
    public boolean scrolled(PointerEvent event) {
        return root.handleScrolled(event);
    }

    @Override
    public boolean touchDown(TouchEvent event) {
        return root.handlePointerDown(touchPointer(event, MouseButton.LEFT));
    }

    @Override
    public boolean touchUp(TouchEvent event) {
        return root.handlePointerUp(touchPointer(event, MouseButton.LEFT));
    }

    @Override
    public boolean touchMoved(TouchEvent event) {
        return root.handlePointerMoved(touchPointer(event, MouseButton.UNKNOWN));
    }

    @Override
    public boolean textInput(TextInputEvent event) {
        return root.handleTextInput(event);
    }

    private static PointerEvent touchPointer(TouchEvent event, MouseButton button) {
        TouchPoint point = event.point();
        return new PointerEvent(event.timeNanos(), point.id(), PointerType.TOUCH, button, point.x(), point.y(), 0.0f,
                0.0f);
    }
}
