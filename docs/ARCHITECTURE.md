# libFDX Architecture

This document is the source of truth for repository structure: module ownership,
dependency direction, provider/backend boundaries, Gradle topology, Maven
artifacts, and Java package roots.

It intentionally does not copy Java declarations or task catalogs. Use
[Common API](COMMON_API.md) for portable behavior, Java source and generated
Javadocs for exact signatures, and the task/domain guides linked from the
[README](../README.md#documentation) for focused workflows.

## Topics

- [1. System Model](#1-system-model)
- [2. Repository Topology](#2-repository-topology)
- [3. Dependency Rules](#3-dependency-rules)
- [4. Provider Model](#4-provider-model)
- [5. Module And Artifact Catalog](#5-module-and-artifact-catalog)
- [6. Java Package Roots](#6-java-package-roots)
- [7. Build And Publication Topology](#7-build-and-publication-topology)
- [8. Current Scope Boundaries](#8-current-scope-boundaries)
- [9. Architecture Invariants](#9-architecture-invariants)

## 1. System Model

libFDX separates portable contracts from selectable implementations:

```text
shared game code
  -> high-level framework features (g2d, g3d, ui-kit, assets)
     -> provider-neutral runtime APIs (application, display, input, net, graphics)
        -> small foundation/runtime contracts (fdx, math, json, collections)

platform launcher
  -> backend (desktop, web, Android, PSP, desktop C, iOS C)
  -> selected provider modules (GL, Vulkan, Direct3D 12, WGPU, WebRTC)
```

The arrows mean "depends on." Portable modules never depend back on a concrete
backend or provider. A launcher is the composition boundary where a backend and
available provider stack are selected.

The typed `Fdx` root is passed to `ApplicationListener.create(Fdx)`. It exposes
backend-owned runtime roots such as application, displays, graphics, input,
files, storage, networking, and logging. Application-owned features--asset
managers, batches, UI roots, ECS worlds, scenes, and game systems--are created
explicitly and are not added to `Fdx`.

## 2. Repository Topology

### 2.1. Folder Ownership

| Folder | Ownership |
| --- | --- |
| `libfdx/framework/` | Portable first-party APIs and high-level framework features. |
| `libfdx/extensions/` | Optional providers and opt-in feature families. |
| `libfdx/backends/` | Platform/runtime implementations and launcher support. |
| `libfdx/tools/` | Build-time tools, the isolated Gradle plugin, and project generation. |
| `tests/` | Shared test scenarios and platform runners. |
| `samples/` | Example applications with shared core and platform launchers. |
| `benchmark/` | Shared benchmark cases, platform runners, and generated reports. |
| `docs/` | Canonical architecture/API contracts and focused task/domain guides. |

New top-level folders require a clear ownership rule for several modules.
Repository-only tests, samples, and benchmarks stay outside `libfdx/`.

### 2.2. Module Shapes

Framework features use the shortest stable path that expresses ownership:

```text
libfdx/framework/<feature>
libfdx/framework/assets/<part>
libfdx/framework/fdx/{core,fdx-build,platform/<platform>}
```

Provider families keep shared Java code in `core` and platform/binding variants
under `platform`:

```text
libfdx/extensions/graphics/<provider>/core
libfdx/extensions/graphics/<provider>/platform/<platform_variant>
libfdx/extensions/net/<provider>/core
libfdx/extensions/net/<provider>/platform/<platform_variant>
```

Use `desktop_jni`, `desktop_ffm`, or `android_jni` only when the implementation
stack is a real choice. A web variant stops at `web` when one artifact supports
the active JavaScript/Wasm arrangements. Variant-only modules may contain no
Java source when their responsibility is native/runtime packaging.

Backends use one flat concrete segment (`desktop`, `desktop_c`, `ios_c`,
`android`, `web`, `psp`). Shared TeaVM C build/resource support belongs in the
flat `c_shared` sibling.

Tests and samples use shared logic plus platform launchers:

```text
tests/core
tests/platform/<platform_or_backend>

samples/<name>/core
samples/<name>/platform/<platform>
```

Provider/binding choices remain Gradle dependencies or target configuration;
they do not create stack-specific sample folders.

## 3. Dependency Rules

### 3.1. Foundation And Runtime

- `framework/fdx/core` is the smallest shared runtime layer. It owns base
  errors, logging, provider identity, async primitives, resource lifetime, and
  portable runtime capabilities used by higher layers.
- `math`, `json`, and `collections` remain provider-neutral and independent of
  backends, graphics providers, UI, and application-owned feature objects.
- Runtime-facing APIs (`application`, `display`, `files`, `input`, `net`, and
  `storage`) depend only on lower portable contracts.
- `framework/application` owns `io.github.libfdx.Fdx`, application lifecycle,
  and launcher/backend contracts. Concrete startup configuration belongs to the
  backend/provider that understands it.

### 3.2. Assets, Graphics, And High-Level Features

- `assets/manager` owns loading, dependency, caching, and lifetime mechanics.
  `assets/loaders` owns provider-neutral source-data loaders.
- Source data such as `ImageData` is not a GPU resource. Texture/model/font
  loaders that create provider-backed resources belong in a module that already
  depends on the relevant graphics feature.
- `graphics` owns provider-neutral GPU resources, contexts, descriptors, frames,
  and command recording. It does not depend on `g2d`, `g3d`, UI, or a provider.
- `camera` owns shared camera state and input-backed camera controllers.
- `g2d` and `g3d` are complete user-facing toolkits built on `graphics`; users
  should not need many tiny rendering artifacts for standard 2D or 3D work.
- `ui-kit` is optional and builds on display/input/assets/graphics/g2d. It is
  one user-facing module and is never a backend requirement except for the
  web backend's private startup preload UI, which is disposed before game code
  starts.
- `ecs` and scenario validation are optional user-created features. They are not
  backend-owned services and are not exposed through `Fdx`.

### 3.3. Providers And Backends

- Graphics providers depend on `framework/graphics`, not on a concrete backend.
  Backends create the platform display/native-window environment and attach the
  selected `GraphicsAttachmentProvider`.
- Network transport providers depend on `framework/net`. Shared provider types
  live in the provider core; platform bindings stay in platform variants.
- Backends implement portable APIs and own application lifecycle, platform
  windows/views, input/file integration, and provider attachment. Portable APIs
  never depend on backends.
- Runtime families must remain coherent. A desktop C launcher uses desktop C
  backend/provider modules; it must not mix JVM desktop JNI/FFM runtime modules.
- Provider/binding platform modules that only supply native libraries normally
  use runtime dependencies. Modules whose public types are imported use normal
  implementation dependencies.

### 3.4. Runtime Versus Graphics

Runtime creates and runs the application, presentation areas, input, files,
storage, and networking. Graphics owns GPU work. `Display` and
`GraphicsContext` therefore have separate lifetimes. The current `Fdx` root
still provides a non-null graphics manager; within it, graphics can render
offscreen or to more than one display/context where supported.

Resources belong to the resource domain that created them, not merely to a
provider ID. Independently created contexts may be incompatible even when both
use the same provider. Frame-owned views, command encoders, and framebuffers
never outlive their frame.

## 4. Provider Model

Dependencies define which providers are available; launcher configuration
chooses the active provider. If one compatible provider is available a backend
may select it. If several are available, selection must be explicit and fail
clearly when the requested provider is absent or unsupported.

`ProviderId` is a logical stable identifier, not an artifact name. Portable
code uses common handles. Advanced provider-specific access is explicit through
`providerId()` followed by typed `as()` access and is valid only for the owning
provider/resource lifetime.

Graphics-provider selection is a startup decision. Changing providers requires
application restart unless a backend explicitly owns complete teardown and
recreation. Portable code does not assume live cross-provider resource sharing.

Shader authoring is WGSL-only. Providers that need GLSL/GLSL ES, SPIR-V, HLSL,
or MSL translate WGSL through the optional runtime compiler capability during
shader module creation. See [Shaders](SHADERS.md).

## 5. Module And Artifact Catalog

The Maven group is configured by `[release].fdxGroup` in `libfdx.toml` and is
currently `io.github.libfdx`. The tables list artifact IDs inside that group.
`internal` projects are not published as user dependencies.

Artifact IDs use lowercase words joined by `_`. Repository categories such as
`framework` and `extensions` do not repeat in artifact IDs. Provider artifacts
name the provider directly (`wgpu_core`, not `graphics_wgpu`).

### 5.1. Foundation, Runtime, And Features

| Gradle path | Artifact | Ownership |
| --- | --- | --- |
| `:libfdx:framework:fdx:core` | `fdx` | Base runtime contracts and portable runtime capabilities. |
| `:libfdx:framework:fdx:fdx-build` | internal | Native dependency resolution and desktop/Android/web runtime builds. |
| `:libfdx:framework:fdx:platform:shared` | `fdx_shared` | Shared native source resources for generated native projects. |
| `:libfdx:framework:fdx:platform:desktop` | `fdx_desktop` | Desktop runtime native resources. |
| `:libfdx:framework:fdx:platform:android` | `fdx_android` | Android runtime native AAR. |
| `:libfdx:framework:fdx:platform:web` | `fdx_web` | Web runtime JavaScript/Wasm resources. |
| `:libfdx:framework:math` | `math` | Portable math values and operations. |
| `:libfdx:framework:json` | `json` | Strict JSON tree, reader/writer, and explicit codecs. |
| `:libfdx:framework:collections` | `collections` | Allocation-conscious general and primitive collections. |
| `:libfdx:framework:application` | `application` | `Fdx`, lifecycle, application state, and startup contracts. |
| `:libfdx:framework:display` | `display` | Displays and presentation-area state. |
| `:libfdx:framework:files` | `files` | Portable file handles and locations. |
| `:libfdx:framework:input` | `input` | Keyboard, pointer, touch, text, cursor, and gamepads. |
| `:libfdx:framework:net` | `net` | HTTP, WebSocket, and provider-neutral multiplayer transport APIs. |
| `:libfdx:framework:storage` | `storage` | Persistent local and rebuildable cache stores. |
| `:libfdx:framework:assets:manager` | `asset_manager` | Asset loading, dependencies, caching, and lifetime. |
| `:libfdx:framework:assets:loaders` | `asset_loaders` | Provider-neutral image and JSON source-data loaders. |
| `:libfdx:framework:graphics` | `graphics` | Provider-neutral graphics contexts, resources, and commands. |
| `:libfdx:framework:camera` | `camera` | Shared camera state and controllers. |
| `:libfdx:framework:g2d` | `g2d` | Complete 2D rendering/font/particle/map toolkit. |
| `:libfdx:framework:g3d` | `g3d` | Complete 3D model/material/animation/lighting toolkit. |
| `:libfdx:framework:ui-kit` | `ui_kit` | Built-in declarative/retained game UI toolkit. |
| `:libfdx:extensions:ecs` | `ecs` | Optional pure-Java entity component system. |
| `:libfdx:extensions:scenario_validator:core` | `scenario_validator` | Optional runtime scenario validation engine. |
| `:libfdx:extensions:scenario_validator:ui-kit` | `scenario_validator_ui_kit` | Optional UI Kit validation adapter. |

### 5.2. Graphics Providers

| Gradle path | Artifact | Ownership |
| --- | --- | --- |
| `:libfdx:extensions:graphics:gl:core` | `gl_core` | Shared GL-family implementation/configuration. |
| `:libfdx:extensions:graphics:gl:platform:desktop` | `gl_desktop` | Desktop GL runtime dependencies. |
| `:libfdx:extensions:graphics:gl:platform:desktop_c` | `gl_desktop_c` | Desktop C GL native resources. |
| `:libfdx:extensions:graphics:gl:platform:web` | `gl_web` | Browser WebGL provider. |
| `:libfdx:extensions:graphics:vulkan:core` | `vulkan_core` | Shared Vulkan provider configuration/types. |
| `:libfdx:extensions:graphics:vulkan:platform:desktop` | `vulkan_desktop` | Desktop Vulkan runtime dependencies. |
| `:libfdx:extensions:graphics:vulkan:platform:desktop_c` | `vulkan_desktop_c` | Desktop C Vulkan bridge resources. |
| `:libfdx:extensions:graphics:vulkan:platform:android_jni` | `vulkan_android_jni` | Android Vulkan JNI/ABI integration. |
| `:libfdx:extensions:graphics:d3d12:core` | `d3d12_core` | Windows Direct3D 12 provider, Java 25 FFM bindings, public setup types, and portable graphics adapters. |
| `:libfdx:extensions:graphics:wgpu:core` | `wgpu_core` | Shared WebGPU/wgpu provider and public setup types. |
| `:libfdx:extensions:graphics:wgpu:platform:desktop_jni` | `wgpu_desktop_jni` | Desktop WGPU JNI runtime. |
| `:libfdx:extensions:graphics:wgpu:platform:desktop_ffm` | `wgpu_desktop_ffm` | Desktop WGPU FFM runtime (Java 25). |
| `:libfdx:extensions:graphics:wgpu:platform:android_jni` | `wgpu_android_jni` | Android WGPU JNI/ABI integration. |
| `:libfdx:extensions:graphics:wgpu:platform:web` | `wgpu_web` | Browser WebGPU integration for JavaScript targets. |

### 5.3. Networking Extensions

| Gradle path | Artifact | Ownership |
| --- | --- | --- |
| `:libfdx:extensions:net:webrtc:core` | `webrtc_core` | WebRTC configs, signaling contracts, and shared transport glue. |
| `:libfdx:extensions:net:webrtc:signaling_server` | `webrtc_signaling_server` | Standalone signaling service/library. |
| `:libfdx:extensions:net:webrtc:platform:desktop_jni` | `webrtc_desktop_jni` | Desktop WebRTC runtime/binding. |
| `:libfdx:extensions:net:webrtc:platform:web` | `webrtc_web` | Browser WebRTC binding. |
| `:libfdx:extensions:net:webrtc:platform:android_jni` | `webrtc_android_jni` | Android WebRTC runtime/binding. |

### 5.4. Backends

| Gradle path | Artifact | Ownership |
| --- | --- | --- |
| `:libfdx:backends:desktop` | `backend_desktop` | JVM desktop lifecycle/window/input/files and provider attachment. |
| `:libfdx:backends:c_shared` | `backend_c_shared` | Shared TeaVM C build mechanics and native resource payloads. |
| `:libfdx:backends:desktop_c` | `backend_desktop_c` | TeaVM C desktop backend and project generation. |
| `:libfdx:backends:ios_c` | `backend_ios_c` | Experimental TeaVM C iOS/Xcode backend. |
| `:libfdx:backends:psp` | `backend_psp` | PSP TeaVM C backend and constrained graphics runtime. |
| `:libfdx:backends:android` | `backend_android` | Android activity/view lifecycle, input, and files. |
| `:libfdx:backends:web` | `backend_web` | TeaVM browser lifecycle, input, files, preload UI, and builders. |

### 5.5. Tools And Internal Applications

| Gradle path/build | Artifact/status | Ownership |
| --- | --- | --- |
| `:libfdx:tools:font` | `tools_font` | Bitmap-font asset generation. |
| `libfdx/tools/gradle-plugin` isolated build | `gradle-plugin` and `io.github.libfdx` marker | Public platform-generation plugin. |
| `:libfdx:tools:project-generator:core` | internal | Project model, validation, and templates. |
| `:libfdx:tools:project-generator:ui` | internal | Shared project-generator UI. |
| `:libfdx:tools:project-generator:platform:desktop` | internal | Desktop generator/export application. |
| `:libfdx:tools:project-generator:platform:web` | internal | Browser generator/ZIP export application. |

### 5.6. Repository Consumers

These projects validate or demonstrate the framework and are not published
libFDX libraries:

| Family | Current topology |
| --- | --- |
| Tests | `:tests:core`; desktop, desktop C, Android, web, PSP, and plugin platform modules. |
| Benchmarks | `:benchmark:core`; desktop, desktop C, and plugin platform modules. |
| Basic sample | Shared core; desktop, desktop C, iOS C, Android, web, and plugin modules. |
| ECS Platformer | Shared core; desktop, desktop C, iOS C, Android, and web modules. |
| WebRTC multiplayer | Shared core; desktop, Android, web, and plugin modules. |

Plugin-use modules are dedicated DSL coverage/generation projects. They are not
the template for ordinary platform launchers.

## 6. Java Package Roots

Public packages start with `io.github.libfdx`. Do not add another `libfdx` or
`fdx` namespace segment. Repository folders such as `framework`, `extensions`,
and binding variants such as `jni`, `ffm`, and `c` are not Java package
segments. Internal implementation helpers may use an `.internal` subpackage and
must not leak through public APIs.

| Owner | Public package root |
| --- | --- |
| Base runtime | `io.github.libfdx.core`, `io.github.libfdx.runtime.core` |
| Typed runtime root | `io.github.libfdx` (`Fdx`) |
| Application | `io.github.libfdx.application` |
| Math / JSON / collections | `io.github.libfdx.math`, `io.github.libfdx.json`, `io.github.libfdx.collections` |
| Display / files / input / storage | `io.github.libfdx.display`, `io.github.libfdx.files`, `io.github.libfdx.input`, `io.github.libfdx.storage` |
| Networking | `io.github.libfdx.net` with focused `http`, `websocket`, `buffer`, `packet`, `codec`, `transform`, `config`, `transport`, `processing`, and `spi` subpackages |
| Assets | `io.github.libfdx.assets`, `io.github.libfdx.assets.loaders` |
| Graphics | `io.github.libfdx.graphics` |
| Camera | `io.github.libfdx.graphics.camera`, `io.github.libfdx.graphics.camera.controller` |
| 2D / 3D | `io.github.libfdx.graphics.g2d`, `io.github.libfdx.graphics.g3d` |
| UI Kit | `io.github.libfdx.ui` |
| ECS | `io.github.libfdx.ecs` |
| Scenario validation | `io.github.libfdx.validation.scenario`, `io.github.libfdx.validation.scenario.ui.kit` |
| Graphics provider core | `io.github.libfdx.graphics.<provider>` |
| Provider platform glue | `io.github.libfdx.graphics.<provider>.<platform>` when Java code is needed |
| WebRTC | `io.github.libfdx.net.webrtc` with focused config/signaling/platform/transport subpackages |
| Backends | `io.github.libfdx.backend.<backend>` |
| Tools | `io.github.libfdx.tools.<tool>` |
| Tests / samples | `io.github.libfdx.tests...`, `io.github.libfdx.samples.<name>...` |

The module owning a public concept also owns its exact package map. Java source
is authoritative for individual class placement; this table defines the stable
roots and prevents repository layout names from leaking into public packages.

## 7. Build And Publication Topology

`libfdx.toml` owns the Maven group and exact release/snapshot versions:

- snapshot tasks use `[release].fdxSnapshotVersion` exactly;
- release tasks use `[release].fdxVersion` exactly;
- publication code must not construct one value from the other.

Repository configuration has three effective modes:

| Mode | Purpose | Graph/dependencies |
| --- | --- | --- |
| Published | Clean checkout and example usage | Consumer projects use Maven artifacts and the published plugin at the configured snapshot version. |
| Local | Contributor/source validation | Consumers use checked-out `:libfdx:*` projects and the included isolated plugin build. |
| Publication | Snapshot/release preparation | Only local publishable framework/extension/backend/tool projects are configured; samples, tests, benchmarks, and consumer plugin resolution are omitted. |

The checked-in `usePublishedLibfdx` default is `true`. A system property
`libfdx.development.usePublishedLibfdx` overrides ignored root
`local.properties`, which overrides `libfdx.toml`. See
[Building](BUILDING.md#3-dependency-mode) for commands.

Aggregate publication first writes library artifacts to the prepared local
deploy repository. The isolated Gradle-plugin build then resolves the same
selected version from that repository before remote repositories and publishes
the plugin and marker. This prevents the clean-checkout default from creating a
publication bootstrap cycle.

The internal `fdx-build` project downloads checksum-verified, pinned static
FreeType/Tint dependency packages from the sibling `fdx-natives` release and
builds only libFDX bridge code. Platform packaging projects publish the generated
desktop, Android, and web runtime outputs. Third-party dependency source is not
committed or rebuilt by normal libFDX tasks.

## 8. Current Scope Boundaries

- Audio is not implemented: there is no audio framework module, artifact,
  provider, or `Fdx.audio()` accessor.
- Gamepad contracts are part of `framework/input`; current backends provide
  their platform implementations directly. No standalone input-provider module
  is checked in.
- WGPU/WebGPU, GL/WebGL, Vulkan, and Windows Direct3D 12 are the checked-in
  graphics provider families. Direct3D 12 is an independent provider implemented
  in `d3d12_core` with Java 25 FFM calls to the Windows `d3d12`, `dxgi`, and
  `d3dcompiler_47` system libraries; it has no custom JNI DLL and does not route
  through WGPU. Portable shader authoring remains WGSL-only, with HLSL generated
  by the runtime compiler for Direct3D 12.
- The Direct3D 12 provider currently targets Windows x64. Its attachments are
  independent; cross-window shared graphics contexts are not supported yet and
  fail during provider setup instead of silently falling back to another
  provider.
- WebGPU browser tasks use JavaScript; current Wasm launchers use WebGL because
  the substituted JS-native jWebGPU binding path is not WasmGC-compatible.
- iOS C is experimental and generates an Xcode handoff project; native build and
  device/simulator execution require macOS with Xcode.

Planned modules and features belong in
[GitHub issues](https://github.com/libfdx/libfdx/issues). They become
architecture only when source, build topology, tests, and documentation land
together.

## 9. Architecture Invariants

- One portable concept has one owning module and one public type family.
- Stable APIs depend toward foundation contracts, never toward providers or
  backends.
- `Fdx` remains finite, typed, and backend-owned.
- User-created features remain explicit and optional.
- Providers are selected at launcher/setup boundaries and expose advanced access
  explicitly.
- Backend/provider setup may carry native handles; normal game-facing APIs do
  not.
- Provider-backed resources respect owner, resource-domain, frame, and disposal
  lifetimes.
- `g2d`, `g3d`, and `ui_kit` remain coherent user-facing artifacts.
- Exact Java declarations live in source/Javadocs; semantic behavior lives in
  [Common API](COMMON_API.md); domain-specific concepts live in their domain
  guides.
- New or renamed modules, artifacts, packages, providers, backends, tasks, or
  public behavior must update all affected sources and docs in the same change.
