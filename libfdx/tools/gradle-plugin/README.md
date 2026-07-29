# libFDX Gradle Plugin

The `io.github.libfdx` plugin creates platform builds and generated assets. It
is build-time integration only: application code continues to use portable
libFDX APIs, and launcher projects declare their backend/provider dependencies
explicitly.

Repository contributors should enable the local dependency mode described in
[Contributing](../../../CONTRIBUTING.md).

## Platform Targets

Apply the plugin to a launcher/export project and declare only the target
families that project needs:

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

Available families are `desktopJvm`, `android`, `js`, `wasm`, `desktopC`,
`psp`, and `iosC`. Named targets produce named build/run/generate tasks.
Inspect the tasks created for the current project rather than relying on a
copied catalog:

```powershell
./gradlew tasks --group libfdx
```

Target blocks configure generation and launch behavior; they do not add
backend or provider artifacts. Desktop JVM packaging uses the configured
runtime classpath. Desktop C generation copies the configured asset roots into
the release `assets` directory, and its run tasks use that release as their
working directory. When one project declares several TeaVM C families, request
tasks from only one native family in a Gradle invocation so the requested target
determines TeaVM configuration.

iOS targets generate an Xcode handoff project. `gles` selects an
OpenGLES/GLKit project and `metal` selects Metal/MetalKit; building or running
the generated project requires macOS and Xcode.

Android application projects apply the Android application plugin before
`io.github.libfdx`. The Android DSL creates launcher tasks and leaves the
manifest, source sets, packaging, and dependencies with the Android project:

```kotlin
plugins {
    id("com.android.application")
    id("io.github.libfdx") version libfdxVersion
}

libfdx {
    android {
        applicationId.set("com.example.game")
        adbExecutable.set(androidComponents.sdkComponents.adb)

        target("gles") {
            activity.set("com.example.game.GlesActivity")
        }
    }
}
```

`libfdx_android_gles_run` depends on `installDebug` by default, then uses the
configured ADB executable to launch the activity. Set `variantName` when the
application uses another installable variant. String and boolean system
properties can be forwarded as Android intent extras with
`forwardStringSystemProperty` and `forwardBooleanSystemProperty`.

## ECS Project Bundles

Apply `ecsProject` to a portable game-core project when a desktop tool must load
that project. This does not add an editor, desktop backend, or UI implementation
dependency to game code.

```kotlin
libfdx {
    ecsProject {
        projectId.set("com.example.game")
        entryClass.set("com.example.game.GameProject")
        projectRoot.set(rootProject.layout.projectDirectory)
        toolingAbi.set(1)
        libfdxAbi.set(libfdxVersion)
    }
}
```

The project root owns `fdx-project.json`, `assets/`, and `scenes/`. Its manifest
identifies the project, entry class, default scene, asset path, Gradle project,
and desktop bundle task relative to that same root.

The `libfdx_ecs_project_bundle` task compiles the main source set and writes a
deterministic `.fdxproject` archive under `build/fdx-project` by default. The
archive contains project classes, explicitly allowed dependency JARs, assets,
scenes, ABI metadata, and content hashes. Bundle creation rejects unsafe paths,
duplicates, manifest/configuration mismatches, and dependencies containing
protected framework/editor packages.

Use `allowedDependencies(...)` only for project libraries that the host is
expected to load. Normal platform launchers remain statically linked and do not
load the editor bundle.

## Bitmap Fonts

`bitmapFont("name")` creates a named source-asset generation task:

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

Font generation is explicit; it does not run as a side effect of every
platform build. The project owns the generated assets and decides when to
commit or package them.

## Shaders

The `shaders` block configures the WGSL source directory, default portability
profile, and validation report. Shader authoring and provider translation are
described in [Shaders](../../../docs/SHADERS.md).

Exact extension properties and generated task behavior are defined by
`LibfdxExtension.kt` and `LibfdxGradlePlugin.kt`. Add focused plugin tests when
changing the public DSL or task wiring.
