package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

public final class DefaultGraphics implements Graphics {
    private final GraphicsContext main;

    public DefaultGraphics(GraphicsContext main) {
        if (main == null) {
            throw new FdxException("Main graphics context cannot be null");
        }
        this.main = main;
    }

    @Override
    public GraphicsContext main() {
        return main;
    }

    @Override
    public boolean supportsMultiple() {
        return false;
    }

    @Override
    public GraphicsContext create(GraphicsConfig config) {
        throw new FdxException("This backend does not support creating additional graphics contexts");
    }
}
