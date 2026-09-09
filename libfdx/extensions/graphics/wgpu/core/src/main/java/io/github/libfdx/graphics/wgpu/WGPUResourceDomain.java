package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.collections.Array;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContextLostException;

import com.github.xpenatan.webgpu.WGPUBindGroupLayout;

/**
 * Identifies one shared native WGPU device resource domain.
 */
final class WGPUResourceDomain {
    private int contextReferences;
    private int preparationReferences;
    private final Array<WGPUContext> contexts = new Array<WGPUContext>();
    private Runnable nativeRelease;
    private volatile boolean closed;
    private volatile String deviceLoss;
    private boolean lossHandled;

    // May run on a native callback thread. Never cancel jobs or release native objects here.
    synchronized void deviceLost(String message) {
        if (deviceLoss != null) return;
        deviceLoss = message;
        closed = true;
    }

    String deviceLoss() { return deviceLoss; }

    GraphicsContextLostException lossException() {
        GraphicsContextLostException failure = new GraphicsContextLostException(WGPUProvider.ID);
        failure.initCause(new FdxException(deviceLoss));
        return failure;
    }

    // Contexts sharing a device belong to the same application owner thread.
    void handleDeviceLoss() {
        if (deviceLoss == null || lossHandled) return;
        lossHandled = true;
        WGPUCleanup cleanup = new WGPUCleanup();
        for (int i = 0; i < contexts.size(); i++) {
            WGPUContext context = contexts.get(i);
            cleanup.run(context::cancelLostDevicePreparation);
        }
        cleanup.throwIfFailed();
    }

    void setNativeRelease(Runnable nativeRelease) {
        if (nativeRelease == null) {
            throw new FdxException("WGPU native release action cannot be null");
        }
        if (this.nativeRelease != null || closed) {
            throw new FdxException("WGPU native resource domain already has an owner");
        }
        this.nativeRelease = nativeRelease;
    }

    synchronized void retainContext() {
        if (closed) {
            throw new FdxException("Cannot retain a closed WGPU resource domain");
        }
        contextReferences++;
    }

    void registerContext(WGPUContext context) {
        if (context == null || contexts.contains(context)) {
            return;
        }
        contexts.add(context);
    }

    void unregisterContext(WGPUContext context) {
        contexts.removeValue(context, true);
    }

    void releaseUniformBindGroups(WGPUBindGroupLayout layout) {
        WGPUCleanup cleanup = new WGPUCleanup();
        for (int i = 0; i < contexts.size(); i++) {
            WGPUContext context = contexts.get(i);
            cleanup.run(() -> context.releaseUniformBindGroups(layout));
        }
        cleanup.throwIfFailed();
    }

    void releaseContext() {
        Runnable release;
        synchronized (this) {
            if (contextReferences <= 0) {
                throw new FdxException("WGPU resource domain context reference underflow");
            }
            contextReferences--;
            if (contextReferences != 0) return;
            closed = true;
            release = takeNativeRelease();
        }
        if (release != null) release.run();
    }

    synchronized void retainPreparation() {
        if (closed || contextReferences == 0) throw new FdxException("Cannot prepare on a closed WGPU resource domain");
        preparationReferences++;
    }

    void releasePreparation() {
        Runnable release;
        synchronized (this) {
            if (preparationReferences <= 0) throw new FdxException("WGPU preparation reference underflow");
            preparationReferences--;
            release = takeNativeRelease();
        }
        if (release != null) release.run();
    }

    private Runnable takeNativeRelease() {
        if (!closed || contextReferences != 0 || preparationReferences != 0) return null;
        Runnable release = nativeRelease;
        nativeRelease = null;
        return release;
    }

    int contextReferences() {
        return contextReferences;
    }

    boolean isClosed() {
        return closed;
    }
}
