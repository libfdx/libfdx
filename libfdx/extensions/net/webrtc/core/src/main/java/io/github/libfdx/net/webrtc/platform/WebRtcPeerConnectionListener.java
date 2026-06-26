package io.github.libfdx.net.webrtc.platform;

/**
 * Receives provider peer-connection events.
 *
 * @author xpenatan
 */
public interface WebRtcPeerConnectionListener {
    void iceCandidate(WebRtcIceCandidate candidate);

    void dataChannel(WebRtcDataChannel dataChannel);

    void stateChanged(WebRtcPeerConnectionState state);

    void error(Throwable error);
}
