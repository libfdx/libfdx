package io.github.libfdx.files;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderHandle;

public interface FileWatch extends ProviderHandle, Disposable {
    FileHandle file();

    void addListener(FileWatchListener listener);

    void removeListener(FileWatchListener listener);
}
