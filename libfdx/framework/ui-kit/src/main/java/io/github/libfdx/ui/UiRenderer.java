package io.github.libfdx.ui;

import io.github.libfdx.core.Disposable;

/**
 * Defines the contract for ui renderer implementations.
 *
 * @author xpenatan
 */
public interface UiRenderer extends Disposable {
    /**
     * Renders the current content.
     *
     * @param root the root
     * @param node the node
     */
    void render(UiRoot root, UiNode node);
}
