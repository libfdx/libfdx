package io.github.libfdx.backend.android;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
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
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.DefaultGraphics;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.GraphicsProviderSupport;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.input.DefaultCursor;
import io.github.libfdx.input.DefaultGamepads;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.DefaultInputCapabilities;
import io.github.libfdx.input.Key;
import io.github.libfdx.math.internal.MathAcceleration;
import io.github.libfdx.runtime.core.RuntimeCore;

/**
 * Implements the backend integration for android application.
 *
 * @author xpenatan
 */
public final class AndroidApplicationBackend implements ApplicationBackend, Application,
        SurfaceHolder.Callback, Choreographer.FrameCallback, View.OnTouchListener, View.OnKeyListener {
    public static final ProviderId ID = ProviderId.of("android");
    private static final Key[] DIGIT_KEYS = {
            Key.NUM_0,
            Key.NUM_1,
            Key.NUM_2,
            Key.NUM_3,
            Key.NUM_4,
            Key.NUM_5,
            Key.NUM_6,
            Key.NUM_7,
            Key.NUM_8,
            Key.NUM_9
    };
    private static final Key[] LETTER_KEYS = {
            Key.A,
            Key.B,
            Key.C,
            Key.D,
            Key.E,
            Key.F,
            Key.G,
            Key.H,
            Key.I,
            Key.J,
            Key.K,
            Key.L,
            Key.M,
            Key.N,
            Key.O,
            Key.P,
            Key.Q,
            Key.R,
            Key.S,
            Key.T,
            Key.U,
            Key.V,
            Key.W,
            Key.X,
            Key.Y,
            Key.Z
    };

    private final SystemLogger logger = new SystemLogger();
    private Activity activity;
    private AndroidApplicationConfig config;
    private ApplicationListener listener;
    private FrameLayout rootView;
    private AndroidInputView surfaceView;
    private AndroidTextInputController textInputController;
    private Surface surface;
    private Fdx fdx;
    private AndroidDisplay display;
    private GraphicsAttachment graphics;
    private DefaultInput input;
    private ApplicationLifecycle lifecycle = ApplicationLifecycle.DISPOSED;
    private boolean running;
    private boolean paused;
    private boolean disposed = true;
    private boolean listenerCreated;
    private boolean startupFailed;
    private boolean frameCallbackPosted;
    private long lastFrameTimeNanos;
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
        throw new FdxException("AndroidApplicationBackend must be attached to an Android Activity");
    }

    /**
     * Runs the attach step.
     *
     * @param activity the activity
     * @param config the configuration
     * @param listener the listener
     */
    public void attach(Activity activity, AndroidApplicationConfig config, ApplicationListener listener) {
        if (activity == null) {
            throw new FdxException("Android Activity cannot be null");
        }
        if (listener == null) {
            throw new FdxException("ApplicationListener cannot be null");
        }
        this.activity = activity;
        this.config = config != null ? config : new AndroidApplicationConfig();
        this.listener = listener;
        this.display = new AndroidDisplay(activity, this.config.displayConfig().title());
        this.disposed = false;
        this.running = true;
        this.paused = false;
        this.lifecycle = ApplicationLifecycle.CREATED;

        setupActivityWindow(activity);
        textInputController = new AndroidTextInputController(activity, this);
        rootView = new FrameLayout(activity);
        surfaceView = new AndroidInputView(activity, textInputController);
        textInputController.container(rootView);
        textInputController.view(surfaceView);
        surfaceView.setFocusable(true);
        surfaceView.setFocusableInTouchMode(true);
        surfaceView.setOnTouchListener(this);
        surfaceView.setOnKeyListener(this);
        surfaceView.getHolder().addCallback(this);
        rootView.addView(surfaceView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        activity.setContentView(rootView);
        surfaceView.requestFocus();
    }

    AndroidTextEditorStyle nativeTextEditorStyle() {
        return config != null ? config.nativeTextEditorStyle() : new AndroidTextEditorStyle();
    }

    private void setupActivityWindow(Activity activity) {
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        View decorView = activity.getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    /**
     * Runs the surface created step.
     *
     * @param holder the holder
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surface = holder.getSurface();
        Rect frame = holder.getSurfaceFrame();
        int width = Math.max(1, frame.width());
        int height = Math.max(1, frame.height());
        refreshDisplaySize(width, height);
        createSessionIfNeeded();
    }

    /**
     * Runs the surface changed step.
     *
     * @param holder the holder
     * @param format the format
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surface = holder.getSurface();
        refreshDisplaySize(width, height);
        if (!listenerCreated) {
            createSessionIfNeeded();
            return;
        }
        if (graphics != null) {
            graphics.resize(display.framebufferWidth(), display.framebufferHeight());
        }
        listener.resize(display.width(), display.height());
    }

    /**
     * Runs the surface destroyed step.
     *
     * @param holder the holder
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        removeFrameCallbackIfNeeded();
        shutdown();
        surface = null;
    }

    private void createSessionIfNeeded() {
        if (startupFailed || listenerCreated || surface == null || !surface.isValid() || display.framebufferWidth() <= 0
                || display.framebufferHeight() <= 0) {
            return;
        }
        GraphicsEnvironment graphicsEnvironment = new AndroidGraphicsEnvironment(display, NativeWindow.android(surface));
        GraphicsAttachment createdGraphics;
        try {
            createdGraphics = createGraphicsAttachment(graphicsEnvironment);
        } catch (FdxException e) {
            handleStartupFailure(e);
            return;
        }

        FileSystem files = new AndroidFileSystem(activity);

        graphics = createdGraphics;
        input = createInput();
        fdx = new DefaultFdx(this, new DefaultDisplays(display), new DefaultGraphics(graphics), input, files, logger);
        RuntimeCore.registerProvider(new AndroidRuntimeCoreProvider());
        MathAcceleration.register(new AndroidNativeMathAccelerator());

        listener.create(fdx);
        listenerCreated = true;
        listener.resize(display.width(), display.height());
        lifecycle = ApplicationLifecycle.RUNNING;
        lastFrameTimeNanos = System.nanoTime();
        postFrameCallbackIfNeeded();
    }

    private DefaultInput createInput() {
        DefaultInput createdInput = new DefaultInput(ProviderId.of("android_input"),
                new DefaultInputCapabilities(true, true, true, true, false, false), new DefaultCursor(),
                new DefaultGamepads(), textInputController);
        if (textInputController != null) {
            textInputController.input(createdInput);
        }
        return createdInput;
    }

    private void refreshDisplaySize(int framebufferWidth, int framebufferHeight) {
        display.size(framebufferWidth, framebufferHeight, framebufferWidth, framebufferHeight);
    }

    private GraphicsAttachment createGraphicsAttachment(GraphicsEnvironment graphicsEnvironment) {
        GraphicsAttachmentProvider graphicsProvider = config.graphics();
        if (graphicsProvider == null) {
            throw new FdxException("No Android graphics provider configured");
        }

        GraphicsFailureCollector failures = new GraphicsFailureCollector();
        GraphicsAttachment graphicsAttachment = tryCreateGraphics(graphicsProvider, graphicsEnvironment, failures);
        if (graphicsAttachment != null) {
            return graphicsAttachment;
        }

        if (config.graphicsFallbackEnabled()) {
            GraphicsAttachmentProvider[] fallbackGraphics = config.fallbackGraphics();
            for (GraphicsAttachmentProvider fallbackProvider : fallbackGraphics) {
                if (fallbackProvider == null || fallbackProvider.providerId().equals(graphicsProvider.providerId())) {
                    continue;
                }
                graphicsAttachment = tryCreateGraphics(fallbackProvider, graphicsEnvironment, failures);
                if (graphicsAttachment != null) {
                    return graphicsAttachment;
                }
            }
        }

        throw new FdxException("Android graphics startup failed. " + failures.message());
    }

    private GraphicsAttachment tryCreateGraphics(GraphicsAttachmentProvider provider,
            GraphicsEnvironment graphicsEnvironment, GraphicsFailureCollector failures) {
        String supportFailure = supportFailureReason(provider);
        if (supportFailure != null) {
            failures.add(provider, supportFailure);
            logger.warn("Android graphics provider " + provider.providerId() + " is not supported: " + supportFailure);
            return null;
        }

        try {
            GraphicsAttachment graphicsAttachment = provider.create(graphicsEnvironment);
            logger.info("Android graphics provider selected: " + provider.providerId());
            return graphicsAttachment;
        } catch (RuntimeException e) {
            failures.add(provider, e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            logger.warn("Android graphics provider " + provider.providerId() + " failed to start: "
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
            return null;
        }
    }

    private static String supportFailureReason(GraphicsAttachmentProvider provider) {
        if (provider instanceof GraphicsProviderSupport) {
            return ((GraphicsProviderSupport) provider).supportFailureReason();
        }
        return null;
    }

    private void handleStartupFailure(FdxException error) {
        if (config.graphicsFailureMode() == AndroidGraphicsFailureMode.THROW) {
            throw error;
        }
        showStartupError(error.getMessage(), error);
    }

    private void showStartupError(String message, Throwable error) {
        startupFailed = true;
        running = false;
        lifecycle = ApplicationLifecycle.DISPOSED;
        removeFrameCallbackIfNeeded();
        logger.error("Android graphics startup failed", error);

        TextView errorView = new TextView(activity);
        errorView.setText("Graphics startup failed\n\n" + (message != null ? message : "Unknown graphics error"));
        errorView.setTextColor(Color.WHITE);
        errorView.setBackgroundColor(Color.rgb(24, 24, 24));
        errorView.setGravity(Gravity.CENTER);
        errorView.setTextSize(16.0f);
        int padding = Math.round(24.0f * activity.getResources().getDisplayMetrics().density);
        errorView.setPadding(padding, padding, padding, padding);
        activity.setContentView(errorView);
    }

    /**
     * Runs the do frame step.
     *
     * @param frameTimeNanos the frame time nanos
     */
    @Override
    public void doFrame(long frameTimeNanos) {
        frameCallbackPosted = false;
        if (!running || paused || disposed || !listenerCreated || surface == null || !surface.isValid()) {
            return;
        }

        if (graphics != null) {
            graphics.processEvents();
        }

        long now = System.nanoTime();
        deltaTime = (now - lastFrameTimeNanos) / 1000000000.0f;
        lastFrameTimeNanos = now;
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
        postFrameCallbackIfNeeded();
    }

    /**
     * Handles application pause.
     */
    public void pause() {
        if (paused || disposed) {
            return;
        }
        paused = true;
        removeFrameCallbackIfNeeded();
        if (listenerCreated) {
            listener.pause();
            lifecycle = ApplicationLifecycle.PAUSED;
        }
    }

    /**
     * Handles application resume.
     */
    public void resume() {
        if (disposed) {
            return;
        }
        paused = false;
        if (listenerCreated) {
            listener.resume();
            lifecycle = ApplicationLifecycle.RUNNING;
            lastFrameTimeNanos = System.nanoTime();
            postFrameCallbackIfNeeded();
        }
    }

    private void postFrameCallbackIfNeeded() {
        if (frameCallbackPosted || paused || disposed || !running || surface == null) {
            return;
        }
        frameCallbackPosted = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void removeFrameCallbackIfNeeded() {
        if (!frameCallbackPosted) {
            return;
        }
        Choreographer.getInstance().removeFrameCallback(this);
        frameCallbackPosted = false;
    }

    /**
     * Handles the touch event.
     *
     * @param view the view
     * @param event the event
     * @return true if on touch succeeds or is active; false otherwise
     */
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (view != null) {
            view.requestFocus();
        }
        if (input == null || event == null) {
            return false;
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            dispatchTouchDown(event, event.getActionIndex());
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            dispatchTouchUp(event, event.getActionIndex());
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                dispatchTouchMoved(event, i);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                dispatchTouchUp(event, i);
            }
            return true;
        }
        return false;
    }

    /**
     * Handles the key event.
     *
     * @param view the view
     * @param keyCode the key code
     * @param event the event
     * @return true if on key succeeds or is active; false otherwise
     */
    @Override
    public boolean onKey(View view, int keyCode, KeyEvent event) {
        if (input == null || event == null) {
            return false;
        }

        Key key = mapKey(keyCode);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            boolean handled = false;
            if (key != Key.UNKNOWN) {
                handled = input.dispatchKeyDown(key);
            }
            int unicode = event.getUnicodeChar();
            if (isPrintableCodePoint(unicode)) {
                handled = input.dispatchTextInput(new String(Character.toChars(unicode))) || handled;
            }
            return handled;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            return key != Key.UNKNOWN && input.dispatchKeyUp(key);
        }
        return false;
    }

    private boolean dispatchTouchDown(MotionEvent event, int pointerIndex) {
        return input.dispatchTouchDown(event.getPointerId(pointerIndex), inputX(event, pointerIndex),
                inputY(event, pointerIndex), event.getPressure(pointerIndex));
    }

    private boolean dispatchTouchUp(MotionEvent event, int pointerIndex) {
        return input.dispatchTouchUp(event.getPointerId(pointerIndex), inputX(event, pointerIndex),
                inputY(event, pointerIndex), event.getPressure(pointerIndex));
    }

    private boolean dispatchTouchMoved(MotionEvent event, int pointerIndex) {
        return input.dispatchTouchMoved(event.getPointerId(pointerIndex), inputX(event, pointerIndex),
                inputY(event, pointerIndex), event.getPressure(pointerIndex));
    }

    private int inputX(MotionEvent event, int pointerIndex) {
        int sourceWidth = surfaceView != null ? surfaceView.getWidth() : 0;
        int targetWidth = display != null && display.width() > 0 ? display.width()
                : config != null && config.displayConfig() != null ? config.displayConfig().width() : 0;
        return scaleInputCoordinate(event.getX(pointerIndex), sourceWidth, targetWidth);
    }

    private int inputY(MotionEvent event, int pointerIndex) {
        int sourceHeight = surfaceView != null ? surfaceView.getHeight() : 0;
        int targetHeight = display != null && display.height() > 0 ? display.height()
                : config != null && config.displayConfig() != null ? config.displayConfig().height() : 0;
        return scaleInputCoordinate(event.getY(pointerIndex), sourceHeight, targetHeight);
    }

    private static int scaleInputCoordinate(float value, int sourceSize, int targetSize) {
        if (sourceSize > 0 && targetSize > 0 && sourceSize != targetSize) {
            return Math.round(value * targetSize / (float) sourceSize);
        }
        return Math.round(value);
    }

    private static boolean isPrintableCodePoint(int codePoint) {
        return Character.isValidCodePoint(codePoint) && !Character.isISOControl(codePoint);
    }

    private static Key mapKey(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return DIGIT_KEYS[keyCode - KeyEvent.KEYCODE_0];
        }
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            return LETTER_KEYS[keyCode - KeyEvent.KEYCODE_A];
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL:
                return Key.BACKSPACE;
            case KeyEvent.KEYCODE_TAB:
                return Key.TAB;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                return Key.ENTER;
            case KeyEvent.KEYCODE_ESCAPE:
                return Key.ESCAPE;
            case KeyEvent.KEYCODE_BACK:
                return Key.BACK;
            case KeyEvent.KEYCODE_SPACE:
                return Key.SPACE;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return Key.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return Key.RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP:
                return Key.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return Key.DOWN;
            case KeyEvent.KEYCODE_MOVE_HOME:
                return Key.HOME;
            case KeyEvent.KEYCODE_MOVE_END:
                return Key.END;
            case KeyEvent.KEYCODE_PAGE_UP:
                return Key.PAGE_UP;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return Key.PAGE_DOWN;
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return Key.DELETE;
            case KeyEvent.KEYCODE_SHIFT_LEFT:
                return Key.SHIFT_LEFT;
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return Key.SHIFT_RIGHT;
            case KeyEvent.KEYCODE_CTRL_LEFT:
                return Key.CONTROL_LEFT;
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                return Key.CONTROL_RIGHT;
            case KeyEvent.KEYCODE_ALT_LEFT:
                return Key.ALT_LEFT;
            case KeyEvent.KEYCODE_ALT_RIGHT:
                return Key.ALT_RIGHT;
            case KeyEvent.KEYCODE_F1:
                return Key.F1;
            case KeyEvent.KEYCODE_F2:
                return Key.F2;
            case KeyEvent.KEYCODE_F3:
                return Key.F3;
            case KeyEvent.KEYCODE_F4:
                return Key.F4;
            case KeyEvent.KEYCODE_F5:
                return Key.F5;
            case KeyEvent.KEYCODE_F6:
                return Key.F6;
            case KeyEvent.KEYCODE_F7:
                return Key.F7;
            case KeyEvent.KEYCODE_F8:
                return Key.F8;
            case KeyEvent.KEYCODE_F9:
                return Key.F9;
            case KeyEvent.KEYCODE_F10:
                return Key.F10;
            case KeyEvent.KEYCODE_F11:
                return Key.F11;
            case KeyEvent.KEYCODE_F12:
                return Key.F12;
            default:
                return Key.UNKNOWN;
        }
    }

    private void shutdown() {
        if (listenerCreated) {
            lifecycle = ApplicationLifecycle.DISPOSED;
            try {
                listener.dispose();
            } finally {
                listenerCreated = false;
            }
        }
        if (graphics != null) {
            graphics.dispose();
            graphics = null;
        }
        if (textInputController != null) {
            textInputController.hideTextInput();
            textInputController.input(null);
        }
        fdx = null;
        input = null;
        RuntimeCore.registerProvider(null);
        MathAcceleration.register(null);
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
        if (activity != null) {
            activity.finish();
        }
    }

    boolean handleBackNavigation() {
        if (input == null) {
            return false;
        }
        boolean handled = input.dispatchKeyDown(Key.BACK);
        input.dispatchKeyUp(Key.BACK);
        return handled;
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
        removeFrameCallbackIfNeeded();
        shutdown();
        if (surfaceView != null) {
            surfaceView.setOnTouchListener(null);
            surfaceView.setOnKeyListener(null);
            surfaceView.getHolder().removeCallback(this);
            if (textInputController != null) {
                textInputController.view(null);
            }
            surfaceView = null;
        }
        rootView = null;
        surface = null;
        running = false;
        disposed = true;
        lifecycle = ApplicationLifecycle.DISPOSED;
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
     * Represents an android graphics environment.
     *
     * @author xpenatan
     */
    private static final class AndroidGraphicsEnvironment implements GraphicsEnvironment {
        private final Display display;
        private final NativeWindow nativeWindow;

        AndroidGraphicsEnvironment(Display display, NativeWindow nativeWindow) {
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
     * Represents a graphics failure collector.
     *
     * @author xpenatan
     */
    private static final class GraphicsFailureCollector {
        private final StringBuilder message = new StringBuilder();

        void add(GraphicsAttachmentProvider provider, String failure) {
            if (message.length() > 0) {
                message.append(' ');
            }
            message.append(provider.providerId()).append(": ").append(failure);
        }

        String message() {
            return message.length() > 0 ? message.toString() : "No provider failure details were reported.";
        }
    }

    /**
     * Represents an android display.
     *
     * @author xpenatan
     */
    private static final class AndroidDisplay implements Display {
        private final Activity activity;
        private String title;
        private int width;
        private int height;
        private int framebufferWidth;
        private int framebufferHeight;
        private float contentScaleX = 1.0f;
        private float contentScaleY = 1.0f;
        private boolean closeRequested;

        AndroidDisplay(Activity activity, String title) {
            this.activity = activity;
            this.title = title != null ? title : "";
            activity.setTitle(this.title);
        }

        void size(int width, int height) {
            size(width, height, width, height);
        }

        void size(int width, int height, int framebufferWidth, int framebufferHeight) {
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
            this.framebufferWidth = Math.max(0, framebufferWidth);
            this.framebufferHeight = Math.max(0, framebufferHeight);
            float scale = platformContentScale();
            this.contentScaleX = scale;
            this.contentScaleY = scale;
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
            activity.setTitle(this.title);
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

        private float platformContentScale() {
            float scale = activity.getResources().getDisplayMetrics().density;
            return scale > 0.0f && Float.isFinite(scale) ? scale : 1.0f;
        }
    }
}
