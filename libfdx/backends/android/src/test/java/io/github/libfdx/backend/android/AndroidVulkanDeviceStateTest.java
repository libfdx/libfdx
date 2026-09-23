package io.github.libfdx.backend.android;

import io.github.libfdx.graphics.GraphicsContextLostException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AndroidVulkanDeviceStateTest {
    @Test
    void reportedLossRemainsLatchedAndCancelsOnce() {
        AtomicBoolean nativeLost = new AtomicBoolean();
        AtomicInteger cancellations = new AtomicInteger();
        var state = new AndroidVulkanDeviceState(nativeLost::get, cancellations::incrementAndGet);
        state.requireUsable(); assertFalse(state.poll());
        state.observe(new IllegalStateException("ordinary failure")); assertFalse(state.poll());
        nativeLost.set(true);
        assertThrows(GraphicsContextLostException.class, state::requireUsable);
        nativeLost.set(false);
        state.observe(new GraphicsContextLostException(AndroidVulkanProvider.ID));
        assertTrue(state.poll()); assertEquals(1, cancellations.get());
    }

    @Test
    void typedWorkerFailureCancelsBeforeAnotherOwnerPollAndRetainsCleanupDiagnostics() {
        var cleanup = new IllegalStateException("cleanup");
        var state = new AndroidVulkanDeviceState(() -> false, () -> { throw cleanup; });
        state.observe(new GraphicsContextLostException(AndroidVulkanProvider.ID));
        assertTrue(state.poll());
        var error = assertThrows(GraphicsContextLostException.class, state::requireUsable);
        assertArrayEquals(new Throwable[]{cleanup}, error.getSuppressed());
    }
}
