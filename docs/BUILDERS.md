# libFDX Builders

This document explains the build-time tools that generate libFDX platform
outputs. These tools are separate from the runtime API: game code still uses the
common API modules, while builders prepare assets, generated files, and platform
project shells.

Use the Gradle plugin when the project is built by Gradle. Use the standalone
Java builders when a tool, editor, or custom pipeline needs to generate a web,
desktop-native, or PSP project without applying the Gradle plugin.

## Index

- [1. Choosing A Builder](#1-choosing-a-builder)
- [2. Gradle Plugin](#2-gradle-plugin)
- [3. Bitmap Font Tasks](#3-bitmap-font-tasks)
- [4. Standalone Web Builder](#4-standalone-web-builder)
- [5. Standalone Desktop Native Builder](#5-standalone-desktop-native-builder)
- [6. Standalone PSP Builder](#6-standalone-psp-builder)

## 1. Choosing A Builder

The builders all serve the same general purpose: take a Java entry point,
classpath, assets, and platform options, then produce the generated output that
the target platform needs.

- Gradle plugin: best for normal game projects, samples, tests, and CI tasks.
- `WebBuilder`: best for editor/export flows that need a browser webapp output.
- `NativeBuilder`: best for custom desktop-native TeaVM C output generation.
- `PspBuilder`: best for custom PSP EBOOT project generation.

The builder should not hide platform requirements. If a target needs a native
toolchain, Android SDK, PSPDEV, CMake, or browser-specific setup, the generated
project still depends on those tools being present.

## 2. Gradle Plugin

The libFDX Gradle plugin lives in `libfdx/tools/gradle-plugin` and is consumed
through an included build. This repository wires it in from
`settings.gradle.kts`; external builds can do the same:

```kotlin
pluginManagement {
    includeBuild("<libfdx>/libfdx/tools/gradle-plugin")
}
```

Apply it with `id("io.github.libfdx")` and configure targets inside
`libfdx { ... }`.

## 3. Bitmap Font Tasks

The plugin can register explicit bitmap-font generation tasks from TTF files. A
`bitmapFont("ui_24")` block creates `libfdx_bitmap_font_ui_24` and the
aggregate `libfdx_generate_bitmap_fonts`; it does not run during target builds
and does not add generated files to `libfdx.assets`.

This is intentional. Bitmap font generation is a source-asset authoring step,
not an implicit build side effect. Developers choose when to regenerate the
bitmap font files, then commit or manage the resulting assets according to their
project workflow.

Set `outputDir` to the asset root when you intentionally want the task to write
source assets, then run the task yourself:

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

Tools that do not use Gradle, such as editors, can call the Java builders from
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
inside a custom tool. The caller is responsible for passing the correct
classpath and choosing the output directory.

## 5. Standalone Desktop Native Builder

```java
NativeBuilder.desktop()
    .mainClass("com.example.EditorLauncher")
    .classpathFromCurrentJvm()
    .nativeResourceClasspathFromCurrentJvm()
    .outputDirectory(Path.of("build/editor-native"))
    .build();
```

The native builder compiles TeaVM C output and writes the same desktop-native
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
