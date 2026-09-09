package io.github.libfdx.input;

/** Immutable typed binding. Configuration allocates; event handling/polling reuse storage. */
public final class InputBinding {
    public enum Kind { KEY, MOUSE, GAMEPAD_BUTTON, GAMEPAD_AXIS, TOUCH }
    final Kind kind;
    final Key key;
    final MouseButton mouse;
    final GamepadButton button;
    final GamepadAxis axis;
    final int gamepad;
    final float scale, deadZone, x, y, width, height;

    private InputBinding(Kind kind,Key key,MouseButton mouse,GamepadButton button,GamepadAxis axis,
            int gamepad,float scale,float deadZone,float x,float y,float width,float height) {
        if(!Float.isFinite(scale) || scale==0 || Math.abs(scale)>1 || gamepad < -1 || gamepad>255
                || !Float.isFinite(deadZone) || deadZone<0 || deadZone>=1
                || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)) {
            throw new IllegalArgumentException("Invalid binding parameters");
        }
        if(kind==Kind.TOUCH && (x<0 || y<0 || x>=1 || y>=1 || width<=0 || height<=0
                || (double)x+width>1.000001 || (double)y+height>1.000001)) {
            throw new IllegalArgumentException("Touch bounds must lie inside normalized [0,1] surface");
        }
        this.kind=kind; this.key=key; this.mouse=mouse; this.button=button; this.axis=axis;
        this.gamepad=gamepad; this.scale=scale; this.deadZone=deadZone;
        this.x=x; this.y=y;
        // Normalize roundoff at the surface edge consistently on JVM and JavaScript.
        this.width=kind==Kind.TOUCH ? Math.min(width,1-x) : width;
        this.height=kind==Kind.TOUCH ? Math.min(height,1-y) : height;
    }
    public static InputBinding key(Key key) { return key(key,1); }
    public static InputBinding key(Key key,float scale) {
        if(key==null || key==Key.UNKNOWN) throw new IllegalArgumentException("Known key required");
        return new InputBinding(Kind.KEY,key,null,null,null,-1,scale,0,0,0,0,0);
    }
    public static InputBinding mouse(MouseButton button) { return mouse(button,1); }
    public static InputBinding mouse(MouseButton button,float scale) {
        if(button==null || button==MouseButton.UNKNOWN) throw new IllegalArgumentException("Known mouse button required");
        return new InputBinding(Kind.MOUSE,null,button,null,null,-1,scale,0,0,0,0,0);
    }
    /** Gamepad index -1 selects the strongest connected mapped control (ties keep connection order). */
    public static InputBinding button(int gamepad,GamepadButton button,float scale) {
        if(button==null) throw new IllegalArgumentException("Gamepad button required");
        return new InputBinding(Kind.GAMEPAD_BUTTON,null,null,button,null,gamepad,scale,0,0,0,0,0);
    }
    /** Axial dead zone; the remaining magnitude is rescaled to [0,1] before multiplying scale. */
    public static InputBinding axis(int gamepad,GamepadAxis axis,float deadZone,float scale) {
        if(axis==null) throw new IllegalArgumentException("Gamepad axis required");
        return new InputBinding(Kind.GAMEPAD_AXIS,null,null,null,axis,gamepad,scale,deadZone,0,0,0,0);
    }
    /** Normalized window coordinates, top-left origin. Right/bottom edges are exclusive. */
    public static InputBinding touch(float x,float y,float width,float height,float scale) {
        return new InputBinding(Kind.TOUCH,null,null,null,null,-1,scale,0,x,y,width,height);
    }
    public Kind kind() { return kind; }
    public Key key() { return key; }
    public MouseButton mouse() { return mouse; }
    public GamepadButton button() { return button; }
    public GamepadAxis axis() { return axis; }
    public int gamepad() { return gamepad; }
    public float scale() { return scale; }
    public float deadZone() { return deadZone; }
    public float x() { return x; }
    public float y() { return y; }
    public float width() { return width; }
    public float height() { return height; }
    String control() {
        return switch(kind) {
            case KEY -> key.name(); case MOUSE -> mouse.name(); case GAMEPAD_BUTTON -> button.name();
            case GAMEPAD_AXIS -> axis.name(); case TOUCH -> "-";
        };
    }
    String encoded() {
        return kind+"\t"+control()+"\t"+gamepad+"\t"+scale+"\t"+deadZone+"\t"+x+"\t"+y+"\t"+width+"\t"+height;
    }
    static InputBinding decode(String[] fields) {
        Kind kind=Kind.valueOf(fields[1]); int pad=Integer.parseInt(fields[3]);
        float scale=Float.parseFloat(fields[4]), dead=Float.parseFloat(fields[5]);
        float x=Float.parseFloat(fields[6]), y=Float.parseFloat(fields[7]);
        float width=Float.parseFloat(fields[8]), height=Float.parseFloat(fields[9]);
        if(kind!=Kind.GAMEPAD_AXIS && dead!=0 || kind!=Kind.TOUCH && (x!=0 || y!=0 || width!=0 || height!=0)
                || kind!=Kind.GAMEPAD_AXIS && kind!=Kind.GAMEPAD_BUTTON && pad!=-1) {
            throw new IllegalArgumentException("Unexpected fields for binding kind");
        }
        return switch(kind) {
            case KEY -> key(Key.valueOf(fields[2]),scale);
            case MOUSE -> mouse(MouseButton.valueOf(fields[2]),scale);
            case GAMEPAD_BUTTON -> button(pad,GamepadButton.valueOf(fields[2]),scale);
            case GAMEPAD_AXIS -> axis(pad,GamepadAxis.valueOf(fields[2]),dead,scale);
            case TOUCH -> {
                if(!fields[2].equals("-")) throw new IllegalArgumentException("Unexpected touch control");
                yield touch(x,y,width,height,scale);
            }
        };
    }
}
