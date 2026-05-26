# libFDX Agent Instructions

These instructions apply to the entire repository.

`AGENTS.md` is the coordination and enforcement layer. It is not the architecture
source or API contract source.

## 1. Source of Truth

Use the canonical docs for durable project facts:

- `docs/ARCHITECTURE.md`: module ownership, dependency direction, package roots, Gradle topology, Maven artifacts, and build layout.
- `docs/COMMON_API.md`: public API contracts, provider-neutral behavior, lifecycle rules, and provider boundaries.
- `README.md`, samples, launchers, tests, benchmarks, and generated report docs: user-facing workflows and examples.
- `AGENTS.md`: process rules only. Do not put detailed architecture/API decisions here.

When architecture or API decisions change, update the canonical doc first, then
align user-facing docs and this process file only if needed.

## 2. Mandatory Working Contract

### Permission gate

- Do not start investigation, editing, code execution, test execution, screenshots, or validation without explicit user permission.
- If the scope is unclear, ask before touching files.
- Do not continue after the user says stop.

### Communication contract

Before every investigation or fix, state:

- what will be inspected or changed;
- why that target matters;
- how success will be measured.

During work, report intermediate findings when the result is ambiguous, risky, blocked, or changes the next step.

### Temporary agent state

- At the start of every new chat/session in this repository, read `.agents/agents_memory.md` before investigation, edits, test execution, or validation. If the file is absent, treat that as no active recovery state.
- Use `.agents/agents_memory.md` only for the current active state: active request, current status, last completed step, next intended step, blockers, and validation state. Replace stale content instead of appending history.
- Before changing repository files, generated files, task wiring, docs, or validation state, update `.agents/agents_memory.md` with the intended change and current status.
- After finishing coding or any meaningful part of a solution, update `.agents/agents_memory.md` with what completed, what remains, validation evidence, and blockers.
- Use `.agents/agents_plan.md` when a task has multiple ordered solutions or steps. This file must be a detailed recovery plan, not a terse checklist. Each active step must include the status, objective, target files/modules/tasks, why the step matters, the exact intended action, dependencies or blockers, and the acceptance or validation evidence needed to mark it complete.
- Keep `.agents/agents_plan.md` limited to the current ordered work. Rewrite it when scope changes, update step status as each step completes, and clear it when no multi-step task is active.
- Keep `.agents/agents_memory.md` and `.agents/agents_plan.md` local and ignored by Git. They are recovery scratch files, not project source, history, architecture, or API contract.

### No guessing rule

- Do not invent API usage, type names, command names, or behavior.
- Confirm code solutions against project sources, docs, or build output before claiming correctness.
- Before proposing or applying a code fix, confirm:
  - referenced symbols exist;
  - call shapes match existing declarations;
  - platform/tooling assumptions are explicit;
  - behavior assumptions are supported by source, docs, or observed output.

### Validation-first rule

- Every change needs validation evidence appropriate to the touched scope.
- Default to the smallest validation that proves the changed behavior. Do not run full platform/API validation by default for a small local change.
- Run full validation only when the user asks for it, when the change touches shared core/runtime/backend/renderer behavior, when the change affects public task wiring or cross-platform behavior, or when a narrow validation result leaves real uncertainty.
- For code changes, validation must include a relevant compile/build/test/run result for the touched scope unless the user explicitly limits validation.
- For docs-only or process-only changes, targeted reading, stale-term checks, and `git diff --check` can be sufficient validation.
- For visual work, task success is not enough. A real rendered frame must be captured or inspected, compared to the expected output, and reported.
- If a required target cannot run, run every validation step that can still execute and report the exact blocker.

### Performance-first rule

- Prefer non-allocating and low-allocation paths, especially in frame loops, render loops, asset upload loops, and input/UI update loops.
- Never allocate Java objects inside a frame loop unless there is a measured, proven reason.
- Reuse builders, buffers, arrays, command lists, render resources, and staging memory where possible.
- If a correctness fix temporarily needs extra allocation, state that explicitly and prefer a bounded reuse strategy before finishing.

## 3. Repository Synchronization Rules

When code, task wiring, launchers, public commands, properties, examples, logs,
docs, or externally visible behavior change:

1. Find affected old names, old task names, old properties, old examples, and old behavior descriptions.
2. Update all impacted source-of-truth and user-facing locations in the same change:
   - `docs/ARCHITECTURE.md`
   - `docs/COMMON_API.md`
   - `README.md`
   - samples
   - tests
   - benchmark launchers
   - Gradle tasks
   - report generators
3. If a generic command remains available but changes meaning or becomes ambiguous, document explicit replacement commands wherever the generic command appears.
4. Treat generated build output and ignored IDE metadata as non-source unless the user explicitly asks to update them.

When editing `docs/ARCHITECTURE.md` or `docs/COMMON_API.md`:

- If module, artifact, folder, package, or dependency rules change, check whether API examples or type ownership also need updates.
- If public types, interfaces, lifecycle rules, `Fdx` root accessors, or provider boundaries change, check whether architecture tables, package maps, examples, or dependency rules also need updates.
- Prefer one authoritative section for each rule. Use short references instead of repeating the same decision in many places.

## 4. API, Type, and State Rules

### Fdx root rules

- Use typed `Fdx` only for backend-owned runtime entry points in `ApplicationListener.create(Fdx fdx)`.
- Keep `Fdx` finite and explicit.
- Add direct `Fdx` accessors only for backend-owned runtime systems/managers, for example `app()`, `displays()`, `graphics()`, `files()`, and `logger()`.
- Do not expose user-created objects through `Fdx`, including `AssetManager`, `SpriteBatch`, UI roots, scenes, worlds, physics objects, and game systems.
- Keep backend setup details out of common-code examples.
- Keep provider-specific access explicit through `providerId()`, `as(...)`, and provider-specific config/types.

### Java primitive and UI state rules

- Prefer primitives for fields, locals, config values, and examples.
- Do not use generic primitive wrapper state such as `UiState<Boolean>`, `UiState<Integer>`, or `UiState<Float>`.
- Use dedicated primitive state types such as `UiBooleanState`, `UiIntState`, `UiFloatState`, `UiLongState`, and `UiDoubleState`.
- Box primitives only where Java collections, map keys, reflection, serialization, or external APIs require objects. Keep boxing local and explicit.

### Interface change classification

Before editing a `docs/COMMON_API.md` interface entry, classify it:

- backend-owned `Fdx` system;
- provider-backed API;
- disposable resource with explicit lifetime;
- provider SPI/factory/setup API;
- launcher/backend infrastructure;
- listener/callback;
- descriptor/config/value type.

After classification, update affected tables, prose, examples, and related architecture sections before finishing.

## 5. Validation Workflow

Before finishing any change, validate in this order:

1. Classify the change type:
   - architecture/docs
   - API
   - code
   - task wiring
   - platform
   - visual/renderer/UI
2. List every affected target:
   - modules
   - tasks
   - files
   - platforms
   - graphics APIs
3. Choose and run the minimal required validation for those targets, and keep the raw result in task notes.
   - For small leaf changes, such as a single test scenario, sample, launcher option, or local UI list, validate the directly affected target first.
   - For shared core, renderer, backend, task wiring, public API, or cross-platform changes, broaden validation to the affected modules, platforms, and graphics APIs.
   - Do not validate unrelated platforms, APIs, or generated outputs only because they exist.
4. Confirm symbol/type correctness from source before making API claims.
5. Cross-check impacted docs in one pass.
6. For visual work, run the visual/API/platform matrix below only for the platforms/APIs in scope, unless the user asks for the full matrix.
7. Report blockers exactly:
   - missing SDK/device/toolchain
   - unsupported platform/API
   - unavailable hardware/runtime
   - environment limits.

Never claim full validation for a platform/API that was not run.

## 6. Platform and Graphics API Validation

### Platform requirements

- Android changes in scope: run relevant Android assemble tasks and relevant Android run tasks against a connected device or emulator.
- Desktop changes in scope: run relevant desktop compile/build tasks and relevant desktop run tasks.
- Desktop native changes in scope: run relevant native build/run tasks and gather platform output.
- Other platform changes in scope: run the closest supported build/run validation for that platform.
- If the user asks for full validation, include every supported platform/API required by the change type.
- If required platform support is unavailable, report the exact blocker and mark that platform/API as `BLOCKED` or `NOT RUN`.

### Visual/API matrix requirements

When UI, renderer, font, image, widget, 2D, 3D, texture, shader, surface, readback, or visual output changes:

- Validate every supported platform and graphics API in scope.
- Desktop graphics API matrix requires GL, Vulkan, and WGPU checks when shared graphics/UI output changes, when provider parity is part of the request, or when the user asks for full validation.
- For a local visual change that only affects one test screen, launcher view, sample screen, or debug UI, validate the directly affected platform/API path first and report broader matrix cells as `NOT RUN` unless the user asks to expand them.
- Use the same scene/layout, viewport, scale, assets, input path, and frame count for every API being compared.
- Use GL as the reference when GL is known-good and another API is failing.
- If a matrix cell is not tested, record:
  - platform
  - API
  - exact reason
  - status: `NOT RUN` or `BLOCKED`.
- Do not mark visual validation complete unless every supported matrix cell is `PASS`, `BLOCKED`, or `NOT RUN` with a concrete reason.
The parity evidence must be included in the final task report, not written as a required tracked project file. The report must list each required matrix cell with `PASS`, `BLOCKED`, or `NOT RUN` plus a one-line reason for every non-pass entry.

Do not create persistent validation report files in tracked repository folders unless the user explicitly asks for one. Temporary validation files, screenshots, captures, and generated reports should stay under generated `build/reports` paths so Git does not track them. These files are disposable and must not become the source of truth.

### UIKit scenario coverage

For UI text, input, widget, and overlay rendering, include these `UiKitTest` scenarios when relevant:

- `slider-text`
- `text-scale-slider`
- `section-drawing`
- text-size slider interactions
- `window-edge-tests`
- `popup-pass-through`
- `open-modal`

`requested` captures are debug captures only. Do not treat them as parity assertions unless the active validation plan explicitly marks them for comparison.

### Baseline and comparison rules

- Build a deterministic GL baseline first for text/widget/font/shape renderer edits.
- Compare each non-GL API against that baseline.
- Treat missing baseline files, dimension mismatch, capture-path mismatch, and visual mismatch failures as validation failures.
- Automated matrix compare tasks must require baselines rather than silently creating missing expected images.
- Baseline captures must include every scenario that a compare run will validate.

## 7. Visual and Graphics Debugging Protocol

For visual defects, do not jump from symptom to broad code changes. Work from
rendered evidence to mechanism.

### Step 1: describe the symptom precisely

Use concrete terms:

- missing text;
- corrupted glyphs;
- wrong widget color;
- wrong size or position;
- bad alpha/scrim;
- texture upside-down;
- one-pixel edge drift;
- whole-scene mismatch;
- platform/API-only failure.

### Step 2: establish the known-good reference

- Identify the known-good platform/API, usually GL when the user says GL works.
- Use the same scene, input sequence, viewport, scale, assets, timing, and frame count.
- Capture expected, actual, and mismatch images when possible.
- Inspect the image manually before trusting only pass/fail status.

### Step 3: quantify before fixing

Capture useful numbers:

- mismatch ratio;
- max channel difference;
- mismatch bounds;
- representative pixel samples when needed;
- which scenario/frame failed.

Low mismatch can still hide a visible localized problem. High mismatch can come from one missing load/blend/readback step. Do not solve real defects by only relaxing thresholds.

### Step 4: isolate the smallest primitive

Before applying a broad renderer/UI fix, test the smallest failing primitive that can reproduce the problem:

- plain colored rectangle;
- plain button without texture;
- semi-transparent rectangle or modal scrim over existing content;
- the same button with texture or nine-patch background;
- single texture quad;
- one glyph or short text label;
- several labels that force batching;
- instanced/batched sprite path, if present;
- non-instanced fallback path, if present;
- readback-only comparison if screenshots differ but live output appears correct.

If a minimal case cannot be created or run, record the exact blocker and do not claim the root cause is proven.

### Step 5: classify the failing layer

Classify before editing:

- UI layout/state/input;
- clipping/scissor equivalent;
- shape renderer;
- sprite/texture renderer;
- text atlas/glyph placement;
- texture upload/readback;
- shader coordinate convention;
- batching/instancing/indexing;
- blend/load/store state;
- GPU resource lifetime/synchronization;
- platform surface format/readback conversion.

Fix one mechanism at a time and rerun the same focused scenario before moving to the next issue.

### Step 6: check GPU API hazards that GL can hide

For recorded-command APIs, especially WGPU and Vulkan:

- GL can appear correct because draw calls often consume current buffers immediately.
- WGPU/Vulkan can fail when vertex, index, instance, or uniform buffers are overwritten after binding but before command submission.
- If text/widgets are corrupted while GL is correct, inspect batching and buffer lifetime before changing UI layout.
- If modal, scrim, or transparent UI differs, inspect alpha blending and render-pass load/store before changing widget code.
- If texture or text atlas output differs, inspect row alignment, format conversion, mipmap generation, sampler state, and texture-coordinate orientation.
- If screenshots differ but on-screen output seems correct, inspect readback row order, bytes-per-row alignment, surface format, and channel swizzle.
- If only edges differ by one pixel, inspect viewport/rasterization conventions and decide whether comparator tolerance is appropriate only after confirming the visual is acceptable.

### Step 7: accept the fix only with evidence

A visual fix is accepted only when:

- the failing focused scenario improves for the right mechanism;
- the broader affected matrix passes or is explicitly blocked;
- expected widgets/text/layout are visible, readable, inside the viewport, and not incoherently overlapping;
- any remaining tolerance is justified by image inspection and numbers;
- tolerance changes do not hide missing widgets, missing text, wrong alpha, wrong layout, or wrong texture content.

The final report for visual work must include:

- original failure symptom or ratio;
- isolated cause;
- changed files;
- validation task/result;
- remaining tolerated difference, if any;
- platforms/APIs not run and exact reasons.

## 8. Source Discovery and Search Rules

Use smart source discovery instead of hardcoded platform commands.

- Prefer explicit file extension targeting.
- Exclude generated/build artifacts and binaries before scanning.
- Do not search inside binaries or generated bundles such as `build`, `out`, `target`, `node_modules`, `*.jar`, `*.zip`, `*.class`, `*.wasm`, `*.dll`, `*.so`, `*.dylib`.
- Do not treat bundled `*.js` output as source unless the task explicitly targets generated JavaScript.
- Use whichever search tool is available on the machine and document the exact command set in task notes.
- Never hardcode a single platform-specific command in this file.
- Empty search output is not a failure by itself. Treat it as a review signal.

## 9. Documentation Quality Rules

- Keep canonical facts in one place whenever possible.
- Avoid repeating the same decision/rule across multiple docs.
- If duplication is unavoidable, add a short cross-reference instead of copying detailed text.
- Keep examples conceptually compilable against the documented interfaces.
- Provider-specific examples must be clearly marked provider-specific.
- Nullable-return behavior must be documented for methods that may not find an object.
- For new, renamed, or deleted files, commands, APIs, properties, or launchers, update all references in one pass.

Before finishing doc changes:

1. Search for stale terms and old decisions.
2. Check that `ARCHITECTURE.md` and `COMMON_API.md` use the same names.
3. Check that examples do not use undefined classes or methods.
4. Check that examples use typed `Fdx` and explicit user-created objects.
5. Check that provider-specific examples are clearly marked.
6. Check that affected README, samples, launchers, tests, and reports are aligned.

## 10. Final Reporting Checklist

Every final report should state:

- what changed;
- why it changed;
- validation run and result;
- platforms/APIs not run and exact reasons;
- any remaining risks or follow-up work.

If ambiguity remains after one pass, propose the highest-confidence option and the safer alternative. Reject ambiguous implementation defaults that waste cycles or introduce undocumented behavior.
