package io.github.libfdx.samples.basic.web;

/**
 * Launches the basic web wasm entry point.
 *
 * @author xpenatan
 */
public final class BasicWebWasmLauncher {
    private BasicWebWasmLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        BasicWebLauncherSupport.start("Wasm", args);
    }
}
