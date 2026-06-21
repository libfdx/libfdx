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
a specific test with `-Dlibfdx.test.name=<name>`. Core names include
`triangle`, `square`, `circle`, `texture`, `sprite`, `model`, `readback`, and
`ui`. Feature/runtime names include `shader-runtime`, `shader-scene`,
`outline-2d`, `fog-2d`, `fog-of-war-2d`, `particles-2d`, `tile-map`,
`model-skinning`, `outline-3d`, `fog-3d`, `fog-of-war-3d`, `skybox-3d`,
`billboard-3d`, `particles-3d`, `point-light-3d`, `spot-light-3d`,
`shadow-map-3d`, `cascade-shadow-map-3d`, and `camera-controllers`.
`model-skinning` renders a chained CPU-skinned bone strip so hierarchy and
palette updates are covered by a visible runtime scene.
`camera-controllers` renders the focused camera controller families in one
active showcase, including slower path-driven cinematic 3D motion and a
SpriteBatch-rendered cinematic 2D player scene.

3D graphics tests with a camera use focused camera controllers from
`io.github.libfdx.graphics.camera.controller`. Most model, lighting, fog,
particle, and billboard scenes use orbit/editor controls for inspection. Shadow
map scenes split the viewport between the game/player camera and the editor
camera. Dragging on the active viewport selects the matching controller, and only
that controller consumes keyboard movement while active. Set
`-Dlibfdx.test.cameraOrbit=true` to rotate camera-backed 3D tests automatically
during finite capture runs. A
multi-capture request with `-Dlibfdx.test.capture=...%02d.ppm` and
`-Dlibfdx.test.captureEvery=<n>` also enables orbit by default, so validation
runs capture more than the front view unless `-Dlibfdx.test.cameraOrbit=false`
is set. Use
`-Dlibfdx.test.cameraOrbitStartDegrees=<degrees>` to offset the first capture
angle and `-Dlibfdx.test.cameraOrbitDegrees=<degrees>` to control the full
finite-run orbit span. Tests that do not receive a finite frame count keep using
their per-frame orbit speed.

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

Desktop tests start maximized by default. Pass `-Dlibfdx.test.maximized=false`
to use the configured startup size instead. Supplying
`-Dlibfdx.test.width=...` or `-Dlibfdx.test.height=...` also disables the
maximized default unless `-Dlibfdx.test.maximized=true` is set explicitly.
Desktop tests also accept `-Dlibfdx.test.visible=false` and
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

Direct URLs support the same selector names as the desktop and Android
launchers. Examples include `?test=shader-runtime`, `?test=shader-scene`,
`?test=fog-of-war-2d`, `?test=particles-2d`, `?test=tile-map`,
`?test=model-skinning`, `?test=fog-of-war-3d`, `?test=skybox-3d`, `?test=billboard-3d`,
`?test=particles-3d`,
`?test=shadow-map-3d`, `?test=cascade-shadow-map-3d`, and `?test=camera-controllers`;
auto mode supports `?mode=auto` or `?auto`.
Test FPS logging is console-only and can be tuned with `?fpsLogSeconds=1`.
UIKit section and render timing probes can be loaded with query values such as
`?test=ui&uiSection=5&uiPerfLogSeconds=1`.
For 3D tests, pointer dragging inside the active viewport routes input to that
viewport's controller, and the mouse wheel adjusts the controller's configured
zoom or movement speed. The web canvas disables the browser context menu so this
drag path remains available. Use `cameraOrbit=true` in web URLs to force
automatic camera orbit, or use `cameraOrbit=false` to keep a static camera. Web
URLs that request a formatted multi-capture path with `captureEvery` orbit
automatically by default.

For browser-side visual checks, add `capture=<name>.ppm` and optionally
`captureFrame=<n>` to the URL. The web test launcher captures the active
framebuffer at frame end and exposes a base64 PPM record as
`window.libfdxLastTestCapture`, which avoids relying on post-frame canvas
exports from non-preserved WebGL drawing buffers.

For WebGPU browser checks, the same `capture=` query publishes a PNG canvas
capture after the rendered frame is presented. If the requested name does not
end in `.png`, the web launcher rewrites only the published capture name to
`.png` and sets `mime` to `image/png`. When using `captureEvery` with WebGPU,
keep `frames` greater than the last requested capture frame so the asynchronous
canvas capture runs before the test app shuts down.

For 3D shadow-map checks, treat a static front camera capture as a smoke check
only. Shadow correctness validation must use a multi-view capture so
front, side, and rear views can reveal projection, cascade selection, texture
orientation, or camera-dependent sampling defects. Use `shadow-map-3d` or
`cascade-shadow-map-3d`, add a capture name with an integer placeholder, and set
`captureEvery` so the shared 3D camera controller publishes multiple views
through `window.libfdxTestCaptures`. Browser URLs must encode the placeholder
percent sign as `%25` so Java receives `%02d` after query decoding:

```text
?graphics=webgl&test=cascade-shadow-map-3d&frames=46&capture=shadow-%2502d.ppm&captureFrame=1&captureEvery=15
```

Automatic orbit can be enabled on camera-backed 3D tests with
`-Dlibfdx.test.cameraOrbit=true`, including `model`, `model-skinning`,
`outline-3d`, `fog-3d`, `fog-of-war-3d`, `skybox-3d`, `billboard-3d`,
`particles-3d`, `point-light-3d`, `spot-light-3d`, and the shadow-map tests.
Formatted multi-capture runs with `-Dlibfdx.test.capture=...%02d.ppm` and
`-Dlibfdx.test.captureEvery=15` orbit automatically by default. Any automatic orbit source can
be disabled with `-Dlibfdx.test.cameraOrbit=false`. Add
`-Dlibfdx.test.cameraOrbitStartDegrees=90` or
`-Dlibfdx.test.cameraOrbitDegrees=180` when a validation run should focus on a
specific side arc instead of the default full finite-run orbit.

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
