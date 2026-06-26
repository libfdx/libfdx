package io.github.libfdx.net.webrtc.web;

import io.github.libfdx.net.webrtc.config.WebRtcEndpointSettings;
import io.github.libfdx.net.webrtc.platform.WebRtcIceCandidate;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnection;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionListener;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionProvider;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionState;
import io.github.libfdx.net.webrtc.config.WebRtcTurnServer;
import org.teavm.jso.JSBody;

/**
 * Browser WebRTC peer connection provider.
 *
 * @author xpenatan
 */
public final class WebWebRtcPeerConnectionProvider implements WebRtcPeerConnectionProvider {
    private boolean closed;

    @Override
    public WebRtcPeerConnection createPeerConnection(WebRtcEndpointSettings settings,
            final WebRtcPeerConnectionListener listener) {
        int handle = createPeerConnection(iceServersJson(settings), settings.forceRelay(),
                new WebWebRtcCallbacks.IceCallback() {
                    @Override
                    public void call(String candidate, String sdpMid, int sdpMLineIndex) {
                        listener.iceCandidate(new WebRtcIceCandidate(candidate, emptyToNull(sdpMid),
                                sdpMLineIndex));
                    }
                },
                new WebWebRtcCallbacks.StringCallback() {
                    @Override
                    public void call(String value) {
                        listener.stateChanged(state(value));
                    }
                },
                new WebWebRtcCallbacks.DataChannelCallback() {
                    @Override
                    public void call(int channelHandle, String label, boolean ordered) {
                        listener.dataChannel(new WebWebRtcDataChannel(channelHandle, label, ordered));
                    }
                },
                new WebWebRtcCallbacks.ErrorCallback() {
                    @Override
                    public void call(String value) {
                        listener.error(new RuntimeException(value));
                    }
                });
        return new WebWebRtcPeerConnection(handle);
    }

    @Override
    public boolean isSupported() {
        return !closed && supported();
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public void dispose() {
        close();
    }

    @Override
    public boolean isDisposed() {
        return closed;
    }

    static String iceServersJson(WebRtcEndpointSettings settings) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        String[] stunServers = settings.stunServers();
        for (int i = 0; i < stunServers.length; i++) {
            appendSeparator(builder);
            builder.append("{\"urls\":\"").append(escape(stunServers[i])).append("\"}");
        }
        WebRtcTurnServer[] turnServers = settings.turnServers();
        for (int i = 0; i < turnServers.length; i++) {
            WebRtcTurnServer turn = turnServers[i];
            appendSeparator(builder);
            builder.append("{\"urls\":\"").append(escape(turn.url())).append("\"");
            if (turn.username() != null) {
                builder.append(",\"username\":\"").append(escape(turn.username())).append("\"");
            }
            if (turn.credential() != null) {
                builder.append(",\"credential\":\"").append(escape(turn.credential())).append("\"");
            }
            builder.append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    static boolean supported() {
        return isSupported0();
    }

    private static WebRtcPeerConnectionState state(String value) {
        if ("connecting".equals(value)) {
            return WebRtcPeerConnectionState.CONNECTING;
        }
        if ("connected".equals(value)) {
            return WebRtcPeerConnectionState.CONNECTED;
        }
        if ("disconnected".equals(value)) {
            return WebRtcPeerConnectionState.DISCONNECTED;
        }
        if ("failed".equals(value)) {
            return WebRtcPeerConnectionState.FAILED;
        }
        if ("closed".equals(value)) {
            return WebRtcPeerConnectionState.CLOSED;
        }
        return WebRtcPeerConnectionState.NEW;
    }

    private static String emptyToNull(String value) {
        return value != null && value.length() > 0 ? value : null;
    }

    private static void appendSeparator(StringBuilder builder) {
        if (builder.length() > 1) {
            builder.append(',');
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @JSBody(params = {}, script = "return typeof RTCPeerConnection !== 'undefined';")
    private static native boolean isSupported0();

    @JSBody(params = {"iceServers", "forceRelay", "ice", "state", "dataChannel", "error"}, script =
            "var root = window.__libfdxWebRtc = window.__libfdxWebRtc || { peers: [], channels: [], sockets: [] };"
                    + "var config = { iceServers: JSON.parse(iceServers), iceTransportPolicy: forceRelay ? 'relay' : 'all' };"
                    + "var pc = new RTCPeerConnection(config);"
                    + "var id = root.peers.length; root.peers[id] = pc;"
                    + "pc.onicecandidate = function(event) { if (event.candidate) ice(event.candidate.candidate, event.candidate.sdpMid || '', event.candidate.sdpMLineIndex || 0); };"
                    + "pc.onconnectionstatechange = function() { state(pc.connectionState || 'new'); };"
                    + "pc.ondatachannel = function(event) {"
                    + "  event.channel.binaryType = 'arraybuffer';"
                    + "  var ch = root.channels.length; root.channels[ch] = event.channel;"
                    + "  dataChannel(ch, event.channel.label || '', event.channel.ordered !== false);"
                    + "};"
                    + "pc.onerror = function(event) { error(event && event.message ? event.message : 'WebRTC peer error'); };"
                    + "return id;")
    private static native int createPeerConnection(String iceServers, boolean forceRelay,
            WebWebRtcCallbacks.IceCallback ice, WebWebRtcCallbacks.StringCallback state,
            WebWebRtcCallbacks.DataChannelCallback dataChannel, WebWebRtcCallbacks.ErrorCallback error);
}
