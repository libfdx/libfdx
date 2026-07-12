package io.github.libfdx.tests.web;

import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;

/**
 * Launches the web test js entry point.
 *
 * @author xpenatan
 */
public final class WebTestJsLauncher {
    private WebTestJsLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        boolean webgpu = WebTestLauncherSupport.webGpuRequested(args);
        GraphicsAttachmentProvider graphics = webgpu ? new WebWGPUProvider() : new WebGLProvider();
        WebTestLauncherSupport.start("JS", args, webgpu, graphics);
    }
}
