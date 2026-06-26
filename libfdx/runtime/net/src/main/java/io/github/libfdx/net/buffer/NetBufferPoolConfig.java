package io.github.libfdx.net.buffer;

import io.github.libfdx.core.FdxException;

/**
 * Configures reusable packet buffers.
 *
 * @author xpenatan
 */
public final class NetBufferPoolConfig {
    private final int initialPackets;
    private final int maxPackets;
    private final int packetBytes;

    private NetBufferPoolConfig(Builder builder) {
        if (builder.initialPackets < 0) {
            throw new FdxException("Initial packet count cannot be negative");
        }
        if (builder.maxPackets <= 0) {
            throw new FdxException("Max packet count must be positive");
        }
        if (builder.initialPackets > builder.maxPackets) {
            throw new FdxException("Initial packet count cannot exceed max packet count");
        }
        if (builder.packetBytes <= 0) {
            throw new FdxException("Packet byte capacity must be positive");
        }
        initialPackets = builder.initialPackets;
        maxPackets = builder.maxPackets;
        packetBytes = builder.packetBytes;
    }

    /**
     * Creates a builder with default values.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates the default config.
     *
     * @return the default config
     */
    public static NetBufferPoolConfig defaults() {
        return builder().build();
    }

    public int initialPackets() {
        return initialPackets;
    }

    public int maxPackets() {
        return maxPackets;
    }

    public int packetBytes() {
        return packetBytes;
    }

    /**
     * Builds buffer pool configs.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private int initialPackets = 256;
        private int maxPackets = 1024;
        private int packetBytes = 1400;

        private Builder() {
        }

        public Builder initialPackets(int initialPackets) {
            this.initialPackets = initialPackets;
            return this;
        }

        public Builder maxPackets(int maxPackets) {
            this.maxPackets = maxPackets;
            return this;
        }

        public Builder packetBytes(int packetBytes) {
            this.packetBytes = packetBytes;
            return this;
        }

        public NetBufferPoolConfig build() {
            return new NetBufferPoolConfig(this);
        }
    }
}
