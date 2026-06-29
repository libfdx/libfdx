package io.github.libfdx.ui;

/**
 * Receives callbacks for ui state events.
 *
 * @author xpenatan
 */
interface UiStateListener {
    /**
     * Runs the state changed step.
     *
     * @param state the state
     */
    void stateChanged(UiObservableState state);
}
