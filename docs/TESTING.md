# libFDX Testing

This document explains how to run libFDX validation and smoke tests from a local
checkout. It is for contributors who need to verify framework behavior across
providers, platforms, UI paths, native backends, PSP output, and benchmarks.

The test launchers are not all the same type of test. Some open interactive
windows or browser pages, some run finite scripted checks, some capture visual
output, and some build platform-specific native projects. Choose the smallest
test that proves the change, then broaden to other providers or platforms when
the touched code is shared.

## Index

- [1. Choosing A Test](#1-choosing-a-test)
- [2. Provider Test Selector](#2-provider-test-selector)
- [3. Desktop Tests](#3-desktop-tests)
- [4. Desktop Native Tests](#4-desktop-native-tests)
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
3. For native backend changes, use the desktop_native or PSP tasks that exercise
   the generated native output.
4. For browser changes, use the web test launcher and query parameters for the
   smallest affected scenario.
5. For performance changes, use benchmark tasks after correctness has already
   been validated.

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
.\gradlew.bat :tests:platform:desktop:test_gl
.\gradlew.bat :tests:platform:desktop:test_wgpu
.\gradlew.bat :tests:platform:desktop:test_vulkan
```

`test_gl_validate` and `test_gl_validate_visual` are the finite validation
variants for CI/manual scripted checks:

```powershell
.\gradlew.bat :tests:platform:desktop:test_gl_validate
.\gradlew.bat :tests:platform:desktop:test_gl_validate_visual
```

`test_gl_validate` enables scripted checks and keeps the run deterministic and
finite. `test_gl_validate_visual` additionally enables visual baseline checks
when `-Dlibfdx.test.visualValidate=true` or when enabled at task level. For a
baseline-enforced run, add `-Dlibfdx.test.visualRequireBaselines=true`.

Use this PowerShell-safe form for system properties:

```powershell
.\gradlew.bat "-Dlibfdx.test.name=ui" :tests:platform:desktop:test_gl
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

## 4. Desktop Native Tests

Desktop native tests exercise the TeaVM C backend instead of the JVM desktop
backend. Use them when the change touches native generation, native resources,
desktop_native launchers, or code paths that can behave differently after TeaVM
compilation.

The desktop_native Vulkan test launcher uses the TeaVM C backend. It opens a
window by default and keeps running until the window is closed:

```powershell
.\gradlew.bat :tests:platform:desktop_native:test_vulkan_debug
.\gradlew.bat :tests:platform:desktop_native:test_vulkan_release
```

Desktop native projects expose explicit native build modes:

```powershell
.\gradlew.bat :tests:platform:desktop_native:libfdx_desktop_native_build_debug
.\gradlew.bat :tests:platform:desktop_native:libfdx_desktop_native_build_release
```

Desktop native sample, test, and benchmark task names must end in `_debug` or
`_release`. On Windows, native builds default to a console subsystem and
sample/test/benchmark run tasks open a separate console window by default so
stdout/stderr logs stay visible.

Use `"-Plibfdx.desktopNative.openConsole=false"` for inline/headless Gradle
runs, and `"-Plibfdx.desktopNative.showConsole=false"` only when a GUI-subsystem
executable is wanted.

For finite smoke runs, pass a frame count:

```powershell
.\gradlew.bat :tests:platform:desktop_native:test_vulkan_debug "-Plibfdx.desktopNative.openConsole=false" "-Dlibfdx.test.frames=60"
.\gradlew.bat :tests:platform:desktop_native:test_vulkan_release "-Plibfdx.desktopNative.openConsole=false" "-Dlibfdx.test.frames=60"
```

## 5. PSP Tests

PSP tests are platform smoke tests for the constrained PSP backend. They are
split by purpose so a failure can be isolated to direct GU rendering,
SpriteBatch, backend startup, input, UIKit, or the scripted UIKit smoke path.

The PSP cube smoke uses the TeaVM C PSP backend and draws only a direct PSP GU
3D cube:

```powershell
.\gradlew.bat :tests:platform:psp:test_cube_generate
.\gradlew.bat :tests:platform:psp:test_cube_build
```

The PSP SpriteBatch smoke is separate and renders a power-of-two checker
texture through the first constrained PSP common graphics/SpriteBatch path:

```powershell
.\gradlew.bat :tests:platform:psp:test_spritebatch_generate
.\gradlew.bat :tests:platform:psp:test_spritebatch_build
```

The PSP ApplicationBackend SpriteBatch smoke runs through
`PspApplicationBackend`, creates a typed `Fdx` root, loads `fdx.png` through
`DefaultAssetManager` and `fdx.files().internal(...)`, then renders it from an
`ApplicationListener` using `fdx.graphics().main()`:

```powershell
.\gradlew.bat :tests:platform:psp:test_backend_spritebatch_generate
.\gradlew.bat :tests:platform:psp:test_backend_spritebatch_build
```

The PSP ApplicationBackend input smoke runs through `PspApplicationBackend`,
exposes PSP controls as a standard-mapped gamepad through
`fdx.input().gamepads().find(0)`, and renders a marker that responds to the
d-pad, analog stick, and face buttons:

```powershell
.\gradlew.bat :tests:platform:psp:test_backend_input_generate
.\gradlew.bat :tests:platform:psp:test_backend_input_build
```

The PSP ApplicationBackend UIKit manual test creates a `UiRoot`, renders
buttons, labels, a checkbox, slider/progress, tabs, and a text field through
`ui-kit`, and waits for PPSSPP/PSP controller input:

```powershell
.\gradlew.bat :tests:platform:psp:test_backend_uikit_generate
.\gradlew.bat :tests:platform:psp:test_backend_uikit_build
```

The scripted UIKit smoke variant drives a short automatic input sequence and is
separate from the manual EBOOT:

```powershell
.\gradlew.bat :tests:platform:psp:test_backend_uikit_smoke_generate
.\gradlew.bat :tests:platform:psp:test_backend_uikit_smoke_build
```

The `*_generate` tasks write the TeaVM C output and PSP project shell. The
`*_build` tasks require PSPDEV/psp-cmake on the native build machine. On
Windows, use a Windows environment variable such as
`PSPDEV=E:\Dev\Env\Ubuntu\pspdev`; the generated `build.bat` converts it for
WSL.

To inspect rendered output in PPSSPP, run the capture task:

```powershell
.\gradlew.bat :tests:platform:psp:test_cube_ppsspp_capture
.\gradlew.bat :tests:platform:psp:test_spritebatch_ppsspp_capture
.\gradlew.bat :tests:platform:psp:test_backend_spritebatch_ppsspp_capture
.\gradlew.bat :tests:platform:psp:test_backend_input_ppsspp_capture
.\gradlew.bat :tests:platform:psp:test_backend_uikit_ppsspp_capture
.\gradlew.bat :tests:platform:psp:test_backend_uikit_smoke_ppsspp_capture
```

The task builds the selected EBOOT, launches PPSSPP in windowed mode, waits six
seconds, asks PPSSPP to run its `Take Screenshot` command, also sends the F12
screenshot key, copies the screenshot to
`tests/platform/psp/build/reports/ppsspp/<target>.png`, and closes PPSSPP.

If PPSSPP still does not write a screenshot, the task falls back to capturing
the emulator client area. If PPSSPP is not installed, the task downloads the
official portable Windows ZIP into `tests/platform/psp/build/tools/ppsspp`.

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
.\gradlew.bat :tests:platform:android:run_gles
.\gradlew.bat :tests:platform:android:run_wgpu_jni
.\gradlew.bat :tests:platform:android:run_vulkan
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

The desktop benchmark task runs the SpriteBatch stress benchmark across GL,
WGPU, and Vulkan. It uses visible windows, vSync disabled, the frame limiter
disabled, 8191 rotating/scaling 32x32 sprites, and 8 seconds per provider:

```powershell
.\gradlew.bat :benchmark:platform:desktop:benchmark_desktop
```

The generated Markdown report is written to
`build/reports/benchmark/desktop-sprite-batch-stress.md`.

The desktop native benchmark runs the same SpriteBatch stress benchmark through
the TeaVM C desktop_native backend. Use the graphics-specific tasks when
comparing providers:

```powershell
.\gradlew.bat :benchmark:platform:desktop_native:benchmark_desktop_native_gl_debug
.\gradlew.bat :benchmark:platform:desktop_native:benchmark_desktop_native_gl_release
.\gradlew.bat :benchmark:platform:desktop_native:benchmark_desktop_native_vulkan_debug
.\gradlew.bat :benchmark:platform:desktop_native:benchmark_desktop_native_vulkan_release
```

The aggregate `benchmark_desktop_native_debug` and
`benchmark_desktop_native_release` tasks run both GL and Vulkan. Generated
Markdown reports are written to paths such as
`build/reports/benchmark/desktop-native-gl-sprite-batch-stress-release.md` and
`build/reports/benchmark/desktop-native-vulkan-sprite-batch-stress-release.md`.

On Windows these tasks also open the native process in a separate console by
default; use `"-Plibfdx.desktopNative.openConsole=false"` for inline/headless
benchmark runs. If CMake finds `Vulkan::Vulkan`, the generated native project
uses the installed Vulkan SDK for Vulkan tasks. If not, it falls back to the
local narrow ABI shim and loads the system Vulkan runtime (`vulkan-1.dll` on
Windows or `libvulkan.so.1` on Linux) at run time. A Vulkan-capable
driver/runtime is still required to run Vulkan benchmarks.
