package io.github.libfdx.net.webrtc.signaling;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.json.JsonValue;

/**
 * Describes one signaling message exchanged through the room server.
 *
 * @author xpenatan
 */
public final class WebRtcSignalingMessage {
    private final WebRtcSignalingMessageType type;
    private final String roomId;
    private final String sourcePeerId;
    private final String targetPeerId;
    private final JsonValue payload;

    private WebRtcSignalingMessage(Builder builder) {
        if (builder.type == null) {
            throw new FdxException("WebRTC signaling message type cannot be null");
        }
        type = builder.type;
        roomId = builder.roomId;
        sourcePeerId = builder.sourcePeerId;
        targetPeerId = builder.targetPeerId;
        payload = builder.payload != null ? builder.payload : JsonValue.object();
    }

    public static Builder builder(WebRtcSignalingMessageType type) {
        return new Builder(type);
    }

    public WebRtcSignalingMessageType type() {
        return type;
    }

    public String roomId() {
        return roomId;
    }

    public String sourcePeerId() {
        return sourcePeerId;
    }

    public String targetPeerId() {
        return targetPeerId;
    }

    public JsonValue payload() {
        return payload;
    }

    /**
     * Builds signaling messages.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private final WebRtcSignalingMessageType type;
        private String roomId;
        private String sourcePeerId;
        private String targetPeerId;
        private JsonValue payload;

        private Builder(WebRtcSignalingMessageType type) {
            this.type = type;
        }

        public Builder roomId(String roomId) {
            this.roomId = roomId;
            return this;
        }

        public Builder sourcePeerId(String sourcePeerId) {
            this.sourcePeerId = sourcePeerId;
            return this;
        }

        public Builder targetPeerId(String targetPeerId) {
            this.targetPeerId = targetPeerId;
            return this;
        }

        public Builder payload(JsonValue payload) {
            this.payload = payload;
            return this;
        }

        public WebRtcSignalingMessage build() {
            return new WebRtcSignalingMessage(this);
        }
    }
}
