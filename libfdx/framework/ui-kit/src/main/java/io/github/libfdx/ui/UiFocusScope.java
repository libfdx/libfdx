package io.github.libfdx.ui;

/**
 * Represents an ui focus scope.
 *
 * @author xpenatan
 */
public final class UiFocusScope {
    private final String id;
    private final boolean wraps;
    private final boolean restoresFocus;

    private UiFocusScope(String id, boolean wraps, boolean restoresFocus) {
        this.id = id;
        this.wraps = wraps;
        this.restoresFocus = restoresFocus;
    }

    /**
     * Creates an UI focus scope.
     *
     * @param id the identifier
     * @return a new UI focus scope
     */
    public static UiFocusScope scope(String id) {
        return new UiFocusScope(id, true, true);
    }

    /**
     * Sets the wraps and returns this UI focus scope.
     *
     * @param wraps the wraps
     * @return this UI focus scope for chaining
     */
    public UiFocusScope wraps(boolean wraps) {
        return new UiFocusScope(id, wraps, restoresFocus);
    }

    /**
     * Sets the restores focus and returns this UI focus scope.
     *
     * @param restoresFocus the restores focus
     * @return this UI focus scope for chaining
     */
    public UiFocusScope restoresFocus(boolean restoresFocus) {
        return new UiFocusScope(id, wraps, restoresFocus);
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
     * Returns the wraps.
     *
     * @return true if wraps succeeds or is active; false otherwise
     */
    public boolean wraps() {
        return wraps;
    }

    /**
     * Returns the restores focus.
     *
     * @return true if restores focus succeeds or is active; false otherwise
     */
    public boolean restoresFocus() {
        return restoresFocus;
    }
}
