package io.github.libfdx.ui;

import com.sun.management.ThreadMXBean;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import io.github.libfdx.input.TextInputEvent;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class UiCustomSurfaceTest {
    private static final UiModifier SURFACE_MODIFIER = UiModifier.none()
            .size(100.0f, 60.0f)
            .focusable(true)
            .clip();
    private static final PointerEvent POINTER_DOWN = PointerEvent.button(1L, MouseButton.LEFT, 10, 10);
    private static final PointerEvent POINTER_MOVE = PointerEvent.pointer(2L, 180, 90);
    private static final PointerEvent POINTER_UP = PointerEvent.button(3L, MouseButton.LEFT, 180, 90);
    private static final PointerEvent POINTER_UP_INSIDE = PointerEvent.button(4L, MouseButton.LEFT, 10, 10);

    @Test
    void customSurfaceCapturesPointerAndReceivesFocusedInput() {
        RecordingSurfaceInput input = new RecordingSurfaceInput();
        StableSurfaceContent content = new StableSurfaceContent(input);
        UiRoot root = new UiRoot(null, null, null, null);
        root.resize(200, 100);
        root.setContent(content);
        root.update(0.0f);

        assertTrue(root.handlePointerDown(POINTER_DOWN));
        assertEquals(UiPointerPhase.DOWN, input.lastPhase);
        assertEquals(10.0f, input.lastLocalX, 0.001f);
        assertEquals(10.0f, input.lastLocalY, 0.001f);
        assertTrue(content.node.focused());
        assertEquals(1, input.focusGained);

        assertTrue(root.handlePointerMoved(POINTER_MOVE));
        assertEquals(UiPointerPhase.MOVE, input.lastPhase);
        assertTrue(input.lastCaptured);
        assertFalse(input.lastInside);
        assertEquals(180.0f, input.lastX, 0.001f);

        assertTrue(root.handleKeyDown(new KeyEvent(5L, Key.A, false)));
        assertTrue(root.handleTextInput(new TextInputEvent(6L, "a", false)));
        assertEquals(1, input.keyDownCount);
        assertEquals(1, input.textInputCount);

        assertTrue(root.handlePointerUp(POINTER_UP));
        assertEquals(UiPointerPhase.UP, input.lastPhase);
        assertTrue(input.lastCaptured);
        int pointerCalls = input.pointerCount;
        assertFalse(root.handlePointerMoved(POINTER_MOVE));
        assertEquals(pointerCalls, input.pointerCount);

        assertFalse(root.handlePointerDown(PointerEvent.button(7L, MouseButton.LEFT, 180, 90)));
        assertFalse(content.node.focused());
        assertEquals(1, input.focusLost);
        root.dispose();
    }

    @Test
    void removingCapturedSurfaceDeliversCancelAndFocusLoss() {
        RecordingSurfaceInput input = new RecordingSurfaceInput();
        StableSurfaceContent content = new StableSurfaceContent(input);
        UiRoot root = new UiRoot(null, null, null, null);
        root.resize(200, 100);
        root.setContent(content);
        root.update(0.0f);

        root.handlePointerDown(POINTER_DOWN);
        root.setContent(scope -> { });
        root.update(0.0f);

        assertEquals(UiPointerPhase.CANCEL, input.lastPhase);
        assertEquals(1, input.cancelCount);
        assertEquals(1, input.focusLost);
        root.dispose();
    }

    @Test
    void clippedParentRejectsHitsOutsideItsBounds() {
        RecordingSurfaceInput input = new RecordingSurfaceInput();
        UiNode[] parent = new UiNode[1];
        UiNode[] surface = new UiNode[1];
        UiRoot root = new UiRoot(null, null, null, null);
        root.resize(200, 100);
        root.setContent(scope -> parent[0] = scope.stack(
                UiModifier.none().size(50.0f, 50.0f).clip(),
                stack -> surface[0] = stack.custom("overflow", SURFACE_MODIFIER, context -> context.input(input))));
        root.update(0.0f);

        parent[0].bounds(0.0f, 0.0f, 50.0f, 50.0f);
        surface[0].bounds(60.0f, 5.0f, 30.0f, 30.0f);

        assertNull(root.hitTest(65.0f, 10.0f));
        surface[0].bounds(10.0f, 5.0f, 30.0f, 30.0f);
        assertSame(surface[0], root.hitTest(15.0f, 10.0f));
        root.dispose();
    }

    @Test
    void retainedPathSupportsLinesAndBezierCurvesWithoutSteadyStateAllocation() {
        UiPath path = new UiPath(8, 24);
        for (int i = 0; i < 10_000; i++) {
            rebuildPath(path);
        }
        assertEquals(5, path.commandCount());
        assertEquals(UiPath.MOVE_TO, path.command(0));
        assertEquals(UiPath.CLOSE, path.command(4));

        ThreadMXBean bean = allocationBean();
        long threadId = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(threadId);
        int checksum = 0;
        for (int i = 0; i < 2_000; i++) {
            rebuildPath(path);
            checksum += path.commandCount();
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;

        assertEquals(10_000, checksum);
        assertTrue(allocated <= 512L,
                "Expected retained UI path rebuilds to allocate no post-warm-up objects, allocated "
                        + allocated + " bytes");
    }

    @Test
    void warmedSurfaceInputRoutingAllocatesNoFrameworkObjects() {
        RecordingSurfaceInput input = new RecordingSurfaceInput();
        StableSurfaceContent content = new StableSurfaceContent(input);
        UiRoot root = new UiRoot(null, null, null, null);
        root.resize(200, 100);
        root.setContent(content);
        root.update(0.0f);
        for (int i = 0; i < 10_000; i++) {
            root.handlePointerDown(POINTER_DOWN);
            root.handlePointerUp(POINTER_UP_INSIDE);
        }

        ThreadMXBean bean = allocationBean();
        long threadId = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(threadId);
        int beforeCalls = input.pointerCount;
        for (int i = 0; i < 2_000; i++) {
            root.handlePointerDown(POINTER_DOWN);
            root.handlePointerUp(POINTER_UP_INSIDE);
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;

        assertEquals(4_000, input.pointerCount - beforeCalls);
        assertTrue(allocated <= 512L,
                "Expected custom-surface input routing to allocate no post-warm-up objects, allocated "
                        + allocated + " bytes");
        root.dispose();
    }

    @Test
    void clipModifierReusesNoOpValue() {
        UiModifier modifier = UiModifier.none().clip();

        assertTrue(modifier.clipsToBounds());
        assertSame(modifier, modifier.clip());
        assertSame(UiModifier.none(), UiModifier.none().clip(false));
    }

    private static void rebuildPath(UiPath path) {
        path.clear()
                .moveTo(1.0f, 2.0f)
                .lineTo(3.0f, 4.0f)
                .quadraticTo(5.0f, 6.0f, 7.0f, 8.0f)
                .cubicTo(9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f)
                .close();
    }

    private static ThreadMXBean allocationBean() {
        java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
        assumeTrue(platformBean instanceof ThreadMXBean);
        ThreadMXBean bean = (ThreadMXBean)platformBean;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        return bean;
    }

    private static final class StableSurfaceContent implements UiContent, UiCustomContent {
        private final RecordingSurfaceInput input;
        private UiNode node;

        private StableSurfaceContent(RecordingSurfaceInput input) {
            this.input = input;
        }

        @Override
        public void build(UiScope scope) {
            node = scope.custom("test-surface", SURFACE_MODIFIER, this);
        }

        @Override
        public void build(UiCustomContext context) {
            context.input(input);
        }
    }

    private static final class RecordingSurfaceInput implements UiSurfaceInput {
        private int pointerCount;
        private int cancelCount;
        private int keyDownCount;
        private int textInputCount;
        private int focusGained;
        private int focusLost;
        private UiPointerPhase lastPhase;
        private float lastX;
        private float lastLocalX;
        private float lastLocalY;
        private boolean lastInside;
        private boolean lastCaptured;

        @Override
        public UiPointerResult pointer(UiPointerEvent event) {
            pointerCount++;
            lastPhase = event.phase();
            lastX = event.x();
            lastLocalX = event.localX();
            lastLocalY = event.localY();
            lastInside = event.inside();
            lastCaptured = event.captured();
            if (event.phase() == UiPointerPhase.CANCEL) {
                cancelCount++;
                return UiPointerResult.RELEASE;
            }
            if (event.phase() == UiPointerPhase.DOWN) {
                return UiPointerResult.CAPTURE;
            }
            return UiPointerResult.HANDLED;
        }

        @Override
        public boolean keyDown(KeyEvent event) {
            keyDownCount++;
            return true;
        }

        @Override
        public boolean textInput(TextInputEvent event) {
            textInputCount++;
            return true;
        }

        @Override
        public void focusChanged(boolean focused) {
            if (focused) {
                focusGained++;
            } else {
                focusLost++;
            }
        }
    }
}
