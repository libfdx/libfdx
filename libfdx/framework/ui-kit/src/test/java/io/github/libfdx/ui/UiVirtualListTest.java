package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.github.libfdx.collections.Array;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import org.junit.jupiter.api.Test;

final class UiVirtualListTest {
    @Test
    void siblingListsHaveIndependentIdentityAndImplicitState() {
        UiRoot root = root();
        UiNode[] lists = new UiNode[2];
        root.setContent(ui -> ui.column(column -> {
            lists[0] = column.virtualList(items(20).view(), null, 3, item -> item,
                    (row, item) -> row.panel(Ui.modifier().height(30), null));
            lists[1] = column.virtualList(items(20).view(), null, 3, item -> item,
                    (row, item) -> row.panel(Ui.modifier().height(30), null));
        }));
        try {
            root.update(0);
            assertNotSame(lists[0], lists[1]);
            assertNotSame(lists[0].listState(), lists[1].listState());
            lists[0].listState().scrollToItem(5, 7);
            root.update(0);
            assertEquals("row-5", lists[0].children().get(0).value());
            assertEquals("row-0", lists[1].children().get(0).value());
        } finally { root.dispose(); }
    }

    @Test
    void scrollRequestsApplyOffsetsWithoutManualCompositionAndClampAtTheEnd() {
        UiRoot root = root();
        UiListState state = new UiListState();
        UiNode[] list = new UiNode[1];
        root.setContent(ui -> list[0] = ui.virtualList(items(100).view(), state, 4, item -> item,
                (row, item) -> row.panel(Ui.modifier().height(30), null)));
        try {
            root.update(0);
            assertTrue(list[0].scrollState().canScrollY());
            state.scrollToItem(20, 7);
            root.update(0);
            assertEquals(20, state.firstVisibleIndex());
            assertEquals(7, state.firstVisibleOffset(), 0.001f);
            assertEquals("row-20", list[0].children().get(0).value());
            assertEquals(list[0].bounds().y() - 7, list[0].children().get(0).bounds().y(), 0.001f);
            assertTrue(list[0].children().size() <= 6, "Compose a bounded window, not all 100 items");
            state.scrollToItem(Integer.MAX_VALUE, Float.POSITIVE_INFINITY);
            root.update(0);
            assertEquals(list[0].scrollState().maxY(), list[0].scrollState().y(), 0.001f);
            UiNode last = list[0].children().get(list[0].children().size() - 1);
            assertEquals("row-99", last.value());
            assertEquals(list[0].bounds().bottom(), last.bounds().bottom(), 0.001f);
        } finally { root.dispose(); }
    }

    @Test
    void wheelAndScrollbarDraggingSynchronizeTheVisibleWindow() {
        UiRoot root = root();
        UiListState state = new UiListState();
        UiNode[] list = new UiNode[1];
        root.setContent(ui -> list[0] = ui.virtualList(items(100).view(), state, 4, item -> item,
                (row, item) -> row.panel(Ui.modifier().height(30), null)));
        try {
            root.update(0);
            root.handleScrolled(PointerEvent.scroll(0, 10, 10, 0, 2));
            root.update(0);
            assertEquals(1, state.firstVisibleIndex());
            assertEquals(18, state.firstVisibleOffset(), 0.001f);
            assertEquals("row-1", list[0].children().get(0).value());
            int x = (int)list[0].bounds().right() - 3;
            root.handlePointerDown(PointerEvent.button(1, MouseButton.LEFT, x, 5));
            root.handlePointerMoved(PointerEvent.pointer(2, x, 110));
            root.handlePointerUp(PointerEvent.button(3, MouseButton.LEFT, x, 110));
            root.update(0);
            assertTrue(state.firstVisibleIndex() > 70);
            assertEquals("row-" + state.firstVisibleIndex(), list[0].children().get(0).value());
        } finally { root.dispose(); }
    }

    @Test
    void variableHeightRowsShrinkEmptyAndRetainKeysInsideTheWindow() {
        UiRoot root = root();
        UiListState state = new UiListState();
        Array<String> values = items(50);
        UiNode[] list = new UiNode[1];
        root.setContent(ui -> list[0] = ui.virtualList(values.view(), state, 3, item -> item,
                (row, item) -> row.panel(Ui.modifier().height(item.equals("row-0") ? 60 : 30), null)));
        try {
            root.update(0);
            UiNode retained = list[0].children().get(1);
            state.scrollToItem(1, 10);
            root.update(0);
            assertSame(retained, list[0].children().get(0));
            assertEquals(10, list[0].bounds().y() - retained.bounds().y(), 0.001f);
            values.clear();
            root.requestCompose();
            root.update(0);
            assertTrue(list[0].children().isEmpty());
            assertEquals(0, state.firstVisibleIndex());
            assertEquals(0, state.firstVisibleOffset());
            assertEquals(0, list[0].scrollState().maxY());
            assertTrue(retained.isDisposed());
        } finally { root.dispose(); }
    }

    private static Array<String> items(int count) {
        Array<String> items = new Array<>();
        for (int i = 0; i < count; i++) items.add("row-" + i);
        return items;
    }

    private static UiRoot root() {
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true);
        root.resize(320, 240);
        return root;
    }
}
