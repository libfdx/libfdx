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

The development generator records the first seven hexadecimal characters of the
checked-out libFDX commit followed by `-SNAPSHOT`, for example
`8c99044-SNAPSHOT`. The Snapshot workflow publishes the libraries, Gradle
plugin and plugin marker under that same version, then exports and launches a
standalone desktop Starter against Sonatype. Direct Gradle users can use the
same coordinate. Development snapshots expire after 90 days.

The version beside the generator title and on the dependency line can be
selected and copied. Double-click the hash to select it, or triple-click the
version to select the complete dependency version.

Build the generator from a Git checkout. Local generator builds use its HEAD.
External projects can resolve that revision after its artifacts are published.
`--project-prop=libfdx.projectGenerator.release=true` selects the numbered
`libfdxRelease` for a release generator.

Every generated project writes that exact value to `libfdxVersion` in
`gradle.properties`. The `io.github.libfdx` Gradle plugin and the `libfdx`
version in the exported catalog both read that property.

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

The generator copies the selected sample's Java, assets, manifests,
documentation, and Gradle modules. It replaces local libFDX project
dependencies with version catalog aliases derived from the modules' Maven
publications. It adds `settings.gradle.kts`, `build.gradle.kts`,
`gradle.properties`, and `gradle/libs.versions.toml`. It also adds a
`.gitignore` when the sample does not provide one.

`:core`, optional `:editor`, and the selected platform modules are included.
Each launcher module applies `io.github.libfdx` directly and owns the plugin
target configuration for that platform.

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
in the `libfdx.github.io` repository. It checks for a newly verified Snapshot
workflow twice an hour and builds its exact libFDX SHA. You can also run it
manually with a published branch, tag, or commit in `libfdx_ref`. A tag matching the repository
`libfdxRelease` value, with or without a leading `v`, embeds the release
version. The deployed generator's coordinate is available in
[`libfdx-version.txt`](https://libfdx.github.io/project-generator/libfdx-version.txt).

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
