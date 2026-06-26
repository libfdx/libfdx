package io.github.libfdx.net.webrtc.config;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.net.config.NetEndpointConfig;

/**
 * Normalized WebRTC endpoint settings shared by client, server, and peer endpoints.
 *
 * @author xpenatan
 */
public final class WebRtcEndpointSettings {
    private final String signalingUrl;
    private final String roomId;
    private final String peerId;
    private final String[] stunServers;
    private final WebRtcTurnServer[] turnServers;
    private final boolean forceRelay;
    private final int iceRestartDelayMillis;
    private final int maxIceRestartAttempts;
    private final int iceBackoffBaseMillis;
    private final int unreliableBufferPackets;

    private WebRtcEndpointSettings(String signalingUrl, String roomId, String peerId, String[] stunServers,
            WebRtcTurnServer[] turnServers, boolean forceRelay, int iceRestartDelayMillis, int maxIceRestartAttempts,
            int iceBackoffBaseMillis, int unreliableBufferPackets) {
        if (signalingUrl == null || signalingUrl.trim().isEmpty()) {
            throw new FdxException("WebRTC signaling URL cannot be empty");
        }
        if (roomId == null || roomId.trim().isEmpty()) {
            throw new FdxException("WebRTC room ID cannot be empty");
        }
        this.signalingUrl = signalingUrl;
        this.roomId = roomId;
        this.peerId = peerId;
        this.stunServers = stunServers != null ? stunServers.clone() : new String[0];
        this.turnServers = turnServers != null ? turnServers.clone() : new WebRtcTurnServer[0];
        this.forceRelay = forceRelay;
        this.iceRestartDelayMillis = iceRestartDelayMillis;
        this.maxIceRestartAttempts = maxIceRestartAttempts;
        this.iceBackoffBaseMillis = iceBackoffBaseMillis;
        this.unreliableBufferPackets = unreliableBufferPackets;
    }

    public static WebRtcEndpointSettings from(NetEndpointConfig config) {
        if (config instanceof WebRtcClientConfig) {
            WebRtcClientConfig webRtc = (WebRtcClientConfig) config;
            return new WebRtcEndpointSettings(webRtc.signalingUrl(), webRtc.roomId(), webRtc.peerId(),
                    webRtc.stunServers(), webRtc.turnServers(), webRtc.forceRelay(),
                    webRtc.iceRestartDelayMillis(), webRtc.maxIceRestartAttempts(), webRtc.iceBackoffBaseMillis(),
                    webRtc.unreliableBufferPackets());
        }
        if (config instanceof WebRtcServerConfig) {
            WebRtcServerConfig webRtc = (WebRtcServerConfig) config;
            return new WebRtcEndpointSettings(webRtc.signalingUrl(), webRtc.roomId(), webRtc.hostPeerId(),
                    webRtc.stunServers(), webRtc.turnServers(), webRtc.forceRelay(),
                    webRtc.iceRestartDelayMillis(), webRtc.maxIceRestartAttempts(), webRtc.iceBackoffBaseMillis(),
                    webRtc.unreliableBufferPackets());
        }
        if (config instanceof WebRtcPeerConfig) {
            WebRtcPeerConfig webRtc = (WebRtcPeerConfig) config;
            return new WebRtcEndpointSettings(webRtc.signalingUrl(), webRtc.roomId(), webRtc.peerId(),
                    webRtc.stunServers(), webRtc.turnServers(), webRtc.forceRelay(),
                    webRtc.iceRestartDelayMillis(), webRtc.maxIceRestartAttempts(), webRtc.iceBackoffBaseMillis(),
                    webRtc.unreliableBufferPackets());
        }
        throw new FdxException("WebRTC endpoint requires a WebRTC config");
    }

    public String signalingUrl() {
        return signalingUrl;
    }

    public String roomId() {
        return roomId;
    }

    public String peerId() {
        return peerId;
    }

    public String[] stunServers() {
        return stunServers.clone();
    }

    public WebRtcTurnServer[] turnServers() {
        return turnServers.clone();
    }

    public boolean forceRelay() {
        return forceRelay;
    }

    public int iceRestartDelayMillis() {
        return iceRestartDelayMillis;
    }

    public int maxIceRestartAttempts() {
        return maxIceRestartAttempts;
    }

    public int iceBackoffBaseMillis() {
        return iceBackoffBaseMillis;
    }

    public int unreliableBufferPackets() {
        return unreliableBufferPackets;
    }
}
