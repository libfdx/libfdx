package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.OrderedMap;
import org.junit.jupiter.api.Test;

final class UiRetainedStateLifetimeTest {
    @Test
    void replacingKeyedEditorsReleasesTheirAutomaticAndScopedState() throws Exception {
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true);
        root.resize(320, 240);
        UiIntState generation = Ui.state(0);
        UiState<String> text = Ui.state("value");
        UiScrollState[] stable = new UiScrollState[1];
        UiScrollState external = new UiScrollState();
        root.setContent(ui -> {
            stable[0] = ui.scrollState("application-scroll");
            ui.items(Array.of("row-" + generation.get()).view(), item -> item, (row, item) -> {
                row.scrollState("named");
                row.listState("named");
                row.textArea(text);
                row.scroll(null);
                row.scroll(Ui.modifier(), external, null);
                row.virtualList(Array.of("item").view(), null, 1, value -> value,
                        (child, value) -> child.panel(Ui.modifier().height(20), null));
            });
        });
        try {
            root.update(0);
            UiScrollState application = stable[0];
            int scrolls = registry(root, "scrollStates").size();
            int lists = registry(root, "listStates").size();
            for (int i = 1; i <= 100; i++) {
                generation.set(i);
                root.update(0);
                assertEquals(scrolls, registry(root, "scrollStates").size());
                assertEquals(lists, registry(root, "listStates").size());
                assertSame(application, stable[0]);
            }
            assertEquals("value", text.get());
            assertEquals(0, external.y());
        } finally { root.dispose(); }
        assertEquals(0, registry(root, "scrollStates").size());
        assertEquals(0, registry(root, "listStates").size());
    }

    private static OrderedMap<?, ?> registry(UiRoot root, String name) throws Exception {
        var field = UiRoot.class.getDeclaredField(name);
        field.setAccessible(true);
        return (OrderedMap<?, ?>)field.get(root);
    }
}
