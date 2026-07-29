# Starter Project

A clean portable libFDX starting point that clears the screen and draws the
bundled libFDX logo.

Use this sample as the base for a new project. Application code and asset
loading live in `core`; each directory under `platform` contains only the
launcher and build wiring required by that platform.

The repository includes launchers for:

- desktop JVM with OpenGL, WGPU, Vulkan, and Direct3D 12;
- Android with OpenGL ES, WGPU, Vulkan, and Vulkan-to-OpenGL fallback;
- web with JavaScript WebGL/WebGPU and WebAssembly WebGL;
- desktop C with OpenGL; and
- iOS C with OpenGL ES and Metal.

For a quick desktop run from the libFDX repository:

```powershell
./gradlew :samples:base:starter-project:platform:desktop:libfdx_desktop_jvm_gl_run
```

Set `-Dlibfdx.sample.exitAfterFrames=120` when a bounded desktop smoke run is
needed.
