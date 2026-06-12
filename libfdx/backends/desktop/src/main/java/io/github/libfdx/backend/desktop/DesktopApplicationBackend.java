package io.github.libfdx.backend.desktop;

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
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.DefaultGraphics;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsClientApi;
import io.github.libfdx.graphics.GraphicsContextProfile;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.DefaultInputCapabilities;
import io.github.libfdx.input.DefaultCursor;
import io.github.libfdx.input.DefaultGamepads;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.math.internal.MathAcceleration;
import io.github.libfdx.runtime.core.RuntimeCore;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWWindowCloseCallback;
import org.lwjgl.glfw.GLFWWindowSizeCallback;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFWNativeWayland.glfwGetWaylandDisplay;
import static org.lwjgl.glfw.GLFWNativeWayland.glfwGetWaylandWindow;
import static org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Display;
import static org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window;

/**
 * Implements the backend integration for desktop application.
 *
 * @author xpenatan
 */
public final class DesktopApplicationBackend implements ApplicationBackend, Application {
    public static final ProviderId ID = ProviderId.of("desktop");

    private final SystemLogger logger = new SystemLogger();
    private final FrameSync sync = new FrameSync();
    private Fdx fdx;
    private ApplicationLifecycle lifecycle = ApplicationLifecycle.DISPOSED;
    private GLFWErrorCallback errorCallback;
    private GLFWFramebufferSizeCallback framebufferSizeCallback;
    private GLFWWindowSizeCallback windowSizeCallback;
    private GLFWWindowCloseCallback closeCallback;
    private GLFWKeyCallback keyCallback;
    private GLFWCharCallback charCallback;
    private GLFWCursorPosCallback cursorPosCallback;
    private GLFWMouseButtonCallback mouseButtonCallback;
    private GLFWScrollCallback scrollCallback;
    private DesktopDisplay display;
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
        DesktopApplicationConfig actualConfig = toDesktopConfig(config);
        DisplayConfig displayConfig = actualConfig.displayConfig();
        GraphicsAttachmentProvider graphicsProvider = actualConfig.graphics();
        if (graphicsProvider == null) {
            throw new FdxException("No graphics provider configured");
        }
        if (actualConfig.graphicsProvider() != null && !actualConfig.graphicsProvider().equals(graphicsProvider.providerId())) {
            throw new FdxException("Configured graphics provider ID does not match attached GraphicsAttachmentProvider");
        }
        GraphicsAttachmentRequirements graphicsRequirements = graphicsProvider.requirements();

        initializeGlfw();
        RuntimeCore.registerProvider(new DesktopRuntimeCoreProvider());
        long windowHandle = createWindow(displayConfig, graphicsRequirements);
        display = new DesktopDisplay(windowHandle, displayConfig.title());
        display.refreshSizes();
        input = new DefaultInput(ProviderId.of("desktop_input"), DefaultInputCapabilities.desktop(),
                new DefaultCursor(), new DefaultGamepads());
        installCallbacks(listener);

        DefaultFileSystem files = new DefaultFileSystem();

        graphics = graphicsProvider.create(new DesktopGraphicsEnvironment(display, createNativeWindow(windowHandle)));
        if (graphicsRequirements.clientApi() == GraphicsClientApi.OPENGL) {
            GLFW.glfwSwapInterval(displayConfig.vSync() ? 1 : 0);
        }
        fdx = new DefaultFdx(this, new DefaultDisplays(display), new DefaultGraphics(graphics), input, files, logger);

        disposed = false;
        running = true;
        lifecycle = ApplicationLifecycle.CREATED;

        if (displayConfig.visible()) {
            GLFW.glfwShowWindow(windowHandle);
        }

        try {
            listener.create(fdx);
            listenerCreated = true;
            listener.resize(display.width(), display.height());
            lifecycle = ApplicationLifecycle.RUNNING;
            loop(listener, displayConfig);
        } finally {
            shutdown(listener);
        }
    }

    /**
     * Runs the start step.
     *
     * @param config the configuration
     * @param listener the listener
     */
    public void start(DesktopApplicationConfig config, ApplicationListener listener) {
        start((ApplicationConfig) config, listener);
    }

    private DesktopApplicationConfig toDesktopConfig(ApplicationConfig config) {
        if (config == null) {
            return new DesktopApplicationConfig();
        }
        if (config instanceof DesktopApplicationConfig) {
            return (DesktopApplicationConfig) config;
        }
        throw new FdxException("DesktopApplicationBackend requires DesktopApplicationConfig");
    }

    private void initializeGlfw() {
        errorCallback = GLFWErrorCallback.createPrint(System.err);
        GLFW.glfwSetErrorCallback(errorCallback);
        if (!GLFW.glfwInit()) {
            throw new FdxException("Unable to initialize GLFW");
        }
    }

    private long createWindow(DisplayConfig config, GraphicsAttachmentRequirements graphicsRequirements) {
        GLFW.glfwDefaultWindowHints();
        applyGraphicsWindowHints(graphicsRequirements);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, config.resizable() ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);

        long windowHandle = GLFW.glfwCreateWindow(config.width(), config.height(), config.title(), 0L, 0L);
        if (windowHandle == 0L) {
            throw new FdxException("Could not create GLFW window");
        }
        centerWindow(windowHandle, config.width(), config.height());
        return windowHandle;
    }

    private void applyGraphicsWindowHints(GraphicsAttachmentRequirements graphicsRequirements) {
        if (graphicsRequirements == null || graphicsRequirements.clientApi() == GraphicsClientApi.NO_API) {
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
            return;
        }
        if (graphicsRequirements.clientApi() == GraphicsClientApi.VULKAN) {
            if (!org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported()) {
                throw new FdxException("Vulkan is not supported by GLFW on this system");
            }
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
            return;
        }
        if (graphicsRequirements.clientApi() != GraphicsClientApi.OPENGL) {
            throw new FdxException("Unsupported desktop graphics client API: " + graphicsRequirements.clientApi());
        }
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, graphicsRequirements.majorVersion());
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, graphicsRequirements.minorVersion());
        GLFW.glfwWindowHint(GLFW.GLFW_DEPTH_BITS, 24);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT,
                graphicsRequirements.forwardCompatible() ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        if (graphicsRequirements.profile() == GraphicsContextProfile.CORE) {
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        } else if (graphicsRequirements.profile() == GraphicsContextProfile.COMPATIBILITY) {
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        } else {
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_ANY_PROFILE);
        }
    }

    private void centerWindow(long windowHandle, int width, int height) {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor == 0L) {
            return;
        }
        org.lwjgl.glfw.GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        if (mode == null) {
            return;
        }
        GLFW.glfwSetWindowPos(windowHandle, (mode.width() - width) / 2, (mode.height() - height) / 2);
    }

    private NativeWindow createNativeWindow(long windowHandle) {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return NativeWindow.windows(windowHandle, GLFWNativeWin32.glfwGetWin32Window(windowHandle));
        }
        if (osName.contains("linux")) {
            if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) {
                return NativeWindow.wayland(windowHandle, glfwGetWaylandDisplay(), glfwGetWaylandWindow(windowHandle));
            }
            return NativeWindow.x11(windowHandle, glfwGetX11Display(), glfwGetX11Window(windowHandle));
        }
        if (osName.contains("mac")) {
            return NativeWindow.macos(windowHandle, windowHandle);
        }
        throw new FdxException("Unsupported native desktop window platform: " + osName);
    }

    private void installCallbacks(final ApplicationListener listener) {
        framebufferSizeCallback = new GLFWFramebufferSizeCallback() {
            @Override
            public void invoke(long window, int width, int height) {
                refreshDisplayAfterResize(listener);
            }
        };
        windowSizeCallback = new GLFWWindowSizeCallback() {
            @Override
            public void invoke(long window, int width, int height) {
                refreshDisplayAfterResize(listener);
            }
        };
        closeCallback = new GLFWWindowCloseCallback() {
            @Override
            public void invoke(long window) {
                running = false;
            }
        };
        keyCallback = new GLFWKeyCallback() {
            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                Key mapped = mapKey(key);
                if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
                    input.dispatchKeyDown(mapped);
                } else if (action == GLFW.GLFW_RELEASE) {
                    input.dispatchKeyUp(mapped);
                }
            }
        };
        charCallback = new GLFWCharCallback() {
            @Override
            public void invoke(long window, int codepoint) {
                input.dispatchTextInput(new String(Character.toChars(codepoint)));
            }
        };
        cursorPosCallback = new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double xpos, double ypos) {
                input.dispatchPointerMoved((int) Math.round(xpos), (int) Math.round(ypos));
            }
        };
        mouseButtonCallback = new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                double[] x = new double[1];
                double[] y = new double[1];
                GLFW.glfwGetCursorPos(window, x, y);
                MouseButton mapped = mapMouseButton(button);
                if (action == GLFW.GLFW_PRESS) {
                    input.dispatchPointerDown(mapped, (int) Math.round(x[0]), (int) Math.round(y[0]));
                } else if (action == GLFW.GLFW_RELEASE) {
                    input.dispatchPointerUp(mapped, (int) Math.round(x[0]), (int) Math.round(y[0]));
                }
            }
        };
        scrollCallback = new GLFWScrollCallback() {
            @Override
            public void invoke(long window, double xoffset, double yoffset) {
                double[] x = new double[1];
                double[] y = new double[1];
                GLFW.glfwGetCursorPos(window, x, y);
                input.dispatchScrolled((int) Math.round(x[0]), (int) Math.round(y[0]), (float) xoffset,
                        (float) -yoffset);
            }
        };
        GLFW.glfwSetFramebufferSizeCallback(display.windowHandle(), framebufferSizeCallback);
        GLFW.glfwSetWindowSizeCallback(display.windowHandle(), windowSizeCallback);
        GLFW.glfwSetWindowCloseCallback(display.windowHandle(), closeCallback);
        GLFW.glfwSetKeyCallback(display.windowHandle(), keyCallback);
        GLFW.glfwSetCharCallback(display.windowHandle(), charCallback);
        GLFW.glfwSetCursorPosCallback(display.windowHandle(), cursorPosCallback);
        GLFW.glfwSetMouseButtonCallback(display.windowHandle(), mouseButtonCallback);
        GLFW.glfwSetScrollCallback(display.windowHandle(), scrollCallback);
    }

    private void refreshDisplayAfterResize(ApplicationListener listener) {
        int oldWidth = display.width();
        int oldHeight = display.height();
        int oldFramebufferWidth = display.framebufferWidth();
        int oldFramebufferHeight = display.framebufferHeight();
        display.refreshSizes();
        if (graphics != null
                && (oldFramebufferWidth != display.framebufferWidth()
                || oldFramebufferHeight != display.framebufferHeight())) {
            graphics.resize(display.framebufferWidth(), display.framebufferHeight());
        }
        if (listenerCreated && (oldWidth != display.width() || oldHeight != display.height())) {
            listener.resize(display.width(), display.height());
        }
    }

    private void loop(ApplicationListener listener, DisplayConfig displayConfig) {
        long lastTime = System.nanoTime();
        while (running && !GLFW.glfwWindowShouldClose(display.windowHandle())) {
            GLFW.glfwPollEvents();
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
            sync.sync(displayConfig.foregroundFps());
        }
    }

    private void shutdown(ApplicationListener listener) {
        if (disposed) {
            return;
        }
        lifecycle = ApplicationLifecycle.PAUSED;
        if (listenerCreated) {
            listener.pause();
        }
        lifecycle = ApplicationLifecycle.DISPOSED;
        try {
            if (listenerCreated) {
                listener.dispose();
            }
        } finally {
            listenerCreated = false;
            if (graphics != null) {
                graphics.dispose();
                graphics = null;
            }
            if (display != null) {
                GLFW.glfwSetFramebufferSizeCallback(display.windowHandle(), null);
                GLFW.glfwSetWindowSizeCallback(display.windowHandle(), null);
                GLFW.glfwSetWindowCloseCallback(display.windowHandle(), null);
                GLFW.glfwSetKeyCallback(display.windowHandle(), null);
                GLFW.glfwSetCharCallback(display.windowHandle(), null);
                GLFW.glfwSetCursorPosCallback(display.windowHandle(), null);
                GLFW.glfwSetMouseButtonCallback(display.windowHandle(), null);
                GLFW.glfwSetScrollCallback(display.windowHandle(), null);
                GLFW.glfwDestroyWindow(display.windowHandle());
                display = null;
            }
            if (framebufferSizeCallback != null) {
                framebufferSizeCallback.free();
                framebufferSizeCallback = null;
            }
            if (windowSizeCallback != null) {
                windowSizeCallback.free();
                windowSizeCallback = null;
            }
            if (closeCallback != null) {
                closeCallback.free();
                closeCallback = null;
            }
            if (keyCallback != null) {
                keyCallback.free();
                keyCallback = null;
            }
            if (charCallback != null) {
                charCallback.free();
                charCallback = null;
            }
            if (cursorPosCallback != null) {
                cursorPosCallback.free();
                cursorPosCallback = null;
            }
            if (mouseButtonCallback != null) {
                mouseButtonCallback.free();
                mouseButtonCallback = null;
            }
            if (scrollCallback != null) {
                scrollCallback.free();
                scrollCallback = null;
            }
            input = null;
            GLFW.glfwTerminate();
            RuntimeCore.registerProvider(null);
            MathAcceleration.register(null);
            if (errorCallback != null) {
                errorCallback.free();
                errorCallback = null;
            }
            running = false;
            disposed = true;
            fdx = null;
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
            GLFW.glfwSetWindowShouldClose(display.windowHandle(), true);
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

    private Key mapKey(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE: return Key.BACKSPACE;
            case GLFW.GLFW_KEY_TAB: return Key.TAB;
            case GLFW.GLFW_KEY_ENTER: return Key.ENTER;
            case GLFW.GLFW_KEY_ESCAPE: return Key.ESCAPE;
            case GLFW.GLFW_KEY_SPACE: return Key.SPACE;
            case GLFW.GLFW_KEY_LEFT: return Key.LEFT;
            case GLFW.GLFW_KEY_RIGHT: return Key.RIGHT;
            case GLFW.GLFW_KEY_UP: return Key.UP;
            case GLFW.GLFW_KEY_DOWN: return Key.DOWN;
            case GLFW.GLFW_KEY_HOME: return Key.HOME;
            case GLFW.GLFW_KEY_END: return Key.END;
            case GLFW.GLFW_KEY_PAGE_UP: return Key.PAGE_UP;
            case GLFW.GLFW_KEY_PAGE_DOWN: return Key.PAGE_DOWN;
            case GLFW.GLFW_KEY_DELETE: return Key.DELETE;
            case GLFW.GLFW_KEY_LEFT_SHIFT: return Key.SHIFT_LEFT;
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return Key.SHIFT_RIGHT;
            case GLFW.GLFW_KEY_LEFT_CONTROL: return Key.CONTROL_LEFT;
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return Key.CONTROL_RIGHT;
            case GLFW.GLFW_KEY_LEFT_ALT: return Key.ALT_LEFT;
            case GLFW.GLFW_KEY_RIGHT_ALT: return Key.ALT_RIGHT;
            case GLFW.GLFW_KEY_A: return Key.A;
            case GLFW.GLFW_KEY_B: return Key.B;
            case GLFW.GLFW_KEY_C: return Key.C;
            case GLFW.GLFW_KEY_D: return Key.D;
            case GLFW.GLFW_KEY_E: return Key.E;
            case GLFW.GLFW_KEY_F: return Key.F;
            case GLFW.GLFW_KEY_G: return Key.G;
            case GLFW.GLFW_KEY_H: return Key.H;
            case GLFW.GLFW_KEY_I: return Key.I;
            case GLFW.GLFW_KEY_J: return Key.J;
            case GLFW.GLFW_KEY_K: return Key.K;
            case GLFW.GLFW_KEY_L: return Key.L;
            case GLFW.GLFW_KEY_M: return Key.M;
            case GLFW.GLFW_KEY_N: return Key.N;
            case GLFW.GLFW_KEY_O: return Key.O;
            case GLFW.GLFW_KEY_P: return Key.P;
            case GLFW.GLFW_KEY_Q: return Key.Q;
            case GLFW.GLFW_KEY_R: return Key.R;
            case GLFW.GLFW_KEY_S: return Key.S;
            case GLFW.GLFW_KEY_T: return Key.T;
            case GLFW.GLFW_KEY_U: return Key.U;
            case GLFW.GLFW_KEY_V: return Key.V;
            case GLFW.GLFW_KEY_W: return Key.W;
            case GLFW.GLFW_KEY_X: return Key.X;
            case GLFW.GLFW_KEY_Y: return Key.Y;
            case GLFW.GLFW_KEY_Z: return Key.Z;
            case GLFW.GLFW_KEY_0: return Key.NUM_0;
            case GLFW.GLFW_KEY_1: return Key.NUM_1;
            case GLFW.GLFW_KEY_2: return Key.NUM_2;
            case GLFW.GLFW_KEY_3: return Key.NUM_3;
            case GLFW.GLFW_KEY_4: return Key.NUM_4;
            case GLFW.GLFW_KEY_5: return Key.NUM_5;
            case GLFW.GLFW_KEY_6: return Key.NUM_6;
            case GLFW.GLFW_KEY_7: return Key.NUM_7;
            case GLFW.GLFW_KEY_8: return Key.NUM_8;
            case GLFW.GLFW_KEY_9: return Key.NUM_9;
            case GLFW.GLFW_KEY_F1: return Key.F1;
            case GLFW.GLFW_KEY_F2: return Key.F2;
            case GLFW.GLFW_KEY_F3: return Key.F3;
            case GLFW.GLFW_KEY_F4: return Key.F4;
            case GLFW.GLFW_KEY_F5: return Key.F5;
            case GLFW.GLFW_KEY_F6: return Key.F6;
            case GLFW.GLFW_KEY_F7: return Key.F7;
            case GLFW.GLFW_KEY_F8: return Key.F8;
            case GLFW.GLFW_KEY_F9: return Key.F9;
            case GLFW.GLFW_KEY_F10: return Key.F10;
            case GLFW.GLFW_KEY_F11: return Key.F11;
            case GLFW.GLFW_KEY_F12: return Key.F12;
            default: return Key.UNKNOWN;
        }
    }

    private MouseButton mapMouseButton(int button) {
        switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT: return MouseButton.LEFT;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT: return MouseButton.RIGHT;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE: return MouseButton.MIDDLE;
            case GLFW.GLFW_MOUSE_BUTTON_4: return MouseButton.BACK;
            case GLFW.GLFW_MOUSE_BUTTON_5: return MouseButton.FORWARD;
            default: return MouseButton.UNKNOWN;
        }
    }

    /**
     * Represents a frame sync.
     *
     * @author xpenatan
     */
    private static final class FrameSync {
        void sync(int fps) {
            if (fps <= 0) {
                return;
            }
            long sleepMillis = 1000L / fps;
            if (sleepMillis <= 0L) {
                return;
            }
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Represents a desktop graphics environment.
     *
     * @author xpenatan
     */
    private static final class DesktopGraphicsEnvironment implements GraphicsEnvironment {
        private final Display display;
        private final NativeWindow nativeWindow;

        DesktopGraphicsEnvironment(Display display, NativeWindow nativeWindow) {
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
     * Represents a desktop display.
     *
     * @author xpenatan
     */
    private static final class DesktopDisplay implements Display {
        private final long windowHandle;
        private String title;
        private int width;
        private int height;
        private int framebufferWidth;
        private int framebufferHeight;
        private final IntBuffer widthBuffer = BufferUtils.createIntBuffer(1);
        private final IntBuffer heightBuffer = BufferUtils.createIntBuffer(1);
        private final FloatBuffer scaleXBuffer = BufferUtils.createFloatBuffer(1);
        private final FloatBuffer scaleYBuffer = BufferUtils.createFloatBuffer(1);
        private float contentScaleX = 1.0f;
        private float contentScaleY = 1.0f;

        DesktopDisplay(long windowHandle, String title) {
            this.windowHandle = windowHandle;
            this.title = title;
        }

        long windowHandle() {
            return windowHandle;
        }

        void refreshSizes() {
            widthBuffer.clear();
            heightBuffer.clear();
            GLFW.glfwGetWindowSize(windowHandle, widthBuffer, heightBuffer);
            width = widthBuffer.get(0);
            height = heightBuffer.get(0);

            widthBuffer.clear();
            heightBuffer.clear();
            GLFW.glfwGetFramebufferSize(windowHandle, widthBuffer, heightBuffer);
            framebufferWidth = widthBuffer.get(0);
            framebufferHeight = heightBuffer.get(0);

            scaleXBuffer.clear();
            scaleYBuffer.clear();
            GLFW.glfwGetWindowContentScale(windowHandle, scaleXBuffer, scaleYBuffer);
            contentScaleX = validScale(scaleXBuffer.get(0));
            contentScaleY = validScale(scaleYBuffer.get(0));
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
            GLFW.glfwSetWindowTitle(windowHandle, this.title);
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
            return GLFW.glfwWindowShouldClose(windowHandle);
        }

        /**
         * Runs the request close step.
         */
        @Override
        public void requestClose() {
            GLFW.glfwSetWindowShouldClose(windowHandle, true);
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
