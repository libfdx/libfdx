package io.github.libfdx.backend.iosc;

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
import io.github.libfdx.display.DefaultDisplays;
import io.github.libfdx.display.Display;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.files.DefaultFileSystem;
import io.github.libfdx.graphics.DefaultGraphics;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.GraphicsProviderSupport;
import io.github.libfdx.input.DefaultCursor;
import io.github.libfdx.input.DefaultGamepads;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.DefaultInputCapabilities;
import io.github.libfdx.runtime.core.RuntimeCore;
import org.teavm.interop.Export;
import org.teavm.interop.Import;
import org.teavm.runtime.Fiber;

/**
 * Implements the backend integration for iOS C application.
 *
 * @author xpenatan
 */
public final class IosCApplicationBackend implements ApplicationBackend, Application {
    public static final ProviderId ID = ProviderId.of("ios_c");
    private static final int TOUCH_DOWN = 0;
    private static final int TOUCH_MOVE = 1;
    private static boolean preserveCallbackExports;
    private static IosCApplicationBackend current;
    private static int statusCode;
    private static int statusStage;

    private final Logger logger = new IosCLogger();
    private IosCApplicationConfig config;
    private ApplicationListener listener;
    private Fdx fdx;
    private IosCDisplay display;
    private GraphicsAttachment graphics;
    private DefaultInput input;
    private ApplicationLifecycle lifecycle = ApplicationLifecycle.DISPOSED;
    private boolean running;
    private boolean paused;
    private boolean disposed = true;
    private boolean listenerCreated;
    private long lastFrameMillis;
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
        IosCApplicationConfig actualConfig = toIosCConfig(config);
        GraphicsAttachmentProvider graphicsProvider = actualConfig.graphics();
        if (graphicsProvider == null) {
            throw new FdxException("No iOS C graphics provider configured");
        }
        if (actualConfig.graphicsProvider() != null
                && !actualConfig.graphicsProvider().equals(graphicsProvider.providerId())) {
            throw new FdxException("Configured graphics provider ID does not match attached GraphicsAttachmentProvider");
        }
        String supportFailure = supportFailureReason(graphicsProvider);
        if (supportFailure != null) {
            throw new FdxException(supportFailure);
        }

        preserveCallbackExports();
        this.config = actualConfig;
        this.listener = listener;
        DisplayConfig displayConfig = actualConfig.displayConfig();
        display = new IosCDisplay(displayConfig.title());
        display.size(displayConfig.width(), displayConfig.height(), displayConfig.width(), displayConfig.height(), 1.0f);
        graphics = graphicsProvider.create(new IosCGraphicsEnvironment(display));
        input = new DefaultInput(ProviderId.of("ios_c_input"), DefaultInputCapabilities.touch(),
                new DefaultCursor(), new DefaultGamepads());
        fdx = new DefaultFdx(this, new DefaultDisplays(display), new DefaultGraphics(graphics), input,
                new DefaultFileSystem(), logger);
        RuntimeCore.registerProvider(null);

        current = this;
        disposed = false;
        running = true;
        paused = false;
        lifecycle = ApplicationLifecycle.CREATED;
        listener.create(fdx);
        listenerCreated = true;
        listener.resize(display.width(), display.height());
        lifecycle = ApplicationLifecycle.RUNNING;
        lastFrameMillis = System.currentTimeMillis();
        statusCode = 1;
    }

    /**
     * Runs the start step.
     *
     * @param config the configuration
     * @param listener the listener
     */
    public void start(IosCApplicationConfig config, ApplicationListener listener) {
        start((ApplicationConfig) config, listener);
    }

    private IosCApplicationConfig toIosCConfig(ApplicationConfig config) {
        if (config == null) {
            return new IosCApplicationConfig();
        }
        if (config instanceof IosCApplicationConfig) {
            return (IosCApplicationConfig) config;
        }
        throw new FdxException("IosCApplicationBackend requires IosCApplicationConfig");
    }

    private static String supportFailureReason(GraphicsAttachmentProvider provider) {
        if (provider instanceof GraphicsProviderSupport && !((GraphicsProviderSupport) provider).isSupported()) {
            String reason = ((GraphicsProviderSupport) provider).supportFailureReason();
            return reason != null ? reason : "iOS C graphics provider is not supported";
        }
        return null;
    }

    /**
     * Handles the host resize callback.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param scale the content scale
     */
    @Export(name = "libfdx_ios_c_resize")
    public static void resize(int width, int height, float scale) {
        IosCApplicationBackend app = current;
        if (app != null) {
            runOnTeaVMFiber(new Fiber.FiberRunner() {
                @Override
                public void run() {
                    app.onResize(width, height, scale);
                }
            });
        }
    }

    /**
     * Handles the host render callback.
     */
    @Export(name = "libfdx_ios_c_render")
    public static void render() {
        IosCApplicationBackend app = current;
        if (app != null && statusCode >= 0) {
            runOnTeaVMFiber(new Fiber.FiberRunner() {
                @Override
                public void run() {
                    app.onRender();
                }
            });
        }
    }

    /**
     * Handles the host pause callback.
     */
    @Export(name = "libfdx_ios_c_pause")
    public static void pause() {
        IosCApplicationBackend app = current;
        if (app != null) {
            runOnTeaVMFiber(new Fiber.FiberRunner() {
                @Override
                public void run() {
                    app.onPause();
                }
            });
        }
    }

    /**
     * Handles the host resume callback.
     */
    @Export(name = "libfdx_ios_c_resume")
    public static void resume() {
        IosCApplicationBackend app = current;
        if (app != null) {
            runOnTeaVMFiber(new Fiber.FiberRunner() {
                @Override
                public void run() {
                    app.onResume();
                }
            });
        }
    }

    /**
     * Handles the host dispose callback.
     */
    @Export(name = "libfdx_ios_c_dispose")
    public static void disposeCurrent() {
        IosCApplicationBackend app = current;
        if (app != null) {
            runOnTeaVMFiber(new Fiber.FiberRunner() {
                @Override
                public void run() {
                    app.dispose();
                }
            });
        }
    }

    /**
     * Handles the host touch callback.
     *
     * @param type the touch type
     * @param pointer the pointer index
     * @param x the x coordinate
     * @param y the y coordinate
     * @param pressure the touch pressure
     */
    @Export(name = "libfdx_ios_c_touch")
    public static void touch(int type, int pointer, int x, int y, float pressure) {
        IosCApplicationBackend app = current;
        if (app != null) {
            runOnTeaVMFiber(new Fiber.FiberRunner() {
                @Override
                public void run() {
                    app.onTouch(type, pointer, x, y, pressure);
                }
            });
        }
    }

    /**
     * Returns the current host status code.
     *
     * @return the current host status code
     */
    @Export(name = "libfdx_ios_c_status_code")
    public static int statusCode() {
        return statusCode;
    }

    private static void preserveCallbackExports() {
        if (preserveCallbackExports) {
            resize(0, 0, 1.0f);
            render();
            pause();
            resume();
            disposeCurrent();
            touch(0, 0, 0, 0, 0.0f);
            statusCode();
        }
    }

    private static void runOnTeaVMFiber(final Fiber.FiberRunner runner) {
        Fiber.start(new Fiber.FiberRunner() {
            @Override
            public void run() {
                try {
                    runner.run();
                } catch (Throwable error) {
                    int stage = statusStage == 0 ? 1 : statusStage;
                    statusCode = -(stage * 100 + exceptionCode(error));
                    IosCApplicationBackend app = current;
                    if (app != null) {
                        app.paused = true;
                        app.logger.error("iOS C callback failed", error);
                    } else {
                        iosLog(error.toString());
                    }
                }
            }
        }, true);
    }

    private static int exceptionCode(Throwable error) {
        if (error instanceof NullPointerException) {
            return 1;
        }
        if (error instanceof IllegalStateException) {
            return 2;
        }
        if (error instanceof IllegalArgumentException) {
            return 3;
        }
        if (error instanceof FdxException) {
            return 4;
        }
        return 99;
    }

    private void onResize(int width, int height, float scale) {
        if (display == null) {
            return;
        }
        int framebufferWidth = Math.max(1, width);
        int framebufferHeight = Math.max(1, height);
        float actualScale = scale > 0.0f && Float.isFinite(scale) ? scale : 1.0f;
        int logicalWidth = Math.max(1, Math.round(framebufferWidth / actualScale));
        int logicalHeight = Math.max(1, Math.round(framebufferHeight / actualScale));
        boolean changed = display.size(logicalWidth, logicalHeight, framebufferWidth, framebufferHeight, actualScale);
        if (graphics != null) {
            graphics.resize(display.framebufferWidth(), display.framebufferHeight());
        }
        if (changed && listenerCreated && listener != null) {
            listener.resize(display.width(), display.height());
        }
    }

    private void onRender() {
        if (!running || paused || disposed || listener == null) {
            return;
        }
        if (graphics != null) {
            graphics.processEvents();
        }
        long now = System.currentTimeMillis();
        deltaTime = (now - lastFrameMillis) / 1000.0f;
        lastFrameMillis = now;
        frameId++;

        if (graphics == null || graphics.beginFrame()) {
            statusStage = 20;
            try {
                listener.render();
                statusCode = 2;
                if (graphics != null) {
                    listener.onFrameEnd();
                }
            } finally {
                statusStage = 0;
                if (graphics != null) {
                    graphics.endFrame();
                }
            }
        }
    }

    private void onPause() {
        if (paused || disposed) {
            return;
        }
        paused = true;
        if (listenerCreated && listener != null) {
            listener.pause();
            lifecycle = ApplicationLifecycle.PAUSED;
        }
    }

    private void onResume() {
        if (disposed) {
            return;
        }
        boolean wasPaused = paused;
        paused = false;
        if (listenerCreated && listener != null && wasPaused) {
            listener.resume();
            lifecycle = ApplicationLifecycle.RUNNING;
            lastFrameMillis = System.currentTimeMillis();
        }
    }

    private void onTouch(int type, int pointer, int x, int y, float pressure) {
        if (input == null) {
            return;
        }
        float actualPressure = pressure > 0.0f && Float.isFinite(pressure) ? pressure : 1.0f;
        if (type == TOUCH_DOWN) {
            input.dispatchTouchDown(pointer, x, y, actualPressure);
        } else if (type == TOUCH_MOVE) {
            input.dispatchTouchMoved(pointer, x, y, actualPressure);
        } else {
            input.dispatchTouchUp(pointer, x, y, actualPressure);
        }
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
            display.requestClose();
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
        if (disposed) {
            return;
        }
        running = false;
        lifecycle = ApplicationLifecycle.DISPOSED;
        try {
            if (listenerCreated && listener != null) {
                if (!paused) {
                    listener.pause();
                }
                listener.dispose();
            }
        } finally {
            listenerCreated = false;
            if (graphics != null) {
                graphics.dispose();
                graphics = null;
            }
            RuntimeCore.registerProvider(null);
            input = null;
            fdx = null;
            listener = null;
            display = null;
            config = null;
            current = null;
            disposed = true;
        }
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

    @Import(name = "libfdx_ios_c_log")
    private static native void iosLog(String message);

    /**
     * Represents an iOS C graphics environment.
     *
     * @author xpenatan
     */
    private static final class IosCGraphicsEnvironment implements GraphicsEnvironment {
        private final Display display;

        IosCGraphicsEnvironment(Display display) {
            this.display = display;
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
        public io.github.libfdx.graphics.NativeWindow nativeWindow() {
            return null;
        }
    }

    /**
     * Represents an iOS C display.
     *
     * @author xpenatan
     */
    private static final class IosCDisplay implements Display {
        private String title;
        private int width;
        private int height;
        private int framebufferWidth;
        private int framebufferHeight;
        private float contentScaleX = 1.0f;
        private float contentScaleY = 1.0f;
        private boolean closeRequested;

        IosCDisplay(String title) {
            this.title = title != null ? title : "";
        }

        boolean size(int width, int height, int framebufferWidth, int framebufferHeight, float scale) {
            boolean changed = this.width != width || this.height != height
                    || this.framebufferWidth != framebufferWidth || this.framebufferHeight != framebufferHeight
                    || this.contentScaleX != scale || this.contentScaleY != scale;
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            this.framebufferWidth = Math.max(1, framebufferWidth);
            this.framebufferHeight = Math.max(1, framebufferHeight);
            this.contentScaleX = scale;
            this.contentScaleY = scale;
            return changed;
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
            return closeRequested;
        }

        /**
         * Runs the request close step.
         */
        @Override
        public void requestClose() {
            closeRequested = true;
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
    }

    /**
     * Writes iOS C log messages.
     *
     * @author xpenatan
     */
    private static final class IosCLogger implements Logger {
        /**
         * Runs the debug step.
         *
         * @param message the message
         */
        @Override
        public void debug(String message) {
            iosLog("[debug] " + safe(message));
        }

        /**
         * Runs the info step.
         *
         * @param message the message
         */
        @Override
        public void info(String message) {
            iosLog("[info] " + safe(message));
        }

        /**
         * Runs the warn step.
         *
         * @param message the message
         */
        @Override
        public void warn(String message) {
            iosLog("[warn] " + safe(message));
        }

        /**
         * Runs the error step.
         *
         * @param message the message
         */
        @Override
        public void error(String message) {
            iosLog("[error] " + safe(message));
        }

        /**
         * Runs the error step.
         *
         * @param message the message
         * @param error the error
         */
        @Override
        public void error(String message, Throwable error) {
            iosLog("[error] " + safe(message));
            if (error != null) {
                iosLog(safe(error.toString()));
                StackTraceElement[] stack = error.getStackTrace();
                int count = Math.min(8, stack.length);
                for (int i = 0; i < count; i++) {
                    iosLog("  at " + stack[i]);
                }
            }
        }

        private static String safe(String value) {
            return value != null ? value : "";
        }
    }
}
