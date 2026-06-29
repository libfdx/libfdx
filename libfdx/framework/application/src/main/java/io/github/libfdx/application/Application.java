package io.github.libfdx.application;

import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for application implementations.
 *
 * @author xpenatan
 */
public interface Application extends ProviderHandle {
    /**
     * Returns the lifecycle.
     *
     * @return the lifecycle
     */
    ApplicationLifecycle lifecycle();

    /**
     * Returns the delta time.
     *
     * @return the delta time
     */
    float deltaTime();

    /**
     * Returns the frame ID.
     *
     * @return the frame ID
     */
    long frameId();

    /**
     * Runs the request exit step.
     */
    void requestExit();
}
