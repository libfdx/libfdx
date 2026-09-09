package io.github.libfdx.input;

/**
 * Opt-in ordered routing. Register this processor with Input, then put UI before
 * gameplay inside this router. A handled event stops traversal, except key,
 * pointer and touch releases, which visit every child to prevent stuck state.
 * DefaultInput's independent processor broadcast remains unchanged. Children are
 * borrowed. Configuration is forbidden while dispatching; calls belong to the
 * application thread. Poll-driven gamepad contexts need explicit enable/disable.
 */
public final class InputRouter extends InputAdapter {
    private final InputProcessor[] processors;
    private int count, dispatchDepth;
    public InputRouter() { this(16); }
    public InputRouter(int capacity) {
        if(capacity<1 || capacity>256) throw new IllegalArgumentException("Router capacity must be 1..256");
        processors=new InputProcessor[capacity];
    }
    public InputRouter add(InputProcessor processor) {
        mutable(); if(processor==null || processor==this) throw new IllegalArgumentException("Child processor required");
        if(processor instanceof InputRouter router && router.reaches(this,0)) {
            throw new IllegalArgumentException("Input router cycle");
        }
        for(int i=0;i<count;i++) if(processors[i]==processor) return this;
        if(count==processors.length) throw new IllegalStateException("Input router is full");
        processors[count++]=processor; return this;
    }
    public void remove(InputProcessor processor) {
        mutable();
        for(int i=0;i<count;i++) if(processors[i]==processor) {
            System.arraycopy(processors,i+1,processors,i,count-i-1); processors[--count]=null; return;
        }
    }
    private void mutable() { if(dispatchDepth!=0) throw new IllegalStateException("Cannot change an active input route"); }
    private boolean reaches(InputRouter target,int depth) {
        if(this==target) return true;
        if(depth>=32) throw new IllegalArgumentException("Input router nesting exceeds 32");
        for(int i=0;i<count;i++) if(processors[i] instanceof InputRouter router && router.reaches(target,depth+1)) return true;
        return false;
    }
    private boolean route(int kind,InputEvent event,boolean release) {
        boolean handled=false; dispatchDepth++;
        try {
            for(int i=0;i<count;i++) {
                InputProcessor p=processors[i];
                boolean result=switch(kind) {
                    case 0 -> p.keyDown((KeyEvent)event); case 1 -> p.keyUp((KeyEvent)event);
                    case 2 -> p.pointerDown((PointerEvent)event); case 3 -> p.pointerUp((PointerEvent)event);
                    case 4 -> p.pointerMoved((PointerEvent)event); case 5 -> p.scrolled((PointerEvent)event);
                    case 6 -> p.touchDown((TouchEvent)event); case 7 -> p.touchUp((TouchEvent)event);
                    case 8 -> p.touchMoved((TouchEvent)event); case 9 -> p.textInput((TextInputEvent)event);
                    default -> throw new AssertionError();
                };
                handled|=result; if(result && !release) break;
            }
            return handled;
        } finally { dispatchDepth--; }
    }
    @Override public boolean keyDown(KeyEvent event) { return route(0,event,false); }
    @Override public boolean keyUp(KeyEvent event) { return route(1,event,true); }
    @Override public boolean pointerDown(PointerEvent event) { return route(2,event,false); }
    @Override public boolean pointerUp(PointerEvent event) { return route(3,event,true); }
    @Override public boolean pointerMoved(PointerEvent event) { return route(4,event,false); }
    @Override public boolean scrolled(PointerEvent event) { return route(5,event,false); }
    @Override public boolean touchDown(TouchEvent event) { return route(6,event,false); }
    @Override public boolean touchUp(TouchEvent event) { return route(7,event,true); }
    @Override public boolean touchMoved(TouchEvent event) { return route(8,event,false); }
    @Override public boolean textInput(TextInputEvent event) { return route(9,event,false); }
}
