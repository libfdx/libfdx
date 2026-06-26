package io.github.libfdx.net.webrtc.platform;

/**
 * Receives provider data-channel events.
 *
 * @author xpenatan
 */
public interface WebRtcDataChannelListener {
    void open();

    void closed();

    void message(byte[] bytes, int offset, int length);

    void error(Throwable error);
}
