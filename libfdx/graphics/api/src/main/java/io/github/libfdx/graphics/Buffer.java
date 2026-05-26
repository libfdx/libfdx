package io.github.libfdx.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderHandle;

public interface Buffer extends ProviderHandle, Disposable {
    int size();

    BufferUsage usage();
}
