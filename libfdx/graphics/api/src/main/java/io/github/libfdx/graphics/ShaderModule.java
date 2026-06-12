package io.github.libfdx.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for shader module implementations.
 *
 * @author xpenatan
 */
public interface ShaderModule extends ProviderHandle, Disposable {
    /**
     * Returns the language.
     *
     * @return the language
     */
    ShaderLanguage language();
}
