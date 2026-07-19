package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.ShaderLanguage;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.TextureWrap;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

abstract class D3D12Resource {
    private final D3D12Context context;
    private long nativeHandle;
    private boolean disposed;

    D3D12Resource(D3D12Context context, long nativeHandle) {
        this.context = context;
        this.nativeHandle = nativeHandle;
        context.register(this);
    }

    final D3D12Context context() {
        return context;
    }

    final long nativeHandle() {
        return nativeHandle;
    }

    final boolean resourceDisposed() {
        return disposed;
    }

    final void disposeResource() {
        if (disposed) {
            return;
        }
        disposed = true;
        long handle = nativeHandle;
        nativeHandle = 0L;
        if (handle != 0L && !context.isDisposed()) {
            destroyNative(context.nativeHandle(), handle);
        }
        context.unregister(this);
    }

    abstract void destroyNative(long contextHandle, long resourceHandle);

    final ProviderId id() {
        return D3D12Provider.ID;
    }
}

final class D3D12Buffer extends D3D12Resource implements Buffer {
    private final int size;
    private final BufferUsage usage;
    private ByteBuffer staging;
    private MemorySegment stagingMemory;
    private ByteBuffer cachedDirectSource;
    private MemorySegment cachedDirectMemory;
    private int cachedDirectPosition = -1;
    private int cachedDirectRemaining = -1;

    D3D12Buffer(D3D12Context context, long nativeHandle, int size, BufferUsage usage) {
        super(context, nativeHandle);
        this.size = size;
        this.usage = usage;
    }

    MemorySegment uploadSource(ByteBuffer source, int sourceSize) {
        if (source.isDirect()) {
            if (source != cachedDirectSource || source.position() != cachedDirectPosition
                    || source.remaining() != cachedDirectRemaining) {
                cachedDirectSource = source;
                cachedDirectPosition = source.position();
                cachedDirectRemaining = source.remaining();
                cachedDirectMemory = MemorySegment.ofBuffer(source);
            }
            return cachedDirectMemory;
        }
        if (staging == null || staging.capacity() < sourceSize) {
            staging = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
            stagingMemory = MemorySegment.ofBuffer(staging);
        }
        staging.put(0, source, source.position(), sourceSize);
        return stagingMemory;
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
        return id();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T)Long.valueOf(nativeHandle());
    }

    @Override
    public void dispose() {
        disposeResource();
    }

    @Override
    public boolean isDisposed() {
        return resourceDisposed();
    }

    @Override
    void destroyNative(long contextHandle, long resourceHandle) {
        D3D12Native.destroyBuffer(contextHandle, resourceHandle);
    }
}

final class D3D12Texture extends D3D12Resource implements Texture {
    private final int width;
    private final int height;
    private final TextureFormat format;
    private final TextureUsage usage;
    private final TextureFilter filter;
    private final TextureWrap wrapS;
    private final TextureWrap wrapT;
    private final D3D12TextureView view;
    private ByteBuffer staging;
    private MemorySegment stagingMemory;
    private ByteBuffer cachedDirectSource;
    private MemorySegment cachedDirectMemory;
    private int cachedDirectPosition = -1;
    private int cachedDirectRemaining = -1;

    D3D12Texture(D3D12Context context, long nativeHandle, int width, int height, TextureFormat format,
            TextureUsage usage, TextureFilter filter, TextureWrap wrapS, TextureWrap wrapT) {
        super(context, nativeHandle);
        this.width = width;
        this.height = height;
        this.format = format;
        this.usage = usage;
        this.filter = filter;
        this.wrapS = wrapS;
        this.wrapT = wrapT;
        view = D3D12TextureView.texture(context, this);
    }

    MemorySegment uploadSource(ByteBuffer source, int sourceSize) {
        if (source.isDirect()) {
            if (source != cachedDirectSource || source.position() != cachedDirectPosition
                    || source.remaining() != cachedDirectRemaining) {
                cachedDirectSource = source;
                cachedDirectPosition = source.position();
                cachedDirectRemaining = source.remaining();
                cachedDirectMemory = MemorySegment.ofBuffer(source);
            }
            return cachedDirectMemory;
        }
        if (staging == null || staging.capacity() < sourceSize) {
            staging = ByteBuffer.allocateDirect(sourceSize).order(ByteOrder.nativeOrder());
            stagingMemory = MemorySegment.ofBuffer(staging);
        }
        staging.put(0, source, source.position(), sourceSize);
        return stagingMemory;
    }

    TextureFilter filter() {
        return filter;
    }

    TextureWrap wrapS() {
        return wrapS;
    }

    TextureWrap wrapT() {
        return wrapT;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public TextureFormat format() {
        return format;
    }

    @Override
    public TextureUsage usage() {
        return usage;
    }

    @Override
    public TextureView view() {
        return view;
    }

    @Override
    public ProviderId providerId() {
        return id();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T)Long.valueOf(nativeHandle());
    }

    @Override
    public void dispose() {
        disposeResource();
    }

    @Override
    public boolean isDisposed() {
        return resourceDisposed();
    }

    @Override
    void destroyNative(long contextHandle, long resourceHandle) {
        D3D12Native.destroyTexture(contextHandle, resourceHandle);
    }
}

final class D3D12TextureView implements TextureView {
    private final D3D12Context context;
    private final D3D12Texture texture;

    private D3D12TextureView(D3D12Context context, D3D12Texture texture) {
        this.context = context;
        this.texture = texture;
    }

    static D3D12TextureView frame(D3D12Context context) {
        return new D3D12TextureView(context, null);
    }

    static D3D12TextureView texture(D3D12Context context, D3D12Texture texture) {
        return new D3D12TextureView(context, texture);
    }

    D3D12Context context() {
        return context;
    }

    D3D12Texture texture() {
        return texture;
    }

    long nativeHandle() {
        return texture != null ? texture.nativeHandle() : 0L;
    }

    int width() {
        return texture != null ? texture.width() : context.width();
    }

    int height() {
        return texture != null ? texture.height() : context.height();
    }

    @Override
    public TextureFormat format() {
        return texture != null ? texture.format() : context.surfaceFormat();
    }

    @Override
    public ProviderId providerId() {
        return D3D12Provider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T)Long.valueOf(nativeHandle());
    }
}

final class D3D12Shader extends D3D12Resource implements ShaderModule {
    D3D12Shader(D3D12Context context, long nativeHandle) {
        super(context, nativeHandle);
    }

    @Override
    public ShaderLanguage language() {
        return ShaderLanguage.HLSL;
    }

    @Override
    public ProviderId providerId() {
        return id();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T)Long.valueOf(nativeHandle());
    }

    @Override
    public void dispose() {
        disposeResource();
    }

    @Override
    public boolean isDisposed() {
        return resourceDisposed();
    }

    @Override
    void destroyNative(long contextHandle, long resourceHandle) {
        D3D12Native.destroyShader(contextHandle, resourceHandle);
    }
}

final class D3D12Pipeline extends D3D12Resource implements RenderPipeline {
    private final int sampledTextureCount;
    private final boolean uniformBufferEnabled;

    D3D12Pipeline(D3D12Context context, long nativeHandle, int sampledTextureCount,
            boolean uniformBufferEnabled) {
        super(context, nativeHandle);
        this.sampledTextureCount = sampledTextureCount;
        this.uniformBufferEnabled = uniformBufferEnabled;
    }

    int sampledTextureCount() {
        return sampledTextureCount;
    }

    boolean uniformBufferEnabled() {
        return uniformBufferEnabled;
    }

    @Override
    public ProviderId providerId() {
        return id();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T)Long.valueOf(nativeHandle());
    }

    @Override
    public void dispose() {
        disposeResource();
    }

    @Override
    public boolean isDisposed() {
        return resourceDisposed();
    }

    @Override
    void destroyNative(long contextHandle, long resourceHandle) {
        D3D12Native.destroyPipeline(contextHandle, resourceHandle);
    }
}

final class D3D12FrameBuffer implements FrameBuffer {
    private final D3D12Context context;
    private final D3D12TextureView colorAttachment;
    D3D12FrameBuffer(D3D12Context context, D3D12TextureView colorAttachment) {
        this.context = context;
        this.colorAttachment = colorAttachment;
    }

    @Override
    public TextureView colorAttachment() {
        return colorAttachment;
    }

    @Override
    public TextureFormat format() {
        return context.surfaceFormat();
    }

    @Override
    public int width() {
        return context.width();
    }

    @Override
    public int height() {
        return context.height();
    }

    @Override
    public ByteBuffer readPixelsRgba8() {
        return context.readPixels();
    }

    @Override
    public ProviderId providerId() {
        return D3D12Provider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T)this;
    }
}

final class D3D12GraphicsFrame implements GraphicsFrame {
    private final D3D12Context context;
    private final D3D12CommandEncoder encoder;
    private final D3D12FrameBuffer frameBuffer;
    private final D3D12TextureView colorAttachment;
    D3D12GraphicsFrame(D3D12Context context, D3D12CommandEncoder encoder,
            D3D12FrameBuffer frameBuffer, D3D12TextureView colorAttachment) {
        this.context = context;
        this.encoder = encoder;
        this.frameBuffer = frameBuffer;
        this.colorAttachment = colorAttachment;
    }

    @Override
    public CommandEncoder commandEncoder() {
        return encoder;
    }

    @Override
    public FrameBuffer frameBuffer() {
        return frameBuffer;
    }

    @Override
    public TextureView colorAttachment() {
        return colorAttachment;
    }

    @Override
    public int width() {
        return context.width();
    }

    @Override
    public int height() {
        return context.height();
    }

    @Override
    public ProviderId providerId() {
        return D3D12Provider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T)this;
    }
}
