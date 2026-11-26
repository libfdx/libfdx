package io.github.libfdx.samples.g2d.platformer.input;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.input.*;
import io.github.libfdx.ui.UiHoldState;

/** Owns the sample's rebindable controls and detaches its processor on disposal. */
public final class BackendPlatformerInput implements PlatformerInput, Disposable {
    private final InputActions actions;
    private final InputAction move, jump, restart;
    public final UiHoldState leftButton = new UiHoldState();
    public final UiHoldState rightButton = new UiHoldState();
    public final UiHoldState jumpButton = new UiHoldState();

    public BackendPlatformerInput(Input input) {
        this(input, null);
    }
    /** Registers gameplay as a borrowed router child when supplied; the caller owns router registration. */
    public BackendPlatformerInput(Input input, InputRouter router) {
        if(input==null) { actions=null; move=jump=restart=null; return; }
        actions=new InputActions(input); move=actions.define("move",.1f);
        jump=actions.define("jump"); restart=actions.define("restart");
        actions.bind(move,InputBinding.key(Key.A,-1)).bind(move,InputBinding.key(Key.LEFT,-1));
        actions.bind(move,InputBinding.key(Key.D)).bind(move,InputBinding.key(Key.RIGHT));
        actions.bind(move,InputBinding.axis(-1,GamepadAxis.LEFT_X,.2f,1));
        actions.bind(move,InputBinding.button(-1,GamepadButton.DPAD_LEFT,-1));
        actions.bind(move,InputBinding.button(-1,GamepadButton.DPAD_RIGHT,1));
        jumpBinding(InputBinding.key(Key.SPACE)); jumpBinding(InputBinding.key(Key.W)); jumpBinding(InputBinding.key(Key.UP));
        jumpBinding(InputBinding.mouse(MouseButton.LEFT)); jumpBinding(InputBinding.button(-1,GamepadButton.SOUTH,1));
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
        if (!enabled) jumpButton.consumePressed();
    }

    @Override
    public boolean leftDown() {
        return actions != null && actions.enabled() && (move.value() < -.1f || leftButton.down());
    }

    @Override
    public boolean rightDown() {
        return actions != null && actions.enabled() && (move.value() > .1f || rightButton.down());
    }

    @Override
    public boolean jumpDown() {
        return actions != null && actions.enabled() && (jump.down() || jumpButton.down());
    }

    @Override
    public boolean restartDown() {
        return actions != null && actions.enabled() && (restart.down() || jumpButton.down());
    }

    @Override
    public boolean consumeJumpPress() {
        return actions != null && actions.enabled() && (jump.consumePressed() | jumpButton.consumePressed());
    }

    @Override
    public boolean consumeRestartPress() { return restart!=null && restart.consumePressed(); }
    @Override
    public boolean isDisposed() { return actions==null || actions.isDisposed(); }
    @Override
    public void dispose() { if(actions!=null) actions.dispose(); }
}
