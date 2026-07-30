# libFDX Shaders

WGSL is the portable shader-language boundary in libFDX. Applications may
author WGSL directly or build a typed shader graph. A graph is compiled to
canonical WGSL plus a complete reflected interface before it reaches a
graphics provider.

```text
handwritten WGSL ---------\
                           -> canonical WGSL + reflected interface
typed shader graph -------/      -> common shader artifact
                                      -> selected provider target
```

Generated languages are provider artifacts, not additional user-maintained
source files. Compiler-library and platform SDK types do not leak through the
common graphics API.

## API Packages

Shader APIs are grouped by responsibility instead of sharing the
`io.github.libfdx.graphics` root:

| Package | Responsibility |
| --- | --- |
| `io.github.libfdx.graphics.shader` | Languages, profiles, modules, bundles, stages, and source validation. |
| `io.github.libfdx.graphics.shader.reflection` | Reflected entry points, resources, parameters, value types, and ABI layouts. |
| `io.github.libfdx.graphics.shader.target` | Target identities, artifacts, translation, verification, remaps, registries, and cache keys. |
| `io.github.libfdx.graphics.shader.runtime` | Provider requests, resolved passes, parameter blocks, and bound resource values. |
| `io.github.libfdx.graphics.shadergraph.model` | Semantic graph data, builders, codecs, types, ports, edges, and editor-neutral metadata. |
| `io.github.libfdx.graphics.shadergraph.node` | Node definitions, properties, and registries. |
| `io.github.libfdx.graphics.shadergraph.compiler` | Graph/program/technique compilers, diagnostics, source maps, and compiled results. |
| `io.github.libfdx.graphics.shadergraph.document` | The single versioned `.fdxgraph` document and deterministic codec. |
| `io.github.libfdx.graphics.shadergraph.cache` | Optional embedded compiled-cache keys, artifacts, and interface summaries. |
| `io.github.libfdx.graphics.shadergraph.ir` | Typed intermediate representation. |
| `io.github.libfdx.graphics.shadergraph.technique` | Program, technique, pass, pipeline-state, and variant declarations/codecs. |
| `io.github.libfdx.graphics.shadergraph.standard` | Standard graph and technique factories. |

Runtime loading/providers and the optional editor remain in their own
`shadergraph.runtime` and `shadergraph.ui` packages. The graphics root
continues to own general device, resource, pass, and pipeline contracts. There
are no compatibility aliases in the former flat shader packages.

## Shader Graph Boundary

The shader graph is a headless authoring and compilation system. Java code,
single-file `.fdxgraph` assets, and runtime loading do not depend on UI Kit.
The optional UI Kit module edits the same semantic document and stores layout
in its optional in-file `editor` block. Editor data never participates in the
semantic hash or shader behavior.

Graph scope is layered:

| Scope | Responsibility |
| --- | --- |
| Function/subgraph | Reusable typed computation. |
| Surface | Material outputs such as base color, normal, metallic, roughness, emissive, occlusion, and alpha. |
| Vertex, fragment, or compute | Complete programmable-stage inputs, outputs, resources, and operations. |
| Program | Linked stages, entry points, and their complete interface. |
| Technique | Named passes, fixed pipeline state, static variants, and explicit capability fallbacks. |

A technique is the complete programmable shader definition, but it is not a
whole-renderer or frame graph. Cameras, scenes, visibility, draw ordering,
attachments, pass scheduling, resource transitions, and submission remain
renderer/render-graph responsibilities. A batch asks a `ShaderProvider` for one
named pass at a time.

The generated WGSL string is not sufficient by itself for SpriteBatch or
ModelBatch. The runtime also needs the reflected resource layout, entry points,
pipeline state, vertex ABI, selected variant, default resources, and exact
render-pass compatibility. `ShaderGraphProvider` carries that complete
technique contract and returns a `ResolvedShaderPass`.

## Authoring And Runtime Paths

`ShaderGraphBuilder`, program/technique builders, codecs, and compilers are
usable directly from Java. The normal render flow is:

```text
graph/program/technique
  -> optional one-file compiled-cache selection
  -> semantic compilation and canonical WGSL on a cache miss
  -> optional target compilation and verification
  -> ShaderGraphProvider
  -> ShaderRequest for one pass and variant
  -> resolved native module, resource layout, and bounded cached pipeline
  -> SpriteBatch or ModelBatch draw
```

SpriteBatch and ModelBatch use the same public `ShaderProvider` vocabulary:

```java
var compiled = StandardSpriteTechnique.compile(graphics);
var provider = new ShaderGraphProvider(graphics, compiled);
var sprites = new SpriteBatch(
        graphics,
        new SpriteBatchConfig().shaderProvider(provider));
```

The common interface does not imply that one compiled technique fits every
renderer. Standard PBR and sprite techniques have different passes, vertex
ABIs, resources, and pipeline state, so applications normally create one
`ShaderGraphProvider` instance for each technique while keeping both variables
typed as `ShaderProvider`. See the
[shader graph sample](../samples/graphics/shader-graph/) for code-authored and
serialized graphs used by both batch families.

The configured provider is borrowed: dispose the batch before disposing the
provider, and do not replace or mutate provider state during an active
`begin()`/`end()` scope. A default ModelBatch owns its internally created
standard provider and disposes it with the batch.

The standard PBR implementation is graph-backed. `StandardPbrTechnique`
exposes replaceable surface, post-skinning vertex, and final-lighting graphs;
`GraphPbrMaterial` stores parameters and resources from that technique's
surface schema. The renderer still owns camera, object, environment, skinning,
shadow, texture-slot, and draw bindings. Its WGSL template is a composition
scaffold and reflected ABI baseline, not a second runtime PBR implementation.

Handwritten WGSL remains first-class. Wrap a complete
`ShaderModuleDescriptor` in `ShaderGraphRenderProgram`, put it in a
`ShaderGraphRenderTechnique`, and resolve it through the same
`ShaderGraphProvider`. Handwritten and graph-generated variants may coexist in
one technique. This is the migration path for existing shaders; there is no
requirement to reconstruct handwritten WGSL as nodes.

`ShaderGraphComputeProvider` is the equivalent runtime owner for compute
programs and techniques. Compute is capability-gated and is rejected before
pipeline creation on profiles such as WebGL2.

Serialized authoring uses one versioned `.fdxgraph` for graph, program,
compute-program, render-technique, and compute-technique semantics. The
required semantic block is sufficient for correctness. An optional `editor`
block restores visual layout, and an optional `compiled` block caches
target/profile-specific results. There are no companion WGSL, reflection,
manifest, source-map, diagnostic, or layout files.

`ShaderGraphRuntimeLoader` parses the document and chooses an exact embedded
entry using semantic/dependency/compiler/library/profile/capability/target/
environment/options/interface/pass/variant identity. A miss compiles the
required semantics in memory. Runtime loading itself never rewrites the asset;
the UI editor's explicit **Save with compiled cache** action may embed the
result back into the same file.

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

Graph target generation uses the same rule. `ShaderGraphCompiler` always emits
canonical WGSL. Extensible `ShaderTargetCompiler` and `ShaderTargetVerifier`
registries may then produce and verify WGPU/WebGPU WGSL, OpenGL/WebGL
GLSL/GLSL ES, Vulkan SPIR-V, Metal MSL, DirectX HLSL, or a custom target. Every
artifact records its compiler, verifier, options, consumer environment, entry
point/binding remaps, and matching reflected interface. Translation success
alone never counts as target verification.

The optional in-file compiled block retains target artifacts and their
provider-neutral interface summaries. Cache decoding verifies hashes and
rejects invalid entries independently; invalid optional cache data never
replaces or repairs invalid required semantics. Absence of a compatible entry
is a cache miss followed by normal in-memory compilation, not a shader
fallback.

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

Graph and technique values are immutable after construction. Material values
and resource bindings use explicit identity/revision tracking. Whole-technique
replacement prepares every module and interface before publishing one new
provider revision; a failed replacement leaves the last valid technique
active. Native module creation, pipeline creation, replacement, and disposal
must occur at a graphics-thread/setup boundary required by the active provider.
Resolved passes are borrowed and must not survive a provider revision or the
owning provider.

`ShaderGraphProvider` owns the shader modules and pipelines it creates and uses
bounded caches. The application owns an explicitly constructed provider and
must dispose it after all borrowing batches and queued work are finished.
Graph material instances do not own textures or samplers bound into them.

## Validation

Compiler changes require focused compiler/ABI tests and the affected provider's
shader-creation path. Renderer shader changes require a rendered scenario, not
only successful compilation. Compare affected providers under the same scene,
viewport, assets, input, and frame conditions.

Project configuration is described in the
[Gradle plugin](../libfdx/tools/gradle-plugin/README.md#shaders).
