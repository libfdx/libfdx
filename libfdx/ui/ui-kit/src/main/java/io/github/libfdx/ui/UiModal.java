package io.github.libfdx.ui;

/**
 * Represents an ui modal.
 *
 * @author xpenatan
 */
public final class UiModal {
    private final String id;
    private final UiColor scrimColor;
    private final boolean dismissOnEscape;

    private UiModal(String id, UiColor scrimColor, boolean dismissOnEscape) {
        this.id = id;
        this.scrimColor = scrimColor != null ? scrimColor : UiColor.rgba(0.0f, 0.0f, 0.0f, 0.5f);
        this.dismissOnEscape = dismissOnEscape;
    }

    /**
     * Creates an UI modal.
     *
     * @param id the identifier
     * @return a new UI modal
     */
    public static UiModal modal(String id) {
        return new UiModal(id, UiColor.rgba(0.0f, 0.0f, 0.0f, 0.5f), true);
    }

    /**
     * Sets the scrim and returns this UI modal.
     *
     * @param scrimColor the scrim color
     * @return this UI modal for chaining
     */
    public UiModal scrim(UiColor scrimColor) {
        return new UiModal(id, scrimColor, dismissOnEscape);
    }

    /**
     * Sets the dismiss on escape and returns this UI modal.
     *
     * @param dismissOnEscape the dismiss on escape
     * @return this UI modal for chaining
     */
    public UiModal dismissOnEscape(boolean dismissOnEscape) {
        return new UiModal(id, scrimColor, dismissOnEscape);
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
     * Returns the scrim color.
     *
     * @return the scrim color
     */
    public UiColor scrimColor() {
        return scrimColor;
    }

    /**
     * Returns the dismiss on escape.
     *
     * @return true if dismiss on escape succeeds or is active; false otherwise
     */
    public boolean dismissOnEscape() {
        return dismissOnEscape;
    }
}
