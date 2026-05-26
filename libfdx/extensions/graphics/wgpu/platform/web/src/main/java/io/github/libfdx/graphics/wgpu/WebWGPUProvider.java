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

public final class WebWGPUProvider implements GraphicsAttachmentProvider, GraphicsProviderSupport {
    private WGPUConfiguration configuration = new WGPUConfiguration().backend(WGPUBackend.WEBGPU);

    @Override
    public ProviderId providerId() {
        return WGPUProvider.ID;
    }

    @Override
    public GraphicsAttachmentRequirements requirements() {
        return GraphicsAttachmentRequirements.noApi();
    }

    @Override
    public boolean isSupported() {
        return hasWebGPU();
    }

    @Override
    public String supportFailureReason() {
        return isSupported() ? null : "WebGPU is not available in this browser";
    }

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

    public WebWGPUProvider configuration(WGPUConfiguration configuration) {
        this.configuration = configuration != null ? configuration : new WGPUConfiguration().backend(WGPUBackend.WEBGPU);
        return this;
    }

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
