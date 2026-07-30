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

## Desktop C

Run the aggregate debug or release benchmark with:

```powershell
.\gradlew.bat :benchmark:platform:desktop_c:benchmark_desktop_c_debug
.\gradlew.bat :benchmark:platform:desktop_c:benchmark_desktop_c_release
```

Native execution requires the matching host toolchain. On Windows, desktop C
run tasks may open a separate console; inspect the task/build source for the
current inline/headless option and generated report location.
