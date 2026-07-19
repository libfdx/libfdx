# Testing libFDX

This guide maps a change to the smallest useful validation and lists the public
platform entry tasks. It is not a catalog of every test screen or system
property; source and Gradle task help are authoritative for those details.

Contributors validating checked-out framework changes should first enable
[local dependency mode](BUILDING.md#3-dependency-mode).

## Topics

- [1. Choose the Validation Scope](#1-choose-the-validation-scope)
- [2. Shared Runtime Test Selection](#2-shared-runtime-test-selection)
- [3. Platform Entry Tasks](#3-platform-entry-tasks)
- [4. Visual and Graphics Validation](#4-visual-and-graphics-validation)
- [5. Benchmarks](#5-benchmarks)

## 1. Choose the Validation Scope

Start with the narrowest target that proves the changed behavior:

| Change | First evidence |
| --- | --- |
| Pure Java leaf module | Its unit-test task |
| Sample/launcher option | Its directly affected build or finite run |
| Backend/provider/runtime wiring | Relevant compile plus provider run |
| Shared graphics, UI, font, texture, shader, or readback | Focused rendered scenario, then affected provider matrix |
| Gradle task/plugin wiring | The generated/public task in local and relevant dependency mode |
| Documentation only | Link/stale-term checks and `git diff --check` |

Expand validation when a change crosses modules, providers, platforms, public
task wiring, or shared runtime behavior. Do not run unrelated targets merely
because they exist. A missing SDK, device, toolchain, runtime, or supported API
is reported as an exact blocker, never as a pass.

## 2. Shared Runtime Test Selection

Platform launchers share system properties such as:

- `-Dlibfdx.test.name=<name>` selects a registered test (`ui`, `sprite`,
  `texture`, `model`, `readback`, and others are defined in source);
- `-Dlibfdx.test.frames=<n>` makes a run finite;
- `-Dlibfdx.test.validate=true` enables runtime assertions;
- `-Dlibfdx.test.driveInput=true` enables deterministic synthetic input where
  supported;
- `-Dlibfdx.test.capture=<path>` requests a capture;
- `-Dlibfdx.test.visible=false` allows a hidden desktop run;
- `-Dlibfdx.test.mode=auto` cycles registered tests.

Scenario validation adds `-Dlibfdx.validation.*` values for scenario selection,
mode, capture policy, timeout, event output, and step delay. The semantic model
is documented in [SCENARIO_VALIDATOR.md](SCENARIO_VALIDATOR.md).

Example focused desktop run:

```powershell
./gradlew "-Dlibfdx.test.name=ui" "-Dlibfdx.test.frames=30" "-Dlibfdx.test.validate=true" "-Dlibfdx.test.driveInput=true" :tests:platform:desktop:test_desktop_gl_run
```

Consult the selected test launcher/config source before using a property not
listed here. Do not infer an option from a similar platform.

## 3. Platform Entry Tasks

### Desktop JVM

```powershell
./gradlew :tests:platform:desktop:test_desktop_gl_run
./gradlew :tests:platform:desktop:test_desktop_wgpu_run
./gradlew :tests:platform:desktop:test_desktop_vulkan_run
./gradlew :tests:platform:desktop:test_desktop_d3d12_run
```

The Direct3D 12 task is Windows x64-only. Add
`-Dlibfdx.validation.d3d12=true` when debug-layer validation is required.

Desktop windows start maximized. Set `-Dlibfdx.test.maximized=false` for the
configured test size. Width/height, visibility, safe area, UI scale, and frame
rate diagnostic properties are launcher options; use them only when relevant to
the scenario.

Recorded-command resource changes should include a focused WGPU, Vulkan, or
Direct3D 12 run, even if GL passes, because delayed submission exposes lifetime
hazards that immediate GL execution may hide.

### Desktop C

```powershell
./gradlew :tests:platform:desktop_c:test_desktop_c_opengl_run_debug
./gradlew :tests:platform:desktop_c:test_desktop_c_vulkan_run_debug
```

Generate/build variants use the same prefix with `generate_debug`,
`build_debug`, `generate_release`, or `build_release`. Native test arguments can
be supplied through `libfdx.desktopC.runArgs` when the task source declares
them, for example:

```powershell
./gradlew "--project-prop=libfdx.desktopC.runArgs=--test=mesh-basic --frames=4" :tests:platform:desktop_c:test_desktop_c_opengl_run_debug
```

### Android

First check the actual device state through the SDK/PATH used by the repository:

```powershell
adb devices -l
```

When at least one entry is in `device` state, Android execution is available.
Use the run tasks, not `assembleDebug` alone, for runtime or visual evidence:

```powershell
./gradlew :tests:platform:android:test_android_gles_run
./gradlew :tests:platform:android:test_android_wgpu_jni_run
./gradlew :tests:platform:android:test_android_vulkan_run
```

Each task builds, installs, and launches the matching activity. Pass
`-Dlibfdx.test.*` and `-Dlibfdx.validation.*` values to Gradle; the Android build
forwards them as intent extras. Classify failures by their real stage:
dependency resolution, compilation, install, activity launch, runtime crash, or
capture--not generically as "emulator unavailable."

### Web

```powershell
./gradlew :tests:platform:web:test_webgl_js_run
./gradlew :tests:platform:web:test_webgl_wasm_run
./gradlew :tests:platform:web:test_webgpu_js_run
```

WebGPU validation uses JavaScript. TeaVM WasmGC cannot currently compile the
substituted JS-native jWebGPU path, so Wasm validation uses WebGL.

### PSP

```powershell
./gradlew :tests:platform:psp:test_psp_generate
./gradlew :tests:platform:psp:test_psp_build
./gradlew :tests:platform:psp:test_psp_ppsspp_capture
```

Generation proves Java-to-C/project output, build requires PSPDEV, and capture
requires PPSSPP plus the repository's capture setup. Report each stage
separately.

## 4. Visual and Graphics Validation

A successful task is not enough for visual work. Capture or inspect a real
rendered frame and connect the observed result to the mechanism changed.

### 4.1 Scope the matrix

For a local screen/widget/sample change, validate the directly affected
platform/API first. For shared graphics/UI output or requested provider parity,
desktop requires GL, Vulkan, and WGPU, plus the Java 25 FFM Direct3D 12 provider
on Windows x64. Add other platforms/APIs only when they are affected or
explicitly requested.

Use the same scene, viewport, scale, assets, input sequence, timing, and frame
count across comparisons. Record every required cell as:

- `PASS` with evidence;
- `BLOCKED` with the exact unavailable dependency/environment;
- `NOT RUN` with the concrete scope reason.

### 4.2 Establish the reference

1. Describe the symptom precisely: missing/corrupt text, wrong color/alpha,
   wrong position/size, flipped texture, edge drift, whole-scene mismatch, or a
   provider-only failure.
2. Use a known-good provider--normally GL--as the deterministic baseline.
3. Capture expected, actual, and mismatch output when possible.
4. Inspect the images manually and record mismatch ratio, maximum channel
   difference, mismatch bounds, representative pixels when useful, and the
   failing scenario/frame.

Missing baselines, dimension/path mismatches, and visual mismatches are
failures. Comparison tasks must not silently create expected images.

### 4.3 Isolate the primitive

Before changing a broad renderer, reproduce the smallest relevant primitive:

- solid rectangle, then transparent/scrim rectangle;
- plain widget, then textured/nine-patch widget;
- one texture quad;
- one glyph/label, then several labels that force batching;
- instanced/batched path, then its fallback;
- readback-only comparison when live output looks correct.

Classify the failing layer before editing: layout/input, clipping, shapes,
sprites/textures, glyph atlas, upload/readback, coordinate convention,
batching/instancing/indexing, blend/load/store state, resource lifetime, or
surface-format conversion.

### 4.4 Recorded-command hazards

When GL is correct but Vulkan, Direct3D 12, or WGPU is not, check these before
changing layout:

- vertex/index/instance/uniform buffers overwritten after binding but before
  submission;
- blend and render-pass load/store state for transparent UI;
- row alignment, format conversion, mipmaps, samplers, and UV orientation;
- frame/resource lifetime and synchronization;
- readback row order, bytes-per-row alignment, surface format, and channel
  swizzle;
- viewport/rasterization conventions for one-pixel edge differences.

Do not hide a visible defect by only relaxing comparator thresholds.

### 4.5 Acceptance

A visual fix is accepted when the focused failure improves for the identified
mechanism, the affected matrix is accounted for, and visible text/widgets/layout
are coherent and readable. Any remaining tolerance must be justified by image
inspection and numbers.

For relevant UI text/input/overlay work, include the applicable UI Kit scenarios:
`slider-text`, `text-scale-slider`, `section-drawing`, text-size slider
interactions, `window-edge-tests`, `popup-pass-through`, and `open-modal`.
"Requested" captures are debug artifacts unless the plan explicitly makes them
parity assertions.

## 5. Benchmarks

Benchmarks measure performance after correctness is established. They are not
the first proof for a backend or rendering fix.

```powershell
./gradlew :benchmark:platform:desktop:benchmark_desktop
./gradlew :benchmark:platform:desktop_c:benchmark_desktop_c_debug
```

Provider-specific tasks and report locations are listed in the
[benchmark README](../benchmark/README.md). Keep the same scene, sprite count,
duration, visibility, and frame-limiter settings when comparing providers.
