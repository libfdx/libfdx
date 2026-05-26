package io.github.libfdx.ui;

public final class UiLayer {
    private final String name;
    private final int order;
    private final boolean blocksInputBehind;

    private UiLayer(String name, int order, boolean blocksInputBehind) {
        this.name = name;
        this.order = order;
        this.blocksInputBehind = blocksInputBehind;
    }

    public static UiLayer layer(String name, int order) {
        return new UiLayer(name, order, false);
    }

    public UiLayer blocksInputBehind(boolean blocksInputBehind) {
        return new UiLayer(name, order, blocksInputBehind);
    }

    public String name() {
        return name;
    }

    public int order() {
        return order;
    }

    public boolean blocksInputBehind() {
        return blocksInputBehind;
    }
}
