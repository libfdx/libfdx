package io.github.libfdx.samples.multiplayer.webrtc.desktop;

import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;
import io.github.libfdx.backend.desktop.DesktopVulkanProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.d3d12.D3D12Provider;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.net.webrtc.desktop.DesktopWebRtcPlatform;
import io.github.libfdx.samples.multiplayer.webrtc.MultiplayerWebRtcApplication;
import io.github.libfdx.samples.multiplayer.webrtc.MultiplayerWebRtcConfig;

/**
 * Launches the desktop WebRTC multiplayer 2D sample.
 *
 * @author xpenatan
 */
public final class MultiplayerWebRtcDesktopLauncher {
    private MultiplayerWebRtcDesktopLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        String graphics = option(args, "--graphics=", System.getProperty("libfdx.sample.graphics", "wgpu"));
        String signalingUrl = option(args, "--signaling-url=",
                System.getProperty("libfdx.sample.signalingUrl", "ws://127.0.0.1:7777"));
        DesktopApplicationConfig desktopConfig = new DesktopApplicationConfig()
                .title("libfdx WebRTC Multiplayer 2D - " + graphicsDisplayName(graphics))
                .size(960, 640)
                .vSync(true)
                .foregroundFps(60)
                .graphics(graphicsProvider(graphics));
        MultiplayerWebRtcConfig sampleConfig = MultiplayerWebRtcConfig.builder(DesktopWebRtcPlatform.factory())
                .signalingUrl(signalingUrl)
                .playerName(option(args, "--player=", System.getProperty("libfdx.sample.playerName", "Desktop")))
                .hostRoomId(option(args, "--host-room-id=", System.getProperty("libfdx.sample.hostRoomId")))
                .autoHost(Boolean.parseBoolean(option(args, "--auto-host=",
                        System.getProperty("libfdx.sample.autoHost", "false"))))
                .autoJoinRoom(option(args, "--auto-join-room=", System.getProperty("libfdx.sample.autoJoinRoom")))
                .exitAfterFrames(exitAfterFrames(args))
                .validationEnabled(Boolean.parseBoolean(option(args, "--validate=",
                        System.getProperty("libfdx.sample.validate", "false"))))
                .validationSelection(option(args, "--validation-scenario=",
                        System.getProperty("libfdx.validation.scenario")))
                .build();
        new DesktopApplicationBackend().start(desktopConfig, new MultiplayerWebRtcApplication(sampleConfig));
    }

    private static GraphicsAttachmentProvider graphicsProvider(String graphics) {
        if ("gl".equalsIgnoreCase(graphics) || "opengl".equalsIgnoreCase(graphics)) {
            return new DesktopOpenGLProvider();
        }
        if ("vulkan".equalsIgnoreCase(graphics) || "vk".equalsIgnoreCase(graphics)) {
            return new DesktopVulkanProvider();
        }
        if (isD3D12(graphics)) {
            return new D3D12Provider();
        }
        return new WGPUProvider();
    }

    private static String graphicsDisplayName(String graphics) {
        String configured = System.getProperty("libfdx.sample.graphicsLabel");
        if (configured != null && configured.trim().length() > 0) {
            return configured.trim();
        }
        if ("gl".equalsIgnoreCase(graphics) || "opengl".equalsIgnoreCase(graphics)) {
            return "GL";
        }
        if ("vulkan".equalsIgnoreCase(graphics) || "vk".equalsIgnoreCase(graphics)) {
            return "Vulkan";
        }
        if (isD3D12(graphics)) {
            return "Direct3D 12";
        }
        return "WGPU";
    }

    private static boolean isD3D12(String graphics) {
        return "d3d12".equalsIgnoreCase(graphics)
                || "direct3d12".equalsIgnoreCase(graphics)
                || "directx12".equalsIgnoreCase(graphics)
                || "dx12".equalsIgnoreCase(graphics);
    }

    private static long exitAfterFrames(String[] args) {
        String value = option(args, "--exit-after-frames=", System.getProperty("libfdx.sample.exitAfterFrames"));
        return value != null && value.trim().length() > 0 ? Long.parseLong(value) : 0L;
    }

    private static String option(String[] args, String prefix, String fallback) {
        if (args == null) {
            return fallback;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg != null && arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return fallback;
    }
}
