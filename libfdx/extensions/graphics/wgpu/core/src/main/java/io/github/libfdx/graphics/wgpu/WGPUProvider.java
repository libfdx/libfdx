package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.jParser.api.NativeObject;
import com.github.xpenatan.webgpu.JWebGPULoader;
import com.github.xpenatan.webgpu.WGPU;
import com.github.xpenatan.webgpu.WGPUInstance;
import com.github.xpenatan.webgpu.WGPUInstanceDescriptor;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.graphics.NativeWindowPlatform;
import java.util.ServiceLoader;

/**
 * Provides WGPU services.
 *
 * @author xpenatan
 */
public final class WGPUProvider implements GraphicsAttachmentProvider {
    public static final ProviderId ID = ProviderId.of("wgpu");
    private static final long LOAD_TIMEOUT_NANOS = 10L * 1000L * 1000L * 1000L;
    private WGPUConfiguration configuration = new WGPUConfiguration();

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return ID;
    }

    /**
     * Returns the requirements.
     *
     * @return the requirements
     */
    @Override
    public GraphicsAttachmentRequirements requirements() {
        return GraphicsAttachmentRequirements.noApi();
    }

    /**
     * Creates a value.
     *
     * @param environment the environment
     * @return the created value
     */
    @Override
    public GraphicsAttachment create(GraphicsEnvironment environment) {
        if (environment == null) {
            throw new FdxException("GraphicsEnvironment cannot be null");
        }
        NativeWindow nativeWindow = environment.nativeWindow();
        GraphicsContext sharedGraphics = environment.sharedContext();
        WGPUContext sharedContext = null;
        if (sharedGraphics != null) {
            if (!ID.equals(sharedGraphics.providerId())) {
                throw new FdxException("Cannot share a non-WGPU graphics context with WGPU");
            }
            if (sharedGraphics instanceof WGPUGraphicsAttachment) {
                sharedContext = ((WGPUGraphicsAttachment) sharedGraphics).context();
            } else if (sharedGraphics instanceof WGPUContext) {
                sharedContext = (WGPUContext) sharedGraphics;
            } else {
                throw new FdxException("Shared WGPU context has an incompatible implementation");
            }
        }
        WGPUContext context = createContext(nativeWindow, configuration,
                environment.display().framebufferWidth(), environment.display().framebufferHeight(), sharedContext);
        return new WGPUGraphicsAttachment(context);
    }

    /**
     * Creates a context.
     *
     * @param nativeWindow the native window
     * @param configuration the configuration
     * @param width the width in pixels
     * @param height the height in pixels
     * @return the created value
     */
    public WGPUContext createContext(NativeWindow nativeWindow, WGPUConfiguration configuration, int width, int height) {
        return createContext(nativeWindow, configuration, width, height, null);
    }

    private WGPUContext createContext(NativeWindow nativeWindow, WGPUConfiguration configuration, int width, int height,
            WGPUContext sharedContext) {
        if (nativeWindow == null) {
            throw new FdxException("WGPU requires a native window from the backend");
        }
        if (sharedContext != null && !sharedContext.isReady()) {
            throw new FdxException("The shared WGPU context is not ready");
        }
        WGPUConfiguration actualConfiguration = configuration != null ? configuration : new WGPUConfiguration();
        loadNativeBackend(actualConfiguration);

        WGPUInstance instance = sharedContext != null ? sharedContext.nativeInstance() : createInstance(actualConfiguration);
        if (instance == null || !instance.isValid()) {
            if (instance != null) instance.dispose();
            throw new FdxException("Could not create a valid WGPU instance");
        }

        boolean surfaceCopySrc = nativeWindow.platform() != NativeWindowPlatform.ANDROID;
        WGPUContext context = null;
        WGPUNativeSurface.SurfaceHandle createdSurface = null;
        try {
            if (sharedContext != null || nativeWindow.platform() != NativeWindowPlatform.ANDROID) {
                // Desktop adapter selection must retain its surface-compatibility constraint.
                createdSurface = WGPUNativeSurface.create(instance, nativeWindow);
                context = new WGPUContext(actualConfiguration, instance, createdSurface.surface(), createdSurface.owner(), surfaceCopySrc,
                        sharedContext);
            } else {
                // Check the adapter/device before connecting the window to a native graphics API.
                context = new WGPUContext(actualConfiguration, instance, nativeWindow, surfaceCopySrc);
            }
            if (sharedContext != null) {
                context.initializeShared();
            } else {
                context.initializeBlocking();
            }
            context.resize(width, height);
            for (WGPUPreparation preparation : ServiceLoader.load(WGPUPreparation.class)) {
                if (!preparation.supports(actualConfiguration)) continue;
                context.initializePreparation(preparation, actualConfiguration.preparationWorkerLimit());
                break;
            }
            return context;
        } catch (RuntimeException | Error failure) {
            try {
                if (context != null) context.dispose();
                else {
                    WGPUCleanup cleanup = new WGPUCleanup();
                    if (createdSurface != null) {
                        cleanup.run(createdSurface.surface()::release);
                        cleanup.run(createdSurface.surface()::dispose);
                        if (createdSurface.owner() instanceof NativeObject owner) cleanup.run(owner::dispose);
                    }
                    if (sharedContext == null) {
                        cleanup.run(instance::release);
                        cleanup.run(instance::dispose);
                    }
                    cleanup.throwIfFailed();
                }
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static WGPUInstance createInstance(WGPUConfiguration configuration) {
        if (configuration.loaderBackend() != WGPULoaderBackend.WGPU || configuration.backend() == WGPUBackend.DEFAULT) {
            return WGPU.setupInstance();
        }
        WGPUInstanceDescriptor descriptor = new WGPUInstanceDescriptor();
        try {
            descriptor.setBackendType(configuration.backend().toNative());
            return WGPU.setupInstance(descriptor);
        } finally {
            descriptor.dispose();
        }
    }

    /**
     * Returns the configuration.
     *
     * @return the configuration
     */
    public WGPUConfiguration configuration() {
        return configuration;
    }

    /**
     * Sets the configuration and returns this WGPU provider.
     *
     * @param configuration the configuration
     * @return this WGPU provider for chaining
     */
    public WGPUProvider configuration(WGPUConfiguration configuration) {
        this.configuration = configuration != null ? configuration : new WGPUConfiguration();
        return this;
    }

    /**
     * Sets the loader backend and returns this WGPU provider.
     *
     * @param loaderBackend the loader backend
     * @return this WGPU provider for chaining
     */
    public WGPUProvider loaderBackend(WGPULoaderBackend loaderBackend) {
        configuration.loaderBackend(loaderBackend);
        return this;
    }

    /**
     * Sets the backend and returns this WGPU provider. With the WGPU loader, an explicit backend also restricts
     * instance creation to that backend, including native surfaces. DEFAULT retains the loader defaults.
     * To retry another backend, configure a separate provider attempt in the application backend.
     *
     * @param backend the backend
     * @return this WGPU provider for chaining
     */
    public WGPUProvider backend(WGPUBackend backend) {
        configuration.backend(backend);
        return this;
    }

    /**
     * Sets the v sync and returns this WGPU provider.
     *
     * @param vSync the v sync
     * @return this WGPU provider for chaining
     */
    public WGPUProvider vSync(boolean vSync) {
        configuration.vSync(vSync);
        return this;
    }

    /**
     * Sets the process events each frame and returns this WGPU provider.
     *
     * @param processEventsEachFrame the process events each frame
     * @return this WGPU provider for chaining
     */
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
