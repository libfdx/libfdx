package io.github.libfdx;

import io.github.libfdx.application.Application;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Displays;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.Graphics;
import io.github.libfdx.input.Input;

public final class DefaultFdx implements Fdx {
    private final Application app;
    private final Displays displays;
    private final Graphics graphics;
    private final Input input;
    private final FileSystem files;
    private final Logger logger;

    public DefaultFdx(Application app, Displays displays, Graphics graphics, FileSystem files, Logger logger) {
        this(app, displays, graphics, null, files, logger);
    }

    public DefaultFdx(Application app, Displays displays, Graphics graphics, Input input, FileSystem files, Logger logger) {
        if (app == null) {
            throw new FdxException("Application cannot be null");
        }
        if (displays == null) {
            throw new FdxException("Displays cannot be null");
        }
        if (graphics == null) {
            throw new FdxException("Graphics cannot be null");
        }
        if (logger == null) {
            throw new FdxException("Logger cannot be null");
        }
        this.app = app;
        this.displays = displays;
        this.graphics = graphics;
        this.input = input;
        this.files = files;
        this.logger = logger;
    }

    @Override
    public Application app() {
        return app;
    }

    @Override
    public Displays displays() {
        return displays;
    }

    @Override
    public Graphics graphics() {
        return graphics;
    }

    @Override
    public Input input() {
        return input;
    }

    @Override
    public FileSystem files() {
        return files;
    }

    @Override
    public Logger logger() {
        return logger;
    }
}
