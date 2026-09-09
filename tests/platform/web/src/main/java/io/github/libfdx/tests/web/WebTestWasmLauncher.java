package io.github.libfdx.tests.web;

import io.github.libfdx.testsupport.web.WebTestLauncherSupport;

import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;

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
        boolean webgpu = WebTestLauncherSupport.webGpuRequested(args);
        GraphicsAttachmentProvider graphics = webgpu ? new WebWGPUProvider() : new WebGLProvider();
        WebTestLauncherSupport.start("Wasm", args, webgpu, graphics);
    }
}
