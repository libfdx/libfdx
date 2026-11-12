package io.github.libfdx.ui;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;

/** An immutable tree item. Items with children are expandable. */
public final class UiTreeItem {
    private final String key;
    private final String label;
    private final ArrayView<UiTreeItem> children;

    /**
     * Creates a branch or leaf.
     *
     * @param key stable, nonempty key, unique within a tree
     * @param label visible label
     * @param children child items in display order
     */
    public UiTreeItem(String key, String label, UiTreeItem... children) {
        if (key == null || key.isEmpty() || label == null || children == null) {
            throw new IllegalArgumentException("Tree items require a key, label, and children array");
        }
        for (UiTreeItem child : children) {
            if (child == null) throw new IllegalArgumentException("Tree children must not be null");
        }
        this.key = key;
        this.label = label;
        this.children = Array.of(children).view();
    }

    /** Returns the stable key used by tree state and selection callbacks. */
    public String key() {
        return key;
    }

    /** Returns the visible label. */
    public String label() {
        return label;
    }

    /** Returns the read-only child items in display order. */
    public ArrayView<UiTreeItem> children() {
        return children;
    }

}
