package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.JWebGPULoader;
import com.github.xpenatan.webgpu.WGPU;
import com.github.xpenatan.webgpu.WGPUInstance;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.NativeWindow;

public final class WGPUProvider implements GraphicsAttachmentProvider {
    public static final ProviderId ID = ProviderId.of("wgpu");
    private static final long LOAD_TIMEOUT_NANOS = 10L * 1000L * 1000L * 1000L;
    private WGPUConfiguration configuration = new WGPUConfiguration();

    @Override
    public ProviderId providerId() {
        return ID;
    }

    @Override
    public GraphicsAttachmentRequirements requirements() {
        return GraphicsAttachmentRequirements.noApi();
    }

    @Override
    public GraphicsAttachment create(GraphicsEnvironment environment) {
        if (environment == null) {
            throw new FdxException("GraphicsEnvironment cannot be null");
        }
        NativeWindow nativeWindow = environment.nativeWindow();
        WGPUContext context = createContext(
                nativeWindow,
                configuration,
                environment.display().framebufferWidth(),
                environment.display().framebufferHeight());
        return new WGPUGraphicsAttachment(context);
    }

    public WGPUContext createContext(NativeWindow nativeWindow, WGPUConfiguration configuration, int width, int height) {
        if (nativeWindow == null) {
            throw new FdxException("WGPU requires a native window from the backend");
        }
        WGPUConfiguration actualConfiguration = configuration != null ? configuration : new WGPUConfiguration();
        loadNativeBackend(actualConfiguration);

        WGPUInstance instance = WGPU.setupInstance();
        if (instance == null || !instance.isValid()) {
            throw new FdxException("Could not create a valid WGPU instance");
        }

        WGPUNativeSurface.SurfaceHandle surface = WGPUNativeSurface.create(instance, nativeWindow);
        WGPUContext context = new WGPUContext(actualConfiguration, instance, surface.surface(), surface.owner());
        context.initializeBlocking();
        context.resize(width, height);
        return context;
    }

    public WGPUConfiguration configuration() {
        return configuration;
    }

    public WGPUProvider configuration(WGPUConfiguration configuration) {
        this.configuration = configuration != null ? configuration : new WGPUConfiguration();
        return this;
    }

    public WGPUProvider loaderBackend(WGPULoaderBackend loaderBackend) {
        configuration.loaderBackend(loaderBackend);
        return this;
    }

    public WGPUProvider backend(WGPUBackend backend) {
        configuration.backend(backend);
        return this;
    }

    public WGPUProvider vSync(boolean vSync) {
        configuration.vSync(vSync);
        return this;
    }

    public WGPUProvider processEventsEachFrame(boolean processEventsEachFrame) {
        configuration.processEventsEachFrame(processEventsEachFrame);
        return this;
    }

    private static void loadNativeBackend(WGPUConfiguration configuration) {
        final LoadState state = new LoadState();
        JWebGPULoader.init(configuration.loaderBackend().toNative(), (success, error) -> {
            if (!success) {
                state.error = error != null ? error : new FdxException("jWebGPU native backend failed to load");
            }
            state.complete = true;
        });

        long deadline = System.nanoTime() + LOAD_TIMEOUT_NANOS;
        while (!state.complete) {
            if (System.nanoTime() > deadline) {
                throw new FdxException("Timed out while loading jWebGPU native backend");
            }
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FdxException("Interrupted while loading jWebGPU native backend", e);
            }
        }
        if (state.error != null) {
            throw new FdxException("Failed to load jWebGPU native backend", state.error);
        }
    }

    private static final class LoadState {
        boolean complete;
        Throwable error;
    }
}
