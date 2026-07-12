package io.github.libfdx.tests.web;

import io.github.libfdx.graphics.gl.web.WebGLProvider;

/**
 * Launches the web test wasm entry point.
 *
 * @author xpenatan
 */
public final class WebTestWasmLauncher {
    private WebTestWasmLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        WebTestLauncherSupport.start("Wasm", args, false, new WebGLProvider());
    }
}
