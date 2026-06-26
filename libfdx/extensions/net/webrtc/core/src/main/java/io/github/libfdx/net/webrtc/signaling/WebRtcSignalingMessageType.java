package io.github.libfdx.net.webrtc.signaling;

import io.github.libfdx.core.FdxException;

/**
 * WebRTC signaling protocol message types.
 *
 * @author xpenatan
 */
public enum WebRtcSignalingMessageType {
    WELCOME("welcome"),
    PEER_JOINED("peer_joined"),
    PEER_LEFT("peer_left"),
    OFFER("offer"),
    ANSWER("answer"),
    ICE("ice"),
    CONNECT_REQUEST("connect_request"),
    ROOM_REGISTER("room_register"),
    ROOM_UNREGISTER("room_unregister"),
    ROOM_LIST("room_list"),
    ROOM_LIST_CHANGED("room_list_changed"),
    ERROR("error"),
    PING("ping"),
    PONG("pong");

    private final String wireName;

    WebRtcSignalingMessageType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static WebRtcSignalingMessageType fromWireName(String wireName) {
        for (int i = 0; i < values().length; i++) {
            WebRtcSignalingMessageType type = values()[i];
            if (type.wireName.equals(wireName)) {
                return type;
            }
        }
        throw new FdxException("Unknown WebRTC signaling message type: " + wireName);
    }
}
