package io.github.libfdx.validation.scenario;

import io.github.libfdx.input.Key;

/**
 * Defines the contract for scenario input driver implementations.
 *
 * @author xpenatan
 */
public interface ScenarioInputDriver {
    /**
     * Runs the key down step.
     *
     * @param key the key
     */
    default void keyDown(Key key) {
    }

    /**
     * Runs the key up step.
     *
     * @param key the key
     */
    default void keyUp(Key key) {
    }

    /**
     * Runs the pointer move step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    default void pointerMove(float x, float y) {
    }

    /**
     * Runs the pointer down step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    default void pointerDown(float x, float y) {
    }

    /**
     * Runs the pointer up step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    default void pointerUp(float x, float y) {
    }

    /**
     * Runs the text step.
     *
     * @param text the text
     */
    default void text(String text) {
    }

    /**
     * Runs the scroll step.
     *
     * @param amountX the amount x
     * @param amountY the amount y
     */
    default void scroll(float amountX, float amountY) {
    }
}
