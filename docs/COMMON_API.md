# libFDX Common API Rules

This document defines cross-cutting behavior shared by libFDX APIs. It is not a
catalog of modules or declarations. Java source and generated Javadocs define
exact signatures; tests and samples demonstrate executable behavior.

See [Architecture](ARCHITECTURE.md) for dependency direction,
[Shaders](SHADERS.md) for shader translation, and [UI Kit](UI_KIT.md) for the
UI runtime model.

## Provider-Neutral APIs

Shared application code uses common interfaces. A common type describes the
job an object performs, not the native object used by one provider.

- Provider-specific APIs live in provider modules.
- Optional behavior is capability-gated or fails clearly.
- Native handles stay out of ordinary application APIs. Explicit setup
  contracts are the exception at backend/provider boundaries.
- A backend or provider does not silently substitute behavior with different
  semantics.
- Higher-level modules depend on common modules, not on GL, Vulkan,
  Direct3D 12, WGPU, or a concrete backend.

Startup configuration is typed. Backend options belong to backend
configuration; provider options belong to provider setup. Public APIs do not
use generic property maps as a substitute for stable contracts.

## Provider Access And Compatibility

Provider-backed common objects implement `ProviderHandle` where advanced
access is useful.

- `providerId()` identifies the logical provider.
- `as()` is an explicit provider-specific escape hatch, not the normal
  programming model.
- Callers check provider identity before requesting a provider view.
- Unsupported views fail clearly.
- A provider view is valid only while its common owner and native resource are
  valid.

Matching provider IDs do not prove resource compatibility. Provider-backed
resources may interact only when they belong to a compatible resource domain,
such as one native device or an explicitly shared context group.

## Ownership And Disposal

Ownership is visible at creation time or documented by the returning method.

- `dispose()` is safe to call more than once.
- Using a disposed provider-backed object fails clearly.
- A container disposes only resources it owns.
- Borrowers do not dispose borrowed references.
- Parent-owned views become invalid with their parent.
- Ownership transfer is explicit; returning or accepting an object does not
  imply transfer by itself.

The backend owns runtime roots exposed through `Fdx`. Application code owns
objects it creates, including asset managers, batches, UI roots, scenes, and
game systems. These objects do not become global runtime services.

## Nullability And Lookup

Absence is explicit and consistent:

- `find(...)` returns `null` when no matching value exists.
- `get(...)` or `require(...)` fails when its promised value is unavailable.
- Optional services return `null` only where their declaration documents it.
- Collections that allow stored `null` values distinguish them from missing
  keys through membership checks.

New nullable returns are documented at the declaration. APIs do not introduce
undocumented sentinel objects or silently manufacture fallback values.

## Application And Callback Lifecycle

The backend creates platform/provider services, constructs `Fdx`, waits for
required asynchronous setup, calls `ApplicationListener.create(Fdx)` once, and
then forwards application lifecycle events.

`render()` is the normal per-frame callback. Timing and frame identity come
from `fdx.app()`. `onFrameEnd()` is reserved for work that must happen after
application rendering while the current frame is still active, such as
backend-owned capture or readback hooks.

Operations owned by a running application dispatch user callbacks on the
application event loop unless their declaration states another policy.
Provider, socket, native, or worker threads enqueue work; they do not invoke
ordinary application, UI, engine, or scenario callbacks directly.

## Futures

`FdxFuture<T>` is the common completion primitive.

- A future completes once with either a value or failure.
- Success and failure callbacks run once, in registration order for the active
  completion path.
- Callback registration returns the same future for chaining.
- A callback failure does not change the completed result or prevent later
  queued callbacks. The first callback failure propagates after dispatch and
  later failures are suppressed on it.
- Blocking access is valid only after completion. Portable code does not assume
  that blocking threads exist.
- Cancellation is not implied unless a more specific API defines it.

Provider completion paths report propagated callback failures through their
logger or provider error path.

## Graphics And Frame Lifetime

`Graphics` is a manager, `GraphicsContext` is one rendering context,
`GraphicsDevice` creates persistent resources, and `GraphicsFrame` exposes the
backend-owned active frame. These roles are deliberately distinct.

The active frame is valid only during backend-owned frame callbacks.

- Frames, framebuffers, command encoders, attachment views, and pass objects are
  borrowed frame-owned handles.
- Application code does not retain or dispose them.
- Passes accept no more commands after `end()`.
- Submission and presentation remain provider-owned.
- Successful end-of-frame readback ends application command recording for that
  frame.

Persistent buffers, textures, shader modules, pipelines, and meshes are
application-owned unless another owner explicitly accepts them. Resources,
descriptors, bindings, and commands reject disposed or incompatible values
before native calls where possible.

Recorded-command providers retain native allocations referenced by recorded
work until it is submitted or abandoned. Later CPU rewrites or resource
disposal must not mutate already recorded commands. Immediate execution on one
provider is not evidence that delayed-submission providers are safe.

WGSL is the public portable shader-language boundary. Applications may author
it directly or compile typed shader graphs to canonical WGSL. Translation and
graph compilation happen during setup or shader-module creation, never per
frame.

## User-Owned High-Level Features

Assets, 2D/3D renderers, UI, external game engines, and scenario validation are optional
application-owned features. Their composition remains explicit; there is no
process-global backend, provider, scene, or world.

- Asset loading keeps decoded source data separate from GPU resources.
- Batches and renderers expose explicit recording scopes and reuse prepared
  state.
- Cameras are user-owned values passed to renderers rather than global runtime
  services.
- UI retains composition/input/layout state inside an application-owned root.
- External engine worlds, scenes, systems, and editors remain owned by that
  engine. They may borrow libFDX runtime roots and frame values only within the
  lifetimes documented by the corresponding libFDX declarations.
- Scenario validation drives public input, time, event, probe, and capture
  boundaries without replacing normal runtime behavior.

Focused usage and configuration belong in module/domain guides and Javadocs,
not in this cross-module contract.

## Hot-Path Behavior

Frame, render, upload, input/UI, game-loop, and network-processing paths are designed
for reuse.

- Prefer primitives for state, configuration, identifiers, and counters.
- Reuse buffers, descriptors, command storage, arrays, collections, events,
  and render objects.
- Do not add steady-state Java allocation without a measured reason.
- Primitive UI state uses dedicated primitive types such as
  `UiBooleanState` and `UiIntState`, not boxed `UiState<Boolean>` values.
- Setup, explicit editor actions, error reporting, and bounded cache growth may
  allocate when their ownership and frequency are clear.

## Changing A Public Contract

Before changing a public interface, determine its owner and lifecycle. Define
nullability, disposal, callback/thread behavior, frame/resource lifetime,
provider compatibility, and failure behavior at the declaration and in tests.

Update this document only when a cross-cutting rule changes. Module additions,
exact methods, implementation status, provider lists, task names, and artifact
catalogs remain in source, build metadata, module-local guides, or issues.
