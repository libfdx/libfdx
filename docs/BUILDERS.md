# libFDX Builders

This document explains the build-time tools that generate libFDX platform
outputs. These tools are separate from the runtime API: game code still uses the
common API modules, while builders prepare assets, generated files, and platform
project shells.

Use the Gradle plugin when an external Gradle project wants libFDX-managed
platform tasks. Use the standalone Java builders when a runtime launcher,
tool, editor, or custom pipeline needs to generate a web, desktop-c, or
PSP project without applying the Gradle plugin.

## Index

- [1. Choosing A Builder](#1-choosing-a-builder)
- [2. Gradle Plugin](#2-gradle-plugin)
- [3. Bitmap Font Tasks](#3-bitmap-font-tasks)
- [4. Standalone Web Builder](#4-standalone-web-builder)
- [5. Standalone Desktop C Builder](#5-standalone-desktop-c-builder)
- [6. Standalone PSP Builder](#6-standalone-psp-builder)
- [7. Project Generator](#7-project-generator)

## 1. Choosing A Builder

The builders all serve the same general purpose: take a Java entry point,
classpath, assets, and platform options, then produce the generated output that
the target platform needs.

- Gradle plugin: best for external Gradle game projects and the dedicated
  plugin-use sample/test modules.
- `WebBuilder`: best for editor/export flows that need a browser webapp output.
- `NativeBuilder`: best for custom desktop-c TeaVM C output generation.
- `PspBuilder`: best for custom PSP EBOOT project generation.

The builder should not hide platform requirements. If a target needs a native
toolchain, Android SDK, PSPDEV, CMake, or browser-specific setup, the generated
project still depends on those tools being present.

Inside this repository, runtime launcher modules keep their Gradle files small
and do not apply the libFDX Gradle plugin. Dedicated plugin-use modules own the
plugin-managed generated targets: `:samples:basic:platform:plugin` and
`:tests:platform:plugin`.

## 2. Gradle Plugin

The libFDX Gradle plugin lives in `libfdx/tools/gradle-plugin` and is consumed
through an included build. This repository keeps plugin DSL usage in
`:samples:basic:platform:plugin` and `:tests:platform:plugin`; external builds
can use the same included build shape:

```kotlin
pluginManagement {
    includeBuild("<libfdx>/libfdx/tools/gradle-plugin")
}
```

Apply it with `id("io.github.libfdx")` and configure targets inside
`libfdx { ... }`. Do not apply the plugin only to reuse the standalone builder
classes; call the builders directly instead.

The plugin target blocks are:

- `desktopJvm { ... }`: creates `libfdx_desktop_jvm_build` and
  `libfdx_desktop_jvm_run` when no explicit target is declared. Each
  `target("name")` entry creates `libfdx_desktop_jvm_<name>_build` and
  `libfdx_desktop_jvm_<name>_run`, with no default desktop JVM tasks.
- `js { ... }`: creates TeaVM JavaScript web output tasks. Add
  `target("name")` entries for public `libfdx_web_js_<name>_build` and
  `libfdx_web_js_<name>_run` tasks.
- `wasm { ... }`: creates TeaVM Wasm web output tasks. Add `target("name")`
  entries for public `libfdx_web_wasm_<name>_build` and
  `libfdx_web_wasm_<name>_run` tasks.
- `desktopC { ... }`: creates TeaVM C desktop-c generate, build, and
  run tasks. Add `target("name")` entries for public
  `libfdx_desktop_c_<name>_generate_debug`,
  `libfdx_desktop_c_<name>_generate_release`,
  `libfdx_desktop_c_<name>_build_debug`,
  `libfdx_desktop_c_<name>_build_release`,
  `libfdx_desktop_c_<name>_run_debug`, and
  `libfdx_desktop_c_<name>_run_release` tasks.
  Each target may set `mainClass` and `targetFileName` for target-specific
  native apps such as OpenGL and Vulkan launchers.
- `psp { ... }`: creates TeaVM C PSP generate, build, and PPSSPP capture tasks.
  Add `target("name")` entries for public `libfdx_psp_<name>_generate`,
  `libfdx_psp_<name>_build`, and `libfdx_psp_<name>_ppsspp_capture` tasks.
- `iosC { ... }`: creates TeaVM C iOS output and a generated Xcode project.
  Add `target("name")` entries for public `libfdx_ios_c_<name>_generate`
  tasks. The task writes C output, copied assets, native resources, Swift
  sources, and an `.xcodeproj` under `build/dist/ios-c` by default. Open the
  generated Xcode project on macOS with Xcode for simulator/device builds.
  Each target may set `graphicsApi` to `gles` or `metal`. The `gles` value
  writes a GLKit/OpenGLES project. The `metal` value writes a native
  Metal/MetalKit project backed by libFDX's Metal provider and generated native
  bridge.

The plugin does not add libFDX backend dependencies to a user project. A
launcher module that configures `iosC` must still declare its own
`implementation("io.github.libfdx:backend_ios_c:<version>")` dependency, just
as desktop-c and PSP launchers declare their backend artifacts explicitly.

When one plugin-use module declares multiple TeaVM C targets such as
`desktopC`, `psp`, and `iosC`, the plugin selects the TeaVM C target from the
requested target task. Do not request tasks from different TeaVM C target
families in the same Gradle invocation.

Example desktop JVM target:

```kotlin
libfdx {
    desktopJvm {
        mainClass.set("com.example.desktop.BasicDesktopLauncher")
        target("gl") {
            displayName.set("GL")
            systemProperty("libfdx.sample.graphics", "gl")
            launchProperty("graphics", "gl")
        }
    }
}
```

If one Gradle project declares multiple platform targets, keep desktop provider
dependencies on the normal runtime classpath. The plugin packages
`runtimeClasspath` into the desktop JVM release jars.

Example iOS C target:

```kotlin
dependencies {
    implementation("io.github.libfdx:backend_ios_c:$libfdxVersion")
}

libfdx {
    iosC {
        bundleIdentifier.set("com.example.game")
        target("gles") {
            mainClass.set("com.example.ios.GameIosCLauncher")
            targetFileName.set("game-ios")
            graphicsApi.set("gles")
        }
        target("metal") {
            mainClass.set("com.example.ios.GameIosCMetalLauncher")
            targetFileName.set("game-metal-ios")
            graphicsApi.set("metal")
        }
    }
}
```

Generate the Xcode handoff project with:

```powershell
.\gradlew.bat libfdx_ios_c_gles_generate
.\gradlew.bat libfdx_ios_c_metal_generate
```

## 3. Bitmap Font Tasks

The plugin can register explicit bitmap-font generation tasks from TTF files. A
`bitmapFont("ui_24")` block creates `libfdx_bitmap_font_ui_24` and the
hidden aggregate `libfdx_generate_bitmap_fonts`; it does not run during target
builds and does not add generated files to `libfdx.assets`.

This is intentional. Bitmap font generation is a source-asset authoring step,
not an implicit build side effect. Developers choose when to regenerate the
bitmap font files, then commit or manage the resulting assets according to their
project workflow.

Set `outputDir` to the asset root when you intentionally want the task to write
source assets, then run the visible per-font task yourself:

```kotlin
libfdx {
    assets(layout.projectDirectory.dir("assets"))
    bitmapFont("ui_24") {
        sourceFile.set(layout.projectDirectory.file("assets/font/lsans.ttf"))
        outputDir.set(layout.projectDirectory.dir("assets"))
        assetPath.set("font/bitmap")
        size.set(24)
        maxTextureSize.set(512)
    }
}
```

## 4. Standalone Web Builder

Tools, runtime launcher modules, and editors can call the Java builders from
the owning backend modules:

```java
WebBuilder.javascript()
    .mainClass("com.example.EditorLauncher")
    .classpathFromCurrentJvm()
    .asset(Path.of("assets"))
    .bitmapFont(Path.of("assets/font/lsans.ttf"), "ui_24", 24)
    .webappDirectory(Path.of("build/editor-web"))
    .fillWindow()
    .build();
```

Use `WebBuilder.wasm()` for a Wasm webapp. The web builder compiles TeaVM,
copies assets into `webapp/assets`, writes generated asset metadata for preload,
and writes the same web shell used by the Gradle plugin.

Use this builder when an application needs to export a playable web build from
inside a custom tool or from explicit Gradle wiring. The caller is responsible
for passing the correct classpath and choosing the output directory.

## 5. Standalone Desktop C Builder

```java
NativeBuilder.desktop()
    .mainClass("com.example.EditorLauncher")
    .classpathFromCurrentJvm()
    .nativeResourceClasspathFromCurrentJvm()
    .outputDirectory(Path.of("build/editor-native"))
    .build();
```

The native builder compiles TeaVM C output and writes the same desktop-c
CMake project shell used by the Gradle plugin.

Use this builder when a non-Gradle pipeline needs the generated C output and
native project shell, but still wants to reuse the backend templates maintained
by libFDX.

## 6. Standalone PSP Builder

```java
PspBuilder.psp()
    .mainClass("com.example.PspLauncher")
    .classpathFromCurrentJvm()
    .nativeResourceClasspathFromCurrentJvm()
    .asset(Path.of("assets"))
    .bitmapFont(Path.of("assets/font/lsans.ttf"), "ui_24", 24)
    .outputDirectory(Path.of("build/editor-psp"))
    .build();
```

The PSP builder compiles TeaVM C output, copies declared assets into the
generated PSP release layout, and writes a PSP CMake/script project shell.
Building the EBOOT requires PSPDEV/psp-cmake on the native build machine. On
Windows, set `PSPDEV` to the Windows PSP toolchain path, for example
`E:\Dev\Env\Ubuntu\pspdev`; the generated `build.bat` converts it to a WSL path
before running `build.sh`.

## 7. Project Generator

The project generator lives under `libfdx/tools/project-generator` and is split
so generation logic can run on desktop or web:

- `core`: validates settings and returns an in-memory generated project tree.
- `ui`: shared UIKit screens and state; it delegates export to the platform.
- `platform/desktop`: LWJGL3 desktop launcher that writes files to disk.
- `platform/web`: browser launcher that packages the same generated project
  tree as a ZIP download.

Run the desktop generator UI with:

```powershell
.\gradlew.bat :libfdx:tools:project-generator:platform:desktop:project_generator_desktop_gl_run
```

Build the desktop generator UI release jar with:

```powershell
.\gradlew.bat :libfdx:tools:project-generator:platform:desktop:project_generator_desktop_gl_build
```

The jar is written under
`libfdx/tools/project-generator/platform/desktop/build/dist/desktop-jvm`.

Build or serve the web generator UI with:

```powershell
.\gradlew.bat :libfdx:tools:project-generator:platform:web:project_generator_webgl_js_build
.\gradlew.bat :libfdx:tools:project-generator:platform:web:project_generator_webgl_js_run
.\gradlew.bat :libfdx:tools:project-generator:platform:web:project_generator_webgpu_js_build
.\gradlew.bat :libfdx:tools:project-generator:platform:web:project_generator_webgpu_js_run
```

The web module also exposes `project_generator_webgl_wasm_build` and
`project_generator_webgl_wasm_run` for the Wasm target. WebGPU uses the
JavaScript tasks above because TeaVM WasmGC cannot currently compile the
substituted JS-native jWebGPU binding path.

The core generator must stay independent from filesystem APIs. Desktop export
writes generated files to a selected directory. Web export downloads an archive
instead of assuming the browser can write a folder tree directly.
