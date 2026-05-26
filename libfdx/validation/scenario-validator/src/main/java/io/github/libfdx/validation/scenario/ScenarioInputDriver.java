package io.github.libfdx.validation.scenario;

import io.github.libfdx.input.Key;

public interface ScenarioInputDriver {
    default void keyDown(Key key) {
    }

    default void keyUp(Key key) {
    }

    default void pointerMove(float x, float y) {
    }

    default void pointerDown(float x, float y) {
    }

    default void pointerUp(float x, float y) {
    }

    default void text(String text) {
    }

    default void scroll(float amountX, float amountY) {
    }
}
