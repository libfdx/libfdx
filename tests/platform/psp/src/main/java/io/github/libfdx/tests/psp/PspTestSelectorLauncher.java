package io.github.libfdx.tests.psp;

import io.github.libfdx.backend.psp.PspApplicationBackend;
import io.github.libfdx.backend.psp.PspApplicationConfig;
import io.github.libfdx.tests.TestChooserApplication;

/**
 * Launches the PSP test selector.
 *
 * @author xpenatan
 */
public final class PspTestSelectorLauncher {
    private PspTestSelectorLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        PspApplicationConfig config = new PspApplicationConfig()
                .title("libfdx PSP tests");
        new PspApplicationBackend().start(config,
                new TestChooserApplication(new String[] { "psp" }, "psp", null, true, true));
    }
}
