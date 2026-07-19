# libFDX Common API

This document defines the provider-neutral behavior shared by libFDX modules. It
is the contract for ownership, lifecycle, portability, nullability, and provider
boundaries. It deliberately does not copy every Java declaration.

Use:

- Java source and generated Javadocs for exact types, signatures, overloads, and
  defaults;
- [ARCHITECTURE.md](ARCHITECTURE.md) for module ownership, dependency direction,
  package roots, Gradle projects, and Maven artifacts;
- [SHADERS.md](SHADERS.md) for the WGSL toolchain and shader profiles;
- [UI_KIT.md](UI_KIT.md) and
  [SCENARIO_VALIDATOR.md](SCENARIO_VALIDATOR.md) for domain-specific behavior.

If this document and source disagree, treat the mismatch as a defect. Confirm
the intended behavior, then update source, tests, and this contract together.

## Topics

- [1. Contract Principles](#1-contract-principles)
- [2. Foundation and Runtime Core](#2-foundation-and-runtime-core)
- [3. Application and the `Fdx` Root](#3-application-and-the-fdx-root)
- [4. Files and Storage](#4-files-and-storage)
- [5. Input](#5-input)
- [6. Displays](#6-displays)
- [7. Networking](#7-networking)
- [8. Assets](#8-assets)
- [9. Graphics](#9-graphics)
- [10. Graphics 2D](#10-graphics-2d)
- [11. Graphics 3D](#11-graphics-3d)
- [12. UI Kit](#12-ui-kit)
- [13. ECS](#13-ecs)
- [14. Scenario Validator](#14-scenario-validator)
- [15. Current Scope Boundaries](#15-current-scope-boundaries)
- [16. Contract Change Checklist](#16-contract-change-checklist)

## 1. Contract Principles

### 1.1 Provider-neutral first

Shared application code uses common interfaces. A common type describes the
job an object performs, not the native object used by one provider.

- Provider-specific APIs live in provider modules.
- Optional behavior is capability-gated or fails with a clear unsupported
  operation/configuration error.
- Native handles stay out of normal application APIs. `NativeWindow` is the
  explicit backend-to-graphics-provider setup exception.
- A backend or provider must not silently substitute behavior with different
  semantics.
- Higher-level modules depend on common modules, not GL, Vulkan, Direct3D 12,
  WGPU, or a concrete platform backend.

### 1.2 Provider access is explicit

Provider-backed common objects implement `ProviderHandle` where advanced access
is useful.

- `providerId()` identifies the backing provider with a stable logical value.
- `as()` is an explicit escape hatch, not the normal programming model.
- Callers should check provider identity before requesting a provider-specific
  view.
- Unsupported views fail clearly; the common API does not promise provider
  types that do not exist.
- A provider view is valid only as long as its common owner and backing native
  resource are valid.

Matching provider IDs do not prove resource compatibility. Resources may be
shared only when their contexts belong to the same provider resource domain,
such as one native device or an explicitly shared GL group.

### 1.3 Ownership and disposal

Ownership must be visible at creation time or documented at the returning
method.

- `dispose()` is safe to call more than once.
- Using a disposed provider-backed object fails clearly.
- A container disposes only resources it owns.
- Borrowed references are not disposed by the borrower.
- Parent-owned views become invalid when their parent is disposed.
- Frame-owned handles must not be retained beyond that frame.

User-created objects such as `AssetManager`, `UiRoot`, `World`, batches, scenes,
and game systems are not backend-owned services and are never added to `Fdx`.

### 1.4 Nullability and lookup behavior

Absence is explicit:

- a lookup for an object that may not exist returns `null`;
- a `find(...)` operation returns `null` when no matching value exists;
- a `get(...)` or `require(...)` operation fails clearly when its promised value
  is unavailable;
- optional services and unsupported subservices return `null` only where their
  API contract says so;
- collections distinguish a missing key from a present key with a `null` value
  through `containsKey(...)`.

New nullable returns must be documented in source and here when they affect a
module boundary. Do not introduce an undocumented sentinel object.

### 1.5 Asynchronous work

`FdxFuture<T>` is the common completion primitive.

- A future completes once with either a value or failure.
- Success and failure callbacks run once, in registration order for the active
  completion path.
- Callback registration returns the same future so registrations can be
  chained.
- Operations owned by a running application dispatch callbacks on the
  application/main event loop. APIs usable without an application document
  their own dispatch policy.
- A callback failure does not change the completed result or stop later queued
  callbacks. After dispatch, the first callback failure propagates and later
  failures are suppressed on it.
- Provider completion code reports propagated callback failures through the
  logger or its provider error path.
- Blocking waits are valid only after completion; portable APIs do not assume
  that blocking threads exist.
- Cancellation is not part of the current contract.

### 1.6 Hot-path behavior

Per-frame APIs are designed for reuse.

- Prefer primitives for state, configuration, and counters.
- Reuse buffers, descriptors, command storage, collections, and render objects.
- Do not allocate Java objects in frame, render, upload, input, UI update, ECS
  update, or network-processing loops unless a measured need justifies it.
- UI primitive state uses `UiBooleanState`, `UiIntState`, `UiFloatState`,
  `UiLongState`, or `UiDoubleState`, not boxed `UiState<Boolean>`-style values.

### 1.7 Naming and API shape

- Public UI types use the `Ui` prefix.
- Graphics manager, context, device, frame, and resource names remain distinct;
  do not use one generic "graphics API" object for all roles.
- Descriptors carry creation values. Resources expose stable metadata,
  lifecycle, and provider access.
- Startup options use typed backend/provider configuration. Do not add generic
  property maps to public config APIs.
- Shared APIs use Java 25 where provider-neutral. Android backend/provider
  projects retain their supported Java 17 bytecode boundary.

## 2. Foundation and Runtime Core

### 2.1 Core

`framework/fdx/core` owns the small contracts every runtime module may depend
on: disposal, framework errors, logging, provider identity/access, futures, and
internal runtime service markers.

`FdxService` is for private backend/provider wiring. It does not authorize a
public service locator, generic resolution from `Fdx`, or user-created objects
in the runtime root.

Core does not depend on assets, graphics, UI, providers, extensions, or
backends.

### 2.2 Math

`framework/math` owns provider-neutral value and calculation types shared by
rendering and feature modules. Math types must not depend on platform or
provider APIs. Mutable hot-path types should offer allocation-free mutation and
copy operations where practical.

### 2.3 JSON

`framework/json` is a standalone provider-neutral JSON tree, parser, writer,
and explicit codec layer.

- Parsing and writing do not require assets or a running application.
- `JsonCodec<T>` owns object construction, field mapping, defaults, missing
  values, and type tags.
- JSON mapping does not use reflection, annotations, or automatic field
  scanning.
- Asset loading may produce `JsonValue`, but the JSON module does not depend on
  asset management.

### 2.4 Collections

`framework/collections` is pure Java and provider-neutral.

- Collection behavior is deterministic for the documented operation.
- Object maps reject `null` keys where the source contract says so.
- Missing keys and stored `null` values remain distinguishable.
- Iteration helpers intended for hot paths should be reusable and should not
  create per-element wrapper objects.

### 2.5 Runtime core services

The runtime-core portion of `framework/fdx/core` supplies small framework-wide
native capabilities registered by a backend/platform. It is not a game-facing
service registry.

Current capabilities include FreeType font rasterization and optional WGSL
translation for providers that require GLSL, SPIR-V, HLSL, or MSL.

- Font rasterization occurs during load/cache creation, never every UI frame.
- Shader translation occurs during shader-module creation, explicit editor
  recompilation, or validation, never in the render loop.
- A provider that consumes WGSL directly does not need the translation
  capability.
- A platform that does not need a capability is not forced to package it.
- When a required provider is absent, the operation fails clearly; there is no
  silent Java or alternate-algorithm fallback.

Backend/platform projects own native registration and bridges. Higher-level
font and shader APIs consume these capabilities without exposing bridge details.

## 3. Application and the `Fdx` Root

### 3.1 Application lifecycle

User application classes implement `ApplicationListener` or extend its adapter.
The backend:

1. creates the selected platform and provider services;
2. constructs the typed `Fdx` root;
3. waits for asynchronous graphics attachment readiness when required;
4. calls `create(Fdx)` once;
5. forwards resize, render, pause, resume, and disposal lifecycle events.

`render()` is the per-frame callback. Frame timing and frame identity come from
`fdx.app()`; there is no separate mandatory `update()` callback.

`onFrameEnd()` runs after `render()` while the current graphics frame is still
active. It exists for backend-owned capture, readback, and validation hooks.
Normal simulation and rendering stay in `render()`.

### 3.2 Typed runtime access

`Fdx` is finite and explicit. It currently exposes:

| Accessor | Contract |
| --- | --- |
| `app()` | Non-null application lifecycle, commands, and frame timing. |
| `displays()` | Non-null display manager. |
| `graphics()` | Non-null graphics manager. |
| `input()` | Non-null input service. |
| `files()` | Non-null file system. |
| `storage()` | Non-null persistent/cache storage service. |
| `network()` | Nullable; `null` when the backend has no networking implementation. |
| `logger()` | Non-null framework/application logger. |

Do not add a generic lookup method. Add a direct accessor only for a
backend-owned runtime system with a stable provider-neutral contract.

There is no current audio module or `Fdx.audio()` accessor.

### 3.3 Backends and configuration

`ApplicationBackend` is launcher infrastructure. Shared game code does not use
it after startup.

- Provider selection is a startup decision.
- If exactly one compatible provider exists, a backend may select it when the
  launcher omitted a choice.
- If multiple compatible providers exist, the backend requires an explicit
  choice or fails with a clear configuration error.
- Provider changes normally require application restart.
- Backend options live on typed backend configs; provider options live on typed
  provider setup objects.

The web backend may create a private preload UI before game creation. It
disposes that UI before `ApplicationListener.create(...)`; this does not make UI
backend-owned or add UI to `Fdx`.

## 4. Files and Storage

### 4.1 Files

`FileSystem` and `FileHandle` represent portable locations and operations.

- A handle retains its location semantics; it is not merely an unqualified
  operating-system path.
- Classpath/application assets may be read-only or packaged differently by each
  platform.
- Writable local, external, or absolute access is capability- and
  platform-dependent.
- Unsupported watch, mapping, native path, or write operations fail clearly.
- File watching is provider-backed and exposes provider access where useful.
- Platform-native handles are available only through backend/provider APIs.

Code that needs a packaged asset should use the file-system location intended
for assets instead of assuming a desktop working directory.

### 4.2 Persistent storage

`Storage` owns named local and cache stores.

- Local stores hold user/application state expected to persist.
- Cache stores hold rebuildable data and may disappear at any time.
- Stores operate in memory and persist mutations only on explicit `flush()`.
- Load and flush failures surface as framework errors.
- JSON/object mapping remains explicit through codecs.
- The storage contract does not promise encryption or secure secret storage.

## 5. Input

`Input` is the single runtime entry point for keyboard, pointer, touch, text
input, cursor, and gamepad access.

- Event routing and polled state describe the same current input system.
- Platform text input is a session: show/configure, update as needed, and hide.
- A backend without a soft keyboard may implement session display as a no-op
  while still dispatching hardware/window text events.
- Back/Escape behavior remains explicit so platform navigation is not hidden.
- Cursor and gamepad service objects are non-null. Their capabilities describe
  what the current backend/provider can do.
- Looking up a disconnected or missing gamepad returns `null`.
- Gamepad discovery/mapping remains provider-backed and provider-specific
  details use the explicit provider escape hatch.
- Native text editors, IME policy, cursor modes, clipboard, and similar startup
  behavior belong in typed backend configuration when they are platform-owned.

Input and UI event loops must reuse event/state objects in hot paths.

## 6. Displays

`Display` is a platform presentation area: a desktop window, browser canvas,
mobile view, or another backend-owned target. It is intentionally separate from
graphics.

- `Displays.main()` returns the non-null backend-created main display.
- Creating additional displays is optional and capability-gated.
- Destroying `null` or the main display is ignored; additional displays follow
  their documented close/disposal path.
- Requested size is a startup preference where a platform can honor it. Mobile
  platforms report and render to the actual view/surface size.
- Logical size, framebuffer size, and content scale remain distinct.
- Content scale reflects the platform DPI/device-pixel-ratio model, with a
  framebuffer/logical-size fallback where necessary.
- `Display` may expose provider/backend details through `ProviderHandle`, but
  normal code uses common display state.
- `framework/display` never depends on `framework/graphics`. Graphics may attach
  a context to a display through its own configuration.

## 7. Networking

Networking is optional at the `Fdx` boundary and capability-driven below it.

- `Fdx.network()` returns `null` when no network service exists.
- HTTP, WebSocket, and multiplayer transport accessors return `null` when that
  subservice is unsupported.
- Request/response and connection operations are async-first.
- Nullable response bodies, headers, peers, and lookup values are documented at
  their returning methods.
- WebSocket lifecycle preserves explicit open, message, error, close, and
  disposal events.

### 7.1 Process-driven multiplayer

Multiplayer endpoints dispatch user callbacks only from their explicit
`process(deltaTime)` call on the application thread. Socket, native, WebRTC,
Bluetooth, and worker threads enqueue data; they never call normal game
listeners directly.

- Connected endpoints are normally processed once per game frame.
- Processing configuration bounds ticks, packets, bytes, sends, and catch-up
  work.
- Reusable buffer pools and packet queues avoid steady-state packet allocation.
- A received packet view is valid only during its callback unless its backing
  buffer is explicitly retained.
- Reliable saturation reports backpressure; it is not silently dropped.
- Unreliable traffic may be dropped only according to configured policy and is
  reflected in statistics.
- Codec and transform layers are explicit. Encryption/compression algorithms
  are not implied by the base transport contract.

### 7.2 WebRTC

WebRTC is an extension transport provider, not a special `Fdx` root. It plugs
into the common transport SPI and keeps signaling explicit. Provider callbacks
enqueue state/data and application-thread `process(...)` performs user-visible
dispatch. Browser, native, and platform setup remains in the provider/backend
module.

## 8. Assets

`AssetManager` is user-created. It coordinates loading, caching, dependencies,
application-thread completion, retrieval, unloading, and owned-resource
disposal.

- Loaders receive `AssetLoadContext`, not the root `Fdx` object.
- Base loaders produce provider-neutral source data such as `ImageData` and
  `JsonValue`.
- GPU-backed loaders live in an explicit bridge/high-level module that already
  depends on both assets and graphics.
- Loading a texture, region, font, model, or similar GPU object requires its
  corresponding loader registration during setup.
- `update()` performs queued application-thread completion such as GPU upload.
- `finishLoading()` advances `update()` until the current requested work is no
  longer queued or loading.
- `get(...)` fails clearly when an asset is unavailable or has the wrong type.
- `find(...)` returns `null` for an unavailable or mismatched asset.
- A handle's asset value remains `null` until loading succeeds.
- Unloading a queued or loading handle, or disposing its manager, completes the
  handle's future with failure.
- A loader result that arrives after unload or manager disposal is not
  published; a disposable result is released immediately.
- The manager disposes only assets transferred to it by a loader.

Decoded image bytes and GPU textures are different ownership domains. A source
loader must not hide provider-backed texture creation.

## 9. Graphics

### 9.1 Runtime model

`Graphics` is the manager returned by `Fdx.graphics()`. `GraphicsContext` is one
provider-backed rendering context. `GraphicsDevice` creates persistent
resources. `GraphicsFrame` exposes the backend-owned current frame.

- `Graphics.main()` returns the non-null main context.
- Additional contexts are an advanced optional capability, mainly for desktop
  multi-display/provider use.
- Destroying `null` or the main context is ignored; live additional attachments
  are disposed through the manager.
- An additional on-window context names its `Display` explicitly. There is no
  hidden current display.
- Shared rendering code uses `GraphicsContext`, never a provider context that
  the selected provider may not expose.

### 9.2 Backend/provider setup boundary

The backend creates a display and `NativeWindow`. A selected
`GraphicsAttachmentProvider` declares window/context requirements and creates a
`GraphicsAttachment` from the provider-neutral environment.

- Requirements are applied before the native target is created.
- Provider setup depends on common environment types, not a concrete backend.
- `NativeWindow` is setup infrastructure, not a gameplay API.
- Provider configuration stays on the provider setup object.
- Backends wait for attachment readiness before creating the user application.
- The backend/provider owns begin-frame, end-frame, resize, command submission,
  and presentation.

`Display` owns the presentation area. `GraphicsAttachment` owns the graphics
lifecycle attached to it. Offscreen targets are textures inside an existing
context and do not require another display.

### 9.3 Frame lifetime

`GraphicsContext.currentFrame()` is valid only during a backend-owned frame,
normally inside `ApplicationListener.render()` and `onFrameEnd()`.

- The frame, framebuffer, command encoder, and attachment views are borrowed
  frame-owned handles.
- They must not be stored across frames or disposed by application code.
- Passes are scoped; a pass accepts no more commands after `end()`.
- Application code records common commands. Submission and presentation remain
  provider-owned.
- Framebuffer readback is an end-of-frame operation. After successful readback,
  no more commands are recorded for that frame; provider end-frame may then be
  a no-op.

### 9.4 Persistent resources

Buffers, textures, shader modules, pipelines, and meshes are application-owned
disposable resources unless a higher-level owner explicitly accepts them.

- Resource metadata reports creation values.
- Resource compatibility uses provider resource-domain identity, not provider
  ID equality alone.
- Writes, descriptors, bindings, and command encoding reject disposed or
  incompatible resources before native calls.
- A texture's default view is parent-owned and becomes invalid with the texture.
- A non-indexed mesh has no index buffer; optional retained CPU attributes are
  `null` when not supplied or retained.
- Higher-level modules wrap the common `Mesh` with domain semantics rather than
  introducing a second portable GPU mesh resource.

Recorded-command providers must retain any native allocation referenced by
recorded work until that work is submitted or abandoned. Later rewrites or
disposal must not mutate or invalidate already recorded commands. This rule is
essential for Vulkan, Direct3D 12, and WGPU even when immediate GL behavior
appears correct.

### 9.5 Rendering conventions

- Public shader authoring is WGSL-only. Translation and profile rules are in
  [SHADERS.md](SHADERS.md).
- Translation happens at setup/module creation, never per frame.
- Vertex and instance layouts state their step mode explicitly.
- Current common indexed drawing uses unsigned 16-bit indices.
- Texture filtering defaults to linear; nearest filtering is explicit.
- Texture wrapping defaults to clamp-to-edge; repeat modes are explicit.
- Scissor and viewport coordinates use framebuffer pixels with `(0, 0)` at the
  lower-left. Providers with another native origin convert internally.
- The current common render pass exposes one color attachment and an optional
  depth attachment. Native subpasses and layout-transition handles remain
  provider-specific.
- Requested unsupported formats, views, binding layouts, or descriptors fail
  clearly.

### 9.6 Camera

Shared camera state lives in `framework/camera`; input-backed controllers live
beside it in the controller package. Camera objects are user-owned and reusable.
Graphics remains independent from camera/controller concerns. Renderers accept
camera state rather than discovering a global camera.

## 10. Graphics 2D

`framework/g2d` is a provider-neutral 2D toolkit built on common graphics.

- Batches, shape renderers, fonts, atlases, particles, and tile renderers are
  user-created; disposable GPU resources have explicit owners.
- `begin()`/draw/`end()` scopes are explicit. Helpers that draw through a
  caller-owned batch do not begin or end it implicitly.
- Reusable batches and renderers cache pipelines, buffers, bindings, and CPU
  staging storage.
- Draw submission performs no steady-state Java allocation.
- Regions reference textures and normalized UVs; they do not own their texture
  unless an enclosing owner says so.
- Tile ID `0` is empty. Missing regions are skipped, and region lookup for an
  empty/missing ID returns `null`.
- Tile visible-area rendering clamps to intersecting cells and preserves the
  full-map path.
- Bitmap fonts combine provider-neutral glyph/layout metadata with
  provider-backed page textures.
- `.fnt` fonts load their declared pages. FreeType `.ttf`/`.otf` assets are
  rasterized during loading into cached bitmap atlases.
- Generated atlas resolution should match or oversample effective UI scale.

Exact batch state transitions, draw overloads, font metrics, atlas formats, and
particle parameters belong to source/Javadocs rather than this contract.

## 11. Graphics 3D

`framework/g3d` owns the high-level model, material, shader, animation, scene,
lighting, and render-path concepts built on common graphics and camera state.

- Normal 3D code does not depend on provider-specific graphics classes.
- `Batch3D` is the submission contract; implementations reuse shaders,
  pipelines, uniform/storage buffers, bindings, and queues.
- Model parts add domain semantics around `framework/graphics` meshes.
- Render targets, framebuffers, depth, and future multi-target primitives remain
  graphics concepts consumed by g3d.
- Opaque work is ordered for state/depth efficiency; transparent work preserves
  required order and is rendered back-to-front where appropriate.
- Custom shader providers receive standard render context rather than bypassing
  common graphics.
- Environment references such as shadow maps and procedural skies are
  non-owning unless their creator explicitly transfers ownership.
- Animation owns provider-neutral clips/channels and updates instance-local
  transforms. Hot updates reuse arrays and transform storage.
- GPU skinning, instancing, and other optimizations use common capabilities with
  clear fallbacks when unavailable.
- Per-frame submission is allocation-free after models, renderers, materials,
  shaders, and queues are prepared.

Feature-specific parameters and all exact renderer declarations remain in
source/Javadocs. Future rendering techniques belong in issues until a source
type and validation exist.

## 12. UI Kit

UI Kit is a user-created retained runtime with declarative Java authoring. The
complete domain guide is [UI_KIT.md](UI_KIT.md).

- `UiRoot` owns composition, persistent nodes, focus, input dispatch, layout,
  animation state, drawing state, and disposal.
- `UiScope` describes content; reconciliation retains interaction and layout
  state between compositions.
- No annotations, reflection, compiler plugin, or generated source are needed.
- UI is not a service on `Fdx`.
- Primitive state uses dedicated primitive state types.
- `UiRoot.update(deltaTime)` advances deterministic animation and state before
  layout/rendering.
- UI scale is logical. Optional automatic scale multiplies it by display content
  scale for DPI/device-pixel-ratio handling.
- Normal rendering uses display/input plus g2d; low-level nodes remain an
  advanced customization/debugging path.
- Fonts are bitmap-backed at render time. FreeType sources are rasterized and
  cached during setup; unavailable system-family rasterization fails clearly.
- Validation IDs are stable developer identifiers and do not affect layout,
  drawing, focus, input, or accessibility behavior.

## 13. ECS

The ECS extension is pure Java and user-created. A `World` owns its entities,
components, mappers, queries, lists, events, managers, systems, commands, and
reusable storage.

- Entity handles are opaque integers belonging to one world; `0` is no entity.
- Components are non-null objects that implement
  `io.github.libfdx.ecs.component.Component` and are keyed by an explicit
  `Class<T>`, where `T extends Component`.
- `GameComponent` and `UiComponent` are concrete marker components. Attaching
  one lets the user choose whether an entity belongs to game or UI routing.
- Game and UI matchers select `GameComponent.class` and `UiComponent.class`
  respectively, optionally combined with the functional components a system
  consumes.
- Names and labels are presentation data, never game/UI routing data.
- There is no reflection, annotation scanning, automatic field mapping, or
  automatic serialization.
- Structural mutations are deferred through world commands and applied at
  explicit flush/update safe points.
- Missing component lookup returns `null`; `require(...)` fails clearly.
- Cached entity lists update when structural commands are flushed.
- Events are queued and flushed explicitly. Registered listeners run in order;
  one-shot dispatch listeners run afterward and before the processed callback.
- Managers and systems attach/detach through deferred commands.
- Enabled systems update in registration order.
- A world update flushes pending commands/events, updates systems, then flushes
  work recorded by those systems.
- Clear detaches systems before managers and keeps reusable storage available.
- Hot code caches mappers, matchers, lists, listeners, managers, systems, and
  callbacks, and mutates existing components instead of replacing them each
  frame.

ECS does not depend on `Fdx`, rendering, providers, backends, launchers, or
editors.

## 14. Scenario Validator

The scenario validator is an optional public runtime-validation engine. Its
complete contract is [SCENARIO_VALIDATOR.md](SCENARIO_VALIDATOR.md).

- Core validation is runtime- and UI-neutral.
- UI Kit support is a separate adapter depending on core plus UI Kit.
- Normal runtime and UI rendering never depend on validation modules.
- Scenarios are ordered actions, waits, assertions, captures, probes, and
  narrowly scoped custom callbacks.
- Waits advance through frames/validation time and always have a timeout; they
  never sleep or busy-loop on the render thread.
- Explicit wait timeouts override the configuration default.
- Provider/engine threads do not directly drive scenario callbacks.
- Behavior mode runs selected behavior without enforcing baselines. Visual mode
  runs baseline-bearing scenarios and enforces captures. Mixed mode runs all
  selected scenarios and enforces declared baselines.
- Capture policy controls automatic, failure, disabled, or explicitly listed
  captures. A required visual baseline without a produced capture is a failure.
- A successful capture task alone is not proof of visual correctness; expected
  output must be rendered, inspected, or compared under the active validation
  plan.
- Reports identify the scenario and failing operation, expected/actual values,
  elapsed wait, useful recent events, capture/baseline paths, and platform/API
  matrix status.
- Matrix cells use `PASS`, `BLOCKED`, or `NOT_RUN`; every non-pass has a concrete
  reason.

## 15. Current Scope Boundaries

These absences are intentional current facts, not placeholder APIs:

- no common audio module or runtime accessor;
- no generic runtime service locator;
- no backend-owned asset manager, UI root, ECS world, scene, or batch;
- no public generic graphics-capabilities object;
- no public common surface/surface-texture type;
- no direct GLSL, SPIR-V, MSL, or HLSL authoring contract;
- no public multi-render-target list in the current render-pass contract;
- no cancellation contract for `FdxFuture`;
- no reflection-based JSON, UI, ECS, or asset mapping.

Proposed APIs and future provider features belong in tracked issues until
source, tests, and ownership are defined. Do not describe planned names as if
they already exist.

## 16. Contract Change Checklist

Before changing a public interface, classify it as a backend-owned `Fdx`
system, provider-backed API, disposable resource, provider SPI/setup API,
launcher infrastructure, listener/callback, or descriptor/value type. Then:

1. confirm the exact declarations and call shapes in source;
2. define ownership, lifetime, nullability, thread/frame boundary, and failure
   behavior;
3. preserve provider-neutral dependency direction;
4. update tests and the smallest runnable example;
5. update this semantic contract if behavior changed;
6. update [ARCHITECTURE.md](ARCHITECTURE.md) if ownership, modules, packages,
   artifacts, or dependencies changed;
7. update the relevant domain/workflow guide without copying the declarations.
