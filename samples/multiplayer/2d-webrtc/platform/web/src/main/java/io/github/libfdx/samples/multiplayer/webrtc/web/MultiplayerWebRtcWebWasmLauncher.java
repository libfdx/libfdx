package io.github.libfdx.samples.multiplayer.webrtc.web;

/**
 * Launches the WebRTC multiplayer 2D web Wasm entry point.
 *
 * @author xpenatan
 */
public final class MultiplayerWebRtcWebWasmLauncher {
    private MultiplayerWebRtcWebWasmLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        MultiplayerWebRtcWebLauncherSupport.start("Wasm", args);
    }
}
