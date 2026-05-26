package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUBuffer;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferUsage;

final class WGPUBufferHandle implements Buffer {
    private WGPUBuffer nativeBuffer;
    private final String label;
    private final int size;
    private final BufferUsage usage;
    private boolean usedByRecordedCommand;
    private boolean disposed;

    WGPUBufferHandle(WGPUBuffer nativeBuffer, String label, int size, BufferUsage usage) {
        this.nativeBuffer = nativeBuffer;
        this.label = label;
        this.size = size;
        this.usage = usage != null ? usage : BufferUsage.VERTEX;
    }

    WGPUBuffer nativeBuffer() {
        return nativeBuffer;
    }

    void nativeBuffer(WGPUBuffer nativeBuffer) {
        this.nativeBuffer = nativeBuffer;
        usedByRecordedCommand = false;
    }

    String label() {
        return label;
    }

    void markUsedByRecordedCommand() {
        usedByRecordedCommand = true;
    }

    void resetUsedByRecordedCommand() {
        usedByRecordedCommand = false;
    }

    boolean usedByRecordedCommand() {
        return usedByRecordedCommand;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public BufferUsage usage() {
        return usage;
    }

    @Override
    public ProviderId providerId() {
        return WGPUProvider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        nativeBuffer.destroy();
        nativeBuffer.release();
        nativeBuffer.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
