package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.graphics.NativeWindowPlatform;

/**
 * Provides Direct3D 12 graphics through Java 25 FFM on Windows desktop.
 */
public final class D3D12Provider implements GraphicsAttachmentProvider {
    public static final ProviderId ID = ProviderId.of("d3d12");

    private D3D12Configuration configuration = new D3D12Configuration();

    /**
     * Returns the Direct3D 12 provider identifier.
     *
     * @return the provider identifier
     */
    @Override
    public ProviderId providerId() {
        return ID;
    }

    /**
     * Returns the no-client-API window requirement used by Direct3D 12.
     *
     * @return attachment requirements
     */
    @Override
    public GraphicsAttachmentRequirements requirements() {
        return GraphicsAttachmentRequirements.noApi();
    }

    /**
     * Creates a Direct3D 12 attachment for a Windows native window.
     *
     * @param environment the graphics environment
     * @return the created attachment
     */
    @Override
    public GraphicsAttachment create(GraphicsEnvironment environment) {
        if (environment == null) {
            throw new FdxException("GraphicsEnvironment cannot be null");
        }
        NativeWindow nativeWindow = environment.nativeWindow();
        if (nativeWindow == null || nativeWindow.platform() != NativeWindowPlatform.WINDOWS
                || nativeWindow.windowHandle() == 0L) {
            throw new FdxException("Direct3D 12 requires a Windows native window handle");
        }
        GraphicsContext sharedContext = environment.sharedContext();
        if (sharedContext != null) {
            throw new FdxException("Direct3D 12 context sharing is not supported yet");
        }
        D3D12Context context = new D3D12Context(configuration, nativeWindow.windowHandle(),
                environment.display().framebufferWidth(), environment.display().framebufferHeight());
        context.initialize();
        return context;
    }

    /**
     * Returns the current provider configuration.
     *
     * @return the configuration
     */
    public D3D12Configuration configuration() {
        return configuration;
    }

    /**
     * Replaces the provider configuration.
     *
     * @param configuration the configuration, or null for defaults
     * @return this provider
     */
    public D3D12Provider configuration(D3D12Configuration configuration) {
        this.configuration = configuration != null ? configuration : new D3D12Configuration();
        return this;
    }

    /**
     * Sets vertical synchronization.
     *
     * @param vSync whether vertical synchronization is enabled
     * @return this provider
     */
    public D3D12Provider vSync(boolean vSync) {
        configuration.vSync(vSync);
        return this;
    }

    /**
     * Requests the Direct3D 12 debug layer.
     *
     * @param validation whether validation is requested
     * @return this provider
     */
    public D3D12Provider validation(boolean validation) {
        configuration.validation(validation);
        return this;
    }

    /**
     * Sets the number of swap-chain frames.
     *
     * @param framesInFlight frames in flight
     * @return this provider
     */
    public D3D12Provider framesInFlight(int framesInFlight) {
        configuration.framesInFlight(framesInFlight);
        return this;
    }
}
