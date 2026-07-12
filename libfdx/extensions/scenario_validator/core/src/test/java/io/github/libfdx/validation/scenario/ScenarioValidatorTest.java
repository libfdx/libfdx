package io.github.libfdx.validation.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ScenarioValidatorTest {
    @Test
    void visualModeFiltersBehaviorScenariosAndBehaviorModeSkipsBaselineEnforcement() {
        AtomicInteger behaviorRuns = new AtomicInteger();
        AtomicInteger visualRuns = new AtomicInteger();
        Scenario behavior = Scenario.named("behavior")
                .custom("behavior", context -> behaviorRuns.incrementAndGet());
        Scenario visual = Scenario.named("visual")
                .custom("visual", context -> visualRuns.incrementAndGet())
                .capture("visual-frame")
                .visualBaselineRequired();
        ScenarioCatalog catalog = ScenarioCatalog.create().add(behavior).add(visual);
        ScenarioHost visualHost = ScenarioHost.create()
                .captureDriver((name, context) -> new ScenarioCapture(name, context.frame(),
                        context.elapsedMillis(), name + ".png")
                        .baseline(name + "-baseline.png", true, Boolean.TRUE, "matched"));

        ScenarioReport visualReport = ScenarioValidator.create(visualHost, catalog)
                .config(ScenarioValidationConfig.defaults()
                        .selection(" behavior, visual ")
                        .mode(ScenarioValidationMode.VISUAL))
                .run();

        assertTrue(visualReport.passed());
        assertEquals(1, visualReport.resultCount());
        assertNotNull(visualReport.result("visual"));
        assertEquals(0, behaviorRuns.get());
        assertEquals(1, visualRuns.get());

        behaviorRuns.set(0);
        visualRuns.set(0);
        ScenarioReport behaviorReport = ScenarioValidator.create(ScenarioHost.create(), catalog)
                .config(ScenarioValidationConfig.defaults()
                        .mode(ScenarioValidationMode.BEHAVIOR)
                        .capturePolicy(ScenarioCapturePolicy.NONE))
                .run();

        assertTrue(behaviorReport.passed());
        assertEquals(2, behaviorReport.resultCount());
        assertEquals(1, behaviorRuns.get());
        assertEquals(1, visualRuns.get());
        assertTrue(behaviorReport.result("visual").captures().isEmpty());
    }

    @Test
    void capturePoliciesControlListedAutomaticAndFailureCaptures() {
        Scenario listed = Scenario.named("listed").capture("listed-frame");
        ScenarioHost listedHost = captureHost();
        ScenarioResult listedResult = listedHost.run(listed, ScenarioValidationConfig.defaults()
                .capturePolicy(ScenarioCapturePolicy.SCENARIO_LISTED));
        assertTrue(listedResult.passed());
        assertEquals(1, listedResult.captures().size());
        assertEquals("listed-frame", listedResult.captures().get(0).name());

        ScenarioResult noneResult = captureHost().run(listed, ScenarioValidationConfig.defaults()
                .capturePolicy(ScenarioCapturePolicy.NONE));
        assertTrue(noneResult.passed());
        assertTrue(noneResult.captures().isEmpty());

        Scenario automatic = Scenario.named("automatic");
        ScenarioResult allResult = captureHost().run(automatic, ScenarioValidationConfig.defaults()
                .capturePolicy(ScenarioCapturePolicy.ALL));
        assertTrue(allResult.passed());
        assertEquals(1, allResult.captures().size());
        assertEquals("automatic", allResult.captures().get(0).name());

        Scenario failure = Scenario.named("failure").custom("fail", context -> context.fail("boom"));
        ScenarioResult failedResult = captureHost().run(failure, ScenarioValidationConfig.defaults()
                .capturePolicy(ScenarioCapturePolicy.FAILED));
        assertFalse(failedResult.passed());
        assertEquals(1, failedResult.captures().size());
        assertEquals("failure", failedResult.captures().get(0).name());
    }

    @Test
    void configTimeoutBoundsWaitsWithoutAnExplicitTimeout() {
        ScenarioHost host = ScenarioHost.create()
                .frameDeltaMillis(10L)
                .frameDriver(context -> {
                });
        Scenario scenario = Scenario.named("timeout")
                .waitFor(ScenarioWaits.until("never", context -> false));

        ScenarioResult result = host.run(scenario, ScenarioValidationConfig.defaults().timeoutMillis(30L));

        assertFalse(result.passed());
        assertEquals("never", result.operationName());
        assertEquals(3, result.frame());
        assertEquals(30L, result.elapsedMillis());
        assertTrue(result.message().contains("Wait timed out"));
    }

    @Test
    void eventsRemainAvailableToScenariosButCanBeOmittedFromResults() {
        Scenario scenario = Scenario.named("events")
                .action(ScenarioActions.emit("validation.ready"))
                .expect(ScenarioAssertions.eventSeen("validation.ready"))
                .custom("fail", context -> context.fail("expected failure"));

        ScenarioResult withoutEvents = ScenarioHost.create().run(scenario,
                ScenarioValidationConfig.defaults().eventsEnabled(false));
        assertFalse(withoutEvents.passed());
        assertTrue(withoutEvents.recentEvents().isEmpty());

        ScenarioResult withEvents = ScenarioHost.create().run(scenario,
                ScenarioValidationConfig.defaults().eventsEnabled(true));
        assertFalse(withEvents.passed());
        assertTrue(withEvents.recentEvents().contains("validation.ready"));
    }

    @Test
    void selectionTrimsWhitespaceAndAcceptsCaseInsensitiveAll() {
        ScenarioCatalog catalog = ScenarioCatalog.create()
                .add(Scenario.named("first"))
                .add(Scenario.named("second"));

        assertEquals(1, catalog.select(" second ").size());
        assertEquals("second", catalog.select(" second ").get(0).name());
        assertEquals(2, catalog.select(" ALL ").size());
        assertEquals("all", ScenarioValidationConfig.defaults().selection("   ").selection());
    }

    private ScenarioHost captureHost() {
        return ScenarioHost.create().captureDriver((name, context) -> new ScenarioCapture(name,
                context.frame(), context.elapsedMillis(), name + ".png"));
    }
}
