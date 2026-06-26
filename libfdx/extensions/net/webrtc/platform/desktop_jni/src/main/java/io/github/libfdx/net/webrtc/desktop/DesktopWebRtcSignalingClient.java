package io.github.libfdx.net.webrtc.desktop;

import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingClient;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingCodec;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingListener;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessage;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessageType;
import io.github.libfdx.net.websocket.WebSocketClient;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import org.java_websocket.handshake.ServerHandshake;

/**
 * Desktop Java-WebSocket signaling client.
 *
 * @author xpenatan
 */
public final class DesktopWebRtcSignalingClient implements WebRtcSignalingClient {
    private static final float PING_INTERVAL_SECONDS = 10.0f;

    private final WebRtcSignalingCodec codec = new WebRtcSignalingCodec();
    private final WebRtcSignalingMessage pingMessage =
            WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.PING).build();
    private org.java_websocket.client.WebSocketClient socket;
    private WebRtcSignalingListener listener;
    private String localPeerId;
    private float pingTimer;
    private boolean closed;

    @Override
    public void connect(String signalingUrl, String roomId, String requestedPeerId,
            WebRtcSignalingListener listener) {
        this.listener = listener;
        URI uri = URI.create(signalingUrl + separator(signalingUrl) + "room=" + encode(roomId)
                + optionalPeerId(requestedPeerId));
        socket = new org.java_websocket.client.WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
            }

            @Override
            public void onMessage(String message) {
                WebRtcSignalingMessage decoded = codec.decode(message);
                if (decoded.type() == WebRtcSignalingMessageType.WELCOME) {
                    localPeerId = decoded.targetPeerId();
                    if (localPeerId == null) {
                        localPeerId = decoded.payload().stringValue("peerId", null);
                    }
                    DesktopWebRtcSignalingClient.this.listener.connected(localPeerId);
                }
                DesktopWebRtcSignalingClient.this.listener.message(decoded);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                DesktopWebRtcSignalingClient.this.listener.disconnected(reason);
            }

            @Override
            public void onError(Exception ex) {
                DesktopWebRtcSignalingClient.this.listener.error(ex);
            }
        };
        socket.connect();
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
        if (socket != null && socket.isOpen()) {
            socket.send(codec.encode(message));
        }
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isOpen();
    }

    @Override
    public String localPeerId() {
        return localPeerId;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (socket != null) {
                socket.close();
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
}
