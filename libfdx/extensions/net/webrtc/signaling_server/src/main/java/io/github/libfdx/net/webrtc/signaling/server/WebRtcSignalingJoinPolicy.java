package io.github.libfdx.net.webrtc.signaling.server;

/**
 * Authorizes authenticated peers before they join a signaling room.
 *
 * @author xpenatan
 */
public interface WebRtcSignalingJoinPolicy {
    WebRtcSignalingAccessDecision allowJoin(WebRtcSignalingJoinContext context);

    static WebRtcSignalingJoinPolicy allowAll() {
        return new WebRtcSignalingJoinPolicy() {
            @Override
            public WebRtcSignalingAccessDecision allowJoin(WebRtcSignalingJoinContext context) {
                return WebRtcSignalingAccessDecision.allow();
            }
        };
    }
}
