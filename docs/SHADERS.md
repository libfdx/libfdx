# libFDX Shader Architecture

This document is the architecture guide for libFDX shader authoring,
translation, runtime compilation, and provider shader module creation.

For module ownership and dependency direction, see [ARCHITECTURE.md](ARCHITECTURE.md).
For public graphics API contracts, see [COMMON_API.md](COMMON_API.md).

## 1. Goal

libFDX shader authoring is WGSL-only. A developer writes one portable WGSL
shader and passes it to `GraphicsDevice.createShaderModule(...)`.
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

`fdx-native` is reserved for future provider-specific shader pipeline metadata.
It does not make GLSL, SPIR-V, or MSL an authoring source in the portable
graphics API. Current graphics providers still receive WGSL and use
Tint/runtime compilation when their GPU API needs another language.

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

WGSL is the only authored shader source accepted by public shader descriptors
and bundles. If a provider needs GLSL, GLSL ES, SPIR-V, or MSL, it must request
runtime compilation from WGSL through the Tint-backed `runtime/fdx/core`
compiler path during shader-module creation. Generated target descriptors are
internal handoffs from the compiler path to the provider and must not become a
second user-maintained source of truth.

If a provider receives WGSL and needs translation, but the active runtime does
not provide the compiler capability, it must fail clearly and name the missing
capability and target.

## 6. Compiler Backend

Tint is the first compiler backend. Dawn/Tint source is not committed to this
repository and libFDX native tasks do not build it locally. The internal
`:libfdx:runtime:fdx:fdx-build` module consumes checksum-verified static
dependency packages from the sibling `fdx-natives` release project, then links
those packages into the platform runtime `fdx` artifacts.
The shared C bridge exposes a small ABI that is linked into platform `fdx`
runtime artifacts when the platform opts into shader compilation.

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

| Provider ID | `ShaderTarget` | Generated/consumed language |
| --- | --- | --- |
| `webgpu` | `WEBGPU_WGSL` | `ShaderLanguage.WGSL` |
| `wgpu` | `WGPU_WGSL` | `ShaderLanguage.WGSL` |
| `webgl` | `WEBGL_GLSL_ES` | `ShaderLanguage.GLSL` |
| `gles` | `GLES_GLSL_ES` | `ShaderLanguage.GLSL` |
| `gl` or `opengl` | `OPENGL_GLSL` | `ShaderLanguage.GLSL` |
| `vulkan` | `VULKAN_SPIRV` | `ShaderLanguage.SPIRV` |
| `metal` | `METAL_MSL` | `ShaderLanguage.MSL` |
| `directx` or `d3d*` | `DIRECTX_HLSL` | future HLSL language support |

Built-in renderer shader modules are authored as WGSL-only descriptors. They
must not attach handwritten GLSL, SPIR-V, or MSL fallbacks. WGPU/WebGPU
providers consume WGSL directly. GL, Vulkan, and Metal providers compile WGSL
through `runtime/fdx/core` when they need native backend shader code. PSP does
not include Tint and does not use the runtime shader compiler. iOS C Metal
currently accepts MSL through its provider path but does not register a runtime
compiler provider, so WGSL-only built-in shaders require a future iOS compiler
bridge before that backend can run them.

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
| Runtime fdx native dependency and CMake task ownership | `:libfdx:runtime:fdx:fdx-build` |
| Desktop runtime shader compiler packaging | `:libfdx:runtime:fdx:platform:desktop` |
| Android runtime shader compiler packaging | `:libfdx:runtime:fdx:platform:android` |
| Web runtime shader compiler packaging | `:libfdx:runtime:fdx:platform:web` |
| iOS C Metal shader module creation | `:libfdx:backends:ios_c` with authored MSL until an iOS compiler bridge exists |
| WGSL profile validation, Gradle task wiring, and user project DSL | `libfdx/tools/gradle-plugin` |
| Provider-specific shader module creation | selected graphics provider or backend-owned provider |

There is no runtime `tools/shader` module. Runtime shader compilation is a
runtime fdx platform capability for providers that cannot consume WGSL directly.
Tint/Dawn dependency resolution is handled by `:libfdx:runtime:fdx:fdx-build`,
which imports static libraries from `fdx-natives` packages for all runtime fdx
native task names. Web runtime fdx builds enable the compiler by default
because WebGL depends on WGSL-to-GLSL ES translation for built-in renderers.

## 11. Validation Requirements

For shader runtime/compiler changes:

- validate Java compile for `runtime/fdx/core` and `graphics/api`;
- validate affected high-level renderer modules such as `graphics/g2d` and
  `graphics/g3d`;
- validate affected providers whose shader creation path changed;
- run the `shader-runtime` test to create a WGSL-only shader module and render
  pipeline through the active `GraphicsDevice`;
- run the `shader-scene` test to render a procedural WGSL-only shader scene
  through the active provider;
- run affected WGSL-authored built-in renderer scenes such as `outline-2d`, `outline-3d`, `fog-of-war-2d`,
  `particles-2d`, `fog-3d`, `fog-of-war-3d`, `skybox-3d`, `billboard-3d`, `particles-3d`, `point-light-3d`, `spot-light-3d`,
  `shadow-map-3d`, `cascade-shadow-map-3d`, and `model-skinning` when shader effects or high-level renderer shader creation
  paths change;
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

1. WGSL is the only shader authoring source of truth.
2. Built-in `SpriteBatch` and `ModelBatch` should pass WGSL descriptors and rely
   on provider setup-time translation where needed. `ModelBatch` owns both the
   default PBR WGSL path and the skinned PBR WGSL variant. The default PBR
   shader can consume a single `DirectionalShadowMap3D` or up to four
   `CascadedShadowMap3D` shadow textures, with split-distance based
   per-fragment cascade selection from the cascade driver camera and
   computed per-cascade bias values. Cascaded bias starts from a world-space
   base bias and is converted to normalized depth bias per cascade.
3. Public shader authoring is WGSL-only. GLSL, SPIR-V, and MSL descriptors are
   generated target artifacts owned by the runtime compiler path, not user
   fallback sources.
4. Tint is the first compiler backend and is packaged by default in desktop,
   Android, and web runtime fdx native builds. Those builds link against
   prebuilt static Tint/FreeType packages from `fdx-natives`.
5. PSP is intentionally outside the Tint runtime compiler path.
6. HLSL remains a future generated target for DirectX, not a second authoring
   source.

## 13. Future Tooling

WGSL is the only source-of-truth language accepted by the graphics API. Future
tools may help produce WGSL, but generated GLSL, SPIR-V, MSL, or HLSL must
remain target artifacts after WGSL enters the provider path.

Future tooling may include:

- importers that convert existing shader libraries to WGSL before runtime;
- a libFDX material graph that emits WGSL;
- validation tools that check WGSL profile portability before launch.

All future frontends must produce a `ShaderModuleDescriptor` or a compatible
runtime compiler request. Provider-facing code should still receive explicit
source or bytecode for the selected provider.
