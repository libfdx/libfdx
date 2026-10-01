package io.github.libfdx.backend.desktopc;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import io.github.libfdx.input.PointerType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

final class DesktopCInputTest {
    @Test
    void drainsInOrderWithFractionalDeltasAndEventCoordinatesUsingOneBuffer() {
        var queue = new TestQueue(new double[][] {
                {5, 0.75, 0.25, 37, 81, -263, 481, 0},
                {5, -0.5, -1.75, 42, 99, -258, 499, 0}
        });
        var scroll = new DesktopCInput(7, queue);
        var input = new DefaultInput();
        var events = new ArrayList<PointerEvent>();
        input.addProcessor(new InputAdapter() {
            @Override public boolean scrolled(PointerEvent event) {
                events.add(event);
                return true;
            }
        });

        scroll.drain(input);
        scroll.drain(input);

        assertEquals(2, events.size());
        assertEquals(0.75f, events.get(0).scrollX());
        assertEquals(-0.25f, events.get(0).scrollY());
        assertEquals(37, events.get(0).x());
        assertEquals(81, events.get(0).y());
        assertEquals(-0.5f, events.get(1).scrollX());
        assertEquals(1.75f, events.get(1).scrollY());
        assertEquals(42, events.get(1).x());
        assertEquals(99, events.get(1).y());
        assertEquals(0, events.get(1).pointerId());
        assertEquals(PointerType.MOUSE, events.get(1).type());
        assertEquals(MouseButton.UNKNOWN, events.get(1).button());
        assertEquals(42, input.pointerX());
        assertEquals(99, input.pointerY());
        assertEquals(-258, input.pointerScreenX());
        assertEquals(499, input.pointerScreenY());
        assertEquals(7, queue.window);
        scroll.dispose();
        scroll.dispose();
        scroll.drain(input);
        assertEquals(1, queue.disposals);
    }

    @Test
    void reportsNativeAllocationFailureAndStillDisposesOnce() {
        var queue = new TestQueue(new double[0][]);
        queue.failed = true;
        var scroll = new DesktopCInput(1, queue);
        assertThrows(FdxException.class, () -> scroll.drain(new DefaultInput()));
        scroll.dispose();
        scroll.dispose();
        assertEquals(1, queue.disposals);
    }

    @Test
    void rejectsFailedInstallation() {
        var queue = new TestQueue(new double[0][]);
        queue.installResult = 0;
        assertThrows(FdxException.class, () -> new DesktopCInput(1, queue));
        assertEquals(0, queue.disposals);
    }

    @Test
    void deliversPressMoveWheelReleaseMoveInOrderWithHeldStateDuringDrag() {
        var queue = new TestQueue(new double[][] {
                {4, 0, 1, 10, 20, -290, 420, 0},
                {3, 0, 0, 15, 28, -285, 428, 0},
                {5, 0.5, 1.25, 15, 28, -285, 428, 0},
                {4, 0, 0, 19, 35, -281, 435, 0},
                {3, 0, 0, 25, 40, -275, 440, 0}
        });
        var input = new DefaultInput();
        var calls = new ArrayList<String>();
        input.addProcessor(new InputAdapter() {
            @Override public boolean pointerDown(PointerEvent event) {
                assertEquals(MouseButton.LEFT, event.button());
                assertTrue(input.isMouseButtonPressed(MouseButton.LEFT));
                calls.add("down:" + event.x());
                return true;
            }
            @Override public boolean pointerMoved(PointerEvent event) {
                calls.add("move:" + event.x() + ":" + input.isMouseButtonPressed(MouseButton.LEFT));
                assertEquals(event.x() - 300, input.pointerScreenX());
                return true;
            }
            @Override public boolean scrolled(PointerEvent event) {
                calls.add("scroll");
                assertEquals(-1.25f, event.scrollY());
                return true;
            }
            @Override public boolean pointerUp(PointerEvent event) {
                assertFalse(input.isMouseButtonPressed(MouseButton.LEFT));
                calls.add("up:" + event.x());
                return true;
            }
        });
        var backendInput = new DesktopCInput(1, queue);
        backendInput.drain(input);
        assertEquals(java.util.List.of("down:10", "move:15:true", "scroll", "up:19", "move:25:false"), calls);
        assertEquals(25, input.pointerX());
        assertEquals(440, input.pointerScreenY());
        backendInput.dispose();
    }

    @Test
    void mapsAllButtonsAndFocusLossReleasesHeldButtonsAtEventCoordinates() {
        var input = new DefaultInput();
        var downs = new ArrayList<MouseButton>();
        var ups = new ArrayList<MouseButton>();
        input.addProcessor(new InputAdapter() {
            @Override public boolean pointerDown(PointerEvent event) { downs.add(event.button()); return true; }
            @Override public boolean pointerUp(PointerEvent event) {
                ups.add(event.button());
                assertEquals(42, event.x());
                assertEquals(-258, input.pointerScreenX());
                assertFalse(input.isMouseButtonPressed(event.button()));
                return true;
            }
        });
        for (int code : new int[] {0, 1, 2, 3, 4, 7}) {
            DesktopCInput.dispatch(new double[] {4, code, 1, 10, 20, -290, 420, 0}, input);
        }
        var expected = java.util.List.of(MouseButton.LEFT, MouseButton.RIGHT, MouseButton.MIDDLE,
                MouseButton.BACK, MouseButton.FORWARD, MouseButton.UNKNOWN);
        assertEquals(expected, downs);
        DesktopCInput.dispatch(new double[] {6, 1, 0, 42, 50, -258, 450, 0}, input);
        assertTrue(ups.isEmpty());
        DesktopCInput.dispatch(new double[] {6, 0, 0, 42, 50, -258, 450, 0}, input);
        assertEquals(6, ups.size());
        assertTrue(ups.containsAll(expected));
        for (MouseButton button : MouseButton.values()) assertFalse(input.isMouseButtonPressed(button));
        DesktopCInput.dispatch(new double[] {6, 0, 0, 42, 50, -258, 450, 0}, input);
        assertEquals(6, ups.size());
    }

    private static final class TestQueue implements DesktopCInput.Queue {
        final double[][] events;
        int next;
        int disposals;
        long window;
        long installResult = 3;
        boolean failed;
        double[] buffer;
        TestQueue(double[][] events) { this.events = events; }
        public long install(long window) { this.window = window; return installResult; }
        public int poll(long handle, double[] event) {
            assertEquals(3, handle);
            if (buffer == null) buffer = event;
            assertSame(buffer, event);
            if (failed) return -1;
            if (next == events.length) return 0;
            assertEquals(8, event.length);
            System.arraycopy(events[next++], 0, event, 0, 8);
            return 1;
        }
        public void dispose(long handle) { assertEquals(3, handle); disposals++; }
    }
}
