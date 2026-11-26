package io.github.libfdx.samples.g2d.platformer.input;

import io.github.libfdx.audio.Audio;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.Logger;
import io.github.libfdx.input.*;

/** Routes menu gestures before gameplay and activates browser audio inside the originating gesture. */
public final class PlatformerMenuInput extends InputAdapter implements Disposable {
    public static final int PAUSE=1, RESTART=2, NEXT=4, QUIETER=8, LOUDER=16, MUTE=32;
    private final InputActions actions;
    private final InputAction pause,restart,next,quieter,louder,mute;
    private final Audio audio;
    private final Logger logger;
    private int commands;
    private boolean activationPending, disposed;
    public PlatformerMenuInput(Input input, InputRouter router, Audio audio, Logger logger) {
        this.audio=audio; this.logger=logger; actions=new InputActions(input);
        pause=actions.define("pause"); restart=actions.define("restart"); next=actions.define("next");
        quieter=actions.define("quieter"); louder=actions.define("louder"); mute=actions.define("mute");
        actions.bind(pause,InputBinding.key(Key.ESCAPE)).bind(pause,InputBinding.key(Key.P))
                .bind(pause,InputBinding.button(-1,GamepadButton.START,1));
        actions.bind(restart,InputBinding.key(Key.R)).bind(restart,InputBinding.key(Key.ENTER));
        actions.bind(next,InputBinding.key(Key.N)).bind(next,InputBinding.button(-1,GamepadButton.EAST,1));
        actions.bind(quieter,InputBinding.key(Key.Q)); actions.bind(louder,InputBinding.key(Key.E));
        actions.bind(mute,InputBinding.key(Key.M));
        router.add(this).add(actions);
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
    public boolean keyDown(KeyEvent event) { if (!event.repeat()) activate(); return false; }
    @Override
    public boolean pointerDown(PointerEvent event) {
        activate();
        return false;
    }

    @Override
    public boolean touchDown(TouchEvent event) {
        activate();
        return false;
    }

    @Override
    public boolean isDisposed() { return disposed; }
    @Override
    public void dispose() { if (!disposed) { disposed=true; actions.dispose(); commands=0; } }
}
