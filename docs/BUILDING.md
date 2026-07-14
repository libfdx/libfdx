# Building libFDX

This guide is for contributors building libFDX from a checkout. For runnable
examples, use [SAMPLES.md](SAMPLES.md). For validation commands, use
[TESTING.md](TESTING.md). Module and artifact ownership is defined in
[ARCHITECTURE.md](ARCHITECTURE.md).

## Topics

- [1. Requirements](#1-requirements)
- [2. Contributor Workflow](#2-contributor-workflow)
- [3. Dependency Mode](#3-dependency-mode)
- [4. Native Artifacts](#4-native-artifacts)
- [5. Publication Preparation](#5-publication-preparation)

## 1. Requirements

- JDK 25 on `PATH`;
- the repository Gradle wrapper;
- the platform SDK/toolchain only for targets you intend to build;
- Android SDK plus a connected device/emulator for Android runs;
- Emscripten SDK for web native artifacts (`emsdk_env` active or `EMSDK` set).

Common JVM, web, and native-facing Java modules target Java 25. Android
application/provider modules use the supported Java 17 bytecode boundary under
AGP.

## 2. Contributor Workflow

Use local dependency mode while changing framework code:

```properties
# local.properties (ignored by Git)
development.usePublishedLibfdx=false
```

Then run the narrowest task that proves the change. Typical starting points are:

```powershell
./gradlew check
./gradlew :tests:platform:desktop:test_desktop_gl_run
./gradlew libfdx_build_native_artifacts
```

The desktop run is interactive unless finite validation properties are supplied.
Native generation is needed only when the affected target consumes generated
native resources. See [TESTING.md](TESTING.md) before expanding to a provider or
platform matrix.

## 3. Dependency Mode

Repository consumers--samples, tests, benchmarks, and plugin-use modules--can use
published artifacts or checked-out projects.

| Mode | Selection | Intended use |
| --- | --- | --- |
| Published | `development.usePublishedLibfdx=true` | Clean checkout and examples without compiling libFDX first. |
| Local | `development.usePublishedLibfdx=false` | Develop and validate framework changes. |
| Publication | Selected automatically by publication tasks | Build Maven output without consumer/bootstrap dependencies. |

Published mode is the checked-in default in `libfdx.toml`. It resolves the exact
`[release].fdxSnapshotVersion`. With the current configuration that version is
`-SNAPSHOT`; it is not constructed from `fdxVersion` as `0.0.2-SNAPSHOT`.
Plugin-use modules resolve the matching published Gradle plugin.

Override the default in ignored root `local.properties`, or for one invocation:

```powershell
./gradlew "-Dlibfdx.development.usePublishedLibfdx=false" <task>
```

Precedence is system property, `local.properties`, then `libfdx.toml`. Gradle
`-P` project properties are not supported for this setting.

Publication tasks ignore the consumer default and use a reduced project graph
without samples, tests, benchmarks, or consumer plugin resolution. Libraries
are prepared first; the isolated Gradle-plugin build then resolves the same
version from the generated deploy repository. Therefore leaving the checked-in
default at `true` does not create a release or snapshot publication bootstrap
cycle.

Version roles remain separate:

- `[release].fdxVersion` is the numeric release coordinate;
- `[release].fdxSnapshotVersion` is the exact snapshot coordinate and must end
  in `-SNAPSHOT`;
- `prepareReleaseDeploy` uses the release version;
- `prepareSnapshotDeploy` uses the snapshot version.

Builder-backed web consumers use generated local runtime resources only in local
mode. Published consumers receive those resources from Maven artifacts.

## 4. Native Artifacts

Build the native artifacts supported by the current machine with:

```powershell
./gradlew libfdx_build_native_artifacts
```

This is the clean-safe setup task for providers that need the runtime `fdx`
bridge, FreeType, or WGSL translation. libFDX links checksum-verified prebuilt
FreeType and Tint/Dawn packages from the pinned `fdx-natives` release; it does
not build those third-party projects from source.

The aggregate task proves only the targets available on the current host. It
does not prove native output for another operating system. For web builds, keep
the shader compiler enabled because built-in renderer sources are WGSL even
when WebGL ultimately executes generated GLSL ES.

If Gradle cannot find `emcmake`, activate `emsdk_env` or set `EMSDK`. The build
uses the SDK-bundled Python when `EMSDK_PYTHON` is not set.

## 5. Publication Preparation

Prepare one version family at a time:

```powershell
./gradlew prepareSnapshotDeploy
./gradlew prepareReleaseDeploy
```

These tasks assemble local deploy repositories for inspection/upload; they do
not change the checked-in dependency-mode default. Confirm produced POM
versions and run the release workflow appropriate to the requested publication
before uploading.
