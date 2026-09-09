package io.github.libfdx.backend.android;

import android.view.Surface;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsContextProfile;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.GraphicsProviderSupport;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.gl.GLGraphicsAttachment;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;

/**
 * Provides android gles services.
 *
 * @author xpenatan
 */
public final class AndroidGlesProvider implements GraphicsAttachmentProvider, GraphicsProviderSupport {
    public static final ProviderId ID = ProviderId.of("gles");
    private int preparationWorkerLimit = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors()));
    private ShaderArtifactCache shaderCache;

    /** Optional borrowed compiler-artifact and supported native program-binary cache.
     * Binary import/export may block and runs only through explicit updateLoading calls;
     * native binary work begun there must finish through loading updates. Runtime preparation
     * never imports/exports binaries. Keep storage available until preparation drains. */
    public ShaderArtifactCache shaderCache() { return shaderCache; }
    public AndroidGlesProvider shaderCache(ShaderArtifactCache value) { shaderCache = value; return this; }

    /** CPU translation worker limit for newly created attachments. The default is at most two
     * workers. Native compilation stays on the owning GLES context and uses driver completion
     * polling only when supported. This does not control the driver's internal worker count. */
    public int preparationWorkerLimit() { return preparationWorkerLimit; }
    public AndroidGlesProvider preparationWorkerLimit(int workers) {
        if (workers < 1 || workers > 64) throw new FdxException("GLES preparation workers must be between 1 and 64");
        preparationWorkerLimit = workers;
        return this;
    }

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
        return GraphicsAttachmentRequirements.openGL(3, 0, GraphicsContextProfile.ANY, false);
    }

    /**
     * Returns whether supported is enabled or true.
     *
     * @return true if supported is enabled or true; false otherwise
     */
    @Override
    public boolean isSupported() {
        return true;
    }

    /**
     * Returns the support failure reason.
     *
     * @return the support failure reason
     */
    @Override
    public String supportFailureReason() {
        return null;
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
        if (nativeWindow == null || !(nativeWindow.objectHandle() instanceof Surface)) {
            throw new FdxException("Android GLES requires an Android Surface");
        }
        AndroidGlesSurface surface = new AndroidGlesSurface((Surface) nativeWindow.objectHandle());
        try {
            surface.makeCurrent();
            return new GLGraphicsAttachment(ID, new AndroidGlesApi(preparationWorkerLimit), surface,
                    environment.display().framebufferWidth(), environment.display().framebufferHeight(),
                    TextureFormat.RGBA8_UNORM, null, shaderCache);
        } catch (RuntimeException | Error failure) {
            surface.dispose();
            throw failure;
        }
    }
}
