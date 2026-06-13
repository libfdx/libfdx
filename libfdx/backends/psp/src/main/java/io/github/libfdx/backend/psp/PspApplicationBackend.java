package io.github.libfdx.backend.psp;

import io.github.libfdx.DefaultFdx;
import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationBackend;
import io.github.libfdx.application.ApplicationConfig;
import io.github.libfdx.application.ApplicationLifecycle;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.psp.natives.PSPCoreApi;
import io.github.libfdx.backend.psp.natives.PSPDebugApi;
import io.github.libfdx.backend.psp.natives.PSPFileApi;
import io.github.libfdx.backend.psp.natives.PSPGraphicsApi;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.display.DefaultDisplays;
import io.github.libfdx.display.Display;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.graphics.DefaultGraphics;
import io.github.libfdx.input.DefaultInput;

/**
 * Implements the backend integration for psp application.
 *
 * @author xpenatan
 */
public final class PspApplicationBackend implements ApplicationBackend, Application {
    public static final ProviderId ID = ProviderId.of("psp");
    private static final long FATAL_ERROR_HOLD_MILLIS = 30000L;
    private static boolean debugScreenReady;

    private final Logger logger = new PspLogger();
    private Fdx fdx;
    private PspDisplay display;
    private PspGraphicsContext graphics;
    private PspInputController inputController;
    private DefaultInput input;
    private ApplicationLifecycle lifecycle = ApplicationLifecycle.DISPOSED;
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
        clearLogFile();
        if (listener == null) {
            throw new FdxException("ApplicationListener cannot be null");
        }
        PspApplicationConfig actualConfig = toPspConfig(config);
        DisplayConfig displayConfig = actualConfig.displayConfig();

        PSPCoreApi.setupCallbacks();
        PSPGraphicsApi.initGraphics();
        display = new PspDisplay(displayConfig.title());
        graphics = new PspGraphicsContext();
        inputController = new PspInputController();
        input = inputController.input();
        fdx = new DefaultFdx(this, new DefaultDisplays(display), new DefaultGraphics(graphics), input,
                new PspFileSystem(), logger);

        disposed = false;
        running = true;
        lifecycle = ApplicationLifecycle.CREATED;

        String phase = "create";
        try {
            listener.create(fdx);
            listenerCreated = true;
            phase = "resize";
            listener.resize(display.width(), display.height());
            lifecycle = ApplicationLifecycle.RUNNING;
            phase = "loop";
            loop(listener, displayConfig);
        } catch (Throwable error) {
            logger.error("PSP application failed during " + phase, error);
            showFatalError(phase, error);
            throw error instanceof RuntimeException
                    ? (RuntimeException) error
                    : new FdxException("PSP application failed during " + phase, error);
        } finally {
            shutdown(listener);
        }
    }

    private static void showFatalError(String phase, Throwable error) {
        try {
            PSPDebugApi.pspDebugScreenInit();
            debugScreenReady = true;
            PSPDebugApi.pspDebugScreenSetXY(0, 0);
            debugPrintLine("libfdx PSP application failed");
            debugPrintLine("phase: " + safe(phase));
            debugPrintLine("type: " + safe(error != null ? error.getClass().getName() : null));
            debugPrintLine("message: " + safe(error != null ? error.getMessage() : null));
            Throwable cause = error != null ? error.getCause() : null;
            if (cause != null) {
                debugPrintLine("cause: " + safe(cause.getClass().getName()));
                debugPrintLine("cause message: " + safe(cause.getMessage()));
            }
            debugPrintLine("");
            debugPrintLine("Waiting for capture...");
            PSPCoreApi.delayMicros((int) (FATAL_ERROR_HOLD_MILLIS * 1000L));
        } catch (Throwable ignored) {
            // Fatal logging must never hide the original application failure.
        }
    }

    private static void debugPrintLine(String message) {
        if (!debugScreenReady) {
            return;
        }
        PSPDebugApi.pspDebugScreenPrintf(truncate(message, 68) + "\n");
    }

    private static String truncate(String value, int maxLength) {
        String safeValue = safe(value);
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    /**
     * Runs the start step.
     *
     * @param config the configuration
     * @param listener the listener
     */
    public void start(PspApplicationConfig config, ApplicationListener listener) {
        start((ApplicationConfig) config, listener);
    }

    private PspApplicationConfig toPspConfig(ApplicationConfig config) {
        if (config == null) {
            return new PspApplicationConfig();
        }
        if (config instanceof PspApplicationConfig) {
            return (PspApplicationConfig) config;
        }
        throw new FdxException("PspApplicationBackend requires PspApplicationConfig");
    }

    private void loop(ApplicationListener listener, DisplayConfig displayConfig) {
        long lastTimeMillis = System.currentTimeMillis();
        int endFrameVsync = displayConfig.vSync() ? PSPGraphicsApi.GU_TRUE : PSPGraphicsApi.GU_FALSE;
        while (true) {
            boolean nativeRunning = PSPCoreApi.isRunning();
            boolean closeRequested = display.closeRequested();
            int javaRunningFlag = nativeFlag(running);
            int nativeRunningFlag = nativeFlag(nativeRunning);
            int closeRequestedFlag = nativeFlag(closeRequested);
            if (!running || !nativeRunning || closeRequested) {
                PSPDebugApi.debugLoopLog((int) frameId, 9, javaRunningFlag, nativeRunningFlag, closeRequestedFlag);
                break;
            }
            long nowMillis = System.currentTimeMillis();
            deltaTime = (nowMillis - lastTimeMillis) / 1000.0f;
            lastTimeMillis = nowMillis;
            frameId++;
            if (frameId == 1L || frameId % 60L == 0L) {
                PSPDebugApi.debugLoopLog((int) frameId, 1, javaRunningFlag, nativeRunningFlag, closeRequestedFlag);
            }

            inputController.poll();
            PSPGraphicsApi.beginFrame(PSPGraphicsApi.GU_FALSE);
            try {
                listener.render();
                listener.onFrameEnd();
            } catch (Throwable error) {
                logger.error("PSP application frame failed", error);
                throw error instanceof RuntimeException
                        ? (RuntimeException) error
                        : new FdxException("PSP application frame failed", error);
            } finally {
                PSPGraphicsApi.endFrame(endFrameVsync, PSPGraphicsApi.GU_FALSE);
            }
            if (frameId % 60L == 0L) {
                System.gc();
                PSPDebugApi.debugHeapLog((int) frameId);
                javaRunningFlag = nativeFlag(running);
                nativeRunningFlag = nativeFlag(PSPCoreApi.isRunning());
                closeRequestedFlag = nativeFlag(display.closeRequested());
                PSPDebugApi.debugLoopLog((int) frameId, 4, javaRunningFlag, nativeRunningFlag, closeRequestedFlag);
            }
        }
        logger.warn("PSP application loop ended running=" + running + " native=" + PSPCoreApi.isRunning()
                + " close=" + (display != null && display.closeRequested()) + " frame=" + frameId);
    }

    private static int nativeFlag(boolean value) {
        return value ? 1 : 0;
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
            }
            graphics = null;
            inputController = null;
            input = null;
            fdx = null;
            display = null;
            running = false;
            disposed = true;
            PSPGraphicsApi.sceGuDisplay(PSPGraphicsApi.GU_FALSE);
            PSPGraphicsApi.sceGuTerm();
        }
    }

    private static void clearLogFile() {
        try {
            PSPFileApi.clearDebugLog();
        } catch (Throwable ignored) {
            // File logging is diagnostic and must not affect backend startup.
        }
    }

    private static void logFileLine(String message) {
        try {
            String safeMessage = message != null ? message : "";
            PSPFileApi.debugLog(safeMessage.toCharArray(), safeMessage.length());
        } catch (Throwable ignored) {
            // Diagnostic logging cannot be allowed to affect backend control flow.
        }
    }

    private static void logThrowable(Throwable error) {
        if (error == null) {
            return;
        }
        logFileLine("error message=" + error.getMessage());
        Throwable cause = error.getCause();
        if (cause != null) {
            logFileLine("cause=" + cause.getClass().getName() + " message=" + cause.getMessage());
        }
        StackTraceElement[] stackTrace = error.getStackTrace();
        int count = stackTrace.length < 8 ? stackTrace.length : 8;
        for (int i = 0; i < count; i++) {
            logFileLine("stack " + i + "=" + stackTrace[i]);
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
     * Represents a psp display.
     *
     * @author xpenatan
     */
    private static final class PspDisplay implements Display {
        private String title;
        private boolean closeRequested;

        PspDisplay(String title) {
            this.title = title != null ? title : "";
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
            return PspGraphicsContext.SCREEN_WIDTH;
        }

        /**
         * Returns the height.
         *
         * @return the height
         */
        @Override
        public int height() {
            return PspGraphicsContext.SCREEN_HEIGHT;
        }

        /**
         * Returns the framebuffer width.
         *
         * @return the framebuffer width
         */
        @Override
        public int framebufferWidth() {
            return PspGraphicsContext.SCREEN_WIDTH;
        }

        /**
         * Returns the framebuffer height.
         *
         * @return the framebuffer height
         */
        @Override
        public int framebufferHeight() {
            return PspGraphicsContext.SCREEN_HEIGHT;
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
     * Represents a psp logger.
     *
     * @author xpenatan
     */
    private static final class PspLogger implements Logger {
        /**
         * Runs the debug step.
         *
         * @param message the message
         */
        @Override
        public void debug(String message) {
            logFileLine("[debug] " + safe(message));
            debugPrintLine("[debug] " + safe(message));
        }

        /**
         * Runs the info step.
         *
         * @param message the message
         */
        @Override
        public void info(String message) {
            logFileLine("[info] " + safe(message));
            debugPrintLine("[info] " + safe(message));
        }

        /**
         * Runs the warn step.
         *
         * @param message the message
         */
        @Override
        public void warn(String message) {
            logFileLine("[warn] " + safe(message));
            debugPrintLine("[warn] " + safe(message));
        }

        /**
         * Runs the error step.
         *
         * @param message the message
         */
        @Override
        public void error(String message) {
            logFileLine("[error] " + safe(message));
            debugPrintLine("[error] " + safe(message));
        }

        /**
         * Runs the error step.
         *
         * @param message the message
         * @param error the error
         */
        @Override
        public void error(String message, Throwable error) {
            logFileLine("[error] " + safe(message));
            debugPrintLine("[error] " + safe(message));
            if (error != null) {
                logFileLine("type=" + safe(error.getClass().getName()));
                debugPrintLine(safe(error.getClass().getName()));
                logThrowable(error);
                debugPrintLine(safe(error.getMessage()));
            }
        }
    }
}
