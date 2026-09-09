package io.github.libfdx.input;

/**
 * Stable application-owned scalar action. Bindings sum and clamp to [-1,1]. Down
 * means abs(value) reaches its threshold. Transitions latch until consumed/cleared,
 * including a complete press/release between simulation ticks. Multiple same-kind
 * transitions coalesce; this is state storage, not an input recording queue.
 */
public final class InputAction {
    final InputActions owner;
    final int index;
    private final String name;
    private final float threshold;
    private float value;
    private boolean down, pressed, released;

    InputAction(InputActions owner,int index,String name,float threshold) {
        this.owner=owner; this.index=index; this.name=name; this.threshold=threshold;
    }
    public String name() { return name; }
    public float threshold() { return threshold; }
    public float value() { return value; }
    public boolean down() { return down; }
    public boolean pressed() { return pressed; }
    public boolean released() { return released; }
    public boolean consumePressed() { boolean result=pressed; pressed=false; return result; }
    public boolean consumeReleased() { boolean result=released; released=false; return result; }
    public void clearTransitions() { pressed=released=false; }
    void value(float next) {
        value=Math.max(-1,Math.min(1,next)); boolean active=Math.abs(value)>=threshold;
        if(active && !down) pressed=true;
        if(!active && down) released=true;
        down=active;
    }
    void reset() { value=0; down=pressed=released=false; }
}
