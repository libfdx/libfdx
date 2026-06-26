package io.github.libfdx.net.webrtc.platform;

/**
 * Provider-neutral WebRTC peer connection state.
 *
 * @author xpenatan
 */
public enum WebRtcPeerConnectionState {
    NEW,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED,
    CLOSED
}
