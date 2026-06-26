# libFDX Common API

This document defines the provider-neutral public API contracts for libfdx-owned modules.

Use this document to decide what a common API type means, what module owns it, and what behavior provider implementations must support. Use [ARCHITECTURE.md](ARCHITECTURE.md) to decide folder layout, Gradle module names, Maven artifact names, dependency direction, and package roots. Use [SHADERS.md](SHADERS.md) for the WGSL-only shader architecture, runtime compilation flow, and optional editor/runtime compiler model.

## Index

1. [Goals](#1-goals)
2. [API Source Of Truth](#2-api-source-of-truth)
3. [Common API Rules](#3-common-api-rules)
4. [Naming Rules](#4-naming-rules)
5. [Core](#5-core)
    1. [Core Base Contracts](#51-core-base-contracts)
    2. [Foundation Math Types](#52-foundation-math-types)
    3. [Foundation JSON Types](#53-foundation-json-types)
    4. [Foundation Collections Types](#54-foundation-collections-types)
6. [Application](#6-application)
    1. [ApplicationListener Contract](#61-applicationlistener-contract)
    2. [Fdx Runtime Root Contract](#62-fdx-runtime-root-contract)
    3. [Application Service Contract](#63-application-service-contract)
    4. [ApplicationBackend Contract](#64-applicationbackend-contract)
    5. [ApplicationConfig Contract](#65-applicationconfig-contract)
7. [Files](#7-files)
    1. [FileSystem And FileHandle Contracts](#71-filesystem-and-filehandle-contracts)
    2. [Persistent Storage](#72-persistent-storage)
8. [Input](#8-input)
    1. [Input And Gamepad Contracts](#81-input-and-gamepad-contracts)
9. [Display](#9-display)
    1. [Display Contract](#91-display-contract)
10. [Audio](#10-audio)
    1. [Audio Contracts](#101-audio-contracts)
11. [Net](#11-net)
    1. [Network Contracts](#111-network-contracts)
12. [Assets](#12-assets)
    1. [Asset Contracts](#121-asset-contracts)
13. [Graphics API](#13-graphics-api)
    1. [Graphics Provider Contract](#131-graphics-provider-contract)
    2. [Graphics Resource And Command Contracts](#132-graphics-resource-and-command-contracts)
    3. [Generic Provider Flow](#133-generic-provider-flow)
    4. [Provider Mapping Examples](#134-provider-mapping-examples)
    5. [Texture And TextureView](#135-texture-and-textureview)
    6. [Graphics Surface Boundary](#136-graphics-surface-boundary)
    7. [Graphics Capabilities](#137-graphics-capabilities)
14. [Graphics 2D](#14-graphics-2d)
    1. [Graphics 2D Contracts](#141-graphics-2d-contracts)
15. [Graphics 3D](#15-graphics-3d)
    1. [Graphics 3D Contracts](#151-graphics-3d-contracts)
16. [UI Kit](#16-ui-kit)
    1. [UI Kit Contracts](#161-ui-kit-contracts)
17. [Scenario Validator](#17-scenario-validator)
    1. [Scenario Validator Contracts](#171-scenario-validator-contracts)
18. [Initial API Decisions](#18-initial-api-decisions)
19. [Runtime Core](#19-runtime-core)

## 1. Goals

- Keep game code written against portable common APIs.
- Keep provider-specific APIs explicit through provider modules and `ProviderHandle.as()`.
- Avoid common APIs that secretly assume one graphics, audio, input, or platform backend.
- Use capabilities for optional behavior instead of pretending every provider supports everything.
- Keep low-level APIs explicit enough that high-level modules such as `g2d`, `g3d`, `ui-kit`, and `scenario-validator` can be built without provider-specific code.

## 2. API Source Of Truth

This document is the source of truth for libfdx common API design. Source code must match this document.

The type names below are the Java source names unless a section explicitly says the type is optional, internal, or owned by an external binding. If a source change adds, removes, renames, or changes the behavior of a common API type, this document must be updated in the same change.

If source code and this document disagree, treat it as an API design issue to resolve instead of allowing the implementation to silently drift.

- common API types live in foundation, runtime, assets, graphics, g2d, g3d, ui-kit, and scenario-validator modules
- provider-specific types live in extension modules
- backend launcher/runtime types live in backend modules

## 3. Common API Rules

Common API types are the default types users should write game code against. They should expose portable concepts only.

Provider-specific work should stay behind the implementation or be reached through the `ProviderHandle` escape hatch defined in [5.1. Core Base Contracts](#51-core-base-contracts).

Interface documentation rule:

- service, resource, listener, and provider contracts should have Java-like interface shapes in this document
- descriptors, options, configs, value types, and enums may use tables when a full interface does not add clarity
- external bindings do not get libfdx-invented interface shapes

Rules:

- Provider-neutral common APIs should target Java 25 source compatibility unless a section explicitly opts out.
- Java 25 language features and JDK APIs may appear in common API signatures only when they are provider-neutral.
- Provider/backend-specific details, such as FFM-based bindings, must stay behind provider/backend modules.
- `as()` is an advanced provider-specific access path.
- `as()` has no `Class<T>` parameter. The caller selects `T` through assignment or target typing.
- A wrong target type should fail clearly, normally through Java casting behavior.
- `providerId()` lets user code check the backing provider before calling `as()`.
- Objects returned by `as()` are valid only for the lifetime of the backing provider/device/resource.
- Portable modules should not require `as()` for normal behavior.
- Native handles should not appear in normal game-facing common APIs. The `NativeWindow` type is the explicit backend/provider setup exception and should be created by backends and consumed by graphics providers, not used by shared game code.
- `runtime/fdx/core` services are framework-internal runtime capabilities. Game code should normally use higher-level APIs such as `UiFont.freeType(...)`, `BitmapFontFiles.loadFreeType(...)`, and math classes. Those APIs may use `runtime/fdx/core` internally for default native-backed behavior.

Common handles backed by provider state should implement `ProviderHandle`, including:

```text
Application
FileSystem
Input
Display
FileWatch
Gamepads
Gamepad
AudioDevice
PlaybackHandle
AudioSource
Sound
Music
AudioBuffer
Network
WebSocket
Graphics
GraphicsContext
GraphicsAttachment
GraphicsDevice
Surface
SurfaceTexture
Texture
TextureView
Buffer
Sampler
ShaderModule
BindGroupLayout
BindGroup
PipelineLayout
RenderPipeline
ComputePipeline
CommandEncoder
CommandBuffer
RenderPass
ComputePass
```

## 4. Naming Rules

Use `Graphics` for the graphics manager and `GraphicsContext` for provider-backed rendering contexts:

```text
Graphics
GraphicsContext
GraphicsDevice
GraphicsCapabilities
```

Use short resource names when the type is already inside `io.github.libfdx.graphics`:

```text
Texture
TextureView
Buffer
Sampler
ShaderModule
RenderPipeline
CommandBuffer
```

Use `GraphicsDevice` and `GraphicsQueue` for public service names instead of GPU-prefixed alternatives.

All public `ui-kit` classes must use the `Ui` prefix:

```text
UiNode
UiRoot
UiScope
UiModifier
UiState
UiTheme
UiStyle
UiNinePatch
UiAnimationSpec
UiTransition
UiTextStyle
UiFont
UiLayer
UiPopup
UiModal
UiTooltip
UiFocusScope
UiNavigation
UiListState
UiScrollState
UiTextAreaOptions
```

Core scenario validator classes use the `Scenario` prefix:

```text
ScenarioValidator
ScenarioCatalog
Scenario
ScenarioSetup
ScenarioHost
ScenarioContext
ScenarioActions
ScenarioAssertions
ScenarioWaits
ScenarioReport
ScenarioResult
ScenarioValidationConfig
ScenarioValidationMode
ScenarioCapturePolicy
ScenarioValidationCell
ScenarioValidationCellStatus
```

Scenario validator domain adapters use a domain prefix followed by `Scenario`.
The UI Kit adapter uses the `UiScenario` prefix:

```text
UiScenarioActions
UiScenarioAssertions
UiScenarioTargets
UiScenarioWaits
```

External binding extensions should not rename public binding classes just to match libfdx style.

## 5. Core

Module:

```text
:libfdx:runtime:fdx:core
```

Package:

```text
io.github.libfdx.core
```

The fdx runtime module owns tiny framework contracts, base errors, logging, provider identity, async primitives, and shared runtime services that every module can depend on. It is the only public common runtime foundation module and publishes as `io.github.libfdx:fdx`.

Defined types:

| Type | Role |
| --- | --- |
| `Disposable` | Common cleanup contract for resources with explicit lifetime. |
| `FdxService` | Internal marker available for backend/provider wiring code when a backend keeps an implementation registry. It is not part of the user-facing runtime access model. |
| `FdxException` | Base framework exception. |
| `Logger` | Logging API independent from a concrete logging implementation. |
| `ProviderId` | Stable logical provider identity, such as `wgpu`, `vulkan`, or `miniaudio`. |
| `ProviderHandle` | Escape hatch contract for provider-backed common handles. |
| `FdxFuture<T>` | Framework async result type for portable async operations across desktop, web, Android, iOS, and C-backed targets. |
| `Consumer<T>` and `Consumer<Throwable>` callbacks | Callback contracts used by `FdxFuture<T>`. |

### 5.1. Core Base Contracts

Core interfaces are intentionally small so every module can depend on them without pulling in higher runtime systems.

Defined shape:

```java
public interface Disposable {
    void dispose();
    boolean isDisposed();
}

public interface FdxService {
}

public interface Logger extends FdxService {
    void debug(String message);
    void info(String message);
    void warn(String message);
    void error(String message);
    void error(String message, Throwable error);
}

public interface ProviderHandle {
    ProviderId providerId();
    <T> T as();
}

public final class ProviderId {
    public static ProviderId of(String value);
    public String value();
    public boolean equals(Object other);
    public int hashCode();
    public String toString();
}

public final class FdxFuture<T> {
    public static <T> FdxFuture<T> pending();
    public static <T> FdxFuture<T> completed(T value);
    public static <T> FdxFuture<T> failed(Throwable error);
    public static <T> FdxFuture<T> supply(FdxTask<T> task);

    public FdxFuture<T> onSuccess(Consumer<T> callback);
    public FdxFuture<T> onFailure(Consumer<Throwable> callback);
    public boolean isDone();
    public boolean isFailed();
    public T join();
    public T get();
    public void complete(T value);
    public void completeExceptionally(Throwable error);
}
```

Example:

```java
network.httpClient()
    .send(request)
    .onSuccess(response -> logger.info("Status: " + response.status().code()))
    .onFailure(error -> logger.error("Request failed", error));
```

Rules:

- `Disposable.dispose()` should be safe to call more than once.
- Using a disposed provider-backed object should fail clearly.
- Core must not depend on assets, graphics, audio, UI, physics, extensions, or backends. Foundation modules may depend on `runtime/fdx/core` for shared base contracts, but not on higher runtime systems.
- Async APIs should not assume blocking threads are available on every platform.
- When a method returns an object and that object does not exist, it returns `null`.
- `FdxFuture.get()` and `FdxFuture.join()` return the completed value and fail clearly if the future has not completed or completed with an error.
- A future completes at most once. Once completed, its result or error must not change.
- `onSuccess` and `onFailure` return the same future so callback registration can be chained.
- Callbacks registered before completion run once when the future completes. Callbacks registered after completion still run once for the already completed result.
- Callback order is registration order for callbacks of the same future and same completion path.
- Framework async APIs should dispatch callbacks on the application/main event loop when a running `Application` owns the operation. APIs used outside a running application must document their dispatch policy.
- Callback exceptions should be reported through `Logger` or the provider's error reporting path. They must not change the completed future result or prevent later callbacks from running.
- The initial `FdxFuture<T>` contract has no cancellation API. Cancellation can be added later through a separate type or explicit operation-specific method.
- `FdxService` is reserved for private backend/provider wiring when an implementation keeps an internal registry. It is not a signal that user code should resolve a type generically.
- Provider and backend factory contracts should not implement `FdxService`.
- `Logger` is returned by `Fdx.logger()` so applications and framework modules can share the same logging API.
- `ProviderId` equality is value-based. Two `ProviderId` instances with the same `value()` must compare equal and have the same hash code.
- Provider ID values should be stable lowercase identifiers such as `wgpu`, `vulkan`, `miniaudio`, or `desktop_gamepads`.

### 5.2. Foundation Math Types

Module:

```text
:libfdx:foundation:math
```

Package:

```text
io.github.libfdx.math
```

Math owns backend-neutral value types used by rendering, UI, physics extensions, and game code.

Defined types:

| Type | Role |
| --- | --- |
| `Vector2`, `Vector3`, `Vector4` | Vector math values. |
| `Matrix3`, `Matrix4` | Matrix math values. |
| `Quaternion` | 3D rotation value. |
| `BoundingBox` | 3D bounds value. |
| `Color` | Backend-neutral color value. |

Rules:

- Math types must not depend on runtime, graphics, assets, UI, extensions, or backends.
- Graphics, g2d, g3d, and ui-kit APIs may use math types in public signatures.
- `Color` is the common color value used by rendering descriptors and 2D drawing helpers.
- Scalar math behavior is the portable contract. Backend-installed acceleration may optimize bulk or in-place math operations, but every accelerated path must keep a scalar fallback with the same results. Desktop x86/x64 acceleration prefers AVX2+FMA for useful bulk work when supported, then falls back to SSE or native scalar paths. Android ARM64 uses NEON through the backend native `libfdx.so` when available.

### 5.3. Foundation JSON Types

Module:

```text
:libfdx:foundation:json
```

Package:

```text
io.github.libfdx.json
```

JSON owns provider-neutral data-tree parsing, writing, and manual class mapping. It is a small foundation library, not an asset-only API and not part of the runtime root.

Defined types:

| Type | Role |
| --- | --- |
| `Json` | Convenience API for reading/writing JSON and using registered manual codecs. |
| `JsonReader` | Strict JSON parser from `String` or UTF-8 bytes into `JsonValue`. |
| `JsonValue` | Typed JSON tree for object, array, string, number, boolean, and null values. |
| `JsonWriter` | Compact or pretty JSON writer, including object/array streaming helpers. |
| `JsonCodec<T>` | User-provided callback that maps between `JsonValue`/`JsonWriter` and a Java type. |

Defined shape:

```java
public final class Json {
    JsonValue read(String text);
    JsonValue read(byte[] bytes);
    String write(JsonValue value);
    String writePretty(JsonValue value);
    <T> Json register(Class<T> type, JsonCodec<T> codec);
    <T> T fromJson(Class<T> type, String text);
    <T> T read(Class<T> type, JsonValue value);
    <T> String toJson(Class<T> type, T value);
    <T> void write(Class<T> type, JsonWriter writer, T value);
}

public interface JsonCodec<T> {
    T read(Json json, JsonValue value);
    void write(Json json, JsonWriter writer, T value);
}
```

Example:

```java
Json json = new Json();
json.register(Player.class, new JsonCodec<Player>() {
    @Override
    public Player read(Json json, JsonValue value) {
        return new Player(value.requireString("name"), value.intValue("level", 1));
    }

    @Override
    public void write(Json json, JsonWriter writer, Player player) {
        writer.object()
                .name("name").value(player.name())
                .name("level").value(player.level())
                .endObject();
    }
});

Player player = json.fromJson(Player.class, "{\"name\":\"Ada\",\"level\":3}");
String text = json.toJson(Player.class, player);
```

Rules:

- JSON APIs must not use reflection, annotations, constructor discovery, field scanning, class-name lookup, or polymorphic type guessing.
- `Class<T>` is allowed only as an explicit codec registry key selected by the caller.
- `JsonCodec<T>` callbacks own object creation and field mapping. Missing values, defaults, and type tags must be handled explicitly by user code or by format-specific code.
- `JsonValue` preserves JSON object member order.
- `JsonReader` parses strict JSON and fails clearly for invalid syntax.
- `JsonWriter` must emit valid JSON with correct string escaping.
- `foundation/json` must not depend on files, assets, graphics, UI, extensions, backends, or platform APIs.
- Asset workflows may use `JsonAssetLoader` from `assets/loaders` to load a `JsonValue`, but parsing and writing remain usable directly from `foundation/json`.

### 5.4. Foundation Collections Types

Module:

```text
:libfdx:foundation:collections
```

Package:

```text
io.github.libfdx.collections
```

Collections owns backend-neutral, allocation-conscious data structures for engine hot paths and user game code that needs tighter control than standard Java collections.

Defined types:

| Type | Role |
| --- | --- |
| `FdxArray<T>` | Growable array with ordered or unordered removal, positional helpers, and stack helpers. |
| `IntArray` | Growable primitive `int` array with ordered or unordered removal, positional helpers, and stack helpers. |
| `FloatArray` | Growable primitive `float` array with ordered or unordered removal, positional helpers, stack helpers, and `Float.floatToIntBits` value identity for searches. |
| `LongArray` | Growable primitive `long` array with ordered or unordered removal, positional helpers, and stack helpers. |
| `ObjectMap<K, V>` | Open-addressed object-key map with reusable entry iteration, key/value views, value lookup helpers, and explicit capacity controls. |
| `IntMap<V>` | Open-addressed primitive `int` key map with reusable entry iteration, key/value views, value lookup helpers, and explicit capacity controls. |
| `LongMap<V>` | Open-addressed primitive `long` key map with reusable entry iteration, key/value views, value lookup helpers, and explicit capacity controls. |
| `FloatMap<V>` | Open-addressed primitive `float` key map using `Float.floatToIntBits` key identity, reusable entry iteration, key/value views, value lookup helpers, and explicit capacity controls. |
| `FdxLinkedList<T>` | Doubly linked list that returns removable node handles and exposes empty-state helpers. |

Rules:

- Collections must remain pure Java and provider-neutral. They must not depend on files, assets, graphics, UI, extensions, backends, or platform APIs.
- Collection APIs must not depend on `runtime/fdx/core` unless they use a shared base contract. The initial collection types have no runtime behavior and no runtime module dependency.
- Primitive arrays must avoid boxing values in their storage, lookup, and removal paths.
- Primitive-key maps must avoid boxing keys in their storage and lookup paths.
- Maps must distinguish a missing key from a present key whose value is `null`; callers use `containsKey(...)` when that distinction matters.
- Maps expose `containsValue(...)`, identity-aware `containsValue(..., true)`, and `findKey(...)` helpers. Primitive-key maps return a caller-provided default key when no value matches.
- Maps expose `ensureCapacity(...)` for expected additional entries and `shrink()` to reduce table storage and compact removed-slot tombstones after heavy churn.
- `ObjectMap.entries()`, `IntMap.entries()`, `LongMap.entries()`, and `FloatMap.entries()` return iterable views over occupied slots. Their iterators may reuse one entry object per iterator to avoid per-entry allocation, so callers that need to retain entries should copy the key and value. `keys()` and `values()` expose direct views over occupied slots. Primitive-key map key iterators expose `nextInt()`, `nextLong()`, and `nextFloat()` to avoid boxing in the common path.
- `ObjectMap` does not accept `null` keys.
- `FdxArray` ordered removal preserves item order. Unordered removal may replace the removed index or inclusive removed range with tail values.
- `FdxArray` exposes equality and identity variants for `contains(...)`, `indexOf(...)`, `lastIndexOf(...)`, `removeValue(...)`, and `removeAll(...)`.
- Collection containers expose `notEmpty()` as the direct inverse of `isEmpty()`.
- Arrays expose `insert(...)`, `lastIndexOf(...)`, `removeAll(...)`, `removeRange(start, end)`, `swap(...)`, `reverse()`, `truncate(...)`, `first()`, `peek()`, `pop()`, and `notEmpty()` helpers. `removeAll(...)` removes the first matching occurrence for each supplied value. `removeRange(...)` uses inclusive indexes. `first()`, `peek()`, and `pop()` throw `NoSuchElementException` when the array is empty.
- These types are correctness-tested in this repository. Performance benchmarks belong under the root `benchmark/` modules.

## 6. Application

Module:

```text
:libfdx:runtime:application
```

Package:

```text
io.github.libfdx.application
```

Defined types:

| Type | Role |
| --- | --- |
| `Fdx` | Typed root object passed to user code at startup. It exposes backend-owned runtime systems without generic service lookup. |
| `Application` | Running application lifecycle and frame-timing interface. |
| `ApplicationListener` | User lifecycle callbacks with `render()` as the per-frame method. |
| `ApplicationConfig` | Startup configuration, including selected providers and initial runtime settings. |
| `ApplicationBackend` | Launcher-side backend lifecycle implementation contract. |
| `ApplicationLifecycle` | Lifecycle state enum or helper. |

### 6.1. ApplicationListener Contract

`ApplicationListener` is implemented by the user's game/application class. The backend creates a typed `Fdx` root, attaches the selected backend-owned runtime systems, and passes it to `create()`. `render()` is the primary per-frame callback; frame timing is read from the `Application` interface returned by `fdx.app()`. Backends call `onFrameEnd()` after `render()` while the current frame is still active, so validation and readback code can capture the just-rendered frame before presentation cleanup.

Defined shape:

```java
public interface ApplicationListener {
    void create(Fdx fdx);
    void resize(int width, int height);
    void render();
    default void onFrameEnd() {
    }
    void pause();
    void resume();
    void dispose();
}
```

Example:

```java
public final class MyGame implements ApplicationListener {
    private Fdx fdx;
    private GameWorld world;

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        world = new GameWorld(fdx);
    }

    @Override
    public void render() {
        world.update(fdx.app().deltaTime());
        world.render();
    }
}
```

Rules:

- Provider selection is a startup decision.
- Graphics, audio, and gamepad provider changes should apply after application restart unless a backend implements full internal recreation later.
- Common application code should not depend on backend packages.
- Backend code wires platform lifecycle into the common `ApplicationListener`.
- `ApplicationListener` does not have `update(float deltaTime)`. Game code updates simulation from `render()` or from user-owned systems called by `render()`.
- `onFrameEnd()` is for backend-owned frame-end hooks such as screenshot capture and validation readback. Normal game simulation and rendering should stay in `render()`.
- Application code receives the typed `Fdx` root during `create(Fdx fdx)`. It should not resolve arbitrary classes from a generic service locator.

### 6.2. Fdx Runtime Root Contract

`Fdx` is the user-facing root for backend-owned systems. It is finite and typed: it exposes the major runtime entry points directly and does not provide `require(Class<T>)`, `find(Class<T>)`, registration methods, or a generic service map.

Defined shape:

```java
public interface Fdx {
    Application app();
    Displays displays();
    Graphics graphics();
    FileSystem files();
    Storage storage();
    Input input();
    AudioDevice audio();
    Network network();
    Logger logger();
}
```

Example:

```java
public void create(Fdx fdx) {
    Display display = fdx.displays().main();
    GraphicsContext graphics = fdx.graphics().main();

    display.title("libfdx Game");
    Batch2D batch = new SpriteBatch(graphics);
}
```

Rules:

- `Fdx` contains only backend-owned runtime systems and root managers.
- `Fdx` must not expose a generic class-based lookup API.
- `Fdx` must not expose normal user-created feature objects such as `AssetManager`, `Batch2D`/`SpriteBatch`, UI roots, physics worlds, or scene objects.
- `Fdx.files()` returns the backend-owned file system when one exists, or `null` on a backend that does not expose files.
- `Fdx.storage()` returns the backend-owned persistent storage service when one exists, or `null` on a backend that does not expose persistence.
- `Fdx.input()`, `Fdx.audio()`, and `Fdx.network()` return `null` when the backend has no implementation for that system.
- If a backend keeps an internal mutable registry for wiring, that registry is private backend implementation detail, not the public programming model.

### 6.3. Application Service Contract

`Application` exposes runtime application state and application-level commands. It is also the common source for frame timing.

Defined shape:

```java
public interface Application extends ProviderHandle {
    ApplicationLifecycle lifecycle();
    float deltaTime();
    long frameId();
    void requestExit();
}
```

Example:

```java
Application app = fdx.app();

float deltaTime = app.deltaTime();

if (shouldQuit) {
    app.requestExit();
}
```

Rules:

- `Application` is returned by `Fdx.app()`.
- Normal game code should use `fdx.app()` instead of resolving `Application` from a generic context.
- `Application` is the runtime lifecycle interface exposed to the game after the backend starts.
- `Application.as()` is the advanced access path for backend-specific application/runtime handles.

### 6.4. ApplicationBackend Contract

`ApplicationBackend` is used by launcher/platform code to start the application. It is not a service that normal game code resolves from `Fdx`.

Defined shape:

```java
public interface ApplicationBackend extends Disposable {
    ProviderId providerId();
    void start(ApplicationConfig config, ApplicationListener listener);
}
```

Launcher example:

```java
public final class DesktopLauncher {
    public static void main(String[] args) {
        DesktopApplicationConfig config = new DesktopApplicationConfig()
            .title("My Game")
            .size(1280, 720)
            .graphics(new WGPUProvider());

        ApplicationBackend backend = new DesktopApplicationBackend();
        backend.start(config, new MyGame());
    }
}
```

Rules:

- `ApplicationBackend` should not extend `FdxService`.
- `ApplicationBackend` is owned by backend/launcher code, not by game logic.
- The backend creates `Fdx`, attaches backend-owned runtime systems and the selected provider managers, then calls `ApplicationListener.create(Fdx fdx)`.
- Backend modules expose concrete backend classes or factories that platform launchers compile against.
- Concrete backends may expose typed config classes, such as `DesktopApplicationConfig`, with direct setters for values that launcher code is expected to configure.

### 6.5. ApplicationConfig Contract

`ApplicationConfig` is launcher-side startup configuration. It is not a context service and should be read by the selected backend before the application starts. The base type stores only provider selection values shared across backends; concrete backends should expose typed config classes for their own startup options.

Defined shape:

```java
public class ApplicationConfig {
    public ApplicationConfig();

    public ProviderId graphicsProvider();
    public ApplicationConfig graphicsProvider(ProviderId providerId);

    public ProviderId audioProvider();
    public ApplicationConfig audioProvider(ProviderId providerId);

    public ProviderId gamepadProvider();
    public ApplicationConfig gamepadProvider(ProviderId providerId);
}
```

Rules:

- Provider selection fields return `null` when the launcher did not request a specific provider.
- If exactly one compatible provider for a system is available and no provider is explicitly selected, a backend may select it automatically.
- If multiple compatible providers are available, the backend should require an explicit `ProviderId` or fail with a clear configuration error.
- `ApplicationConfig` uses `ProviderId`, not Maven artifact names.
- String values loaded from user settings should be converted with `ProviderId.of(String)` before being stored in `ApplicationConfig`.
- `ApplicationConfig` is owned by `runtime/application` and must not depend on `assets`, `ui-kit`, extensions, or backend modules.
- Do not add a generic key/value config map to `ApplicationConfig`. Startup options should be discoverable through typed backend/provider config APIs.
- Backend-specific values such as window title, size, foreground FPS, and Android native text editor style belong on backend config classes such as `DesktopApplicationConfig` and `AndroidApplicationConfig`.
- Provider-specific values such as WGPU backend selection belong on provider setup types such as `WGPUProvider` or provider-owned configuration descriptors.

## 7. Files

Module:

```text
:libfdx:runtime:files
```

Package:

```text
io.github.libfdx.files
```

Defined types:

| Type | Role |
| --- | --- |
| `FileSystem` | File service exposed through the context. |
| `FileHandle` | Portable reference to a file-like resource. |
| `FileLocation` | Logical location such as classpath, internal, local, external, cache, or temp. |
| `FileMetadata` | File size, modification time, and type metadata when available. |
| `FileWatch` | File watching contract for platforms that support it. |
| `FileWatchListener` | File watching callback contract. |
| `Storage` | Persistent local/cache storage service returned by `Fdx.storage()`. |
| `KeyValueStore` | Named loaded key/value store for settings, save data, and rebuildable caches. |
| `StorageScope` | Local or cache persistence scope. |
| `StorageCodec` | Optional user-supplied byte transform for encryption, compression, or other encoding. |

### 7.1. FileSystem And FileHandle Contracts

`FileSystem` is the service users ask for file handles. `FileHandle` is the portable reference users pass to loaders, decoders, tools, and runtime systems.

Defined shape:

```java
public interface FileSystem extends FdxService, ProviderHandle {
    FileHandle classpath(String path);
    FileHandle internal(String path);
    FileHandle local(String path);
    FileHandle external(String path);
    FileHandle cache(String path);
    FileHandle temp(String prefix, String suffix);
    FdxFuture<FileWatch> watch(FileHandle file);
}

public interface FileHandle {
    FileLocation location();
    String path();
    String name();
    String extension();
    FileHandle parent();
    FileHandle child(String relativePath);
    boolean exists();
    boolean isDirectory();
    FdxFuture<FileMetadata> metadata();
    FdxFuture<byte[]> readBytes();
    FdxFuture<String> readString(Charset charset);
    FdxFuture<Void> writeBytes(byte[] bytes, boolean append);
    FdxFuture<Void> writeString(String text, Charset charset, boolean append);
}

public interface FileWatch extends ProviderHandle, Disposable {
    FileHandle file();
    void addListener(FileWatchListener listener);
    void removeListener(FileWatchListener listener);
}

public interface FileWatchListener {
    void changed(FileHandle file);
    void deleted(FileHandle file);
}
```

Example:

```java
FileSystem files = fdx.files();
FileHandle config = files.local("settings.json");

config.readString(StandardCharsets.UTF_8)
    .onSuccess(json -> logger.info(json));
```

Rules:

- `FileHandle` should represent a path plus location, not only a Java `File`.
- Not every location is writable on every platform.
- Web backends may not support blocking file APIs for every storage location.
- Browser internal/classpath assets declared through Gradle `libfdx.assets` or standalone `backend_web` `WebBuilder` assets are copied into generated webapp assets, preloaded by the generated page, and exposed through `fdx.files().internal(...)`.
- PSP internal/classpath assets declared through Gradle `libfdx.assets` or standalone `backend_psp` `PspBuilder.asset(...)` are copied into the generated PSP release layout and exposed through read-only `fdx.files().internal(...)` handles.
- iOS C internal/classpath assets declared through Gradle `libfdx.assets` are copied into the generated Xcode project asset bundle and exposed through read-only `fdx.files().internal(...)` handles.
- Asset loading should be designed so web implementations can be async.
- In `WebApplicationConfig`, a display width or height of `0` or a negative value means the canvas fills the browser window.
- Platform-specific native file handles should be reachable only through provider/backend-specific APIs.
- `FileSystem.as()` is the advanced access path for backend-specific filesystem services.
- `FileWatch` is provider-backed because file watching is implemented differently across platforms.

### 7.2. Persistent Storage

`Storage` is the service users ask for persistent named stores. It is intended for small settings/preferences, save metadata, editor state, and rebuildable cache records. It is not an asset manager and not a database abstraction.

Defined shape:

```java
public interface Storage extends ProviderHandle {
    KeyValueStore local(String name);
    KeyValueStore local(String name, StorageCodec codec);
    KeyValueStore cache(String name);
    KeyValueStore cache(String name, StorageCodec codec);
}

public interface KeyValueStore {
    String name();
    StorageScope scope();
    boolean loaded();
    boolean dirty();
    KeyValueStore load();
    KeyValueStore flush();
    boolean contains(String key);
    String[] keys();
    KeyValueStore remove(String key);
    KeyValueStore clear();
    String getString(String key, String fallback);
    KeyValueStore putString(String key, String value);
    int getInt(String key, int fallback);
    KeyValueStore putInt(String key, int value);
    long getLong(String key, long fallback);
    KeyValueStore putLong(String key, long value);
    float getFloat(String key, float fallback);
    KeyValueStore putFloat(String key, float value);
    double getDouble(String key, double fallback);
    KeyValueStore putDouble(String key, double value);
    boolean getBoolean(String key, boolean fallback);
    KeyValueStore putBoolean(String key, boolean value);
    byte[] getBytes(String key, byte[] fallback);
    KeyValueStore putBytes(String key, byte[] value);
    JsonValue getJson(String key, JsonValue fallback);
    KeyValueStore putJson(String key, JsonValue value);
    <T> T getJson(String key, Class<T> type, Json json, T fallback);
    <T> KeyValueStore putJson(String key, Class<T> type, Json json, T value);
}
```

Example:

```java
KeyValueStore settings = fdx.storage().local("settings").load();

settings.putString("playerName", "Ada")
        .putInt("volume", 80)
        .putJson("profile", JsonValue.object().put("level", 3))
        .flush();
```

Rules:

- `local(name)` is durable user-owned data such as preferences, save data, editor state, recent files, and key bindings.
- `cache(name)` is rebuildable data such as generated shader outputs, thumbnails, downloaded metadata, and pipeline caches. It may survive between runs, but the app must tolerate losing it.
- Storage has no `temp` scope. Use `FileSystem.temp(...)` for short-lived files.
- Stores load into memory and write changes only when `flush()` is called.
- `load()` and `flush()` may throw `FdxException` when backend persistence fails.
- Default native/JVM-style storage is file-backed through `FileSystem.local(...)` and `FileSystem.cache(...)`.
- Web storage uses browser persistence, currently `localStorage`, because the web file system's writable handles are not guaranteed to survive page reloads.
- The base storage API is not encrypted. `StorageCodec` is only a byte transform hook for user-owned encryption, compression, or encoding. libFDX does not provide encryption algorithms or manage encryption keys.
- Typed JSON uses the existing explicit `Json` and `JsonCodec<T>` registry. Storage must not introduce reflection, annotations, field scanning, class-name lookup, or polymorphic guessing.

## 8. Input

Module:

```text
:libfdx:runtime:input
```

Package:

```text
io.github.libfdx.input
```

Defined types:

| Type | Role |
| --- | --- |
| `Input` | Main input service. |
| `InputProcessor` | Input event callback/routing contract. |
| `InputEvent` | Base input event. |
| `Key`, `KeyEvent` | Keyboard and platform key values and events. |
| `MouseButton`, `PointerEvent` | Mouse/pointer values and events. |
| `TouchPoint`, `TouchEvent` | Touch values and events. |
| `TextInputEvent` | Text input event for typed characters and IME-oriented text input. |
| `TextInputRequest` | Current text-input session state requested by UI or game code. |
| `TextInputType` | Portable text keyboard type hint such as text, integer, or decimal. |
| `TextInputController` | Backend delegate used by `Input` implementations to show, update, and hide platform text input. |
| `Cursor` | Cursor shape, visibility, and lock/capture requests when supported. |
| `CursorShape` | Portable cursor shape identifier. |
| `Gamepads` | Gamepad access API backed by a gamepad provider. |
| `Gamepad` | Portable gamepad/controller handle. |
| `GamepadButton` | Portable gamepad button identifiers. |
| `GamepadAxis` | Portable gamepad axis identifiers. |
| `GamepadMapping` | Mapping from platform-specific controls to portable controls. |
| `GamepadState` | Snapshot of gamepad state. |
| `GamepadListener` | Gamepad connection event callback. |
| `InputCapabilities` | Supported input features for the current backend/provider stack. |

### 8.1. Input And Gamepad Contracts

`Input` is the single runtime service for keyboard, pointer, touch, text input, cursor, and gamepad access. `Gamepads` is provider-backed because each platform discovers and maps controllers differently.

Platform navigation Back is represented as `Key.BACK`. Keyboard Escape remains `Key.ESCAPE`; shared code that wants both behaviors should check both keys explicitly.

Defined shape:

```java
public interface Input extends FdxService, ProviderHandle {
    InputCapabilities capabilities();
    void addProcessor(InputProcessor processor);
    void removeProcessor(InputProcessor processor);

    void showTextInput(TextInputRequest request);
    void updateTextInput(TextInputRequest request);
    void hideTextInput();

    boolean isKeyPressed(Key key);
    boolean isMouseButtonPressed(MouseButton button);
    int pointerX();
    int pointerY();

    Cursor cursor();
    Gamepads gamepads();
}

public interface InputCapabilities {
    boolean supportsKeyboard();
    boolean supportsPointer();
    boolean supportsTouch();
    boolean supportsTextInput();
    boolean supportsCursor();
    boolean supportsGamepads();
}

public interface InputProcessor {
    boolean keyDown(KeyEvent event);
    boolean keyUp(KeyEvent event);
    boolean pointerDown(PointerEvent event);
    boolean pointerUp(PointerEvent event);
    boolean pointerMoved(PointerEvent event);
    boolean scrolled(PointerEvent event);
    boolean touchDown(TouchEvent event);
    boolean touchUp(TouchEvent event);
    boolean touchMoved(TouchEvent event);
    boolean textInput(TextInputEvent event);
}

public final class TextInputRequest {
    String text();
    int selectionStart();
    int selectionEnd();
    boolean multiline();
    boolean password();
    boolean readOnly();
    TextInputType type();
    boolean hasBounds();
    int boundsX();
    int boundsY();
    int boundsWidth();
    int boundsHeight();
}

public enum TextInputType {
    TEXT,
    INTEGER,
    DECIMAL
}

public interface TextInputController {
    void showTextInput(TextInputRequest request);
    void updateTextInput(TextInputRequest request);
    void hideTextInput();
}

public interface Cursor {
    boolean isVisible();
    void visible(boolean visible);
    boolean isCaptured();
    void captured(boolean captured);
    CursorShape shape();
    void shape(CursorShape shape);
}

public interface Gamepads extends ProviderHandle {
    List<Gamepad> connected();
    Gamepad find(int index);
    void addListener(GamepadListener listener);
    void removeListener(GamepadListener listener);
}

public interface Gamepad extends ProviderHandle {
    String id();
    String name();
    int index();
    boolean isConnected();
    GamepadMapping mapping();
    GamepadState state();
    float axis(GamepadAxis axis);
    boolean pressed(GamepadButton button);
}
```

Listener contract:

```java
public interface GamepadListener {
    void connected(Gamepad gamepad);
    void disconnected(Gamepad gamepad);
}
```

Example:

```java
Input input = fdx.input();
Gamepads gamepads = input.gamepads();

if (gamepads != null) {
    for (Gamepad gamepad : gamepads.connected()) {
        float x = gamepad.axis(GamepadAxis.LEFT_X);
        boolean jump = gamepad.pressed(GamepadButton.SOUTH);
    }
}
```

Rules:

- Keyboard, mouse, touch, text input, and gamepad access should be available from one `Input` service.
- Gamepads are part of `runtime/input`; platform-specific gamepad providers live under `extensions/input/gamepads`.
- Text input is not the same as key input. UI and text fields should use text input events.
- `Input.showTextInput(...)` requests a platform text-input session for the supplied value, selection, multiline, password, read-only, keyboard-type, and optional display-space focused text bounds. Multiline widgets should use the active caret or selected line bounds so mobile backends can keep the edited line visible.
- `Input.updateTextInput(...)` refreshes platform text-input state after cursor, selection, or text changes. It must not dispatch a duplicate text event by itself.
- `Input.hideTextInput()` ends the active platform text-input session. Backends without a platform soft keyboard may implement these session methods as no-ops while still dispatching text events from hardware or window-system text callbacks.
- Android text input opens the soft keyboard for non-read-only text requests, maps `TextInputType` to Android keyboard flags, and may use a native editor panel above the keyboard for platform-owned cursor, selection, and IME behavior. Native editor panels commit the final text back through normal input events when the user accepts the edit, and close without syncing edits when the user cancels.
- Android native editor panel colors, text sizes, labels, padding, margins, and button sizes are configured through `AndroidApplicationConfig.nativeTextEditorStyle(AndroidTextEditorStyle)`. Style colors are Android ARGB `int` values. UI Kit widget style does not implicitly recolor the native Android editor panel.
- Web input dispatches browser mouse, touch, wheel, keyboard, and text events through the same `Input` service. Web text input uses a DOM-backed editor panel for browser keyboard and IME integration while the session is active. On mobile browsers, including Android browsers, the panel is positioned above the visual viewport keyboard area when that browser reports it. Accepting the edit commits the final text back through normal input events; canceling closes the panel without syncing edits. Browser text selection handles and keyboard behavior remain browser-owned.
- PSP input exposes the built-in PSP buttons and analog stick as one standard-mapped connected gamepad through `fdx.input().gamepads().find(0)`. The backend also maps PSP controller edges to portable key events for UI focus/navigation: d-pad to arrow keys, Cross to `ENTER`, Square to `SPACE`, Circle to `ESCAPE`, and shoulder buttons to page keys. Physical PSP keyboard, pointer, touch, text input, and cursor capabilities are unsupported until implemented by the PSP backend.

```java
AndroidApplicationConfig config = new AndroidApplicationConfig()
        .nativeTextEditorStyle(new AndroidTextEditorStyle()
                .panelBackgroundColor(0xFFF6F8FA)
                .editorBackgroundColor(0xFFFFFFFF)
                .editorTextColor(0xFF14181E)
                .actionButtonWidthDp(48.0f));
```

- Full IME composition is a backend/text-model extension point. Baseline text input handles committed text.
- Cursor lock, vibration, haptics, and advanced controller features must be capability-gated.
- Input events should be usable by both game code and `ui-kit`.
- `Input.as()` is the advanced access path for backend-specific input services.
- `Input.cursor()` returns `null` when the platform has no cursor concept.
- `Input.gamepads()` returns `null` when no gamepad provider is available.
- Cursor capture or shape changes should fail clearly when `InputCapabilities.supportsCursor()` is false or the requested cursor operation is unsupported.
- `Gamepads.find(int index)` returns `null` when no connected gamepad exists for that index.
- `Gamepads` is returned from `Input.gamepads()` and should not be registered as a separate `FdxService`.

## 9. Display

Module:

```text
:libfdx:runtime:display
```

Package:

```text
io.github.libfdx.display
```

Defined types:

| Type | Role |
| --- | --- |
| `Display` | Runtime presentation area abstraction. |
| `Displays` | Backend-owned display/window/canvas manager returned by `Fdx.displays()`. |
| `DisplayConfig` | Startup display configuration. |
| `DisplayMode` | Resolution, refresh rate, and fullscreen mode metadata. |
| `Monitor` | Physical or logical monitor/display metadata when available. |
| `Orientation` | Orientation value for mobile and rotation-aware platforms. |
| `DisplayCapabilities` | Supported display operations. |

### 9.1. Display Contract

`Display` is a runtime/platform presentation area: a desktop window, browser canvas, Android view, iOS view, or backend-owned presentation target. `Displays` owns the main display and optional creation of additional displays. Graphics contexts can create surfaces for displays, but display code itself must stay independent from `graphics/api`.

Defined shape:

```java
public interface Displays {
    Display main();
    boolean supportsMultiple();
    Display create(DisplayConfig config);
}

public interface Display extends ProviderHandle {
    int width();
    int height();
    int framebufferWidth();
    int framebufferHeight();
    float contentScaleX();
    float contentScaleY();
    float contentScale();

    String title();
    void title(String title);
    boolean closeRequested();
    void requestClose();
}

public interface DisplayCapabilities {
    boolean supportsTitle();
    boolean supportsFullscreen();
    boolean supportsResizable();
    boolean supportsDisplayModeChange();
    boolean supportsOrientation();
}
```

Example:

```java
Display display = fdx.displays().main();

if (display != null) {
    display.title("libfdx Game");
}
```

Rules:

- `Display` represents the platform presentation area: desktop window, browser canvas, Android view, iOS view, or headless placeholder.
- `Displays.main()` returns the backend-created main display, or `null` on headless backends.
- `Displays.create(DisplayConfig)` creates another display only when the backend and platform support it. Desktop backends may support this; mobile and web backends may return unsupported capability or fail clearly.
- `DisplayConfig.size(...)` is a requested startup size where the platform can honor it. Mobile backends should report the actual platform view/surface size and render to that size instead of stretching a fixed-size framebuffer to fill the device display.
- `DisplayConfig.maximized(...)` is a requested startup window state for windowed desktop-style platforms. If a backend cannot honor maximized startup, it should still report the actual display size after creation or resize.
- `Display.contentScaleX()`, `contentScaleY()`, and `contentScale()` expose the platform content scale for DPI/high-density presentation. Desktop backends should use platform window content scale, web backends should use browser device pixel ratio, mobile backends should use platform display density, and generic implementations may fall back to framebuffer-size-to-logical-size ratio.
- `Display` belongs to runtime and must not depend on `graphics/api`.
- `Surface` belongs to `graphics/api` and represents the connection between a `GraphicsContext` and a `Display`.
- `Display` implements `ProviderHandle` so provider/backend-specific display handles are available through `as()`.
- Fullscreen, icons, cursor capture, DPI, monitor metadata, and orientation should be added only through capability-aware APIs.
- `DisplayConfig` is owned by `runtime/display`. Concrete backend config classes may wrap or compose it through direct methods so launchers do not need generic config keys.

Boundary:

```text
runtime/display Display
  -> no dependency on graphics/api

graphics/api Surface
  -> may use a Display to create/configure a render target
```

## 10. Audio

Module:

```text
:libfdx:runtime:audio
```

Package:

```text
io.github.libfdx.audio
```

Defined types:

| Type | Role |
| --- | --- |
| `AudioDevice` | Main provider-backed audio service used by game code. |
| `AudioProvider` | Provider factory/SPI. |
| `AudioCapabilities` | Provider capabilities and limits. |
| `AudioFormat` | Channels, sample rate, and sample format. |
| `AudioBuffer` | Raw decoded PCM audio data or provider-backed buffer. |
| `Sound` | Short reusable sound effect asset/handle. |
| `Music` | Streaming or long-form audio asset/handle. |
| `PlaybackHandle` | Active playback control returned by play operations. |
| `AudioSource` | Advanced persistent playback source/channel. |
| `AudioConfig` | Startup audio configuration. |
| `AudioPlayOptions` | Volume, pan, pitch, looping, and priority options for playback. |
| `PlaybackState` | Playing, paused, stopped, completed, or failed state. |

Role separation:

- `Sound` is the normal high-level type for short sound effects.
- `Music` is the normal high-level type for streamed or long-form playback.
- `AudioBuffer` is lower-level decoded audio data.
- `PlaybackHandle` controls one active playback instance.
- `AudioSource` is the advanced persistent source/channel type for users who need to configure a reusable playback source before starting playback.

### 10.1. Audio Contracts

`AudioDevice` is the common service used by game code. Providers such as miniaudio or WebAudio implement it behind the same API.

Defined shape:

```java
public interface AudioDevice extends FdxService, ProviderHandle, Disposable {
    AudioCapabilities capabilities();

    PlaybackHandle play(Sound sound);
    PlaybackHandle play(Sound sound, AudioPlayOptions options);
    PlaybackHandle play(Music music);
    PlaybackHandle play(Music music, AudioPlayOptions options);

    AudioSource createSource();
    void pauseAll();
    void resumeAll();
    void stopAll();
}

public interface AudioProvider {
    ProviderId providerId();
    AudioDevice createDevice(AudioConfig config);
}

public interface AudioCapabilities {
    boolean supportsSound();
    boolean supportsMusic();
    boolean supportsStreaming();
    boolean supportsPan();
    boolean supportsPitch();
    boolean supportsLooping();
}

public interface AudioBuffer extends ProviderHandle, Disposable {
    AudioFormat format();
    int frameCount();
}

public interface Sound extends ProviderHandle, Disposable {
    AudioFormat format();
    float duration();
}

public interface Music extends ProviderHandle, Disposable {
    AudioFormat format();
    float duration();
    boolean isStreaming();
}

public interface PlaybackHandle extends ProviderHandle, Disposable {
    PlaybackState state();
    void pause();
    void resume();
    void stop();
    void volume(float volume);
    void pan(float pan);
    void pitch(float pitch);
    void looping(boolean looping);
}

public interface AudioSource extends ProviderHandle, Disposable {
    void setSound(Sound sound);
    void setMusic(Music music);
    PlaybackHandle play(AudioPlayOptions options);
    void stop();
}

public final class AudioPlayOptions {
    public static AudioPlayOptions defaults();
    public static AudioPlayOptions volume(float volume);

    public float volume();
    public float pan();
    public float pitch();
    public boolean looping();
}
```

Example:

```java
AudioDevice audio = fdx.audio();
Sound click = assets.get("click.wav", Sound.class);
PlaybackHandle playback = audio.play(click, AudioPlayOptions.volume(0.5f));
```

Rules:

- Audio provider selection is a startup decision for portable applications.
- Basic game code should use `AudioDevice`, `Sound`, `Music`, and `PlaybackHandle`.
- Provider-specific device handles and native details should be available only through provider-specific types or `as()`.
- Spatial audio, capture/microphone, device hotplug, and advanced mixing should be capability-gated or added as separate modules later.
- `AudioProvider` is a provider factory/SPI used by backend setup and should not be registered as a normal `FdxService`.
- `AudioConfig` is owned by `runtime/audio`. Concrete backend or audio-provider config classes should expose direct methods for audio startup values instead of using a generic config map.

Basic usage:

```java
AudioDevice audio = fdx.audio();
Sound click = assets.get("click.wav", Sound.class);
PlaybackHandle playback = audio.play(click);
playback.volume(0.5f);
```

## 11. Net

Module:

```text
:libfdx:runtime:net
```

Packages:

```text
io.github.libfdx.net
io.github.libfdx.net.http
io.github.libfdx.net.websocket
io.github.libfdx.net.buffer
io.github.libfdx.net.packet
io.github.libfdx.net.codec
io.github.libfdx.net.transform
io.github.libfdx.net.config
io.github.libfdx.net.transport
io.github.libfdx.net.processing
io.github.libfdx.net.spi
```

`io.github.libfdx.net` is the small service entry package. Focused subpackages own HTTP,
WebSocket, reusable packet storage, packet dispatch, manual message codecs, packet transforms,
endpoint configuration, multiplayer transports, processing helpers, and provider/backend SPI.

Defined types:

| Type | Role |
| --- | --- |
| `Network`, `NetworkCapabilities` | Main network service and supported network feature query. |
| `http.HttpClient`, `http.HttpRequest`, `http.HttpResponse`, `http.HttpMethod`, `http.HttpHeaders`, `http.HttpBody`, `http.HttpStatus` | HTTP entry point, descriptors, response data, headers, bodies, and status helpers. |
| `websocket.WebSocketClient`, `websocket.WebSocketConfig`, `websocket.WebSocket`, `websocket.WebSocketListener` | WebSocket connection entry point, config, active socket, and listener contract. |
| `buffer.NetBuffer`, `buffer.NetBufferPool`, `buffer.NetBufferPoolConfig`, `buffer.NetReader`, `buffer.NetWriter` | Reusable packet byte storage, pool configuration, and primitive packet readers/writers. |
| `packet.NetPacket`, `packet.NetPacketQueue`, `packet.NetPacketHandler` | Reusable packet view, inbound queue, and queue dispatch handler. |
| `codec.NetMessageCodec<T>` | Manual reusable message serialization contract. |
| `transform.NetPacketTransform`, `transform.NetTransformContext`, `transform.NetTransformResult` | User-provided packet transform hook for encryption, compression, authentication, or other byte transforms. |
| `config.NetClientConfig`, `config.NetServerConfig`, `config.NetPeerConfig`, `config.NetEndpointConfig`, `config.NetChannelConfig`, `config.NetProcessingConfig` | Provider-typed endpoint, channel, buffer, transform, and processing configuration base types. |
| `processing.NetProcessingState` | Fixed-rate processing helper used by reusable packet queues and providers. |
| `transport.NetTransports`, `transport.NetTransportProvider` | Factory and provider SPI for transport families such as WebRTC, TCP, UDP, Bluetooth, or in-memory test transports. |
| `transport.NetClient`, `transport.NetServer`, `transport.NetPeerGroup`, `transport.NetConnection` | Connected multiplayer endpoints and common connection handle. |
| `transport.NetDelivery`, `transport.NetSendResult`, `transport.NetConnectionState`, `transport.NetStats`, `transport.NetClientListener`, `transport.NetServerListener`, `transport.NetPeerListener` | Transport delivery values, send results, state, counters, and callbacks. |
| `spi.NetworkProvider`, `spi.DefaultNetwork`, `spi.DefaultNetworkCapabilities`, `spi.DefaultNetTransports` | Provider/backend setup SPI and reusable default implementations. |

### 11.1. Network Contracts

`Network` is async-first for request/response and socket APIs. Multiplayer transports are process-driven: native, WebRTC, socket, Bluetooth, or test-provider threads may enqueue inbound data, but normal user callbacks dispatch only when the endpoint's `process(float deltaTime)` method is called from the application thread.

Defined shape:

```java
public interface Network extends FdxService, ProviderHandle {
    NetworkCapabilities capabilities();
    HttpClient httpClient();
    WebSocketClient webSocketClient();
    NetTransports transports();
}

public interface NetworkProvider {
    ProviderId providerId();
    Network createNetwork();
}

public interface NetworkCapabilities {
    boolean supportsHttp();
    boolean supportsWebSocket();
    boolean supportsTransports();
    boolean supportsTransport(ProviderId providerId);
}

public interface HttpClient {
    FdxFuture<HttpResponse> send(HttpRequest request);
}

public interface HttpRequest {
    static HttpRequest get(String url);
    static HttpRequest post(String url, HttpBody body);

    HttpMethod method();
    String url();
    HttpHeaders headers();
    HttpBody body();
}

public interface HttpResponse {
    HttpStatus status();
    HttpHeaders headers();
    HttpBody body();
}

public interface HttpStatus {
    int code();
    String reason();
    boolean isSuccess();
}

public interface HttpHeaders {
    String first(String name);
    List<String> all(String name);
}

public interface HttpBody {
    byte[] bytes();
    String text(Charset charset);
}

public interface WebSocketClient {
    FdxFuture<WebSocket> connect(WebSocketConfig config, WebSocketListener listener);
}

public interface WebSocket extends ProviderHandle, Disposable {
    boolean isOpen();
    FdxFuture<Void> sendText(String text);
    FdxFuture<Void> sendBinary(byte[] bytes);
    FdxFuture<Void> close(int code, String reason);
}

public interface WebSocketListener {
    void opened(WebSocket socket);
    void text(WebSocket socket, String message);
    void binary(WebSocket socket, byte[] message);
    void error(WebSocket socket, Throwable error);
    void closed(WebSocket socket, int code, String reason);
}

public interface NetTransports {
    boolean supports(ProviderId providerId);
    NetClient connect(NetClientConfig config, NetClientListener listener);
    NetServer listen(NetServerConfig config, NetServerListener listener);
    NetPeerGroup join(NetPeerConfig config, NetPeerListener listener);
}

public interface NetClient extends ProviderHandle, Disposable {
    void process(float deltaTime);
    NetConnection connection();
    boolean isConnected();
    NetBufferPool buffers();
    NetStats stats();
}

public interface NetServer extends ProviderHandle, Disposable {
    void process(float deltaTime);
    NetBufferPool buffers();
    NetStats stats();
    int connectionCount();
    NetConnection connectionAt(int index);
    NetSendResult broadcast(int channelId, NetBuffer buffer);
}

public interface NetPeerGroup extends ProviderHandle, Disposable {
    void process(float deltaTime);
    NetBufferPool buffers();
    NetStats stats();
    int peerCount();
    NetConnection peerAt(int index);
}

public interface NetConnection extends ProviderHandle, Disposable {
    int id();
    NetConnectionState state();
    NetSendResult send(int channelId, NetBuffer buffer);
    NetSendResult send(int channelId, byte[] bytes, int offset, int length);
    void setTransform(int channelId, NetPacketTransform transform);
    void close();
}

public interface NetPacketHandler {
    void message(NetConnection connection, NetPacket packet);
}

public interface NetPacketTransform {
    int maxOutputBytes(int inputBytes);
    NetTransformResult encode(NetTransformContext context, NetReader input, NetWriter output);
    NetTransformResult decode(NetTransformContext context, NetReader input, NetWriter output);
}

public interface NetMessageCodec<T> {
    void write(T message, NetWriter out);
    void read(NetReader in, T target);
}
```

Example:

```java
Network network = fdx.network();
if (network != null) {
    HttpClient http = network.httpClient();
    if (http != null) {
        http.send(HttpRequest.get("https://example.com/status"))
            .onSuccess(response -> logger.info("Status: " + response.status().code()));
    }
}
```

Multiplayer example:

```java
Network network = fdx.network();
NetTransports transports = network != null ? network.transports() : null;

if (transports != null && transports.supports(WebRtcProvider.ID)) {
    WebRtcClientConfig config = WebRtcClientConfig.builder()
        .signalingUrl("wss://example.com/signaling")
        .roomId("lobby-1")
        .buffers(NetBufferPoolConfig.builder()
            .initialPackets(256)
            .maxPackets(1024)
            .packetBytes(1400)
            .build())
        .processing(NetProcessingConfig.builder()
            .tickRate(30)
            .maxTicksPerFrame(2)
            .maxReceivePacketsPerTick(64)
            .maxReceiveBytesPerTick(64 * 1024)
            .dropUnreliableWhenBehind(true)
            .build())
        .build();

    NetClient client = transports.connect(config, listener);
    client.process(fdx.app().deltaTime());

    NetBuffer packet = client.buffers().acquire();
    packet.writer().putByte(MSG_INPUT).putFloat(x).putFloat(y);
    client.connection().send(0, packet);
}
```

Rules:

- Network APIs should be async-first.
- Do not design network APIs around blocking calls because browser/web targets cannot support that reliably.
- HTTP redirects, cookies, TLS details, streaming bodies, and custom transports must be capability-aware.
- WebSocket lifecycle should clearly define open, message, error, close, and dispose behavior.
- Backend-specific transport details should not leak into common request/response types.
- `Network.as()` is the advanced access path for backend/provider-specific network services.
- `Fdx.network()` returns `null` when the backend has no networking implementation.
- `Network.httpClient()` returns `null` when HTTP is not supported by the active backend/provider.
- `Network.webSocketClient()` returns `null` when WebSocket is not supported by the active backend/provider.
- `Network.transports()` returns `null` when multiplayer transports are not supported by the active backend/provider.
- Use `NetworkCapabilities` or `NetTransports.supports(ProviderId)` before assuming a platform supports HTTP, WebSocket, WebRTC, TCP, UDP, Bluetooth, or another transport.
- Provider configs stay typed. WebRTC uses `WebRtcClientConfig`, `WebRtcServerConfig`, and `WebRtcPeerConfig`; future Bluetooth, TCP, UDP, or platform-specific transports should add their own typed config classes.
- Multiplayer callbacks must not fire directly from socket, WebRTC, Bluetooth, native, or worker threads. Providers enqueue work and dispatch `NetClientListener`, `NetServerListener`, and `NetPeerListener` callbacks only during `process(float deltaTime)`.
- Connected endpoints should be processed explicitly, normally once from the game frame:

```java
client.process(fdx.app().deltaTime());
server.process(fdx.app().deltaTime());
```

- `NetProcessingConfig` controls tick rate, maximum ticks per frame, receive packet/byte limits, send packet limits, and whether unreliable packets may be dropped when the endpoint falls behind.
- `NetBufferPoolConfig` controls reusable packet storage. Sending through `NetBuffer` should avoid per-frame packet allocation after the configured pool has been created.
- `NetPacketQueue` is the common reusable inbound queue helper for providers. It stores queued packets in `NetBufferPool` buffers, dispatches through `NetPacketHandler` only during `dispatch(float, NetPacketHandler)`, applies `NetProcessingConfig` tick/receive limits, reports reliable backpressure, and counts unreliable drops.
- Received `NetPacket` views are valid during the callback. User code must explicitly retain the backing `NetBuffer` when packet data needs to outlive the callback.
- Reliable transports should report backpressure through `NetSendResult` instead of allocating unbounded queues. Unreliable transports may drop packets according to processing limits and provider/channel configuration.
- Packet transforms are user-provided byte hooks. libFDX provides the `NetPacketTransform` contract but does not implement encryption, compression, or authentication algorithms.
- Transforms may be configured at endpoint, channel, or connection/channel override boundaries. Providers apply transform changes at processing boundaries.
- V1 serialization is manual and allocation-conscious through `NetMessageCodec<T>`, where `read(NetReader, T target)` fills a reusable target object. Automatic no-reflection serialization may be added later through code generation that emits `NetMessageCodec` implementations.
- `HttpRequest.body()` returns `null` for requests without a body.
- `HttpResponse.body()` returns `null` for responses without a body.
- `HttpHeaders.first(String name)` returns `null` when the header is not present.
- `spi.NetworkProvider` is a provider/backend SPI used by backend setup and should not be registered as a normal `FdxService`.
- `transport.NetTransportProvider` is provider SPI used by backend setup and should not be registered as a normal `FdxService`.

Async shape:

```java
HttpClient http = network.httpClient();
WebSocketClient webSocket = network.webSocketClient();
NetTransports transports = network.transports();

if (http != null) {
    FdxFuture<HttpResponse> response = http.send(request);
}

if (webSocket != null) {
    FdxFuture<WebSocket> socket = webSocket.connect(config, listener);
}

if (transports != null && transports.supports(WebRtcProvider.ID)) {
    NetClient client = transports.connect(webRtcConfig, listener);
}
```

Use `FdxFuture<T>` consistently across net, assets, and other common async APIs.

WebRTC provider-specific types live under `:libfdx:extensions:net:webrtc:core`. `WebRtcProvider.ID` remains in `io.github.libfdx.net.webrtc`; endpoint configs live in `io.github.libfdx.net.webrtc.config`; signaling contracts live in `io.github.libfdx.net.webrtc.signaling`; platform bridge interfaces live in `io.github.libfdx.net.webrtc.platform`; transport implementations live in `io.github.libfdx.net.webrtc.transport`. `WebRtcClientConfig`, `WebRtcServerConfig`, and `WebRtcPeerConfig` map reliable and unreliable common channels to WebRTC data channels internally; common game code still uses `NetConnection` and integer channel ids.

WebRTC provider-defined types:

| Type | Role |
| --- | --- |
| `transport.WebRtcNetworkProvider` | Creates a `Network` service with WebRTC transports installed. |
| `transport.WebRtcNetTransportProvider` | Installs `WebRtcNetClient`, `WebRtcNetServer`, and `WebRtcNetPeerGroup` behind `NetTransports`. |
| `transport.WebRtcNetClient`, `transport.WebRtcNetServer`, `transport.WebRtcNetPeerGroup`, `transport.WebRtcNetConnection` | WebRTC-backed implementations of the provider-neutral net endpoint contracts. |
| `signaling.WebRtcSignalingClient`, `signaling.WebRtcSignalingListener`, `signaling.WebRtcSignalingMessage`, `signaling.WebRtcSignalingMessageType`, `signaling.WebRtcSignalingCodec` | JSON signaling contracts for `welcome`, `peer_joined`, `peer_left`, `offer`, `answer`, `ice`, `connect_request`, `error`, `ping`, and `pong`. |
| `signaling.WebRtcRoomPolicy` | Room join policy hook used by signaling servers. |
| `platform.WebRtcPeerConnectionProvider`, `platform.WebRtcPeerConnection`, `platform.WebRtcDataChannel`, `platform.WebRtcIceCandidate`, `platform.WebRtcSessionDescription`, `platform.WebRtcPlatformFactory` | Provider-neutral bridge interfaces implemented by desktop, web, Android, and future platform binding modules. `WebRtcPlatformFactory` is disposable because it owns native/provider peer-connection resources. |

The WebRTC signaling server lives under `:libfdx:extensions:net:webrtc:signaling_server` in package `io.github.libfdx.net.webrtc.signaling.server`. It uses Java-WebSocket for peer discovery and SDP/ICE relay only; game data travels through WebRTC data channels. It is a standalone infrastructure process, not a sample client feature and not a gameplay/TCP server. `WebRtcSignalingServerConfig` controls bind host, port, auth hook, room policy, join policy, message policy, peer ID generation, processing config, maximum peers per room, idle timeout, and logging. The repository run task is `:libfdx:extensions:net:webrtc:signaling_server:webrtc_signaling_server_run`; clients connect through endpoint configs such as `WebRtcClientConfig.signalingUrl(...)` and `WebRtcServerConfig.signalingUrl(...)`. V1 supports external STUN/TURN configuration through endpoint configs and does not embed a TURN server.

Signaling server work is process-driven like the multiplayer endpoint API. Java-WebSocket callbacks enqueue reusable internal event objects only; auth, join policy, message policy, room mutation, SDP/ICE relay, directory updates, and disconnect cleanup run during `WebRtcSignalingServer.process(float deltaTime)`. `WebRtcSignalingProcessingConfig` controls tick rate, max ticks per frame, events per tick, bytes per tick, initial preallocated events, and max queued events. Embedded backend tools should call `process(...)` from their backend loop. The standalone launcher runs this loop internally, so the Gradle `webrtc_signaling_server_run` task remains a one-command server.

Custom signaling deployments may extend access control without changing the WebRTC clients. `WebRtcSignalingJoinRequest` exposes the requested room/peer, token query value, resource path, parsed query values, request headers, and remote address. Simple auth can still use `WebRtcSignalingAuth.allow(...)`; protected servers can override `WebRtcSignalingAuth.authenticate(...)` and return `WebRtcSignalingAuthResult.accepted(session)` to attach application-owned session metadata. `WebRtcSignalingJoinPolicy` can reject a specific join after peer ID generation, and `WebRtcSignalingMessagePolicy` can reject individual signaling messages such as room registration or SDP relay based on the session, room, source peer, target peer, and peer count. libFDX does not implement or prescribe token validation, login, encryption, or authorization storage; applications supply those policies.

Platform setup entry points:

```java
NetworkProvider desktopProvider = DesktopWebRtcPlatform.networkProvider();
NetworkProvider webProvider = WebWebRtcPlatform.networkProvider();
NetworkProvider androidProvider = AndroidWebRtcPlatform.networkProvider(androidContext);
```

Backends may install the returned provider into the typed `Fdx` root setup path. User code should still obtain WebRTC through `Fdx.network().transports()` and typed `WebRtc*Config` objects rather than static global factories.

## 12. Assets

Modules:

```text
:libfdx:assets:manager
:libfdx:assets:loaders
```

Packages:

```text
io.github.libfdx.assets
io.github.libfdx.assets.loaders
```

Defined manager types:

| Type | Role |
| --- | --- |
| `AssetManager` | Load, cache, retrieve, update, and dispose assets. |
| `AssetDescriptor<T>` | Asset path, Java type, and loader options. |
| `AssetHandle<T>` | Typed handle/reference to a loaded or loading asset. |
| `AssetLoader<T>` | Loader contract implemented by format loaders. |
| `AssetLoadContext` | Loader context for file access, asset dependencies, and application-thread completion. |
| `AssetStatus` | Loading state if handles expose state. |

Defined loader-facing types:

| Type | Role |
| --- | --- |
| `ImageData` | Provider-neutral decoded image data before GPU upload. |
| `ImageAssetLoader` | Default provider-neutral PNG/JPG image loader that produces `ImageData`. |
| `JsonAssetLoader` | Default provider-neutral JSON asset loader that produces `JsonValue`. |

### 12.1. Asset Contracts

`AssetManager` coordinates loading, caching, dependency loading, and disposal. `AssetLoader<T>` owns the logic for one asset type or format.

Defined shape:

```java
public interface AssetManager extends Disposable {
    <T> AssetHandle<T> load(AssetDescriptor<T> descriptor);
    <T> FdxFuture<T> loadAsync(AssetDescriptor<T> descriptor);
    boolean update();
    void finishLoading();
    <T> T get(String path, Class<T> type);
    <T> T find(String path, Class<T> type);
    void unload(String path);
    void registerLoader(Class<?> type, AssetLoader<?> loader);
}

public final class AssetDescriptor<T> {
    static <T> AssetDescriptor<T> of(String path, Class<T> type);
    static <T> AssetDescriptor<T> of(String path, Class<T> type, Map<String, Object> options);

    String path();
    Class<T> type();
    Map<String, Object> options();
}

public interface AssetHandle<T> {
    AssetDescriptor<T> descriptor();
    AssetStatus status();
    boolean isLoaded();
    T asset();
    FdxFuture<T> future();
}

public interface AssetLoader<T> {
    Class<T> type();
    FdxFuture<T> load(AssetLoadContext context, AssetDescriptor<T> descriptor);
}

public interface AssetLoadContext {
    FileSystem files();
    <T> FdxFuture<T> dependency(AssetDescriptor<T> descriptor);
    <T> FdxFuture<T> completeOnUpdate(Callable<T> task);
}
```

Example with an optional graphics-aware texture loader installed:

```java
AssetManager assets = new DefaultAssetManager(fdx.files());
GraphicsContext graphics = fdx.graphics().main();
G2DAssetLoaders.register(assets, graphics);

assets.load(AssetDescriptor.of("player.png", Texture.class));
assets.finishLoading();

Texture texture = assets.get("player.png", Texture.class);
TextureRegion[][] playerFrames = TextureRegion.split(texture, 256, 256);
```

Rules:

- Base asset loaders should not force a graphics provider.
- `AssetManager` is user-created or framework-feature code, not a backend-owned service returned by `Fdx`.
- `AssetLoadContext` exposes file and asset-dependency loading support, not the root `Fdx` object.
- `ImageData` is asset/source data. `Texture` is a GPU resource owned by `graphics/api`.
- Future audio source data should stay provider-neutral. `Sound` and `Music` are provider-backed audio handles owned by `runtime/audio` and the selected audio provider.
- `assets/loaders` may provide provider-neutral loaders such as image, JSON, properties, atlas metadata, font metadata, shader-source, and audio-source data. JSON loading produces the `JsonValue` tree owned by `foundation/json`.
- `assets/loaders` must not create provider-backed `Texture`, `Sound`, or `Music` objects directly.
- Graphics-aware loaders for `Texture`, `TextureRegion`, bitmap fonts, atlases, models, or other GPU-backed assets should live in a high-level module or explicit bridge that already depends on both `assets/manager` and the relevant graphics module.
- Audio-aware loaders for `Sound` and `Music` should live in the selected audio provider module or an explicit audio asset bridge that depends on both `assets/manager` and `runtime/audio`.
- Examples that load `Texture`, `Sound`, or `Music` through `AssetManager` assume the corresponding optional loader has been registered during startup.
- Asset loading should support async implementations.
- `AssetManager.update()` runs completion work that must happen on the application/update thread, such as GPU texture creation after image decode.
- `AssetManager.finishLoading()` repeatedly calls `update()` until currently requested assets are no longer queued or loading.
- Loaders that need application-thread completion should use `AssetLoadContext.completeOnUpdate(...)`.
- Asset disposal must respect ownership. The manager should only dispose assets it owns.
- `AssetManager.get(String, Class<T>)` returns the asset or fails clearly when it is not loaded or does not match the requested type.
- `AssetManager.find(String, Class<T>)` returns `null` when the asset is not loaded or does not match the requested type.
- `AssetHandle.asset()` returns `null` until the asset is loaded successfully.

## 13. Graphics API

Module:

```text
:libfdx:graphics:api
```

Package:

```text
io.github.libfdx.graphics
```

The graphics API is a low-level provider-neutral rendering API. It should be modern enough for WebGPU/wgpu and Vulkan while not leaking either provider's native object model.

Defined root, context, and setup types:

| Type | Role |
| --- | --- |
| `Graphics` | Graphics manager/factory returned by `Fdx.graphics()`. It owns the main graphics context and optional creation of additional contexts. |
| `GraphicsContext` | Provider-backed rendering context and device entry point used by game and rendering code. |
| `GraphicsConfig` | Configuration for creating an additional graphics context when supported. |
| `GraphicsAttachment` | Backend-driven graphics context that owns frame begin/end, resize, and presentation lifecycle for a display-backed context. |
| `GraphicsAttachmentReadiness` | Optional backend/provider marker for attachments that finish initialization asynchronously before game code can create graphics resources. |
| `GraphicsAttachmentProvider` | Launcher/backend setup factory for attaching a selected graphics provider to a backend-created display/native target. |
| `GraphicsAttachmentRequirements` | Provider-declared window/context requirements that the backend must apply before creating the presentation target. |
| `GraphicsEnvironment` | Provider-neutral setup view passed from a backend to a `GraphicsAttachmentProvider`. |
| `NativeWindow` | Backend-created native presentation handle/object bundle used only by provider setup code. |
| `NativeWindowPlatform` | Platform identifier for `NativeWindow` handle interpretation. |
| `GraphicsDevice` | Provider-backed device interface used by common code to create first rendering resources. |
| `GraphicsFrame` | Current backend-owned frame view exposed during `ApplicationListener.render()`. |
| `FrameBuffer` | Current frame drawable and readback view. |
| `ImmediateModeRenderer` | Provider-neutral immediate-style renderer for simple 2D and 3D diagnostic lines. |

`Fdx.graphics()` returns the graphics manager, not "the one active graphics API". Simple apps use `fdx.graphics().main()`. Advanced desktop apps can ask the manager to create another context, then attach that context to a display/surface when the backend supports it. Provider-specific frame plumbing such as surface acquisition, native command encoder creation, submission, and presentation is owned by the backend/provider attachment. Common game code should use `GraphicsContext`, not `WGPUContext`, Vulkan objects, or backend-native window handles.

Initial shape:

```java
public interface Graphics {
    GraphicsContext main();
    boolean supportsMultiple();
    GraphicsContext create(GraphicsConfig config);
}

public final class GraphicsConfig {
    public static GraphicsConfig provider(GraphicsAttachmentProvider provider);
    public GraphicsConfig display(Display display);
    public Display display();
    public GraphicsAttachmentProvider provider();
}

public interface GraphicsContext extends ProviderHandle {
    GraphicsDevice device();
    TextureFormat surfaceFormat();
    GraphicsFrame currentFrame();
    void clear(float red, float green, float blue, float alpha);
}

public interface GraphicsAttachment extends GraphicsContext, Disposable {
    void resize(int framebufferWidth, int framebufferHeight);
    void processEvents();
    boolean beginFrame();
    void endFrame();
}

public interface GraphicsAttachmentReadiness {
    boolean isReady();
}

public interface GraphicsDevice extends ProviderHandle {
    Buffer createBuffer(BufferDescriptor descriptor);
    void writeBuffer(Buffer buffer, ByteBuffer data);
    Texture createTexture(TextureDescriptor descriptor);
    void writeTexture(Texture texture, ByteBuffer data);
    ShaderModule createShaderModule(ShaderModuleDescriptor descriptor);
    RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor);
}

public interface GraphicsFrame extends ProviderHandle {
    CommandEncoder commandEncoder();
    FrameBuffer frameBuffer();
    TextureView colorAttachment();
    int width();
    int height();
}

public interface FrameBuffer extends ProviderHandle {
    TextureView colorAttachment();
    TextureFormat format();
    int width();
    int height();
    ByteBuffer readPixelsRgba8();
}

public final class ImmediateModeRenderer implements Disposable {
    public ImmediateModeRenderer(GraphicsContext graphics);
    public void clear();
    public void clear2D();
    public void clear3D();
    public void line2D(float x1, float y1, float x2, float y2,
            float red, float green, float blue, float alpha);
    public void line3D(float x1, float y1, float z1, float x2, float y2, float z2,
            float red, float green, float blue, float alpha);
    public void render2D();
    public void render2D(int x, int y, int width, int height);
    public void render3D(float[] viewProjection);
    public void render3D(float[] viewProjection, int x, int y, int width, int height);
    public void dispose();
}

public interface GraphicsAttachmentProvider {
    ProviderId providerId();
    GraphicsAttachmentRequirements requirements();
    GraphicsAttachment create(GraphicsEnvironment environment);
}

public interface GraphicsEnvironment {
    Display display();
    NativeWindow nativeWindow();
}

public final class NativeWindow {
    public static NativeWindow windows(long backendHandle, long windowHandle);
    public static NativeWindow x11(long backendHandle, long displayHandle, long windowHandle);
    public static NativeWindow wayland(long backendHandle, long displayHandle, long windowHandle);
    public static NativeWindow macos(long backendHandle, long windowHandle);
    public static NativeWindow glfw(long windowHandle);
    public static NativeWindow android(Object surface);
    public static NativeWindow web(Object canvas);
    NativeWindowPlatform platform();
    long backendHandle();
    long displayHandle();
    long windowHandle();
    Object objectHandle();
}

public enum NativeWindowPlatform {
    WINDOWS,
    X11,
    WAYLAND,
    MACOS,
    GLFW,
    ANDROID,
    WEB
}

public final class GraphicsAttachmentRequirements {
    public static GraphicsAttachmentRequirements noApi();
    public static GraphicsAttachmentRequirements openGL(int majorVersion, int minorVersion,
        GraphicsContextProfile profile, boolean forwardCompatible);
    public static GraphicsAttachmentRequirements vulkan();
    GraphicsClientApi clientApi();
    int majorVersion();
    int minorVersion();
    GraphicsContextProfile profile();
    boolean forwardCompatible();
}
```

Rules:

- `Graphics` is returned by `Fdx.graphics()` and acts as a manager/factory for graphics contexts.
- `Graphics.main()` returns the backend-created main graphics context, or `null` on a headless backend without graphics.
- `Graphics.create(GraphicsConfig)` is an advanced capability. Desktop backends may support additional contexts/providers; mobile and web backends may reject it clearly.
- `GraphicsConfig.display(...)` binds an additional on-window context to the display it should render into. There is no hidden current display.
- `GraphicsContext.as()` is the advanced provider-specific escape hatch.
- `GraphicsContext.device()` returns a common device interface backed by the selected provider.
- `GraphicsContext.surfaceFormat()` returns the current presentation color format used for render pipeline creation.
- `ImmediateModeRenderer` belongs to `graphics/api`. It queues colored line-list vertices on the CPU, supports normalized-device-coordinate 2D lines and matrix-projected 3D lines, and uses depth testing only for the 3D render path.
- `GraphicsContext.currentFrame()` is valid only during a backend-owned frame, normally inside `ApplicationListener.render()`.
- Resources are owned by one `GraphicsContext`. A texture created by a GL context is not automatically usable by a Vulkan context.
- Shared game modules should depend on `graphics/api`, not on `extensions/graphics/<provider>`.
- Backends must read `GraphicsAttachmentProvider.requirements()` before creating the window or canvas. WGPU normally requests `NO_API`; desktop GL requests a GL context; Vulkan requests `VULKAN`, which desktop backends usually realize as a no-client-API native window plus Vulkan support checks.
- Backends that receive a `GraphicsAttachmentReadiness` attachment must wait for `isReady()` before calling `ApplicationListener.create(...)`.
- Backend/provider code owns frame begin/end and presentation. Normal game code should not call provider-specific frame lifecycle methods such as wgpu surface acquisition directly.
- Backends create `NativeWindow` values from their own platform technology and pass them through `GraphicsEnvironment`.
- Graphics providers consume `GraphicsEnvironment` and must not depend on concrete backend modules just to create a surface.
- `GraphicsAttachmentProvider` is launcher/backend setup SPI, not a context service.
- `NativeWindow` is not a portable gameplay API. It may contain platform native handles or platform objects, such as an Android `Surface`, and should stay inside backend/provider setup code.
- Provider-specific graphics configuration should be stored on the provider setup object itself, not looked up through `GraphicsEnvironment`.
- `FrameBuffer` is the provider-neutral current drawable view. GL implementations may use the default framebuffer, Vulkan implementations may use the current swapchain image, and WGPU implementations may use the acquired surface texture.
- Multi-render targets should be exposed as an ordered color attachment list with one optional depth/stencil attachment. The public API should not expose provider-specific subpass, layout transition, or framebuffer handle details.
- Camera state belongs to `graphics/camera`; `graphics/api` must stay independent from camera input/controller concerns.

### 13.3.1. Graphics Camera

Module:

```text
:libfdx:graphics:camera
```

Packages:

```text
io.github.libfdx.graphics.camera
io.github.libfdx.graphics.camera.controller
```

`graphics/camera` contains shared camera state in `io.github.libfdx.graphics.camera` and input-backed camera controllers in `io.github.libfdx.graphics.camera.controller`. It depends on `runtime/input` for controllers; renderer helpers belong to `graphics/api`.

Defined shape:

```java
public enum CameraProjection {
    ORTHOGRAPHIC,
    PERSPECTIVE
}

public final class Camera {
    public Camera projection(CameraProjection projection);
    public CameraProjection projection();
    public Camera viewport(float width, float height);
    public Camera fieldOfView(float fieldOfViewDegrees);
    public Camera nearFar(float near, float far);
    public Camera zoom(float zoom);
    public Camera position(float x, float y, float z);
    public Camera direction(float x, float y, float z);
    public Camera lookAt(float x, float y, float z);
    public Camera up(float x, float y, float z);
    public Camera update();
    public Vector3 position();
    public Vector3 direction();
    public Vector3 up();
    public Matrix4 projectionMatrix();
    public Matrix4 view();
    public Matrix4 combined();
    public float near();
    public float far();
}

public interface CameraAnchor2D {
    void position(Vector2 out);
}

public interface CameraAnchor3D {
    void position(Vector3 out);
    void up(Vector3 out);
}

public interface CameraPointerRegion {
    boolean contains(int x, int y);
}

public final class CameraInputBindings3D {
    public static CameraInputBindings3D defaults();
    public CameraInputBindings3D forwardKey(Key key);
    public CameraInputBindings3D backwardKey(Key key);
    public CameraInputBindings3D leftKey(Key key);
    public CameraInputBindings3D rightKey(Key key);
    public CameraInputBindings3D upKey(Key key);
    public CameraInputBindings3D downKey(Key key);
    public CameraInputBindings3D alternateForwardKey(Key key);
    public CameraInputBindings3D alternateBackwardKey(Key key);
    public CameraInputBindings3D alternateLeftKey(Key key);
    public CameraInputBindings3D alternateRightKey(Key key);
    public CameraInputBindings3D alternateUpKey(Key key);
    public CameraInputBindings3D alternateDownKey(Key key);
    public CameraInputBindings3D fastKey(Key key);
    public CameraInputBindings3D alternateFastKey(Key key);
    public CameraInputBindings3D boostKey(Key key);
    public CameraInputBindings3D alternateBoostKey(Key key);
    public CameraInputBindings3D lookButton(MouseButton button);
    public CameraInputBindings3D touchLookButton(MouseButton button);
}

public final class CinematicCameraPathSample3D {
    public CinematicCameraPathSample3D camera(float x, float y, float z);
    public CinematicCameraPathSample3D lookAt(float x, float y, float z);
    public CinematicCameraPathSample3D up(float x, float y, float z);
    public float cameraX();
    public float cameraY();
    public float cameraZ();
    public float lookAtX();
    public float lookAtY();
    public float lookAtZ();
    public float upX();
    public float upY();
    public float upZ();
}

public interface CinematicCameraPath3D {
    void sample(float timeSeconds, CinematicCameraPathSample3D out);
}

public final class KeyframeCinematicCameraPath3D implements CinematicCameraPath3D {
    public KeyframeCinematicCameraPath3D(float durationSeconds,
            float[] cameraPoints, float[] lookAtPoints);
    public KeyframeCinematicCameraPath3D(float durationSeconds,
            float[] cameraPoints, float[] lookAtPoints, float[] upPoints);
    public KeyframeCinematicCameraPath3D loop(boolean loop);
    public boolean loop();
    public float durationSeconds();
    public int pointCount();
    public void sample(float timeSeconds, CinematicCameraPathSample3D out);
}

public final class CameraController2D implements Disposable {
    public CameraController2D(Input input, Camera camera);
    public CameraController2D position(float x, float y);
    public CameraController2D pointerRegion(CameraPointerRegion pointerRegion);
    public CameraController2D activationListener(Runnable activationListener);
    public CameraController2D enabled(boolean enabled);
    public CameraController2D touchEnabled(boolean touchEnabled);
    public CameraController2D zoomRange(float minZoom, float maxZoom);
    public CameraController2D zoomSpeed(float zoomSpeed);
    public CameraController2D update(float deltaSeconds);
    public void dispose();

    public interface PointerRegion extends CameraPointerRegion {
        boolean contains(int x, int y);
    }
}

public class FreeCameraController3D implements Disposable {
    public FreeCameraController3D(Input input, Camera camera);
    public FreeCameraController3D position(float x, float y, float z);
    public FreeCameraController3D up(float x, float y, float z);
    public FreeCameraController3D speed(float speed);
    public float speed();
    public FreeCameraController3D speedRange(float minSpeed, float maxSpeed);
    public FreeCameraController3D scrollSpeedFactor(float scrollSpeedFactor);
    public FreeCameraController3D speedMultipliers(float fastMultiplier, float boostMultiplier);
    public FreeCameraController3D inputBindings(CameraInputBindings3D bindings);
    public FreeCameraController3D pointerRegion(CameraPointerRegion pointerRegion);
    public FreeCameraController3D activationListener(Runnable activationListener);
    public FreeCameraController3D enabled(boolean enabled);
    public FreeCameraController3D keyboardEnabled(boolean keyboardEnabled);
    public FreeCameraController3D touchEnabled(boolean touchEnabled);
    public FreeCameraController3D sensitivity(float sensitivityDegrees);
    public FreeCameraController3D invert(boolean invertX, boolean invertY);
    public FreeCameraController3D update(float deltaSeconds);
}

public class FirstPersonCameraController3D implements Disposable {
    public FirstPersonCameraController3D(Input input, Camera camera, CameraAnchor3D anchor);
    public FirstPersonCameraController3D anchor(CameraAnchor3D anchor);
    public FirstPersonCameraController3D eyeOffset(float right, float up, float forward);
    public FirstPersonCameraController3D inputBindings(CameraInputBindings3D bindings);
    public FirstPersonCameraController3D pointerRegion(CameraPointerRegion pointerRegion);
    public FirstPersonCameraController3D activationListener(Runnable activationListener);
    public FirstPersonCameraController3D enabled(boolean enabled);
    public FirstPersonCameraController3D touchEnabled(boolean touchEnabled);
    public FirstPersonCameraController3D sensitivity(float sensitivityDegrees);
    public FirstPersonCameraController3D invert(boolean invertX, boolean invertY);
    public FirstPersonCameraController3D update(float deltaSeconds);
}

public class ThirdPersonCameraController3D implements Disposable {
    public ThirdPersonCameraController3D(Input input, Camera camera, CameraAnchor3D anchor);
    public ThirdPersonCameraController3D anchor(CameraAnchor3D anchor);
    public ThirdPersonCameraController3D distance(float distance);
    public ThirdPersonCameraController3D distanceRange(float minDistance, float maxDistance);
    public ThirdPersonCameraController3D offsets(float shoulderOffset, float heightOffset, float lookHeight);
    public ThirdPersonCameraController3D damping(float damping);
    public ThirdPersonCameraController3D scrollDistanceFactor(float scrollDistanceFactor);
    public ThirdPersonCameraController3D inputBindings(CameraInputBindings3D bindings);
    public ThirdPersonCameraController3D pointerRegion(CameraPointerRegion pointerRegion);
    public ThirdPersonCameraController3D activationListener(Runnable activationListener);
    public ThirdPersonCameraController3D enabled(boolean enabled);
    public ThirdPersonCameraController3D sensitivity(float sensitivityDegrees);
    public ThirdPersonCameraController3D update(float deltaSeconds);
}

public class OrbitCameraController3D implements Disposable {
    public OrbitCameraController3D(Input input, Camera camera);
    public OrbitCameraController3D target(float x, float y, float z);
    public OrbitCameraController3D position(float cameraX, float cameraY, float cameraZ,
            float targetX, float targetY, float targetZ);
    public OrbitCameraController3D radiusRange(float minRadius, float maxRadius);
    public OrbitCameraController3D radius(float radius);
    public OrbitCameraController3D inputBindings(CameraInputBindings3D bindings);
    public OrbitCameraController3D pointerRegion(CameraPointerRegion pointerRegion);
    public OrbitCameraController3D activationListener(Runnable activationListener);
    public OrbitCameraController3D enabled(boolean enabled);
    public OrbitCameraController3D sensitivity(float sensitivityDegrees);
    public OrbitCameraController3D keyboardEnabled(boolean keyboardEnabled);
    public OrbitCameraController3D autoOrbit(boolean enabled, float yawDegreesPerFrame,
            long frames, float startDegrees, float totalDegrees);
    public OrbitCameraController3D update(float deltaSeconds);
}

public class OrthographicCameraController3D implements Disposable {
    public OrthographicCameraController3D(Input input, Camera camera);
    public OrthographicCameraController3D position(float x, float y, float z);
    public OrthographicCameraController3D zoomRange(float minZoom, float maxZoom);
    public OrthographicCameraController3D zoomSpeed(float zoomSpeed);
    public OrthographicCameraController3D pointerRegion(CameraPointerRegion pointerRegion);
    public OrthographicCameraController3D enabled(boolean enabled);
    public OrthographicCameraController3D keyboardEnabled(boolean keyboardEnabled);
    public OrthographicCameraController3D update(float deltaSeconds);
}

public class CinematicCameraController {
    public CinematicCameraController(Camera camera);
    public CinematicCameraController anchor(CameraAnchor2D anchor);
    public CinematicCameraController anchor(CameraAnchor3D anchor);
    public CinematicCameraController path3D(CinematicCameraPath3D path);
    public CinematicCameraController pathTime(float timeSeconds);
    public CinematicCameraController pathPlaybackSpeed(float secondsPerSecond);
    public CinematicCameraController damping(float damping);
    public CinematicCameraController offset2D(float x, float y);
    public CinematicCameraController zoom(float zoom);
    public CinematicCameraController rotation(float radians);
    public CinematicCameraController orbit(float yawDegrees, float pitchDegrees, float distance);
    public CinematicCameraController rotate(float yawDegrees, float pitchDegrees);
    public CinematicCameraController offsets3D(float shoulderOffset, float heightOffset, float lookHeight);
    public CinematicCameraController update(float deltaSeconds);
}

```

Rules:

- Camera controllers are user-created helpers, not `Fdx` root services.
- `Camera` is one mutable graphics camera type. It must not split into separate 2D, 3D, orthographic, or perspective subclasses. A camera changes mode through `projection(CameraProjection)` and keeps the same instance identity.
- Camera controllers manipulate a caller-owned `graphics/camera` `Camera`; they do not own or replace it.
- Camera controllers register input processors when constructed and must be disposed or disabled when no longer active.
- `CameraController2D` is for orthographic 2D pan and zoom.
- `FreeCameraController3D` moves a camera directly and is intended for editor, debug, sample, and test fly cameras. Scroll changes movement speed multiplicatively; fast and boost modifiers come from `CameraInputBindings3D`.
- `FirstPersonCameraController3D` attaches to a caller-owned `CameraAnchor3D`, rotates the camera view, and must not translate the anchor or player body.
- `ThirdPersonCameraController3D` follows a caller-owned `CameraAnchor3D` with distance, offset, smoothing, and zoom. It does not own collision, physics, character movement, or obstruction handling.
- `FirstPersonCameraController3D`, `ThirdPersonCameraController3D`, and 3D `CinematicCameraController` derive right, forward, and up axes from the anchor's local up each frame so games can use slopes, spherical worlds, wall walking, and space scenes.
- `CinematicCameraController` is projection-aware and supports smooth 2D follow/pan/zoom/rotation, smooth 3D follow/look-at/orbit/offset around anchors, and explicit 3D sampled paths for authored shots. It is for trailers, intros, flybys, and scene presentation, not gameplay movement ownership.
- `CinematicCameraPath3D` samples into caller-owned `CinematicCameraPathSample3D` output so path playback does not allocate every frame. `KeyframeCinematicCameraPath3D` is a built-in spline-smoothed, constant-distance keyframe path for steady camera travel without hard target changes; custom path implementations can use timelines, rails, easing, or gameplay-authored camera tracks.
- Focused controllers do not contain hardcoded mode-switch keys. Any optional mode switching belongs to caller code.

Defined resource and command types in the first rendering slice:

| Type | Role |
| --- | --- |
| `Buffer` | Provider-backed GPU buffer used by vertex data in the first rendering slice. |
| `Texture` | Provider-backed sampled GPU texture used by the first sprite rendering slice. |
| `TextureView` | View of a render target attachment for render pass setup. |
| `FrameBuffer` | Current frame drawable and end-of-frame RGBA8 readback view. |
| `Mesh` | Concrete provider-neutral GPU mesh wrapper for vertex/index buffers and layout metadata. |
| `ShaderModule` | Compiled or loaded shader module. |
| `RenderPipeline` | Render pipeline object. |
| `CommandEncoder` | Frame command recording object owned by the current frame. |
| `RenderPass` | Render pass encoder. |

Defined descriptor types:

| Type | Role |
| --- | --- |
| `BufferDescriptor` | Buffer creation label, size, usage, and dynamic/static update intent. |
| `TextureDescriptor` | Texture creation label, size, format, usage, and sampler wrap state. |
| `ShaderModuleDescriptor` | WGSL shader source plus provider-ready generated shader output used internally after runtime compilation. Public shader authoring uses WGSL only. |
| `ShaderBundle` | Optional WGSL source-of-truth container plus profile and reflection metadata. |
| `ShaderReflection`, `ShaderBinding`, `ShaderAttribute` | Setup-time metadata for shader bindings and vertex inputs declared by a shader owner or produced by shader tooling. |
| `RenderPassDescriptor` | Color/depth attachments, load/store operations, clear values. |
| `RenderPipelineDescriptor` | Shader module, entry points, target format, shader reflection metadata, primitive topology, vertex layouts, sampled texture count, depth state, and debug label. |

Defined value/state types:

| Type | Role |
| --- | --- |
| `TextureFormat` | Portable texture/surface format. |
| `ShaderLanguage` | Shader source family. WGSL, GLSL, SPIR-V, and MSL are represented in the current graphics API. |
| `ShaderProfile` | WGSL portability profile: WebGL2-compatible, WebGPU-compatible, or provider-native. |
| `ShaderTarget` | Provider target language/output selection, such as WebGPU WGSL, OpenGL GLSL, WebGL/GLES GLSL ES, Vulkan SPIR-V, Metal MSL, or DirectX HLSL. |
| `ShaderStage`, `ShaderBindingType` | Shader metadata values for setup-time reflection and validation. |
| `ShaderValidationResult`, `ShaderValidationDiagnostic`, `ShaderValidationSeverity` | Build/setup-time shader profile validation result types. |
| `PrimitiveTopology` | Primitive assembly mode for first render pipelines. |
| `BufferUsage` | Portable buffer usage. The first implementation defines vertex and index buffers. |
| `TextureUsage` | Portable texture usage. The current slice defines sampled textures, render-attachment textures, and sampled render-attachment textures. |
| `TextureWrap` | Portable sampled-texture coordinate wrap mode. |
| `VertexLayout`, `VertexStepMode`, `VertexAttribute`, `VertexFormat` | Portable vertex input layout for render pipelines. |
| `LoadOp`, `StoreOp` | Render pass attachment load/store behavior. |
| `GraphicsClientApi` | Backend window/client API mode, such as `NO_API`, `OPENGL`, or `VULKAN`. |
| `GraphicsContextProfile` | GL context profile request, such as core or compatibility. |

Descriptor construction helpers used by examples:

```java
public final class ShaderModuleDescriptor {
    public static ShaderModuleDescriptor wgsl(String label, String source);
    public ShaderModuleDescriptor wgsl(String source);
    public ShaderModuleDescriptor entryPoints(String vertexEntryPoint, String fragmentEntryPoint);
    public boolean hasSource(ShaderLanguage language);
}

public enum ShaderProfile {
    PORTABLE_WEBGL2, PORTABLE_WEBGPU, NATIVE;
    public String id();
    public static ShaderProfile fromId(String id, ShaderProfile fallback);
}

public enum ShaderTarget {
    WEBGPU_WGSL, WGPU_WGSL, WEBGL_GLSL_ES, GLES_GLSL_ES,
    OPENGL_GLSL, VULKAN_SPIRV, METAL_MSL, DIRECTX_HLSL;
    public static ShaderTarget forProvider(ProviderId providerId);
    public static ShaderTarget forProvider(String providerId);
}

public final class ShaderBundle {
    public static ShaderBundle.Builder builder(String label);
    public ShaderProfile profile();
    public String wgslSource();
    public ShaderReflection reflection();
    public boolean hasTarget(ShaderTarget target);
    public ShaderValidationResult validateProfile();
    public ShaderModuleDescriptor descriptorForProvider(ProviderId providerId);
    public ShaderModuleDescriptor descriptorForProvider(String providerId);
    public ShaderModuleDescriptor descriptorForTarget(ShaderTarget target);

    public static final class Builder {
        public Builder profile(ShaderProfile profile);
        public Builder wgsl(String source);
        public Builder reflection(ShaderReflection reflection);
        public ShaderBundle build();
    }
}

public final class RenderPassDescriptor {
    public static RenderPassDescriptor color(TextureView colorAttachment, LoadOp loadOp, StoreOp storeOp);
}

public final class BufferDescriptor {
    public static BufferDescriptor vertex(String label, int size);
    public static BufferDescriptor staticVertex(String label, int size);
    public static BufferDescriptor index(String label, int size);
    public static BufferDescriptor staticIndex(String label, int size);
    public BufferDescriptor dynamic(boolean dynamic);
    public boolean dynamic();
}

public final class TextureDescriptor {
    public static TextureDescriptor rgba8(String label, int width, int height);
    public static TextureDescriptor rgba8RenderTarget(String label, int width, int height);
    public TextureDescriptor label(String label);
    public TextureDescriptor size(int width, int height);
    public TextureDescriptor format(TextureFormat format);
    public TextureDescriptor usage(TextureUsage usage);
    public TextureDescriptor wrap(TextureWrap wrap);
    public TextureDescriptor wrap(TextureWrap wrapS, TextureWrap wrapT);
    public TextureWrap wrapS();
    public TextureWrap wrapT();
}

public final class LoadOp {
    public static LoadOp clear(float red, float green, float blue, float alpha);
    public static LoadOp load();
}

public final class StoreOp {
    public static StoreOp store();
    public static StoreOp discard();
}

public final class RenderPipelineDescriptor {
    public static RenderPipelineDescriptor shader(ShaderModule shaderModule, TextureFormat colorFormat);
    public RenderPipelineDescriptor label(String label);
    public RenderPipelineDescriptor vertexEntryPoint(String vertexEntryPoint);
    public RenderPipelineDescriptor fragmentEntryPoint(String fragmentEntryPoint);
    public RenderPipelineDescriptor shaderReflection(ShaderReflection shaderReflection);
    public RenderPipelineDescriptor primitiveTopology(PrimitiveTopology primitiveTopology);
    public RenderPipelineDescriptor vertexLayout(VertexLayout vertexLayout);
    public RenderPipelineDescriptor vertexLayouts(VertexLayout... vertexLayouts);
    public RenderPipelineDescriptor sampledTextureCount(int sampledTextureCount);
    public RenderPipelineDescriptor depthTestEnabled(boolean depthTestEnabled);
    public RenderPipelineDescriptor depthWriteEnabled(boolean depthWriteEnabled);
}
```

### 13.1. Graphics Provider Contract

The current graphics provider contract separates backend window ownership from graphics provider attachment. A backend creates the display and native window handles, then a graphics extension creates a `GraphicsAttachment` for that environment.

This shape lets desktop, desktop_c, web, Android, and iOS backends attach the same graphics provider without the provider depending on a concrete backend module.

Defined interface roles:

| Interface | What it is for | Why it is generic |
| --- | --- | --- |
| `GraphicsAttachmentProvider` | Entry point implemented by a graphics extension such as wgpu, GL, or Vulkan. | Every graphics family needs setup code that can attach to backend-owned presentation handles. |
| `GraphicsAttachmentRequirements` | Provider-declared context/window requirements. | WGPU needs no client API; desktop GL needs a GL context; future WebGL needs a web canvas path. |
| `GraphicsEnvironment` | Backend-provided setup values, currently `Display` and `NativeWindow`. | Providers need presentation metadata without importing backend classes. |
| `GraphicsAttachment` | Backend-driven graphics lifecycle object. | Backends own frame timing, resize, and presentation; providers own GPU work. |
| `Graphics` | Graphics manager returned by `Fdx.graphics()`. | Game code has one typed graphics entry point that can own one or more provider contexts. |
| `GraphicsContext` | Provider-backed rendering context. | Simple code uses `fdx.graphics().main()`; advanced code may create additional contexts when supported. |
| `GraphicsDevice` | Common interface for creating first low-level rendering resources. | Providers may map this to a native device, context, or device wrapper. |
| `GraphicsFrame` | Current frame command and color target access. | Backends/providers keep native frame acquisition and presentation hidden from game code. |

Defined provider setup shape:

```java
public interface GraphicsAttachmentProvider {
    ProviderId providerId();
    GraphicsAttachmentRequirements requirements();
    GraphicsAttachment create(GraphicsEnvironment environment);
}

public interface GraphicsEnvironment {
    Display display();
    NativeWindow nativeWindow();
}

public interface GraphicsAttachment extends GraphicsContext, Disposable {
    void resize(int framebufferWidth, int framebufferHeight);
    void processEvents();
    boolean beginFrame();
    void endFrame();
}
```

Rules:

- A concrete backend config selects the graphics attachment provider.
- Backends must apply `GraphicsAttachmentRequirements` before creating the native presentation target.
- Backends attach the created `GraphicsAttachment` as the main `GraphicsContext` returned by `fdx.graphics().main()`.
- `GraphicsAttachmentProvider` is setup SPI and should not be resolved by normal game code.
- Provider-specific setup options live on the provider object, such as `WGPUProvider`, not in generic backend config maps.
- `NativeWindow` is the only common type that intentionally carries native handles or platform objects, and it is for backend/provider setup only. It may carry both a backend window handle, such as a GLFW window pointer, and platform-native handles or objects, such as HWND, X11 Window, or Android `Surface`.
- Providers may expose advanced provider-specific objects through `as()`, but normal rendering should use common API types.

### 13.2. Graphics Resource And Command Contracts

Defined resource shape:

```java
public interface TextureView extends ProviderHandle {
    TextureFormat format();
}

public interface Texture extends ProviderHandle, Disposable {
    int width();
    int height();
    TextureFormat format();
    TextureUsage usage();
}

public interface FrameBuffer extends ProviderHandle {
    TextureView colorAttachment();
    TextureFormat format();
    int width();
    int height();
    ByteBuffer readPixelsRgba8();
}

public interface Buffer extends ProviderHandle, Disposable {
    int size();
    BufferUsage usage();
}

public final class Mesh implements Disposable {
    public static final VertexLayout POSITION_COLOR_LAYOUT;
    public static final VertexLayout PBR_LAYOUT;
    public static final VertexLayout PBR_SKINNED_LAYOUT;
    public Mesh(GraphicsContext graphics, String id, VertexLayout vertexLayout, float[] vertices, int vertexCount);
    public Mesh(GraphicsContext graphics, String id, VertexLayout vertexLayout, float[] vertices, int vertexCount,
            BoundingBox bounds);
    public Mesh(GraphicsContext graphics, String id, VertexLayout vertexLayout, float[] vertices, int vertexCount,
            short[] indices, int indexCount, BoundingBox bounds);
    public static Mesh coloredTriangle(GraphicsContext graphics, String id);
    public static Mesh positionColor3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, BoundingBox bounds);
    public static Mesh positionColor3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, float[] sourceBakedColors, float[] sourceNormals, float[] sourceTexCoords,
            float[] sourcePbr, float[] sourceBakedPbr, float[] sourceEmissive, float[] sourceBakedEmissive,
            BoundingBox bounds, boolean retainSourceData);
    public static Mesh positionColor3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, float[] sourceBakedColors, float[] sourceNormals, float[] sourceTexCoords,
            float[] sourcePbr, float[] sourceBakedPbr, float[] sourceEmissive, float[] sourceBakedEmissive,
            int[] sourceJoints, float[] sourceWeights, BoundingBox bounds, boolean retainSourceData);
    public String id();
    public Buffer vertexBuffer();
    public Buffer indexBuffer();
    public VertexLayout vertexLayout();
    public int vertexCount();
    public int indexCount();
    public BoundingBox bounds();
    public boolean hasPositionColor3DSource();
    public float[] sourcePositions();
    public float[] sourceColors();
    public float[] sourceBakedColors();
    public float[] sourceNormals();
    public float[] sourceTexCoords();
    public float[] sourcePbr();
    public float[] sourceBakedPbr();
    public float[] sourceEmissive();
    public float[] sourceBakedEmissive();
    public int[] sourceJoints();
    public float[] sourceWeights();
}

public interface ShaderModule extends ProviderHandle, Disposable {
    ShaderLanguage language();
}
```

Defined pipeline and binding shape:

```java
public interface RenderPipeline extends ProviderHandle, Disposable {
}
```

Defined command shape:

```java
public interface CommandEncoder extends ProviderHandle {
    RenderPass beginRenderPass(RenderPassDescriptor descriptor);
}

public interface RenderPass extends ProviderHandle {
    void setPipeline(RenderPipeline pipeline);
    void setVertexBuffer(Buffer buffer);
    void setVertexBuffer(int slot, Buffer buffer);
    void setIndexBuffer(Buffer buffer);
    void setTexture(int slot, Texture texture);
    void setScissor(int x, int y, int width, int height);
    void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance);
    void drawIndexed(int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance);
    void end();
}
```

Rules:

- Descriptor objects carry creation parameters; resource interfaces expose stable identity, metadata, lifecycle, and provider access.
- Resource metadata methods should return the values the resource was created with.
- `ShaderModuleDescriptor` public authoring is WGSL-only. Providers that need GLSL, SPIR-V, or MSL receive descriptors generated from WGSL through the `runtime/fdx/core` shader compiler capability during shader-module creation.
- `ShaderBundle` remains an optional setup-time wrapper for tools and users that want to group WGSL, profile metadata, and reflection metadata. Normal built-in renderers pass WGSL `ShaderModuleDescriptor` values directly and let the selected provider compile when translation is required.
- Runtime WGSL-to-GLSL/SPIR-V/MSL translation is an explicit provider feature backed by `runtime/fdx/core`. It happens at shader-module creation/setup time, not in a render loop. If the active runtime does not provide the compiler capability for a provider that needs translation, shader creation must fail clearly.
- A shader that passes WebGPU/WGSL validation is not automatically portable to WebGL/OpenGL ES. Use `ShaderProfile.PORTABLE_WEBGL2` for shaders that must run on WebGL2/GLES-style targets and `ShaderProfile.PORTABLE_WEBGPU` for shaders that only need modern WebGPU/wgpu-class targets.
- Metal uses translated MSL through the same WGSL-only descriptor contract. DirectX/HLSL remains a future target until the language enum, descriptor shape, and provider path are implemented.
- `BufferDescriptor.vertex(label, size)` creates provider-backed vertex storage. `BufferDescriptor.index(label, size)` creates provider-backed index storage. Buffers are dynamic by default for frequent writes; `staticVertex(...)`, `staticIndex(...)`, or `dynamic(false)` mark storage that is optimized for infrequent uploads and repeated draws.
- The first common indexed draw shape uses unsigned 16-bit indices. `GraphicsDevice.writeBuffer(buffer, data)` uploads the bytes in the provided `ByteBuffer` range.
- `Mesh` is the single concrete graphics API mesh class, not a g3d type. It can be used by 2D, UI, custom renderers, and 3D. It owns static vertex and optional unsigned 16-bit index buffers, exposes its `VertexLayout`, counts, optional bounds metadata, and disposes the underlying buffers.
- There must not be a second public mesh class for the same portable concept. Higher-level modules add semantics around `Mesh`, such as `g3d` `MeshPart`, instead of defining another mesh resource type.
- `Mesh.positionColor3D(...)` is the current source-retained constructor path used by the first g3d renderer. It keeps optional CPU-side attribute arrays only when `retainSourceData` is true; the arrays are for renderer fallback paths and are not a separate mesh type.
- `Mesh.indexBuffer()` returns `null` for non-indexed meshes.
- `Mesh.sourcePositions()`, `sourceColors()`, `sourceBakedColors()`, `sourceNormals()`, `sourceTexCoords()`, `sourcePbr()`, `sourceBakedPbr()`, `sourceEmissive()`, and `sourceBakedEmissive()` return `null` when that source attribute was not retained or was not supplied.
- A pipeline only needs a `VertexLayout` when shader inputs read vertex attributes. Procedural shaders may continue to use no vertex layout. Pipelines that read multiple vertex buffers use `RenderPipelineDescriptor.vertexLayouts(...)` and bind them with `RenderPass.setVertexBuffer(slot, buffer)`.
- `VertexLayout.of(...)` creates per-vertex input by default. `VertexLayout.instance(...)` or `VertexLayout.of(..., VertexStepMode.INSTANCE, ...)` creates per-instance input for instanced draws.
- `VertexFormat.UNORM8X4` is a packed four-component unsigned-byte normalized vertex format for colors and other compact attributes. Providers must map it to normalized attribute input, not four raw floats.
- `TextureDescriptor.rgba8(label, width, height)` creates an RGBA8 sampled texture descriptor.
- `TextureDescriptor.rgba8RenderTarget(label, width, height)` creates an RGBA8 texture descriptor that can be rendered into and sampled later, for example by a shadow-map or post-processing pass.
- Texture wrap defaults to `TextureWrap.CLAMP_TO_EDGE`. Call `TextureDescriptor.wrap(...)` to request `REPEAT` or `MIRRORED_REPEAT` sampled-texture addressing.
- `GraphicsFrame.frameBuffer()` exposes the current drawable. `FrameBuffer.readPixelsRgba8()` is an end-of-frame capture operation: after it succeeds, no more commands should be recorded against that frame, and a later `GraphicsAttachment.endFrame()` for the same frame may be a no-op.
- `GraphicsDevice.writeTexture(texture, data)` uploads the full RGBA byte range from the provided `ByteBuffer`.
- Pipelines that sample textures declare the number of sampled textures they expect with `RenderPipelineDescriptor.sampledTextureCount(...)`.
- `RenderPass.setTexture(slot, texture)` binds a sampled texture for subsequent draws in the active pass.
- `RenderPass.setIndexBuffer(buffer)` binds an index buffer for subsequent `drawIndexed(...)` calls in the active pass.
- `RenderPass.setScissor(x, y, width, height)` sets the active pass clip rectangle for subsequent draws. Coordinates are framebuffer pixel coordinates in the provider's render-target origin convention. Higher-level renderers that target multiple providers are responsible for converting their logical clip origin before calling this method.
- `Texture.view()` returns the default texture view when the selected provider supports texture-backed attachments.
- Frame command encoders are owned by the backend/provider attachment. Game code records passes through `Graphics.currentFrame().commandEncoder()`.
- Pass objects are scoped. Once `end()` is called, the pass should not accept more commands.
- `ShaderModule` and `RenderPipeline` are application-owned disposable resources.
- `TextureView`, `FrameBuffer`, `GraphicsFrame`, and `CommandEncoder` returned from `currentFrame()` are frame-owned handles. Application code must not store them across frames.

### 13.3. Generic Provider Flow

Launcher setup selects a concrete backend and a concrete graphics provider:

```java
DesktopApplicationConfig config = new DesktopApplicationConfig()
    .title("My Game")
    .size(1280, 720)
    .graphics(new WGPUProvider());

new DesktopApplicationBackend().start(config, new MyGame());
```

The same launcher can select desktop GL explicitly. On the desktop backend, that launcher also includes the `gl_desktop` runtime/native module.

```java
config.graphics(new DesktopOpenGLProvider());
```

The same desktop backend can select desktop Vulkan explicitly. That launcher also includes the `vulkan_desktop` runtime module.

```java
config.graphics(new DesktopVulkanProvider());
```

Application creation can build provider-neutral resources:

```java
GraphicsContext gfx = fdx.graphics().main();

ShaderModule shader = gfx.device().createShaderModule(
    ShaderModuleDescriptor.wgsl("triangle", wgslSource)
);

RenderPipeline pipeline = gfx.device().createRenderPipeline(
    RenderPipelineDescriptor.shader(shader, gfx.surfaceFormat())
);
```

Per-frame rendering uses the backend-owned current frame:

```java
GraphicsFrame frame = gfx.currentFrame();
CommandEncoder encoder = frame.commandEncoder();
RenderPass pass = encoder.beginRenderPass(RenderPassDescriptor.color(
    frame.colorAttachment(),
    LoadOp.clear(0.0f, 0.0f, 0.0f, 1.0f),
    StoreOp.store()
));

pass.setPipeline(pipeline);
pass.draw(3, 1, 0, 0);
pass.end();
```

The same application code should work regardless of which provider is selected, as long as the selected provider implements the required common rendering slice.

Advanced desktop code can create another display and another provider-backed context when supported:

```java
Display toolsDisplay = fdx.displays().create(new DisplayConfig()
    .title("Vulkan Tools")
    .size(900, 600));

GraphicsContext vulkan = fdx.graphics().create(
    GraphicsConfig.provider(new DesktopVulkanProvider())
        .display(toolsDisplay));

if (vulkan.providerId().equals(DesktopVulkanProvider.ID)) {
    VulkanContext nativeVulkan = vulkan.as();
    // Use provider-specific Vulkan APIs here.
}
```

This does not imply automatic resource sharing. Textures, buffers, command encoders, and render pipelines belong to the `GraphicsContext` that created them unless a future explicit interop API says otherwise.

### 13.4. Provider Mapping Examples

These mappings explain why the common interfaces are generic. They are not a commitment to support every listed provider family as a first milestone.

| Common type | WebGPU/wgpu provider | Vulkan provider | Metal-style provider | Legacy GL-style provider |
| --- | --- | --- | --- | --- |
| `Graphics` / `GraphicsContext` | Manager owns context creation; context owns wgpu instance/runtime and surface state. | Manager owns context creation; context owns instance, extension loading, and surface state. | Manager owns context creation; context owns Metal runtime/device discovery and layer/view integration. | Manager owns context creation; context owns display/profile state. |
| `GraphicsAdapter` | Wraps the selected wgpu adapter. | Wraps the selected physical device and queue family choices. | Wraps the selected Metal device. | Represents selected driver/profile/display configuration. |
| `GraphicsDevice` | Wraps wgpu device. | Wraps logical device. | Wraps Metal device plus common API state. | Wraps current graphics context plus common API state. |
| `GraphicsQueue` | Wraps wgpu queue. | Wraps graphics/compute/present queue handle. | Wraps command queue. | Serializes and flushes recorded command work against the current context. |
| `Surface` | Wraps wgpu surface. | Wraps platform surface plus swapchain ownership. | Wraps layer/drawable source. | Wraps window/canvas drawable or default framebuffer target. |
| `SurfaceTexture` | Wraps acquired surface texture. | Wraps acquired swapchain image plus view. | Wraps current drawable texture. | Wraps current backbuffer or default framebuffer as a frame object. |
| `Texture` | Wraps wgpu texture. | Wraps image plus allocation ownership. | Wraps texture. | Wraps texture object/storage. |
| `TextureView` | Wraps native texture view. | Wraps image view. | Wraps texture view or view descriptor. | Uses native texture view if available, otherwise a lightweight view descriptor. |
| `Sampler` | Wraps sampler. | Wraps sampler. | Wraps sampler state. | Wraps or caches sampler state. |
| `ShaderModule` | Wraps shader module. | Wraps shader module or translated shader. | Wraps library/function metadata. | Wraps compiled shader/program inputs. |
| `BindGroupLayout` / `BindGroup` | Maps directly to bind group layout and bind group. | Maps to descriptor set layout and descriptor set. | Maps to argument buffer or binding metadata. | Maps to generated binding table applied before draw. |
| `RenderPipeline` | Wraps render pipeline. | Wraps graphics pipeline. | Wraps render pipeline state. | Wraps shader program plus fixed-function state cache. |
| `CommandEncoder` / `CommandBuffer` | Maps directly to command encoder and command buffer. | Maps to command buffer recording. | Maps to command buffer/encoder recording. | Records common commands and replays them against the current context on submit. |

The common API should be designed around the semantic job each type performs, not around whether every native provider has the same object name.

### 13.5. Texture And TextureView

`Texture` owns GPU image/storage. `TextureView` owns a specific interpretation of a texture when the texture is used as a frame/render attachment.

Current implemented texture slice:

- `Texture` exposes width, height, format, usage, provider identity, disposal, and `as()`.
- `TextureDescriptor.rgba8(label, width, height)` creates sampled RGBA8 textures.
- `TextureDescriptor.rgba8RenderTarget(label, width, height)` creates RGBA8 textures with both render-attachment and sampled usage for offscreen passes that feed later draw calls.
- `TextureDescriptor.wrap(...)` controls sampled-texture coordinate addressing. The default is `TextureWrap.CLAMP_TO_EDGE`.
- `GraphicsDevice.writeTexture(texture, data)` uploads full RGBA image data.
- `RenderPass.setTexture(slot, texture)` binds sampled textures for draw calls.
- `TextureRegion` in `graphics/g2d` maps sub-rectangles of a `Texture` to normalized UV coordinates.

`TextureView` is common because modern graphics APIs need a way to bind or render to a specific interpretation, mip range, layer range, or aspect of a texture.

Providers may implement `TextureView` as:

- a native provider object
- a lightweight Java object that references the texture plus view metadata

Advanced view features should be capability-gated:

- format reinterpretation
- partial mip ranges
- array layer ranges
- cube/cube-array views
- depth-only or stencil-only aspects

Rules:

- `Texture` must not expose native API handles directly.
- `TextureView` must not contain provider-specific native methods.
- Provider-specific view details belong in types such as `WGPUTexture` or `VkTexture` through `as()`.
- If a provider cannot support a requested view descriptor, it should fail with a clear capability/configuration error.

### 13.6. Graphics Surface Boundary

`Display` and `Surface` are separate:

```text
runtime/display Display
graphics/api Surface
```

`Display` is the platform presentation area. `Surface` is the graphics API object used for rendering/presentation.

Rules:

- `runtime/display` must not depend on `graphics/api`.
- `graphics/api` may depend on `runtime/display` for surface creation or presentation handles.
- Headless backends may not expose a display-backed surface.
- Offscreen rendering should not require a `Display`.

### 13.7. Graphics Capabilities

The common graphics API should not pretend every provider supports every operation.

Capability examples:

```text
texture view reinterpretation
storage textures
compute
timestamp queries
multi-sampling
depth/stencil formats
surface present modes
shader languages
maximum texture dimensions
maximum bind groups
```

Provider implementations should validate descriptors against capabilities at creation time.

## 14. Graphics 2D

Module:

```text
:libfdx:graphics:g2d
```

Package:

```text
io.github.libfdx.graphics.g2d
```

`g2d` is a complete 2D toolkit built on `graphics/api`.

Defined types:

| Type | Role |
| --- | --- |
| `Batch2D` | Common textured 2D batch contract. |
| `SpriteBatch` | Default batched sprite renderer implementation. |
| `SpriteOutlineRenderer2D` | WGSL-authored sprite outline effect renderer. |
| `FogOfWarRenderer2D` | WGSL-authored 2D fog-of-war overlay renderer. |
| `ParticleEmitter2D` | Fixed-capacity 2D particle emitter that renders through `Batch2D`. |
| `TextureRegion` | Region of a `Texture`. |
| `TileLayer` | Tile id grid for one tile-map layer. |
| `TileMap` | Tile-map dimensions and ordered layers. |
| `TileSet` | Positive tile id to `TextureRegion` mapping. |
| `TileMapRenderer` | Provider-neutral renderer that draws tile maps through `Batch2D`. |
| `ShapeRenderer2D` | Debug/simple 2D shape rendering. |
| `BitmapFont`, `BitmapFontGlyph`, `BitmapFontLayout` | Bitmap font data, glyph regions, metrics, and text layout. |
| `BitmapFontFiles`, `FreeTypeFontOptions` | `.fnt` bitmap font loading and FreeType-style vector font rasterization options. |

### 14.1. Graphics 2D Contracts

`g2d` provides higher-level rendering helpers on top of `graphics/api`. It should hide low-level graphics details where possible, but it still renders through common `RenderPass`, `Texture`, and `Buffer` concepts internally.

Defined shape:

```java
public interface Batch2D extends Disposable {
    void begin();
    void begin(LoadOp loadOp);
    void begin(RenderPass pass);
    Batch2D color(float red, float green, float blue, float alpha);
    Batch2D viewport(int width, int height);
    void draw(Texture texture, float x, float y, float width, float height);
    void draw(Texture texture, float x, float y, float width, float height,
        float originX, float originY, float rotationDegrees);
    void draw(TextureRegion region, float x, float y, float width, float height);
    void draw(TextureRegion region, float x, float y, float width, float height,
        float originX, float originY, float rotationDegrees);
    void draw(TextureRegion region, float[] centerX, float[] centerY, int count,
        float width, float height, float originX, float originY, float rotationDegrees);
    void end();
}

public final class SpriteBatch implements Batch2D {
    public SpriteBatch(GraphicsContext graphics);
    public SpriteBatch(GraphicsContext graphics, int initialMaxSprites);
    public void begin();
    public void begin(LoadOp loadOp);
    public void begin(RenderPass pass);
    public SpriteBatch color(float red, float green, float blue, float alpha);
    public SpriteBatch viewport(int width, int height);
    public void draw(Texture texture, float x, float y, float width, float height);
    public void draw(Texture texture, float x, float y, float width, float height,
        float originX, float originY, float rotationDegrees);
    public void draw(TextureRegion region, float x, float y, float width, float height);
    public void draw(TextureRegion region, float x, float y, float width, float height,
        float originX, float originY, float rotationDegrees);
    public void draw(TextureRegion region, float[] centerX, float[] centerY, int count,
        float width, float height, float originX, float originY, float rotationDegrees);
    public void end();
}

public final class SpriteOutlineRenderer2D implements Disposable {
    public SpriteOutlineRenderer2D(GraphicsContext graphics);
    public SpriteOutlineRenderer2D(GraphicsContext graphics, int initialMaxSprites);
    public void begin();
    public void begin(LoadOp loadOp);
    public void begin(RenderPass pass);
    public SpriteOutlineRenderer2D color(float red, float green, float blue, float alpha);
    public SpriteOutlineRenderer2D outlineColor(float red, float green, float blue, float alpha);
    public SpriteOutlineRenderer2D outlineWidth(float width);
    public void draw(TextureRegion region, float x, float y, float width, float height);
    public void end();
}

public final class FogOfWarRenderer2D implements Disposable {
    public static final int MAX_LIGHTS = 4;
    public FogOfWarRenderer2D(GraphicsContext graphics);
    public FogOfWarRenderer2D(GraphicsContext graphics, int initialMaxQuads);
    public void begin();
    public void begin(LoadOp loadOp);
    public void begin(RenderPass pass);
    public FogOfWarRenderer2D color(float red, float green, float blue, float alpha);
    public FogOfWarRenderer2D clearLights();
    public FogOfWarRenderer2D light(float x, float y, float radius, float softness);
    public void draw(float x, float y, float width, float height);
    public void end();
}

public final class ParticleEmitter2D {
    public ParticleEmitter2D(int maxParticles);
    public ParticleEmitter2D seed(int seed);
    public ParticleEmitter2D position(float x, float y);
    public ParticleEmitter2D emissionRate(float particlesPerSecond);
    public ParticleEmitter2D lifetime(float seconds);
    public ParticleEmitter2D lifetime(float minSeconds, float maxSeconds);
    public ParticleEmitter2D speed(float unitsPerSecond);
    public ParticleEmitter2D speed(float minUnitsPerSecond, float maxUnitsPerSecond);
    public ParticleEmitter2D direction(float degrees, float spreadDegrees);
    public ParticleEmitter2D gravity(float x, float y);
    public ParticleEmitter2D size(float start, float end);
    public ParticleEmitter2D size(float minStart, float maxStart, float minEnd, float maxEnd);
    public ParticleEmitter2D color(float startRed, float startGreen, float startBlue, float startAlpha,
        float endRed, float endGreen, float endBlue, float endAlpha);
    public ParticleEmitter2D rotation(float minDegrees, float maxDegrees,
        float minAngularVelocityDegrees, float maxAngularVelocityDegrees);
    public int update(float deltaSeconds);
    public int emit(int count);
    public ParticleEmitter2D clear();
    public int render(TextureRegion region, Batch2D batch);
    public int maxParticles();
    public int activeCount();
    public float x(int index);
    public float y(int index);
    public float age(int index);
    public float lifetime(int index);
    public float size(int index);
    public float red(int index);
    public float green(int index);
    public float blue(int index);
    public float alpha(int index);
    public float rotationDegrees(int index);
}

public final class TextureRegion {
    public TextureRegion(Texture texture);
    public TextureRegion(Texture texture, int x, int y, int width, int height);
    public static TextureRegion[][] split(Texture texture, int tileWidth, int tileHeight);
    public Texture texture();
    public int x();
    public int y();
    public int width();
    public int height();
    public float u();
    public float v();
    public float u2();
    public float v2();
}

public final class TileLayer {
    public TileLayer(int width, int height);
    public int tile(int x, int y);
    public TileLayer tile(int x, int y, int tileId);
    public TileLayer fill(int tileId);
    public boolean isVisible();
    public TileLayer visible(boolean visible);
    public int width();
    public int height();
    public int size();
    public int[] tiles();
}

public final class TileMap {
    public TileMap(int width, int height, float tileWidth, float tileHeight);
    public TileLayer addLayer();
    public TileMap addLayer(TileLayer layer);
    public TileLayer layer(int index);
    public TileLayer removeLayer(int index);
    public void clearLayers();
    public int layerCount();
    public int width();
    public int height();
    public float tileWidth();
    public float tileHeight();
    public float worldWidth();
    public float worldHeight();
}

public final class TileSet {
    public static final int EMPTY_TILE = 0;
    public static TileSet from(TextureRegion[][] regions);
    public TileSet region(int tileId, TextureRegion region);
    public TextureRegion region(int tileId);
    public boolean contains(int tileId);
    public TextureRegion remove(int tileId);
    public void clear();
    public int size();
}

public final class TileMapRenderer {
    public int render(TileMap map, TileSet tileSet, Batch2D batch, float x, float y);
    public int render(TileMap map, TileSet tileSet, Batch2D batch, float x, float y,
        float visibleX, float visibleY, float visibleWidth, float visibleHeight);
}

public final class BitmapFont implements Disposable {
    public static BitmapFont fromGrid(Texture texture, String characters, int glyphWidth, int glyphHeight);
    public BitmapFontGlyph glyph(int codePoint);
    public boolean hasGlyph(int codePoint);
    public int kerning(int first, int second);
    public float scale(float size);
    public float lineHeight(float size);
    public float width(String text, float size);
    public BitmapFontLayout layout(String text, float size, float maxWidth, boolean wrap, boolean ellipsis);
}

public final class BitmapFontFiles {
    public static BitmapFont load(GraphicsContext graphics, FileSystem files, String path);
    public static BitmapFont loadBitmap(GraphicsContext graphics, FileSystem files, String path);
    public static BitmapFont loadFreeType(GraphicsContext graphics, FileSystem files, String path,
        FreeTypeFontOptions options);
    public static BitmapFont generateFreeType(GraphicsContext graphics, FreeTypeFontOptions options);
}

public final class ShapeRenderer2D implements Disposable {
    public ShapeRenderer2D(GraphicsContext graphics);
    public ShapeRenderer2D(GraphicsContext graphics, int initialMaxVertices);
    public void begin();
    public void begin(LoadOp loadOp);
    public void begin(RenderPass pass);
    public ShapeRenderer2D color(float red, float green, float blue, float alpha);
    public void line(float x1, float y1, float x2, float y2);
    public void triangle(float x1, float y1, float x2, float y2, float x3, float y3);
    public void filledTriangle(float x1, float y1, float x2, float y2, float x3, float y3);
    public void rect(float x, float y, float width, float height);
    public void filledRect(float x, float y, float width, float height);
    public void circle(float x, float y, float radius);
    public void filledCircle(float x, float y, float radius);
    public void end();
}
```

Example:

```java
spriteBatch.begin(LoadOp.clear(1.0f, 1.0f, 1.0f, 1.0f));
spriteBatch.draw(playerFrame, -0.5f, -0.5f, 1.0f, 1.0f);
spriteBatch.end();
```

Rules:

- `g2d` should use `graphics/api`, not provider-specific graphics types.
- `g2d` should hide `TextureView` from simple sprite users when possible.
- `ShapeRenderer2D` is the first g2d implementation. It streams CPU-generated vertices into common `Buffer` objects and currently uses normalized -1..1 coordinates.
- `Batch2D` is the common textured g2d batch contract. `SpriteBatch` is the first implementation. It streams quad vertices into common `Buffer` objects, binds common `Texture` handles, and currently uses normalized -1..1 coordinates. Rotation is expressed in degrees around the supplied local origin. `viewport(width, height)` supplies the framebuffer size used to keep rotated sprites pixel-proportional while coordinates are still normalized. The array-based `draw(TextureRegion, float[], float[], ...)` overload submits repeated same-region sprites in one logical batch and may use instanced/static GPU buffers internally.
- `SpriteOutlineRenderer2D` is a provider-neutral effect renderer for `TextureRegion` sprites. It owns a WGSL shader module, streams reusable quad vertex data into common `Buffer` objects, binds common `Texture` handles, and currently uses normalized -1..1 coordinates. `outlineWidth(float)` is measured in source texture texels. Neighbor samples are clamped to the source region UV bounds to avoid sampling adjacent atlas regions.
- `FogOfWarRenderer2D` is a provider-neutral overlay renderer. It owns a WGSL shader module, streams reusable quad vertex data into common `Buffer` objects, and currently uses normalized -1..1 coordinates. `light(x, y, radius, softness)` adds circular reveal areas in the same coordinate space as the drawn fog rectangle. At most `FogOfWarRenderer2D.MAX_LIGHTS` reveal circles are submitted per draw call.
- `ParticleEmitter2D` is provider-neutral particle simulation data plus a `Batch2D` draw helper. It owns fixed-capacity primitive arrays, does not allocate per particle, updates with explicit `deltaSeconds`, and renders through a caller-owned, already-begun `Batch2D`. Rendering resets the batch color to white after submitting active particles.
- Tile maps are provider-neutral data and draw helpers. `TileMap` tile sizes are `float` render units so the same API can work with normalized, pixel, or camera-transformed 2D coordinates. `TileSet.EMPTY_TILE` (`0`) means no tile. Positive tile ids map to `TextureRegion` values. `TileSet.region(tileId)` returns `null` for empty or missing ids, and `TileMapRenderer` skips both empty and missing ids.
- `TileMapRenderer` draws through a caller-owned, already-begun `Batch2D`. It does not create graphics resources and does not call `begin()` or `end()`. The visible-rectangle overload treats the rectangle as world-space coordinates, clamps the tile loops to cells intersecting that rectangle, returns `0` for empty rectangles, and preserves full-map rendering through the shorter overload.
- Bitmap fonts are provider-neutral glyph metadata plus provider-backed page textures. `BitmapFontFiles.loadBitmap(...)` reads AngelCode BMFont-style `.fnt` files and page images. `BitmapFontFiles.loadFreeType(...)` rasterizes `.ttf`/`.otf` font assets into a bitmap atlas when the selected backend has registered a runtime fdx FreeType provider. `BitmapFontFiles.generateFreeType(...)` is reserved for backend-specific system-font providers and must fail clearly until a provider exists. Generated atlases should match or oversample the effective UI scale because rendering still submits texture quads.
- Particles, sprites, and additional 2D helpers belong in `g2d`, not separate required user dependencies.

## 15. Graphics 3D

Module:

```text
:libfdx:graphics:g3d
```

Package:

```text
io.github.libfdx.graphics.g3d
```

`g3d` is a complete 3D toolkit built on `graphics/api` and the shared `graphics/camera` camera types. It owns model, material, shader, animation, scene, and render-path concepts. `Batch3D` is the common 3D submission contract; `ModelBatch` is the first implementation.

Defined types:

| Type | Role |
| --- | --- |
| `Camera` | Shared graphics camera from `graphics/camera` used for 3D render submissions. |
| `Color`, `Vector3`, `Matrix4`, `BoundingBox` | Shared math types from `foundation/math` used in 3D materials, transforms, and bounds. |
| `Batch3D` | Common 3D render submission contract. |
| `ModelBatch` | Default optimized model batch implementation. |
| `OutlineRenderer3D` | WGSL-authored shell outline renderer for PBR-layout 3D meshes. |
| `FogOfWarRenderer3D` | WGSL-authored world-space 3D fog-of-war overlay renderer. |
| `SkyboxRenderer3D` | WGSL-authored procedural world-space sky/background renderer for 3D scenes. |
| `SkyEnvironment3D` | Procedural sky environment description sampled by the default PBR path for IBL-style diffuse and specular lighting. |
| `ModelBuilder` | Programmatic primitive model construction for cubes, boxes, spheres, and custom triangle meshes. |
| `Mesh` | Concrete low-level mesh from `graphics/api` used by 3D model parts. |
| `MeshPart` | 3D subset of a graphics `Mesh` rendered with one material and primitive topology. |
| `Model` | Loaded 3D model asset. |
| `DefaultModel` | Default loaded-model implementation. |
| `ModelInstance` | Instance of a model in a scene. |
| `DefaultModelInstance` | Default model instance implementation. |
| `ModelNode`, `ModelNodePart` | Model hierarchy and material-bound mesh parts. |
| `Renderable3D` | Flattened render item submitted to a batch. |
| `RenderQueue3D`, `DefaultRenderQueue3D` | Culling, grouping, and sorting queue for renderables. |
| `Material`, `MaterialAlphaMode` | 3D material abstraction and alpha mode values. |
| `PbrMaterial` | Default metallic-roughness PBR material data. |
| `Shader3D` | 3D shader implementation used by a batch. |
| `ShaderProvider3D` | Selects or creates shaders for renderables. |
| `PbrShaderProvider` | Default PBR shader provider. |
| `PbrShaderConfig` | Default PBR shader provider configuration. |
| `ShaderMaterial` | Material that opts into a custom shader provider. |
| `AnimationClip` | 3D node animation data. |
| `AnimationController` | Animation playback controller. |
| `Skeleton`, `Skin`, `Bone`, `SkinningPalette`, `CpuSkinningMeshUpdater`, `CpuSkinnedModelAnimator` | Skeletal animation data and CPU skinning helpers. |
| `MorphTarget` | Morph/blend-shape animation target. |
| `Light` | Base light description. |
| `DirectionalLight` | Directional light description. |
| `PointLight` | Point light description. |
| `SpotLight` | Spot light description. |
| `Environment3D` | Scene/environment lighting, directional and cascaded shadow-map references, sky environment lighting, and fog data. |
| `DirectionalShadowMap3D` | Helper that renders a directional-light shadow map into a sampled render target for the default PBR path. |
| `CascadedShadowMap3D` | Helper that manages multiple directional shadow maps split from a view camera for default PBR cascade sampling. |
| `BillboardRenderer3D` | WGSL-authored camera-facing textured quad renderer for 3D markers, effects, impostors, and simple particles. |
| `ParticleEmitter3D` | Fixed-capacity 3D particle emitter that renders through `BillboardRenderer3D`. |
| `RenderTarget3D` | High-level 3D render target view backed by `graphics/api` attachments. |
| `DefaultRenderTarget3D` | Default wrapper around color/depth attachments for a 3D pass. |
| `RenderPath3D` | Forward, deferred, shadow, post-processing, or custom render path. |
| `RenderGraph3D` | Ordered set of 3D passes and their render targets. |
| `G3DAssetLoaders` | Asset loader registration for 3D formats such as glTF. |
| `FrameBuffer` | Provider-neutral current drawable view owned by `graphics/api` and used by `g3d` capture paths. |

### 15.1. Graphics 3D Contracts

`g3d` provides scene/model helpers on top of `graphics/api`. Normal 3D code should use `g3d` types and not provider-specific graphics classes.

Framebuffers and render targets are graphics concepts, not GL-only concepts. The common API exposes the current drawable as a provider-neutral `FrameBuffer`; GL maps it to the default framebuffer, Vulkan maps it to the current swapchain image, and WGPU maps it to the acquired surface texture. Offscreen render targets use `TextureUsage.SAMPLED_RENDER_ATTACHMENT` and the texture's default `TextureView`, so `g3d` can render shadow maps, cascaded shadow maps, environment maps, post-processing passes, and custom render paths without naming a provider.

Defined shape:

```java
public interface Batch3D extends Disposable {
    void begin(Camera camera);
    void begin(LoadOp loadOp, Camera camera);
    void begin(RenderPass pass, Camera camera);
    void begin(RenderTarget3D target, Camera camera);
    Batch3D environment(Environment3D environment);
    Batch3D shaderProvider(ShaderProvider3D shaderProvider);
    void render(ModelInstance instance);
    void render(Renderable3D renderable);
    void render(Iterable<? extends ModelInstance> instances);
    void flush();
    void end();
}

public final class ModelBatch implements Batch3D {
    public ModelBatch(GraphicsContext graphics);
    public ModelBatch(GraphicsContext graphics, ModelBatchConfig config);
    public void begin(Camera camera);
    public void begin(LoadOp loadOp, Camera camera);
    public void begin(RenderPass pass, Camera camera);
    public void begin(RenderTarget3D target, Camera camera);
    public ModelBatch environment(Environment3D environment);
    public ModelBatch shaderProvider(ShaderProvider3D shaderProvider);
    public void render(ModelInstance instance);
    public void render(Renderable3D renderable);
    public void render(Iterable<? extends ModelInstance> instances);
    public void flush();
    public void end();
}

public final class OutlineRenderer3D implements Disposable {
    public OutlineRenderer3D(GraphicsContext graphics);
    public void begin(Camera camera);
    public void begin(LoadOp loadOp, Camera camera);
    public void begin(RenderPass pass, Camera camera);
    public OutlineRenderer3D outlineColor(float red, float green, float blue, float alpha);
    public OutlineRenderer3D outlineWidth(float width);
    public void render(ModelInstance instance);
    public void render(ModelInstance[] instances);
    public void render(Renderable3D renderable);
    public void render(Iterable<? extends ModelInstance> instances);
    public void flush();
    public void end();
}

public final class FogOfWarRenderer3D implements Disposable {
    public static final int MAX_LIGHTS = 4;
    public FogOfWarRenderer3D(GraphicsContext graphics);
    public FogOfWarRenderer3D(GraphicsContext graphics, int initialMaxQuads);
    public void begin();
    public void begin(LoadOp loadOp);
    public void begin(RenderPass pass);
    public FogOfWarRenderer3D color(float red, float green, float blue, float alpha);
    public FogOfWarRenderer3D clearLights();
    public FogOfWarRenderer3D light(float x, float y, float z, float radius, float softness);
    public void draw(Camera camera, float x, float z, float width, float depth, float y);
    public void end();
}

public final class BillboardRenderer3D implements Disposable {
    public BillboardRenderer3D(GraphicsContext graphics);
    public BillboardRenderer3D(GraphicsContext graphics, int initialMaxBillboards);
    public void begin();
    public void begin(LoadOp loadOp);
    public void begin(RenderPass pass);
    public BillboardRenderer3D color(float red, float green, float blue, float alpha);
    public void draw(Texture texture, Camera camera, float centerX, float centerY, float centerZ,
            float width, float height);
    public void draw(Texture texture, Camera camera, float centerX, float centerY, float centerZ,
            float width, float height, float rotationDegrees);
    public void draw(Texture texture, Camera camera, float centerX, float centerY, float centerZ,
            float width, float height, float rotationDegrees, float u, float v, float u2, float v2);
    public void end();
}

public final class ParticleEmitter3D {
    public ParticleEmitter3D(int maxParticles);
    public ParticleEmitter3D seed(int seed);
    public ParticleEmitter3D position(float x, float y, float z);
    public ParticleEmitter3D emissionRate(float particlesPerSecond);
    public ParticleEmitter3D lifetime(float seconds);
    public ParticleEmitter3D lifetime(float minSeconds, float maxSeconds);
    public ParticleEmitter3D speed(float unitsPerSecond);
    public ParticleEmitter3D speed(float minUnitsPerSecond, float maxUnitsPerSecond);
    public ParticleEmitter3D direction(float x, float y, float z, float spreadDegrees);
    public ParticleEmitter3D gravity(float x, float y, float z);
    public ParticleEmitter3D size(float start, float end);
    public ParticleEmitter3D size(float minStart, float maxStart, float minEnd, float maxEnd);
    public ParticleEmitter3D color(float startRed, float startGreen, float startBlue, float startAlpha,
            float endRed, float endGreen, float endBlue, float endAlpha);
    public ParticleEmitter3D rotation(float minDegrees, float maxDegrees,
            float minAngularVelocityDegrees, float maxAngularVelocityDegrees);
    public int update(float deltaSeconds);
    public int emit(int count);
    public ParticleEmitter3D clear();
    public int render(Texture texture, Camera camera, BillboardRenderer3D renderer);
    public int maxParticles();
    public int activeCount();
    public float x(int index);
    public float y(int index);
    public float z(int index);
    public float age(int index);
    public float lifetime(int index);
    public float size(int index);
    public float red(int index);
    public float green(int index);
    public float blue(int index);
    public float alpha(int index);
    public float rotationDegrees(int index);
}

public final class SkyboxRenderer3D implements Disposable {
    public SkyboxRenderer3D(GraphicsContext graphics);
    public void begin();
    public void begin(LoadOp loadOp);
    public void begin(RenderPass pass);
    public SkyboxRenderer3D zenithColor(float red, float green, float blue);
    public SkyboxRenderer3D horizonColor(float red, float green, float blue);
    public SkyboxRenderer3D nadirColor(float red, float green, float blue);
    public SkyboxRenderer3D sunColor(float red, float green, float blue, float intensity);
    public SkyboxRenderer3D sunPosition(float x, float y);
    public SkyboxRenderer3D sunDirection(float x, float y, float z);
    public SkyboxRenderer3D sunSize(float size);
    public void draw(Camera camera);
    public void end();
}

public final class SkyEnvironment3D {
    public SkyEnvironment3D zenithColor(float red, float green, float blue);
    public SkyEnvironment3D horizonColor(float red, float green, float blue);
    public SkyEnvironment3D nadirColor(float red, float green, float blue);
    public SkyEnvironment3D sunColor(float red, float green, float blue);
    public SkyEnvironment3D sunDirection(float x, float y, float z);
    public SkyEnvironment3D intensity(float diffuseIntensity, float specularIntensity);
    public SkyEnvironment3D sunIntensity(float sunIntensity);
    public SkyEnvironment3D horizonBlend(float horizonBlend);
    public Color zenithColor();
    public Color horizonColor();
    public Color nadirColor();
    public Color sunColor();
    public float sunDirectionX();
    public float sunDirectionY();
    public float sunDirectionZ();
    public float diffuseIntensity();
    public float specularIntensity();
    public float sunIntensity();
    public float horizonBlend();
}

public final class ModelBatchConfig {
    public ModelBatchConfig maxLights(int maxLights);
    public ModelBatchConfig maxBones(int maxBones);
    public ModelBatchConfig enableInstancing(boolean enabled);
    public ModelBatchConfig enableGpuSkinning(boolean enabled);
    public ModelBatchConfig shaderProvider(ShaderProvider3D shaderProvider);
    public int maxLights();
    public int maxBones();
    public boolean instancingEnabled();
    public boolean gpuSkinningEnabled();
    public ShaderProvider3D shaderProvider();
}

public final class ModelBuilder {
    public ModelBuilder(GraphicsContext graphics);
    public ModelBuilder material(Material material);
    public Model cube(float size);
    public Model cube(String id, float size);
    public Model box(float width, float height, float depth);
    public Model box(String id, float width, float height, float depth);
    public Model sphere(float radius, int divisions);
    public Model sphere(String id, float radius, int slices, int stacks);
    public Model triangles(String id, float[] positions, int[] indices, float[] colors);
}

public final class MeshPart {
    public String id();
    public Mesh mesh();
    public PrimitiveTopology primitiveTopology();
    public int firstVertex();
    public int vertexCount();
    public int firstIndex();
    public int indexCount();
}

public interface Model extends Disposable {
    List<ModelNode> nodes();
    List<Material> materials();
    List<AnimationClip> animations();
    List<Skin> skins();
}

public interface ModelInstance {
    Model model();
    Matrix4 transform();
    void collectRenderables(RenderQueue3D queue);
}

public final class DefaultModelInstance implements ModelInstance {
    public DefaultModelInstance(Model model);
    public DefaultModelInstance transform(Matrix4 transform);
    public DefaultModelInstance nodeTransform(String nodeId, Matrix4 localTransform);
    public Matrix4 copyNodeTransform(String nodeId, Matrix4 out);
    public Matrix4 copyNodeModelTransform(String nodeId, Matrix4 out);
    public Matrix4 copyNodeWorldTransform(String nodeId, Matrix4 out);
    public Matrix4 nodeTransform(String nodeId);
    public Matrix4 nodeModelTransform(String nodeId);
    public Matrix4 nodeWorldTransform(String nodeId);
    public DefaultModelInstance resetNodeTransforms();
    public boolean hasNode(String nodeId);
}

public final class ModelNode {
    public String id();
    public Matrix4 localTransform();
    public List<ModelNode> children();
    public List<ModelNodePart> parts();
}

public final class ModelNodePart {
    public MeshPart meshPart();
    public Material material();
    public int[] bones();
    public Skin skin();
    public int[] joints();
    public float[] weights();
}

public final class Renderable3D {
    public MeshPart meshPart();
    public Material material();
    public Matrix4 worldTransform();
    public BoundingBox bounds();
    public SkinningPalette skinningPalette();
}

public interface RenderQueue3D {
    void clear();
    void add(Renderable3D renderable);
    int size();
    Renderable3D get(int index);
    void sort(Camera camera);
    List<Renderable3D> renderables();
}

public interface Material {
    String id();
    MaterialAlphaMode alphaMode();
    boolean doubleSided();
    ShaderProvider3D shaderProvider();
}

public final class PbrMaterial implements Material {
    public Color baseColor();
    public Texture baseColorTexture();
    public float metallicFactor();
    public float roughnessFactor();
    public Texture metallicRoughnessTexture();
}

public final class ShaderMaterial implements Material {
    public String id();
    public MaterialAlphaMode alphaMode();
    public boolean doubleSided();
    public ShaderProvider3D shaderProvider();
}

public enum MaterialAlphaMode {
    OPAQUE,
    MASK,
    BLEND
}

public interface Shader3D extends Disposable {
    boolean canRender(Renderable3D renderable);
    void begin(RenderContext3D context);
    void render(Renderable3D renderable);
    void end();
}

public interface ShaderProvider3D {
    Shader3D shader(Renderable3D renderable, RenderContext3D context);
}

public final class PbrShaderProvider implements ShaderProvider3D, Disposable {
    public PbrShaderProvider(GraphicsContext graphics);
    public PbrShaderProvider(GraphicsContext graphics, PbrShaderConfig config);
    public Shader3D shader(Renderable3D renderable, RenderContext3D context);
}

public final class PbrShaderConfig {
    public PbrShaderConfig maxLights(int maxLights);
    public PbrShaderConfig maxBones(int maxBones);
    public PbrShaderConfig enableShadows(boolean enabled);
    public PbrShaderConfig enableImageBasedLighting(boolean enabled);
}

public final class RenderContext3D {
    public GraphicsContext graphics();
    public Camera camera();
    public Environment3D environment();
    public RenderTarget3D target();
    public RenderPass pass();
}

public final class Environment3D {
    public Environment3D ambientColor(Color ambientColor);
    public Environment3D add(Light light);
    public Environment3D clearLights();
    public Environment3D skyEnvironment(SkyEnvironment3D skyEnvironment);
    public Environment3D clearSkyEnvironment();
    public Environment3D directionalShadowMap(DirectionalShadowMap3D shadowMap);
    public Environment3D cascadedShadowMap(CascadedShadowMap3D shadowMap);
    public Environment3D clearDirectionalShadowMap();
    public Environment3D clearCascadedShadowMap();
    public Environment3D fog(Color fogColor, float startDistance, float endDistance);
    public Environment3D fog(float red, float green, float blue, float alpha,
            float startDistance, float endDistance);
    public Environment3D clearFog();
    public Color ambientColor();
    public SkyEnvironment3D skyEnvironment();
    public List<Light> lights();
    public DirectionalShadowMap3D directionalShadowMap();
    public CascadedShadowMap3D cascadedShadowMap();
    public boolean fogEnabled();
    public Color fogColor();
    public float fogStartDistance();
    public float fogEndDistance();
}

public final class DirectionalShadowMap3D implements Disposable {
    public DirectionalShadowMap3D(GraphicsContext graphics, int width, int height);
    public DirectionalShadowMap3D bounds(float centerX, float centerY, float centerZ,
            float halfSize, float near, float far);
    public DirectionalShadowMap3D bias(float bias);
    public DirectionalShadowMap3D strength(float strength);
    public void render(DirectionalLight light, ModelInstance[] instances);
    public void render(DirectionalLight light, Iterable<? extends ModelInstance> instances);
    public Texture texture();
    public Matrix4 lightViewProjection();
    public float bias();
    public float strength();
}

public final class CascadedShadowMap3D implements Disposable {
    public CascadedShadowMap3D(GraphicsContext graphics, int cascadeCount, int width, int height);
    public CascadedShadowMap3D splitLambda(float splitLambda);
    public CascadedShadowMap3D padding(float padding);
    public CascadedShadowMap3D maxDistance(float maxDistance);
    public CascadedShadowMap3D clearMaxDistance();
    public CascadedShadowMap3D bias(float bias);
    public CascadedShadowMap3D minTexelBias(float texels);
    public CascadedShadowMap3D strength(float strength);
    public CascadedShadowMap3D update(Camera viewCamera);
    public void render(DirectionalLight light, Camera viewCamera, ModelInstance[] instances);
    public void render(DirectionalLight light, Camera viewCamera,
            Iterable<? extends ModelInstance> instances);
    public int cascadeCount();
    public DirectionalShadowMap3D cascade(int index);
    public DirectionalShadowMap3D activeShadowMap();
    public float cascadeBias(int index);
    public Vector3 viewCameraPosition();
    public Vector3 viewCameraDirection();
    public Vector3 viewCameraUp();
    public float viewCameraNear();
    public float viewCameraFar();
    public float viewCameraTanHalfFov();
    public float viewCameraAspect();
    public float splitDistance(int index);
    public float cascadeCenterX(int index);
    public float cascadeCenterY(int index);
    public float cascadeCenterZ(int index);
    public float cascadeHalfSize(int index);
    public float splitLambda();
    public float padding();
    public float maxDistance();
    public float minTexelBias();
}

public interface Light {
    Color color();
    float intensity();
}

public final class DirectionalLight implements Light {
    public Vector3 direction();
}

public final class PointLight implements Light {
    public Vector3 position();
    public float range();
}

public final class SpotLight implements Light {
    public Vector3 position();
    public Vector3 direction();
    public float range();
    public float innerConeDegrees();
    public float outerConeDegrees();
}

public final class AnimationClip {
    public AnimationClip(String id, float durationSeconds);
    public AnimationClip(String id, float durationSeconds,
            AnimationClip.NodeTransformChannel[] nodeTransformChannels);
    public static AnimationClip.NodeTransformChannel nodeTransform(String nodeId,
            AnimationClip.TransformKeyframe... keyframes);
    public static AnimationClip.TransformKeyframe keyframe(float timeSeconds,
            float translationX, float translationY, float translationZ);
    public static AnimationClip.TransformKeyframe keyframe(float timeSeconds,
            float translationX, float translationY, float translationZ,
            float rotationX, float rotationY, float rotationZ, float rotationW,
            float scaleX, float scaleY, float scaleZ);
    public String id();
    public float durationSeconds();
    public AnimationClip.NodeTransformChannel[] nodeTransformChannels();

    public static final class NodeTransformChannel {
        public NodeTransformChannel(String nodeId, AnimationClip.TransformKeyframe... keyframes);
        public String nodeId();
        public AnimationClip.TransformKeyframe[] keyframes();
        public Matrix4 sample(float timeSeconds, Matrix4 out);
    }

    public static final class TransformKeyframe {
        public TransformKeyframe(float timeSeconds,
                float translationX, float translationY, float translationZ,
                float rotationX, float rotationY, float rotationZ, float rotationW,
                float scaleX, float scaleY, float scaleZ);
        public float timeSeconds();
        public Matrix4 toMatrix(Matrix4 out);
    }
}

public final class Skeleton {
    List<Bone> bones();
}

public final class Bone {
    String id();
    int parentIndex();
    Matrix4 inverseBindTransform();
}

public final class Skin {
    String id();
    Skeleton skeleton();
}

public final class SkinningPalette {
    public SkinningPalette(Skin skin);
    public SkinningPalette update(DefaultModelInstance instance);
    public Skin skin();
    public int size();
    public Matrix4 boneMatrix(int index);
    public Matrix4 copyBoneMatrix(int index, Matrix4 out);
    public float[] values();
    public float[] copyValues(float[] out);
    public float[] copyValues(float[] out, int offset);
}

public final class CpuSkinningMeshUpdater {
    public CpuSkinningMeshUpdater(GraphicsContext graphics, Mesh mesh, int[] joints, float[] weights);
    public CpuSkinningMeshUpdater update(SkinningPalette palette);
}

public final class CpuSkinnedModelAnimator {
    public CpuSkinnedModelAnimator(GraphicsContext graphics, DefaultModelInstance instance);
    public CpuSkinnedModelAnimator play(AnimationClip clip, boolean looping);
    public CpuSkinnedModelAnimator time(float timeSeconds);
    public CpuSkinnedModelAnimator update(float deltaSeconds);
    public CpuSkinnedModelAnimator stop();
    public CpuSkinnedModelAnimator updateSkinning();
    public DefaultModelInstance instance();
    public AnimationController controller();
    public int skinCount();
    public int skinnedPartCount();
}

public final class MorphTarget {
    String id();
    float weight();
}

public final class AnimationController {
    public ModelInstance instance();
    public AnimationClip clip();
    public float timeSeconds();
    public AnimationController play(AnimationClip clip, boolean looping);
    public AnimationController time(float timeSeconds);
    public AnimationController update(float deltaSeconds);
    public AnimationController stop();
}

public interface RenderTarget3D {
    int width();
    int height();
    TextureView colorAttachment(int index);
    TextureView depthAttachment();
    int colorAttachmentCount();
}

public interface RenderPath3D extends Disposable {
    void render(Batch3D batch, Camera camera, Environment3D environment,
            Iterable<? extends ModelInstance> instances);
}

public interface RenderGraph3D extends Disposable {
    RenderTarget3D target(String name);
    void render(Camera camera, Environment3D environment, Iterable<? extends ModelInstance> instances);
}

public final class G3DAssetLoaders {
    public static void register(AssetManager assets, GraphicsContext graphics);
}
```

Example:

```java
RenderPass pass = encoder.beginRenderPass(renderPassDescriptor);
modelBatch.begin(pass, camera);
modelBatch.environment(environment);
modelBatch.render(sceneInstances);
modelBatch.end();
pass.end();
```

Rules:

- `g3d` should use `graphics/api`, not provider-specific graphics types.
- `Batch3D` is the common model/renderable submission contract. `ModelBatch` is the first implementation.
- The first `ModelBatch` source slice renders static position/color meshes through reusable `Buffer`, `ShaderModule`, and `RenderPipeline` objects. The default `PbrShaderProvider` also owns metallic-roughness PBR paths for `Mesh.PBR_LAYOUT` and `Mesh.PBR_SKINNED_LAYOUT` mesh data and creates its built-in shader modules from WGSL `ShaderModuleDescriptor` values plus explicit setup-time reflection metadata.
- `ModelBuilder` creates simple primitive models and custom triangle meshes using the current position/color renderer path.
- `G3DAssetLoaders.register(...)` installs the initial glTF loader. The current glTF slice supports glTF 2.0 `.gltf`/`.glb` triangle meshes with `POSITION`, optional `NORMAL`, optional `TEXCOORD_0`, optional `COLOR_0`, optional indices, node hierarchy/local transforms, skins, `JOINTS_0`/`WEIGHTS_0`, inverse bind matrices, LINEAR translation/rotation/scale animation channels, metallic-roughness factors, and base-color, metallic-roughness, normal, occlusion, and emissive textures. Morph targets, non-LINEAR animation interpolation, sparse accessors, and broader material policies are later slices.
- The default `PbrShaderProvider` consumes ambient light, an optional `SkyEnvironment3D`, the first directional light, the first four point lights, and the first four spot lights from `Environment3D`. Point lights use distance falloff from `PointLight.position()` and `PointLight.range()`. Spot lights use distance falloff plus cone falloff from `SpotLight.position()`, `SpotLight.direction()`, `SpotLight.range()`, `SpotLight.innerConeDegrees()`, and `SpotLight.outerConeDegrees()`. These default light rules share the same behavior in the GPU WGSL PBR path and CPU-projected fallback path.
- `Environment3D.directionalShadowMap(...)` stores a non-owning reference to a `DirectionalShadowMap3D`. `Environment3D.cascadedShadowMap(...)` stores a non-owning reference to a `CascadedShadowMap3D`. The application owns disposal and should render the selected shadow helper before the main `ModelBatch` pass each frame. When a non-disposed cascaded shadow map is present, the default WGSL PBR path uploads up to four cascade view-projection matrices, split distances, computed per-cascade biases, the cascade driver camera, and shadow textures, then selects the cascade per fragment from that driver camera. If no cascaded map is present, the PBR path falls back to the single `DirectionalShadowMap3D` binding.
- `DirectionalShadowMap3D.bias(...)` is a direct normalized depth comparison bias. `CascadedShadowMap3D.bias(...)` is a base world-space bias; each cascade converts it to normalized depth bias with a minimum texel-sized floor from `minTexelBias(...)`.
- `Environment3D` distance fog is disabled by default. `fog(color, startDistance, endDistance)` enables linear distance fog for the default `PbrShaderProvider`; `color.alpha()` is the maximum fog amount. `clearFog()` disables it. Distances are measured from the active `Camera` position to each shaded world position, and the same environment state is used by both the GPU PBR shader path and the CPU-projected fallback path.
- `Environment3D.skyEnvironment(...)` stores a non-owning `SkyEnvironment3D` reference. The default `PbrShaderProvider` samples that procedural sky analytically as IBL-style diffuse irradiance plus roughness-aware specular reflection, including a sun reflection lobe from `sunDirection(...)`, `sunColor(...)`, and `sunIntensity(...)`. This is provider-neutral and does not require cubemap texture support. Future texture-backed cubemap/IBL support should add explicit texture/environment-map types and may use the same environment slot as its public entry point.
- `OutlineRenderer3D` owns a WGSL shader module and cached render pipelines. It renders `Mesh.PBR_LAYOUT` renderables by expanding vertex positions along normals in world space. The intended first-pass usage is to draw the outline with a clear or loaded color attachment, then draw the normal `ModelBatch` pass with `LoadOp.load()` so the model covers the shell center.
- `FogOfWarRenderer3D` owns a WGSL shader module and streams reusable overlay vertices into common `Buffer` objects. It projects horizontal world-space rectangles through the supplied `Camera`, draws them after the main scene with `LoadOp.load()`, and uses world-space reveal spheres from `light(x, y, z, radius, softness)`. At most `FogOfWarRenderer3D.MAX_LIGHTS` reveal spheres are submitted per draw call. It is independent from `Environment3D` distance fog and does not mutate the default PBR uniform layout.
- `BillboardRenderer3D` owns a WGSL shader module and streams reusable camera-facing textured quad vertices into common `Buffer` objects. It uses `Texture` directly instead of `graphics/g2d.TextureRegion` so `g3d` remains independent from `g2d`; atlas users can pass normalized `u`, `v`, `u2`, and `v2` coordinates explicitly. The renderer depth-tests billboards and disables depth writes. For scene occlusion, draw it inside the same depth-enabled `RenderPass` as `ModelBatch` by using `begin(RenderPass)`.
- `ParticleEmitter3D` is provider-neutral particle simulation data plus a `BillboardRenderer3D` draw helper. It owns fixed-capacity primitive arrays, does not allocate per particle, updates with explicit `deltaSeconds`, and renders through a caller-owned, already-begun `BillboardRenderer3D`. Rendering resets the billboard color to white after submitting active particles.
- `SkyboxRenderer3D` owns a WGSL shader module and renders a procedural sky from world-space view rays into the active color attachment. The sky rotates correctly with the camera orientation because each fragment is shaded from its world direction, and `sunDirection(...)` anchors the sun in world space. `sunPosition(...)` remains a normalized-sky-coordinate convenience for simple demos. It should normally draw before `ModelBatch`, then the model pass should use `LoadOp.load()` so geometry appears over the sky. It is a background renderer; use `SkyEnvironment3D` on `Environment3D` when the same sky should affect PBR lighting.
- `AnimationClip` currently owns provider-neutral node transform channels. `AnimationController` samples translation, quaternion rotation, and scale keyframes into instance-local `DefaultModelInstance` node transforms without per-frame channel-array allocation. `DefaultModelInstance` exposes allocation-free copy methods for local, model-space, and world-space node transforms; convenience getters return copies.
- `SkinningPalette` prepares bone matrices from a `Skin` and an animated `DefaultModelInstance`. Each bone matrix is `boneModelTransform * inverseBindTransform` in model space, which lets the renderer apply the model transform once. The palette reuses `Matrix4` objects and packed float storage across `update(...)` calls.
- `ModelNodePart` may carry an imported `Skin` plus four joint indices and four joint weights per expanded mesh vertex. `bones()` is a compatibility alias for `joints()` until a broader animation API cleanup removes the old name. `DefaultModelInstance` keeps one `SkinningPalette` per `Skin`, updates those palettes when node transforms change, and exposes the matching palette through each skinned `Renderable3D`.
- `Mesh.PBR_SKINNED_LAYOUT` appends joint and weight `vec4f` attributes to the normal PBR vertex data. glTF primitives with `JOINTS_0` and `WEIGHTS_0` use this layout so the default WGSL PBR path can skin vertices on the GPU when the selected provider has the PBR uniform-block path.
- `PbrShaderProvider` uploads skinned renderable palettes as a fixed 64-matrix uniform array and applies them in the WGSL vertex shader. `PbrShaderConfig.maxBones(...)` may lower that limit per shader. The current Java mappings cover GL/GLES/WebGL, WGPU, Vulkan, and iOS C Metal render passes. iOS C Metal can receive the PBR uniform block and depth state when MSL is available, but the iOS C backend still does not register a runtime shader compiler provider, so WGSL-only built-in shaders require a future iOS compiler bridge there.
- `CpuSkinningMeshUpdater` is a compatibility and validation path that rewrites an existing `Mesh.PBR_LAYOUT`, `Mesh.PBR_SKINNED_LAYOUT`, or `Mesh.POSITION_COLOR_LAYOUT` vertex buffer from retained source arrays, four joint indices per vertex, four joint weights per vertex, and a `SkinningPalette`. It reuses its staging byte buffer across updates.
- `CpuSkinnedModelAnimator` is the convenience CPU path for imported skinned models. It owns one `AnimationController`, caches one `SkinningPalette` per `Skin`, caches one `CpuSkinningMeshUpdater` per skinned `ModelNodePart`, and updates every skinned mesh with `play(...)`, `time(...)`, `update(...)`, or `updateSkinning()`.
- CPU skinned animation mutates the model mesh vertex buffers. It is appropriate for compatibility, validation, and single-instance use of a loaded skinned model. Independent simultaneous animation of several instances that share one `Model` should use the GPU skinning path or cloned meshes for the CPU path.
- This is node-pose playback, skin-palette preparation, glTF skin/animation import, CPU mesh-update compatibility, and a first WGSL GPU skinning path for PBR meshes. Morph targets, non-LINEAR glTF animation interpolation, and broader material policies remain later layers.
- `g3d` should keep model loading, materials, PBR data, custom shaders, animation, lighting, frame targets, render paths, and rendering helpers in one user-facing artifact.
- Provider-specific rendering paths can exist internally, but normal user code should not need provider-specific graphics classes.
- `ModelBatch` should batch by shader key, material state, mesh, primitive topology, vertex layout, and render target. It should sort opaque renderables for state locality and depth efficiency, sort transparent renderables back-to-front, and keep stable ordering where required.
- `ModelBatch` should use API-neutral performance features through `graphics/api`: immutable/static mesh buffers, dynamic uniform or storage buffers, per-context pipeline caches, material/shader variant caches, texture and sampler binding reuse, instancing for repeated meshes, GPU skinning where supported, and clear fallbacks where a provider lacks an optimization.
- Later PBR slices should broaden material policy and lighting support, including alpha mode, double-sided state, texture-backed cubemap/environment maps, shadow quality, instancing, morph targets, and render-target integration.
- Custom shaders should plug in through `ShaderProvider3D` and still receive standard camera, model, material, light, animation, and render-target inputs through `RenderContext3D`.
- Framebuffer and future multi-render-target support belongs in `graphics/api`; `g3d` render paths should consume those targets for capture, shadow maps, G-buffers, reflection/environment captures, post-processing, and user-created offscreen passes.
- Animation should support node transforms first, then skeletal skinning and morph targets. CPU skinning may exist as a compatibility fallback, but GPU skinning should be the optimized default when the selected provider can support it.
- The public API should prefer immutable descriptors and reusable render objects in hot paths. Per-frame submission should avoid object allocation once models, materials, shaders, and queues have been created.

## 16. UI Kit

Module:

```text
:libfdx:ui:ui-kit
```

Package:

```text
io.github.libfdx.ui
```

`ui-kit` is a libfdx-owned UI toolkit. The detailed specification lives in [UI_KIT.md](UI_KIT.md).

Defined types:

| Type | Role |
| --- | --- |
| `UiToolkit` | Factory for roots, themes, shared UI resources, and default renderer setup. |
| `UiRoot` | Retained UI root, composition owner, input dispatch, focus, layout, rendering, and disposal. |
| `UiScope` | Declarative builder passed to UI content lambdas. |
| `UiModifier` | Immutable layout, drawing, input, and behavior options. |
| `UiState<T>`, `UiBooleanState`, `UiIntState`, `UiFloatState`, `UiLongState`, `UiDoubleState` | Explicit observable state used by UI content. Primitive state uses dedicated classes to avoid boxed primitive wrappers. |
| `UiContent` | Functional interface for root content. |
| `UiNode` | Retained node handle for advanced cases, debugging, and custom widgets. |
| `UiTheme` | Defaults for colors, fonts, drawables, spacing, and widget styles. |
| `UiStyle` | Widget style values. |
| `UiDrawable` | Renderable background/foreground value. |
| `UiNinePatch` | Ninepatch metadata and drawable construction. |
| `UiAnimationSpec`, `UiEasing` | Animation timing, delay, repeat, and interpolation descriptors. |
| `UiTransition`, `UiAnimatable<T>`, `UiFloatAnimatable` | Retained state-driven transitions and custom animated values. Primitive scalar animation uses `UiFloatAnimatable`. |
| `UiTextStyle`, `UiFont` | Text styling, font selection, measurement, and localization-ready text rendering. |
| `UiLayer`, `UiPopup`, `UiModal`, `UiTooltip` | Ordered UI layers, popups, modal input blocking, and anchored tooltips. |
| `UiWindowState` | Retained position and size for movable and resizable UI windows. |
| `UiFocusScope`, `UiNavigation` | Keyboard/gamepad focus scopes and navigation rules. |
| `UiListState`, `UiScrollState` | Retained scroll and virtualized list/grid state. `UiScrollState` supports vertical range visibility checks and scroll-into-view behavior that does not move already-visible content. |
| `UiTextAreaOptions` | Text-area sizing policy for fixed-height and bounded auto-grow behavior. |

### 16.1. UI Kit Contracts

`ui-kit` uses Compose-inspired declarative authoring over a retained runtime. User code describes UI from explicit state; `UiRoot` reconciles that description into persistent nodes that own focus, hover, pressed state, scroll positions, text editing state, animations, layout, and rendering.

Target authoring shape:

```java
UiToolkit toolkit = new UiToolkit(fdx.files());
UiRoot root = toolkit.root(fdx.displays().main(), fdx.graphics().main());
UiBooleanState fullscreen = Ui.state(false);

root.setContent(ui -> {
    ui.column(Ui.modifier().fill().padding(16).gap(8), column -> {
        column.text("Settings");
        column.checkbox("Fullscreen", fullscreen);
        column.button("Apply", this::applySettings);
    });
});

root.update(fdx.app().deltaTime());
root.render();
```

Rules:

- All public `ui-kit` types must use the `Ui` prefix.
- `ui-kit` is plain Java: no annotations, no reflection, no compiler plugin, and no generated source requirement.
- `ui-kit` is user-created and must not add a `ui()` accessor to `Fdx`.
- Primitive UI state must use dedicated primitive classes such as `UiBooleanState`, `UiIntState`, `UiFloatState`, `UiLongState`, and `UiDoubleState`, not `UiState<Boolean>`, `UiState<Integer>`, or `UiState<Float>`.
- `ui-kit` uses runtime display/input APIs for routing and `graphics/g2d` for rendering.
- `UiRoot` is the public root update/render entry point for normal UI rendering.
- `UiRoot.uiScale(...)` scales logical UI units for DPI and accessibility. `autoUiScale(true)` multiplies the root scale by `Display.contentScale()` so high-DPI desktops, browser device-pixel-ratio displays, and mobile density are handled through the runtime display API.
- Low-level retained nodes and event listeners may exist for advanced/custom widgets, but the normal authoring style should be declarative `UiScope` calls.
- Ninepatch styling is first-class through `UiNinePatch` and `UiDrawable`.
- Animation is first-class through retained animation state, `UiAnimationSpec`, `UiEasing`, `UiTransition`, `UiAnimatable<T>`, and `UiFloatAnimatable`.
- `UiFont` supports direct `BitmapFont` instances, `.fnt` bitmap font files, and `.ttf`/`.otf` FreeType font assets. Family-font descriptors are allowed API values, but default family rasterization must fail clearly until a backend-specific system-font provider exists. UI rendering measures text through `g2d` bitmap font layout before drawing.
- `UiRoot.update(deltaTime)` advances animations deterministically before layout and rendering.
- Text, font, localization, layers, modals, popups, tooltips, keyboard/gamepad navigation, virtualized lists, and drag/drop are part of the game UI target, not unrelated add-ons.
- `UiTooltip.text()` matches the hovered node text or semantic label by default. `UiModifier.tooltipTarget(String)` assigns an explicit tooltip key for targets whose displayed text differs from the tooltip key, targets with no visible text, and text nodes that must be hover-addressable. Delayed tooltips must request a new composition when the hover delay elapses.
- Built-in input widgets include text fields with pointer selection and root-local copy/cut/paste shortcuts, plus text areas for multiline written text with internal scroll state, touch-drag scrolling, and optional bounded auto-grow.
- Rows, columns, stacks, grids, scroll containers, panels, movable windows, and spacers are the primary layout vocabulary.
- Built-in selection widgets include tabs backed by a `UiIntState` active index.
- Built-in display widgets include progress bars backed by fixed values or `UiFloatState` ranges.
- `ui-kit` is not the common API for every possible UI solution.

## 17. Scenario Validator

### 17.1. Scenario Validator Contracts

Core module:

```text
:libfdx:validation:scenario-validator
```

Core package:

```text
io.github.libfdx.validation.scenario
```

UI Kit adapter module:

```text
:libfdx:validation:scenario-validator-ui-kit
```

UI Kit adapter package:

```text
io.github.libfdx.validation.scenario.ui.kit
```

`scenario-validator` is an optional public validation engine for complete runtime scenarios. The detailed contract lives in [SCENARIO_VALIDATOR.md](SCENARIO_VALIDATOR.md). It is reusable by libfdx's own tests, samples, tools, benchmark modules, and user projects, but it is not required for normal runtime execution or rendering. UI Kit validation is an optional adapter capability, not the whole validator.

Defined types:

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

Rules:

- `scenario-validator` is not UI-only and not game-only.
- `scenario-validator` depends on portable runtime/input/display concepts as needed, not on backend implementations, platform-specific launchers, UI Kit, JUnit, or external test frameworks.
- `scenario-validator-ui-kit` depends on `scenario-validator` and `ui-kit`.
- Normal runtime execution and normal UI rendering must not depend on scenario validation modules.
- Built-in actions, waits, and assertions cover normal runtime behavior flows, including input, wait-for-event, capture, probe, and adapter validation.
- Custom callbacks are escape hatches for project-specific behavior that cannot be expressed by built-in operations, adapters, captures, or probes.
- Scenario catalogs must not require project-specific driver interfaces or adapter implementations before a developer can write a scenario.
- UI Kit validation IDs are stable developer-facing test/debug identifiers, not user-facing text.
- UI Kit validation IDs must not affect layout, rendering, input, focus, accessibility, or normal runtime behavior.
- Waits must always have a timeout. They advance through frames and accumulated validation time, not sleeps or render-thread busy loops.
- Event waits consume scenario-local validation events. Engine-emitted events are allowed only when they do not introduce normal per-frame allocation.
- Visual validation remains explicit. A capture task passing is not enough for visual work unless the expected frame is captured or compared according to the active validation plan.
- Failure reports name the scenario, operation, action/wait/assertion/capture/custom callback, target selector or probe type, expected value, actual value, elapsed wait time, recent events, and capture/baseline paths when relevant.

## 18. Initial API Decisions

These decisions are part of the common API contract:

- Use `FdxFuture<T>` for portable async APIs.
- Use `runtime/fdx/core` as the shared framework runtime service layer. Its first default service is FreeType font rasterization for `.ttf`/`.otf` assets. Backends provide the platform implementation; higher-level APIs consume it and keep rendering cached atlas data every frame.
- Use `HttpClient` as the HTTP entry point type.
- Keep `AudioSource` as the advanced persistent playback source/channel type. Basic playback should still use `Sound`, `Music`, and `PlaybackHandle`.
- Use descriptor names ending in `Descriptor` for graphics creation inputs, such as `TextureDescriptor`, `BufferDescriptor`, and `RenderPipelineDescriptor`.
- Include `ShaderLanguage`, `ShaderProfile`, `ShaderTarget`, and `ShaderBundle` from the start. WGSL is the only shader authoring source of truth. GL/WebGL/GLES translate WGSL to GLSL/GLSL ES through Tint, Vulkan translates WGSL to SPIR-V through Tint, Metal translates WGSL to MSL through Tint, and HLSL is a future DirectX target.
- Keep `TextureView` as a required common graphics type. Advanced view behavior is capability-gated.

## 19. Runtime Core

Module:

```text
:libfdx:runtime:fdx:core
```

Package:

```text
io.github.libfdx.runtime.core
io.github.libfdx.runtime.core.shader
```

`runtime/fdx/core` is the shared framework runtime service layer. It is not a user-created feature object and it is not a generic service locator. It provides small framework-wide services that common modules can use by default when the selected backend/platform has supplied the implementation.

Initial scope:

- FreeType font rasterization for `.ttf` and `.otf` assets.
- Optional WGSL shader compilation for platforms/backends that package the runtime `fdx` compiler capability.

Future possible scope:

- Native Matrix4 or fast-math helpers.
- Native image decode helpers if the common image loader needs them.
- Compression or memory helpers when they become framework-wide runtime services.

Defined types:

The shader compiler contract types live in `io.github.libfdx.runtime.core.shader`.

| Type | Role |
| --- | --- |
| `RuntimeCore` | Static framework runtime fdx access point and provider registration hook used by backend/platform wiring. |
| `RuntimeCoreProvider` | Backend/platform provider contract for runtime fdx services. |
| `FontRasterizer` | Rasterizes font bytes into glyph metrics and atlas pixels. |
| `FontRasterizerOptions` | Size, character set, padding, and atlas width for font rasterization. |
| `RasterizedFont` | Rasterized font result: atlas pixels, glyph metrics, line height, baseline, and kerning. |
| `RasterizedGlyph` | One glyph's atlas region and layout metrics. |
| `RuntimeShaderCompiler` | Optional WGSL compiler service used by providers, tools, and editors when a runtime can translate shaders. |
| `RuntimeShaderCompileRequest` | WGSL source, target, stage, entry point, and profile options for one compilation. |
| `RuntimeShaderCompileResult` | Compilation success flag, output kind, output bytes, and diagnostics. |
| `RuntimeShaderCompileTarget`, `RuntimeShaderCompileStage`, `RuntimeShaderCompileOutputKind` | Provider-neutral shader compiler values. |
| `RuntimeCoreException` | Clear framework exception for runtime fdx failures. |

Rules:

- Normal game code should not call low-level native runtime bindings directly.
- `UiFont.freeType(...)` and `BitmapFontFiles.loadFreeType(...)` are the normal public path for FreeType fonts.
- Font rasterization happens when a font is loaded or cached, not during every UI render frame.
- UI and g2d rendering consume cached `BitmapFont` atlas data after rasterization.
- Backends must register a platform-specific `RuntimeCoreProvider` before code loads `.ttf`/`.otf` fonts through `UiFont.freeType(...)` or `BitmapFontFiles.loadFreeType(...)`.
- Shader compilation happens when a shader module is created, an editor explicitly recompiles, or tooling validates a shader. It must not happen inside a frame loop.
- Providers that consume WGSL directly do not require the shader compiler capability. Providers that need GLSL, SPIR-V, or MSL request the capability for normal built-in renderer shaders because those descriptors are WGSL-only.
- Platforms that do not need shader translation, such as PSP, must not be forced to package the compiler capability.
- If no platform provider is registered, `runtime/fdx/core` must fail clearly. Do not silently fall back to a Java rasterizer or any non-FreeType implementation.
- Desktop JVM registers a provider backed by LWJGL FreeType and may also expose the native runtime shader compiler when the selected desktop `fdx` native library includes it. Desktop C and Android register providers backed by the runtime fdx C/C++ bridge linked with FreeType and shader compiler dependencies imported from `fdx-natives` prebuilt packages. Web registers a provider backed by the runtime fdx Emscripten JS/WASM bridge and enables the shader compiler by default so WebGL can translate WGSL-only built-in shaders. iOS C currently registers no runtime shader compiler provider.
- Native bridge source is owned by `runtime/fdx/platform/*`, not by the Java core module. The core module owns Java contracts. `fdx_shared` packages the shared native source payload needed by TeaVM/native project generation, and `fdx_desktop`, `fdx_android`, and `fdx_web` package platform native outputs generated by the internal `fdx-build` module.
- Third-party FreeType and Tint/Dawn source are not committed into the repository and are not built by libFDX native tasks. `:libfdx:runtime:fdx:fdx-build` links checksum-verified static dependency packages from `fdx-natives`.
- Platform-specific native resources generated for the core JAR are scoped under `libfdx-native/<platform>/...` in generated resource output.
- The shared runtime fdx FreeType bridge source is scoped under `runtime/fdx/platform/shared/src/main/cpp/runtime_fdx/...` until a platform needs a separate bridge source.
- Native implementation code should be reused across platforms. Platform-specific code should be limited to small ABI adapters, not duplicated copies of the same FreeType rasterizer.
- Runtime fdx native artifacts use core framework names. Web runtime fdx output is `fdx.js` plus `fdx.wasm`; shared-library outputs should use the platform's `fdx` library filename convention, such as `fdx.dll` or `libfdx.so`, even when the first exported service is FreeType.
- `:libfdx:runtime:fdx:fdx-build` is responsible for compiling and linking runtime fdx native outputs. Backend/platform package modules are responsible for copying or loading the matching platform resources.
