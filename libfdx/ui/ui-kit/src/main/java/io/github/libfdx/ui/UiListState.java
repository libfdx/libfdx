package io.github.libfdx.ui;

/**
 * Represents an ui list state.
 *
 * @author xpenatan
 */
public final class UiListState {
    private int firstVisibleIndex;
    private float firstVisibleOffset;

    /**
     * Returns the first visible index.
     *
     * @return the first visible index
     */
    public int firstVisibleIndex() {
        return firstVisibleIndex;
    }

    /**
     * Returns the first visible offset.
     *
     * @return the first visible offset
     */
    public float firstVisibleOffset() {
        return firstVisibleOffset;
    }

    /**
     * Runs the scroll to item step.
     *
     * @param index the index
     * @param offset the offset
     */
    public void scrollToItem(int index, float offset) {
        this.firstVisibleIndex = Math.max(0, index);
        this.firstVisibleOffset = Math.max(0.0f, offset);
    }
}
