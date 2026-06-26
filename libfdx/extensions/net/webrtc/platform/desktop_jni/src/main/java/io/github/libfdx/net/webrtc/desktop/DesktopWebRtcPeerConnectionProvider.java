package io.github.libfdx.net.webrtc.desktop;

import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.PeerConnectionObserver;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceServer;
import dev.onvoid.webrtc.RTCIceTransportPolicy;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCPeerConnectionState;
import io.github.libfdx.net.webrtc.config.WebRtcEndpointSettings;
import io.github.libfdx.net.webrtc.platform.WebRtcIceCandidate;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnection;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionListener;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionProvider;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionState;
import io.github.libfdx.net.webrtc.config.WebRtcTurnServer;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Desktop peer connection provider backed by webrtc-java.
 *
 * @author xpenatan
 */
public final class DesktopWebRtcPeerConnectionProvider implements WebRtcPeerConnectionProvider {
    private final PeerConnectionFactory factory = new PeerConnectionFactory();
    private boolean closed;

    @Override
    public WebRtcPeerConnection createPeerConnection(WebRtcEndpointSettings settings,
            final WebRtcPeerConnectionListener listener) {
        RTCConfiguration configuration = configuration(settings);
        RTCPeerConnection peerConnection = factory.createPeerConnection(configuration, new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(RTCIceCandidate candidate) {
                listener.iceCandidate(new WebRtcIceCandidate(candidate.sdp, candidate.sdpMid,
                        candidate.sdpMLineIndex));
            }

            @Override
            public void onDataChannel(dev.onvoid.webrtc.RTCDataChannel dataChannel) {
                listener.dataChannel(new DesktopWebRtcDataChannel(dataChannel));
            }

            @Override
            public void onConnectionChange(RTCPeerConnectionState state) {
                listener.stateChanged(map(state));
            }
        });
        return new DesktopWebRtcPeerConnection(peerConnection);
    }

    @Override
    public boolean isSupported() {
        return !closed;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            factory.dispose();
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

    private static RTCConfiguration configuration(WebRtcEndpointSettings settings) {
        RTCConfiguration configuration = new RTCConfiguration();
        configuration.iceServers = new ArrayList<RTCIceServer>();
        String[] stunServers = settings.stunServers();
        for (int i = 0; i < stunServers.length; i++) {
            RTCIceServer server = new RTCIceServer();
            server.urls = Collections.singletonList(stunServers[i]);
            configuration.iceServers.add(server);
        }
        WebRtcTurnServer[] turnServers = settings.turnServers();
        for (int i = 0; i < turnServers.length; i++) {
            WebRtcTurnServer source = turnServers[i];
            RTCIceServer server = new RTCIceServer();
            server.urls = Collections.singletonList(source.url());
            server.username = source.username();
            server.password = source.credential();
            configuration.iceServers.add(server);
        }
        configuration.iceTransportPolicy = settings.forceRelay() ? RTCIceTransportPolicy.RELAY
                : RTCIceTransportPolicy.ALL;
        return configuration;
    }

    private static WebRtcPeerConnectionState map(RTCPeerConnectionState state) {
        if (state == RTCPeerConnectionState.CONNECTING) {
            return WebRtcPeerConnectionState.CONNECTING;
        }
        if (state == RTCPeerConnectionState.CONNECTED) {
            return WebRtcPeerConnectionState.CONNECTED;
        }
        if (state == RTCPeerConnectionState.DISCONNECTED) {
            return WebRtcPeerConnectionState.DISCONNECTED;
        }
        if (state == RTCPeerConnectionState.FAILED) {
            return WebRtcPeerConnectionState.FAILED;
        }
        if (state == RTCPeerConnectionState.CLOSED) {
            return WebRtcPeerConnectionState.CLOSED;
        }
        return WebRtcPeerConnectionState.NEW;
    }
}
