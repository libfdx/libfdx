# libFDX Project Generator

The project generator creates a standalone Gradle project from one of the
repository samples embedded in the generator build. The default **Starter
Project** is a clean application base; the other starting points preserve the
code and assets of their sample.

## Sample and version model

When the generator core is compiled, its build discovers every directory under
`samples/` that owns a `core/build.gradle.kts`. Each complete sample directory
is compressed into generated Java sources, so the desktop and web generators
can work without GitHub or another network source.

The generator build also records one libFDX dependency version:

- the default generator build records `libfdxSnapshot`;
- `--project-prop=libfdx.projectGenerator.release=true` records `libfdxRelease`;
- `--project-prop=libfdx.projectGenerator.version=<coordinate-version>` records an explicit
  version for release engineering or timestamped snapshots.

Every generated project writes that exact value to `libfdxVersion` in
`gradle.properties`. Both the `io.github.libfdx` Gradle plugin and the sample's
published libFDX dependencies use it.

A release generator should be built and distributed with the matching libFDX
release. Publish and verify the libFDX libraries and the `io.github.libfdx`
Gradle plugin marker first; only then build and publish the release generator.
Otherwise the generator itself can run while its exported projects cannot
resolve the selected release.

Ordinary users should select a bundled sample, not a Git commit. A commit-based
GitHub download can be an advanced contributor workflow, but it should not
replace the offline, version-matched bundled default: GitHub availability,
branch drift, and mismatched published artifacts would otherwise make
generation less reproducible.

## Project choices

The Starter Project is listed first and selected by default. It lets users set
the Java package before generation. Desktop is the initial platform selection,
and users can choose any combination the selected starting point provides:

- Desktop;
- Android;
- Web;
- Desktop C; and
- iOS C.

Only selected platform directories are exported. This keeps a desktop-only
project free of Android, web, and native modules and their toolchain
requirements.

## Generated layout

The selected sample's Java, assets, scenes, manifests, documentation, and
sample-owned Gradle files are copied. The generator adds only the standalone
root files:

- `settings.gradle.kts`;
- `build.gradle.kts`;
- `gradle.properties`;
- `gradle/libs.versions.toml`;
- `.gitignore` when the sample does not own one; and
- `PROJECT_GENERATOR.md` with sample and dependency provenance.

`:core`, optional `:editor`, and the selected platform modules are included.
The support `:platform:plugin` module is included only when one of the selected
platforms uses its shared application-task configuration.

Sample Gradle files must own their dependencies and derive their sample root
and sibling project paths from their own project location. Repository-root
build logic must not be required by a copied sample.

## Desktop generator

Run the snapshot-pinned generator:

```powershell
./gradlew :libfdx:tools:project-generator:platform:desktop:project_generator_desktop_gl_run
```

Build the distributable snapshot generator:

```powershell
./gradlew :libfdx:tools:project-generator:platform:desktop:project_generator_desktop_gl_build
```

Build a release-pinned generator:

```powershell
./gradlew --project-prop=libfdx.projectGenerator.release=true :libfdx:tools:project-generator:platform:desktop:project_generator_desktop_gl_build
```

The desktop UI uses the bundled Liberation Sans 2.1.5 TrueType font. Its SIL
Open Font License is packaged beside the font.

## Hosted web generator

The public project generator is a WebGPU Wasm application. Build it with:

```powershell
./gradlew :libfdx:tools:project-generator:platform:web:project_generator_webgpu_wasm_build
```

The root `stage_pages` task copies that application directly to
`build/pages/project-generator`. It does not publish a runtime or graphics
selector, so `/project-generator/` opens the generator itself.

Deployment is owned by the
[`Pages` workflow](https://github.com/libfdx/libfdx.github.io/actions/workflows/pages.yml)
in the `libfdx.github.io` repository. Run that workflow manually and set
`libfdx_ref` to the libFDX branch, tag, or commit to build. A branch or commit
embeds the snapshot dependency version. A tag matching the checked-out
`libfdxRelease` value, with or without a leading `v`, embeds the release
version.

## Validation

Run the focused generator checks:

```powershell
./gradlew :libfdx:tools:project-generator:core:test_generate_project
./gradlew :libfdx:tools:project-generator:platform:desktop:test_export_project
./gradlew :libfdx:tools:project-generator:platform:web:test_archive_project
```

After an export, validate the copied sample from its generated root, for
example:

```powershell
./gradlew -p <generated-project> :core:classes :platform:desktop:classes
```
