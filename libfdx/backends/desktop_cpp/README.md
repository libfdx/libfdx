# Desktop C++ backend

Translates the shared libFDX tests to C++ with jNative and runs them through GLFW
with OpenGL or Vulkan. The adjacent `../jNative` checkout is included directly by
Gradle; override it with `-Pjnative.dir=E:/path/to/jNative`.

The [libFDX Gradle plugin](../../tools/gradle-plugin/README.md#desktop-c-with-jnative)
exposes this backend as `desktopCPP`, with named debug/release generation, build,
and run tasks. The shared `tests:platform:desktop_native` module configures both
`desktopC` and `desktopCPP`. Its `main` source set contains the TeaVM C launchers;
`cpp` contains the jNative launchers and their dependencies. The C++ plugin targets
use `DesktopCPPOpenGLTestLauncher` and `DesktopCPPVulkanTestLauncher`.

From the libFDX root:

```powershell
.\gradlew.bat :tests:platform:desktop_native:libfdx_desktop_cpp_opengl_generate_debug
.\gradlew.bat :tests:platform:desktop_native:libfdx_desktop_cpp_opengl_build_release
.\gradlew.bat :tests:platform:desktop_native:libfdx_desktop_cpp_opengl_run_debug -PnativeTest=SpriteBatchTest -PnativeFrames=60
.\gradlew.bat :tests:platform:desktop_native:libfdx_desktop_cpp_vulkan_run_release -PnativeTest=auto
```

Each graphics target has `generate`, `build`, and `run` tasks with explicit
`debug` and `release` suffixes. Build tasks generate and compile; run tasks also
build before launching.

The run tasks open the shared test chooser. `nativeTest` selects any registered
test, `selector`, or `auto`. `nativeFrames=60` bounds chooser and individual runs;
zero keeps them open. Automatic runs always finish the registry.
Automatic runs fail on test errors and report unsupported capabilities.
Deferred scenes finish loading and scripted checks before
the automatic runner starts its observation interval.
It does not replace visual or interactive review. Java system properties named
`libfdx.test.*` are forwarded to the native executable.

Use JDK 25, CMake, and a C++ toolchain. Windows defaults to Visual Studio 2026;
`-Plibfdx.desktopCPP.generator=...` or `CMAKE_GENERATOR` selects another generator. The build fetches
pinned GLFW, GLEW, FreeType, and zlib sources as needed and packages the existing
runtime fdx shader compiler and OpenAL Soft shared library. Vulkan uses the installed driver. Other desktop
platforms require their GLFW, GLEW, and OpenGL development packages.

jNative checks absolute source and object paths against portable length limits
before native compilation. Long checkout paths or generated class names can
produce a `JN4010` error; the error identifies the rejected path and its limit.

The shared native-test module overrides `desktopCPP.outputDir` to the root project's
`build/tests-cpp`, keeping generated C++ object paths within that budget. Its targets
use `build/tests-cpp/<opengl|vulkan>/<debug|release>`; other applications use the
[plugin's default output directory](../../tools/gradle-plugin/README.md#desktop-c-with-jnative)
unless they configure an override.
Each contains the generated `native/src` sources and CMake
project. Executables and runtime libraries are in `native/<debug|release>`, with
test assets and bundled framework resources in its `assets` subdirectory.
For example, the OpenGL release executable is
`build/tests-cpp/opengl/release/native/release/libfdx-tests-opengl-desktop-cpp`
(with `.exe` on Windows). Run tasks use that executable directory's `assets`
folder as the working directory. When launching the executable yourself, use
that working directory and arguments such as `selector 0` or `SpriteBatchTest 60`;
the launcher selects its graphics provider. Override the complete argument list
with `-Plibfdx.desktopCPP.runArgs='SpriteBatchTest 60'`.

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

For an individual native test, `-Dlibfdx.test.performance=true` disables VSync and
frame limits, measures frame timing, and exits after the measurement. It defaults
to a 3-second warmup and 8-second measurement. Customize these with
`libfdx.test.performance.warmupSeconds` and `libfdx.test.performance.seconds`;
`libfdx.test.performance.minFps` sets an optional FPS floor. Alternatively,
`libfdx.test.performance.frames` selects a measured frame count, with
`libfdx.test.performance.warmupFrames` controlling warmup (60 by default).
Select one scene rather than `auto` and leave `nativeFrames=0` so the scene remains
open until measurement completes.

```powershell
.\gradlew.bat :tests:platform:desktop_native:libfdx_desktop_cpp_opengl_run_release -PnativeTest=SpriteBatchStressTest -Dlibfdx.test.performance=true
```

Frame times use the monotonic clock, including
presentation; render-call time is reported separately. Run without competing GPU
workloads. These measurements do not establish performance on other machines.
A run fails when its measured FPS misses the configured floor.
