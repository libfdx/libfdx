package io.github.libfdx.backend.android;

import io.github.libfdx.graphics.GraphicsContextLostException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Poll only while the caller owns a native context/device reference. Loss cancels work once;
 * it never releases references still owned by active native calls or storage transactions. */
final class AndroidVulkanDeviceState {
    private final BooleanSupplier nativeLost;
    private final Runnable cancelPreparation;
    private final AtomicBoolean lost = new AtomicBoolean();
    private volatile Throwable cleanupFailure;

    AndroidVulkanDeviceState(BooleanSupplier nativeLost, Runnable cancelPreparation) {
        this.nativeLost = nativeLost;
        this.cancelPreparation = cancelPreparation;
    }

    boolean poll() {
        if (!lost.get() && nativeLost.getAsBoolean()) markLost();
        return lost.get();
    }

    void observe(Throwable failure) {
        if (failure instanceof GraphicsContextLostException) markLost();
    }

    private void markLost() {
        if (!lost.compareAndSet(false, true)) return;
        try { cancelPreparation.run(); }
        catch (RuntimeException | Error failure) { cleanupFailure = failure; }
    }

    void requireUsable() {
        if (!poll()) return;
        var failure = new GraphicsContextLostException(AndroidVulkanProvider.ID);
        if (cleanupFailure != null) failure.addSuppressed(cleanupFailure);
        throw failure;
    }
}
