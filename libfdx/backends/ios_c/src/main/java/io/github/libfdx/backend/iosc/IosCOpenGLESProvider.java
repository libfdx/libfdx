package io.github.libfdx.backend.iosc;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsContextProfile;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.GraphicsProviderSupport;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.gl.GLGraphicsAttachment;

/**
 * Provides iOS C OpenGLES services.
 *
 * @author xpenatan
 */
public final class IosCOpenGLESProvider implements GraphicsAttachmentProvider, GraphicsProviderSupport {
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
     * @return true if supported succeeds or is active; false otherwise
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
        if (environment == null || environment.display() == null) {
            throw new FdxException("iOS C GLES requires a display environment");
        }
        return new GLGraphicsAttachment(ID, new IosCGLApi(), new IosCGLSurface(),
                environment.display().framebufferWidth(), environment.display().framebufferHeight(),
                TextureFormat.RGBA8_UNORM);
    }
}
