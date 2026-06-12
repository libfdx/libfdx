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

/**
 * Provides android gles services.
 *
 * @author xpenatan
 */
public final class AndroidGlesProvider implements GraphicsAttachmentProvider, GraphicsProviderSupport {
    public static final ProviderId ID = ProviderId.of("gles");

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
        surface.makeCurrent();
        return new GLGraphicsAttachment(ID, new AndroidGlesApi(), surface,
                environment.display().framebufferWidth(), environment.display().framebufferHeight(),
                TextureFormat.RGBA8_UNORM);
    }
}
