package io.github.libfdx.samples.basic.web;

import io.github.libfdx.backend.web.WebApplicationBackend;
import io.github.libfdx.backend.web.WebApplicationConfig;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;
import io.github.libfdx.samples.basic.BasicApplication;

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
