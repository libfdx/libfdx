package io.github.libfdx.tests.psp;

import io.github.libfdx.backend.psp.PspApplicationBackend;
import io.github.libfdx.backend.psp.PspApplicationConfig;

public final class PspBackendSpriteBatchTestLauncher {
    private PspBackendSpriteBatchTestLauncher() {
    }

    public static void main(String[] args) {
        PspApplicationConfig config = new PspApplicationConfig()
                .title("libfdx PSP backend SpriteBatch");
        new PspApplicationBackend().start(config, new PspBackendSpriteBatchTest(0L));
    }
}
