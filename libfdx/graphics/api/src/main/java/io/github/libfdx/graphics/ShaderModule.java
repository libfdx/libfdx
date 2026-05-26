package io.github.libfdx.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderHandle;

public interface ShaderModule extends ProviderHandle, Disposable {
    ShaderLanguage language();
}
