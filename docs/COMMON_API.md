# libFDX Common API

This document defines the provider-neutral public API contracts for libfdx-owned modules.

Use this document to decide what a common API type means, what module owns it, and what behavior provider implementations must support. Use [ARCHITECTURE.md](ARCHITECTURE.md) to decide folder layout, Gradle module names, Maven artifact names, dependency direction, and package roots.

## Index

1. [Goals](#1-goals)
2. [API Source Of Truth](#2-api-source-of-truth)
3. [Common API Rules](#3-common-api-rules)
4. [Naming Rules](#4-naming-rules)
5. [Core](#5-core)
    1. [Core Base Contracts](#51-core-base-contracts)
    2. [Foundation Math Types](#52-foundation-math-types)
    3. [Foundation JSON Types](#53-foundation-json-types)
6. [Application](#6-application)
    1. [ApplicationListener Contract](#61-applicationlistener-contract)
    2. [Fdx Runtime Root Contract](#62-fdx-runtime-root-contract)
    3. [Application Service Contract](#63-application-service-contract)
    4. [ApplicationBackend Contract](#64-applicationbackend-contract)
    5. [ApplicationConfig Contract](#65-applicationconfig-contract)
7. [Files](#7-files)
    1. [FileSystem And FileHandle Contracts](#71-filesystem-and-filehandle-contracts)
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
| `Logger` | Logging facade independent from a concrete logging implementation. |
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
- `Logger` is returned by `Fdx.logger()` so applications and framework modules can share the same logging facade.
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
| `Json` | Convenience facade for reading/writing JSON and using registered manual codecs. |
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

`ApplicationListener` is implemented by the user's game/application class. The backend creates a typed `Fdx` root, attaches the selected backend-owned runtime systems, and passes it to `create()`. `render()` is the only per-frame callback; frame timing is read from the `Application` interface returned by `fdx.app()`.

Defined shape:

```java
public interface ApplicationListener {
    void create(Fdx fdx);
    void resize(int width, int height);
    void render();
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
- Asset loading should be designed so web implementations can be async.
- In `WebApplicationConfig`, a display width or height of `0` or a negative value means the canvas fills the browser window.
- Platform-specific native file handles should be reachable only through provider/backend-specific APIs.
- `FileSystem.as()` is the advanced access path for backend-specific filesystem services.
- `FileWatch` is provider-backed because file watching is implemented differently across platforms.

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
| `Gamepads` | Gamepad access facade backed by a gamepad provider. |
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

Package:

```text
io.github.libfdx.net
```

Defined types:

| Type | Role |
| --- | --- |
| `Network` | Main network service. |
| `HttpClient` | HTTP entry point. |
| `HttpRequest` | HTTP request descriptor. |
| `HttpResponse` | HTTP response data. |
| `HttpMethod` | GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS. |
| `HttpHeaders` | Header collection. |
| `HttpBody` | Request or response body abstraction. |
| `HttpStatus` | Response status code helper. |
| `WebSocketClient` | WebSocket connection entry point. |
| `WebSocketConfig` | WebSocket URL, headers, protocols, and connection options. |
| `WebSocket` | Active WebSocket connection. |
| `WebSocketListener` | WebSocket callback/listener contract. |
| `NetworkCapabilities` | Supported network features. |
| `NetworkProvider` | Provider/backend network SPI if needed. |

### 11.1. Network Contracts

`Network` is async-first so the same API works on desktop, web, Android, iOS, and C-backed targets.

Defined shape:

```java
public interface Network extends FdxService, ProviderHandle {
    NetworkCapabilities capabilities();
    HttpClient httpClient();
    WebSocketClient webSocketClient();
}

public interface NetworkProvider {
    ProviderId providerId();
    Network createNetwork();
}

public interface NetworkCapabilities {
    boolean supportsHttp();
    boolean supportsWebSocket();
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
```

Example:

```java
Network network = fdx.network();
HttpClient http = network.httpClient();

HttpRequest request = HttpRequest.get("https://example.com/status");
if (http != null) {
    http.send(request)
        .onSuccess(response -> logger.info("Status: " + response.status().code()));
}
```

Rules:

- Network APIs should be async-first.
- Do not design network APIs around blocking calls because browser/web targets cannot support that reliably.
- HTTP redirects, cookies, TLS details, streaming bodies, and custom transports should be capability-aware.
- WebSocket lifecycle should clearly define open, message, error, close, and dispose behavior.
- Backend-specific transport details should not leak into common request/response types.
- `Network.as()` is the advanced access path for backend/provider-specific network services.
- `Network.httpClient()` returns `null` when HTTP is not supported by the active backend/provider.
- `Network.webSocketClient()` returns `null` when WebSocket is not supported by the active backend/provider.
- `HttpRequest.body()` returns `null` for requests without a body.
- `HttpResponse.body()` returns `null` for responses without a body.
- `HttpHeaders.first(String name)` returns `null` when the header is not present.
- `NetworkProvider` is a provider/backend SPI used by backend setup and should not be registered as a normal `FdxService`.

Async shape:

```java
HttpClient http = network.httpClient();
WebSocketClient webSocket = network.webSocketClient();

if (http != null) {
    FdxFuture<HttpResponse> response = http.send(request);
}

if (webSocket != null) {
    FdxFuture<WebSocket> socket = webSocket.connect(config, listener);
}
```

Use `FdxFuture<T>` consistently across net, assets, and other common async APIs.

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
| `GraphicsContext` | Provider-backed rendering context/device facade used by game and rendering code. |
| `GraphicsConfig` | Configuration for creating an additional graphics context when supported. |
| `GraphicsAttachment` | Backend-driven graphics context that owns frame begin/end, resize, and presentation lifecycle for a display-backed context. |
| `GraphicsAttachmentReadiness` | Optional backend/provider marker for attachments that finish initialization asynchronously before game code can create graphics resources. |
| `GraphicsAttachmentProvider` | Launcher/backend setup factory for attaching a selected graphics provider to a backend-created display/native target. |
| `GraphicsAttachmentRequirements` | Provider-declared window/context requirements that the backend must apply before creating the presentation target. |
| `GraphicsEnvironment` | Provider-neutral setup view passed from a backend to a `GraphicsAttachmentProvider`. |
| `NativeWindow` | Backend-created native presentation handle/object bundle used only by provider setup code. |
| `NativeWindowPlatform` | Platform identifier for `NativeWindow` handle interpretation. |
| `GraphicsDevice` | Provider-backed device facade used by common code to create first rendering resources. |
| `GraphicsFrame` | Current backend-owned frame view exposed during `ApplicationListener.render()`. |
| `FrameBuffer` | Current frame drawable and readback view. |
| `Camera`, `CameraProjection` | Shared mutable camera state for orthographic and perspective projection. |

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
- `GraphicsContext.device()` returns a common device facade backed by the selected provider.
- `GraphicsContext.surfaceFormat()` returns the current presentation color format used for render pipeline creation.
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
- `Camera` is one mutable graphics API type. It must not split into separate 2D, 3D, orthographic, or perspective subclasses. A camera changes mode through `projection(CameraProjection)` and keeps the same instance identity.

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
| `ShaderModuleDescriptor` | Provider-facing shader source or bytecode plus shader language metadata. |
| `ShaderBundle` | WGSL source-of-truth plus generated target artifacts, reflection metadata, profile validation, and provider-target descriptor selection. |
| `ShaderReflection`, `ShaderBinding`, `ShaderAttribute` | Setup-time metadata for shader bindings and vertex inputs generated or declared with a shader bundle. |
| `RenderPassDescriptor` | Color/depth attachments, load/store operations, clear values. |
| `RenderPipelineDescriptor` | Shader module, entry points, target format, primitive topology, vertex layouts, sampled texture count, and debug label. |

Defined value/state types:

| Type | Role |
| --- | --- |
| `TextureFormat` | Portable texture/surface format. |
| `ShaderLanguage` | Shader source family. WGSL, GLSL, and SPIR-V are available in the first rendering slice. |
| `ShaderProfile` | WGSL portability profile: WebGL2-compatible, WebGPU-compatible, or provider-native. |
| `ShaderTarget` | Provider target language/output selection, such as WebGPU WGSL, OpenGL GLSL, WebGL/GLES GLSL ES, Vulkan SPIR-V, Metal MSL, or DirectX HLSL. |
| `ShaderStage`, `ShaderBindingType` | Shader metadata values for generated reflection and setup-time validation. |
| `ShaderValidationResult`, `ShaderValidationDiagnostic`, `ShaderValidationSeverity` | Build/setup-time shader profile validation result types. |
| `PrimitiveTopology` | Primitive assembly mode for first render pipelines. |
| `BufferUsage` | Portable buffer usage. The first implementation defines vertex and index buffers. |
| `TextureUsage` | Portable texture usage. The first implementation defines sampled textures. |
| `TextureWrap` | Portable sampled-texture coordinate wrap mode. |
| `VertexLayout`, `VertexStepMode`, `VertexAttribute`, `VertexFormat` | Portable vertex input layout for render pipelines. |
| `LoadOp`, `StoreOp` | Render pass attachment load/store behavior. |
| `GraphicsClientApi` | Backend window/client API mode, such as `NO_API`, `OPENGL`, or `VULKAN`. |
| `GraphicsContextProfile` | GL context profile request, such as core or compatibility. |

Descriptor construction helpers used by examples:

```java
public final class ShaderModuleDescriptor {
    public static ShaderModuleDescriptor wgsl(String label, String source);
    public static ShaderModuleDescriptor glsl(String label, String vertexSource, String fragmentSource);
    public static ShaderModuleDescriptor spirv(String label, int[] vertexWords, int[] fragmentWords);
    public ShaderModuleDescriptor wgsl(String source);
    public ShaderModuleDescriptor glsl(String vertexSource, String fragmentSource);
    public ShaderModuleDescriptor spirv(int[] vertexWords, int[] fragmentWords);
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
    public String glslVertexSource();
    public String glslFragmentSource();
    public String glslEsVertexSource();
    public String glslEsFragmentSource();
    public int[] spirvVertexWords();
    public int[] spirvFragmentWords();
    public String mslSource();
    public String hlslSource();
    public ShaderReflection reflection();
    public boolean hasTarget(ShaderTarget target);
    public ShaderValidationResult validateProfile();
    public ShaderModuleDescriptor descriptorForProvider(ProviderId providerId);
    public ShaderModuleDescriptor descriptorForProvider(String providerId);
    public ShaderModuleDescriptor descriptorForTarget(ShaderTarget target);

    public static final class Builder {
        public Builder profile(ShaderProfile profile);
        public Builder wgsl(String source);
        public Builder glsl(String vertexSource, String fragmentSource);
        public Builder glslEs(String vertexSource, String fragmentSource);
        public Builder spirv(int[] vertexWords, int[] fragmentWords);
        public Builder msl(String source);
        public Builder hlsl(String source);
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
    public RenderPipelineDescriptor primitiveTopology(PrimitiveTopology primitiveTopology);
    public RenderPipelineDescriptor vertexLayout(VertexLayout vertexLayout);
    public RenderPipelineDescriptor vertexLayouts(VertexLayout... vertexLayouts);
    public RenderPipelineDescriptor sampledTextureCount(int sampledTextureCount);
}
```

### 13.1. Graphics Provider Contract

The current graphics provider contract separates backend window ownership from graphics provider attachment. A backend creates the display and native window handles, then a graphics extension creates a `GraphicsAttachment` for that environment.

This shape lets desktop, desktop_native, web, Android, and iOS backends attach the same graphics provider without the provider depending on a concrete backend module.

Defined interface roles:

| Interface | What it is for | Why it is generic |
| --- | --- | --- |
| `GraphicsAttachmentProvider` | Entry point implemented by a graphics extension such as wgpu, GL, or Vulkan. | Every graphics family needs setup code that can attach to backend-owned presentation handles. |
| `GraphicsAttachmentRequirements` | Provider-declared context/window requirements. | WGPU needs no client API; desktop GL needs a GL context; future WebGL needs a web canvas path. |
| `GraphicsEnvironment` | Backend-provided setup values, currently `Display` and `NativeWindow`. | Providers need presentation metadata without importing backend classes. |
| `GraphicsAttachment` | Backend-driven graphics lifecycle object. | Backends own frame timing, resize, and presentation; providers own GPU work. |
| `Graphics` | Graphics manager returned by `Fdx.graphics()`. | Game code has one typed graphics entry point that can own one or more provider contexts. |
| `GraphicsContext` | Provider-backed rendering context. | Simple code uses `fdx.graphics().main()`; advanced code may create additional contexts when supported. |
| `GraphicsDevice` | Common facade for creating first low-level rendering resources. | Providers may map this to a native device, context, or device wrapper. |
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
- `ShaderModuleDescriptor` may contain multiple source-language variants for the same shader intent. Providers select a supported source variant; they should not pretend to support a language by silently translating through provider-specific hacks unless that translation is an explicit provider feature. Vulkan providers should prefer SPIR-V bytecode for predictable startup and portability to Android later.
- `ShaderBundle` is the common setup-time wrapper for WGSL-first shaders. It validates the WGSL profile when built, stores generated target artifacts, and returns the correct `ShaderModuleDescriptor` for the active provider through `descriptorForProvider(...)`.
- Runtime shader creation must not perform hidden WGSL-to-GLSL/SPIR-V/MSL/HLSL translation. Translation belongs to build tooling, checked-in generated bootstrap code, or an explicitly documented provider feature. Missing generated output for the active provider is a setup error.
- A shader that passes WebGPU/WGSL validation is not automatically portable to WebGL/OpenGL ES. Use `ShaderProfile.PORTABLE_WEBGL2` for shaders that must run on WebGL2/GLES-style targets and `ShaderProfile.PORTABLE_WEBGPU` for shaders that only need modern WebGPU/wgpu-class targets.
- Metal and DirectX should be added as generated `ShaderTarget` outputs from the same WGSL source contract. They should not require a second authoring language unless the shader declares `ShaderProfile.NATIVE` and the owning module documents the native-only behavior.
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
- `TextureDescriptor.rgba8(label, width, height)` creates an RGBA8 sampled texture descriptor for the first sprite rendering slice.
- Texture wrap defaults to `TextureWrap.CLAMP_TO_EDGE`. Call `TextureDescriptor.wrap(...)` to request `REPEAT` or `MIRRORED_REPEAT` sampled-texture addressing.
- `GraphicsFrame.frameBuffer()` exposes the current drawable. `FrameBuffer.readPixelsRgba8()` is an end-of-frame capture operation: after it succeeds, no more commands should be recorded against that frame, and a later `GraphicsAttachment.endFrame()` for the same frame may be a no-op.
- `GraphicsDevice.writeTexture(texture, data)` uploads the full RGBA byte range from the provided `ByteBuffer`.
- Pipelines that sample textures declare the number of sampled textures they expect with `RenderPipelineDescriptor.sampledTextureCount(...)`.
- `RenderPass.setTexture(slot, texture)` binds a sampled texture for subsequent draws in the active pass.
- `RenderPass.setIndexBuffer(buffer)` binds an index buffer for subsequent `drawIndexed(...)` calls in the active pass.
- `RenderPass.setScissor(x, y, width, height)` sets the active pass clip rectangle for subsequent draws. Coordinates are framebuffer pixel coordinates in the provider's render-target origin convention. Higher-level renderers that target multiple providers are responsible for converting their logical clip origin before calling this method.
- The current `TextureView` shape is still a frame color attachment view. Texture-created views can be added when view descriptors are implemented.
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
        .glsl(glslVertexSource, glslFragmentSource)
        .spirv(spirvVertexWords, spirvFragmentWords)
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
| `GraphicsQueue` | Wraps wgpu queue. | Wraps graphics/compute/present queue facade. | Wraps command queue. | Serializes and flushes recorded command work against the current context. |
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
| `TextureRegion` | Region of a `Texture`. |
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
- Bitmap fonts are provider-neutral glyph metadata plus provider-backed page textures. `BitmapFontFiles.loadBitmap(...)` reads AngelCode BMFont-style `.fnt` files and page images. `BitmapFontFiles.loadFreeType(...)` rasterizes `.ttf`/`.otf` font assets into a bitmap atlas when the selected backend has registered a runtime fdx FreeType provider. `BitmapFontFiles.generateFreeType(...)` is reserved for backend-specific system-font providers and must fail clearly until a provider exists. Generated atlases should match or oversample the effective UI scale because rendering still submits texture quads.
- Future tile maps, particles, sprites, and additional 2D helpers belong in `g2d`, not separate required user dependencies.

## 15. Graphics 3D

Module:

```text
:libfdx:graphics:g3d
```

Package:

```text
io.github.libfdx.graphics.g3d
```

`g3d` is a complete 3D toolkit built on `graphics/api`. It owns model, material, shader, animation, scene, and render-path concepts. `Batch3D` is the common 3D submission contract; `ModelBatch` is the first implementation.

Defined types:

| Type | Role |
| --- | --- |
| `Camera` | Shared graphics camera from `graphics/api` used for 3D render submissions. |
| `Color`, `Vector3`, `Matrix4`, `BoundingBox` | Shared math types from `foundation/math` used in 3D materials, transforms, and bounds. |
| `Batch3D` | Common 3D render submission contract. |
| `ModelBatch` | Default optimized model batch implementation. |
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
| `AnimationClip` | 3D animation data. |
| `AnimationController` | Animation playback controller. |
| `Skeleton`, `Skin`, `Bone` | Skeletal animation data. |
| `MorphTarget` | Morph/blend-shape animation target. |
| `Light` | Base light description. |
| `DirectionalLight` | Directional light description. |
| `PointLight` | Point light description. |
| `SpotLight` | Spot light description. |
| `Environment3D` | Scene/environment lighting, skybox, fog, and image-based lighting data. |
| `RenderTarget3D` | High-level 3D render target view backed by `graphics/api` attachments. |
| `DefaultRenderTarget3D` | Default wrapper around color/depth attachments for a 3D pass. |
| `RenderPath3D` | Forward, deferred, shadow, post-processing, or custom render path. |
| `RenderGraph3D` | Ordered set of 3D passes and their render targets. |
| `G3DAssetLoaders` | Asset loader registration for 3D formats such as glTF. |
| `FrameBuffer` | Provider-neutral current drawable view owned by `graphics/api` and used by `g3d` capture paths. |

### 15.1. Graphics 3D Contracts

`g3d` provides scene/model helpers on top of `graphics/api`. Normal 3D code should use `g3d` types and not provider-specific graphics classes.

Framebuffers are graphics concepts, not GL-only concepts. The common API exposes the current drawable as a provider-neutral `FrameBuffer`; GL maps it to the default framebuffer, Vulkan maps it to the current swapchain image, and WGPU maps it to the acquired surface texture. Future offscreen and multi-render-target APIs should stay provider-neutral as well, so `g3d` can consume them for shadow maps, environment maps, deferred G-buffers, post-processing, and custom render paths.

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
}

public interface ModelInstance {
    Model model();
    Matrix4 transform();
    void collectRenderables(RenderQueue3D queue);
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
}

public final class Renderable3D {
    public MeshPart meshPart();
    public Material material();
    public Matrix4 worldTransform();
    public BoundingBox bounds();
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
    public Color ambientColor();
    public List<Light> lights();
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
    String id();
    float durationSeconds();
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

public final class MorphTarget {
    String id();
    float weight();
}

public final class AnimationController {
    public ModelInstance instance();
    public AnimationClip clip();
    public float timeSeconds();
    public AnimationController play(AnimationClip clip, boolean looping);
    public AnimationController update(float deltaSeconds);
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
- The first `ModelBatch` source slice renders static position/color meshes through reusable `Buffer`, `ShaderModule`, and `RenderPipeline` objects. It is a correctness and API integration base for the later PBR, uniform/storage buffer, instancing, skinning, and render-target work.
- `ModelBuilder` creates simple primitive models and custom triangle meshes using the current position/color renderer path.
- `G3DAssetLoaders.register(...)` installs the initial glTF loader. The first glTF slice supports static glTF 2.0 `.gltf`/`.glb` triangle meshes with `POSITION`, optional `COLOR_0`, optional indices, and `pbrMetallicRoughness.baseColorFactor`. Textures, node transforms, skins, morph targets, and animations are later slices.
- `g3d` should keep model loading, materials, PBR data, custom shaders, animation, lighting, frame targets, render paths, and rendering helpers in one user-facing artifact.
- Provider-specific rendering paths can exist internally, but normal user code should not need provider-specific graphics classes.
- `ModelBatch` should batch by shader key, material state, mesh, primitive topology, vertex layout, and render target. It should sort opaque renderables for state locality and depth efficiency, sort transparent renderables back-to-front, and keep stable ordering where required.
- `ModelBatch` should use API-neutral performance features through `graphics/api`: immutable/static mesh buffers, dynamic uniform or storage buffers, per-context pipeline caches, material/shader variant caches, texture and sampler binding reuse, instancing for repeated meshes, GPU skinning where supported, and clear fallbacks where a provider lacks an optimization.
- The full default PBR path is not implemented in the first source slice. When implemented, it should be metallic-roughness and support base color, normal, metallic-roughness, occlusion, emissive, alpha mode, double-sided state, image-based lighting, and shadow inputs.
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
| `UiListState`, `UiScrollState` | Retained scroll and virtualized list/grid state. |
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

`scenario-validator` is an optional public validation engine for complete runtime scenarios. The detailed contract lives in [SCENARIO_VALIDATOR.md](SCENARIO_VALIDATOR.md). It is reusable by libfdx's own tests, samples, tools, external benchmark projects, and user projects, but it is not required for normal runtime execution or rendering. UI Kit validation is an optional adapter capability, not the whole validator.

Defined types:

| Type | Role |
| --- | --- |
| `ScenarioValidator` | Main validation runner/facade for executing selected scenarios against a host. |
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
- Include `ShaderLanguage`, `ShaderProfile`, `ShaderTarget`, and `ShaderBundle` from the start. WGSL is the authoring source for portable shaders, GLSL/GLSL ES is used by the GL/WebGL/GLES provider family, SPIR-V is used by Vulkan, and MSL/HLSL are future generated targets for Metal/DirectX.
- Keep `TextureView` as a required common graphics type. Advanced view behavior is capability-gated.

## 19. Runtime Core

Module:

```text
:libfdx:runtime:fdx:core
```

Package:

```text
io.github.libfdx.runtime.core
```

`runtime/fdx/core` is the shared framework runtime service layer. It is not a user-created feature object and it is not a generic service locator. It provides small framework-wide services that common modules can use by default when the selected backend/platform has supplied the implementation.

Initial scope:

- FreeType font rasterization for `.ttf` and `.otf` assets.

Future possible scope:

- Native Matrix4 or fast-math helpers.
- Native image decode helpers if the common image loader needs them.
- Compression or memory helpers when they become framework-wide runtime services.

Defined types:

| Type | Role |
| --- | --- |
| `RuntimeCore` | Static framework runtime fdx access point and provider registration hook used by backend/platform wiring. |
| `RuntimeCoreProvider` | Backend/platform provider contract for runtime fdx services. |
| `FontRasterizer` | Rasterizes font bytes into glyph metrics and atlas pixels. |
| `FontRasterizerOptions` | Size, character set, padding, and atlas width for font rasterization. |
| `RasterizedFont` | Rasterized font result: atlas pixels, glyph metrics, line height, baseline, and kerning. |
| `RasterizedGlyph` | One glyph's atlas region and layout metrics. |
| `RuntimeCoreException` | Clear framework exception for runtime fdx failures. |

Rules:

- Normal game code should not call low-level native runtime bindings directly.
- `UiFont.freeType(...)` and `BitmapFontFiles.loadFreeType(...)` are the normal public path for FreeType fonts.
- Font rasterization happens when a font is loaded or cached, not during every UI render frame.
- UI and g2d rendering consume cached `BitmapFont` atlas data after rasterization.
- Backends must register a platform-specific `RuntimeCoreProvider` before code loads `.ttf`/`.otf` fonts through `UiFont.freeType(...)` or `BitmapFontFiles.loadFreeType(...)`.
- If no platform provider is registered, `runtime/fdx/core` must fail clearly. Do not silently fall back to a Java rasterizer or any non-FreeType implementation.
- Desktop JVM registers a provider backed by LWJGL FreeType. Desktop_native and Android register providers backed by the runtime fdx C/C++ bridge linked with downloaded FreeType source. Web registers a provider backed by the runtime fdx Emscripten JS/WASM bridge.
- Native bridge source is owned by `runtime/fdx/platform/*`, not by the Java core module. The core module owns Java contracts. `fdx_shared` packages the shared native source payload needed by TeaVM/native project generation, and `fdx_desktop`, `fdx_android`, and `fdx_web` package platform native outputs.
- Third-party FreeType source is not committed into the repository. The `runtime/fdx/platform` build prepares the pinned FreeType source archive under `build/third-party/...`; Android and web runtime fdx platform builds use that source when compiling the native bridge.
- Platform-specific native resources generated for the core JAR are scoped under `libfdx-native/<platform>/...` in generated resource output.
- The shared runtime fdx FreeType bridge source is scoped under `runtime/fdx/platform/shared/src/main/cpp/runtime_fdx/...` until a platform needs a separate bridge source.
- Native implementation code should be reused across platforms. Platform-specific code should be limited to small ABI adapters, not duplicated copies of the same FreeType rasterizer.
- Runtime fdx native artifacts use core framework names. Web runtime fdx output is `fdx.js` plus `fdx.wasm`; shared-library outputs should use the platform's `fdx` library filename convention, such as `fdx.dll` or `libfdx.so`, even when the first exported service is FreeType.
- Backend/native build logic is responsible for copying, compiling, linking, or loading the matching platform resources.
