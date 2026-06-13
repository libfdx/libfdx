package io.github.libfdx.backend.desktopc;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.graphics.NativeWindowPlatform;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.gl.GLConfiguration;
import io.github.libfdx.graphics.gl.GLGraphicsAttachment;

/**
 * Provides desktop C open GL services.
 *
 * @author xpenatan
 */
public final class DesktopCOpenGLProvider implements GraphicsAttachmentProvider {
    public static final ProviderId ID = ProviderId.of("gl");

    private GLConfiguration configuration = new GLConfiguration();

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
        return GraphicsAttachmentRequirements.openGL(configuration.majorVersion(), configuration.minorVersion(),
                configuration.profile(), configuration.forwardCompatible());
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
        if (nativeWindow == null || nativeWindow.platform() != NativeWindowPlatform.GLFW
                || nativeWindow.backendHandle() == 0L) {
            throw new FdxException("desktop C OpenGL requires a GLFW NativeWindow");
        }
        long windowHandle = nativeWindow.backendHandle();
        DesktopCGLFW.makeContextCurrent(windowHandle);
        DesktopCOpenGL.glewInit();
        return new GLGraphicsAttachment(ID, new DesktopCGLApi(), new DesktopCGLSurface(windowHandle),
                environment.display().framebufferWidth(), environment.display().framebufferHeight(),
                TextureFormat.RGBA8_UNORM);
    }

    /**
     * Returns the configuration.
     *
     * @return the configuration
     */
    public GLConfiguration configuration() {
        return configuration;
    }

    /**
     * Sets the configuration and returns this desktop C open GL provider.
     *
     * @param configuration the configuration
     * @return this desktop C open GL provider for chaining
     */
    public DesktopCOpenGLProvider configuration(GLConfiguration configuration) {
        this.configuration = configuration != null ? configuration : new GLConfiguration();
        return this;
    }
}
