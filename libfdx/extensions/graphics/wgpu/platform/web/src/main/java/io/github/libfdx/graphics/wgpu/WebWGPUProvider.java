package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.jParser.loader.JParserLibraryLoaderPlatform;
import com.github.xpenatan.jmultiplatform.core.JMultiplatform;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.GraphicsProviderSupport;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.graphics.NativeWindowPlatform;
import org.teavm.jso.JSBody;

/**
 * Provides web WGPU services.
 *
 * @author xpenatan
 */
public final class WebWGPUProvider implements GraphicsAttachmentProvider, GraphicsProviderSupport {
    private WGPUConfiguration configuration = new WGPUConfiguration().backend(WGPUBackend.WEBGPU);

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
     * Returns the requirements.
     *
     * @return the requirements
     */
    @Override
    public GraphicsAttachmentRequirements requirements() {
        return GraphicsAttachmentRequirements.noApi();
    }

    /**
     * Returns whether supported is enabled or true.
     *
     * @return true if supported is enabled or true; false otherwise
     */
    @Override
    public boolean isSupported() {
        return hasWebGPU();
    }

    /**
     * Returns the support failure reason.
     *
     * @return the support failure reason
     */
    @Override
    public String supportFailureReason() {
        return isSupported() ? null : "WebGPU is not available in this browser";
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
        if (nativeWindow == null || nativeWindow.platform() != NativeWindowPlatform.WEB) {
            throw new FdxException("WebGPU requires a web canvas NativeWindow");
        }
        configureScriptPath();
        return new WGPUWebGraphicsAttachment(
                nativeWindow,
                configuration,
                environment.display().framebufferWidth(),
                environment.display().framebufferHeight());
    }

    /**
     * Sets the configuration and returns this web WGPU provider.
     *
     * @param configuration the configuration
     * @return this web WGPU provider for chaining
     */
    public WebWGPUProvider configuration(WGPUConfiguration configuration) {
        this.configuration = configuration != null ? configuration : new WGPUConfiguration().backend(WGPUBackend.WEBGPU);
        return this;
    }

    /**
     * Sets the v sync and returns this web WGPU provider.
     *
     * @param vSync the v sync
     * @return this web WGPU provider for chaining
     */
    public WebWGPUProvider vSync(boolean vSync) {
        configuration.vSync(vSync);
        return this;
    }

    @JSBody(script = "return typeof navigator !== 'undefined' && !!navigator.gpu;")
    private static native boolean hasWebGPU();

    private static void configureScriptPath() {
        JMultiplatform.getInstance().getMap()
                .put(JParserLibraryLoaderPlatform.PLATFORM_WEB_SCRIPT_PATH, scriptPath());
    }

    @JSBody(script = "return new URL('scripts/', window.location.href).href;")
    private static native String scriptPath();
}
