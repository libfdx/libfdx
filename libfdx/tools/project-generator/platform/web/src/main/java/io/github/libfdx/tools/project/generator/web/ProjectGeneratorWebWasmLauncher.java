package io.github.libfdx.tools.project.generator.web;

import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;

/**
 * Launches the project generator web wasm entry point.
 *
 * @author xpenatan
 */
public final class ProjectGeneratorWebWasmLauncher {
    private ProjectGeneratorWebWasmLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        boolean webgpu = ProjectGeneratorWebLauncherSupport.webGpuRequested(args);
        GraphicsAttachmentProvider graphics = webgpu ? new WebWGPUProvider() : new WebGLProvider();
        ProjectGeneratorWebLauncherSupport.start("Wasm", webgpu, graphics);
    }
}
