package io.github.libfdx.core;

public interface FdxTask<T> {
    T run() throws Exception;
}
