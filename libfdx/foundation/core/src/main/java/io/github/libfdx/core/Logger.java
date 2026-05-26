package io.github.libfdx.core;

public interface Logger extends FdxService {
    void debug(String message);

    void info(String message);

    void warn(String message);

    void error(String message);

    void error(String message, Throwable error);
}
