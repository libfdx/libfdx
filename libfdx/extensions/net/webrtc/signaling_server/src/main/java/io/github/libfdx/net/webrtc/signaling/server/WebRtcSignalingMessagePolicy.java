package io.github.libfdx.net.webrtc.signaling.server;

/**
 * Authorizes inbound signaling messages from accepted peers.
 *
 * @author xpenatan
 */
public interface WebRtcSignalingMessagePolicy {
    WebRtcSignalingAccessDecision allowMessage(WebRtcSignalingMessageContext context);

    static WebRtcSignalingMessagePolicy allowAll() {
        return new WebRtcSignalingMessagePolicy() {
            @Override
            public WebRtcSignalingAccessDecision allowMessage(WebRtcSignalingMessageContext context) {
                return WebRtcSignalingAccessDecision.allow();
            }
        };
    }
}
