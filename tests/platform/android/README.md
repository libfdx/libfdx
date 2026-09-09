# Android graphics tests

Run against the connected device or active emulator. The launch tasks install
the test APK and start the requested activity; they do not create an AVD.
Use `ANDROID_SERIAL` when more than one device is connected.

```powershell
adb devices -l
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :tests:platform:android:libfdx_android_vulkan_run '-Dlibfdx.test.name=Fog3DTest' '-Dlibfdx.test.frames=240'
```

The regular debug APK includes x86_64 for emulators as well as ARM ABIs.
No emulator-specific Gradle init script is required.

## Distinguishing WGPU from its graphics backend

The WGPU activity accepts `libfdx.test.wgpuBackend=default|vulkan|gles`.
`default` preserves automatic adapter selection. An explicit value restricts
the requested adapter backend, so a Vulkan diagnostic cannot silently pass
using a GL adapter. This setting only affects the WGPU test activity.

`libfdx.test.wgpuLoader=WGPU|DAWN` selects the native implementation. Pass it to
Gradle when building and launching: both implementations name their Android
library `libjWebGPU.so`, so the test APK packages only the selected implementation.
The default is `WGPU`. Setting an activity extra alone cannot switch that library.
For Dawn, request Vulkan explicitly; the tested Dawn Android publication has no
GLES adapter. Dawn/Vulkan shader preparation uses native async pipeline creation.

```powershell
.\gradlew.bat :tests:platform:android:libfdx_android_wgpu_jni_run '-Dlibfdx.test.wgpuLoader=DAWN' '-Dlibfdx.test.wgpuBackend=vulkan' '-Dlibfdx.test.name=MultipleShadersTest' '-Dlibfdx.test.shaderAsync=true' '-Dlibfdx.test.shaderCount=128' '-Dlibfdx.test.frames=20'
```

```powershell
.\gradlew.bat :tests:platform:android:libfdx_android_wgpu_jni_run '-Dlibfdx.test.name=Fog3DTest' '-Dlibfdx.test.wgpuBackend=vulkan' '-Dlibfdx.test.frames=240' '-Dlibfdx.test.captureFrame=90' '-Dlibfdx.test.capture=fog3d.ppm'
```

Relative capture paths are placed in the app's private files directory and can
be read using `adb exec-out run-as io.github.libfdx.tests.android cat files/fog3d.ppm`.
Preserve binary output when saving the PPM on the host.

A successful Gradle launch alone does not prove rendering completed. Check the
app's runtime log for the selected provider, requested WGPU backend, and the
expected `rendered ... frames` message, then inspect a captured frame.

## Vulkan reported-loss regression

`AndroidVulkanDeviceLossTest` requires the explicitly enabled debug JNI test build:

```powershell
.\gradlew.bat :tests:platform:android:libfdx_android_vulkan_run '-PlibfdxVulkanLossTests=true' '-Dlibfdx.test.name=AndroidVulkanDeviceLossTest' '-Dlibfdx.test.vulkanLoss=cache' '-Dlibfdx.test.shaderWorkers=1' '-Dlibfdx.test.shaderCacheDirectory=vulkan-loss-test'
```

The executable test defines the supported `vulkanLoss` cases. Cache cases need a
cache directory; use one worker for deterministic queued-source checks. Inspect
`ANDROID_VULKAN_LOSS PASS` and subsequent teardown logs. The fixture substitutes
Vulkan return codes and leaves the GPU healthy; it does not validate actual device
loss. The injection hooks are excluded by default, and release builds reject the
option. Omit `-PlibfdxVulkanLossTests=true` when rebuilding the normal test APK.

## WGPU native loss regression

`WGPUAndroidDeviceLossTest` uses the published JNI bindings. It destroys the
WebGPU device. Dawn delivers its callback through event processing; wgpu-native
requires an unused native command encoder to exercise its reported-loss callback.
The test verifies the actual native implementation matches the requested loader.
It does not submit work after destruction
or reset the host GPU. This is a real native `Destroyed` notification, distinct
from the injected Vulkan return codes above and from physical driver failure.

```powershell
.\gradlew.bat :tests:platform:android:libfdx_android_wgpu_jni_run '-Dlibfdx.test.name=WGPUAndroidDeviceLossTest' '-Dlibfdx.test.wgpuBackend=vulkan' '-Dlibfdx.test.wgpuLoss=source' '-Dlibfdx.test.shaderWorkers=1'
```

Add `-Dlibfdx.test.wgpuLoader=DAWN` to build and test Dawn/Vulkan.
Use `wgpuBackend=gles` with the default wgpu-native loader for the GLES route.
Supported `wgpuLoss` cases are defined
by `WGPUAndroidDeviceLossChecks`; one worker makes held/queued-source checks
deterministic. Run each case in a fresh app process and check for both
`ANDROID_WGPU_NATIVE_LOSS NOTIFIED` and `ANDROID_WGPU_NATIVE_LOSS PASS`.
The notification marker alone does not prove cleanup succeeded. Retain native
crash logs as failed results. See
[WGPU-001](../../../docs/UNRESOLVED_ISSUES.md#wgpu-001-crash-releasing-an-acquired-frame-after-device-loss)
for the acquired-frame cleanup failure, affected versions and retest criteria.
Android Dawn/Vulkan has a separate surface cleanup crash, including without an
acquired frame: see
[WGPU-002](../../../docs/UNRESOLVED_ISSUES.md#wgpu-002-android-dawn-vulkan-surface-cleanup-crash-after-device-destruction).
A subsequent fresh-session render verifies recovery separately.
