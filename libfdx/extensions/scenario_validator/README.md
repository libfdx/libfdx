# Scenario Validator

The scenario validator drives complete application flows through normal input,
update, event, probe, and capture boundaries. It can validate UI behavior, game
state, tool workflows, and project-specific runtime conditions without adding a
service to `Fdx`.

The `core` module is UI-neutral. The optional `ui-kit` module adds selectors,
actions, waits, and assertions for [UI Kit](../../../docs/UI_KIT.md). Normal
runtime and UI modules do not depend on validation.

## Authoring A Scenario

A scenario is an ordered sequence of setup, actions, bounded waits, assertions,
captures, and narrowly scoped callbacks:

```java
Scenario scenario = Scenario.named("main-menu-to-play")
    .setup(MainMenuSetup::new)
    .content(MainMenuSetup::build)
    .action(UiScenarioActions.click("menu.playButton"))
    .waitFor(ScenarioWaits.event("game.started").timeoutMillis(5000))
    .action(ScenarioActions.key(Key.W).holdFrames(60))
    .expect(UiScenarioAssertions.visible("hud.healthBar"))
    .expect(ScenarioAssertions.probe(
        PlayerProbe.class,
        probe -> probe.position().x > 10));
```

Catalogs group stable scenario names for selection and reporting. Renaming a
scenario also changes its default report/capture identity.

Every wait has an explicit or configured timeout and advances with application
frames/validation time; waits never sleep or busy-loop on the render thread.
Provider and worker threads enqueue work rather than invoking scenario
callbacks directly.

Project probes are the preferred boundary for application-specific state. Use
a custom callback only when built-in operations, adapters, and probes cannot
express the behavior cleanly.

## UI Selection

The UI adapter primarily selects nodes by stable developer-facing validation
IDs:

```java
Ui.modifier().validationId("settings.applyButton")
```

Validation IDs do not affect layout, drawing, input, focus, or accessibility.
Semantic labels may be used as a fallback; text/type selectors are less stable.

## Modes And Captures

| Mode | Behavior |
| --- | --- |
| `behavior` | Runs selected scenarios without enforcing visual baselines. |
| `visual` | Runs baseline-bearing scenarios and requires their captures. |
| `mixed` | Runs all selected scenarios and enforces declared baselines. |

Capture policies are `all`, `failed`, `none`, and `scenario-listed`. A scenario
that requires a visual baseline fails when no capture or baseline is available,
regardless of capture policy. A generated capture is evidence only after it is
inspected or compared under the validation plan.

## Configuration

| System property | Meaning |
| --- | --- |
| `libfdx.validation.scenario` | One name, comma-separated names, or `all`. |
| `libfdx.validation.mode` | `behavior`, `visual`, or `mixed`. |
| `libfdx.validation.timeoutMs` | Default timeout for waits. |
| `libfdx.validation.events` | Include recent event history in results. |
| `libfdx.validation.capture` | `all`, `failed`, `none`, or `scenario-listed`. |
| `libfdx.validation.stepDelaySeconds` | Minimum elapsed time between steps. |

`ScenarioValidationConfig` owns these semantics. Hosts forward configuration
rather than maintaining another property model.

## Results And Performance

Failures identify the scenario/operation, expected and resolved state, elapsed
wait, recent events when enabled, and relevant capture/baseline paths. Platform
matrix cells use `PASS`, `BLOCKED`, or `NOT_RUN`, with a reason for every
non-pass result.

Scenario construction and failure reporting may allocate. Enabling validation
must not add steady-state allocation to normal input, update, layout,
rendering, networking, or game-loop paths.

Exact declarations remain in source/Javadocs, and executable behavior remains
in the scenario-validator tests.
