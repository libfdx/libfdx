<img src="data/libfdx_logo_dark.svg" width="300" />

[![Build](https://github.com/libfdx/libfdx/actions/workflows/workflow_snapshot.yml/badge.svg)](https://github.com/libfdx/libfdx/actions/workflows/workflow_snapshot.yml)
[![Maven Central Version](https://img.shields.io/maven-central/v/io.github.libfdx/fdx)](https://central.sonatype.com/artifact/io.github.libfdx/fdx)
[![Snapshot](https://img.shields.io/badge/snapshot-0.0.2--SNAPSHOT-red)](https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/io/github/libfdx/fdx/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

libFDX is a modular Java game framework focused on provider-neutral application,
runtime, storage, and graphics APIs. Game code is intended to depend on common
API modules, while platform launchers choose the backend and provider stack.

libFDX is inspired by libGDX, but it is a new framework rather than a fork,
port, or compatibility layer.

This repository is in early implementation. The detailed contracts live in the
project docs.

## Requirements

- JDK 25 available on `PATH`
- Gradle wrapper from this repository
- Desktop runtime support for desktop launchers
- Android SDK plus a connected device or emulator for Android launchers
- Platform toolchains only when building native platform artifacts

Common/JVM/web/native modules target Java 25 source and bytecode compatibility.
Android application and library modules target Java 17 bytecode for AGP, lint,
and device compatibility while consuming the same common APIs.

## Quick Start

Hosted web tools and demos:

- [Project Generator](https://libfdx.github.io/project-generator/)
- [Tests](https://libfdx.github.io/tests/)
- [Basic Sample](https://libfdx.github.io/samples/basic/)
- [ECS Platformer](https://libfdx.github.io/samples/ecs-platformer/)

From the repository root on Windows:

```powershell
.\gradlew.bat :samples:basic:platform:desktop:basic_desktop_gl_run
.\gradlew.bat :samples:ecs-platformer:platform:desktop:libfdx_desktop_jvm_gl_run
```

To open the desktop test selector:

```powershell
.\gradlew.bat :tests:platform:desktop:test_desktop_gl_run
```

To run the WebRTC multiplayer sample, start the standalone signaling server
first, then launch the sample clients:

```powershell
.\gradlew.bat :libfdx:extensions:net:webrtc:signaling_server:webrtc_signaling_server_run
.\gradlew.bat :samples:multiplayer:2d-webrtc:platform:desktop:multiplayer_2d_webrtc_desktop_wgpu_run
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md): module layout, dependency direction,
  package roots, artifact naming, Maven artifacts, and backend boundaries.
- [Common API](docs/COMMON_API.md): provider-neutral public API contracts,
  lifecycle rules, and provider boundaries.
- [Shaders](docs/SHADERS.md): WGSL-only shader authoring, runtime translation,
  Tint-backed compiler packaging, and validation expectations.
- [UI Kit](docs/UI_KIT.md): retained UI toolkit specification.
- [Scenario Validator](docs/SCENARIO_VALIDATOR.md): scenario validation
  engine contract.
- [Building](docs/BUILDING.md): local setup, native artifacts, and sample
  launch commands.
- [Testing](docs/TESTING.md): provider tests, platform test launchers, PSP
  capture, and validation tasks.
- [Benchmarks](benchmark/README.md): in-repository performance benchmark
  tasks.
- [Builders](docs/BUILDERS.md): Gradle plugin usage, bitmap font generation,
  and standalone Java builders.

## Support

Support libFDX development through [Patreon](https://patreon.com/libfdx) or
[GitHub Sponsors](https://github.com/sponsors/xpenatan).

## Community

Join the [libFDX Discord](https://discord.gg/CutyWq27Gu) to ask questions,
discuss the framework, and follow development.

## License

libFDX is licensed under the [Apache License 2.0](LICENSE).
