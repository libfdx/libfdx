package io.github.libfdx.tools.project.generator.web;

import io.github.libfdx.backend.web.WebApplicationBackend;
import io.github.libfdx.backend.web.WebApplicationConfig;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;
import io.github.libfdx.tools.project.generator.ui.ProjectGeneratorApplication;

/**
 * Represents a project generator web launcher support.
 *
 * @author xpenatan
 */
final class ProjectGeneratorWebLauncherSupport {
    private static final String CANVAS_ID = "libfdx-canvas";

    private ProjectGeneratorWebLauncherSupport() {
    }

    static void start(String runtimeName, String[] args) {
        boolean webgpu = isWebGPU(graphics(args));
        String graphicsName = webgpu ? "WebGPU" : "WebGL";
        String title = "libfdx Project Generator - " + graphicsName + " " + runtimeName;
        WebApplicationConfig config = new WebApplicationConfig()
                .title(title)
                .size(0, 0)
                .canvasId(CANVAS_ID);
        if (webgpu) {
            config.graphics(new WebWGPUProvider());
        } else {
            config.graphics(new WebGLProvider());
        }

        new WebApplicationBackend().start(config, new ProjectGeneratorApplication(new WebProjectExportTarget()));
    }

    private static String graphics(String[] args) {
        if (args == null) {
            return "webgl";
        }
        String prefix = "--graphics=";
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg != null && arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return "webgl";
    }

    private static boolean isWebGPU(String graphics) {
        return "webgpu".equalsIgnoreCase(graphics) || "wgpu".equalsIgnoreCase(graphics);
    }
}
