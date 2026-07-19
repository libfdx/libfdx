# Running the Samples

Samples are executable user-facing examples. A clean checkout uses published
libFDX snapshot artifacts, so most examples can run without compiling the
framework first. Contributors testing checked-out framework changes should
select local mode as described in [BUILDING.md](BUILDING.md#3-dependency-mode).

Commands below use the repository root and the Gradle wrapper. On Windows,
replace `./gradlew` with `./gradlew.bat` if needed.

## Topics

- [1. Basic Sample](#1-basic-sample)
- [2. ECS Platformer](#2-ecs-platformer)
- [3. WebRTC Multiplayer](#3-webrtc-multiplayer)
- [4. Project Generator and Plugin Targets](#4-project-generator-and-plugin-targets)

## 1. Basic Sample

### Desktop JVM

```powershell
./gradlew :samples:basic:platform:desktop:basic_desktop_gl_run
./gradlew :samples:basic:platform:desktop:basic_desktop_wgpu_run
./gradlew :samples:basic:platform:desktop:basic_desktop_vulkan_run
./gradlew :samples:basic:platform:desktop:basic_desktop_d3d12_run
```

Desktop windows start maximized. Use
`-Dlibfdx.sample.maximized=false` to use the configured size.
The Direct3D 12 task requires Windows x64 and Java 25 native access. It uses the
Windows system Direct3D libraries through FFM; there is no provider DLL to build.

### Android

With an Android device or emulator connected:

```powershell
./gradlew :samples:basic:platform:android:basic_android_gles_run
./gradlew :samples:basic:platform:android:basic_android_wgpu_jni_run
./gradlew :samples:basic:platform:android:basic_android_vulkan_run
./gradlew :samples:basic:platform:android:basic_android_vulkan_fallback_run
```

These tasks build, install, and launch the matching application.

### Web

```powershell
./gradlew :samples:basic:platform:web:basic_webgl_js_run
./gradlew :samples:basic:platform:web:basic_webgl_wasm_run
./gradlew :samples:basic:platform:web:basic_webgpu_js_run
```

WebGPU currently uses the JavaScript target. TeaVM WasmGC cannot compile the
substituted JS-native jWebGPU path; use WebGL for the Wasm target. A non-positive
configured width or height makes the canvas fill the browser window.

### Desktop C and iOS C

```powershell
./gradlew :samples:basic:platform:desktop_c:basic_desktop_c_opengl_run_debug
./gradlew :samples:basic:platform:ios_c:basic_ios_c_gles_generate
./gradlew :samples:basic:platform:ios_c:basic_ios_c_metal_generate
```

Desktop C requires its native toolchain. iOS generation can run on other hosts,
but building/running the generated Xcode project requires macOS and Xcode. The
Xcode output is under `samples/basic/platform/plugin/build/dist/ios-c/xcode`.

## 2. ECS Platformer

The platformer demonstrates the optional ECS module, common input, cameras, and
SpriteBatch rendering with CC0 Kenney assets.

```powershell
./gradlew :samples:ecs-platformer:core:test
./gradlew :samples:ecs-platformer:platform:desktop:libfdx_desktop_jvm_gl_run
./gradlew :samples:ecs-platformer:platform:desktop:libfdx_desktop_jvm_wgpu_run
./gradlew :samples:ecs-platformer:platform:desktop:libfdx_desktop_jvm_vulkan_run
./gradlew :samples:ecs-platformer:platform:desktop:libfdx_desktop_jvm_d3d12_run
./gradlew :samples:ecs-platformer:platform:web:libfdx_web_js_webgl_run
```

Move with A/D or Left/Right, jump with Space/Up/W/click/tap, and restart with R
or Enter. See the [sample README](../samples/ecs-platformer/README.md) for its
code organization and capture command.

## 3. WebRTC Multiplayer

Start the signaling server in one terminal:

```powershell
./gradlew :libfdx:extensions:net:webrtc:signaling_server:webrtc_signaling_server_run
```

Then start two clients in separate terminals:

```powershell
./gradlew -Dlibfdx.sample.playerName=Host -Dlibfdx.sample.autoHost=true -Dlibfdx.sample.hostRoomId=test-room :samples:multiplayer:2d-webrtc:platform:desktop:multiplayer_2d_webrtc_desktop_wgpu_run
./gradlew -Dlibfdx.sample.playerName=Client -Dlibfdx.sample.autoJoinRoom=test-room :samples:multiplayer:2d-webrtc:platform:desktop:multiplayer_2d_webrtc_desktop_wgpu_run
```

The default signaling endpoint is `ws://127.0.0.1:7777`. Server host, port,
tick rate, and queue limits are configurable with `libfdx.webrtc.signaling.*`
system properties. Web clients use:

```powershell
./gradlew :samples:multiplayer:2d-webrtc:platform:web:multiplayer_2d_webrtc_webgl_js_run
```

Pass a `signaling` query parameter when the server is not at the default local
endpoint.

## 4. Project Generator and Plugin Targets

The project-generator UI and the complete generated-task naming rules live in
[BUILDERS.md](BUILDERS.md). Use the sample modules above to learn runtime APIs;
use the plugin-use modules when validating the Gradle plugin itself.
