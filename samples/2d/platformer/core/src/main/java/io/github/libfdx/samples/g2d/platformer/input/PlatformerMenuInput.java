package io.github.libfdx.samples.g2d.platformer.input;

import io.github.libfdx.audio.Audio;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.Logger;
import io.github.libfdx.input.*;
import io.github.libfdx.samples.g2d.platformer.PlatformerView;

/** Routes menu gestures before gameplay and activates browser audio inside the originating gesture. */
public final class PlatformerMenuInput extends InputAdapter implements Disposable {
    public static final int PAUSE=1, RESTART=2, NEXT=4, QUIETER=8, LOUDER=16, MUTE=32;
    private final InputActions actions;
    private final InputAction pause,restart,next,quieter,louder,mute;
    private final PlatformerView view;
    private final Audio audio;
    private final Logger logger;
    private BackendPlatformerInput controls;
    private int commands;
    private boolean menuOpen, blocked, activationPending, disposed;
    public PlatformerMenuInput(Input input, InputRouter router, PlatformerView view, Audio audio, Logger logger) {
        this.view=view; this.audio=audio; this.logger=logger; actions=new InputActions(input);
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
    public void controls(BackendPlatformerInput controls) { this.controls=controls; }
    public void state(boolean menuOpen, boolean blocked) { this.menuOpen=menuOpen; this.blocked=blocked; }
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
        activate(); return event.button()==MouseButton.LEFT && press(event.x(),event.y(),true);
    }
    @Override
    public boolean pointerUp(PointerEvent event) {
        if (event.button()==MouseButton.LEFT && controls!=null) controls.pointerControl(0);
        return false;
    }
    @Override
    public boolean touchDown(TouchEvent event) {
        activate(); return event.point()!=null && press(event.point().x(),event.point().y(),false);
    }
    private boolean press(float windowX,float windowY,boolean mouse) {
        if (!view.contains(windowX,windowY)) return true;
        float x=view.x(windowX),y=view.y(windowY);
        if (PlatformerView.hit(x,y,258,156,34,16)) { commands|=PAUSE; return true; }
        if (menuOpen || blocked) {
            if (!blocked) {
                if (PlatformerView.hit(x,y,62,92,80,18)) commands|=PAUSE;
                if (PlatformerView.hit(x,y,158,92,80,18)) commands|=RESTART;
                if (PlatformerView.hit(x,y,62,68,176,18)) commands|=NEXT;
                if (PlatformerView.hit(x,y,62,46,24,18)) commands|=QUIETER;
                if (PlatformerView.hit(x,y,180,46,24,18)) commands|=LOUDER;
                if (PlatformerView.hit(x,y,212,46,26,18)) commands|=MUTE;
            }
            return true;
        }
        if (mouse && controls!=null) {
            int control = PlatformerView.hit(x,y,8,8,36,28) ? -1 : PlatformerView.hit(x,y,50,8,36,28) ? 1
                    : PlatformerView.hit(x,y,242,8,50,28) ? 2 : 0;
            if (control!=0) { controls.pointerControl(control); return true; }
        }
        return false;
    }
    @Override
    public boolean isDisposed() { return disposed; }
    @Override
    public void dispose() { if (!disposed) { disposed=true; actions.dispose(); commands=0; controls=null; } }
}
