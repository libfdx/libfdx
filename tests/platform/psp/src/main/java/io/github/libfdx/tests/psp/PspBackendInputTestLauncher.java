package io.github.libfdx.tests.psp;

import io.github.libfdx.backend.psp.PspApplicationBackend;
import io.github.libfdx.backend.psp.PspApplicationConfig;

/**
 * Launches the psp backend input test entry point.
 *
 * @author xpenatan
 */
public final class PspBackendInputTestLauncher {
    private PspBackendInputTestLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        PspApplicationConfig config = new PspApplicationConfig()
                .title("libfdx PSP backend input");
        new PspApplicationBackend().start(config, new PspBackendInputTest(0L));
    }
}
