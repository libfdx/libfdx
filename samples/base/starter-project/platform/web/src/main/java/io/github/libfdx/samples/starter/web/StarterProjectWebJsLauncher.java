package io.github.libfdx.samples.starter.web;

import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;

/**
 * Launches the Starter Project JavaScript entry point.
 *
 * @author xpenatan
 */
public final class StarterProjectWebJsLauncher {
    private StarterProjectWebJsLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args command-line arguments supplied by TeaVM
     */
    public static void main(String[] args) {
        boolean webgpu = StarterProjectWebLauncherSupport.webGpuRequested(args);
        GraphicsAttachmentProvider graphics =
                webgpu ? new WebWGPUProvider() : new WebGLProvider();
        StarterProjectWebLauncherSupport.start("JavaScript", webgpu, graphics);
    }
}
