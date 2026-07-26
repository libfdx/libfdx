package io.github.libfdx.tests;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.storage.KeyValueStore;
import io.github.libfdx.tests.graphics.FramebufferCapture;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiFont;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiScrollState;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTextAlign;
import io.github.libfdx.ui.UiTextStyle;
import io.github.libfdx.ui.UiTheme;
import io.github.libfdx.ui.UiToolkit;
import java.nio.ByteBuffer;

/**
 * Represents a test chooser application.
 *
 * @author xpenatan
 */
public final class TestChooserApplication extends ApplicationAdapter {
    private static final String FREETYPE_FONT_ASSET = "font/freetype/lsans.ttf";
    private static final String[] DEFAULT_GRAPHICS = { "wgpu" };
    private static final String SELECTED_TEST_PROPERTY = "libfdx.test.selected";
    private static final String SELECTED_TEST_STORE = "test-selector";
    private static final String SELECTED_TEST_KEY = "selected";
    private static final float TEST_ROW_HEIGHT = 40.0f;
    private static final float TEST_ROW_MARGIN = 2.0f;

    private final String[] graphicsOptions;
    private final TestLaunchHandler launchHandler;
    private final boolean embeddedFallback;
    private final boolean compactLayout;
    private final InputAdapter returnInputProcessor = new InputAdapter() {
        @Override
        public boolean keyDown(KeyEvent event) {
            Key key = event.key();
            if (key == Key.ESCAPE || key == Key.BACK) {
                return requestReturnToList();
            }
            return false;
        }
    };
    private Fdx fdx;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private UiRoot root;
    private UiScrollState testScroll;
    private KeyValueStore selectedTestStore;
    private UiBooleanState debugLines;
    private UiIntState selectedGraphicsIndex;
    private UiIntState selectedTestIndex;
    private ApplicationListener currentTest;
    private String currentTestName;
    private String pendingLaunchName;
    private String pendingReturnStatus;
    private String capturePath;
    private long captureFrame;
    private long exitAfterFrames;
    private long renderedFrames;
    private boolean captured;
    private boolean pendingReturnToList;
    private boolean scrollToSelectedTestPending = true;
    private String status = "Ready";

    /**
     * Creates a test chooser application.
     *
     * @param graphicsOptions the graphics options
     * @param initialGraphics the initial graphics
     * @param launchHandler the launch handler
     * @param embeddedFallback the embedded fallback
     */
    public TestChooserApplication(String[] graphicsOptions, String initialGraphics, TestLaunchHandler launchHandler,
            boolean embeddedFallback) {
        this(graphicsOptions, initialGraphics, launchHandler, embeddedFallback, false);
    }

    /**
     * Creates a test chooser application.
     *
     * @param graphicsOptions the graphics options
     * @param initialGraphics the initial graphics
     * @param launchHandler the launch handler
     * @param embeddedFallback the embedded fallback
     * @param compactLayout the compact layout
     */
    public TestChooserApplication(String[] graphicsOptions, String initialGraphics, TestLaunchHandler launchHandler,
            boolean embeddedFallback, boolean compactLayout) {
        this.graphicsOptions = normalizedGraphicsOptions(graphicsOptions);
        this.launchHandler = launchHandler;
        this.embeddedFallback = embeddedFallback;
        this.compactLayout = compactLayout;
        this.selectedGraphicsIndex = Ui.state(initialGraphicsIndex(this.graphicsOptions, initialGraphics));
        this.selectedTestIndex = Ui.state(initialTestIndex());
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
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "TestSelector");
        exitAfterFrames = longProperty("libfdx.test.frames", 0L);
        captureFrame = longProperty("libfdx.test.captureFrame", 0L);
        capturePath = trim(System.getProperty("libfdx.test.capture"));
        selectedTestStore = fdx.storage().cache(SELECTED_TEST_STORE).load();
        restoreSelectedTest();
        testScroll = new UiScrollState();
        debugLines = Ui.state(Boolean.parseBoolean(System.getProperty("libfdx.test.uiDebugLines", "false")));
        fdx.input().addProcessor(returnInputProcessor);
        root = new UiToolkit(fdx.files())
                .theme(theme(compactLayout))
                .root(display, graphics)
                .input(fdx.input())
                .debugLines(debugLines.get());
        root.setContent(this::buildUi);
        System.out.println("[info] TestChooserApplication ready, graphicsOptions=" + graphicsOptionList());
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
            currentTest.resize(width, height);
        }
        if (root != null) {
            root.resize(width, height);
        }
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        processPendingLaunch();
        if (currentTest != null) {
            try {
                currentTest.render();
            } catch (Throwable error) {
                String failedName = currentTestName;
                disposeCurrentTest();
                status = failedName + " failed: " + error.getClass().getSimpleName() + " - " + error.getMessage();
                error.printStackTrace();
                if (root != null) {
                    root.requestCompose();
                }
            }
            if (pendingReturnToList) {
                disposeCurrentTest();
                pendingReturnToList = false;
                scrollToSelectedTestPending = true;
                status = pendingReturnStatus != null ? pendingReturnStatus : "Returned to test list";
                pendingReturnStatus = null;
                if (root != null) {
                    root.requestCompose();
                }
            }
        } else if (graphics != null) {
            graphics.clear(0.02f, 0.025f, 0.032f, 1.0f);
        }
        if (root != null && currentTest == null) {
            root.update(deltaSeconds);
            root.debugLines(debugLines != null && debugLines.get());
            root.render();
        }
        captureIfRequested();
        renderedFrames++;
        if (shouldLogSelectorFps()) {
            fpsLogger.frame(deltaSeconds, renderedFrames);
        } else {
            fpsLogger.reset();
        }
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    /**
     * Handles the frame end event.
     */
    @Override
    public void onFrameEnd() {
        if (currentTest != null) {
            currentTest.onFrameEnd();
        }
    }

    /**
     * Handles application pause.
     */
    @Override
    public void pause() {
        if (currentTest != null) {
            currentTest.pause();
        }
    }

    /**
     * Handles application resume.
     */
    @Override
    public void resume() {
        if (currentTest != null) {
            currentTest.resume();
        }
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        disposeCurrentTest();
        if (fdx != null) {
            fdx.input().removeProcessor(returnInputProcessor);
        }
        if (root != null) {
            root.dispose();
            root = null;
        }
        if (capturePath != null && !captured) {
            throw new FdxException("TestChooserApplication did not capture framebuffer to " + capturePath);
        }
    }

    private void processPendingLaunch() {
        if (pendingLaunchName == null) {
            return;
        }
        String launchName = pendingLaunchName;
        pendingLaunchName = null;
        syncDebugLineProperty();
        String graphicsName = selectedGraphicsName();
        if (launchHandler != null && launchHandler.launch(launchName, graphicsName)) {
            status = "Launched " + launchLabel(launchName) + " with " + graphicsDisplayName(graphicsName);
            if (root != null) {
                root.requestCompose();
            }
            return;
        }
        if (!embeddedFallback) {
            status = "Could not launch " + launchLabel(launchName) + " with " + graphicsDisplayName(graphicsName);
            if (root != null) {
                root.requestCompose();
            }
            return;
        }
        if (TestSelector.AUTO_TEST_NAME.equals(launchName)) {
            startEmbeddedAuto();
        } else {
            startEmbeddedTest(launchName);
        }
    }

    private void startEmbeddedTest(String testName) {
        disposeCurrentTest();
        TestSelector.TestDescriptor descriptor = TestSelector.descriptor(testName);
        if (descriptor == null) {
            status = "Unknown test: " + testName;
            return;
        }
        currentTestName = descriptor.name();
        try {
            currentTest = descriptor.create(0L);
            currentTest.create(fdx);
            currentTest.resize(display.width(), display.height());
            status = "Running " + descriptor.displayName();
        } catch (Throwable error) {
            currentTest = null;
            currentTestName = null;
            status = descriptor.displayName() + " failed to start: " + error.getClass().getSimpleName()
                    + " - " + error.getMessage();
            error.printStackTrace();
        }
        if (root != null) {
            refreshChooserUi();
        }
    }

    private void startEmbeddedAuto() {
        disposeCurrentTest();
        currentTestName = TestSelector.AUTO_TEST_NAME;
        currentTest = new AutoTestApplication(new AutoTestApplication.CompletionHandler() {
            @Override
            public void completed(int totalTests, int failedTests) {
                pendingReturnToList = true;
                pendingReturnStatus = "Auto complete: " + (totalTests - failedTests) + " / " + totalTests + " passed";
            }
        }, false);
        try {
            currentTest.create(fdx);
            currentTest.resize(display.width(), display.height());
            status = "Running auto";
        } catch (Throwable error) {
            currentTest = null;
            currentTestName = null;
            status = "Auto failed to start: " + error.getClass().getSimpleName() + " - " + error.getMessage();
            error.printStackTrace();
        }
        if (root != null) {
            refreshChooserUi();
        }
    }

    private void disposeCurrentTest() {
        if (currentTest == null) {
            currentTestName = null;
            return;
        }
        try {
            currentTest.pause();
        } finally {
            currentTest.dispose();
            currentTest = null;
            currentTestName = null;
        }
    }

    private void requestLaunch(String testName) {
        selectTest(testName);
        pendingLaunchName = testName;
    }

    private boolean requestReturnToList() {
        if (currentTest == null) {
            return false;
        }
        pendingReturnToList = true;
        pendingReturnStatus = "Returned to test list";
        return true;
    }

    private void buildUi(UiScope ui) {
        if (currentTest != null) {
            return;
        }
        buildMenu(ui);
    }

    private void buildMenu(UiScope ui) {
        float pagePadding = compactLayout ? 8.0f : 14.0f;
        float pageGap = compactLayout ? 6.0f : 10.0f;
        scrollToSelectedTestIfNeeded();
        ui.column(Ui.modifier().fill().padding(pagePadding).gap(pageGap), page -> {
            buildMenuHeader(page);
            page.scroll(Ui.modifier().fill().weight(1.0f), testScroll, list -> {
                TestSelector.TestDescriptor[] descriptors = TestSelector.descriptors();
                for (int i = 0; i < descriptors.length; i++) {
                    final TestSelector.TestDescriptor descriptor = descriptors[i];
                    buildTestRow(list, descriptor, i);
                }
            });
        });
    }

    private void buildMenuHeader(UiScope page) {
        float padding = compactLayout ? 8.0f : 12.0f;
        float gap = compactLayout ? 5.0f : 6.0f;
        page.panel(Ui.modifier().fillWidth().padding(padding).gap(gap).style("panel-strong"), header -> {
            if (compactLayout) {
                header.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
                    row.text("Tests", Ui.modifier().style("title").weight(1.0f));
                    row.button("Auto", Ui.modifier().width(88.0f).height(34.0f).style("accent-button"),
                            () -> requestLaunch(TestSelector.AUTO_TEST_NAME));
                    row.checkbox(Ui.modifier().semanticLabel("Debug line"), debugLines);
                });
                header.text(status, Ui.modifier().style("muted"));
            } else {
                header.text("libfdx tests", Ui.modifier().style("title"));
                header.text(status, Ui.modifier().style("muted"));
                buildGraphicsSelector(header);
                header.row(Ui.modifier().gap(8.0f), row -> {
                    row.button("Auto", Ui.modifier().width(120.0f).style("accent-button"),
                            () -> requestLaunch(TestSelector.AUTO_TEST_NAME));
                    row.checkbox(Ui.modifier().semanticLabel("Debug line"), debugLines);
                    row.text("Debug line", Ui.modifier().style("muted"));
                });
            }
        });
    }

    private void buildTestRow(UiScope list, TestSelector.TestDescriptor descriptor, int index) {
        boolean selected = selectedTestIndex.get() == index;
        String style = selected ? "test-row-selected" : "test-row-button";
        list.button(descriptor.displayName(), Ui.modifier()
                .fillWidth()
                .height(TEST_ROW_HEIGHT)
                .margin(TEST_ROW_MARGIN)
                .style(style)
                .semanticLabel(descriptor.name()), () -> {
                    selectedTestIndex.set(index);
                    rememberSelectedTest(descriptor.name());
                    requestLaunch(descriptor.name());
                });
    }

    private void buildGraphicsSelector(UiScope header) {
        if (graphicsOptions.length <= 1) {
            header.text("graphics: " + graphicsDisplayName(graphicsOptions[0]), Ui.modifier().style("muted"));
            return;
        }
        header.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.text("graphics", Ui.modifier().width(84.0f).style("muted"));
            for (int i = 0; i < graphicsOptions.length; i++) {
                final int index = i;
                String graphicsName = graphicsOptions[i];
                String style = selectedGraphicsIndex.get() == index ? "selected-button" : "button";
                row.button(graphicsDisplayName(graphicsName), Ui.modifier().width(112.0f).style(style),
                        () -> selectedGraphicsIndex.set(index));
            }
        });
    }

    private boolean shouldLogSelectorFps() {
        if (currentTest != null || fpsLogger == null) {
            return false;
        }
        return launchHandler == null || !launchHandler.hasActiveLaunch();
    }

    private void refreshChooserUi() {
        if (root == null) {
            return;
        }
        root.requestCompose();
        if (currentTest != null) {
            root.rootNode();
        }
    }

    private String selectedGraphicsName() {
        int index = selectedGraphicsIndex.get();
        if (index < 0 || index >= graphicsOptions.length) {
            return graphicsOptions[0];
        }
        return graphicsOptions[index];
    }

    private void selectTest(String testName) {
        if (TestSelector.AUTO_TEST_NAME.equals(testName)) {
            return;
        }
        int index = testIndex(testName);
        if (index < 0) {
            return;
        }
        selectedTestIndex.set(index);
        rememberSelectedTest(testName);
        scrollToSelectedTestPending = true;
    }

    private void scrollToSelectedTestIfNeeded() {
        if (!scrollToSelectedTestPending || testScroll == null) {
            return;
        }
        scrollToSelectedTestPending = false;
        int index = selectedTestIndex.get();
        if (index < 0) {
            return;
        }
        float rowPitch = TEST_ROW_HEIGHT + TEST_ROW_MARGIN * 2.0f;
        float rowTop = index * rowPitch;
        testScroll.scrollYRangeIntoView(rowTop, rowTop + rowPitch, rowPitch);
    }

    private void rememberSelectedTest(String testName) {
        TestSelector.TestDescriptor descriptor = TestSelector.descriptor(testName);
        if (descriptor == null) {
            return;
        }
        System.setProperty(SELECTED_TEST_PROPERTY, descriptor.name());
        if (selectedTestStore != null) {
            selectedTestStore.putString(SELECTED_TEST_KEY, descriptor.name()).flush();
        }
    }

    private void restoreSelectedTest() {
        int propertyIndex = testIndex(System.getProperty(SELECTED_TEST_PROPERTY));
        if (propertyIndex >= 0) {
            selectedTestIndex.set(propertyIndex);
            rememberSelectedTest(TestSelector.descriptors()[propertyIndex].name());
            return;
        }
        if (selectedTestStore == null) {
            return;
        }
        String storedTestName = selectedTestStore.getString(SELECTED_TEST_KEY, "");
        int storedIndex = testIndex(storedTestName);
        if (storedIndex >= 0) {
            selectedTestIndex.set(storedIndex);
            System.setProperty(SELECTED_TEST_PROPERTY, TestSelector.descriptors()[storedIndex].name());
        }
    }

    private void syncDebugLineProperty() {
        if (debugLines != null && debugLines.get()) {
            System.setProperty("libfdx.test.uiDebugLines", "true");
        } else {
            System.clearProperty("libfdx.test.uiDebugLines");
        }
    }

    private String graphicsOptionList() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < graphicsOptions.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(graphicsOptions[i]);
        }
        return builder.toString();
    }

    private static String launchLabel(String testName) {
        if (TestSelector.AUTO_TEST_NAME.equals(testName)) {
            return "auto";
        }
        TestSelector.TestDescriptor descriptor = TestSelector.descriptor(testName);
        return descriptor != null ? descriptor.displayName() : testName;
    }

    private static String[] normalizedGraphicsOptions(String[] options) {
        if (options == null || options.length == 0) {
            return DEFAULT_GRAPHICS.clone();
        }
        String[] normalized = new String[options.length];
        int count = 0;
        for (int i = 0; i < options.length; i++) {
            String value = options[i] != null ? options[i].trim().toLowerCase() : "";
            if (value.length() == 0 || contains(normalized, count, value)) {
                continue;
            }
            normalized[count++] = value;
        }
        if (count == 0) {
            return DEFAULT_GRAPHICS.clone();
        }
        String[] result = new String[count];
        System.arraycopy(normalized, 0, result, 0, count);
        return result;
    }

    private static boolean contains(String[] values, int count, String value) {
        for (int i = 0; i < count; i++) {
            if (values[i].equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static int initialGraphicsIndex(String[] options, String initialGraphics) {
        String value = initialGraphics != null ? initialGraphics.trim().toLowerCase() : "";
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private static int initialTestIndex() {
        String selected = trim(System.getProperty(SELECTED_TEST_PROPERTY));
        int selectedIndex = testIndex(selected);
        if (selectedIndex >= 0) {
            return selectedIndex;
        }
        String requested = trim(System.getProperty("libfdx.test.name"));
        int requestedIndex = testIndex(requested);
        if (requestedIndex >= 0) {
            return requestedIndex;
        }
        int defaultIndex = testIndex(TestSelector.DEFAULT_TEST_NAME);
        return defaultIndex >= 0 ? defaultIndex : 0;
    }

    private static int testIndex(String testName) {
        if (testName == null || TestSelector.AUTO_TEST_NAME.equalsIgnoreCase(testName)
                || TestSelector.SELECTOR_NAME.equalsIgnoreCase(testName)
                || "menu".equalsIgnoreCase(testName)
                || "chooser".equalsIgnoreCase(testName)) {
            return -1;
        }
        TestSelector.TestDescriptor[] descriptors = TestSelector.descriptors();
        for (int i = 0; i < descriptors.length; i++) {
            if (descriptors[i].name().equalsIgnoreCase(testName)) {
                return i;
            }
        }
        return -1;
    }

    private void captureIfRequested() {
        if (capturePath == null || captured || renderedFrames < captureFrame) {
            return;
        }
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            FramebufferCapture.writePpm(capturePath, framebufferWidth(), framebufferHeight(), pixels);
            captured = true;
            System.out.println("[info] TestChooserApplication captured framebuffer to " + capturePath);
        } catch (Exception error) {
            throw new FdxException("Could not capture TestChooserApplication framebuffer", error);
        }
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 900;
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 740;
    }

    private static long longProperty(String name, long fallback) {
        String value = System.getProperty(name);
        if (value == null || value.trim().length() == 0) {
            return fallback;
        }
        return Long.parseLong(value.trim());
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 0 ? trimmed : null;
    }

    static String graphicsDisplayName(String graphicsName) {
        if ("gl".equalsIgnoreCase(graphicsName)) {
            return "GL";
        }
        if ("gles".equalsIgnoreCase(graphicsName)) {
            return "GLES";
        }
        if ("webgl".equalsIgnoreCase(graphicsName)) {
            return "WebGL";
        }
        if ("webgpu".equalsIgnoreCase(graphicsName) || "wgpu".equalsIgnoreCase(graphicsName)) {
            return "WGPU";
        }
        if ("vulkan".equalsIgnoreCase(graphicsName) || "vk".equalsIgnoreCase(graphicsName)) {
            return "Vulkan";
        }
        if ("d3d12".equalsIgnoreCase(graphicsName)
                || "direct3d12".equalsIgnoreCase(graphicsName)
                || "directx12".equalsIgnoreCase(graphicsName)
                || "dx12".equalsIgnoreCase(graphicsName)) {
            return "Direct3D 12";
        }
        return graphicsName;
    }

    private static UiTheme theme(boolean compact) {
        float textSize = compact ? 14.0f : 16.0f;
        float textLineHeight = compact ? 18.0f : 21.0f;
        float titleSize = compact ? 21.0f : 26.0f;
        float titleLineHeight = compact ? 26.0f : 32.0f;
        float sectionSize = compact ? 16.0f : 18.0f;
        float sectionLineHeight = compact ? 20.0f : 23.0f;
        float mutedSize = compact ? 12.0f : 14.0f;
        float mutedLineHeight = compact ? 15.0f : 18.0f;
        UiTextStyle text = UiTextStyle.text()
                .font(UiFont.freeType(FREETYPE_FONT_ASSET, textSize))
                .size(textSize)
                .lineHeight(textLineHeight)
                .color(UiColor.rgba8888(0xf2f4f8ff));
        UiTextStyle title = UiTextStyle.text()
                .font(UiFont.freeType(FREETYPE_FONT_ASSET, titleSize))
                .size(titleSize)
                .lineHeight(titleLineHeight)
                .color(UiColor.rgba8888(0xffffffff));
        UiTextStyle section = UiTextStyle.text()
                .font(UiFont.freeType(FREETYPE_FONT_ASSET, sectionSize))
                .size(sectionSize)
                .lineHeight(sectionLineHeight)
                .color(UiColor.rgba8888(0xffffffff));
        UiTextStyle muted = UiTextStyle.text()
                .font(UiFont.freeType(FREETYPE_FONT_ASSET, mutedSize))
                .size(mutedSize)
                .lineHeight(mutedLineHeight)
                .color(UiColor.rgba8888(0xaeb7c4ff));
        UiTextStyle buttonText = text.align(UiTextAlign.CENTER).wrap(false).ellipsis(true);
        UiTextStyle listButtonText = text.align(UiTextAlign.START).ellipsis(true).wrap(false);
        UiTextStyle selectedListButtonText = listButtonText.color(UiColor.rgba8888(0xffffffff));
        UiStyle button = UiStyle.button()
                .text(buttonText)
                .background(UiDrawable.color(UiColor.rgba8888(0x2f4052ff)))
                .hover(UiStyle.button().text(buttonText).background(UiDrawable.color(UiColor.rgba8888(0x3d5066ff))))
                .pressed(UiStyle.button().text(buttonText).background(UiDrawable.color(UiColor.rgba8888(0x263544ff))));
        UiStyle selected = UiStyle.button()
                .text(buttonText)
                .background(UiDrawable.color(UiColor.rgba8888(0x3f6fb6ff)));
        UiStyle accent = UiStyle.button()
                .text(buttonText)
                .background(UiDrawable.color(UiColor.rgba8888(0x4b7f52ff)));
        UiStyle testRow = UiStyle.button()
                .padding(14.0f, 7.0f)
                .text(listButtonText)
                .background(UiDrawable.color(UiColor.rgba8888(0x101820ff)))
                .hover(UiStyle.button()
                        .padding(14.0f, 7.0f)
                        .text(listButtonText)
                        .background(UiDrawable.color(UiColor.rgba8888(0x182637ff))))
                .pressed(UiStyle.button()
                        .padding(14.0f, 7.0f)
                        .text(listButtonText)
                        .background(UiDrawable.color(UiColor.rgba8888(0x0d151fff))));
        UiStyle selectedTestRow = UiStyle.button()
                .padding(14.0f, 7.0f)
                .text(selectedListButtonText)
                .background(UiDrawable.color(UiColor.rgba8888(0x315f9eff)))
                .hover(UiStyle.button()
                        .padding(14.0f, 7.0f)
                        .text(selectedListButtonText)
                        .background(UiDrawable.color(UiColor.rgba8888(0x3f71b9ff))))
                .pressed(UiStyle.button()
                        .padding(14.0f, 7.0f)
                        .text(selectedListButtonText)
                        .background(UiDrawable.color(UiColor.rgba8888(0x274d83ff))));
        return Ui.darkTheme()
                .colors(UiColor.rgba8888(0x0d1117ff), UiColor.rgba8888(0xf2f4f8ff))
                .text(UiStyle.style().text(text))
                .button(button)
                .style("button", button)
                .style("selected-button", selected)
                .style("accent-button", accent)
                .style("panel-strong", UiStyle.style()
                        .padding(12.0f)
                        .background(UiDrawable.color(UiColor.rgba8888(0x161b22ff))))
                .style("test-row-button", testRow)
                .style("test-row-selected", selectedTestRow)
                .style("title", UiStyle.style().text(title))
                .style("section", UiStyle.style().text(section))
                .style("muted", UiStyle.style().text(muted));
    }
}
