package io.github.libfdx.net.webrtc.config;

import io.github.libfdx.core.FdxException;

/**
 * Describes a TURN server used by WebRTC ICE negotiation.
 *
 * @author xpenatan
 */
public final class WebRtcTurnServer {
    private final String url;
    private final String username;
    private final String credential;

    private WebRtcTurnServer(String url, String username, String credential) {
        if (url == null || url.trim().isEmpty()) {
            throw new FdxException("TURN server URL cannot be empty");
        }
        this.url = url;
        this.username = username;
        this.credential = credential;
    }

    public static WebRtcTurnServer of(String url) {
        return new WebRtcTurnServer(url, null, null);
    }

    public static WebRtcTurnServer authenticated(String url, String username, String credential) {
        return new WebRtcTurnServer(url, username, credential);
    }

    public String url() {
        return url;
    }

    public String username() {
        return username;
    }

    public String credential() {
        return credential;
    }
}
