package io.github.libfdx.net.webrtc.signaling;

import io.github.libfdx.core.Disposable;

/**
 * Connects an endpoint to a room-scoped signaling server.
 *
 * @author xpenatan
 */
public interface WebRtcSignalingClient extends Disposable {
    void connect(String signalingUrl, String roomId, String requestedPeerId, WebRtcSignalingListener listener);

    void process(float deltaTime);

    void send(WebRtcSignalingMessage message);

    boolean isConnected();

    String localPeerId();

    void close();
}
