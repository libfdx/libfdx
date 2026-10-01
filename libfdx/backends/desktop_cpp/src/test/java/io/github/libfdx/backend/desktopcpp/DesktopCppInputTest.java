package io.github.libfdx.backend.desktopcpp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import io.github.libfdx.input.PointerType;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

class DesktopCppInputTest {
    @Test
    void deliversDownMoveUpMoveWithHeldStateDuringDrag() {
        var input = new DefaultInput();
        var calls = new ArrayList<String>();
        input.addProcessor(new InputAdapter() {
            @Override public boolean pointerDown(PointerEvent event) {
                assertEquals(MouseButton.LEFT, event.button());
                calls.add("down");
                return true;
            }
            @Override public boolean pointerMoved(PointerEvent event) {
                calls.add("move:" + input.isMouseButtonPressed(MouseButton.LEFT));
                assertEquals(event.x() - 300, input.pointerScreenX());
                return true;
            }
            @Override public boolean pointerUp(PointerEvent event) {
                assertEquals(MouseButton.LEFT, event.button());
                assertFalse(input.isMouseButtonPressed(MouseButton.LEFT));
                calls.add("up");
                return true;
            }
        });
        DesktopCppInput.dispatch(new double[] {4, 0, 1, 10, 20, -290, 420, 0}, input, null);
        DesktopCppInput.dispatch(new double[] {3, 0, 0, 15, 28, -285, 428, 0}, input, null);
        DesktopCppInput.dispatch(new double[] {4, 0, 0, 19, 35, -281, 435, 0}, input, null);
        DesktopCppInput.dispatch(new double[] {3, 0, 0, 25, 40, -275, 440, 0}, input, null);
        assertEquals(List.of("down", "move:true", "up", "move:false"), calls);
        assertEquals(25, input.pointerX());
        assertEquals(440, input.pointerScreenY());
    }

    @Test
    void focusLossReleasesPressedButtonsAndAllowsLaterMotionWithoutDragging() {
        var input = new DefaultInput();
        var released = new ArrayList<MouseButton>();
        input.addProcessor(new InputAdapter() {
            @Override public boolean pointerUp(PointerEvent event) {
                released.add(event.button());
                assertFalse(input.isMouseButtonPressed(event.button()));
                return true;
            }
            @Override public boolean pointerMoved(PointerEvent event) {
                assertFalse(input.isMouseButtonPressed(MouseButton.LEFT));
                assertFalse(input.isMouseButtonPressed(MouseButton.RIGHT));
                return true;
            }
        });
        DesktopCppInput.dispatch(new double[] {4, 0, 1, 10, 20, -290, 420, 0}, input, null);
        DesktopCppInput.dispatch(new double[] {4, 1, 1, 10, 20, -290, 420, 0}, input, null);
        DesktopCppInput.dispatch(new double[] {6, 1, 0, 0, 0, 0, 0, 0}, input, null);
        assertTrue(released.isEmpty());
        DesktopCppInput.dispatch(new double[] {6, 0, 0, 0, 0, 0, 0, 0}, input, null);
        assertEquals(List.of(MouseButton.LEFT, MouseButton.RIGHT), released);
        DesktopCppInput.dispatch(new double[] {3, 0, 0, 25, 40, -275, 440, 0}, input, null);
        DesktopCppInput.dispatch(new double[] {6, 0, 0, 0, 0, 0, 0, 0}, input, null);
        assertEquals(2, released.size());
    }

    @Test
    void upwardScrollBecomesNegativeWithoutChangingHorizontalScrollOrPointerMetadata() {
        assertScroll(0.75, 0.25, -0.25f);
    }

    @Test
    void downwardScrollBecomesPositiveWithoutChangingHorizontalScrollOrPointerMetadata() {
        assertScroll(-0.5, -1.75, 1.75f);
    }

    private static void assertScroll(double scrollX, double nativeScrollY, float expectedScrollY) {
        var input = new DefaultInput();
        var received = new PointerEvent[1];
        input.addProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(PointerEvent event) {
                received[0] = event;
                return true;
            }
        });
        double[] nativeEvent = {5, scrollX, nativeScrollY, 37, 81, -263, 481, 0};
        double[] originalEvent = nativeEvent.clone();

        DesktopCppInput.dispatch(nativeEvent, input, null);

        PointerEvent event = received[0];
        assertNotNull(event);
        assertEquals((float) scrollX, event.scrollX());
        assertEquals(expectedScrollY, event.scrollY());
        assertEquals(37, event.x());
        assertEquals(81, event.y());
        assertEquals(0, event.pointerId());
        assertEquals(PointerType.MOUSE, event.type());
        assertEquals(MouseButton.UNKNOWN, event.button());
        assertEquals(37, input.pointerX());
        assertEquals(81, input.pointerY());
        assertEquals(-263, input.pointerScreenX());
        assertEquals(481, input.pointerScreenY());
        assertArrayEquals(originalEvent, nativeEvent);
    }
}
