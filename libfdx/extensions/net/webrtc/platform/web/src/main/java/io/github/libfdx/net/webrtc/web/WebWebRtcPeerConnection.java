package io.github.libfdx.net.webrtc.web;

import io.github.libfdx.net.transport.NetDelivery;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannel;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannelListener;
import io.github.libfdx.net.webrtc.platform.WebRtcIceCandidate;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnection;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionListener;
import io.github.libfdx.net.webrtc.platform.WebRtcSessionDescription;
import io.github.libfdx.net.webrtc.platform.WebRtcSessionDescriptionCallback;
import org.teavm.jso.JSBody;

/**
 * Browser peer connection wrapper.
 *
 * @author xpenatan
 */
public final class WebWebRtcPeerConnection implements WebRtcPeerConnection {
    private final int handle;
    private final WebRtcPeerConnectionListener listener;
    private final WebWebRtcPeerConnectionProvider owner;
    private boolean closed;

    WebWebRtcPeerConnection(int handle, WebRtcPeerConnectionListener listener,
            WebWebRtcPeerConnectionProvider owner) {
        this.handle = handle;
        this.listener = listener;
        this.owner = owner;
    }

    @Override
    public WebRtcDataChannel createDataChannel(String label, NetDelivery delivery,
            WebRtcDataChannelListener listener) {
        boolean ordered = delivery == NetDelivery.RELIABLE_ORDERED;
        int channelHandle = createDataChannel0(handle, label, ordered, delivery == NetDelivery.UNRELIABLE_UNORDERED);
        WebWebRtcDataChannel channel = new WebWebRtcDataChannel(channelHandle, label, ordered);
        channel.listener(listener);
        return channel;
    }

    @Override
    public void createOffer(final WebRtcSessionDescriptionCallback callback) {
        createOffer0(handle, false, new WebWebRtcCallbacks.StringCallback() {
            @Override
            public void call(String value) {
                callback.success(new WebRtcSessionDescription(WebRtcSessionDescription.Type.OFFER, value));
            }
        }, new WebWebRtcCallbacks.ErrorCallback() {
            @Override
            public void call(String value) {
                callback.error(new RuntimeException(value));
            }
        });
    }

    @Override
    public void handleOffer(WebRtcSessionDescription offer, final WebRtcSessionDescriptionCallback callback) {
        handleOffer0(handle, offer.sdp(), new WebWebRtcCallbacks.StringCallback() {
            @Override
            public void call(String value) {
                callback.success(new WebRtcSessionDescription(WebRtcSessionDescription.Type.ANSWER, value));
            }
        }, new WebWebRtcCallbacks.ErrorCallback() {
            @Override
            public void call(String value) {
                callback.error(new RuntimeException(value));
            }
        });
    }

    @Override
    public void setRemoteAnswer(WebRtcSessionDescription answer) {
        setRemoteAnswer0(handle, answer.sdp(), new WebWebRtcCallbacks.ErrorCallback() {
            @Override
            public void call(String value) {
                listener.error(new RuntimeException(value));
            }
        });
    }

    @Override
    public void addIceCandidate(WebRtcIceCandidate candidate) {
        addIceCandidate0(handle, candidate.candidate(), candidate.sdpMid(), candidate.sdpMLineIndex(),
                new WebWebRtcCallbacks.ErrorCallback() {
                    @Override
                    public void call(String value) {
                        listener.error(new RuntimeException(value));
                    }
                });
    }

    @Override
    public void restartIce(final WebRtcSessionDescriptionCallback callback) {
        createOffer0(handle, true, new WebWebRtcCallbacks.StringCallback() {
            @Override
            public void call(String value) {
                callback.success(new WebRtcSessionDescription(WebRtcSessionDescription.Type.OFFER, value));
            }
        }, new WebWebRtcCallbacks.ErrorCallback() {
            @Override
            public void call(String value) {
                callback.error(new RuntimeException(value));
            }
        });
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                close0(handle);
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

    @JSBody(params = {"peer", "label", "ordered", "unreliable"}, script =
            "var root = window.__libfdxWebRtc;"
                    + "var pc = root.peers[peer];"
                    + "var init = { ordered: ordered };"
                    + "if (unreliable) init.maxRetransmits = 0;"
                    + "var channel = pc.createDataChannel(label, init);"
                    + "channel.binaryType = 'arraybuffer';"
                    + "var id = root.channels.length; root.channels[id] = channel;"
                    + "return id;")
    private static native int createDataChannel0(int peer, String label, boolean ordered, boolean unreliable);

    @JSBody(params = {"peer", "iceRestart", "success", "error"}, script =
            "var pc = window.__libfdxWebRtc.peers[peer];"
                    + "pc.createOffer({ iceRestart: iceRestart }).then(function(offer) {"
                    + "  return pc.setLocalDescription(offer).then(function() { success(offer.sdp); });"
                    + "}).catch(function(reason) { error(reason && reason.message ? reason.message : String(reason)); });")
    private static native void createOffer0(int peer, boolean iceRestart, WebWebRtcCallbacks.StringCallback success,
            WebWebRtcCallbacks.ErrorCallback error);

    @JSBody(params = {"peer", "sdp", "success", "error"}, script =
            "var pc = window.__libfdxWebRtc.peers[peer];"
                    + "pc.setRemoteDescription({ type: 'offer', sdp: sdp }).then(function() {"
                    + "  return pc.createAnswer();"
                    + "}).then(function(answer) {"
                    + "  return pc.setLocalDescription(answer).then(function() { success(answer.sdp); });"
                    + "}).catch(function(reason) { error(reason && reason.message ? reason.message : String(reason)); });")
    private static native void handleOffer0(int peer, String sdp, WebWebRtcCallbacks.StringCallback success,
            WebWebRtcCallbacks.ErrorCallback error);

    @JSBody(params = {"peer", "sdp", "error"}, script =
            "window.__libfdxWebRtc.peers[peer].setRemoteDescription({ type: 'answer', sdp: sdp })"
                    + ".catch(function(reason) { error(reason && reason.message ? reason.message : String(reason)); });")
    private static native void setRemoteAnswer0(int peer, String sdp, WebWebRtcCallbacks.ErrorCallback error);

    @JSBody(params = {"peer", "candidate", "sdpMid", "sdpMLineIndex", "error"}, script =
            "window.__libfdxWebRtc.peers[peer].addIceCandidate({ candidate: candidate, sdpMid: sdpMid || null, sdpMLineIndex: sdpMLineIndex })"
                    + ".catch(function(reason) { error(reason && reason.message ? reason.message : String(reason)); });")
    private static native void addIceCandidate0(int peer, String candidate, String sdpMid, int sdpMLineIndex,
            WebWebRtcCallbacks.ErrorCallback error);

    @JSBody(params = {"peer"}, script = "window.__libfdxWebRtc.peers[peer].close(); window.__libfdxWebRtc.peers[peer] = null;")
    private static native void close0(int peer);
}
