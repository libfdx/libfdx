package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.core.FdxException;

import com.github.xpenatan.webgpu.WGPUBindGroupLayout;

import java.util.ArrayList;

/**
 * Identifies one shared native WGPU device resource domain.
 */
final class WGPUResourceDomain {
    private int contextReferences;
    private final ArrayList<WGPUContext> contexts = new ArrayList<WGPUContext>();
    private Runnable nativeRelease;
    private boolean closed;

    void setNativeRelease(Runnable nativeRelease) {
        if (nativeRelease == null) {
            throw new FdxException("WGPU native release action cannot be null");
        }
        if (this.nativeRelease != null || closed) {
            throw new FdxException("WGPU native resource domain already has an owner");
        }
        this.nativeRelease = nativeRelease;
    }

    void retainContext() {
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
        contexts.remove(context);
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
        if (contextReferences <= 0) {
            throw new FdxException("WGPU resource domain context reference underflow");
        }
        contextReferences--;
        if (contextReferences != 0) {
            return;
        }
        closed = true;
        Runnable release = nativeRelease;
        nativeRelease = null;
        if (release != null) {
            release.run();
        }
    }

    int contextReferences() {
        return contextReferences;
    }

    boolean isClosed() {
        return closed;
    }
}
