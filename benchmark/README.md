# libFDX Benchmarks

Performance benchmarks for libFDX. These modules live in the main repository so
benchmark runs can stay aligned with local framework changes.

## Layout

- `core`: benchmark cases and result writing.
- `platform/desktop`: JVM desktop benchmark launchers and reports for GL, WGPU, and Vulkan.
- `platform/desktop_c`: TeaVM C desktop_c benchmark launchers and report task aliases for GL and Vulkan.
- `platform/plugin`: libFDX Gradle plugin wiring for generated desktop_c benchmark executables.
- `assets`: benchmark-owned assets loaded at runtime.

## Desktop Benchmarks

The desktop benchmark task runs the SpriteBatch stress benchmark across GL,
WGPU, and Vulkan with visible windows, vSync disabled, the frame limiter
disabled, 8191 rotating/scaling 32x32 sprites, and 8 seconds per provider:

```powershell
.\gradlew.bat :benchmark:platform:desktop:benchmark_desktop
```

The generated report is written to
`build/reports/benchmark/desktop-sprite-batch-stress.md`.

Individual provider tasks are also available:

```powershell
.\gradlew.bat :benchmark:platform:desktop:benchmark_sprite_batch_stress_gl_ffm
.\gradlew.bat :benchmark:platform:desktop:benchmark_sprite_batch_stress_gl_jni
.\gradlew.bat :benchmark:platform:desktop:benchmark_sprite_batch_stress_wgpu_jni
.\gradlew.bat :benchmark:platform:desktop:benchmark_sprite_batch_stress_wgpu_ffm
.\gradlew.bat :benchmark:platform:desktop:benchmark_sprite_batch_stress_vulkan_ffm
.\gradlew.bat :benchmark:platform:desktop:benchmark_sprite_batch_stress_vulkan_jni
```

## Desktop C Benchmarks

The desktop_c benchmark runs the same SpriteBatch stress benchmark through the
TeaVM C desktop backend:

```powershell
.\gradlew.bat :benchmark:platform:desktop_c:benchmark_desktop_c_gl_debug
.\gradlew.bat :benchmark:platform:desktop_c:benchmark_desktop_c_gl_release
.\gradlew.bat :benchmark:platform:desktop_c:benchmark_desktop_c_vulkan_debug
.\gradlew.bat :benchmark:platform:desktop_c:benchmark_desktop_c_vulkan_release
```

Aggregate tasks run both GL and Vulkan:

```powershell
.\gradlew.bat :benchmark:platform:desktop_c:benchmark_desktop_c_debug
.\gradlew.bat :benchmark:platform:desktop_c:benchmark_desktop_c_release
```

On Windows, desktop_c benchmark run tasks open a separate console by default.
Use `"-Plibfdx.desktopC.openConsole=false"` for inline/headless Gradle runs.

Generated reports are written under `build/reports/benchmark`.
