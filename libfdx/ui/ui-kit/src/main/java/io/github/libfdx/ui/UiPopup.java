package io.github.libfdx.ui;

/**
 * Represents an ui popup.
 *
 * @author xpenatan
 */
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

    /**
     * Creates an UI popup.
     *
     * @param id the identifier
     * @return a new UI popup
     */
    public static UiPopup popup(String id) {
        return new UiPopup(id, UiAlign.START, UiAlign.START, true, false);
    }

    /**
     * Sets the align and returns this UI popup.
     *
     * @param horizontalAlign the horizontal align
     * @param verticalAlign the vertical align
     * @return this UI popup for chaining
     */
    public UiPopup align(UiAlign horizontalAlign, UiAlign verticalAlign) {
        return new UiPopup(id, horizontalAlign, verticalAlign, dismissOnOutsidePress, blockingInput);
    }

    /**
     * Sets the dismiss on outside press and returns this UI popup.
     *
     * @param dismissOnOutsidePress the dismiss on outside press
     * @return this UI popup for chaining
     */
    public UiPopup dismissOnOutsidePress(boolean dismissOnOutsidePress) {
        return new UiPopup(id, horizontalAlign, verticalAlign, dismissOnOutsidePress, blockingInput);
    }

    /**
     * Sets the blocking input and returns this UI popup.
     *
     * @param blockingInput the blocking input
     * @return this UI popup for chaining
     */
    public UiPopup blockingInput(boolean blockingInput) {
        return new UiPopup(id, horizontalAlign, verticalAlign, dismissOnOutsidePress, blockingInput);
    }

    /**
     * Returns the ID.
     *
     * @return the ID
     */
    public String id() {
        return id;
    }

    /**
     * Returns the horizontal align.
     *
     * @return the horizontal align
     */
    public UiAlign horizontalAlign() {
        return horizontalAlign;
    }

    /**
     * Returns the vertical align.
     *
     * @return the vertical align
     */
    public UiAlign verticalAlign() {
        return verticalAlign;
    }

    /**
     * Returns the dismiss on outside press.
     *
     * @return true if dismiss on outside press succeeds or is active; false otherwise
     */
    public boolean dismissOnOutsidePress() {
        return dismissOnOutsidePress;
    }

    /**
     * Returns the blocking input.
     *
     * @return true if blocking input succeeds or is active; false otherwise
     */
    public boolean blockingInput() {
        return blockingInput;
    }
}
