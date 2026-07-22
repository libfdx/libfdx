package io.github.libfdx.samples.g2d.spritemovement.web;

import io.github.libfdx.graphics.gl.web.WebGLProvider;

/**
 * Launches the 2D Sprite Movement web Wasm entry point.
 *
 * @author xpenatan
 */
public final class SpriteMovementWebWasmLauncher {
    private SpriteMovementWebWasmLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        SpriteMovementWebLauncherSupport.start("Wasm", false, new WebGLProvider());
    }
}
