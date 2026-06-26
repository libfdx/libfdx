package io.github.libfdx.net.webrtc.android;

import android.content.Context;
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
    public WebRtcPeerConnection createPeerConnection(WebRtcEndpointSettings settings,
            final WebRtcPeerConnectionListener listener) {
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
        return new AndroidWebRtcPeerConnection(peerConnection);
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
