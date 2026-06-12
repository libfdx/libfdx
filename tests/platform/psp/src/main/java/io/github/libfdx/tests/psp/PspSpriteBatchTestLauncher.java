package io.github.libfdx.tests.psp;

/**
 * Launches the psp sprite batch test entry point.
 *
 * @author xpenatan
 */
public final class PspSpriteBatchTestLauncher {
    private PspSpriteBatchTestLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        PspTestLauncher.run(new PspSpriteBatchTest());
    }
}
