package io.github.libfdx.samples.ecs.platformer.web;

import io.github.libfdx.graphics.gl.web.WebGLProvider;

/**
 * Launches the ECS platformer Wasm web entry point.
 *
 * @author xpenatan
 */
public final class EcsPlatformerWebWasmLauncher {
    private EcsPlatformerWebWasmLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        EcsPlatformerWebLauncherSupport.start("Wasm", false, new WebGLProvider());
    }
}
