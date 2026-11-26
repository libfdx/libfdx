package io.github.libfdx.samples.g2d.platformer.input;

import io.github.libfdx.input.*;
import io.github.libfdx.ui.*;
import io.github.libfdx.display.Display;
import io.github.libfdx.samples.g2d.platformer.render.PlatformerHud;
import java.lang.reflect.Proxy;
import io.github.libfdx.samples.g2d.platformer.PlatformerView;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PlatformerMenuInputTest {
    @Test
    void menuClicksAreConsumedAndVisibleTouchButtonsFollowHighDpiLetterboxing() {
        DefaultInput input=new DefaultInput(); InputRouter router=new InputRouter(); PlatformerView view=new PlatformerView();
        view.update(480,270,960,540);
        PlatformerMenuInput menu=new PlatformerMenuInput(input,router,null,null);
        int[] metrics = {480, 270, 960, 540};
        Display display = (Display) Proxy.newProxyInstance(Display.class.getClassLoader(), new Class<?>[]{Display.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "width" -> metrics[0];
                    case "height" -> metrics[1];
                    case "framebufferWidth" -> metrics[2];
                    case "framebufferHeight" -> metrics[3];
                    case "contentScale" -> (float) metrics[2] / metrics[0];
                    case "focused" -> true;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        UiRoot root = new UiToolkit(null).root(display, null).allowFontFallback(true).autoUiScale(false).input(input, router);
        PlatformerHud hud = new PlatformerHud();
        hud.loading.set(false);
        BackendPlatformerInput game=new BackendPlatformerInput(input,router); input.addProcessor(router);
        try {
            hud.layout(root, view, 540);
            root.setContent(ui -> hud.build(ui, menu, game));
            root.update(0);
            game.update(480,270);
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
            // In a 200 by 100 framebuffer the movement controls are fully cropped below the world.
            metrics[0] = metrics[2] = 200;
            metrics[1] = metrics[3] = 100;
            view.update(200, 100, 200, 100);
            hud.layout(root, view, 100);
            root.resize(200, 100);
            root.update(0);
            input.dispatchPointerDown(MouseButton.LEFT, 18, 97);
            assertFalse(game.rightDown());
            input.dispatchPointerUp(MouseButton.LEFT, 18, 97);
            game.enabled(false);
            input.dispatchKeyDown(Key.D); game.enabled(true); game.update(480,270); assertFalse(game.rightDown());
            input.dispatchKeyUp(Key.D); input.dispatchKeyDown(Key.D); game.update(480,270); assertTrue(game.rightDown());
        } finally { input.removeProcessor(router); root.dispose(); game.dispose(); menu.dispose(); }
    }
    @Test
    void resizePreservesCustomKeyboardBindings() {
        DefaultInput input=new DefaultInput(); BackendPlatformerInput game=new BackendPlatformerInput(input);
        try {
            var jump=game.actions().find("jump"); game.actions().clearBindings(jump).bind(jump,InputBinding.key(Key.K));
            game.update(200,100);
            input.dispatchKeyDown(Key.K); assertTrue(game.consumeJumpPress());
            input.dispatchKeyUp(Key.K); game.update(800,600);
            input.dispatchKeyDown(Key.K); assertTrue(game.consumeJumpPress());
        } finally { game.dispose(); }
    }
}
