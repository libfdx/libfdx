package io.github.libfdx.input;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class InputActionsTest {
    @Test void shortTapsLatchAcrossRenderOnlyFramesAndRepeatDoesNotCreateAnotherPress() {
        DefaultInput input=new DefaultInput(); InputActions actions=new InputActions(input);
        InputAction jump=actions.define("jump"); actions.bind(jump,InputBinding.key(Key.SPACE)); input.addProcessor(actions);
        input.dispatchKeyDown(Key.SPACE); input.dispatchKeyUp(Key.SPACE);
        for(int i=0;i<5;i++) actions.update();
        assertFalse(jump.down()); assertTrue(jump.consumePressed()); assertTrue(jump.consumeReleased());
        assertFalse(jump.consumePressed()); input.dispatchKeyDown(Key.SPACE); assertTrue(jump.consumePressed());
        input.dispatchKeyDown(Key.SPACE); assertFalse(jump.consumePressed());
        actions.enabled(false); input.dispatchKeyDown(Key.SPACE); actions.enabled(true);
        input.dispatchKeyDown(Key.SPACE); assertFalse(jump.down());
        input.dispatchKeyUp(Key.SPACE); input.dispatchKeyDown(Key.SPACE); assertTrue(jump.consumePressed());
        actions.dispose(); input.dispatchKeyUp(Key.SPACE); assertFalse(jump.down());
    }
    @Test void alternateBindingsAggregateAndRouterRespectsUiWithoutSwallowingReleases() {
        DefaultInput input=new DefaultInput(); InputActions actions=new InputActions(input);
        InputAction move=actions.define("move");
        actions.bind(move,InputBinding.key(Key.A,-1)).bind(move,InputBinding.key(Key.LEFT,-1)).bind(move,InputBinding.key(Key.D));
        boolean[] modal={false}; InputRouter router=new InputRouter().add(new InputAdapter() {
            @Override public boolean keyDown(KeyEvent e) { return modal[0]; }
            @Override public boolean keyUp(KeyEvent e) { return modal[0]; }
        }).add(actions); input.addProcessor(router);
        input.dispatchKeyDown(Key.A); input.dispatchKeyDown(Key.LEFT); input.dispatchKeyUp(Key.A); actions.update();
        assertEquals(-1,move.value()); input.dispatchKeyDown(Key.D); assertEquals(0,move.value());
        input.dispatchKeyUp(Key.D); assertEquals(-1,move.value()); modal[0]=true;
        input.dispatchKeyUp(Key.LEFT); actions.update(); assertEquals(0,move.value()); actions.clearTransitions();
        input.dispatchKeyDown(Key.D); actions.update(); assertTrue(input.isKeyPressed(Key.D));
        assertFalse(move.down()); assertFalse(move.pressed()); input.dispatchKeyUp(Key.D);
        InputRouter other=new InputRouter().add(router);
        assertThrows(IllegalArgumentException.class,()->router.add(other));
        actions.dispose();
    }
    @Test void gamepadsUseDeadZonesHotplugNeutralGatesAndStrongestMappedControl() {
        DefaultInput input=new DefaultInput(); DefaultGamepads pads=input.gamepads().as();
        InputActions actions=new InputActions(input); InputAction move=actions.define("move",.1f), fire=actions.define("fire");
        actions.bind(move,InputBinding.axis(-1,GamepadAxis.LEFT_X,.2f,1));
        actions.bind(fire,InputBinding.button(1,GamepadButton.SOUTH,1));
        DefaultGamepad a=new DefaultGamepad("a","First",0,GamepadMapping.STANDARD);
        DefaultGamepad b=new DefaultGamepad("b","Second",1,GamepadMapping.STANDARD);
        pads.connect(a); pads.connect(b); a.state().axis(GamepadAxis.LEFT_X,.15f); actions.update(); assertEquals(0,move.value());
        a.state().axis(GamepadAxis.LEFT_X,.6f); actions.update(); assertEquals(.5f,move.value(),.00001f);
        b.state().axis(GamepadAxis.LEFT_X,-.8f); b.state().button(GamepadButton.SOUTH,true); actions.update();
        assertEquals(-.75f,move.value(),.00001f); assertTrue(fire.consumePressed());
        actions.enabled(false); actions.enabled(true); actions.update(); assertEquals(0,move.value()); assertFalse(fire.down());
        a.state().axis(GamepadAxis.LEFT_X,0); b.state().axis(GamepadAxis.LEFT_X,0); b.state().button(GamepadButton.SOUTH,false); actions.update();
        b.state().axis(GamepadAxis.LEFT_X,1); actions.update(); assertEquals(1,move.value());
        b.connected(false); pads.disconnect(b); actions.update(); assertEquals(0,move.value());
        a.state().axis(GamepadAxis.LEFT_X,Float.NaN); actions.update(); assertEquals(0,move.value());
        actions.dispose();
    }
    @Test void touchRegionsTrackIndependentFingersAndUseLogicalSurfaceCoordinates() {
        InputBinding edge=InputBinding.touch(.6f,.6f,.4f,.4f,1);
        assertEquals(1,(double)edge.x()+edge.width(),.0000001);
        assertThrows(IllegalArgumentException.class,()->InputBinding.touch(.6f,0,.4001f,1,1));
        DefaultInput input=new DefaultInput(); InputActions actions=new InputActions(input);
        InputAction left=actions.define("left"), right=actions.define("right");
        actions.bind(left,InputBinding.touch(0,.5f,.4f,.5f,1)); actions.bind(right,InputBinding.touch(.6f,.5f,.4f,.5f,1));
        actions.surface(200,100); input.addProcessor(actions);
        assertTrue(input.dispatchTouchDown(7,20,80,1)); assertTrue(left.down());
        assertFalse(input.dispatchTouchDown(9,100,10,1),"Other finger must not consume an unrelated touch");
        input.dispatchTouchDown(8,180,80,1); assertTrue(right.down());
        input.dispatchTouchUp(7,20,80,0); assertFalse(left.down()); assertTrue(right.down());
        input.dispatchTouchMoved(8,20,80,1); assertTrue(left.down()); assertFalse(right.down());
        actions.surface(400,200); assertFalse(left.down());
        input.dispatchTouchUp(8,20,80,0); actions.reset(); assertFalse(left.down()); assertFalse(right.down());
        actions.dispose();
    }
    @Test void bindingsPersistByNamesAndInvalidReplacementPreservesConfigurationAndState() {
        DefaultInput input=new DefaultInput(); InputActions actions=new InputActions(input);
        InputAction move=actions.define("move"), jump=actions.define("jump");
        actions.bind(move,InputBinding.key(Key.A,-1)).bind(move,InputBinding.axis(0,GamepadAxis.LEFT_X,.15f,1));
        actions.bind(jump,InputBinding.key(Key.SPACE)).bind(jump,InputBinding.mouse(MouseButton.LEFT));
        actions.bind(jump,InputBinding.touch(.5f,.5f,.5f,.5f,1));
        actions.bind(jump,InputBinding.button(-1,GamepadButton.SOUTH,1));
        String saved=actions.exportBindings(); assertTrue(saved.contains("GAMEPAD_AXIS\tLEFT_X"));
        input.addProcessor(actions); input.dispatchKeyDown(Key.A);
        assertThrows(IllegalArgumentException.class,()->actions.importBindings(saved.replace("KEY\tSPACE","KEY\tNO_SUCH_KEY")));
        assertEquals(saved,actions.exportBindings()); assertEquals(-1,move.value());
        actions.clearBindings(jump).bind(jump,InputBinding.key(Key.ENTER));
        actions.importBindings(saved.replace("\n","\r\n")); assertEquals(saved,actions.exportBindings());
        assertEquals(0,move.value()); input.dispatchKeyUp(Key.A); input.dispatchKeyDown(Key.SPACE); assertTrue(jump.consumePressed());
        assertThrows(IllegalArgumentException.class,()->actions.importBindings(saved.replace("jump\t","unknown\t")));
        assertThrows(IllegalArgumentException.class,()->actions.importBindings(saved.replace(".15","NaN")));
        actions.dispose();
    }
}
