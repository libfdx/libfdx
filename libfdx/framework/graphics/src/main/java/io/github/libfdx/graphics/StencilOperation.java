package io.github.libfdx.graphics;

/**
 * Stencil-buffer update operations.
 */
public enum StencilOperation {
    KEEP,
    ZERO,
    REPLACE,
    INVERT,
    INCREMENT_CLAMP,
    DECREMENT_CLAMP,
    INCREMENT_WRAP,
    DECREMENT_WRAP
}
