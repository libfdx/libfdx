package io.github.libfdx.net.webrtc.android;

import io.github.libfdx.collections.Array;
import android.content.Context;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.net.webrtc.config.WebRtcEndpointSettings;
import io.github.libfdx.net.webrtc.platform.WebRtcIceCandidate;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnection;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionListener;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionProvider;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionState;
import io.github.libfdx.net.webrtc.config.WebRtcTurnServer;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import java.util.ArrayList;

/**
 * Android peer connection provider backed by android-webrtc.
 *
 * @author xpenatan
 */
public final class AndroidWebRtcPeerConnectionProvider implements WebRtcPeerConnectionProvider {
    private static boolean initialized;

    private final PeerConnectionFactory factory;
    private final Array<AndroidWebRtcPeerConnection> connections =
            new Array<AndroidWebRtcPeerConnection>();
    private boolean closed;

    AndroidWebRtcPeerConnectionProvider(Context context) {
        Context appContext = context.getApplicationContext();
        if (!initialized) {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .createInitializationOptions());
            initialized = true;
        }
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory();
    }

    @Override
    public synchronized WebRtcPeerConnection createPeerConnection(WebRtcEndpointSettings settings,
            final WebRtcPeerConnectionListener listener) {
        if (closed) {
            throw new FdxException("Android WebRTC peer connection provider is disposed");
        }
        PeerConnection.RTCConfiguration configuration = configuration(settings);
        PeerConnection peerConnection = factory.createPeerConnection(configuration, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            }

            @Override
            public void onIceConnectionReceivingChange(boolean receiving) {
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            }

            @Override
            public void onIceCandidate(IceCandidate candidate) {
                listener.iceCandidate(new WebRtcIceCandidate(candidate.sdp, candidate.sdpMid,
                        candidate.sdpMLineIndex));
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] candidates) {
            }

            @Override
            public void onAddStream(MediaStream stream) {
            }

            @Override
            public void onRemoveStream(MediaStream stream) {
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {
                listener.dataChannel(new AndroidWebRtcDataChannel(dataChannel));
            }

            @Override
            public void onRenegotiationNeeded() {
            }

            @Override
            public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
            }

            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                listener.stateChanged(map(newState));
            }
        });
        if (peerConnection == null) {
            throw new FdxException("Android WebRTC peer connection creation failed");
        }
        AndroidWebRtcPeerConnection connection = new AndroidWebRtcPeerConnection(peerConnection, listener, this);
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
                AndroidWebRtcPeerConnection connection = connections.get(connections.size() - 1);
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

    synchronized void connectionClosed(AndroidWebRtcPeerConnection connection) {
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
        throw new FdxException("Android WebRTC peer connection provider disposal failed", failure);
    }

    private static PeerConnection.RTCConfiguration configuration(WebRtcEndpointSettings settings) {
        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<PeerConnection.IceServer>();
        String[] stunServers = settings.stunServers();
        for (int i = 0; i < stunServers.length; i++) {
            iceServers.add(PeerConnection.IceServer.builder(stunServers[i]).createIceServer());
        }
        WebRtcTurnServer[] turnServers = settings.turnServers();
        for (int i = 0; i < turnServers.length; i++) {
            WebRtcTurnServer source = turnServers[i];
            iceServers.add(PeerConnection.IceServer.builder(source.url())
                    .setUsername(source.username())
                    .setPassword(source.credential())
                    .createIceServer());
        }
        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(iceServers);
        configuration.iceTransportsType = settings.forceRelay() ? PeerConnection.IceTransportsType.RELAY
                : PeerConnection.IceTransportsType.ALL;
        return configuration;
    }

    private static WebRtcPeerConnectionState map(PeerConnection.PeerConnectionState state) {
        if (state == PeerConnection.PeerConnectionState.CONNECTING) {
            return WebRtcPeerConnectionState.CONNECTING;
        }
        if (state == PeerConnection.PeerConnectionState.CONNECTED) {
            return WebRtcPeerConnectionState.CONNECTED;
        }
        if (state == PeerConnection.PeerConnectionState.DISCONNECTED) {
            return WebRtcPeerConnectionState.DISCONNECTED;
        }
        if (state == PeerConnection.PeerConnectionState.FAILED) {
            return WebRtcPeerConnectionState.FAILED;
        }
        if (state == PeerConnection.PeerConnectionState.CLOSED) {
            return WebRtcPeerConnectionState.CLOSED;
        }
        return WebRtcPeerConnectionState.NEW;
    }
}
