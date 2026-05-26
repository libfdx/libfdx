package io.github.libfdx.graphics;

public interface Graphics {
    GraphicsContext main();

    boolean supportsMultiple();

    GraphicsContext create(GraphicsConfig config);
}
