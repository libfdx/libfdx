# libFDX ECS

The ECS extension publishes one core module. It owns the portable ECS runtime,
single project attachment contract, standalone application adapter, world
phases, camera selection, reflection-free scene description, and deterministic
scene persistence. It does not depend on an editor, backend implementation, or
graphics provider, and there are no separate ECS scene or tooling artifacts.

A `World` remains usable without a running `Fdx` instance. Project hosting is
optional and receives the portable `Fdx` root only during initialization.
Every world has scene management even if an application never saves a scene.
Projects and hosts therefore need only `io.github.libfdx:ecs`.

## Runtime Model

A `World` owns entity handles, components, mappers, queries, entity lists,
events, managers, systems, commands, and reusable storage.

- Entity handles are opaque integers belonging to one world; `0` means no
  entity.
- Components are non-null `Component` objects keyed by an explicit Java class.
- Missing component lookup returns `null`; `require(...)` fails clearly.
- Structural mutations are deferred through world commands and become visible
  at explicit flush/update safe points.
- User-added managers are keyed by an explicitly supplied manager type. A
  second live or pending registration for that type returns `null` and leaves
  the first manager unchanged. The world-owned `SceneManager` is intrinsic and
  cannot be replaced or removed.
- Cached entity lists update when structural commands are flushed. A complete
  `World.clear()` releases class-keyed component caches and detaches previously
  obtained mappers and lists; code that reuses the world reacquires them after
  the clear is flushed.
- Events are queued and flushed explicitly.
- `System` defines lifecycle and enablement only. `UpdateSystem`,
  `RenderSystem`, and `UiRenderSystem` opt a registered system into the
  corresponding world phase.
- Enabled phase systems run in registration order. A system implementing more
  than one phase participates in each matching phase.
- A world update flushes pending work, invokes `UpdateSystem.update()`, and
  then flushes work recorded by those systems.
- World render methods invoke the matching render systems without advancing
  simulation or retaining borrowed graphics objects.

Hot code caches mappers, matchers, lists, listeners, managers, and systems, and
mutates existing components rather than replacing them each frame.

`GameComponent` and `UiComponent` classify entities for application routing.
Names and labels remain presentation data rather than routing data.

`TransformComponent` owns a reusable `Transform` value containing position,
scale, quaternion rotation, and a derived local matrix. Position and scale
mutators mark the matrix dirty, quaternion edits are detected, and `matrix()`
rebuilds lazily only when TRS data changed. The derived matrix is runtime state
rather than persistence data.

Core `CameraManager` is an ordinary world manager with independent nullable
`game()` and `ui()` slots. It retains user-owned `Camera` references without
creating, rendering, or disposing them, and clears both references when
detached from a world.

Core `SceneManager` is constructed with every world and is always available
through `world.scenes()`. It owns stable entity IDs, names and hierarchy; scene
documents and deterministic serialization; and the reflection-free component
descriptor and preset catalog. Its default catalog supports entity-only scenes
and core component data without project configuration. It is separate from
`CameraManager`: scene identity and persistence do not select a render camera.
Construction and entity-lifecycle synchronization are owned exclusively by
`World`; projects and hosts use only the instance returned by `world.scenes()`.

Exact mutation, event, and query behavior is covered by the ECS unit tests and
public declarations.

## Project Entry And Host Ownership

`EcsProject` is the single class-loader attachment point shared by ordinary
applications and external hosts:

```java
@FunctionalInterface
public interface EcsProject {
    void initialize(Fdx fdx, World world);
}
```

The project author implements only `initialize`. The host creates and owns the
world, constructs a fresh project entry for each independent Edit, Play, or
reloaded instance, and invokes that method once. Initialization adds project
components, managers, phase systems, and initial state to the supplied world.
When custom component data is persistable or editor-visible, initialization
adds its reflection-free descriptors and presets through `world.scenes()`.
There is no schema manager for a host to discover and no codec for a host to
construct. Project metadata such as its ID, entry class, assets directory, and
default scene stays in `fdx-project.json`, not on the Java interface.

Core `EcsApplication` is the normal `ApplicationListener` adapter. It creates a
world, initializes the project, updates the world with application delta time,
calls the world's game and UI render phases against the main graphics frame,
and tears the world down. External hosts use the same contract while selecting
their own update policy, render target, and optional camera override.

The host owns teardown. Clearing and flushing a world detaches its systems and
managers in reverse registration order. Project systems and managers release
only their owned resources during detach; closing a project class loader occurs
after those detach callbacks complete.

## Rendering And Cameras

A rendering project registers `RenderSystem` and, when needed,
`UiRenderSystem` implementations in its world. A host calls the world directly
with the provider-neutral graphics frame, color/depth targets, dimensions, and
an optional camera override:

```java
world.render(frame, colorTarget, depthTarget, width, height, cameraOverride);
world.renderUi(frame, colorTarget, depthTarget, width, height, cameraOverride);
```

The world resolves the camera once for that call and invokes enabled systems in
registration order. A non-null host override wins. Without an override,
`render(...)` uses `CameraManager.game()` and `renderUi(...)` uses
`CameraManager.ui()`; the resolved camera remains nullable when neither source
provides one. The frame and color target must be non-null when the selected
phase has registered systems; the depth target is optional. All are borrowed
for the current call and must not be retained or disposed by a system.

Camera-bearing projects register core `CameraManager` in the supplied world.
Its independent `game()` and `ui()` slots retain user-owned cameras. A
standalone host normally passes no override. An editor can render an Edit world
with its tool camera and render a Game world with no override, so project code
does not know whether the target belongs to an editor. Rendering does not
advance simulation or silently redirect an external target to the main
surface. There is no separate render-manager, render-context, or editor-purpose
API.

## Scene Management And Persistence

Every world exposes its intrinsic `SceneManager` through `world.scenes()`.
The manager supplies stable IDs, names, and parent relationships for entities;
owns the scene document and deterministic serializer; and maintains component
descriptors, property descriptors, presets, and optional transform, bounds, and
asset adapters. Core defaults allow a host to capture and restore entity-only
scenes and core component data without any project setup. No field, annotation,
classpath, or reflection scan defines custom persistent or editor-visible data.

Projects extend the manager's catalog during `EcsProject.initialize(...)`.
Every attached custom component must be registered as persistent or explicitly
marked transient before a scene is saved. Capture fails on an undeclared type
instead of silently losing project state. Applying a document likewise rejects
unknown persistent types. File paths, file dialogs, reading and writing files,
autosave, and editor policy belong to the host; both core `EcsApplication` and
an editor consume the same world-owned scene contract.

Persisted project, scene, entity, component, property, and preset identifiers
are stable data IDs rather than Java class names. Scene decoding validates the
complete deterministic JSON document before mutating a target world and rejects
incompatible versions, duplicates, unknown persistent types, invalid
references, and invalid parent graphs.

Normal platform launchers construct the project through core `EcsApplication`.
Desktop hosts that need dynamic loading consume a bundle created by the
[Gradle plugin](../../tools/gradle-plugin/README.md#ecs-project-bundles).

Project ABI 6 and the exact libFDX ABI are compatibility boundaries for those
desktop bundles. Core ECS, application, and scene-management contract classes
are parent-loaded, while project classes are loaded from the bundle. Hosts
reject mismatches rather than attempting an implicit migration.
