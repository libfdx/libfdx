package io.github.libfdx.net.webrtc.platform;

import io.github.libfdx.core.FdxException;

/**
 * WebRTC ICE candidate data.
 *
 * @author xpenatan
 */
public final class WebRtcIceCandidate {
    private final String candidate;
    private final String sdpMid;
    private final int sdpMLineIndex;

    public WebRtcIceCandidate(String candidate, String sdpMid, int sdpMLineIndex) {
        if (candidate == null || candidate.trim().isEmpty()) {
            throw new FdxException("WebRTC ICE candidate cannot be empty");
        }
        if (sdpMLineIndex < 0) {
            throw new FdxException("WebRTC ICE candidate m-line index cannot be negative");
        }
        this.candidate = candidate;
        this.sdpMid = sdpMid;
        this.sdpMLineIndex = sdpMLineIndex;
    }

    public String candidate() {
        return candidate;
    }

    public String sdpMid() {
        return sdpMid;
    }

    public int sdpMLineIndex() {
        return sdpMLineIndex;
    }
}
