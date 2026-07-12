package io.github.libfdx.backend.desktop;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.gl.GLConfiguration;
import io.github.libfdx.graphics.gl.GLGraphicsAttachment;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

/**
 * Provides desktop open GL services.
 *
 * @author xpenatan
 */
public final class DesktopOpenGLProvider implements GraphicsAttachmentProvider {
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
        if (nativeWindow == null || nativeWindow.backendHandle() == 0L) {
            throw new FdxException("Desktop OpenGL requires a backend window handle");
        }
        long windowHandle = nativeWindow.backendHandle();
        GLGraphicsAttachment sharedAttachment = null;
        GraphicsContext sharedContext = environment.sharedContext();
        if (sharedContext != null) {
            if (!ID.equals(sharedContext.providerId())) {
                throw new FdxException("Cannot share a non-OpenGL graphics context with desktop OpenGL");
            }
            if (!(sharedContext instanceof GLGraphicsAttachment)) {
                throw new FdxException("Desktop OpenGL shared context has an incompatible implementation");
            }
            sharedAttachment = (GLGraphicsAttachment) sharedContext;
        }
        GLFW.glfwMakeContextCurrent(windowHandle);
        GLCapabilities capabilities = GL.createCapabilities();
        return new GLGraphicsAttachment(ID, new DesktopGLApi(), new DesktopGLSurface(windowHandle, capabilities),
                environment.display().framebufferWidth(), environment.display().framebufferHeight(),
                TextureFormat.RGBA8_UNORM, sharedAttachment);
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
     * Sets the configuration and returns this desktop open GL provider.
     *
     * @param configuration the configuration
     * @return this desktop open GL provider for chaining
     */
    public DesktopOpenGLProvider configuration(GLConfiguration configuration) {
        this.configuration = configuration != null ? configuration : new GLConfiguration();
        return this;
    }
}
