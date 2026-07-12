package io.github.libfdx.net.webrtc.desktop;

import dev.onvoid.webrtc.CreateSessionDescriptionObserver;
import dev.onvoid.webrtc.RTCAnswerOptions;
import dev.onvoid.webrtc.RTCDataChannelInit;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCOfferOptions;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCSdpType;
import dev.onvoid.webrtc.RTCSessionDescription;
import dev.onvoid.webrtc.SetSessionDescriptionObserver;
import io.github.libfdx.net.transport.NetDelivery;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannel;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannelListener;
import io.github.libfdx.net.webrtc.platform.WebRtcIceCandidate;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnection;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionListener;
import io.github.libfdx.net.webrtc.platform.WebRtcSessionDescription;
import io.github.libfdx.net.webrtc.platform.WebRtcSessionDescriptionCallback;

/**
 * Desktop peer connection wrapper.
 *
 * @author xpenatan
 */
public final class DesktopWebRtcPeerConnection implements WebRtcPeerConnection {
    private final RTCPeerConnection peerConnection;
    private final WebRtcPeerConnectionListener listener;
    private final DesktopWebRtcPeerConnectionProvider owner;
    private boolean closed;

    DesktopWebRtcPeerConnection(RTCPeerConnection peerConnection, WebRtcPeerConnectionListener listener,
            DesktopWebRtcPeerConnectionProvider owner) {
        this.peerConnection = peerConnection;
        this.listener = listener;
        this.owner = owner;
    }

    @Override
    public WebRtcDataChannel createDataChannel(String label, NetDelivery delivery,
            WebRtcDataChannelListener listener) {
        RTCDataChannelInit init = new RTCDataChannelInit();
        init.ordered = delivery == NetDelivery.RELIABLE_ORDERED;
        if (delivery == NetDelivery.UNRELIABLE_UNORDERED) {
            init.maxRetransmits = 0;
        }
        DesktopWebRtcDataChannel channel = new DesktopWebRtcDataChannel(peerConnection.createDataChannel(label, init));
        channel.listener(listener);
        return channel;
    }

    @Override
    public void createOffer(final WebRtcSessionDescriptionCallback callback) {
        createOffer(callback, false);
    }

    @Override
    public void handleOffer(WebRtcSessionDescription offer, final WebRtcSessionDescriptionCallback callback) {
        peerConnection.setRemoteDescription(toNative(offer), new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                peerConnection.createAnswer(new RTCAnswerOptions(), new CreateSessionDescriptionObserver() {
                    @Override
                    public void onSuccess(final RTCSessionDescription description) {
                        setLocal(description, callback);
                    }

                    @Override
                    public void onFailure(String error) {
                        callback.error(new RuntimeException(error));
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                callback.error(new RuntimeException(error));
            }
        });
    }

    @Override
    public void setRemoteAnswer(WebRtcSessionDescription answer) {
        peerConnection.setRemoteDescription(toNative(answer), new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(String error) {
                listener.error(new RuntimeException(error));
            }
        });
    }

    @Override
    public void addIceCandidate(WebRtcIceCandidate candidate) {
        peerConnection.addIceCandidate(new RTCIceCandidate(candidate.sdpMid(), candidate.sdpMLineIndex(),
                candidate.candidate()));
    }

    @Override
    public void restartIce(WebRtcSessionDescriptionCallback callback) {
        peerConnection.restartIce();
        createOffer(callback, true);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                peerConnection.close();
            }
            finally {
                owner.connectionClosed(this);
            }
        }
    }

    @Override
    public void dispose() {
        close();
    }

    @Override
    public boolean isDisposed() {
        return closed;
    }

    private void createOffer(final WebRtcSessionDescriptionCallback callback, boolean iceRestart) {
        RTCOfferOptions options = new RTCOfferOptions();
        options.iceRestart = iceRestart;
        peerConnection.createOffer(options, new CreateSessionDescriptionObserver() {
            @Override
            public void onSuccess(final RTCSessionDescription description) {
                setLocal(description, callback);
            }

            @Override
            public void onFailure(String error) {
                callback.error(new RuntimeException(error));
            }
        });
    }

    private void setLocal(final RTCSessionDescription description, final WebRtcSessionDescriptionCallback callback) {
        peerConnection.setLocalDescription(description, new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                callback.success(fromNative(description));
            }

            @Override
            public void onFailure(String error) {
                callback.error(new RuntimeException(error));
            }
        });
    }

    private static RTCSessionDescription toNative(WebRtcSessionDescription description) {
        RTCSdpType type = description.type() == WebRtcSessionDescription.Type.OFFER ? RTCSdpType.OFFER
                : RTCSdpType.ANSWER;
        return new RTCSessionDescription(type, description.sdp());
    }

    private static WebRtcSessionDescription fromNative(RTCSessionDescription description) {
        WebRtcSessionDescription.Type type = description.sdpType == RTCSdpType.OFFER
                ? WebRtcSessionDescription.Type.OFFER : WebRtcSessionDescription.Type.ANSWER;
        return new WebRtcSessionDescription(type, description.sdp);
    }
}
