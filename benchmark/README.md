# libFDX Benchmarks

Benchmarks exercise the current framework sources. `core` owns benchmark
cases/results; platform modules own launchers and generated reports. The plugin
module validates generated native benchmark tasks.

Establish correctness before using benchmark results as evidence. Compare
providers with the same scene, workload, duration, visibility, and frame-limit
settings.

## Desktop JVM

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
