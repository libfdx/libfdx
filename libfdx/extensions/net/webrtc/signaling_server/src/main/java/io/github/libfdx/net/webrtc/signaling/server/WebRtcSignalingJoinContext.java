package io.github.libfdx.net.webrtc.signaling.server;

/**
 * Describes an authenticated peer before it is accepted into a room.
 *
 * @author xpenatan
 */
public final class WebRtcSignalingJoinContext {
    private final WebRtcSignalingJoinRequest request;
    private final String peerId;
    private final int peersInRoom;
    private final Object session;

    WebRtcSignalingJoinContext(WebRtcSignalingJoinRequest request, String peerId, int peersInRoom, Object session) {
        this.request = request;
        this.peerId = peerId;
        this.peersInRoom = peersInRoom;
        this.session = session;
    }

    public WebRtcSignalingJoinRequest request() {
        return request;
    }

    public String roomId() {
        return request.roomId();
    }

    public String requestedPeerId() {
        return request.requestedPeerId();
    }

    public String peerId() {
        return peerId;
    }

    public int peersInRoom() {
        return peersInRoom;
    }

    public Object session() {
        return session;
    }
}
