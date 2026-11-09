package io.github.libfdx.tests.utils.uikittest.validation;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.validation.scenario.ScenarioCapture;
import io.github.libfdx.validation.scenario.ScenarioCapturePolicy;
import io.github.libfdx.validation.scenario.ScenarioHost;
import io.github.libfdx.validation.scenario.ScenarioResult;
import io.github.libfdx.validation.scenario.ScenarioValidationConfig;

/** Schedules validation scenarios and defers failures until the frame is captured. */
public final class UiKitScenarioRunner {
    private static final long VALIDATION_WAIT_FRAME_MILLIS = 100L;

    private final Logger logger;
    private final UiKitTestCapture capture;
    private final UiKitBehaviorPlan behavior;
    private final ScenarioValidationConfig validationConfig;
    private final ScenarioHost scenarioHost;
    private final UiKitValidationScenarios.Entry[] validationScenarios;
    private final boolean validationFullPlan;

    private float validationElapsedSeconds;
    private float nextValidationScenarioSeconds;
    private RuntimeException pendingValidationFailure;
    private int nextValidationScenario;
    private long currentFrame;

    public UiKitScenarioRunner(UiKitShowcase showcase, UiRoot root, DefaultInput input, Display display, Logger logger,
            RecordingTextInputController textInputController, ScenarioValidationConfig validationConfig,
            UiKitTestCapture capture) {
        this.logger = logger;
        this.validationConfig = validationConfig;
        this.capture = capture;
        UiKitTestInput interaction = new UiKitTestInput(root, input, display, showcase.activeSection());
        behavior = new UiKitBehaviorPlan(showcase, root, input, display, logger, textInputController, interaction);
        scenarioHost = ScenarioHost.create()
                .frameDeltaMillis(VALIDATION_WAIT_FRAME_MILLIS)
                .frameDriver(context -> {
                    root.update(VALIDATION_WAIT_FRAME_MILLIS / 1000.0f);
                    root.rootNode();
                })
                .inputDriver(interaction)
                .captureDriver((name, context) -> {
                    capture.queue(name, true, context.scenario().requiresVisualBaseline());
                    return new ScenarioCapture(name, context.frame(), context.elapsedMillis(),
                            capture.outputPath(name, currentFrame));
                })
                .registerProbe(UiRoot.class, root);
        boolean captureListed = validationConfig.capturePolicy() == ScenarioCapturePolicy.ALL
                || validationConfig.capturePolicy() == ScenarioCapturePolicy.SCENARIO_LISTED;
        UiKitValidationScenarios.Plan plan = behavior.build(capture.captureAllScenarios(),
                () -> capture.queue("section-drawing", captureListed, false))
                .select(validationConfig.selection(), validationConfig.mode());
        validationScenarios = plan.entries();
        validationFullPlan = plan.fullPlan();
    }

    public long exitFrameLimit(long exitAfterFrames) {
        long minimumFrames = minimumValidationFrames();
        if (minimumFrames <= 0L) {
            return exitAfterFrames;
        }
        long effectiveFrames = exitAfterFrames > 0L ? Math.max(exitAfterFrames, minimumFrames) : minimumFrames;
        if (exitAfterFrames <= 0L) {
            logger.info("UiKitTest forced validation frame limit to " + effectiveFrames);
        } else if (effectiveFrames != exitAfterFrames) {
            logger.info("UiKitTest increased validation frame limit from " + exitAfterFrames + " to " + effectiveFrames
                    + " to execute the full validation plan.");
        }
        return effectiveFrames;
    }

    private long minimumValidationFrames() {
        if (validationScenarios.length == 0) {
            return 0L;
        }
        long executionFrame = -1L;
        for (UiKitValidationScenarios.Entry scenario : validationScenarios) {
            executionFrame = Math.max(scenario.frame(), executionFrame + 1L);
        }
        return executionFrame + (capture.available() ? 1L : 2L);
    }

    public void advanceTime(float deltaSeconds) {
        validationElapsedSeconds += Math.max(0.0f, deltaSeconds);
    }

    public void execute(long renderedFrames) {
        long validationFrame = capture.available() ? renderedFrames : renderedFrames - 1L;
        if (validationFrame < 0L) {
            return;
        }
        if (nextValidationScenario >= validationScenarios.length
                || validationScenarios[nextValidationScenario].frame() > validationFrame) {
            return;
        }
        if (validationConfig.stepDelaySeconds() > 0.0f && nextValidationScenario > 0
                && validationElapsedSeconds + 0.0001f < nextValidationScenarioSeconds) {
            return;
        }
        UiKitValidationScenarios.Entry scenario = validationScenarios[nextValidationScenario++];
        currentFrame = renderedFrames;
        ScenarioResult result = scenarioHost.run(scenario.scenario(), validationConfig);
        if (!result.passed()) {
            if (scenario.captureOnFailure(validationConfig.capturePolicy())) {
                capture.queue(scenario.name(), true, false);
            }
            String eventSummary = validationConfig.eventsEnabled()
                    ? ", events=" + result.recentEvents()
                    : "";
            pendingValidationFailure = new FdxException("UiKitTest scenario failed: " + scenario.name()
                    + ", operation=" + result.operationName()
                    + ", message=" + result.message()
                    + eventSummary);
            return;
        }
        capture.queue(scenario.name(), scenario.captureOnSuccess(validationConfig.capturePolicy()),
                capture.visualValidationEnabled() && scenario.validateVisual(validationConfig.mode()));
        if (validationConfig.stepDelaySeconds() > 0.0f) {
            nextValidationScenarioSeconds = validationElapsedSeconds + validationConfig.stepDelaySeconds();
        }
    }

    public boolean complete() {
        return nextValidationScenario >= validationScenarios.length;
    }

    public void throwPendingFailure() {
        if (pendingValidationFailure != null) {
            RuntimeException failure = pendingValidationFailure;
            pendingValidationFailure = null;
            throw failure;
        }
    }

    public void verifyCompletion(long renderedFrames, long exitAfterFrames) {
        if (nextValidationScenario < validationScenarios.length) {
            throw new FdxException("UiKitTest did not execute all validation scenarios: expected="
                    + validationScenarios.length + ", executed=" + nextValidationScenario
                    + ", frames=" + renderedFrames);
        }
        if (validationFullPlan) {
            behavior.verifyCoverage(renderedFrames);
        }
        capture.verifyRequestedCapture(renderedFrames);
        if (renderedFrames < exitAfterFrames) {
            throw new FdxException("UiKitTest rendered " + renderedFrames + " of " + exitAfterFrames
                    + " required frames");
        }
    }

}
