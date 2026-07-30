# libFDX Architecture

This document explains the durable structure of libFDX: who owns each kind of
code, which way dependencies point, and where platform/provider composition
happens. Gradle files are authoritative for the current project, artifact, and
task inventory; Java source is authoritative for package and type placement.

## System Model

libFDX separates portable contracts from selectable implementations:

```text
application code
  -> high-level framework features (assets, 2D, 3D, UI)
     -> provider-neutral runtime and graphics APIs
        -> small foundation contracts

launcher
  -> platform backend
  -> selected graphics/network providers
```

Arrows mean "depends on." Application code may use high-level or low-level
portable APIs, but those APIs never depend back on a concrete backend or
provider. The launcher is the composition boundary.

The backend constructs the typed `Fdx` root and passes it to
`ApplicationListener.create(Fdx)`. `Fdx` contains backend-owned runtime roots
such as application, displays, graphics, input, files, storage, networking, and
logging. Application-owned objects—including asset managers, batches, UI roots,
scenes, game worlds, and systems—are constructed explicitly.

## Repository Ownership

| Folder | Responsibility |
| --- | --- |
| `libfdx/framework/` | Portable first-party contracts and high-level framework features. |
| `libfdx/extensions/` | Optional providers and opt-in feature families. |
| `libfdx/backends/` | Platform lifecycle, windows/views, input/files, and provider attachment. |
| `libfdx/tools/` | Build-time tools, Gradle integration, and project generation. |
| `tests/` | Shared executable scenarios and platform runners. |
| `samples/` | Applications with shared game code and platform launchers. |
| `benchmark/` | Shared benchmark cases and platform runners. |
| `docs/` | Small cross-module design and domain guides. |

Repository-only tests, samples, and benchmarks remain outside published
framework code.

Framework modules use the shortest stable path that expresses ownership.
Provider families normally keep shared implementation in `core` and native or
platform integration below `platform`. Backends use a concrete platform name.
Samples and tests separate shared logic from platform launchers.

```text
libfdx/framework/<feature>
libfdx/extensions/<domain>/<provider>/core
libfdx/extensions/<domain>/<provider>/platform/<variant>
libfdx/backends/<platform>
samples/<category>/<sample>/{core,platform/...}
```

Create a variant module only when it represents a real implementation or
packaging choice. Folder names such as `framework`, `extensions`, `jni`, and
`ffm` describe repository ownership; they do not automatically become Java
package segments.

## Dependency Direction

Foundation modules contain small, provider-neutral contracts such as disposal,
errors, logging, futures, math, JSON, and reusable collections. Runtime-facing
application, display, input, file, storage, and networking APIs depend only on
portable lower layers.

Common graphics owns contexts, devices, resources, frames, descriptors, and
command recording. It does not depend on 2D, 3D, UI, a graphics provider, or a
backend. Higher-level rendering and UI modules build on common graphics.

Source data and provider resources remain separate ownership domains. For
example, decoded image data is not a texture; a loader that creates a GPU
resource belongs in a module that already depends on graphics.

Optional application features and external engines do not become backend
services. They may depend on the portable framework contracts their APIs
require, but libFDX does not depend back on them. In particular, ECS project,
world, scene, and editor contracts are owned by fdx-engine rather than a libFDX
extension. libFDX supplies the portable application, graphics, camera, input,
file, JSON, math, and collection contracts that such an engine may compose.

## Backends And Providers

A backend owns application lifecycle and platform integration. A provider
implements an optional capability such as a graphics API or network transport.
Backends attach compatible providers through provider-neutral setup contracts.

Dependencies determine which providers are available; launcher configuration
chooses the active provider. Selection must be explicit when more than one
compatible provider is available, and unsupported combinations fail clearly.
Provider changes normally require application restart.

Portable code uses common handles. Advanced access checks `providerId()` and
uses typed `as()` access only inside the owning resource lifetime. Matching
provider IDs alone do not prove that two resources share a compatible native
device or resource domain.

WGSL is the portable shader-language boundary. Applications may author it
directly or compile typed shader graphs to canonical WGSL; providers either
consume WGSL or translate it while creating shader modules. See
[Shaders](SHADERS.md).

The shader-graph core and runtime are provider-neutral layers above common
graphics. They compile semantic graphs to canonical WGSL and complete
interfaces, then resolve one technique pass through the common
`ShaderProvider` contract. Graphics providers consume ordinary shader
artifacts and do not depend on graph classes. Standard G2D/G3D techniques may
use this headless runtime; the standard G3D PBR implementation does so.

One versioned `.fdxgraph` owns required semantics plus optional editor state and
an optional compiled cache. Runtime loading selects an exact embedded entry or
compiles the semantics in memory; no dedicated build/package module or
companion shader artifacts are required.

The optional UI Kit editor depends on graph core and UI Kit only. Neither graph
core, runtime, standard PBR, nor providers depend on the editor. Frame/pass
scheduling remains in renderers and render graphs rather than shader nodes or
techniques.

## Runtime And Resource Ownership

Runtime creates and runs applications, presentation areas, input, files,
storage, and networking. Graphics owns GPU work. A `Display` and its
`GraphicsContext` therefore have related but distinct lifetimes.

Persistent resources belong to the device/resource domain that created them.
Frame, framebuffer, encoder, attachment, and pass views are borrowed from the
active frame and never survive it. Complete lifecycle and failure rules are in
[Common API](COMMON_API.md).

## Build And Publication

The `io.github.libfdx` Gradle plugin owns build DSL and Gradle task wiring. Font
generation, shader validation, web application generation, and native project
generation remain owned by their tool or backend modules. Those modules expose
command-line entry points that plugin tasks invoke without moving their
implementation classes into the plugin.

Framework, backend, tool, and Gradle plugin modules are published as separate
artifacts. Exact versions, project topology, publication coordinates, and task
names are defined by Gradle source.

Public packages start with `io.github.libfdx`. The module owning a concept owns
its package placement. Exact package names and declarations remain in Java
source and generated Javadocs.

## Invariants

- One portable concept has one owning module and one public type family.
- Dependencies point toward portable foundation contracts, never toward
  providers or backends.
- `Fdx` remains finite, typed, and backend-owned.
- Application-owned features remain explicit and optional.
- Providers are selected at setup boundaries and expose advanced access
  explicitly.
- Backend/provider setup may carry native handles; ordinary game APIs do not.
- Resources respect owner, resource-domain, frame, and disposal lifetimes.
- Hot paths use reusable or bounded storage rather than steady-state Java
  allocation.
- Exact inventories live in source/build metadata; this document changes only
  when a durable ownership or dependency rule changes.
