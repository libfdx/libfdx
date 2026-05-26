package io.github.libfdx.display;

public interface Displays {
    Display main();

    boolean supportsMultiple();

    Display create(DisplayConfig config);
}
