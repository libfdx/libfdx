package io.github.libfdx.net.webrtc.web;

import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingClient;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingCodec;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingListener;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessage;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessageType;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import org.teavm.jso.JSBody;

/**
 * Browser WebSocket signaling client.
 *
 * @author xpenatan
 */
public final class WebWebRtcSignalingClient implements WebRtcSignalingClient {
    private static final float PING_INTERVAL_SECONDS = 10.0f;

    private final WebRtcSignalingCodec codec = new WebRtcSignalingCodec();
    private final WebRtcSignalingMessage pingMessage =
            WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.PING).build();
    private WebRtcSignalingListener listener;
    private int socket = -1;
    private String localPeerId;
    private float pingTimer;
    private boolean closed;

    @Override
    public void connect(String signalingUrl, String roomId, String requestedPeerId,
            WebRtcSignalingListener listener) {
        this.listener = listener;
        String url = signalingUrl + separator(signalingUrl) + "room=" + encode(roomId)
                + optionalPeerId(requestedPeerId);
        socket = connect0(url, new WebWebRtcCallbacks.StringCallback() {
            @Override
            public void call(String value) {
                WebRtcSignalingMessage message = codec.decode(value);
                if (message.type() == WebRtcSignalingMessageType.WELCOME) {
                    localPeerId = message.targetPeerId();
                    if (localPeerId == null) {
                        localPeerId = message.payload().stringValue("peerId", null);
                    }
                    WebWebRtcSignalingClient.this.listener.connected(localPeerId);
                }
                WebWebRtcSignalingClient.this.listener.message(message);
            }
        }, new WebWebRtcCallbacks.StringCallback() {
            @Override
            public void call(String value) {
                WebWebRtcSignalingClient.this.listener.disconnected(value);
            }
        }, new WebWebRtcCallbacks.ErrorCallback() {
            @Override
            public void call(String value) {
                WebWebRtcSignalingClient.this.listener.error(new RuntimeException(value));
            }
        });
    }

    @Override
    public void process(float deltaTime) {
        if (!isConnected()) {
            return;
        }
        pingTimer += deltaTime;
        if (pingTimer >= PING_INTERVAL_SECONDS) {
            pingTimer = 0.0f;
            send(pingMessage);
        }
    }

    @Override
    public void send(WebRtcSignalingMessage message) {
        if (socket >= 0) {
            send0(socket, codec.encode(message));
        }
    }

    @Override
    public boolean isConnected() {
        return socket >= 0 && isOpen0(socket);
    }

    @Override
    public String localPeerId() {
        return localPeerId;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (socket >= 0) {
                close0(socket);
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

    private static String separator(String url) {
        return url.indexOf('?') >= 0 ? "&" : "?";
    }

    private static String optionalPeerId(String peerId) {
        if (peerId == null || peerId.trim().isEmpty()) {
            return "";
        }
        return "&peerId=" + encode(peerId);
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        }
        catch (UnsupportedEncodingException exception) {
            throw new RuntimeException(exception);
        }
    }

    @JSBody(params = {"url", "message", "close", "error"}, script =
            "var root = window.__libfdxWebRtc = window.__libfdxWebRtc || { peers: [], channels: [], sockets: [] };"
                    + "var socket = new WebSocket(url);"
                    + "var id = root.sockets.length; root.sockets[id] = socket;"
                    + "socket.onmessage = function(event) { message(String(event.data)); };"
                    + "socket.onclose = function(event) { close(event && event.reason ? event.reason : 'closed'); };"
                    + "socket.onerror = function(event) { error(event && event.message ? event.message : 'WebSocket signaling error'); };"
                    + "return id;")
    private static native int connect0(String url, WebWebRtcCallbacks.StringCallback message,
            WebWebRtcCallbacks.StringCallback close, WebWebRtcCallbacks.ErrorCallback error);

    @JSBody(params = {"socket", "text"}, script = "var s = window.__libfdxWebRtc.sockets[socket]; if (s && s.readyState === WebSocket.OPEN) s.send(text);")
    private static native void send0(int socket, String text);

    @JSBody(params = {"socket"}, script = "var s = window.__libfdxWebRtc.sockets[socket]; return !!s && s.readyState === WebSocket.OPEN;")
    private static native boolean isOpen0(int socket);

    @JSBody(params = {"socket"}, script = "var s = window.__libfdxWebRtc.sockets[socket]; if (s) s.close(); window.__libfdxWebRtc.sockets[socket] = null;")
    private static native void close0(int socket);
}
