package io.github.libfdx.backend.web;

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
import io.github.libfdx.graphics.DefaultGraphics;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentReadiness;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.GraphicsProviderSupport;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.input.DefaultGamepads;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.DefaultInputCapabilities;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.runtime.core.RuntimeCore;
import io.github.libfdx.storage.DefaultStorage;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiToolkit;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.AnimationFrameCallback;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.KeyboardEvent;
import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.events.Registration;
import org.teavm.jso.dom.events.Touch;
import org.teavm.jso.dom.events.TouchEvent;
import org.teavm.jso.dom.events.WheelEvent;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.runtime.Fiber;

/**
 * Implements the backend integration for web application.
 *
 * @author xpenatan
 */
public final class WebApplicationBackend implements ApplicationBackend, Application, AnimationFrameCallback {
    public static final ProviderId ID = ProviderId.of("web");
    private static final List<WebApplicationBackend> ACTIVE_BACKENDS = new ArrayList<WebApplicationBackend>();
    private static final long MINIMUM_PRELOAD_DISPLAY_MILLIS = 2000L;

    private final SystemLogger logger = new SystemLogger();
    private final Fiber.FiberRunner frameRunner = new Fiber.FiberRunner() {
        @Override
        public void run() {
            runAnimationFrame(frameTimestamp);
        }
    };
    private WebApplicationConfig config;
    private ApplicationListener listener;
    private Fdx fdx;
    private WebDisplay display;
    private HTMLCanvasElement canvas;
    private GraphicsAttachment graphics;
    private DefaultInput input;
    private WebCursor cursor;
    private double mouseX;
    private double mouseY;
    private WebTextInputController textInputController;
    private WebPreloadApplicationListener preloadApplicationListener;
    private WebPreloadContext preloadContext;
    private final List<Registration> inputRegistrations = new ArrayList<Registration>();
    private ApplicationLifecycle lifecycle = ApplicationLifecycle.DISPOSED;
    private boolean running;
    private boolean disposed = true;
    private boolean listenerCreated;
    private boolean preloadListenerCreated;
    private boolean activeRetained;
    private long lastFrameMillis;
    private long preloadStartedMillis;
    private int preloadRenderedFrames;
    private float deltaTime;
    private long frameId;
    private double frameTimestamp;

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
        retainActiveBackend();
        disposed = false;
        try {
            WebApplicationConfig actualConfig = toWebConfig(config);
            GraphicsAttachmentProvider graphicsProvider = actualConfig.graphics();
            if (graphicsProvider == null) {
                throw new FdxException("No web graphics provider configured");
            }
            if (actualConfig.graphicsProvider() != null
                    && !actualConfig.graphicsProvider().equals(graphicsProvider.providerId())) {
                throw new FdxException("Configured graphics provider ID does not match attached GraphicsAttachmentProvider");
            }
            if (graphicsProvider instanceof GraphicsProviderSupport
                    && !((GraphicsProviderSupport) graphicsProvider).isSupported()) {
                String reason = ((GraphicsProviderSupport) graphicsProvider).supportFailureReason();
                throw new FdxException(reason != null ? reason : "Web graphics provider is not supported");
            }
            logger.info("Web graphics provider selected: " + graphicsProvider.providerId());

            this.config = actualConfig;
            this.listener = listener;
            DisplayConfig displayConfig = actualConfig.displayConfig();
            setDocumentTitle(displayConfig.title());
            canvas = getOrCreateCanvas(actualConfig.canvasId(), displayConfig.width(), displayConfig.height());
            setCanvasInteractive(canvas);
            display = new WebDisplay(displayConfig.title());
            refreshDisplaySize();
            input = createInput();
            installInputListeners();

            graphics = graphicsProvider.create(new WebGraphicsEnvironment(display,
                    NativeWindow.web(canvas, "#" + actualConfig.canvasId())));
            fdx = new DefaultFdx(this, new DefaultDisplays(display), new DefaultGraphics(graphics), input,
                    new WebFileSystem(), new DefaultStorage(new WebStorageBackend()), logger);
            RuntimeCore.registerProvider(new WebRuntimeCoreProvider());
            preloadApplicationListener = actualConfig.preloadApplicationListener() != null
                    ? actualConfig.preloadApplicationListener()
                    : new WebDefaultPreloadApplicationListener();
            WebAssetPreloader.installAndBeginPreload();

            running = true;
            lifecycle = ApplicationLifecycle.CREATED;
            lastFrameMillis = System.currentTimeMillis();

            if (isGraphicsReady()) {
                createPreloadListener();
            }
            Window.requestAnimationFrame(this);
        } catch (RuntimeException | Error error) {
            try {
                dispose();
            } catch (Throwable disposeError) {
                logger.error("Web application dispose after start failure failed", disposeError);
            }
            throw error;
        }
    }

    /**
     * Runs the start step.
     *
     * @param config the configuration
     * @param listener the listener
     */
    public void start(WebApplicationConfig config, ApplicationListener listener) {
        start((ApplicationConfig) config, listener);
    }

    private WebApplicationConfig toWebConfig(ApplicationConfig config) {
        if (config == null) {
            return new WebApplicationConfig();
        }
        if (config instanceof WebApplicationConfig) {
            return (WebApplicationConfig) config;
        }
        throw new FdxException("WebApplicationBackend requires WebApplicationConfig");
    }

    private DefaultInput createInput() {
        textInputController = new WebTextInputController();
        textInputController.canvas(canvas);
        cursor = new WebCursor(canvas);
        DefaultInput createdInput = new DefaultInput(ProviderId.of("web_input"),
                new DefaultInputCapabilities(true, true, true, true, false, false), cursor,
                new DefaultGamepads(), textInputController, new WebClipboard());
        textInputController.input(createdInput);
        return createdInput;
    }

    private void installInputListeners() {
        if (canvas == null || input == null) {
            return;
        }
        final Window window = Window.current();
        final HTMLDocument document = HTMLDocument.current();
        inputRegistrations.add(document.onEvent("pointerlockchange", new EventListener<Event>() {
            @Override
            public void handleEvent(Event event) {
                cursor.pointerLockChanged();
            }
        }));
        inputRegistrations.add(document.onEvent("pointerlockerror", new EventListener<Event>() {
            @Override
            public void handleEvent(Event event) {
                cursor.pointerLockFailed();
            }
        }));
        inputRegistrations.add(canvas.onMouseDown(new EventListener<MouseEvent>() {
            @Override
            public void handleEvent(MouseEvent event) {
                cursor.beginUserGesture();
                focusCanvas(canvas);
                event.preventDefault();
                updateMousePosition(event);
                input.dispatchPointerDown(mapMouseButton(event.getButton()), roundedMouseX(), roundedMouseY());
            }
        }));
        inputRegistrations.add(window.onMouseMove(new EventListener<MouseEvent>() {
            @Override
            public void handleEvent(MouseEvent event) {
                if (textInputController != null && textInputController.handlesEvent(event)) {
                    return;
                }
                updateMousePosition(event);
                input.dispatchPointerMoved(roundedMouseX(), roundedMouseY());
            }
        }));
        inputRegistrations.add(window.onMouseUp(new EventListener<MouseEvent>() {
            @Override
            public void handleEvent(MouseEvent event) {
                if (textInputController != null && textInputController.handlesEvent(event)) {
                    return;
                }
                updateMousePosition(event);
                input.dispatchPointerUp(mapMouseButton(event.getButton()), roundedMouseX(), roundedMouseY());
            }
        }));
        inputRegistrations.add(canvas.onWheel(new EventListener<WheelEvent>() {
            @Override
            public void handleEvent(WheelEvent event) {
                event.preventDefault();
                int x = cursor.pointerLocked() ? roundedMouseX() : eventX(canvas, event.getClientX());
                int y = cursor.pointerLocked() ? roundedMouseY() : eventY(canvas, event.getClientY());
                input.dispatchScrolled(x, y,
                        wheelDelta(event.getDeltaX(), event.getDeltaMode(), display != null ? display.height() : 0),
                        wheelDelta(event.getDeltaY(), event.getDeltaMode(), display != null ? display.height() : 0));
            }
        }));
        inputRegistrations.add(canvas.onTouchStart(new EventListener<TouchEvent>() {
            @Override
            public void handleEvent(TouchEvent event) {
                focusCanvas(canvas);
                event.preventDefault();
                dispatchTouches(event, TouchDispatch.DOWN);
            }
        }));
        inputRegistrations.add(window.onTouchMove(new EventListener<TouchEvent>() {
            @Override
            public void handleEvent(TouchEvent event) {
                if (textInputController != null && textInputController.handlesEvent(event)) {
                    return;
                }
                event.preventDefault();
                dispatchTouches(event, TouchDispatch.MOVE);
            }
        }));
        inputRegistrations.add(window.onTouchEnd(new EventListener<TouchEvent>() {
            @Override
            public void handleEvent(TouchEvent event) {
                if (textInputController != null && textInputController.handlesEvent(event)) {
                    return;
                }
                event.preventDefault();
                dispatchTouches(event, TouchDispatch.UP);
            }
        }));
        inputRegistrations.add(window.onTouchCancel(new EventListener<TouchEvent>() {
            @Override
            public void handleEvent(TouchEvent event) {
                if (textInputController != null && textInputController.handlesEvent(event)) {
                    return;
                }
                event.preventDefault();
                dispatchTouches(event, TouchDispatch.UP);
            }
        }));
        inputRegistrations.add(window.onKeyDown(new EventListener<KeyboardEvent>() {
            @Override
            public void handleEvent(KeyboardEvent event) {
                if (textInputController != null && textInputController.handlesEvent(event)) {
                    return;
                }
                Key key = mapKey(event);
                if (key == Key.UNKNOWN) {
                    return;
                }
                boolean handled = input.dispatchKeyDown(key);
                if (handled || preventsBrowserDefault(key)) {
                    event.preventDefault();
                }
            }
        }));
        inputRegistrations.add(window.onKeyPress(new EventListener<KeyboardEvent>() {
            @Override
            public void handleEvent(KeyboardEvent event) {
                if (textInputController != null && textInputController.handlesEvent(event)) {
                    return;
                }
                String text = printableText(event);
                if (text != null && text.length() > 0) {
                    boolean handled = input.dispatchTextInput(text);
                    if (handled) {
                        event.preventDefault();
                    }
                }
            }
        }));
        inputRegistrations.add(window.onKeyUp(new EventListener<KeyboardEvent>() {
            @Override
            public void handleEvent(KeyboardEvent event) {
                if (textInputController != null && textInputController.handlesEvent(event)) {
                    return;
                }
                Key key = mapKey(event);
                if (key != Key.UNKNOWN && input.dispatchKeyUp(key)) {
                    event.preventDefault();
                }
            }
        }));
    }

    private void updateMousePosition(MouseEvent event) {
        if (cursor != null && cursor.pointerLocked()) {
            mouseX += event.getMovementX();
            mouseY += event.getMovementY();
        } else {
            mouseX = eventX(canvas, event.getClientX());
            mouseY = eventY(canvas, event.getClientY());
        }
    }

    private int roundedMouseX() {
        return saturatedRound(mouseX);
    }

    private int roundedMouseY() {
        return saturatedRound(mouseY);
    }

    private static int saturatedRound(double value) {
        if (value <= Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.round(value);
    }

    private void dispatchTouches(TouchEvent event, TouchDispatch dispatch) {
        if (input == null || event == null || event.getChangedTouches() == null) {
            return;
        }
        for (int i = 0; i < event.getChangedTouches().getLength(); i++) {
            Touch touch = event.getChangedTouches().get(i);
            int x = eventX(canvas, touch.getClientX());
            int y = eventY(canvas, touch.getClientY());
            float pressure = touch.getForce() > 0.0 ? (float) touch.getForce() : 1.0f;
            if (dispatch == TouchDispatch.DOWN) {
                input.dispatchTouchDown(touch.getIdentifier(), x, y, pressure);
            } else if (dispatch == TouchDispatch.MOVE) {
                input.dispatchTouchMoved(touch.getIdentifier(), x, y, pressure);
            } else {
                input.dispatchTouchUp(touch.getIdentifier(), x, y, pressure);
            }
        }
    }

    /**
     * Handles the animation frame event.
     *
     * @param timestamp the timestamp
     */
    @Override
    public void onAnimationFrame(double timestamp) {
        frameTimestamp = timestamp;
        Fiber.start(frameRunner, true);
    }

    private void runAnimationFrame(double timestamp) {
        if (!running || disposed || listener == null) {
            return;
        }
        try {
            if (!listenerCreated) {
                if (graphics != null) {
                    graphics.processEvents();
                }
                if (isGraphicsReady()) {
                    if (WebAssetPreloader.isComplete()) {
                        if (WebAssetPreloader.isFailed()) {
                            throw new FdxException("Web asset preload failed: " + WebAssetPreloader.errorMessage());
                        }
                        createPreloadListener();
                        if (shouldKeepPreloadVisible()) {
                            stepPreload();
                        } else {
                            createListener();
                        }
                    } else {
                        createPreloadListener();
                        stepPreload();
                    }
                }
            }
            if (listenerCreated) {
                step();
            }
        } catch (Throwable error) {
            logger.error("Web application frame failed", error);
            try {
                dispose();
            } catch (Throwable disposeError) {
                logger.error("Web application dispose after frame failure failed", disposeError);
            }
            throw error instanceof RuntimeException ? (RuntimeException) error : new FdxException("Web frame failed", error);
        }
        if (!running && !disposed) {
            dispose();
            return;
        }
        if (running && !disposed) {
            Window.requestAnimationFrame(this);
        }
    }

    private boolean isGraphicsReady() {
        return graphics == null || !(graphics instanceof GraphicsAttachmentReadiness)
                || ((GraphicsAttachmentReadiness) graphics).isReady();
    }

    private void step() {
        boolean resized = refreshDisplaySize();
        if (resized && graphics != null) {
            graphics.resize(display.framebufferWidth(), display.framebufferHeight());
            if (listenerCreated) {
                listener.resize(display.width(), display.height());
            }
        }
        if (graphics != null) {
            graphics.processEvents();
        }

        updateFrameTime();

        if (graphics == null || graphics.beginFrame()) {
            try {
                listener.render();
                listener.onFrameEnd();
            } finally {
                if (graphics != null) {
                    graphics.endFrame();
                }
            }
        }
    }

    private void stepPreload() {
        if (!preloadListenerCreated || preloadApplicationListener == null || preloadContext == null) {
            return;
        }
        boolean resized = refreshDisplaySize();
        if (resized && graphics != null) {
            graphics.resize(display.framebufferWidth(), display.framebufferHeight());
            preloadContext.resize(display.width(), display.height());
            preloadApplicationListener.resize(preloadContext, display.width(), display.height());
        }

        updateFrameTime();

        if (graphics == null || graphics.beginFrame()) {
            try {
                preloadContext.update(deltaTime);
                preloadApplicationListener.render(preloadContext);
                preloadRenderedFrames++;
            } finally {
                if (graphics != null) {
                    graphics.endFrame();
                }
            }
        }
    }

    private boolean shouldKeepPreloadVisible() {
        if (!preloadListenerCreated || preloadApplicationListener == WebNoopPreloadApplicationListener.INSTANCE) {
            return false;
        }
        if (preloadRenderedFrames == 0) {
            return true;
        }
        return System.currentTimeMillis() - preloadStartedMillis < MINIMUM_PRELOAD_DISPLAY_MILLIS;
    }

    private void updateFrameTime() {
        long now = System.currentTimeMillis();
        deltaTime = lastFrameMillis > 0L ? (now - lastFrameMillis) / 1000.0f : 0.0f;
        lastFrameMillis = now;
        frameId++;
    }

    private boolean refreshDisplaySize() {
        int cssWidth = Math.max(1, clientWidth(canvas));
        int cssHeight = Math.max(1, clientHeight(canvas));
        float scale = Math.max(1.0f, devicePixelRatio());
        int framebufferWidth = Math.max(1, Math.round(cssWidth * scale));
        int framebufferHeight = Math.max(1, Math.round(cssHeight * scale));
        if (canvasWidth(canvas) != framebufferWidth || canvasHeight(canvas) != framebufferHeight) {
            setCanvasFramebufferSize(canvas, framebufferWidth, framebufferHeight);
        }
        return display.size(cssWidth, cssHeight, framebufferWidth, framebufferHeight, scale, scale);
    }

    /**
     * Returns the lifecycle.
     *
     * @return the lifecycle
     */
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

    private void createListener() {
        if (listenerCreated || listener == null) {
            return;
        }
        disposePreloadListener();
        listener.create(fdx);
        listenerCreated = true;
        listener.resize(display.width(), display.height());
        lifecycle = ApplicationLifecycle.RUNNING;
        lastFrameMillis = System.currentTimeMillis();
    }

    private void createPreloadListener() {
        if (preloadListenerCreated || preloadApplicationListener == null || fdx == null || display == null
                || graphics == null) {
            return;
        }
        UiRoot root = new UiToolkit(fdx.files()).root(display, graphics).input(input);
        preloadContext = new WebPreloadContext(fdx, display, graphics, root);
        preloadContext.update(0.0f);
        preloadApplicationListener.create(preloadContext);
        preloadContext.resize(display.width(), display.height());
        preloadApplicationListener.resize(preloadContext, display.width(), display.height());
        preloadListenerCreated = true;
        preloadStartedMillis = System.currentTimeMillis();
        preloadRenderedFrames = 0;
    }

    private void disposePreloadListener() {
        if (!preloadListenerCreated && preloadContext == null) {
            preloadApplicationListener = null;
            return;
        }
        WebPreloadContext context = preloadContext;
        WebPreloadApplicationListener listener = preloadApplicationListener;
        try {
            if (listener != null && context != null) {
                listener.dispose(context);
            }
        } finally {
            if (context != null) {
                context.dispose();
            }
            preloadListenerCreated = false;
            preloadContext = null;
            preloadApplicationListener = null;
            preloadStartedMillis = 0L;
            preloadRenderedFrames = 0;
        }
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
                listener.dispose();
            }
        } finally {
            listenerCreated = false;
            disposePreloadListener();
            if (graphics != null) {
                graphics.dispose();
                graphics = null;
            }
            if (cursor != null) {
                cursor.dispose();
            }
            for (int i = 0; i < inputRegistrations.size(); i++) {
                inputRegistrations.get(i).dispose();
            }
            inputRegistrations.clear();
            if (textInputController != null) {
                textInputController.dispose();
                textInputController.input(null);
                textInputController = null;
            }
            cursor = null;
            input = null;
            fdx = null;
            listener = null;
            display = null;
            canvas = null;
            RuntimeCore.registerProvider(null);
            releaseActiveBackend();
            disposed = true;
        }
    }

    private void retainActiveBackend() {
        if (!activeRetained) {
            ACTIVE_BACKENDS.add(this);
            activeRetained = true;
        }
    }

    private void releaseActiveBackend() {
        if (activeRetained) {
            ACTIVE_BACKENDS.remove(this);
            activeRetained = false;
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

    @JSBody(params = { "id", "width", "height" }, script =
            "var canvas = document.getElementById(id);\n" +
            "if (!canvas) {\n" +
            "  canvas = document.createElement('canvas');\n" +
            "  canvas.id = id;\n" +
            "  document.body.appendChild(canvas);\n" +
            "}\n" +
            "var fillWindow = width <= 0 || height <= 0 || canvas.getAttribute('data-libfdx-fill-window') === 'true';\n" +
            "var cssWidth = fillWindow ? Math.max(1, Math.round(window.innerWidth || document.documentElement.clientWidth || 1)) : Math.max(1, width);\n" +
            "var cssHeight = fillWindow ? Math.max(1, Math.round(window.innerHeight || document.documentElement.clientHeight || 1)) : Math.max(1, height);\n" +
            "canvas.width = cssWidth;\n" +
            "canvas.height = cssHeight;\n" +
            "if (fillWindow) {\n" +
            "  canvas.style.width = '100vw';\n" +
            "  canvas.style.height = '100vh';\n" +
            "} else {\n" +
            "  canvas.style.width = cssWidth + 'px';\n" +
            "  canvas.style.height = cssHeight + 'px';\n" +
            "}\n" +
            "canvas.style.display = 'block';\n" +
            "return canvas;")
    private static native HTMLCanvasElement getOrCreateCanvas(String id, int width, int height);

    @JSBody(params = { "title" }, script = "document.title = title || '';")
    private static native void setDocumentTitle(String title);

    @JSBody(params = { "canvas" }, script = "return canvas.clientWidth || canvas.width || 1;")
    private static native int clientWidth(HTMLCanvasElement canvas);

    @JSBody(params = { "canvas" }, script = "return canvas.clientHeight || canvas.height || 1;")
    private static native int clientHeight(HTMLCanvasElement canvas);

    @JSBody(params = { "canvas" }, script = "return canvas.width || 1;")
    private static native int canvasWidth(HTMLCanvasElement canvas);

    @JSBody(params = { "canvas" }, script = "return canvas.height || 1;")
    private static native int canvasHeight(HTMLCanvasElement canvas);

    @JSBody(params = { "canvas", "width", "height" }, script =
            "canvas.width = width;\n" +
            "canvas.height = height;")
    private static native void setCanvasFramebufferSize(HTMLCanvasElement canvas, int width, int height);

    @JSBody(script = "return window.devicePixelRatio || 1;")
    private static native float devicePixelRatio();

    @JSBody(params = { "canvas" }, script =
            "canvas.tabIndex = canvas.tabIndex >= 0 ? canvas.tabIndex : 0;\n" +
            "canvas.style.outline = 'none';\n" +
            "canvas.style.touchAction = 'none';\n" +
            "canvas.style.userSelect = 'none';\n" +
            "canvas.style.webkitUserSelect = 'none';\n" +
            "canvas.oncontextmenu = function(event) { event.preventDefault(); return false; };")
    private static native void setCanvasInteractive(HTMLCanvasElement canvas);

    @JSBody(params = { "canvas" }, script = "canvas.focus();")
    private static native void focusCanvas(HTMLCanvasElement canvas);

    @JSBody(params = { "canvas", "clientX" }, script =
            "var rect = canvas.getBoundingClientRect();\n" +
            "return Math.max(0, Math.min(canvas.clientWidth || canvas.width || 1, Math.round(clientX - rect.left)));")
    private static native int eventX(HTMLCanvasElement canvas, double clientX);

    @JSBody(params = { "canvas", "clientY" }, script =
            "var rect = canvas.getBoundingClientRect();\n" +
            "return Math.max(0, Math.min(canvas.clientHeight || canvas.height || 1, Math.round(clientY - rect.top)));")
    private static native int eventY(HTMLCanvasElement canvas, double clientY);

    private static MouseButton mapMouseButton(short button) {
        switch (button) {
            case 0: return MouseButton.LEFT;
            case 1: return MouseButton.MIDDLE;
            case 2: return MouseButton.RIGHT;
            case 3: return MouseButton.BACK;
            case 4: return MouseButton.FORWARD;
            default: return MouseButton.UNKNOWN;
        }
    }

    private static float wheelDelta(double delta, int mode, int displayHeight) {
        double scale = 1.0;
        if (mode == WheelEvent.DOM_DELTA_LINE) {
            scale = 16.0;
        } else if (mode == WheelEvent.DOM_DELTA_PAGE) {
            scale = Math.max(1, displayHeight);
        }
        return (float) (delta * scale);
    }

    private static boolean preventsBrowserDefault(Key key) {
        return key == Key.TAB || key == Key.SPACE || key == Key.LEFT || key == Key.RIGHT || key == Key.UP
                || key == Key.DOWN || key == Key.PAGE_UP || key == Key.PAGE_DOWN || key == Key.HOME
                || key == Key.END || key == Key.BACKSPACE || key == Key.DELETE;
    }

    private static String printableText(KeyboardEvent event) {
        if (event.isCtrlKey() || event.isAltKey() || event.isMetaKey()) {
            return "";
        }
        String key = event.getKey();
        if (key != null && key.length() == 1) {
            return key;
        }
        int charCode = event.getCharCode();
        if (isPrintableCodePoint(charCode)) {
            return new String(Character.toChars(charCode));
        }
        return "";
    }

    private static boolean isPrintableCodePoint(int codePoint) {
        return codePoint >= 32 && codePoint != 127 && Character.isValidCodePoint(codePoint);
    }

    private static Key mapKey(KeyboardEvent event) {
        String code = event.getCode();
        if (code != null && code.length() > 0) {
            Key byCode = mapCode(code);
            if (byCode != Key.UNKNOWN) {
                return byCode;
            }
        }
        String key = event.getKey();
        if (key == null || key.length() == 0) {
            return Key.UNKNOWN;
        }
        if (key.length() == 1) {
            char c = Character.toUpperCase(key.charAt(0));
            if (c >= 'A' && c <= 'Z') {
                return Key.valueOf(String.valueOf(c));
            }
            if (c >= '0' && c <= '9') {
                return digitKey(c - '0');
            }
            if (c == ' ') {
                return Key.SPACE;
            }
        }
        if ("Backspace".equals(key)) return Key.BACKSPACE;
        if ("Tab".equals(key)) return Key.TAB;
        if ("Enter".equals(key)) return Key.ENTER;
        if ("Escape".equals(key) || "Esc".equals(key)) return Key.ESCAPE;
        if ("ArrowLeft".equals(key) || "Left".equals(key)) return Key.LEFT;
        if ("ArrowRight".equals(key) || "Right".equals(key)) return Key.RIGHT;
        if ("ArrowUp".equals(key) || "Up".equals(key)) return Key.UP;
        if ("ArrowDown".equals(key) || "Down".equals(key)) return Key.DOWN;
        if ("Home".equals(key)) return Key.HOME;
        if ("End".equals(key)) return Key.END;
        if ("PageUp".equals(key)) return Key.PAGE_UP;
        if ("PageDown".equals(key)) return Key.PAGE_DOWN;
        if ("Delete".equals(key) || "Del".equals(key)) return Key.DELETE;
        if ("Shift".equals(key)) {
            return event.getLocation() == KeyboardEvent.DOM_KEY_LOCATION_RIGHT ? Key.SHIFT_RIGHT : Key.SHIFT_LEFT;
        }
        if ("Control".equals(key) || "Ctrl".equals(key)) {
            return event.getLocation() == KeyboardEvent.DOM_KEY_LOCATION_RIGHT ? Key.CONTROL_RIGHT : Key.CONTROL_LEFT;
        }
        if ("Alt".equals(key)) {
            return event.getLocation() == KeyboardEvent.DOM_KEY_LOCATION_RIGHT ? Key.ALT_RIGHT : Key.ALT_LEFT;
        }
        return Key.UNKNOWN;
    }

    private static Key mapCode(String code) {
        if (code.length() == 4 && code.startsWith("Key")) {
            char c = code.charAt(3);
            if (c >= 'A' && c <= 'Z') {
                return Key.valueOf(String.valueOf(c));
            }
        }
        if (code.length() == 6 && code.startsWith("Digit")) {
            char c = code.charAt(5);
            if (c >= '0' && c <= '9') {
                return digitKey(c - '0');
            }
        }
        if ("Backspace".equals(code)) return Key.BACKSPACE;
        if ("Tab".equals(code)) return Key.TAB;
        if ("Enter".equals(code) || "NumpadEnter".equals(code)) return Key.ENTER;
        if ("Escape".equals(code)) return Key.ESCAPE;
        if ("Space".equals(code)) return Key.SPACE;
        if ("ArrowLeft".equals(code)) return Key.LEFT;
        if ("ArrowRight".equals(code)) return Key.RIGHT;
        if ("ArrowUp".equals(code)) return Key.UP;
        if ("ArrowDown".equals(code)) return Key.DOWN;
        if ("Home".equals(code)) return Key.HOME;
        if ("End".equals(code)) return Key.END;
        if ("PageUp".equals(code)) return Key.PAGE_UP;
        if ("PageDown".equals(code)) return Key.PAGE_DOWN;
        if ("Delete".equals(code)) return Key.DELETE;
        if ("ShiftLeft".equals(code)) return Key.SHIFT_LEFT;
        if ("ShiftRight".equals(code)) return Key.SHIFT_RIGHT;
        if ("ControlLeft".equals(code)) return Key.CONTROL_LEFT;
        if ("ControlRight".equals(code)) return Key.CONTROL_RIGHT;
        if ("AltLeft".equals(code)) return Key.ALT_LEFT;
        if ("AltRight".equals(code)) return Key.ALT_RIGHT;
        if (code.length() >= 2 && code.charAt(0) == 'F') {
            try {
                int number = Integer.parseInt(code.substring(1));
                if (number >= 1 && number <= 12) {
                    return Key.valueOf("F" + number);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return Key.UNKNOWN;
    }

    private static Key digitKey(int digit) {
        switch (digit) {
            case 0: return Key.NUM_0;
            case 1: return Key.NUM_1;
            case 2: return Key.NUM_2;
            case 3: return Key.NUM_3;
            case 4: return Key.NUM_4;
            case 5: return Key.NUM_5;
            case 6: return Key.NUM_6;
            case 7: return Key.NUM_7;
            case 8: return Key.NUM_8;
            case 9: return Key.NUM_9;
            default: return Key.UNKNOWN;
        }
    }

    /**
     * Lists the supported touch dispatch values.
     *
     * @author xpenatan
     */
    private enum TouchDispatch {
        DOWN,
        MOVE,
        UP
    }

    /**
     * Represents a web graphics environment.
     *
     * @author xpenatan
     */
    private static final class WebGraphicsEnvironment implements GraphicsEnvironment {
        private final Display display;
        private final NativeWindow nativeWindow;

        WebGraphicsEnvironment(Display display, NativeWindow nativeWindow) {
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
     * Represents a web display.
     *
     * @author xpenatan
     */
    private static final class WebDisplay implements Display {
        private String title;
        private int width;
        private int height;
        private int framebufferWidth;
        private int framebufferHeight;
        private float contentScaleX = 1.0f;
        private float contentScaleY = 1.0f;
        private boolean closeRequested;

        WebDisplay(String title) {
            this.title = title != null ? title : "";
        }

        boolean size(int width, int height, int framebufferWidth, int framebufferHeight,
                float contentScaleX, float contentScaleY) {
            boolean changed = this.width != width || this.height != height
                    || this.framebufferWidth != framebufferWidth || this.framebufferHeight != framebufferHeight
                    || this.contentScaleX != contentScaleX || this.contentScaleY != contentScaleY;
            this.width = width;
            this.height = height;
            this.framebufferWidth = framebufferWidth;
            this.framebufferHeight = framebufferHeight;
            this.contentScaleX = validScale(contentScaleX);
            this.contentScaleY = validScale(contentScaleY);
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
            setDocumentTitle(this.title);
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

        private static float validScale(float scale) {
            return scale > 0.0f && Float.isFinite(scale) ? scale : 1.0f;
        }
    }
}
