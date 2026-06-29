package io.github.libfdx.ui;

/**
 * Defines the contract for ui item content implementations.
 *
 * @param <T> the value type
 *
 * @author xpenatan
 */
public interface UiItemContent<T> {
    /**
     * Runs the build step.
     *
     * @param ui the UI
     * @param item the item
     */
    void build(UiScope ui, T item);
}
