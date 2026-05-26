package io.github.libfdx.tests.ui;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.input.Cursor;
import io.github.libfdx.input.DefaultCursor;
import io.github.libfdx.input.DefaultGamepads;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.DefaultInputCapabilities;
import io.github.libfdx.input.Gamepads;
import io.github.libfdx.input.Input;
import io.github.libfdx.input.InputCapabilities;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.TextInputController;
import io.github.libfdx.input.TextInputRequest;
import io.github.libfdx.input.TextInputType;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiAlign;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiFloatState;
import io.github.libfdx.ui.UiFloatAnimatable;
import io.github.libfdx.ui.UiFont;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiListState;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiNinePatch;
import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiNodeType;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScrollState;
import io.github.libfdx.ui.UiSize;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTextStyle;
import io.github.libfdx.ui.UiTextAreaOptions;
import io.github.libfdx.ui.UiTheme;
import io.github.libfdx.ui.UiToolkit;
import io.github.libfdx.ui.UiTextInputFilter;
import io.github.libfdx.ui.UiWindowState;
import io.github.libfdx.tests.TestFpsLogger;
import io.github.libfdx.validation.scenario.Scenario;
import io.github.libfdx.validation.scenario.ScenarioActions;
import io.github.libfdx.validation.scenario.ScenarioCapture;
import io.github.libfdx.validation.scenario.ScenarioContext;
import io.github.libfdx.validation.scenario.ScenarioHost;
import io.github.libfdx.validation.scenario.ScenarioInputDriver;
import io.github.libfdx.validation.scenario.ScenarioResult;
import io.github.libfdx.validation.scenario.ScenarioValidationConfig;
import io.github.libfdx.validation.scenario.ui.kit.UiScenarioActions;
import io.github.libfdx.validation.scenario.ui.kit.UiScenarioAssertions;
import io.github.libfdx.validation.scenario.ui.kit.UiScenarioTargets;
import io.github.libfdx.validation.scenario.ui.kit.UiScenarioWaits;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public final class UiKitTest extends ApplicationAdapter {
    private static final String LOGO_ASSET = "fdx_logo_dark.png";
    private static final String PATCH_ASSET = "ui_panel_patch.png";
    private static final String FREETYPE_FONT_ASSET = "font/freetype/lsans.ttf";
    private static final String REQUESTED_SCENARIO = "requested";
    private static final float TOOLS_INITIAL_X = 0.0f;
    private static final float TOOLS_INITIAL_Y = 430.0f;
    private static final float STATS_INITIAL_X = 575.0f;
    private static final float STATS_INITIAL_Y = 430.0f;
    private static final float STATS_INITIAL_WIDTH = 270.0f;
    private static final float STATS_INITIAL_HEIGHT = 170.0f;
    private static final float DEFAULT_SAFE_AREA = 0.0f;
    private static final float DEFAULT_CHECKBOX_SIZE = 20.0f;
    private static final float COMPACT_CHECKBOX_SIZE = 18.0f;
    private static final float UI_SCALE_INITIAL = 1.0f;
    private static final float UI_SCALE_MINIMUM = 0.75f;
    private static final float UI_SCALE_MAXIMUM = 2.25f;
    private static final float DRAWING_TEXT_SIZE = 22.0f;
    private static final float DRAWING_TEXT_MINIMUM = 12.0f;
    private static final float DRAWING_TEXT_MAXIMUM = 52.0f;
    private static final int DEFAULT_VALIDATION_FRAMES = 180;
    private static final float VALIDATION_SETTLE_SECONDS = 0.25f;
    private static final long VALIDATION_WAIT_FRAME_MILLIS = 100L;
    private static final int VISUAL_CHANNEL_TOLERANCE = 8;
    private static final float VISUAL_MISMATCH_RATIO = 0.01f;
    private static final String DEFAULT_CAPTURE_TEMPLATE = "build/reports/uikit/{scenario}-{frame}.png";
    private static final String DEFAULT_VISUAL_BASELINE_DIR = "tests/assets/ui-baselines";
    private static final String DEFAULT_VISUAL_BASELINE_TEMPLATE = "{scenario}.png";
    private static final int SECTION_BUTTONS = 0;
    private static final int SECTION_CHECKBOXES = 1;
    private static final int SECTION_SLIDERS = 2;
    private static final int SECTION_PROGRESS_BARS = 3;
    private static final int SECTION_TABS = 4;
    private static final int SECTION_TEXT_FIELDS = 5;
    private static final int SECTION_LISTS = 6;
    private static final int SECTION_DRAWING = 7;
    private static final int SECTION_WINDOWS = 8;
    private static final int SECTION_SCROLLVIEW = 9;
    private static final int SECTION_OVERLAYS = 10;
    private static final int SECTION_TOOLTIPS = 11;
    private static final String[] SECTIONS = new String[] {
            "Buttons",
            "Checkboxes",
            "Sliders",
            "Progress bars",
            "Tabs",
            "Text fields",
            "Lists",
            "Drawing",
            "Windows",
            "Scroll view",
            "Overlays",
            "Tooltips"
    };
    private static final String[] ACTIVITY = new String[] {
            "preload assets",
            "hover row",
            "focus input",
            "drag slider",
            "submit text"
    };
    private static final String[] INVENTORY = new String[] {
            "med kit",
            "blue key",
            "map",
            "torch",
            "part",
            "flare"
    };
    private long exitAfterFrames;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private AssetManager assets;
    private Logger logger;
    private UiRoot root;
    private DefaultInput input;
    private UiIntState clickCount;
    private UiBooleanState checked;
    private UiBooleanState visualDebug;
    private UiBooleanState showDetails;
    private UiBooleanState showModal;
    private UiBooleanState showPopup;
    private UiBooleanState blockPopupInput;
    private UiBooleanState scaleGlobally;
    private UiBooleanState compactChecked;
    private UiBooleanState bareChecked;
    private UiBooleanState disabledChecked;
    private UiFloatState uiScale;
    private UiFloatState drawingTextSize;
    private UiFloatState volume;
    private UiFloatState sliderNormal;
    private UiFloatState sliderCompact;
    private UiFloatState sliderWide;
    private UiFloatState sliderNegative;
    private UiFloatState sliderDisabled;
    private UiFloatState sliderTiny;
    private UiFloatState sliderPrecise;
    private UiIntState activeSection;
    private UiIntState activeDemoTab;
    private UiState<String> name;
    private UiState<String> intInput;
    private UiState<String> floatInput;
    private UiState<String> messageInput;
    private UiState<String> growingMessageInput;
    private UiScrollState navScroll;
    private UiScrollState settingsScroll;
    private UiScrollState[] sectionScrolls;
    private UiScrollState feedScroll;
    private UiScrollState mediumScroll;
    private UiListState inventoryList;
    private UiWindowState toolsWindow;
    private UiWindowState statsWindow;
    private TextureRegion logoRegion;
    private TextureRegion panelPatchRegion;
    private String capturePath;
    private long captureFrame;
    private boolean captured;
    private boolean created;
    private boolean windowMoveChecked;
    private boolean windowResizeChecked;
    private boolean edgeMoveChecked;
    private boolean edgeResizeChecked;
    private boolean textScaleReleaseCommitChecked;
    private float toolsInitialX;
    private float toolsInitialY;
    private float toolsInitialWidth;
    private float toolsInitialHeight;
    private float statsInitialX;
    private float statsInitialY;
    private float statsInitialWidth;
    private float statsInitialHeight;
    private float initialUiScale;
    private boolean checkboxAlignmentChecked;
    private boolean popupPassthroughChecked;
    private boolean popupBlockingChecked;
    private boolean progressBarChecked;
    private boolean tabsChecked;
    private boolean textInputSessionChecked;
    private boolean textSelectionChecked;
    private boolean textAreaChecked;
    private boolean textInputTouchDragChecked;
    private boolean scrollBodyDragChecked;
    private boolean scrollChildDragChecked;
    private boolean initialVisualDebug;
    private boolean scaleGloballyActive;
    private boolean driveAutomation;
    private String hoverLabel;
    private String captureTemplate;
    private String baselineDirectory;
    private String baselineTemplate;
    private boolean visualCaptureAllScenarios;
    private boolean captureOutputRequested;
    private int captureEveryFrames;
    private int visualChannelTolerance;
    private float visualMismatchRatio;
    private boolean visualValidate;
    private boolean visualValidateRequireBaselines;
    private boolean validationActive;
    private boolean validationEnabled;
    private boolean desktopImageCaptureEnabled;
    private TestFpsLogger fpsLogger;
    private float uiPerfLogSeconds;
    private float uiPerfElapsedSeconds;
    private long uiPerfFrames;
    private long uiPerfUpdateNanos;
    private long uiPerfRenderNanos;
    private long uiPerfFrameNanos;
    private ScenarioValidationConfig validationConfig;
    private float validationElapsedSeconds;
    private float nextValidationScenarioSeconds;
    private RuntimeException renderFailure;
    private long renderedFrames;
    private ScenarioHost scenarioHost;
    private UiKitValidationScenarios.Entry[] validationScenarios;
    private int nextValidationScenario;
    private RecordingTextInputController textInputController;
    private final LinkedHashMap<String, Integer> pendingValidationCaptures = new LinkedHashMap<>();

    public UiKitTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        logger = fdx.logger();
        exitAfterFrames = Math.max(0L, exitAfterFrames);
        String configuredCaptureTemplate = trimOrEmpty(System.getProperty("libfdx.test.capture"));
        captureOutputRequested = configuredCaptureTemplate != null && configuredCaptureTemplate.length() > 0;
        captureTemplate = captureOutputRequested ? configuredCaptureTemplate : DEFAULT_CAPTURE_TEMPLATE;
        if (captureTemplate == null) {
            captureTemplate = DEFAULT_CAPTURE_TEMPLATE;
        }
        baselineDirectory = trimOrEmpty(System.getProperty("libfdx.test.visualBaselineDir"));
        if (baselineDirectory == null || baselineDirectory.length() == 0) {
            baselineDirectory = DEFAULT_VISUAL_BASELINE_DIR;
        }
        baselineTemplate = trimOrEmpty(System.getProperty("libfdx.test.visualBaselineTemplate"));
        if (baselineTemplate == null || baselineTemplate.length() == 0) {
            baselineTemplate = DEFAULT_VISUAL_BASELINE_TEMPLATE;
        }
        visualValidate = Boolean.parseBoolean(System.getProperty("libfdx.test.visualValidate", "false"));
        visualValidateRequireBaselines = Boolean.parseBoolean(System.getProperty("libfdx.test.visualRequireBaselines",
                String.valueOf(false)));
        visualCaptureAllScenarios = Boolean.parseBoolean(System.getProperty("libfdx.test.visualCaptureAllScenarios",
                String.valueOf(false)));
        captureEveryFrames = Integer.parseInt(System.getProperty("libfdx.test.captureEvery", "0"));
        if (captureEveryFrames < 0) {
            captureEveryFrames = 0;
        }
        visualChannelTolerance = Integer.parseInt(System.getProperty("libfdx.test.visualChannelTolerance",
                String.valueOf(VISUAL_CHANNEL_TOLERANCE)));
        visualMismatchRatio = Float.parseFloat(System.getProperty("libfdx.test.visualMismatchRatio",
                String.valueOf(VISUAL_MISMATCH_RATIO)));
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "0"));
        capturePath = captureTemplate;
        driveAutomation = Boolean.parseBoolean(System.getProperty("libfdx.test.driveInput", "false"));
        hoverLabel = System.getProperty("libfdx.test.hoverLabel", "");
        validationEnabled = Boolean.parseBoolean(System.getProperty("libfdx.test.validate", "true"));
        fpsLogger = TestFpsLogger.create(logger, "UiKitTest");
        uiPerfLogSeconds = Float.parseFloat(System.getProperty("libfdx.test.uiPerfLogSeconds", "0"));
        desktopImageCaptureEnabled = desktopImageCaptureAvailable();
        if (!desktopImageCaptureEnabled) {
            visualValidate = false;
            visualCaptureAllScenarios = false;
            captureOutputRequested = false;
            captureEveryFrames = 0;
        }
        visualValidate = validationEnabled && visualValidate;
        validationActive = validationEnabled && driveAutomation;
        validationConfig = ScenarioValidationConfig.fromSystemProperties();

        assets = new DefaultAssetManager(fdx.files());
        G2DAssetLoaders.register(assets, graphics);
        assets.load(AssetDescriptor.of(LOGO_ASSET, Texture.class));
        assets.load(AssetDescriptor.of(PATCH_ASSET, Texture.class));
        assets.finishLoading();

        Texture logo = assets.get(LOGO_ASSET, Texture.class);
        Texture patch = assets.get(PATCH_ASSET, Texture.class);
        logoRegion = new TextureRegion(logo);
        panelPatchRegion = new TextureRegion(patch);

        Input backendInput = fdx.input();
        Input rootInput;
        if (validationActive) {
            textInputController = new RecordingTextInputController();
            input = new DefaultInput(ProviderId.of("uikit_validation_input"), inputCapabilities(backendInput),
                    inputCursor(backendInput), inputGamepads(backendInput), textInputController);
            rootInput = input;
        } else {
            input = backendInput instanceof DefaultInput ? backendInput.<DefaultInput>as() : new DefaultInput();
            rootInput = backendInput instanceof DefaultInput ? backendInput : input;
        }

        clickCount = Ui.state(0);
        checked = Ui.state(false);
        initialVisualDebug = Boolean.parseBoolean(System.getProperty("libfdx.test.uiDebugLines", "false"));
        visualDebug = Ui.state(initialVisualDebug);
        showDetails = Ui.state(true);
        showModal = Ui.state(false);
        showPopup = Ui.state(false);
        blockPopupInput = Ui.state(false);
        scaleGlobally = Ui.state(Boolean.parseBoolean(System.getProperty("libfdx.test.scaleGlobally", "false")));
        compactChecked = Ui.state(true);
        bareChecked = Ui.state(false);
        disabledChecked = Ui.state(true);
        initialUiScale = Float.parseFloat(System.getProperty("libfdx.test.uiScale",
                String.valueOf(UI_SCALE_INITIAL)));
        uiScale = Ui.state(initialUiScale);
        drawingTextSize = Ui.state(DRAWING_TEXT_SIZE);
        volume = Ui.state(0.35f);
        sliderNormal = Ui.state(0.45f);
        sliderCompact = Ui.state(0.60f);
        sliderWide = Ui.state(25.0f);
        sliderNegative = Ui.state(0.0f);
        sliderDisabled = Ui.state(0.70f);
        sliderTiny = Ui.state(0.30f);
        sliderPrecise = Ui.state(0.125f);
        activeSection = Ui.state(initialSection());
        activeDemoTab = Ui.state(0);
        name = Ui.state("Player");
        intInput = Ui.state("42");
        floatInput = Ui.state("0.75");
        messageInput = Ui.state("Line 1 message\nLine 2 with more text\nLine 3 keeps the body scrollable\n"
                + "Line 4 still inside the area\nLine 5 validates clipping\nLine 6 remains reachable");
        growingMessageInput = Ui.state("Short message\nsecond line");
        navScroll = new UiScrollState();
        settingsScroll = new UiScrollState();
        sectionScrolls = new UiScrollState[SECTIONS.length];
        for (int i = 0; i < sectionScrolls.length; i++) {
            sectionScrolls[i] = new UiScrollState();
        }
        feedScroll = new UiScrollState();
        mediumScroll = new UiScrollState();
        feedScroll.scrollTo(0.0f, 8.0f);
        inventoryList = new UiListState();
        inventoryList.scrollToItem(1, 0.0f);
        boolean compactWindows = compactViewport();
        toolsInitialX = compactWindows ? 0.0f : TOOLS_INITIAL_X;
        toolsInitialY = compactWindows ? 0.0f : TOOLS_INITIAL_Y;
        toolsInitialWidth = compactWindows ? 240.0f : 260.0f;
        toolsInitialHeight = compactWindows ? 170.0f : 190.0f;
        statsInitialX = compactWindows ? 80.0f : STATS_INITIAL_X;
        statsInitialY = compactWindows ? 80.0f : STATS_INITIAL_Y;
        statsInitialWidth = compactWindows ? 240.0f : STATS_INITIAL_WIDTH;
        statsInitialHeight = compactWindows ? 170.0f : STATS_INITIAL_HEIGHT;
        toolsWindow = new UiWindowState(toolsInitialX, toolsInitialY, toolsInitialWidth, toolsInitialHeight)
                .minSize(240.0f, 170.0f);
        statsWindow = new UiWindowState(statsInitialX, statsInitialY, statsInitialWidth, statsInitialHeight)
                .minSize(240.0f, 170.0f);

        root = new UiToolkit(fdx.files())
                .theme(theme())
                .root(display, graphics)
                .input(rootInput)
                .safeArea(Ui.insets(Float.parseFloat(System.getProperty("libfdx.test.safeArea",
                        String.valueOf(DEFAULT_SAFE_AREA)))))
                .autoUiScale(Boolean.parseBoolean(System.getProperty("libfdx.test.autoUiScale", "false")))
                .debugLines(visualDebug.get());
        scaleGloballyActive = scaleGlobally.get();
        if (scaleGloballyActive) {
            root.uiScale(uiScale.get());
        }
        root.setContent(this::buildUi);
        scenarioHost = ScenarioHost.create()
                .frameDeltaMillis(VALIDATION_WAIT_FRAME_MILLIS)
                .frameDriver(context -> {
                    root.update(VALIDATION_WAIT_FRAME_MILLIS / 1000.0f);
                    root.rootNode();
                })
                .inputDriver(new UiKitScenarioInputDriver())
                .captureDriver((name, context) -> {
                    queueValidationCapture(name, true, context.scenario().requiresVisualBaseline());
                    String path = desktopImageCaptureEnabled
                            ? resolveCapturePath(captureTemplate, safeScenarioName(name), renderedFrames)
                            : null;
                    return new ScenarioCapture(name, context.frame(), context.elapsedMillis(), path);
                })
                .registerProbe(UiRoot.class, root);
        UiKitValidationScenarios.Plan scenarioPlan = buildValidationScenarios();
        validationScenarios = scenarioPlan.entries();
        applyValidationFrameBudget();
        created = true;
        logger.info("UiKitTest created. Validation mode="
                + (validationActive ? "AUTOMATION" : "IDLE")
                + ", frames=" + exitAfterFrames
                + ", driveInput=" + driveAutomation
                + ", stepDelaySeconds=" + validationConfig.stepDelaySeconds()
                + ", visualValidate=" + visualValidate
                + ", capture=" + captureTemplate);
    }

    private InputCapabilities inputCapabilities(Input input) {
        return input != null && input.capabilities() != null
                ? input.capabilities()
                : DefaultInputCapabilities.desktop();
    }

    private Cursor inputCursor(Input input) {
        return input != null && input.cursor() != null ? input.cursor() : new DefaultCursor();
    }

    private Gamepads inputGamepads(Input input) {
        return input != null && input.gamepads() != null ? input.gamepads() : new DefaultGamepads();
    }

    @Override
    public void resize(int width, int height) {
        if (root != null) {
            root.resize(width, height);
        }
    }

    @Override
    public void render() {
        if (assets != null) {
            assets.update();
        }
        graphics.clear(0.045f, 0.052f, 0.066f, 1.0f);
        float deltaSeconds = 0.0f;
        try {
            applyGlobalScale();
            if (validationActive && !desktopImageCaptureEnabled) {
                executeValidationPlan();
            }
            deltaSeconds = application.deltaTime();
            if (validationActive) {
                validationElapsedSeconds += Math.max(0.0f, deltaSeconds);
            }
            boolean trackUiPerf = uiPerfLogSeconds > 0.0f;
            long updateStart = trackUiPerf ? System.nanoTime() : 0L;
            root.update(deltaSeconds);
            long updateEnd = trackUiPerf ? System.nanoTime() : 0L;
            if (validationActive && desktopImageCaptureEnabled) {
                executeValidationPlan();
            } else if (exitAfterFrames > 0L) {
                driveCaptureInput();
            }
            root.debugLines(visualDebug.get());
            long renderStart = trackUiPerf ? System.nanoTime() : 0L;
            root.render();
            if (trackUiPerf) {
                long renderEnd = System.nanoTime();
                recordUiPerf(deltaSeconds, updateEnd - updateStart, renderEnd - renderStart, renderEnd - updateStart);
            }
            captureValidationPostRender();
        } catch (RuntimeException error) {
            renderFailure = error;
            throw error;
        }

        renderedFrames++;
        fpsLogger.frame(deltaSeconds, renderedFrames);
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames && validationCanExit()) {
            application.requestExit();
        }
    }

    @Override
    public void onFrameEnd() {
    }

    private void captureValidationPostRender() {
        flushPendingValidationCaptures();
        captureRequestedFrame();
        if (captureEveryFrames > 0 && shouldCaptureEveryFrame()) {
            queueValidationCapture("capture-every-" + renderedFrames, true, false);
        }
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.dispose();
            root = null;
        }
        if (assets != null) {
            assets.dispose();
            assets = null;
        }
        if (renderFailure != null) {
            throw new FdxException("UiKitTest failed during render", renderFailure);
        }
        if (!created) {
            throw new FdxException("UiKitTest did not create UI resources");
        }
        if (validationActive) {
            if (nextValidationScenario < validationScenarios.length) {
                throw new FdxException("UiKitTest did not execute all validation scenarios: expected="
                        + validationScenarios.length + ", executed=" + nextValidationScenario
                        + ", frames=" + renderedFrames);
            }
            if (clickCount.get() <= 0) {
                throw new FdxException("UiKitTest button callback was not invoked by input dispatch");
            }
            if (!checked.get()) {
                throw new FdxException("UiKitTest checkbox callback was not invoked by input dispatch");
            }
            if (!checkboxAlignmentChecked) {
                throw new FdxException("UiKitTest did not check checkbox label alignment");
            }
            if (!popupPassthroughChecked) {
                throw new FdxException("UiKitTest did not check non-blocking popup input pass-through");
            }
            if (!popupBlockingChecked) {
                throw new FdxException("UiKitTest did not check blocking popup input capture");
            }
            if (!name.get().endsWith("!")) {
                throw new FdxException("UiKitTest text input dispatch did not update the text field");
            }
            if (!"427".equals(intInput.get())) {
                throw new FdxException("UiKitTest integer text field accepted non-integer input: " + intInput.get());
            }
            if (!"0.755".equals(floatInput.get())) {
                throw new FdxException("UiKitTest float text field accepted non-float input: " + floatInput.get());
            }
            if (volume.get() < 0.95f) {
                throw new FdxException("UiKitTest slider drag did not keep updating after leaving widget bounds");
            }
            if (scaleGlobally.get() && Math.abs(uiScale.get() - initialUiScale) < 0.01f) {
                throw new FdxException("UiKitTest text size setting did not change the global UI scale");
            }
            if (!textScaleReleaseCommitChecked) {
                throw new FdxException("UiKitTest did not verify touch text-size scaling commits on release");
            }
            if (!progressBarChecked) {
                throw new FdxException("UiKitTest did not validate progress bar nodes");
            }
            if (!tabsChecked) {
                throw new FdxException("UiKitTest did not validate tab switching");
            }
            if (!textInputSessionChecked) {
                throw new FdxException("UiKitTest did not validate text input session requests");
            }
            if (!textSelectionChecked) {
                throw new FdxException("UiKitTest did not validate text selection and copy/paste");
            }
            if (!textAreaChecked) {
                throw new FdxException("UiKitTest did not validate text area input, scroll, and auto-grow bounds");
            }
            if (!textInputTouchDragChecked) {
                throw new FdxException("UiKitTest did not validate text input touch drag scrolling");
            }
            if (!scrollBodyDragChecked) {
                throw new FdxException("UiKitTest did not validate scroll view body drag scrolling");
            }
            if (!scrollChildDragChecked) {
                throw new FdxException("UiKitTest did not validate scroll view child drag scrolling");
            }
            if (!windowMoveChecked) {
                throw new FdxException("UiKitTest movable window did not move after title-bar drag");
            }
            if (!windowResizeChecked) {
                throw new FdxException("UiKitTest resizable window did not resize from the corner handle");
            }
            if (statsWindow.zOrder() <= toolsWindow.zOrder()) {
                throw new FdxException("UiKitTest resizable window did not render above the overlapped tools window");
            }
            if (!edgeMoveChecked) {
                throw new FdxException("UiKitTest did not run the edge-bounded window move check after "
                        + renderedFrames + " rendered frames; tools=" + toolsWindow.x() + "," + toolsWindow.y()
                        + " " + toolsWindow.width() + "x" + toolsWindow.height());
            }
            if (!edgeResizeChecked) {
                throw new FdxException("UiKitTest did not run the edge-bounded window resize check after "
                        + renderedFrames + " rendered frames; stats=" + statsWindow.x() + "," + statsWindow.y()
                        + " " + statsWindow.width() + "x" + statsWindow.height());
            }
            if (captureOutputRequested && captureFrame <= renderedFrames && !captured) {
                throw new FdxException("UiKitTest did not capture a validation frame");
            }
            if (renderedFrames < exitAfterFrames) {
                throw new FdxException("UiKitTest rendered " + renderedFrames + " of " + exitAfterFrames
                        + " required frames");
            }
        }
        if (logger != null) {
            logger.info("UiKitTest rendered " + renderedFrames + " frames");
        }
    }

    private void applyGlobalScale() {
        if (root == null || scaleGlobally == null || uiScale == null) {
            return;
        }
        boolean currentlyGlobal = scaleGlobally.get();
        if (scaleGloballyActive != currentlyGlobal) {
            if (!currentlyGlobal) {
                root.uiScale(1.0f);
            } else {
                root.uiScale(uiScale.get());
            }
            scaleGloballyActive = currentlyGlobal;
            return;
        }
        if (!scaleGloballyActive) {
            return;
        }
        if (input != null && input.isMouseButtonPressed(MouseButton.LEFT)) {
            return;
        }
        scaleGloballyActive = currentlyGlobal;
        if (Math.abs(root.uiScale() - uiScale.get()) > 0.0001f) {
            root.uiScale(uiScale.get());
        }
    }

    private void buildUi(UiScope ui) {
        final float currentVolume = volume.get();
        final UiFloatAnimatable animatedVolume = ui.floatAnimatable("volume-meter", currentVolume);
        animatedVolume.animateTo(currentVolume, Ui.animation().durationMillis(180));
        final float drawnVolume = animatedVolume.get();
        final float bodyGap = 10.0f;
        final float navWidth = 188.0f;
        final float settingsWidth = 270.0f;

        ui.column(Ui.modifier().fill().gap(10.0f), page -> {
            buildHeader(page);
            page.row(Ui.modifier().fill().weight(1.0f).gap(bodyGap), body -> {
                buildSectionNav(body, navWidth);
                body.panel(Ui.modifier().fill().weight(1.0f).padding(12.0f).gap(10.0f).style("section-panel"),
                        content -> content.scrollView(Ui.modifier().fill().weight(1.0f)
                                        .semanticLabel("Active section"),
                                activeSectionScroll(),
                                scroll -> buildActiveSection(scroll, drawnVolume)));
                buildSettingsPanel(body, settingsWidth);
            });
            buildFloatingWindows(page, drawnVolume);
            buildOverlays(page);
        });
    }

    private void buildHeader(UiScope page) {
        page.panel(Ui.modifier().fillWidth().height(62.0f).padding(12.0f).gap(6.0f).style("nine-panel"),
                header -> {
                    header.row(Ui.modifier().fillWidth().gap(10.0f), row -> {
                        row.text("UI KIT", Ui.modifier().width(96.0f).style("title"));
                        row.text(activeSectionName() + "  |  " + statusText(),
                                Ui.modifier().fillWidth().weight(1.0f).style("muted"));
                        row.button("Press", () -> incrementClick());
                        row.button("Modal",
                                Ui.modifier().validationId(UiKitValidationScenarios.HEADER_MODAL_BUTTON),
                                () -> showModal.set(true));
                        row.button("Popup", () -> showPopup.toggle());
                    });
                });
    }

    private void buildSectionNav(UiScope body, float width) {
        body.panel(Ui.modifier().width(width).fillHeight().padding(10.0f).gap(6.0f).style("nav-panel"), nav -> {
            nav.scrollView(Ui.modifier().fill().weight(1.0f).semanticLabel("Sections nav"), navScroll, scroll -> {
                scroll.text("sections", Ui.modifier().style("section"));
                for (int i = 0; i < SECTIONS.length; i++) {
                    final int section = i;
                    String label = SECTIONS[i];
                    UiModifier modifier = Ui.modifier().fillWidth();
                    if (activeSection.get() == section) {
                        modifier = modifier.style("nav-active");
                    }
                    scroll.button(label, modifier, () -> activeSection.set(section));
                }
                if (validationActive) {
                    for (int i = 1; i <= 48; i++) {
                        scroll.text("validation spacer " + i, Ui.modifier().style("small"));
                    }
                }
            });
        });
    }

    private void buildSettingsPanel(UiScope body, float width) {
        body.panel(Ui.modifier().width(width).fillHeight().padding(10.0f).gap(8.0f).style("nav-panel"), settings -> {
            settings.scrollView(Ui.modifier().fill().weight(1.0f), settingsScroll, scroll -> {
                scroll.text("settings", Ui.modifier().style("section"));
                scroll.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
                    row.text("Content scale", Ui.modifier().fillWidth().weight(1.0f).style("muted"));
                    row.text(contentScaleValue(), Ui.modifier().width(64.0f).style("metric"));
                });
                checkboxLabel(scroll, "Visual debug", visualDebug, COMPACT_CHECKBOX_SIZE);
                checkboxLabel(scroll, "Scale globally", scaleGlobally);
                if (compactViewport()) {
                    scroll.slider(Ui.modifier().fillWidth().enabled(scaleGlobally.get())
                                    .semanticLabel("Text size setting")
                                    .validationId(UiKitValidationScenarios.SETTINGS_TEXT_SIZE_SLIDER),
                            uiScale, UI_SCALE_MINIMUM, UI_SCALE_MAXIMUM);
                } else {
                    scroll.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
                        row.text("Text size", Ui.modifier().width(78.0f).style("muted").enabled(scaleGlobally.get()));
                        row.slider(Ui.modifier().fillWidth().weight(1.0f).enabled(scaleGlobally.get())
                                        .semanticLabel("Text size setting")
                                        .validationId(UiKitValidationScenarios.SETTINGS_TEXT_SIZE_SLIDER),
                                uiScale, UI_SCALE_MINIMUM, UI_SCALE_MAXIMUM);
                        row.text(scaleValue(uiScale.get()), Ui.modifier().width(48.0f).style("metric").enabled(scaleGlobally.get()));
                    });
                }
            });
        });
    }

    private void buildActiveSection(UiScope content, float drawnVolume) {
        int section = activeSection.get();
        if (section == SECTION_BUTTONS) {
            buildButtonSection(content);
        } else if (section == SECTION_CHECKBOXES) {
            buildCheckboxSection(content);
        } else if (section == SECTION_SLIDERS) {
            buildSliderSection(content);
        } else if (section == SECTION_PROGRESS_BARS) {
            buildProgressBarSection(content);
        } else if (section == SECTION_TABS) {
            buildTabsSection(content);
        } else if (section == SECTION_TEXT_FIELDS) {
            buildTextFieldSection(content);
        } else if (section == SECTION_LISTS) {
            buildListSection(content);
        } else if (section == SECTION_DRAWING) {
            buildDrawingSection(content, drawnVolume);
        } else if (section == SECTION_WINDOWS) {
            buildWindowSection(content);
        } else if (section == SECTION_SCROLLVIEW) {
            buildScrollViewSection(content);
        } else if (section == SECTION_OVERLAYS) {
            buildOverlaySection(content);
        } else {
            buildTooltipSection(content);
        }
    }

    private void buildButtonSection(UiScope content) {
        content.text("Buttons", Ui.modifier().style("title"));
        content.text("Variants", Ui.modifier().style("muted"));
        content.row(Ui.modifier().fillWidth().gap(12.0f), row -> {
            row.button("Press", Ui.modifier().validationId(UiKitValidationScenarios.BUTTON_PRESS),
                    () -> incrementClick());
            row.button("Details", () -> showDetails.toggle());
            row.button("Disabled", Ui.modifier().enabled(false), () -> incrementClick());
        });
        content.spacer(Ui.modifier().height(8.0f));
        content.row(Ui.modifier().fillWidth().gap(12.0f), row -> {
            row.button("Wide action", Ui.modifier().fillWidth().weight(1.0f), () -> incrementClick());
            row.button("Compact", Ui.modifier().width(104.0f), () -> incrementClick());
            row.button("Popup", () -> showPopup.toggle());
        });
        content.animateContentSize(Ui.modifier().fillWidth().gap(4.0f), Ui.animation().durationMillis(160),
                animated -> animated.animatedVisibility(showDetails.get(),
                        Ui.animation().durationMillis(160).fade().slideY(-4.0f),
                        details -> details.text("Animated content",
                                Ui.modifier().style("small"))));
        content.grid(3, Ui.modifier().fillWidth().gap(8.0f), grid -> {
            tile(grid, "primary", "Press");
            tile(grid, "toggle", showDetails.get() ? "shown" : "hidden");
            tile(grid, "count", String.valueOf(clickCount.get()));
        });
    }

    private void buildCheckboxSection(UiScope content) {
        content.text("Checkboxes", Ui.modifier().style("title"));
        content.text("States", Ui.modifier().style("muted"));
        content.row(Ui.modifier().fillWidth().gap(18.0f), row -> {
            checkboxLabel(row, "Compact option", compactChecked, 16.0f);
            checkboxLabel(row, "Section option", checked);
            checkboxLabel(row, "Disabled", disabledChecked, 18.0f, false);
        });
        content.panel(Ui.modifier().width(150.0f).padding(10.0f).gap(7.0f).style("tile"), panel -> {
            panel.text("Bare boxes", Ui.modifier().style("section"));
            panel.row(Ui.modifier().gap(10.0f), row -> {
                row.checkbox(Ui.modifier().size(16.0f, 16.0f).semanticLabel("Bare checkbox A"), bareChecked);
                row.checkbox(Ui.modifier().size(20.0f, 20.0f).semanticLabel("Bare checkbox B"), compactChecked);
            });
        });
        content.panel(Ui.modifier().fillWidth().padding(10.0f).gap(5.0f).style("tile"), panel -> {
            panel.text("checked " + checked.get(), Ui.modifier().style("small"));
            panel.text("visual debug " + visualDebug.get(), Ui.modifier().style("small"));
        });
    }

    private void buildSliderSection(UiScope content) {
        content.text("Sliders", Ui.modifier().style("title"));
        content.text("Variants", Ui.modifier().style("muted"));
        sliderRow(content, "Volume", volume, 0.0f, 1.0f,
                Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Volume setting")
                        .validationId(UiKitValidationScenarios.SLIDER_VOLUME));
        sliderRow(content, "Normal", sliderNormal, 0.0f, 1.0f,
                Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Normal slider"));
        sliderRow(content, "Wide", sliderWide, 0.0f, 100.0f,
                Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Wide slider"));
        sliderRow(content, "Negative", sliderNegative, -1.0f, 1.0f,
                Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Negative slider"));
        sliderRow(content, "Precise", sliderPrecise, 0.0f, 1.0f,
                Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Precise slider"));
        sliderRow(content, "Disabled", sliderDisabled, 0.0f, 1.0f,
                Ui.modifier().fillWidth().weight(1.0f).enabled(false).semanticLabel("Disabled slider"));
        if (compactViewport()) {
            sliderCard(content, "Compact", sliderCompact, 0.0f, 1.0f,
                    Ui.modifier().width(130.0f).semanticLabel("Compact slider"));
            sliderCard(content, "Tiny", sliderTiny, 0.0f, 1.0f,
                    Ui.modifier().width(82.0f).semanticLabel("Tiny slider"));
        } else {
            content.row(Ui.modifier().fillWidth().gap(10.0f), row -> {
                sliderCard(row, "Compact", sliderCompact, 0.0f, 1.0f,
                        Ui.modifier().width(130.0f).semanticLabel("Compact slider"));
                sliderCard(row, "Tiny", sliderTiny, 0.0f, 1.0f,
                        Ui.modifier().width(82.0f).semanticLabel("Tiny slider"));
            });
        }
        content.custom("slider-preview", Ui.modifier().fillWidth().height(90.0f), custom -> {
            custom.measure(bounds -> new UiSize(Math.min(520.0f, bounds.maxWidth()), 90.0f));
            custom.draw((draw, bounds) -> {
                UiRect track = new UiRect(bounds.x() + 10.0f, bounds.y() + 18.0f,
                        Math.max(1.0f, bounds.width() - 20.0f), 14.0f);
                float normalizedNegative = (sliderNegative.get() + 1.0f) * 0.5f;
                draw.rect(bounds, UiColor.rgba8888(0x0d1117ff));
                draw.rect(track, UiColor.rgba8888(0x283342ff));
                draw.rect(new UiRect(track.x(), track.y(), track.width() * sliderNormal.get(), track.height()),
                        UiColor.rgba8888(0x4da3ffff));
                draw.rect(new UiRect(track.x(), track.y() + 24.0f, track.width() * normalizedNegative,
                        track.height()), UiColor.rgba8888(0x7bd88fff));
                draw.text("normal " + percent(sliderNormal.get()) + "  negative "
                                + signedValue(sliderNegative.get()),
                        new UiRect(bounds.x() + 10.0f, bounds.y() + 62.0f, bounds.width() - 20.0f, 18.0f),
                        textStyle(13.0f, 0xc7d4e3ff));
            });
        });
    }

    private void buildProgressBarSection(UiScope content) {
        content.text("Progress bars", Ui.modifier().style("title"));
        content.text("State mirrors", Ui.modifier().style("muted"));
        progressRow(content, "Volume", volume, 0.0f, 1.0f,
                Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Volume progress")
                        .validationId(UiKitValidationScenarios.PROGRESS_VOLUME));
        progressRow(content, "Wide", sliderWide, 0.0f, 100.0f,
                Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Wide progress")
                        .validationId(UiKitValidationScenarios.PROGRESS_WIDE));
        progressRow(content, "Disabled", sliderDisabled, 0.0f, 1.0f,
                Ui.modifier().fillWidth().weight(1.0f).enabled(false).semanticLabel("Disabled progress"));
        content.panel(Ui.modifier().fillWidth().padding(10.0f).gap(6.0f).style("tile"), panel -> {
            panel.text("read-only progress", Ui.modifier().style("section"));
            panel.progressBar(Ui.modifier().fillWidth().semanticLabel("Fixed progress"), 0.64f);
            panel.text("fixed " + percent(0.64f), Ui.modifier().style("small"));
        });
    }

    private void buildTabsSection(UiScope content) {
        content.text("Tabs", Ui.modifier().style("title"));
        content.text("Selectable views", Ui.modifier().style("muted"));
        content.tabs(Ui.modifier().fillWidth().semanticLabel("Demo tabs")
                        .validationId(UiKitValidationScenarios.TABS_DEMO).style("tabs"),
                activeDemoTab, "Overview", "Loadout", "Stats", "Log");
        content.panel(Ui.modifier().fillWidth().padding(10.0f).gap(5.0f).style("tile"), panel -> {
            int tab = activeDemoTab.get();
            panel.text("active tab " + demoTabLabel(tab), Ui.modifier().style("section"));
            if (tab == 0) {
                panel.text("Overview panel", Ui.modifier().style("small"));
                panel.text("volume " + percent(volume.get()) + "  clicks " + clickCount.get(),
                        Ui.modifier().style("small"));
            } else if (tab == 1) {
                panel.text("Loadout panel", Ui.modifier().style("small"));
                panel.text("item " + INVENTORY[inventoryList.firstVisibleIndex() % INVENTORY.length],
                        Ui.modifier().style("small"));
            } else if (tab == 2) {
                panel.text("Stats panel", Ui.modifier().style("small"));
                panel.text("scale " + scaleValue(uiScale.get()), Ui.modifier().style("small"));
            } else {
                panel.text("Log panel", Ui.modifier().style("small"));
                panel.text("latest " + ACTIVITY[0], Ui.modifier().style("small"));
            }
        });
    }

    private String demoTabLabel(int tab) {
        if (tab == 1) {
            return "Loadout";
        }
        if (tab == 2) {
            return "Stats";
        }
        if (tab == 3) {
            return "Log";
        }
        return "Overview";
    }

    private void sliderRow(UiScope content, String label, UiFloatState state, float minimum, float maximum,
            UiModifier sliderModifier) {
        content.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.text(label, Ui.modifier().width(78.0f).style("muted"));
            row.slider(sliderModifier, state, minimum, maximum);
            row.text(sliderValue(state.get(), minimum, maximum), Ui.modifier().width(54.0f).style("metric"));
        });
    }

    private void progressRow(UiScope content, String label, UiFloatState state, float minimum, float maximum,
            UiModifier progressModifier) {
        content.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.text(label, Ui.modifier().width(78.0f).style("muted"));
            row.progressBar(progressModifier, state, minimum, maximum);
            row.text(sliderValue(state.get(), minimum, maximum), Ui.modifier().width(54.0f).style("metric"));
        });
    }

    private void sliderCard(UiScope content, String label, UiFloatState state, float minimum, float maximum,
            UiModifier sliderModifier) {
        UiModifier cardModifier = compactViewport()
                ? Ui.modifier().fillWidth().padding(8.0f).gap(6.0f).style("tile")
                : Ui.modifier().width(220.0f).padding(8.0f).gap(6.0f).style("tile");
        content.panel(cardModifier, card -> {
            card.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
                row.text(label, Ui.modifier().fillWidth().weight(1.0f).style("muted"));
                row.text(sliderValue(state.get(), minimum, maximum), Ui.modifier().width(48.0f).style("metric"));
            });
            card.slider(sliderModifier, state, minimum, maximum);
        });
    }

    private void buildTextFieldSection(UiScope content) {
        content.text("Text inputs", Ui.modifier().style("title"));
        content.text("Inputs", Ui.modifier().style("muted"));
        fieldRow(content, "String", name, UiTextInputFilter.STRING);
        fieldRow(content, "Integer", intInput, UiTextInputFilter.INTEGER);
        fieldRow(content, "Float", floatInput, UiTextInputFilter.FLOAT);
        content.text("Message area", Ui.modifier().style("muted"));
        content.textArea(Ui.modifier().fillWidth().height(92.0f).semanticLabel("Message area").style("text-area"),
                messageInput, UiTextAreaOptions.defaults().minHeight(92.0f));
        content.text("Auto-grow area", Ui.modifier().style("muted"));
        content.textArea(Ui.modifier().fillWidth().semanticLabel("Auto grow text area").style("text-area"),
                growingMessageInput, UiTextAreaOptions.defaults().autoGrow(true).minHeight(52.0f).maxHeight(118.0f));
        content.panel(Ui.modifier().fillWidth().padding(10.0f).gap(4.0f).style("tile"), panel -> {
            panel.text("name " + name.get(), Ui.modifier().style("small"));
            panel.text("int " + intInput.get() + "  float " + floatInput.get(), Ui.modifier().style("small"));
            panel.text("message lines " + messageLineCount(), Ui.modifier().style("small"));
        });
    }

    private void fieldRow(UiScope content, String label, UiState<String> state, UiTextInputFilter inputFilter) {
        content.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.text(label, Ui.modifier().width(86.0f).style("muted"));
            row.textField(Ui.modifier().fillWidth().weight(1.0f).semanticLabel(label + " field"),
                    state, inputFilter);
        });
    }

    private void buildListSection(UiScope content) {
        content.text("Lists", Ui.modifier().style("title"));
        content.text("Scrollable data", Ui.modifier().style("muted"));
        content.row(Ui.modifier().fillWidth().gap(10.0f), row -> {
            row.scroll(Ui.modifier().width(300.0f).height(190.0f).padding(8.0f).gap(5.0f).style("scroll"),
                    feedScroll, scroll -> {
                        scroll.text("scroll y=" + Math.round(feedScroll.y()), Ui.modifier().style("small"));
                        scroll.items(Arrays.asList(ACTIVITY), item -> "feed-" + item,
                                (itemUi, item) -> itemUi.text(item, Ui.modifier().style("small")));
                    });
            row.panel(Ui.modifier().width(230.0f).height(190.0f).padding(8.0f).gap(5.0f).style("tile"), list -> {
                list.text("virtual list", Ui.modifier().style("section"));
                list.virtualList(Arrays.asList(INVENTORY), inventoryList, 4, item -> item,
                        (itemUi, item) -> itemUi.text(item, Ui.modifier().style("small")));
            });
        });
    }

    private void buildScrollViewSection(UiScope content) {
        content.text("Scroll view", Ui.modifier().style("title"));
        content.text("Medium scroll container", Ui.modifier().style("muted"));
        content.panel(Ui.modifier().fillWidth().padding(10.0f).gap(8.0f).style("tile"), panel -> {
            panel.text("Scroll the area to reach the hidden rows", Ui.modifier().style("section"));
            panel.text("This container is fixed height and keeps interaction widgets inside it.",
                    Ui.modifier().style("small"));
            panel.scroll(Ui.modifier().fillWidth().height(260.0f).padding(8.0f).gap(8.0f).style("scroll")
                            .semanticLabel("Medium scroll body"),
                    mediumScroll, scroll -> {
                scroll.textField(Ui.modifier().fillWidth().semanticLabel("Scroll text field"),
                        name, UiTextInputFilter.STRING);
                for (int i = 1; i <= 24; i++) {
                    final int item = i;
                    scroll.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
                        row.text("Row " + item, Ui.modifier().width(56.0f).style("muted"));
                        row.text("Scrollable widget sample " + item,
                                Ui.modifier().fillWidth().weight(1.0f).style("small"));
                        row.button("Action", Ui.modifier().width(72.0f),
                                () -> incrementClick());
                    });
                }
                    });
        });
    }

    private void buildDrawingSection(UiScope content, float drawnVolume) {
        content.text("Drawing", Ui.modifier().style("title"));
        content.text("Custom render areas", Ui.modifier().style("muted"));
        content.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.text("Resize this text", Ui.modifier().width(108.0f).style("muted"));
            row.slider(Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Drawing text size"),
                    drawingTextSize, DRAWING_TEXT_MINIMUM, DRAWING_TEXT_MAXIMUM);
            row.text(Math.round(drawingTextSize.get()) + " px", Ui.modifier().width(52.0f).style("metric"));
        });
        content.row(Ui.modifier().fillWidth().gap(10.0f), row -> {
            row.custom("draw-bars", Ui.modifier().width(260.0f).height(122.0f).style("tile"), custom -> {
                custom.draw((draw, bounds) -> {
                    draw.rect(bounds, UiColor.rgba8888(0x121a23ff));
                    draw.text("Bars", new UiRect(bounds.x() + 10.0f, bounds.y() + 8.0f,
                            bounds.width() - 20.0f, 18.0f), textStyle(13.0f, 0xa9b6c5ff));
                    draw.rect(new UiRect(bounds.x() + 10.0f, bounds.y() + 40.0f,
                            (bounds.width() - 20.0f) * drawnVolume, 12.0f), UiColor.rgba8888(0x4da3ffff));
                    draw.rect(new UiRect(bounds.x() + 10.0f, bounds.y() + 64.0f,
                            (bounds.width() - 20.0f) * sliderNormal.get(), 12.0f), UiColor.rgba8888(0x7bd88fff));
                });
            });
            row.custom("draw-text", Ui.modifier().fillWidth().weight(1.0f).height(122.0f).style("tile"), custom -> {
                custom.draw((draw, bounds) -> {
                    draw.rect(bounds, UiColor.rgba8888(0x101720ff));
                    draw.text("FreeType " + Math.round(drawingTextSize.get()) + " px",
                            new UiRect(bounds.x() + 10.0f, bounds.y() + 8.0f, bounds.width() - 20.0f, 18.0f),
                            textStyle(13.0f, 0xa9b6c5ff));
                    draw.text("Resize this text",
                            new UiRect(bounds.x() + 10.0f, bounds.y() + 42.0f, bounds.width() - 20.0f, 48.0f),
                            textStyle(drawingTextSize.get(), 0xf7fbffff));
                });
            });
        });
        content.stack(Ui.modifier().fillWidth().height(136.0f).style("stage"), stack -> {
            stack.image(logoRegion, Ui.modifier().fill());
            stack.panel(Ui.modifier().width(210.0f).align(UiAlign.END).padding(10.0f).gap(5.0f),
                    overlay -> {
                        overlay.text("Image layer", Ui.modifier().style("section"));
                        overlay.text("Logo with a panel overlay", Ui.modifier().style("small"));
                    });
        });
    }

    private void buildWindowSection(UiScope content) {
        content.text("Windows", Ui.modifier().style("title"));
        content.text("Floating layer", Ui.modifier().style("muted"));
    }

    private void buildOverlaySection(UiScope content) {
        content.text("Overlays", Ui.modifier().style("title"));
        content.text("Layer modes", Ui.modifier().style("muted"));
        content.row(Ui.modifier().gap(8.0f), row -> {
            row.button("Popup", () -> showPopup.toggle());
            row.button("Modal", () -> showModal.set(true));
        });
        content.text("Centered popup and modal", Ui.modifier().style("small"));
    }

    private void buildTooltipSection(UiScope content) {
        content.text("Tooltips", Ui.modifier().style("title"));
        content.text("Hover targets", Ui.modifier().style("muted"));
        content.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.button("Hover me", Ui.modifier().semanticLabel("Hover me"), () -> incrementClick());
            row.button("Slow hint", Ui.modifier().semanticLabel("Slow hint"), () -> incrementClick());
        });
        content.panel(Ui.modifier().fillWidth().padding(10.0f).gap(12.0f).style("tile"), panel -> {
            panel.row(Ui.modifier().fillWidth().gap(18.0f), row -> {
                row.text("Text hover",
                        Ui.modifier()
                                .width(132.0f)
                                .height(32.0f)
                                .validationId(UiKitValidationScenarios.TOOLTIP_TEXT_TARGET)
                                .tooltipTarget(UiKitValidationScenarios.TOOLTIP_TEXT_TARGET)
                                .style("section"));
                row.checkbox("Checkbox hover",
                        Ui.modifier()
                                .width(184.0f)
                                .validationId(UiKitValidationScenarios.TOOLTIP_CHECKBOX_TARGET)
                                .tooltipTarget(UiKitValidationScenarios.TOOLTIP_CHECKBOX_TARGET)
                                .style("small"),
                        compactChecked);
                row.textField(Ui.modifier().fillWidth().weight(1.0f)
                                .height(32.0f)
                                .validationId(UiKitValidationScenarios.TOOLTIP_TEXT_FIELD_TARGET)
                                .tooltipTarget(UiKitValidationScenarios.TOOLTIP_TEXT_FIELD_TARGET)
                                .semanticLabel("Tooltip text field"),
                        name, UiTextInputFilter.STRING);
            });
            panel.spacer(Ui.modifier().height(64.0f));
        });
    }

    private void buildFloatingWindows(UiScope page, float drawnVolume) {
        if (activeSection.get() != SECTION_WINDOWS) {
            return;
        }
        page.window("Tools Window", toolsWindow, tools -> {
            tools.text("drag the title bar", Ui.modifier().style("muted"));
            checkboxLabel(tools, "Window option", checked);
            tools.row(Ui.modifier().gap(8.0f), row -> {
                row.button("Press", () -> incrementClick());
                row.button("Hide", () -> showDetails.set(false));
            });
        });

        page.window("Resizable Stats", statsWindow, stats -> {
            stats.text("resize from the corner", Ui.modifier().style("muted"));
            stats.text(statusText(), Ui.modifier().style("small"));
            stats.custom("stats-meter", Ui.modifier().fillWidth().height(48.0f), custom -> {
                custom.draw((draw, bounds) -> {
                    UiRect track = new UiRect(bounds.x() + 8.0f, bounds.y() + 18.0f,
                            Math.max(1.0f, bounds.width() - 16.0f), 12.0f);
                    draw.rect(bounds, UiColor.rgba8888(0x101820f2));
                    draw.rect(track, UiColor.rgba8888(0x2b3442ff));
                    draw.rect(new UiRect(track.x(), track.y(), track.width() * drawnVolume, track.height()),
                            UiColor.rgba8888(0x7bd88fff));
                });
            });
        });
    }

    private void buildOverlays(UiScope page) {
        page.tooltip(Ui.tooltip("Hover me").delayMillis(0).align(UiAlign.START), tooltip -> {
            tooltip.panel(Ui.modifier().width(164.0f).padding(6.0f).gap(3.0f).style("tooltip-panel"), panel -> {
                panel.text("tooltip", Ui.modifier().style("section"));
                panel.text("button hover", Ui.modifier().style("small"));
            });
        });
        page.tooltip(Ui.tooltip("Slow hint").delayMillis(650).align(UiAlign.START), tooltip -> {
            tooltip.panel(Ui.modifier().width(178.0f).padding(6.0f).gap(3.0f).style("tooltip-panel"), panel -> {
                panel.text("delayed tooltip", Ui.modifier().style("section"));
                panel.text("alpha fade-in", Ui.modifier().style("small"));
            });
        });
        page.tooltip(Ui.tooltip(UiKitValidationScenarios.TOOLTIP_TEXT_TARGET).delayMillis(2000)
                .align(UiAlign.START), tooltip -> {
                    tooltip.panel(Ui.modifier().width(190.0f).padding(6.0f).gap(3.0f).style("tooltip-panel"),
                            panel -> {
                                panel.text("text tooltip", Ui.modifier().style("section"));
                                panel.text("shown after 2 seconds", Ui.modifier().style("small"));
                            });
                });
        page.tooltip(Ui.tooltip(UiKitValidationScenarios.TOOLTIP_CHECKBOX_TARGET).delayMillis(2000)
                .align(UiAlign.START), tooltip -> {
                    tooltip.panel(Ui.modifier().width(210.0f).padding(6.0f).gap(3.0f).style("tooltip-panel"),
                            panel -> {
                                panel.text("checkbox tooltip", Ui.modifier().style("section"));
                                panel.text("shown after 2 seconds", Ui.modifier().style("small"));
                            });
                });
        page.tooltip(Ui.tooltip(UiKitValidationScenarios.TOOLTIP_TEXT_FIELD_TARGET).delayMillis(2000)
                .align(UiAlign.START), tooltip -> {
                    tooltip.panel(Ui.modifier().width(216.0f).padding(6.0f).gap(3.0f).style("tooltip-panel"),
                            panel -> {
                                panel.text("text field tooltip", Ui.modifier().style("section"));
                                panel.text("shown after 2 seconds", Ui.modifier().style("small"));
                            });
                });
        page.popup(Ui.popup("uikit-popup").align(UiAlign.CENTER, UiAlign.CENTER)
                .dismissOnOutsidePress(false).blockingInput(blockPopupInput.get()), popup -> {
                    popup.animatedVisibility(showPopup.get(), Ui.animation().durationMillis(80).fade().slideY(-10.0f),
                            animated -> animated.panel(Ui.modifier().width(280.0f).padding(12.0f).gap(7.0f)
                                    .style("popup-panel"), panel -> {
                                        panel.text("popup", Ui.modifier().style("section"));
                                        panel.text("centered overlay content", Ui.modifier().style("small"));
                                        checkboxLabel(panel, "Block input", blockPopupInput, COMPACT_CHECKBOX_SIZE);
                                        panel.row(Ui.modifier().gap(6.0f), row -> {
                                            row.button("Keep", () -> incrementClick());
                                            row.button("Close",
                                                    Ui.modifier().validationId(
                                                            UiKitValidationScenarios.POPUP_CLOSE_BUTTON),
                                                    () -> showPopup.set(false));
                                        });
                                    }));
                });
        if (showModal.get()) {
            page.modal(Ui.modal("uikit-modal").scrim(UiColor.rgba(0.0f, 0.0f, 0.0f, 0.55f)), modal -> {
                modal.panel(Ui.modifier().width(300.0f).padding(12.0f).gap(8.0f).style("nine-panel"), panel -> {
                    panel.text("modal", Ui.modifier().style("title"));
                    panel.text("overlay layer and scrim", Ui.modifier().style("muted"));
                    panel.row(Ui.modifier().gap(6.0f), row -> {
                        row.button("Close", () -> showModal.set(false));
                        row.button("Keep", () -> incrementClick());
                    });
                });
            });
        }
    }

    private String activeSectionName() {
        int index = activeSection != null ? activeSection.get() : SECTION_BUTTONS;
        if (index < 0 || index >= SECTIONS.length) {
            return SECTIONS[SECTION_BUTTONS];
        }
        return SECTIONS[index];
    }

    private boolean compactViewport() {
        return display != null && display.width() > 0 && display.width() < 1100;
    }

    private UiScrollState activeSectionScroll() {
        int index = activeSection != null ? activeSection.get() : SECTION_BUTTONS;
        if (index < 0 || index >= sectionScrolls.length) {
            index = SECTION_BUTTONS;
        }
        return sectionScrolls[index];
    }

    private int initialSection() {
        String requested = System.getProperty("libfdx.test.uiSection", "");
        if (requested == null || requested.length() == 0) {
            return SECTION_BUTTONS;
        }
        for (int i = 0; i < SECTIONS.length; i++) {
            if (SECTIONS[i].equalsIgnoreCase(requested)) {
                return i;
            }
        }
        try {
            int index = Integer.parseInt(requested);
            if (index >= 0 && index < SECTIONS.length) {
                return index;
            }
        } catch (NumberFormatException ignored) {
        }
        return SECTION_BUTTONS;
    }

    private void tile(UiScope grid, String title, String value) {
        grid.panel(Ui.modifier().padding(6.0f).gap(3.0f).style("tile"), tile -> {
            tile.text(title, Ui.modifier().style("section"));
            tile.text(value, Ui.modifier().style("small"));
        });
    }

    private void checkboxLabel(UiScope ui, String label, UiBooleanState state) {
        checkboxLabel(ui, label, state, Float.NaN);
    }

    private void checkboxLabel(UiScope ui, String label, UiBooleanState state, float checkboxSize) {
        checkboxLabel(ui, label, state, checkboxSize, true);
    }

    private void checkboxLabel(UiScope ui, String label, UiBooleanState state, float checkboxSize, boolean enabled) {
        ui.row(Ui.modifier().gap(6.0f), row -> {
            UiModifier checkboxModifier = Ui.modifier().semanticLabel(label).enabled(enabled);
            String validationId = checkboxValidationId(label);
            if (validationId != null) {
                checkboxModifier = checkboxModifier.validationId(validationId);
            }
            if (!Float.isNaN(checkboxSize)) {
                checkboxModifier = checkboxModifier.size(checkboxSize, checkboxSize);
            }
            row.checkbox(checkboxModifier, state);
            row.text(label, Ui.modifier().enabled(enabled));
        });
    }

    private String checkboxValidationId(String label) {
        if ("Section option".equals(label)) {
            return UiKitValidationScenarios.CHECKBOX_SECTION_OPTION;
        }
        if ("Block input".equals(label)) {
            return UiKitValidationScenarios.POPUP_BLOCK_INPUT;
        }
        return null;
    }

    private void checkCheckboxLabelAlignment() {
        if (checkboxAlignmentChecked) {
            return;
        }
        UiNode defaultCheckbox = find(root.rootNode(), UiNodeType.CHECKBOX, "Section option");
        UiNode compactCheckbox = find(root.rootNode(), UiNodeType.CHECKBOX, "Visual debug");
        assertAlignedCenters(defaultCheckbox, find(root.rootNode(), UiNodeType.TEXT, "Section option"),
                "Section option checkbox label");
        assertAlignedCenters(compactCheckbox, find(root.rootNode(), UiNodeType.TEXT, "Visual debug"),
                "Visual debug checkbox label");
        assertNodeSize(defaultCheckbox, DEFAULT_CHECKBOX_SIZE, DEFAULT_CHECKBOX_SIZE, "default checkbox");
        assertNodeSize(compactCheckbox, COMPACT_CHECKBOX_SIZE, COMPACT_CHECKBOX_SIZE, "custom checkbox");
        checkboxAlignmentChecked = true;
    }

    private void assertAlignedCenters(UiNode left, UiNode right, String label) {
        if (left == null || right == null) {
            throw new FdxException("UiKitTest could not find " + label + " nodes");
        }
        float leftCenter = left.bounds().y() + left.bounds().height() * 0.5f;
        float rightCenter = right.bounds().y() + right.bounds().height() * 0.5f;
        if (Math.abs(leftCenter - rightCenter) > 0.5f) {
            throw new FdxException("UiKitTest did not center " + label + ": left="
                    + left.bounds().y() + " " + left.bounds().height() + ", right="
                    + right.bounds().y() + " " + right.bounds().height());
        }
    }

    private void assertNodeSize(UiNode node, float width, float height, String label) {
        if (node == null) {
            throw new FdxException("UiKitTest could not find " + label + " node");
        }
        if (Math.abs(node.bounds().width() - width) > 0.5f || Math.abs(node.bounds().height() - height) > 0.5f) {
            throw new FdxException("UiKitTest unexpected " + label + " size: actual="
                    + node.bounds().width() + "x" + node.bounds().height() + ", expected=" + width + "x" + height);
        }
    }

    private UiTheme theme() {
        UiTextStyle text = textStyle(16.0f, 0xe7edf5ff).lineHeight(20.0f);
        UiTextStyle muted = textStyle(13.0f, 0xa9b6c5ff).lineHeight(17.0f);
        UiTextStyle title = textStyle(22.0f, 0xf7fbffff).lineHeight(26.0f);
        UiStyle button = UiStyle.button()
                .text(textStyle(15.0f, 0xf7fbffff).lineHeight(19.0f))
                .background(UiDrawable.color(UiColor.rgba8888(0x33404dff)))
                .hover(UiStyle.button().text(text).background(UiDrawable.color(UiColor.rgba8888(0x40546aff))))
                .pressed(UiStyle.button().text(text).background(UiDrawable.color(UiColor.rgba8888(0x297ed6ff))))
                .disabled(UiStyle.button()
                        .text(textStyle(15.0f, 0x7f8a96ff).lineHeight(19.0f))
                        .background(UiDrawable.color(UiColor.rgba8888(0x202832aa))));
        UiStyle textField = UiStyle.style()
                .padding(10.0f, 6.0f)
                .text(text.wrap(false).ellipsis(true))
                .background(UiDrawable.color(UiColor.rgba8888(0x101720ff)));
        UiStyle textArea = UiStyle.style()
                .padding(10.0f, 8.0f)
                .text(text.wrap(false).ellipsis(false))
                .background(UiDrawable.color(UiColor.rgba8888(0x101720ff)));
        UiStyle ninePanel = UiStyle.style()
                .padding(12.0f)
                .text(text)
                .background(UiDrawable.ninePatch(UiNinePatch.region(panelPatchRegion,
                        Ui.insets(5.0f), Ui.insets(7.0f))));
        UiStyle window = UiStyle.style()
                .padding(Ui.insets(14.0f, 42.0f, 14.0f, 14.0f))
                .text(text)
                .background(UiDrawable.color(UiColor.rgba8888(0x18212bff)));

        return Ui.darkTheme()
                .text(UiStyle.style().text(text))
                .button(button)
                .panel(UiStyle.style().padding(12.0f).text(text)
                        .background(UiDrawable.color(UiColor.rgba8888(0x18212bff))))
                .window(window)
                .tabs(UiStyle.style().padding(4.0f).text(textStyle(14.0f, 0xe7edf5ff).lineHeight(18.0f))
                        .background(UiDrawable.color(UiColor.rgba8888(0x101820ff))))
                .textField(textField.focused(textField.background(UiDrawable.color(UiColor.rgba8888(0x172334ff)))))
                .style("text-area", textArea.focused(textArea.background(UiDrawable.color(UiColor.rgba8888(0x172334ff)))))
                .style("title", UiStyle.style().text(title))
                .style("section", UiStyle.style().text(textStyle(15.0f, 0xdce8f5ff).lineHeight(19.0f)))
                .style("muted", UiStyle.style().text(muted))
                .style("small", UiStyle.style().text(textStyle(12.0f, 0xa9b6c5ff).lineHeight(16.0f)))
                .style("metric", UiStyle.style().text(textStyle(16.0f, 0x9fd7ffff).lineHeight(20.0f)))
                .style("nine-panel", ninePanel)
                .style("section-panel", UiStyle.style().padding(12.0f).text(text)
                        .background(UiDrawable.color(UiColor.rgba8888(0x151d26ff))))
                .style("nav-panel", UiStyle.style().padding(10.0f).text(text)
                        .background(UiDrawable.color(UiColor.rgba8888(0x101820ff))))
                .style("nav-active", UiStyle.button()
                        .text(textStyle(15.0f, 0xf8fbffff).lineHeight(19.0f))
                        .background(UiDrawable.color(UiColor.rgba8888(0x2f6f5eff)))
                        .hover(UiStyle.button().text(text)
                                .background(UiDrawable.color(UiColor.rgba8888(0x3b7d6aff))))
                        .pressed(UiStyle.button().text(text)
                                .background(UiDrawable.color(UiColor.rgba8888(0x246050ff)))))
                .style("tile", UiStyle.style().padding(10.0f).text(text)
                        .background(UiDrawable.color(UiColor.rgba8888(0x121a23ff))))
                .style("stage", UiStyle.style().background(UiDrawable.color(UiColor.rgba8888(0x0f151dff))))
                .style("scroll", UiStyle.style().background(UiDrawable.color(UiColor.rgba8888(0x0f151dff))))
                .style("popup-panel", UiStyle.style().padding(10.0f).text(text)
                        .background(UiDrawable.color(UiColor.rgba8888(0x243244ff))))
                .style("tooltip-panel", UiStyle.style().padding(8.0f).text(muted)
                        .background(UiDrawable.color(UiColor.rgba8888(0x111820ee))));
    }

    private UiTextStyle textStyle(float size, int rgba) {
        return UiTextStyle.text()
                .font(UiFont.freeType(FREETYPE_FONT_ASSET, size))
                .size(size)
                .lineHeight(size + 3.0f)
                .color(UiColor.rgba8888(rgba));
    }

    private UiKitValidationScenarios.Plan buildValidationScenarios() {
        boolean captureAllScenarios = visualCaptureAllScenarios;
        UiKitValidationScenarios.Builder builder = UiKitValidationScenarios.builder(validationActive);
        builder.entry(0L, captureAllScenarios, false,
                Scenario.named("press-button-down")
                        .custom("show-buttons", context -> showValidationSection(SECTION_BUTTONS))
                        .expect(UiScenarioAssertions.enabled(UiKitValidationScenarios.BUTTON_PRESS, true))
                        .action(UiScenarioActions.press(UiKitValidationScenarios.BUTTON_PRESS)));
        builder.entry(1L, captureAllScenarios, false,
                Scenario.named("press-button-up")
                        .action(UiScenarioActions.release(UiKitValidationScenarios.BUTTON_PRESS))
                        .custom("show-checkboxes", context -> showValidationSection(SECTION_CHECKBOXES)));
        builder.entry(2L, true, true,
                Scenario.named("section-checkboxes")
                        .action(UiScenarioActions.click(UiKitValidationScenarios.CHECKBOX_SECTION_OPTION))
                        .expect(UiScenarioAssertions.checked(UiKitValidationScenarios.CHECKBOX_SECTION_OPTION, true))
                        .custom("check-checkbox-label-alignment", context -> checkCheckboxLabelAlignment())
                        .custom("show-sliders", context -> showValidationSection(SECTION_SLIDERS)));
        builder.entry(3L, true, true,
                Scenario.named("slider-volume")
                        .custom("drag-volume-slider", context -> {
                            settleValidationLayout();
                            dragVolumeSlider();
                        })
                        .expect(UiScenarioAssertions.sliderValue(UiKitValidationScenarios.SLIDER_VOLUME,
                                1.0f, 0.01f)));
        builder.entry(4L, true, true,
                Scenario.named("progress-bars")
                        .custom("show-progress-bars", context -> showValidationSection(SECTION_PROGRESS_BARS))
                        .expect(UiScenarioAssertions.boundsAtLeast(UiKitValidationScenarios.PROGRESS_VOLUME,
                                20.0f, 4.0f))
                        .expect(UiScenarioAssertions.floatValue(UiKitValidationScenarios.PROGRESS_VOLUME,
                                1.0f, 0.01f))
                        .expect(UiScenarioAssertions.exists(UiKitValidationScenarios.PROGRESS_WIDE))
                        .custom("validate-progress-bars", context -> validateProgressBars()));
        builder.entry(5L, true, true,
                Scenario.named("tabs-switch")
                        .custom("show-tabs", context -> showValidationTabs())
                        .expect(UiScenarioAssertions.activeTab(UiKitValidationScenarios.TABS_DEMO, 0))
                        .action(UiScenarioActions.clickTab(UiKitValidationScenarios.TABS_DEMO, 1))
                        .expect(UiScenarioAssertions.activeTab(UiKitValidationScenarios.TABS_DEMO, 1))
                        .custom("validate-tabs-pointer", context -> validateTabs()));
        builder.entry(6L, true, true,
                Scenario.named("slider-text")
                        .custom("slider-text", context -> {
                            showValidationSection(SECTION_TEXT_FIELDS);
                            validateTextSelectionAndCopyPaste();
                            activeSection.set(SECTION_SLIDERS);
                        }));
        builder.entry(7L, true, true,
                Scenario.named("text-area-edit")
                        .custom("text-area-edit", context -> {
                            showValidationSection(SECTION_TEXT_FIELDS);
                            validateTextArea();
                        }));
        builder.entry(8L, true, true,
                Scenario.named("text-input-touch-drag")
                        .custom("text-input-touch-drag", context -> {
                            showValidationSection(SECTION_SCROLLVIEW);
                            validateTextInputTouchDrag();
                        }));
        builder.entry(8L, captureAllScenarios, true,
                Scenario.named("section-drawing")
                        .custom("section-drawing", context -> {
                            showValidationSection(SECTION_DRAWING);
                            captureCurrentValidationFrame("section-drawing");
                        }));
        builder.entry(9L, captureAllScenarios, true,
                Scenario.named("list-interaction")
                        .custom("list-interaction", context -> {
                            showValidationSection(SECTION_LISTS);
                            input.dispatchScrolled(root.displayX(20.0f), root.displayY(20.0f), 0.0f, 1.0f);
                            inventoryList.scrollToItem(2, 0.0f);
                            activeSection.set(SECTION_WINDOWS);
                        }));
        builder.entry(10L, true, true,
                Scenario.named("scroll-body-drag")
                        .custom("scroll-body-drag", context -> {
                            showValidationSection(SECTION_SCROLLVIEW);
                            validateScrollBodyDrag();
                        }));
        builder.entry(10L, true, true,
                Scenario.named("scroll-child-drag")
                        .custom("scroll-child-drag", context -> {
                            showValidationSection(SECTION_BUTTONS);
                            validateScrollChildDrag();
                        }));
        builder.entry(11L, captureAllScenarios, true,
                Scenario.named("window-drag")
                        .custom("window-drag", context -> {
                            showValidationSection(SECTION_WINDOWS);
                            UiNode tools = find(root.rootNode(), UiNodeType.WINDOW, "Tools Window");
                            validateWindowDrag(tools, 560.0f, 44.0f);
                        }));
        builder.entry(12L, true, true,
                Scenario.named("window-edge-tests")
                        .custom("window-edge-tests", context -> {
                            settleValidationLayout();
                            dragWindowPastEdge(find(root.rootNode(), UiNodeType.WINDOW, "Tools Window"));
                            dragWindow(find(root.rootNode(), UiNodeType.WINDOW, "Tools Window"), -400.0f, -17.0f);
                            validateWindowResize(find(root.rootNode(), UiNodeType.WINDOW, "Resizable Stats"),
                                    86.0f, 58.0f);
                            resizeWindowPastEdge(find(root.rootNode(), UiNodeType.WINDOW, "Resizable Stats"));
                        }));
        builder.entry(14L, captureAllScenarios, true,
                Scenario.named("popup-pass-through")
                        .custom("popup-pass-through", context -> {
                            activeSection.set(SECTION_BUTTONS);
                            showPopup.set(true);
                            settleValidationLayout();
                            checkPopupPassThrough();
                        }));
        builder.entry(15L, captureAllScenarios, true,
                Scenario.named("popup-enable-block")
                        .action(UiScenarioActions.click(UiKitValidationScenarios.POPUP_BLOCK_INPUT))
                        .expect(UiScenarioAssertions.checked(UiKitValidationScenarios.POPUP_BLOCK_INPUT, true))
                        .custom("settle", context -> settleValidationLayout()));
        builder.entry(16L, captureAllScenarios, true,
                Scenario.named("popup-blocking")
                        .custom("popup-blocking", context -> {
                            settleValidationLayout();
                            checkPopupBlocking();
                        }));
        builder.entry(17L, captureAllScenarios, true,
                Scenario.named("popup-close")
                        .action(UiScenarioActions.click(UiKitValidationScenarios.POPUP_CLOSE_BUTTON))
                        .custom("settle", context -> settleValidationLayout()));
        builder.entry(18L, true, false,
                Scenario.named("tooltip-text-hover")
                        .custom("show-tooltips", context -> showValidationSection(SECTION_TOOLTIPS))
                        .custom("hover-visible-text", context -> hoverValidationTarget(context,
                                UiKitValidationScenarios.TOOLTIP_TEXT_TARGET, 0.25f, 0.5f))
                        .waitFor(UiScenarioWaits.visible(UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP,
                                UiKitValidationScenarios.TOOLTIP_TEXT_TARGET)).timeoutMillis(2500L))
                        .expect(UiScenarioAssertions.visible(UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP,
                                UiKitValidationScenarios.TOOLTIP_TEXT_TARGET))));
        builder.entry(19L, true, false,
                Scenario.named("tooltip-close-keeps-anchor")
                        .custom("show-tooltips", context -> showValidationSection(SECTION_TOOLTIPS))
                        .custom("hover-visible-text", context -> hoverValidationTarget(context,
                                UiKitValidationScenarios.TOOLTIP_TEXT_TARGET, 0.25f, 0.5f))
                        .waitFor(UiScenarioWaits.visible(UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP,
                                UiKitValidationScenarios.TOOLTIP_TEXT_TARGET)).timeoutMillis(2500L))
                        .custom("hover-away", context -> hoverAwayFromTooltipTargets(context))
                        .custom("advance-fade", context -> context.host().advanceFrame(context))
                        .custom("assert-close-position", context -> assertTooltipPanelAwayFromOrigin(context,
                                UiKitValidationScenarios.TOOLTIP_TEXT_TARGET)));
        builder.entry(20L, true, false,
                Scenario.named("tooltip-checkbox-hover")
                        .custom("show-tooltips", context -> showValidationSection(SECTION_TOOLTIPS))
                        .custom("hover-checkbox-label", context -> hoverValidationTarget(context,
                                UiKitValidationScenarios.TOOLTIP_CHECKBOX_TARGET, 0.72f, 0.5f))
                        .waitFor(UiScenarioWaits.visible(UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP,
                                UiKitValidationScenarios.TOOLTIP_CHECKBOX_TARGET)).timeoutMillis(2500L))
                        .expect(UiScenarioAssertions.visible(UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP,
                                UiKitValidationScenarios.TOOLTIP_CHECKBOX_TARGET))));
        builder.entry(21L, true, false,
                Scenario.named("tooltip-text-field-hover")
                        .custom("show-tooltips", context -> showValidationSection(SECTION_TOOLTIPS))
                        .custom("hover-text-field-value", context -> hoverValidationTarget(context,
                                UiKitValidationScenarios.TOOLTIP_TEXT_FIELD_TARGET, 0.18f, 0.5f))
                        .waitFor(UiScenarioWaits.visible(UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP,
                                UiKitValidationScenarios.TOOLTIP_TEXT_FIELD_TARGET)).timeoutMillis(2500L))
                        .expect(UiScenarioAssertions.visible(UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP,
                                UiKitValidationScenarios.TOOLTIP_TEXT_FIELD_TARGET))));
        builder.entry(22L, true, true,
                Scenario.named("text-scale-slider")
                        .custom("text-scale-slider", context -> {
                            enableGlobalScaleForValidation();
                            settleValidationLayout();
                            validateTextSizeSliderReleaseCommit();
                        })
                        .expect(UiScenarioAssertions.sliderValue(
                                UiKitValidationScenarios.SETTINGS_TEXT_SIZE_SLIDER, 2.02f, 0.05f)));
        builder.entry(23L, captureAllScenarios, true,
                Scenario.named("open-modal")
                        .custom("reset-global-scale", context -> resetGlobalScaleForValidation())
                        .custom("settle", context -> settleValidationLayout())
                        .action(UiScenarioActions.click(UiKitValidationScenarios.HEADER_MODAL_BUTTON))
                        .custom("settle", context -> settleValidationLayout())
                        .expect(UiScenarioAssertions.modalOpen(UiKitValidationScenarios.MODAL_ID))
                        .action(ScenarioActions.emit("ui.modal.opened:" + UiKitValidationScenarios.MODAL_ID)));
        return builder.build();
    }

    private void showValidationSection(int section) {
        activeSection.set(section);
        settleValidationLayout();
    }

    private void assertTooltipPanelAwayFromOrigin(ScenarioContext context, String tooltipKey) {
        UiNode tooltip = UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP, tooltipKey).resolve(root);
        context.assertTrue(renderedVisible(tooltip), "Tooltip is not rendered during fade: " + tooltipKey);
        UiNode panel = firstRenderedChild(tooltip);
        context.assertTrue(panel != null, "Tooltip has no rendered panel during fade: " + tooltipKey);
        UiRect bounds = panel.bounds();
        context.assertTrue(bounds.x() > 1.0f || bounds.y() > 1.0f,
                "Tooltip panel moved to the root origin during fade: " + tooltipKey
                        + " bounds=" + bounds.x() + "," + bounds.y() + " "
                        + bounds.width() + "x" + bounds.height());
    }

    private void hoverAwayFromTooltipTargets(ScenarioContext context) {
        ScenarioInputDriver inputDriver = context.host().inputDriver();
        context.assertTrue(inputDriver != null, "Scenario host does not provide an input driver.");
        float x = 4.0f;
        float y = display != null ? Math.max(4.0f, display.height() - 4.0f) : 4.0f;
        inputDriver.pointerMove(x, y);
    }

    private void hoverValidationTarget(ScenarioContext context, String validationId, float xPercent, float yPercent) {
        UiNode node = UiScenarioTargets.id(validationId).require(context);
        context.assertTrue(renderedVisible(node), "UI target is not rendered: " + validationId);
        ScenarioInputDriver inputDriver = context.host().inputDriver();
        context.assertTrue(inputDriver != null, "Scenario host does not provide an input driver.");
        UiRect bounds = node.bounds();
        context.assertTrue(bounds.width() > 0.0f && bounds.height() > 0.0f,
                "UI target does not have usable bounds: " + validationId);
        float x = bounds.x() + bounds.width() * clamp(xPercent, 0.0f, 1.0f);
        float y = bounds.y() + bounds.height() * clamp(yPercent, 0.0f, 1.0f);
        inputDriver.pointerMove(root.displayX(x), root.displayY(y));
    }

    private UiNode firstRenderedChild(UiNode node) {
        if (node == null) {
            return null;
        }
        for (int i = 0; i < node.children().size(); i++) {
            UiNode child = node.children().get(i);
            if (renderedVisible(child) && child.bounds().width() > 0.0f && child.bounds().height() > 0.0f) {
                return child;
            }
        }
        return null;
    }

    private boolean renderedVisible(UiNode node) {
        return node != null && node.visible()
                && (node.modifier() == null || node.modifier().alpha() > 0.001f);
    }

    private void showValidationTabs() {
        activeSection.set(SECTION_TABS);
        activeDemoTab.set(0);
        settleValidationLayout();
    }

    private final class UiKitScenarioInputDriver implements ScenarioInputDriver {
        @Override
        public void keyDown(Key key) {
            input.dispatchKeyDown(key);
        }

        @Override
        public void keyUp(Key key) {
            input.dispatchKeyUp(key);
        }

        @Override
        public void pointerMove(float x, float y) {
            input.dispatchPointerMoved(Math.round(x), Math.round(y));
        }

        @Override
        public void pointerDown(float x, float y) {
            input.dispatchPointerDown(MouseButton.LEFT, Math.round(x), Math.round(y));
        }

        @Override
        public void pointerUp(float x, float y) {
            input.dispatchPointerUp(MouseButton.LEFT, Math.round(x), Math.round(y));
        }

        @Override
        public void text(String text) {
            input.dispatchTextInput(text);
        }

        @Override
        public void scroll(float amountX, float amountY) {
            int x = display != null ? Math.max(0, display.width() / 2) : 0;
            int y = display != null ? Math.max(0, display.height() / 2) : 0;
            input.dispatchScrolled(x, y, amountX, amountY);
        }
    }

    private void recordUiPerf(float deltaSeconds, long updateNanos, long renderNanos, long frameNanos) {
        if (uiPerfLogSeconds <= 0.0f || logger == null) {
            return;
        }
        uiPerfElapsedSeconds += Math.max(0.0f, deltaSeconds);
        uiPerfFrames++;
        uiPerfUpdateNanos += Math.max(0L, updateNanos);
        uiPerfRenderNanos += Math.max(0L, renderNanos);
        uiPerfFrameNanos += Math.max(0L, frameNanos);
        if (uiPerfElapsedSeconds + 0.0001f < uiPerfLogSeconds) {
            return;
        }
        logger.info("UiKitTest perf: updateMs=" + roundedMillis(uiPerfUpdateNanos, uiPerfFrames)
                + ", renderMs=" + roundedMillis(uiPerfRenderNanos, uiPerfFrames)
                + ", frameWorkMs=" + roundedMillis(uiPerfFrameNanos, uiPerfFrames)
                + ", frames=" + uiPerfFrames
                + ", seconds=" + rounded(uiPerfElapsedSeconds));
        uiPerfElapsedSeconds = 0.0f;
        uiPerfFrames = 0L;
        uiPerfUpdateNanos = 0L;
        uiPerfRenderNanos = 0L;
        uiPerfFrameNanos = 0L;
    }

    private String roundedMillis(long nanos, long count) {
        if (count <= 0L) {
            return "0.0";
        }
        return rounded(nanos / 1000000.0f / count);
    }

    private String rounded(float value) {
        return String.valueOf(Math.round(value * 100.0f) / 100.0f);
    }

    private void enableGlobalScaleForValidation() {
        if (scaleGlobally == null || root == null) {
            return;
        }
        if (!scaleGlobally.get()) {
            scaleGlobally.set(true);
        }
        if (uiScale != null) {
            root.uiScale(uiScale.get());
        }
        scaleGloballyActive = true;
    }

    private void resetGlobalScaleForValidation() {
        if (scaleGlobally == null || root == null) {
            return;
        }
        if (scaleGlobally.get()) {
            scaleGlobally.set(false);
        }
        root.uiScale(1.0f);
        scaleGloballyActive = false;
    }

    private void validateProgressBars() {
        UiNode volumeProgress = find(root.rootNode(), UiNodeType.PROGRESS_BAR, "Volume progress");
        UiNode wideProgress = find(root.rootNode(), UiNodeType.PROGRESS_BAR, "Wide progress");
        if (volumeProgress == null || wideProgress == null) {
            throw new FdxException("UiKitTest could not find progress bar nodes");
        }
        if (volumeProgress.bounds().width() <= 20.0f || volumeProgress.bounds().height() <= 4.0f) {
            throw new FdxException("UiKitTest volume progress bar did not receive usable bounds: "
                    + volumeProgress.bounds().width() + "x" + volumeProgress.bounds().height());
        }
        if (Math.abs(volumeProgress.floatValue() - volume.get()) > 0.001f) {
            throw new FdxException("UiKitTest volume progress bar did not mirror slider state: progress="
                    + volumeProgress.floatValue() + ", volume=" + volume.get());
        }
        progressBarChecked = true;
    }

    private void validateTabs() {
        activeDemoTab.set(0);
        settleValidationLayout();
        UiNode tabs = find(root.rootNode(), UiNodeType.TABS, "Demo tabs");
        if (tabs == null) {
            throw new FdxException("UiKitTest could not find tabs node");
        }
        if (tabs.bounds().width() <= 120.0f || tabs.bounds().height() <= 24.0f) {
            throw new FdxException("UiKitTest tabs did not receive usable bounds: "
                    + tabs.bounds().width() + "x" + tabs.bounds().height());
        }
        if (activeDemoTab.get() != 0) {
            throw new FdxException("UiKitTest tabs did not start from the first active tab: " + activeDemoTab.get());
        }
        UiRect bounds = tabs.bounds();
        int x = root.displayX(bounds.x() + bounds.width() * 0.375f);
        int y = root.displayY(bounds.y() + bounds.height() * 0.5f);
        input.dispatchPointerMoved(x, y);
        input.dispatchPointerDown(MouseButton.LEFT, x, y);
        input.dispatchPointerUp(MouseButton.LEFT, x, y);
        settleValidationLayout();
        if (activeDemoTab.get() != 1) {
            throw new FdxException("UiKitTest tabs did not select the second tab: " + activeDemoTab.get());
        }
        activeDemoTab.set(3);
        settleValidationLayout();
        tabs = find(root.rootNode(), UiNodeType.TABS, "Demo tabs");
        if (tabs == null) {
            throw new FdxException("UiKitTest could not find tabs node for border click validation");
        }
        bounds = tabs.bounds();
        x = root.displayX(bounds.x() + bounds.width() * 0.875f);
        y = root.displayY(bounds.y() + 1.0f);
        input.dispatchPointerMoved(x, y);
        input.dispatchPointerDown(MouseButton.LEFT, x, y);
        input.dispatchPointerUp(MouseButton.LEFT, x, y);
        settleValidationLayout();
        if (activeDemoTab.get() != 3) {
            throw new FdxException("UiKitTest tab border click selected the wrong tab: " + activeDemoTab.get());
        }
        activeDemoTab.set(1);
        settleValidationLayout();
        tabsChecked = true;
    }

    private void validateTextSelectionAndCopyPaste() {
        UiNode stringField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "String field");
        UiNode intField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "Integer field");
        UiNode floatField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "Float field");
        if (stringField == null || intField == null || floatField == null) {
            throw new FdxException("UiKitTest could not find text fields for editing validation");
        }
        validateTextInputSession(stringField, intField, floatField);
        stringField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "String field");
        intField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "Integer field");
        floatField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "Float field");
        clickTextInputAt(stringField, 1.0f, 0.5f);
        input.dispatchTextInput("!");
        if (!name.get().endsWith("!")) {
            throw new FdxException("UiKitTest text field hit selection did not place the cursor at the end: "
                    + name.get());
        }
        settleValidationLayout();
        stringField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "String field");
        dragTextInput(stringField, 0.0f, 1.0f);
        pressShortcut(Key.C);
        clickTextInputAt(stringField, 1.0f, 0.5f);
        pressShortcut(Key.V);
        if (!name.get().contains("Player!Player!")) {
            throw new FdxException("UiKitTest text field copy/paste did not duplicate the selected text: "
                    + name.get());
        }
        clickTextInputAt(intField, 1.0f, 0.5f);
        input.dispatchTextInput("abc7");
        clickTextInputAt(floatField, 1.0f, 0.5f);
        input.dispatchTextInput("x.5q");
        textSelectionChecked = true;
    }

    private void validateTextInputSession(UiNode stringField, UiNode intField, UiNode floatField) {
        if (textInputController == null) {
            textInputSessionChecked = true;
            return;
        }
        textInputController.reset();
        clickTextInputAt(stringField, 1.0f, 0.5f);
        textInputController.assertVisible(TextInputType.TEXT, false, "String field");
        if (textInputController.updateCount() <= 0) {
            throw new FdxException("UiKitTest text field did not update the platform text input selection");
        }
        int showCount = textInputController.showCount();
        textInputController.simulatePlatformHidden();
        clickTextInputAt(stringField, 1.0f, 0.5f);
        if (!textInputController.visible() || textInputController.showCount() <= showCount) {
            throw new FdxException("UiKitTest did not reopen platform text input for focused String field");
        }
        clickTextInputAt(intField, 1.0f, 0.5f);
        textInputController.assertVisible(TextInputType.INTEGER, false, "Integer field");
        clickTextInputAt(floatField, 1.0f, 0.5f);
        textInputController.assertVisible(TextInputType.DECIMAL, false, "Float field");
        showValidationSection(SECTION_BUTTONS);
        settleValidationLayout();
        if (textInputController.hideCount() <= 0 || textInputController.visible()) {
            throw new FdxException("UiKitTest text input session was not hidden after leaving text fields");
        }
        showValidationSection(SECTION_TEXT_FIELDS);
        textInputSessionChecked = true;
    }

    private void validateTextArea() {
        UiNode messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        UiNode growArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Auto grow text area");
        if (messageArea == null || growArea == null) {
            throw new FdxException("UiKitTest could not find text area nodes");
        }
        if (messageArea.scrollState() == null) {
            throw new FdxException("UiKitTest message text area did not expose internal scroll state");
        }
        float beforeScroll = messageArea.scrollState().y();
        UiRect bounds = messageArea.bounds();
        input.dispatchScrolled(root.displayX(bounds.x() + bounds.width() * 0.5f),
                root.displayY(bounds.y() + bounds.height() * 0.5f), 0.0f, 3.0f);
        settleValidationLayout();
        messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        if (messageArea.scrollState().y() <= beforeScroll) {
            throw new FdxException("UiKitTest text area wheel scroll did not update internal scroll state: before="
                    + beforeScroll + ", after=" + messageArea.scrollState().y());
        }
        messageArea.scrollState().scrollTo(0.0f, 0.0f);
        settleValidationLayout();
        messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        float beforeTouchDrag = messageArea.scrollState().y();
        if (textInputController != null) {
            textInputController.reset();
        }
        dragTextAreaTouch(messageArea, 0.5f, 0.85f, 0.5f, 0.25f);
        messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        if (messageArea.scrollState().y() <= beforeTouchDrag + 20.0f) {
            throw new FdxException("UiKitTest text area touch drag did not scroll the text area: before="
                    + beforeTouchDrag + ", after=" + messageArea.scrollState().y());
        }
        if (textInputController != null) {
            if (textInputController.visible() || textInputController.showCount() > 0) {
                throw new FdxException("UiKitTest text area touch drag opened platform text input");
            }
        }
        clickTextInputAt(messageArea, 1.0f, 1.0f);
        if (textInputController != null) {
            textInputController.assertVisible(TextInputType.TEXT, true, "Message area");
            TextInputRequest request = textInputController.lastRequest();
            UiRect areaBounds = messageArea.bounds();
            int areaTop = root.displayY(areaBounds.y());
            int areaMid = root.displayY(areaBounds.y() + areaBounds.height() * 0.5f);
            int areaBottom = root.displayY(areaBounds.y() + areaBounds.height());
            if (request.boundsY() <= areaMid || request.boundsY() >= areaBottom) {
                throw new FdxException("UiKitTest text area platform bounds did not track the lower caret line: "
                        + request.boundsY() + " in " + areaTop + ".." + areaBottom);
            }
            if (request.boundsHeight() >= Math.max(1, areaBottom - areaTop)) {
                throw new FdxException("UiKitTest text area platform bounds used the whole widget instead of a line: "
                        + request.boundsHeight() + " >= " + (areaBottom - areaTop));
            }
        }
        input.dispatchTextInput("\nvalidated text area");
        if (messageInput.get().indexOf("validated text area") < 0) {
            throw new FdxException("UiKitTest text area did not accept multiline text input: " + messageInput.get());
        }
        growingMessageInput.set("Auto grow line 1\nline 2\nline 3\nline 4\nline 5\nline 6\nline 7\nline 8");
        settleValidationLayout();
        growArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Auto grow text area");
        if (growArea.bounds().height() <= 52.0f || growArea.bounds().height() > 118.5f) {
            throw new FdxException("UiKitTest auto-grow text area did not respect min/max height: "
                    + growArea.bounds().height());
        }
        textAreaChecked = true;
    }

    private void validateTextInputTouchDrag() {
        if (textInputController == null) {
            textInputTouchDragChecked = true;
            return;
        }
        mediumScroll.scrollTo(0.0f, 0.0f);
        settleValidationLayout();
        UiNode scroll = find(root.rootNode(), UiNodeType.SCROLL, "Medium scroll body");
        UiNode stringField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "Scroll text field");
        if (scroll == null || stringField == null) {
            throw new FdxException("UiKitTest could not find text input touch drag targets");
        }
        if (!mediumScroll.canScrollY()) {
            throw new FdxException("UiKitTest text input scroll body was not scrollable in validation mode: maxY="
                    + mediumScroll.maxY());
        }
        textInputController.reset();
        UiRect scrollBounds = scroll.bounds();
        UiRect fieldBounds = stringField.bounds();
        int x = root.displayX(fieldBounds.x() + fieldBounds.width() * 0.5f);
        int downY = root.displayY(fieldBounds.y() + fieldBounds.height() * 0.5f);
        int moveY = root.displayY(scrollBounds.y() - 96.0f);
        input.dispatchTouchDown(0, x, downY, 1.0f);
        input.dispatchTouchMoved(0, x, moveY, 1.0f);
        input.dispatchTouchUp(0, x, moveY, 1.0f);
        settleValidationLayout();
        if (textInputController.visible() || textInputController.showCount() > 0) {
            throw new FdxException("UiKitTest text field touch drag opened platform text input");
        }
        if (mediumScroll.y() <= 20.0f) {
            throw new FdxException("UiKitTest text field touch drag did not scroll the medium scroll body: "
                    + mediumScroll.y());
        }
        mediumScroll.scrollTo(0.0f, 0.0f);
        showValidationSection(SECTION_BUTTONS);
        settleValidationLayout();
        showValidationSection(SECTION_SCROLLVIEW);
        settleValidationLayout();
        scroll = find(root.rootNode(), UiNodeType.SCROLL, "Medium scroll body");
        stringField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "Scroll text field");
        if (scroll == null || stringField == null) {
            throw new FdxException("UiKitTest could not find scroll text field after touch drag reset");
        }
        UiRect resetScrollBounds = scroll.bounds();
        UiRect resetFieldBounds = stringField.bounds();
        if (resetFieldBounds.y() < resetScrollBounds.y() || resetFieldBounds.bottom() > resetScrollBounds.bottom()) {
            throw new FdxException("UiKitTest scroll text field was not visible after text touch drag reset: field="
                    + resetFieldBounds.y() + ".." + resetFieldBounds.bottom() + ", scroll="
                    + resetScrollBounds.y() + ".." + resetScrollBounds.bottom());
        }
        textInputController.reset();
        touchTextInputAt(stringField, 0.5f, 0.5f);
        textInputController.assertVisible(TextInputType.TEXT, false, "Scroll text field");
        textInputTouchDragChecked = true;
    }

    private void validateScrollBodyDrag() {
        mediumScroll.scrollTo(0.0f, 0.0f);
        settleValidationLayout();
        UiNode scroll = find(root.rootNode(), UiNodeType.SCROLL, "Medium scroll body");
        if (scroll == null) {
            throw new FdxException("UiKitTest could not find the medium scroll body");
        }
        UiRect bounds = scroll.bounds();
        int x = root.displayX(bounds.x() + 12.0f);
        int downY = root.displayY(bounds.y() + 120.0f);
        int moveY = root.displayY(bounds.y() + 36.0f);
        input.dispatchPointerMoved(x, downY);
        input.dispatchPointerDown(MouseButton.LEFT, x, downY);
        input.dispatchPointerMoved(x, moveY);
        input.dispatchPointerUp(MouseButton.LEFT, x, moveY);
        settleValidationLayout();
        if (mediumScroll.y() <= 20.0f) {
            throw new FdxException("UiKitTest scroll view body drag did not update scroll y: " + mediumScroll.y());
        }
        scrollBodyDragChecked = true;
    }

    private void validateScrollChildDrag() {
        navScroll.scrollTo(0.0f, 0.0f);
        activeSection.set(SECTION_BUTTONS);
        settleValidationLayout();
        UiNode nav = find(root.rootNode(), UiNodeType.SCROLL, "Sections nav");
        UiNode checkboxes = find(root.rootNode(), UiNodeType.BUTTON, "Checkboxes");
        if (nav == null || checkboxes == null) {
            throw new FdxException("UiKitTest could not find the sections nav child drag targets");
        }
        if (!navScroll.canScrollY()) {
            throw new FdxException("UiKitTest sections nav was not scrollable in validation mode: maxY="
                    + navScroll.maxY());
        }
        try {
            UiRect navBounds = nav.bounds();
            UiRect bounds = checkboxes.bounds();
            int x = root.displayX(bounds.x() + bounds.width() * 0.5f);
            int downY = root.displayY(bounds.y() + bounds.height() * 0.5f);
            int moveY = root.displayY(navBounds.y() + 10.0f);
            input.dispatchPointerMoved(x, downY);
            input.dispatchPointerDown(MouseButton.LEFT, x, downY);
            input.dispatchPointerMoved(x, moveY);
            input.dispatchPointerUp(MouseButton.LEFT, x, moveY);
            settleValidationLayout();
            if (activeSection.get() != SECTION_BUTTONS) {
                throw new FdxException("UiKitTest section child drag selected a section instead of scrolling: "
                        + activeSection.get());
            }
            if (navScroll.y() <= 20.0f) {
                throw new FdxException("UiKitTest section child drag did not scroll the nav list: " + navScroll.y());
            }
            navScroll.scrollTo(0.0f, 0.0f);
            settleValidationLayout();
            checkboxes = find(root.rootNode(), UiNodeType.BUTTON, "Checkboxes");
            if (checkboxes == null) {
                throw new FdxException("UiKitTest could not find the sections button after nav scroll reset");
            }
            bounds = checkboxes.bounds();
            x = root.displayX(bounds.x() + bounds.width() * 0.5f);
            downY = root.displayY(bounds.y() + bounds.height() * 0.5f);
            input.dispatchPointerMoved(x, downY);
            input.dispatchPointerDown(MouseButton.LEFT, x, downY);
            input.dispatchPointerUp(MouseButton.LEFT, x, downY);
            settleValidationLayout();
            if (activeSection.get() != SECTION_CHECKBOXES) {
                throw new FdxException("UiKitTest section tap did not select after child drag validation: "
                        + activeSection.get());
            }
            scrollChildDragChecked = true;
        } finally {
            navScroll.scrollTo(0.0f, 0.0f);
            settleValidationLayout();
        }
    }

    private void clickTextInputAt(UiNode node, float xPercent, float yPercent) {
        if (node == null) {
            return;
        }
        UiRect bounds = node.bounds();
        int x = root.displayX(bounds.x() + insideExtent(bounds.width(), xPercent));
        int y = root.displayY(bounds.y() + insideExtent(bounds.height(), yPercent));
        input.dispatchPointerMoved(x, y);
        input.dispatchPointerDown(MouseButton.LEFT, x, y);
        input.dispatchPointerUp(MouseButton.LEFT, x, y);
        settleValidationLayout();
    }

    private void touchTextInputAt(UiNode node, float xPercent, float yPercent) {
        if (node == null) {
            return;
        }
        UiRect bounds = node.bounds();
        int x = root.displayX(bounds.x() + insideExtent(bounds.width(), xPercent));
        int y = root.displayY(bounds.y() + insideExtent(bounds.height(), yPercent));
        input.dispatchTouchDown(0, x, y, 1.0f);
        input.dispatchTouchUp(0, x, y, 1.0f);
        settleValidationLayout();
    }

    private void dragTextInput(UiNode node, float startPercent, float endPercent) {
        if (node == null) {
            return;
        }
        UiRect bounds = node.bounds();
        int downX = root.displayX(bounds.x() + bounds.width() * clamp(startPercent, 0.0f, 1.0f));
        int upX = root.displayX(bounds.x() + bounds.width() * clamp(endPercent, 0.0f, 1.0f));
        int y = root.displayY(bounds.y() + bounds.height() * 0.5f);
        input.dispatchPointerMoved(downX, y);
        input.dispatchPointerDown(MouseButton.LEFT, downX, y);
        input.dispatchPointerMoved(upX, y);
        input.dispatchPointerUp(MouseButton.LEFT, upX, y);
        settleValidationLayout();
    }

    private void dragTextAreaTouch(UiNode node, float startXPercent, float startYPercent,
            float endXPercent, float endYPercent) {
        if (node == null) {
            return;
        }
        UiRect bounds = node.bounds();
        int downX = root.displayX(bounds.x() + bounds.width() * clamp(startXPercent, 0.0f, 1.0f));
        int downY = root.displayY(bounds.y() + bounds.height() * clamp(startYPercent, 0.0f, 1.0f));
        int moveX = root.displayX(bounds.x() + bounds.width() * clamp(endXPercent, 0.0f, 1.0f));
        int moveY = root.displayY(bounds.y() + bounds.height() * clamp(endYPercent, 0.0f, 1.0f));
        input.dispatchTouchDown(0, downX, downY, 1.0f);
        input.dispatchTouchMoved(0, moveX, moveY, 1.0f);
        input.dispatchTouchUp(0, moveX, moveY, 1.0f);
        settleValidationLayout();
    }

    private float insideExtent(float extent, float percent) {
        float clamped = clamp(percent, 0.0f, 1.0f);
        float value = extent * clamped;
        if (clamped >= 1.0f && extent > 1.0f) {
            return extent - 1.0f;
        }
        return value;
    }

    private void pressShortcut(Key key) {
        input.dispatchKeyDown(Key.CONTROL_LEFT);
        input.dispatchKeyDown(key);
        input.dispatchKeyUp(key);
        input.dispatchKeyUp(Key.CONTROL_LEFT);
        settleValidationLayout();
    }

    private void applyValidationFrameBudget() {
        if (!validationActive) {
            return;
        }
        long minimumFrames = minimumValidationFrames();
        if (minimumFrames <= 0L) {
            return;
        }
        long baselineFrames = Math.max(exitAfterFrames, minimumFrames);
        long effectiveFrames = minimumFrames;
        if (exitAfterFrames <= 0L) {
            effectiveFrames = Math.max(DEFAULT_VALIDATION_FRAMES, minimumFrames);
            logger.info("UiKitTest forced validation frame limit to " + effectiveFrames);
        } else if (baselineFrames != exitAfterFrames) {
            logger.info("UiKitTest increased validation frame limit from " + exitAfterFrames + " to " + baselineFrames
                    + " to execute the full validation plan.");
            effectiveFrames = baselineFrames;
        }
        exitAfterFrames = effectiveFrames;
    }

    private long minimumValidationFrames() {
        if (validationScenarios.length == 0) {
            return 0L;
        }
        long lastPlanFrame = 0L;
        for (UiKitValidationScenarios.Entry scenario : validationScenarios) {
            if (scenario.frame() > lastPlanFrame) {
                lastPlanFrame = scenario.frame();
            }
        }
        return lastPlanFrame + (desktopImageCaptureEnabled ? 1L : 2L);
    }

    private void settleValidationLayout() {
        if (root != null) {
            root.update(VALIDATION_SETTLE_SECONDS);
            root.rootNode();
        }
    }

    private void executeValidationPlan() {
        long validationFrame = desktopImageCaptureEnabled ? renderedFrames : renderedFrames - 1L;
        if (validationFrame < 0L) {
            return;
        }
        while (nextValidationScenario < validationScenarios.length
                && validationScenarios[nextValidationScenario].frame() <= validationFrame) {
            if (validationConfig.stepDelaySeconds() > 0.0f && nextValidationScenario > 0
                    && validationElapsedSeconds + 0.0001f < nextValidationScenarioSeconds) {
                return;
            }
            UiKitValidationScenarios.Entry scenario = validationScenarios[nextValidationScenario++];
            ScenarioResult result = scenarioHost.run(scenario.scenario());
            if (!result.passed()) {
                throw new FdxException("UiKitTest scenario failed: " + scenario.name()
                        + ", operation=" + result.operationName()
                        + ", message=" + result.message()
                        + ", events=" + result.recentEvents());
            }
            queueValidationCapture(scenario.name(), scenario.captureImage(), scenario.validateVisual());
            if (validationConfig.stepDelaySeconds() > 0.0f) {
                nextValidationScenarioSeconds = validationElapsedSeconds + validationConfig.stepDelaySeconds();
                return;
            }
        }
    }

    private boolean validationCanExit() {
        return !validationActive || nextValidationScenario >= validationScenarios.length;
    }

    private void queueValidationCapture(String scenario, boolean captureImage, boolean validateVisual) {
        if (!desktopImageCaptureEnabled) {
            return;
        }
        String safeScenario = safeScenarioName(scenario);
        Integer combined = pendingValidationCaptures.get(safeScenario);
        int flags = combined == null ? 0 : combined;
        if (captureImage) {
            flags |= 1;
        }
        if (validateVisual) {
            flags |= 2;
        }
        if (flags != 0) {
            pendingValidationCaptures.put(safeScenario, flags);
        }
    }

    private void flushPendingValidationCaptures() {
        if (pendingValidationCaptures.isEmpty()) {
            return;
        }
        CapturedFrame capturedFrame = null;
        for (java.util.Map.Entry<String, Integer> pending : pendingValidationCaptures.entrySet()) {
            int flags = pending.getValue();
            boolean requestCapture = (flags & 1) != 0;
            boolean requestValidate = shouldValidateVisual(pending.getKey(), flags);
            if (requestCapture || requestValidate) {
                if (capturedFrame == null) {
                    capturedFrame = captureCurrentFrame();
                }
                captureValidationFrame(pending.getKey(), requestCapture, requestValidate, capturedFrame);
            }
        }
        pendingValidationCaptures.clear();
    }

    private boolean shouldValidateVisual(String safeScenario, int flags) {
        if ((flags & 2) != 0) {
            return true;
        }
        return visualValidate && !REQUESTED_SCENARIO.equals(safeScenario);
    }

    private void captureRequestedFrame() {
        if (captureOutputRequested && !captured && renderedFrames >= captureFrame) {
            queueValidationCapture(REQUESTED_SCENARIO, true, false);
        }
    }

    private void captureValidationFrame(String scenario, boolean requestCapture, boolean requestValidate) {
        if (!requestCapture && !requestValidate) {
            return;
        }
        CapturedFrame frame = captureCurrentFrame();
        captureValidationFrame(scenario, requestCapture, requestValidate, frame);
    }

    private void captureValidationFrame(String scenario, boolean requestCapture, boolean requestValidate, CapturedFrame image) {
        if (!requestCapture && !requestValidate) {
            return;
        }
        if (requestCapture) {
            writeImage(image, resolveCapturePath(captureTemplate, scenario, renderedFrames), "capture");
            captured = captured || shouldPersistCaptureOutput();
        }
        if (requestValidate) {
            validateVisualMatch(scenario, renderedFrames, image);
        }
    }

    private boolean shouldCaptureEveryFrame() {
        return captureEveryFrames > 0 && renderedFrames > 0L && renderedFrames % captureEveryFrames == 0L;
    }

    private CapturedFrame captureCurrentFrame() {
        io.github.libfdx.graphics.GraphicsFrame frame = graphics.currentFrame();
        int width = frame.width();
        int height = frame.height();
        ByteBuffer pixels = frame.frameBuffer().readPixelsRgba8();
        int[] image = new int[width * height];
        for (int y = 0; y < height; y++) {
            int sourceY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                int offset = (sourceY * width + x) * 4;
                int red = pixels.get(offset) & 0xff;
                int green = pixels.get(offset + 1) & 0xff;
                int blue = pixels.get(offset + 2) & 0xff;
                int alpha = pixels.get(offset + 3) & 0xff;
                image[y * width + x] = (alpha << 24) | (red << 16) | (green << 8) | blue;
            }
        }
        return new CapturedFrame(width, height, image);
    }

    private void captureCurrentValidationFrame(String scenario) {
        queueValidationCapture(scenario, true, false);
    }

    private void validateVisualMatch(String scenario, long frame, CapturedFrame current) {
        if (!visualValidate) {
            return;
        }
        String safeScenario = safeScenarioName(scenario);
        String baselinePath = resolveCapturePath(baselineTemplate, safeScenario, frame);
        File baseline = new File(baselineDirectory, baselinePath);
        if (!baseline.isFile()) {
            if (visualValidateRequireBaselines) {
                throw new FdxException("UiKitTest visual baseline not found for scenario " + safeScenario + ": "
                        + baseline.getAbsolutePath());
            }
            if (logger != null) {
                logger.info("UiKitTest visual baseline not found for scenario " + safeScenario + ", skipping compare: "
                        + baseline.getAbsolutePath());
            }
            return;
        }

        CapturedFrame expected = readImage(baseline, safeScenario);
        if (expected.width != current.width || expected.height != current.height) {
            throw new FdxException("UiKitTest visual dimension mismatch for " + safeScenario
                    + ": expected " + expected.width + "x" + expected.height
                    + ", actual " + current.width + "x" + current.height);
        }

        int width = current.width;
        int height = current.height;
        int mismatchPixels = 0;
        int maxChannelDiff = 0;
        int total = width * height;
        for (int i = 0; i < total; i++) {
            int actual = current.argb[i];
            int expectedPixel = expected.argb[i];
            int diffR = Math.abs(((actual >>> 16) & 0xff) - ((expectedPixel >>> 16) & 0xff));
            int diffG = Math.abs(((actual >>> 8) & 0xff) - ((expectedPixel >>> 8) & 0xff));
            int diffB = Math.abs((actual & 0xff) - (expectedPixel & 0xff));
            int pixelMax = Math.max(Math.max(diffR, diffG), diffB);
            if (pixelMax > visualChannelTolerance) {
                mismatchPixels++;
            }
            if (pixelMax > maxChannelDiff) {
                maxChannelDiff = pixelMax;
            }
        }

        float mismatchRatio = total == 0 ? 1.0f : (float) mismatchPixels / (float) total;
        if (mismatchRatio > visualMismatchRatio) {
            if (captureOutputRequested || captureEveryFrames > 0) {
                writeImage(current, resolveCapturePath("build/reports/uikit/{scenario}-{frame}-mismatch.png", scenario, renderedFrames),
                        "visual mismatch");
            }
            throw new FdxException("UiKitTest visual check failed for " + safeScenario
                    + ": mismatch ratio=" + mismatchRatio + ", max channel diff=" + maxChannelDiff
                    + ", total=" + total + ", mismatched=" + mismatchPixels);
        }
    }

    private boolean shouldPersistCaptureOutput() {
        return captureOutputRequested && (captureFrame <= renderedFrames || captureEveryFrames > 0);
    }

    private boolean desktopImageCaptureAvailable() {
        return Boolean.parseBoolean(System.getProperty("libfdx.test.desktopImageCapture", "true"));
    }

    private CapturedFrame readImage(File file, String safeScenario) {
        try {
            Class<?> imageIoClass = Class.forName("javax.imageio.ImageIO");
            Object buffered = imageIoClass.getMethod("read", File.class).invoke(null, file);
            if (buffered == null) {
                throw new FdxException("UiKitTest read invalid baseline image for scenario " + safeScenario);
            }
            Class<?> imageClass = buffered.getClass();
            int width = ((Integer) imageClass.getMethod("getWidth").invoke(buffered)).intValue();
            int height = ((Integer) imageClass.getMethod("getHeight").invoke(buffered)).intValue();
            int[] argb = (int[]) imageClass
                    .getMethod("getRGB", int.class, int.class, int.class, int.class, int[].class, int.class, int.class)
                    .invoke(buffered, 0, 0, width, height, null, 0, width);
            return new CapturedFrame(width, height, argb);
        } catch (FdxException e) {
            throw e;
        } catch (Exception e) {
            throw new FdxException("UiKitTest could not read baseline for scenario " + safeScenario, e);
        }
    }

    private void writeImage(CapturedFrame image, String path, String actionLabel) {
        try {
            File output = new File(path);
            File parent = output.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            writeDesktopPng(image, output);
            if (logger != null) {
                logger.info("UiKitTest " + actionLabel + " " + output.getPath());
            }
        } catch (Exception e) {
            throw new FdxException("Could not write image for " + actionLabel, e);
        }
    }

    private void writeDesktopPng(CapturedFrame image, File output) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream((image.width * 4 + 1) * image.height);
        for (int y = 0; y < image.height; y++) {
            raw.write(0);
            int row = y * image.width;
            for (int x = 0; x < image.width; x++) {
                int argb = image.argb[row + x];
                raw.write((argb >>> 16) & 0xff);
                raw.write((argb >>> 8) & 0xff);
                raw.write(argb & 0xff);
                raw.write((argb >>> 24) & 0xff);
            }
        }

        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        byte[] rawBytes = raw.toByteArray();
        deflater.setInput(rawBytes);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(rawBytes.length);
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            compressed.write(buffer, 0, count);
        }
        deflater.end();

        ByteArrayOutputStream ihdrBytes = new ByteArrayOutputStream(13);
        DataOutputStream ihdr = new DataOutputStream(ihdrBytes);
        ihdr.writeInt(image.width);
        ihdr.writeInt(image.height);
        ihdr.writeByte(8);
        ihdr.writeByte(6);
        ihdr.writeByte(0);
        ihdr.writeByte(0);
        ihdr.writeByte(0);
        ihdr.flush();

        DataOutputStream png = new DataOutputStream(new FileOutputStream(output));
        try {
            png.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            writePngChunk(png, "IHDR", ihdrBytes.toByteArray());
            writePngChunk(png, "IDAT", compressed.toByteArray());
            writePngChunk(png, "IEND", new byte[0]);
        } finally {
            png.close();
        }
    }

    private void writePngChunk(DataOutputStream png, String type, byte[] data) throws Exception {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        png.writeInt(data.length);
        png.write(typeBytes);
        png.write(data);
        png.writeInt((int) crc.getValue());
    }

    private static final class CapturedFrame {
        final int width;
        final int height;
        final int[] argb;

        CapturedFrame(int width, int height, int[] argb) {
            this.width = width;
            this.height = height;
            this.argb = argb;
        }
    }

    private static final class RecordingTextInputController implements TextInputController {
        private TextInputRequest lastRequest = TextInputRequest.builder().build();
        private int showCount;
        private int updateCount;
        private int hideCount;
        private boolean visible;

        @Override
        public void showTextInput(TextInputRequest request) {
            lastRequest = request != null ? request : TextInputRequest.builder().build();
            showCount++;
            visible = true;
        }

        @Override
        public void updateTextInput(TextInputRequest request) {
            lastRequest = request != null ? request : TextInputRequest.builder().build();
            updateCount++;
        }

        @Override
        public void hideTextInput() {
            hideCount++;
            visible = false;
        }

        void reset() {
            lastRequest = TextInputRequest.builder().build();
            showCount = 0;
            updateCount = 0;
            hideCount = 0;
            visible = false;
        }

        int updateCount() {
            return updateCount;
        }

        int showCount() {
            return showCount;
        }

        int hideCount() {
            return hideCount;
        }

        boolean visible() {
            return visible;
        }

        TextInputRequest lastRequest() {
            return lastRequest;
        }

        void simulatePlatformHidden() {
            visible = false;
        }

        void assertVisible(TextInputType expectedType, boolean expectedMultiline, String label) {
            if (!visible || showCount <= 0) {
                throw new FdxException("UiKitTest did not show text input for " + label);
            }
            if (lastRequest.type() != expectedType) {
                throw new FdxException("UiKitTest text input type mismatch for " + label + ": "
                        + lastRequest.type());
            }
            if (lastRequest.multiline() != expectedMultiline) {
                throw new FdxException("UiKitTest text input multiline mismatch for " + label + ": "
                        + lastRequest.multiline());
            }
            if (lastRequest.readOnly()) {
                throw new FdxException("UiKitTest requested a read-only platform text input for " + label);
            }
            if (!lastRequest.hasBounds() || lastRequest.boundsWidth() <= 0 || lastRequest.boundsHeight() <= 0) {
                throw new FdxException("UiKitTest text input request did not include visible bounds for " + label);
            }
        }
    }

    private String resolveCapturePath(String template, String scenario, long frame) {
        String targetScenario = safeScenarioName(scenario);
        String path = template;
        path = path.replace("{scenario}", targetScenario);
        path = path.replace("{frame}", String.valueOf(frame));
        path = path.replace("{name}", targetScenario);
        return path;
    }

    private String safeScenarioName(String label) {
        if (label == null || label.length() == 0) {
            return "uikit";
        }
        String normalized = label.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-\\.]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return normalized.length() == 0 ? "uikit" : normalized;
    }

    private String trimOrEmpty(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    private void driveCaptureInput() {
        if (hoverLabel == null || hoverLabel.length() == 0) {
            return;
        }
        UiNode node = find(root.rootNode(), UiNodeType.BUTTON, hoverLabel);
        if (node == null) {
            node = find(root.rootNode(), UiNodeType.TEXT, hoverLabel);
        }
        if (node == null) {
            return;
        }
        UiRect bounds = node.bounds();
        input.dispatchPointerMoved(root.displayX(bounds.x() + bounds.width() * 0.5f),
                root.displayY(bounds.y() + bounds.height() * 0.5f));
    }

    private void checkPopupPassThrough() {
        int before = clickCount.get();
        click(find(root.rootNode(), UiNodeType.BUTTON, "Press"));
        if (clickCount.get() <= before) {
            throw new FdxException("UiKitTest non-blocking popup did not allow input behind it");
        }
        popupPassthroughChecked = true;
    }

    private void checkPopupBlocking() {
        int before = clickCount.get();
        click(find(root.rootNode(), UiNodeType.BUTTON, "Press"));
        if (clickCount.get() != before) {
            throw new FdxException("UiKitTest blocking popup allowed input behind it");
        }
        click(find(root.rootNode(), UiNodeType.BUTTON, "Keep"));
        if (clickCount.get() <= before) {
            throw new FdxException("UiKitTest blocking popup did not allow input inside the popup");
        }
        click(find(root.rootNode(), UiNodeType.CHECKBOX, "Block input"));
        if (blockPopupInput.get()) {
            throw new FdxException("UiKitTest blocking popup did not allow turning blocking input off inside popup");
        }
        popupBlockingChecked = true;
    }

    private void dragVolumeSlider() {
        dragSlider("Volume setting", 0.20f, 1.35f);
    }

    private void validateTextSizeSliderReleaseCommit() {
        UiNode slider = find(root.rootNode(), UiNodeType.SLIDER, "Text size setting");
        if (slider == null) {
            throw new FdxException("UiKitTest could not find the text size slider");
        }
        UiRect bounds = slider.bounds();
        int downX = root.displayX(bounds.x() + bounds.width() * 0.50f);
        int y = root.displayY(bounds.y() + bounds.height() * 0.5f);
        int moveX = root.displayX(bounds.x() + bounds.width() * 0.78f);
        float appliedBefore = root.uiScale();

        input.dispatchTouchDown(0, downX, y, 1.0f);
        input.dispatchTouchMoved(0, moveX, y, 1.0f);
        if (Math.abs(uiScale.get() - appliedBefore) < 0.01f) {
            input.dispatchTouchUp(0, moveX, y, 1.0f);
            throw new FdxException("UiKitTest text size touch drag did not update the slider state");
        }
        applyGlobalScale();
        if (Math.abs(root.uiScale() - appliedBefore) > 0.0001f) {
            input.dispatchTouchUp(0, moveX, y, 1.0f);
            throw new FdxException("UiKitTest text size scale applied before touch release: before="
                    + appliedBefore + ", during=" + root.uiScale() + ", target=" + uiScale.get());
        }

        input.dispatchTouchUp(0, moveX, y, 1.0f);
        applyGlobalScale();
        if (Math.abs(root.uiScale() - uiScale.get()) > 0.0001f) {
            throw new FdxException("UiKitTest text size scale did not apply after touch release: root="
                    + root.uiScale() + ", target=" + uiScale.get());
        }
        textScaleReleaseCommitChecked = true;
        settleValidationLayout();
    }

    private void dragSlider(String semanticLabel, float progress) {
        UiNode slider = find(root.rootNode(), UiNodeType.SLIDER, semanticLabel);
        if (slider == null) {
            return;
        }
        UiRect bounds = slider.bounds();
        int x = root.displayX(bounds.x() + bounds.width() * progress);
        int y = root.displayY(bounds.y() + bounds.height() * 0.5f);
        input.dispatchPointerMoved(x, y);
        input.dispatchPointerDown(MouseButton.LEFT, x, y);
        input.dispatchPointerUp(MouseButton.LEFT, x, y);
    }

    private void dragSlider(String semanticLabel, float startProgress, float endProgress) {
        UiNode slider = find(root.rootNode(), UiNodeType.SLIDER, semanticLabel);
        if (slider == null) {
            return;
        }
        UiRect bounds = slider.bounds();
        int downX = root.displayX(bounds.x() + bounds.width() * startProgress);
        int downY = root.displayY(bounds.y() + bounds.height() * 0.5f);
        int moveX = root.displayX(bounds.x() + bounds.width() * endProgress);
        int moveY = root.displayY(bounds.y() + bounds.height() * 0.5f);
        input.dispatchPointerMoved(downX, downY);
        input.dispatchPointerDown(MouseButton.LEFT, downX, downY);
        input.dispatchPointerMoved(moveX, moveY);
        input.dispatchPointerUp(MouseButton.LEFT, moveX, moveY);
        settleValidationLayout();
    }

    private void dragWindow(UiNode window, float deltaX, float deltaY) {
        if (window == null) {
            return;
        }
        UiRect bounds = window.bounds();
        float startX = bounds.x() + Math.min(92.0f, bounds.width() * 0.35f);
        float startY = bounds.y() + 14.0f;
        int downX = root.displayX(startX);
        int downY = root.displayY(startY);
        int moveX = root.displayX(startX + deltaX);
        int moveY = root.displayY(startY + deltaY);
        input.dispatchPointerMoved(downX, downY);
        input.dispatchPointerDown(MouseButton.LEFT, downX, downY);
        input.dispatchPointerMoved(moveX, moveY);
        input.dispatchPointerUp(MouseButton.LEFT, moveX, moveY);
        settleValidationLayout();
    }

    private void validateWindowDrag(UiNode window, float deltaX, float deltaY) {
        if (window == null) {
            throw new FdxException("UiKitTest could not find the movable window");
        }
        float beforeX = toolsWindow.x();
        float beforeY = toolsWindow.y();
        UiRect area = root.rootNode().bounds();
        float expectedX = clamp(beforeX + deltaX, area.x(), Math.max(area.x(), area.right() - toolsWindow.width()));
        float expectedY = clamp(beforeY + deltaY, area.y(), Math.max(area.y(), area.bottom() - toolsWindow.height()));
        dragWindow(window, deltaX, deltaY);
        if (Math.abs(toolsWindow.x() - expectedX) > 0.5f || Math.abs(toolsWindow.y() - expectedY) > 0.5f) {
            throw new FdxException("UiKitTest movable window did not clamp to the expected drag position: actual="
                    + toolsWindow.x() + "," + toolsWindow.y() + ", expected=" + expectedX + "," + expectedY);
        }
        if (Math.abs(toolsWindow.x() - beforeX) <= 0.5f && Math.abs(toolsWindow.y() - beforeY) <= 0.5f) {
            throw new FdxException("UiKitTest movable window did not move after title-bar drag");
        }
        windowMoveChecked = true;
    }

    private void resizeWindow(UiNode window, float deltaX, float deltaY) {
        if (window == null) {
            return;
        }
        UiRect bounds = window.bounds();
        float startX = bounds.right() - 6.0f;
        float startY = bounds.bottom() - 6.0f;
        int downX = root.displayX(startX);
        int downY = root.displayY(startY);
        int moveX = root.displayX(startX + deltaX);
        int moveY = root.displayY(startY + deltaY);
        input.dispatchPointerMoved(downX, downY);
        input.dispatchPointerDown(MouseButton.LEFT, downX, downY);
        input.dispatchPointerMoved(moveX, moveY);
        input.dispatchPointerUp(MouseButton.LEFT, moveX, moveY);
    }

    private void validateWindowResize(UiNode window, float deltaX, float deltaY) {
        if (window == null) {
            throw new FdxException("UiKitTest could not find the resizable window");
        }
        float beforeWidth = statsWindow.width();
        float beforeHeight = statsWindow.height();
        UiRect area = root.rootNode().bounds();
        float expectedWidth = clamp(beforeWidth + deltaX, statsWindow.minWidth(),
                Math.max(statsWindow.minWidth(), area.right() - statsWindow.x()));
        float expectedHeight = clamp(beforeHeight + deltaY, statsWindow.minHeight(),
                Math.max(statsWindow.minHeight(), area.bottom() - statsWindow.y()));
        resizeWindow(window, deltaX, deltaY);
        if (Math.abs(statsWindow.width() - expectedWidth) > 0.5f
                || Math.abs(statsWindow.height() - expectedHeight) > 0.5f) {
            throw new FdxException("UiKitTest resizable window did not clamp to the expected size: actual="
                    + statsWindow.width() + "x" + statsWindow.height()
                    + ", expected=" + expectedWidth + "x" + expectedHeight);
        }
        if (Math.abs(statsWindow.width() - beforeWidth) <= 0.5f
                && Math.abs(statsWindow.height() - beforeHeight) <= 0.5f) {
            throw new FdxException("UiKitTest resizable window did not resize from the corner handle");
        }
        windowResizeChecked = true;
    }

    private float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private void dragWindowPastEdge(UiNode window) {
        if (window == null) {
            throw new FdxException("UiKitTest could not find the movable window for the edge move check");
        }
        edgeMoveChecked = true;
        UiRect area = root.rootNode().bounds();
        float maxX = Math.max(area.x(), area.right() - toolsWindow.width());
        float maxY = Math.max(area.y(), area.bottom() - toolsWindow.height());
        dragWindow(window, 2000.0f, 2000.0f);
        if (toolsWindow.x() > maxX + 0.5f || toolsWindow.y() > maxY + 0.5f) {
            throw new FdxException("UiKitTest edge move grew beyond the available layout area: area="
                    + area.x() + "," + area.y() + " " + area.width() + "x" + area.height()
                    + ", window=" + toolsWindow.x() + "," + toolsWindow.y() + " "
                    + toolsWindow.width() + "x" + toolsWindow.height()
                    + ", max=" + maxX + "," + maxY);
        }
        if (toolsWindow.x() < maxX - 1.0f || toolsWindow.y() < maxY - 1.0f) {
            throw new FdxException("UiKitTest edge move did not reach the available layout area: area="
                    + area.x() + "," + area.y() + " " + area.width() + "x" + area.height()
                    + ", window=" + toolsWindow.x() + "," + toolsWindow.y() + " "
                    + toolsWindow.width() + "x" + toolsWindow.height()
                    + ", max=" + maxX + "," + maxY);
        }
    }

    private void resizeWindowPastEdge(UiNode window) {
        if (window == null) {
            throw new FdxException("UiKitTest could not find the resizable window for the edge resize check");
        }
        edgeResizeChecked = true;
        float startX = statsWindow.x();
        float startY = statsWindow.y();
        UiRect area = root.rootNode().bounds();
        float maxWidth = Math.max(statsWindow.minWidth(), area.right() - startX);
        float maxHeight = Math.max(statsWindow.minHeight(), area.bottom() - startY);
        resizeWindow(window, 2000.0f, 2000.0f);
        if (Math.abs(statsWindow.x() - startX) > 0.5f || Math.abs(statsWindow.y() - startY) > 0.5f) {
            throw new FdxException("UiKitTest edge resize moved the anchored window origin");
        }
        if (statsWindow.width() > maxWidth + 0.5f || statsWindow.height() > maxHeight + 0.5f) {
            throw new FdxException("UiKitTest edge resize grew beyond the available layout area: area="
                    + area.x() + "," + area.y() + " " + area.width() + "x" + area.height()
                    + ", window=" + statsWindow.x() + "," + statsWindow.y() + " "
                    + statsWindow.width() + "x" + statsWindow.height()
                    + ", max=" + maxWidth + "x" + maxHeight);
        }
        if (statsWindow.width() < maxWidth - 1.0f || statsWindow.height() < maxHeight - 1.0f) {
            throw new FdxException("UiKitTest edge resize did not reach the available layout area: area="
                    + area.x() + "," + area.y() + " " + area.width() + "x" + area.height()
                    + ", window=" + statsWindow.x() + "," + statsWindow.y() + " "
                    + statsWindow.width() + "x" + statsWindow.height()
                    + ", max=" + maxWidth + "x" + maxHeight);
        }
    }

    private void click(UiNode node) {
        pointerDown(node);
        pointerUp(node);
    }

    private void pointerDown(UiNode node) {
        if (node == null) {
            return;
        }
        UiRect bounds = node.bounds();
        int x = root.displayX(bounds.x() + bounds.width() * 0.5f);
        int y = root.displayY(bounds.y() + bounds.height() * 0.5f);
        input.dispatchPointerMoved(x, y);
        input.dispatchPointerDown(MouseButton.LEFT, x, y);
    }

    private void pointerUp(UiNode node) {
        if (node == null) {
            return;
        }
        UiRect bounds = node.bounds();
        int x = root.displayX(bounds.x() + bounds.width() * 0.5f);
        int y = root.displayY(bounds.y() + bounds.height() * 0.5f);
        input.dispatchPointerUp(MouseButton.LEFT, x, y);
    }

    private void incrementClick() {
        clickCount.set(clickCount.get() + 1);
    }


    private String statusText() {
        return "clicks " + clickCount.get() + "  checked " + checked.get()
                + "  volume " + percent(volume.get()) + "  name " + name.get();
    }

    private int messageLineCount() {
        String value = messageInput != null ? messageInput.get() : "";
        int count = 1;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private String scaleValue(float value) {
        return Math.round(value * 100.0f) / 100.0f + "x";
    }

    private String contentScaleValue() {
        return scaleValue(display != null ? display.contentScale() : 1.0f);
    }

    private String percent(float value) {
        return Math.round(value * 100.0f) + "%";
    }

    private String sliderValue(float value, float minimum, float maximum) {
        if (maximum == 100.0f) {
            return String.valueOf(Math.round(value));
        }
        if (minimum < 0.0f) {
            return signedValue(value);
        }
        return percent(value);
    }

    private String signedValue(float value) {
        int rounded = Math.round(value * 100.0f);
        if (rounded > 0) {
            return "+" + rounded + "%";
        }
        return rounded + "%";
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 640;
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 480;
    }

    private UiNode find(UiNode node, UiNodeType type, String text) {
        if (node == null || !node.visible()) {
            return null;
        }
        if (node.type() == type && (text == null || text.equals(nodeLabel(node)))) {
            return node;
        }
        for (UiNode child : node.children()) {
            UiNode found = find(child, type, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String nodeLabel(UiNode node) {
        if (node == null) {
            return null;
        }
        if (node.text() != null) {
            return node.text();
        }
        return node.modifier().semanticLabel();
    }

}
