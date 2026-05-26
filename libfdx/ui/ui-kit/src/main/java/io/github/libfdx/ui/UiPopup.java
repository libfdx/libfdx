package io.github.libfdx.ui;

public final class UiPopup {
    private final String id;
    private final UiAlign horizontalAlign;
    private final UiAlign verticalAlign;
    private final boolean dismissOnOutsidePress;
    private final boolean blockingInput;

    private UiPopup(String id, UiAlign horizontalAlign, UiAlign verticalAlign, boolean dismissOnOutsidePress,
            boolean blockingInput) {
        this.id = id;
        this.horizontalAlign = horizontalAlign != null ? horizontalAlign : UiAlign.START;
        this.verticalAlign = verticalAlign != null ? verticalAlign : UiAlign.START;
        this.dismissOnOutsidePress = dismissOnOutsidePress;
        this.blockingInput = blockingInput;
    }

    public static UiPopup popup(String id) {
        return new UiPopup(id, UiAlign.START, UiAlign.START, true, false);
    }

    public UiPopup align(UiAlign horizontalAlign, UiAlign verticalAlign) {
        return new UiPopup(id, horizontalAlign, verticalAlign, dismissOnOutsidePress, blockingInput);
    }

    public UiPopup dismissOnOutsidePress(boolean dismissOnOutsidePress) {
        return new UiPopup(id, horizontalAlign, verticalAlign, dismissOnOutsidePress, blockingInput);
    }

    public UiPopup blockingInput(boolean blockingInput) {
        return new UiPopup(id, horizontalAlign, verticalAlign, dismissOnOutsidePress, blockingInput);
    }

    public String id() {
        return id;
    }

    public UiAlign horizontalAlign() {
        return horizontalAlign;
    }

    public UiAlign verticalAlign() {
        return verticalAlign;
    }

    public boolean dismissOnOutsidePress() {
        return dismissOnOutsidePress;
    }

    public boolean blockingInput() {
        return blockingInput;
    }
}
