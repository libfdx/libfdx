package io.github.libfdx.samples.g2d.platformer.input;

import io.github.libfdx.audio.Audio;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.Logger;
import io.github.libfdx.input.*;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;

/** Routes menu gestures before gameplay and activates browser audio inside the originating gesture. */
public final class PlatformerMenuInput extends InputAdapter implements Disposable {
    public static final int PAUSE=1, RESTART=2, NEXT=4, QUIETER=8, LOUDER=16, MUTE=32, CONFIRM=64;
    public static final int OPTIONS=128, HELP=256, BACK=512, MAIN_MENU=1024;
    public final UiBooleanState touchControls = Ui.state(false);
    private final Input input;
    private final InputActions actions;
    private final InputAction pause,restart,next,quieter,louder,mute,confirm;
    private final Audio audio;
    private final Logger logger;
    private int commands;
    private boolean activationPending, disposed;
    public PlatformerMenuInput(Input input, Audio audio, Logger logger) {
        this.input = input;
        this.audio=audio; this.logger=logger; actions=new InputActions(input);
        pause=actions.define("pause"); restart=actions.define("restart"); next=actions.define("next");
        quieter=actions.define("quieter"); louder=actions.define("louder"); mute=actions.define("mute");
        actions.bind(pause,InputBinding.key(Key.ESCAPE)).bind(pause,InputBinding.key(Key.P))
                .bind(pause,InputBinding.button(-1,GamepadButton.START,1));
        actions.bind(restart,InputBinding.key(Key.R));
        confirm = actions.define("confirm");
        actions.bind(confirm, InputBinding.key(Key.ENTER)).bind(confirm, InputBinding.button(-1, GamepadButton.SOUTH, 1));
        actions.bind(next,InputBinding.key(Key.N)).bind(next,InputBinding.button(-1,GamepadButton.EAST,1));
        actions.bind(quieter,InputBinding.key(Key.Q)); actions.bind(louder,InputBinding.key(Key.E));
        actions.bind(mute,InputBinding.key(Key.M));
    }

    public InputActions shortcuts() {
        return actions;
    }

    public void command(int command) {
        if (actions.enabled()) commands |= command;
    }

    public void enabled(boolean enabled) { actions.enabled(enabled); if (!enabled) commands=0; }
    public int consumeCommands() {
        actions.update(); int result=commands; commands=0;
        if (pause.consumePressed()) result|=PAUSE;
        if (restart.consumePressed()) result|=RESTART;
        if (next.consumePressed()) result|=NEXT;
        if (quieter.consumePressed()) result|=QUIETER;
        if (louder.consumePressed()) result|=LOUDER;
        if (mute.consumePressed()) result|=MUTE;
        if (confirm.consumePressed()) result|=CONFIRM;
        if (touchControls.get() && input.gamepads() != null) {
            var pads = input.gamepads().connected();
            for (int i = 0; i < pads.size(); i++) {
                Gamepad pad = pads.get(i);
                if (Math.abs(pad.axis(GamepadAxis.LEFT_X)) > .2f || pad.pressed(GamepadButton.DPAD_LEFT)
                        || pad.pressed(GamepadButton.DPAD_RIGHT) || pad.pressed(GamepadButton.DPAD_DOWN) || pad.pressed(GamepadButton.SOUTH)
                        || pad.pressed(GamepadButton.START) || pad.pressed(GamepadButton.EAST)) {
                    touchControls.set(false);
                    break;
                }
            }
        }
        return result;
    }
    private void activate() {
        if (audio==null || audio.isDisposed() || !audio.isSuspended() || activationPending) return;
        activationPending=true;
        try {
            audio.resume().onSuccess(unused -> activationPending=false).onFailure(error -> {
                activationPending=false; logger.warn("Platformer audio activation failed: "+error.getMessage());
            });
        } catch (RuntimeException error) { activationPending=false; logger.warn("Platformer audio activation failed: "+error.getMessage()); }
    }
    @Override
    public boolean keyDown(KeyEvent event) {
        touchControls.set(false);
        if (!event.repeat()) activate();
        return false;
    }
    @Override
    public boolean pointerDown(PointerEvent event) {
        touchControls.set(event.type() == PointerType.TOUCH);
        activate();
        return false;
    }

    @Override
    public boolean touchDown(TouchEvent event) {
        touchControls.set(true);
        activate();
        return false;
    }

    @Override
    public boolean isDisposed() { return disposed; }
    @Override
    public void dispose() { if (!disposed) { disposed=true; actions.dispose(); commands=0; } }
}
