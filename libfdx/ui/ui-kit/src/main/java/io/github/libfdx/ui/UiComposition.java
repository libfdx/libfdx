package io.github.libfdx.ui;

final class UiComposition {
    static final ThreadLocal<UiRoot> CURRENT_ROOT = new ThreadLocal<UiRoot>();

    private UiComposition() {
    }
}
