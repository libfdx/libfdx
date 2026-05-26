package io.github.libfdx.tests;

public interface TestLaunchHandler {
    boolean launch(String testName, String graphicsName);

    default boolean hasActiveLaunch() {
        return false;
    }
}
