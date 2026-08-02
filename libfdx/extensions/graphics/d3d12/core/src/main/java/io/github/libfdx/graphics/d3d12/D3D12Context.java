package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.collections.Array;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.TextureFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
final class D3D12Context implements GraphicsAttachment {
    private final D3D12Configuration configuration;
    private final long windowHandle;
    private final D3D12Device device;
    private final D3D12CommandEncoder commandEncoder;
    private final D3D12TextureView colorAttachment;
    private final D3D12FrameBuffer frameBuffer;
    private final D3D12GraphicsFrame frame;
    private final Array<D3D12Resource> resources = new Array<D3D12Resource>();
    private long nativeHandle;
    private int width;
    private int height;
    private int pendingWidth;
    private int pendingHeight;
    private boolean frameStarted;
    private boolean disposed;

    D3D12Context(D3D12Configuration configuration, long windowHandle, int width, int height) {
        this.configuration = configuration != null ? configuration : new D3D12Configuration();
        this.windowHandle = windowHandle;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        device = new D3D12Device(this);
        commandEncoder = new D3D12CommandEncoder(this);
        colorAttachment = D3D12TextureView.frame(this);
        frameBuffer = new D3D12FrameBuffer(this, colorAttachment);
        frame = new D3D12GraphicsFrame(this, commandEncoder, frameBuffer, colorAttachment);
    }

    void initialize() {
        nativeHandle = D3D12Native.createContext(windowHandle, width, height, configuration.vSync(),
                configuration.validation(), configuration.framesInFlight());
        if (nativeHandle == 0L) {
            throw new FdxException("Could not create a Direct3D 12 context");
        }
        System.out.println("[libfdx-d3d12] selected adapter: " + adapterName());
    }

    long nativeHandle() {
        requireUsable("access the native context");
        return nativeHandle;
    }

    String adapterName() {
        return D3D12Native.adapterName(nativeHandle());
    }

    boolean frameStarted() {
        return frameStarted;
    }

    void requireFrame(String operation) {
        requireUsable(operation);
        if (!frameStarted) {
            throw new FdxException("Cannot " + operation + " outside an active Direct3D 12 frame");
        }
    }

    void requireUsable(String operation) {
        if (disposed || nativeHandle == 0L) {
            throw new FdxException("Cannot " + operation + " after disposing Direct3D 12");
        }
    }

    void register(D3D12Resource resource) {
        requireUsable("create a resource");
        resources.add(resource);
    }

    void unregister(D3D12Resource resource) {
        resources.removeValue(resource, true);
    }

    D3D12Buffer requireBuffer(io.github.libfdx.graphics.Buffer value, String name) {
        if (!(value instanceof D3D12Buffer buffer) || buffer.context() != this || buffer.isDisposed()) {
            throw new FdxException(name + " does not belong to this Direct3D 12 context");
        }
        return buffer;
    }

    D3D12Texture requireTexture(io.github.libfdx.graphics.Texture value, String name) {
        if (!(value instanceof D3D12Texture texture) || texture.context() != this || texture.isDisposed()) {
            throw new FdxException(name + " does not belong to this Direct3D 12 context");
        }
        return texture;
    }

    D3D12Shader requireShader(io.github.libfdx.graphics.shader.ShaderModule value, String name) {
        if (!(value instanceof D3D12Shader shader) || shader.context() != this || shader.isDisposed()) {
            throw new FdxException(name + " does not belong to this Direct3D 12 context");
        }
        return shader;
    }

    D3D12Pipeline requirePipeline(io.github.libfdx.graphics.RenderPipeline value, String name) {
        if (!(value instanceof D3D12Pipeline pipeline) || pipeline.context() != this || pipeline.isDisposed()) {
            throw new FdxException(name + " does not belong to this Direct3D 12 context");
        }
        return pipeline;
    }

    D3D12TextureView requireTextureView(io.github.libfdx.graphics.TextureView value, String name) {
        if (!(value instanceof D3D12TextureView view) || view.context() != this) {
            throw new FdxException(name + " does not belong to this Direct3D 12 context");
        }
        if (view.texture() != null && view.texture().isDisposed()) {
            throw new FdxException(name + " texture has been disposed");
        }
        return view;
    }

    @Override
    public GraphicsDevice device() {
        return device;
    }

    @Override
    public TextureFormat surfaceFormat() {
        return TextureFormat.BGRA8_UNORM;
    }

    @Override
    public GraphicsFrame currentFrame() {
        requireFrame("access the current frame");
        return frame;
    }

    @Override
    public void clear(float red, float green, float blue, float alpha) {
        requireFrame("clear");
        RenderPass pass = commandEncoder.beginRenderPass(
                io.github.libfdx.graphics.RenderPassDescriptor.color(colorAttachment,
                        LoadOp.clear(red, green, blue, alpha), StoreOp.store()));
        pass.end();
    }

    @Override
    public void resize(int framebufferWidth, int framebufferHeight) {
        if (disposed || framebufferWidth <= 0 || framebufferHeight <= 0) {
            return;
        }
        if (frameStarted) {
            pendingWidth = framebufferWidth;
            pendingHeight = framebufferHeight;
            return;
        }
        applyResize(framebufferWidth, framebufferHeight);
    }

    private void applyResize(int framebufferWidth, int framebufferHeight) {
        if (framebufferWidth == width && framebufferHeight == height) {
            return;
        }
        D3D12Native.resizeContext(nativeHandle(), framebufferWidth, framebufferHeight);
        width = framebufferWidth;
        height = framebufferHeight;
    }

    @Override
    public void processEvents() {
        // The desktop backend owns window event processing.
    }

    @Override
    public boolean beginFrame() {
        requireUsable("begin a frame");
        if (frameStarted) {
            throw new FdxException("A Direct3D 12 frame is already active");
        }
        if (pendingWidth > 0 && pendingHeight > 0) {
            applyResize(pendingWidth, pendingHeight);
            pendingWidth = 0;
            pendingHeight = 0;
        }
        if (!D3D12Native.beginFrame(nativeHandle())) {
            return false;
        }
        frameStarted = true;
        commandEncoder.beginFrame();
        return true;
    }

    @Override
    public void endFrame() {
        if (!frameStarted) {
            return;
        }
        commandEncoder.requireEnded();
        D3D12Native.endFrame(nativeHandle());
        frameStarted = false;
    }

    ByteBuffer readPixels() {
        requireFrame("read the framebuffer");
        commandEncoder.requireEnded();
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        D3D12Native.readPixels(nativeHandle(), pixels);
        pixels.position(0);
        frameStarted = false;
        return pixels;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
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

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        if (frameStarted) {
            endFrame();
        }
        for (int i = resources.size() - 1; i >= 0; i--) {
            resources.get(i).disposeResource();
        }
        resources.clear();
        if (nativeHandle != 0L) {
            D3D12Native.destroyContext(nativeHandle);
            nativeHandle = 0L;
        }
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
