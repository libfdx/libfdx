package io.github.libfdx.net.transport;

/**
 * Stores transport counters.
 *
 * @author xpenatan
 */
public final class NetStats {
    private long queuedPackets;
    private long queuedBytes;
    private long sentPackets;
    private long sentBytes;
    private long receivedPackets;
    private long receivedBytes;
    private long droppedUnreliablePackets;

    public long queuedPackets() {
        return queuedPackets;
    }

    public long queuedBytes() {
        return queuedBytes;
    }

    public long sentPackets() {
        return sentPackets;
    }

    public long sentBytes() {
        return sentBytes;
    }

    public long receivedPackets() {
        return receivedPackets;
    }

    public long receivedBytes() {
        return receivedBytes;
    }

    public long droppedUnreliablePackets() {
        return droppedUnreliablePackets;
    }

    public void queued(int bytes) {
        queuedPackets++;
        queuedBytes += Math.max(0, bytes);
    }

    public void sent(int bytes) {
        sentPackets++;
        sentBytes += Math.max(0, bytes);
    }

    public void received(int bytes) {
        receivedPackets++;
        receivedBytes += Math.max(0, bytes);
    }

    public void droppedUnreliable() {
        droppedUnreliablePackets++;
    }
}
