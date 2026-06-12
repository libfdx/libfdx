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

/**
 * Represents a WGPU web graphics attachment.
 *
 * @author xpenatan
 */
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

    /**
     * Handles a size change.
     *
     * @param framebufferWidth the framebuffer width
     * @param framebufferHeight the framebuffer height
     */
    @Override
    public void resize(int framebufferWidth, int framebufferHeight) {
        width = framebufferWidth;
        height = framebufferHeight;
        if (context != null) {
            context.resize(framebufferWidth, framebufferHeight);
        }
    }

    /**
     * Runs the process events step.
     */
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

    /**
     * Returns the begin frame.
     *
     * @return true if begin frame succeeds or is active; false otherwise
     */
    @Override
    public boolean beginFrame() {
        return context != null && context.beginFrame();
    }

    /**
     * Returns whether ready is enabled or true.
     *
     * @return true if ready is enabled or true; false otherwise
     */
    @Override
    public boolean isReady() {
        return context != null && context.isReady();
    }

    /**
     * Ends frame.
     */
    @Override
    public void endFrame() {
        if (context != null) {
            context.endFrame();
        }
    }

    /**
     * Returns the device.
     *
     * @return the device
     */
    @Override
    public GraphicsDevice device() {
        return readyContext().device();
    }

    /**
     * Returns the surface format.
     *
     * @return the surface format
     */
    @Override
    public TextureFormat surfaceFormat() {
        return readyContext().surfaceFormat();
    }

    /**
     * Returns the current frame.
     *
     * @return the current frame
     */
    @Override
    public GraphicsFrame currentFrame() {
        return readyContext().currentFrame();
    }

    /**
     * Runs the clear step.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    @Override
    public void clear(float red, float green, float blue, float alpha) {
        readyContext().clear(red, green, blue, alpha);
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
        return (T) readyContext();
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        disposed = true;
        if (context != null) {
            context.dispose();
            context = null;
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

    private WGPUContext readyContext() {
        if (context == null || !context.isReady()) {
            throw new FdxException("WGPU web attachment is not ready");
        }
        return context;
    }

    /**
     * Represents a load state.
     *
     * @author xpenatan
     */
    private static final class LoadState {
        boolean complete;
        Throwable error;
    }
}
