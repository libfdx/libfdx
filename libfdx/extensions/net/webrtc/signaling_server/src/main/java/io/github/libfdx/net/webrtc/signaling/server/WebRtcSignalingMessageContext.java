package io.github.libfdx.net.webrtc.signaling.server;

import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessage;

/**
 * Describes an inbound signaling message before the server handles or relays it.
 *
 * @author xpenatan
 */
public final class WebRtcSignalingMessageContext {
    private final WebRtcSignalingMessage message;
    private final WebRtcSignalingJoinRequest joinRequest;
    private final String roomId;
    private final String sourcePeerId;
    private final String targetPeerId;
    private final int peersInRoom;
    private final Object session;

    WebRtcSignalingMessageContext(WebRtcSignalingMessage message, WebRtcSignalingJoinRequest joinRequest,
            String roomId, String sourcePeerId, String targetPeerId, int peersInRoom, Object session) {
        this.message = message;
        this.joinRequest = joinRequest;
        this.roomId = roomId;
        this.sourcePeerId = sourcePeerId;
        this.targetPeerId = targetPeerId;
        this.peersInRoom = peersInRoom;
        this.session = session;
    }

    public WebRtcSignalingMessage message() {
        return message;
    }

    public WebRtcSignalingJoinRequest joinRequest() {
        return joinRequest;
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

    public int peersInRoom() {
        return peersInRoom;
    }

    public Object session() {
        return session;
    }
}
