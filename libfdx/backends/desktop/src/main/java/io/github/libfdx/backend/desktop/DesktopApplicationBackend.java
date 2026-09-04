package io.github.libfdx.backend.desktop;

import io.github.libfdx.math.ClipDepthRange;
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
import io.github.libfdx.display.Display;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.display.Displays;
import io.github.libfdx.files.DefaultFileSystem;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.Graphics;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsClientApi;
import io.github.libfdx.graphics.GraphicsConfig;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsContextProfile;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.GraphicsFrameMetrics;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.input.Cursor;
import io.github.libfdx.input.CursorShape;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.DefaultInputCapabilities;
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
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private final DesktopFrameProfiler frameProfiler = new DesktopFrameProfiler(
            logger, Boolean.parseBoolean(System.getProperty(
            "libfdx.profileFrames", "false")));
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
    private GraphicsAttachmentProvider graphicsProvider;
    private GraphicsAttachmentRequirements graphicsRequirements;
    private DefaultInput input;
    private DesktopCursor cursor;
    private final Map<DesktopDisplay, SecondaryWindowCallbacks> secondaryWindows =
            new LinkedHashMap<DesktopDisplay, SecondaryWindowCallbacks>();
    private final Map<GraphicsAttachment, DesktopDisplay> secondaryGraphics =
            new IdentityHashMap<GraphicsAttachment, DesktopDisplay>();
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
        this.graphicsProvider = graphicsProvider;
        this.graphicsRequirements = graphicsRequirements;

        initializeGlfw();
        RuntimeCore.registerProvider(new DesktopRuntimeCoreProvider());
        long windowHandle = createWindow(displayConfig, graphicsRequirements);
        display = new DesktopDisplay(windowHandle, displayConfig.title());
        display.refreshSizes();
        cursor = new DesktopCursor(windowHandle);
        input = new DefaultInput(ProviderId.of("desktop_input"), DefaultInputCapabilities.desktop(),
                cursor, new DefaultGamepads(), null, new DesktopClipboard(windowHandle));
        installCallbacks(listener);

        DefaultFileSystem files = new DefaultFileSystem()
                .classpathResourceResolver(
                        new DesktopClasspathResourceResolver());

        graphics = graphicsProvider.create(new DesktopGraphicsEnvironment(display, createNativeWindow(windowHandle)));
        if (graphicsRequirements.clientApi() == GraphicsClientApi.OPENGL) {
            GLFW.glfwSwapInterval(displayConfig.vSync() ? 1 : 0);
        }
        // Publish the range this device clips against. The other backends get
        // this from DefaultGraphics; this one has its own Graphics, so it has
        // to say so itself or the default is never corrected for OpenGL.
        ClipDepthRange.setDefault(graphics.device().capabilities().clipDepthRange());
        fdx = new DefaultFdx(this, new DesktopDisplays(), new DesktopGraphics(), input, files, logger);

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
        GLFW.glfwWindowHint(GLFW.GLFW_MAXIMIZED, config.maximized() ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        if (graphicsRequirements.clientApi() == GraphicsClientApi.OPENGL) {
            GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES, config.samples());
        }

        long sharedWindow = graphicsRequirements.clientApi() == GraphicsClientApi.OPENGL && display != null
                ? display.windowHandle()
                : 0L;
        long windowHandle = GLFW.glfwCreateWindow(config.width(), config.height(), config.title(), 0L, sharedWindow);
        if (windowHandle == 0L) {
            throw new FdxException("Could not create GLFW window");
        }
        if (!config.maximized()) {
            centerWindow(windowHandle, config.width(), config.height());
        }
        return windowHandle;
    }

    private DesktopDisplay createSecondaryDisplay(DisplayConfig config) {
        if (!running || disposed) {
            throw new FdxException("Cannot create a display when the desktop backend is not running");
        }
        DisplayConfig actualConfig = config != null ? config : new DisplayConfig();
        long windowHandle = createWindow(actualConfig, graphicsRequirements);
        DesktopDisplay secondaryDisplay = new DesktopDisplay(windowHandle, actualConfig.title());
        secondaryDisplay.refreshSizes();
        SecondaryWindowCallbacks callbacks = new SecondaryWindowCallbacks(secondaryDisplay);
        callbacks.install();
        secondaryWindows.put(secondaryDisplay, callbacks);
        if (actualConfig.visible()) {
            secondaryDisplay.show();
        }
        return secondaryDisplay;
    }

    private void destroySecondaryDisplay(Display candidate) {
        if (!(candidate instanceof DesktopDisplay) || candidate == display) {
            return;
        }
        DesktopDisplay secondaryDisplay = (DesktopDisplay) candidate;
        GraphicsAttachment attachedGraphics = null;
        for (Map.Entry<GraphicsAttachment, DesktopDisplay> entry : secondaryGraphics.entrySet()) {
            if (entry.getValue() == secondaryDisplay) {
                attachedGraphics = entry.getKey();
                break;
            }
        }
        if (attachedGraphics != null) {
            destroySecondaryGraphics(attachedGraphics);
        }
        SecondaryWindowCallbacks callbacks = secondaryWindows.remove(secondaryDisplay);
        if (callbacks != null) {
            callbacks.dispose();
        }
        cursor.windowDestroyed(secondaryDisplay.windowHandle());
        GLFW.glfwDestroyWindow(secondaryDisplay.windowHandle());
    }

    private GraphicsAttachment createSecondaryGraphics(GraphicsConfig config) {
        if (config == null || config.display() == null) {
            throw new FdxException("A secondary graphics attachment requires a display");
        }
        if (!(config.display() instanceof DesktopDisplay) || config.display() == display) {
            throw new FdxException("The graphics display was not created by this desktop backend");
        }
        GraphicsAttachmentProvider provider = config.provider();
        if (!graphicsProvider.providerId().equals(provider.providerId())) {
            throw new FdxException("Secondary graphics must use the application's graphics provider");
        }
        GraphicsAttachmentRequirements requirements = provider.requirements();
        if (requirements.clientApi() != graphicsRequirements.clientApi()) {
            throw new FdxException("Secondary graphics requirements do not match the application window configuration");
        }
        DesktopDisplay secondaryDisplay = (DesktopDisplay) config.display();
        if (!secondaryWindows.containsKey(secondaryDisplay)) {
            throw new FdxException("The secondary display has already been destroyed");
        }
        GraphicsAttachment attachment = provider.create(new DesktopGraphicsEnvironment(secondaryDisplay,
                createNativeWindow(secondaryDisplay.windowHandle()), graphics));
        secondaryGraphics.put(attachment, secondaryDisplay);
        secondaryDisplay.graphics = attachment;
        return attachment;
    }

    private void destroySecondaryGraphics(GraphicsContext context) {
        if (!(context instanceof GraphicsAttachment) || context == graphics) {
            return;
        }
        GraphicsAttachment attachment = (GraphicsAttachment) context;
        DesktopDisplay owner = secondaryGraphics.remove(attachment);
        if (!attachment.isDisposed()) {
            attachment.dispose();
        }
        if (owner != null && owner.graphics == attachment) {
            owner.graphics = null;
        }
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
                cursor.activeWindow(window);
                int x = (int) Math.round(xpos);
                int y = (int) Math.round(ypos);
                input.dispatchPointerMoved(x, y, display.x() + x, display.y() + y);
            }
        };
        mouseButtonCallback = new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                cursor.activeWindow(window);
                double[] x = new double[1];
                double[] y = new double[1];
                GLFW.glfwGetCursorPos(window, x, y);
                int pointerX = (int) Math.round(x[0]);
                int pointerY = (int) Math.round(y[0]);
                int screenX = display.x() + pointerX;
                int screenY = display.y() + pointerY;
                MouseButton mapped = mapMouseButton(button);
                if (action == GLFW.GLFW_PRESS) {
                    input.dispatchPointerDown(mapped, pointerX, pointerY, screenX, screenY);
                } else if (action == GLFW.GLFW_RELEASE) {
                    input.dispatchPointerUp(mapped, pointerX, pointerY, screenX, screenY);
                }
            }
        };
        scrollCallback = new GLFWScrollCallback() {
            @Override
            public void invoke(long window, double xoffset, double yoffset) {
                cursor.activeWindow(window);
                double[] x = new double[1];
                double[] y = new double[1];
                GLFW.glfwGetCursorPos(window, x, y);
                int pointerX = (int) Math.round(x[0]);
                int pointerY = (int) Math.round(y[0]);
                input.dispatchScrolled(pointerX, pointerY, display.x() + pointerX, display.y() + pointerY,
                        (float) xoffset, (float) -yoffset);
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
            long frameStart = System.nanoTime();
            GLFW.glfwPollEvents();
            // GLFW normally reports both logical and framebuffer size changes,
            // but native maximize/restore transitions can coalesce a callback.
            // Reconcile cached dimensions every frame so graphics attachments
            // cannot remain configured for a stale surface size.
            refreshDisplayAfterResize(listener);
            if (graphics != null) {
                graphics.processEvents();
            }
            long eventsEnd = System.nanoTime();

            long now = System.nanoTime();
            deltaTime = (now - lastTime) / 1000000000.0f;
            lastTime = now;
            frameId++;

            long beginStart = System.nanoTime();
            boolean frameBegan = graphics == null || graphics.beginFrame();
            long beginEnd = System.nanoTime();
            long renderNanos = 0L;
            long frameEndNanos = 0L;
            long presentNanos = 0L;
            if (frameBegan) {
                Throwable frameFailure = null;
                try {
                    long renderStart = System.nanoTime();
                    listener.render();
                    renderNanos = System.nanoTime() - renderStart;
                    if (graphics != null) {
                        long frameEndStart = System.nanoTime();
                        listener.onFrameEnd();
                        frameEndNanos = System.nanoTime() - frameEndStart;
                    }
                }
                catch (RuntimeException | Error failure) {
                    frameFailure = failure;
                    throw failure;
                }
                finally {
                    if (graphics != null) {
                        try {
                            long presentStart = System.nanoTime();
                            graphics.endFrame();
                            presentNanos = System.nanoTime() - presentStart;
                        }
                        catch (RuntimeException | Error cleanupFailure) {
                            if (frameFailure != null) {
                                frameFailure.addSuppressed(cleanupFailure);
                            }
                            else {
                                throw cleanupFailure;
                            }
                        }
                    }
                }
            }
            // The graphics provider already performs presentation pacing when
            // VSync is enabled. Sleeping as well adds an entire second frame
            // interval and lowers a nominal 60 FPS loop to roughly 53 FPS.
            long syncStart = System.nanoTime();
            sync.sync(displayConfig.vSync() ? 0
                    : displayConfig.foregroundFps(), frameStart);
            long frameComplete = System.nanoTime();
            frameProfiler.record(display, displayConfig,
                    graphics != null ? graphics.frameMetrics()
                            : GraphicsFrameMetrics.UNAVAILABLE,
                    frameStart, frameComplete,
                    eventsEnd - frameStart, beginEnd - beginStart,
                    renderNanos, frameEndNanos, presentNanos,
                    frameComplete - syncStart);
        }
    }

    private static final class DesktopFrameProfiler {
        private static final long REPORT_INTERVAL_NANOS = 2_000_000_000L;
        private static final double NANOS_TO_MILLIS = 1.0 / 1_000_000.0;
        private static final double BYTES_TO_MIB = 1.0 / (1024.0 * 1024.0);

        private final Logger logger;
        private final boolean enabled;
        private final double[] frameSamplesMillis = new double[8192];
        private long windowStartNanos;
        private long frameCount;
        private int frameSampleCount;
        private long totalFrameNanos;
        private long totalEventsNanos;
        private long totalBeginNanos;
        private long totalRenderNanos;
        private long totalFrameEndNanos;
        private long totalPresentNanos;
        private long totalSyncNanos;
        private long totalDrawCalls;
        private long totalVertices;
        private long totalPrimitives;
        private long totalProgramBinds;
        private long totalTextureBinds;
        private long totalFramebufferBinds;
        private long totalUniformUpdates;
        private long totalBufferUploads;
        private long totalBufferUploadBytes;
        private long totalTextureUploads;
        private long totalTextureUploadBytes;
        private long gpuSampleCount;
        private double totalGpuMillis;
        private long lastGpuFrameId = -1L;
        private long pipelineSampleCount;
        private long totalVertexShaderInvocations;
        private long totalFragmentShaderInvocations;
        private long totalClippingInputPrimitives;
        private long totalClippingOutputPrimitives;
        private long lastPipelineFrameId = -1L;
        private long latestSubmittedFrameId = -1L;
        private long initialGcCount;
        private long initialGcMillis;
        private boolean announced;

        DesktopFrameProfiler(Logger logger, boolean enabled) {
            this.logger = logger;
            this.enabled = enabled;
            if (enabled) {
                initialGcCount = gcCount();
                initialGcMillis = gcMillis();
            }
        }

        void record(Display display, DisplayConfig config,
                GraphicsFrameMetrics graphicsMetrics,
                long frameStart, long frameComplete,
                long eventsNanos, long beginNanos, long renderNanos,
                long frameEndNanos, long presentNanos, long syncNanos) {
            if (!enabled) {
                return;
            }
            if (windowStartNanos == 0L) {
                windowStartNanos = frameStart;
            }
            long frameNanos = frameComplete - frameStart;
            frameCount++;
            totalFrameNanos += frameNanos;
            totalEventsNanos += eventsNanos;
            totalBeginNanos += beginNanos;
            totalRenderNanos += renderNanos;
            totalFrameEndNanos += frameEndNanos;
            totalPresentNanos += presentNanos;
            totalSyncNanos += syncNanos;
            if (frameSampleCount < frameSamplesMillis.length) {
                frameSamplesMillis[frameSampleCount++] =
                        frameNanos * NANOS_TO_MILLIS;
            }

            if (graphicsMetrics.available()) {
                latestSubmittedFrameId = graphicsMetrics.frameId();
                totalDrawCalls += graphicsMetrics.drawCalls();
                totalVertices += graphicsMetrics.submittedVertices();
                totalPrimitives += graphicsMetrics.submittedPrimitives();
                totalProgramBinds += graphicsMetrics.programBinds();
                totalTextureBinds += graphicsMetrics.textureBinds();
                totalFramebufferBinds += graphicsMetrics.framebufferBinds();
                totalUniformUpdates += graphicsMetrics.uniformUpdates();
                totalBufferUploads += graphicsMetrics.bufferUploads();
                totalBufferUploadBytes += graphicsMetrics.bufferUploadBytes();
                totalTextureUploads += graphicsMetrics.textureUploads();
                totalTextureUploadBytes += graphicsMetrics.textureUploadBytes();
                long gpuFrameId = graphicsMetrics.gpuFrameId();
                if (gpuFrameId > lastGpuFrameId
                        && Double.isFinite(graphicsMetrics.gpuTimeMillis())) {
                    lastGpuFrameId = gpuFrameId;
                    gpuSampleCount++;
                    totalGpuMillis += graphicsMetrics.gpuTimeMillis();
                }
                long pipelineFrameId = graphicsMetrics.pipelineFrameId();
                if (pipelineFrameId > lastPipelineFrameId) {
                    lastPipelineFrameId = pipelineFrameId;
                    pipelineSampleCount++;
                    totalVertexShaderInvocations +=
                            graphicsMetrics.vertexShaderInvocations();
                    totalFragmentShaderInvocations +=
                            graphicsMetrics.fragmentShaderInvocations();
                    totalClippingInputPrimitives +=
                            graphicsMetrics.clippingInputPrimitives();
                    totalClippingOutputPrimitives +=
                            graphicsMetrics.clippingOutputPrimitives();
                }
                if (!announced) {
                    logger.info(String.format(
                            "libFDX detailed frame profiler enabled: %s, VSync %s, software cap %d FPS",
                            graphicsMetrics.renderer(), config.vSync() ? "on" : "off",
                            config.foregroundFps()));
                    announced = true;
                }
            }

            long elapsedNanos = frameComplete - windowStartNanos;
            if (elapsedNanos < REPORT_INTERVAL_NANOS) {
                return;
            }
            report(display, config, elapsedNanos);
            reset(frameComplete);
        }

        private void report(Display display, DisplayConfig config,
                long elapsedNanos) {
            double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
            double frames = Math.max(1L, frameCount);
            int width = display.framebufferWidth() > 0
                    ? display.framebufferWidth() : display.width();
            int height = display.framebufferHeight() > 0
                    ? display.framebufferHeight() : display.height();
            double megapixels = (long) width * height / 1_000_000.0;
            double p95 = percentile(0.95);
            double p99 = percentile(0.99);
            logger.info(String.format(
                    "libFDX frame profile: %.1f FPS at %dx%d (%.2f MP), %.3f ms avg / %.3f p95 / %.3f p99; VSync %s, cap %d",
                    frameCount / elapsedSeconds, width, height, megapixels,
                    totalFrameNanos * NANOS_TO_MILLIS / frames,
                    p95, p99, config.vSync() ? "on" : "off",
                    config.foregroundFps()));
            logger.info(String.format(
                    "libFDX CPU/frame: events %.3f ms, begin %.3f ms, app %.3f ms, frame-end %.3f ms, present %.3f ms, limiter %.3f ms",
                    totalEventsNanos * NANOS_TO_MILLIS / frames,
                    totalBeginNanos * NANOS_TO_MILLIS / frames,
                    totalRenderNanos * NANOS_TO_MILLIS / frames,
                    totalFrameEndNanos * NANOS_TO_MILLIS / frames,
                    totalPresentNanos * NANOS_TO_MILLIS / frames,
                    totalSyncNanos * NANOS_TO_MILLIS / frames));
            if (totalDrawCalls > 0L || gpuSampleCount > 0L) {
                double appMillis = totalRenderNanos * NANOS_TO_MILLIS
                        / frames;
                double gpuMillis = gpuSampleCount > 0L
                        ? totalGpuMillis / gpuSampleCount : Double.NaN;
                long gpuLag = lastGpuFrameId >= 0L && latestSubmittedFrameId >= 0L
                        ? Math.max(0L, latestSubmittedFrameId - lastGpuFrameId) : 0L;
                logger.info(String.format(
                        "libFDX GPU/frame: %.3f ms (%d async samples, latest lag %d); %.1f draws, %.3f M vertices, %.3f M primitives, %.1f programs, %.1f textures, %.1f FBO binds",
                        gpuMillis,
                        gpuSampleCount, gpuLag,
                        totalDrawCalls / frames,
                        totalVertices / frames / 1_000_000.0,
                        totalPrimitives / frames / 1_000_000.0,
                        totalProgramBinds / frames,
                        totalTextureBinds / frames,
                        totalFramebufferBinds / frames));
                logger.info(String.format(
                        "libFDX command/frame: %.1f uniform updates, %.1f buffer uploads / %.1f KiB, %.2f texture uploads / %.1f KiB",
                        totalUniformUpdates / frames,
                        totalBufferUploads / frames,
                        totalBufferUploadBytes / frames / 1024.0,
                        totalTextureUploads / frames,
                        totalTextureUploadBytes / frames / 1024.0));
                if (Double.isFinite(gpuMillis)) {
                    logger.info(String.format(
                            "libFDX bottleneck: %s (application %.3f ms vs GPU %.3f ms)",
                            bottleneck(appMillis, gpuMillis),
                            appMillis, gpuMillis));
                }
                if (pipelineSampleCount > 0L) {
                    double samples = pipelineSampleCount;
                    double framebufferPixels = Math.max(1L,
                            (long) width * height);
                    double fragmentInvocations =
                            totalFragmentShaderInvocations / samples;
                    logger.info(String.format(
                            "libFDX pipeline/frame: %.3f M vertex shader, %.3f M fragment shader (%.2fx main framebuffer), %.3f M clipping input / %.3f M output primitives; %d async samples",
                            totalVertexShaderInvocations / samples / 1_000_000.0,
                            fragmentInvocations / 1_000_000.0,
                            fragmentInvocations / framebufferPixels,
                            totalClippingInputPrimitives / samples / 1_000_000.0,
                            totalClippingOutputPrimitives / samples / 1_000_000.0,
                            pipelineSampleCount));
                }
            }
            MemoryUsage heap = ManagementFactory.getMemoryMXBean()
                    .getHeapMemoryUsage();
            logger.info(String.format(
                    "JVM profile: heap %.1f / %.1f MiB, GC +%d collections / +%d ms",
                    heap.getUsed() * BYTES_TO_MIB,
                    heap.getCommitted() * BYTES_TO_MIB,
                    Math.max(0L, gcCount() - initialGcCount),
                    Math.max(0L, gcMillis() - initialGcMillis)));
        }

        private static String bottleneck(double appMillis,
                double gpuMillis) {
            if (appMillis > gpuMillis * 1.35) {
                return "CPU/application";
            }
            if (gpuMillis > appMillis * 1.35) {
                return "GPU";
            }
            return "balanced CPU/GPU";
        }

        private double percentile(double fraction) {
            if (frameSampleCount == 0) {
                return 0.0;
            }
            double[] sorted = Arrays.copyOf(frameSamplesMillis,
                    frameSampleCount);
            Arrays.sort(sorted);
            int index = Math.min(sorted.length - 1,
                    Math.max(0, (int) Math.ceil(fraction * sorted.length) - 1));
            return sorted[index];
        }

        private void reset(long now) {
            windowStartNanos = now;
            frameCount = 0L;
            frameSampleCount = 0;
            totalFrameNanos = 0L;
            totalEventsNanos = 0L;
            totalBeginNanos = 0L;
            totalRenderNanos = 0L;
            totalFrameEndNanos = 0L;
            totalPresentNanos = 0L;
            totalSyncNanos = 0L;
            totalDrawCalls = 0L;
            totalVertices = 0L;
            totalPrimitives = 0L;
            totalProgramBinds = 0L;
            totalTextureBinds = 0L;
            totalFramebufferBinds = 0L;
            totalUniformUpdates = 0L;
            totalBufferUploads = 0L;
            totalBufferUploadBytes = 0L;
            totalTextureUploads = 0L;
            totalTextureUploadBytes = 0L;
            gpuSampleCount = 0L;
            totalGpuMillis = 0.0;
            pipelineSampleCount = 0L;
            totalVertexShaderInvocations = 0L;
            totalFragmentShaderInvocations = 0L;
            totalClippingInputPrimitives = 0L;
            totalClippingOutputPrimitives = 0L;
            initialGcCount = gcCount();
            initialGcMillis = gcMillis();
        }

        private static long gcCount() {
            long total = 0L;
            for (GarbageCollectorMXBean collector
                    : ManagementFactory.getGarbageCollectorMXBeans()) {
                long count = collector.getCollectionCount();
                if (count > 0L) {
                    total += count;
                }
            }
            return total;
        }

        private static long gcMillis() {
            long total = 0L;
            for (GarbageCollectorMXBean collector
                    : ManagementFactory.getGarbageCollectorMXBeans()) {
                long millis = collector.getCollectionTime();
                if (millis > 0L) {
                    total += millis;
                }
            }
            return total;
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
            destroySecondaryResources();
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
            cursor = null;
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
            graphicsProvider = null;
            graphicsRequirements = null;
        }
    }

    private void destroySecondaryResources() {
        for (GraphicsAttachment attachment : new ArrayList<GraphicsAttachment>(secondaryGraphics.keySet())) {
            destroySecondaryGraphics(attachment);
        }
        for (DesktopDisplay secondaryDisplay : new ArrayList<DesktopDisplay>(secondaryWindows.keySet())) {
            destroySecondaryDisplay(secondaryDisplay);
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

    private final class DesktopDisplays implements Displays {
        @Override
        public Display main() {
            return display;
        }

        @Override
        public boolean supportsMultiple() {
            return true;
        }

        @Override
        public Display create(DisplayConfig config) {
            return createSecondaryDisplay(config);
        }

        @Override
        public void destroy(Display display) {
            destroySecondaryDisplay(display);
        }
    }

    private final class DesktopGraphics implements Graphics {
        @Override
        public GraphicsContext main() {
            return graphics;
        }

        @Override
        public boolean supportsMultiple() {
            return true;
        }

        @Override
        public GraphicsAttachment create(GraphicsConfig config) {
            return createSecondaryGraphics(config);
        }

        @Override
        public void destroy(GraphicsContext context) {
            destroySecondaryGraphics(context);
        }
    }

    private final class SecondaryWindowCallbacks {
        private final DesktopDisplay secondaryDisplay;
        private GLFWFramebufferSizeCallback framebufferSize;
        private GLFWWindowSizeCallback windowSize;
        private GLFWWindowCloseCallback close;
        private GLFWKeyCallback key;
        private GLFWCharCallback character;
        private GLFWCursorPosCallback cursorPos;
        private GLFWMouseButtonCallback mouseButton;
        private GLFWScrollCallback scroll;

        SecondaryWindowCallbacks(DesktopDisplay secondaryDisplay) {
            this.secondaryDisplay = secondaryDisplay;
        }

        void install() {
            framebufferSize = new GLFWFramebufferSizeCallback() {
                @Override
                public void invoke(long window, int width, int height) {
                    refreshAfterResize();
                }
            };
            windowSize = new GLFWWindowSizeCallback() {
                @Override
                public void invoke(long window, int width, int height) {
                    refreshAfterResize();
                }
            };
            close = new GLFWWindowCloseCallback() {
                @Override
                public void invoke(long window) {
                }
            };
            key = new GLFWKeyCallback() {
                @Override
                public void invoke(long window, int keyCode, int scancode, int action, int mods) {
                    Key mapped = mapKey(keyCode);
                    if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
                        input.dispatchKeyDown(mapped);
                    } else if (action == GLFW.GLFW_RELEASE) {
                        input.dispatchKeyUp(mapped);
                    }
                }
            };
            character = new GLFWCharCallback() {
                @Override
                public void invoke(long window, int codepoint) {
                    input.dispatchTextInput(new String(Character.toChars(codepoint)));
                }
            };
            cursorPos = new GLFWCursorPosCallback() {
                @Override
                public void invoke(long window, double xpos, double ypos) {
                    cursor.activeWindow(window);
                    int x = (int) Math.round(xpos);
                    int y = (int) Math.round(ypos);
                    input.dispatchPointerMoved(x, y, secondaryDisplay.x() + x, secondaryDisplay.y() + y);
                }
            };
            mouseButton = new GLFWMouseButtonCallback() {
                @Override
                public void invoke(long window, int button, int action, int mods) {
                    cursor.activeWindow(window);
                    double[] x = new double[1];
                    double[] y = new double[1];
                    GLFW.glfwGetCursorPos(window, x, y);
                    int pointerX = (int) Math.round(x[0]);
                    int pointerY = (int) Math.round(y[0]);
                    int screenX = secondaryDisplay.x() + pointerX;
                    int screenY = secondaryDisplay.y() + pointerY;
                    MouseButton mapped = mapMouseButton(button);
                    if (action == GLFW.GLFW_PRESS) {
                        input.dispatchPointerDown(mapped, pointerX, pointerY, screenX, screenY);
                    } else if (action == GLFW.GLFW_RELEASE) {
                        input.dispatchPointerUp(mapped, pointerX, pointerY, screenX, screenY);
                    }
                }
            };
            scroll = new GLFWScrollCallback() {
                @Override
                public void invoke(long window, double xoffset, double yoffset) {
                    cursor.activeWindow(window);
                    double[] x = new double[1];
                    double[] y = new double[1];
                    GLFW.glfwGetCursorPos(window, x, y);
                    int pointerX = (int) Math.round(x[0]);
                    int pointerY = (int) Math.round(y[0]);
                    input.dispatchScrolled(pointerX, pointerY, secondaryDisplay.x() + pointerX,
                            secondaryDisplay.y() + pointerY, (float) xoffset, (float) -yoffset);
                }
            };

            long handle = secondaryDisplay.windowHandle();
            GLFW.glfwSetFramebufferSizeCallback(handle, framebufferSize);
            GLFW.glfwSetWindowSizeCallback(handle, windowSize);
            GLFW.glfwSetWindowCloseCallback(handle, close);
            GLFW.glfwSetKeyCallback(handle, key);
            GLFW.glfwSetCharCallback(handle, character);
            GLFW.glfwSetCursorPosCallback(handle, cursorPos);
            GLFW.glfwSetMouseButtonCallback(handle, mouseButton);
            GLFW.glfwSetScrollCallback(handle, scroll);
        }

        private void refreshAfterResize() {
            int oldFramebufferWidth = secondaryDisplay.framebufferWidth();
            int oldFramebufferHeight = secondaryDisplay.framebufferHeight();
            secondaryDisplay.refreshSizes();
            if (secondaryDisplay.graphics != null
                    && (oldFramebufferWidth != secondaryDisplay.framebufferWidth()
                    || oldFramebufferHeight != secondaryDisplay.framebufferHeight())) {
                secondaryDisplay.graphics.resize(secondaryDisplay.framebufferWidth(),
                        secondaryDisplay.framebufferHeight());
            }
        }

        void dispose() {
            long handle = secondaryDisplay.windowHandle();
            GLFW.glfwSetFramebufferSizeCallback(handle, null);
            GLFW.glfwSetWindowSizeCallback(handle, null);
            GLFW.glfwSetWindowCloseCallback(handle, null);
            GLFW.glfwSetKeyCallback(handle, null);
            GLFW.glfwSetCharCallback(handle, null);
            GLFW.glfwSetCursorPosCallback(handle, null);
            GLFW.glfwSetMouseButtonCallback(handle, null);
            GLFW.glfwSetScrollCallback(handle, null);
            if (framebufferSize != null) framebufferSize.free();
            if (windowSize != null) windowSize.free();
            if (close != null) close.free();
            if (key != null) key.free();
            if (character != null) character.free();
            if (cursorPos != null) cursorPos.free();
            if (mouseButton != null) mouseButton.free();
            if (scroll != null) scroll.free();
        }
    }

    /**
     * Represents a frame sync.
     *
     * @author xpenatan
     */
    private static final class FrameSync {
        void sync(int fps, long frameStart) {
            if (fps <= 0) {
                return;
            }
            long targetFrameNanos = 1_000_000_000L / fps;
            long remainingNanos = targetFrameNanos
                    - (System.nanoTime() - frameStart);
            if (remainingNanos <= 0L) {
                return;
            }
            try {
                long sleepMillis = remainingNanos / 1_000_000L;
                int sleepNanos = (int)(remainingNanos % 1_000_000L);
                Thread.sleep(sleepMillis, sleepNanos);
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
        private final GraphicsContext sharedContext;

        DesktopGraphicsEnvironment(Display display, NativeWindow nativeWindow) {
            this(display, nativeWindow, null);
        }

        DesktopGraphicsEnvironment(Display display, NativeWindow nativeWindow, GraphicsContext sharedContext) {
            this.display = display;
            this.nativeWindow = nativeWindow;
            this.sharedContext = sharedContext;
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

        @Override
        public GraphicsContext sharedContext() {
            return sharedContext;
        }
    }

    /**
     * Represents a desktop display.
     *
     * @author xpenatan
     */
    private static final class DesktopDisplay implements Display {
        private final long windowHandle;
        private GraphicsAttachment graphics;
        private String title;
        private int x;
        private int y;
        private int width;
        private int height;
        private int framebufferWidth;
        private int framebufferHeight;
        private int monitorX;
        private int monitorY;
        private int monitorWidth;
        private int monitorHeight;
        private int workAreaX;
        private int workAreaY;
        private int workAreaWidth;
        private int workAreaHeight;
        private final IntBuffer widthBuffer = BufferUtils.createIntBuffer(1);
        private final IntBuffer heightBuffer = BufferUtils.createIntBuffer(1);
        private final IntBuffer xBuffer = BufferUtils.createIntBuffer(1);
        private final IntBuffer yBuffer = BufferUtils.createIntBuffer(1);
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
            refreshPosition();
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

        private void refreshPosition() {
            xBuffer.clear();
            yBuffer.clear();
            GLFW.glfwGetWindowPos(windowHandle, xBuffer, yBuffer);
            x = xBuffer.get(0);
            y = yBuffer.get(0);
        }

        private void refreshMonitor() {
            long monitor = GLFW.glfwGetPrimaryMonitor();
            if (monitor == 0L) {
                monitorX = x();
                monitorY = y();
                monitorWidth = width();
                monitorHeight = height();
                workAreaX = monitorX;
                workAreaY = monitorY;
                workAreaWidth = monitorWidth;
                workAreaHeight = monitorHeight;
                return;
            }
            xBuffer.clear();
            yBuffer.clear();
            GLFW.glfwGetMonitorPos(monitor, xBuffer, yBuffer);
            monitorX = xBuffer.get(0);
            monitorY = yBuffer.get(0);
            org.lwjgl.glfw.GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
            monitorWidth = mode != null ? mode.width() : width();
            monitorHeight = mode != null ? mode.height() : height();
            xBuffer.clear();
            yBuffer.clear();
            widthBuffer.clear();
            heightBuffer.clear();
            GLFW.glfwGetMonitorWorkarea(monitor, xBuffer, yBuffer, widthBuffer, heightBuffer);
            workAreaX = xBuffer.get(0);
            workAreaY = yBuffer.get(0);
            workAreaWidth = widthBuffer.get(0);
            workAreaHeight = heightBuffer.get(0);
        }

        @Override
        public int x() {
            refreshPosition();
            return x;
        }

        @Override
        public int y() {
            refreshPosition();
            return y;
        }

        @Override
        public void position(int x, int y) {
            GLFW.glfwSetWindowPos(windowHandle, x, y);
            this.x = x;
            this.y = y;
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

        @Override
        public void size(int width, int height) {
            GLFW.glfwSetWindowSize(windowHandle, Math.max(1, width), Math.max(1, height));
            refreshSizes();
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

        @Override
        public int monitorX() {
            refreshMonitor();
            return monitorX;
        }

        @Override
        public int monitorY() {
            refreshMonitor();
            return monitorY;
        }

        @Override
        public int monitorWidth() {
            refreshMonitor();
            return monitorWidth;
        }

        @Override
        public int monitorHeight() {
            refreshMonitor();
            return monitorHeight;
        }

        @Override
        public int workAreaX() {
            refreshMonitor();
            return workAreaX;
        }

        @Override
        public int workAreaY() {
            refreshMonitor();
            return workAreaY;
        }

        @Override
        public int workAreaWidth() {
            refreshMonitor();
            return workAreaWidth;
        }

        @Override
        public int workAreaHeight() {
            refreshMonitor();
            return workAreaHeight;
        }

        @Override
        public void show() {
            GLFW.glfwShowWindow(windowHandle);
        }

        @Override
        public void focus() {
            GLFW.glfwFocusWindow(windowHandle);
        }

        @Override
        public boolean focused() {
            return GLFW.glfwGetWindowAttrib(windowHandle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        }

        @Override
        public boolean minimized() {
            return GLFW.glfwGetWindowAttrib(windowHandle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
        }

        @Override
        public void opacity(float opacity) {
            GLFW.glfwSetWindowOpacity(windowHandle, Math.max(0.0f, Math.min(1.0f, opacity)));
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

    /** GLFW-backed cursor state for the main and ImGui viewport windows. */
    private static final class DesktopCursor implements Cursor {
        private final long mainWindow;
        private long activeWindow;
        private long capturedWindow;
        private boolean visible = true;
        private boolean captured;
        private CursorShape shape = CursorShape.DEFAULT;

        DesktopCursor(long mainWindow) {
            this.mainWindow = mainWindow;
            activeWindow = mainWindow;
        }

        void activeWindow(long window) {
            if (window != 0L) {
                activeWindow = window;
            }
        }

        void windowDestroyed(long window) {
            if (activeWindow == window) {
                activeWindow = mainWindow;
            }
            if (capturedWindow == window) {
                capturedWindow = 0L;
                captured = false;
            }
        }

        @Override
        public boolean isVisible() {
            return visible;
        }

        @Override
        public void visible(boolean visible) {
            if (this.visible == visible) {
                return;
            }
            this.visible = visible;
            if(!captured) {
                GLFW.glfwSetInputMode(targetWindow(), GLFW.GLFW_CURSOR,
                        visible ? GLFW.GLFW_CURSOR_NORMAL
                                : GLFW.GLFW_CURSOR_HIDDEN);
            }
        }

        @Override
        public boolean isCaptured() {
            return captured;
        }

        @Override
        public void captured(boolean captured) {
            if (this.captured == captured) {
                return;
            }
            this.captured = captured;
            if(captured) {
                capturedWindow = targetWindow();
                GLFW.glfwSetInputMode(capturedWindow, GLFW.GLFW_CURSOR,
                        GLFW.GLFW_CURSOR_DISABLED);
            }
            else {
                long window = capturedWindow != 0L
                        ? capturedWindow : targetWindow();
                GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR,
                        visible ? GLFW.GLFW_CURSOR_NORMAL
                                : GLFW.GLFW_CURSOR_HIDDEN);
                capturedWindow = 0L;
            }
        }

        @Override
        public CursorShape shape() {
            return shape;
        }

        @Override
        public void shape(CursorShape shape) {
            this.shape = shape != null ? shape : CursorShape.DEFAULT;
        }

        private long targetWindow() {
            return activeWindow != 0L ? activeWindow : mainWindow;
        }
    }
}
