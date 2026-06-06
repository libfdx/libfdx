package io.github.libfdx.runtime.core;

import io.github.libfdx.core.FdxException;

public final class RuntimeCoreException extends FdxException {
    public RuntimeCoreException(String message) {
        super(message);
    }

    public RuntimeCoreException(String message, Throwable cause) {
        super(message, cause);
    }
}