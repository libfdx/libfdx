# libFDX Shaders

WGSL is the public shader authoring language in libFDX. Providers either consume
WGSL directly or translate it while creating a shader module.

```text
WGSL source
  -> common shader descriptor
     -> selected provider
        -> WGSL, GLSL/GLSL ES, SPIR-V, HLSL, or MSL handoff
```

Generated languages are provider artifacts, not additional user-maintained
source files. Compiler-library and platform SDK types do not leak through the
common graphics API.

## Source And Profiles

Project shader files conventionally live under:

```text
src/main/fdx-shaders/**/*.wgsl
```

Built-in renderer WGSL stays with its owning renderer so it is available on all
supported build targets. Generated translations belong in build/report output.

A leading comment may declare a portability profile:

```wgsl
// @fdx.profile webgl2
```

| Profile | Intent |
| --- | --- |
| `webgl2` | WebGL2/OpenGL ES 3-class render subset. |
| `webgpu` | Portable WebGPU/wgpu render and compute subset. |
| `native` | Explicit provider-specific opt-in; not portable by default. |

When no comment is present, the configured project default applies. The
WebGL2 profile excludes compute, storage resources, atomics, subgroups, WGSL
extensions, 16-bit floats, and 64-bit integers. The WebGPU profile still
excludes provider-specific extensions and subgroups unless a later
capability-gated profile defines them.

`native` does not make a generated target language part of the public authoring
contract. Providers still receive WGSL through the common descriptor.

## Runtime Translation

The runtime exposes an optional provider-neutral shader compiler capability.
Backends package it only when an active provider needs translation.

- Translation runs during shader-module creation, explicit editor
  recompilation, or validation—never in the render loop.
- Compiler setup and translations may be cached by source, target, stage, entry
  point, profile, options, and compiler version.
- A missing required compiler or unsupported profile/feature fails clearly.
- Failed translation returns diagnostics rather than a partial shader.
- Providers that consume WGSL directly do not require the compiler capability.

Tint is the current implementation backend, but Tint types and generated
languages are not common API. Changing compiler implementation does not change
the authored WGSL contract.

## Provider Responsibilities

Each provider chooses the appropriate compiler target and creates its native
shader module. Built-in 2D/3D renderers supply WGSL only; providers do not
silently select handwritten fallback shaders.

Reflection metadata may describe entry points, stages, bindings, vertex
locations/formats, and translated names. Reflection is created and consumed
during setup. It never runs per draw or allocates per frame.

Unsupported target, profile, feature, or binding combinations fail during
setup rather than changing rendering semantics silently.

## Hot Reload

Editor reload follows the same pipeline:

1. Compile after a file change or explicit action, preferably off the render
   thread.
2. Create provider resources on the required graphics thread.
3. Keep the old valid pipeline until replacement succeeds.
4. Surface diagnostics without replacing a working pipeline on failure.
5. Dispose replaced resources only after in-flight use ends.

Allocation for an explicit reload is acceptable; reload work does not become a
steady-state frame-loop path.

## Validation

Compiler changes require focused compiler/ABI tests and the affected provider's
shader-creation path. Renderer shader changes require a rendered scenario, not
only successful compilation. Compare affected providers under the same scene,
viewport, assets, input, and frame conditions.

See [Contributing](../CONTRIBUTING.md#visual-and-graphics-changes) for the visual
validation protocol and the
[Gradle plugin](../libfdx/tools/gradle-plugin/README.md#shaders) for project
configuration.
