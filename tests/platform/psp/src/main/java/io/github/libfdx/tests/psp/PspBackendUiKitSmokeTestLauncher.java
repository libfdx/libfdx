package io.github.libfdx.tests.psp;

import io.github.libfdx.backend.psp.PspApplicationBackend;
import io.github.libfdx.backend.psp.PspApplicationConfig;

/**
 * Launches the psp backend ui kit smoke test entry point.
 *
 * @author xpenatan
 */
public final class PspBackendUiKitSmokeTestLauncher {
    private PspBackendUiKitSmokeTestLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        PspApplicationConfig config = new PspApplicationConfig()
                .title("libfdx PSP backend UIKit smoke");
        new PspApplicationBackend().start(config, new PspBackendUiKitTest(0L, true));
    }
}
