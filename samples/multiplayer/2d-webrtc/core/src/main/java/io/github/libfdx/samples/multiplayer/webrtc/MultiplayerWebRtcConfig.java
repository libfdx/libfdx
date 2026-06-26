package io.github.libfdx.samples.multiplayer.webrtc;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.net.webrtc.platform.WebRtcPlatformFactory;

/**
 * Configures the WebRTC multiplayer sample.
 *
 * @author xpenatan
 */
public final class MultiplayerWebRtcConfig {
    private final WebRtcPlatformFactory platformFactory;
    private final String signalingUrl;
    private final String lobbyRoomId;
    private final String playerName;
    private final String hostRoomId;
    private final boolean autoHost;
    private final String autoJoinRoom;
    private final long exitAfterFrames;
    private final boolean validationEnabled;
    private final String validationSelection;

    private MultiplayerWebRtcConfig(Builder builder) {
        if (builder.platformFactory == null) {
            throw new FdxException("WebRTC platform factory cannot be null");
        }
        platformFactory = builder.platformFactory;
        signalingUrl = valueOrDefault(builder.signalingUrl, "ws://127.0.0.1:7777");
        lobbyRoomId = valueOrDefault(builder.lobbyRoomId, "libfdx-multiplayer-2d-lobby");
        playerName = valueOrDefault(builder.playerName, "Player");
        hostRoomId = trimOrNull(builder.hostRoomId);
        autoHost = builder.autoHost;
        autoJoinRoom = trimOrNull(builder.autoJoinRoom);
        exitAfterFrames = Math.max(0L, builder.exitAfterFrames);
        validationEnabled = builder.validationEnabled;
        validationSelection = trimOrNull(builder.validationSelection);
    }

    public static Builder builder(WebRtcPlatformFactory platformFactory) {
        return new Builder(platformFactory);
    }

    public WebRtcPlatformFactory platformFactory() {
        return platformFactory;
    }

    public String signalingUrl() {
        return signalingUrl;
    }

    public String lobbyRoomId() {
        return lobbyRoomId;
    }

    public String playerName() {
        return playerName;
    }

    public String hostRoomId() {
        return hostRoomId;
    }

    public boolean autoHost() {
        return autoHost;
    }

    public String autoJoinRoom() {
        return autoJoinRoom;
    }

    public long exitAfterFrames() {
        return exitAfterFrames;
    }

    public boolean validationEnabled() {
        return validationEnabled;
    }

    public String validationSelection() {
        return validationSelection;
    }

    private static String valueOrDefault(String value, String fallback) {
        String trimmed = trimOrNull(value);
        return trimmed != null ? trimmed : fallback;
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 0 ? trimmed : null;
    }

    /**
     * Builds sample configs.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private final WebRtcPlatformFactory platformFactory;
        private String signalingUrl;
        private String lobbyRoomId;
        private String playerName;
        private String hostRoomId;
        private boolean autoHost;
        private String autoJoinRoom;
        private long exitAfterFrames;
        private boolean validationEnabled;
        private String validationSelection;

        private Builder(WebRtcPlatformFactory platformFactory) {
            this.platformFactory = platformFactory;
        }

        public Builder signalingUrl(String signalingUrl) {
            this.signalingUrl = signalingUrl;
            return this;
        }

        public Builder lobbyRoomId(String lobbyRoomId) {
            this.lobbyRoomId = lobbyRoomId;
            return this;
        }

        public Builder playerName(String playerName) {
            this.playerName = playerName;
            return this;
        }

        public Builder hostRoomId(String hostRoomId) {
            this.hostRoomId = hostRoomId;
            return this;
        }

        public Builder autoHost(boolean autoHost) {
            this.autoHost = autoHost;
            return this;
        }

        public Builder autoJoinRoom(String autoJoinRoom) {
            this.autoJoinRoom = autoJoinRoom;
            return this;
        }

        public Builder exitAfterFrames(long exitAfterFrames) {
            this.exitAfterFrames = exitAfterFrames;
            return this;
        }

        public Builder validationEnabled(boolean validationEnabled) {
            this.validationEnabled = validationEnabled;
            return this;
        }

        public Builder validationSelection(String validationSelection) {
            this.validationSelection = validationSelection;
            return this;
        }

        public MultiplayerWebRtcConfig build() {
            return new MultiplayerWebRtcConfig(this);
        }
    }
}
