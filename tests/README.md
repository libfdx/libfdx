# Running and reviewing tests

The test chooser is both a collection of interactive examples and a runtime
regression suite. Its automatic run visits the scenarios registered in
`TestSelector`; the selector is the authoritative catalog.

Each test implementation has one chooser entry. Useful options belong in the
test's own controls; scenario-specific wrappers must not create extra entries.

The executable class name is the test name, both in the chooser and in launch
arguments, for example `-Dlibfdx.test.name=Fog3DTest` or `?test=Fog3DTest`.
Lookup ignores case and surrounding whitespace. The `auto` and `selector`
values select launcher modes. The chooser lists tests alphabetically, and
search matches part of the class name, ignoring case. Click once to select a row and
double-click (or double-tap) to open it. Clear restores the full list.

## What a result proves

- Unit tests check specific contracts with assertions. Gradle's `UP-TO-DATE`
  means a previous result was reused, not that the test executed again.
- An automatic runtime pass means creation, rendering, frame-end checks and
  cleanup completed without reported errors. Scenarios with loading, streaming
  or audio completion checks must finish those checks too.
- A skipped scenario was not validated. Read its reason: missing graphics
  features and an absent audio provider are platform limitations.
- A visual check requires inspecting the rendered scene or comparing a capture
  with the relevant fixture checker. A clear-only screen, a successful build,
  or an automatic pass is not proof that a picture is correct.
- Input, camera controls and audible playback also need interaction. In a
  browser, click the audio scene to activate playback. An unactivated automatic
  audio scenario must fail its completion check.

The automatic runner gives each scenario six seconds after its frame-stability
warm-up. Music needs at least 4.8 seconds after loading and activation to finish
its crossfade, pause, seek and release sequence. For a slower device, increase
`libfdx.test.autoDurationSeconds`; do not remove completion assertions to obtain
a green result. Zero frame limits mean interactive use; negative limits are
reserved for the automatic runner and still enforce completion checks.

## Windows

### Complete platform matrices

Run one of these from the repository root:

```powershell
.\gradlew.bat validate_desktop_graphics
.\gradlew.bat validate_android_graphics
.\gradlew.bat validate_web_graphics
```

Shared IDE run configurations expose these exact names in the `libfdx-tests` folder.
Each root task delegates to `:tests:platform:<platform>:validate_<platform>_graphics`.
The host supervisor visits the chooser catalog, runs each supported target
combination in isolation, and writes a numbered checklist plus per-case logs
under `build/<platform>-auto/run-*/`. Test exceptions, crashes and timeouts do
not stop later cases. Setup errors (such as an APK build/install failure or a
disconnected device) can prevent a matrix from starting. Unsuccessful results
make Gradle fail after the matrix finishes, keeping the report available.

| Task | Default combinations | Prerequisite |
| --- | --- | --- |
| `validate_desktop_graphics` | GL, Vulkan, Direct3D 12, WGPU | Desktop graphics drivers |
| `validate_android_graphics` | GLES, Vulkan, WGPU JNI | One authorized ADB device; use `ANDROID_SERIAL` to select it |
| `validate_web_graphics` | JS/WebGL, JS/WebGPU, Wasm/WebGL | Chromium, or an installed Chrome/Edge channel |

Android installs the debug APK once, then force-stops and relaunches the test
application for every pair. Completion uses a unique token emitted after
activity/backend cleanup; logcat is scoped to the test application's UID.
The supervisor also force-stops the app after a timed-out worker.

Web builds the applications once and serves them locally. Every pair uses a
fresh browser worker with console/error collection, an activation click after
the test is ready, and completion verification after backend disposal.
The browser version is logged. A final-page PNG is diagnostic evidence, not
an image-comparison assertion. Headed Chromium is the default so graphics
support reflects an ordinary desktop browser. Headless results can differ.

Install the pinned test Chromium once, or use an already installed channel:

```powershell
.\gradlew.bat :tests:platform:web:install_test_browser
.\gradlew.bat validate_web_graphics '-Dlibfdx.test.autoWebChannel=msedge'
```

`autoWebChannel` accepts Playwright Chromium channels such as `chrome` and
`msedge`; `autoWebExecutable` selects an explicit browser executable.
`autoWebHeadless=true` selects headless mode. No browser is downloaded by
`validate_web_graphics`; missing browsers are recorded as failures with launch diagnostics in the case log.

All matrix tasks accept `libfdx.test.autoTests` (comma-separated test class
names), `libfdx.test.autoGraphics` (combinations from the table, using
`gl,vulkan,d3d12,wgpu`, `gles,vulkan,wgpu_jni`, or
`js-webgl,js-webgpu,wasm-webgl`), `autoTimeoutSeconds` (180 by default),
`autoDurationSeconds` (six by default), and `autoReportDirectory`.
These are system properties with the `libfdx.test.` prefix. For example:

```powershell
.\gradlew.bat validate_web_graphics '-Dlibfdx.test.autoTests=CircleTest,TextureTest' '-Dlibfdx.test.autoGraphics=js-webgl,js-webgpu'
```

The report distinguishes PASS, FAIL, TIMEOUT, UNSUPPORTED, LAUNCH ERROR,
RUNNING and PENDING. PASS proves runtime observation, built-in checks and
cleanup; it does not certify visuals, every input control, or audible output.

From the repository root:

```powershell
.\gradlew.bat test :gradle-plugin:test --rerun-tasks --continue
.\gradlew.bat :libfdx:tools:project-generator:core:test_generate_project :libfdx:tools:project-generator:platform:desktop:test_export_project :libfdx:tools:project-generator:platform:web:test_archive_project
.\gradlew.bat :tests:platform:desktop:libfdx_desktop_jvm_tests_gl_run '-Dlibfdx.test.name=auto'
```

Desktop Auto (including the chooser's Auto button) visits each chooser test
across GL, Vulkan, Direct3D 12 and WGPU in a fresh JVM per pair. The selected
chooser provider does not restrict Auto. Crashes, exceptions, unsupported
features and timeouts are recorded, then the next pair runs. Unsupported
features reported by the capability guard appear as UNSUPPORTED, not
successful skips. Other startup failures retain their error details. The supervisor returns a nonzero exit code only after all
pairs finish if any were unsuccessful.

Each invocation prints a unique `build/desktop-auto/run-*/checklist.md` path.
The numbered checklist is updated before and after each pair and links to
individual logs. PASS requires the full observation interval, scenario
completion assertions, cleanup and a clean child exit. Early closure is a
failure. Pending/running entries are not passes. Runtime success does not
certify image correctness, manual input or audible playback.

`libfdx.test.autoTimeoutSeconds` sets the wall-clock limit per pair (default
180 seconds, including startup and cleanup). Desktop additionally limits shutdown
to 15 seconds after observation completes, controlled by
`libfdx.test.autoShutdownTimeoutSeconds`. A shutdown hang is recorded as TIMEOUT
and the child is forcibly terminated before continuing. Existing `autoDurationSeconds`
and stabilization properties control observation (six seconds by default).
For focused reruns, `libfdx.test.autoTests` and `libfdx.test.autoGraphics` accept
comma-separated catalog names and providers. `libfdx.test.autoReportDirectory`
changes the parent output directory; each run still gets a unique subdirectory.
For example:

```powershell
.\gradlew.bat :tests:platform:desktop:libfdx_desktop_jvm_tests_gl_run '-Dlibfdx.test.name=auto' '-Dlibfdx.test.autoTests=CircleTest,ShadowQualityTest' '-Dlibfdx.test.autoGraphics=gl,vulkan,d3d12,wgpu'
```

Android and web chooser Auto buttons still use their in-process runner.
Use the root matrix tasks above for host supervision and recovery on those platforms.

The project generator uses executable smoke checks instead of JUnit, hence its
separate tasks. Use the chooser for focused visual and interactive review.
Select other graphics providers through the desktop launch tasks when reviewing
features unavailable through GL. Gradle task help describes the available targets.

## Android

```powershell
adb devices -l
.\gradlew.bat :tests:platform:android:libfdx_android_gles_run '-Dlibfdx.test.name=auto'
```

Use a booted emulator or device with enough space to install the app. With
multiple devices, set `ANDROID_SERIAL` to the intended device before launching.
Inspect a real frame and retain the app's log output through the final summary;
installation and launch success do not prove that the suite finished.

## Web

```powershell
.\gradlew.bat :tests:platform:web:libfdx_web_js_webgl_run
```

Open the served page in a real browser. Append `?auto` for the automatic run,
or use the chooser to open individual scenarios. Keep the tab visible because
browsers can throttle background rendering. Retain console errors and the final
summary. WebGPU and WebAssembly launch tasks exercise different paths and must
be reported separately when tested.

## Keeping the suite useful

Keep small shape tests: they isolate geometry, shader and presentation failures
that complex scenes obscure. Keep paired rendering tests when the two sides
provide a meaningful reference. Combined showcases exercise interaction between
features, but do not replace focused assertions. Performance checks should state
what they measure and warm the measured code before collecting samples.

Every new scene should tell the viewer what to expect, provide understandable
controls, and fail when required work never completed. For example, the shader
graph program displays its actual offscreen outputs: a blue triangle, plus an
orange triangle when multiple render targets are available. A plain status color
would not demonstrate that the shader drew anything.

Fixture-specific expectations and image comparison commands live beside their
checkers under `core/src/test/fixtures`. Use those instructions for exact poses,
capture sizes and tolerances rather than applying one generic image threshold
to every scene.
