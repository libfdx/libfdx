package io.github.libfdx.net.webrtc.desktop;

import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelBuffer;
import dev.onvoid.webrtc.RTCDataChannelObserver;
import dev.onvoid.webrtc.RTCDataChannelState;
import io.github.libfdx.net.transport.NetDelivery;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannel;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannelListener;
import java.nio.ByteBuffer;

/**
 * Desktop data-channel wrapper.
 *
 * @author xpenatan
 */
public final class DesktopWebRtcDataChannel implements WebRtcDataChannel {
    private final RTCDataChannel channel;
    private final NetDelivery delivery;
    private WebRtcDataChannelListener listener;
    private ByteBuffer sendBuffer = ByteBuffer.allocateDirect(0);
    private byte[] receiveScratch = new byte[4096];
    private boolean closed;

    DesktopWebRtcDataChannel(RTCDataChannel channel) {
        this.channel = channel;
        delivery = channel.isOrdered() ? NetDelivery.RELIABLE_ORDERED : NetDelivery.UNRELIABLE_UNORDERED;
    }

    @Override
    public String label() {
        return channel.getLabel();
    }

    @Override
    public NetDelivery delivery() {
        return delivery;
    }

    @Override
    public boolean isOpen() {
        return channel.getState() == RTCDataChannelState.OPEN;
    }

    @Override
    public long bufferedAmount() {
        return channel.getBufferedAmount();
    }

    @Override
    public void listener(WebRtcDataChannelListener listener) {
        this.listener = listener;
        channel.registerObserver(new RTCDataChannelObserver() {
            @Override
            public void onBufferedAmountChange(long previousAmount) {
            }

            @Override
            public void onStateChange() {
                WebRtcDataChannelListener current = DesktopWebRtcDataChannel.this.listener;
                if (current == null) {
                    return;
                }
                if (isOpen()) {
                    current.open();
                }
                else if (channel.getState() == RTCDataChannelState.CLOSED) {
                    current.closed();
                }
            }

            @Override
            public void onMessage(RTCDataChannelBuffer buffer) {
                WebRtcDataChannelListener current = DesktopWebRtcDataChannel.this.listener;
                if (current == null) {
                    return;
                }
                ByteBuffer data = buffer.data.slice();
                int length = data.remaining();
                ensureReceiveCapacity(length);
                data.get(receiveScratch, 0, length);
                current.message(receiveScratch, 0, length);
            }
        });
    }

    @Override
    public boolean send(byte[] bytes, int offset, int length) {
        ensureSendCapacity(length);
        sendBuffer.clear();
        sendBuffer.put(bytes, offset, length);
        sendBuffer.flip();
        try {
            channel.send(new RTCDataChannelBuffer(sendBuffer, true));
            return true;
        }
        catch (Exception exception) {
            WebRtcDataChannelListener current = listener;
            if (current != null) {
                current.error(exception);
            }
            return false;
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            channel.close();
            channel.unregisterObserver();
            channel.dispose();
        }
    }

    @Override
    public void dispose() {
        close();
    }

    @Override
    public boolean isDisposed() {
        return closed;
    }

    private void ensureSendCapacity(int length) {
        if (sendBuffer.capacity() != length) {
            sendBuffer = ByteBuffer.allocateDirect(length);
        }
    }

    private void ensureReceiveCapacity(int length) {
        if (receiveScratch.length < length) {
            receiveScratch = new byte[length];
        }
    }
}
