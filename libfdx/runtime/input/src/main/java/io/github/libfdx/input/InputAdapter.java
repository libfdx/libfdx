package io.github.libfdx.input;

public class InputAdapter implements InputProcessor {
    @Override
    public boolean keyDown(KeyEvent event) {
        return false;
    }

    @Override
    public boolean keyUp(KeyEvent event) {
        return false;
    }

    @Override
    public boolean pointerDown(PointerEvent event) {
        return false;
    }

    @Override
    public boolean pointerUp(PointerEvent event) {
        return false;
    }

    @Override
    public boolean pointerMoved(PointerEvent event) {
        return false;
    }

    @Override
    public boolean scrolled(PointerEvent event) {
        return false;
    }

    @Override
    public boolean touchDown(TouchEvent event) {
        return false;
    }

    @Override
    public boolean touchUp(TouchEvent event) {
        return false;
    }

    @Override
    public boolean touchMoved(TouchEvent event) {
        return false;
    }

    @Override
    public boolean textInput(TextInputEvent event) {
        return false;
    }
}
