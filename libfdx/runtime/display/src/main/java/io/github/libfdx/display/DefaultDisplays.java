package io.github.libfdx.display;

import io.github.libfdx.core.FdxException;

public final class DefaultDisplays implements Displays {
    private final Display main;

    public DefaultDisplays(Display main) {
        if (main == null) {
            throw new FdxException("Main display cannot be null");
        }
        this.main = main;
    }

    @Override
    public Display main() {
        return main;
    }

    @Override
    public boolean supportsMultiple() {
        return false;
    }

    @Override
    public Display create(DisplayConfig config) {
        throw new FdxException("This backend does not support creating additional displays");
    }
}
