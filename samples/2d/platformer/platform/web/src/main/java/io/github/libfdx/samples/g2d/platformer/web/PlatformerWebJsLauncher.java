package io.github.libfdx.samples.g2d.platformer.web;

import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;

/**
 * Launches the platformer JavaScript web entry point.
 *
 * @author xpenatan
 */
public final class PlatformerWebJsLauncher {
    private PlatformerWebJsLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        boolean webgpu = PlatformerWebLauncherSupport.webGpuRequested(args);
        GraphicsAttachmentProvider graphics = webgpu ? new WebWGPUProvider() : new WebGLProvider();
        PlatformerWebLauncherSupport.start("JS", webgpu, graphics);
    }
}
