package io.github.libfdx.ui;

/**
 * Represents an ui list state.
 *
 * @author xpenatan
 */
public final class UiListState extends UiObservableState {
    private int firstVisibleIndex;
    private float firstVisibleOffset;
    private long requestRevision;

    /**
     * Returns the first visible index.
     *
     * @return the first visible index
     */
    public int firstVisibleIndex() {
        observeRead();
        return firstVisibleIndex;
    }

    /**
     * Returns the first visible offset.
     *
     * @return the first visible offset
     */
    public float firstVisibleOffset() {
        observeRead();
        return firstVisibleOffset;
    }

    /**
     * Requests an item and a pixel offset into that item, invalidating roots that
     * observe this state. Layout clamps the request to the available scroll range.
     *
     * @param index the index
     * @param offset the offset
     */
    public void scrollToItem(int index, float offset) {
        int targetIndex = Math.max(0, index);
        float targetOffset = Float.isFinite(offset) ? Math.max(0.0f, offset) : 0.0f;
        if (firstVisibleIndex == targetIndex && firstVisibleOffset == targetOffset) return;
        firstVisibleIndex = targetIndex;
        firstVisibleOffset = targetOffset;
        requestRevision++;
        notifyListeners();
    }

    long requestRevision() { return requestRevision; }

    void position(int index, float offset) {
        if (firstVisibleIndex == index && Math.abs(firstVisibleOffset - offset) < 0.001f) return;
        firstVisibleIndex = index;
        firstVisibleOffset = offset;
        notifyListeners();
    }
}
