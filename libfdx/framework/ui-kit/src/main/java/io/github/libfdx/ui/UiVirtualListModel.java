package io.github.libfdx.ui;

import java.util.Arrays;

/** Retained measurement and pixel-scroll bookkeeping for one virtual list. */
final class UiVirtualListModel {
    final UiScrollState scroll = new UiScrollState();
    private UiListState state;
    private float[] heights = new float[0];
    private float estimatedHeight = 40;
    private long appliedRequest = Long.MIN_VALUE;
    private boolean pendingRequest;
    int itemCount;
    int visibleCount;
    int firstComposed;
    private float firstOffset;

    void configure(UiListState state, int itemCount, int visibleCount) {
        if (this.state != state) {
            this.state = state;
            appliedRequest = Long.MIN_VALUE;
            Arrays.fill(heights, 0);
        }
        if (heights.length < itemCount) heights = Arrays.copyOf(heights, Math.max(itemCount, heights.length * 2));
        if (this.itemCount != itemCount) {
            Arrays.fill(heights, Math.min(this.itemCount, itemCount), Math.max(this.itemCount, itemCount), 0);
        }
        this.itemCount = itemCount;
        this.visibleCount = Math.max(0, visibleCount);
        pendingRequest = appliedRequest != state.requestRevision();
        firstComposed = pendingRequest
                ? Math.min(state.firstVisibleIndex(), Math.max(0, itemCount - 1)) : indexAt(scroll.y());
        firstOffset = pendingRequest ? state.firstVisibleOffset() : scroll.y() - top(firstComposed);
        state.firstVisibleOffset(); // Observe both parts of the public scroll position.
    }

    int endComposed() {
        if (visibleCount == 0) return firstComposed;
        int end = firstComposed;
        float extent = -firstOffset;
        float target = Math.max(preferredHeight(), scroll.viewportHeight());
        while (end < itemCount && ((long)end - firstComposed < (long)visibleCount + 1 || extent < target)) {
            extent += height(end++);
        }
        return end;
    }

    float height(int index) { return heights[index] > 0 ? heights[index] : estimatedHeight; }

    void measuredHeight(int index, float height) {
        heights[index] = Math.max(1, height);
    }

    boolean updateEstimate(int composedCount) {
        float total = 0;
        int count = 0;
        for (int i = firstComposed; i < firstComposed + composedCount; i++) {
            if (heights[i] > 0) { total += heights[i]; count++; }
        }
        float previous = estimatedHeight;
        if (count > 0) estimatedHeight = total / count;
        return Math.abs(previous - estimatedHeight) > 0.001f;
    }

    float preferredHeight() { return visibleCount * estimatedHeight; }

    float top(int index) {
        float top = 0;
        for (int i = 0; i < Math.min(index, itemCount); i++) top += height(i);
        return top;
    }

    private int indexAt(float y) {
        int index = 0;
        while (index < itemCount - 1 && y >= height(index)) y -= height(index++);
        return index;
    }

    boolean updateMetrics(float width, float height) {
        float requestedY = top(firstComposed) + firstOffset;
        scroll.updateMetrics(width, height, width, top(itemCount));
        scroll.scrollTo(0, requestedY);
        appliedRequest = state.requestRevision();
        pendingRequest = false;
        int first = indexAt(scroll.y());
        state.position(first, scroll.y() - top(first));
        return visibleCount > 0 && first != firstComposed;
    }
}
