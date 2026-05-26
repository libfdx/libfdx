package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.JWebGPULoader;
import com.github.xpenatan.webgpu.WGPU;
import com.github.xpenatan.webgpu.WGPUInstance;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentReadiness;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.graphics.TextureFormat;

final class WGPUWebGraphicsAttachment implements GraphicsAttachment, GraphicsAttachmentReadiness {
    private final NativeWindow nativeWindow;
    private final WGPUConfiguration configuration;
    private final LoadState loadState = new LoadState();
    private WGPUContext context;
    private int width;
    private int height;
    private boolean disposed;

    WGPUWebGraphicsAttachment(NativeWindow nativeWindow, WGPUConfiguration configuration, int width, int height) {
        if (nativeWindow == null) {
            throw new FdxException("WGPU web attachment requires a native window");
        }
        this.nativeWindow = nativeWindow;
        this.configuration = configuration != null ? configuration : new WGPUConfiguration();
        this.width = width;
        this.height = height;
        JWebGPULoader.init(this.configuration.loaderBackend().toNative(), (success, error) -> {
            if (!success) {
                loadState.error = error != null ? error : new FdxException("jWebGPU web backend failed to load");
            }
            loadState.complete = true;
        });
    }

    @Override
    public void resize(int framebufferWidth, int framebufferHeight) {
        width = framebufferWidth;
        height = framebufferHeight;
        if (context != null) {
            context.resize(framebufferWidth, framebufferHeight);
        }
    }

    @Override
    public void processEvents() {
        if (disposed) {
            return;
        }
        if (context == null) {
            createContextWhenLoaded();
        }
        if (context != null) {
            context.processEvents();
        }
    }

    private void createContextWhenLoaded() {
        if (loadState.error != null) {
            throw new FdxException("Failed to load jWebGPU web backend", loadState.error);
        }
        if (!loadState.complete) {
            return;
        }
        WGPUInstance instance = WGPU.setupInstance();
        if (instance == null || !instance.isValid()) {
            throw new FdxException("Could not create a valid WGPU web instance");
        }
        WGPUNativeSurface.SurfaceHandle surface = WGPUNativeSurface.create(instance, nativeWindow);
        context = new WGPUContext(configuration, instance, surface.surface(), surface.owner());
        context.initializeAsync();
        context.resize(width, height);
    }

    @Override
    public boolean beginFrame() {
        return context != null && context.beginFrame();
    }

    @Override
    public boolean isReady() {
        return context != null && context.isReady();
    }

    @Override
    public void endFrame() {
        if (context != null) {
            context.endFrame();
        }
    }

    @Override
    public GraphicsDevice device() {
        return readyContext().device();
    }

    @Override
    public TextureFormat surfaceFormat() {
        return readyContext().surfaceFormat();
    }

    @Override
    public GraphicsFrame currentFrame() {
        return readyContext().currentFrame();
    }

    @Override
    public void clear(float red, float green, float blue, float alpha) {
        readyContext().clear(red, green, blue, alpha);
    }

    @Override
    public ProviderId providerId() {
        return WGPUProvider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) readyContext();
    }

    @Override
    public void dispose() {
        disposed = true;
        if (context != null) {
            context.dispose();
            context = null;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private WGPUContext readyContext() {
        if (context == null || !context.isReady()) {
            throw new FdxException("WGPU web attachment is not ready");
        }
        return context;
    }

    private static final class LoadState {
        boolean complete;
        Throwable error;
    }
}
