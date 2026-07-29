# Physics extensions

libFDX owns the rendering and math adapters for the external jBox2D, jBox3D,
and jJolt bindings:

| Binding | libFDX extension |
| --- | --- |
| `com.github.xpenatan.jBox2D:core` | `io.github.libfdx:box2d_ext` |
| `com.github.xpenatan.jBox3D:core` | `io.github.libfdx:box3d_ext` |
| `com.github.xpenatan.jJolt:core` | `io.github.libfdx:jolt_ext` |

Applications depend on an extension and separately select the matching binding
runtime for desktop, Android, or web. The extensions do not pull a native
runtime transitively.

```kotlin
dependencies {
    implementation("com.github.xpenatan.jJolt:core:<jJolt-version>")
    implementation("io.github.libfdx:jolt_ext:<libFDX-version>")
    runtimeOnly("com.github.xpenatan.jJolt:desktop-jni:<jJolt-version>")
}
```

Snapshot libFDX builds may compile against snapshot binding APIs. A libFDX
release is blocked until every external binding version is changed to a
published non-snapshot version.
