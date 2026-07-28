package io.github.libfdx.samples.starter.iosc;

import io.github.libfdx.backend.iosc.IosCApplicationBackend;
import io.github.libfdx.backend.iosc.IosCApplicationConfig;
import io.github.libfdx.backend.iosc.IosCMetalProvider;
import io.github.libfdx.samples.starter.StarterProjectApplication;

/**
 * Launches the Starter Project iOS C Metal entry point.
 *
 * @author xpenatan
 */
public final class StarterProjectIosCMetalLauncher {
    private StarterProjectIosCMetalLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args command-line arguments supplied by the native launcher
     */
    public static void main(String[] args) {
        IosCApplicationConfig config = new IosCApplicationConfig()
                .title("libFDX Starter Project - Metal iOS C")
                .size(640, 480)
                .graphics(new IosCMetalProvider());

        new IosCApplicationBackend().start(config, StarterProjectApplication.nativeAssets(""));
    }
}
