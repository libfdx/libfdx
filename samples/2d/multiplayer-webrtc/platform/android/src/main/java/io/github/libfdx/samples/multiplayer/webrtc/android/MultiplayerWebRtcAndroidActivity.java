package io.github.libfdx.samples.multiplayer.webrtc.android;

import android.os.Bundle;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.android.AndroidApplicationActivity;
import io.github.libfdx.backend.android.AndroidApplicationConfig;
import io.github.libfdx.backend.android.AndroidVulkanProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.net.webrtc.android.AndroidWebRtcPlatform;
import io.github.libfdx.samples.multiplayer.webrtc.MultiplayerWebRtcApplication;
import io.github.libfdx.samples.multiplayer.webrtc.MultiplayerWebRtcConfig;

/**
 * Base Android activity for the WebRTC multiplayer 2D sample.
 *
 * @author xpenatan
 */
public class MultiplayerWebRtcAndroidActivity extends AndroidApplicationActivity {
    @Override
    protected AndroidApplicationConfig createApplicationConfig() {
        return new AndroidApplicationConfig()
                .title("libfdx WebRTC Multiplayer 2D - " + graphicsDisplayName())
                .size(960, 640)
                .vSync(true)
                .foregroundFps(60)
                .graphics(graphicsProvider());
    }

    @Override
    protected ApplicationListener createApplicationListener() {
        return new MultiplayerWebRtcApplication(MultiplayerWebRtcConfig.builder(AndroidWebRtcPlatform.factory(this))
                .signalingUrl(option("libfdx.sample.signalingUrl", "ws://10.0.2.2:7777"))
                .playerName(option("libfdx.sample.playerName", "Android"))
                .hostRoomId(option("libfdx.sample.hostRoomId", ""))
                .autoHost(Boolean.parseBoolean(option("libfdx.sample.autoHost", "false")))
                .autoJoinRoom(option("libfdx.sample.autoJoinRoom", ""))
                .exitAfterFrames(longOption("libfdx.sample.exitAfterFrames", 0L))
                .validationEnabled(Boolean.parseBoolean(option("libfdx.sample.validate", "false")))
                .validationSelection(option("libfdx.validation.scenario", ""))
                .build());
    }

    protected GraphicsAttachmentProvider graphicsProvider() {
        if ("vulkan".equalsIgnoreCase(graphicsName()) || "vk".equalsIgnoreCase(graphicsName())) {
            return new AndroidVulkanProvider();
        }
        return new WGPUProvider();
    }

    protected String graphicsName() {
        return "wgpu";
    }

    protected String graphicsDisplayName() {
        if ("vulkan".equalsIgnoreCase(graphicsName()) || "vk".equalsIgnoreCase(graphicsName())) {
            return "Vulkan JNI";
        }
        return "WGPU JNI";
    }

    private String option(String name, String fallback) {
        Bundle extras = getIntent() != null ? getIntent().getExtras() : null;
        if (extras != null && extras.containsKey(name)) {
            Object value = extras.get(name);
            if (value != null && value.toString().trim().length() > 0) {
                return value.toString().trim();
            }
        }
        String value = System.getProperty(name);
        return value != null && value.trim().length() > 0 ? value.trim() : fallback;
    }

    private long longOption(String name, long fallback) {
        String value = option(name, "");
        return value.length() > 0 ? Long.parseLong(value) : fallback;
    }
}
