# libFDX Testing

This document explains how to run libFDX validation and smoke tests from a local
checkout. It is for contributors who need to verify framework behavior across
providers, platforms, UI paths, native backends, PSP output, and external
benchmark work.

The test launchers are not all the same type of test. Some open interactive
windows or browser pages, some run finite scripted checks, some capture visual
output, and some build platform-specific native projects. Choose the smallest
test that proves the change, then broaden to other providers or platforms when
the touched code is shared.

## Index

- [1. Choosing A Test](#1-choosing-a-test)
- [2. Provider Test Selector](#2-provider-test-selector)
- [3. Desktop Tests](#3-desktop-tests)
- [4. Desktop C Tests](#4-desktop-c-tests)
- [5. PSP Tests](#5-psp-tests)
- [6. Android Tests](#6-android-tests)
- [7. Web Tests](#7-web-tests)
- [8. Benchmarks](#8-benchmarks)

## 1. Choosing A Test

Use this order when deciding what to run:

1. For a simple desktop-facing change, start with the matching desktop provider
   launcher.
2. For UI, text, widget, or visual output changes, run the focused UI/test
   scenario first, then broaden providers when the renderer path is shared.
3. For native backend changes, use the desktop_c or PSP tasks that exercise
   the generated native output.
4. For browser changes, use the web test launcher and query parameters for the
   smallest affected scenario.
5. For performance changes, use the external benchmark repository after
   correctness has already been validated.

Interactive launchers are useful for manual inspection. Validation tasks are
better when a change needs deterministic pass/fail evidence.

## 2. Provider Test Selector

Interactive test launchers open a UI selector when no test is requested. Select
a specific test with `-Dlibfdx.test.name=ui`, `texture`, `triangle`, `square`,
`circle`, `sprite`, `model`, or `readback`.

Use `-Dlibfdx.test.mode=auto` to cycle through the registered tests
automatically after stable frames.

## 3. Desktop Tests

Desktop tests are the default first stop for most rendering, input, UI, and
asset smoke checks. They run quickly, expose separate GL/WGPU/Vulkan provider
paths, and can be switched between manual selector mode and finite validation
mode.

Each desktop provider-specific interactive task opens the same selector when no
test is requested. The selector includes GL, WGPU, and Vulkan launch options,
and selected tests open in separate desktop windows.

```powershell
.\gradlew.bat :tests:platform:desktop:test_desktop_gl_run
.\gradlew.bat :tests:platform:desktop:test_desktop_wgpu_run
.\gradlew.bat :tests:platform:desktop:test_desktop_vulkan_run
```

For finite validation, pass the test properties directly to the provider run
task:

```powershell
.\gradlew.bat "-Dlibfdx.test.name=ui" "-Dlibfdx.test.frames=19" "-Dlibfdx.test.validate=true" "-Dlibfdx.test.driveInput=true" :tests:platform:desktop:test_desktop_gl_run
.\gradlew.bat "-Dlibfdx.test.name=ui" "-Dlibfdx.test.frames=19" "-Dlibfdx.test.validate=true" "-Dlibfdx.test.driveInput=true" "-Dlibfdx.test.visualValidate=true" :tests:platform:desktop:test_desktop_gl_run
```

The first command enables scripted checks and keeps the run deterministic and
finite. The second additionally enables visual checks. For a baseline-enforced
run, add `-Dlibfdx.test.visualRequireBaselines=true` and the matching baseline
path properties.

Use this PowerShell-safe form for system properties:

```powershell
.\gradlew.bat "-Dlibfdx.test.name=ui" :tests:platform:desktop:test_desktop_gl_run
```

Desktop tests accept `-Dlibfdx.test.width=...`,
`-Dlibfdx.test.height=...`, `-Dlibfdx.test.visible=false`, and
`-Dlibfdx.test.safeArea=12` to adjust layout tests.

Additional runtime toggles include:

- `-Dlibfdx.test.uiScale=1.5`
- `-Dlibfdx.test.uiDebugLines=true`
- `-Dlibfdx.test.fpsLogSeconds=1`
- `-Dlibfdx.test.foregroundFps=60`

Desktop tests default `-Dlibfdx.test.foregroundFps=0`, which disables the
software frame limiter. Set `-Dlibfdx.test.fpsLogSeconds=0` to disable the FPS
logger.

## 4. Desktop C Tests

Desktop C tests exercise the TeaVM C backend instead of the JVM desktop
backend. Use them when the change touches native generation, native resources,
desktop_c launchers, or code paths that can behave differently after TeaVM
compilation.

The desktop_c OpenGL and Vulkan test launchers use the TeaVM C backend.
They open a window by default and keep running until the window is closed. Run
them through the desktop_c runtime module tasks:

```powershell
.\gradlew.bat :tests:platform:desktop_c:test_desktop_c_opengl_run_debug
.\gradlew.bat :tests:platform:desktop_c:test_desktop_c_vulkan_run_debug
.\gradlew.bat :tests:platform:desktop_c:test_desktop_c_opengl_run_release
.\gradlew.bat :tests:platform:desktop_c:test_desktop_c_vulkan_run_release
```

The desktop_c runtime module also exposes explicit desktop C build
and generate tasks:

```powershell
.\gradlew.bat :tests:platform:desktop_c:test_desktop_c_opengl_build_debug
.\gradlew.bat :tests:platform:desktop_c:test_desktop_c_vulkan_build_debug
.\gradlew.bat :tests:platform:desktop_c:test_desktop_c_opengl_build_release
.\gradlew.bat :tests:platform:desktop_c:test_desktop_c_vulkan_build_release
.\gradlew.bat :tests:platform:desktop_c:test_desktop_c_opengl_generate_debug
.\gradlew.bat :tests:platform:desktop_c:test_desktop_c_vulkan_generate_debug
.\gradlew.bat :tests:platform:desktop_c:test_desktop_c_opengl_generate_release
.\gradlew.bat :tests:platform:desktop_c:test_desktop_c_vulkan_generate_release
```

The dedicated plugin-use module still owns the plugin DSL implementation behind
those tasks and exposes matching `libfdx_desktop_c_opengl_*` and
`libfdx_desktop_c_vulkan_*` tasks for plugin coverage. Desktop C
plugin-use test tasks follow the generic task order, such as
`libfdx_desktop_c_vulkan_build_debug`.
On Windows, native builds default to a console subsystem and sample/test run
tasks open a separate console window by default so stdout/stderr logs stay
visible.

Use `"-Plibfdx.desktopC.openConsole=false"` for inline/headless Gradle
runs, and `"-Plibfdx.desktopC.showConsole=false"` only when a GUI-subsystem
executable is wanted.

## 5. PSP Tests

PSP tests use the same shared `tests/core` selector as desktop. The PSP module
only provides the PSP backend launcher and small task aliases; individual tests
are selected from the in-app UI instead of having one Gradle task per test.

```powershell
.\gradlew.bat :tests:platform:psp:test_psp_generate
.\gradlew.bat :tests:platform:psp:test_psp_build
```

The `test_psp_generate` task writes the TeaVM C output and PSP project shell.
The `test_psp_build` task requires PSPDEV/psp-cmake on the native build
machine. On Windows, use a Windows environment variable such as
`PSPDEV=E:\Dev\Env\Ubuntu\pspdev`; the generated `build.bat` converts it for
WSL.

To inspect rendered output in PPSSPP, run the capture task:

```powershell
.\gradlew.bat :tests:platform:psp:test_psp_ppsspp_capture
```

The task builds the selector EBOOT, launches PPSSPP in windowed mode, waits six
seconds, asks PPSSPP to run its `Take Screenshot` command, also sends the F12
screenshot key, copies the screenshot to
`tests/platform/plugin/build/reports/ppsspp/libfdx-plugin-tests-psp.png`, and closes
PPSSPP.

If PPSSPP still does not write a screenshot, the task falls back to capturing
the emulator client area. If PPSSPP is not installed, the task downloads the
official portable Windows ZIP into `tests/platform/plugin/build/tools/ppsspp`.

The executable can be supplied with `PPSSPP_EXECUTABLE`, `PPSSPP_HOME`, or
`-Plibfdx.psp.ppssppExecutable=C:\Path\To\PPSSPPWindows64.exe`. Adjust the wait
with `-Plibfdx.psp.ppssppCaptureDelaySeconds=8`. Disable the download fallback
with `-Plibfdx.psp.ppssppAutoDownload=false`, or override the ZIP URL with
`-Plibfdx.psp.ppssppDownloadUrl=...`.

## 6. Android Tests

Android tests validate the Android launchers and graphics-provider paths on a
real device or emulator. They require Android SDK setup and a target that Gradle
can install to.

Android test launchers open the selector by default. Selected tests run in the
same activity and show a `Back` overlay to return to the list.

```powershell
.\gradlew.bat :tests:platform:android:test_android_gles_run
.\gradlew.bat :tests:platform:android:test_android_wgpu_jni_run
.\gradlew.bat :tests:platform:android:test_android_vulkan_run
```

Android test launchers forward `-Dlibfdx.test.*` properties as activity extras,
including `libfdx.test.name`, `libfdx.test.mode=auto`, `libfdx.test.width`,
`libfdx.test.height`, and UIKit options such as `libfdx.test.uiScale`.

## 7. Web Tests

Web tests validate browser launchers and web graphics paths. Use direct query
parameters to load the smallest affected scenario, especially when debugging UI
or model rendering in a browser.

The web test webapps open the selector by default. Selected tests run in the
same canvas and show a `Back` overlay to return to the list.

Direct URLs support `?test=triangle`, `square`, `circle`, `texture`, `sprite`,
`readback`, `model`, or `ui`; auto mode supports `?mode=auto` or `?auto`. Test
FPS logging is console-only and can be tuned with `?fpsLogSeconds=1`. UIKit
section and render timing probes can be loaded with query values such as
`?test=ui&uiSection=5&uiPerfLogSeconds=1`.

The default model test uses
`data/g3d/gltf/DamagedHelmet/DamagedHelmet.gltf` and preloads declared test
assets into `webapp/assets`:

```powershell
.\gradlew.bat :tests:platform:web:test_webgl_js_run
.\gradlew.bat :tests:platform:web:test_webgl_wasm_run
.\gradlew.bat :tests:platform:web:test_webgpu_js_run
.\gradlew.bat :tests:platform:web:test_webgpu_wasm_run
```

## 8. Benchmarks

Benchmarks measure performance after correctness is already established. They
should not be used as the first validation step for a rendering or backend
change, because a fast broken frame is still a failing frame.

The benchmark project now lives outside this repository at
`https://github.com/libfdx/benchmark`. Use that repository for SpriteBatch
stress benchmark runs, provider comparisons, generated benchmark reports, and
future libGDX comparison work. This repository no longer defines `:benchmark:*`
Gradle tasks.
