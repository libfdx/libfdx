package io.github.libfdx.net.webrtc.signaling.server;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.net.webrtc.signaling.WebRtcRoomPolicy;

/**
 * Configures the Java-WebSocket WebRTC signaling server.
 *
 * @author xpenatan
 */
public final class WebRtcSignalingServerConfig {
    private final String bindHost;
    private final int port;
    private final WebRtcRoomPolicy roomPolicy;
    private final WebRtcSignalingJoinPolicy joinPolicy;
    private final WebRtcPeerIdGenerator peerIdGenerator;
    private final WebRtcSignalingAuth auth;
    private final WebRtcSignalingMessagePolicy messagePolicy;
    private final WebRtcSignalingProcessingConfig processing;
    private final int maxPeersPerRoom;
    private final long idleTimeoutMillis;
    private final WebRtcSignalingServerLogger logger;

    private WebRtcSignalingServerConfig(Builder builder) {
        if (builder.bindHost == null || builder.bindHost.trim().isEmpty()) {
            throw new FdxException("WebRTC signaling bind host cannot be empty");
        }
        if (builder.port < 0 || builder.port > 65535) {
            throw new FdxException("WebRTC signaling port is invalid: " + builder.port);
        }
        if (builder.maxPeersPerRoom <= 0) {
            throw new FdxException("WebRTC signaling max peers per room must be positive");
        }
        if (builder.idleTimeoutMillis < 0) {
            throw new FdxException("WebRTC signaling idle timeout cannot be negative");
        }
        bindHost = builder.bindHost;
        port = builder.port;
        roomPolicy = builder.roomPolicy != null ? builder.roomPolicy : WebRtcRoomPolicy.allowAll();
        joinPolicy = builder.joinPolicy != null ? builder.joinPolicy : WebRtcSignalingJoinPolicy.allowAll();
        peerIdGenerator = builder.peerIdGenerator != null ? builder.peerIdGenerator
                : WebRtcPeerIdGenerator.requestedOrSequential();
        auth = builder.auth != null ? builder.auth : WebRtcSignalingAuth.allowAll();
        messagePolicy = builder.messagePolicy != null ? builder.messagePolicy
                : WebRtcSignalingMessagePolicy.allowAll();
        processing = builder.processing != null ? builder.processing : WebRtcSignalingProcessingConfig.defaults();
        maxPeersPerRoom = builder.maxPeersPerRoom;
        idleTimeoutMillis = builder.idleTimeoutMillis;
        logger = builder.logger != null ? builder.logger : WebRtcSignalingServerLogger.none();
    }

    /**
     * Creates a signaling server config builder.
     *
     * @param port the bind port, or {@code 0} to request an ephemeral port from
     *             the operating system
     * @return the config builder
     */
    public static Builder builder(int port) {
        return new Builder(port);
    }

    public String bindHost() {
        return bindHost;
    }

    /**
     * Returns the requested bind port. This remains {@code 0} when an
     * ephemeral port was requested; use
     * {@link WebRtcSignalingServer#port()} after startup for the resolved port.
     */
    public int port() {
        return port;
    }

    public WebRtcRoomPolicy roomPolicy() {
        return roomPolicy;
    }

    public WebRtcSignalingJoinPolicy joinPolicy() {
        return joinPolicy;
    }

    public WebRtcPeerIdGenerator peerIdGenerator() {
        return peerIdGenerator;
    }

    public WebRtcSignalingAuth auth() {
        return auth;
    }

    public WebRtcSignalingMessagePolicy messagePolicy() {
        return messagePolicy;
    }

    public WebRtcSignalingProcessingConfig processing() {
        return processing;
    }

    public int maxPeersPerRoom() {
        return maxPeersPerRoom;
    }

    public long idleTimeoutMillis() {
        return idleTimeoutMillis;
    }

    public WebRtcSignalingServerLogger logger() {
        return logger;
    }

    /**
     * Builds signaling server configs.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private String bindHost = "0.0.0.0";
        private final int port;
        private WebRtcRoomPolicy roomPolicy;
        private WebRtcSignalingJoinPolicy joinPolicy;
        private WebRtcPeerIdGenerator peerIdGenerator;
        private WebRtcSignalingAuth auth;
        private WebRtcSignalingMessagePolicy messagePolicy;
        private WebRtcSignalingProcessingConfig processing;
        private int maxPeersPerRoom = 32;
        private long idleTimeoutMillis = 30000;
        private WebRtcSignalingServerLogger logger;

        private Builder(int port) {
            this.port = port;
        }

        public Builder bindHost(String bindHost) {
            this.bindHost = bindHost;
            return this;
        }

        public Builder roomPolicy(WebRtcRoomPolicy roomPolicy) {
            this.roomPolicy = roomPolicy;
            return this;
        }

        public Builder joinPolicy(WebRtcSignalingJoinPolicy joinPolicy) {
            this.joinPolicy = joinPolicy;
            return this;
        }

        public Builder peerIdGenerator(WebRtcPeerIdGenerator peerIdGenerator) {
            this.peerIdGenerator = peerIdGenerator;
            return this;
        }

        public Builder auth(WebRtcSignalingAuth auth) {
            this.auth = auth;
            return this;
        }

        public Builder messagePolicy(WebRtcSignalingMessagePolicy messagePolicy) {
            this.messagePolicy = messagePolicy;
            return this;
        }

        public Builder processing(WebRtcSignalingProcessingConfig processing) {
            this.processing = processing;
            return this;
        }

        public Builder maxPeersPerRoom(int maxPeersPerRoom) {
            this.maxPeersPerRoom = maxPeersPerRoom;
            return this;
        }

        public Builder idleTimeoutMillis(long idleTimeoutMillis) {
            this.idleTimeoutMillis = idleTimeoutMillis;
            return this;
        }

        public Builder logger(WebRtcSignalingServerLogger logger) {
            this.logger = logger;
            return this;
        }

        public WebRtcSignalingServerConfig build() {
            return new WebRtcSignalingServerConfig(this);
        }
    }
}
