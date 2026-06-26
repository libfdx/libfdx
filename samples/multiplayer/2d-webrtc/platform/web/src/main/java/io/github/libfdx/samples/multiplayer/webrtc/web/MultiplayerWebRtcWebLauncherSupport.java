package io.github.libfdx.samples.multiplayer.webrtc.web;

import io.github.libfdx.backend.web.WebApplicationBackend;
import io.github.libfdx.backend.web.WebApplicationConfig;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;
import io.github.libfdx.net.webrtc.web.WebWebRtcPlatform;
import io.github.libfdx.samples.multiplayer.webrtc.MultiplayerWebRtcApplication;
import io.github.libfdx.samples.multiplayer.webrtc.MultiplayerWebRtcConfig;
import org.teavm.jso.JSBody;

/**
 * Shared web launcher support for the WebRTC multiplayer 2D sample.
 *
 * @author xpenatan
 */
final class MultiplayerWebRtcWebLauncherSupport {
    private static final String CANVAS_ID = "libfdx-canvas";

    private MultiplayerWebRtcWebLauncherSupport() {
    }

    static void start(String runtimeName, String[] args) {
        boolean webgpu = isWebGPU(graphics(args));
        String graphicsName = webgpu ? "WebGPU" : "WebGL";
        WebApplicationConfig config = new WebApplicationConfig()
                .title("libfdx WebRTC Multiplayer 2D - " + graphicsName + " " + runtimeName)
                .size(0, 0)
                .canvasId(CANVAS_ID);
        if (webgpu) {
            config.graphics(new WebWGPUProvider());
        } else {
            config.graphics(new WebGLProvider());
        }
        MultiplayerWebRtcConfig sampleConfig = MultiplayerWebRtcConfig.builder(WebWebRtcPlatform.factory())
                .signalingUrl(query("signaling", "ws://127.0.0.1:7777"))
                .playerName(query("player", "Web"))
                .hostRoomId(query("hostRoom", ""))
                .autoHost(Boolean.parseBoolean(query("host", "false")))
                .autoJoinRoom(query("join", ""))
                .validationEnabled(Boolean.parseBoolean(query("validate", "false")))
                .validationSelection(query("scenario", ""))
                .build();
        new WebApplicationBackend().start(config, new MultiplayerWebRtcApplication(sampleConfig));
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
