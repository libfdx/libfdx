package io.github.libfdx.ui;

/**
 * Represents an ui layer.
 *
 * @author xpenatan
 */
public final class UiLayer {
    private final String name;
    private final int order;
    private final boolean blocksInputBehind;

    private UiLayer(String name, int order, boolean blocksInputBehind) {
        this.name = name;
        this.order = order;
        this.blocksInputBehind = blocksInputBehind;
    }

    /**
     * Creates an UI layer.
     *
     * @param name the name
     * @param order the order
     * @return a new UI layer
     */
    public static UiLayer layer(String name, int order) {
        return new UiLayer(name, order, false);
    }

    /**
     * Sets the blocks input behind and returns this UI layer.
     *
     * @param blocksInputBehind the blocks input behind
     * @return this UI layer for chaining
     */
    public UiLayer blocksInputBehind(boolean blocksInputBehind) {
        return new UiLayer(name, order, blocksInputBehind);
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the order.
     *
     * @return the order
     */
    public int order() {
        return order;
    }

    /**
     * Returns the blocks input behind.
     *
     * @return true if blocks input behind succeeds or is active; false otherwise
     */
    public boolean blocksInputBehind() {
        return blocksInputBehind;
    }
}
