package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferUsage;

/**
 * Represents a GL buffer handle.
 *
 * @author xpenatan
 */
final class GLBufferHandle implements Buffer {
    private final ProviderId providerId;
    private final GLApi gl;
    private final GLResourceDomain resourceDomain;
    private final int buffer;
    private final int size;
    private final BufferUsage usage;
    private boolean disposed;

    GLBufferHandle(ProviderId providerId, GLApi gl, GLResourceDomain resourceDomain, int buffer, int size,
            BufferUsage usage) {
        this.providerId = providerId;
        this.gl = gl;
        this.resourceDomain = resourceDomain;
        this.buffer = buffer;
        this.size = size;
        this.usage = usage != null ? usage : BufferUsage.VERTEX;
    }

    int buffer() {
        return buffer;
    }

    GLResourceDomain resourceDomain() {
        return resourceDomain;
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
        return providerId;
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
        if (resourceDomain.makeAnyContextCurrent()) {
            gl.deleteBuffer(buffer);
        }
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
