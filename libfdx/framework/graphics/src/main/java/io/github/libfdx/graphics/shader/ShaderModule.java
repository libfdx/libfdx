package io.github.libfdx.graphics.shader;

import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
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

    /**
     * Returns the immutable interface reflection captured when this module was created.
     *
     * @return the reflection, or the empty incomplete reflection for compatibility implementations
     */
    default ShaderReflection reflection() {
        return ShaderReflection.empty();
    }
}
