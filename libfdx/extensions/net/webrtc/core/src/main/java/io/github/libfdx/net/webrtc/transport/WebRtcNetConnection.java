package io.github.libfdx.net.webrtc.transport;

import io.github.libfdx.collections.IntMap;
import io.github.libfdx.collections.ObjectIterator;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.net.buffer.NetBuffer;
import io.github.libfdx.net.config.NetChannelConfig;
import io.github.libfdx.net.transform.NetPacketTransform;
import io.github.libfdx.net.transform.NetTransformContext;
import io.github.libfdx.net.transform.NetTransformResult;
import io.github.libfdx.net.transport.NetConnection;
import io.github.libfdx.net.transport.NetConnectionState;
import io.github.libfdx.net.transport.NetDelivery;
import io.github.libfdx.net.transport.NetSendResult;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannel;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannelListener;
import io.github.libfdx.net.webrtc.platform.WebRtcIceCandidate;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnection;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionState;
import io.github.libfdx.net.webrtc.platform.WebRtcSessionDescription;
import io.github.libfdx.net.webrtc.platform.WebRtcSessionDescriptionCallback;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessageType;
import io.github.libfdx.net.webrtc.WebRtcProvider;

/**
 * WebRTC-backed NetConnection.
 *
 * @author xpenatan
 */
public final class WebRtcNetConnection implements NetConnection {
    private static final String CHANNEL_PREFIX = "libfdx:";

    private final AbstractWebRtcEndpoint endpoint;
    private final int id;
    private final String remotePeerId;
    private final IntMap<WebRtcDataChannel> channels = new IntMap<WebRtcDataChannel>();
    private final IntMap<NetPacketTransform> transformOverrides = new IntMap<NetPacketTransform>();
    private final NetTransformContext transformContext = new NetTransformContext();
    private WebRtcPeerConnection peerConnection;
    private NetConnectionState state = NetConnectionState.CONNECTING;
    private WebRtcPeerConnectionState peerState = WebRtcPeerConnectionState.NEW;
    private float iceTimer;
    private int iceAttempts;
    private boolean endpointCallbackEnabled = true;

    WebRtcNetConnection(AbstractWebRtcEndpoint endpoint, int id, String remotePeerId) {
        this.endpoint = endpoint;
        this.id = id;
        this.remotePeerId = remotePeerId;
    }

    void peerConnection(WebRtcPeerConnection peerConnection) {
        this.peerConnection = peerConnection;
    }

    String remotePeerId() {
        return remotePeerId;
    }

    void createDefaultDataChannels() {
        NetChannelConfig[] channelConfigs = endpoint.channels();
        for (int i = 0; i < channelConfigs.length; i++) {
            NetChannelConfig config = channelConfigs[i];
            createDataChannel(config.id(), config.delivery());
        }
    }

    void createOffer() {
        peerConnection.createOffer(new WebRtcSessionDescriptionCallback() {
            @Override
            public void success(WebRtcSessionDescription description) {
                endpoint.sendSignaling(WebRtcSignalingMessageType.OFFER, remotePeerId,
                        endpoint.signalingCodec().writeSessionDescription(description));
            }

            @Override
            public void error(Throwable error) {
                endpoint.onConnectionError(error);
            }
        });
    }

    void handleOffer(WebRtcSessionDescription offer) {
        peerConnection.handleOffer(offer, new WebRtcSessionDescriptionCallback() {
            @Override
            public void success(WebRtcSessionDescription description) {
                endpoint.sendSignaling(WebRtcSignalingMessageType.ANSWER, remotePeerId,
                        endpoint.signalingCodec().writeSessionDescription(description));
            }

            @Override
            public void error(Throwable error) {
                endpoint.onConnectionError(error);
            }
        });
    }

    void setRemoteAnswer(WebRtcSessionDescription answer) {
        peerConnection.setRemoteAnswer(answer);
    }

    void addIceCandidate(WebRtcIceCandidate candidate) {
        peerConnection.addIceCandidate(candidate);
    }

    void attachDataChannel(final WebRtcDataChannel dataChannel) {
        int channelId = parseChannelId(dataChannel.label());
        WebRtcDataChannelListener listener = dataChannelListener(channelId, dataChannel);
        dataChannel.listener(listener);
        channels.put(channelId, dataChannel);
        if (dataChannel.isOpen()) {
            listener.open();
        }
    }

    void peerStateChanged(WebRtcPeerConnectionState state) {
        peerState = state;
        if (state == WebRtcPeerConnectionState.CONNECTED) {
            iceAttempts = 0;
            iceTimer = 0;
        }
        else if (state == WebRtcPeerConnectionState.DISCONNECTED) {
            iceTimer = endpoint.settings().iceRestartDelayMillis() / 1000f;
        }
        else if (state == WebRtcPeerConnectionState.FAILED) {
            scheduleIceRestart();
        }
        else if (state == WebRtcPeerConnectionState.CLOSED) {
            close();
        }
    }

    void process(float deltaTime) {
        if (peerState != WebRtcPeerConnectionState.DISCONNECTED && peerState != WebRtcPeerConnectionState.FAILED) {
            return;
        }
        if (iceTimer > 0) {
            iceTimer -= deltaTime;
            return;
        }
        if (iceAttempts >= endpoint.settings().maxIceRestartAttempts()) {
            close();
            return;
        }
        iceAttempts++;
        peerConnection.restartIce(new WebRtcSessionDescriptionCallback() {
            @Override
            public void success(WebRtcSessionDescription description) {
                endpoint.sendSignaling(WebRtcSignalingMessageType.OFFER, remotePeerId,
                        endpoint.signalingCodec().writeSessionDescription(description));
            }

            @Override
            public void error(Throwable error) {
                endpoint.onConnectionError(error);
            }
        });
        scheduleIceRestart();
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public NetConnectionState state() {
        return state;
    }

    @Override
    public NetSendResult send(int channelId, NetBuffer buffer) {
        if (buffer == null) {
            throw new FdxException("Network send buffer cannot be null");
        }
        return sendInternal(channelId, buffer, buffer.bytes(), buffer.offset(), buffer.length());
    }

    @Override
    public NetSendResult send(int channelId, byte[] bytes, int offset, int length) {
        if (bytes == null) {
            throw new FdxException("Network send bytes cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > bytes.length) {
            throw new FdxException("Network send byte range is invalid");
        }
        return sendInternal(channelId, null, bytes, offset, length);
    }

    @Override
    public void setTransform(int channelId, NetPacketTransform transform) {
        if (transform == null) {
            transformOverrides.remove(channelId);
        }
        else {
            transformOverrides.put(channelId, transform);
        }
    }

    @Override
    public void close() {
        closeInternal(true);
    }

    @Override
    public void dispose() {
        close();
    }

    @Override
    public boolean isDisposed() {
        return state == NetConnectionState.DISCONNECTED;
    }

    @Override
    public ProviderId providerId() {
        return WebRtcProvider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    void closeWithoutEndpointCallback() {
        closeInternal(false);
    }

    private NetSendResult sendInternal(int channelId, NetBuffer sourceBuffer, byte[] bytes, int offset, int length) {
        if (state != NetConnectionState.CONNECTED) {
            return NetSendResult.NOT_CONNECTED;
        }
        NetChannelConfig channelConfig = endpoint.channelConfig(channelId);
        if (channelConfig == null) {
            return NetSendResult.UNSUPPORTED_DELIVERY;
        }
        WebRtcDataChannel channel = channels.get(channelId);
        if (channel == null || !channel.isOpen()) {
            return NetSendResult.NOT_CONNECTED;
        }
        if (shouldDropUnreliable(channelConfig.delivery(), channel)) {
            endpoint.stats().droppedUnreliable();
            return NetSendResult.DROPPED_BACKPRESSURE;
        }
        NetPacketTransform transform = transform(channelConfig);
        if (transform == null) {
            boolean sent = channel.send(bytes, offset, length);
            if (sent) {
                endpoint.stats().sent(length);
            }
            return sent ? NetSendResult.SENT : NetSendResult.FAILED;
        }
        NetBuffer input = sourceBuffer;
        boolean ownsInput = false;
        if (input == null) {
            input = endpoint.buffers().tryAcquire();
            if (input == null) {
                return NetSendResult.DROPPED_BACKPRESSURE;
            }
            ownsInput = true;
            input.set(bytes, offset, length);
        }
        NetBuffer output = endpoint.buffers().tryAcquire();
        if (output == null) {
            if (ownsInput) {
                input.release();
            }
            return NetSendResult.DROPPED_BACKPRESSURE;
        }
        NetTransformResult transformResult = transform.encode(
                transformContext.set(WebRtcProvider.ID, this, channelId, channelConfig.delivery()),
                input.reader().rewind(), output.writer());
        if (ownsInput) {
            input.release();
        }
        if (transformResult != NetTransformResult.OK) {
            output.release();
            return transformResult == NetTransformResult.DROP ? NetSendResult.DROPPED_BACKPRESSURE
                    : NetSendResult.FAILED;
        }
        boolean sent = channel.send(output.bytes(), output.offset(), output.length());
        if (sent) {
            endpoint.stats().sent(output.length());
        }
        output.release();
        return sent ? NetSendResult.SENT : NetSendResult.FAILED;
    }

    private void createDataChannel(final int channelId, NetDelivery delivery) {
        WebRtcDataChannel channel = peerConnection.createDataChannel(label(channelId), delivery,
                dataChannelListener(channelId, null));
        channels.put(channelId, channel);
    }

    private WebRtcDataChannelListener dataChannelListener(final int channelId, final WebRtcDataChannel provided) {
        return new WebRtcDataChannelListener() {
            @Override
            public void open() {
                if (provided != null) {
                    channels.put(channelId, provided);
                }
                if (channelId == 0 && state != NetConnectionState.CONNECTED) {
                    state = NetConnectionState.CONNECTED;
                    endpoint.onConnectionConnected(WebRtcNetConnection.this);
                }
            }

            @Override
            public void closed() {
                if (channelId == 0) {
                    close();
                }
            }

            @Override
            public void message(byte[] bytes, int offset, int length) {
                NetChannelConfig channelConfig = endpoint.channelConfig(channelId);
                if (channelConfig == null) {
                    return;
                }
                endpoint.enqueueInbound(WebRtcNetConnection.this, channelId, channelConfig.delivery(),
                        transform(channelConfig), bytes, offset, length);
            }

            @Override
            public void error(Throwable error) {
                endpoint.onConnectionError(error);
            }
        };
    }

    private NetPacketTransform transform(NetChannelConfig channelConfig) {
        NetPacketTransform override = transformOverrides.get(channelConfig.id());
        if (override != null) {
            return override;
        }
        if (channelConfig.transform() != null) {
            return channelConfig.transform();
        }
        return endpoint.defaultTransform();
    }

    private boolean shouldDropUnreliable(NetDelivery delivery, WebRtcDataChannel channel) {
        if (delivery != NetDelivery.UNRELIABLE_UNORDERED) {
            return false;
        }
        int packets = endpoint.settings().unreliableBufferPackets();
        return packets > 0 && channel.bufferedAmount() > (long) packets * endpoint.buffers().config().packetBytes();
    }

    private void scheduleIceRestart() {
        int baseMillis = endpoint.settings().iceBackoffBaseMillis();
        int shift = Math.min(iceAttempts, 12);
        iceTimer = (baseMillis * (1 << shift)) / 1000f;
    }

    private void closeInternal(boolean notifyEndpoint) {
        if (state == NetConnectionState.DISCONNECTED) {
            return;
        }
        state = NetConnectionState.DISCONNECTED;
        ObjectIterator<IntMap.Entry<WebRtcDataChannel>> iterator = channels.entries().iterator();
        while (iterator.hasNext()) {
            IntMap.Entry<WebRtcDataChannel> entry = iterator.next();
            entry.value().close();
        }
        channels.clear();
        if (peerConnection != null) {
            peerConnection.close();
        }
        if (notifyEndpoint && endpointCallbackEnabled) {
            endpoint.onConnectionDisconnected(this);
        }
    }

    private static String label(int channelId) {
        return CHANNEL_PREFIX + channelId;
    }

    private static int parseChannelId(String label) {
        if (label == null || !label.startsWith(CHANNEL_PREFIX)) {
            return 0;
        }
        return Integer.parseInt(label.substring(CHANNEL_PREFIX.length()));
    }
}
