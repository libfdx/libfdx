package io.github.libfdx.tools.project.generator.web;

/**
 * Launches the project generator web wasm entry point.
 *
 * @author xpenatan
 */
public final class ProjectGeneratorWebWasmLauncher {
    private ProjectGeneratorWebWasmLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        ProjectGeneratorWebLauncherSupport.start("Wasm", args);
    }
}
