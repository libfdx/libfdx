# libFDX Agent Instructions

These rules apply to the whole repository. Keep work inside the user's stated
scope, preserve unrelated changes, and prefer evidence from the repository over
assumptions.

## Sources Of Truth

Use the narrowest authoritative source:

- Java source and generated Javadocs define exact public declarations.
- Tests and samples define executable behavior and usage.
- Gradle build files and task help define projects, dependencies, artifacts,
  properties, and task names.
- [Architecture](docs/ARCHITECTURE.md) defines durable ownership and dependency
  direction.
- [Common API](docs/COMMON_API.md) defines cross-cutting lifecycle, ownership,
  portability, and performance rules.
- [Contributing](CONTRIBUTING.md) covers checkout setup and validation.
- Focused guidance stays beside its owner or in a small domain guide, such as
  [Shaders](docs/SHADERS.md), [UI Kit](docs/UI_KIT.md), the
  [Gradle plugin](libfdx/tools/gradle-plugin/README.md), and the
  [scenario validator](libfdx/extensions/scenario_validator/README.md).

Do not manually duplicate inventories that can be read from source or Gradle.

## Working Rules

- Treat the user's request as permission for normal investigation, editing, and
  proportionate validation within that scope. Ask before materially expanding
  scope or performing destructive, expensive, or external actions.
- Stop immediately when the user says stop.
- Inspect the current worktree before editing. Existing changes belong to the
  user unless the active request says otherwise.
- Before a material investigation or change, state what is being checked, why
  it matters, and what evidence will establish success.
- Confirm names, paths, declarations, tasks, and behavior from source or
  observed output before claiming correctness.
- Generated output and ignored IDE metadata are not source unless explicitly in
  scope.

## Architecture Guardrails

- Portable framework modules do not depend on providers or backends.
- Backends own runtime lifecycle and platform integration; providers implement
  optional graphics or transport capabilities.
- `Fdx` remains finite, typed, and limited to backend-owned runtime roots.
  Application-owned assets, batches, UI roots, scenes, worlds, and systems stay
  explicit.
- Provider-specific access remains explicit through provider identity, typed
  setup, and `as()` escape hatches.
- Public ownership, disposal, nullability, callback/thread, and frame-lifetime
  behavior must be clear at the API boundary.
- Prefer primitives and reusable storage in frame, render, input/UI, upload,
  ECS, and network loops. Do not add steady-state allocation without a measured
  reason.

Use the architecture and common API documents for the complete durable rules.

## Change And Validation

1. Identify the smallest affected modules, public contracts, platforms, and
   consumers.
2. Change the canonical source first, then update only directly affected tests,
   examples, or documentation.
3. Run the narrowest check that proves the result. Broaden when behavior is
   shared, public, cross-platform, or still uncertain.
4. Report exact commands, results, untested targets, and blockers.

For Android work, check `adb devices -l` and run the relevant repository launch
task when a device is available. For desktop work, compile or run the affected
desktop path. Visual changes require inspection of an actual rendered frame; a
successful build alone is not visual proof. Never describe an unavailable
platform/provider as validated.

## Documentation Policy

Update documentation when a change invalidates:

- the public introduction or getting-started path;
- a public build/plugin workflow;
- a durable architecture or ownership rule; or
- externally observable lifecycle, portability, or failure behavior that is not
  adequately documented at the declaration.

Central documentation does not need an update for an internal refactor, a new
implementation following an existing rule, additional tests, generated task
variants, or current implementation status that belongs in source, task help,
release notes, or issues.

Keep one owner for each fact. Prefer a short summary and link over synchronized
copies. When moving or removing a document, update inbound links and verify
local Markdown links and anchors.

## Final Report

State what changed and why, validation commands and results, targets not run
with reasons, and remaining risks. If ambiguity remains, give the
highest-confidence recommendation and the safer alternative.
