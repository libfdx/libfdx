package io.github.libfdx.input;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.collections.ArrayView;
import java.util.Arrays;

/**
 * Application-owned action context and input processor. Attach explicitly, directly
 * or inside InputRouter. Keyboard/mouse/touch activation uses admitted events, so
 * consumed presses are not recovered through raw polling. Update polls mapped
 * gamepads and releases missing keyboard/mouse up events; call once per frame even
 * if no fixed simulation step runs. Calls and retained actions belong to the
 * application thread. State/transition updates allocate no storage.
 *
 * <p>Reset/disable clears held controls and pending transitions. Held gamepad
 * bindings must return to neutral after reset; repeated key-down events cannot
 * reactivate a held key. Applications reset on focus loss and disable gameplay
 * contexts during modal UI. Low-level backend state remains available.</p>
 */
public final class InputActions extends InputAdapter implements Disposable {
    private final Input input;
    private final InputAction[] actions;
    private final InputBinding[] bindings;
    private final int[] targets;
    private final float[] values, sums;
    private final boolean[] neutralRequired;
    private final int[] touchIds = new int[16], touchX = new int[16], touchY = new int[16];
    private final boolean[] touching = new boolean[16];
    private int actionCount, bindingCount, surfaceWidth=1, surfaceHeight=1;
    private boolean enabled=true, disposed;

    public InputActions(Input input) { this(input,32,128); }
    public InputActions(Input input,int maxActions,int maxBindings) {
        if(input==null || maxActions<1 || maxActions>256 || maxBindings<1 || maxBindings>1024) {
            throw new IllegalArgumentException("Input, 1..256 actions and 1..1024 bindings required");
        }
        this.input=input; actions=new InputAction[maxActions]; sums=new float[maxActions];
        bindings=new InputBinding[maxBindings]; targets=new int[maxBindings]; values=new float[maxBindings];
        neutralRequired=new boolean[maxBindings];
    }
    private void check() { if(disposed) throw new IllegalStateException("Input actions are disposed"); }
    private void owned(InputAction action) {
        check(); if(action==null || action.owner!=this) throw new IllegalArgumentException("Action belongs to another context");
    }
    public InputAction define(String name) { return define(name,.5f); }
    /** Unique portable identifier (ASCII letters/digits/underscore/dot/hyphen), 1..64 characters. */
    public InputAction define(String name,float threshold) {
        check();
        if(name==null || name.isEmpty() || name.length()>64 || !Float.isFinite(threshold) || threshold<=0 || threshold>1) {
            throw new IllegalArgumentException("Invalid action name or threshold");
        }
        for(int i=0;i<name.length();i++) {
            char c=name.charAt(i);
            if(!(c>='a' && c<='z' || c>='A' && c<='Z' || c>='0' && c<='9' || c=='_' || c=='.' || c=='-')) {
                throw new IllegalArgumentException("Action name must be a portable identifier");
            }
        }
        if(find(name)!=null) throw new IllegalArgumentException("Duplicate action: "+name);
        if(actionCount==actions.length) throw new IllegalStateException("Action capacity exhausted");
        InputAction action=new InputAction(this,actionCount,name,threshold); actions[actionCount++]=action; return action;
    }
    public InputAction find(String name) {
        for(int i=0;i<actionCount;i++) if(actions[i].name().equals(name)) return actions[i]; return null;
    }
    /** Adds a binding. Configuration changes reset all live state/transitions. */
    public InputActions bind(InputAction action,InputBinding binding) {
        owned(action); if(binding==null) throw new IllegalArgumentException("Binding required");
        if(bindingCount==bindings.length) throw new IllegalStateException("Binding capacity exhausted");
        targets[bindingCount]=action.index; bindings[bindingCount++]=binding; reset(); return this;
    }
    public InputActions clearBindings(InputAction action) {
        owned(action); int count=0;
        for(int i=0;i<bindingCount;i++) if(targets[i]!=action.index) {
            bindings[count]=bindings[i]; targets[count++]=targets[i];
        }
        Arrays.fill(bindings,count,bindingCount,null); bindingCount=count; reset(); return this;
    }
    public int bindingCount() { return bindingCount; }
    public InputBinding binding(int index) { checkIndex(index); return bindings[index]; }
    public InputAction bindingAction(int index) { checkIndex(index); return actions[targets[index]]; }
    private void checkIndex(int index) { if(index<0 || index>=bindingCount) throw new IndexOutOfBoundsException(index); }
    /** Sets the logical window dimensions used by normalized touch rectangles. */
    public void surface(int width,int height) {
        check(); if(width<1 || height<1) throw new IllegalArgumentException("Positive input surface required");
        if(surfaceWidth==width && surfaceHeight==height) return;
        surfaceWidth=width; surfaceHeight=height; if(enabled) { touchValues(); recompute(); }
    }
    public boolean enabled() { return enabled && !disposed; }
    public void enabled(boolean enabled) { check(); if(this.enabled!=enabled) { this.enabled=enabled; reset(); } }
    /** Clears all state without synthesizing releases. Future physical presses can activate again. */
    public void reset() {
        check(); Arrays.fill(values,0); Arrays.fill(touching,false);
        for(int i=0;i<bindingCount;i++) {
            InputBinding b=bindings[i];
            neutralRequired[i]=(b.kind==InputBinding.Kind.GAMEPAD_AXIS || b.kind==InputBinding.Kind.GAMEPAD_BUTTON)
                    && gamepadValue(b)!=0;
        }
        for(int i=0;i<actionCount;i++) actions[i].reset();
    }
    /** Clears latches after their consumers have read them; omit on render-only frames. */
    public void clearTransitions() { check(); for(int i=0;i<actionCount;i++) actions[i].clearTransitions(); }
    public void update() {
        check(); if(!enabled) return;
        for(int i=0;i<bindingCount;i++) {
            InputBinding b=bindings[i];
            switch(b.kind) {
                case KEY -> { if(values[i]!=0 && !input.isKeyPressed(b.key)) values[i]=0; }
                case MOUSE -> { if(values[i]!=0 && !input.isMouseButtonPressed(b.mouse)) values[i]=0; }
                case GAMEPAD_AXIS, GAMEPAD_BUTTON -> {
                    float value=gamepadValue(b);
                    if(neutralRequired[i]) { if(value==0) neutralRequired[i]=false; values[i]=0; }
                    else values[i]=value;
                }
                case TOUCH -> { }
            }
        }
        recompute();
    }
    private float gamepadValue(InputBinding binding) {
        Gamepads gamepads=input.gamepads(); if(gamepads==null) return 0;
        if(binding.gamepad>=0) return gamepadValue(binding,gamepads.find(binding.gamepad));
        ArrayView<Gamepad> connected=gamepads.connected(); float strongest=0;
        for(int i=0;i<connected.size();i++) {
            float value=gamepadValue(binding,connected.get(i));
            if(Math.abs(value)>Math.abs(strongest)) strongest=value;
        }
        return strongest;
    }
    private static float gamepadValue(InputBinding binding,Gamepad gamepad) {
        if(gamepad==null || !gamepad.isConnected() || gamepad.mapping()!=GamepadMapping.STANDARD) return 0;
        if(binding.kind==InputBinding.Kind.GAMEPAD_BUTTON) return gamepad.pressed(binding.button) ? binding.scale : 0;
        float raw=gamepad.axis(binding.axis); if(!Float.isFinite(raw)) return 0;
        float magnitude=Math.min(1,Math.abs(raw));
        if(magnitude<=binding.deadZone) return 0;
        return Math.copySign((magnitude-binding.deadZone)/(1-binding.deadZone),raw)*binding.scale;
    }
    private void recompute() {
        Arrays.fill(sums,0);
        for(int i=0;i<bindingCount;i++) sums[targets[i]]+=values[i];
        for(int i=0;i<actionCount;i++) actions[i].value(sums[i]);
    }
    @Override
    public boolean keyDown(KeyEvent event) {
        if(!enabled() || event.repeat()) return false;
        return key(event.key(),true);
    }
    @Override
    public boolean keyUp(KeyEvent event) { return enabled() && key(event.key(),false); }
    private boolean key(Key key,boolean down) {
        boolean matched=false;
        for(int i=0;i<bindingCount;i++) if(bindings[i].kind==InputBinding.Kind.KEY && bindings[i].key==key) {
            values[i]=down ? bindings[i].scale : 0; matched=true;
        }
        if(matched) recompute(); return matched;
    }
    @Override
    public boolean pointerDown(PointerEvent event) { return enabled() && mouse(event.button(),true); }
    @Override
    public boolean pointerUp(PointerEvent event) { return enabled() && mouse(event.button(),false); }
    private boolean mouse(MouseButton button,boolean down) {
        boolean matched=false;
        for(int i=0;i<bindingCount;i++) if(bindings[i].kind==InputBinding.Kind.MOUSE && bindings[i].mouse==button) {
            values[i]=down ? bindings[i].scale : 0; matched=true;
        }
        if(matched) recompute(); return matched;
    }
    private int touch(int id) { for(int i=0;i<touchIds.length;i++) if(touching[i] && touchIds[i]==id) return i; return -1; }
    @Override
    public boolean touchDown(TouchEvent event) {
        if(!enabled() || event.point()==null) return false;
        TouchPoint p=event.point(); int index=touch(p.id());
        if(index<0) for(int i=0;i<touchIds.length;i++) if(!touching[i]) { index=i; break; }
        if(index<0) return false; // Never evict another finger's binding.
        touching[index]=true; touchIds[index]=p.id(); touchX[index]=p.x(); touchY[index]=p.y();
        touchValues(); recompute(); return touchHit(p.x(),p.y());
    }
    @Override
    public boolean touchMoved(TouchEvent event) {
        if(!enabled() || event.point()==null) return false;
        TouchPoint p=event.point(); int index=touch(p.id()); if(index<0) return false;
        boolean wasHit=touchHit(touchX[index],touchY[index]);
        touchX[index]=p.x(); touchY[index]=p.y(); touchValues(); recompute(); return wasHit || touchHit(p.x(),p.y());
    }
    @Override
    public boolean touchUp(TouchEvent event) {
        if(!enabled() || event.point()==null) return false;
        int index=touch(event.point().id()); if(index<0) return false;
        touching[index]=false; touchValues(); recompute(); return true;
    }
    private boolean touchValues() {
        boolean matched=false;
        for(int i=0;i<bindingCount;i++) {
            InputBinding b=bindings[i]; if(b.kind!=InputBinding.Kind.TOUCH) continue;
            values[i]=0;
            for(int t=0;t<touchIds.length;t++) if(touching[t]) {
                float x=(float)touchX[t]/surfaceWidth, y=(float)touchY[t]/surfaceHeight;
                if(x>=b.x && x<b.x+b.width && y>=b.y && y<b.y+b.height) {
                    values[i]=b.scale; matched=true; break;
                }
            }
        }
        return matched;
    }
    private boolean touchHit(int px,int py) {
        float x=(float)px/surfaceWidth,y=(float)py/surfaceHeight;
        for(int i=0;i<bindingCount;i++) {
            InputBinding b=bindings[i];
            if(b.kind==InputBinding.Kind.TOUCH && x>=b.x && x<b.x+b.width && y>=b.y && y<b.y+b.height) return true;
        }
        return false;
    }
    /** Stable versioned text using enum names, never ordinals; store through application preferences/files. */
    public String exportBindings() {
        check(); StringBuilder output=new StringBuilder("libfdx-actions 1\n");
        for(int i=0;i<bindingCount;i++) output.append(actions[targets[i]].name()).append('\t').append(bindings[i].encoded()).append('\n');
        return output.toString();
    }
    /** Atomically replaces bindings for existing action names. Invalid/oversized input changes nothing. */
    public void importBindings(String encoded) {
        check();
        if(encoded==null || encoded.length()>131072) throw new IllegalArgumentException("Binding text exceeds 128KiB");
        String[] lines=encoded.replace("\r\n","\n").split("\n",-1);
        if(lines.length==0 || !lines[0].equals("libfdx-actions 1")) throw new IllegalArgumentException("Unsupported binding version");
        InputBinding[] parsed=new InputBinding[bindings.length]; int[] ids=new int[bindings.length]; int count=0;
        for(int i=1;i<lines.length;i++) {
            if(i==lines.length-1 && lines[i].isEmpty()) continue;
            String[] fields=lines[i].split("\t",-1);
            if(fields.length!=10 || count==bindings.length) throw new IllegalArgumentException("Invalid or excessive binding rows");
            InputAction action=find(fields[0]); if(action==null) throw new IllegalArgumentException("Unknown action: "+fields[0]);
            parsed[count]=InputBinding.decode(fields); ids[count++]=action.index;
        }
        System.arraycopy(parsed,0,bindings,0,bindings.length); System.arraycopy(ids,0,targets,0,targets.length);
        bindingCount=count; reset();
    }
    @Override
    public boolean isDisposed() { return disposed; }
    /** Detaches direct registration. If routed, remove this borrowed child from its router as well. */
    @Override
    public void dispose() {
        if(disposed) return; reset(); disposed=true; input.removeProcessor(this);
    }
}
