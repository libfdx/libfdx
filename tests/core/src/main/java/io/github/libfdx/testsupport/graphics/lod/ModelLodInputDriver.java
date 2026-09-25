package io.github.libfdx.testsupport.graphics.lod;

import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.Key;
import io.github.libfdx.validation.scenario.ScenarioInputDriver;

/** Sends automatic clicks and slider drags through the UI's normal input pipeline. */
public final class ModelLodInputDriver implements ScenarioInputDriver {
    private final DefaultInput input;
    public ModelLodInputDriver(DefaultInput input) { this.input = input; }
    @Override public void pointerMove(float x, float y) { input.dispatchPointerMoved(Math.round(x),Math.round(y)); }
    @Override public void pointerDown(float x, float y) { input.dispatchPointerDown(MouseButton.LEFT,Math.round(x),Math.round(y)); }
    @Override public void pointerUp(float x, float y) { input.dispatchPointerUp(MouseButton.LEFT,Math.round(x),Math.round(y)); }
    @Override public void keyDown(Key key) { input.dispatchKeyDown(key); }
    @Override public void keyUp(Key key) { input.dispatchKeyUp(key); }
    @Override public void text(String text) { input.dispatchTextInput(text); }
}
