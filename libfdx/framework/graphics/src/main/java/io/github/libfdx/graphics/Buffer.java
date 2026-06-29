package io.github.libfdx.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for buffer implementations.
 *
 * @author xpenatan
 */
public interface Buffer extends ProviderHandle, Disposable {
    /**
     * Returns the size.
     *
     * @return the size
     */
    int size();

    /**
     * Returns the usage.
     *
     * @return the usage
     */
    BufferUsage usage();
}
