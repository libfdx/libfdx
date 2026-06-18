# libFDX Shader Architecture

This document is the architecture guide for libFDX shader authoring,
translation, runtime compilation, and provider shader module creation.

For module ownership and dependency direction, see [ARCHITECTURE.md](ARCHITECTURE.md).
For public graphics API contracts, see [COMMON_API.md](COMMON_API.md).

## 1. Goal

libFDX shader authoring is WGSL-first. A developer should usually write one
portable WGSL shader and pass it to `GraphicsDevice.createShaderModule(...)`.
The selected provider then keeps WGSL as-is or translates it at shader-module
creation time when the native graphics API needs another shader language.

The target flow is:

```text
WGSL source
  -> ShaderModuleDescriptor.wgsl(...)
     -> provider creates a shader module
        -> WebGPU/wgpu uses WGSL directly
        -> GL/WebGL/GLES compiles WGSL to GLSL or GLSL ES
        -> Vulkan compiles WGSL to SPIR-V
        -> Metal compiles WGSL to MSL
        -> DirectX/HLSL later
```

The public promise is the libFDX shader pipeline. Tint is the first compiler
backend, but Tint types are not part of the common graphics API.

## 2. Non-Goals

- Do not generate Java shader classes for normal built-in renderer shaders.
- Do not check in translated GLSL, SPIR-V, MSL, or HLSL copies of every shader.
- Do not require users to add separate shader compiler artifacts when the
  selected backend already packages the needed runtime `fdx` native capability.
- Do not force platforms that do not need shader translation, such as PSP, to
  package Tint or the shader compiler.
- Do not expose Tint, SPIRV-Cross, shaderc, DXC, or platform SDK compiler types
  through the common graphics API.
- Do not claim target support until the compiler path and provider path are both
  implemented and validated.

## 3. Source Layout

User-project portable shader source belongs under:

```text
src/main/fdx-shaders/**/*.wgsl
```

libFDX modules that own built-in renderer shaders keep their WGSL source inside
the owning renderer classes. That keeps the built-in shaders available on TeaVM,
where ordinary dependency resources are not a reliable runtime lookup path for
framework internals. The provider still compiles that WGSL if the active
graphics API cannot consume WGSL directly. Generated translated shader resources
are build/test diagnostics only, not framework source.

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

## 5. Runtime Compilation Contract

`runtime/fdx/core` owns the provider-neutral shader compiler contract. It is an
optional runtime capability supplied by backends that package the matching
native `fdx` runtime library.

The runtime compiler input is WGSL source plus:

- requested target language;
- shader stage;
- entry point name;
- GLSL profile options for GL-family targets.

The runtime compiler output is:

- text for WGSL, GLSL, GLSL ES, MSL, and future HLSL;
- SPIR-V bytes for Vulkan;
- diagnostics for failures.

Providers compile at shader-module creation/setup time, never inside a render
loop. Implementations must cache native compiler setup and may cache translated
outputs by source hash, target, stage, entry point, compiler backend, compiler
version, and options.

If a provider receives a native descriptor that it already supports, it uses it
directly and does not invoke the runtime compiler. These direct descriptors are
the escape hatches:

```java
ShaderModuleDescriptor.glsl(label, vertexSource, fragmentSource);
ShaderModuleDescriptor.spirv(label, vertexWords, fragmentWords);
ShaderModuleDescriptor.msl(label, mslSource);
```

If a provider receives WGSL and needs translation, but the active runtime does
not provide the compiler capability, it must fail clearly and name the missing
capability and target.

## 6. Compiler Backend

Tint is the first compiler backend. Dawn/Tint source is downloaded into Gradle
build output and is not committed to the repository. The shared C bridge exposes
a small ABI that is linked into platform `fdx` runtime artifacts when the
platform opts into shader compilation.

Web Emscripten Tint builds use conservative `-O0` code generation because the
optimized Wasm path has been unstable. Treat any higher optimization level as a
separate validation target.

Required first Tint target set:

| Target | Tint direction |
| --- | --- |
| WebGPU/wgpu | WGSL input, WGSL output or preserved source |
| WebGL/GLES | WGSL input, GLSL output configured for GLSL ES |
| OpenGL | WGSL input, GLSL output configured for desktop GLSL |
| Vulkan | WGSL input, SPIR-V output |
| Metal | WGSL input, MSL output |
| DirectX later | WGSL input, HLSL output |

If Tint cannot translate a valid libFDX shader for a target, the runtime
compiler must report a diagnostic and fail that shader module. It must not emit
a partial or mismatched shader.

## 7. Provider Responsibilities

Provider target mapping:

| Provider ID | `ShaderTarget` | Native descriptor language |
| --- | --- | --- |
| `webgpu` | `WEBGPU_WGSL` | `ShaderLanguage.WGSL` |
| `wgpu` | `WGPU_WGSL` | `ShaderLanguage.WGSL` |
| `webgl` | `WEBGL_GLSL_ES` | `ShaderLanguage.GLSL` |
| `gles` | `GLES_GLSL_ES` | `ShaderLanguage.GLSL` |
| `gl` or `opengl` | `OPENGL_GLSL` | `ShaderLanguage.GLSL` |
| `vulkan` | `VULKAN_SPIRV` | `ShaderLanguage.SPIRV` |
| `metal` | `METAL_MSL` | `ShaderLanguage.MSL` |
| `directx` or `d3d*` | `DIRECTX_HLSL` | future HLSL language support |

WGPU/WebGPU providers consume WGSL directly. GL, Vulkan, and Metal providers may
compile WGSL through `runtime/fdx/core` when their native descriptor source is
not supplied. PSP does not include Tint and does not use the runtime shader
compiler.

Provider-specific setup remains explicit through provider IDs and capabilities.
Advanced provider compiler features may exist only as documented provider
features.

## 8. Reflection And Bindings

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
`RenderPipelineDescriptor.shaderReflection(...)` carries the metadata to
providers that need setup-time resource layout decisions.

## 9. Hot Reload Rules

Editor hot reload uses the same runtime compiler capability, but compilation
should happen only on file changes or explicit editor actions.

Rules:

- Compile off the render thread when using a native bridge, process, or daemon.
- Apply new shader modules and pipelines on the render thread or the provider's
  required graphics thread.
- Keep the old pipeline alive until the replacement is fully created.
- If compilation or pipeline creation fails, keep rendering with the previous
  valid pipeline and surface diagnostics to the editor.
- Dispose replaced shader modules, pipelines, bind group layouts, and related
  provider resources after they are no longer in use.
- Do not allocate compiler request/result objects in a game frame loop. Editor
  tools may allocate during explicit compile actions.

## 10. Module Ownership

Stable ownership:

| Area | Owner |
| --- | --- |
| `ShaderLanguage`, `ShaderProfile`, `ShaderTarget`, descriptors, reflection values | `:libfdx:graphics:api` |
| Built-in 2D shader WGSL sources | `:libfdx:graphics:g2d` |
| Built-in 3D shader WGSL sources | `:libfdx:graphics:g3d` |
| Runtime shader compiler Java contract | `:libfdx:runtime:fdx:core` |
| Shader compiler native C ABI source | `:libfdx:runtime:fdx:platform:shared` |
| Desktop runtime shader compiler packaging | `:libfdx:runtime:fdx:platform:desktop` |
| Android runtime shader compiler packaging | `:libfdx:runtime:fdx:platform:android` |
| Web runtime shader compiler packaging | `:libfdx:runtime:fdx:platform:web` |
| WGSL profile validation, Gradle task wiring, and user project DSL | `libfdx/tools/gradle-plugin` |
| Provider-specific shader module creation | selected graphics provider or backend-owned provider |

There is no runtime `tools/shader` module. Runtime shader compilation is an
optional runtime fdx platform capability, and Tint/Dawn source is resolved by
`:libfdx:runtime:fdx:platform:shared` under build output.

## 11. Validation Requirements

For shader runtime/compiler changes:

- validate Java compile for `runtime/fdx/core` and `graphics/api`;
- validate affected high-level renderer modules such as `graphics/g2d` and
  `graphics/g3d`;
- validate affected providers whose shader creation path changed;
- run runtime shader compiler tests when the compiler backend or ABI changes;
- run Gradle plugin tests or a plugin sample task when task wiring changes;
- run provider render validation for every provider whose built-in shaders or
  shader creation path changed.

For visual renderer changes:

- use a known-good GL baseline when GL is in scope;
- compare WebGPU/wgpu, Vulkan, Metal, WebGL/GLES, and DirectX targets only when
  they are in scope and available;
- report `PASS`, `BLOCKED`, or `NOT RUN` for every matrix cell in scope;
- never claim visual parity from shader compilation alone.

## 12. Implementation Status

1. WGSL remains the first source-of-truth shader language.
2. Built-in `SpriteBatch` and `ModelBatch` should pass WGSL descriptors and rely
   on provider setup-time translation where needed.
3. Native descriptors remain supported for users who want direct GLSL, SPIR-V,
   or MSL control.
4. Tint is the first compiler backend and is packaged only by platforms that
   opt into shader compilation.
5. PSP is intentionally outside the Tint runtime compiler path.
6. HLSL and additional frontends remain future work.

## 13. Future Frontends

WGSL is the first source-of-truth language. The architecture must still leave
room for future authoring frontends.

Future frontends may include:

- GLSL for importing existing shader libraries;
- HLSL for DirectX-oriented projects;
- MSL for Metal-only native work;
- Slang or another multi-target frontend;
- a libFDX material graph that emits WGSL.

All future frontends must produce a `ShaderModuleDescriptor` or a compatible
runtime compiler request. Provider-facing code should still receive explicit
source or bytecode for the selected provider.
