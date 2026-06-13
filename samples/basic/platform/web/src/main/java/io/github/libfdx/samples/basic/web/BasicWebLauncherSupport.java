package io.github.libfdx.samples.basic.web;

import io.github.libfdx.backend.web.WebApplicationBackend;
import io.github.libfdx.backend.web.WebApplicationConfig;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;
import io.github.libfdx.samples.basic.BasicApplication;
import org.teavm.jso.JSBody;

/**
 * Represents a basic web launcher support.
 *
 * @author xpenatan
 */
final class BasicWebLauncherSupport {
    private static final String CANVAS_ID = "libfdx-canvas";

    private BasicWebLauncherSupport() {
    }

    static void start(String runtimeName, String[] args) {
        boolean webgpu = isWebGPU(graphics(args));
        String graphicsName = webgpu ? "WebGPU" : "WebGL";
        WebApplicationConfig config = new WebApplicationConfig()
                .title("libfdx Basic - " + graphicsName + " " + runtimeName)
                .size(0, 0)
                .canvasId(CANVAS_ID);
        if (webgpu) {
            config.graphics(new WebWGPUProvider());
        } else {
            config.graphics(new WebGLProvider());
        }

        new WebApplicationBackend().start(config, new BasicApplication());
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

    @JSBody(params = { "name", "fallback" }, script =
            "var params = new URLSearchParams(window.location.search || '');\n" +
                    "var value = params.get(name);\n" +
                    "return value || fallback;")
    private static native String query(String name, String fallback);
}
