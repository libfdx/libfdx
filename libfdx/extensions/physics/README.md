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

When changing `box3d_ext` and jBox3D together, run the build from the jBox3D
checkout with its local-libFDX composite enabled:

```powershell
.\gradlew.bat "-Plibfdx.local=true" :samples:fdx:platforms:desktop-jni:box3d_fdx_desktop_vulkan_jni_run
```

The jBox3D build self-registers its projects for composite substitution, so
`box3d_ext` compiles directly against the exact local `:box3d:core` project.
No intermediate publication or older-API compatibility layer is used.

## Box3D debug rendering

`FdxDebugRenderer` caches geometry by Box3D geometry ID and exact scale. On
graphics providers with instanced drawing, repeated solids, shadow casters, and
depth-tested wireframes are submitted as one draw per shared geometry instead
of one `ModelBatch` draw per body. `getVisibleInstanceCount()`,
`getSolidDrawCallCount()`, `getShadowDrawCallCount()`, and
`getWireDrawCallCount()` expose the latest-frame counts for profiling. A
provider without instanced drawing uses the regular model and immediate-line
paths; this is a graphics-capability path, not a Box3D API compatibility layer.
