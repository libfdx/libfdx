package io.github.libfdx.net.webrtc.signaling.server;

/**
 * Allow or reject result returned by signaling policy hooks.
 *
 * @author xpenatan
 */
public final class WebRtcSignalingAccessDecision {
    private static final WebRtcSignalingAccessDecision ALLOW =
            new WebRtcSignalingAccessDecision(true, null);

    private final boolean allowed;
    private final String rejectionReason;

    private WebRtcSignalingAccessDecision(boolean allowed, String rejectionReason) {
        this.allowed = allowed;
        this.rejectionReason = rejectionReason;
    }

    public static WebRtcSignalingAccessDecision allow() {
        return ALLOW;
    }

    public static WebRtcSignalingAccessDecision reject(String reason) {
        return new WebRtcSignalingAccessDecision(false, reason);
    }

    public boolean allowed() {
        return allowed;
    }

    public String rejectionReason() {
        return rejectionReason;
    }
}
