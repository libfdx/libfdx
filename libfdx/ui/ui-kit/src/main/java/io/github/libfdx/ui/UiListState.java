package io.github.libfdx.ui;

public final class UiListState {
    private int firstVisibleIndex;
    private float firstVisibleOffset;

    public int firstVisibleIndex() {
        return firstVisibleIndex;
    }

    public float firstVisibleOffset() {
        return firstVisibleOffset;
    }

    public void scrollToItem(int index, float offset) {
        this.firstVisibleIndex = Math.max(0, index);
        this.firstVisibleOffset = Math.max(0.0f, offset);
    }
}
