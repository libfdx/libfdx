# libFDX Shader Architecture

This document is the architecture guide for libFDX shader authoring,
translation, generated bundles, and optional editor/runtime compilation.

For module ownership and dependency direction, see [ARCHITECTURE.md](ARCHITECTURE.md).
For public graphics API contracts, see [COMMON_API.md](COMMON_API.md).

## 1. Goal

libFDX shader authoring is WGSL-first. A developer should be able to write one
portable shader source and let libFDX tooling produce the target artifacts needed
by each graphics provider.

The target flow is:

```text
WGSL source
  -> libFDX shader tool
     -> compiler adapter, initially Naga
        -> WGSL for WebGPU/wgpu
        -> GLSL ES for WebGL/GLES
        -> GLSL for desktop OpenGL
        -> SPIR-V for Vulkan
        -> MSL for Metal
        -> HLSL for DirectX later
  -> ShaderBundle metadata
  -> active provider selects its generated ShaderModuleDescriptor
```

The public promise is the libFDX shader pipeline. Naga is the first compiler
adapter, not the public API contract.

## 2. Non-Goals

- Do not make normal game runtimes depend on a shader translator.
- Do not make graphics providers silently translate shader languages as a hidden
  side effect of `GraphicsDevice.createShaderModule(...)`.
- Do not require Metal, Vulkan, GL, WebGPU, and future DirectX users to maintain
  separate hand-written shader source files for the same portable shader.
- Do not expose Naga, Tint, SPIRV-Cross, shaderc, DXC, or platform SDK compiler
  types through the common graphics API.
- Do not claim target support until the compiler path and provider path are both
  implemented and validated.

## 3. Source Layout

Portable shader source belongs under:

```text
src/main/fdx-shaders/**/*.wgsl
```

This path applies to user projects and libFDX modules that own built-in shaders.

A shader may declare its intended profile with a leading comment:

```wgsl
// @fdx.profile webgl2
```

Valid profile IDs are:

| Profile ID | Java enum | Meaning |
| --- | --- | --- |
| `webgl2` or `fdx-wgsl-webgl2` | `ShaderProfile.PORTABLE_WEBGL2` | Lowest common render profile for WebGL2, OpenGL ES 3, and providers with similar limits. |
| `webgpu` or `fdx-wgsl-webgpu` | `ShaderProfile.PORTABLE_WEBGPU` | WebGPU/wgpu-class profile for modern render and compute shaders. |
| `native` or `fdx-native` | `ShaderProfile.NATIVE` | Provider-specific opt-in profile. Not portable unless the owner documents the supported targets. |

If no profile comment exists, the project default profile is used. The default is
`webgpu`.

## 4. Portable WGSL Profiles

`fdx-wgsl-webgl2` is the strict profile. It exists so a shader can target
WebGL2/GLES-style platforms without surprises. It excludes compute shaders,
storage buffers, storage textures, atomics, external textures, multisampled
textures, WGSL extensions, `requires` directives, subgroup operations, override
constants, 16-bit float types, and 64-bit integer types.

`fdx-wgsl-webgpu` is the normal modern profile. It allows WebGPU/wgpu-class
render and compute WGSL, but it still excludes backend-specific WGSL extensions,
`requires` directives, and subgroup operations unless a later capability-gated
profile is added.

`fdx-native` is an escape hatch. It may be used for provider-specific shaders or
future language frontends, but it must name and document the supported provider
targets. A native shader is not part of the portable promise.

## 5. Shader Bundle Contract

`ShaderBundle` is the setup-time bridge between one source-of-truth shader and
provider-specific target artifacts.

A bundle contains:

- the WGSL source of truth;
- the selected `ShaderProfile`;
- generated GLSL or GLSL ES sources when GL-family targets are supported;
- generated SPIR-V words when Vulkan is supported;
- generated MSL when Metal is supported;
- generated HLSL when DirectX support is added;
- reflection metadata for bindings and vertex attributes.

At runtime, portable game code should ask the bundle for the active provider's
descriptor:

```java
ShaderModuleDescriptor descriptor = bundle.descriptorForProvider(fdx.graphics().main().providerId());
ShaderModule shader = fdx.graphics().main().device().createShaderModule(descriptor);
```

Missing generated output for the active provider is a setup error. The provider
should fail clearly instead of translating through an undocumented fallback.

## 6. Build-Time Compilation

Build-time compilation is the default path for games, samples, tests, and
release builds.

The libFDX Gradle plugin owns the project-facing tasks:

- `libfdx_validate_shaders` validates `src/main/fdx-shaders`.
- A future generation task should translate WGSL into requested target outputs.
- Generated reports belong under `build/reports/libfdx/shaders`.
- Generated Java/source artifacts belong under Gradle build output folders.
- Checked-in generated shader code is allowed only for libFDX bootstrap shaders
  when a module intentionally documents that choice.

The build tool must be deterministic. Generated bundles should include enough
metadata to reproduce or diagnose the output:

- source path;
- source hash;
- profile ID;
- target list;
- compiler adapter ID;
- compiler version;
- compiler options;
- generated reflection metadata;
- diagnostics.

## 7. Runtime And Editor Compilation

Runtime shader compilation is allowed for tools and editors, but it is an
optional tooling feature. It must not be pulled into normal game runtime
dependencies by `graphics/api`, providers, backends, or high-level renderers.

The editor flow is:

```text
editor changes WGSL
  -> optional libFDX shader compiler service
  -> validate profile
  -> translate requested provider targets
  -> produce an in-memory ShaderBundle or generated bundle files
  -> dispose old shader module and pipelines
  -> create replacement shader module and pipelines on the render thread
```

The first runtime/editor implementation should prefer a compiler process:

```text
Java editor
  -> libfdx shader runtime compiler API
  -> bundled libfdx-shaderc executable
  -> Naga adapter
  -> translated outputs and diagnostics
```

This keeps Rust/Naga out of normal game classpaths and works well for desktop
editors. A later implementation may replace the process boundary with JNI, FFM,
or a local compiler daemon without changing the editor-facing API.

Editor runtime compilation must cache outputs by source hash, profile, target,
compiler adapter, compiler version, and options. Recompilation should happen on
file changes or explicit editor actions, never every frame.

## 8. Compiler Adapter Model

The shader tool should own a small compiler adapter contract internally.
The common graphics API should not expose this contract.

Target shape:

```text
ShaderCompilerRequest
  source path or source text
  profile
  requested targets
  entry point names
  vertex layout hints when needed

ShaderCompilerResult
  success or diagnostics
  generated target outputs
  reflection metadata
  compiler metadata
```

The first adapter is Naga. Current Naga documentation states that Naga can
translate source code written in one shading language to another, has frontends
for WGSL, GLSL, and SPIR-V, and has backends for GLSL, HLSL, MSL, SPIR-V, and
WGSL:

- [Naga crate docs](https://docs.rs/naga/latest/naga/)
- [Naga frontends](https://docs.rs/naga/latest/naga/front/index.html)
- [Naga backends](https://docs.rs/naga/latest/naga/back/index.html)
- [Naga feature flags](https://docs.rs/crate/naga/latest/features)

Required first Naga feature set:

| Target | Naga direction |
| --- | --- |
| WebGPU/wgpu | WGSL input, WGSL output or preserved source |
| WebGL/GLES | WGSL input, GLSL output configured for GLSL ES |
| OpenGL | WGSL input, GLSL output configured for desktop GLSL |
| Vulkan | WGSL input, SPIR-V output |
| Metal | WGSL input, MSL output |
| DirectX later | WGSL input, HLSL output |

If Naga cannot translate a valid libFDX shader for a target, the shader tool must
report a compiler diagnostic and fail that target. It must not silently emit a
partial or mismatched shader.

## 9. Provider Responsibilities

Providers consume generated shader descriptors. They do not own the portable
shader authoring language.

Target mapping:

| Provider ID | `ShaderTarget` | Expected descriptor language |
| --- | --- | --- |
| `webgpu` | `WEBGPU_WGSL` | `ShaderLanguage.WGSL` |
| `wgpu` | `WGPU_WGSL` | `ShaderLanguage.WGSL` |
| `webgl` | `WEBGL_GLSL_ES` | `ShaderLanguage.GLSL` |
| `gles` | `GLES_GLSL_ES` | `ShaderLanguage.GLSL` |
| `gl` or `opengl` | `OPENGL_GLSL` | `ShaderLanguage.GLSL` |
| `vulkan` | `VULKAN_SPIRV` | `ShaderLanguage.SPIRV` |
| `metal` | `METAL_MSL` | `ShaderLanguage.MSL` |
| `directx` or `d3d*` | `DIRECTX_HLSL` | future HLSL language support |

Provider-specific setup remains explicit through provider IDs and capabilities.
A provider may expose advanced compiler features only as an explicitly documented
provider feature. Hidden translation is not allowed.

## 10. Reflection And Bindings

Shader reflection is setup-time metadata. It should describe the shader contract
without forcing per-frame inspection or allocation.

Reflection should include:

- bind group;
- binding index;
- binding type;
- shader stage visibility;
- vertex attribute location;
- vertex attribute format;
- entry point names;
- target-specific generated names if the compiler rewrites them.

Reflection must be stable enough for high-level renderers to create bind groups,
pipelines, and validation errors without provider-specific parsing.

## 11. Hot Reload Rules

Editor hot reload must treat shader modules and render pipelines as disposable
provider resources.

Rules:

- Compile off the render thread when using a process, native bridge, or daemon.
- Apply new shader modules and pipelines on the render thread or the provider's
  required graphics thread.
- Keep the old pipeline alive until the replacement is fully created.
- If compilation or pipeline creation fails, keep rendering with the previous
  valid pipeline and surface diagnostics to the editor.
- Dispose replaced shader modules, pipelines, bind group layouts, and related
  provider resources after they are no longer in use.
- Do not allocate compiler request/result objects in a game frame loop. Editor
  tools may allocate during explicit compile actions.

## 12. Module Ownership

Stable ownership:

| Area | Owner |
| --- | --- |
| `ShaderLanguage`, `ShaderProfile`, `ShaderTarget`, `ShaderBundle`, descriptors, reflection values | `:libfdx:graphics:api` |
| Built-in 2D shader sources and generated bundles | `:libfdx:graphics:g2d` |
| Built-in 3D shader sources and generated bundles | `:libfdx:graphics:g3d` |
| WGSL profile validation and shader generation API | `:libfdx:tools:shader` |
| Gradle task wiring and user project DSL | `:libfdx:tools:gradle-plugin` |
| Provider-specific shader module creation | selected graphics provider or backend-owned provider |
| Optional editor/runtime compiler API | tooling module, not `graphics/api` |

The first implementation may keep the Naga adapter inside `:libfdx:tools:shader`
if that is simplest. Split the adapter only when native packaging, CLI
distribution, or dependency boundaries require it.

## 13. Validation Requirements

For shader tooling changes:

- validate Java compile for `graphics/api` and `tools/shader`;
- run `tools/shader` tests;
- run Gradle plugin tests or a plugin sample task when task wiring changes;
- compile every requested generated target with the target compiler path when
  available;
- run provider render validation for every provider whose built-in shaders or
  shader creation path changed.

For visual renderer changes:

- use a known-good GL baseline when GL is in scope;
- compare WebGPU/wgpu, Vulkan, Metal, WebGL/GLES, and DirectX targets only when
  they are in scope and available;
- report `PASS`, `BLOCKED`, or `NOT RUN` for every matrix cell in scope;
- never claim visual parity from shader generation alone.

## 14. Implementation Phases

1. Freeze this architecture document and align canonical docs.
2. Tighten `ShaderBundle` and `ShaderModuleDescriptor` so documented targets and
   implemented descriptor selection match.
3. Extend `tools/shader` from profile validation to deterministic bundle
   generation.
4. Add the Naga-backed compiler adapter for WGSL to GLSL ES, GLSL, SPIR-V, MSL,
   and preserved WGSL.
5. Wire Gradle generation tasks and reports.
6. Migrate built-in `g2d` shaders to WGSL source plus generated target outputs.
7. Add native Metal generated MSL validation through the iOS C Metal provider.
8. Add optional editor/runtime compilation through a compiler process API.
9. Add faster runtime integration later through JNI, FFM, or a daemon if editor
   latency demands it.
10. Add future frontends only through the shader tool API, not through
    provider APIs.

## 15. Future Frontends

WGSL is the first source-of-truth language. The architecture must still leave
room for future authoring frontends.

Future frontends may include:

- GLSL for importing existing shader libraries;
- HLSL for DirectX-oriented projects;
- MSL for Metal-only native work;
- Slang or another multi-target frontend;
- a libFDX material graph that emits WGSL.

All future frontends must produce the same bundle contract. Provider-facing code
should still receive a `ShaderModuleDescriptor` selected from `ShaderBundle`;
normal game renderers should not care which frontend produced the artifacts.
