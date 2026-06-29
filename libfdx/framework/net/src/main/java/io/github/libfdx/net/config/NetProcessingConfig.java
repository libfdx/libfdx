package io.github.libfdx.net.config;

import io.github.libfdx.core.FdxException;

/**
 * Configures per-frame network processing limits.
 *
 * @author xpenatan
 */
public final class NetProcessingConfig {
    private final int tickRate;
    private final int maxTicksPerFrame;
    private final int maxReceivePacketsPerTick;
    private final int maxReceiveBytesPerTick;
    private final int maxSendPacketsPerTick;
    private final boolean dropUnreliableWhenBehind;

    private NetProcessingConfig(Builder builder) {
        if (builder.tickRate <= 0) {
            throw new FdxException("Tick rate must be positive");
        }
        if (builder.maxTicksPerFrame <= 0) {
            throw new FdxException("Max ticks per frame must be positive");
        }
        if (builder.maxReceivePacketsPerTick < 0) {
            throw new FdxException("Max receive packets per tick cannot be negative");
        }
        if (builder.maxReceiveBytesPerTick < 0) {
            throw new FdxException("Max receive bytes per tick cannot be negative");
        }
        if (builder.maxSendPacketsPerTick < 0) {
            throw new FdxException("Max send packets per tick cannot be negative");
        }
        tickRate = builder.tickRate;
        maxTicksPerFrame = builder.maxTicksPerFrame;
        maxReceivePacketsPerTick = builder.maxReceivePacketsPerTick;
        maxReceiveBytesPerTick = builder.maxReceiveBytesPerTick;
        maxSendPacketsPerTick = builder.maxSendPacketsPerTick;
        dropUnreliableWhenBehind = builder.dropUnreliableWhenBehind;
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
    public static NetProcessingConfig defaults() {
        return builder().build();
    }

    public int tickRate() {
        return tickRate;
    }

    public int maxTicksPerFrame() {
        return maxTicksPerFrame;
    }

    public int maxReceivePacketsPerTick() {
        return maxReceivePacketsPerTick;
    }

    public int maxReceiveBytesPerTick() {
        return maxReceiveBytesPerTick;
    }

    public int maxSendPacketsPerTick() {
        return maxSendPacketsPerTick;
    }

    public boolean dropUnreliableWhenBehind() {
        return dropUnreliableWhenBehind;
    }

    /**
     * Builds processing configs.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private int tickRate = 30;
        private int maxTicksPerFrame = 2;
        private int maxReceivePacketsPerTick = 64;
        private int maxReceiveBytesPerTick = 64 * 1024;
        private int maxSendPacketsPerTick = 64;
        private boolean dropUnreliableWhenBehind = true;

        private Builder() {
        }

        public Builder tickRate(int tickRate) {
            this.tickRate = tickRate;
            return this;
        }

        public Builder maxTicksPerFrame(int maxTicksPerFrame) {
            this.maxTicksPerFrame = maxTicksPerFrame;
            return this;
        }

        public Builder maxReceivePacketsPerTick(int maxReceivePacketsPerTick) {
            this.maxReceivePacketsPerTick = maxReceivePacketsPerTick;
            return this;
        }

        public Builder maxReceiveBytesPerTick(int maxReceiveBytesPerTick) {
            this.maxReceiveBytesPerTick = maxReceiveBytesPerTick;
            return this;
        }

        public Builder maxSendPacketsPerTick(int maxSendPacketsPerTick) {
            this.maxSendPacketsPerTick = maxSendPacketsPerTick;
            return this;
        }

        public Builder dropUnreliableWhenBehind(boolean dropUnreliableWhenBehind) {
            this.dropUnreliableWhenBehind = dropUnreliableWhenBehind;
            return this;
        }

        public NetProcessingConfig build() {
            return new NetProcessingConfig(this);
        }
    }
}
