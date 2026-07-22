package io.github.libfdx.samples.g2d.spritemovement.web;

import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;

/**
 * Launches the 2D Sprite Movement web JavaScript entry point.
 *
 * @author xpenatan
 */
public final class SpriteMovementWebJsLauncher {
    private SpriteMovementWebJsLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        boolean webgpu = SpriteMovementWebLauncherSupport.webGpuRequested(args);
        GraphicsAttachmentProvider graphics = webgpu ? new WebWGPUProvider() : new WebGLProvider();
        SpriteMovementWebLauncherSupport.start("JS", webgpu, graphics);
    }
}
