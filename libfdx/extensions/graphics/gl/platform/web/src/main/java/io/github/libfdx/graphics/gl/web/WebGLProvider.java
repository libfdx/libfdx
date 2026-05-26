package io.github.libfdx.graphics.gl.web;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsContextProfile;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.GraphicsProviderSupport;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.graphics.NativeWindowPlatform;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.gl.GLGraphicsAttachment;
import org.teavm.jso.JSBody;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.webgl.WebGLRenderingContext;

public final class WebGLProvider implements GraphicsAttachmentProvider, GraphicsProviderSupport {
    public static final ProviderId ID = ProviderId.of("webgl");

    @Override
    public ProviderId providerId() {
        return ID;
    }

    @Override
    public GraphicsAttachmentRequirements requirements() {
        return GraphicsAttachmentRequirements.openGL(3, 0, GraphicsContextProfile.ANY, false);
    }

    @Override
    public boolean isSupported() {
        return hasWebGL();
    }

    @Override
    public String supportFailureReason() {
        return isSupported() ? null : "WebGL is not available in this browser";
    }

    @Override
    public GraphicsAttachment create(GraphicsEnvironment environment) {
        if (environment == null) {
            throw new FdxException("GraphicsEnvironment cannot be null");
        }
        NativeWindow nativeWindow = environment.nativeWindow();
        if (nativeWindow == null || nativeWindow.platform() != NativeWindowPlatform.WEB
                || !(nativeWindow.objectHandle() instanceof HTMLCanvasElement)) {
            throw new FdxException("WebGL requires a web canvas NativeWindow");
        }
        HTMLCanvasElement canvas = (HTMLCanvasElement) nativeWindow.objectHandle();
        WebGLRenderingContext context = createContext(canvas);
        if (context == null) {
            throw new FdxException("Could not create WebGL context");
        }
        return new GLGraphicsAttachment(ID, new WebGLApi(context), new WebGLSurface(),
                environment.display().framebufferWidth(), environment.display().framebufferHeight(),
                TextureFormat.RGBA8_UNORM);
    }

    @JSBody(script =
            "var canvas = document.createElement('canvas');\n" +
            "return !!(canvas.getContext('webgl2') || canvas.getContext('webgl') || canvas.getContext('experimental-webgl'));")
    private static native boolean hasWebGL();

    @JSBody(params = { "canvas" }, script =
            "var options = { alpha: true, antialias: false, depth: true, stencil: false, " +
            "premultipliedAlpha: false, preserveDrawingBuffer: false };\n" +
            "return canvas.getContext('webgl2', options) || canvas.getContext('webgl', options) || " +
            "canvas.getContext('experimental-webgl', options);")
    private static native WebGLRenderingContext createContext(HTMLCanvasElement canvas);
}
