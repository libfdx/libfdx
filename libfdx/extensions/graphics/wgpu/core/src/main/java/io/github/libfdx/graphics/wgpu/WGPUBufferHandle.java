package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUBuffer;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferUsage;

/**
 * Represents a WGPU buffer handle.
 *
 * @author xpenatan
 */
final class WGPUBufferHandle implements Buffer {
    private final WGPUResourceDomain resourceDomain;
    private WGPUBufferAllocation allocation;
    private final String label;
    private final int size;
    private final BufferUsage usage;
    private boolean disposed;

    WGPUBufferHandle(WGPUResourceDomain resourceDomain, WGPUBufferAllocation allocation, String label, int size,
            BufferUsage usage) {
        if (resourceDomain == null || allocation == null || allocation.resourceDomain() != resourceDomain) {
            throw new FdxException("WGPU buffer allocation is incompatible with its resource domain");
        }
        this.resourceDomain = resourceDomain;
        this.allocation = allocation;
        this.label = label;
        this.size = size;
        this.usage = usage != null ? usage : BufferUsage.VERTEX;
    }

    WGPUBuffer nativeBuffer() {
        return allocation.nativeBuffer();
    }

    WGPUBufferAllocation allocation() {
        return allocation;
    }

    WGPUResourceDomain resourceDomain() {
        return resourceDomain;
    }

    void replaceAllocation(WGPUBufferAllocation replacement) {
        if (replacement == null || replacement.resourceDomain() != resourceDomain) {
            throw new FdxException("Replacement WGPU buffer allocation is incompatible");
        }
        WGPUBufferAllocation previous = allocation;
        allocation = replacement;
        previous.retire();
    }

    String label() {
        return label;
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
        allocation.retire();
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
