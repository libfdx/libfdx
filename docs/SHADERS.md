# libFDX Shaders

libFDX has one public shader authoring language: WGSL. Providers either consume
WGSL or translate it when a shader module is created.

Module ownership is in [ARCHITECTURE.md](ARCHITECTURE.md); graphics resource and
lifetime rules are in [COMMON_API.md](COMMON_API.md#9-graphics).

## Topics

- [1. Pipeline](#1-pipeline)
- [2. Source and Profiles](#2-source-and-profiles)
- [3. Runtime Compilation](#3-runtime-compilation)
- [4. Provider Responsibilities](#4-provider-responsibilities)
- [5. Reflection and Bindings](#5-reflection-and-bindings)
- [6. Hot Reload](#6-hot-reload)
- [7. Ownership](#7-ownership)
- [8. Validation](#8-validation)

## 1. Pipeline

```text
WGSL source
  -> ShaderModuleDescriptor.wgsl(...)
     -> selected provider
        -> WebGPU/wgpu: WGSL
        -> GL/WebGL/GLES: generated GLSL/GLSL ES
        -> Vulkan: generated SPIR-V
        -> Direct3D 12: generated HLSL
        -> Metal: generated MSL where runtime translation is available
```

Tint is the current compiler backend, but Tint types are not public graphics
API. Generated languages are provider handoff artifacts, never additional
user-maintained source files.

libFDX does not:

- require generated Java shader classes;
- check in translated copies of every built-in shader;
- expose compiler-library or platform SDK types through common graphics;
- package the compiler on platforms that do not need translation;
- claim a target until both translation and provider paths are validated.

## 2. Source and Profiles

User-project shader files conventionally live under:

```text
src/main/fdx-shaders/**/*.wgsl
```

Built-in renderer WGSL stays in its owning renderer module/class so it is
available on TeaVM without relying on dependency-resource lookup. Generated
translations are diagnostics/build output, not source.

A leading comment may declare portability:

```wgsl
// @fdx.profile webgl2
```

| Profile ID | Java value | Contract |
| --- | --- | --- |
| `webgl2` / `fdx-wgsl-webgl2` | `PORTABLE_WEBGL2` | WebGL2/OpenGL ES 3-class render subset. |
| `webgpu` / `fdx-wgsl-webgpu` | `PORTABLE_WEBGPU` | Modern WebGPU/wgpu render and compute subset. |
| `native` / `fdx-native` | `NATIVE` | Explicit provider-specific opt-in; not portable by default. |

No comment uses the project default, currently `webgpu`.

The WebGL2 profile excludes compute, storage buffers/textures, atomics,
external/multisampled textures, WGSL extensions/`requires`, subgroup operations,
override constants, 16-bit floats, and 64-bit integers. The WebGPU profile still
excludes backend-specific extensions, `requires`, and subgroups unless a later
capability-gated profile defines them.

`NATIVE` does not make GLSL, SPIR-V, HLSL, or MSL public authoring languages.
Providers still receive WGSL through the common descriptor path.

## 3. Runtime Compilation

`framework/fdx/core` owns the optional provider-neutral compiler capability.
Backends that need it package and register the matching native runtime `fdx`
library.

Compiler input includes WGSL, target, stage, entry point, and target options.
Output is translated text or SPIR-V bytes plus diagnostics.

- Translation occurs at shader-module creation, explicit editor recompilation,
  or validation--never in a render loop.
- Implementations cache compiler setup and may cache output by source hash,
  target, stage, entry point, compiler/version, profile, and options.
- A provider needing translation fails clearly when the capability is absent,
  naming the missing capability and target.
- Failed translation returns diagnostics and no partial/mismatched shader.
- Platforms consuming WGSL directly do not require the compiler.

The internal `fdx-build` project links checksum-verified prebuilt Tint/Dawn
packages from the pinned `fdx-natives` release. Third-party compiler source is
not committed or built by libFDX tasks. Web Emscripten Tint currently uses the
validated conservative optimization configuration; changing optimization is a
separate native validation target.

## 4. Provider Responsibilities

| Provider family | Target | Consumed/generated form |
| --- | --- | --- |
| WebGPU | `WEBGPU_WGSL` | WGSL |
| wgpu | `WGPU_WGSL` | WGSL |
| WebGL | `WEBGL_GLSL_ES` | GLSL ES |
| GLES | `GLES_GLSL_ES` | GLSL ES |
| OpenGL | `OPENGL_GLSL` | desktop GLSL |
| Vulkan | `VULKAN_SPIRV` | SPIR-V |
| Direct3D 12 | `DIRECTX_HLSL` | HLSL |
| Metal | `METAL_MSL` | MSL |

Built-in g2d/g3d shader descriptors contain WGSL only. A GL, Vulkan,
Direct3D 12, or Metal provider requests translation instead of selecting a
handwritten fallback. WGPU/WebGPU consumes the authored source.

PSP is intentionally outside the Tint path. iOS C Metal can create modules from
MSL but does not currently register the runtime WGSL compiler, so WGSL-only
built-in renderers require that bridge before they work on that path. The
Windows Direct3D 12 provider compiles generated HLSL during shader-module
creation; HLSL is a provider handoff target, not a user authoring language.

Provider-specific setup remains explicit. Unsupported language/profile/feature
combinations fail during setup rather than changing rendering silently.

## 5. Reflection and Bindings

Reflection is setup-time metadata for resource layout and diagnostics. It may
describe bind group/index/type, stage visibility, vertex locations/formats,
entry points, and target-renamed symbols.

- Metadata must be stable enough for high-level renderers and providers to
  create/validate layouts without provider-specific parsing in game code.
- Pipeline descriptors carry reflection to providers that need it.
- Reflection and binding discovery never run per draw or allocate per frame.
- The metadata describes the authored contract even if a compiler rewrites
  target names.

## 6. Hot Reload

Editor reload uses the same compiler path:

1. compile only after a file change or explicit action, preferably off the
   render thread;
2. create/apply provider resources on the provider's required graphics thread;
3. keep the old valid pipeline until replacement succeeds;
4. on failure, keep rendering with the old pipeline and expose diagnostics;
5. dispose replaced modules, pipelines, layouts, and related resources only
   after in-flight use ends.

Compiler request/result allocation is acceptable for an explicit editor action,
not in the game frame loop.

## 7. Ownership

| Area | Owner |
| --- | --- |
| Shader language/profile/target, descriptors, reflection | `framework/graphics` |
| Built-in 2D WGSL | `framework/g2d` |
| Built-in 3D WGSL | `framework/g3d` |
| Runtime compiler Java contract | `framework/fdx/core` |
| Shared compiler C ABI | `framework/fdx/platform/shared` |
| Native dependency/link tasks | `framework/fdx/fdx-build` |
| Desktop/Android/Web runtime packaging | matching `framework/fdx/platform/*` module |
| Profile validation and user build DSL | Gradle plugin |
| Native shader-module creation | selected provider/backend path |

There is no separate runtime `tools/shader` service. Providers request the
runtime-core capability only when their target cannot consume WGSL.

## 8. Validation

For compiler/runtime changes, validate the affected core/graphics compile, the
native ABI/compiler tests, and each changed provider's shader creation path.
Use focused `shader-runtime` and `shader-scene` tests before broader renderers.

For high-level renderer shader changes, run the affected g2d/g3d scene on every
provider in scope. Shader compilation alone does not prove rendered parity.
Build a known-good GL baseline when applicable, compare rendered output under
the same scene/input/frame conditions, and report every scoped platform/API as
`PASS`, `BLOCKED`, or `NOT_RUN` with reasons. See the full
[visual validation protocol](TESTING.md#4-visual-and-graphics-validation).

Potential frontends such as material graphs or importers belong in tracked
issues until implemented. Any future frontend must still produce WGSL for the
same public descriptor and provider pipeline.
