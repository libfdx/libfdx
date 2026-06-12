package io.github.libfdx.tests.psp;

import io.github.libfdx.backend.psp.PspApplicationBackend;
import io.github.libfdx.backend.psp.PspApplicationConfig;

/**
 * Launches the psp backend clear test entry point.
 *
 * @author xpenatan
 */
public final class PspBackendClearTestLauncher {
    private PspBackendClearTestLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        PspApplicationConfig config = new PspApplicationConfig()
                .title("libfdx PSP backend clear");
        new PspApplicationBackend().start(config, new PspBackendClearTest(0L));
    }
}
