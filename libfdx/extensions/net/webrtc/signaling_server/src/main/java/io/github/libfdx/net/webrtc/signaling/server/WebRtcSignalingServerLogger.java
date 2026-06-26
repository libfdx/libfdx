package io.github.libfdx.net.webrtc.signaling.server;

/**
 * Receives optional signaling server log events.
 *
 * @author xpenatan
 */
public interface WebRtcSignalingServerLogger {
    void info(String message);

    void error(String message, Throwable error);

    static WebRtcSignalingServerLogger none() {
        return new WebRtcSignalingServerLogger() {
            @Override
            public void info(String message) {
            }

            @Override
            public void error(String message, Throwable error) {
            }
        };
    }
}
