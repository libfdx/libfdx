package io.github.libfdx.graphics;

/**
 * Represents a store op.
 *
 * @author xpenatan
 */
public final class StoreOp {
    private static final StoreOp STORE = new StoreOp(true);
    private static final StoreOp DISCARD = new StoreOp(false);

    private final boolean store;

    private StoreOp(boolean store) {
        this.store = store;
    }

    /**
     * Creates a store op.
     *
     * @return a new store op
     */
    public static StoreOp store() {
        return STORE;
    }

    /**
     * Creates a store op.
     *
     * @return a new store op
     */
    public static StoreOp discard() {
        return DISCARD;
    }

    /**
     * Returns whether store is enabled or true.
     *
     * @return true if store is enabled or true; false otherwise
     */
    public boolean isStore() {
        return store;
    }
}
