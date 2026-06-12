package io.github.libfdx.tests.psp;

import io.github.libfdx.backend.psp.PspApplicationBackend;
import io.github.libfdx.backend.psp.PspApplicationConfig;

/**
 * Launches the psp backend sprite batch test entry point.
 *
 * @author xpenatan
 */
public final class PspBackendSpriteBatchTestLauncher {
    private PspBackendSpriteBatchTestLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        PspApplicationConfig config = new PspApplicationConfig()
                .title("libfdx PSP backend SpriteBatch");
        new PspApplicationBackend().start(config, new PspBackendSpriteBatchTest(0L));
    }
}
