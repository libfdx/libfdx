package io.github.libfdx.samples.basic.web;

import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;

/**
 * Launches the basic web js entry point.
 *
 * @author xpenatan
 */
public final class BasicWebJsLauncher {
    private BasicWebJsLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        boolean webgpu = BasicWebLauncherSupport.webGpuRequested(args);
        GraphicsAttachmentProvider graphics = webgpu ? new WebWGPUProvider() : new WebGLProvider();
        BasicWebLauncherSupport.start("JS", webgpu, graphics);
    }
}
