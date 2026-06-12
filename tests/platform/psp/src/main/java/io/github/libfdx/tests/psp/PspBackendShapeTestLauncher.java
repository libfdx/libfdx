package io.github.libfdx.tests.psp;

import io.github.libfdx.backend.psp.PspApplicationBackend;
import io.github.libfdx.backend.psp.PspApplicationConfig;

/**
 * Launches the psp backend shape test entry point.
 *
 * @author xpenatan
 */
public final class PspBackendShapeTestLauncher {
    private PspBackendShapeTestLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        PspApplicationConfig config = new PspApplicationConfig()
                .title("libfdx PSP backend shape");
        new PspApplicationBackend().start(config, new PspBackendShapeTest(0L));
    }
}
