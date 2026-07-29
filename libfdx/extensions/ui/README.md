# UI extensions

The `imgui_ext` artifact provides the libFDX renderer, input bridge, texture
registry, and viewport integration for jImGui:

```kotlin
dependencies {
    implementation("com.github.xpenatan.jImGui:imgui-core:<jImGui-version>")
    implementation("io.github.libfdx:imgui_ext:<libFDX-version>")
}
```

Applications separately select the matching jImGui desktop, Android, or web
runtime. The libFDX extension does not pull a native runtime transitively.
