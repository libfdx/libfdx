package io.github.libfdx;

import io.github.libfdx.application.Application;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Displays;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.Graphics;
import io.github.libfdx.input.Input;
import io.github.libfdx.storage.DefaultStorage;
import io.github.libfdx.storage.Storage;

/**
 * Provides the default implementation of a fdx.
 *
 * @author xpenatan
 */
public final class DefaultFdx implements Fdx {
    private final Application app;
    private final Displays displays;
    private final Graphics graphics;
    private final Input input;
    private final FileSystem files;
    private final Storage storage;
    private final Logger logger;

    /**
     * Creates a default fdx.
     *
     * @param app the app
     * @param displays the displays
     * @param graphics the graphics context
     * @param files the files
     * @param logger the logger
     */
    public DefaultFdx(Application app, Displays displays, Graphics graphics, FileSystem files, Logger logger) {
        this(app, displays, graphics, null, files, logger);
    }

    /**
     * Creates a default fdx.
     *
     * @param app the app
     * @param displays the displays
     * @param graphics the graphics context
     * @param input the input
     * @param files the files
     * @param logger the logger
     */
    public DefaultFdx(Application app, Displays displays, Graphics graphics, Input input, FileSystem files, Logger logger) {
        this(app, displays, graphics, input, files, files != null ? new DefaultStorage(files) : null, logger);
    }

    /**
     * Creates a default fdx.
     *
     * @param app the app
     * @param displays the displays
     * @param graphics the graphics context
     * @param input the input
     * @param files the files
     * @param storage the storage
     * @param logger the logger
     */
    public DefaultFdx(Application app, Displays displays, Graphics graphics, Input input, FileSystem files,
            Storage storage, Logger logger) {
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
        this.storage = storage;
        this.logger = logger;
    }

    /**
     * Returns the app.
     *
     * @return the app
     */
    @Override
    public Application app() {
        return app;
    }

    /**
     * Returns the displays.
     *
     * @return the displays
     */
    @Override
    public Displays displays() {
        return displays;
    }

    /**
     * Returns the graphics.
     *
     * @return the graphics
     */
    @Override
    public Graphics graphics() {
        return graphics;
    }

    /**
     * Returns the input.
     *
     * @return the input
     */
    @Override
    public Input input() {
        return input;
    }

    /**
     * Returns the files.
     *
     * @return the files
     */
    @Override
    public FileSystem files() {
        return files;
    }

    /**
     * Returns the storage.
     *
     * @return the storage
     */
    @Override
    public Storage storage() {
        return storage;
    }

    /**
     * Returns the logger.
     *
     * @return the logger
     */
    @Override
    public Logger logger() {
        return logger;
    }
}
