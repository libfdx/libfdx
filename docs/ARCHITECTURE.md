# libFDX Architecture

This document defines the checked-in libFDX architecture and is the source of truth for module layout, dependency direction, naming, and provider boundaries. Current module tables list only projects present in `gradlew projects`. Future direction is labeled explicitly and is not a promise that a module, artifact, package, or API already exists.

For provider-neutral public API contract details, see [COMMON_API.md](COMMON_API.md).
For shader authoring, translation, runtime compilation, and optional
editor/runtime compiler behavior, see [SHADERS.md](SHADERS.md).

## Index

1. [Goals](#1-goals)
2. [Folder Rules](#2-folder-rules)
3. [Repository Layout](#3-repository-layout)
4. [Dependency Direction](#4-dependency-direction)
5. [Runtime vs Graphics](#5-runtime-vs-graphics)
6. [API And Provider Model](#6-api-and-provider-model)
7. [Common Types And Provider Types](#7-common-types-and-provider-types)
8. [Common API Surface](#8-common-api-surface)
9. [Module Catalog](#9-module-catalog)
10. [User Dependency Examples](#10-user-dependency-examples)
11. [Graphics Direction](#11-graphics-direction)
12. [Backends](#12-backends)
13. [Input Direction](#13-input-direction)
14. [Audio Direction (Future, Not Implemented)](#14-audio-direction-future-not-implemented)
15. [Tests And Benchmarking](#15-tests-and-benchmarking)
16. [Java Package Map](#16-java-package-map)
17. [Naming Notes](#17-naming-notes)

## 1. Goals

- Build a modular Java game framework.
- Keep the core module small and avoid forcing optional systems into foundation-style modules.
- Start with a modern WebGPU/wgpu graphics direction.
- Keep modules plug-and-play, including UI, rendering helpers, tools, external extensions, and backends.
- Keep dependency boundaries clear and explicit.

## 2. Folder Rules

Use a small number of broad folders. A new top-level folder should exist only when it gives a clear ownership rule for several modules. Framework folders live under `libfdx/`. Repository-only test, sample, and benchmark folders live at the repository root, not under `libfdx/`.

| Folder | Rule |
| --- | --- |
| `framework/` | Portable first-party framework modules. This includes the `fdx` runtime service family, foundation-style helpers such as math/JSON/collections, runtime-facing APIs, assets, common graphics, built-in 2D/3D toolkits, and `ui-kit`. `framework` is a source/Gradle organization parent only; Java packages and Maven artifact IDs stay module-specific. |
| `extensions/` | Optional provider/library families and pure-Java extensions, including graphics providers, WebRTC, ECS, and scenario validation. Use platform submodules only when a capability truly needs desktop/web/Android/iOS variants. |
| `backends/` | Platform/runtime implementation families. Each concrete backend uses one flat folder segment, such as `headless`, `headless_native`, `desktop`, or `desktop_c`. Shared TeaVM build and native resource support belongs in `c_shared`. Do not make a backend folder both a source module and a parent namespace for more backend modules. |
| `tools/` | Build-time and command-line tools. |
| `tests/` | Cross-platform framework test projects: core tests plus platform/backend test runners. |
| `samples/` | Example applications. |
| `benchmark/` | Performance benchmark projects: shared benchmark cases plus platform/backend benchmark runners and generated reports. |

Extension module shape:

- Extension libraries use `core` for provider-specific shared Java types when needed.
- Non-graphics extension runtime modules use one Gradle path segment per concrete platform variant.
- Graphics extension runtime modules use `extensions/graphics/<provider>/platform/<platform_variant>` so the provider root can keep `core` beside a platform folder, matching the shape used by tests and samples.
- Platform/runtime variant modules may be dependency-only when their job is native binding/runtime packaging. Shared Java provider code stays in the extension `core` module unless the binding variant truly requires different Java code.
- Use `<platform>` when the platform has one provider variant, such as `web`.
- Use lowercase `<platform>_<implementation>` when the platform has multiple provider variants, such as `desktop_jni`, `desktop_ffm`, `android_jni`, or `ios_native`.
- Web provider variant folders stop at `web`; use one `_web` artifact when the same module supports JavaScript and Wasm, and split into `_web_js` or `_web_wasm` only when the runtimes need separate artifacts.
- `_native` identifies the native runtime family. A launcher that uses a `_native` backend must use matching `_native` provider/runtime modules for graphics, audio, input, UI, and native-backed extensions. It must not mix normal platform modules such as `desktop`, `desktop_jni`, `desktop_ffm`, or `android_jni` into the same runtime.
- Backend folders use one flat segment per concrete backend. Use the runtime/library name when it uniquely identifies the backend, such as `desktop`; use `<platform>` for default platform backends, such as `web`, `android`, or `ios`; use `<platform>_<implementation>` for alternate platform runtimes, such as `headless_native` or `desktop_c`.
- Shared TeaVM build and native resource support belongs in the flat `c_shared` sibling rather than in a backend-specific resource-only module.

## 3. Repository Layout

```text
repo-root/
  settings.gradle.kts
  build.gradle.kts
  gradle/
  buildSrc/
  docs/

  libfdx/
    build.gradle.kts

    framework/
      fdx/
        core/
        fdx-build/
        platform/
          shared/
          desktop/
          android/
          web/
      math/
      json/
      collections/
      application/
      display/
      files/
      input/
      net/
      storage/
      assets/
        manager/
        loaders/
      graphics/
      camera/
      g2d/
      g3d/
      ui-kit/

    extensions/
      ecs/
      scenario_validator/
        core/
        ui-kit/
      net/
        webrtc/
          core/
          signaling_server/
          platform/
            desktop_jni/
            web/
            android_jni/
      graphics/
        wgpu/
          core/
          platform/
            desktop_jni/
            desktop_ffm/
            web/
            android_jni/
        gl/
          core/
          platform/
            desktop/
            desktop_c/
            web/
        vulkan/
          core/
          platform/
            desktop/
            desktop_c/
            android_jni/
    backends/
      desktop/
      c_shared/
      desktop_c/
      psp/
      ios_c/
      web/
      android/

    tools/
      font/
      gradle-plugin/
      project-generator/
        core/
        ui/
        platform/
          desktop/
          web/

  tests/
    core/
    platform/
      desktop/
      desktop_c/
      web/
      android/
      psp/
      plugin/

  samples/
    basic/
      core/
      platform/
        desktop/
        desktop_c/
        web/
        android/
        ios_c/
        plugin/

    ecs-platformer/
      core/
      platform/
        desktop/
        desktop_c/
        web/
        android/
        ios_c/

    multiplayer/2d-webrtc/
      core/
      platform/
        desktop/
        web/
        android/
        plugin/

  benchmark/
    core/
    platform/
      desktop/
      desktop_c/
      plugin/
```

## 4. Dependency Direction

Dependencies should point from higher-level modules to lower-level contracts, never from stable contracts back into concrete platform implementations.

Allowed dependency shape:

```text
framework/math, framework/json, framework/collections -> framework/fdx/core

framework/fdx/core -> no lower libFDX module
framework runtime-facing APIs -> framework/fdx/core and selected framework helpers
framework/storage -> framework/fdx/core, framework/files, and framework/json
framework/application -> framework/fdx/core plus typed runtime root systems

framework/assets/* -> framework/fdx/core, framework helpers, and framework/files when file access is needed

framework/graphics -> framework/fdx/core, framework/math, and framework/display for presentation handles
framework/camera -> framework/fdx/core, framework/math, and framework/input
framework/g2d -> framework/graphics, framework helpers, framework/assets/* when asset integration is needed
framework/g3d -> framework/graphics, framework/camera, framework helpers, framework/assets/* when asset integration is needed

framework/ui-kit -> framework/display, framework/files, framework/input, framework/assets/loaders, framework/graphics, framework/g2d
extensions/ecs -> no lower libFDX module
extensions/scenario_validator/core -> framework/fdx/core, framework/display, and framework/input
extensions/scenario_validator/ui-kit -> extensions/scenario_validator/core, framework/ui-kit

extensions/graphics/* -> framework/graphics and framework/display integration points
extensions/* -> the public modules needed by that provider or binding family

backends/* -> framework runtime-facing APIs plus selected provider SPI modules needed for platform wiring
backends/web additionally -> framework/ui-kit for the private web preload screen created before game code starts

tools/* -> only the public modules that tool needs

samples/*/core -> public framework APIs and feature modules
samples/*/platform/<platform> -> sample core, selected backend, selected platform providers chosen by dedicated Gradle tasks or platform build variants

tests/core -> public modules being tested
tests/platform/<backend_variant> -> tests/core, selected backend, selected platform providers chosen by dedicated Gradle tasks or platform build variants

benchmark/core -> public framework APIs and feature modules needed by benchmark cases
benchmark/platform/<backend_variant> -> benchmark/core, selected backend, selected platform providers chosen by dedicated Gradle tasks or platform build variants
benchmark/platform/plugin -> benchmark platform modules and generated platform task wiring

```

The arrows above mean "depends on". For example, `framework/g2d` may depend on `framework/graphics`, but `framework/graphics` must not depend on `framework/g2d`.

General rules:

- `framework/fdx/core` is the single public `core` module and should stay small.
- Non-required systems must not be placed in `framework/fdx/core`.
- Runtime APIs should be backend-neutral.
- `framework/fdx/core` owns base framework contracts and default runtime services shared by all systems, such as framework exceptions, provider identity, logging, async primitives, FreeType font rasterization, optional WGSL shader compilation, and native math/image helpers later. User code normally reaches runtime services through higher-level APIs such as `UiFont`, `BitmapFontFiles`, graphics shader creation, or math classes, not by calling native runtime bindings directly.
- Backends and platform launchers register the platform implementation for `framework/fdx/core`. Common modules consume the shared Java contracts and must not know the concrete native file layout.
- `framework/fdx/core` owns the Java contracts and does not compile or package platform native code directly.
- `framework/fdx/fdx-build` owns runtime fdx native compilation and dependency resolution for Windows, Linux, macOS, Android, and web. The desktop, Android, and web platform modules package generated native outputs into their published JAR/AAR artifacts and validate required files; they do not own CMake, dependency resolution, host classifiers, or toolchain arguments. Desktop, Android, and web runtime fdx native builds include the Tint-backed shader compiler by default so WGSL remains the single built-in shader source across GL/GLES/WebGL, Vulkan, and WGPU paths.
- Third-party FreeType and Tint/Dawn source are not committed into this repository and are not built by libFDX native tasks. Runtime fdx builds download checksum-verified static dependency packages from the external `libfdx/fdx-natives` release project, then compile only libFDX's small runtime bridge/glue sources.
- Runtime fdx C/C++ implementation files should be shared by default under `framework/fdx/platform/shared`. Platform modules should add only thin adapters required by the platform ABI or toolchain, such as JNI for Android or JS/WASM loading for web. Do not fork the FreeType rasterizer implementation per platform.
- Runtime fdx native artifacts use the framework core name, not feature-specific names. Web emits `fdx.js` and `fdx.wasm`; native shared-library platforms should emit the platform convention for `fdx`, such as `fdx.dll` on Windows and `libfdx.so` on Android/Linux. Platforms that do not need shader translation, such as PSP, must not package the Tint compiler capability.
- `framework/application` owns application lifecycle and the base `ApplicationConfig`. Backend-specific startup classes such as `DesktopApplicationConfig` should expose direct typed setters instead of requiring generic config keys in launcher code.
- Backends should implement APIs instead of being depended on by portable framework APIs.
- Opt-in feature objects such as `AssetManager`, ECS worlds, UI roots, and physics worlds should be created by user/framework feature code from explicit dependencies. Backends should not be forced to depend on those feature modules just because examples need them. The web backend's startup preloader is the exception: it may create a private `ui-kit` root while web assets load, and must dispose that root before calling the game `ApplicationListener.create(...)`.
- Input extensions should build on `framework/input`, not leak through normal game code.
- Shared graphics helpers should build on `framework/graphics`, not on a specific backend.
- Graphics extensions should build on `framework/graphics`, not leak through normal game code.
- `framework/assets/loaders` owns provider-neutral source-data loaders only. Loaders that create provider-backed `Texture` objects belong in a module that already depends on the graphics API/provider.
- 2D rendering, fonts, particles, and tile maps should stay in `framework/g2d`.
- 3D rendering, models, animation, materials, and lighting should stay in `framework/g3d`.
- Built-in UI modules should build on the graphics/runtime modules they need and stay optional. A project can use `ui-kit`, another UI solution, or no UI.
- Extension modules are optional capability add-ons. They include provider/platform integrations and other opt-in capabilities that extend libFDX, such as `extensions/ecs` and `extensions/scenario_validator`.
- ECS is an optional pure Java extension. It owns runtime entity handles, component storage, mappers, matchers, entity lists, event dispatch, managers, systems, and world commands. It must not depend on backends, providers, editor modules, rendering modules, or `Fdx`.
- Engine and editor modules that need ECS should depend on `io.github.libfdx:ecs`; `framework/fdx/core` and typed `Fdx` must not expose ECS root accessors.
- Scenario validator modules should stay optional and should not be required by normal runtime execution or rendering. A project can use `extensions/scenario_validator/core` for generic runtime flows and add domain adapters such as `extensions/scenario_validator/ui-kit` only when needed.
- Selectable platform/provider implementations belong in `extensions/`, not inside provider-neutral framework API modules or a root solution folder.
- Physics engine extensions should not depend on UI.
- Web targets copy declared web assets into `webapp/assets`, add the built-in `fdx_logo_dark.png` preload logo when the app has not supplied that asset path, pass asset paths and sizes into TeaVM properties, generate structured asset metadata through the web backend TeaVM plugin, render the web preload screen during startup asset preload, keep it visible for about two seconds unless preload drawing is explicitly disabled, and expose those files through the web backend file system. Gradle builds declare these assets through `libfdx.assets`; standalone tools and editors can declare them through `WebBuilder`. The web runtime obtains the preload list from the generated metadata class, not from an external JavaScript file or text manifest.
- Web target `htmlWidth`/`htmlHeight` values of `0` or negative values mark the generated canvas as browser-window sized.

## 5. Runtime vs Graphics

Runtime and graphics are related, but they are not the same layer.

Runtime is responsible for running the application and exposing platform services:

- application lifecycle and main loop
- display/canvas/presentation area creation
- input events
- files/storage
- networking

Graphics is responsible for GPU and rendering work:

- graphics contexts/devices
- buffers/textures and texture views
- shader modules
- render pipelines
- frame-owned command encoders and render passes
- 2D and 3D renderers

Graphics needs a running application to present frames to a real display/canvas/view, but it does not make it a runtime module. A headless backend can run without graphics, and graphics can also be used for offscreen rendering or tests without user input/audio/display features.

The practical relationship is:

```text
framework/display exposes displays/windows/canvases and platform display handles
framework/graphics exposes the Graphics manager and provider-backed GraphicsContext objects
backend creates the typed Fdx root and attaches the selected runtime/provider systems
```

## 6. API And Provider Model

Swappable systems should be designed as API modules plus provider modules.

API modules define the interfaces, handles, descriptors, and service contracts that normal game code uses:

```text
framework/input
framework/net
framework/graphics
```

Extension provider modules implement those contracts:

```text
extensions/net/webrtc/core
extensions/net/webrtc/signaling_server
extensions/net/webrtc/platform/desktop_jni
extensions/net/webrtc/platform/web
extensions/net/webrtc/platform/android_jni
extensions/graphics/gl/core
extensions/graphics/gl/platform/desktop
extensions/graphics/gl/platform/desktop_c
extensions/graphics/gl/platform/web
extensions/graphics/wgpu/core
extensions/graphics/wgpu/platform/desktop_jni
extensions/graphics/wgpu/platform/desktop_ffm
extensions/graphics/wgpu/platform/web
extensions/graphics/wgpu/platform/android_jni
extensions/graphics/vulkan/core
extensions/graphics/vulkan/platform/desktop
extensions/graphics/vulkan/platform/desktop_c
extensions/graphics/vulkan/platform/android_jni
extensions/ecs
extensions/scenario_validator/core
extensions/scenario_validator/ui-kit
```

This is the complete checked-in extension-provider graph. Input is a current
common API, but the checked-in backends currently create their platform input
and `DefaultGamepads` implementations directly; there are no standalone
`extensions/input/gamepads/*` projects. Audio is not implemented; its future
direction is isolated in [14. Audio Direction](#14-audio-direction-future-not-implemented).

Normal game code should depend on API modules and high-level feature modules. Provider platform modules should usually be selected in the launcher/platform module, not in shared game code. The user-facing root object is `Fdx`, a typed runtime root, not a generic service locator.

Use these Gradle dependency rules:

- Use `implementation` for common APIs and feature modules that source code imports directly, such as `input`, `graphics`, `g2d`, `g3d`, `ecs`, and `ui_kit`.
- Use `implementation` for extension cores that expose user-facing APIs.
- Use `implementation` in launcher/platform modules for selectable graphics stacks that the launcher intentionally enables, such as `gl_desktop`, `wgpu_core`, and `wgpu_desktop_jni`.
- Use `runtimeOnly` or a dedicated resolvable runtime configuration for provider variant modules that only contribute bindings or native libraries and are not imported by launcher source, such as `gl_desktop`, `vulkan_desktop`, and `wgpu_desktop_jni`.
- Use `implementation` instead of `runtimeOnly` for any module whose provider-specific classes are imported directly by user code or launcher code.
- Keep runtime families consistent. For example, `backends/desktop_c` may use the checked-in `gl_desktop_c` or `vulkan_desktop_c` modules, but it must not mix in `gl_desktop`, `vulkan_desktop`, `wgpu_desktop_jni`, or `wgpu_desktop_ffm`.

Gamepad support is part of the `framework/input` API because gamepads are input devices. The current backends own their platform input integration and expose it through that common API. If separately selectable gamepad providers are introduced later, they should live under `extensions/input/gamepads`; those are not current modules or artifacts.

There are no current audio dependencies or provider runtimes. Any eventual audio implementation must first add a common API, backend ownership, provider modules, tests, and synchronized documentation before artifact names are presented as usable.

Graphics provider runtime modules should use a provider root with `core` directly under it and concrete platform variants under `platform/`. Avoid a generic `native` module or a plain platform module when there are multiple implementation choices because it does not say which binding/runtime owns the binaries or packaging. Shared Java provider classes should live in the provider `core` module and depend only on provider-neutral APIs, not concrete backends. If a backend technology already owns the graphics binding and context model, such as desktop GL, the backend may expose a selectable `GraphicsAttachmentProvider` while reusing the provider-neutral shared implementation from the extension `core` module. The matching graphics extension platform module, such as `extensions/graphics/gl/platform/desktop`, still owns the optional runtime/native dependencies. For web, use `extensions/graphics/<provider>/platform/web` as the Gradle module and put `js` or `wasm` only in the published artifact ID.

Network transport provider modules should build on `framework/net`. Provider-specific shared types live in `extensions/net/<provider>/core`, concrete runtime/binding variants live under `extensions/net/<provider>/platform/<platform_variant>`, and optional provider tools or services may live beside `core` when they are not platform bindings. WebRTC follows this shape so `WebRtcClientConfig`, `WebRtcServerConfig`, `WebRtcPeerConfig`, signaling contracts, and bridge interfaces stay in the WebRTC core module; the customizable signaling server lives in `extensions/net/webrtc/signaling_server`; desktop, web, Android, and future iOS bindings remain platform-specific. Normal game code should use `Network`, `NetTransports`, `NetClient`, `NetServer`, `NetPeerGroup`, `NetConnection`, and channel IDs rather than WebRTC data-channel classes.

Extension artifacts should not repeat the `extensions` category. Graphics provider artifacts also should not repeat the `graphics` category. For example, `extensions/ecs` publishes as `ecs`, `extensions/graphics/wgpu/core` publishes as `wgpu_core`, `extensions/graphics/wgpu/platform/desktop_jni` publishes as `wgpu_desktop_jni`, `extensions/graphics/gl/core` publishes as `gl_core`, and `extensions/graphics/gl/platform/desktop` publishes as `gl_desktop`. WebRTC network artifacts publish as `webrtc_core`, `webrtc_signaling_server`, `webrtc_desktop_jni`, `webrtc_web`, and `webrtc_android_jni`. Scenario validator extension artifacts publish as `scenario_validator` and `scenario_validator_ui_kit`.

External bindings should not be hidden behind fake shared APIs when the underlying libraries have different concepts. A project should choose the binding module it actually uses. For graphics, `graphics` provides the `Graphics` manager and `GraphicsContext` contract; provider modules supply concrete contexts:

```kotlin
dependencies {
    implementation("io.github.libfdx:graphics:$libfdxVersion")
    implementation("io.github.libfdx:g2d:$libfdxVersion")
    implementation("io.github.libfdx:wgpu_core:$libfdxVersion")

    runtimeOnly("io.github.libfdx:wgpu_desktop_jni:$libfdxVersion")
}
```

If user code directly uses provider-specific types, then that code is intentionally provider-specific and will need refactoring when switching providers.

### 6.1. Fdx Root, Displays, And Graphics Contexts

`Fdx` is the typed root passed to `ApplicationListener.create(Fdx fdx)`. It exposes only backend-owned runtime systems and managers, such as `app()`, `displays()`, `graphics()`, `files()`, and `logger()`. It must not expose `require(Class<T>)`, `find(Class<T>)`, registration methods, or feature objects that users can construct themselves.

`AssetManager` is not a backend-owned root service. A game creates it from `fdx.files()` and registers the loaders it wants. Higher-level systems such as sprite batches, UI roots, scenes, physics worlds, and tools follow the same rule: create them explicitly from the APIs they need.

`Displays` and `Graphics` are separate because offscreen rendering and multi-window/provider setups need independent lifetimes:

```java
Display mainDisplay = fdx.displays().main();
GraphicsContext mainGraphics = fdx.graphics().main();
```

The simple path is one backend-created display and one backend-created graphics context. Advanced desktop code can create another display and another graphics context when supported:

```java
Display toolsDisplay = fdx.displays().create(new DisplayConfig()
    .title("Vulkan Tools")
    .size(900, 600));

GraphicsContext vulkan = fdx.graphics().create(
    GraphicsConfig.provider(new DesktopVulkanProvider())
        .display(toolsDisplay));
```

This allows a main GL context and a secondary Vulkan context on desktop backends that can support that combination. `GraphicsConfig.display(...)` is required for additional on-window contexts so there is no hidden "current display" state. Mobile and web backends may expose only one main display and one main graphics context, and should fail clearly when additional displays or contexts are unsupported.

Resources are owned by their `GraphicsContext`. A texture, buffer, pipeline, command encoder, or frame created by one provider context is not automatically usable by another provider context. Cross-provider sharing, if added later, must be an explicit provider-specific or capability-gated API.

Provider-specific access stays explicit:

```java
GraphicsContext graphics = fdx.graphics().main();
if (graphics.providerId().equals(WGPUProvider.ID)) {
    WGPUContext wgpu = graphics.as();
}
```

The desktop Vulkan implementation does not expose a public `VulkanContext`
type, so current documentation must not invent one for `as()` examples.

### 6.2. Provider Selection And Switching

Dependencies define which providers are available. Runtime configuration chooses which available provider is active.

If an application depends on only one provider for a system, the backend can select it automatically. If more than one compatible provider is present, the launcher or application configuration should select a concrete provider setup object or a `ProviderId`.

`ProviderId` values are logical provider IDs, not Maven artifact names. Current providers expose constants such as `WGPUProvider.ID`, `DesktopOpenGLProvider.ID`, `DesktopVulkanProvider.ID`, and `WebRtcProvider.ID` so application code does not need to hard-code strings.

Example dependencies with multiple selectable providers when launcher code imports provider ID constants:

```kotlin
dependencies {
    implementation("io.github.libfdx:graphics:$libfdxVersion")
    implementation("io.github.libfdx:wgpu_core:$libfdxVersion")
    implementation("io.github.libfdx:backend_desktop:$libfdxVersion")

    runtimeOnly("io.github.libfdx:gl_desktop:$libfdxVersion")
    runtimeOnly("io.github.libfdx:vulkan_desktop:$libfdxVersion")
    runtimeOnly("io.github.libfdx:wgpu_desktop_jni:$libfdxVersion")
}
```

If the launcher uses string IDs or service discovery only, those provider modules can remain `runtimeOnly`.

Selecting a provider that is not present on the classpath or is not supported on the current platform should fail early with a clear configuration error.

A default provider should not be magic. A platform-default graphics provider is still selected through a normal `GraphicsAttachmentProvider`, with explicit provider identity and lifecycle. Any future separately selectable input or audio providers should follow the same rule only after those APIs and modules exist.

Example startup selection:

```java
DesktopApplicationConfig config = new DesktopApplicationConfig()
    .title("My Game")
    .size(1280, 720)
    .graphics(new WGPUProvider()
        .backend(WGPUBackend.DEFAULT));

ApplicationBackend backend = new DesktopApplicationBackend();
backend.start(config, new MyGame());
```

Example user setting flow:

```java
String selectedGraphics = "vulkan";

DesktopApplicationConfig config = new DesktopApplicationConfig();
ProviderId graphicsId = ProviderId.of(selectedGraphics);
if (graphicsId.equals(WGPUProvider.ID)) {
    config.graphics(new WGPUProvider());
} else if (graphicsId.equals(DesktopOpenGLProvider.ID)) {
    config.graphics(new DesktopOpenGLProvider());
} else if (graphicsId.equals(DesktopVulkanProvider.ID)) {
    config.graphics(new DesktopVulkanProvider());
}
```

Graphics-provider switching is a startup decision for portable applications. A user may change the selected provider ID in settings while the game is running, but the new provider should be applied on the next application start. `Application` exposes `requestExit()` only; restart UI and relaunch behavior belong to the application or platform launcher.

Graphics resources are lifecycle-bound to their owning context, native device, surface/presentation path, and provider implementation. Because of that, live graphics-provider switching is not part of the common API. A platform backend may implement full internal application recreation later, but portable code should treat a graphics-provider change as requiring restart.

## 7. Common Types And Provider Types

The recommended model is to have common interfaces for portable game code and provider-specific classes for advanced escape hatches.

Portable API example:

```java
Texture texture = assets.get("player.png", Texture.class);
```

Provider-specific implementation examples:

```java
GraphicsContext graphics = fdx.graphics().main();
if (graphics.providerId().equals(WGPUProvider.ID)) {
    WGPUContext wgpu = graphics.as();
}
```

Naming rule:

- `Texture`, `GraphicsDevice`, `Gamepads`, and `Gamepad` are common API types.
- `WGPUContext`, `WGPUProvider`, `GLConfiguration`, `VulkanConfiguration`, `DesktopOpenGLProvider`, and `DesktopVulkanProvider` are current provider/backend-specific public types.
- High-level modules such as `g2d`, `g3d`, `ui-kit`, and `framework/assets/loaders` should use common API types unless they are explicitly provider-specific modules.

This avoids a design where a generic type is secretly tied to one graphics API. A `Texture` in libFDX means a portable texture handle. Current WGPU texture implementations are internal; provider-specific texture access must not be documented until a public provider type exists.

## 8. Common API Surface

Common API types are the default types users should write game code against. They should expose portable concepts only. Provider-specific work should stay behind the implementation or be accessed through an explicit escape hatch.

Common types may be Java interfaces, abstract handles, or final portable classes backed by provider internals. The important rule is not the Java keyword; the important rule is that the public type is portable.

Examples that use `fdx` refer to the typed `Fdx` root passed to `ApplicationListener.create(Fdx fdx)`.

Any common object backed by provider-specific state should implement `ProviderHandle`.

Example shape:

```java
public interface ProviderHandle {
    ProviderId providerId();
    <T> T as();
}
```

Common handles should use this for advanced access:

```java
Texture texture = assets.get("player.png", Texture.class);

GraphicsContext graphics = fdx.graphics().main();
if (graphics.providerId().equals(WGPUProvider.ID)) {
    WGPUContext wgpu = graphics.as();
}
```

Rules:

- `as()` is an explicit provider-specific access path, not the normal path.
- `as()` returns the caller-selected generic type `T` through Java assignment or target typing.
- Because Java erases generic `T`, `as()` does not receive the requested class at runtime. A wrong target type should fail clearly, normally through a cast error.
- `providerId()` gives users a no-argument way to check which provider backs the common handle before calling `as()`.
- Provider-specific returned objects are only valid for that provider/device lifetime.
- Portable modules should not require `as()` for normal behavior.
- Provider modules may expose richer public types, but docs and examples must use only types that are actually exported by the selected provider module.

### 8.1. Provider Access Examples

Provider-specific examples require the corresponding provider `core` artifact as an `implementation` dependency because the source code imports provider-specific classes.

Portable texture usage:

```java
Texture texture = assets.get("player.png", Texture.class);
spriteBatch.draw(texture, x, y);
```

Provider-specific context access:

```java
GraphicsContext graphics = fdx.graphics().main();
WGPUContext wgpu = graphics.as();
```

Safe provider-specific context access:

```java
if (graphics.providerId().equals(WGPUProvider.ID)) {
    WGPUContext wgpu = graphics.as();
    // Use public wgpu-specific context features here.
}
```

Portable display usage:

```java
Display display = fdx.displays().main();
display.title("libfdx Game");
```

Provider-specific native display access:

```java
if (display.providerId().equals(DesktopBackendProvider.ID)) {
    DesktopDisplayHandle handle = display.as();
}
```

The same pattern applies to other provider-backed common types:

```java
GraphicsContext graphics = fdx.graphics().main();
WGPUContext wgpu = graphics.as();
```

Common types that should implement `ProviderHandle`:

```text
Application
FileSystem
Input
Display
FileWatch
Gamepads
Gamepad
Network
WebSocket
GraphicsContext
GraphicsAttachment
GraphicsDevice
GraphicsFrame
FrameBuffer
Texture
TextureView
Buffer
ShaderModule
RenderPipeline
CommandEncoder
RenderPass
```

### 8.2. Core And Foundation Common Types

Common type summary for `framework/fdx/core`:

`framework/fdx/core` is the only public `core` module. It owns the tiny shared base contracts in package `io.github.libfdx.core`, shared runtime-service contracts in package `io.github.libfdx.runtime.core`, and shader compiler contracts in package `io.github.libfdx.runtime.core.shader`. It is not only interfaces. It can contain interfaces, abstract contracts, small final value classes, exceptions, and lightweight helpers that every module can safely depend on.

It should not contain solution APIs such as graphics, audio, files, assets, application lifecycle, UI, or physics.

| Type | Purpose |
| --- | --- |
| `Disposable` | Common resource cleanup contract. |
| `FdxService` | Internal marker available to backend/provider wiring code when an implementation keeps a private registry. |
| `FdxException` | Base framework exception type. |
| `Logger` | Logging API independent from a logging implementation. |
| `ProviderId` | Stable provider identity value, such as the current `wgpu`, `vulkan`, `gl`, or `webrtc` IDs. |
| `ProviderHandle` | Base contract for common handles that can expose provider-specific internals through `as()`. |
| `FdxFuture<T>` and callbacks | Portable async result and callback contracts. |

Common type summary for `framework/math`:

| Type | Purpose |
| --- | --- |
| `Vector2`, `Vector3`, `Vector4` | Vector math. |
| `Matrix3`, `Matrix4` | Matrix math. |
| `Quaternion` | 3D rotation math. |
| `BoundingBox` | 3D bounds value. |
| `Color` | Backend-neutral color value. |

### 8.3. Runtime Common Types

Common type summary:

| Type | Module | Purpose |
| --- | --- | --- |
| `Fdx` | `framework/application` | Typed runtime root passed to user code by the backend. |
| `Application` | `framework/application` | Running application lifecycle and frame timing. |
| `ApplicationListener` | `framework/application` | User lifecycle callbacks with `render()` as the per-frame method. |
| `ApplicationConfig` | `framework/application` | Startup configuration. |
| `ApplicationBackend` | `framework/application` | Launcher-side backend lifecycle implementation contract; not a context service. |
| `FileSystem` | `framework/files` | File service. |
| `FileHandle` | `framework/files` | Portable file reference. |
| `Storage`, `KeyValueStore` | `framework/storage` | Persistent local settings/preferences and rebuildable cache stores. |
| `Input` | `framework/input` | Input service. |
| `Key`, `MouseButton`, `TouchPoint` | `framework/input` | Portable input values. |
| `InputProcessor` | `framework/input` | Input event callback/routing contract. |
| `Gamepads`, `Gamepad`, `GamepadMapping` | `framework/input` | Portable controller/gamepad contracts implemented by gamepad providers. |
| `Display` | `framework/display` | Presentation area abstraction for desktop windows, browser canvases, Android views, and future platform surfaces. |
| `DisplayConfig` | `framework/display` | Startup title, size, resize, visibility, maximized, v-sync, and foreground-FPS configuration. |
| `Network` | `framework/net` | Network service. |
| `http.HttpRequest`, `http.HttpResponse`, `websocket.WebSocket` | `framework/net` | Portable request/response and WebSocket contracts. |
| `transport.NetTransports`, `transport.NetClient`, `transport.NetServer`, `transport.NetPeerGroup`, `transport.NetConnection`, `packet.NetPacket`, `packet.NetPacketQueue`, `buffer.NetBuffer`, `transform.NetPacketTransform`, `codec.NetMessageCodec` | `framework/net` | Provider-neutral multiplayer transport contracts, inbound queue dispatch, pooled packet storage, packet transforms, and manual message serialization. |

### 8.4. Assets Common Types

Common type summary for `framework/assets/manager`:

| Type | Purpose |
| --- | --- |
| `AssetManager` | Load, cache, retrieve, update, and dispose assets. |
| `AssetDescriptor<T>` | Describes an asset path/type/options. |
| `AssetHandle<T>` | Typed handle/reference to a loaded or loading asset. |
| `AssetLoader<T>` | Loader contract implemented by format loaders. |
| `AssetLoadContext` | Loader context for file access, asset dependencies, and application-thread completion. |

Common loader-facing type summary for `framework/assets/loaders`:

| Type | Purpose |
| --- | --- |
| `ImageData` | Provider-neutral decoded image data before upload to GPU. |
| `ImageAssetLoader` | Provider-neutral PNG/JPG image loader that produces `ImageData`. |

### 8.5. Audio Status

Audio is not implemented in the current module graph or common API. There is no `framework/audio` artifact and no `Fdx.audio()` accessor. See [14. Audio Direction](#14-audio-direction-future-not-implemented) for explicitly future, non-API direction.

### 8.6. Graphics Common Types

Common type summary for `framework/graphics`:

| Type | Purpose |
| --- | --- |
| `Graphics` | Graphics manager/factory returned by `Fdx.graphics()`. |
| `GraphicsContext` | Provider-backed rendering context returned by `Graphics.main()` or `Graphics.create(...)`. |
| `GraphicsAttachment`, `GraphicsAttachmentReadiness` | Backend/provider presentation attachment lifecycle, including optional async readiness. |
| `GraphicsDevice` | GPU device abstraction owned by a `GraphicsContext`. |
| `ImmediateModeRenderer` | Provider-neutral immediate-style renderer for simple 2D and 3D diagnostic lines. |
| `GraphicsFrame`, `FrameBuffer` | Frame-owned command/attachment access and end-of-frame RGBA8 readback. |
| `Buffer` | Portable GPU buffer handle. |
| `Mesh` | Concrete provider-neutral GPU mesh wrapper for vertex/index buffers and layout metadata. |
| `Texture` | Portable GPU texture handle. |
| `TextureView` | Portable view into a `Texture` for binding, sampling, render targets, mip ranges, array layers, and aspects. |
| `ShaderModule` | Compiled/loaded shader module. |
| `RenderPipeline` | Render pipeline. |
| `CommandEncoder` | Command recording interface. |
| `RenderPass` | Render pass encoder. |
| `BufferDescriptor`, `TextureDescriptor`, `RenderPipelineDescriptor` | Portable creation descriptors. |
| `VertexLayout`, `VertexStepMode`, `VertexAttribute`, `VertexFormat` | Portable vertex input layout types. |
| `TextureFormat`, `TextureUsage`, `TextureFilter`, `TextureWrap`, `BufferUsage` | Portable enum/value types. |

Common type summary for `framework/camera`:

| Type | Purpose |
| --- | --- |
| `Camera`, `CameraProjection` | Shared mutable camera state and orthographic/perspective mode selection. |
| `CameraAnchor2D`, `CameraAnchor3D`, `CameraPointerRegion`, `CameraInputBindings3D` | Shared anchor, pointer-filter, and input-binding contracts for reusable camera controllers. |
| `CinematicCameraPath3D`, `CinematicCameraPathSample3D`, `KeyframeCinematicCameraPath3D` | No-allocation sampled 3D camera paths for authored cinematic shots. |
| `CameraController2D` | Reusable orthographic 2D pan/zoom controller. |
| `FreeCameraController3D`, `FirstPersonCameraController3D`, `ThirdPersonCameraController3D`, `OrbitCameraController3D`, `OrthographicCameraController3D`, `CinematicCameraController` | Focused game-facing camera controllers for editor fly cameras, anchor-driven gameplay views, orbit/orthographic inspection, and smooth 2D/3D cinematic presentation. |

`Texture` should describe a portable texture, not a native API object. It should expose things like size, format, usage, dimension, capabilities, provider identity, and `as()`. It should not expose native API handles, Vulkan layout transitions, or WebGPU-only view internals directly.

Current provider-specific public examples:

```text
WGPUProvider
WGPUConfiguration
WGPUContext
GLConfiguration
VulkanConfiguration
DesktopOpenGLProvider
DesktopVulkanProvider
```

### 8.7. Graphics 2D Common Types

Common type summary for `framework/g2d`:

| Type | Module | Purpose |
| --- | --- | --- |
| `Batch2D` | `g2d` | Common textured 2D batch contract. |
| `SpriteBatch` | `g2d` | Default batched 2D sprite renderer implementation. |
| `SpriteOutlineRenderer2D` | `g2d` | WGSL-authored sprite outline effect renderer. |
| `FogOfWarRenderer2D` | `g2d` | WGSL-authored 2D fog-of-war overlay renderer. |
| `ParticleEmitter2D` | `g2d` | Fixed-capacity 2D particle emitter that renders through `Batch2D`. |
| `TextureRegion` | `g2d` | Region of a `Texture`. |
| `TileLayer`, `TileMap`, `TileSet`, `TileMapRenderer` | `g2d` | Provider-neutral tile-map data and `Batch2D` rendering helper. |
| `ShapeRenderer2D` | `g2d` | Debug/simple 2D shape rendering. |

2D types use common graphics API types such as `Texture`, `Buffer`, `Mesh`, and `GraphicsDevice`; they do not depend on provider-internal resource handle classes.

### 8.8. Graphics 3D Common Types

Common type summary for `framework/g3d`:

| Type | Module | Purpose |
| --- | --- | --- |
| `Camera` | `framework/camera` | Shared mutable graphics camera that can switch between orthographic and perspective projection. |
| `Color`, `Vector3`, `Matrix4`, `BoundingBox` | `framework/math` | Shared math types used by 3D materials, transforms, and bounds. |
| `Batch3D` | `g3d` | Common 3D render submission contract. |
| `ModelBatch` | `g3d` | Default optimized model batch implementation, including WGSL PBR and skinned PBR paths. |
| `SkyboxRenderer3D` | `g3d` | WGSL-authored procedural world-space sky/background renderer for 3D scenes. |
| `SkyEnvironment3D` | `g3d` | Procedural sky environment description sampled by the default PBR path for IBL-style diffuse and specular lighting. |
| `BillboardRenderer3D` | `g3d` | WGSL-authored camera-facing textured quad renderer for markers, effects, impostors, and simple 3D particles. |
| `ParticleEmitter3D` | `g3d` | Fixed-capacity 3D particle emitter that renders through `BillboardRenderer3D`. |
| `OutlineRenderer3D` | `g3d` | WGSL-authored shell outline renderer for PBR-layout 3D meshes. |
| `FogOfWarRenderer3D` | `g3d` | WGSL-authored world-space 3D fog-of-war overlay renderer. |
| `ModelBuilder` | `g3d` | Programmatic primitive model construction for cubes, boxes, spheres, and custom triangle meshes. |
| `Mesh` | `framework/graphics` | Concrete low-level GPU mesh usable by 2D, UI, custom renderers, and 3D, including PBR and skinned PBR layouts. |
| `MeshPart` | `g3d` | 3D subset of a graphics `Mesh` rendered with one material. |
| `Model` | `g3d` | Loaded 3D model asset. |
| `DefaultModel` | `g3d` | Default loaded-model implementation. |
| `ModelInstance` | `g3d` | Instance of a model in a scene. |
| `DefaultModelInstance` | `g3d` | Default model instance implementation. |
| `ModelNode`, `ModelNodePart`, `Renderable3D` | `g3d` | Model hierarchy, skin metadata, and flattened render items. |
| `Material`, `MaterialAlphaMode` | `g3d` | 3D material abstraction and alpha mode values. |
| `PbrMaterial`, `ShaderMaterial` | `g3d` | Default PBR material data and custom shader material hooks. |
| `Shader3D`, `ShaderProvider3D`, `PbrShaderProvider`, `PbrShaderConfig` | `g3d` | 3D shader selection and default PBR/skinned PBR shader path. |
| `AnimationClip`, `AnimationController`, `Skeleton`, `Skin`, `Bone`, `SkinningPalette`, `CpuSkinningMeshUpdater`, `CpuSkinnedModelAnimator`, `MorphTarget` | `g3d` | Node, skeletal, GPU/CPU skinning, and morph target animation data and playback. |
| `Light`, `DirectionalLight`, `PointLight`, `SpotLight` | `g3d` | Portable light descriptions. |
| `Environment3D` | `g3d` | Scene/environment lighting, point-light and spotlight inputs, directional and cascaded shadow-map references, distance fog, and sky environment lighting data. |
| `DirectionalShadowMap3D` | `g3d` | Directional-light shadow-map helper backed by a sampled render-attachment texture and WGSL depth pass. |
| `CascadedShadowMap3D` | `g3d` | Provider-neutral manager for up to four directional shadow maps split from a driver view camera and sampled by the default PBR shader. |
| `RenderQueue3D`, `DefaultRenderQueue3D`, `RenderTarget3D`, `DefaultRenderTarget3D`, `RenderPath3D`, `RenderGraph3D` | `g3d` | Culling, sorting, pass orchestration, render target views, and render path helpers. |
| `G3DAssetLoaders` | `g3d` | Asset loader registration for 3D formats such as glTF. |
| `FrameBuffer`, `RenderTarget3D`, `DefaultRenderTarget3D` | `framework/graphics`, `g3d` | Frame readback plus provider-neutral offscreen color/depth target support used by 3D render paths. |

3D types should use common graphics API types and should not require provider-specific classes for normal rendering. GL FBOs, Vulkan image/framebuffer setup, and WGPU texture-view attachments should be hidden behind `framework/graphics` render-target abstractions.

### 8.9. UI Toolkit Types

There is no single common UI API that every UI solution must implement.

The detailed `ui-kit` specification lives in [UI_KIT.md](UI_KIT.md). The model is a Compose-inspired declarative API over a retained runtime, implemented as plain Java with no annotations, reflection, compiler plugin, or generated source requirement.

All public `ui-kit` types must use the `Ui` prefix. This keeps UI classes easy to recognize in game code and avoids collisions with common names such as `Button`, `Label`, `Style`, or `Table`.

| Type | Module | Purpose |
| --- | --- | --- |
| `UiToolkit` | `ui-kit` | UI Kit factory for roots, widgets, and shared UI resources. |
| `UiRoot` | `ui-kit` | Retained UI root, composition owner, layout owner, input dispatch, focus, rendering, and disposal. |
| `UiScope` | `ui-kit` | Declarative builder surface for content lambdas. |
| `UiModifier` | `ui-kit` | Immutable layout, drawing, input, and behavior options. |
| `UiState<T>`, `UiBooleanState`, `UiIntState`, `UiFloatState`, `UiLongState`, `UiDoubleState` | `ui-kit` | Explicit observable UI state. Primitive state uses dedicated classes to avoid boxed primitive wrappers. |
| `UiNode` | `ui-kit` | Retained node handle for advanced cases, debugging, and custom widgets. |
| `UiTheme`, `UiStyle` | `ui-kit` | Programmatic theme and widget style values. |
| `UiDrawable`, `UiNinePatch` | `ui-kit` | UI drawing values, including first-class ninepatch support. |
| `UiAnimationSpec`, `UiEasing`, `UiTransition`, `UiAnimatable<T>`, `UiFloatAnimatable` | `ui-kit` | Animation timing, easing, state-driven transitions, and custom retained animated values. Primitive scalar animation uses `UiFloatAnimatable`. |
| `UiTextStyle`, `UiFont` | `ui-kit` | Text styling, font selection, measurement, and localization-ready text rendering. |
| `UiLayer`, `UiPopup`, `UiModal`, `UiTooltip` | `ui-kit` | Ordered UI layers, popups, modal input blocking, and anchored tooltips. |
| `UiWindowState` | `ui-kit` | Retained position and size for movable and resizable UI windows. |
| `UiFocusScope`, `UiNavigation` | `ui-kit` | Keyboard/gamepad focus scopes and navigation rules. |
| `UiListState`, `UiScrollState` | `ui-kit` | Retained scroll and virtualized list/grid state. |
| `UiTextAreaOptions` | `ui-kit` | Text-area sizing policy for fixed-height and bounded auto-grow behavior. |

### 8.10. External Binding Types

External binding APIs are not common provider-neutral APIs. Do not add generic shared wrapper names just to normalize different third-party libraries; binding modules should either expose their selected upstream API shape or document their own API separately.

## 9. Module Catalog

The coordinates below define the external Maven coordinate shape.

The Maven group ID is configured in `libfdx.toml` as `release.fdxGroup`; the default group is `io.github.libfdx`. Artifact IDs should be as short as possible, but they must be unique inside that group. When an artifact ID has multiple name parts, use `_` instead of `-` so long coordinates are easy to select and copy as one word. The shared runtime module publishes as `fdx`, while solution API modules use clear names:

```text
io.github.libfdx:fdx
io.github.libfdx:graphics
```

Use `g2d` and `g3d` as complete user-facing toolkits, not as tiny rendering-only artifacts that force users to add many small dependencies.

Extension/provider families should use explicit `core` artifacts and short runtime names:

```text
<solution>_core
<solution>_<platform>          // only when the platform has a true default implementation
<solution>_<platform>_<stack>  // required when the user must choose a binding/runtime stack
```

Graphics provider artifacts omit the `graphics_` prefix because `wgpu` and `vulkan` are already graphics providers. For example, publish `wgpu_core` and `vulkan_core`, not `graphics_wgpu` or `graphics_vulkan`.

The `extensions` repository folder is organizational only. It should not appear in Maven artifact IDs. For example, the checked-in `:libfdx:extensions:net:webrtc:platform:desktop_jni` project publishes as `webrtc_desktop_jni`, not `extensions_net_webrtc_desktop_jni`.

Do not publish a default platform runtime artifact for an extension when there is no real default implementation. Users must select an explicit runtime stack such as `jni`, `ffm`, `native`, or `wasm` when several stacks can exist.

In module names, `_native` means the native runtime family, not a specific compiler or translation tool. Do not expose a compiler/toolchain name in public module names unless a future module must distinguish between multiple native toolchains.

Internal Gradle paths should remain the source of truth while the project is young.

The sibling `fdx-natives` repository builds static FreeType and Tint/Dawn dependency packages for `linux-x64-gcc`, `windows-x64-msvc`, `macos-x64-appleclang`, `macos-arm64-appleclang`, `android-arm64-v8a`, `android-armeabi-v7a`, `android-x86`, `android-x86_64`, and `web-emscripten`. Its `fdx-natives.toml` file owns dependency and toolchain pins, with Gradle update tasks for FreeType, Dawn/Tint, Android, and Emscripten versions. libFDX consumes those packages through clean task names such as `libfdx_build_native_artifacts`, `libfdx_build_windows_native_artifact_prebuilt`, `libfdx_build_web_native_artifacts_prebuilt`, and `libfdx_build_android_native_artifacts_prebuilt`; the `_prebuilt` names remain explicit aliases, not a separate dependency mode. The checked-in resolver default targets the current verified `fdx-natives` v0.1.1 release from one fixed top-level version in `:libfdx:framework:fdx:fdx-build`; advanced local overrides may still use `libfdx.runtimeFdx.nativeDepsBaseUrl` for mirror testing. Prebuilt packages are a build-time optimization only and must not become user-visible runtime dependencies.

The configured dependency mode controls repository consumer wiring for tests and samples. `LibExt` reads `libfdx.toml` `[development]` values, then lets ignored root `local.properties` keys `development.usePublishedLibfdx`, `development.publishedLibfdxVersion`, and `development.pluginBootstrapLibfdxVersion` override them for one checkout. Settings always includes the local `:libfdx:*` source modules. The checked-in default is false so clean source checkouts and CI use the local Gradle plugin build for the dedicated plugin-use modules and local project dependencies for consumers. That included plugin build must stay isolated to the plugin project; it must not include or remap root `:libfdx:*` source modules under the plugin build id. It compiles against the already published, compatible `pluginBootstrapLibfdxVersion`; this bootstrap coordinate does not control emitted publication versions. When `usePublishedLibfdx` is true, plugin-use modules resolve the Gradle plugin from Maven and consumers resolve libFDX dependencies as published `<fdxGroup>:<artifact>:<publishedLibfdxVersion>` coordinates. Snapshot publication derives `<release.fdxVersion>-SNAPSHOT` independently. Builder-backed web tasks attach local generated runtime fdx web resources only when the consumer runtime classpath has direct or transitive local `:libfdx:*` project dependencies; Maven-backed consumers must get those resources from the published artifacts.

### 9.1. Foundation Modules

| Gradle path | Maven coordinate | Purpose |
| --- | --- | --- |
| `:libfdx:framework:math` | `io.github.libfdx:math` | Pure math primitives: vectors, matrices, quaternions, bounds, and backend-independent color math. Should be usable without an application or backend. |
| `:libfdx:framework:json` | `io.github.libfdx:json` | Strict provider-neutral JSON tree parsing, writing, and manual callback-based class mapping. It must not use reflection, annotations, files, assets, graphics, backend services, or platform APIs. |
| `:libfdx:framework:collections` | `io.github.libfdx:collections` | Specialized collections and allocation-conscious data structures for engine hot paths where standard Java collections are not enough. |

### 9.2. Runtime Modules

| Gradle path | Maven coordinate | Purpose |
| --- | --- | --- |
| `:libfdx:framework:fdx:core` | `io.github.libfdx:fdx` | Single public fdx module: lifecycle primitives, resource ownership, errors, logging API, provider identity, async primitives, and shared runtime services such as FreeType rasterization and optional native math helpers. This should not contain files, input, audio, assets, rendering, UI, physics, or backend code. |
| `:libfdx:framework:fdx:fdx-build` | internal | Internal Gradle build module that owns runtime fdx native dependency resolution through `fdx-natives` prebuilt packages plus desktop, Android, and web CMake task configuration. It is not published to Maven. |
| `:libfdx:framework:fdx:platform:shared` | `io.github.libfdx:fdx_shared` | Shared runtime fdx native-source/tooling artifact from `libfdx/framework/fdx/platform/shared`. It packages `libfdx-native/...` C/C++ source resources used by the Gradle plugin and standalone builders when TeaVM/native output is generated in a user project. It is resolved transitively by native backend artifacts and is not a normal user `implementation` dependency. |
| `:libfdx:framework:fdx:platform:desktop` | `io.github.libfdx:fdx_desktop` | Desktop runtime fdx native-resource JAR from `libfdx/framework/fdx/platform/desktop`; packages `fdx-build` generated `fdx.dll`, `libfdx.so`, and `libfdx.dylib` under `libfdx-native/desktop/<classifier>/` for the classifiers generated by the current build. |
| `:libfdx:framework:fdx:platform:android` | `io.github.libfdx:fdx_android` | Android runtime fdx native AAR from `libfdx/framework/fdx/platform/android` that packages `fdx-build` generated `libfdx.so` files for Android ABIs. Android backends depend on this module instead of compiling runtime fdx native code themselves. |
| `:libfdx:framework:fdx:platform:web` | `io.github.libfdx:fdx_web` | Web runtime fdx native-resource JAR from `libfdx/framework/fdx/platform/web`; packages `fdx-build` generated `fdx.js` and `fdx.wasm`. |
| `:libfdx:framework:application` | `io.github.libfdx:application` | Application lifecycle API: create, resize, render, pause, resume, dispose, main loop contracts, application configuration, and platform capability discovery. |
| `:libfdx:framework:files` | `io.github.libfdx:files` | File abstraction: classpath/internal/local/external files, virtual file handles, path normalization, read/write capabilities, and storage rules per backend. |
| `:libfdx:framework:storage` | `io.github.libfdx:storage` | Persistent key/value storage for local user data and rebuildable cache data. It supports primitives, strings, bytes, JSON trees, explicit JSON codecs, and user-provided byte transforms for encryption/compression without owning encryption itself. |
| `:libfdx:framework:input` | `io.github.libfdx:input` | Keyboard, mouse, touch, gestures, text input, cursor state, gamepad/controller contracts, mappings, hotplugging events, vibration contracts, dead zones, and input routing primitives. |
| `:libfdx:framework:display` | `io.github.libfdx:display` | Display/presentation abstraction: logical and framebuffer size, position, title, content scale, monitor/work-area bounds, focus/minimize state, visibility, opacity, close requests, and creation/destruction of additional displays where supported. |
| `:libfdx:framework:net` | `io.github.libfdx:net` | Networking API: HTTP, WebSocket, provider-neutral multiplayer transports, reusable packet buffers, reusable inbound queue dispatch, tick-limited processing, packet transforms, manual message codecs, and transport provider SPI. |

### 9.3. Input Extension Modules

There are no separate input-extension Gradle projects in the current repository. Portable gamepad contracts live in `framework/input`; each checked-in backend supplies its platform input/gamepad implementation directly. A future standalone input provider must be added to the Gradle graph and module catalog before its coordinate is documented.

### 9.4. Assets Modules

| Gradle path | Maven coordinate | Purpose |
| --- | --- | --- |
| `:libfdx:framework:assets:manager` | `io.github.libfdx:asset_manager` | Asset manager API: descriptors, handles, dependencies, async loading contracts, update-thread completion, and cache/lifetime rules. It should not force specific file formats. |
| `:libfdx:framework:assets:loaders` | `io.github.libfdx:asset_loaders` | Common provider-neutral loaders built on `framework/assets/manager`: currently PNG/JPG image loading to `ImageData` and JSON loading to `JsonValue`. Format-specific heavy dependencies should stay optional. This module must not create provider-backed `Texture` handles directly. |

### 9.5. Audio Extension Modules

Audio is future direction only. The current repository contains no `framework/audio`, audio-provider project, audio Maven artifact, or `Fdx.audio()` accessor. Names such as miniaudio, WebAudio, `AudioDevice`, `Sound`, and `Music` are not current libFDX APIs.

### 9.6. Net Extension Modules

Net transport provider modules build on `framework/net`. WebRTC is kept in this repository so Gradle/Maven wiring stays with the rest of libFDX.

| Gradle path | Maven coordinate | Purpose |
| --- | --- | --- |
| `:libfdx:extensions:net:webrtc:core` | `io.github.libfdx:webrtc_core` | WebRTC provider-specific public types, provider ID, ICE/STUN/TURN configuration, signaling contracts/codecs, provider-neutral bridge interfaces, and shared Java transport glue that depends on `framework/net` and `framework/json`. |
| `:libfdx:extensions:net:webrtc:signaling_server` | `io.github.libfdx:webrtc_signaling_server` | Standalone Java-WebSocket signaling server app/library for rooms, peer discovery, SDP/ICE relay, auth/session hooks, room and join policies, message policy, peer ID generation, tick-limited processing, reusable queued event storage, max peers, idle cleanup, and logging hooks. It exposes the `webrtc_signaling_server_run` task. It is not a gameplay/TCP server and it is not a TURN server. |
| `:libfdx:extensions:net:webrtc:platform:desktop_jni` | `io.github.libfdx:webrtc_desktop_jni` | Desktop WebRTC runtime/binding variant using `dev.onvoid.webrtc:webrtc-java`, native runtime classifiers, and Java-WebSocket signaling. |
| `:libfdx:extensions:net:webrtc:platform:web` | `io.github.libfdx:webrtc_web` | Browser WebRTC runtime/binding variant using TeaVM JS interop over browser `RTCPeerConnection`, data channels, and WebSocket signaling. |
| `:libfdx:extensions:net:webrtc:platform:android_jni` | `io.github.libfdx:webrtc_android_jni` | Android WebRTC runtime/binding variant using `io.github.webrtc-sdk:android` and Java-WebSocket signaling. |

### 9.7. Graphics Modules

| Gradle path | Maven coordinate | Purpose |
| --- | --- | --- |
| `:libfdx:framework:graphics` | `io.github.libfdx:graphics` | Current low-level provider-neutral rendering slice: graphics managers/contexts/devices, backend attachments, buffers, textures/views, framebuffers/readback, meshes, WGSL shader descriptors/reflection, render pipelines, frame-owned command encoders, and render passes. Compute, public samplers/bind groups/queues/command buffers, and a public capabilities object are not implemented. |

Shader authoring is WGSL-only. The detailed shader architecture, including
portable profiles, Tint as the first compiler backend, runtime/setup-time
compilation, and optional editor hot reload,
lives in [SHADERS.md](SHADERS.md).

The short architecture rule is that normal game runtimes may pass WGSL directly.
Providers that cannot consume WGSL translate it through the optional
`framework/fdx/core` shader compiler capability during shader-module creation
when the active platform packages that capability. Direct GLSL, SPIR-V, and MSL
are generated target artifacts, not public shader authoring sources.

### 9.8. wgpu/WebGPU Provider Modules

| Gradle path | Maven coordinate | Purpose |
| --- | --- | --- |
| `:libfdx:extensions:graphics:wgpu:core` | `io.github.libfdx:wgpu_core` | Base WebGPU/wgpu provider. Public setup/escape-hatch types are `WGPUProvider`, `WGPUConfiguration`, `WGPUBackend`, `WGPULoaderBackend`, and `WGPUContext`; resource handle implementations remain internal. The attachment consumes the `framework/graphics` native-window bridge. |
| `:libfdx:extensions:graphics:wgpu:platform:desktop_jni` | `io.github.libfdx:wgpu_desktop_jni` | Desktop jWebGPU JNI runtime dependency module. It contributes the JNI binding/native libraries and should not contain WGPU provider Java classes unless the JNI binding requires variant-specific Java code. |
| `:libfdx:extensions:graphics:wgpu:platform:desktop_ffm` | `io.github.libfdx:wgpu_desktop_ffm` | Desktop WebGPU/wgpu runtime dependency module using Java FFM bindings. This module requires Java 25 because the current jWebGPU FFM runtime is Java 25-only. |
| `:libfdx:extensions:graphics:wgpu:platform:web` | `io.github.libfdx:wgpu_web` | WebGPU integration for JavaScript web targets. TeaVM WasmGC does not preserve the JS-native intrinsics used by the substituted jWebGPU binding classes, so current Wasm launchers use WebGL and do not expose WebGPU-Wasm tasks. |
| `:libfdx:extensions:graphics:wgpu:platform:android_jni` | `io.github.libfdx:wgpu_android_jni` | Android WebGPU/wgpu runtime packaging and surface integration using Android JNI/ABI packaging. |

### 9.9. GL/WebGL Provider Modules

| Gradle path | Maven coordinate | Purpose |
| --- | --- | --- |
| `:libfdx:extensions:graphics:gl:core` | `io.github.libfdx:gl_core` | Shared GL-family configuration, GL command abstraction, and common graphics API implementation classes. It must not depend on LWJGL, WebGL, or a concrete backend. The desktop backend exposes GL through `DesktopOpenGLProvider` in `:libfdx:backends:desktop`. |
| `:libfdx:extensions:graphics:gl:platform:desktop` | `io.github.libfdx:gl_desktop` | Desktop GL runtime dependency module for the desktop backend. It contributes the LWJGL GL API and native artifacts and should not contain GL provider Java classes. |
| `:libfdx:extensions:graphics:gl:platform:desktop_c` | `io.github.libfdx:gl_desktop_c` | Desktop GL native resource module for the desktop_c backend. It contributes GLEW headers and Windows libraries for generated C builds, reuses shared C native resources from `:libfdx:backends:c_shared`, and should not contain GL provider Java classes. |
| `:libfdx:extensions:graphics:gl:platform:web` | `io.github.libfdx:gl_web` | Browser WebGL provider for web JS/Wasm targets. It adapts the shared GL-family implementation to WebGL semantics directly, not by forcing desktop GL assumptions onto WebGL. |

### 9.10. Vulkan Provider Modules

| Gradle path | Maven coordinate | Purpose |
| --- | --- | --- |
| `:libfdx:extensions:graphics:vulkan:core` | `io.github.libfdx:vulkan_core` | Shared Vulkan provider configuration, provider ID, and public Vulkan-facing setup types that do not depend on LWJGL or a concrete backend. The desktop backend exposes Vulkan through `DesktopVulkanProvider` in `:libfdx:backends:desktop`. |
| `:libfdx:extensions:graphics:vulkan:platform:desktop` | `io.github.libfdx:vulkan_desktop` | Desktop Vulkan runtime dependency module for the desktop backend. It contributes the LWJGL Vulkan artifact and should not contain Vulkan provider Java classes. |
| `:libfdx:extensions:graphics:vulkan:platform:desktop_c` | `io.github.libfdx:vulkan_desktop_c` | Desktop Vulkan bridge resources for the native runtime stack. It contributes the libfdx C/C++ bridge, prefers CMake `Vulkan::Vulkan` when a Vulkan SDK is installed, and otherwise uses a narrow local ABI shim while loading the system Vulkan runtime at run time. |
| `:libfdx:extensions:graphics:vulkan:platform:android_jni` | `io.github.libfdx:vulkan_android_jni` | Android Vulkan runtime packaging and surface integration using Android JNI/ABI packaging. |

### 9.11. Graphics 2D Modules

Use `g2d` instead of `2d` because Java package segments cannot start with a number.

| Gradle path | Maven coordinate | Purpose |
| --- | --- | --- |
| `:libfdx:framework:g2d` | `io.github.libfdx:g2d` | Complete 2D toolkit: sprites, sprite batches, sprite shader effects, fog-of-war overlays, shape rendering, texture regions, atlases, bitmap fonts, vector-rasterized font atlases, text layout, 2D particles, tile maps, and 2D render helpers. Internally this module can still use packages such as `font`, `particles`, and `maps`, but users should depend on one `g2d` artifact. Camera state belongs to the shared `framework/camera` `Camera`, not a g2d-specific camera class. |

### 9.12. Graphics Camera Module

| Gradle path | Maven coordinate | Purpose |
| --- | --- | --- |
| `:libfdx:framework:camera` | `io.github.libfdx:camera` | Shared camera state and focused camera controllers, including reusable 2D pan/zoom, free/editor, first-person, third-person, orbit, orthographic, cinematic controllers, and cinematic path samplers. This module depends on `framework/input` for input-backed controllers and does not own renderer helpers. |

### 9.13. Graphics 3D Modules

Use `g3d` instead of `3d` because Java package segments cannot start with a number.

| Gradle path | Maven coordinate | Purpose |
| --- | --- | --- |
| `:libfdx:framework:g3d` | `io.github.libfdx:g3d` | Complete 3D toolkit: `Batch3D`, `ModelBatch`, meshes, models, model instances, materials, PBR shader/material data, skinned PBR GPU path, custom shader hooks, lights, environments, sky environment lighting, point-light and spotlight shading, directional and cascaded shadow maps, distance fog, fog-of-war overlays, animation, glTF hierarchy/skin/animation loading, render queues, render paths, frame target helpers, and scene rendering helpers. Internally this module can still use packages such as `models`, `animation`, `materials`, `shaders`, `render`, and `lighting`, but users should depend on one `g3d` artifact. Shared camera state belongs to `framework/camera`; input-backed camera controllers belong to `framework/camera`'s controller subpackage. |

### 9.14. UI Modules

UI modules contain built-in libfdx UI solutions. `ui-kit` is the default retained-mode UI toolkit and one user-facing library. Do not split `ui-kit` into separate scene graph and widget artifacts. See [UI_KIT.md](UI_KIT.md) for the detailed UI toolkit specification.

| Gradle path | Maven coordinate | Purpose |
| --- | --- | --- |
| `:libfdx:framework:ui-kit` | `io.github.libfdx:ui_kit` | Default libfdx UI toolkit: Compose-inspired declarative authoring over a retained runtime, widgets, layout, animation, themes, ninepatch styling, hit detection, input focus, and 2D rendering integration. Users should depend on one `ui_kit` artifact for this UI solution. The folder repeats `ui` for clarity inside the repository, but the artifact must stay `ui_kit`, not `ui_ui_kit`. |

### 9.15. Optional Extension Modules

The ECS module is an optional pure Java entity component system. It is framework-owned, user-created, and independent from backend/provider setup. Engines, editors, samples, and user projects that want the framework ECS depend on `io.github.libfdx:ecs`; projects that do not want ECS omit the artifact.

| Gradle path | Maven coordinate | Purpose |
| --- | --- | --- |
| `:libfdx:extensions:ecs` | `io.github.libfdx:ecs` | Optional entity component system: root `World` plus `command`, `component`, `entity`, `event`, `manager`, `query`, and `system` subpackages for integer entity handles, component stores and mappers, matchers, entity lists, queued events, deferred world commands, managers, and systems. It is not an engine module and is not exposed through `Fdx`. |

Scenario validator modules are optional extension modules that contain reusable scenario validation engines and domain adapters. `extensions/scenario_validator/core` is not UI-only and not game-only. It can validate complete runtime flows, UI flows, input sequences, captures, events, and project probes. UI Kit validation is provided by an adapter module. See [SCENARIO_VALIDATOR.md](SCENARIO_VALIDATOR.md) for the detailed scenario validator contract.

| Gradle path | Maven coordinate | Purpose |
| --- | --- | --- |
| `:libfdx:extensions:scenario_validator:core` | `io.github.libfdx:scenario_validator` | Optional scenario validation engine: reusable catalogs, scenarios, runtime actions, waits, events, assertions, probes, reports, and visual capture integration. It is a user-facing engine/tooling module, not an internal test runner and not a dependency of normal runtime execution. |
| `:libfdx:extensions:scenario_validator:ui-kit` | `io.github.libfdx:scenario_validator_ui_kit` | Optional UI Kit adapter for `extensions/scenario_validator/core`: UI targets, UI actions, UI assertions, UI waits, UI events, UI captures, and validation ID integration for projects that use `ui-kit`. |

### 9.16. Backend Modules

Backend modules use one flat folder segment per concrete backend variant:

```text
backends/<runtime>
backends/<platform>
backends/<platform>_<implementation>
```

The concrete backend module owns the launcher, lifecycle wiring, platform event loop integration, display creation, file/input bridge, and service registration. This allows several backend choices for the same platform without pretending that one module can represent every runtime.

Backends should attach graphics through the provider-neutral `framework/graphics` attachment SPI instead of constructing one graphics provider directly. For example, `backends/desktop` creates the GLFW window, exports a generic `NativeWindow`, and drives a `GraphicsAttachment` supplied by the launcher. `extensions/graphics/wgpu/core` consumes that generic native-window bridge, and `extensions/graphics/wgpu/platform/desktop_jni` supplies only the JNI runtime libraries. Shared game code still sees only `Graphics`.

Do not create a backend module that is also a parent folder for another backend module. For example, use `backends/android` and `backends/android_native`, not `backends/android` plus `backends/android/c`. Shared TeaVM build and native resource payloads belong in the flat `backends/c_shared` sibling.

Backend artifacts should be `implementation` dependencies in launcher/platform modules, not `runtimeOnly` dependencies. The platform launcher compiles against the selected backend implementation because it creates and configures the actual application runtime. Resource-only backend support artifacts are the exception: provider modules may depend on them as `runtimeOnly` because they carry native build resources and no Java API. Graphics, audio, and other providers may still be `runtimeOnly` when game code compiles only against their common APIs.

Artifact IDs should include the implementation name unless the implementation is the platform's normal default.

| Gradle path | Maven coordinate | Purpose |
| --- | --- | --- |
| `:libfdx:backends:desktop` | `io.github.libfdx:backend_desktop` | Default desktop backend using LWJGL3 internally for application lifecycle, OS display/window creation, input, and files. It can expose desktop-owned provider setup classes such as `DesktopOpenGLProvider`, but graphics runtime/native modules such as `gl_desktop` and `wgpu_desktop_jni` are still selected by launcher dependencies. |
| `:libfdx:backends:c_shared` | `io.github.libfdx:backend_c_shared` | Flat shared C build and native resource support, including TeaVM build execution, shared builder errors, optimization settings, and classpath resources copied into generated native projects. It is not a parent folder for web or native backend modules. |
| `:libfdx:backends:desktop_c` | `io.github.libfdx:backend_desktop_c` | Desktop backend for native runtime desktop targets. It owns GLFW window/lifecycle wiring, `NativeBuilder`, desktop-c project generation, and currently exposes `DesktopCOpenGLProvider` and `DesktopCVulkanProvider` while keeping graphics attached through the common `framework/graphics` SPI. |
| `:libfdx:backends:psp` | `io.github.libfdx:backend_psp` | PSP TeaVM C backend module. It owns `PspBuilder`, PSP project generation, PSP native import declarations, PSP native resource payloads, `PspApplicationBackend`, `PspApplicationConfig`, and the first constrained PSP common graphics API slice for clear, non-indexed SpriteBatch-style rendering, and RGBA8 power-of-two sampled textures. The current application backend exposes fixed 480x272 display metadata, `PspGraphicsContext`, read-only internal/classpath assets staged through `libfdx.assets` or `PspBuilder.asset(...)`, and PSP controls as one standard-mapped gamepad plus controller-backed portable key events for focus/navigation. Physical keyboard, pointer, touch, text input, cursor, writable files, audio, and broader graphics runtime behavior belong here only when implemented as PSP backend classes. |
| `:libfdx:backends:ios_c` | `io.github.libfdx:backend_ios_c` | Experimental iOS TeaVM C backend module. It owns the iOS C application lifecycle bridge, generated Xcode project shell, Swift/GLKit and MetalKit view controller templates, iOS GLES and native Metal provider/API adapters, graphics API project selection, and generated asset/native-resource layout. The `metal` graphics option uses a native Metal command-encoder path with reflected texture/sampler bindings, PBR named uniform-buffer uploads, and depth-state support when MSL is available. iOS C currently registers no runtime shader compiler provider, so WGSL-only built-in shaders require a future iOS compiler bridge on this backend. Launcher modules must declare this backend as an explicit `implementation` dependency; the Gradle plugin configures TeaVM C and Xcode generation tasks but does not add runtime dependencies to user projects. The generated Xcode project is the handoff point for simulator/device builds on macOS with Xcode. |
| `:libfdx:backends:web` | `io.github.libfdx:backend_web` | Default browser backend using TeaVM internally for canvas/display integration, browser input and text-editor bridging, browser lifecycle, UI-kit preload screen rendering, `WebBuilder`, and webapp asset metadata generation. It supports JS and Wasm build targets through the libfdx Gradle plugin and should not hard-code one graphics, audio, or gamepad provider. |
| `:libfdx:backends:android` | `io.github.libfdx:backend_android` | Default Android backend: activity/view integration, Android input/files, and mobile lifecycle. Graphics, audio, and gamepad providers should remain replaceable. |

Additional backend implementations should be added as new flat variant folders only when there is a real second backend choice to support. Until then, keep the default platform backend at the platform folder itself.

### 9.17. Tool Modules

| Gradle path | Coordinate/status | Purpose |
| --- | --- | --- |
| `libfdx/tools/gradle-plugin` included build | `io.github.libfdx:gradle-plugin` | Gradle plugin for libfdx platform targets. It is intentionally an included build under `libfdx/tools`, not `buildSrc`, so this repository and external projects can consume the same plugin with `pluginManagement { includeBuild("<libfdx>/libfdx/tools/gradle-plugin") }`. The public plugin ID is `io.github.libfdx`, and external builds should configure it with one `libfdx { ... }` block. The target blocks are `desktopJvm`, `js`, `wasm`, `desktopC`, `psp`, and `iosC`. Inside this repository, dedicated plugin coverage modules such as `:samples:basic:platform:plugin` and `:tests:platform:plugin` validate combined plugin usage, while plugin-first samples may apply the plugin directly in their platform modules. The plugin must not add backend `implementation` or `api` dependencies to user projects; platform launcher modules declare their own backend artifacts. |
| `:libfdx:tools:font` | `io.github.libfdx:tools_font` | Build-time font tools. The current tool generates AngelCode BMFont-style `.fnt` metadata and PNG atlases from TTF files for platforms that should ship prebuilt bitmap fonts, such as PSP. It is a general libfdx tooling module, not a TeaVM backend module. |
| `:libfdx:tools:project-generator:core` | internal | Project generator model, validation, template rendering, and in-memory generated project tree. It must not depend on desktop, web, UIKit, or filesystem APIs. |
| `:libfdx:tools:project-generator:ui` | internal | Shared UIKit project generator UI. It depends on project-generator core and delegates persistence/download behavior to a platform export target. |
| `:libfdx:tools:project-generator:platform:desktop` | internal | Desktop LWJGL3 runtime for the shared project generator UI. It writes generated project files to a selected directory. |
| `:libfdx:tools:project-generator:platform:web` | internal | Web runtime for the shared project generator UI. It packages the generated in-memory file tree as a ZIP and starts a browser download instead of assuming direct folder writes. Its Gradle tasks call `WebBuilder` directly for JS and Wasm builds. |

Project-generator submodules are internal launch tooling in the first implementation. Add them to the Maven deploy allowlist only after the public distribution shape is decided.

### 9.18. Test Modules

| Gradle path | Status | Purpose |
| --- | --- | --- |
| `:tests:core` | internal | Core test project containing reusable runtime, input, assets, graphics, g2d, g3d, UI, and backend/provider scenarios. |
| `:tests:platform:desktop` | internal | Desktop test runner using `backends/desktop`. Dedicated Gradle tasks select provider stacks such as GL, WGPU JNI, or WGPU FFM. |
| `:tests:platform:desktop_c` | internal | Desktop test runner using `backends/desktop_c` with selected C-backed provider implementations. |
| `:tests:platform:web` | internal | Web test runner source set for selected web provider implementations. It exposes thin `test_web*` task aliases while the plugin-use test module owns the generated web target implementation. |
| `:tests:platform:android` | internal | Android test runner. Gradle tasks select Android backend/provider variants such as GLES or WGPU. |
| `:tests:platform:psp` | internal | PSP TeaVM C platform launcher for the shared `tests/core` selector. It exposes thin `test_psp_generate`, `test_psp_build`, and `test_psp_ppsspp_capture` task entrypoints while the plugin-use module owns the generated PSP target implementation. |
| `:tests:platform:plugin` | internal | Dedicated test-side Gradle plugin DSL coverage module. It mirrors desktop JVM, web, desktop-c, and PSP test launch tasks through the libFDX Gradle plugin and must stay one module instead of splitting into platform-suffixed plugin folders. |

### 9.19. Benchmark Modules

Performance benchmarks live under the root `benchmark/` folder. Keep correctness assertions in `tests/` and use benchmark modules only after correctness is established. The benchmark modules own benchmark cases, platform runners, provider comparisons, and generated performance reports.

| Gradle path | Purpose |
| --- | --- |
| `:benchmark:core` | Shared benchmark cases and result writing. |
| `:benchmark:platform:desktop` | Desktop JVM benchmark runner using `backends/desktop` with GL, WGPU JNI, WGPU FFM, and Vulkan task variants. |
| `:benchmark:platform:desktop_c` | Desktop C benchmark launcher module using `backends/desktop_c` with thin benchmark task aliases. |
| `:benchmark:platform:plugin` | Dedicated benchmark-side Gradle plugin DSL module that generates the desktop_c executable used by desktop C benchmark aliases. |

### 9.20. Sample Modules

Every sample must be a sample family, not one flat module. New samples should start with this structure:

```text
samples/<sample-name>/
  core/
  platform/
    desktop/
    desktop_c/
    web/
    android/
    ios_c/
```

The sample `core` module contains the shared sample application code. Platform modules are launchers/wiring modules only: they select the backend, platform packaging, and platform-specific configuration. Provider stacks such as JNI or FFM should be selected by dedicated Gradle tasks or platform build variants inside the platform sample module, not by adding more sample folders. A different backend/runtime family such as desktop C or iOS C uses its own platform module because it has different compiler output and native build tasks. These modules should be created when the sample is created so every sample starts cross-platform by default.

Required module shape for every sample:

| Gradle path | Purpose |
| --- | --- |
| `:samples:<name>:core` | Shared sample logic and assets references. No platform launcher code belongs here. |
| `:samples:<name>:platform:desktop` | Desktop launcher for the sample. Depends on `<name>:core`, one desktop backend implementation, and provider stacks selected by Gradle. |
| `:samples:<name>:platform:desktop_c` | desktop_c launcher for the sample. Depends on `<name>:core`, `backends/desktop_c`, and desktop_c provider/native-resource modules selected by Gradle. |
| `:samples:<name>:platform:web` | Web launcher for the sample. Depends on `<name>:core`, one web backend implementation, and browser providers selected by Gradle. |
| `:samples:<name>:platform:android` | Android launcher for the sample. Depends on `<name>:core`, one Android backend implementation, and Android provider stacks selected by Gradle. |
| `:samples:<name>:platform:ios_c` | iOS TeaVM C launcher for the sample. Depends on `<name>:core` and `backends/ios_c`; Gradle plugin generation is owned by the plugin-use module and writes GLES/GLKit or native Metal/MetalKit Xcode handoff projects. |

Do not create stack-specific sample modules such as `samples/g2d/platform/desktop_jni` or nested sample folders for JNI/FFM. The sample platform module should stay stable while Gradle changes the selected provider stack.

The internal `:samples:basic:platform:plugin` module is the exception that
exercises Gradle plugin DSL usage. It is not a normal runtime platform launcher
shape and should not be copied into new sample families unless a dedicated
plugin-use sample is explicitly needed.

The internal `:tests:platform:plugin` module is the analogous test-side plugin
DSL coverage module. It owns plugin-use test target tasks and should stay one module;
do not split it into `plugin_web`, `plugin_desktop_c`, `plugin_psp`, or `plugin_ios_c`
folders.

Combined plugin-use modules may set `desktopJvm.runtimeClasspath(...)` to the
desktop launcher dependency path. That keeps desktop JVM plugin tasks from
loading web or TeaVM C runtime jars that are present only for the same module's
web, desktop-c, PSP, or iOS C plugin target tasks.

Planned sample families:

| Sample family | Purpose |
| --- | --- |
| `basic` | Minimal application sample showing lifecycle, files, input, and platform launchers. |
| `g2d` | 2D sample showing textures, sprite rendering, cameras, particles, maps, and fonts if included. |
| `g3d` | 3D sample showing meshes, models, materials, lighting, animation, and cameras if included. |
| `ui` | UI Kit sample showing UI roots, nodes, widgets, skins, and input routing. |

## 10. User Dependency Examples

These examples use a placeholder version:

```kotlin
val libfdxVersion = "1.0"
```

External users would normally use published coordinates:

```kotlin
implementation("io.github.libfdx:fdx:$libfdxVersion")
```

Inside this repository, tests and samples should keep the source-versus-published dependency choice explicit:

```kotlin
if (LibExt.usePublishedLibfdx) {
    implementation("${LibExt.fdxGroup}:fdx:${LibExt.publishedLibfdxVersion}")
} else {
    implementation(project(":libfdx:framework:fdx:core"))
}
```

### 10.1. All Module Dependency Reference

This is a reference list, not a recommendation to put every module in one game. Users should choose only the modules they need.

```kotlin
dependencies {
    implementation("io.github.libfdx:fdx:$libfdxVersion")
    runtimeOnly("io.github.libfdx:fdx_desktop:$libfdxVersion")
    runtimeOnly("io.github.libfdx:fdx_web:$libfdxVersion")
    runtimeOnly("io.github.libfdx:fdx_android:$libfdxVersion")
    implementation("io.github.libfdx:math:$libfdxVersion")
    implementation("io.github.libfdx:json:$libfdxVersion")
    implementation("io.github.libfdx:collections:$libfdxVersion")

    implementation("io.github.libfdx:application:$libfdxVersion")
    implementation("io.github.libfdx:files:$libfdxVersion")
    implementation("io.github.libfdx:input:$libfdxVersion")
    implementation("io.github.libfdx:display:$libfdxVersion")
    implementation("io.github.libfdx:storage:$libfdxVersion")
    implementation("io.github.libfdx:net:$libfdxVersion")
    implementation("io.github.libfdx:webrtc_core:$libfdxVersion")
    implementation("io.github.libfdx:webrtc_signaling_server:$libfdxVersion") // only for apps/tools that host signaling
    runtimeOnly("io.github.libfdx:webrtc_desktop_jni:$libfdxVersion")
    runtimeOnly("io.github.libfdx:webrtc_web:$libfdxVersion")
    runtimeOnly("io.github.libfdx:webrtc_android_jni:$libfdxVersion")

    implementation("io.github.libfdx:asset_manager:$libfdxVersion")
    implementation("io.github.libfdx:asset_loaders:$libfdxVersion")

    implementation("io.github.libfdx:graphics:$libfdxVersion")
    implementation("io.github.libfdx:camera:$libfdxVersion")
    implementation("io.github.libfdx:gl_core:$libfdxVersion")
    runtimeOnly("io.github.libfdx:gl_desktop:$libfdxVersion")
    runtimeOnly("io.github.libfdx:gl_desktop_c:$libfdxVersion")
    runtimeOnly("io.github.libfdx:gl_web:$libfdxVersion")
    implementation("io.github.libfdx:wgpu_core:$libfdxVersion")
    runtimeOnly("io.github.libfdx:wgpu_desktop_jni:$libfdxVersion")
    runtimeOnly("io.github.libfdx:wgpu_desktop_ffm:$libfdxVersion")
    runtimeOnly("io.github.libfdx:wgpu_web:$libfdxVersion")
    runtimeOnly("io.github.libfdx:wgpu_android_jni:$libfdxVersion")
    implementation("io.github.libfdx:vulkan_core:$libfdxVersion")
    runtimeOnly("io.github.libfdx:vulkan_desktop:$libfdxVersion")
    runtimeOnly("io.github.libfdx:vulkan_desktop_c:$libfdxVersion")
    runtimeOnly("io.github.libfdx:vulkan_android_jni:$libfdxVersion")
    implementation("io.github.libfdx:g2d:$libfdxVersion")
    implementation("io.github.libfdx:g3d:$libfdxVersion")

    implementation("io.github.libfdx:ecs:$libfdxVersion")
    implementation("io.github.libfdx:ui_kit:$libfdxVersion")
    implementation("io.github.libfdx:scenario_validator:$libfdxVersion")
    implementation("io.github.libfdx:scenario_validator_ui_kit:$libfdxVersion")

    implementation("io.github.libfdx:backend_desktop:$libfdxVersion")
    implementation("io.github.libfdx:backend_desktop_c:$libfdxVersion")
    implementation("io.github.libfdx:backend_psp:$libfdxVersion")
    implementation("io.github.libfdx:backend_ios_c:$libfdxVersion")
    implementation("io.github.libfdx:backend_web:$libfdxVersion")
    implementation("io.github.libfdx:backend_android:$libfdxVersion")
}
```

The published font tool is optional:

```kotlin
dependencies {
    implementation("io.github.libfdx:tools_font:$libfdxVersion")
}
```

The project-generator modules are repository tooling and are not in the Maven deploy allowlist.

Sample modules are source examples, not artifacts that normal users should depend on. The implemented sample modules are:

```text
:samples:basic:core
:samples:basic:platform:desktop
:samples:basic:platform:desktop_c
:samples:basic:platform:web
:samples:basic:platform:android
:samples:basic:platform:ios_c
:samples:basic:platform:plugin
:samples:ecs-platformer:core
:samples:ecs-platformer:platform:desktop
:samples:ecs-platformer:platform:desktop_c
:samples:ecs-platformer:platform:web
:samples:ecs-platformer:platform:android
:samples:ecs-platformer:platform:ios_c
```

Future sample families should reuse the same `:samples:<name>:core` and `:samples:<name>:platform:<platform>` shape when added.
`samples:ecs-platformer` keeps that full shape, but its desktop, web, desktop C,
and iOS C platform modules apply `io.github.libfdx` directly and keep their
build files to plugin target/asset DSL plus launcher ownership. The root build
convention wires their local sample/backend classpaths so those platform build
files do not carry dependency blocks. Android remains a normal Android
application module because it owns manifest, resource, and activity wiring.

### 10.2. Logic-Only Module

The current repository does not contain a headless backend or a
`backend_headless` artifact. Logic-only libraries and JUnit tests can depend on
the leaf APIs they use without starting an `ApplicationListener`:

```kotlin
dependencies {
    implementation("io.github.libfdx:math:$libfdxVersion")
    implementation("io.github.libfdx:collections:$libfdxVersion")
    implementation("io.github.libfdx:json:$libfdxVersion")
}
```

A runnable headless application example must wait until a real backend module,
lifecycle implementation, tests, and artifact exist.

### 10.3. Desktop 2D Game

This is the likely first real user-facing target.

```kotlin
dependencies {
    implementation("io.github.libfdx:fdx:$libfdxVersion")
    implementation("io.github.libfdx:math:$libfdxVersion")

    implementation("io.github.libfdx:application:$libfdxVersion")
    implementation("io.github.libfdx:files:$libfdxVersion")
    implementation("io.github.libfdx:input:$libfdxVersion")
    implementation("io.github.libfdx:display:$libfdxVersion")

    implementation("io.github.libfdx:asset_manager:$libfdxVersion")
    implementation("io.github.libfdx:asset_loaders:$libfdxVersion")

    implementation("io.github.libfdx:graphics:$libfdxVersion")
    implementation("io.github.libfdx:g2d:$libfdxVersion")

    implementation("io.github.libfdx:wgpu_core:$libfdxVersion")
    runtimeOnly("io.github.libfdx:wgpu_desktop_jni:$libfdxVersion")
    implementation("io.github.libfdx:backend_desktop:$libfdxVersion")
}
```

### 10.4. Desktop 3D Game

3D rendering uses `framework/g3d` instead of `framework/g2d`.

```kotlin
dependencies {
    implementation("io.github.libfdx:fdx:$libfdxVersion")
    implementation("io.github.libfdx:math:$libfdxVersion")

    implementation("io.github.libfdx:application:$libfdxVersion")
    implementation("io.github.libfdx:files:$libfdxVersion")
    implementation("io.github.libfdx:input:$libfdxVersion")
    implementation("io.github.libfdx:display:$libfdxVersion")

    implementation("io.github.libfdx:asset_manager:$libfdxVersion")
    implementation("io.github.libfdx:asset_loaders:$libfdxVersion")

    implementation("io.github.libfdx:graphics:$libfdxVersion")
    implementation("io.github.libfdx:g3d:$libfdxVersion")

    implementation("io.github.libfdx:wgpu_core:$libfdxVersion")
    runtimeOnly("io.github.libfdx:wgpu_desktop_jni:$libfdxVersion")
    implementation("io.github.libfdx:backend_desktop:$libfdxVersion")
}
```

### 10.5. Desktop 2D Game With Explicit wgpu Implementation

If the selected desktop backend implementation does not bring the graphics provider transitively, the user can select it directly.

```kotlin
dependencies {
    implementation("io.github.libfdx:fdx:$libfdxVersion")
    implementation("io.github.libfdx:math:$libfdxVersion")

    implementation("io.github.libfdx:application:$libfdxVersion")
    implementation("io.github.libfdx:display:$libfdxVersion")
    implementation("io.github.libfdx:input:$libfdxVersion")
    implementation("io.github.libfdx:files:$libfdxVersion")

    implementation("io.github.libfdx:graphics:$libfdxVersion")
    implementation("io.github.libfdx:wgpu_core:$libfdxVersion")
    runtimeOnly("io.github.libfdx:wgpu_desktop_jni:$libfdxVersion")
    implementation("io.github.libfdx:g2d:$libfdxVersion")

    implementation("io.github.libfdx:backend_desktop:$libfdxVersion")
}
```

### 10.6. Desktop 2D Game With Explicit Vulkan Implementation

The game code can still compile against `graphics` and `g2d`; only the selected provider changes.

```kotlin
dependencies {
    implementation("io.github.libfdx:fdx:$libfdxVersion")
    implementation("io.github.libfdx:math:$libfdxVersion")

    implementation("io.github.libfdx:application:$libfdxVersion")
    implementation("io.github.libfdx:display:$libfdxVersion")
    implementation("io.github.libfdx:input:$libfdxVersion")
    implementation("io.github.libfdx:files:$libfdxVersion")

    implementation("io.github.libfdx:graphics:$libfdxVersion")
    implementation("io.github.libfdx:vulkan_core:$libfdxVersion")
    runtimeOnly("io.github.libfdx:vulkan_desktop:$libfdxVersion")
    implementation("io.github.libfdx:g2d:$libfdxVersion")

    implementation("io.github.libfdx:backend_desktop:$libfdxVersion")
}
```

### 10.7. Desktop 2D Game With Particles And Maps

Particles and maps are included in `g2d` because they are normal 2D rendering features.

```kotlin
dependencies {
    implementation("io.github.libfdx:g2d:$libfdxVersion")
}
```

### 10.8. UI Kit Application

`ui_kit` remains opt-in. A user who does not want it does not depend on it. `ecs` is also opt-in and is used only by projects that want libFDX's entity component system. `scenario_validator` is opt-in and is used only by projects that want reusable scenario validation. UI Kit validation uses the optional `scenario_validator_ui_kit` adapter.

```kotlin
dependencies {
    implementation("io.github.libfdx:fdx:$libfdxVersion")
    implementation("io.github.libfdx:math:$libfdxVersion")

    implementation("io.github.libfdx:application:$libfdxVersion")
    implementation("io.github.libfdx:display:$libfdxVersion")
    implementation("io.github.libfdx:input:$libfdxVersion")
    implementation("io.github.libfdx:files:$libfdxVersion")

    implementation("io.github.libfdx:asset_manager:$libfdxVersion")
    implementation("io.github.libfdx:asset_loaders:$libfdxVersion")

    implementation("io.github.libfdx:graphics:$libfdxVersion")
    implementation("io.github.libfdx:g2d:$libfdxVersion")

    implementation("io.github.libfdx:ecs:$libfdxVersion")
    implementation("io.github.libfdx:ui_kit:$libfdxVersion")
    implementation("io.github.libfdx:scenario_validator:$libfdxVersion")
    implementation("io.github.libfdx:scenario_validator_ui_kit:$libfdxVersion")

    implementation("io.github.libfdx:wgpu_core:$libfdxVersion")
    runtimeOnly("io.github.libfdx:wgpu_desktop_jni:$libfdxVersion")
    implementation("io.github.libfdx:backend_desktop:$libfdxVersion")
}
```

### 10.9. Web Game

The web backend implementation should choose browser-specific runtime services and graphics providers.

```kotlin
dependencies {
    implementation("io.github.libfdx:fdx:$libfdxVersion")
    implementation("io.github.libfdx:math:$libfdxVersion")

    implementation("io.github.libfdx:application:$libfdxVersion")
    implementation("io.github.libfdx:files:$libfdxVersion")
    implementation("io.github.libfdx:input:$libfdxVersion")
    implementation("io.github.libfdx:display:$libfdxVersion")
    implementation("io.github.libfdx:asset_manager:$libfdxVersion")

    implementation("io.github.libfdx:graphics:$libfdxVersion")
    implementation("io.github.libfdx:wgpu_web:$libfdxVersion")
    implementation("io.github.libfdx:g2d:$libfdxVersion")

    implementation("io.github.libfdx:backend_web:$libfdxVersion")
}
```

### 10.10. Game With Gamepads

Gamepad contracts are part of `input`. The current backends expose their
backend-owned `DefaultGamepads` implementation through `fdx.input()`; there is
no separately published `gamepads_desktop` provider.

```kotlin
dependencies {
    implementation("io.github.libfdx:input:$libfdxVersion")
    implementation("io.github.libfdx:backend_desktop:$libfdxVersion")
}
```

### 10.11. Local Repository Sample Dependencies

Tests and samples inside this repository generally use explicit `if (LibExt.usePublishedLibfdx)` branches for libFDX dependencies. By default the checked-in TOML sets the flag false, so clean source checkouts and CI use local project dependencies and the local Gradle plugin build. Settings still includes the local `:libfdx:*` modules so source projects remain available in the checkout; the Maven-vs-local choice belongs to each consumer dependency block unless a repository-only example intentionally opts out of published-artifact validation. ECS Platformer is such an example: its platform modules are plugin-first, example-only modules, so the root build convention wires their local sample/backend classpaths and their platform build files stay limited to plugin target and asset configuration. Builder-backed web tasks must not build local runtime fdx web native resources for Maven-backed consumers, but may attach those generated resources when local `:libfdx:*` project dependencies are reached directly or transitively. Developers can set `development.usePublishedLibfdx=true` in ignored root `local.properties` to resolve the Gradle plugin from Maven and use published coordinates from `LibExt.fdxGroup` and `LibExt.publishedLibfdxVersion`; `LibExt.pluginBootstrapLibfdxVersion` is reserved for the isolated included plugin build and does not select consumer or publication coordinates. The plugin included build must remain only the plugin project and must not remap the root source modules. Published desktop JVM artifacts must not encode only the publish host's LWJGL native classifier; they declare all supported LWJGL native artifacts as non-runtime-scope dependencies so LWJGL's loader can select the current runtime platform.

Sample `core` modules should depend on public framework APIs and feature modules:

```kotlin
// :samples:basic:core
dependencies {
    if (LibExt.usePublishedLibfdx) {
        api("${LibExt.fdxGroup}:application:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:graphics:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:g2d:${LibExt.publishedLibfdxVersion}")
    } else {
        api(project(":libfdx:framework:application"))
        implementation(project(":libfdx:framework:graphics"))
        implementation(project(":libfdx:framework:g2d"))
    }
}
```

Sample platform modules should depend on their sample `core` module and let Gradle select platform providers. The concrete dependencies below show one default stack for each platform; additional stacks should be represented as dedicated Gradle tasks or platform build variants, not additional sample folders.

```kotlin
// :samples:basic:platform:desktop
dependencies {
    implementation(project(":samples:basic:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:application:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:display:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:wgpu_core:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:backend_desktop:${LibExt.publishedLibfdxVersion}")

        glRuntimeClasspath("${LibExt.fdxGroup}:gl_desktop:${LibExt.publishedLibfdxVersion}")
        vulkanRuntimeClasspath("${LibExt.fdxGroup}:vulkan_desktop:${LibExt.publishedLibfdxVersion}")
        wgpuRuntimeClasspath("${LibExt.fdxGroup}:wgpu_desktop_ffm:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:framework:application"))
        implementation(project(":libfdx:framework:display"))
        implementation(project(":libfdx:extensions:graphics:wgpu:core"))
        implementation(project(":libfdx:backends:desktop"))

        glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
        vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
        wgpuRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
    }
}
```

Example desktop sample stack selection:

```bash
./gradlew :samples:basic:platform:desktop:basic_desktop_gl_run
./gradlew :samples:basic:platform:desktop:basic_desktop_wgpu_run
./gradlew :samples:basic:platform:desktop:basic_desktop_vulkan_run
```

```kotlin
// :samples:basic:platform:desktop_c
dependencies {
    implementation(project(":samples:basic:core"))

    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_desktop_c:${LibExt.publishedLibfdxVersion}")
        runtimeOnly("${LibExt.fdxGroup}:gl_desktop_c:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:desktop_c"))
        runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_c"))
    }
}
```

```kotlin
// :samples:basic:platform:ios_c
dependencies {
    implementation(project(":samples:basic:core"))

    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_ios_c:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:ios_c"))
    }
}
```

```kotlin
// :samples:basic:platform:web
dependencies {
    implementation(project(":samples:basic:core"))

    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_web:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:gl_web:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:wgpu_web:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:web"))
        implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
        implementation(project(":libfdx:extensions:graphics:wgpu:platform:web"))
    }
}
```

```kotlin
// :samples:basic:platform:android
dependencies {
    implementation(project(":samples:basic:core"))

    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_android:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:wgpu_android_jni:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:vulkan_android_jni:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:android"))
        implementation(project(":libfdx:extensions:graphics:wgpu:platform:android_jni"))
        implementation(project(":libfdx:extensions:graphics:vulkan:platform:android_jni"))
    }
}
```

## 11. Graphics Direction

The checked-in graphics API is provider-neutral, with GL/WebGL, WGPU, and
Vulkan provider families implementing the same `framework/graphics`
contracts. Provider-specific public classes stay in their own modules.

Current low-level `framework/graphics` concepts:

- `Graphics`, `GraphicsContext`, and `GraphicsDevice`
- presentation attachments and readiness
- graphics frames and framebuffer readback
- buffers
- textures and texture views
- shader modules
- render pipelines
- frame-owned command encoders and render passes

Adapters, public queues, samplers, bind groups, compute pipelines, command
buffers, and surface objects are not current common API types. They must not be
presented as implemented until source declarations and provider parity exist.

2D and 3D are separate layers above this low-level API:

```text
framework/graphics
  -> extensions/graphics/wgpu/core
  -> extensions/graphics/gl/core
  -> extensions/graphics/vulkan/core
  -> framework/g2d
  -> framework/g3d
```

The low-level GPU API should not contain SpriteBatch-style 2D concepts or model/material/lighting-style 3D concepts. Those belong in `framework/g2d` and `framework/g3d`.

## 12. Backends

Backends wire the typed runtime root, display, files, input, storage/network
availability, and graphics attachment together. Audio is not a current backend
service. A backend may expose providers for APIs owned by its implementation
library, such as `DesktopOpenGLProvider`, but graphics providers are still
selected explicitly through backend configuration.

The backend module name must identify both the platform and the runtime technology only when there can be more than one backend choice for that platform. `desktop` and `desktop_c` are different desktop backend implementations. The default web backend is just `web`; TeaVM is an internal implementation detail until another web backend exists.

Game and sample common code should remain backend-neutral. Launcher/platform
modules depend on the concrete backend project or artifact, such as
`:libfdx:backends:desktop` / `backend_desktop`.

## 13. Input Direction

Input common API belongs in `framework/input`. Keyboard, mouse, touch, text input, and gamepad contracts should be available through the same input service so user code has one place to read input state and subscribe to input events.

The current checked-in backends create `DefaultInput`, `DefaultCursor`, and
`DefaultGamepads` instances as part of backend setup. There are no standalone
platform gamepad provider projects or artifacts. If input providers become
separately selectable later, that change requires new extension modules,
backend selection wiring, tests, and synchronized API documentation.

Normal code should use:

```java
Input input = fdx.input();
Gamepads gamepads = input.gamepads();

for (Gamepad gamepad : gamepads.connected()) {
    float x = gamepad.axis(GamepadAxis.LEFT_X);
    boolean jump = gamepad.pressed(GamepadButton.SOUTH);
}
```

`Input.gamepads()` is non-null in the current implementations and may return an
empty connected list. `Gamepads.find(int)` returns `null` when no controller
with that index is connected. No current public `DesktopGamepadProvider` type
exists for an `as()` example.

## 14. Audio Direction (Future, Not Implemented)

The current repository has no audio Gradle module, Maven artifact, Java package, backend service, or `Fdx.audio()` accessor. The names in this section are design direction only and must not be used in current dependency snippets or API examples.

If audio is implemented later, it should follow the same API/provider model as graphics.

Common `framework/audio` concepts:

- audio system/device service
- sound buffer or sound handle
- music/streaming audio handle
- playback instance
- volume/pan/pitch controls
- pause/resume/stop
- provider capabilities
- provider identity and provider-specific `as()` access for advanced users

Provider modules:

- `extensions/audio/miniaudio/core` for provider-specific miniaudio Java types if they are needed.
- `extensions/audio/miniaudio/desktop_jni`, `extensions/audio/miniaudio/desktop_ffm`, and `extensions/audio/miniaudio/desktop_c` for desktop miniaudio runtimes.
- `extensions/audio/miniaudio/android_jni`, `extensions/audio/miniaudio/android_native`, and `extensions/audio/miniaudio/ios_native` for mobile miniaudio runtimes.
- `extensions/audio/miniaudio/web` for a web miniaudio runtime if it is useful.
- `extensions/audio/webaudio/web` for browser WebAudio.
- `extensions/audio/openal/core` and platform OpenAL runtimes only if there is a real reason to support OpenAL later.
- Custom external providers should be possible without changing game code that uses `framework/audio`.

Before any of these concepts become current, the implementation must add the module graph, public source declarations, backend ownership, provider modules, tests, and synchronized `COMMON_API.md` contracts.

## 15. Tests And Benchmarking

The `tests/` folder uses one core test project for reusable tests, test helpers, and the test registry. Test runner modules should represent backend/platform launchers, not every provider stack. JNI, FFM, C, and other provider choices should be selected by Gradle tasks, variants, or properties inside the runner project.

Layout:

```text
tests/
  core/
  platform/
    android/
    desktop/
    desktop_c/
    plugin/
    psp/
    web/
```

Responsibilities:

- `tests/core` contains reusable tests, test helpers, provider contracts, the test registry, the UI selector, and the auto-cycle harness.
- `tests/platform/desktop` runs `tests/core` through `backends/desktop`; dedicated tasks select GL, WGPU, or Vulkan runtime classpaths.
- `tests/platform/desktop_c` runs `tests/core` through `backends/desktop_c`; dedicated tasks select the checked-in OpenGL or Vulkan C-backed path.
- `tests/platform/web` runs `tests/core` with `backends/web` plus the checked-in WebGL or WebGPU provider.
- `tests/platform/android` runs `tests/core` on Android; repository run tasks install and launch GLES, WGPU JNI, or Vulkan activities.
- `tests/platform/plugin` is the plugin-use coverage project for generated desktop-C, web, iOS-C, and PSP targets. It is not a separate runtime test registry.
- `tests/platform/psp` runs the shared `tests/core` selector through `backends/psp`. The PSP module keeps only the platform launcher and thin task aliases; the actual selectable tests live in `tests/core` alongside the desktop tests. PSP TeaVM C project generation, native build, and PPSSPP capture entrypoints are exposed by `:tests:platform:psp` as `test_psp_*` tasks and by `:tests:platform:plugin` as the plugin coverage `libfdx_psp_test_*` tasks.

There are no checked-in headless, Android-native, or iOS runtime test projects.
Future runners must not be listed as current until they exist in `gradlew
projects` and have executable task wiring.

The core test project should be organized by solution/package, not by platform:

```text
tests/core/src/main/java/io/github/libfdx/tests/
  graphics/
  ui/
  AutoTestApplication.java
  StorageRuntimeTest.java
  TestChooserApplication.java
  TestSelector.java
```

Platform test projects should select the same test registry:

```text
:tests:platform:android
:tests:platform:desktop
:tests:platform:desktop_c
:tests:platform:plugin
:tests:platform:psp
:tests:platform:web
```

Test rules:

- Every public solution should get at least one core test when it has observable behavior.
- A test should make its required capabilities explicit through its selector/launcher wiring or focused preconditions.
- Platform test projects should skip unsupported tests explicitly instead of silently failing or maintaining separate test lists.
- Desktop, desktop-C, web, Android, and PSP runners should use the shared selector wherever the platform supports the selected test.
- Interactive platform runners should expose the shared selector by default where practical, while direct selection through `libfdx.test.name` remains available for scripted validation.
- Auto-cycle runners should wait for stable frames before advancing so first-load shader, asset, or texture work does not hide startup failures.
- If a backend has a named implementation folder, such as `backends/desktop`, tests should include that backend name only when it changes the launcher/runtime project shape, such as `tests/platform/desktop`.
- Provider stacks should be selected inside the test runner with dedicated Gradle tasks or platform build variants, not by adding stack-specific test folders.
- If a library provider has a platform implementation, there should be at least one matching test task or CI matrix entry that wires that platform implementation when feasible.
- Provider-specific tests may exist, but they must use current provider/test names and state the provider boundary explicitly.
- Portable tests should use common API types and should not call `as()` unless the test is specifically about provider access.

Example provider stack selection:

```bash
./gradlew :tests:platform:desktop:test_desktop_gl_run
./gradlew :tests:platform:desktop:test_desktop_wgpu_run
./gradlew :tests:platform:desktop:test_desktop_vulkan_run
./gradlew -Dlibfdx.test.name=ui -Dlibfdx.test.frames=30 -Dlibfdx.test.validate=true -Dlibfdx.test.driveInput=true :tests:platform:desktop:test_desktop_gl_run
```

Desktop JVM sample runtime tasks start with the sample name and end with `_run`, such as `basic_desktop_gl_run`, `basic_desktop_wgpu_run`, and `basic_desktop_vulkan_run`, when the runtime module owns direct `JavaExec` declarations for each graphics API. Plugin-first samples such as ECS Platformer apply `io.github.libfdx` directly in the platform module and use the generated `libfdx_desktop_jvm_*` plugin tasks without extra sample aliases. Packaged desktop JVM `_build` tasks belong to modules that configure the plugin `desktopJvm { ... }` target with `libfdx_desktop_jvm_*` tasks under the `libfdx` task group. Desktop JVM test runtime app tasks start with `test_desktop_` and keep direct `_run` tasks such as `test_desktop_gl_run`, `test_desktop_wgpu_run`, and `test_desktop_vulkan_run`; plugin-use modules own the matching packaged `_build` tasks. Finite desktop test validation uses the same direct `_run` tasks with `-Dlibfdx.test.*` properties instead of extra runtime Gradle tasks. Binding implementation details such as FFM or JNI are dependency wiring inside the platform module or a repository-only sample convention and should not appear in desktop JVM sample/test task names. Each desktop interactive provider task should expose the same selector API options: `gl`, `wgpu`, and `vulkan`.

Desktop C runtime sample and test modules keep app-facing tasks such as `basic_desktop_c_opengl_generate_debug`, `test_desktop_c_opengl_generate_debug`, `test_desktop_c_opengl_build_debug`, `test_desktop_c_vulkan_generate_debug`, and `test_desktop_c_vulkan_build_debug` so contributors can run the C-backed desktop samples and tests from the platform modules. Traditional runtime modules may keep Gradle minimal and delegate generated targets to a dedicated plugin-use module. Plugin-first samples, such as ECS Platformer, apply `io.github.libfdx` directly in the platform module and use the same module's `libfdx_desktop_c_*` tasks. Dedicated plugin-use modules still expose plugin coverage tasks such as `libfdx_desktop_c_opengl_generate_debug`, `libfdx_desktop_c_opengl_build_debug`, `libfdx_desktop_c_vulkan_generate_debug`, and `libfdx_desktop_c_vulkan_build_debug`.

iOS C runtime sample modules may expose generation aliases such as `basic_ios_c_gles_generate` and `basic_ios_c_metal_generate`. Traditional runtime modules may delegate the actual `iosC { ... }` targets to a plugin-use module; plugin-first samples apply `io.github.libfdx` directly in the iOS C platform module and use the same module's `libfdx_ios_c_*` tasks. The `gles` target emits a GLKit/OpenGLES project. The `metal` target emits a native Metal/MetalKit project and links only system Apple frameworks plus the generated libFDX native bridge. The generated project is opened and built on macOS with Xcode for simulator/device validation; repository generation tasks do not invoke Xcode.

Web runtime sample and test modules expose app-facing aliases for the supported matrix: WebGL JavaScript, WebGPU JavaScript, and WebGL Wasm, such as `basic_webgl_js_run`, `basic_webgpu_js_run`, and `test_webgl_wasm_run`. TeaVM WasmGC does not currently support the substituted JS-native jWebGPU binding path, so WebGPU-Wasm aliases are intentionally absent; use the matching WebGPU JavaScript task or WebGL Wasm task. Traditional runtime modules may delegate generated web build/server implementation to a plugin-use module; plugin-first samples apply `io.github.libfdx` directly in the web platform module and use the same module's `libfdx_web_*` tasks.

PSP platform test tasks live in `:tests:platform:psp` as the shared selector entrypoints `test_psp_generate`, `test_psp_build`, and `test_psp_ppsspp_capture`. The PSP module does not apply the libFDX Gradle plugin; those tasks are thin runtime entrypoints for the single plugin-use module's `test` PSP target. Build tasks execute the generated PSP build script and require a PSPDEV/psp-cmake toolchain on `PATH` or through `PSPDEV`. Because the Gradle plugin has one TeaVM C task, the plugin-use module selects the PSP plugin target only for requested PSP target tasks and otherwise defaults to desktop-c plugin tasks. On Windows, `PSPDEV` may be set to a Windows path; the generated `build.bat` converts it to a WSL path before invoking `build.sh`. PPSSPP capture tasks build the EBOOT, launch PPSSPP in windowed mode, wait `libfdx.psp.ppssppCaptureDelaySeconds`, and write a capture under `build/reports/ppsspp`. The emulator executable is resolved from `libfdx.psp.ppssppExecutable`, `PPSSPP_EXECUTABLE`, `PPSSPP_HOME`, `PATH`, standard Windows install locations, or the generated local `build/tools/ppsspp` directory. If no executable is found and `libfdx.psp.ppssppAutoDownload` is true, the task downloads the portable ZIP from `libfdx.psp.ppssppDownloadUrl` and extracts it into `build/tools/ppsspp`.

`TestSelector` is the current registry source. Its checked-in descriptors map
stable names such as `texture`, `sprite`, `model`, `model-skinning`, `ui`,
`render-target-chain`, and `recorded-resource-rewrite` to concrete
`ApplicationListener` factories and default dimensions. Documentation and
launchers must use names present in that source array rather than inventing a
parallel registry API.

Each module should still keep its local unit tests in:

```text
module/src/test/java
```

Benchmark code lives under `benchmark/` because performance checks need to stay close to local framework changes. It still measures performance instead of correctness, so correctness tests should not depend on benchmark code. Benchmark modules may depend on public framework modules and platform launchers, but shared framework modules must not depend on benchmarks.

## 16. Java Package Map

The Gradle module path decides where source files live. The Java package root decides how classes are named and imported. Use this map when adding new Java source files.

Package rules:

- Public packages start with `io.github.libfdx`.
- Do not add another `fdx` or `libfdx` segment after `io.github.libfdx`.
- Java package names use dots, not underscores.
- Binding and packaging mechanisms such as `jni`, `ffm`, and `c` are Gradle/artifact variant names only. Do not add `.jni`, `.ffm`, or `.c` package segments.
- A platform variant such as `desktop_jni`, `desktop_ffm`, or `desktop_c` should use the same platform package, such as `desktop`. A variant such as `android_jni` or `android_native` should use `android`.
- Java package names should be user-facing. Repository organization folders such as `foundation`, `runtime`, and `extensions` do not need to appear in public package names when a shorter package is clearer.
- Provider-specific packages should still name the provider, such as the current `graphics.wgpu`, `graphics.vulkan`, or `net.webrtc` packages.
- Platform runtime implementation code should live under provider/backend platform packages, such as `desktop`, `android`, `ios`, or `web`, only when the platform variant module owns Java code. Dependency-only platform variant modules have no Java package. Shared Java provider code should remain in the provider `core` package, such as `io.github.libfdx.graphics.wgpu`.
- Internal helpers may use an `.internal` subpackage under the module package root. Public APIs should not expose `.internal` types.

Core and runtime packages:

| Gradle module | Java package root | What belongs there |
| --- | --- | --- |
| `:libfdx:framework:fdx:core` | `io.github.libfdx.core`, `io.github.libfdx.runtime.core`, `io.github.libfdx.runtime.core.shader` | Minimal framework core types plus shared runtime services and native-service contracts. |
| `:libfdx:framework:math` | `io.github.libfdx.math` | Vectors, matrices, quaternions, bounds, and backend-independent color math. |
| `:libfdx:framework:json` | `io.github.libfdx.json` | Strict JSON value tree, reader, writer, and callback-based class codecs. |
| `:libfdx:framework:collections` | `io.github.libfdx.collections` | Specialized collections and allocation-conscious data structures. |
| `:libfdx:framework:application` | `io.github.libfdx.application` | Application lifecycle, config, loop contracts, platform capabilities, and application startup contracts. |
| `:libfdx:framework:files` | `io.github.libfdx.files` | File handles, storage locations, path normalization, and file read/write abstractions. |
| `:libfdx:framework:input` | `io.github.libfdx.input` | Keyboard, mouse, touch, gestures, text input, cursor state, gamepad contracts, and input routing primitives. |
| `:libfdx:framework:display` | `io.github.libfdx.display` | Display state, logical/framebuffer size, position, content scale, monitor/work-area bounds, title, visibility/focus/minimize state, opacity, and close requests. |
| `:libfdx:framework:net` | `io.github.libfdx.net` plus focused subpackages | Root `Network` and `NetworkCapabilities`; HTTP in `net.http`; WebSocket in `net.websocket`; reusable packet storage in `net.buffer`; packet views/queues in `net.packet`; message codecs in `net.codec`; transform hooks in `net.transform`; endpoint configs in `net.config`; multiplayer endpoints/providers in `net.transport`; fixed-rate helpers in `net.processing`; backend/provider setup types in `net.spi`. |

Asset, graphics, and UI packages:

| Gradle module | Java package root | What belongs there |
| --- | --- | --- |
| `:libfdx:framework:assets:manager` | `io.github.libfdx.assets` | Asset manager, asset references, handles, dependency tracking, async loading contracts, and lifetime rules. |
| `:libfdx:framework:assets:loaders` | `io.github.libfdx.assets.loaders` | Default provider-neutral image/JSON asset loaders and loader support types. GPU resource objects still belong to graphics modules. |
| `:libfdx:framework:graphics` | `io.github.libfdx.graphics` | Current common graphics API: managers, contexts, devices, attachments, frames/framebuffers, buffers, textures/views, meshes, shader descriptors/reflection, render pipelines, command encoders, and render passes. |
| `:libfdx:framework:camera` | `io.github.libfdx.graphics.camera`, `io.github.libfdx.graphics.camera.controller` | Shared camera state in the root package plus anchor contracts, pointer filters, input bindings, cinematic paths, and reusable 2D/3D camera controllers in the controller subpackage. |
| `:libfdx:framework:g2d` | `io.github.libfdx.graphics.g2d` | Complete 2D toolkit: sprites, sprite batches, texture regions, atlases, bitmap fonts, vector-rasterized font atlases, text layout, tile maps, particles, and 2D render helpers. Shared camera state lives in `io.github.libfdx.graphics.camera.Camera`. |
| `:libfdx:framework:g3d` | `io.github.libfdx.graphics.g3d` | Complete 3D toolkit: `Batch3D`, `ModelBatch`, `ModelBuilder`, meshes, models, materials, PBR/default shaders, skinned PBR GPU path, custom shader hooks, lights, environments, sky environment lighting, directional and cascaded shadow maps, animation, render queues, render paths, frame target helpers, glTF loading, and 3D render helpers. Shared camera state lives in `io.github.libfdx.graphics.camera.Camera`; input-backed controllers live in `framework/camera`. |
| `:libfdx:framework:ui-kit` | `io.github.libfdx.ui` | Built-in UI toolkit: Compose-inspired declarative authoring over a retained runtime, widgets, layout, animation, themes, ninepatch styling, layers, focus, navigation, and input routing. |
| `:libfdx:extensions:ecs` | `io.github.libfdx.ecs`, `io.github.libfdx.ecs.*` | Optional ECS: root `World` plus subpackages for integer entity handles, component mappers, entity matchers and lists, queued events, world commands, managers, and systems. |
| `:libfdx:extensions:scenario_validator:core` | `io.github.libfdx.validation.scenario` | Scenario validation engine: reusable catalogs, scenarios, runtime actions, assertions, waits, step pacing, events, probes, reports, and visual validation hooks. |
| `:libfdx:extensions:scenario_validator:ui-kit` | `io.github.libfdx.validation.scenario.ui.kit` | UI Kit adapter for scenario validation: UI targets, UI actions, UI assertions, UI waits, UI events, UI captures, and validation ID integration. |

Provider and extension packages:

| Gradle module pattern | Java package root pattern | What belongs there |
| --- | --- | --- |
| `:libfdx:extensions:net:<provider>:core` | `io.github.libfdx.net.<provider>` plus focused subpackages | Provider-specific network transport public types and shared provider glue. WebRTC keeps only `WebRtcProvider` at `net.webrtc`; endpoint configs live in `net.webrtc.config`, protocol contracts in `net.webrtc.signaling`, bridge interfaces in `net.webrtc.platform`, and transport implementations in `net.webrtc.transport`. |
| `:libfdx:extensions:net:<provider>:<service>` provider service modules | `io.github.libfdx.net.<provider>.<service>` | Optional provider-owned service processes or tools. For WebRTC, `webrtc:signaling_server` maps to `net.webrtc.signaling.server` and owns only the standalone Java-WebSocket signaling server app/library, including its auth/session, join, message policy, and tick-limited processing extension hooks. |
| `:libfdx:extensions:net:<provider>:platform:<platform_variant>` | `io.github.libfdx.net.<provider>.<platform>` when Java code is required | Platform network transport binding/runtime packaging. For example, `webrtc:platform:desktop_jni` maps to `net.webrtc.desktop`, `webrtc:platform:web` maps to `net.webrtc.web`, and `webrtc:platform:android_jni` maps to `net.webrtc.android`. |
| `:libfdx:extensions:graphics:<provider>:core` | `io.github.libfdx.graphics.<provider>` | Provider-specific setup/configuration types and shared Java provider glue. Current public examples include `WGPUProvider`, `WGPUConfiguration`, `WGPUContext`, `GLConfiguration`, and `VulkanConfiguration`; resource implementations may remain internal. |
| `:libfdx:extensions:graphics:<provider>:platform:<platform_variant>` | `io.github.libfdx.graphics.<provider>.<platform>` when Java code is required | Platform graphics binding/runtime packaging. These modules may be dependency-only and should contain Java code only for variant-specific glue that cannot live in `core`. |
Backend, tool, test, and sample packages:

| Gradle module pattern | Java package root pattern | What belongs there |
| --- | --- | --- |
| `:libfdx:backends:<platform>` | `io.github.libfdx.backend.<platform>` | Default platform backend launcher/runtime classes. For example, `backends:web` maps to `backend.web`, and `backends:android` maps to `backend.android`. |
| `:libfdx:backends:c_shared` | `io.github.libfdx.backend.cshared` | Shared TeaVM build mechanics and native resource payloads used by TeaVM-backed platform backends. |
| `:libfdx:backends:<platform>_<implementation>` | `io.github.libfdx.backend.<platform>[.<backend_name>]` | Named backend implementation classes. Include the implementation in the package only when it is a real backend technology, such as `desktop` mapping to `backend.desktop`. Artifact-only variants such as `c` should keep the platform package, such as `backend.headless` or `backend.desktop`, and use distinct class names. |
| `:libfdx:backends:<platform>_<implementation>_resources` | none | Resource-only native payloads shared by provider/runtime modules for that backend family. These modules should not contain Java source. |
| `:libfdx:tools:<tool>` and `:libfdx:tools:<tool>:<part>` | `io.github.libfdx.tools.<tool_package>` | Tool implementation code. Use normal Java package words, such as `tools.project.generator` or `tools.texture.packer`. Tool platform adapters may add a platform package segment, such as `tools.project.generator.desktop`. |
| `:tests:core` | `io.github.libfdx.tests` | Shared test registry, test helpers, contracts, and reusable tests. |
| `:tests:platform:<platform_or_backend>` | `io.github.libfdx.tests.<platform_or_backend>` | Test runner and platform/backend test wiring. Follow the same package rule as backends: real backend names may appear in packages, but artifact-only variants such as `c`, `jni`, and `ffm` should not. |
| `:samples:<name>:core` | `io.github.libfdx.samples.<name>` | Shared sample application code. |
| `:samples:<name>:platform:<platform>` | `io.github.libfdx.samples.<name>.<platform>` | Platform sample launcher/wiring code. |

Class placement examples:

| Class | Module | Package |
| --- | --- | --- |
| `ProviderId` | `:libfdx:framework:fdx:core` | `io.github.libfdx.core` |
| `ApplicationConfig` | `:libfdx:framework:application` | `io.github.libfdx.application` |
| `FileHandle` | `:libfdx:framework:files` | `io.github.libfdx.files` |
| `Texture` | `:libfdx:framework:graphics` | `io.github.libfdx.graphics` |
| `TextureFilter` | `:libfdx:framework:graphics` | `io.github.libfdx.graphics` |
| `FrameBuffer` | `:libfdx:framework:graphics` | `io.github.libfdx.graphics` |
| `Camera`, `CameraProjection` | `:libfdx:framework:camera` | `io.github.libfdx.graphics.camera` |
| `ImmediateModeRenderer` | `:libfdx:framework:graphics` | `io.github.libfdx.graphics` |
| `CameraController2D`, focused camera controllers, cinematic camera paths, camera anchors, and camera input bindings | `:libfdx:framework:camera` | `io.github.libfdx.graphics.camera.controller` |
| `Batch2D` | `:libfdx:framework:g2d` | `io.github.libfdx.graphics.g2d` |
| `SpriteBatch` | `:libfdx:framework:g2d` | `io.github.libfdx.graphics.g2d` |
| `BitmapFont` | `:libfdx:framework:g2d` | `io.github.libfdx.graphics.g2d` |
| `TileMap` | `:libfdx:framework:g2d` | `io.github.libfdx.graphics.g2d` |
| `TileMapRenderer` | `:libfdx:framework:g2d` | `io.github.libfdx.graphics.g2d` |
| `Batch3D` | `:libfdx:framework:g3d` | `io.github.libfdx.graphics.g3d` |
| `ModelBatch` | `:libfdx:framework:g3d` | `io.github.libfdx.graphics.g3d` |
| `ModelBuilder` | `:libfdx:framework:g3d` | `io.github.libfdx.graphics.g3d` |
| `ModelInstance` | `:libfdx:framework:g3d` | `io.github.libfdx.graphics.g3d` |
| `CpuSkinnedModelAnimator` | `:libfdx:framework:g3d` | `io.github.libfdx.graphics.g3d` |
| `BillboardRenderer3D` | `:libfdx:framework:g3d` | `io.github.libfdx.graphics.g3d` |
| `ParticleEmitter3D` | `:libfdx:framework:g3d` | `io.github.libfdx.graphics.g3d` |
| `FogOfWarRenderer3D` | `:libfdx:framework:g3d` | `io.github.libfdx.graphics.g3d` |
| `CascadedShadowMap3D` | `:libfdx:framework:g3d` | `io.github.libfdx.graphics.g3d` |
| `G3DAssetLoaders` | `:libfdx:framework:g3d` | `io.github.libfdx.graphics.g3d` |
| `ModelBatchTest` | `:tests:core` | `io.github.libfdx.tests.graphics` |
| `GraphicsAttachmentProvider` | `:libfdx:framework:graphics` | `io.github.libfdx.graphics` |
| `NativeWindow` | `:libfdx:framework:graphics` | `io.github.libfdx.graphics` |
| `WGPUContext` | `:libfdx:extensions:graphics:wgpu:core` | `io.github.libfdx.graphics.wgpu` |
| `WGPUProvider` | `:libfdx:extensions:graphics:wgpu:core` | `io.github.libfdx.graphics.wgpu` |
| `GLConfiguration` | `:libfdx:extensions:graphics:gl:core` | `io.github.libfdx.graphics.gl` |
| `GLGraphicsAttachment` | `:libfdx:extensions:graphics:gl:core` | `io.github.libfdx.graphics.gl` |
| `GLApi` / `GLSurface` | `:libfdx:extensions:graphics:gl:core` | `io.github.libfdx.graphics.gl` |
| `DesktopApplicationBackend` | `:libfdx:backends:desktop` | `io.github.libfdx.backend.desktop` |
| `DesktopApplicationConfig` | `:libfdx:backends:desktop` | `io.github.libfdx.backend.desktop` |
| `DesktopOpenGLProvider` | `:libfdx:backends:desktop` | `io.github.libfdx.backend.desktop` |
| `DesktopVulkanProvider` | `:libfdx:backends:desktop` | `io.github.libfdx.backend.desktop` |
| `DesktopCApplicationBackend` | `:libfdx:backends:desktop_c` | `io.github.libfdx.backend.desktopc` |
| `DesktopCApplicationConfig` | `:libfdx:backends:desktop_c` | `io.github.libfdx.backend.desktopc` |
| `DesktopCOpenGLProvider` | `:libfdx:backends:desktop_c` | `io.github.libfdx.backend.desktopc` |
| `DesktopCVulkanProvider` | `:libfdx:backends:desktop_c` | `io.github.libfdx.backend.desktopc` |
| `PspBuilder` | `:libfdx:backends:psp` | `io.github.libfdx.backend.psp` |
| `PspProject` / `PspProjectWriter` | `:libfdx:backends:psp` | `io.github.libfdx.backend.psp` |
| `PspApplicationBackend` / `PspApplicationConfig` | `:libfdx:backends:psp` | `io.github.libfdx.backend.psp` |
| `PspGraphicsContext` | `:libfdx:backends:psp` | `io.github.libfdx.backend.psp` |
| `IosCProject` / `IosCProjectWriter` | `:libfdx:backends:ios_c` | `io.github.libfdx.backend.iosc` |
| `IosCApplicationBackend` / `IosCApplicationConfig` | `:libfdx:backends:ios_c` | `io.github.libfdx.backend.iosc` |
| `IosCOpenGLESProvider` / `IosCMetalProvider` / `IosCGraphicsApi` | `:libfdx:backends:ios_c` | `io.github.libfdx.backend.iosc` |
| `AndroidApplicationConfig` | `:libfdx:backends:android` | `io.github.libfdx.backend.android` |
| `AndroidTextEditorStyle` | `:libfdx:backends:android` | `io.github.libfdx.backend.android` |

## 17. Naming Notes

Internal module folders and Gradle paths should avoid the `fdx-` prefix because the modules already live under `:libfdx`.

Example:

```text
:libfdx:framework:graphics
```

Do not add another project prefix inside path segments, because `:libfdx` already scopes every internal module.

Java packages should follow the package map above and include the project name once:

```java
package io.github.libfdx.graphics;
package io.github.libfdx.graphics.camera;
package io.github.libfdx.graphics.camera.controller;
```

For 2D and 3D graphics, use `g2d` and `g3d` in package names. Public types may live in grouped subpackages when the group is a stable API area:

```java
package io.github.libfdx.graphics.g2d;
package io.github.libfdx.graphics.g3d;
```

Published artifacts use only one Maven group ID:

```text
io.github.libfdx
```

Use the shortest unique artifact ID that still makes the module clear:

```text
io.github.libfdx:fdx
io.github.libfdx:fdx_desktop
io.github.libfdx:fdx_web
io.github.libfdx:fdx_android
io.github.libfdx:graphics
io.github.libfdx:display
io.github.libfdx:g2d
io.github.libfdx:g3d
io.github.libfdx:gl_core
io.github.libfdx:gl_desktop
io.github.libfdx:wgpu_core
io.github.libfdx:vulkan_core
io.github.libfdx:vulkan_desktop
io.github.libfdx:ui_kit
io.github.libfdx:ecs
io.github.libfdx:scenario_validator
io.github.libfdx:scenario_validator_ui_kit
io.github.libfdx:backend_desktop
io.github.libfdx:backend_android
io.github.libfdx:backend_web
```

Prefer short names when there is no collision:

```text
io.github.libfdx:math
io.github.libfdx:display
io.github.libfdx:asset_manager
```

Use prefixes only when the short name would be ambiguous or collide with another module:

```text
io.github.libfdx:graphics
io.github.libfdx:gl_desktop
io.github.libfdx:wgpu_desktop_jni
io.github.libfdx:vulkan_desktop
io.github.libfdx:backend_desktop
```

Do not publish a default platform runtime when there is no real default. Use explicit stack artifacts when a provider has several valid runtime implementations.
