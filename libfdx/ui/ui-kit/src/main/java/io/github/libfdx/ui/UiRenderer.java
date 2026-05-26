package io.github.libfdx.ui;

import io.github.libfdx.core.Disposable;

public interface UiRenderer extends Disposable {
    void render(UiRoot root, UiNode node);
}
