package io.github.libfdx.net.webrtc.signaling;

/**
 * Decides whether a peer may join a room.
 *
 * @author xpenatan
 */
public interface WebRtcRoomPolicy {
    boolean allowJoin(String roomId, String requestedPeerId, int peersInRoom);

    static WebRtcRoomPolicy allowAll() {
        return new WebRtcRoomPolicy() {
            @Override
            public boolean allowJoin(String roomId, String requestedPeerId, int peersInRoom) {
                return true;
            }
        };
    }
}
