# libFDX Shaders

WGSL is the portable shader-language boundary in libFDX. Applications may
author WGSL directly or build a typed shader graph. A graph is compiled to
canonical WGSL plus a complete reflected interface before it reaches a
graphics provider.

```text
handwritten WGSL ---------\
                           -> canonical WGSL + reflected interface
typed shader graph -------/      -> common shader artifact
                                      -> selected provider target
```

Generated languages are provider artifacts, not additional user-maintained
source files. Compiler-library and platform SDK types do not leak through the
common graphics API.

Direct3D 12 requires Windows x64 and a hardware adapter supporting Shader Model
6.0. Its provider artifact bundles Microsoft DXC and the matching DXIL validator;
it needs neither an installed compiler SDK nor a compiler on PATH. The packaged
libraries are verified and extracted beneath `java.io.tmpdir`, which must be
writable, and remain loaded for the process lifetime. Application startup does
not download compiler binaries.

WGSL is translated by Tint's DXC HLSL 2018 writer and compiled in process through
`IDxcCompiler3`, targeting `vs_6_0`, `ps_6_0` or `cs_6_0`, with column-major matrix
packing. Normal compilation uses O3. `D3D12Configuration.optimizeShaders(false)`
uses Od; validation uses Od with embedded debug information. DXIL validation
stays enabled. D3D12 accepts `d3d12-dxc-sm-6.0` artifacts; regenerate older HLSL
artifacts from WGSL for this environment. There is no legacy compiler fallback.

Explicit synchronous single-pipeline creation reuses one compiler on the owning
thread and a bounded context bytecode cache. Async preparation instead uses
persistent bounded workers, one DXC session per worker, and shared retained stage
results keyed by exact source, entry point and stage profile. Compiler options
remain fixed for that queue. These in-memory caches do not persist bytecode across
application launches.

When preparing several render pipelines during loading, submit their descriptors
to `GraphicsDevice.createRenderPipelines(...)`. It returns owned pipelines in
input order and disposes partial results if creation fails. Keep descriptors and
borrowed modules unchanged and alive until the call returns. The default path is
sequential; D3D12 compiles independent HLSL stages concurrently with up to eight
workers, preserving the configured optimization level and runtime compilation.
The call waits for all compiler work and pipeline creation, so its completion is
an explicit preparation boundary. Provider work normally deferred until first use
can still occur there. Driver pipeline creation and fresh driver cache misses can
remain significant; batching does not guarantee a fixed startup duration.

## Preparation service contracts

For native `wgpu-native`, synchronous shader-module and render-pipeline creation
report validation/allocation errors from the creation call and release partial
resources. A rejected shader or pipeline does not prevent later valid creation
or context event processing. Errors outside those calls retain the context error
path. This call-local handling does not enable concurrent descriptor use or async
WGPU preparation; browser WebGPU and Dawn require their own completion handling.

`ShaderPreparation` is an application-owned queue for one graphics resource domain.
`ShaderPreparationScope.include(provider, request)` collects and retains requirements;
`seal()` fixes its total, and `prepareAsync(scope)` returns an `FdxFuture` report after
all members settle. A failed member does not abandon other members. The report counts
ready, failed, unsupported, and cancelled members separately.

Runtime `request(provider, request)` shares entries with matching preload requirements.
Cache the returned `PreparedShaderPass` when content or configuration changes and use
its borrowed `readyPass()` during drawing. A null result means the pass is unavailable;
inspect `state()` and `failure()` to distinguish preparation from an error. Keep scopes
or retained handles alive to pin prepared resources. Dispose each owned handle once it
is no longer needed; handles borrowed from a scope belong to that scope.

Call `update()` on the constructing application thread before opening render passes.
It submits and publishes bounded work without waiting, and dispatches completion
listeners there, including listeners registered after future completion. Explicit
`updateLoading()` additionally permits loading-only providers to run owner-thread
compilation, which can block. `hasPendingWork()` becoming false does not imply that
every requirement succeeded. Failures remain attached to their entries; `retry(handle)`
explicitly creates a new attempt while old handles retain their outcome.

Providers participate through `preparationDevice()` and `beginPreparation(request)`.
Device capabilities describe the actual CPU/native execution and cache support;
their default is unavailable. The service never calls synchronous `resolve()` as a
fallback. An adapter must retain immutable inputs and native device access until its
operation actually finishes and transfer owned resources at publication. Capabilities
alone do not move native creation off the application thread.

Direct D3D12, desktop JVM Vulkan and Android Vulkan preparation use bounded workers for source
translation and native pipeline creation. Device ownership lasts until outstanding
jobs and unpublished results are released. GL and Android GLES use source workers
and owner-context program completion polling when the parallel-compilation extension
is available. GL submissions queue shader compilation and linking before checking
program completion; status and binding queries follow completion. `runtimeNonblocking`
allows this runtime strategy, but does not bound native call duration. Driver initialization
and even link-status queries after completion can stall the owner thread. Preload expected
programs before latency-sensitive gameplay; completion polling alone cannot guarantee smooth
frames for a cold runtime miss.

Desktop C Vulkan uses explicit loading preparation. TeaVM C does not provide
parallel Java execution, and its current Tint/Vulkan bridge is synchronous.
Capabilities report CPU/native `OWNER_THREAD`, zero workers and
`runtimeNonblocking=false`. Source generation, translation and pipeline creation
run only when `updateLoading()` advances a request; an individual compiler call
can block the loading frame. Ordinary `update()` does not compile. Gameplay can
reuse preloaded pipelines, while new runtime requirements report unsupported.
Context shutdown cancels queued work and releases unpublished pipelines before
destroying the native device. This path does not advertise persistent caches.

Desktop WGPU with the JNI or FFM binding and wgpu-native loader uses bounded workers for
source generation, WGSL validation/reflection, native modules and render pipelines.
Android WGPU JNI supports the same execution path with the wgpu-native loader and
explicit `WGPUConfiguration.backend(WGPUBackend.VULKAN)` selection. Android's default
is at most two workers, limited by available processors; desktop's default is at most
eight. `WGPUConfiguration.preparationWorkerLimit(...)` overrides the platform default.
Read the device's preparation capabilities for the effective limit. Each job owns
its descriptors and vectors, retains the device until native work and unpublished
results drain, and publishes through the application's preparation update. Context
shutdown cancels pending work without joining workers. Native rejection fails only
that request. An optional `WGPUConfiguration.shaderCache(...)` persists validated
WGSL/reflection; cache waits release compiler workers. Native pipeline persistence
is unavailable in the current bindings. Android WGPU with `DEFAULT` selection leaves preparation unavailable:
automatic selection does not establish the backend's threading contract.
Desktop Dawn through JNI/FFM and Android Dawn JNI with explicit Vulkan selection
use the same source workers, then native
`createRenderPipelineAsync`; its native capability is `NATIVE_ASYNC`. Java's worker
limit controls source/module/descriptor work, not Dawn's internal compiler pool.
Native Dawn devices explicitly request `ImplicitDeviceSynchronization` before workers
or event polling start. Dawn requires this feature for concurrent device/queue calls;
an adapter without it is rejected during setup. Command encoders remain owned by the
rendering thread. The current binding's custom-feature enum carries Dawn's published
extension value; no native memory layout or binding patch is used.
Callbacks are drained independently of frame event polling, including after context
shutdown. Cancellation retains inputs and the device until completion, discards late
results and never joins native compilation. Synchronous module errors fail their job;
async pipeline failures arrive through their request callback. Android must package
the Dawn native library matching its loader configuration. Always inspect device
capabilities. Confirmed native device-loss cleanup failures are tracked in
[Unresolved issues](UNRESOLVED_ISSUES.md); successful preparation does not establish
safe cleanup after loss.

Browser WebGPU uses `createRenderPipelineAsync` for GPU pipeline completion on
both TeaVM JavaScript and Wasm GC application targets. Java
source generation, WGSL validation/reflection, shader modules and descriptor setup
run only during `updateLoading()`. Capabilities report CPU `OWNER_THREAD`, native
`NATIVE_ASYNC`, zero Java workers and `runtimeNonblocking=false`. A loading update
can therefore block while its CPU work runs; GPU async completion does not make
arbitrary Java generators transferable to a Web Worker. Ordinary `update()` only
publishes finished work. Gameplay reuses preloaded pipelines; new requirements
remain unsupported until explicitly preloaded.

Each browser request owns its descriptors and retains the native device through
callback completion. Cancellation and context shutdown discard late results without
waiting for the GPU compiler. Native callback objects are retired after their native
invocation returns. Runtime capture can record a loading-only miss and export its
registered recipe; import that recipe into a loading scope before its next use.
An optional `WGPUConfiguration.shaderCache(...)` persists validated WGSL/reflection.
Storage callbacks queue subsequent compilation and native submission for explicit
loading updates; ordinary polling does not run those continuations. Native shader
and pipeline caching remains browser-managed.

Android WGPU JNI with explicit `WGPUBackend.OPENGL_ES` uses the same bounded CPU
workers for source generation and WGSL validation/reflection, then hands native
shader/pipeline creation to the application thread during `updateLoading()`.
Its capabilities report CPU `WORKERS`, native `OWNER_THREAD` and
`runtimeNonblocking=false`: wgpu-native's GLES pipeline creation and rendering
share a context lock, so native creation may block a loading frame. Ordinary
`update()` never starts that stage, even after its CPU work finishes. Preloaded
pipelines remain usable during gameplay; new runtime requirements are reported
as unsupported and must be prepared during explicit loading. Startup fallback
to GLES selects these capabilities for the replacement graphics session.

Pausing Android rendering leaves completed preparation results waiting for an
application update. Surface destruction disposes the Android graphics session and
cancels unpublished work; worker-held device access survives until pending jobs drain.
The surface's native-window wrapper is released with the surface. When the Android
backend recreates the session, rebuild its preparation scopes and graphics resources
from the new `create(Fdx)` callback rather than publishing results from the old session.

WebGL uses the same program polling during explicit loading, but its current source
compiler runs on the browser thread. Its capabilities therefore report owner-thread
CPU work and `runtimeNonblocking=false`. Without completion polling, GL adapters
still use available source workers, but advance blocking native compilation only
during `updateLoading()`. If source work finishes after loading updates stop, that
native stage stays pending until loading updates resume. Regular updates can publish
finished results without starting further blocking work. Runtime
misses on loading-only adapters remain unsupported; preload their required variants.

`disposeAsync()` starts cancellation. Continue `update()` until its future completes
before destroying the device; cancellation cannot terminate an active native compiler
call. Unused late results are released. A provider revision invalidates unfinished work;
already ready retained revisions remain available for an explicitly compatible hot
reload transition. A changed device resource-domain token cancels the whole service.
Provider resource disposal must still respect recorded and submitted GPU use.

Desktop D3D12 detects native device removal at preparation polling, submission,
publication and frame boundaries. Detection invalidates its domain once, cancels
queued and running preparation, and releases ready or unpublished results. Native
calls already executing retain their inputs until they return; teardown does not
join compiler workers or wait for fences on a removed device. After loss, create a
new graphics session, preparation service and resources before rendering again.

Direct desktop and Android Vulkan retain the first `VK_ERROR_DEVICE_LOST` reported by a
checked native operation, including worker pipeline creation and pipeline-cache
operations. Desktop loss invalidates all contexts sharing that device. Android
retains the signal in its native context and propagates `GraphicsContextLostException`
through JNI; preparation and frame boundaries observe that state without querying
the driver. Loss cancels preparation queues. `ShaderPreparation.update()` observes the changed domain and
retires ready results; old results cannot be published or used for drawing. New
graphics operations fail with `GraphicsContextLostException`. Already executing
workers retain native inputs until they return; teardown skips lost-device fence
waits and releases objects before destroying the device. Ordinary pipeline or
cache errors do not invalidate the device. This detects reported loss, not a
guaranteed proactive reset notification. Create a fresh graphics session,
preparation service and resources to resume rendering. Android does not attempt
swapchain recovery or recreate a submission fence after terminal device loss.

WGPU registers a device-loss callback through jWebGPU before requesting the device.
The callback retains the first reason/message and immediately closes the shared
resource domain. The application thread cancels preparation across its shared
contexts; `ShaderPreparation.update()` retires ready results after the domain changes.
Frame boundaries and new graphics operations report `GraphicsContextLostException`,
with the native loss report as its cause. Late results cannot be published, and
native work retains its inputs until it returns. Device release waits for both the
last context and the last preparation job. Browser callback storage survives device
destruction until the loss notification returns to the event loop. Rebuild the
graphics session, preparation service and resources to resume rendering.

This requires matching jWebGPU Java and native artifacts exposing
`WGPUDeviceDescriptor.setDeviceLostCallback`. Dawn and browser WebGPU deliver loss on
device destruction. Native wgpu 29 delivers the callback inline when an operation
reports a recognized device-loss error; command-encoder creation verifies this route
through both desktop JNI and FFM. It can notify repeatedly and ignores callback mode.
Destruction plus event polling alone does not notify, and some wrapped loss errors
are misclassified as ordinary validation errors. These upstream limits prevent a
guarantee that every loss reaches libFDX. Ordinary validation errors do not substitute
for loss notification. Do not submit work on an explicitly destroyed native device
to probe notification: wgpu-native can abort the process. Actual driver-loss and
Android runtime loss coverage remain separate from the desktop reported-loss checks.

GL preparation checks context loss at polling, loading, publication and cancellation
boundaries on adapters that expose loss detection. Loss closes preparation throughout
the resource share group. WebGL also retains the browser's loss event, so restoring
the canvas before the next poll cannot revive old shader results. Lost native names
are abandoned without deletion; rebuild the graphics session, preparation service
and resources after restoration. Desktop GL requests reset notifications through
GLFW and queries core/KHR/ARB robustness on the owning context. Android GLES
requests notifications through EGL 1.5 or EXT robustness and queries core/KHR/EXT
reset status through the bundled libFDX JNI runtime. Both retain the first reset;
Android also treats `EGL_CONTEXT_LOST` during binding or presentation as terminal.
Other EGL errors remain ordinary surface errors. These queries do not consume
ordinary GL errors. Drivers without the required query or reset-notification
support cannot provide this detection guarantee. Loss invalidates the old resource
domain; automatic graphics-session recreation is not provided.

## Persistent preparation artifacts

`ShaderArtifactCache` borrows an application-owned `ShaderCacheStore`. Its independent
layers distinguish source, translation, DXIL, SPIR-V and driver data. Providers only
advertise layers they actually use. Desktop D3D12 asynchronous preparation persists
validated WGSL/reflection, HLSL/interface data, validated DXIL and cached PSOs. Direct
desktop Vulkan persists validated WGSL/reflection, SPIR-V/interface data and a shared
pipeline cache. Matching translation/bytecode hits bypass the corresponding Tint/DXC
calls. Native pipeline creation still runs with compatible cached driver data. Source
generation still runs to establish exact content identity.

On desktop, inject the cache before starting the graphics provider:

```java
DesktopShaderCacheStore store = new DesktopShaderCacheStore(appCacheDirectory, 128L * 1024 * 1024);
ShaderArtifactCache artifacts = new ShaderArtifactCache(store);
D3D12Provider provider = new D3D12Provider();
provider.configuration().shaderCache(artifacts);
```

`WGPUProvider.configuration().shaderCache(artifacts)` uses the same borrowed store
on desktop JNI/FFM and Android's audited WGPU Vulkan/GLES preparation paths. Matching
`SOURCE` hits bypass libFDX's Tint WGSL validation/reflection calls. WGSL generation
still establishes content identity, and native shader modules and pipelines are
created on every launch. This does not persist Naga, DXIL or driver pipeline data;
WGPU's `pipelineCache` capability remains false. A native rejection permits one
rebuild that bypasses restored artifacts before failing that request.

For browser WebGPU, configure application-owned IndexedDB storage in the launcher:

```java
WebShaderCacheStore store = new WebShaderCacheStore("my-game-shaders", 64 * 1024 * 1024);
ShaderArtifactCache artifacts = new ShaderArtifactCache(store);
WebWGPUProvider provider = new WebWGPUProvider().configuration(
        new WGPUConfiguration().backend(WGPUBackend.WEBGPU).shaderCache(artifacts));
```

Browser WebGL accepts the same application-owned cache:

```java
WebGLProvider provider = new WebGLProvider().shaderCache(artifacts);
```

On both JS and Wasm, WebGL persists validated WGSL/reflection in `SOURCE` and
translated GLSL/interface artifacts in `TRANSLATION`. Matching hits bypass the
corresponding libFDX Tint calls. Source generation still runs to establish content
identity, and the browser still compiles/links GLSL. Native program binaries are
not persisted, so `pipelineCache` remains false.

WebGL cache callbacks enqueue shader continuations. Source generation, translation
and new native shader submissions run during explicit `updateLoading()` advances; ordinary updates can
poll a previously submitted program but cannot start a new compilation. A rejected
cached translation permits one loading-only rebuild. Cancelling a pending storage
read discards its continuation without using a closed or lost graphics context.

The store works on TeaVM JS and Wasm GC. It uses asynchronous transactions, atomically
replaces entries and evicts oldest writes within the configured byte budget and
4096-entry limit. Separate connections share that transaction boundary. Storage
denial, quota failures and corrupt records fall back to normal loading preparation.
`WebAppWriter` fingerprints the packaged `fdx.js` and `fdx.wasm` compiler at build
time; a changed compiler invalidates matching artifacts. Unknown compiler identity
disables persistent reuse. Close the store after preparation drains; `flushAsync()`
can observe accepted I/O completion, and `dispose()` never waits. Browser eviction
may remove cached data, so loading must also work with an empty cache.

The cache directory belongs to the application. The store bounds record size and total
disk usage, evicts older stored entries and uses atomic replacements coordinated by a
cross-process write lock. Busy, unwritable or unavailable storage falls back to normal
preparation. It never accesses disk on the submitting thread. D3D12 reads asynchronously
before scheduling native work, so waiting for storage does not occupy compiler workers.
`DesktopVulkanProvider.configuration().shaderCache(artifacts)` uses the same store contract.
`DesktopOpenGLProvider.configuration().shaderCache(artifacts)` and
`AndroidGlesProvider.shaderCache(artifacts)` persist WGSL/reflection and GLSL/interface
artifacts through their platform worker executors. Reads release compiler workers;
completion never invokes native GL on a storage thread. Rejected translated data gets
one asynchronous rebuild before failure becomes terminal. On desktop GL and Android
GLES contexts exposing binary formats, `pipelineCache` also reports program-binary
persistence during explicit `updateLoading()` calls. Keys include exact linked GLSL,
entry points, adapter schema, GPU/driver/context identity and supported formats. Native
restore/export and record encoding can block a loading update; disk I/O remains
asynchronous. Once an operation starts native binary work in loading, keep calling
`updateLoading()` until it finishes. Ordinary runtime updates never import/export
binaries and keep the existing source-compilation/completion-polling path. Missing,
corrupt or rejected binaries fall back once to the already translated GLSL and replace
that record after successful linking. A binary restore still needs link validation and
may leave driver work for first use; GL supplies no pipeline-cache-hit feedback.
Contexts without binary formats, WebGL and the deferred C adapter expose no program
cache. Driver completion polling does not guarantee constant-time GL submission or
first use; measure these costs on the target driver.

Android direct Vulkan also accepts `VulkanConfiguration.shaderCache(artifacts)`.
Use `AndroidShaderCacheStore` with a dedicated path under the application's private
cache directory. It has the same bounded, asynchronous, atomic storage and nonjoining
shutdown contract. Android preparation persists validated WGSL/reflection and
SPIR-V/interface data; cache reads release compiler workers until their continuations
are ready. Its native Vulkan cache is shared by preparation workers, validates the
GPU/driver/API/UUID/native ABI identity, and lives until the final retained device
reference is released. Native snapshots are bounded to 16 MiB and exported on workers.
Concurrent independent contexts/processes atomically replace snapshots; they do not
merge newly discovered entries from each other. Android reports native pipeline-call
counts and enables pipeline creation feedback when available. Only valid feedback
counts as a driver hit or miss; confirmed hits avoid redundant snapshot copies.

The Android test launcher accepts `libfdx.test.shaderCacheDirectory` as a simple
directory name under its private cache directory and `libfdx.test.shaderWorkers` as
the direct Vulkan or GLES worker limit. Reusing that directory in a later process exercises
persistence. Cache metrics are logged after the test's submitted storage work drains.

Translation keys contain exact WGSL, entry point, stage, target and profile options, plus
the actual runtime compiler binary/bridge identity. Desktop hashes the library it loaded
on a preparation worker. Android fingerprints the library identified by its native
compiler function, reading either the extracted library or its exact APK entry on a
worker. An unidentified compiler (including an unresolved library-path
load) compiles normally without translation persistence. Restored results retain full
reflection and target binding/entry-point remaps and pass normal artifact/interface checks.
Malformed records compile again; downstream native rejection permits one rebuild that
bypasses translation reads and replaces successful artifacts. Android and browser
translation persistence requires an adapter with a verified compiler identity and store.

DXIL keys include exact HLSL, entry/profile, all compiler arguments, schema and packaged
compiler/validator identities. Each record binds its key, version, length and checksum.
Bad records become misses; driver-rejected cached stages are retired and recompiled
once. Compiler options and validation remain identical on cache misses. A driver change
does not itself discard compatible DXIL. A null configured cache disables persistence.

D3D12 PSO keys include the complete supported pipeline state, vertex/resource layouts,
stage identities, adapter IDs/LUID, driver version and hashes of the loaded D3D12/DXGI
runtime binaries. An unidentified adapter or runtime disables PSO persistence. The cache
directory must remain machine-local. Rejected PSOs retry once without cached PSO bytes,
preserving valid DXIL; successful replacements are exported on the preparation worker.

Direct desktop and Android Vulkan use an internally synchronized `VkPipelineCache` across a queue's workers.
Its key includes vendor/device/driver/API identity and the pipeline-cache UUID; restored
headers must match the device. Workers serialize bounded snapshots and skip incomplete
queries; the last finishing job retries the snapshot. Driver data is capped at 16 MiB per
stored record. Unavailable cache creation/storage permits ordinary worker preparation.
Caches remain owned by the resource domain until its contexts, preparation jobs and accepted
cache saves drain, and are destroyed before the native device. Saving uses the store's optional
`supportsAtomicUpdate()` / `updateAsync()` contract. Desktop and Android stores hold a
cross-process lock across reading the latest record, merging and atomic replacement. Lock
acquisition waits at most two seconds on a storage worker. Failed updates preserve the previous
record; lock timeout, unavailable storage or an oversized merged record can prevent a new save.

Vulkan merges compatible snapshots using private temporary cache objects on the storage
worker, retaining the device until the save completes. The live compilation cache is never
the merge destination. Queued snapshots from one adapter coalesce before its transaction
starts, and already accepted saves still finish after owner/store disposal. Independent
writers therefore preserve each other's compatible entries on successful updates, subject
to the driver's merge behavior and normal cache eviction. Custom stores without atomic
updates still support source/translation caching; Vulkan reports `pipelineCache=false` and
does not replace aggregate records through separate reads/writes.

When `VK_EXT_pipeline_creation_feedback` is available, desktop Vulkan enables it and
records the driver's valid application-cache-hit feedback. Confirmed hits do not trigger
unchanged cache snapshots. Providers without feedback continue reporting creation calls
without inventing hit/miss results.

`artifacts.metrics(layer)` reports record hits/misses, completed writes, invalid entries,
storage failures, disabled reads and explicit compiler invocations for identified artifacts.
`pipelineCreations()` counts native pipeline calls separately, and
`pipelineCreationsWithoutCache()` counts calls without application cache input. A Vulkan
cache object may start empty. These counters do not establish internal driver cache hits
or guarantee that cached creation has no remaining driver work.
`pipelineFeedbacks()` counts valid native feedback records; `pipelineCacheHits()` counts
their explicit application-cache-hit flags. Zero feedback means unavailable feedback.
Hits count checked record reads, including repeated
requests for a shared stage; native rejection may subsequently increase invalid entries.
Record counts are not shader totals; use `compilerInvocations()` to distinguish actual
compiler calls from cache reads. Large artifact hashing
and encoding belong to preparation/storage workers, not drawing.

The application owns cache lifetime. Stop and drain preparation producers before calling
`store.flushAsync()` to observe completion of submitted I/O, then dispose the store.
Poll or handle completion through the application's event loop; rendering must never wait
for a flush. Closing the store rejects new operations and lets accepted operations finish
without joining its worker. Native resources and ready passes remain owned by preparation.

For desktop measurements, `libfdx.test.shaderWorkers` selects the provider worker limit.
`MultipleShadersTest` reports separate callback-body, entry-to-entry frame interval and
preparation-update percentiles. Body samples exclude optional pixel readback/file diagnostics;
intervals also include backend submission, presentation, frame pacing and those diagnostics.
Disable captures and `libfdx.test.shaderVerifyPixels` for timing comparisons, and run pixel
validation separately. The default 128-shader serial mode and compiler options are unchanged.

`PreparedShaderPass.timings()` allocates an immutable diagnostic snapshot. It separates
service queue time from preparation and directly recorded provider phases, including the
wait for publication versus publication work. Uninstrumented custom providers return
unavailable phase/cache measurements. Cache read/write durations and counters belong to
the operation that initiated the work, even when storage completes on another worker.
Shared or coalesced work is counted once; aggregate cache merges include lookup counters
and time the entire transaction as a write. Durations measure wall-clock residence and
may overlap; they are not CPU time, frame stalls or a promise of faster rendering.
Android Vulkan exposes native pipeline counters only in aggregate; these are excluded
from per-entry counters because concurrent jobs cannot be identified from their totals.

`firstDrawNanos()` measures enqueue to the first successfully recorded nonempty draw,
and `readyToFirstDrawNanos()` measures publication to that draw. `firstDrawUpdate()` is
the service update index, not a presentation frame. Missing observations return `-1`.
SpriteBatch and the built-in prepared ModelBatch renderers record this automatically.
Custom renderers call `ResolvedShaderPass.recordDraw()` after a successful nonempty draw
using that pass. Demand, polling, skipped draws and zero-size draws must not call it.
Repeated draw recording allocates nothing and reads no clock after the first observation.

Scope reports freeze timings at completion, commonly before any draw. Retained handles
and later runtime capture snapshots also observe first use and late cache writes. Capture
Markdown includes these timings and cache outcomes; no snapshot owns native resources.
`MultipleShadersTest` emits `SHADER_TIMING` and `SHADER_TIMING_CACHE` per shader at shutdown,
and its `FIRST_DRAW` log waits until at least one shader actually records a draw.

## Batch preloading and runtime demand

Async ModelBatch requires a renderer that consumes the shared preparation plan.
`PreparedShaderProvider3D` declares that contract; a `PbrShaderProvider` configured
with `PbrShaderConfig.shaderPlan(...)` exposes its borrowed plan through it. Material
overrides must use that same plan. Opaque or mismatched renderers produce retained
`UNSUPPORTED` requirements and `REQUIRES_INPUT` capture recipes without invoking
their shader callbacks. Preload collection uses the same check. Changing a material
to an unsupported renderer hides all its required group passes at the next frame
snapshot, including previously ready shadows.

Pass the same application-owned `ShaderPreparation` and `SpriteShaderPlan` or
`ModelShaderPlan` to collection and rendering. The plans retain shader definitions;
preparation entries own native resources. On the explicit preparation path, batch
construction does not compile shaders. Draws request missing configurations once,
skip pending or failed content before upload, and use ready entries on later frames.
`skippedDrawsLastFrame()` counts logical sprites or model renderables per submitted
pass, with separate pending/failure/unsupported/cancelled counts. It resets at the
next preparation update boundary, so several begin/end pairs in one frame aggregate.

For example, after loading a model's assets, collect its surface requirements at a
frame boundary before opening render passes:

```java
ShaderPreparation shaders = new ShaderPreparation(graphics.device(), ShaderPreparationOptions.DEFAULT);
ModelShaderPlan modelPlan = new ModelShaderPlan(graphics);
ModelBatch models = new ModelBatch(graphics,
        new ModelBatchConfig().preparation(shaders).shaderPlan(modelPlan));
ShaderPreparationScope level = shaders.createScope("level");
modelPlan.include(level, modelInstance, ShaderPassId.FORWARD,
        modelPlan.surfaceTarget(graphics.currentFrame()));
shaders.prepareAsync(level.seal()).onSuccess(report -> {
    // Activate required content only when report.allReady(); otherwise show report.items().
});
```

Continue `shaders.update()` at the start of each rendering frame while loading and
drawing. Keep the level scope alive while its content is active. Sprite collection
uses `SpriteShaderPlan.include(scope, targetLayout)` and an explicit
`SpriteBatchConfig.preparation(shaders).shaderPlan(spritePlan)`. Preload a small
loading UI scope before scheduling larger content. Scope completion reports shader
readiness; asset readiness remains the application's responsibility.

[ShaderPreloadingTest](../tests/core/src/main/java/io/github/libfdx/tests/graphics/ShaderPreloadingTest.java)
is an executable startup/streaming example. It prepares its SpriteBatch HUD first,
preloads the initial ModelBatch world, keeps both drawing during a streamed preload,
then records a deliberately unplanned material. The desktop launcher accepts
`libfdx.test.shaderCaptureDir` to export its JSON/Markdown guidance and
`libfdx.test.shaderManifest` to import it before gameplay. Replay checks that the same
path has no runtime misses or skipped models. It requires a provider advertising
nonblocking runtime preparation.

Use the actual pass layout for preload requests. A ModelBatch surface pass enables
depth, so its layout differs from the frame's color-only layout; `surfaceTarget`
derives that metadata without opening a pass. Register logical target roles on a
plan before capturing external render passes or offscreen targets. Resize dimensions
alone do not change pipeline identity, but attachment formats or sample counts do.

For required shadow/depth/forward dependencies, declare stable renderables and their
pass/target pairs in a `ModelShaderGroup`, pass that group to participating model
batches, and call `group.beginFrame()` after preparation update and before any of
those passes. A missing required pass defers that renderable across the group.
Continue clearing/rebuilding shadow targets normally. The group freezes readiness
for a frame and does not construct or schedule a render graph.

Use `group.usePlan(ShaderPassId.SHADOW, shadow.shaderPlan())` when forward and shadow
rendering use different shader definitions. Each participating batch must use the plan
declared for its pass. `DirectionalShadowMap3D` and `CascadedShadowMap3D` have constructors
that borrow preparation and an optional group, contribute packed-depth preload recipes,
and expose `preparationTarget()` for collection. Cascades share their shadow plan and
prepared pipelines. Async shadow maps clear/rebuild their targets rather than reusing
previous cascade output while readiness may change. A custom `PreparedShaderProvider3D`
can supply its borrowed plan and consume `RenderContext3D.preparedShaderPass()`; its
shader selection and drawing methods must not compile. Opaque providers remain unsupported
on an async batch.

`ShadowShaderPreparationTest` preloads a scene and its HUD, then introduces a material
requiring new forward and shadow passes. Ready objects and shadows continue rendering
while the new caster is omitted consistently. Set `libfdx.test.shaderCascades=3` on desktop
to exercise multiple cascades with the same readiness group.

Definition changes increment the provider revision. A batch may keep its previous
ready pipeline only when `canRenderPreparedRevision(request, previous)` confirms
compatibility with current inputs and bindings. Unknown compatibility skips draws
until a replacement succeeds. One incompatible required pass invalidates the old
group together. Uniform/texture/sampler value revisions remain separate from shader
compilation revisions.

## Capturing preload requirements

`shaders.captureRuntime(segment)` observes actual draw demand, including demands
already satisfied by a ready entry. Preload declarations or readiness polling alone
do not count as runtime use. `onDiscovery` delivers one initial callback per unique
requirement through update; an explicit snapshot supplies later outcomes and counts.
Capture limits bound retained observations; dropped demand and discarded preload
history remain visible in exported artifacts and import diagnostics.

A discovery is keyed by provider instance, revision and structural request. Explicit
retry or eviction/recreation updates the same discovery without another notification
or capture slot. Draw counts accumulate; the initial demand frame/readiness/preload
flags stay fixed. Outcome, cause and timing describe the latest observed attempt,
even if an older retained consumer continues drawing or reporting a failure.

Several models or materials can share one prepared pipeline. Each discovery retains
their supplied content/material labels and distinct recipe target roles, while draw
and skip totals remain aggregated for that requirement. Captures retain at most 16
origins per requirement and 65,536 origins overall. Exceeding either bound makes the
capture incomplete; exports and imports report the dropped demands. Repeated draws
with already observed labels do not allocate new origin objects.

`capture.exportAsync(destination)` snapshots observations and passes them to an
injected asynchronous exporter. `DesktopShaderPreloadDestination` writes
`shader-preload.json` and `shader-preload.md` on a bounded desktop worker. Encoding
and storage belong to that adapter. Capture disposal stops observations without
cancelling preparation or pinning native pipelines. Export completion listeners,
including late registrations, are delivered through preparation update.

`AndroidShaderPreloadDestination` performs the same JSON/Markdown export in an
app-private directory on a bounded worker. The Android launcher accepts
`libfdx.test.shaderCaptureDir` and `libfdx.test.shaderManifest` for `ShaderPreloadingTest`,
resolving these paths under its private files directory. Manifest loading is explicit
test startup work; recipe import itself does not read files or load assets.

Load the manifest as application content and import it into a scope with
`scope.include(manifest, resolver)`. Built-in sprite/model plans can resolve their
own recipes against current logical target roles. Custom procedural shaders need
a stable factory and immutable inputs; unresolved inputs remain `REQUIRES_INPUT`
entries. Review both import diagnostics and the eventual preparation report.
Merging manifests preserves segment membership, conditions and incomplete-capture
diagnostics; `manifest.select(...)` limits preloading to the intended segments.

The report distinguishes missing preload declarations, declarations submitted too
late, lost residency, configuration changes and failures. Starting earlier or
retaining a scope can be the appropriate fix instead of adding duplicate recipes.
Preparation duration is background latency, not a measurement of render-thread
stalls. Replay the captured path in a fresh process and check for remaining misses
and skipped draws. A manifest identifies what to prepare and is separate from a
compiled-code cache; one captured path does not establish coverage of all game
content or graphics providers.

## API Packages

Shader APIs are grouped by responsibility instead of sharing the
`io.github.libfdx.graphics` root:

| Package | Responsibility |
| --- | --- |
| `io.github.libfdx.graphics.shader` | Languages, profiles, modules, bundles, stages, and source validation. |
| `io.github.libfdx.graphics.shader.reflection` | Reflected entry points, resources, parameters, value types, and ABI layouts. |
| `io.github.libfdx.graphics.shader.target` | Target identities, artifacts, translation, verification, remaps, registries, and cache keys. |
| `io.github.libfdx.graphics.shader.runtime` | Provider requests, resolved passes, parameter blocks, and bound resource values. |
| `io.github.libfdx.graphics.shadergraph.model` | Semantic graph data, builders, codecs, types, ports, edges, and editor-neutral metadata. |
| `io.github.libfdx.graphics.shadergraph.node` | Node definitions, properties, and registries. |
| `io.github.libfdx.graphics.shadergraph.compiler` | Graph/program/technique compilers, diagnostics, source maps, and compiled results. |
| `io.github.libfdx.graphics.shadergraph.document` | The single versioned `.fdxgraph` document and deterministic codec. |
| `io.github.libfdx.graphics.shadergraph.cache` | Optional embedded compiled-cache keys, artifacts, and interface summaries. |
| `io.github.libfdx.graphics.shadergraph.ir` | Typed intermediate representation. |
| `io.github.libfdx.graphics.shadergraph.technique` | Program, technique, pass, pipeline-state, and variant declarations/codecs. |
| `io.github.libfdx.graphics.shadergraph.standard` | Standard graph and technique factories. |

Runtime loading/providers and the optional editor remain in their own
`shadergraph.runtime` and `shadergraph.ui` packages. The graphics root
continues to own general device, resource, pass, and pipeline contracts. There
are no compatibility aliases in the former flat shader packages.

## Shader Graph Boundary

The shader graph is a headless authoring and compilation system. Java code,
single-file `.fdxgraph` assets, and runtime loading do not depend on UI Kit.
The optional UI Kit module edits the same semantic document and stores layout
in its optional in-file `editor` block. Editor data never participates in the
semantic hash or shader behavior.

Graph scope is layered:

| Scope | Responsibility |
| --- | --- |
| Function/subgraph | Reusable typed computation. |
| Surface | Material outputs such as base color, normal, metallic, roughness, emissive, occlusion, and alpha. |
| Vertex, fragment, or compute | Complete programmable-stage inputs, outputs, resources, and operations. |
| Program | Linked stages, entry points, and their complete interface. |
| Technique | Named passes, fixed pipeline state, static variants, and explicit capability fallbacks. |

A technique is the complete programmable shader definition, but it is not a
whole-renderer or frame graph. Cameras, scenes, visibility, draw ordering,
attachments, pass scheduling, resource transitions, and submission remain
renderer/render-graph responsibilities. A batch asks a `ShaderProvider` for one
named pass at a time.

The generated WGSL string is not sufficient by itself for SpriteBatch or
ModelBatch. The runtime also needs the reflected resource layout, entry points,
pipeline state, vertex ABI, selected variant, default resources, and exact
render-pass compatibility. `ShaderGraphProvider` carries that complete
technique contract and returns a `ResolvedShaderPass`.

## Authoring And Runtime Paths

`ShaderGraphBuilder`, program/technique builders, codecs, and compilers are
usable directly from Java. The normal render flow is:

```text
graph/program/technique
  -> optional one-file compiled-cache selection
  -> semantic compilation and canonical WGSL on a cache miss
  -> optional target compilation and verification
  -> ShaderGraphProvider
  -> ShaderRequest for one pass and variant
  -> resolved native module, resource layout, and bounded cached pipeline
  -> SpriteBatch or ModelBatch draw
```

SpriteBatch and ModelBatch use the same public `ShaderProvider` vocabulary:

```java
var compiled = StandardSpriteTechnique.compile(graphics);
var provider = new ShaderGraphProvider(graphics, compiled);
var sprites = new SpriteBatch(
        graphics,
        new SpriteBatchConfig().shaderProvider(provider));
```

The common interface does not imply that one compiled technique fits every
renderer. Standard PBR and sprite techniques have different passes, vertex
ABIs, resources, and pipeline state, so applications normally create one
`ShaderGraphProvider` instance for each technique while keeping both variables
typed as `ShaderProvider`. See the
[shader graph sample](../samples/graphics/shader-graph/) for code-authored and
serialized graphs used by both batch families.

The configured provider is borrowed: dispose the batch before disposing the
provider, and do not replace or mutate provider state during an active
`begin()`/`end()` scope. A default ModelBatch owns its internally created
standard provider and disposes it with the batch.

The standard PBR implementation is graph-backed. `Material` is a generic
typed-attribute container. `MaterialAttributes` owns shading-model-neutral
surface semantics such as base color, normal and emissive textures, alpha
cutoff, and lighting influence; `PbrAttributes` adds only metallic, roughness,
and occlusion semantics. Materials without an attribute use the shader's
documented default, and other shaders may consume or ignore the same
attributes without requiring a PBR-specific material class.

Every material also has a stable `ShadingModel` ID. The built-in provider
supports `PBR` and `UNLIT` materials in one batch. Unlit output bypasses scene
light and received-shadow evaluation while retaining transforms, depth,
alpha, emissive, and fog behavior; it may still be submitted to a shadow pass
as a caster. A PBR material can use the common `lightingInfluence` attribute to
blend continuously between full-bright base color and fully evaluated
lighting. Custom shading-model IDs require an explicit material shader
provider and fail clearly when sent to the built-in provider.

Shading-model selection remains independent from `RenderPath3D`: forward,
forward-plus, or deferred scheduling decides when and where geometry is drawn,
while PBR, unlit, toon, or custom shading decides how one material produces
surface color. This allows a render path to mix supported shading models
without encoding the render path into material data.

`StandardPbrTechnique` exposes replaceable surface, post-skinning vertex, and
final-lighting graphs. `GraphMaterial` combines ordinary material attributes
with parameters and borrowed resources from that technique's surface schema.
The renderer still owns camera, object, environment, skinning, shadow,
texture-slot, and draw bindings. Its WGSL template is a composition scaffold
and reflected ABI baseline, not a second runtime PBR implementation.

Handwritten WGSL remains first-class. Wrap a complete
`ShaderModuleDescriptor` in `ShaderGraphRenderProgram`, put it in a
`ShaderGraphRenderTechnique`, and resolve it through the same
`ShaderGraphProvider`. Handwritten and graph-generated variants may coexist in
one technique. This is the migration path for existing shaders; there is no
requirement to reconstruct handwritten WGSL as nodes.

`ShaderGraphComputeProvider` is the equivalent runtime owner for compute
programs and techniques. Compute is capability-gated and is rejected before
pipeline creation on profiles such as WebGL2.

Serialized authoring uses one versioned `.fdxgraph` for graph, program,
compute-program, render-technique, and compute-technique semantics. The
required semantic block is sufficient for correctness. An optional `editor`
block restores visual layout, and an optional `compiled` block caches
target/profile-specific results. There are no companion WGSL, reflection,
manifest, source-map, diagnostic, or layout files.

`ShaderGraphRuntimeLoader` parses the document and chooses an exact embedded
entry using semantic/dependency/compiler/library/profile/capability/target/
environment/options/interface/pass/variant identity. A miss compiles the
required semantics in memory. Runtime loading itself never rewrites the asset;
the UI editor's explicit **Save with compiled cache** action may embed the
result back into the same file.

## Source And Profiles

Project shader files conventionally live under:

```text
src/main/fdx-shaders/**/*.wgsl
```

Built-in renderer WGSL stays with its owning renderer so it is available on all
supported build targets. Generated translations belong in build/report output.

A leading comment may declare a portability profile:

```wgsl
// @fdx.profile webgl2
```

| Profile | Intent |
| --- | --- |
| `webgl2` | WebGL2/OpenGL ES 3-class render subset. |
| `webgpu` | Portable WebGPU/wgpu render and compute subset. |
| `native` | Explicit provider-specific opt-in; not portable by default. |

When no comment is present, the configured project default applies. The
WebGL2 profile excludes compute, storage resources, atomics, subgroups, WGSL
extensions, 16-bit floats, and 64-bit integers. The WebGPU profile still
excludes provider-specific extensions and subgroups unless a later
capability-gated profile defines them.

`native` does not make a generated target language part of the public authoring
contract. Providers still receive WGSL through the common descriptor.

## Runtime Translation

The runtime exposes an optional provider-neutral shader compiler capability.
Backends package it only when an active provider needs translation.

- Translation runs during shader-module creation, explicit editor
  recompilation, or validation—never in the render loop.
- Compiler setup and translations may be cached by source, target, stage, entry
  point, profile, options, and compiler version.
- A missing required compiler or unsupported profile/feature fails clearly.
- Failed translation returns diagnostics rather than a partial shader.
- Providers that consume WGSL directly do not require the compiler capability.

The web loader installs the native compiler before invoking the Java application
entry point. WebGL startup renderers therefore use the same runtime translation
path as every other shader; the backend does not embed or source-match a second
set of startup shaders. UI Kit preloading begins afterward and reports
application-asset progress separately from this runtime bootstrap.

Tint is the current implementation backend, but Tint types and generated
languages are not common API. Changing compiler implementation does not change
the authored WGSL contract.

Graph target generation uses the same rule. `ShaderGraphCompiler` always emits
canonical WGSL. Extensible `ShaderTargetCompiler` and `ShaderTargetVerifier`
registries may then produce and verify WGPU/WebGPU WGSL, OpenGL/WebGL
GLSL/GLSL ES, Vulkan SPIR-V, Metal MSL, DirectX HLSL, or a custom target. Every
artifact records its compiler, verifier, options, consumer environment, entry
point/binding remaps, and matching reflected interface. Translation success
alone never counts as target verification.

The optional in-file compiled block retains target artifacts and their
provider-neutral interface summaries. Cache decoding verifies hashes and
rejects invalid entries independently; invalid optional cache data never
replaces or repairs invalid required semantics. Absence of a compatible entry
is a cache miss followed by normal in-memory compilation, not a shader
fallback.

## Provider Responsibilities

Each provider chooses the appropriate compiler target and creates its native
shader module. Built-in 2D/3D renderers supply WGSL only; providers do not
silently select handwritten fallback shaders.

Reflection metadata may describe entry points, stages, bindings, vertex
locations/formats, and translated names. Reflection is created and consumed
during setup. It never runs per draw or allocates per frame.

Unsupported target, profile, feature, or binding combinations fail during
setup rather than changing rendering semantics silently.

GL links the vertex/fragment pair selected by `ShaderModuleDescriptor.entryPoints`.
Its pipelines must select that same pair; changing pipeline entry points alone is
rejected. Create another shader module for another pair. This keeps GL's linked
program behavior explicit while other providers can select entries at pipeline creation.

## Hot Reload

Editor reload follows the same pipeline:

1. Compile after a file change or explicit action, preferably off the render
   thread.
2. Create provider resources on the required graphics thread.
3. Keep the old valid pipeline until replacement succeeds.
4. Surface diagnostics without replacing a working pipeline on failure.
5. Dispose replaced resources only after in-flight use ends.

Allocation for an explicit reload is acceptable; reload work does not become a
steady-state frame-loop path.

Graph and technique values are immutable after construction. Material values
and resource bindings use explicit identity/revision tracking. Whole-technique
replacement validates source definitions and publishes one new provider revision;
it does not compile native modules. Native validation occurs during preparation
or explicit synchronous resolution. Async batch drawing retains a previous revision
only when the provider establishes compatibility; replacement failures remain
diagnostic entries. Perform publication, replacement and disposal on the owning
application thread. Native creation follows the selected provider's execution rules.
Passes borrowed from synchronous resolution must not survive its provider revision
or owning provider; preparation leases have their own explicit retained lifetime.

`ShaderGraphProvider` owns modules and pipelines created by synchronous resolution
and uses bounded caches. Async preparation entries independently own their native
results. The application owns an explicitly constructed provider and must dispose
it after all borrowing batches and queued work are finished.
Graph material instances do not own textures or samplers bound into them.

## Validation

Compiler changes require focused compiler/ABI tests and the affected provider's
shader-creation path. Renderer shader changes require a rendered scenario, not
only successful compilation. Compare affected providers under the same scene,
viewport, assets, input, and frame conditions.

Project configuration is described in the
[Gradle plugin](../libfdx/tools/gradle-plugin/README.md#shaders).
