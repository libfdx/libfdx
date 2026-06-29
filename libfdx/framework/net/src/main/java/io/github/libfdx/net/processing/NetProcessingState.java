package io.github.libfdx.net.processing;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.net.config.NetProcessingConfig;

/**
 * Tracks fixed-rate network processing for an endpoint.
 *
 * @author xpenatan
 */
public final class NetProcessingState {
    private final NetProcessingConfig config;
    private float accumulatedTime;

    /**
     * Creates processing state.
     *
     * @param config the config
     */
    public NetProcessingState(NetProcessingConfig config) {
        if (config == null) {
            throw new FdxException("NetProcessingConfig cannot be null");
        }
        this.config = config;
    }

    /**
     * Adds frame time and returns the number of network ticks allowed this frame.
     *
     * @param deltaTime the frame delta time in seconds
     * @return the tick count
     */
    public int beginFrame(float deltaTime) {
        if (deltaTime > 0.0f && Float.isFinite(deltaTime)) {
            accumulatedTime += deltaTime;
        }
        float tickSeconds = 1.0f / config.tickRate();
        int ticks = 0;
        while (accumulatedTime >= tickSeconds && ticks < config.maxTicksPerFrame()) {
            accumulatedTime -= tickSeconds;
            ticks++;
        }
        if (ticks == config.maxTicksPerFrame() && config.dropUnreliableWhenBehind()) {
            float maxCarry = tickSeconds * config.maxTicksPerFrame();
            if (accumulatedTime > maxCarry) {
                accumulatedTime = maxCarry;
            }
        }
        return ticks;
    }

    /**
     * Returns the accumulated unprocessed time.
     *
     * @return the time in seconds
     */
    public float accumulatedTime() {
        return accumulatedTime;
    }

    /**
     * Clears accumulated time.
     */
    public void clear() {
        accumulatedTime = 0.0f;
    }
}
