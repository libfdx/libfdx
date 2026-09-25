# Model level of detail

The public `io.github.libfdx.graphics.g3d.lod` API selects prepared geometry
before ordinary model rendering. It works with imported models and models
built in memory. It has no engine, ECS, editor, graphics-provider or native
simplifier dependency. Mesh simplification and asset loading remain separate.

## Basic use

Create the instances once, after their models are ready. The instances belong
to one logical object; their immutable model assets may be shared by other
objects. All levels must represent the same object in the same coordinate
space, with matching origin, scale and appearance.

```java
DefaultModelInstance base = new DefaultModelInstance(highDetailModel);
ModelLodModels levels = new ModelLodModels(base,
        new DefaultModelInstance(mediumDetailModel),
        new DefaultModelInstance(lowDetailModel));
ModelLodBinding lod = new ModelLodBinding(new ModelLodConfig(240, 80), levels);
ModelLodView mainView = new ModelLodView();
ProjectedBounds projectedBounds = new ProjectedBounds();
// Keep conservative full-detail bounds in the base instance's local coordinates.
BoundingBox bounds = new BoundingBox(new Vector3(-1, -1, -1), new Vector3(1, 1, 1));

// Each frame, after camera, viewport and object transforms are current:
projectedBounds.update(camera, renderViewportWidth, renderViewportHeight);
ModelInstance selected = lod.select(mainView, projectedBounds, bounds);
batch.begin(camera);
batch.render(selected);
batch.end();
```

Imports are from `graphics.g3d`, `graphics.g3d.lod`, `graphics.camera`, and
`math` under `io.github.libfdx`. `highDetailModel` and the other models can
come from `G3DAssetLoaders` or `ModelBuilder`. No file is required by selection.
The example assumes the application's normal batch/pass/depth setup.

Level zero is always full detail. Thresholds describe the larger projected
width/height of conservative full-detail bounds, in render-viewport pixels.
On first selection, below 240 pixels chooses LOD 1; below 80 chooses LOD 2.
Subsequent transitions use a ten-percent hysteresis band: LOD 1 is entered
below 216 pixels and left above 264 pixels. This prevents rapid switching
around a threshold. `withHysteresis(fraction)` returns a new immutable config;
zero disables the band. Thresholds must be finite, positive and strictly
decreasing. Up to eight reduced levels are supported.

`ProjectedBounds` supports perspective, orthographic, nonuniform/mirrored
affine transforms and all libFDX clip-depth ranges, including infinite reversed
depth. Update the camera first. Supply explicit pixel dimensions when an
orthographic viewport uses world units, or when rendering to an offscreen
target or a split viewport. An explicit matrix
overload supports application-owned camera-relative coordinate systems.
Unknown/invalid bounds or bounds crossing the eye/near plane keep full detail.
Projection does not cull offscreen objects; frustum/shadow admission stays with
the renderer. Keep bounds current for node motion and shader deformation.

## Views, availability and ownership

Keep one `ModelLodView` per object and camera/viewport/pass. Different views
share immutable `ModelLodConfig` but have separate transition histories.
There is no global camera registry. `desiredLevel()`, `renderedLevel()` and
`fallback()` expose the last decision. Call `reset()` when recycling a state.
For a custom renderer, `ModelLodView.select(config, projectedPixels)` provides
the standalone selector without model binding.

`ModelLodModels` borrows all instances and resources. Reduced slots may be
null while loading. Publish a ready instance with `levels.level(2, instance)`
between submissions; the next selection can use it. If the desired level is
unavailable or incompatible, the binding tries ready higher-detail levels,
ending at the base. It does not choose a coarser level than requested and does
not lose the desired-level history while resources load. A custom nonblocking
`ModelLodSource` can adapt an application's asset scope.

The base identity and number of slots are fixed for a binding. Recreate it
after replacing the base; reset reused view states. `config(newConfig)` can
change thresholds with the same slot count; views reset when they next see
the new config. `enabled(false)` uses the base without discarding levels.
`selectLevel(previewState, level)` explicitly previews a level, including when
automatic LOD is disabled, and still applies readiness/compatibility fallback.
Use a separate preview state to preserve other views' hysteresis.

All mutable LOD objects are single-thread confined. Loading workers may prepare
data, but publish ready GPU instances on the rendering thread. Selection never
starts a loader, parses metadata, simplifies geometry or uploads buffers.
Construction and publishing a level allocate CPU compatibility snapshots;
steady-state projection/selection reuse storage. The default compatibility
guard scans model nodes/materials; custom sources may use application-owned
revision tracking when the application can establish equivalent correctness.

The binding owns no GPU resources and has no dispose operation. Its owner
releases asset scopes/models only after submitted work is finished. Dropping
the binding/table releases its CPU references. Keep the base instance for
authored state, bounds and picking; selecting a level does not replace it.
Do not modify poses/materials or replace/dispose instances already queued in a
batch. Root transforms are copied to the selected candidate before submission.

See the interactive [ModelLodTest](../../../tests/README.md#model-lod) for a
full-detail comparison, live threshold controls, level previews and automatic
rendering checks.

## Moving parts and material changes

To generate reduced meshes from a loaded model, use the optional
[model LOD optimizer](../../extensions/graphics/meshoptimizer/README.md). It
provides portable Java simplification, cooperative preparation and incremental GPU upload;
the runtime selection API remains independent of that extension.

The default `ModelLodModels` guard accepts unchanged static
`DefaultModelInstance` geometry/materials with root movement. It falls back
when node transforms, material values/overrides or topology change, or when
skins, morph targets, animations, missing/ambiguous node names, unknown material attributes,
custom shaders or unknown instance implementations require behavior it cannot infer. It never silently
freezes a moving part at a lower level.

A vehicle body and independent wheel groups can each have a binding: shared
model assets, separate instances and transforms, and independent LOD state.
All levels retain the group's pivot. For named-node or material-slot mappings,
provide an explicit synchronizer:

```java
levels.synchronizer((baseInstance, candidate, level) -> {
    // Apply a validated mapping of the current pose/materials to this candidate.
    // Return false when the mapping cannot represent the current state.
    return synchronizeCompatiblePose(baseInstance, candidate, level);
});
```

This replaces the default static guard. The application must validate pivots,
node/material identities and any animation compatibility, keep bounds current,
and avoid editing shared model assets. The binding copies the root transform
after success. GPU/CPU skinning, morph and arbitrary animation transfer between
different LOD skeletons are not provided automatically.

The optional optimizer's generated chains have a known correspondence and supply
their own pose/material synchronizer. They preserve skinning and morph streams;
`bindAnimated` explicitly owns portable CPU morph geometry. See its
[animation lifecycle](../../extensions/graphics/meshoptimizer/README.md#animation-and-morph-generation).
`DefaultModelInstance.calculatePoseBounds` supplies root-local current bounds
after morph updates; use full detail when it reports unknown bounds. Authored
LOD files with different skeletons still require an application mapping.

For separately loaded animated levels with preserved correspondence, framework
`ModelLodAnimatedLevel(graphics, base, candidate)` provides an explicit owner.
It validates ordered node names/hierarchy, rest transforms, material slots, morph
defaults/counts and skin joints/inverse binds. It copies the base pose, root
transform and material overrides on `synchronize()` and updates only the candidate's
CPU morph geometry. It does not advance an animation clock or retarget skeletons.
Wire synchronization into the level source's explicit synchronizer and retain the
base if construction or synchronization fails. Keep shared model metadata immutable
while the owner is live. Dispose this owner before either borrowed instance's model
asset; it does not dispose those instances or their source models.

## Optional sidecar reader

```java
ModelLodDefinition definition = ModelLodDefinition.read(jsonText, "models/car.glb");
ModelLodConfig config = definition.config();
// definition.levels() is a read-only view of reduced asset paths/thresholds/counts.
// Load those assets using your own asset manager, then populate ModelLodModels.
```

The version-1 format is compatible with existing Xpe-generated sidecars:

```json
{"version":1,"levels":[
  {"asset":"car-lod1.glb","maxScreenPixels":240,"triangleCount":500},
  {"asset":"car-lod2.glb","maxScreenPixels":80,"triangleCount":120}
]}
```

Paths resolve inside the supplied base asset's directory. Absolute paths,
parent traversal, duplicate/base references, non-GLB reduced files and invalid
thresholds/counts are rejected. Input is limited to 65,536 characters; unknown
provenance fields are ignored. This reader performs no I/O and has no mandatory
asset-manager convention. Optional CPU simplifiers can generate prepared levels
as optional tooling without becoming dependencies of the runtime API.
