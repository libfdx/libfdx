package io.github.libfdx.tests.utils.uikittest;

import static io.github.libfdx.tests.utils.uikittest.validation.UiKitTestInput.find;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.input.Clipboard;
import io.github.libfdx.input.Cursor;
import io.github.libfdx.input.DefaultCursor;
import io.github.libfdx.input.DefaultGamepads;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.DefaultInputCapabilities;
import io.github.libfdx.input.Gamepads;
import io.github.libfdx.input.Input;
import io.github.libfdx.input.InputCapabilities;
import io.github.libfdx.tests.utils.shared.TestFpsLogger;
import io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase;
import io.github.libfdx.tests.utils.uikittest.validation.RecordingTextInputController;
import io.github.libfdx.tests.utils.uikittest.validation.UiKitScenarioRunner;
import io.github.libfdx.tests.utils.uikittest.validation.UiKitTestCapture;
import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiNodeType;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiToolkit;
import io.github.libfdx.validation.scenario.ScenarioValidationConfig;
import io.github.libfdx.validation.scenario.ScenarioValidationMode;

/** Owns the UI Kit test assets, root, and frame lifecycle. */
public final class UiKitTestSession {
    private final UiKitShowcase showcase = new UiKitShowcase(false);
    private long exitAfterFrames;
    private Application application;
    private GraphicsContext graphics;
    private AssetManager assets;
    private Runnable assetSetup;
    private Logger logger;
    private UiRoot root;
    private DefaultInput input;
    private boolean created;
    private String hoverLabel;
    private TestFpsLogger fpsLogger;
    private RuntimeException renderFailure;
    private long renderedFrames;
    private UiKitTestCapture capture;
    private UiKitTestPerformance performance;
    private UiKitScenarioRunner scenarios;

    public UiKitTestSession(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    /**
     * Initializes the application with the libFDX runtime root.
     *
     * @param fdx the libFDX runtime root
     */
    public void create(Fdx fdx) {
        application = fdx.app();
        Display display = fdx.displays().main();
        graphics = fdx.graphics().main();
        logger = fdx.logger();
        exitAfterFrames = Math.max(0L, exitAfterFrames);
        boolean driveAutomation = Boolean.parseBoolean(System.getProperty("libfdx.test.driveInput", "false"));
        hoverLabel = System.getProperty("libfdx.test.hoverLabel", "");
        boolean validationEnabled = Boolean.parseBoolean(System.getProperty("libfdx.test.validate", "true"));
        boolean validationActive = validationEnabled && driveAutomation;
        ScenarioValidationConfig validationConfig = ScenarioValidationConfig.fromSystemProperties();
        capture = new UiKitTestCapture(graphics, logger,
                validationEnabled && validationConfig.mode() != ScenarioValidationMode.BEHAVIOR);
        fpsLogger = TestFpsLogger.create(logger, "UiKitTest");
        performance = new UiKitTestPerformance(logger);

        assets = new DefaultAssetManager(fdx.files());
        G2DAssetLoaders.register(assets, graphics);
        UiKitShowcase.loadAssets(assets);
        assetSetup = () -> createLoadedAssets(fdx, display, validationConfig, driveAutomation, validationActive);
    }

    private void createLoadedAssets(Fdx fdx, Display display, ScenarioValidationConfig validationConfig,
            boolean driveAutomation, boolean validationActive) {
        showcase.initialize(assets, display, validationActive);
        Input backendInput = fdx.input();
        Input rootInput;
        RecordingTextInputController textInputController = null;
        if (validationActive) {
            textInputController = new RecordingTextInputController();
            Clipboard backendClipboard = backendInput != null ? backendInput.clipboard() : null;
            input = new DefaultInput(ProviderId.of("uikit_validation_input"), inputCapabilities(backendInput),
                    inputCursor(backendInput), inputGamepads(backendInput), textInputController, backendClipboard);
            rootInput = input;
        } else {
            input = backendInput instanceof DefaultInput ? backendInput.<DefaultInput>as() : new DefaultInput();
            rootInput = backendInput instanceof DefaultInput ? backendInput : input;
        }

        root = new UiToolkit(fdx.files()).root(display, graphics).input(rootInput);
        showcase.configureRoot(root);
        root.setContent(showcase.content());
        if (validationActive) {
            scenarios = new UiKitScenarioRunner(showcase, root, input, display, logger,
                    textInputController, validationConfig, capture);
            exitAfterFrames = scenarios.exitFrameLimit(exitAfterFrames);
        }
        created = true;
        logger.info("UiKitTest created. Validation mode="
                + (validationActive ? "AUTOMATION" : "IDLE")
                + ", frames=" + exitAfterFrames
                + ", driveInput=" + driveAutomation
                + ", selection=" + validationConfig.selection()
                + ", scenarioMode=" + validationConfig.mode()
                + ", capturePolicy=" + validationConfig.capturePolicy()
                + ", events=" + validationConfig.eventsEnabled()
                + ", timeoutMs=" + validationConfig.timeoutMillis()
                + ", stepDelaySeconds=" + validationConfig.stepDelaySeconds()
                + ", visualValidate=" + capture.visualValidationEnabled()
                + ", capture=" + capture.template());
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

    /**
     * Handles a size change.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    public void resize(int width, int height) {
        if (root != null) {
            root.resize(width, height);
        }
    }

    /**
     * Renders the current content.
     */
    public void render() {
        boolean assetsFinished = assets.update(4, 1_000_000L);
        if (assetSetup != null) {
            if (!assetsFinished) {
                graphics.clear(0.02f, 0.025f, 0.04f, 1.0f);
                return;
            }
            Runnable setup = assetSetup;
            assetSetup = null;
            setup.run();
        }
        graphics.clear(0.045f, 0.052f, 0.066f, 1.0f);
        float deltaSeconds = 0.0f;
        try {
            showcase.applyGlobalScale(input);
            if (scenarios != null && !capture.available()) {
                scenarios.execute(renderedFrames);
            }
            deltaSeconds = application.deltaTime();
            if (scenarios != null) {
                scenarios.advanceTime(deltaSeconds);
            }
            boolean trackUiPerf = performance.enabled();
            long updateStart = trackUiPerf ? System.nanoTime() : 0L;
            root.update(deltaSeconds);
            long updateEnd = trackUiPerf ? System.nanoTime() : 0L;
            if (scenarios != null && capture.available()) {
                scenarios.execute(renderedFrames);
            } else if (exitAfterFrames > 0L) {
                driveCaptureInput();
            }
            root.debugLines(showcase.visualDebug());
            long renderStart = trackUiPerf ? System.nanoTime() : 0L;
            root.render();
            if (trackUiPerf) {
                long renderEnd = System.nanoTime();
                performance.recordFrame(deltaSeconds, updateEnd - updateStart, renderEnd - renderStart, renderEnd - updateStart);
            }
            capture.afterRender(renderedFrames);
            if (scenarios != null) {
                scenarios.throwPendingFailure();
            }
        } catch (RuntimeException error) {
            renderFailure = error;
            throw error;
        }

        renderedFrames++;
        fpsLogger.frame(deltaSeconds, renderedFrames);
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames && (scenarios == null || scenarios.complete())) {
            application.requestExit();
        }
    }

    /**
     * Releases resources held by this instance.
     */
    public void dispose() {
        boolean cancelledLoading = assetSetup != null;
        assetSetup = null;
        if (root != null) {
            root.dispose();
            root = null;
            showcase.detachRoot();
        }
        if (assets != null) {
            assets.dispose();
            assets = null;
        }
        if (renderFailure != null) {
            throw new FdxException("UiKitTest failed during render", renderFailure);
        }
        if (cancelledLoading && exitAfterFrames == 0L) {
            return;
        }
        if (!created) {
            throw new FdxException("UiKitTest did not create UI resources");
        }
        if (scenarios != null) {
            scenarios.verifyCompletion(renderedFrames, exitAfterFrames);
        }
        if (logger != null) {
            logger.info("UiKitTest rendered " + renderedFrames + " frames");
        }
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

}
