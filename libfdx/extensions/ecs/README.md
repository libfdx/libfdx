# libFDX ECS

The ECS extension is a user-created, pure-Java feature. It does not depend on
`Fdx`, a backend, a graphics provider, or an editor.

The extension is split into two sibling modules:

- `core` owns the portable ECS runtime and has no libFDX runtime dependency.
- `tooling` builds on `core` with project, schema, scene, and editor-hosting APIs.

## Runtime Model

A `World` owns entity handles, components, mappers, queries, entity lists,
events, managers, systems, commands, and reusable storage.

- Entity handles are opaque integers belonging to one world; `0` means no
  entity.
- Components are non-null `Component` objects keyed by an explicit Java class.
- Missing component lookup returns `null`; `require(...)` fails clearly.
- Structural mutations are deferred through world commands and become visible
  at explicit flush/update safe points.
- Cached entity lists update when structural commands are flushed.
- Events are queued and flushed explicitly.
- Enabled systems update in registration order.
- A world update flushes pending work, updates systems, and then flushes work
  recorded by those systems.

Hot code caches mappers, matchers, lists, listeners, managers, and systems, and
mutates existing components rather than replacing them each frame.

`GameComponent` and `UiComponent` classify entities for application routing.
Names and labels remain presentation data rather than routing data.

`TransformComponent` owns a reusable `Transform` value containing position,
scale, quaternion rotation, and a derived local matrix. Position and scale
mutators mark the matrix dirty, quaternion edits are detected, and `matrix()`
rebuilds lazily only when TRS data changed. The derived matrix is runtime state
rather than persistence data.

Exact mutation, event, and query behavior is covered by the ECS unit tests and
public declarations.

## Optional Tooling

The separate tooling module lets one ECS project run as an ordinary libFDX
application and be hosted by external tools without making game code depend on
an editor.

An `EcsProject` owns immutable metadata, exposes an explicit `EcsProjectSchema`,
and creates independent `EcsProjectRuntime` instances.
Each runtime owns one world and its mutable render state. Tools may create an
inactive edit runtime and a separately updated play runtime from the same
project.

Runtime lifecycle, update, and render callbacks are separate. Render callbacks
record against the provider-neutral targets in a reused `EcsRenderContext` and
do not advance simulation or silently redirect tool rendering to the main
surface.

Schemas register entity/hierarchy adapters, component descriptors, property
descriptors, presets, and optional transform/camera/bounds/asset adapters.
Discovery is explicit: no field, annotation, classpath, or reflection scan
defines editor-visible data.

Persisted project, scene, entity, component, property, and preset identifiers
are stable data IDs rather than Java class names. Scene decoding validates the
complete deterministic JSON document before mutating a target world and rejects
incompatible versions, duplicates, unknown persistent types, invalid
references, and invalid parent graphs.

Normal platform launchers construct the project directly through
`EcsProjectApplication`. Desktop tools that need dynamic loading consume a
bundle created by the
[Gradle plugin](../../tools/gradle-plugin/README.md#ecs-project-bundles).

The tooling and libFDX ABI values are exact compatibility boundaries for those
desktop bundles. Hosts reject mismatches rather than attempting an implicit
migration.
