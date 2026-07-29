package io.github.libfdx.samples.g2d.spritemovement.web;

import io.github.libfdx.backend.web.WebApplicationBackend;
import io.github.libfdx.backend.web.WebApplicationConfig;
import io.github.libfdx.ecs.EcsApplication;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.samples.g2d.spritemovement.SpriteMovementProject;
import org.teavm.jso.JSBody;

/**
 * Shared web launcher support for the 2D Sprite Movement sample.
 *
 * @author xpenatan
 */
final class SpriteMovementWebLauncherSupport {
    private static final String CANVAS_ID = "libfdx-canvas";

    private SpriteMovementWebLauncherSupport() {
    }

    static void start(String runtimeName, boolean webgpu, GraphicsAttachmentProvider graphics) {
        String graphicsName = webgpu ? "WebGPU" : "WebGL";
        WebApplicationConfig config = new WebApplicationConfig()
                .title("libfdx 2D Sprite Movement - " + graphicsName + " " + runtimeName)
                .size(0, 0)
                .canvasId(CANVAS_ID)
                .graphics(graphics);

        new WebApplicationBackend().start(config,
                new EcsApplication(new SpriteMovementProject()));
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
