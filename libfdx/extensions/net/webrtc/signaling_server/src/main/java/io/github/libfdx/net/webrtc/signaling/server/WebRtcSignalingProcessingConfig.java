package io.github.libfdx.net.webrtc.signaling.server;

import io.github.libfdx.core.FdxException;

/**
 * Configures tick-limited signaling server processing.
 *
 * @author xpenatan
 */
public final class WebRtcSignalingProcessingConfig {
    private final int tickRate;
    private final int maxTicksPerFrame;
    private final int maxEventsPerTick;
    private final int maxBytesPerTick;
    private final int initialEvents;
    private final int maxQueuedEvents;

    private WebRtcSignalingProcessingConfig(Builder builder) {
        if (builder.tickRate <= 0) {
            throw new FdxException("WebRTC signaling tick rate must be positive");
        }
        if (builder.maxTicksPerFrame <= 0) {
            throw new FdxException("WebRTC signaling max ticks per frame must be positive");
        }
        if (builder.maxEventsPerTick < 0) {
            throw new FdxException("WebRTC signaling max events per tick cannot be negative");
        }
        if (builder.maxBytesPerTick < 0) {
            throw new FdxException("WebRTC signaling max bytes per tick cannot be negative");
        }
        if (builder.initialEvents < 0) {
            throw new FdxException("WebRTC signaling initial events cannot be negative");
        }
        if (builder.maxQueuedEvents <= 0) {
            throw new FdxException("WebRTC signaling max queued events must be positive");
        }
        if (builder.initialEvents > builder.maxQueuedEvents) {
            throw new FdxException("WebRTC signaling initial events cannot exceed max queued events");
        }
        tickRate = builder.tickRate;
        maxTicksPerFrame = builder.maxTicksPerFrame;
        maxEventsPerTick = builder.maxEventsPerTick;
        maxBytesPerTick = builder.maxBytesPerTick;
        initialEvents = builder.initialEvents;
        maxQueuedEvents = builder.maxQueuedEvents;
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
    public static WebRtcSignalingProcessingConfig defaults() {
        return builder().build();
    }

    public int tickRate() {
        return tickRate;
    }

    public int maxTicksPerFrame() {
        return maxTicksPerFrame;
    }

    public int maxEventsPerTick() {
        return maxEventsPerTick;
    }

    public int maxBytesPerTick() {
        return maxBytesPerTick;
    }

    public int initialEvents() {
        return initialEvents;
    }

    public int maxQueuedEvents() {
        return maxQueuedEvents;
    }

    /**
     * Builds signaling processing configs.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private int tickRate = 30;
        private int maxTicksPerFrame = 2;
        private int maxEventsPerTick = 128;
        private int maxBytesPerTick = 256 * 1024;
        private int initialEvents = 256;
        private int maxQueuedEvents = 4096;

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

        public Builder maxEventsPerTick(int maxEventsPerTick) {
            this.maxEventsPerTick = maxEventsPerTick;
            return this;
        }

        public Builder maxBytesPerTick(int maxBytesPerTick) {
            this.maxBytesPerTick = maxBytesPerTick;
            return this;
        }

        public Builder initialEvents(int initialEvents) {
            this.initialEvents = initialEvents;
            return this;
        }

        public Builder maxQueuedEvents(int maxQueuedEvents) {
            this.maxQueuedEvents = maxQueuedEvents;
            return this;
        }

        public WebRtcSignalingProcessingConfig build() {
            return new WebRtcSignalingProcessingConfig(this);
        }
    }
}
