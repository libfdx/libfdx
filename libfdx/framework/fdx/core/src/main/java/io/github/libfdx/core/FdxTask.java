package io.github.libfdx.core;

/**
 * Defines the contract for fdx task implementations.
 *
 * @param <T> the value type
 *
 * @author xpenatan
 */
public interface FdxTask<T> {
    /**
     * Returns the run.
     *
     * @return the run
     * @throws Exception if the operation cannot be completed
     */
    T run() throws Exception;
}
