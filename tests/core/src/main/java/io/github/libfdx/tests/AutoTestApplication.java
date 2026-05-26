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

public final class AutoTestApplication extends ApplicationAdapter {
    public interface CompletionHandler {
        void completed(int totalTests, int failedTests);
    }

    private static final String FREETYPE_FONT_ASSET = "font/freetype/lsans.ttf";
    private static final float DEFAULT_TEST_DURATION_SECONDS = 4.0f;
    private static final int DEFAULT_STABLE_FRAMES_REQUIRED = 10;
    private static final float DEFAULT_SPIKE_THRESHOLD_SECONDS = 0.10f;
    private static final float DEFAULT_LOAD_TIMEOUT_SECONDS = 15.0f;

    private final CompletionHandler completionHandler;
    private final boolean failOnComplete;
    private Fdx fdx;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private TestFpsLogger fpsLogger;
    private UiRoot overlay;
    private TestSelector.TestDescriptor[] tests;
    private ApplicationListener currentTest;
    private int currentIndex = -1;
    private float testTimerSeconds;
    private float loadWaitSeconds;
    private float testDurationSeconds;
    private float spikeThresholdSeconds;
    private float loadTimeoutSeconds;
    private int stableFramesRequired;
    private int stableFrameCount;
    private boolean currentTestLoaded;
    private boolean currentTestFailed;
    private boolean pendingSwitch;
    private boolean completed;
    private boolean summaryPrinted;
    private long renderedFrames;
    private final List<String> failures = new ArrayList<String>();

    public AutoTestApplication() {
        this(null, true);
    }

    public AutoTestApplication(CompletionHandler completionHandler, boolean failOnComplete) {
        this.completionHandler = completionHandler;
        this.failOnComplete = failOnComplete;
    }

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        fpsLogger = TestFpsLogger.create(fdx.logger(), "AutoTestApplication");
        tests = TestSelector.descriptors();
        testDurationSeconds = floatProperty("libfdx.test.autoDurationSeconds", DEFAULT_TEST_DURATION_SECONDS);
        stableFramesRequired = intProperty("libfdx.test.autoStableFrames", DEFAULT_STABLE_FRAMES_REQUIRED);
        spikeThresholdSeconds = floatProperty("libfdx.test.autoSpikeSeconds", DEFAULT_SPIKE_THRESHOLD_SECONDS);
        loadTimeoutSeconds = floatProperty("libfdx.test.autoLoadTimeoutSeconds", DEFAULT_LOAD_TIMEOUT_SECONDS);
        overlay = new UiToolkit(fdx.files())
                .theme(theme())
                .root(display, graphics)
                .autoUiScale(true);
        overlay.setContent(this::buildOverlay);
        System.out.println("[info] AutoTestApplication starting " + tests.length + " tests"
                + ", durationSeconds=" + testDurationSeconds
                + ", stableFrames=" + stableFramesRequired
                + ", spikeSeconds=" + spikeThresholdSeconds
                + ", loadTimeoutSeconds=" + loadTimeoutSeconds);
        nextTest();
    }

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
            disposeCurrentTest();
            nextTest();
            pendingSwitch = false;
            if (completed) {
                renderOverlayOnly(deltaSeconds);
                renderedFrames++;
                fpsLogger.frame(deltaSeconds, renderedFrames);
                return;
            }
        }

        if (currentTest != null) {
            try {
                currentTest.render();
                updateLoadState(deltaSeconds);
            } catch (Throwable error) {
                recordFailure(currentName(), "render", error);
                currentTestLoaded = true;
                pendingSwitch = true;
            }
        } else if (graphics != null) {
            graphics.clear(0.02f, 0.025f, 0.032f, 1.0f);
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

    @Override
    public void onFrameEnd() {
        if (currentTest != null) {
            currentTest.onFrameEnd();
        }
    }

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

    @Override
    public void dispose() {
        disposeCurrentTest();
        if (overlay != null) {
            overlay.dispose();
            overlay = null;
        }
        printSummary();
        if (failOnComplete && failures.size() > 0) {
            throw new FdxException("Auto test runner failed " + failures.size() + " of " + tests.length + " tests");
        }
    }

    private void nextTest() {
        currentIndex++;
        if (currentIndex >= tests.length) {
            completed = true;
            printSummary();
            if (completionHandler != null) {
                completionHandler.completed(tests.length, failures.size());
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
        currentTestLoaded = false;
        stableFrameCount = 0;
        loadWaitSeconds = 0.0f;
        testTimerSeconds = 0.0f;
        System.out.println("[info] Auto test " + (currentIndex + 1) + "/" + tests.length + ": " + descriptor.name());
        try {
            currentTest = descriptor.create(0L);
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

    private void recordFailure(String testName, String phase, Throwable error) {
        String failure = testName + " [" + phase + "]: " + error.getClass().getSimpleName()
                + " - " + error.getMessage();
        System.err.println("[error] " + failure);
        error.printStackTrace();
        if (!currentTestFailed) {
            failures.add(failure);
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
        int failed = failures.size();
        int passed = tests.length - failed;
        System.out.println("[info] Auto test runner complete: " + passed + " / " + tests.length + " passed.");
        for (int i = 0; i < failures.size(); i++) {
            System.out.println("[error] Auto test failure: " + failures.get(i));
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
            return "Auto complete: " + (tests.length - failures.size()) + " / " + tests.length + " passed";
        }
        if (tests == null || currentIndex < 0 || currentIndex >= tests.length) {
            return "Auto starting";
        }
        TestSelector.TestDescriptor descriptor = tests[currentIndex];
        return (currentIndex + 1) + " / " + tests.length + " - " + descriptor.displayName();
    }

    private String secondaryStatus() {
        if (completed) {
            return failures.size() == 0 ? "No failures" : failures.size() + " failures";
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
