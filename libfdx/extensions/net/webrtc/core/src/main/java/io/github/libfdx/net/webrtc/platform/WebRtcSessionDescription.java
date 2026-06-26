package io.github.libfdx.net.webrtc.platform;

import io.github.libfdx.core.FdxException;

/**
 * WebRTC SDP description.
 *
 * @author xpenatan
 */
public final class WebRtcSessionDescription {
    private final Type type;
    private final String sdp;

    public WebRtcSessionDescription(Type type, String sdp) {
        if (type == null) {
            throw new FdxException("WebRTC session description type cannot be null");
        }
        if (sdp == null || sdp.trim().isEmpty()) {
            throw new FdxException("WebRTC session description SDP cannot be empty");
        }
        this.type = type;
        this.sdp = sdp;
    }

    public Type type() {
        return type;
    }

    public String sdp() {
        return sdp;
    }

    /**
     * SDP description type.
     *
     * @author xpenatan
     */
    public enum Type {
        OFFER("offer"),
        ANSWER("answer");

        private final String wireName;

        Type(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static Type fromWireName(String wireName) {
            for (int i = 0; i < values().length; i++) {
                Type type = values()[i];
                if (type.wireName.equals(wireName)) {
                    return type;
                }
            }
            throw new FdxException("Unknown WebRTC session description type: " + wireName);
        }
    }
}
