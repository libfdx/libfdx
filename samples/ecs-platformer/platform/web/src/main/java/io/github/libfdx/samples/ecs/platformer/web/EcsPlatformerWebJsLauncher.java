package io.github.libfdx.samples.ecs.platformer.web;

import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;

/**
 * Launches the ECS platformer JavaScript web entry point.
 *
 * @author xpenatan
 */
public final class EcsPlatformerWebJsLauncher {
    private EcsPlatformerWebJsLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        boolean webgpu = EcsPlatformerWebLauncherSupport.webGpuRequested(args);
        GraphicsAttachmentProvider graphics = webgpu ? new WebWGPUProvider() : new WebGLProvider();
        EcsPlatformerWebLauncherSupport.start("JS", webgpu, graphics);
    }
}
