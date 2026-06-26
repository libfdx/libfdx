package io.github.libfdx.net.webrtc.signaling;

/**
 * Receives signaling transport events.
 *
 * @author xpenatan
 */
public interface WebRtcSignalingListener {
    void connected(String localPeerId);

    void message(WebRtcSignalingMessage message);

    void disconnected(String reason);

    void error(Throwable error);
}
