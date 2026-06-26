package io.github.libfdx.net.webrtc.android;

import io.github.libfdx.net.transport.NetDelivery;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannel;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannelListener;
import org.webrtc.DataChannel;
import java.nio.ByteBuffer;

/**
 * Android data-channel wrapper.
 *
 * @author xpenatan
 */
public final class AndroidWebRtcDataChannel implements WebRtcDataChannel {
    private final DataChannel channel;
    private final NetDelivery delivery;
    private WebRtcDataChannelListener listener;
    private ByteBuffer sendBuffer = ByteBuffer.allocateDirect(0);
    private byte[] receiveScratch = new byte[4096];
    private boolean closed;

    AndroidWebRtcDataChannel(DataChannel channel) {
        this.channel = channel;
        delivery = NetDelivery.RELIABLE_ORDERED;
    }

    @Override
    public String label() {
        return channel.label();
    }

    @Override
    public NetDelivery delivery() {
        return delivery;
    }

    @Override
    public boolean isOpen() {
        return channel.state() == DataChannel.State.OPEN;
    }

    @Override
    public long bufferedAmount() {
        return channel.bufferedAmount();
    }

    @Override
    public void listener(WebRtcDataChannelListener listener) {
        this.listener = listener;
        channel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long previousAmount) {
            }

            @Override
            public void onStateChange() {
                WebRtcDataChannelListener current = AndroidWebRtcDataChannel.this.listener;
                if (current == null) {
                    return;
                }
                if (isOpen()) {
                    current.open();
                }
                else if (channel.state() == DataChannel.State.CLOSED) {
                    current.closed();
                }
            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                WebRtcDataChannelListener current = AndroidWebRtcDataChannel.this.listener;
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
        return channel.send(new DataChannel.Buffer(sendBuffer, true));
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
