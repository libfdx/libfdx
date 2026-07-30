package io.github.libfdx.samples.g2d.platformer.web;

import io.github.libfdx.graphics.gl.web.WebGLProvider;

/**
 * Launches the platformer Wasm web entry point.
 *
 * @author xpenatan
 */
public final class PlatformerWebWasmLauncher {
    private PlatformerWebWasmLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        PlatformerWebLauncherSupport.start("Wasm", false, new WebGLProvider());
    }
}
