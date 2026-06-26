package io.github.libfdx.net.webrtc.platform;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.net.transport.NetDelivery;

/**
 * Provider-neutral WebRTC data channel.
 *
 * @author xpenatan
 */
public interface WebRtcDataChannel extends Disposable {
    String label();

    NetDelivery delivery();

    boolean isOpen();

    long bufferedAmount();

    void listener(WebRtcDataChannelListener listener);

    boolean send(byte[] bytes, int offset, int length);

    void close();
}
