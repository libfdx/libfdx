# libFDX Builders

Builders create platform projects, packages, and generated assets. They are
build-time tools; game code still uses the provider-neutral runtime APIs.

Use [BUILDING.md](BUILDING.md) for repository setup and [SAMPLES.md](SAMPLES.md)
for runnable examples.

## Topics

- [1. Choose the Integration](#1-choose-the-integration)
- [2. Gradle Plugin](#2-gradle-plugin)
- [3. Bitmap Font Generation](#3-bitmap-font-generation)
- [4. Standalone Java Builders](#4-standalone-java-builders)
- [5. Project Generator](#5-project-generator)

## 1. Choose the Integration

| Need | Use |
| --- | --- |
| A Gradle game project with named platform tasks | libFDX Gradle plugin |
| A tool/editor exporting a browser build | `WebBuilder` |
| A custom pipeline generating TeaVM C desktop output | `NativeBuilder` |
| A custom pipeline generating a PSP project/EBOOT layout | `PspBuilder` |
| A new starter project from UI/settings | project generator |

Builders do not install platform toolchains. Android SDK, Emscripten, CMake,
PSPDEV, Xcode, and native compilers must exist when required by the selected
target.

## 2. Gradle Plugin

The plugin ID is `io.github.libfdx`. Published dependency mode resolves the
plugin from Maven; local mode uses the isolated build at
`libfdx/tools/gradle-plugin`. See
[dependency mode](BUILDING.md#3-dependency-mode).

Apply the plugin to a launcher/export project and configure one or more targets:

```kotlin
plugins {
    id("io.github.libfdx") version libfdxVersion
}

libfdx {
    assets(layout.projectDirectory.dir("assets"))
    desktopJvm {
        mainClass.set("com.example.desktop.GameLauncher")
        target("gl") {
            displayName.set("GL")
            systemProperty("libfdx.sample.graphics", "gl")
        }
    }
}
```

The plugin generates task names from the platform and optional target name:

| Block | Named-target task pattern |
| --- | --- |
| `desktopJvm` | `libfdx_desktop_jvm_<name>_{build,run}` |
| `js` | `libfdx_web_js_<name>_{build,run}` |
| `wasm` | `libfdx_web_wasm_<name>_{build,run}` |
| `desktopC` | `libfdx_desktop_c_<name>_{generate,build,run}_{debug,release}` |
| `psp` | `libfdx_psp_<name>_{generate,build,ppsspp_capture}` |
| `iosC` | `libfdx_ios_c_<name>_generate` |

An unnamed desktop JVM target also receives
`libfdx_desktop_jvm_{build,run}`. Once explicit named targets are declared, use
their named tasks.

Target blocks configure generation and launch behavior; they do not add libFDX
backend artifacts to the project. Declare the backend/provider dependencies the
launcher uses. Desktop JVM packaging includes the normal `runtimeClasspath`.

When one module declares several TeaVM C target families, request tasks from
only one family in a Gradle invocation. The requested target determines TeaVM's
C configuration.

### iOS C

An iOS C target names its launcher, bundle ID, output name, and graphics API:

```kotlin
dependencies {
    implementation("io.github.libfdx:backend_ios_c:$libfdxVersion")
}

libfdx {
    iosC {
        bundleIdentifier.set("com.example.game")
        target("metal") {
            mainClass.set("com.example.ios.GameLauncher")
            targetFileName.set("game-ios")
            graphicsApi.set("metal")
        }
    }
}
```

`gles` generates a GLKit/OpenGLES project; `metal` generates a native
Metal/MetalKit project. Open the generated Xcode project on macOS for simulator
or device builds.

Repository plugin DSL coverage lives in `:samples:basic:platform:plugin` and
`:tests:platform:plugin`. Ordinary runtime launcher modules do not apply the
plugin merely to reuse builder classes.

## 3. Bitmap Font Generation

`bitmapFont("name")` registers the visible task
`libfdx_bitmap_font_<name>`. The hidden aggregate is
`libfdx_generate_bitmap_fonts`.

Font generation is an explicit source-asset authoring step. It does not run as
a side effect of every platform build and generated files are not automatically
added to `libfdx.assets`.

```kotlin
libfdx {
    bitmapFont("ui_24") {
        sourceFile.set(layout.projectDirectory.file("assets/font/ui.ttf"))
        outputDir.set(layout.projectDirectory.dir("assets"))
        assetPath.set("font/bitmap")
        size.set(24)
        maxTextureSize.set(512)
    }
}
```

Choose the output directory deliberately, run the per-font task, then manage the
generated bitmap assets through the project's normal asset workflow.

## 4. Standalone Java Builders

Standalone builders expose the same maintained templates without requiring the
Gradle plugin:

| Builder | Output |
| --- | --- |
| `WebBuilder.javascript()` / `.wasm()` | TeaVM webapp, copied assets, preload metadata, browser shell |
| `NativeBuilder.desktop()` | TeaVM C output and desktop CMake project |
| `PspBuilder.psp()` | TeaVM C output, PSP assets, CMake/scripts, release layout |

Typical web export:

```java
WebBuilder.javascript()
    .mainClass("com.example.EditorLauncher")
    .classpathFromCurrentJvm()
    .asset(Path.of("assets"))
    .webappDirectory(Path.of("build/editor-web"))
    .fillWindow()
    .build();
```

The caller owns classpath selection, native-resource classpaths where required,
assets, and output location. PSP native building requires PSPDEV/psp-cmake; the
generated project documents its platform handoff.

## 5. Project Generator

The project generator is split by responsibility:

- `core` validates settings and creates an in-memory file tree;
- `ui` owns shared UI and delegates export;
- `platform/desktop` writes that tree to a selected directory;
- `platform/web` downloads the same tree as an archive.

Run the desktop UI:

```powershell
./gradlew :libfdx:tools:project-generator:platform:desktop:project_generator_desktop_gl_run
```

Run a web UI:

```powershell
./gradlew :libfdx:tools:project-generator:platform:web:project_generator_webgl_js_run
./gradlew :libfdx:tools:project-generator:platform:web:project_generator_webgpu_js_run
```

WebGPU uses JavaScript because the substituted jWebGPU path is not currently
compatible with TeaVM WasmGC. Generator core remains independent from desktop
filesystem APIs so desktop and browser export stay equivalent.
