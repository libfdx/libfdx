package io.github.libfdx.backend.desktop;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.gl.GLConfiguration;
import io.github.libfdx.graphics.gl.GLGraphicsAttachment;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

public final class DesktopOpenGLProvider implements GraphicsAttachmentProvider {
    public static final ProviderId ID = ProviderId.of("gl");

    private GLConfiguration configuration = new GLConfiguration();

    @Override
    public ProviderId providerId() {
        return ID;
    }

    @Override
    public GraphicsAttachmentRequirements requirements() {
        return GraphicsAttachmentRequirements.openGL(configuration.majorVersion(), configuration.minorVersion(),
                configuration.profile(), configuration.forwardCompatible());
    }

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
        GLFW.glfwMakeContextCurrent(windowHandle);
        GL.createCapabilities();
        return new GLGraphicsAttachment(ID, new DesktopGLApi(), new DesktopGLSurface(windowHandle),
                environment.display().framebufferWidth(), environment.display().framebufferHeight(),
                TextureFormat.RGBA8_UNORM);
    }

    public GLConfiguration configuration() {
        return configuration;
    }

    public DesktopOpenGLProvider configuration(GLConfiguration configuration) {
        this.configuration = configuration != null ? configuration : new GLConfiguration();
        return this;
    }
}
