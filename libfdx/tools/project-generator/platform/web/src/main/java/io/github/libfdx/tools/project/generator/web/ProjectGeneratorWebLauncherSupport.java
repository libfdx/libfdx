package io.github.libfdx.tools.project.generator.web;

import io.github.libfdx.backend.web.WebApplicationBackend;
import io.github.libfdx.backend.web.WebApplicationConfig;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.tools.project.generator.ui.ProjectGeneratorApplication;
import org.teavm.jso.JSBody;

/**
 * Represents a project generator web launcher support.
 *
 * @author xpenatan
 */
final class ProjectGeneratorWebLauncherSupport {
    private static final String CANVAS_ID = "libfdx-canvas";

    private ProjectGeneratorWebLauncherSupport() {
    }

    static void start(String runtimeName, boolean webgpu, GraphicsAttachmentProvider graphics) {
        String graphicsName = webgpu ? "WebGPU" : "WebGL";
        String title = "libfdx Project Generator - " + graphicsName + " " + runtimeName;
        WebApplicationConfig config = new WebApplicationConfig()
                .title(title)
                .size(0, 0)
                .canvasId(CANVAS_ID)
                .graphics(graphics);

        new WebApplicationBackend().start(config, new ProjectGeneratorApplication(new WebProjectExportTarget()));
    }

    private static String graphics(String[] args) {
        String prefix = "--graphics=";
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg != null && arg.startsWith(prefix)) {
                    return arg.substring(prefix.length());
                }
            }
        }
        return query("graphics", "webgl");
    }

    private static boolean isWebGPU(String graphics) {
        return "webgpu".equalsIgnoreCase(graphics) || "wgpu".equalsIgnoreCase(graphics);
    }

    static boolean webGpuRequested(String[] args) {
        return isWebGPU(graphics(args));
    }

    @JSBody(params = { "name", "fallback" }, script =
            "var params = new URLSearchParams(window.location.search || '');\n" +
                    "var value = params.get(name);\n" +
                    "return value || fallback;")
    private static native String query(String name, String fallback);
}
