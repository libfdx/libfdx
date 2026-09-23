package io.github.libfdx.backend.desktop;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.input.*;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.glfw.GLFWJoystickCallback;
import static org.lwjgl.glfw.GLFW.*;

/** Backend-owned GLFW mapping/polling. Native storage is reused; objects allocate on hotplug only. */
final class DesktopGamepads implements Disposable {
    private static final GamepadButton[] BUTTONS=GamepadButton.values();
    private static final int[] NATIVE_BUTTONS={GLFW_GAMEPAD_BUTTON_A,GLFW_GAMEPAD_BUTTON_B,GLFW_GAMEPAD_BUTTON_X,
            GLFW_GAMEPAD_BUTTON_Y,GLFW_GAMEPAD_BUTTON_LEFT_BUMPER,GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER,
            GLFW_GAMEPAD_BUTTON_LEFT_THUMB,GLFW_GAMEPAD_BUTTON_RIGHT_THUMB,GLFW_GAMEPAD_BUTTON_BACK,
            GLFW_GAMEPAD_BUTTON_START,GLFW_GAMEPAD_BUTTON_GUIDE,GLFW_GAMEPAD_BUTTON_DPAD_UP,
            GLFW_GAMEPAD_BUTTON_DPAD_DOWN,GLFW_GAMEPAD_BUTTON_DPAD_LEFT,GLFW_GAMEPAD_BUTTON_DPAD_RIGHT};
    private static final GamepadAxis[] AXES=GamepadAxis.values();
    private static final int[] NATIVE_AXES={GLFW_GAMEPAD_AXIS_LEFT_X,GLFW_GAMEPAD_AXIS_LEFT_Y,
            GLFW_GAMEPAD_AXIS_RIGHT_X,GLFW_GAMEPAD_AXIS_RIGHT_Y,GLFW_GAMEPAD_AXIS_LEFT_TRIGGER,GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER};
    final DefaultGamepads registry=new DefaultGamepads();
    private final DefaultGamepad[] slots=new DefaultGamepad[GLFW_JOYSTICK_LAST+1];
    private final boolean[] changed=new boolean[slots.length];
    private GLFWGamepadState state;
    private GLFWJoystickCallback callback;
    private boolean disposed;

    void update() {
        if(disposed) return;
        if(state==null) {
            state=GLFWGamepadState.calloc();
            callback=GLFWJoystickCallback.create((index,event)-> { if(index>=0 && index<changed.length) changed[index]=true; });
            glfwSetJoystickCallback(callback);
        }
        for(int i=0;i<slots.length;i++) {
            boolean ready=glfwJoystickIsGamepad(i) && glfwGetGamepadState(i,state);
            if(slots[i]!=null && (!ready || changed[i])) disconnect(i);
            changed[i]=false;
            if(!ready) continue;
            if(slots[i]==null) {
                String id=glfwGetJoystickGUID(i), name=glfwGetGamepadName(i);
                DefaultGamepad gamepad=new DefaultGamepad(id==null ? "glfw:"+i : id,name==null ? "Gamepad" : name,i,GamepadMapping.STANDARD);
                copy(state,gamepad.state()); slots[i]=gamepad; registry.connect(gamepad);
            } else copy(state,slots[i].state());
        }
    }
    static void copy(GLFWGamepadState source,GamepadState target) {
        for(int i=0;i<BUTTONS.length;i++) target.button(BUTTONS[i],source.buttons(NATIVE_BUTTONS[i])==GLFW_PRESS);
        for(int i=0;i<AXES.length;i++) {
            float value=source.axes(NATIVE_AXES[i]);
            if(!Float.isFinite(value)) value=i<4 ? 0 : -1;
            value=Math.max(-1,Math.min(1,value));
            target.axis(AXES[i],i<4 ? value : (value+1)*.5f);
        }
    }
    private void disconnect(int index) {
        DefaultGamepad removed=slots[index]; slots[index]=null;
        removed.connected(false);
        for(GamepadAxis axis:AXES) removed.state().axis(axis,0);
        for(GamepadButton button:BUTTONS) removed.state().button(button,false);
        registry.disconnect(removed);
    }
    @Override
    public boolean isDisposed() { return disposed; }
    @Override
    public void dispose() {
        if(disposed) return; disposed=true;
        Throwable failure=null;
        try {
            for(int i=0;i<slots.length;i++) if(slots[i]!=null) {
                try { disconnect(i); } catch (RuntimeException | Error error) {
                    if(failure==null) failure=error; else if(failure!=error) failure.addSuppressed(error);
                }
            }
        }
        finally {
            if(callback!=null) { glfwSetJoystickCallback(null); callback.free(); callback=null; }
            if(state!=null) { state.free(); state=null; }
        }
        if(failure instanceof RuntimeException runtime) throw runtime;
        if(failure instanceof Error error) throw error;
    }
}
