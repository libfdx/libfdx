# Asset loading

Create a `DefaultAssetManager` on the application thread and register loaders
before requesting assets. The application owns the manager; it borrows its file
system and optional `AssetExecutor`. Public declarations in
[AssetManager](src/main/java/io/github/libfdx/assets/AssetManager.java) and
[AssetLoadContext](src/main/java/io/github/libfdx/assets/AssetLoadContext.java)
define the lifecycle and scheduling contracts.

```java
// Application setup; supported web image work automatically uses a worker.
DefaultAssetManager assets = new DefaultAssetManager(fdx.files());
G2DAssetLoaders.register(assets, fdx.graphics().main());
AssetScope level = assets.createScope();
AssetLease<TextureRegion> logo = level.load(
        AssetDescriptor.of("logo.png", TextureRegion.class));

// Each application frame: at most four queue steps, with time checked between them.
boolean finished = assets.update(4, 1_000_000L);
if (logo.isLoaded()) {
    TextureRegion region = logo.asset(); // Borrowed; use it without disposing it.
}
// finished also includes failures; inspect each handle's future/status.

// Level exit: release this level's assets; other levels remain usable.
level.dispose();

// Application exit also closes every remaining scope and standalone lease.
assets.dispose();
```

On desktop, pass an application-owned
[`DesktopAssetExecutor`](../../../backends/desktop/src/main/java/io/github/libfdx/backend/desktop/DesktopAssetExecutor.java)
to the manager constructor, for example `new DesktopAssetExecutor(2, 16)`.
Android uses the equivalent
[`AndroidAssetExecutor`](../../../backends/android/src/main/java/io/github/libfdx/backend/android/AndroidAssetExecutor.java),
for example `new DefaultAssetManager(fdx.files(), new AndroidAssetExecutor(2, 16))`.
Keep an explicit reference to the executor for shutdown; the manager only borrows it.
It bounds workers and waiting tasks. A full executor defers submission until
another update, allowing ready results to proceed. Dispose the manager first,
then the executor. Executor disposal lets accepted work finish without blocking
the application. It cannot interrupt native file or decoder operations.

`update(maxTasks, maxNanos)` budgets acquisition submissions, result delivery,
dependency readiness, publication, and finalizers together. Either limit set to
zero performs no queued work. A running step is indivisible: one decode, callback,
or GPU upload may exceed the time limit. The manager exposes the most recent
update's task count, elapsed time, and longest step for measurement. The glTF loader
prepares JSON, geometry, images, and mipmaps through the preparation queue, then yields
between texture uploads during finalization. Without workers, managed JSON parsing,
triangle expansion, and mipmap filtering advance in bounded character/triangle/pixel
batches. Raw browser PNG decoding yields between animation frames, including CRC,
inflate, and filtering work, preserving hidden RGB at alpha zero. Other supported
browser image layouts use the browser decoder. Browser pixel transfers use bounded
copies directly into buffer storage, avoiding large Java-array copies on Wasm GC.
Embedded images use the same async image path after their buffers arrive.

These work budgets are not hard deadlines: allocation, individual number conversion,
accessor/structure validation, mesh packing, buffer uploads, and model
publication still run on the application thread; an individual upload cannot be
preempted by this budget. With workers, CPU validation and preparation run there.
Default managed image loaders and glTF mipmap preparation automatically share a
[`WebAssetPreparation`](../../../backends/web/src/main/java/io/github/libfdx/backend/web/WebAssetPreparation.java)
worker on web. Ordinary `ImageAssetLoader.register(assets)` and
`G3DAssetLoaders.register(assets, graphics)` need no worker setup. The manager creates
one worker lazily, shares it across image dependencies and embedded glTF images/mipmaps,
and disposes it after releasing/cancelling assets. Managers have independent worker
lifetimes. It implements explicit CPU jobs; it does not execute arbitrary Java
`Runnable` closures. Custom contexts without `preparationResource` ownership retain
the executor/cooperative path, as do desktop and Android.

Applications may still explicitly borrow a worker through
`G3DAssetLoaders.register(assets, graphics, preparation, preparation)`, then dispose
it after all borrowing managers. Static `ImageAssetLoader.decodeAsync` and direct
`TextureMipmaps` APIs keep their existing execution behavior. The bundled
worker runs the same Java PNG/mipmap code, compiled as a separate JavaScript entry
point, for both JavaScript and Wasm GC applications. Browser image formats outside
the raw PNG path use `createImageBitmap` and worker `OffscreenCanvas` conversion.

The worker processes one job at a time, accepting at most eight jobs and 64 MiB of
input. Manager-owned defaults prepare overflow cooperatively so bursts do not fail
merely because the worker is busy. Explicitly constructed workers retain their
strict queue limit and reject saturation. Transfers copy borrowed input before
detaching message buffers; application-side copies yield between bounded batches.
Unavailable worker/canvas support, startup failure, or a worker crash uses cooperative
preparation. Cancellation of an asset does not interrupt its accepted CPU job;
disposing the owning manager (or an explicitly owned preparation service) terminates
its worker and cancels remaining jobs.
GPU resources, asset dependencies and publication remain on the application thread.
Other CPU stages, including glTF geometry, retain their existing scheduling.
This image worker does not eliminate shader or driver stalls.

Asset completion does not prepare a renderer's shaders. Configure `ModelBatch` with
`ShaderPreparation` and a shared `ModelShaderPlan`, collect model requirements into
a loading scope, and wait for its successful completion before drawing. Browser
providers require explicit `updateLoading()` to advance CPU shader preparation;
ordinary `update()` is sufficient for providers reporting runtime nonblocking support.
The web backend offloads async Tint compilation to its compiler worker; loading
updates can still block during custom Java source generation or worker fallback.
Standard model plans automatically offload their PBR graph recipe to a plan-owned
web worker. See the
[shader preparation contracts](../../../../docs/SHADERS.md#preparation-service-contracts).

`update()` drains available work without waiting for pending input. Use budgeted
updates each frame and keep rendering while downloads or preparation remain
pending. Browser preloading is an optimization, not a requirement for managed
loading. The manager has no blocking completion method; migrate former
`finishLoading()` calls to a loading state that checks handles/futures after
each update. Completion includes failures, which must be handled before entering
the ready state. Calling update recursively from a callback is invalid.

A loader can read its input with `context.readBytes(file)`, prepare CPU data with
`context.async(task)`, then discover dependencies from the preparation callback.
For resumable CPU work, `context.asyncSteps(step)` repeats a bounded step until it
returns true. The default manager drains this on a worker when supplied, otherwise
each step goes through the update budget. Keep its captured staging data GC-managed;
do not put resources requiring explicit cancellation cleanup in that state.
These methods compose pending futures without waiting on them. Declare every
dependency before the first `context.completeOnUpdate(task)` call. That finalizer
only runs once all declared dependencies, including their children, have loaded.
Dependency failures prevent finalization; cycles fail with the involved paths.
GPU access belongs in finalization, and staging data successfully delivered to
the loader remains its responsibility through later failure or cancellation.

The image → texture → texture-region loaders use this pipeline. Managed bitmap
fonts acquire their `.fnt` definition asynchronously, retain page images as
dependencies, and create owned GPU pages after every image is ready. TTF/OTF
fonts similarly acquire bytes before rasterization and GPU upload. Standalone
`BitmapFontFiles` helpers still require already available file reads.
The glTF loader
also acquires external buffer and image dependencies asynchronously; embedded
images are decoded after their buffers arrive. `G3DAssetLoaders.register` installs
its required binary and image loaders. A caller registering `modelLoader` alone
must register those dependencies too. A model owns its uploaded GPU resources;
its shared managed dependencies are decoded images and external bytes.

[`AssetScope`](src/main/java/io/github/libfdx/assets/AssetScope.java) groups leases
for an application-owned lifetime, such as one level. Every `scope.load` returns
a distinct [`AssetLease`](src/main/java/io/github/libfdx/assets/AssetLease.java),
while compatible requests share the cached resource and dependencies. A lease
can be disposed early; closing its scope releases all remaining leases. For one
independent resource outside a scope, use `assets.acquire(descriptor)` and dispose
the returned lease. Keep these ownership operations on the application thread.

Two scopes may load the same texture. Closing the first invalidates its leases
and leaves the second scope's texture usable. Closing the last owner disposes the
texture and its retained dependencies. Each lease has its own future, so cancelling
one pending lease does not fail another scope's load. Success/failure delivery,
including a new lease on an already loaded entry, uses the shared update budget.
Closing a scope invalidates all its leases before dispatching cancellation callbacks;
it continues cleanup through callback/disposer errors before reporting them.

Dependencies are borrowed and retained by their parents. A loaded parent disposes
its own resource before releasing its children. `unload(path)` releases direct
ownership acquired by `assets.load` for all types at that path. Scoped and
standalone leases remain valid. Repeated direct `assets.load` calls share one
direct owner for compatibility; use leases for independent releases. Failed
entries remain cached until all owners release them: dispose failed leases and
release any direct ownership before retrying. Old attempts cannot publish into
a new entry. A completed future is only a result, and cannot keep its resource
alive after its lease is released.

Each path/type supports one immutable options configuration per manager.
Conflicting options and loader replacement with live entries fail explicitly.
Use separate managers for different graphics devices or resource domains.
Scopes belong to exactly one manager; matching file paths or provider names do
not cause cross-manager sharing. Domain compatibility remains explicit through
the application's loader registration and manager setup.
Legacy loaders keep their signatures and may still execute inline during load;
migrate their work to the context methods to participate in scheduling.
Custom `AssetManager` implementations must implement `createScope` and `acquire`
with the ownership and notification contracts above.

Managed preparation and load results are delivered on the application thread.
Unload/dispose cancellation callbacks run during those operations. As with
`FdxFuture`, a callback attached after completion runs immediately on the
registering thread. Closing the manager disposes queued owned results; late owned
CPU results are released on the producing thread and must support that cleanup.

`AssetLoadContext.asyncFuture` also queues provider-asynchronous operations, such as
`FileHandle.openRead` and bounded `FileDataSource.read`, without waiting for their
futures. It uses the same worker/cooperative preparation queue, update budget and
late-result cleanup as `async`. Capture file handles on the application thread
before submitting work. After an owned source is delivered, the loader must close
it on success, failure and cancellation, or transfer ownership to its result.
Custom contexts must implement this optional operation to support such loaders;
the default reports unsupported behavior explicitly.
