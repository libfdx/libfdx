package io.github.libfdx.net.config;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.buffer.NetBufferPoolConfig;
import io.github.libfdx.net.transform.NetPacketTransform;

/**
 * Base configuration for network endpoints.
 *
 * @author xpenatan
 */
public abstract class NetEndpointConfig {
    private static final NetChannelConfig[] DEFAULT_CHANNELS = new NetChannelConfig[] {
            NetChannelConfig.reliable(0), NetChannelConfig.unreliable(1)
    };

    private final ProviderId providerId;
    private final NetBufferPoolConfig buffers;
    private final NetProcessingConfig processing;
    private final NetChannelConfig[] channels;
    private final NetPacketTransform defaultTransform;

    protected NetEndpointConfig(Builder<?> builder) {
        if (builder.providerId == null) {
            throw new FdxException("Network transport provider ID cannot be null");
        }
        providerId = builder.providerId;
        buffers = builder.buffers != null ? builder.buffers : NetBufferPoolConfig.defaults();
        processing = builder.processing != null ? builder.processing : NetProcessingConfig.defaults();
        channels = builder.channels != null ? builder.channels.clone() : DEFAULT_CHANNELS.clone();
        if (channels.length == 0) {
            throw new FdxException("At least one network channel is required");
        }
        for (int i = 0; i < channels.length; i++) {
            if (channels[i] == null) {
                throw new FdxException("Network channel cannot be null");
            }
        }
        defaultTransform = builder.defaultTransform;
    }

    public ProviderId providerId() {
        return providerId;
    }

    public NetBufferPoolConfig buffers() {
        return buffers;
    }

    public NetProcessingConfig processing() {
        return processing;
    }

    public NetChannelConfig[] channels() {
        return channels.clone();
    }

    public NetPacketTransform defaultTransform() {
        return defaultTransform;
    }

    /**
     * Base builder for endpoint configs.
     *
     * @param <T> the concrete builder type
     *
     * @author xpenatan
     */
    public abstract static class Builder<T extends Builder<T>> {
        private final ProviderId providerId;
        private NetBufferPoolConfig buffers;
        private NetProcessingConfig processing;
        private NetChannelConfig[] channels;
        private NetPacketTransform defaultTransform;

        protected Builder(ProviderId providerId) {
            this.providerId = providerId;
        }

        public T buffers(NetBufferPoolConfig buffers) {
            this.buffers = buffers;
            return self();
        }

        public T processing(NetProcessingConfig processing) {
            this.processing = processing;
            return self();
        }

        public T channels(NetChannelConfig... channels) {
            this.channels = channels != null ? channels.clone() : null;
            return self();
        }

        public T defaultTransform(NetPacketTransform defaultTransform) {
            this.defaultTransform = defaultTransform;
            return self();
        }

        protected ProviderId providerId() {
            return providerId;
        }

        protected abstract T self();
    }
}
