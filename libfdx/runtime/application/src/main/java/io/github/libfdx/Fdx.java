package io.github.libfdx;

import io.github.libfdx.application.Application;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Displays;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.Graphics;
import io.github.libfdx.input.Input;
import io.github.libfdx.net.Network;
import io.github.libfdx.storage.Storage;

/**
 * Defines the contract for fdx implementations.
 *
 * @author xpenatan
 */
public interface Fdx {
    /**
     * Returns the app.
     *
     * @return the app
     */
    Application app();

    /**
     * Returns the displays.
     *
     * @return the displays
     */
    Displays displays();

    /**
     * Returns the graphics.
     *
     * @return the graphics
     */
    Graphics graphics();

    /**
     * Returns the input.
     *
     * @return the input
     */
    Input input();

    /**
     * Returns the files.
     *
     * @return the files
     */
    FileSystem files();

    /**
     * Returns the storage.
     *
     * @return the storage
     */
    Storage storage();

    /**
     * Returns the network service, or {@code null} when the active backend does not provide one.
     *
     * @return the network service, or null
     */
    Network network();

    /**
     * Returns the logger.
     *
     * @return the logger
     */
    Logger logger();
}
