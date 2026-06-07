# libFDX Building

This document explains how to build and run libFDX from a local checkout. It
is for contributors and engine developers who need to run samples, generate
platform-native artifacts, or verify that a local environment can build the
current repository.

This is not the architecture source of truth. Use
[Architecture](ARCHITECTURE.md) for module ownership and dependency direction,
and use [Testing](TESTING.md) when the goal is to validate behavior rather than
start a sample.

## Index

- [1. Requirements](#1-requirements)
- [2. Local Build Workflow](#2-local-build-workflow)
- [3. Repository Dependency Mode](#3-repository-dependency-mode)
- [4. Native Artifacts](#4-native-artifacts)
- [5. Basic Desktop Sample](#5-basic-desktop-sample)
- [6. Basic Android Sample](#6-basic-android-sample)
- [7. Basic Web Sample](#7-basic-web-sample)

## 1. Requirements

- JDK 25 available on `PATH`
- Gradle wrapper from this repository
- Desktop runtime support for desktop launchers
- Android SDK plus a connected device or emulator for Android launchers
- Native platform toolchains only for platform-native artifact builds

Modules target Java 25 source and bytecode compatibility.

## 2. Local Build Workflow

For a normal local development pass, start with the smallest launcher that
proves the change:

1. Use a desktop sample when checking application startup or a provider-neutral
   rendering path.
2. Use `build_native_artifacts` only when the changed target consumes generated
   native resources.
3. Use the commands in [Testing](TESTING.md) when checking a specific provider,
   widget, visual path, input path, benchmark, or PSP capture.

The sample tasks in this document are interactive launchers. They are useful for
manual checks and smoke runs, but they do not replace validation tasks when a
change needs deterministic pass/fail evidence.

## 3. Repository Dependency Mode

Tests, samples, and benchmarks can run against either the local source modules
or already published libFDX artifacts. This is controlled by the development
block in `libfdx.toml`, with local overrides from ignored `local.properties`:

```toml
[development]
usePublishedLibfdx = true
publishedLibfdxVersion = "-SNAPSHOT"
```

This TOML default lets users run tests, samples, or benchmarks against artifacts
that already exist in Maven repositories. In this mode, those consumers resolve
libFDX dependencies as published coordinates such as
`<fdxGroup>:<artifact>:<publishedLibfdxVersion>` instead of project
dependencies. This avoids rebuilding local libFDX modules when the goal is to
check consumers against a released or snapshot build.

Use ignored `local.properties` overrides when developing libFDX itself:

```properties
development.usePublishedLibfdx=false
development.publishedLibfdxVersion=-SNAPSHOT
```

With `development.usePublishedLibfdx=false`, tests, samples, and benchmarks
depend on the local `:libfdx:*` project modules, so changes in the current
checkout are compiled and exercised. Delete the local override keys to use the
`libfdx.toml` defaults again.

This flag applies only to repository consumers: tests, samples, and benchmarks.
Internal libFDX modules still use source-project dependencies.

To switch modes for one checkout, edit `local.properties` before running the
launcher or validation task. Gradle `-P` overrides are not supported for libFDX
dependency mode.

## 4. Native Artifacts

Native artifacts are built by explicit native/platform tasks before they are
used by tests, samples, generated platform projects, or local packaging tasks.
The aggregate task is a convenience entry point for generating the native files
that the current machine can build.

Use the aggregate task when you want to build the native artifacts supported by
the current machine:

```powershell
.\gradlew.bat build_native_artifacts
```

On Windows, this builds the current host runtime fdx desktop native file, the
runtime fdx web JS/WASM files, and Android AAR outputs when the Android SDK is
available. It does not prove that Linux or macOS native files were built; those
must be built on their matching platform jobs or machines.

## 5. Basic Desktop Sample

The basic desktop sample is the fastest way to confirm that a desktop launcher
starts and that a selected graphics provider can present a frame. Use the
provider-specific task that matches the backend path you are checking.

From the repository root on Windows, use the task for the graphics stack you
want:

```powershell
.\gradlew.bat :samples:basic:platform:desktop:run_gl
.\gradlew.bat :samples:basic:platform:desktop:run_wgpu
.\gradlew.bat :samples:basic:platform:desktop:run_vulkan
.\gradlew.bat :samples:basic:platform:desktop_native:run_gl_debug
.\gradlew.bat :samples:basic:platform:desktop_native:run_gl_release
```

## 6. Basic Android Sample

The Android sample tasks build and launch the basic sample on a connected
Android device or emulator. They require the Android SDK and device/emulator
setup before Gradle can install or run the app.

Use the task for the Android graphics stack you want:

```powershell
.\gradlew.bat :samples:basic:platform:android:run_gles
.\gradlew.bat :samples:basic:platform:android:run_wgpu_jni
.\gradlew.bat :samples:basic:platform:android:run_vulkan
.\gradlew.bat :samples:basic:platform:android:run_vulkan_fallback
```

## 7. Basic Web Sample

The web sample tasks generate webapps for browser-based smoke checks. They are
useful for validating the web build path and the browser launcher shell before
running the fuller web test matrix.

The web sample builds JavaScript and Wasm WebGL webapps:

```powershell
.\gradlew.bat :samples:basic:platform:web:libfdx_web_js_run
.\gradlew.bat :samples:basic:platform:web:libfdx_web_wasm_run
```

For web launchers, a width or height of `0` or a negative value means the
canvas fills the browser window.
