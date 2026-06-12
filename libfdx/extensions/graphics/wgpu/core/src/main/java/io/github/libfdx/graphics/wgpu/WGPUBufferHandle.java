package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUBuffer;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferUsage;

/**
 * Represents a WGPU buffer handle.
 *
 * @author xpenatan
 */
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

    /**
     * Returns the size.
     *
     * @return the size
     */
    @Override
    public int size() {
        return size;
    }

    /**
     * Returns the usage.
     *
     * @return the usage
     */
    @Override
    public BufferUsage usage() {
        return usage;
    }

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return WGPUProvider.ID;
    }

    /**
     * Returns the provider-specific representation requested by the caller.
     *
     * @param <T> the value type
     * @return the as
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    /**
     * Releases resources held by this instance.
     */
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

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
