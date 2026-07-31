package io.github.libfdx.net.webrtc.web;

import io.github.libfdx.net.transport.NetDelivery;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannel;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannelListener;
import org.teavm.jso.JSBody;
import java.util.Base64;

/**
 * Browser data-channel wrapper.
 *
 * @author xpenatan
 */
public final class WebWebRtcDataChannel implements WebRtcDataChannel {
    private final int handle;
    private final String label;
    private final NetDelivery delivery;
    private WebRtcDataChannelListener listener;
    private byte[] receiveScratch = new byte[4096];
    private boolean closed;

    WebWebRtcDataChannel(int handle, String label, boolean ordered) {
        this.handle = handle;
        this.label = label;
        delivery = ordered ? NetDelivery.RELIABLE_ORDERED : NetDelivery.UNRELIABLE_UNORDERED;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public NetDelivery delivery() {
        return delivery;
    }

    @Override
    public boolean isOpen() {
        return isOpen0(handle);
    }

    @Override
    public long bufferedAmount() {
        return bufferedAmount0(handle);
    }

    @Override
    public void listener(WebRtcDataChannelListener listener) {
        this.listener = listener;
        listen0(handle, new WebWebRtcCallbacks.BytesCallback() {
            @Override
            public void call(String base64) {
                byte[] decoded = Base64.getDecoder().decode(base64);
                ensureReceiveCapacity(decoded.length);
                System.arraycopy(decoded, 0, receiveScratch, 0, decoded.length);
                WebRtcDataChannelListener current = WebWebRtcDataChannel.this.listener;
                if (current != null) {
                    current.message(receiveScratch, 0, decoded.length);
                }
            }
        }, new WebWebRtcCallbacks.StringCallback() {
            @Override
            public void call(String value) {
                WebRtcDataChannelListener current = WebWebRtcDataChannel.this.listener;
                if (current == null) {
                    return;
                }
                if ("open".equals(value)) {
                    current.open();
                }
                else if ("closed".equals(value)) {
                    current.closed();
                }
            }
        }, new WebWebRtcCallbacks.ErrorCallback() {
            @Override
            public void call(String value) {
                WebRtcDataChannelListener current = WebWebRtcDataChannel.this.listener;
                if (current != null) {
                    current.error(new RuntimeException(value));
                }
            }
        });
    }

    @Override
    public boolean send(byte[] bytes, int offset, int length) {
        return send0(handle, bytes, offset, length);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            close0(handle);
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

    private void ensureReceiveCapacity(int length) {
        if (receiveScratch.length < length) {
            receiveScratch = new byte[length];
        }
    }

    @JSBody(params = {"channel"}, script = "var c = window.__libfdxWebRtc.channels[channel]; return !!c && c.readyState === 'open';")
    private static native boolean isOpen0(int channel);

    @JSBody(params = {"channel"}, script = "var c = window.__libfdxWebRtc.channels[channel]; return c ? c.bufferedAmount : 0;")
    private static native int bufferedAmount0(int channel);

    @JSBody(params = {"channel", "bytes", "offset", "length"}, script =
            "var dataChannel = window.__libfdxWebRtc.channels[channel];"
                    + "if (!dataChannel || dataChannel.readyState !== 'open') return false;"
                    + "var out = new Uint8Array(length);"
                    + "for (var i = 0; i < length; i++) out[i] = bytes[offset + i] & 255;"
                    + "dataChannel.send(out.buffer);"
                    + "return true;")
    private static native boolean send0(int channel, byte[] bytes, int offset, int length);

    @JSBody(params = {"channel", "bytes", "state", "error"}, script =
            "var dataChannel = window.__libfdxWebRtc.channels[channel];"
                    + "if (!dataChannel) return;"
                    + "dataChannel.binaryType = 'arraybuffer';"
                    + "dataChannel.onopen = function() { state('open'); };"
                    + "dataChannel.onclose = function() { state('closed'); };"
                    + "dataChannel.onerror = function(event) { error(event && event.message ? event.message : 'WebRTC data channel error'); };"
                    + "dataChannel.onmessage = function(event) {"
                    + "  function emit(buffer) {"
                    + "    var u8 = new Uint8Array(buffer); var s = '';"
                    + "    for (var i = 0; i < u8.length; i++) s += String.fromCharCode(u8[i]);"
                    + "    bytes(btoa(s));"
                    + "  }"
                    + "  if (event.data instanceof ArrayBuffer) emit(event.data);"
                    + "  else if (event.data && event.data.arrayBuffer) event.data.arrayBuffer().then(emit);"
                    + "};"
                    + "if (dataChannel.readyState === 'open') state('open');")
    private static native void listen0(int channel, WebWebRtcCallbacks.BytesCallback bytes,
            WebWebRtcCallbacks.StringCallback state, WebWebRtcCallbacks.ErrorCallback error);

    @JSBody(params = {"channel"}, script = "var c = window.__libfdxWebRtc.channels[channel]; if (c) c.close(); window.__libfdxWebRtc.channels[channel] = null;")
    private static native void close0(int channel);
}
