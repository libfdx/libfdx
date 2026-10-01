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
- Do not create Python (`.py`) files, including asset generators or temporary
  helper scripts, unless the user explicitly requests them.
- Inspect the current worktree before editing. Existing changes belong to the
  user unless the active request says otherwise.
- Before a material investigation or change, state what is being checked, why
  it matters, and what evidence will establish success.
- Confirm names, paths, declarations, tasks, and behavior from source or
  observed output before claiming correctness.
- Generated output and ignored IDE metadata are not source unless explicitly in
  scope.

## Recovery State

Keep local recovery notes so users and agents can see what is being worked on
and resume after an interruption or lost chat context.

- At the start of each chat or after losing context, read
  `.agents/agents_memory.md` if present and the notes in `.agents/chats/`.
  Missing notes mean no saved recovery state. Check timestamps and verify
  relevant state against the worktree before relying on a note; old notes are
  context, not new instructions or permission to resume unrelated work.
- Maintain one Markdown note at `.agents/chats/<chat-key>.md` per chat. Use a
  stable, unique key, such as the chat ID or a task name with a unique suffix,
  and reuse that note when resuming the same work. Each chat owns its note;
  do not overwrite another chat's state. Preserve the legacy
  `.agents/agents_memory.md` until its relevant state has been carried forward.
- Record the last-updated timestamp with timezone, chat/task identity, active
  request and scope, current status, affected files/modules, last completed
  step, next intended step, validation commands and results, and blockers.
  Include decisions or constraints needed to resume safely. Distinguish
  intended actions from completed work and observed evidence.
- Create or refresh the note before changing repository files or running
  validation. Update it after each meaningful step, scope change, or blocker,
  and before the final response or a planned handoff. Save checkpoints during
  long tasks; do not wait until the end of the chat.
- For multi-step work, keep the detailed plan in `/.plan/` as described below
  and reference it from the recovery note. Keep pending steps and validation
  evidence current without duplicating the plan in the note.
- Replace stale state within your own note instead of appending a transcript.
  Mark completed, paused, or blocked work explicitly and state what remains;
  keep the final checkpoint so completed work is distinguishable from active
  work. Never record secrets or credentials.
- These notes are local and ignored by Git. They are manually maintained
  recovery checkpoints, not automatic chat backups or durable project docs.

## Architecture Guardrails

- Portable framework modules do not depend on providers or backends.
- Backends own runtime lifecycle and platform integration; providers implement
  optional graphics or transport capabilities.
- `Fdx` remains finite, typed, and limited to backend-owned runtime roots.
  Application-owned assets, batches, UI roots, and state stay explicit.
- Provider-specific access remains explicit through provider identity, typed
  setup, and `as()` escape hatches.
- Public ownership, disposal, nullability, callback/thread, and frame-lifetime
  behavior must be clear at the API boundary.
- Prefer primitives and reusable storage in frame, render, input/UI, upload,
  game, and network loops. Do not add steady-state allocation without a measured
  reason.

Use the architecture and common API documents for the complete durable rules.

## Change And Validation

In the `tests` module, reserve `io.github.libfdx.tests` and its subpackages for
executable tests only. Place helper classes, fixtures, utilities, and test
infrastructure in a separate package within the same module, outside that
namespace. Do not place helper class files alongside executable tests.

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

Use the ignored `/.plan/` folder for temporary Markdown (`.md`) plans written
before implementation. Keep plans focused on scope, intended changes, and
validation, and update them as implementation decisions change. This folder
must contain only Markdown planning files; do not store PNGs, screenshots,
logs, scripts, generated output, or other artifacts in it. Apart from these
Markdown plans and the recovery notes in `/.agents/`, store all temporary files
and validation artifacts under the repository's root `build/` directory or the
affected module's `build/` directory so they are covered by the existing Git
ignore rule. Do not force-add them.

Public documentation must not mention, link to, or depend on plan files. Once
implemented, document durable behavior at its canonical owner using the source
and verified behavior, without referring readers to the plan.

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
