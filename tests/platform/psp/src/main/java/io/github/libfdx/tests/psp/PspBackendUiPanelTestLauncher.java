package io.github.libfdx.tests.psp;

import io.github.libfdx.backend.psp.PspApplicationBackend;
import io.github.libfdx.backend.psp.PspApplicationConfig;

public final class PspBackendUiPanelTestLauncher {
    private PspBackendUiPanelTestLauncher() {
    }

    public static void main(String[] args) {
        PspApplicationConfig config = new PspApplicationConfig()
                .title("libfdx PSP backend UI panel");
        new PspApplicationBackend().start(config, new PspBackendUiPanelTest(0L));
    }
}
