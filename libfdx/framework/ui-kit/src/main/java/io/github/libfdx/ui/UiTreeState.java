package io.github.libfdx.ui;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.collections.ObjectMap;

/** Application-owned selection, expansion, and scroll position for one tree. Use on the UI thread. */
public final class UiTreeState extends UiObservableState {
    static final class Row {
        final UiTreeItem item;
        final String compositionKey;
        final Row parent;
        final int index;
        final int depth;
        int end;
        boolean expanded;

        Row(UiTreeItem item, Row parent, int index) {
            this.item = item;
            this.compositionKey = item.key().length() + ":" + item.key();
            this.parent = parent;
            this.index = index;
            this.depth = parent == null ? 0 : parent.depth + 1;
            expanded = parent == null && branch();
        }

        boolean branch() {
            return item.children().size() > 0;
        }

        boolean contains(Row other) {
            return other != null && other.index > index && other.index < end;
        }

    }

    private final Array<Row> rows = new Array<>();
    private final ObjectMap<String, Row> byKey = new ObjectMap<>();
    private final Array<Row> visible = new Array<>();
    private final UiScrollState scroll = new UiScrollState();
    private Row selected;
    private boolean visibilityDirty = true;
    private boolean revealPending;

    /**
     * Creates state for a fixed set of items. Root branches start expanded.
     *
     * @param roots items in display order with globally unique keys
     */
    public UiTreeState(UiTreeItem... roots) {
        if (roots == null) throw new IllegalArgumentException("Tree roots array required");
        for (UiTreeItem root : roots) append(root, null);
    }

    /** Returns the selected item's key, or null when selection is empty. */
    public String selectedKey() {
        observeRead();
        return selected == null ? null : selected.item.key();
    }

    /**
     * Changes the selected item, opening its ancestors and revealing it on the next layout.
     * Selecting the current key is a no-op. This method does not invoke a selection callback.
     *
     * @param key an existing item key, or null to clear selection
     */
    public void select(String key) {
        Row next = key == null ? null : requireRow(key);
        if (selected == next) return;
        selected = next;
        revealPending = next != null;
        if (next != null) {
            for (Row row = next.parent; row != null; row = row.parent) {
                if (!row.expanded) {
                    row.expanded = true;
                    visibilityDirty = true;
                }
            }
        }
        notifyListeners();
    }

    /** Returns the expansion state of an existing item. Leaves return false. */
    public boolean isExpanded(String key) {
        observeRead();
        return requireRow(key).expanded;
    }

    /**
     * Sets a branch's expansion state. Closing a branch preserves selection.
     * Calls for leaves have no effect.
     *
     * @param key an existing item key
     * @param expanded whether to show the branch's children
     */
    public void setExpanded(String key, boolean expanded) {
        Row row = requireRow(key);
        if (!row.branch() || row.expanded == expanded) return;
        row.expanded = expanded;
        visibilityDirty = true;
        notifyListeners();
    }

    /**
     * Returns the next visible key in display order, wrapping at either end.
     * A hidden key is treated as its visible collapsed ancestor.
     *
     * @param key the starting key, or null to start at the first or last visible item
     * @param backward whether to traverse in reverse
     * @return the visible key, or null for an empty tree
     */
    public String nextVisibleKey(String key, boolean backward) {
        ArrayView<Row> visibleRows = visibleRows();
        int count = visibleRows.size();
        if (count == 0) return null;
        if (key == null) return visibleRows.get(backward ? count - 1 : 0).item.key();
        Row anchor = requireRow(key);
        for (Row row = anchor.parent; row != null; row = row.parent) {
            if (!row.expanded) anchor = row;
        }
        int position = visible.indexOf(anchor, true);
        int next = backward ? (position + count - 1) % count : (position + 1) % count;
        return visibleRows.get(next).item.key();
    }

    private void append(UiTreeItem item, Row parent) {
        if (item == null || byKey.containsKey(item.key())) {
            throw new IllegalArgumentException(item == null ? "Tree items must not be null"
                    : "Duplicate tree item key: " + item.key());
        }
        Row row = new Row(item, parent, rows.size());
        rows.add(row);
        byKey.put(item.key(), row);
        ArrayView<UiTreeItem> children = item.children();
        for (int index = 0; index < children.size(); index++) append(children.get(index), row);
        row.end = rows.size();
    }

    private Row requireRow(String key) {
        Row row = key == null ? null : byKey.get(key);
        if (row == null) throw new IllegalArgumentException("Unknown tree item key: " + key);
        return row;
    }

    ArrayView<Row> visibleRows() {
        observeRead();
        if (visibilityDirty) {
            visible.clear();
            for (int index = 0; index < rows.size();) {
                Row row = rows.get(index);
                visible.add(row);
                index = row.expanded ? index + 1 : row.end;
            }
            visibilityDirty = false;
        }
        return visible;
    }

    boolean hasNextSibling(Row row) {
        return row.end < rows.size() && rows.get(row.end).parent == row.parent;
    }

    Row selectedRow() {
        return selected;
    }

    UiScrollState scroll() {
        return scroll;
    }

    boolean revealSelection(float rowHeight, float gap) {
        if (!revealPending || visibilityDirty || scroll.viewportHeight() <= 0) return false;
        int position = visible.indexOf(selected, true);
        if (position < 0) {
            revealPending = false;
            return false;
        }
        float top = position * (rowHeight + gap);
        float bottom = top + rowHeight;
        if (bottom <= scroll.contentHeight()) revealPending = false;
        float previous = scroll.y();
        if (top < previous || rowHeight >= scroll.viewportHeight()) {
            scroll.scrollTo(scroll.x(), top);
        } else if (bottom > previous + scroll.viewportHeight()) {
            scroll.scrollTo(scroll.x(), bottom - scroll.viewportHeight());
        }
        return previous != scroll.y();
    }

}
