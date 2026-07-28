package io.github.libfdx.samples.starter.web;

import io.github.libfdx.graphics.gl.web.WebGLProvider;

/**
 * Launches the Starter Project WebAssembly entry point.
 *
 * @author xpenatan
 */
public final class StarterProjectWebWasmLauncher {
    private StarterProjectWebWasmLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args command-line arguments supplied by TeaVM
     */
    public static void main(String[] args) {
        StarterProjectWebLauncherSupport.start(
                "WebAssembly", false, new WebGLProvider());
    }
}
