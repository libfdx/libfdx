package io.github.libfdx.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for render pipeline implementations.
 *
 * @author xpenatan
 */
public interface RenderPipeline extends ProviderHandle, Disposable {
    /**
     * Returns the exact render-target layout this pipeline was created for.
     *
     * @return immutable target layout
     */
    RenderTargetLayout targetLayout();
}
