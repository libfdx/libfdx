package io.github.libfdx.net.webrtc.android;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.net.transport.NetDelivery;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannel;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannelListener;
import io.github.libfdx.net.webrtc.platform.WebRtcIceCandidate;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnection;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionListener;
import io.github.libfdx.net.webrtc.platform.WebRtcSessionDescription;
import io.github.libfdx.net.webrtc.platform.WebRtcSessionDescriptionCallback;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

/**
 * Android peer connection wrapper.
 *
 * @author xpenatan
 */
public final class AndroidWebRtcPeerConnection implements WebRtcPeerConnection {
    private final PeerConnection peerConnection;
    private final WebRtcPeerConnectionListener listener;
    private final AndroidWebRtcPeerConnectionProvider owner;
    private boolean closed;

    AndroidWebRtcPeerConnection(PeerConnection peerConnection, WebRtcPeerConnectionListener listener,
            AndroidWebRtcPeerConnectionProvider owner) {
        this.peerConnection = peerConnection;
        this.listener = listener;
        this.owner = owner;
    }

    @Override
    public WebRtcDataChannel createDataChannel(String label, NetDelivery delivery,
            WebRtcDataChannelListener listener) {
        DataChannel.Init init = new DataChannel.Init();
        init.ordered = delivery == NetDelivery.RELIABLE_ORDERED;
        if (delivery == NetDelivery.UNRELIABLE_UNORDERED) {
            init.maxRetransmits = 0;
        }
        AndroidWebRtcDataChannel channel = new AndroidWebRtcDataChannel(peerConnection.createDataChannel(label, init));
        channel.listener(listener);
        return channel;
    }

    @Override
    public void createOffer(final WebRtcSessionDescriptionCallback callback) {
        createOffer(callback, false);
    }

    @Override
    public void handleOffer(WebRtcSessionDescription offer, final WebRtcSessionDescriptionCallback callback) {
        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription description) {
            }

            @Override
            public void onSetSuccess() {
                peerConnection.createAnswer(new CreateObserver(callback), new MediaConstraints());
            }

            @Override
            public void onCreateFailure(String error) {
                callback.error(new RuntimeException(error));
            }

            @Override
            public void onSetFailure(String error) {
                callback.error(new RuntimeException(error));
            }
        }, toNative(offer));
    }

    @Override
    public void setRemoteAnswer(WebRtcSessionDescription answer) {
        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription description) {
            }

            @Override
            public void onSetSuccess() {
            }

            @Override
            public void onCreateFailure(String error) {
                listener.error(new RuntimeException(error));
            }

            @Override
            public void onSetFailure(String error) {
                listener.error(new RuntimeException(error));
            }
        }, toNative(answer));
    }

    @Override
    public void addIceCandidate(WebRtcIceCandidate candidate) {
        peerConnection.addIceCandidate(new IceCandidate(candidate.sdpMid(), candidate.sdpMLineIndex(),
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
            Throwable failure = null;
            try {
                peerConnection.close();
            }
            catch (Throwable throwable) {
                failure = throwable;
            }
            try {
                peerConnection.dispose();
            }
            catch (Throwable throwable) {
                if (failure == null) {
                    failure = throwable;
                }
                else if (failure != throwable) {
                    failure.addSuppressed(throwable);
                }
            }
            finally {
                owner.connectionClosed(this);
            }
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            if (failure instanceof Error) {
                throw (Error) failure;
            }
            if (failure != null) {
                throw new FdxException("Android WebRTC peer connection disposal failed", failure);
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

    private void createOffer(WebRtcSessionDescriptionCallback callback, boolean iceRestart) {
        MediaConstraints constraints = new MediaConstraints();
        if (iceRestart) {
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("IceRestart", "true"));
        }
        peerConnection.createOffer(new CreateObserver(callback), constraints);
    }

    private void setLocal(final SessionDescription description, final WebRtcSessionDescriptionCallback callback) {
        peerConnection.setLocalDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription description) {
            }

            @Override
            public void onSetSuccess() {
                callback.success(fromNative(description));
            }

            @Override
            public void onCreateFailure(String error) {
                callback.error(new RuntimeException(error));
            }

            @Override
            public void onSetFailure(String error) {
                callback.error(new RuntimeException(error));
            }
        }, description);
    }

    private static SessionDescription toNative(WebRtcSessionDescription description) {
        SessionDescription.Type type = description.type() == WebRtcSessionDescription.Type.OFFER
                ? SessionDescription.Type.OFFER : SessionDescription.Type.ANSWER;
        return new SessionDescription(type, description.sdp());
    }

    private static WebRtcSessionDescription fromNative(SessionDescription description) {
        WebRtcSessionDescription.Type type = description.type == SessionDescription.Type.OFFER
                ? WebRtcSessionDescription.Type.OFFER : WebRtcSessionDescription.Type.ANSWER;
        return new WebRtcSessionDescription(type, description.description);
    }

    private final class CreateObserver implements SdpObserver {
        private final WebRtcSessionDescriptionCallback callback;

        CreateObserver(WebRtcSessionDescriptionCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onCreateSuccess(SessionDescription description) {
            setLocal(description, callback);
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String error) {
            callback.error(new RuntimeException(error));
        }

        @Override
        public void onSetFailure(String error) {
            callback.error(new RuntimeException(error));
        }
    }

}
