# libFDX Scenario Validator

The scenario validator drives complete runtime flows through normal input,
update, event, and capture paths. It is suitable for menus, UI behavior, game or
tool workflows, world/application state, and project-specific probes.

This guide owns scenario behavior and configuration. Exact declarations belong
to Java source/Javadocs. Visual comparison procedure belongs to
[TESTING.md](TESTING.md#4-visual-and-graphics-validation).

## Topics

- [1. Scope](#1-scope)
- [2. Modules and Dependencies](#2-modules-and-dependencies)
- [3. Runtime Model](#3-runtime-model)
- [4. Authoring Scenarios](#4-authoring-scenarios)
- [5. Actions, Waits, and Events](#5-actions-waits-and-events)
- [6. Assertions and Probes](#6-assertions-and-probes)
- [7. UI Kit Adapter](#7-ui-kit-adapter)
- [8. Modes and Captures](#8-modes-and-captures)
- [9. Configuration Properties](#9-configuration-properties)
- [10. Reports and Performance](#10-reports-and-performance)

## 1. Scope

A scenario links a setup/runtime state to ordered actions, waits, assertions,
captures, and narrowly scoped custom callbacks. Catalogs group scenarios under
stable selection names. A host connects them to a running application.

The validator supports:

- pointer, keyboard, text, scroll, focus, hover, drag, and capture actions;
- frame, elapsed-time, condition, event, layout, animation, and capture waits;
- state, event, probe, timing, UI, and visual assertions;
- behavior-only, visual-only, and mixed execution;
- domain adapters such as UI Kit;
- structured results and platform/API matrix cells.

It does not require JUnit, annotations, reflection binding, generated source, or
a compiler plugin. It does not add a service to `Fdx`, mutate private runtime
state to manufacture a pass, replace rendered visual evidence, or sleep/busy
loop in update/render.

## 2. Modules and Dependencies

| Layer | Module | Maven artifact | Package |
| --- | --- | --- | --- |
| Core | `:libfdx:extensions:scenario_validator:core` | `io.github.libfdx:scenario_validator` | `io.github.libfdx.validation.scenario` |
| UI Kit adapter | `:libfdx:extensions:scenario_validator:ui-kit` | `io.github.libfdx:scenario_validator_ui_kit` | `io.github.libfdx.validation.scenario.ui.kit` |

Core may depend on portable runtime, input, and display concepts. It does not
depend on UI Kit, internal test runners, JUnit, platform launchers, or backends.
The optional UI adapter depends on core plus UI Kit.

Normal runtime and UI modules never depend on scenario validation. Public API
stays out of `.internal` packages.

## 3. Runtime Model

Responsibilities remain separate:

| Owner | Responsibility |
| --- | --- |
| Setup | Creates or selects screens, roots, worlds, probes, and capture hooks. |
| Scenario | Owns the ordered validation behavior. |
| Catalog | Groups stable names for selection and reuse. |
| Host | Advances time/frames, dispatches input, stores events/probes/captures, and builds reports. |
| Adapter | Adds domain-specific selectors, actions, waits, and assertions. |

Test screens and sample runners can provide host wiring, property forwarding,
and capture hooks. They do not own hard-coded action scripts, event-wait logic,
or dispose-time pass/fail flags.

The host advances validation from application frame/update progress. Provider or
worker threads enqueue runtime work; they do not invoke scenario callbacks
directly.

## 4. Authoring Scenarios

A typical scenario uses built-in operations and an optional adapter:

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

Scenario names are stable report and default capture identifiers. Renaming one
requires synchronizing task selection, baseline names, docs, and report readers.

Catalogs must be authorable from public scenarios, built-in operations,
adapters, and lambdas. A project-specific driver interface is not required.

## 5. Actions, Waits, and Events

Actions exercise the same public path used by an application. If required input,
state, target, or adapter capability is unavailable, the action fails clearly.
Domain adapters add higher-level actions without bypassing that path.

Every wait is bounded:

- an explicit frame/millisecond timeout wins over configuration defaults;
- an otherwise unspecified wait uses the configured default;
- waits advance from frames or accumulated validation delta time;
- no wait sleeps or blocks the render thread;
- condition waits retain the last observed state for diagnostics;
- event waits consume scenario-local events and retain useful recent history;
- capture waits end only when the host reports capture completion or timeout.

Events are small scenario-local strings. Framework/adapters use short prefixes
such as `screen.`, `input.`, `ui.`, `capture.`, or `validation.`. Event-history
output can be disabled without disabling event emission, event waits, or event
assertions.

Engine-emitted events reuse buffers/compact objects where possible and must not
introduce allocation into normal frame loops merely because validation is
enabled.

## 6. Assertions and Probes

Assertions are composable checks over resolved runtime state. Common categories
are:

- event observed;
- expected screen/state active;
- project probe satisfies a predicate;
- capture exists or matches its baseline;
- operation completed within a timing bound;
- adapter-specific node/value/focus/bounds state.

A failure records both the requested condition and the last resolved state.

Project probes are the preferred boundary for application-specific state. A
setup creates and registers a probe; scenario code reads it through
`ScenarioContext`. This keeps validation independent of private runner or screen
implementation.

Use a custom callback only when actions, waits, assertions, captures, adapters,
and probes cannot express the behavior cleanly. Custom callbacks use
`ScenarioContext` for probes, failure helpers, events, and diagnostic summaries;
they do not reach into runner internals.

## 7. UI Kit Adapter

The adapter validates UI Kit without becoming the UI widget contract. UI
behavior remains defined in [UI_KIT.md](UI_KIT.md).

Primary lookup uses stable developer-facing validation IDs:

```java
Ui.modifier().validationId("settings.applyButton")
```

- IDs are unique within a setup unless a scenario intentionally selects a
  collection.
- IDs are retained for lookup but do not affect layout, rendering, input,
  focus, accessibility, or normal behavior.
- Semantic labels can be fallback selectors.
- Type/text selectors exist for compatibility but are less stable than IDs.

The adapter supplies UI actions (click, press/release, drag, slider, type,
focus, hover), waits (existence, visibility, text/value, disappearance), and
assertions (enabled/focused/text/value/bounds, popup/modal state, captures).
Adapters may emit namespaced events such as `ui.clicked:<id>` and
`ui.valueChanged:<id>`.

## 8. Modes and Captures

| Mode | Selected scenarios | Baseline enforcement |
| --- | --- | --- |
| `behavior` | All selected scenarios | Disabled |
| `visual` | Selected scenarios requiring a visual baseline | Required |
| `mixed` | All selected scenarios | Required where declared |

Capture policy:

- `all` adds automatic success captures and captures failures;
- `failed` captures failures only;
- `none` suppresses scenario-listed capture steps;
- `scenario-listed` permits only explicit capture steps.

A visual or mixed scenario requiring a baseline fails if it produces no
capture, regardless of capture policy. Missing required baselines are failures.
Debug captures are not assertions unless the scenario/validation plan marks
them for comparison.

Matrix results use `PASS`, `BLOCKED`, or `NOT_RUN`. Every non-pass includes a
concrete reason. Task success alone is not visual proof.

## 9. Configuration Properties

| Property | Meaning |
| --- | --- |
| `libfdx.validation.scenario` | Name, comma-separated names, or `all` (empty also selects all). |
| `libfdx.validation.mode` | `behavior`, `visual`, or `mixed`. |
| `libfdx.validation.timeoutMs` | Default for waits without an explicit timeout. |
| `libfdx.validation.events` | Include recent events in results/failures. |
| `libfdx.validation.capture` | `all`, `failed`, `none`, or `scenario-listed`. |
| `libfdx.validation.stepDelaySeconds` | Minimum elapsed time between steps; `0` is the fast path. |

`libfdx.validation.*` is the public property root. Test launchers may translate
legacy/host `libfdx.test.*` values for compatibility, but must not invent a
second domain-specific scenario configuration.

Configuration objects own these semantics. Hosts forward configuration rather
than reimplementing selection, mode, timeout, pacing, or capture policy.

## 10. Reports and Performance

Each failure identifies:

- scenario plus operation name/index and operation category;
- target selector or probe type where relevant;
- expected and actual/resolved state;
- timeout and elapsed time for waits;
- recent events when enabled;
- capture and baseline paths for visual work.

`ScenarioReport` preserves equivalent structured data for generated reports and
task output, including platform/API cells.

Scenario construction and failure reporting may allocate. The presence of
validation must not add allocation to normal update, layout, input, animation,
rendering, networking, or world-update paths. There is no runtime reflection or
type scanning.

Scenarios drive behavior only through host-supplied public input, time, event,
probe, capture, and adapter boundaries. This invariant keeps a passing scenario
representative of the application path users actually run.
