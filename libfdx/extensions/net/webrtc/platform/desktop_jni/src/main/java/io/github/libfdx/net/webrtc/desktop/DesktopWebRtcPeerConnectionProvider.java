package io.github.libfdx.net.webrtc.desktop;

import io.github.libfdx.collections.Array;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.PeerConnectionObserver;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceServer;
import dev.onvoid.webrtc.RTCIceTransportPolicy;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCPeerConnectionState;
import dev.onvoid.webrtc.media.audio.HeadlessAudioDeviceModule;
import io.github.libfdx.core.FdxException;
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
    private final HeadlessAudioDeviceModule audioDeviceModule;
    private final PeerConnectionFactory factory;
    private final Array<DesktopWebRtcPeerConnection> connections =
            new Array<DesktopWebRtcPeerConnection>();
    private boolean closed;

    public DesktopWebRtcPeerConnectionProvider() {
        audioDeviceModule = new HeadlessAudioDeviceModule();
        try {
            factory = new PeerConnectionFactory(audioDeviceModule);
        }
        catch (RuntimeException | Error failure) {
            try {
                audioDeviceModule.dispose();
            }
            catch (Throwable disposeFailure) {
                failure.addSuppressed(disposeFailure);
            }
            throw failure;
        }
    }

    @Override
    public synchronized WebRtcPeerConnection createPeerConnection(WebRtcEndpointSettings settings,
            final WebRtcPeerConnectionListener listener) {
        if (closed) {
            throw new FdxException("Desktop WebRTC peer connection provider is disposed");
        }
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
        if (peerConnection == null) {
            throw new FdxException("Desktop WebRTC peer connection creation failed");
        }
        DesktopWebRtcPeerConnection connection = new DesktopWebRtcPeerConnection(peerConnection, listener, this);
        connections.add(connection);
        return connection;
    }

    @Override
    public synchronized boolean isSupported() {
        return !closed;
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            Throwable failure = null;
            while (!connections.isEmpty()) {
                DesktopWebRtcPeerConnection connection = connections.get(connections.size() - 1);
                try {
                    connection.close();
                }
                catch (Throwable throwable) {
                    failure = firstFailure(failure, throwable);
                    connections.removeValue(connection, true);
                }
            }
            try {
                factory.dispose();
            }
            catch (Throwable throwable) {
                failure = firstFailure(failure, throwable);
            }
            try {
                audioDeviceModule.dispose();
            }
            catch (Throwable throwable) {
                failure = firstFailure(failure, throwable);
            }
            rethrow(failure);
        }
    }

    @Override
    public void dispose() {
        close();
    }

    @Override
    public synchronized boolean isDisposed() {
        return closed;
    }

    synchronized void connectionClosed(DesktopWebRtcPeerConnection connection) {
        connections.removeValue(connection, true);
    }

    private static Throwable firstFailure(Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    private static void rethrow(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new FdxException("Desktop WebRTC peer connection provider disposal failed", failure);
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
