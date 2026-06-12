package io.github.libfdx.tests.psp;

import io.github.libfdx.backend.psp.natives.PSPCoreApi;
import io.github.libfdx.backend.psp.natives.PSPGraphicsApi;

/**
 * Launches the psp test entry point.
 *
 * @author xpenatan
 */
final class PspTestLauncher {
    private PspTestLauncher() {
    }

    static void run(PspTest test) {
        PSPCoreApi.setupCallbacks();
        PSPGraphicsApi.initGraphics();
        try {
            test.create();
            while (PSPCoreApi.isRunning()) {
                PSPGraphicsApi.beginFrame(PSPGraphicsApi.GU_FALSE);
                test.render();
                PSPGraphicsApi.endFrame(PSPGraphicsApi.GU_TRUE, PSPGraphicsApi.GU_FALSE);
            }
        } finally {
            PSPGraphicsApi.sceGuDisplay(PSPGraphicsApi.GU_FALSE);
            PSPGraphicsApi.sceGuTerm();
        }
    }
}
