package io.github.libfdx.tests;

/**
 * Defines the contract for test launch handler implementations.
 *
 * @author xpenatan
 */
public interface TestLaunchHandler {
    /**
     * Runs the launch step.
     *
     * @param testName the test name
     * @param graphicsName the graphics name
     * @return true if launch succeeds or is active; false otherwise
     */
    boolean launch(String testName, String graphicsName);

    /**
     * Returns whether this instance has active launch.
     *
     * @return true if this instance has active launch; false otherwise
     */
    default boolean hasActiveLaunch() {
        return false;
    }
}
