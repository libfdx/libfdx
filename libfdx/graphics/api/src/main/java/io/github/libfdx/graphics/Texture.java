package io.github.libfdx.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderHandle;

public interface Texture extends ProviderHandle, Disposable {
    int width();

    int height();

    TextureFormat format();

    TextureUsage usage();
}
