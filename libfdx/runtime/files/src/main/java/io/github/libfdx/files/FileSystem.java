package io.github.libfdx.files;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderHandle;

public interface FileSystem extends ProviderHandle {
    FileHandle classpath(String path);

    FileHandle internal(String path);

    FileHandle local(String path);

    FileHandle external(String path);

    FileHandle cache(String path);

    FileHandle temp(String prefix, String suffix);

    FdxFuture<FileWatch> watch(FileHandle file);
}
