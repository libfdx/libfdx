package io.github.libfdx.ui;

final class UiTabsModel {
    private final UiIntState activeIndex;
    private final String[] labels;

    UiTabsModel(UiIntState activeIndex, String[] labels) {
        this.activeIndex = activeIndex;
        if (labels == null) {
            this.labels = new String[0];
        } else {
            this.labels = new String[labels.length];
            for (int i = 0; i < labels.length; i++) {
                this.labels[i] = labels[i] != null ? labels[i] : "";
            }
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
