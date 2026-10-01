# Desktop C++ backend

Translates the shared libFDX tests to C++ with jNative and runs them through GLFW
with OpenGL or Vulkan. The adjacent `../jNative` checkout is included directly by
Gradle; override it with `-Pjnative.dir=E:/path/to/jNative`.

From the libFDX root:

```powershell
.\gradlew.bat :tests:platform:desktop_cpp:run_opengl
.\gradlew.bat :tests:platform:desktop_cpp:run_vulkan
.\gradlew.bat :tests:platform:desktop_cpp:run_opengl -PnativeTest=SpriteBatchTest
.\gradlew.bat :tests:platform:desktop_cpp:run_opengl -PnativeTest=auto -PnativeBuildType=RELEASE
.\gradlew.bat :tests:platform:desktop_cpp:verify_native
```

The run tasks open the shared test chooser. `nativeTest` selects any registered
test, `selector`, or `auto`. `nativeFrames=60` bounds chooser and individual runs;
zero keeps them open. Automatic runs always finish the registry.
`verify_native` builds release by default and runs the complete shared automatic registry on
both providers, even if the first provider fails. It fails on test errors and reports
unsupported capabilities. Deferred scenes finish loading and scripted checks before
the automatic runner starts its observation interval.
It does not replace visual or interactive review. Java system properties named
`libfdx.test.*` are forwarded to the native executable.

Use JDK 25, CMake, and a C++ toolchain. Windows defaults to Visual Studio 2026;
`nativeGenerator` or `CMAKE_GENERATOR` selects another generator. The build fetches
pinned GLFW, GLEW, FreeType, and zlib sources as needed and packages the existing
runtime fdx shader compiler and OpenAL Soft shared library. Vulkan uses the installed driver. Other desktop
platforms require their GLFW, GLEW, and OpenGL development packages.

`generate_native` produces C++; `build_native` also compiles. All outputs stay in
`tests/platform/desktop_cpp/build`. Executables and their runtime libraries are in
`native/debug` or `native/release`, selected with `-PnativeBuildType=RELEASE`.
The test process runs from `tests/platform/desktop_cpp/build/assets`, which contains
the test assets and bundled framework resources. When launching the executable yourself,
use that working directory and arguments such as `gl selector 0`.

The backend provides native fonts, shader compilation, image decoding, keyboard,
mouse, text input, clipboard, cursor control, sound playback, and streamed music.
Audio is selected explicitly with `DesktopCppAudioProvider`; the test launcher enables it.
Gamepads are not provided. PNG decoding is portable; the native image fallback currently uses
Windows WIC. Debug enables Java stack traces; release disables that instrumentation.
Native crash reporting and symbol bundles remain disabled pending MSVC support.

Both providers support offscreen targets, depth attachments, mip chains, HDR textures,
multiple color attachments, multisampling and resolves, and compute with storage
buffers, storage textures, atomics, and readback. Vulkan prepares shaders and pipelines
on bounded workers. OpenGL prepares shader sources on workers; nonblocking driver
compilation requires the parallel shader compile extension. Final Vulkan context
disposal waits for its workers and submitted GPU work before releasing the native window.

`verify_performance` builds both release backends and compares the same OpenGL
`SpriteBatchStressTest` with 20,000 sprites at 960x640. It disables VSync and frame
limits, warms up for 3 seconds, and measures 8 seconds per run, three times per
backend in alternating order. Reports and raw logs go to
`tests/platform/desktop_cpp/build/reports/performance`. The report includes median
FPS and p95 frame time; the CSV retains every run. Executable hashes and launch
arguments are saved, and completed measurements survive a later scene failure.

```powershell
.\gradlew.bat :tests:platform:desktop_cpp:verify_performance
```

The default check requires the C++ median FPS to match or exceed the C result.
Use `-Dperformance.minRatio=0` for measurement without a relative threshold.
`performance.tests` accepts comma-separated test names; `performance.repeats`,
`performance.warmupSeconds`, `performance.seconds`, and `performance.minFps`
customize the run. For an individual native test, `-Dlibfdx.test.performance=true`
enables the same timing and bounded exit, with settings under
`libfdx.test.performance.*`. Frame times use the monotonic clock, including
presentation; render-call time is reported separately. Run without competing GPU
workloads. These measurements do not establish performance on other machines.
The check deliberately fails when the measured C++ performance misses the floor;
a successful native build by itself does not imply performance parity.
