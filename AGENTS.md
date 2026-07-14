# libFDX Agent Instructions

These rules apply to the entire repository. `AGENTS.md` coordinates work; it is
not an architecture, API, platform, or troubleshooting reference.

## 1. Sources Of Truth

Use the narrowest authoritative document:

- `docs/ARCHITECTURE.md`: module ownership, dependency direction, package
  roots, Gradle topology, artifacts, and provider/backend boundaries.
- `docs/COMMON_API.md`: portable behavior, lifecycle, ownership, nullability,
  and provider-neutral contracts.
- Domain guides: `docs/SHADERS.md`, `docs/UI_KIT.md`, and
  `docs/SCENARIO_VALIDATOR.md`.
- Workflow guides: `docs/BUILDING.md`, `docs/SAMPLES.md`,
  `docs/BUILDERS.md`, `docs/TESTING.md`, and `benchmark/README.md`.
- Java source and generated Javadocs: exact declarations and signatures.
- Tests and samples: executable behavior and usage examples.

When a durable decision changes, update its canonical document first, then
align affected workflow docs, samples, tests, and this file only when the
process itself changed.

## 2. Working Contract

### Permission

- Do not investigate, edit, execute, test, capture, or validate without explicit
  user permission.
- If scope is unclear, ask before touching files.
- Stop immediately when the user says stop.

### Communication

Before each investigation or fix, state:

- what will be inspected or changed;
- why it matters;
- what evidence will count as success.

Report intermediate findings when they change the approach, reveal risk, or
block progress.

### Recovery State

At the start of a session, read `.agents/agents_memory.md` if it exists. Keep it
limited to the active request, current state, last completed step, next step,
validation, and blockers. Replace stale content; do not append history.

Before changing tracked files or validation state, update that memory. For
multi-step work, maintain `.agents/agents_plan.md` with ordered steps, targets,
rationale, actions, dependencies, status, and acceptance evidence. Clear the
plan when the work is complete. Both files are local, ignored scratch state.

### Evidence, Not Guessing

- Confirm symbols, call shapes, tasks, paths, and behavior from source, docs, or
  observed output before claiming correctness.
- Distinguish current implementation from proposals. Future work belongs in
  issues or roadmap tooling, not current contracts.
- Do not claim a platform, provider, or graphics API was validated unless it
  was actually run.

## 3. Change Workflow

1. Classify the change: docs/architecture, API, code, task wiring, platform, or
   visual/renderer/UI.
2. Identify affected modules, files, tasks, platforms, APIs, and public names.
3. Find stale names, examples, commands, properties, and behavior descriptions.
4. Update the canonical source first, then all affected user-facing and
   executable references in the same change.
5. Run the smallest validation that proves the behavior; broaden only when the
   change is shared, public, cross-platform, or still uncertain.
6. Record exact evidence and blockers in recovery state before reporting.

Generated build output and ignored IDE metadata are not source unless the user
explicitly places them in scope.

## 4. Architecture And API Guardrails

Follow `ARCHITECTURE.md` and `COMMON_API.md`; do not restate their full rules
here. In particular:

- Keep provider-neutral modules independent from backends and provider
  implementations.
- Keep typed `Fdx` finite and limited to backend-owned runtime roots. User-owned
  objects such as asset managers, batches, UI roots, scenes, worlds, and game
  systems stay explicit.
- Keep provider-specific access explicit through provider IDs, typed provider
  setup, and `as()` escape hatches.
- Prefer primitives and dedicated primitive UI state types; do not introduce
  boxed primitive state such as `UiState<Boolean>`.
- Classify public interface changes by ownership and lifecycle before editing
  their contracts.

When public behavior changes, synchronize architecture/API docs, README and
workflow docs, samples, tests, launchers, Gradle tasks, benchmarks, and report
generators wherever affected.

## 5. Performance

- Prefer non-allocating or bounded-reuse paths in frame, render, input/UI,
  upload, and network processing loops.
- Reuse builders, buffers, arrays, command storage, render resources, and
  staging memory.
- Do not add Java object allocation to a frame loop without a measured reason.
  If correctness temporarily requires allocation, state it and prefer a bounded
  reuse design before finishing.

## 6. Validation

Use [Testing](docs/TESTING.md) for task selection, platform entry points, visual
evidence, parity matrices, baselines, and renderer-debugging procedure.

Minimum expectations:

- Code: relevant compile/build/test/run evidence for the touched scope.
- Docs/process: targeted reading, stale-term checks, local link/anchor checks,
  and `git diff --check` can be sufficient.
- Task wiring/public API/shared core: broaden to every affected consumer or
  platform path.
- Android in scope: check `adb devices -l`; run the relevant assemble and
  repository Android launch task when a device is available.
- Desktop in scope: run the relevant desktop compile/build and launcher.
- Visual work: inspect a real rendered frame. A successful build or capture task
  alone is not visual validation.

For shared graphics/UI behavior, use GL as the known-good reference when
appropriate and compare the same scene, viewport, scale, assets, input, timing,
and frame count across providers in scope. Report each matrix cell as `PASS`,
`BLOCKED`, or `NOT RUN` with a reason. Never hide a real defect by only relaxing
comparison tolerance.

Keep disposable captures and validation reports under generated `build/reports`
paths. Do not add persistent tracked validation reports unless requested.

If a target cannot run, complete every other relevant check and report the
exact missing SDK, device, toolchain, hardware, runtime, dependency, or task
failure.

## 7. Documentation Quality

- Keep each durable fact in one authoritative place.
- Replace duplicated detail with a short summary and link.
- Keep current contracts separate from future plans and implementation status.
- Keep examples conceptually compilable and verify referenced types, methods,
  tasks, files, and properties.
- Mark provider-specific examples explicitly.
- Document nullable returns where absence is valid.
- When names or behavior change, update all references in one pass.

Before finishing documentation changes, confirm:

1. `ARCHITECTURE.md` and `COMMON_API.md` agree on ownership and names.
2. Examples use typed `Fdx` and explicit user-created objects.
3. Provider-specific code is clearly identified.
4. README, workflow guides, samples, tests, launchers, and generated-report
   inputs remain aligned.
5. Local Markdown links and anchors resolve.

## 8. Source Discovery

- Prefer fast source search, explicit extensions, and tracked-file inventories.
- Exclude `build`, `out`, `target`, `node_modules`, binaries, archives, classes,
  native libraries, and generated bundles unless the task targets them.
- Do not treat generated JavaScript as source by default.
- Empty search output is a review signal, not automatically a failure.

## 9. Final Report

State:

- what changed and why;
- validation commands and results;
- platforms/providers/APIs not run and exact reasons;
- remaining risks or follow-up work.

If ambiguity remains, give the highest-confidence recommendation and the safer
alternative. Do not choose an undocumented default merely to avoid asking for
needed direction.
