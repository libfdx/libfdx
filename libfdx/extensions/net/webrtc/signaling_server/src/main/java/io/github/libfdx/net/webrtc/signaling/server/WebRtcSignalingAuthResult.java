package io.github.libfdx.net.webrtc.signaling.server;

/**
 * Result returned by signaling authentication hooks.
 *
 * @author xpenatan
 */
public final class WebRtcSignalingAuthResult {
    private static final WebRtcSignalingAuthResult ACCEPTED =
            new WebRtcSignalingAuthResult(true, null, null);

    private final boolean accepted;
    private final String rejectionReason;
    private final Object session;

    private WebRtcSignalingAuthResult(boolean accepted, String rejectionReason, Object session) {
        this.accepted = accepted;
        this.rejectionReason = rejectionReason;
        this.session = session;
    }

    public static WebRtcSignalingAuthResult accepted() {
        return ACCEPTED;
    }

    public static WebRtcSignalingAuthResult accepted(Object session) {
        return session != null ? new WebRtcSignalingAuthResult(true, null, session) : ACCEPTED;
    }

    public static WebRtcSignalingAuthResult rejected(String reason) {
        return new WebRtcSignalingAuthResult(false, reason, null);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String rejectionReason() {
        return rejectionReason;
    }

    public Object session() {
        return session;
    }
}
