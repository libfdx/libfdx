package io.github.libfdx.net.webrtc.platform;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.net.transport.NetDelivery;

/**
 * Provider-neutral WebRTC peer connection.
 *
 * @author xpenatan
 */
public interface WebRtcPeerConnection extends Disposable {
    WebRtcDataChannel createDataChannel(String label, NetDelivery delivery, WebRtcDataChannelListener listener);

    void createOffer(WebRtcSessionDescriptionCallback callback);

    void handleOffer(WebRtcSessionDescription offer, WebRtcSessionDescriptionCallback callback);

    void setRemoteAnswer(WebRtcSessionDescription answer);

    void addIceCandidate(WebRtcIceCandidate candidate);

    void restartIce(WebRtcSessionDescriptionCallback callback);

    void close();
}
