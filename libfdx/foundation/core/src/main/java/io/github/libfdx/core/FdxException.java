package io.github.libfdx.core;

public class FdxException extends RuntimeException {
    public FdxException(String message) {
        super(message);
    }

    public FdxException(String message, Throwable cause) {
        super(message, cause);
    }
}
