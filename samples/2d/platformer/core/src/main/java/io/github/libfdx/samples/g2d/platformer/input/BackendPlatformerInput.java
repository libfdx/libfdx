package io.github.libfdx.samples.g2d.platformer.input;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.input.*;
import io.github.libfdx.samples.g2d.platformer.PlatformerView;

/** Owns the sample's rebindable controls and detaches its processor on disposal. */
public final class BackendPlatformerInput implements PlatformerInput, Disposable {
    private final InputActions actions;
    private final InputAction move,jump,restart,touchMove,touchJump;
    private int layoutRevision = -1, pointerControl;
    private boolean pointerJumpPressed;

    public BackendPlatformerInput(Input input) {
        this(input, null);
    }
    /** Registers gameplay as a borrowed router child when supplied; the caller owns router registration. */
    public BackendPlatformerInput(Input input, InputRouter router) {
        if(input==null) { actions=null; move=jump=restart=touchMove=touchJump=null; return; }
        actions=new InputActions(input); move=actions.define("move",.1f);
        jump=actions.define("jump"); restart=actions.define("restart");
        touchMove=actions.define("touch-move",.1f); touchJump=actions.define("touch-jump");
        actions.bind(move,InputBinding.key(Key.A,-1)).bind(move,InputBinding.key(Key.LEFT,-1));
        actions.bind(move,InputBinding.key(Key.D)).bind(move,InputBinding.key(Key.RIGHT));
        actions.bind(move,InputBinding.axis(-1,GamepadAxis.LEFT_X,.2f,1));
        actions.bind(move,InputBinding.button(-1,GamepadButton.DPAD_LEFT,-1));
        actions.bind(move,InputBinding.button(-1,GamepadButton.DPAD_RIGHT,1));
        actions.bind(touchMove,InputBinding.touch(0,.6f,.25f,.4f,-1));
        actions.bind(touchMove,InputBinding.touch(.25f,.6f,.25f,.4f,1));
        jumpBinding(InputBinding.key(Key.SPACE)); jumpBinding(InputBinding.key(Key.W)); jumpBinding(InputBinding.key(Key.UP));
        jumpBinding(InputBinding.mouse(MouseButton.LEFT)); jumpBinding(InputBinding.button(-1,GamepadButton.SOUTH,1));
        actions.bind(touchJump,InputBinding.touch(.6f,.6f,.4f,.4f,1));
        actions.bind(restart,InputBinding.key(Key.R)).bind(restart,InputBinding.key(Key.ENTER));
        if (router == null) { input.addProcessor(actions); } else { router.add(actions); }
    }
    private void jumpBinding(InputBinding binding) { actions.bind(jump,binding).bind(restart,binding); }
    /** Borrowed context for settings/rebinding; null for a null backend. */
    public InputActions actions() { return actions; }
    public void update(int width,int height) {
        if(actions!=null) { actions.surface(Math.max(1,width),Math.max(1,height)); actions.update(); }
    }
    public void enabled(boolean enabled) {
        if(actions!=null) actions.enabled(enabled);
        if (!enabled) { pointerControl=0; pointerJumpPressed=false; }
    }
    /** Visible button bindings are rebuilt only after viewport/window resize; keyboard/controller rebinding is preserved. */
    public void layout(PlatformerView view) {
        if (actions == null || layoutRevision == view.revision()) { return; }
        layoutRevision = view.revision(); actions.clearBindings(touchMove); actions.clearBindings(touchJump);
        bindTouch(touchMove, view.touch(8,8,36,28,-1)); bindTouch(touchMove, view.touch(50,8,36,28,1));
        bindTouch(touchJump, view.touch(242,8,50,28,1));
    }
    private void bindTouch(InputAction action, InputBinding binding) { if (binding != null) { actions.bind(action,binding); } }
    /** Mouse interaction with the visible buttons: -1 left, 1 right, 2 jump, 0 release. */
    public void pointerControl(int control) {
        if (actions == null || !actions.enabled()) { return; }
        if (control == 2 && pointerControl != 2) { pointerJumpPressed=true; }
        pointerControl=control;
    }
    @Override public boolean leftDown() { return move!=null && (move.value()+touchMove.value()<-.1f || pointerControl==-1); }
    @Override public boolean rightDown() { return move!=null && (move.value()+touchMove.value()>.1f || pointerControl==1); }
    @Override public boolean jumpDown() { return jump!=null && (jump.down() || touchJump.down() || pointerControl==2); }
    @Override public boolean restartDown() { return restart!=null && (restart.down() || touchJump.down()); }
    @Override public boolean consumeJumpPress() {
        boolean pointer = pointerJumpPressed; pointerJumpPressed=false;
        return jump!=null && (jump.consumePressed() | touchJump.consumePressed() | pointer);
    }
    @Override public boolean consumeRestartPress() { return restart!=null && restart.consumePressed(); }
    @Override public boolean isDisposed() { return actions==null || actions.isDisposed(); }
    @Override public void dispose() { if(actions!=null) actions.dispose(); }
}
