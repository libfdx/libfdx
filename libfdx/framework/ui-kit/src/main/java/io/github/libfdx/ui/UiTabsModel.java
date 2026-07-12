package io.github.libfdx.ui;

/**
 * Represents an ui tabs model.
 *
 * @author xpenatan
 */
final class UiTabsModel {
    private static final String[] EMPTY_LABELS = new String[0];

    private UiIntState activeIndex;
    private String[] labels = EMPTY_LABELS;

    UiTabsModel(UiIntState activeIndex, String[] labels) {
        update(activeIndex, labels);
    }

    void update(UiIntState activeIndex, String[] labels) {
        this.activeIndex = activeIndex;
        int length = labels != null ? labels.length : 0;
        if (this.labels.length != length) {
            this.labels = new String[length];
        }
        for (int i = 0; i < length; i++) {
            this.labels[i] = labels[i] != null ? labels[i] : "";
        }
    }

    UiIntState activeIndexState() {
        return activeIndex;
    }

    int count() {
        return labels.length;
    }

    String label(int index) {
        if (index < 0 || index >= labels.length) {
            return "";
        }
        return labels[index];
    }

    int clamp(int index) {
        if (labels.length == 0) {
            return -1;
        }
        return Math.max(0, Math.min(labels.length - 1, index));
    }

    void select(int index) {
        int selected = clamp(index);
        if (selected >= 0 && activeIndex != null) {
            activeIndex.set(selected);
        }
    }
}
