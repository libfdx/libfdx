package io.github.libfdx.input;

public interface InputProcessor {
    boolean keyDown(KeyEvent event);

    boolean keyUp(KeyEvent event);

    boolean pointerDown(PointerEvent event);

    boolean pointerUp(PointerEvent event);

    boolean pointerMoved(PointerEvent event);

    boolean scrolled(PointerEvent event);

    boolean touchDown(TouchEvent event);

    boolean touchUp(TouchEvent event);

    boolean touchMoved(TouchEvent event);

    boolean textInput(TextInputEvent event);
}
