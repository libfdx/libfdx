# Contributing To libFDX

This guide covers the minimum setup and validation needed when changing the
repository. Exact projects, task names, dependencies, and publications remain
authoritative in Gradle source and task help.

## Requirements

- JDK 25 on `PATH`.
- The repository Gradle wrapper.
- Platform SDKs and native toolchains only for targets you intend to build.
- Android SDK plus a connected device/emulator for Android execution.
- Emscripten for web native artifacts.

Shared JVM/web/native-facing Java code targets Java 25. Android application and
provider projects retain their supported Java 17 bytecode boundary.

On Windows, use `gradlew.bat` in place of `./gradlew` in the examples below.

## Use Checked-Out Framework Code

Repository consumers—samples, tests, benchmarks, and plugin-use modules—use
published artifacts by default. While changing framework code, create or edit
the ignored root `local.properties`:

```properties
development.usePublishedLibfdx=false
```

For one invocation, use:

```powershell
./gradlew "-Dlibfdx.development.usePublishedLibfdx=false" <task>
```

The system property overrides `local.properties`, which overrides
`gradle/libs.versions.toml`. Composite and publication builds select their own reduced graphs;
contributors do not set a separate publication mode.

## Choose Validation By Scope

Start with the smallest evidence that exercises the changed behavior:

| Change | First evidence |
| --- | --- |
| Pure Java leaf module | Its unit-test task. |
| Sample or launcher | The directly affected build or finite run. |
| Backend/provider wiring | Relevant compile plus a provider run. |
| Shared graphics, UI, font, texture, shader, or readback | Focused rendered scenario, then affected providers. |
| Gradle plugin/task wiring | Generated/public task in the relevant dependency mode. |
| Documentation only | Stale-reference and local-link checks plus `git diff --check`. |

Useful starting points are:

```powershell
./gradlew check
./gradlew :tests:platform:desktop:test_desktop_gl_run
./gradlew libfdx_build_native_artifacts
./gradlew tasks --all
```

The first three are examples, not a mandatory matrix. Expand when a change is
shared, public, cross-platform, or still uncertain. Report an unavailable SDK,
device, toolchain, runtime, or graphics API as a blocker rather than a pass.

## Platform Evidence

For desktop work, compile or run the affected desktop launcher/provider. A
finite test run can be selected with the test launcher's documented
`libfdx.test.*` properties.

For Android work, first check the actual device state:

```powershell
adb devices -l
```

When a device is available, use the repository run task that installs and
launches the affected activity. `assembleDebug` alone is not runtime evidence.

Native generation proves only project/source generation. Native build and run
evidence require the matching host toolchain. iOS execution requires macOS and
Xcode; PSP building requires PSPDEV, and capture requires PPSSPP plus the
repository capture setup.

## Visual And Graphics Changes

A successful build or capture task is not visual validation. Inspect a real
rendered frame and connect the result to the mechanism changed.

1. Reproduce the smallest relevant primitive or scenario.
2. Use a known-good provider, normally GL, as a reference where appropriate.
3. Compare the same scene, viewport, scale, assets, input, timing, and frame
   count across affected providers.
4. Record each scoped target as `PASS`, `BLOCKED`, or `NOT RUN` with evidence or
   a concrete reason.

When immediate GL behavior is correct but a recorded-command provider is not,
check buffer reuse after recording, pass load/store state, row alignment and
format conversion, synchronization, resource lifetime, and coordinate
conventions. Do not hide a visible defect by only relaxing comparison
tolerance.

Keep disposable captures and generated reports under `build/reports`.

## Native Artifacts And Publication

Build native artifacts supported by the current machine with:

```powershell
./gradlew libfdx_build_native_artifacts
```

The build uses pinned, checksum-verified native dependency packages and builds
libFDX bridge code. Success on one host does not prove outputs for another host.

Prepare local deployment repositories for inspection with:

```powershell
./gradlew prepareSnapshot
./gradlew prepareRelease
```

These EasyPublishing tasks prepare `build/snapshot-deploy` and
`build/staging-deploy`; they do not upload or change the checked-in
dependency-mode default. Use `publishSnapshot` or `publishRelease` only through
the appropriate release workflow.

## Reporting Results

Report what changed, the exact validation commands and results, targets not run
and why, and any remaining risk. Do not claim a platform, provider, or graphics
API was validated unless it actually ran.
