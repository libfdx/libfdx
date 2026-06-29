package io.github.libfdx.validation.scenario;

/**
 * Defines the contract for scenario wait implementations.
 *
 * @author xpenatan
 */
public interface ScenarioWait {
    /**
     * Returns the name.
     *
     * @return the name
     */
    String name();

    /**
     * Runs the complete step.
     *
     * @param context the context
     * @param startMillis the start millis
     * @param startFrame the start frame
     * @return true if complete succeeds or is active; false otherwise
     */
    boolean complete(ScenarioContext context, long startMillis, int startFrame);

    /**
     * Returns the timeout millis.
     *
     * @return the timeout millis
     */
    long timeoutMillis();

    /**
     * Returns the timeout frames.
     *
     * @return the timeout frames
     */
    int timeoutFrames();

    /**
     * Returns the last observed value.
     *
     * @return the last observed value
     */
    String lastObservedValue();
}
