package io.github.libfdx.net.webrtc.signaling.server;

/**
 * Generates peer IDs for accepted signaling clients.
 *
 * @author xpenatan
 */
public interface WebRtcPeerIdGenerator {
    String generatePeerId(String roomId, String requestedPeerId, int peersInRoom);

    static WebRtcPeerIdGenerator requestedOrSequential() {
        return new WebRtcPeerIdGenerator() {
            @Override
            public String generatePeerId(String roomId, String requestedPeerId, int peersInRoom) {
                if (requestedPeerId != null && !requestedPeerId.trim().isEmpty()) {
                    return requestedPeerId;
                }
                return "peer-" + (peersInRoom + 1);
            }
        };
    }
}
