package io.github.libfdx.samples.multiplayer.webrtc.web;

/**
 * Launches the WebRTC multiplayer 2D web JS entry point.
 *
 * @author xpenatan
 */
public final class MultiplayerWebRtcWebJsLauncher {
    private MultiplayerWebRtcWebJsLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        MultiplayerWebRtcWebLauncherSupport.start("JS", args);
    }
}
