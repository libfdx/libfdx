package io.github.libfdx.net.packet;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.net.buffer.NetBuffer;
import io.github.libfdx.net.buffer.NetBufferPool;
import io.github.libfdx.net.config.NetProcessingConfig;
import io.github.libfdx.net.processing.NetProcessingState;
import io.github.libfdx.net.transport.NetConnection;
import io.github.libfdx.net.transport.NetDelivery;
import io.github.libfdx.net.transport.NetSendResult;
import io.github.libfdx.net.transport.NetStats;

/**
 * Reusable inbound packet queue for transport providers.
 *
 * @author xpenatan
 */
public final class NetPacketQueue {
    private final NetBufferPool buffers;
    private final NetProcessingConfig config;
    private final NetProcessingState processing;
    private final NetStats stats;
    private final NetConnection[] connections;
    private final NetBuffer[] packetBuffers;
    private final int[] channelIds;
    private final NetDelivery[] deliveries;
    private final NetPacket packetView = new NetPacket();
    private int head;
    private int size;

    /**
     * Creates a packet queue.
     *
     * @param buffers the buffer pool
     * @param config the processing config
     * @param stats the stats, or null
     */
    public NetPacketQueue(NetBufferPool buffers, NetProcessingConfig config, NetStats stats) {
        if (buffers == null) {
            throw new FdxException("NetBufferPool cannot be null");
        }
        this.buffers = buffers;
        this.config = config != null ? config : NetProcessingConfig.defaults();
        processing = new NetProcessingState(this.config);
        this.stats = stats;
        int capacity = buffers.config().maxPackets();
        connections = new NetConnection[capacity];
        packetBuffers = new NetBuffer[capacity];
        channelIds = new int[capacity];
        deliveries = new NetDelivery[capacity];
    }

    /**
     * Enqueues inbound bytes into a pooled packet buffer.
     *
     * @param connection the connection
     * @param channelId the channel ID
     * @param delivery the delivery mode
     * @param bytes the bytes
     * @param offset the byte offset
     * @param length the byte length
     * @return the enqueue result
     */
    public NetSendResult enqueue(NetConnection connection, int channelId, NetDelivery delivery, byte[] bytes,
            int offset, int length) {
        if (bytes == null) {
            throw new FdxException("Network packet bytes cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > bytes.length) {
            throw new FdxException("Network packet byte range is invalid");
        }
        if (delivery == null) {
            throw new FdxException("Network delivery cannot be null");
        }
        NetBuffer buffer = buffers.tryAcquire();
        if (buffer == null) {
            return dropOrBackpressure(delivery);
        }
        buffer.set(bytes, offset, length);
        NetSendResult result = enqueue(connection, channelId, delivery, buffer);
        if (result != NetSendResult.QUEUED) {
            buffer.release();
        }
        return result;
    }

    /**
     * Enqueues an already acquired packet buffer. The queue owns the buffer after a QUEUED result and releases it after
     * dispatch.
     *
     * @param connection the connection
     * @param channelId the channel ID
     * @param delivery the delivery mode
     * @param buffer the acquired buffer
     * @return the enqueue result
     */
    public NetSendResult enqueue(NetConnection connection, int channelId, NetDelivery delivery, NetBuffer buffer) {
        if (delivery == null) {
            throw new FdxException("Network delivery cannot be null");
        }
        if (buffer == null) {
            throw new FdxException("Network packet buffer cannot be null");
        }
        int index = (head + size) % packetBuffers.length;
        if (size == packetBuffers.length) {
            return dropOrBackpressure(delivery);
        }
        connections[index] = connection;
        channelIds[index] = channelId;
        deliveries[index] = delivery;
        packetBuffers[index] = buffer;
        size++;
        if (stats != null) {
            stats.queued(buffer.length());
        }
        return NetSendResult.QUEUED;
    }

    /**
     * Dispatches queued packets according to the configured tick budget.
     *
     * @param deltaTime the frame delta time in seconds
     * @param handler the packet handler
     * @return the dispatched packet count
     */
    public int dispatch(float deltaTime, NetPacketHandler handler) {
        if (handler == null) {
            throw new FdxException("NetPacketHandler cannot be null");
        }
        int ticks = processing.beginFrame(deltaTime);
        int dispatched = 0;
        for (int tick = 0; tick < ticks && size > 0; tick++) {
            int packetsThisTick = 0;
            int bytesThisTick = 0;
            while (size > 0 && canDispatchMore(packetsThisTick)) {
                NetBuffer buffer = packetBuffers[head];
                int byteLength = buffer.length();
                if (bytesThisTick > 0 && exceedsByteLimit(bytesThisTick, byteLength)) {
                    break;
                }
                NetConnection connection = connections[head];
                int channelId = channelIds[head];
                NetDelivery delivery = deliveries[head];
                removeHead();

                try {
                    handler.message(connection, packetView.set(connection, channelId, delivery, buffer));
                    if (stats != null) {
                        stats.received(byteLength);
                    }
                }
                finally {
                    buffer.release();
                }
                dispatched++;
                packetsThisTick++;
                bytesThisTick += byteLength;
            }
        }
        return dispatched;
    }

    /**
     * Clears all queued packets.
     */
    public void clear() {
        while (size > 0) {
            NetBuffer buffer = packetBuffers[head];
            removeHead();
            buffer.release();
        }
        processing.clear();
    }

    /**
     * Returns the queued packet count.
     *
     * @return the queued packet count
     */
    public int size() {
        return size;
    }

    /**
     * Returns the queue capacity.
     *
     * @return the capacity
     */
    public int capacity() {
        return packetBuffers.length;
    }

    private boolean canDispatchMore(int packetsThisTick) {
        int maxPackets = config.maxReceivePacketsPerTick();
        return maxPackets == 0 || packetsThisTick < maxPackets;
    }

    private boolean exceedsByteLimit(int bytesThisTick, int nextPacketBytes) {
        int maxBytes = config.maxReceiveBytesPerTick();
        return maxBytes > 0 && bytesThisTick + nextPacketBytes > maxBytes;
    }

    private NetSendResult dropOrBackpressure(NetDelivery delivery) {
        if (delivery == NetDelivery.UNRELIABLE_UNORDERED && config.dropUnreliableWhenBehind() && stats != null) {
            stats.droppedUnreliable();
        }
        return NetSendResult.DROPPED_BACKPRESSURE;
    }

    private void removeHead() {
        connections[head] = null;
        packetBuffers[head] = null;
        deliveries[head] = null;
        channelIds[head] = 0;
        head = (head + 1) % packetBuffers.length;
        size--;
        if (size == 0) {
            head = 0;
        }
    }
}
