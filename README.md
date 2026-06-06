<img src="data/libfdx_logo_dark.svg" width="300" />

libFDX is a modular Java game framework focused on provider-neutral application, runtime, and graphics APIs. Game code is intended to depend on common API modules, while platform launchers choose the backend and provider stack.

libFDX is inspired by libGDX, but it is a new framework rather than a fork, port, or compatibility layer.

This repository is in early implementation. The detailed contracts live in the docs:

- [Architecture](docs/ARCHITECTURE.md): module layout, dependency direction, package roots, artifact naming, and provider boundaries.
- [Common API](docs/COMMON_API.md): provider-neutral public API contracts and behavior.
- [UI Kit](docs/UI_KIT.md): Compose-inspired retained UI toolkit specification.

## Community

Join the [libFDX Discord](https://discord.gg/CutyWq27Gu) to ask questions, discuss the framework, and follow development.

## Requirements

- JDK available on `PATH`
- Gradle wrapper from this repository
- Desktop runtime support for the desktop sample
- Android SDK plus a connected device or emulator for Android launchers

Modules target Java 25 source and bytecode compatibility. Use JDK 25 for builds and desktop tasks.

## Gradle Plugin

The libfdx Gradle plugin lives in `libfdx/tools/gradle-plugin` and is consumed through an included build. This repository wires it in from `settings.gradle.kts`; external builds can do the same:

```kotlin
pluginManagement {
    includeBuild("<libfdx>/libfdx/tools/gradle-plugin")
}
```

Apply it with `id("io.github.libfdx")` and configure targets inside `libfdx { ... }`.

The plugin can register explicit bitmap-font generation tasks from TTF files. A `bitmapFont("ui_24")` block creates `libfdx_bitmap_font_ui_24` and the aggregate `libfdx_generate_bitmap_fonts`; it does not run during target builds and does not add generated files to `libfdx.assets`. Set `outputDir` to the asset root when you intentionally want the task to write source assets, then run the task yourself:

```kotlin
libfdx {
    assets(layout.projectDirectory.dir("assets"))
    bitmapFont("ui_24") {
        sourceFile.set(layout.projectDirectory.file("assets/font/lsans.ttf"))
        outputDir.set(layout.projectDirectory.dir("assets"))
        assetPath.set("font/bitmap")
        size.set(24)
        maxTextureSize.set(512)
    }
}
```

## Maven Publishing

Set the Maven group in `libfdx.toml` as `release.fdxGroup`, and set the upcoming release version as `release.fdxVersion` without `-SNAPSHOT`. The publish tasks derive the Maven version from the task: release tasks publish the configured version, and snapshot tasks publish exactly `-SNAPSHOT`.

Tests, samples, and benchmarks use local source modules by default. To validate those consumers against published libFDX artifacts, set `development.usePublishedLibfdx = true` in `libfdx.toml`; their dependency blocks use explicit `if (LibExt.usePublishedLibfdx)` branches and then resolve libFDX dependencies as `<fdxGroup>:<artifact>:<publishedLibfdxVersion>`. The default `development.publishedLibfdxVersion` is `-SNAPSHOT`. The Gradle override is `-Plibfdx.usePublishedLibfdx=true`, and `-Plibfdx.publishedVersion=<version>` can override the dependency version when needed.

```powershell
.\gradlew.bat prepareSnapshotDeploy
.\gradlew.bat prepareReleaseDeploy
.\gradlew.bat publishSnapshot
.\gradlew.bat publishRelease
```

`prepareSnapshotDeploy` writes local snapshot artifacts with Maven version `-SNAPSHOT` to `build/snapshot-deploy`. `prepareReleaseDeploy` writes release artifacts with the configured base version to `build/staging-deploy` and creates `build/staging-deploy.zip` for Maven Central Portal upload.

`publishSnapshot` uploads snapshot artifacts to the Central Portal snapshot repository. `publishRelease` prepares the release bundle and uploads it to Maven Central Portal. Remote publish tasks require `CENTRAL_PORTAL_USERNAME` and `CENTRAL_PORTAL_PASSWORD`; signed Central releases also require `SIGNING_KEY` and `SIGNING_PASSWORD`.

The published `core` artifact includes generated desktop and web runtime-core native resources. Local publish/deploy tasks build the current host desktop native and web FreeType bridge. GitHub publication uses platform jobs to build Windows, Linux, macOS, Android, and web artifacts first, then the final publish job downloads those artifacts and runs Gradle with `-Plibfdx.runtimeCore.usePrebuiltNatives=true`.

## Standalone Builders

Tools that do not use Gradle, such as editors, can call the Java builders from the owning backend modules:

```java
WebBuilder.javascript()
    .mainClass("com.example.EditorLauncher")
    .classpathFromCurrentJvm()
    .asset(Path.of("assets"))
    .bitmapFont(Path.of("assets/font/lsans.ttf"), "ui_24", 24)
    .webappDirectory(Path.of("build/editor-web"))
    .fillWindow()
    .build();
```

Use `WebBuilder.wasm()` for a Wasm webapp. The web builder compiles TeaVM, copies assets into `webapp/assets`, writes generated asset metadata for preload, and writes the same web shell used by the Gradle plugin.

```java
NativeBuilder.desktop()
    .mainClass("com.example.EditorLauncher")
    .classpathFromCurrentJvm()
    .nativeResourceClasspathFromCurrentJvm()
    .outputDirectory(Path.of("build/editor-native"))
    .build();
```

The native builder compiles TeaVM C output and writes the same desktop-native CMake project shell used by the Gradle plugin.

```java
PspBuilder.psp()
    .mainClass("com.example.PspLauncher")
    .classpathFromCurrentJvm()
    .nativeResourceClasspathFromCurrentJvm()
    .asset(Path.of("assets"))
    .bitmapFont(Path.of("assets/font/lsans.ttf"), "ui_24", 24)
    .outputDirectory(Path.of("build/editor-psp"))
    .build();
```

The PSP builder compiles TeaVM C output, copies declared assets into the generated PSP release layout, and writes a PSP CMake/script project shell. Building the EBOOT requires PSPDEV/psp-cmake on the native build machine. On Windows, set `PSPDEV` to the Windows PSP toolchain path, for example `E:\Dev\Env\Ubuntu\pspdev`; the generated `build.bat` converts it to a WSL path before running `build.sh`.

## Run The Basic Desktop Sample

From the repository root on Windows, use the task for the graphics stack you want:

```powershell
.\gradlew.bat :samples:basic:platform:desktop:run_gl
.\gradlew.bat :samples:basic:platform:desktop:run_wgpu
.\gradlew.bat :samples:basic:platform:desktop:run_vulkan
.\gradlew.bat :samples:basic:platform:desktop_native:run_gl_debug
.\gradlew.bat :samples:basic:platform:desktop_native:run_gl_release
```

## Run The Basic Android Sample

Use the task for the Android graphics stack you want:

```powershell
.\gradlew.bat :samples:basic:platform:android:run_gles
.\gradlew.bat :samples:basic:platform:android:run_wgpu_jni
.\gradlew.bat :samples:basic:platform:android:run_vulkan
.\gradlew.bat :samples:basic:platform:android:run_vulkan_fallback
```

## Run The Basic Web Sample

The web sample builds JavaScript and Wasm WebGL webapps:

```powershell
.\gradlew.bat :samples:basic:platform:web:libfdx_web_js_run
.\gradlew.bat :samples:basic:platform:web:libfdx_web_wasm_run
```

For web launchers, a width or height of `0` or a negative value means the canvas fills the browser window.

## Run Provider Tests

Interactive test launchers open a UI selector when no test is requested. Select a specific test with `-Dlibfdx.test.name=ui`, `texture`, `triangle`, `square`, `circle`, `sprite`, `model`, or `readback`. Use `-Dlibfdx.test.mode=auto` to cycle through the registered tests automatically after stable frames.

### Desktop

Each desktop provider-specific interactive task opens the same selector when no test is requested. The selector includes GL, WGPU, and Vulkan launch options, and selected tests open in separate desktop windows.

```powershell
.\gradlew.bat :tests:platform:desktop:test_gl
.\gradlew.bat :tests:platform:desktop:test_wgpu
.\gradlew.bat :tests:platform:desktop:test_vulkan
```

Provider-specific interactive tasks open the selector with all desktop API options when `libfdx.test.name` is not set.
`test_gl_validate` and `test_gl_validate_visual` are the finite validation variants for CI/manual scripted checks.

LWJGL 3.4.x uses its Java 25 backend automatically through multi-release classes, so the desktop provider tasks use one Java 25 default name per API.

Desktop validation tasks also exist:

```powershell
.\gradlew.bat :tests:platform:desktop:test_gl_validate
.\gradlew.bat :tests:platform:desktop:test_gl_validate_visual
```

`test_gl_validate` enables the scripted checks and keeps the run deterministic and finite. `test_gl_validate_visual` additionally enables visual baseline checks when `-Dlibfdx.test.visualValidate=true` (or keeps them enabled when set at task level). If you want a baseline-enforced run, add `-Dlibfdx.test.visualRequireBaselines=true`.

Use this PowerShell-safe form for system properties:

```powershell
.\gradlew.bat "-Dlibfdx.test.name=ui" :tests:platform:desktop:test_gl
```

Desktop tests accept `-Dlibfdx.test.width=...`, `-Dlibfdx.test.height=...`, `-Dlibfdx.test.visible=false`, and `-Dlibfdx.test.safeArea=12` to adjust layout tests. Additional runtime toggles include `-Dlibfdx.test.uiScale=1.5` for default scale, `-Dlibfdx.test.uiDebugLines=true` (or the UITest `Visual debug` checkbox) to control scaling and overlay behavior, and `-Dlibfdx.test.fpsLogSeconds=1` to log active test or selector FPS to the console every second. Desktop tests default `-Dlibfdx.test.foregroundFps=0`, which disables the software frame limiter; set a positive value such as `-Dlibfdx.test.foregroundFps=60` only when you want a cap. Set `-Dlibfdx.test.fpsLogSeconds=0` to disable the FPS logger.

### Desktop Native

The desktop_native Vulkan test launcher uses the TeaVM C backend. It opens a window by default and keeps running until the window is closed:

```powershell
.\gradlew.bat :tests:platform:desktop_native:test_vulkan_debug
.\gradlew.bat :tests:platform:desktop_native:test_vulkan_release
```

Desktop native projects expose explicit native build modes:

```powershell
.\gradlew.bat :tests:platform:desktop_native:libfdx_desktop_native_build_debug
.\gradlew.bat :tests:platform:desktop_native:libfdx_desktop_native_build_release
```

Desktop native sample, test, and benchmark task names must end in `_debug` or `_release`. On Windows, native builds default to a console subsystem and sample/test/benchmark run tasks open a separate console window by default so stdout/stderr logs stay visible. Use `"-Plibfdx.desktopNative.openConsole=false"` for inline/headless Gradle runs, and `"-Plibfdx.desktopNative.showConsole=false"` only when a GUI-subsystem executable is wanted.

For finite smoke runs, pass a frame count:

```powershell
.\gradlew.bat :tests:platform:desktop_native:test_vulkan_debug "-Plibfdx.desktopNative.openConsole=false" "-Dlibfdx.test.frames=60"
.\gradlew.bat :tests:platform:desktop_native:test_vulkan_release "-Plibfdx.desktopNative.openConsole=false" "-Dlibfdx.test.frames=60"
```

### PSP

The PSP cube smoke uses the TeaVM C PSP backend and draws only a direct PSP GU 3D cube:

```powershell
.\gradlew.bat :tests:platform:psp:test_cube_generate
.\gradlew.bat :tests:platform:psp:test_cube_build
```

The PSP SpriteBatch smoke is separate and renders a power-of-two checker texture through the first constrained PSP common graphics/SpriteBatch path:

```powershell
.\gradlew.bat :tests:platform:psp:test_spritebatch_generate
.\gradlew.bat :tests:platform:psp:test_spritebatch_build
```

The PSP ApplicationBackend SpriteBatch smoke runs through `PspApplicationBackend`, creates a typed `Fdx` root, loads `fdx.png` through `DefaultAssetManager` and `fdx.files().internal(...)`, then renders it from an `ApplicationListener` using `fdx.graphics().main()`:

```powershell
.\gradlew.bat :tests:platform:psp:test_backend_spritebatch_generate
.\gradlew.bat :tests:platform:psp:test_backend_spritebatch_build
```

The PSP ApplicationBackend input smoke runs through `PspApplicationBackend`, exposes PSP controls as a standard-mapped gamepad through `fdx.input().gamepads().find(0)`, and renders a marker that responds to the d-pad, analog stick, and face buttons:

```powershell
.\gradlew.bat :tests:platform:psp:test_backend_input_generate
.\gradlew.bat :tests:platform:psp:test_backend_input_build
```

The PSP ApplicationBackend UIKit manual test creates a `UiRoot`, renders buttons, labels, a checkbox, slider/progress, tabs, and a text field through `ui-kit`, and waits for PPSSPP/PSP controller input:

```powershell
.\gradlew.bat :tests:platform:psp:test_backend_uikit_generate
.\gradlew.bat :tests:platform:psp:test_backend_uikit_build
```

The scripted UIKit smoke variant drives a short automatic input sequence and is separate from the manual EBOOT:

```powershell
.\gradlew.bat :tests:platform:psp:test_backend_uikit_smoke_generate
.\gradlew.bat :tests:platform:psp:test_backend_uikit_smoke_build
```

The `*_generate` tasks write the TeaVM C output and PSP project shell. The `*_build` tasks require PSPDEV/psp-cmake on the native build machine. On Windows, use a Windows environment variable such as `PSPDEV=E:\Dev\Env\Ubuntu\pspdev`; the generated `build.bat` converts it for WSL.

To inspect the rendered output in PPSSPP, run the capture task:

```powershell
.\gradlew.bat :tests:platform:psp:test_cube_ppsspp_capture
.\gradlew.bat :tests:platform:psp:test_spritebatch_ppsspp_capture
.\gradlew.bat :tests:platform:psp:test_backend_spritebatch_ppsspp_capture
.\gradlew.bat :tests:platform:psp:test_backend_input_ppsspp_capture
.\gradlew.bat :tests:platform:psp:test_backend_uikit_ppsspp_capture
.\gradlew.bat :tests:platform:psp:test_backend_uikit_smoke_ppsspp_capture
```

The task builds the selected EBOOT, launches PPSSPP in windowed mode, waits six seconds, asks PPSSPP to run its `Take Screenshot` command, also sends the F12 screenshot key, copies the screenshot to `tests/platform/psp/build/reports/ppsspp/<target>.png`, and closes PPSSPP. If PPSSPP still does not write a screenshot, the task falls back to capturing the emulator client area. If PPSSPP is not installed, the task downloads the official portable Windows ZIP into `tests/platform/psp/build/tools/ppsspp`. The executable can be supplied with `PPSSPP_EXECUTABLE`, `PPSSPP_HOME`, or `-Plibfdx.psp.ppssppExecutable=C:\Path\To\PPSSPPWindows64.exe`; adjust the wait with `-Plibfdx.psp.ppssppCaptureDelaySeconds=8`. Disable the download fallback with `-Plibfdx.psp.ppssppAutoDownload=false`, or override the ZIP URL with `-Plibfdx.psp.ppssppDownloadUrl=...`.

### Android

Android test launchers open the selector by default. Selected tests run in the same activity and show a `Back` overlay to return to the list.

```powershell
.\gradlew.bat :tests:platform:android:run_gles
.\gradlew.bat :tests:platform:android:run_wgpu_jni
.\gradlew.bat :tests:platform:android:run_vulkan
```

Android test launchers forward `-Dlibfdx.test.*` properties as activity extras, including `libfdx.test.name`, `libfdx.test.mode=auto`, `libfdx.test.width`, `libfdx.test.height`, and UIKit options such as `libfdx.test.uiScale`.

### Web

The web test webapps open the selector by default. Selected tests run in the same canvas and show a `Back` overlay to return to the list. Direct URLs support `?test=triangle`, `square`, `circle`, `texture`, `sprite`, `readback`, `model`, or `ui`; auto mode supports `?mode=auto` or `?auto`. Test FPS logging is console-only and can be tuned with `?fpsLogSeconds=1`. UIKit section and render timing probes can be loaded with query values such as `?test=ui&uiSection=5&uiPerfLogSeconds=1`.
The default model test uses `data/g3d/gltf/DamagedHelmet/DamagedHelmet.gltf` and preloads declared test assets into `webapp/assets`:

```powershell
.\gradlew.bat :tests:platform:web:test_webgl_js_run
.\gradlew.bat :tests:platform:web:test_webgl_wasm_run
.\gradlew.bat :tests:platform:web:test_webgpu_js_run
.\gradlew.bat :tests:platform:web:test_webgpu_wasm_run
```

## Run Desktop Benchmark

The desktop benchmark task runs the SpriteBatch stress benchmark across GL, WGPU, and Vulkan. It uses visible windows, vSync disabled, the frame limiter disabled, 8191 rotating/scaling 32x32 sprites, and 8 seconds per provider:

```powershell
.\gradlew.bat :benchmark:platform:desktop:benchmark_desktop
```

The generated Markdown report is written to `build/reports/benchmark/desktop-sprite-batch-stress.md`.

The desktop native benchmark runs the same SpriteBatch stress benchmark through the TeaVM C desktop_native backend. Use the graphics-specific tasks when comparing providers:

```powershell
.\gradlew.bat :benchmark:platform:desktop_native:benchmark_desktop_native_gl_debug
.\gradlew.bat :benchmark:platform:desktop_native:benchmark_desktop_native_gl_release
.\gradlew.bat :benchmark:platform:desktop_native:benchmark_desktop_native_vulkan_debug
.\gradlew.bat :benchmark:platform:desktop_native:benchmark_desktop_native_vulkan_release
```

The aggregate `benchmark_desktop_native_debug` and `benchmark_desktop_native_release` tasks run both GL and Vulkan. Generated Markdown reports are written to paths such as `build/reports/benchmark/desktop-native-gl-sprite-batch-stress-release.md` and `build/reports/benchmark/desktop-native-vulkan-sprite-batch-stress-release.md`. On Windows these tasks also open the native process in a separate console by default; use `"-Plibfdx.desktopNative.openConsole=false"` for inline/headless benchmark runs. If CMake finds `Vulkan::Vulkan`, the generated native project uses the installed Vulkan SDK for Vulkan tasks. If not, it falls back to the local narrow ABI shim and loads the system Vulkan runtime (`vulkan-1.dll` on Windows or `libvulkan.so.1` on Linux) at run time. A Vulkan-capable driver/runtime is still required to run Vulkan benchmarks.

## Design Shape

- Common game code receives a typed `Fdx` root and uses provider-neutral APIs such as `Display`, `Graphics`, and `GraphicsContext`.
- Backend and provider choices belong in launcher or platform modules.
- Provider-specific access is explicit through `providerId()` and `as()`.
- User-created systems such as asset managers, UI roots, sprite batches, and physics worlds stay explicit instead of being returned from a generic service locator.

## License

libFDX is licensed under the [Apache License 2.0](LICENSE).
