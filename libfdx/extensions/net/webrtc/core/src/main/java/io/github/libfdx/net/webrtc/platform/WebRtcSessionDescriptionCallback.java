package io.github.libfdx.net.webrtc.platform;

/**
 * Receives asynchronous SDP creation results.
 *
 * @author xpenatan
 */
public interface WebRtcSessionDescriptionCallback {
    void success(WebRtcSessionDescription description);

    void error(Throwable error);
}
