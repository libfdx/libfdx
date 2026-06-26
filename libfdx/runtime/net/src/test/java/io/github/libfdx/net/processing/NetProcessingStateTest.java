package io.github.libfdx.net.processing;

import io.github.libfdx.net.config.NetProcessingConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests fixed-rate network processing limits.
 *
 * @author xpenatan
 */
final class NetProcessingStateTest {
    @Test
    void beginFrameCapsTicksPerFrame() {
        NetProcessingConfig config = NetProcessingConfig.builder()
                .tickRate(10)
                .maxTicksPerFrame(2)
                .dropUnreliableWhenBehind(true)
                .build();
        NetProcessingState state = new NetProcessingState(config);

        assertEquals(2, state.beginFrame(0.5f));
        assertTrue(state.accumulatedTime() <= 0.2f);
    }

    @Test
    void beginFrameCarriesPartialTick() {
        NetProcessingConfig config = NetProcessingConfig.builder()
                .tickRate(20)
                .maxTicksPerFrame(4)
                .build();
        NetProcessingState state = new NetProcessingState(config);

        assertEquals(0, state.beginFrame(0.025f));
        assertEquals(1, state.beginFrame(0.025f));
        assertEquals(0.0f, state.accumulatedTime(), 0.00001f);
    }

    @Test
    void beginFrameIgnoresInvalidDeltaTime() {
        NetProcessingState state = new NetProcessingState(NetProcessingConfig.defaults());

        assertEquals(0, state.beginFrame(Float.NaN));
        assertEquals(0, state.beginFrame(-1.0f));
        assertEquals(0.0f, state.accumulatedTime());
    }
}
