package io.github.libfdx.backend.desktopc;

import io.github.libfdx.DefaultFdx;
import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationBackend;
import io.github.libfdx.application.ApplicationConfig;
import io.github.libfdx.application.ApplicationLifecycle;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.core.SystemLogger;
import io.github.libfdx.display.DefaultDisplays;
import io.github.libfdx.display.Display;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.files.DefaultFileSystem;
import io.github.libfdx.graphics.DefaultGraphics;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsClientApi;
import io.github.libfdx.graphics.GraphicsContextProfile;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.input.DefaultCursor;
import io.github.libfdx.input.DefaultGamepads;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.DefaultInputCapabilities;
import io.github.libfdx.runtime.core.RuntimeCore;

/**
 * Implements the backend integration for desktop C application.
 *
 * @author xpenatan
 */
public final class DesktopCApplicationBackend implements ApplicationBackend, Application {
    public static final ProviderId ID = ProviderId.of("desktop_c");

    private final SystemLogger logger = new SystemLogger();
    private Fdx fdx;
    private ApplicationLifecycle lifecycle = ApplicationLifecycle.DISPOSED;
    private DesktopCDisplay display;
    private GraphicsAttachment graphics;
    private DefaultInput input;
    private boolean running;
    private boolean disposed = true;
    private boolean listenerCreated;
    private float deltaTime;
    private long frameId;

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
     * Runs the start step.
     *
     * @param config the configuration
     * @param listener the listener
     */
    @Override
    public void start(ApplicationConfig config, ApplicationListener listener) {
        if (listener == null) {
            throw new FdxException("ApplicationListener cannot be null");
        }
        DesktopCApplicationConfig actualConfig = toDesktopCConfig(config);
        DisplayConfig displayConfig = actualConfig.displayConfig();
        GraphicsAttachmentProvider graphicsProvider = actualConfig.graphics();
        if (graphicsProvider == null) {
            throw new FdxException("No desktop_c graphics provider configured");
        }
        if (actualConfig.graphicsProvider() != null && !actualConfig.graphicsProvider().equals(graphicsProvider.providerId())) {
            throw new FdxException("Configured graphics provider ID does not match attached GraphicsAttachmentProvider");
        }
        GraphicsAttachmentRequirements requirements = graphicsProvider.requirements();

        initializeGlfw();
        RuntimeCore.registerProvider(new DesktopCRuntimeCoreProvider());
        long windowHandle = 0L;
        try {
            windowHandle = createWindow(displayConfig, requirements);
            display = new DesktopCDisplay(windowHandle, displayConfig.title());
            display.refreshSizes();
            input = new DefaultInput(ProviderId.of("desktop_c_input"), DefaultInputCapabilities.desktop(),
                    new DefaultCursor(), new DefaultGamepads(), null, new DesktopCClipboard(windowHandle));

            graphics = graphicsProvider.create(new DesktopCGraphicsEnvironment(display, NativeWindow.glfw(windowHandle)));
        } catch (RuntimeException error) {
            if (display != null) {
                DesktopCGLFW.destroyWindow(display.windowHandle());
                display = null;
            } else if (windowHandle != 0L) {
                DesktopCGLFW.destroyWindow(windowHandle);
            }
            DesktopCGLFW.terminate();
            throw error;
        }
        if (requirements.clientApi() == GraphicsClientApi.OPENGL) {
            DesktopCGLFW.swapInterval(displayConfig.vSync() ? 1 : 0);
        }
        fdx = new DefaultFdx(this, new DefaultDisplays(display), new DefaultGraphics(graphics), input,
                new DefaultFileSystem(), logger);

        disposed = false;
        running = true;
        lifecycle = ApplicationLifecycle.CREATED;

        if (displayConfig.visible()) {
            DesktopCGLFW.showWindow(windowHandle);
        }

        String phase = "create";
        Throwable applicationFailure = null;
        try {
            listener.create(fdx);
            listenerCreated = true;
            phase = "resize";
            listener.resize(display.width(), display.height());
            lifecycle = ApplicationLifecycle.RUNNING;
            phase = "loop";
            loop(listener, displayConfig);
        } catch (Throwable error) {
            logger.error("Desktop C application failed during " + phase, error);
            applicationFailure = error;
        }

        Throwable shutdownFailure = shutdown(listener);
        if (applicationFailure != null) {
            if (shutdownFailure != null && shutdownFailure != applicationFailure) {
                applicationFailure.addSuppressed(shutdownFailure);
            }
            if (applicationFailure instanceof Error) {
                throw (Error) applicationFailure;
            }
            throw applicationFailure instanceof RuntimeException
                    ? (RuntimeException) applicationFailure
                    : new FdxException("Desktop C application failed during " + phase, applicationFailure);
        }
        if (shutdownFailure != null) {
            if (shutdownFailure instanceof Error) {
                throw (Error) shutdownFailure;
            }
            throw shutdownFailure instanceof RuntimeException
                    ? (RuntimeException) shutdownFailure
                    : new FdxException("Desktop C application failed during shutdown", shutdownFailure);
        }
    }

    /**
     * Runs the start step.
     *
     * @param config the configuration
     * @param listener the listener
     */
    public void start(DesktopCApplicationConfig config, ApplicationListener listener) {
        start((ApplicationConfig) config, listener);
    }

    private DesktopCApplicationConfig toDesktopCConfig(ApplicationConfig config) {
        if (config == null) {
            return new DesktopCApplicationConfig();
        }
        if (config instanceof DesktopCApplicationConfig) {
            return (DesktopCApplicationConfig) config;
        }
        throw new FdxException("DesktopCApplicationBackend requires DesktopCApplicationConfig");
    }

    private void initializeGlfw() {
        if (!DesktopCGLFW.init()) {
            throw new FdxException("Unable to initialize GLFW");
        }
    }

    private long createWindow(DisplayConfig config, GraphicsAttachmentRequirements requirements) {
        DesktopCGLFW.defaultWindowHints();
        applyGraphicsWindowHints(requirements);
        DesktopCGLFW.windowHint(DesktopCGLFW.VISIBLE, DesktopCGLFW.FALSE);
        DesktopCGLFW.windowHint(DesktopCGLFW.RESIZABLE, config.resizable() ? DesktopCGLFW.TRUE : DesktopCGLFW.FALSE);
        DesktopCGLFW.windowHint(DesktopCGLFW.MAXIMIZED, config.maximized() ? DesktopCGLFW.TRUE : DesktopCGLFW.FALSE);
        long windowHandle = DesktopCGLFW.createWindow(config.width(), config.height(), config.title());
        if (windowHandle == 0L) {
            throw new FdxException("Could not create GLFW window");
        }
        return windowHandle;
    }

    private void applyGraphicsWindowHints(GraphicsAttachmentRequirements requirements) {
        if (requirements == null || requirements.clientApi() == GraphicsClientApi.NO_API) {
            DesktopCGLFW.windowHint(DesktopCGLFW.CLIENT_API, DesktopCGLFW.NO_API);
            return;
        }
        if (requirements.clientApi() == GraphicsClientApi.VULKAN) {
            DesktopCGLFW.windowHint(DesktopCGLFW.CLIENT_API, DesktopCGLFW.NO_API);
            return;
        }
        if (requirements.clientApi() != GraphicsClientApi.OPENGL) {
            throw new FdxException("Unsupported desktop_c graphics client API: " + requirements.clientApi());
        }
        DesktopCGLFW.windowHint(DesktopCGLFW.CLIENT_API, DesktopCGLFW.OPENGL_API);
        DesktopCGLFW.windowHint(DesktopCGLFW.CONTEXT_VERSION_MAJOR, requirements.majorVersion());
        DesktopCGLFW.windowHint(DesktopCGLFW.CONTEXT_VERSION_MINOR, requirements.minorVersion());
        DesktopCGLFW.windowHint(DesktopCGLFW.OPENGL_FORWARD_COMPAT,
                requirements.forwardCompatible() ? DesktopCGLFW.TRUE : DesktopCGLFW.FALSE);
        if (requirements.profile() == GraphicsContextProfile.CORE) {
            DesktopCGLFW.windowHint(DesktopCGLFW.OPENGL_PROFILE, DesktopCGLFW.OPENGL_CORE_PROFILE);
        } else if (requirements.profile() == GraphicsContextProfile.COMPATIBILITY) {
            DesktopCGLFW.windowHint(DesktopCGLFW.OPENGL_PROFILE, DesktopCGLFW.OPENGL_COMPAT_PROFILE);
        } else {
            DesktopCGLFW.windowHint(DesktopCGLFW.OPENGL_PROFILE, DesktopCGLFW.OPENGL_ANY_PROFILE);
        }
    }

    private void loop(ApplicationListener listener, DisplayConfig displayConfig) {
        long lastTime = System.nanoTime();
        int lastWindowWidth = display.width();
        int lastWindowHeight = display.height();
        int lastFramebufferWidth = display.framebufferWidth();
        int lastFramebufferHeight = display.framebufferHeight();
        while (running && !DesktopCGLFW.windowShouldClose(display.windowHandle())) {
            try {
                DesktopCGLFW.pollEvents();
                display.refreshSizes();
                boolean windowSizeChanged = display.width() != lastWindowWidth || display.height() != lastWindowHeight;
                boolean framebufferSizeChanged = display.framebufferWidth() != lastFramebufferWidth
                        || display.framebufferHeight() != lastFramebufferHeight;
                if (framebufferSizeChanged && graphics != null) {
                    graphics.resize(display.framebufferWidth(), display.framebufferHeight());
                }
                if (windowSizeChanged) {
                    listener.resize(display.width(), display.height());
                }
                if (windowSizeChanged || framebufferSizeChanged) {
                    lastWindowWidth = display.width();
                    lastWindowHeight = display.height();
                    lastFramebufferWidth = display.framebufferWidth();
                    lastFramebufferHeight = display.framebufferHeight();
                }
                if (graphics != null) {
                    graphics.processEvents();
                }

                long now = System.nanoTime();
                deltaTime = (now - lastTime) / 1000000000.0f;
                lastTime = now;
                frameId++;

                if (graphics == null || graphics.beginFrame()) {
                    try {
                        listener.render();
                        if (graphics != null) {
                            listener.onFrameEnd();
                        }
                    } finally {
                        if (graphics != null) {
                            graphics.endFrame();
                        }
                    }
                }
                if (running && !DesktopCGLFW.windowShouldClose(display.windowHandle())) {
                    sync(displayConfig.foregroundFps());
                }
            } catch (Throwable error) {
                logger.error("Desktop C application frame failed", error);
                throw error instanceof RuntimeException
                        ? (RuntimeException) error
                        : new FdxException("Desktop C application frame failed", error);
            }
        }
    }



    private void sync(int fps) {
        if (fps <= 0) {
            return;
        }
        long sleepMillis = 1000L / fps;
        if (sleepMillis <= 0L) {
            return;
        }
        DesktopCGLFW.waitEventsTimeout(sleepMillis / 1000.0);
    }

    private Throwable shutdown(ApplicationListener listener) {
        if (disposed) {
            return null;
        }
        Throwable failure = null;
        lifecycle = ApplicationLifecycle.PAUSED;
        if (listenerCreated) {
            try {
                listener.pause();
            } catch (Throwable error) {
                failure = recordShutdownFailure(failure, "listener pause", error);
            }
        }
        lifecycle = ApplicationLifecycle.DISPOSED;
        if (listenerCreated) {
            try {
                listener.dispose();
            } catch (Throwable error) {
                failure = recordShutdownFailure(failure, "listener dispose", error);
            }
        }
        listenerCreated = false;

        GraphicsAttachment closingGraphics = graphics;
        graphics = null;
        if (closingGraphics != null) {
            try {
                closingGraphics.dispose();
            } catch (Throwable error) {
                failure = recordShutdownFailure(failure, "graphics dispose", error);
            }
        }

        DesktopCDisplay closingDisplay = display;
        display = null;
        if (closingDisplay != null) {
            try {
                DesktopCGLFW.destroyWindow(closingDisplay.windowHandle());
            } catch (Throwable error) {
                failure = recordShutdownFailure(failure, "window destroy", error);
            }
        }

        input = null;
        try {
            DesktopCGLFW.terminate();
        } catch (Throwable error) {
            failure = recordShutdownFailure(failure, "GLFW terminate", error);
        }
        try {
            RuntimeCore.registerProvider(null);
        } catch (Throwable error) {
            failure = recordShutdownFailure(failure, "runtime provider reset", error);
        }
        running = false;
        disposed = true;
        fdx = null;
        return failure;
    }

    private Throwable recordShutdownFailure(Throwable firstFailure, String phase, Throwable error) {
        logger.error("Desktop C application failed during shutdown " + phase, error);
        if (firstFailure == null) {
            return error;
        }
        if (firstFailure != error) {
            firstFailure.addSuppressed(error);
        }
        return firstFailure;
    }

    /**
     * Returns the lifecycle.
     *
     * @return the lifecycle
     */
    @Override
    public ApplicationLifecycle lifecycle() {
        return lifecycle;
    }

    /**
     * Returns the delta time.
     *
     * @return the delta time
     */
    @Override
    public float deltaTime() {
        return deltaTime;
    }

    /**
     * Returns the frame ID.
     *
     * @return the frame ID
     */
    @Override
    public long frameId() {
        return frameId;
    }

    /**
     * Runs the request exit step.
     */
    @Override
    public void requestExit() {
        running = false;
        if (display != null) {
            DesktopCGLFW.setWindowShouldClose(display.windowHandle(), true);
        }
    }

    /**
     * Returns the provider-specific representation requested by the caller.
     *
     * @param <T> the value type
     * @return the as
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        requestExit();
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Represents a desktop C graphics environment.
     *
     * @author xpenatan
     */
    private static final class DesktopCGraphicsEnvironment implements GraphicsEnvironment {
        private final Display display;
        private final NativeWindow nativeWindow;

        DesktopCGraphicsEnvironment(Display display, NativeWindow nativeWindow) {
            this.display = display;
            this.nativeWindow = nativeWindow;
        }

        /**
         * Returns the display.
         *
         * @return the display
         */
        @Override
        public Display display() {
            return display;
        }

        /**
         * Returns the native window.
         *
         * @return the native window
         */
        @Override
        public NativeWindow nativeWindow() {
            return nativeWindow;
        }
    }

    /**
     * Represents a desktop C display.
     *
     * @author xpenatan
     */
    private static final class DesktopCDisplay implements Display {
        private final long windowHandle;
        private final int[] widthBuffer = new int[1];
        private final int[] heightBuffer = new int[1];
        private final float[] scaleXBuffer = new float[1];
        private final float[] scaleYBuffer = new float[1];
        private String title;
        private int width;
        private int height;
        private int framebufferWidth;
        private int framebufferHeight;
        private float contentScaleX = 1.0f;
        private float contentScaleY = 1.0f;

        DesktopCDisplay(long windowHandle, String title) {
            this.windowHandle = windowHandle;
            this.title = title != null ? title : "";
        }

        long windowHandle() {
            return windowHandle;
        }

        void refreshSizes() {
            DesktopCGLFW.getWindowSize(windowHandle, widthBuffer, heightBuffer);
            width = widthBuffer[0];
            height = heightBuffer[0];
            DesktopCGLFW.getFramebufferSize(windowHandle, widthBuffer, heightBuffer);
            framebufferWidth = widthBuffer[0];
            framebufferHeight = heightBuffer[0];
            DesktopCGLFW.getWindowContentScale(windowHandle, scaleXBuffer, scaleYBuffer);
            contentScaleX = validScale(scaleXBuffer[0]);
            contentScaleY = validScale(scaleYBuffer[0]);
        }

        /**
         * Returns the title.
         *
         * @return the title
         */
        @Override
        public String title() {
            return title;
        }

        /**
         * Runs the title step.
         *
         * @param title the title
         */
        @Override
        public void title(String title) {
            this.title = title != null ? title : "";
            DesktopCGLFW.setWindowTitle(windowHandle, this.title);
        }

        /**
         * Returns the width.
         *
         * @return the width
         */
        @Override
        public int width() {
            return width;
        }

        /**
         * Returns the height.
         *
         * @return the height
         */
        @Override
        public int height() {
            return height;
        }

        /**
         * Returns the framebuffer width.
         *
         * @return the framebuffer width
         */
        @Override
        public int framebufferWidth() {
            return framebufferWidth;
        }

        /**
         * Returns the framebuffer height.
         *
         * @return the framebuffer height
         */
        @Override
        public int framebufferHeight() {
            return framebufferHeight;
        }

        /**
         * Returns the content scale x.
         *
         * @return the content scale x
         */
        @Override
        public float contentScaleX() {
            return contentScaleX;
        }

        /**
         * Returns the content scale y.
         *
         * @return the content scale y
         */
        @Override
        public float contentScaleY() {
            return contentScaleY;
        }

        /**
         * Returns the close requested.
         *
         * @return true if close requested succeeds or is active; false otherwise
         */
        @Override
        public boolean closeRequested() {
            return DesktopCGLFW.windowShouldClose(windowHandle);
        }

        /**
         * Runs the request close step.
         */
        @Override
        public void requestClose() {
            DesktopCGLFW.setWindowShouldClose(windowHandle, true);
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
         * Returns the provider-specific representation requested by the caller.
         *
         * @param <T> the value type
         * @return the as
         */
        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }

        private static float validScale(float scale) {
            return scale > 0.0f && Float.isFinite(scale) ? scale : 1.0f;
        }
    }
}
