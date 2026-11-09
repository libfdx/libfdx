# libFDX Benchmarks

Benchmarks exercise the current framework sources. `core` owns benchmark
cases/results; platform modules own launchers and generated reports. The plugin
module validates generated native benchmark tasks.

Establish correctness before using benchmark results as evidence. Compare
providers with the same scene, workload, duration, visibility, and frame-limit
settings.

The [jNative performance comparisons](JNATIVE_PERFORMANCE.md) list measurements,
runtime comparisons, and the next target.

The default libFDX CPU sprite workload follows the per-sprite algorithm used by
gdx-teavm's stock SpriteBatchTest. Each sprite refreshes its cached vertices when
dirty and submits them through the batch's ordinary array-copy draw method.
libFDX supplies the texture and graphics provider. The opt-in vertex packing
comparison below measures a separate workload.

For the libFDX CPU sprite benchmark, the desktop C and C++ launchers accept
`--frameProfile=true` with `--benchmark=sprite_batch_libfdx --sprites=8191`.
Result properties include unrounded nanosecond totals for coarse frame components,
paired with the measured frame intervals. This diagnostic excludes capture and
`cpuDiagnostic` modes.
Outside-render time includes backend work, presentation, reporting, and scheduling;
the phase values measure CPU wall time, not GPU execution. `--cpuDiagnostic=staging`
instead isolates the full float-array-to-direct-buffer transfer and checks every
output float's raw bits outside timing.

## Desktop JVM

The OpenGL CPU sprite benchmark can compare Java and C++ vertex packing on the
same JVM. The C++ mode builds a Release JNI library using CMake and a host C++
compiler. Both modes advance the scene by a fixed step per frame and use the
same vertex layout, upload path, and draw calls. Before warm-up they check the
packed vertices and updated sprite state against the Java implementation.

```powershell
.\gradlew.bat :benchmark:platform:desktop:benchmark_sprite_batch_libfdx_gl_jni '-Dlibfdx.benchmark.spritePacking=java' '-Dlibfdx.benchmark.frameProfile=true'
.\gradlew.bat :benchmark:platform:desktop:benchmark_sprite_batch_libfdx_gl_jni '-Dlibfdx.benchmark.spritePacking=cpp' '-Dlibfdx.benchmark.frameProfile=true'
```

The default scene contains 8,191 sprites in one batch. In these profiled runs,
divide `profileFillTotalNanos` by `profileIntervals` to get mean packing time
per frame, including the JNI call in C++ mode. Compare that with
`measuredFrameFps`, `frameTimeP50Millis`, and `frameTimeP99Millis`. The result
files are `libfdx-cpu-jvm-java.properties` and `libfdx-cpu-jvm-cpp.properties`
under the desktop benchmark module's `build/benchmark-results/` directory.
Set `libfdx.benchmark.result` to keep separate files for repeated runs.

For the C++ implementation of the full sprite workload, use the Windows x64
task below. Java loads a JNI library and calls it once. The library owns GLFW,
texture loading, sprite state, batch staging, OpenGL submission, and timing.
It mirrors the Java workload's per-sprite objects, separate vertex arrays,
dirty-vertex updates and batch append. It renders the same 8,191 sprites with one
indexed draw per frame through direct OpenGL calls.

```powershell
.\gradlew.bat :benchmark:platform:desktop:benchmark_sprite_batch_libfdx_cpp_jni
.\gradlew.bat :benchmark:platform:plugin:benchmark_libfdx_desktop_c_gl_release
.\gradlew.bat :benchmark:platform:plugin:benchmark_libfdx_desktop_cpp_gl_release
```

The C++ task uses CMake and MSVC in Release mode and defaults to 5 seconds of
warm-up and 15 seconds measured. It accepts `libfdx.benchmark.seconds` for total
duration, `libfdx.benchmark.warmupSeconds`, `libfdx.benchmark.frameProfile`, and
`libfdx.benchmark.result`. Setting `libfdx.benchmark.capture` writes the first
frame at a fixed animation step and exits before timing.
Set `libfdx.benchmark.cpuDiagnostic=full` to run the matching CPU-only update
and batch append loop with vertex hashes and graphics work outside timing.

Run the maintained desktop provider set with:

```powershell
.\gradlew.bat :benchmark:platform:desktop:benchmark_desktop
```

The current SpriteBatch stress report is written under
`build/reports/benchmark`. Use Gradle task discovery for individual provider
variants rather than copying their names into another catalog:

```powershell
.\gradlew.bat :benchmark:platform:desktop:tasks --all
```

Platform-specific providers are included only where supported by the active
host.

SpriteBatch runs keep `libfdx.benchmark.seconds` as the total render-loop duration
(default 8 seconds). `libfdx.benchmark.warmupSeconds` excludes the first 2 seconds
from measured results; set it to 0 to disable warm-up. Use a total duration long
enough to leave many complete measured frames. Runs without a complete interval
after warm-up cannot be ranked by the aggregate report.

Raw properties retain the whole-run `averageFrameFps` for compatibility. The
report ranks `measuredFrameFps`, using render-start-to-render-start intervals
after warm-up, including presentation and pacing. Matching `cpuRender` timings
cover work through `batch.end()` and exclude subsequent presentation and logging;
they are CPU wall times, not GPU timings. The final render has no following
interval and is omitted from both measured distributions. Resource loading before
the first render is outside these timings.

Results include p50/p95/p99, worst duration, counts of hitches over 50 ms, and
bounded histograms with 32 subdivisions per power of two. Percentiles use bucket
upper bounds with less than 3.125% bucket error, capped by the observed maximum;
displayed milliseconds are rounded to two decimal places. Histograms preserve
all measured counts as `upperBoundNanos:count` pairs, with no allocation while
recording. Periodic benchmark logging remains part of the measured frame interval.

Resolution, random seed, workload path, Java, OS, provider, and run controls are
recorded. Desktop JVM tasks also forward optional `libfdx.benchmark.device`,
`libfdx.benchmark.driver`, and `libfdx.benchmark.revision` labels. Supply verified
values when collecting a comparison; missing labels remain `unspecified`, and an
unavailable renderer diagnostic is recorded as such. Repeat comparable runs
before attributing performance differences to an implementation change.

## Desktop C

Run the aggregate debug or release benchmark with:

```powershell
.\gradlew.bat :benchmark:platform:desktop_c:benchmark_desktop_c_debug
.\gradlew.bat :benchmark:platform:desktop_c:benchmark_desktop_c_release
```

Native execution requires the matching host toolchain. On Windows, desktop C
run tasks may open a separate console; inspect the task/build source for the
current inline/headless option and generated report location.
