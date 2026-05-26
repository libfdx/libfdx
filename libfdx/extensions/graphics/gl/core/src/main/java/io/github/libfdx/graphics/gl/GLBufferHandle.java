package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferUsage;

final class GLBufferHandle implements Buffer {
    private final ProviderId providerId;
    private final GLApi gl;
    private final int buffer;
    private final int size;
    private final BufferUsage usage;
    private boolean disposed;

    GLBufferHandle(ProviderId providerId, GLApi gl, int buffer, int size, BufferUsage usage) {
        this.providerId = providerId;
        this.gl = gl;
        this.buffer = buffer;
        this.size = size;
        this.usage = usage != null ? usage : BufferUsage.VERTEX;
    }

    int buffer() {
        return buffer;
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
        return providerId;
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
        gl.deleteBuffer(buffer);
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
