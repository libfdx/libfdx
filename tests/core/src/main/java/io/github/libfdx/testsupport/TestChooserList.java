package io.github.libfdx.testsupport;

import java.util.Arrays;
import java.util.Locale;

/** Presentation order and interaction state for the chooser, independent of auto-run order. */
final class TestChooserList {
    private static final long DOUBLE_CLICK_NANOS = 450_000_000L;
    private final TestSelector.TestDescriptor[] sorted = TestSelector.descriptors();
    private TestSelector.TestDescriptor[] visible;
    private String query = "";
    private String lastClickedName;
    private long lastClickNanos;

    TestChooserList() {
        Arrays.sort(sorted, (left, right) -> left.displayName().compareToIgnoreCase(right.displayName()));
        visible = sorted;
    }

    boolean filter(String text) {
        String next = text.trim().toLowerCase(Locale.ROOT);
        if (query.equals(next)) {
            return false;
        }
        query = next;
        resetClick();
        TestSelector.TestDescriptor[] matches = new TestSelector.TestDescriptor[sorted.length];
        int count = 0;
        for (TestSelector.TestDescriptor descriptor : sorted) {
            if (descriptor.displayName().toLowerCase(Locale.ROOT).contains(query)
                    || descriptor.name().toLowerCase(Locale.ROOT).contains(query)
                    || descriptor.description().toLowerCase(Locale.ROOT).contains(query)) {
                matches[count++] = descriptor;
            }
        }
        visible = Arrays.copyOf(matches, count);
        return true;
    }

    TestSelector.TestDescriptor[] visible() {
        return visible;
    }

    int totalCount() {
        return sorted.length;
    }

    int visibleIndex(String name) {
        for (int i = 0; i < visible.length; i++) {
            if (visible[i].name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    boolean click(String name, long timeNanos) {
        long elapsed = timeNanos - lastClickNanos;
        if (name.equals(lastClickedName) && elapsed >= 0 && elapsed <= DOUBLE_CLICK_NANOS) {
            resetClick();
            return true;
        }
        lastClickedName = name;
        lastClickNanos = timeNanos;
        return false;
    }

    void resetClick() {
        lastClickedName = null;
    }
}
