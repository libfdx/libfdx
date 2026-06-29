package io.github.libfdx.net.transform;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.transport.NetConnection;
import io.github.libfdx.net.transport.NetDelivery;

/**
 * Provides context for packet transforms.
 *
 * @author xpenatan
 */
public final class NetTransformContext {
    private ProviderId providerId;
    private NetConnection connection;
    private int channelId;
    private NetDelivery delivery;

    /**
     * Sets this context.
     *
     * @param providerId the provider ID
     * @param connection the connection
     * @param channelId the channel ID
     * @param delivery the delivery mode
     * @return this context
     */
    public NetTransformContext set(ProviderId providerId, NetConnection connection, int channelId,
            NetDelivery delivery) {
        this.providerId = providerId;
        this.connection = connection;
        this.channelId = channelId;
        this.delivery = delivery;
        return this;
    }

    public ProviderId providerId() {
        return providerId;
    }

    public NetConnection connection() {
        return connection;
    }

    public int channelId() {
        return channelId;
    }

    public NetDelivery delivery() {
        return delivery;
    }
}
