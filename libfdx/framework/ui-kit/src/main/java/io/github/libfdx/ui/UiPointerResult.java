package io.github.libfdx.ui;

/**
 * Describes how an interactive custom UI surface handled a pointer operation.
 *
 * @author xpenatan
 */
public enum UiPointerResult {
    /**
     * The surface did not handle the operation.
     */
    IGNORED,

    /**
     * The surface handled the operation without changing pointer capture.
     */
    HANDLED,

    /**
     * The surface handled the operation and captures this pointer.
     */
    CAPTURE,

    /**
     * The surface handled the operation and releases its pointer capture.
     */
    RELEASE
}
