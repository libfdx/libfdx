package io.github.libfdx.tests.psp;

import io.github.libfdx.backend.psp.PspApplicationBackend;
import io.github.libfdx.backend.psp.PspApplicationConfig;

public final class PspBackendClearTestLauncher {
    private PspBackendClearTestLauncher() {
    }

    public static void main(String[] args) {
        PspApplicationConfig config = new PspApplicationConfig()
                .title("libfdx PSP backend clear");
        new PspApplicationBackend().start(config, new PspBackendClearTest(0L));
    }
}
