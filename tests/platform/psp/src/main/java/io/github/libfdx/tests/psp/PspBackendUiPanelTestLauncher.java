package io.github.libfdx.tests.psp;

import io.github.libfdx.backend.psp.PspApplicationBackend;
import io.github.libfdx.backend.psp.PspApplicationConfig;

/**
 * Launches the psp backend ui panel test entry point.
 *
 * @author xpenatan
 */
public final class PspBackendUiPanelTestLauncher {
    private PspBackendUiPanelTestLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        PspApplicationConfig config = new PspApplicationConfig()
                .title("libfdx PSP backend UI panel");
        new PspApplicationBackend().start(config, new PspBackendUiPanelTest(0L));
    }
}
