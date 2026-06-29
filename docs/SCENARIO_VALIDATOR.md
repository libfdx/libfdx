# libFDX Scenario Validator Contract

This document is the source of truth for the libfdx scenario validation engine.
Source code, tests, task wiring, user-facing docs, and generated reports for
the validator must follow this contract.

The scenario validator is not a UI-only tool and not a game-only tool. It
validates runtime scenarios: menu flows, UI behavior, input sequences, loaded
screens, world/application state, captures, events, and project-specific probes.
`docs/UI_KIT.md` remains the UI Kit contract, `docs/COMMON_API.md` remains the
public API contract, and `docs/ARCHITECTURE.md` remains the module, artifact,
and layout source.

The API, type, package, module, artifact, and property names below are contract
names. Renaming or changing any of them requires updating this document and the
canonical docs in the same change.

## Index

1. [Purpose](#1-purpose)
2. [Scope](#2-scope)
3. [Module Ownership](#3-module-ownership)
4. [Public API Surface](#4-public-api-surface)
5. [Runtime Architecture](#5-runtime-architecture)
6. [Scenario Model](#6-scenario-model)
7. [Host Contract](#7-host-contract)
8. [Actions](#8-actions)
9. [Waits And Events](#9-waits-and-events)
10. [Assertions](#10-assertions)
11. [Probes And Custom Validation](#11-probes-and-custom-validation)
12. [UI Kit Adapter](#12-ui-kit-adapter)
13. [Visual Validation](#13-visual-validation)
14. [Failure Reports](#14-failure-reports)
15. [Validator Properties](#15-validator-properties)
16. [Runtime And Performance Rules](#16-runtime-and-performance-rules)
17. [Architecture Invariants](#17-architecture-invariants)

## 1. Purpose

`scenario-validator` is a reusable validation engine for complete runtime
flows. It links named scenarios to setups or screens, drives input and time
through the normal runtime, waits for visible or scenario-local events, verifies
behavior and state, coordinates visual captures, and produces structured
reports.

Setups build or select the application state under validation. Scenarios own
scripted validation logic. A scenario can validate a simple UI screen, a
main-menu to gameplay flow, a tool workflow, a benchmark setup screen, or a
project-specific interactive runtime path.

## 2. Scope

The scenario validator supports:

- stable scenario names and catalog selection;
- runtime input actions for pointer, keyboard, text, scroll, focus, hover,
  drag, and capture behavior;
- frame waits, elapsed-time waits, condition waits, event waits,
  layout-settled waits, and animation-finished waits;
- generic assertions for screen/state, probe values, events, captures, and
  timing;
- optional adapters for framework-specific domains such as UI Kit;
- optional custom validation callbacks for project-specific probes or behavior
  that built-in operations cannot express;
- visual-only, behavior-only, and mixed validation scenarios;
- scenario-local event history and structured failure reports.

The scenario validator does not:

- require JUnit or another external test framework;
- add scenario accessors to `Fdx`;
- require annotations, reflection binding, generated source, or a compiler
  plugin;
- mutate private application or widget state to force a pass;
- replace visual parity validation or manual inspection where visual work
  requires rendered evidence;
- use sleeps or busy loops inside render/update.

## 3. Module Ownership

Core module:

```text
:libfdx:validation:scenario-validator
```

Core artifact:

```text
io.github.libfdx:scenario_validator
```

Core package:

```text
io.github.libfdx.validation.scenario
```

UI Kit adapter module:

```text
:libfdx:validation:scenario-validator-ui-kit
```

UI Kit adapter artifact:

```text
io.github.libfdx:scenario_validator_ui_kit
```

UI Kit adapter package:

```text
io.github.libfdx.validation.scenario.ui.kit
```

`scenario-validator` is an optional public engine module. It depends on
portable runtime input/display concepts as needed. It must not depend on UI Kit,
libfdx internal test runners, JUnit, desktop-only APIs, Android-only APIs,
web-only APIs, native-only APIs, or backend implementations.

`scenario-validator-ui-kit` is an optional adapter module. It depends on
`scenario-validator` and `ui-kit`. Projects that only need generic runtime
scenario validation do not depend on the UI Kit adapter.

Normal runtime execution and normal UI rendering must not depend on scenario
validation modules.

## 4. Public API Surface

Core public API types live in `io.github.libfdx.validation.scenario`.
Implementation helpers live under `io.github.libfdx.validation.scenario.internal`
and must not leak into public APIs.

UI Kit adapter API types live in `io.github.libfdx.validation.scenario.ui.kit`.
Adapter implementation helpers live under
`io.github.libfdx.validation.scenario.ui.kit.internal`.

| Type | Role |
| --- | --- |
| `ScenarioValidator` | Main validation runner for executing selected scenarios against a host. |
| `ScenarioCatalog` | Named collection of reusable validation scenarios. |
| `Scenario` | Ordered validation flow linked to a setup, screen, or runtime state. |
| `ScenarioSetup` | Creates or selects runtime state, screens, UI roots, worlds, probes, and capture hooks for a scenario. |
| `ScenarioHost` | Runtime owner connected to input dispatch, clock, capture hooks, events, probes, and reports. |
| `ScenarioContext` | Callback surface for custom extension operations, state probes, failure helpers, and scenario-local events. |
| `ScenarioAction` | Built-in input, state-driving, or capture operation. |
| `ScenarioAssertion` | Built-in check against runtime state, probes, events, captures, or adapter data. |
| `ScenarioWait` | Frame/time/condition/event wait with timeout. |
| `ScenarioActions` | Factory for built-in runtime actions. |
| `ScenarioAssertions` | Factory for built-in runtime assertions. |
| `ScenarioWaits` | Factory for built-in waits. |
| `ScenarioEvents` | Scenario-local event sink and bounded event history. |
| `ScenarioProbe` | Project-owned state probe made available to scenarios. |
| `ScenarioCapture` | Capture request/result metadata. |
| `ScenarioReport` | Structured report for scenario results, failures, captures, and platform/API validation cells. |
| `ScenarioResult` | Pass/fail result for one scenario or validation run. |
| `ScenarioValidationConfig` | Property-backed validator behavior selection: scenario selection, mode, timeout, event output, capture policy, and step delay. |
| `ScenarioValidationMode` | Behavior, visual, or mixed validation mode. |
| `ScenarioCapturePolicy` | Capture policy for all, failed, none, or scenario-listed captures. |
| `ScenarioValidationCell` | Platform/API validation matrix cell with status and reason. |
| `ScenarioValidationCellStatus` | Matrix cell status: `PASS`, `BLOCKED`, or `NOT_RUN`. |
| `UiScenarioTargets` | UI Kit adapter target factories for validation IDs, semantic labels, and compatibility selectors. |
| `UiScenarioActions` | UI Kit adapter actions such as click, press/release, drag slider, type, focus, hover, and capture. |
| `UiScenarioAssertions` | UI Kit adapter assertions for visibility, text, value, bounds, focus, popup/modal state, and capture comparisons. |
| `UiScenarioWaits` | UI Kit adapter waits for UI existence, visibility, text, value, and disappearance. |

Core scenario validator classes use the `Scenario` prefix. Domain adapter
classes use a domain prefix followed by `Scenario`, such as `UiScenarioActions`.

## 5. Runtime Architecture

The runtime architecture has five layers:

| Layer | Responsibility |
| --- | --- |
| Scenario setup | Builds or selects application state, screens, UI roots, worlds, probes, and capture hooks. |
| Scenario catalog | Owns reusable validation flows and stable selection names. |
| Scenario host | Connects scenarios to input dispatch, frame/update progress, event history, probes, captures, and reports. |
| Domain adapters | Add framework-specific actions/assertions, such as UI Kit node lookup and UI assertions. |
| Application runtime | Performs normal update, layout, input dispatch, animation, rendering, and state changes. |

The validator drives the runtime through public input, update, event, and
capture paths used by an application. It must not bypass behavior by mutating
private runtime, world, or widget state directly. Direct state reads are allowed
only through scenario-owned probes exposed to `ScenarioContext`.

## 6. Scenario Model

A scenario links a setup to a sequence of validation operations. Scenario
operations are actions, waits, assertions, and captures. Custom callbacks are
optional extension operations for project-specific probes or behavior that
built-in operations cannot express.

```java
Scenario.named("main-menu-to-play")
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

Catalogs group related scenarios and provide stable selection names:

```java
ScenarioCatalog catalog = ScenarioCatalog.create()
        .add(MenuScenarios.all())
        .add(UiKitScenarios.all())
        .add(projectScenario);
```

Scenario names are stable report identifiers. Renaming a scenario changes report
and capture names and must be synchronized with task docs, baseline names, and
generated report readers.

## 7. Host Contract

`ScenarioHost` is the runtime owner for a validation run. It owns:

- the selected catalog and scenario filter;
- input dispatch helpers;
- validation time based on frame/update progress;
- scenario-local event history;
- registered project probes;
- domain adapter hooks;
- capture scheduling and capture result metadata;
- structured report collection.

`ScenarioHost` advances waits from frame/update progress. It must not block the
render thread with sleeps.

libfdx test screens and sample runners can act as host adapters. They select
scenarios, build runtime content, forward relevant properties, register probes,
and provide capture hooks. They must not own scenario action scripts, custom
assertions, event-wait logic, or dispose-time validation flags.

Scenario catalogs must be usable directly from `Scenario`, built-in operations,
domain adapter operations, and lambdas. They must not require a project-specific
driver interface or adapter implementation before a developer can write a
scenario.

## 8. Actions

Built-in runtime actions cover common interaction behavior.

| Action | Purpose |
| --- | --- |
| `pointerMove(x, y)` | Move the pointer to a viewport point. |
| `pointerDown()` / `pointerUp()` | Dispatch split pointer press/release. |
| `click(x, y)` | Pointer move, down, and up at a viewport point. |
| `key(key)` | Dispatch a key press/release. |
| `holdKey(key, frames)` | Hold a key for a deterministic number of frames. |
| `type(text)` | Dispatch text input. |
| `scroll(dx, dy)` | Dispatch wheel/scroll input. |
| `capture(name)` | Queue a named image capture for the current scenario. |
| `emit(event)` | Emit a scenario-local event from scenario code. |

Actions fail clearly when the target input path, runtime state, or adapter
capability needed by the action is unavailable.

Domain adapters add domain-specific actions. UI Kit actions can click a node by
validation ID, drag a slider, focus a text field, or capture a UI state without
forcing a custom callback.

## 9. Waits And Events

Validation supports real runtime timing. Not every scenario is an instant action
and assertion.

Built-in waits:

```java
ScenarioWaits.frames(2)
ScenarioWaits.millis(250)
ScenarioWaits.until(ctx -> ctx.probe(GameStateProbe.class).isLevelLoaded())
        .timeoutMillis(3000)
ScenarioWaits.event("game.started").timeoutMillis(5000)
ScenarioWaits.captureReady("main-menu").timeoutFrames(10)
```

Rules:

- Every wait must have a timeout. Default timeouts are allowed, but unbounded
  waits are not.
- Waits advance from frame/update progress.
- Waits must not block the render thread with sleeps.
- `millis(...)` waits use accumulated validation time from frame delta.
- Event waits consume events from the scenario-local event sink.
- Condition waits record the last observed value for failure output.
- Event waits record the last received events for failure output.

Scenario code and adapters emit events through `ScenarioEvents`:

```java
ctx.events().emit("game.started");
ctx.events().emit("screen.changed:gameplay");
ctx.events().emit("ui.clicked:menu.playButton");
```

Event names are scenario-local strings. Built-in and adapter-emitted names use a
short domain prefix such as `screen.`, `input.`, `ui.`, `capture.`, or
`validation.`.

## 10. Assertions

Assertions are small, composable checks.

| Assertion | Purpose |
| --- | --- |
| `eventSeen(name)` | Event exists in recent scenario history. |
| `screen(type)` | Current screen/runtime state matches the expected type or identifier. |
| `probe(type, predicate)` | Project probe satisfies a predicate. |
| `captureExists(name)` | Capture was produced. |
| `captureMatches(name)` | Capture matches a required baseline. |
| `elapsedLessThan(millis)` | Scenario operation completed within a timing bound. |

Assertions report both the requested condition and the resolved state summary:

```text
FAIL main-menu-to-play operation=wait-game-started index=3
waited 5000ms for event game.started
last events: ui.clicked:menu.playButton, screen.changed:loading
current screen=LoadingScreen
```

Domain adapters add domain-specific assertions. UI Kit assertions validate node
presence, visibility, text, values, focus, bounds, popups, modals, and UI
captures.

## 11. Probes And Custom Validation

Project probes are the preferred way to expose application-specific state to
scenarios. A probe is a setup-owned object registered with the host and read
through `ScenarioContext`.

```java
Scenario.named("load-records-request")
        .setup(LoadSetup::new)
        .content(LoadSetup::build)
        .action(UiScenarioActions.click("loadButton"))
        .waitFor(ScenarioWaits.event("load.completed").timeoutMillis(3000))
        .expect(ScenarioAssertions.probe(
                LoadProbe.class,
                probe -> probe.completedRequestCount() == 1));
```

Custom callbacks are escape hatches for behavior that cannot be expressed
cleanly as built-in actions, waits, assertions, captures, adapter operations,
or probes.

```java
Scenario.named("external-service-contract")
        .custom("verify fake service transcript", ctx -> {
            ServiceTranscript transcript = ctx.probe(ServiceTranscript.class);
            if (!transcript.contains("inventory:loaded")) {
                ctx.fail("inventory load was not recorded");
            }
        });
```

Custom callbacks use `ScenarioContext`, not direct access to test runner
internals. The context exposes:

- project-owned probes through `probe(Class<T>)`;
- custom failure helpers such as `assertTrue(...)` and `fail(...)`;
- scenario-local event helpers;
- recent event, capture, and state summaries for failure reporting.

## 12. UI Kit Adapter

The UI Kit adapter validates UI built with `:libfdx:framework:ui-kit`. It is not the UI
widget contract source. `docs/UI_KIT.md` remains the source of truth for UI Kit
widgets, state, layout, animation, styling, and input behavior.

Validation uses stable developer-facing IDs, not visible text, for primary UI
node lookup. Validation IDs live directly on `UiModifier`:

```java
Ui.modifier().validationId("settings.applyButton")
```

UI Kit target selectors:

```java
UiScenarioTargets.id("settings.applyButton")
UiScenarioTargets.semanticLabel("Text size setting")
UiScenarioTargets.typeAndText(UiNodeType.BUTTON, "Press")
UiScenarioTargets.type(UiNodeType.MODAL)
```

Rules:

- Validation IDs are test/debug identifiers, not user-facing text.
- Validation IDs must be unique inside a validation setup unless a scenario
  explicitly targets a collection.
- Validation IDs are exposed on retained UI nodes for lookup.
- Validation IDs must not affect layout, rendering, input, focus,
  accessibility, or normal runtime behavior.
- Semantic labels remain user/debug labels. They can be fallback selectors, but
  primary UI validation uses validation IDs.
- Type/text selectors are compatibility selectors for existing screens and are
  not the preferred contract for new validation scenarios.

The UI Kit adapter provides:

- actions such as `click(id)`, `press(id)`, `release(id)`, `drag(id, ...)`,
  `dragSlider(id, value)`, `type(id, text)`, `focus(id)`, `hover(id)`, and
  `capture(name)`;
- assertions such as `exists(id)`, `visible(id)`, `enabled(id)`,
  `focused(id)`, `textEquals(id, value)`, `checked(id, value)`,
  `sliderValue(id, value, tolerance)`, `popupOpen(id)`, `modalOpen(id)`, and
  `boundsInsideViewport(id)`;
- waits such as `exists(id)`, `visible(id)`, `notVisible(id)`,
  `textEquals(id, value)`, and `valueEquals(id, value)`;
- adapter events such as `ui.clicked:<id>`, `ui.textChanged:<id>`,
  `ui.valueChanged:<id>`, `ui.focusChanged:<id>`, `ui.popupOpened:<id>`, and
  `ui.modalOpened:<id>`.

## 13. Visual Validation

The validator coordinates captures through host-provided capture hooks.

```java
Scenario.named("tabs-switch")
        .action(UiScenarioActions.clickTab("demoTabs", 1))
        .expect(UiScenarioAssertions.activeTab("demoTabs", 1))
        .capture("tabs-switch")
        .visualBaselineRequired();
```

Rules:

- Scenario names become capture names unless a capture overrides them.
- Missing baselines are failures when a compare run requires baselines.
- Debug captures remain debug captures unless a scenario explicitly marks them
  as assertions.
- Visual validation reports platform/API matrix cells as `PASS`, `BLOCKED`, or
  `NOT RUN` with reasons.
- Visual validation requires rendered evidence according to the active
  validation plan. A build or task success alone is not enough for visual work.

## 14. Failure Reports

Every failure includes:

- scenario name;
- operation name and index;
- action, wait, assertion, capture, adapter operation, or custom callback that
  failed;
- target selector or probe type when relevant;
- expected value;
- actual value;
- resolved runtime, probe, node, or capture summary when available;
- timeout duration and elapsed duration for waits;
- recent validation events;
- capture path or baseline path when visual comparison is involved.

`ScenarioReport` stores the same data in structured form for generated reports
and task output.

## 15. Validator Properties

These properties select validator behavior:

| Property | Purpose |
| --- | --- |
| `libfdx.validation.scenario` | Scenario name, comma-separated names, or `all`. |
| `libfdx.validation.mode` | `behavior`, `visual`, or `mixed`. |
| `libfdx.validation.timeoutMs` | Default timeout for waits. |
| `libfdx.validation.events` | Enable event history in failure output. |
| `libfdx.validation.capture` | Capture all, failed, none, or scenario-listed captures. |
| `libfdx.validation.stepDelaySeconds` | Minimum elapsed seconds between scenario steps. `0` keeps the validator on the fast path. |

libfdx test hosts may forward existing `libfdx.test.*` properties to these
properties for compatibility, but the public scenario validator property root is
`libfdx.validation.*`.
Step pacing is owned by `ScenarioValidationConfig`; test hosts must forward
`libfdx.validation.stepDelaySeconds` instead of defining a UI-specific delay
property.

Adding, removing, or renaming validator properties requires updating this
document, `docs/COMMON_API.md`, `docs/ARCHITECTURE.md` when module or task
wiring is affected, and any affected task or user docs.

## 16. Runtime And Performance Rules

- Scenario construction can allocate.
- Failure reporting can allocate.
- Normal update, layout, input dispatch, animation, rendering, and runtime state
  paths must not allocate just because validation support exists.
- Engine-emitted events reuse buffers or compact event objects where possible.
- The validator does not add reflection or runtime type scanning.
- The validator does not mutate private runtime, world, or widget state to
  force a pass.
- The validator does not make image inspection optional for visual work.

## 17. Architecture Invariants

- `scenario-validator` is a user-facing engine/tooling module, not an internal
  test class.
- Scenario validation is not UI-only and not game-only.
- UI Kit validation is an adapter capability, not the whole validator.
- Scenario-owned catalogs replace hardcoded validation scripts inside screens.
- Scenarios may be authored by libfdx tests, samples, tools, external
  benchmark projects, or user projects.
- Scenario selection, capture naming, failure reports, and visual baseline names
  use stable scenario names.
- Public validator APIs stay provider-neutral. Provider-specific behavior enters
  only through host-supplied input, display, clock, probe, capture, and adapter
  hooks.
- Any implementation change that alters the contract must update this document
  and the canonical docs in the same change.
