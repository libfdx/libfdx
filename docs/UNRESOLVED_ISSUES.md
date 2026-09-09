# Unresolved issues

Track confirmed problems affecting libFDX that remain unfixed. Each entry records
the affected version, observed behavior, investigation result and criteria for
retesting. A dependency update alone does not resolve an entry: record the new
version and validation results before marking it resolved. Keep resolved entries
with their resolution date and verified version for future regressions.

## WGPU-001: Crash releasing an acquired frame after device loss

| Field | Recorded value |
| --- | --- |
| Status | **Deferred — unresolved; awaiting an updated consumed wgpu-native release** |
| Last validated | 2026-09-21 |
| Affected dependency | Official `wgpu-native` 29.0.1.1, consumed through jWebGPU |
| Tested jWebGPU publication | Snapshot `20260920.222852` |
| Confirmed platforms | Android emulator: WGPU/Vulkan and WGPU/GLES; Windows direct native API: Vulkan and D3D12 |
| Revisit when | A consumed jWebGPU release includes an updated official wgpu-native implementation |

### Trigger and impact

An application acquires a surface frame, the device becomes lost, and cleanup
releases the acquired, unpresented texture. `wgpuTextureRelease` aborts the process
with `Parent device is lost`. Java exception handling cannot recover from this
native abort.

The regression tests deliberately destroy the native device after acquiring the
frame. This validates native `Destroyed` handling; it does not measure the
frequency of unexpected device loss or simulate a physical GPU reset. Normal
startup, rendering and shader preparation passed their tested Android cases.

Four Android cases remain failed: Vulkan and GLES, each with an open or ended
render pass while the frame remains acquired. The Android environment was a
Pixel_7 Android 16/API 36 x86_64 emulator with host graphics. Physical Android
devices and other native operating systems were not validated for this issue.
Do not infer Dawn or browser behavior from these wgpu-native results.

Separate validation on 2026-09-21 passed all seven browser WebGPU loss/recovery
cases on both TeaVM JavaScript and Wasm GC, including acquired frames with open
and ended render passes. Android Dawn/Vulkan failed with a different surface
cleanup crash, tracked as WGPU-002 below.

### Investigation result

The libFDX cleanup path and jWebGPU handle ownership/callback bridge were audited.
Moving surface unconfiguration after texture release did not fix any of the four
Android failures. That experimental change was removed.

A standalone Windows probe using the official public C API reproduced the same
abort without libFDX, jWebGPU, JNI/FFM, shaders or render passes. Releasing an
acquired texture while the device was healthy passed. Releasing after loss
failed with either cleanup order, including without triggering a loss callback.

The acquired texture carries an application-owned reference that must be
released. Skipping its release or moving the deliberate loss after cleanup is
not an accepted fix. No safe correction in our code has been established. Keep
official dependencies unchanged; no native fork or external issue report is
part of this tracking entry.

### Reproduction and retest

The executable regression is
[WGPUAndroidDeviceLossTest](../tests/platform/android/src/main/java/io/github/libfdx/tests/android/WGPUAndroidDeviceLossTest.java),
with checks in
[WGPUAndroidDeviceLossChecks](../tests/platform/android/src/main/java/io/github/libfdx/graphics/wgpu/WGPUAndroidDeviceLossChecks.java).
See the [Android test instructions](../tests/platform/android/README.md#wgpu-native-loss-regression)
for launching and interpreting native-loss results.

From the repository root, with an Android device or emulator available:

```powershell
adb devices -l
.\gradlew.bat :tests:platform:android:libfdx_android_wgpu_jni_run '-Dlibfdx.test.name=WGPUAndroidDeviceLossTest' '-Dlibfdx.test.wgpuBackend=vulkan' '-Dlibfdx.test.wgpuLoss=open-pass' '-Dlibfdx.test.shaderWorkers=1'
```

Repeat in fresh application processes for both `wgpuBackend=vulkan` and `gles`,
using both `wgpuLoss=open-pass` and `ended-pass`. Keep crash results as failures;
the `NOTIFIED` marker alone does not establish successful cleanup.

Before closing this entry:

- [ ] Record the actual resolved jWebGPU artifacts and embedded wgpu-native version.
- [ ] All four acquired-frame Android cases emit `ANDROID_WGPU_NATIVE_LOSS PASS`
      and finish cleanup without an abort, hang or skipped resource release.
- [ ] Recheck owned texture/view/surface/device references and callback retirement;
      disappearance of the crash alone does not prove absence of leaks.
- [ ] No-frame loss controls, ordinary rendering and async shader preparation pass.
- [ ] Recheck the direct native Windows Vulkan/D3D12 reproduction.
- [ ] Record tested hardware, remaining platform gaps and the resolution date.

Local investigation artifacts are optional, ignored build output and may be
absent from a fresh checkout: `build/wgpu-loss-ownership-audit/validation.md`
contains the ownership audit, commands, native-library hashes, direct C probe and
original-versus-candidate Android logs. `build/android-wgpu-native-loss/validation.md`
contains the original Android reproduction. The source regression and description
above remain usable without those artifacts.

## WGPU-002: Android Dawn Vulkan surface cleanup crash after device destruction

| Field | Recorded value |
| --- | --- |
| Status | **Deferred — unresolved; awaiting an updated consumed Dawn release** |
| Last validated | 2026-09-21 |
| Tested dependency | Published `webgpu-android-dawn` snapshot `20260920.222852-56` |
| Confirmed platform | Pixel_7 Android 16/API 36 x86_64 emulator, host graphics, Vulkan |
| Revisit when | A consumed jWebGPU release includes an updated Dawn implementation |

### Trigger and impact

Destroying a configured Dawn device delivers the native `Destroyed` callback.
Subsequent libFDX context disposal crashes with SIGSEGV in
`dawn::native::vulkan::SwapChain::PerImage::~PerImage()`, reached through
`SwapChain::DetachFromSurfaceImpl()` and `Surface::~Surface()`.

All five native-loss cases fail: held/queued source, completed unpublished
pipeline, published pipeline, acquired frame with an open pass, and acquired
frame with an ended pass. The first three do not acquire a frame. This is a
different failure from WGPU-001's wgpu-native texture-release abort.

The test verifies `WGPU.isDawnBackend()` and receives `Device was destroyed.`
before the crash. Successful notification alone does not mean cleanup passed.
Normal async rendering with 128 shaders passes. A separate 128-request run with
one deliberate invalid shader passes its expected 127-ready/1-failed assertions
and pixel checks. Those controls do not resolve the loss-cleanup failure.

### Investigation result

A standalone Android NativeActivity reproduced the crash by calling the exported
Dawn C API in the exact same published `libjWebGPU.so`. It uses no libFDX code,
JGPU wrappers, Java bindings, shaders, render passes or compilation workers.
The minimal failing sequence is: configure surface, destroy device, unconfigure
surface, release surface. The application still owns its device reference at
the crash.

The published binary clears the Vulkan device's internal deletion manager during
`wgpuDeviceDestroy`. The surface retains its swapchain resources; releasing it
later reaches `SwapChain::PerImage` destruction, which dereferences the cleared
`GetFencedDeleter()` result. The crash address, zero register value and disassembly
confirm this mechanism in the tested binary.

Seven direct destruction variants crash, including with no acquired frame,
texture release before unconfiguration, no explicit unconfiguration, prior
unconfiguration, no requested implicit synchronization and no loss callback.
Four controls pass: healthy cleanup with/without an acquired frame, a surface
never configured, and full surface release before deliberate device destruction.
Prior unconfiguration alone does not prevent the crash; releasing the surface
before triggering loss changes the failure condition and is not a loss-recovery
fix. Skipping release would retain owned resources.

libFDX already releases its surface before destroying/releasing its owned device
during normal disposal. The loss test deliberately destroys the device earlier.
Its resource domain retains the device until contexts and preparation jobs drain;
the inspected wrapper release/unconfigure methods forward to the corresponding
C API calls. No production cleanup change is justified by this reproduction.

There is a significant distinction between loss and destruction: Dawn's existing
native diagnostic `wgpuDeviceForceLoss` hook, using reason `Unknown`, permits
cleanup without early device destruction. Direct tests pass with/without an
acquired frame. A temporary test-only JNI adapter to that hook also passes all
five libFDX lifecycle cases with unchanged production cleanup, including open
and ended acquired-frame passes, cancellation and ownership assertions. Calling
`wgpuDeviceDestroy` after the forced loss but before surface release reproduces
the crash again. These controlled diagnostics do not simulate a physical GPU
reset, and their passes do not replace the five failed destruction cases. The
adapter stays in ignored investigation artifacts; it is not a new runtime API.

No Dawn source change,
binding patch, skipped release or external issue report is part of this result.
Physical Android hardware, other Android ABIs and unexpected driver resets were
not tested. The tested Dawn Android package rejects GLES with `No supported adapters`.

### Reproduction and retest

Use the shared Android native-loss regression and
[launch instructions](../tests/platform/android/README.md#wgpu-native-loss-regression):

```powershell
adb devices -l
.\gradlew.bat :tests:platform:android:libfdx_android_wgpu_jni_run '-Dlibfdx.test.wgpuLoader=DAWN' '-Dlibfdx.test.wgpuBackend=vulkan' '-Dlibfdx.test.name=WGPUAndroidDeviceLossTest' '-Dlibfdx.test.wgpuLoss=unpublished' '-Dlibfdx.test.shaderWorkers=1'
```

Repeat in fresh processes for every supported `wgpuLoss` case. Before closing:

- [ ] Record the resolved Dawn publication and APK native-library hash.
- [ ] All five cases report `ANDROID_WGPU_NATIVE_LOSS PASS` and complete cleanup.
- [ ] Device, callback, context, surface and pending-result ownership checks pass
      without intentionally retaining native resources to hide the crash.
- [ ] Both normal preparation controls still render correctly.
- [ ] Retest native forced-loss diagnostics separately from device destruction;
      neither substitutes for physical driver-loss coverage.
- [ ] Record hardware/ABI coverage and any remaining limitations.

Ignored local evidence: `build/dawn-android-web-loss/validation.md`, resolved
artifact manifests, per-case native logs, Android rendering captures and browser
screenshots. The source regression and instructions remain usable without these
build artifacts. The follow-up ownership audit, direct C API probe, native binary
disassembly, cleanup-order controls and temporary forced-loss diagnostics are in
`build/dawn-loss-ownership-audit/validation.md`.
