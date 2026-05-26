package io.github.libfdx.graphics;

public final class StoreOp {
    private static final StoreOp STORE = new StoreOp(true);
    private static final StoreOp DISCARD = new StoreOp(false);

    private final boolean store;

    private StoreOp(boolean store) {
        this.store = store;
    }

    public static StoreOp store() {
        return STORE;
    }

    public static StoreOp discard() {
        return DISCARD;
    }

    public boolean isStore() {
        return store;
    }
}
