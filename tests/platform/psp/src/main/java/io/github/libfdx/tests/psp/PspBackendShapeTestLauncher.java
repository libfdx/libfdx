package io.github.libfdx.tests.psp;

import io.github.libfdx.backend.psp.PspApplicationBackend;
import io.github.libfdx.backend.psp.PspApplicationConfig;

public final class PspBackendShapeTestLauncher {
    private PspBackendShapeTestLauncher() {
    }

    public static void main(String[] args) {
        PspApplicationConfig config = new PspApplicationConfig()
                .title("libfdx PSP backend shape");
        new PspApplicationBackend().start(config, new PspBackendShapeTest(0L));
    }
}
