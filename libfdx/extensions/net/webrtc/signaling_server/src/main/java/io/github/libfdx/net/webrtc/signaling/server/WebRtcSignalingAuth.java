package io.github.libfdx.net.webrtc.signaling.server;

/**
 * Accepts or rejects signaling room joins.
 *
 * @author xpenatan
 */
public interface WebRtcSignalingAuth {
    boolean allow(WebRtcSignalingJoinRequest request);

    default WebRtcSignalingAuthResult authenticate(WebRtcSignalingJoinRequest request) {
        return allow(request) ? WebRtcSignalingAuthResult.accepted()
                : WebRtcSignalingAuthResult.rejected("auth rejected");
    }

    static WebRtcSignalingAuth allowAll() {
        return new WebRtcSignalingAuth() {
            @Override
            public boolean allow(WebRtcSignalingJoinRequest request) {
                return true;
            }
        };
    }
}
