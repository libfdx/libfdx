package io.github.libfdx.samples.starter.web;

import io.github.libfdx.backend.web.WebApplicationBackend;
import io.github.libfdx.backend.web.WebApplicationConfig;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.samples.starter.StarterProjectApplication;
import org.teavm.jso.JSBody;

/**
 * Shared web launcher support for the Starter Project sample.
 *
 * @author xpenatan
 */
final class StarterProjectWebLauncherSupport {
    private static final String CANVAS_ID = "libfdx-canvas";

    private StarterProjectWebLauncherSupport() {
    }

    static void start(String runtimeName, boolean webgpu,
            GraphicsAttachmentProvider graphics) {
        String graphicsName = webgpu ? "WebGPU" : "WebGL";
        WebApplicationConfig config = new WebApplicationConfig()
                .title("libFDX Starter Project - " + graphicsName + " " + runtimeName)
                .size(0, 0)
                .canvasId(CANVAS_ID)
                .graphics(graphics);

        new WebApplicationBackend().start(config, new StarterProjectApplication());
    }

    static boolean webGpuRequested(String[] args) {
        return isWebGpu(graphics(args));
    }

    private static String graphics(String[] args) {
        String prefix = "--graphics=";
        if (args != null) {
            for (String arg : args) {
                if (arg != null && arg.startsWith(prefix)) {
                    return arg.substring(prefix.length());
                }
            }
        }
        return query("graphics", "webgl");
    }

    private static boolean isWebGpu(String graphics) {
        return "webgpu".equalsIgnoreCase(graphics)
                || "wgpu".equalsIgnoreCase(graphics);
    }

    @JSBody(params = {"name", "fallback"}, script =
            "var params = new URLSearchParams(window.location.search || '');\n"
                    + "var value = params.get(name);\n"
                    + "return value || fallback;")
    private static native String query(String name, String fallback);
}
