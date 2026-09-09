package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.GraphicsParityTest;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.FixedStepClock;
import io.github.libfdx.input.*;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import java.nio.ByteBuffer;

/** Keyboard/mouse/touch/controller action exercise with UI routing and persisted rebinding. */
public final class InputActionsTest extends GraphicsParityTest {
    private static final LoadOp CLEAR=LoadOp.clear(.025f,.04f,.06f,1);
    private final FixedStepClock clock=new FixedStepClock(1.0/60,8);
    private Input input;
    private InputActions actions;
    private InputAction move,jump;
    private InputRouter router;
    private SpriteBatch batch;
    private Texture white;
    private int jumps,connected=-1;
    private float previousMove;
    private String saved;

    public InputActionsTest(long frames) { super(frames); }
    @Override public void create(Fdx fdx) {
        initialize(fdx,"InputActionsTest"); input=fdx.input(); actions=new InputActions(input);
        move=actions.define("move",.1f); jump=actions.define("jump");
        actions.bind(move,InputBinding.key(Key.A,-1)).bind(move,InputBinding.key(Key.D));
        actions.bind(move,InputBinding.axis(-1,GamepadAxis.LEFT_X,.2f,1));
        actions.bind(move,InputBinding.touch(0,.6f,.25f,.4f,-1)).bind(move,InputBinding.touch(.25f,.6f,.25f,.4f,1));
        actions.bind(jump,InputBinding.key(Key.SPACE)).bind(jump,InputBinding.key(Key.Q));
        actions.bind(jump,InputBinding.mouse(MouseButton.LEFT)).bind(jump,InputBinding.button(-1,GamepadButton.SOUTH,1));
        actions.bind(jump,InputBinding.touch(.6f,.6f,.4f,.4f,1)); saved=actions.exportBindings();
        router=new InputRouter().add(new InputAdapter() {
            @Override public boolean keyDown(KeyEvent event) {
                if(event.repeat()) return false;
                if(event.key()==Key.Q) { logger.info("InputActionsTest UI consumed Q"); return true; }
                if(event.key()==Key.F1) {
                    actions.enabled(!actions.enabled()); logger.info("InputActionsTest enabled="+actions.enabled()); return true;
                }
                if(event.key()==Key.F2) {
                    actions.importBindings(saved.replace("KEY\tSPACE","KEY\tJ"));
                    logger.info("InputActionsTest rebound jump=J"); return true;
                }
                return false;
            }
        }).add(actions); input.addProcessor(router);
        batch=new SpriteBatch(graphics); white=graphics.device().createTexture(TextureDescriptor.rgba8("Input status",1,1));
        ByteBuffer pixel=ByteBuffer.allocateDirect(4); pixel.putInt(-1).flip(); graphics.device().writeTexture(white,pixel);
        markCreated(); logger.info("InputActionsTest ready: gamepadCapability="+input.capabilities().supportsGamepads());
    }
    @Override public void render() {
        actions.surface(Math.max(1,display.width()),Math.max(1,display.height())); actions.update();
        int pads=input.gamepads()==null ? 0 : input.gamepads().connected().size();
        if(pads!=connected) {
            connected=pads; logger.info("InputActionsTest connected="+pads);
            if(pads>0) logger.info("InputActionsTest leftTrigger="+input.gamepads().connected().get(0).axis(GamepadAxis.LEFT_TRIGGER));
        }
        if(move.value()!=previousMove) { previousMove=move.value(); logger.info("InputActionsTest move="+previousMove); }
        int steps=clock.advance(Math.max(0,application.deltaTime()));
        if(steps>0) {
            if(jump.consumePressed()) logger.info("InputActionsTest jump="+(++jumps));
            actions.clearTransitions();
        }
        batch.viewport(framebufferWidth(),framebufferHeight()); batch.begin(CLEAR);
        batch.color(.12f,.22f,.34f,1); batch.draw(white,-.9f,-.85f,.4f,.5f); batch.draw(white,-.4f,-.85f,.4f,.5f);
        batch.color(jump.down()?.9f:.25f,.55f,.25f,1); batch.draw(white,.3f,-.85f,.6f,.5f);
        batch.color(.2f,.55f,.8f,1); batch.draw(white,-.85f,.2f,1.7f,.15f);
        batch.color(1,.7f,.25f,1); batch.draw(white,move.value()*.8f-.025f,.15f,.05f,.25f);
        batch.color(actions.enabled()?.3f:.8f,.7f,.4f,1); batch.draw(white,-.85f,.65f,Math.min(jumps,16)*.1f,.08f);
        batch.end(); finishFrame();
    }
    @Override public void pause() { clock.pause(); if(actions!=null) actions.enabled(false); }
    @Override public void resume() { clock.resume(); if(actions!=null) actions.enabled(true); }
    @Override public void dispose() {
        if(input!=null) input.removeProcessor(router);
        dispose(actions); dispose(batch); dispose(white); verifyDisposed();
    }
}
