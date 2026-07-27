package io.github.libfdx.tests;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiFont;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTextStyle;
import io.github.libfdx.ui.UiTheme;
import io.github.libfdx.ui.UiToolkit;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an auto test application.
 *
 * @author xpenatan
 */
public final class AutoTestApplication extends ApplicationAdapter {
    /**
     * Defines the contract for completion handler implementations.
     *
     * @author xpenatan
     */
    public interface CompletionHandler {
        /**
         * Runs the completed step.
         *
         * @param totalTests the total tests
         * @param failedTests the failed tests
         * @param skippedTests the tests skipped for unavailable provider features
         */
        void completed(int totalTests, int failedTests, int skippedTests);
    }

    private static final String FREETYPE_FONT_ASSET = "font/freetype/lsans.ttf";
    private static final float DEFAULT_TEST_DURATION_SECONDS = 4.0f;
    private static final int DEFAULT_STABLE_FRAMES_REQUIRED = 10;
    private static final float DEFAULT_SPIKE_THRESHOLD_SECONDS = 0.10f;
    private static final float DEFAULT_LOAD_TIMEOUT_SECONDS = 15.0f;
    private static final long MANAGED_TEST_FRAME_LIMIT = -1L;

    private final CompletionHandler completionHandler;
    private final boolean failOnComplete;
    private Fdx fdx;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private TestFpsLogger fpsLogger;
    private UiRoot overlay;
    private TestSelector.TestDescriptor[] tests;
    private boolean[] failedByTest;
    private ApplicationListener currentTest;
    private int currentIndex = -1;
    private int completedTests;
    private int skippedTests;
    private float testTimerSeconds;
    private float loadWaitSeconds;
    private float testDurationSeconds;
    private float spikeThresholdSeconds;
    private float loadTimeoutSeconds;
    private int stableFramesRequired;
    private int stableFrameCount;
    private boolean currentTestLoaded;
    private boolean currentTestFailed;
    private boolean currentTestInProgress;
    private boolean pendingSwitch;
    private boolean completed;
    private boolean summaryPrinted;
    private long renderedFrames;
    private Throwable firstFailure;
    private final List<String> failures = new ArrayList<String>();

    /**
     * Creates an auto test application.
     */
    public AutoTestApplication() {
        this(null, true);
    }

    /**
     * Creates an auto test application.
     *
     * @param completionHandler the completion handler
     * @param failOnComplete the fail on complete
     */
    public AutoTestApplication(CompletionHandler completionHandler, boolean failOnComplete) {
        this.completionHandler = completionHandler;
        this.failOnComplete = failOnComplete;
    }

    /**
     * Initializes the application with the libFDX runtime root.
     *
     * @param fdx the libFDX runtime root
     */
    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        fpsLogger = TestFpsLogger.create(fdx.logger(), "AutoTestApplication");
        tests = TestSelector.descriptors();
        failedByTest = new boolean[tests.length];
        testDurationSeconds = floatProperty("libfdx.test.autoDurationSeconds", DEFAULT_TEST_DURATION_SECONDS);
        stableFramesRequired = intProperty("libfdx.test.autoStableFrames", DEFAULT_STABLE_FRAMES_REQUIRED);
        spikeThresholdSeconds = floatProperty("libfdx.test.autoSpikeSeconds", DEFAULT_SPIKE_THRESHOLD_SECONDS);
        loadTimeoutSeconds = floatProperty("libfdx.test.autoLoadTimeoutSeconds", DEFAULT_LOAD_TIMEOUT_SECONDS);
        overlay = new UiToolkit(fdx.files())
                .theme(theme())
                .root(display, graphics);
        overlay.setContent(this::buildOverlay);
        System.out.println("[info] AutoTestApplication starting " + tests.length + " tests"
                + ", durationSeconds=" + testDurationSeconds
                + ", stableFrames=" + stableFramesRequired
                + ", spikeSeconds=" + spikeThresholdSeconds
                + ", loadTimeoutSeconds=" + loadTimeoutSeconds);
        nextTest();
    }

    /**
     * Handles a size change.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void resize(int width, int height) {
        if (currentTest != null) {
            try {
                currentTest.resize(width, height);
            } catch (Throwable error) {
                recordFailure(currentName(), "resize", error);
                pendingSwitch = true;
            }
        }
        if (overlay != null) {
            overlay.resize(width, height);
        }
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        if (completed) {
            renderOverlayOnly(deltaSeconds);
            renderedFrames++;
            fpsLogger.frame(deltaSeconds, renderedFrames);
            return;
        }
        if (pendingSwitch) {
            pendingSwitch = false;
            finishCurrentTest();
            nextTest();
            if (completed) {
                renderOverlayOnly(deltaSeconds);
                renderedFrames++;
                fpsLogger.frame(deltaSeconds, renderedFrames);
                return;
            }
        }

        boolean renderFailed = false;
        if (currentTest != null) {
            try {
                currentTest.render();
                updateLoadState(deltaSeconds);
            } catch (Throwable error) {
                recordFailure(currentName(), "render", error);
                currentTestLoaded = true;
                pendingSwitch = true;
                renderFailed = true;
            }
        } else if (graphics != null) {
            graphics.clear(0.02f, 0.025f, 0.032f, 1.0f);
        }

        if (renderFailed) {
            renderedFrames++;
            fpsLogger.reset();
            return;
        }

        if (currentTestLoaded) {
            testTimerSeconds += deltaSeconds;
            if (testTimerSeconds >= testDurationSeconds) {
                pendingSwitch = true;
            }
        }
        renderOverlayOnly(deltaSeconds);
        renderedFrames++;
        if (currentTest == null) {
            fpsLogger.frame(deltaSeconds, renderedFrames);
        } else {
            fpsLogger.reset();
        }
    }

    /**
     * Handles the frame end event.
     */
    @Override
    public void onFrameEnd() {
        if (currentTest != null) {
            try {
                currentTest.onFrameEnd();
            } catch (Throwable error) {
                recordFailure(currentName(), "frame-end", error);
                currentTestLoaded = true;
                pendingSwitch = true;
            }
        }
    }

    /**
     * Handles application pause.
     */
    @Override
    public void pause() {
        if (currentTest != null) {
            try {
                currentTest.pause();
            } catch (Throwable error) {
                recordFailure(currentName(), "pause", error);
            }
        }
    }

    /**
     * Handles application resume.
     */
    @Override
    public void resume() {
        if (currentTest != null) {
            try {
                currentTest.resume();
            } catch (Throwable error) {
                recordFailure(currentName(), "resume", error);
            }
        }
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        disposeCurrentTest();
        if (overlay != null) {
            overlay.dispose();
            overlay = null;
        }
        if (!completed) {
            rememberFailure(new FdxException("Auto test runner stopped after completing " + completedTests
                    + " of " + tests.length + " tests"));
        }
        printSummary();
        if (failOnComplete && (!completed || failures.size() > 0)) {
            throw new FdxException("Auto test runner completed " + completedTests + " of " + tests.length
                    + " tests with " + failures.size() + " failed tests", firstFailure);
        }
    }

    private void nextTest() {
        currentIndex++;
        while (currentIndex < tests.length && !tests[currentIndex].supports(graphics.device().capabilities())) {
            TestSelector.TestDescriptor skipped = tests[currentIndex];
            skippedTests++;
            completedTests++;
            System.out.println("[info] Auto test " + (currentIndex + 1) + "/" + tests.length + ": "
                    + skipped.name() + " skipped; provider " + graphics.providerId().value()
                    + " does not support " + skipped.unsupportedFeatures(graphics.device().capabilities()));
            currentIndex++;
        }
        if (currentIndex >= tests.length) {
            completed = true;
            printSummary();
            if (completionHandler != null) {
                completionHandler.completed(tests.length, failures.size(), skippedTests);
            } else {
                application.requestExit();
            }
            if (overlay != null) {
                overlay.requestCompose();
            }
            return;
        }

        TestSelector.TestDescriptor descriptor = tests[currentIndex];
        currentTestFailed = false;
        currentTestInProgress = true;
        currentTestLoaded = false;
        stableFrameCount = 0;
        loadWaitSeconds = 0.0f;
        testTimerSeconds = 0.0f;
        System.out.println("[info] Auto test " + (currentIndex + 1) + "/" + tests.length + ": " + descriptor.name());
        try {
            currentTest = descriptor.create(MANAGED_TEST_FRAME_LIMIT);
            currentTest.create(fdx);
            currentTest.resize(display.width(), display.height());
        } catch (Throwable error) {
            recordFailure(descriptor.name(), "create", error);
            disposeCurrentTest();
            pendingSwitch = true;
        }
        if (overlay != null) {
            overlay.requestCompose();
        }
    }

    private void updateLoadState(float deltaSeconds) {
        if (currentTestLoaded) {
            return;
        }
        loadWaitSeconds += deltaSeconds;
        if (deltaSeconds < spikeThresholdSeconds) {
            stableFrameCount++;
        } else {
            stableFrameCount = 0;
        }
        if (stableFrameCount >= stableFramesRequired || loadWaitSeconds >= loadTimeoutSeconds) {
            currentTestLoaded = true;
            testTimerSeconds = 0.0f;
            if (overlay != null) {
                overlay.requestCompose();
            }
        }
    }

    private void renderOverlayOnly(float deltaSeconds) {
        if (overlay == null) {
            return;
        }
        overlay.update(deltaSeconds);
        overlay.render();
    }

    private void disposeCurrentTest() {
        if (currentTest == null) {
            return;
        }
        String name = currentName();
        try {
            currentTest.pause();
        } catch (Throwable error) {
            recordFailure(name, "pause", error);
        }
        try {
            currentTest.dispose();
        } catch (Throwable error) {
            recordFailure(name, "dispose", error);
        }
        currentTest = null;
    }

    private void finishCurrentTest() {
        disposeCurrentTest();
        if (currentTestInProgress) {
            completedTests++;
            currentTestInProgress = false;
        }
    }

    private void recordFailure(String testName, String phase, Throwable error) {
        String failure = testName + " [" + phase + "]: " + error.getClass().getSimpleName()
                + " - " + error.getMessage();
        System.err.println("[error] " + failure);
        error.printStackTrace();
        rememberFailure(error);
        if (!currentTestFailed) {
            failures.add(failure);
            if (failedByTest != null && currentIndex >= 0 && currentIndex < failedByTest.length) {
                failedByTest[currentIndex] = true;
            }
            currentTestFailed = true;
        }
        if (overlay != null) {
            overlay.requestCompose();
        }
    }

    private void printSummary() {
        if (summaryPrinted || tests == null) {
            return;
        }
        summaryPrinted = true;
        int failed = completedFailureCount();
        int passed = completedTests - failed - skippedTests;
        if (completed) {
            System.out.println("[info] Auto test runner complete: " + passed + " passed, "
                    + skippedTests + " skipped, " + failed + " failed.");
        } else {
            System.out.println("[error] Auto test runner stopped: " + completedTests + " / " + tests.length
                    + " completed, " + passed + " passed, " + failures.size() + " failures observed.");
        }
        for (int i = 0; i < failures.size(); i++) {
            System.out.println("[error] Auto test failure: " + failures.get(i));
        }
    }

    private int completedFailureCount() {
        int failed = 0;
        if (failedByTest == null) {
            return failed;
        }
        int limit = Math.min(completedTests, failedByTest.length);
        for (int i = 0; i < limit; i++) {
            if (failedByTest[i]) {
                failed++;
            }
        }
        return failed;
    }

    private void rememberFailure(Throwable error) {
        if (error == null) {
            return;
        }
        if (firstFailure == null) {
            firstFailure = error;
        } else if (firstFailure != error) {
            firstFailure.addSuppressed(error);
        }
    }

    private String currentName() {
        if (tests == null || currentIndex < 0 || currentIndex >= tests.length) {
            return "<none>";
        }
        return tests[currentIndex].name();
    }

    private void buildOverlay(UiScope ui) {
        ui.column(Ui.modifier().fill().padding(12.0f).gap(8.0f), page -> {
            page.panel(Ui.modifier().fillWidth().padding(10.0f).gap(4.0f).style("overlay-panel"), panel -> {
                panel.text(primaryStatus(), Ui.modifier().style(currentTestFailed ? "error" : "title"));
                panel.text(secondaryStatus(), Ui.modifier().style("muted"));
            });
            page.spacer(Ui.modifier().weight(1.0f));
        });
    }

    private String primaryStatus() {
        if (completed) {
            return "Auto complete: " + (completedTests - completedFailureCount() - skippedTests)
                    + " / " + tests.length
                    + " passed";
        }
        if (tests == null || currentIndex < 0 || currentIndex >= tests.length) {
            return "Auto starting";
        }
        TestSelector.TestDescriptor descriptor = tests[currentIndex];
        return (currentIndex + 1) + " / " + tests.length + " - " + descriptor.displayName();
    }

    private String secondaryStatus() {
        if (completed) {
            String skipped = skippedTests == 1 ? "1 skipped" : skippedTests + " skipped";
            return failures.size() == 0 ? "No failures, " + skipped : failures.size() + " failures, " + skipped;
        }
        if (!currentTestLoaded) {
            return "Waiting for stable frames";
        }
        return currentTestFailed ? "Failed, moving to next test" : "Running";
    }

    private static UiTheme theme() {
        UiTextStyle text = UiTextStyle.text()
                .font(UiFont.freeType(FREETYPE_FONT_ASSET, 16.0f))
                .size(16.0f)
                .lineHeight(20.0f)
                .color(UiColor.rgba8888(0xf2f4f8ff));
        UiTextStyle title = UiTextStyle.text()
                .font(UiFont.freeType(FREETYPE_FONT_ASSET, 20.0f))
                .size(20.0f)
                .lineHeight(26.0f)
                .color(UiColor.rgba8888(0xffffffff));
        UiTextStyle muted = UiTextStyle.text()
                .font(UiFont.freeType(FREETYPE_FONT_ASSET, 14.0f))
                .size(14.0f)
                .lineHeight(18.0f)
                .color(UiColor.rgba8888(0xaeb7c4ff));
        UiTextStyle error = UiTextStyle.text()
                .font(UiFont.freeType(FREETYPE_FONT_ASSET, 20.0f))
                .size(20.0f)
                .lineHeight(26.0f)
                .color(UiColor.rgba8888(0xff7b72ff));
        return Ui.darkTheme()
                .style("overlay-panel", UiStyle.style()
                        .padding(10.0f)
                        .background(UiDrawable.color(UiColor.rgba8888(0x101820dd))))
                .style("title", UiStyle.style().text(title))
                .style("muted", UiStyle.style().text(muted))
                .style("error", UiStyle.style().text(error))
                .text(UiStyle.style().text(text));
    }

    private static int intProperty(String name, int fallback) {
        String value = System.getProperty(name);
        if (value == null || value.trim().length() == 0) {
            return fallback;
        }
        return Integer.parseInt(value.trim());
    }

    private static float floatProperty(String name, float fallback) {
        String value = System.getProperty(name);
        if (value == null || value.trim().length() == 0) {
            return fallback;
        }
        return Float.parseFloat(value.trim());
    }
}
