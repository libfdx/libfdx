# Shader Graph Sample

This sample shows the complete shader-graph path without making rendering
depend on a visual editor:

- `core` constructs a graph in Java or loads the equivalent semantic
  `.fdxgraph` asset.
- `core` replaces the standard PBR surface, supplies graph-owned values per
  material, and renders through `ModelBatch`.
- `core` also renders the standard graph-backed sprite technique through
  `SpriteBatch`.
- `editor` is an optional UI Kit host for the same semantic asset. It is not a
  dependency of the code-only application.
- `platform:desktop` selects GL, WGPU, Vulkan, or Direct3D 12.

The authored graph produces canonical WGSL and a reflected interface.
`ShaderGraphProvider` carries the complete compiled technique to a batch; a
WGSL string by itself is not enough because a batch also needs pass semantics,
entry points, resource layouts, vertex ABI, pipeline state, and render-target
compatibility.

## Run It

Use JDK 25 and run from the repository root:

```powershell
.\gradlew.bat :samples:graphics:shader-graph:platform:desktop:libfdx_desktop_jvm_wgpu_run
```

Replace `wgpu` in the task name with:

| Task suffix | Provider |
| --- | --- |
| `gl` | Desktop OpenGL |
| `vulkan` | Vulkan |
| `d3d12` | Direct3D 12 on Windows |

The default path loads
[`warm-pbr-surface.fdxgraph`](assets/shaders/warm-pbr-surface.fdxgraph).
Select direct Java authoring with:

```powershell
.\gradlew.bat "-Dlibfdx.sample.graphSource=code" :samples:graphics:shader-graph:platform:desktop:libfdx_desktop_jvm_wgpu_run
```

The canonical Java definition is
[`ShaderGraphSampleGraphs.codeAuthoredSurface()`](core/src/main/java/io/github/libfdx/samples/shadergraph/ShaderGraphSampleGraphs.java).
A test requires its serialized form to remain semantically identical and
compilable for both portable WebGPU and WebGL2 profiles.

## One Provider Contract, Technique-Specific Instances

Both batch configurations accept the common `ShaderProvider` interface. There
is no public `ShaderProvider2D`/`ShaderProvider3D` split:

```java
ShaderProvider modelProvider = new ShaderGraphProvider(
        graphics, pbrTechnique.technique());
ShaderProvider spriteProvider = new ShaderGraphProvider(
        graphics, StandardSpriteTechnique.compile(graphics));

var models = new ModelBatch(graphics,
        new ModelBatchConfig().shaderProvider(modelProvider));
var sprites = new SpriteBatch(graphics,
        new SpriteBatchConfig().shaderProvider(spriteProvider));
```

The standard PBR and sprite techniques still use separate provider instances.
They have different pass sets, vertex ABIs, resources, and pipeline state.
Sharing a public interface does not make one compiled technique interchangeable
with another.

Providers created by the application are owned by the application. Dispose the
borrowing batches first, then dispose the providers. Graph materials do not own
textures or samplers assigned to them.

## Customize the PBR Surface

`ShaderGraphSampleGraphs.codeAuthoredSurface()` preserves the renderer-owned
standard PBR input/output contract and adds material-owned `tint`, `warmth`,
and `emissive_gain` parameters. The sample creates independent
`GraphPbrMaterial` instances and assigns different values to prove the graph
parameters are applied per material.

The surface graph does not take over the renderer. `ModelBatch` continues to
own camera/object data, lighting, shadows, skinning, draw ordering, and
submission. Complete stage/program/technique graphs are available when a
surface replacement is not enough, while renderer and frame-graph scheduling
remain separate responsibilities.

Press `R` in the code-only sample to reload the serialized surface. Reload
parses and compiles a complete replacement, checks that existing material
instances remain schema-compatible, and only then atomically publishes the new
provider revision. A failure keeps the last-good technique active and reports
the error.

For a finite runtime validation, add
`-Dlibfdx.sample.validateReload=true`. The sample compiles an uncached document
in memory, embeds that result into the same document, reloads it as an exact
cache hit, verifies both paths produced identical WGSL and interfaces, then
proves an invalid replacement cannot displace the last-good shader. No file is
written by this check.

## Optional UI Kit Editor

Launch the visual editor for the same graph with:

```powershell
.\gradlew.bat "-Dlibfdx.sample.editor=true" :samples:graphics:shader-graph:platform:desktop:libfdx_desktop_jvm_wgpu_run
```

The editor compiles the semantic document through the same headless compiler.
**Save** writes shader semantics and optional editor state into the same
`.fdxgraph` without requiring a successful shader compile, so unfinished
graphs remain editable. **Save with compiled cache** additionally embeds the
current WGPU/WGSL result in that same file. A failed compile writes nothing;
layout-only saves retain valid cache entries, while semantic edits clear them.
No layout or shader-artifact sidecar is created. File selection and I/O belong
to this sample host; the reusable editor does not own project files or the
graphics provider.

## Regenerate and Validate the Asset

The generator writes a candidate asset under the core module's build
directory:

```powershell
.\gradlew.bat :samples:graphics:shader-graph:core:shader_graph_sample_generate_asset
```

Run the focused checks with:

```powershell
.\gradlew.bat :libfdx:extensions:graphics:shader-graph:runtime:test :samples:graphics:shader-graph:core:test
```

The sample has desktop launch targets for GL, WGPU, Vulkan, and Direct3D 12.
Metal requires a macOS host and is not validated by the Windows desktop
sample.

The focused tests also prove that direct Java and `.fdxgraph` authoring produce
the same runtime graph, that removing the optional compiled block returns to an
in-memory cache miss, and that stale or profile-mismatched entries are never
used. Complete program, multi-pass technique/variant, compute, SpriteBatch,
ModelBatch, and PBR coverage lives in the corresponding shader-graph runtime
and desktop visual tests. Existing handwritten WGSL uses the independent
shader runtime path and does not require a graph document.
