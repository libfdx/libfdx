package io.github.libfdx.ui;

/**
 * Defines the contract for ui content implementations.
 *
 * @author xpenatan
 */
public interface UiContent {
    /**
     * Runs the build step.
     *
     * @param ui the UI
     */
    void build(UiScope ui);
}
