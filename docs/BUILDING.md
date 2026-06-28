# libFDX Building

This document explains how to build and run libFDX from a local checkout. It
is for contributors and engine developers who need to run samples, generate
platform-native artifacts, or verify that a local environment can build the
current repository.

This is not the architecture source of truth. Use
[Architecture](ARCHITECTURE.md) for module ownership and dependency direction,
and use [Testing](TESTING.md) when the goal is to validate behavior rather than
start a sample.

## Index

- [1. Requirements](#1-requirements)
- [2. Local Build Workflow](#2-local-build-workflow)
- [3. Repository Dependency Mode](#3-repository-dependency-mode)
- [4. Native Artifacts](#4-native-artifacts)
- [5. Basic Desktop Sample](#5-basic-desktop-sample)
- [6. Basic Android Sample](#6-basic-android-sample)
- [7. Basic iOS C Sample](#7-basic-ios-c-sample)
- [8. Basic Web Sample](#8-basic-web-sample)
- [9. WebRTC Multiplayer Sample](#9-webrtc-multiplayer-sample)

## 1. Requirements

- JDK 25 available on `PATH`
- Gradle wrapper from this repository
- Desktop runtime support for desktop launchers
- Android SDK plus a connected device or emulator for Android launchers
- Native platform toolchains only for compiling the libFDX runtime bridge and
  linking prebuilt dependency packages
- Emscripten SDK for web native artifacts; activate it with `emsdk_env` or set
  `EMSDK` so repository tasks can find the SDK checkout

Modules target Java 25 source and bytecode compatibility.

## 2. Local Build Workflow

For a normal local development pass, start with the smallest launcher that
proves the change:

1. Use a desktop sample when checking application startup or a provider-neutral
   rendering path.
2. Use `libfdx_build_native_artifacts` only when the changed target consumes
   generated native resources.
3. Use the commands in [Testing](TESTING.md) when checking a specific provider,
   widget, visual path, input path, or PSP capture.

The sample tasks in this document are interactive launchers. They are useful for
manual checks and smoke runs, but they do not replace validation tasks when a
change needs deterministic pass/fail evidence.

## 3. Repository Dependency Mode

Tests and samples can run against either the local source modules or already
published libFDX artifacts. A clean checkout compiles and exercises the local
`:libfdx:*` project dependencies and the local `libfdx/tools/gradle-plugin`
build. That is the expected mode for repository development and CI because
task wiring, samples, and tests must match the source that was checked out.
Runtime launcher modules use explicit Gradle tasks and standalone builders
instead of applying the plugin. The included plugin build must stay isolated to
the plugin project; it must not include or remap root `:libfdx:*` source modules
under the plugin build id.

Use ignored root `local.properties` overrides when checking published artifacts:

```properties
development.usePublishedLibfdx=true
development.publishedLibfdxVersion=-SNAPSHOT
```

With `development.usePublishedLibfdx=true`, the dedicated plugin-use modules
resolve the libFDX Gradle plugin from Maven, and consumers resolve libFDX
dependencies as published coordinates such as
`<fdxGroup>:<artifact>:<publishedLibfdxVersion>`. This avoids rebuilding local
libFDX modules when the goal is to check consumers against a released or
snapshot build. Settings still includes the local `:libfdx:*` source modules;
the Maven-vs-local choice is made in each consumer dependency block.
Builder-backed web tasks use local generated runtime fdx web resources only
when that consumer uses local `:libfdx:*` project dependencies. Delete the local
override keys to use the `libfdx.toml` defaults again.

To switch modes for one checkout, edit `local.properties` before running the
launcher or validation task. Gradle `-P` overrides are not supported for libFDX
dependency mode.

## 4. Native Artifacts

Native artifacts are built by explicit native/platform tasks before they are
used by tests, samples, generated platform projects, or local packaging tasks.
The aggregate task is a convenience entry point for generating the native files
that the current machine can build.

Use the aggregate task when you want to build the native artifacts supported by
the current machine, including built-in WGSL shader support for GL, Vulkan,
WebGL/GLES, or other providers that require runtime shader translation:

```powershell
.\gradlew.bat libfdx_build_native_artifacts
```

Runtime fdx desktop, Android, and web native builds enable the Tint-backed
runtime shader compiler by default. They do not build FreeType or Tint/Dawn
from source in libFDX; those static libraries are downloaded from the pinned
`fdx-natives` release and linked into the final runtime `fdx` artifacts. The
aggregate task is the clean-safe setup command to run before desktop GL/UI
samples or tests that use WGSL-only built-in renderers such as `SpriteBatch`.
It can be run after the normal Gradle `clean` task when you want a fresh native
rebuild of the libFDX bridge artifacts.

On Windows, this builds the current host runtime fdx desktop C file, the
runtime fdx web JS/WASM files, and Android AAR outputs when the Android SDK is
available. It does not prove that Linux or macOS native files were built; those
must be built on their matching platform jobs or machines.

If the web native configure step cannot start `emcmake` after a reinstall,
confirm the Emscripten SDK is installed and either run `emsdk_env.bat`/
`emsdk_env.ps1` in the shell before Gradle or set `EMSDK` to the SDK root.
The repository task also uses the SDK-bundled Python when `EMSDK_PYTHON` is
not already set, which avoids depending on a global Python installation.
Do not disable the web shader compiler for WebGL renderer validation: WebGL
executes generated GLSL ES, but built-in renderer shader source is WGSL-only.

## 5. Basic Desktop Sample

The basic desktop sample is the fastest way to confirm that a desktop launcher
starts and that a selected graphics provider can present a frame. Use the
provider-specific task that matches the backend path you are checking.
Desktop sample windows start maximized by default. Pass
`-Dlibfdx.sample.maximized=false` to use the sample's configured startup size.

From the repository root on Windows, use the task for the graphics stack you
want:

```powershell
.\gradlew.bat :samples:basic:platform:desktop:basic_desktop_gl_run
.\gradlew.bat :samples:basic:platform:desktop:basic_desktop_wgpu_run
.\gradlew.bat :samples:basic:platform:desktop:basic_desktop_vulkan_run
```

The desktop-c sample exposes platform-module aliases backed by the
plugin-use module's generated project:

```powershell
.\gradlew.bat :samples:basic:platform:desktop_c:basic_desktop_c_opengl_generate_debug
.\gradlew.bat :samples:basic:platform:desktop_c:basic_desktop_c_opengl_build_debug
.\gradlew.bat :samples:basic:platform:desktop_c:basic_desktop_c_opengl_run_debug
```

Packaged desktop JVM `_build` aliases are part of the plugin-use sample module:

```powershell
.\gradlew.bat :samples:basic:platform:plugin:libfdx_desktop_jvm_gl_build
.\gradlew.bat :samples:basic:platform:plugin:libfdx_desktop_jvm_wgpu_build
.\gradlew.bat :samples:basic:platform:plugin:libfdx_desktop_jvm_vulkan_build
```

## 6. Basic Android Sample

The Android sample tasks build and launch the basic sample on a connected
Android device or emulator. They require the Android SDK and device/emulator
setup before Gradle can install or run the app.

Use the task for the Android graphics stack you want:

```powershell
.\gradlew.bat :samples:basic:platform:android:basic_android_gles_run
.\gradlew.bat :samples:basic:platform:android:basic_android_wgpu_jni_run
.\gradlew.bat :samples:basic:platform:android:basic_android_vulkan_run
.\gradlew.bat :samples:basic:platform:android:basic_android_vulkan_fallback_run
```

## 7. Basic iOS C Sample

The iOS C sample generates TeaVM C output plus an Xcode project. The Gradle task
can run on Windows, but building or running the generated iOS app requires
macOS with Xcode.

```powershell
.\gradlew.bat :samples:basic:platform:ios_c:basic_ios_c_gles_generate
.\gradlew.bat :samples:basic:platform:ios_c:basic_ios_c_metal_generate
```

The generated Xcode project is written under
`samples/basic/platform/plugin/build/dist/ios-c/xcode`. The GLES task writes a
GLKit/OpenGLES project. The Metal task writes a native Metal/MetalKit project
that links system Apple frameworks and the generated libFDX native bridge.

## 8. Basic Web Sample

The basic web launchers live in `:samples:basic:platform:web`. The runtime web
module exposes WebGL and WebGPU JavaScript/Wasm aliases:

```powershell
.\gradlew.bat :samples:basic:platform:web:basic_webgl_js_run
.\gradlew.bat :samples:basic:platform:web:basic_webgl_wasm_run
.\gradlew.bat :samples:basic:platform:web:basic_webgpu_js_run
.\gradlew.bat :samples:basic:platform:web:basic_webgpu_wasm_run
```

For web launchers, a width or height of `0` or a negative value means the
canvas fills the browser window.

## 9. WebRTC Multiplayer Sample

The WebRTC multiplayer sample requires a standalone signaling server process.
Start the signaling server first:

```powershell
.\gradlew.bat :libfdx:extensions:net:webrtc:signaling_server:webrtc_signaling_server_run
```

The default endpoint is `ws://127.0.0.1:7777`. Configure it with Gradle system
properties when needed:

```powershell
.\gradlew.bat `
  -Dlibfdx.webrtc.signaling.host=0.0.0.0 `
  -Dlibfdx.webrtc.signaling.port=7777 `
  -Dlibfdx.webrtc.signaling.tickRate=30 `
  -Dlibfdx.webrtc.signaling.maxEventsPerTick=128 `
  -Dlibfdx.webrtc.signaling.maxQueuedEvents=4096 `
  :libfdx:extensions:net:webrtc:signaling_server:webrtc_signaling_server_run
```

The standalone server runs its own processing loop. Embedded tools that create
`WebRtcSignalingServer` directly should call `server.process(deltaTime)` from
their backend loop.

Then run the desktop sample clients in separate terminals. One client can host
a room:

```powershell
.\gradlew.bat `
  -Dlibfdx.sample.playerName=Host `
  -Dlibfdx.sample.autoHost=true `
  -Dlibfdx.sample.hostRoomId=test-room `
  :samples:multiplayer:2d-webrtc:platform:desktop:multiplayer_2d_webrtc_desktop_wgpu_run
```

Another client can join the same room:

```powershell
.\gradlew.bat `
  -Dlibfdx.sample.playerName=Client `
  -Dlibfdx.sample.autoJoinRoom=test-room `
  :samples:multiplayer:2d-webrtc:platform:desktop:multiplayer_2d_webrtc_desktop_wgpu_run
```

Web clients also connect to the standalone signaling server. Use the `signaling`
query parameter when the server is not on the default local endpoint.

```powershell
.\gradlew.bat :samples:multiplayer:2d-webrtc:platform:web:multiplayer_2d_webrtc_webgl_js_run
```
