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
     -> Tint compiler backend
        -> WGSL for WebGPU/wgpu
        -> GLSL ES for WebGL/GLES
        -> GLSL for desktop OpenGL
        -> SPIR-V for Vulkan
        -> MSL for Metal
        -> HLSL for DirectX later
  -> ShaderBundle metadata
  -> active provider selects its generated ShaderModuleDescriptor
```

The public promise is the libFDX shader pipeline. Tint is the first compiler
backend, not the public API contract.

## 2. Non-Goals

- Do not make normal game runtimes depend on a shader translator.
- Do not make graphics providers silently translate shader languages as a hidden
  side effect of `GraphicsDevice.createShaderModule(...)`.
- Do not require Metal, Vulkan, GL, WebGPU, and future DirectX users to maintain
  separate hand-written shader source files for the same portable shader.
- Do not expose Tint, SPIRV-Cross, shaderc, DXC, or platform SDK compiler types
  through the common graphics API.
- Do not claim target support until the compiler path and provider path are both
  implemented and validated.

## 3. Source Layout

User-project portable shader source belongs under:

```text
src/main/fdx-shaders/**/*.wgsl
```

libFDX modules that own built-in renderer shaders keep their source inputs under
the owning module:

```text
libfdx/graphics/g2d/src/main/shaders/**/*.wgsl
libfdx/graphics/g3d/src/main/shaders/**/*.wgsl
```

Those built-in inputs are translated by module-local Gradle tasks into generated
Java bundle classes under `build/generated/sources/libfdxShaders`. The generated
classes are build output, not source-of-truth files.

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
- Project-facing generation should translate WGSL into requested target outputs
  through the same shader tool API used by libFDX built-ins.
- Generated reports belong under `build/reports/libfdx/shaders`.
- Generated Java/source artifacts belong under Gradle build output folders.
- Checked-in generated shader code is allowed only for libFDX bootstrap shaders
  when a module intentionally documents that choice.

Current built-in generation tasks are module-owned:

| Task | Owner | Output |
| --- | --- | --- |
| `:libfdx:graphics:g2d:generate_g2d_shader_bundles` | `graphics/g2d` | `GeneratedSpriteBatchShaders` |
| `:libfdx:graphics:g3d:generate_g3d_shader_bundles` | `graphics/g3d` | `GeneratedModelBatchShaders` |

The tasks use `:libfdx:tools:shader:core` and the host
`libfdx_shaderc_cli` executable built from Tint/Dawn source resolved under that
module's `build/third-party` directory.

Current built-in shader status:

| Renderer path | Source-of-truth | Generated/selected targets |
| --- | --- | --- |
| `SpriteBatch` | WGSL files in `graphics/g2d/src/main/shaders` | WebGPU/wgpu WGSL, WebGL/GLES GLSL ES, desktop GL GLSL, Vulkan SPIR-V, Metal MSL |
| `ModelBatch` position/color | WGSL file in `graphics/g3d/src/main/shaders` | WebGPU/wgpu WGSL, WebGL/GLES GLSL ES, desktop GL GLSL, Vulkan SPIR-V, Metal MSL |
| `ModelBatch` PBR | WGSL file in `graphics/g3d/src/main/shaders` | WebGPU/wgpu WGSL, WebGL/GLES GLSL ES, desktop GL GLSL, Vulkan SPIR-V, Metal MSL |

The build tool must be deterministic. Generated bundles should include enough
metadata to reproduce or diagnose the output:

- source path;
- source hash;
- profile ID;
- target list;
- compiler backend ID;
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
  -> Tint compiler backend
  -> translated outputs and diagnostics
```

This keeps compiler tooling out of normal game classpaths and works well for
desktop editors. Platform-specific runtime/editor modules may replace the
process boundary with JNI, FFM, Emscripten, or a local compiler daemon without
changing the editor-facing API.

Editor runtime compilation must cache outputs by source hash, profile, target,
compiler backend, compiler version, and options. Recompilation should happen on
file changes or explicit editor actions, never every frame.

## 8. Compiler Backend Model

The shader tool should own a small compiler backend contract internally.
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

The first backend is Tint through Dawn/Tint source resolved into the Gradle
build directory. The source checkout is build output and must not be committed.
The shared native bridge exposes a small C ABI and command-line executable; Java
runtime/editor integration uses platform modules for the ABI that each runtime
can support.

Required first Tint target set:

| Target | Tint direction |
| --- | --- |
| WebGPU/wgpu | WGSL input, WGSL output or preserved source |
| WebGL/GLES | WGSL input, GLSL output configured for GLSL ES |
| OpenGL | WGSL input, GLSL output configured for desktop GLSL |
| Vulkan | WGSL input, SPIR-V output |
| Metal | WGSL input, MSL output |
| DirectX later | WGSL input, HLSL output |

If Tint cannot translate a valid libFDX shader for a target, the shader tool must
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
pipelines, and validation errors without provider-specific parsing. Generated
built-in bundles currently emit binding and vertex input metadata from the WGSL
source. `RenderPipelineDescriptor.shaderReflection(...)` carries the metadata to
providers that need setup-time resource layout decisions.

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
| WGSL profile validation, shader generation API, shared compiler bridge, and native Tint bridge source | `:libfdx:tools:shader:core` |
| Android runtime/editor shader compilation | `:libfdx:tools:shader:platform:android_jni` |
| Web runtime/editor shader compilation | `:libfdx:tools:shader:platform:web` |
| Desktop runtime/editor shader compilation | `:libfdx:tools:shader:platform:desktop_ffm` |
| Gradle task wiring and user project DSL | `:libfdx:tools:gradle-plugin` |
| Provider-specific shader module creation | selected graphics provider or backend-owned provider |
| Optional editor/runtime compiler API | tooling module, not `graphics/api` |

The parent `libfdx/tools/shader` and `libfdx/tools/shader/platform` folders are
grouping folders only. They must not contain parent Gradle build files or source
sets. Gradle should include the nested modules directly and must not remap them
with custom directory remaps.

## 13. Validation Requirements

For shader tooling changes:

- validate Java compile for `graphics/api` and `tools/shader`;
- run `:libfdx:tools:shader:core` tests;
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

## 14. Implementation Status

1. Freeze this architecture document and align canonical docs. This is active
   source-of-truth documentation and should be updated with shader architecture
   changes.
2. Tighten `ShaderBundle` and `ShaderModuleDescriptor` so documented targets and
   implemented descriptor selection match.
3. `:libfdx:tools:shader:core` owns profile validation,
   deterministic bundle generation, Tint process compilation, and the shared C
   ABI/native source.
4. Tint-backed generation currently covers preserved WGSL, GLSL ES, desktop
   GLSL, SPIR-V, and MSL. HLSL remains a future target.
5. Built-in Gradle generation is wired for `graphics/g2d` and `graphics/g3d`.
   Project-facing generation remains a plugin-facing follow-up.
6. Built-in `SpriteBatch` and `ModelBatch` now consume generated bundle classes
   with generated reflection metadata. `ModelBatch` PBR no longer uses separate
   handwritten GL GLSL or Vulkan SPIR-V fallback sources.
7. Desktop GL, desktop WGPU, desktop Vulkan, Android GLES, Android WGPU, WebGL
   JS, and WebGPU JS have rendered the generated PBR model path in local
   validation. Native Metal generated MSL validation through the iOS C Metal provider
   remains pending.
8. Optional editor/runtime compilation exists through the compiler request/result
   API and process/native bridge model.
9. Platform runtime integration modules exist for Android JNI, desktop FFM, and
   web Emscripten; each platform still needs full packaging/runtime validation
   before release claims.
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
