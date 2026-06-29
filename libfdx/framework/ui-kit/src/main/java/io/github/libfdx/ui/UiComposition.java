package io.github.libfdx.ui;

/**
 * Represents an ui composition.
 *
 * @author xpenatan
 */
final class UiComposition {
    static final ThreadLocal<UiRoot> CURRENT_ROOT = new ThreadLocal<UiRoot>();

    private UiComposition() {
    }
}
