package io.github.libfdx.net.config;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.net.transform.NetPacketTransform;
import io.github.libfdx.net.transport.NetDelivery;

/**
 * Configures a message channel.
 *
 * @author xpenatan
 */
public final class NetChannelConfig {
    private final int id;
    private final NetDelivery delivery;
    private final NetPacketTransform transform;

    private NetChannelConfig(Builder builder) {
        if (builder.id < 0) {
            throw new FdxException("Channel ID cannot be negative");
        }
        if (builder.delivery == null) {
            throw new FdxException("Channel delivery cannot be null");
        }
        id = builder.id;
        delivery = builder.delivery;
        transform = builder.transform;
    }

    /**
     * Creates a reliable channel config.
     *
     * @param id the channel ID
     * @return the channel config
     */
    public static NetChannelConfig reliable(int id) {
        return builder(id, NetDelivery.RELIABLE_ORDERED).build();
    }

    /**
     * Creates an unreliable channel config.
     *
     * @param id the channel ID
     * @return the channel config
     */
    public static NetChannelConfig unreliable(int id) {
        return builder(id, NetDelivery.UNRELIABLE_UNORDERED).build();
    }

    /**
     * Creates a builder.
     *
     * @param id the channel ID
     * @param delivery the delivery mode
     * @return the builder
     */
    public static Builder builder(int id, NetDelivery delivery) {
        return new Builder().id(id).delivery(delivery);
    }

    public int id() {
        return id;
    }

    public NetDelivery delivery() {
        return delivery;
    }

    public NetPacketTransform transform() {
        return transform;
    }

    /**
     * Builds channel configs.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private int id;
        private NetDelivery delivery;
        private NetPacketTransform transform;

        private Builder() {
        }

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder delivery(NetDelivery delivery) {
            this.delivery = delivery;
            return this;
        }

        public Builder transform(NetPacketTransform transform) {
            this.transform = transform;
            return this;
        }

        public NetChannelConfig build() {
            return new NetChannelConfig(this);
        }
    }
}
