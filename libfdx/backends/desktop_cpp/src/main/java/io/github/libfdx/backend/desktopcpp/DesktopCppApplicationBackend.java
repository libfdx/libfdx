package io.github.libfdx.backend.desktopcpp;

import io.github.libfdx.DefaultFdx;
import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationBackend;
import io.github.libfdx.application.ApplicationConfig;
import io.github.libfdx.application.ApplicationLifecycle;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.audio.Audio;
import io.github.libfdx.core.FdxException;
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
import io.github.libfdx.input.DefaultGamepads;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.DefaultInputCapabilities;
import io.github.libfdx.runtime.core.RuntimeCore;
import io.github.libfdx.storage.DefaultStorage;

/**
 * Implements the backend integration for jNative application.
 *
 * @author xpenatan
 */
public final class DesktopCppApplicationBackend implements ApplicationBackend, Application {
    public static final ProviderId ID = ProviderId.of("desktop_cpp");

    private final SystemLogger logger = new SystemLogger();
    private Fdx fdx;
    private ApplicationLifecycle lifecycle = ApplicationLifecycle.DISPOSED;
    private WindowDisplay display;
    private GraphicsAttachment graphics;
    private Audio audio;
    private DefaultInput input;
    private DesktopCppInput nativeInput;
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
        DesktopCppApplicationConfig actualConfig = toDesktopCppConfig(config);
        DisplayConfig displayConfig = actualConfig.displayConfig();
        GraphicsAttachmentProvider graphicsProvider = actualConfig.graphics();
        if (graphicsProvider == null) {
            throw new FdxException("No desktop_cpp graphics provider configured");
        }
        if (actualConfig.graphicsProvider() != null
                && !actualConfig.graphicsProvider().equals(graphicsProvider.providerId())) {
            throw new FdxException(
                    "Configured graphics provider ID does not match attached"
                            + " GraphicsAttachmentProvider");
        }
        GraphicsAttachmentRequirements requirements = graphicsProvider.requirements();

        initializeGlfw();
        long windowHandle = 0L;
        try {
            windowHandle = createWindow(displayConfig, requirements);
            display = new WindowDisplay(windowHandle, displayConfig.title());
            display.refreshSizes();
            nativeInput = new DesktopCppInput(windowHandle);
            input =
                    new DefaultInput(
                            ProviderId.of("desktop_cpp_input"),
                            DefaultInputCapabilities.desktop(),
                            nativeInput,
                            new DefaultGamepads(),
                            null,
                            new DesktopCppClipboard(windowHandle));

            graphics =
                    graphicsProvider.create(
                            new Environment(display, NativeWindow.glfw(windowHandle)));
        } catch (RuntimeException error) {
            if (nativeInput != null) {
                nativeInput.dispose();
                nativeInput = null;
            }
            if (display != null) {
                DesktopCppGLFW.destroyWindow(display.windowHandle());
                display = null;
            } else if (windowHandle != 0L) {
                DesktopCppGLFW.destroyWindow(windowHandle);
            }
            DesktopCppGLFW.terminate();
            throw error;
        }
        if (requirements.clientApi() == GraphicsClientApi.OPENGL) {
            DesktopCppGLFW.swapInterval(displayConfig.vSync() ? 1 : 0);
        }
        disposed = false;
        running = true;
        lifecycle = ApplicationLifecycle.CREATED;

        if (displayConfig.visible()) {
            DesktopCppGLFW.showWindow(windowHandle);
        }

        String phase = "create";
        Throwable applicationFailure = null;
        try {
            RuntimeCore.registerProvider(new DesktopCppRuntimeCoreProvider());
            if (actualConfig.audio() != null) {
                audio = actualConfig.audio().create();
            }
            DefaultFileSystem files = new DefaultFileSystem();
            fdx =
                    new DefaultFdx(
                            this,
                            new DefaultDisplays(display),
                            new DefaultGraphics(graphics),
                            input,
                            files,
                            new DefaultStorage(files),
                            null,
                            audio,
                            logger);
            listener.create(fdx);
            listenerCreated = true;
            phase = "resize";
            listener.resize(display.width(), display.height());
            lifecycle = ApplicationLifecycle.RUNNING;
            phase = "loop";
            loop(listener, displayConfig);
        } catch (Throwable error) {
            logger.error("jNative application failed during " + phase, error);
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
                    : new FdxException(
                            "jNative application failed during " + phase, applicationFailure);
        }
        if (shutdownFailure != null) {
            if (shutdownFailure instanceof Error) {
                throw (Error) shutdownFailure;
            }
            throw shutdownFailure instanceof RuntimeException
                    ? (RuntimeException) shutdownFailure
                    : new FdxException(
                            "jNative application failed during shutdown", shutdownFailure);
        }
    }

    /**
     * Runs the start step.
     *
     * @param config the configuration
     * @param listener the listener
     */
    public void start(DesktopCppApplicationConfig config, ApplicationListener listener) {
        start((ApplicationConfig) config, listener);
    }

    private DesktopCppApplicationConfig toDesktopCppConfig(ApplicationConfig config) {
        if (config == null) {
            return new DesktopCppApplicationConfig();
        }
        if (config instanceof DesktopCppApplicationConfig) {
            return (DesktopCppApplicationConfig) config;
        }
        throw new FdxException("DesktopCppApplicationBackend requires DesktopCppApplicationConfig");
    }

    private void initializeGlfw() {
        if (!DesktopCppGLFW.init()) {
            throw new FdxException("Unable to initialize GLFW");
        }
    }

    private long createWindow(DisplayConfig config, GraphicsAttachmentRequirements requirements) {
        DesktopCppGLFW.defaultWindowHints();
        applyGraphicsWindowHints(requirements);
        DesktopCppGLFW.windowHint(DesktopCppGLFW.VISIBLE, DesktopCppGLFW.FALSE);
        DesktopCppGLFW.windowHint(
                DesktopCppGLFW.RESIZABLE,
                config.resizable() ? DesktopCppGLFW.TRUE : DesktopCppGLFW.FALSE);
        DesktopCppGLFW.windowHint(
                DesktopCppGLFW.MAXIMIZED,
                config.maximized() ? DesktopCppGLFW.TRUE : DesktopCppGLFW.FALSE);
        long windowHandle =
                DesktopCppGLFW.createWindow(config.width(), config.height(), config.title());
        if (windowHandle == 0L) {
            throw new FdxException("Could not create GLFW window");
        }
        return windowHandle;
    }

    private void applyGraphicsWindowHints(GraphicsAttachmentRequirements requirements) {
        if (requirements == null || requirements.clientApi() == GraphicsClientApi.NO_API) {
            DesktopCppGLFW.windowHint(DesktopCppGLFW.CLIENT_API, DesktopCppGLFW.NO_API);
            return;
        }
        if (requirements.clientApi() == GraphicsClientApi.VULKAN) {
            DesktopCppGLFW.windowHint(DesktopCppGLFW.CLIENT_API, DesktopCppGLFW.NO_API);
            return;
        }
        if (requirements.clientApi() != GraphicsClientApi.OPENGL) {
            throw new FdxException(
                    "Unsupported desktop_cpp graphics client API: " + requirements.clientApi());
        }
        DesktopCppGLFW.windowHint(DesktopCppGLFW.CLIENT_API, DesktopCppGLFW.OPENGL_API);
        DesktopCppGLFW.windowHint(
                DesktopCppGLFW.CONTEXT_VERSION_MAJOR, requirements.majorVersion());
        DesktopCppGLFW.windowHint(
                DesktopCppGLFW.CONTEXT_VERSION_MINOR, requirements.minorVersion());
        DesktopCppGLFW.windowHint(
                DesktopCppGLFW.OPENGL_FORWARD_COMPAT,
                requirements.forwardCompatible() ? DesktopCppGLFW.TRUE : DesktopCppGLFW.FALSE);
        if (requirements.profile() == GraphicsContextProfile.CORE) {
            DesktopCppGLFW.windowHint(
                    DesktopCppGLFW.OPENGL_PROFILE, DesktopCppGLFW.OPENGL_CORE_PROFILE);
        } else if (requirements.profile() == GraphicsContextProfile.COMPATIBILITY) {
            DesktopCppGLFW.windowHint(
                    DesktopCppGLFW.OPENGL_PROFILE, DesktopCppGLFW.OPENGL_COMPAT_PROFILE);
        } else {
            DesktopCppGLFW.windowHint(
                    DesktopCppGLFW.OPENGL_PROFILE, DesktopCppGLFW.OPENGL_ANY_PROFILE);
        }
    }

    private void loop(ApplicationListener listener, DisplayConfig displayConfig) {
        long lastTime = System.nanoTime();
        int lastWindowWidth = display.width();
        int lastWindowHeight = display.height();
        int lastFramebufferWidth = display.framebufferWidth();
        int lastFramebufferHeight = display.framebufferHeight();
        while (running && !DesktopCppGLFW.windowShouldClose(display.windowHandle())) {
            try {
                DesktopCppGLFW.pollEvents();
                nativeInput.drain(input, display);
                if (audio != null) {
                    audio.update();
                }
                boolean windowSizeChanged =
                        display.width() != lastWindowWidth || display.height() != lastWindowHeight;
                boolean framebufferSizeChanged =
                        display.framebufferWidth() != lastFramebufferWidth
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
                if (running && !DesktopCppGLFW.windowShouldClose(display.windowHandle())) {
                    sync(displayConfig.foregroundFps());
                }
            } catch (Throwable error) {
                logger.error("jNative application frame failed", error);
                throw error instanceof RuntimeException
                        ? (RuntimeException) error
                        : new FdxException("jNative application frame failed", error);
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
        DesktopCppGLFW.waitEventsTimeout(sleepMillis / 1000.0);
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

        Audio closingAudio = audio;
        audio = null;
        if (closingAudio != null) {
            try {
                closingAudio.dispose();
            } catch (Throwable error) {
                failure = recordShutdownFailure(failure, "audio dispose", error);
            }
        }

        GraphicsAttachment closingGraphics = graphics;
        graphics = null;
        if (closingGraphics != null) {
            try {
                closingGraphics.dispose();
            } catch (Throwable error) {
                failure = recordShutdownFailure(failure, "graphics dispose", error);
            }
        }

        WindowDisplay closingDisplay = display;
        display = null;
        if (closingDisplay != null) {
            try {
                if (nativeInput != null) {
                    nativeInput.dispose();
                    nativeInput = null;
                }
                DesktopCppGLFW.destroyWindow(closingDisplay.windowHandle());
            } catch (Throwable error) {
                failure = recordShutdownFailure(failure, "window destroy", error);
            }
        }

        input = null;
        RuntimeCore.registerProvider(null);
        try {
            DesktopCppGLFW.terminate();
        } catch (Throwable error) {
            failure = recordShutdownFailure(failure, "GLFW terminate", error);
        }
        running = false;
        disposed = true;
        fdx = null;
        return failure;
    }

    private Throwable recordShutdownFailure(Throwable firstFailure, String phase, Throwable error) {
        logger.error("jNative application failed during shutdown " + phase, error);
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

    /** Runs the request exit step. */
    @Override
    public void requestExit() {
        running = false;
        if (display != null) {
            DesktopCppGLFW.setWindowShouldClose(display.windowHandle(), true);
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

    /** Releases resources held by this instance. */
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
     * Represents a jNative graphics environment.
     *
     * @author xpenatan
     */
    private static final class Environment implements GraphicsEnvironment {
        private final Display display;
        private final NativeWindow nativeWindow;

        Environment(Display display, NativeWindow nativeWindow) {
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
     * Represents a jNative display.
     *
     * @author xpenatan
     */
    static final class WindowDisplay implements Display {
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

        WindowDisplay(long windowHandle, String title) {
            this.windowHandle = windowHandle;
            this.title = title != null ? title : "";
        }

        long windowHandle() {
            return windowHandle;
        }

        void refreshSizes() {
            DesktopCppGLFW.getWindowSize(windowHandle, widthBuffer, heightBuffer);
            width = widthBuffer[0];
            height = heightBuffer[0];
            DesktopCppGLFW.getFramebufferSize(windowHandle, widthBuffer, heightBuffer);
            framebufferWidth = widthBuffer[0];
            framebufferHeight = heightBuffer[0];
            DesktopCppGLFW.getWindowContentScale(windowHandle, scaleXBuffer, scaleYBuffer);
            contentScaleX = validScale(scaleXBuffer[0]);
            contentScaleY = validScale(scaleYBuffer[0]);
        }

        void windowSizeChanged(int width, int height) {
            this.width = width;
            this.height = height;
        }

        void framebufferSizeChanged(int width, int height) {
            framebufferWidth = width;
            framebufferHeight = height;
        }

        void contentScaleChanged(float x, float y) {
            contentScaleX = validScale(x);
            contentScaleY = validScale(y);
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
            DesktopCppGLFW.setWindowTitle(windowHandle, this.title);
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
            return DesktopCppGLFW.windowShouldClose(windowHandle);
        }

        /** Runs the request close step. */
        @Override
        public void requestClose() {
            DesktopCppGLFW.setWindowShouldClose(windowHandle, true);
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
