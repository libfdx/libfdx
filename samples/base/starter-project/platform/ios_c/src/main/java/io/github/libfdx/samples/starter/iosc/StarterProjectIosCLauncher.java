package io.github.libfdx.samples.starter.iosc;

import io.github.libfdx.backend.iosc.IosCApplicationBackend;
import io.github.libfdx.backend.iosc.IosCApplicationConfig;
import io.github.libfdx.backend.iosc.IosCOpenGLESProvider;
import io.github.libfdx.samples.starter.StarterProjectApplication;

/**
 * Launches the Starter Project iOS C OpenGL ES entry point.
 *
 * @author xpenatan
 */
public final class StarterProjectIosCLauncher {
    private StarterProjectIosCLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args command-line arguments supplied by the native launcher
     */
    public static void main(String[] args) {
        IosCApplicationConfig config = new IosCApplicationConfig()
                .title("libFDX Starter Project - GLES iOS C")
                .size(640, 480)
                .graphics(new IosCOpenGLESProvider());

        new IosCApplicationBackend().start(config, StarterProjectApplication.nativeAssets(""));
    }
}
