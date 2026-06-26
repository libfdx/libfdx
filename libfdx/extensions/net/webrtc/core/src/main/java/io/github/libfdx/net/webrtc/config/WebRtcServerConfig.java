package io.github.libfdx.net.webrtc.config;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.net.config.NetServerConfig;
import io.github.libfdx.net.webrtc.WebRtcProvider;

/**
 * WebRTC host/server configuration.
 *
 * @author xpenatan
 */
public final class WebRtcServerConfig extends NetServerConfig {
    private final String signalingUrl;
    private final String roomId;
    private final String hostPeerId;
    private final int maxConnections;
    private final String[] stunServers;
    private final WebRtcTurnServer[] turnServers;
    private final boolean forceRelay;
    private final int iceRestartDelayMillis;
    private final int maxIceRestartAttempts;
    private final int iceBackoffBaseMillis;
    private final int unreliableBufferPackets;

    private WebRtcServerConfig(Builder builder) {
        super(builder);
        signalingUrl = builder.signalingUrl;
        roomId = builder.roomId;
        hostPeerId = builder.hostPeerId;
        maxConnections = validateNonNegative("max connections", builder.maxConnections);
        stunServers = builder.stunServers != null ? builder.stunServers.clone() : new String[0];
        turnServers = builder.turnServers != null ? builder.turnServers.clone() : new WebRtcTurnServer[0];
        forceRelay = builder.forceRelay;
        iceRestartDelayMillis = validateNonNegative("ICE restart delay", builder.iceRestartDelayMillis);
        maxIceRestartAttempts = validateNonNegative("max ICE restart attempts", builder.maxIceRestartAttempts);
        iceBackoffBaseMillis = validateNonNegative("ICE backoff base", builder.iceBackoffBaseMillis);
        unreliableBufferPackets = validateNonNegative("unreliable buffer packets", builder.unreliableBufferPackets);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String signalingUrl() {
        return signalingUrl;
    }

    public String roomId() {
        return roomId;
    }

    public String hostPeerId() {
        return hostPeerId;
    }

    public int maxConnections() {
        return maxConnections;
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

    private static int validateNonNegative(String name, int value) {
        if (value < 0) {
            throw new FdxException(name + " cannot be negative");
        }
        return value;
    }

    /**
     * Builder for WebRTC host/server configuration.
     *
     * @author xpenatan
     */
    public static final class Builder extends NetServerConfig.Builder<Builder> {
        private String signalingUrl;
        private String roomId;
        private String hostPeerId;
        private int maxConnections = 32;
        private String[] stunServers;
        private WebRtcTurnServer[] turnServers;
        private boolean forceRelay;
        private int iceRestartDelayMillis = 1000;
        private int maxIceRestartAttempts = 3;
        private int iceBackoffBaseMillis = 250;
        private int unreliableBufferPackets = 64;

        private Builder() {
            super(WebRtcProvider.ID);
        }

        public Builder signalingUrl(String signalingUrl) {
            this.signalingUrl = signalingUrl;
            return this;
        }

        public Builder roomId(String roomId) {
            this.roomId = roomId;
            return this;
        }

        public Builder hostPeerId(String hostPeerId) {
            this.hostPeerId = hostPeerId;
            return this;
        }

        public Builder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        public Builder stunServers(String... stunServers) {
            this.stunServers = stunServers != null ? stunServers.clone() : null;
            return this;
        }

        public Builder turnServers(WebRtcTurnServer... turnServers) {
            this.turnServers = turnServers != null ? turnServers.clone() : null;
            return this;
        }

        public Builder forceRelay(boolean forceRelay) {
            this.forceRelay = forceRelay;
            return this;
        }

        public Builder iceRestartDelayMillis(int iceRestartDelayMillis) {
            this.iceRestartDelayMillis = iceRestartDelayMillis;
            return this;
        }

        public Builder maxIceRestartAttempts(int maxIceRestartAttempts) {
            this.maxIceRestartAttempts = maxIceRestartAttempts;
            return this;
        }

        public Builder iceBackoffBaseMillis(int iceBackoffBaseMillis) {
            this.iceBackoffBaseMillis = iceBackoffBaseMillis;
            return this;
        }

        public Builder unreliableBufferPackets(int unreliableBufferPackets) {
            this.unreliableBufferPackets = unreliableBufferPackets;
            return this;
        }

        public WebRtcServerConfig build() {
            return new WebRtcServerConfig(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
