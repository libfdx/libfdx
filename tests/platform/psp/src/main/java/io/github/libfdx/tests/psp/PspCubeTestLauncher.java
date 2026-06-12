package io.github.libfdx.tests.psp;

/**
 * Launches the psp cube test entry point.
 *
 * @author xpenatan
 */
public final class PspCubeTestLauncher {
    private PspCubeTestLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        PspTestLauncher.run(new PspCubeTest());
    }
}
