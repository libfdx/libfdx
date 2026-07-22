<img src="data/libfdx_logo_dark.svg" width="300" />

[![Build](https://github.com/libfdx/libfdx/actions/workflows/workflow_snapshot.yml/badge.svg)](https://github.com/libfdx/libfdx/actions/workflows/workflow_snapshot.yml)
[![Maven Central Version](https://img.shields.io/maven-central/v/io.github.libfdx/fdx)](https://central.sonatype.com/artifact/io.github.libfdx/fdx)
[![Snapshot](https://img.shields.io/badge/snapshot---SNAPSHOT-red)](https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/io/github/libfdx/fdx/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

libFDX is a modular Java game framework. Shared game code uses portable APIs;
platform launchers select a backend and optional graphics/network providers.
It is inspired by libGDX, but is a new framework rather than a fork or
compatibility layer.

The project is under active early development. Source and Javadocs define exact
APIs, while implemented behavior is exercised by tests and samples. Planned
work belongs in [GitHub issues](https://github.com/libfdx/libfdx/issues).

## Structure

libFDX separates four responsibilities:

1. **Framework modules** define portable runtime, graphics, asset, 2D, 3D, and
   UI APIs.
2. **Extensions** add optional providers and features.
3. **Backends** connect the runtime to desktop, web, Android, and native
   platforms.
4. **Launchers** select the backend/provider stack for one application.

The backend passes a typed `Fdx` runtime root to application code. User-owned
objects such as asset managers, batches, UI roots, and ECS worlds remain
explicit rather than becoming global services.

```java
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

See [Architecture](docs/ARCHITECTURE.md) for the dependency model and
[Common API](docs/COMMON_API.md) for cross-cutting lifecycle and ownership
rules.

## Try It

Use JDK 25 and the repository Gradle wrapper. From the repository root on
Windows:

```powershell
.\gradlew.bat :samples:2d:sprite-movement:platform:desktop:sprite_movement_desktop_gl_run
```

A clean checkout uses configured published artifacts. Contributors changing
checked-out framework code should enable local dependency mode; see
[Contributing](CONTRIBUTING.md).

Hosted tools and demos:

- [Project Generator](https://libfdx.github.io/project-generator/)
- [Tests](https://libfdx.github.io/tests/)

## Guides

- [Contributing](CONTRIBUTING.md): checkout setup and validation.
- [Shaders](docs/SHADERS.md): WGSL authoring and provider translation.
- [UI Kit](docs/UI_KIT.md): UI ownership, composition, state, and rendering.
- [Gradle plugin](libfdx/tools/gradle-plugin/README.md): project-generation DSL.
- [Scenario validator](libfdx/extensions/scenario_validator/README.md): runtime
  validation flows.
- [2D samples](samples/2d/) and [benchmarks](benchmark/README.md): executable
  examples and performance entry points.

## Community

- [Discord](https://discord.gg/CutyWq27Gu)
- [Patreon](https://patreon.com/libfdx)
- [GitHub Sponsors](https://github.com/sponsors/xpenatan)

libFDX is licensed under the [Apache License 2.0](LICENSE).
