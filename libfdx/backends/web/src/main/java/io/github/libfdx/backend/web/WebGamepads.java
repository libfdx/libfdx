package io.github.libfdx.backend.web;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.input.*;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;

/** Backend-owned standard Gamepad API polling. Unknown mappings remain unexposed. */
final class WebGamepads implements Disposable {
    private static final GamepadButton[] BUTTONS=GamepadButton.values();
    private static final int[] WEB_BUTTONS={0,1,2,3,4,5,10,11,8,9,16,12,13,14,15};
    private static final GamepadAxis[] AXES=GamepadAxis.values();
    final DefaultGamepads registry=new DefaultGamepads();
    private final DefaultGamepad[] slots=new DefaultGamepad[16];
    // Keep browser references in a JS array so null stores use the same representation in JS and Wasm GC.
    private final JSArray<JSObject> identities=new JSArray<>(16);
    private boolean disposed;

    void update() {
        if(disposed) return;
        JSObject snapshot=snapshot();
        for(int i=0;i<slots.length;i++) {
            JSObject source=gamepad(snapshot,i);
            if(slots[i]!=null && (source==null || !same(source,identities.get(i)))) disconnect(i);
            if(source==null) continue;
            if(slots[i]==null) {
                DefaultGamepad gamepad=new DefaultGamepad(id(source),id(source),i,GamepadMapping.STANDARD);
                copy(source,gamepad.state()); identities.set(i,identity(source)); slots[i]=gamepad; registry.connect(gamepad);
            } else copy(source,slots[i].state());
        }
    }
    private static void copy(JSObject source,GamepadState target) {
        for(int i=0;i<BUTTONS.length;i++) target.button(BUTTONS[i],pressed(source,WEB_BUTTONS[i]));
        for(int i=0;i<AXES.length;i++) {
            float value=i<4 ? axis(source,i) : trigger(source,i+2);
            if(!Float.isFinite(value)) value=0;
            target.axis(AXES[i],Math.max(i<4 ? -1 : 0,Math.min(1,value)));
        }
    }
    private void disconnect(int index) {
        DefaultGamepad removed=slots[index]; slots[index]=null; identities.set(index,null); removed.connected(false);
        for(GamepadAxis axis:AXES) removed.state().axis(axis,0);
        for(GamepadButton button:BUTTONS) removed.state().button(button,false);
        registry.disconnect(removed);
    }
    @Override
    public boolean isDisposed() { return disposed; }
    @Override
    public void dispose() {
        if(disposed) return; disposed=true; Throwable failure=null;
        for(int i=0;i<slots.length;i++) if(slots[i]!=null) {
            try { disconnect(i); } catch (RuntimeException | Error error) {
                if(failure==null) failure=error; else if(failure!=error) failure.addSuppressed(error);
            }
        }
        if(failure instanceof RuntimeException runtime) throw runtime;
        if(failure instanceof Error error) throw error;
    }
    @JSBody(script="return typeof navigator.getGamepads === 'function' && globalThis.isSecureContext;")
    static native boolean available();
    @JSBody(script="try { return navigator.getGamepads ? navigator.getGamepads() : null; } catch(error) { return null; }")
    private static native JSObject snapshot();
    @JSBody(params={"pads","index"},script="var p=pads && pads[index]; return p && p.connected && p.mapping==='standard' ? p : null;")
    private static native JSObject gamepad(JSObject pads,int index);
    @JSBody(params={"pad","identity"},script="return pad.id===identity.id && pad.mapping===identity.mapping;")
    private static native boolean same(JSObject pad,JSObject identity);
    @JSBody(params="pad",script="return {id:pad.id,mapping:pad.mapping};")
    private static native JSObject identity(JSObject pad);
    @JSBody(params="pad",script="return pad.id || 'Browser gamepad';")
    private static native String id(JSObject pad);
    @JSBody(params={"pad","index"},script="return !!(pad.buttons[index] && pad.buttons[index].pressed);")
    private static native boolean pressed(JSObject pad,int index);
    @JSBody(params={"pad","index"},script="return index<pad.axes.length ? pad.axes[index] : 0;")
    private static native float axis(JSObject pad,int index);
    @JSBody(params={"pad","index"},script="return pad.buttons[index] ? pad.buttons[index].value : 0;")
    private static native float trigger(JSObject pad,int index);
}
