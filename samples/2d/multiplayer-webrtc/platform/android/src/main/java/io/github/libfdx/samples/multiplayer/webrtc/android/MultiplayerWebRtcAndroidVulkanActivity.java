package io.github.libfdx.samples.multiplayer.webrtc.android;

/**
 * Android Vulkan entry point for the WebRTC multiplayer 2D sample.
 *
 * @author xpenatan
 */
public final class MultiplayerWebRtcAndroidVulkanActivity extends MultiplayerWebRtcAndroidActivity {
    @Override
    protected String graphicsName() {
        return "vulkan";
    }
}
