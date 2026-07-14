<img src="data/libfdx_logo_dark.svg" width="300" />

[![Build](https://github.com/libfdx/libfdx/actions/workflows/workflow_snapshot.yml/badge.svg)](https://github.com/libfdx/libfdx/actions/workflows/workflow_snapshot.yml)
[![Maven Central Version](https://img.shields.io/maven-central/v/io.github.libfdx/fdx)](https://central.sonatype.com/artifact/io.github.libfdx/fdx)
[![Snapshot](https://img.shields.io/badge/snapshot---SNAPSHOT-red)](https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/io/github/libfdx/fdx/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

libFDX is a modular Java game framework. Shared game code uses provider-neutral
APIs; a platform launcher selects the backend and graphics provider.

The project is inspired by libGDX, but is a new framework rather than a fork or
compatibility layer. It is under active early development: current contracts
are documented, while planned work belongs in
[GitHub issues](https://github.com/libfdx/libfdx/issues).

## Mental Model

Four layers keep portable code separate from platform details:

1. **Framework APIs** define application, files, input, display, networking,
   graphics, assets, 2D, 3D, and UI concepts.
2. **Extensions** add optional providers and features such as GL, Vulkan, WGPU,
   WebRTC, ECS, and scenario validation.
3. **Backends** run the application on desktop, web, Android, PSP, desktop C,
   or iOS C.
4. **Launchers** choose a backend/provider stack; shared game modules do not.

The backend passes a typed `Fdx` runtime root to application code. User-created
objects such as asset managers, sprite batches, UI roots, and ECS worlds remain
explicitly owned by the application.

```java
import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;

public final class MyGame extends ApplicationAdapter {
    private Fdx fdx;

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
    }

    @Override
    public void render() {
        float deltaTime = fdx.app().deltaTime();
        // Update and render application-owned systems.
    }
}
```

## Try It

Hosted tools and demos:

- [Project Generator](https://libfdx.github.io/project-generator/)
- [Tests](https://libfdx.github.io/tests/)
- [Basic Sample](https://libfdx.github.io/samples/basic/)
- [ECS Platformer](https://libfdx.github.io/samples/ecs-platformer/)

Repository requirements:

- JDK 25 and this repository's Gradle wrapper
- Android SDK/device only for Android targets
- Native platform toolchains only for native targets

Run a desktop sample from the repository root on Windows:

```powershell
.\gradlew.bat :samples:basic:platform:desktop:basic_desktop_gl_run
```

A clean checkout resolves sample dependencies from the configured Maven
snapshot, so the framework does not need to be published locally first.
Contributors validating checked-out source should select local dependency mode;
see [Building](docs/BUILDING.md#3-dependency-mode).

Common/JVM/web/native modules target Java 25. Android application and library
modules target Java 17 bytecode for Android toolchain compatibility.

## Documentation

Choose the document that matches the question:

| Goal | Document |
| --- | --- |
| Understand module ownership, dependencies, providers, packages, and artifacts | [Architecture](docs/ARCHITECTURE.md) |
| Understand portable behavior, lifecycle, ownership, nullability, and provider boundaries | [Common API](docs/COMMON_API.md) |
| Build the checkout or prepare native artifacts | [Building](docs/BUILDING.md) |
| Run samples | [Samples](docs/SAMPLES.md) |
| Choose and configure a build-time generator | [Builders](docs/BUILDERS.md) |
| Select validation for a change | [Testing](docs/TESTING.md) |
| Understand WGSL authoring and provider translation | [Shaders](docs/SHADERS.md) |
| Understand UI composition and runtime behavior | [UI Kit](docs/UI_KIT.md) |
| Understand reusable runtime scenario validation | [Scenario Validator](docs/SCENARIO_VALIDATOR.md) |
| Run performance benchmarks | [Benchmarks](benchmark/README.md) |

Java source and published Javadoc artifacts are authoritative for exact class,
method, and constructor signatures. The documents above explain the stable
meaning and ownership of those APIs instead of copying every declaration.

## Support And Community

- [Discord](https://discord.gg/CutyWq27Gu)
- [Patreon](https://patreon.com/libfdx)
- [GitHub Sponsors](https://github.com/sponsors/xpenatan)

libFDX is licensed under the [Apache License 2.0](LICENSE).
