package io.github.libfdx.samples.g2d.platformer.input;

import io.github.libfdx.input.*;
import io.github.libfdx.samples.g2d.platformer.PlatformerView;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PlatformerMenuInputTest {
    @Test
    void menuClicksAreConsumedAndVisibleTouchButtonsFollowHighDpiLetterboxing() {
        DefaultInput input=new DefaultInput(); InputRouter router=new InputRouter(); PlatformerView view=new PlatformerView();
        view.update(480,270,960,540);
        PlatformerMenuInput menu=new PlatformerMenuInput(input,router,view,null,null);
        BackendPlatformerInput game=new BackendPlatformerInput(input,router); menu.controls(game); input.addProcessor(router);
        try {
            game.layout(view); game.update(480,270);
            // Logical menu (275,164) -> framebuffer (855,492) -> window (427,24).
            input.dispatchPointerDown(MouseButton.LEFT,427,24); input.dispatchPointerUp(MouseButton.LEFT,427,24);
            assertEquals(PlatformerMenuInput.PAUSE,menu.consumeCommands()); assertFalse(game.consumeJumpPress());
            // Logical right (68,22) and jump (267,22); independent fingers.
            input.dispatchTouchDown(1,117,237,1); input.dispatchTouchDown(2,415,237,1); game.update(480,270);
            assertTrue(game.rightDown()); assertTrue(game.consumeJumpPress()); assertFalse(game.leftDown());
            input.dispatchTouchUp(1,117,237,0); input.dispatchTouchUp(2,415,237,0); game.update(480,270);
            assertFalse(game.rightDown()); assertFalse(game.jumpDown());
            input.dispatchPointerDown(MouseButton.LEFT,54,237); game.update(480,270);
            assertTrue(game.leftDown()); assertFalse(game.consumeJumpPress());
            input.dispatchPointerUp(MouseButton.LEFT,54,237); assertFalse(game.leftDown());
            menu.state(true,false); game.enabled(false);
            input.dispatchKeyDown(Key.D); game.enabled(true); game.update(480,270); assertFalse(game.rightDown());
            input.dispatchKeyUp(Key.D); menu.state(false,false); input.dispatchKeyDown(Key.D); game.update(480,270); assertTrue(game.rightDown());
        } finally { input.removeProcessor(router); game.dispose(); menu.dispose(); }
    }
    @Test
    void croppedControlsAndResizePreserveCustomKeyboardBindings() {
        DefaultInput input=new DefaultInput(); BackendPlatformerInput game=new BackendPlatformerInput(input);
        PlatformerView view=new PlatformerView();
        try {
            var jump=game.actions().find("jump"); game.actions().clearBindings(jump).bind(jump,InputBinding.key(Key.K));
            view.update(200,100,200,100); game.layout(view); game.update(200,100);
            assertNull(view.touch(8,8,36,28,-1));
            input.dispatchKeyDown(Key.K); assertTrue(game.consumeJumpPress());
            input.dispatchKeyUp(Key.K); view.update(800,600,1600,1200); game.layout(view); game.update(800,600);
            input.dispatchKeyDown(Key.K); assertTrue(game.consumeJumpPress());
        } finally { game.dispose(); }
    }
}
