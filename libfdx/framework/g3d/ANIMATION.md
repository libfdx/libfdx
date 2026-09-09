# Animation and skinning

An `AnimationClip` owns immutable channel/sampler data and optional application event
markers. An `AnimationController` borrows a clip and instance, owns reusable pose
storage, and runs on the application/graphics thread before drawing. Node animation
requires `DefaultModelInstance`; keep one controller responsible for each animated
node. Clips and the underlying model can be shared by independently posed instances.

```java
DefaultModelInstance instance = new DefaultModelInstance(model);
AnimationController animation = new AnimationController(instance);
animation.play(idleClip, true);
animation.crossFade(walkClip, true, 0.25f);
// Before collecting renderables each frame:
animation.update(deltaSeconds);
```

Crossfades blend translation and signed scale linearly, and rotation using normalized
shortest-path quaternion interpolation. Both clips advance during an ordinary fade.
Interrupting a fade captures its current mixed pose; a new fade starts there without
a discontinuity. Channels absent from the incoming clip blend to their first bound
authored defaults. A first fade can start from the instance's existing TRS pose.
Programmatic matrix defaults must be affine and decomposable without shear. Zero
scale is supported, though a matrix alone cannot recover rotation on collapsed axes.
Imported channels retain their authored TRS values.

`play` starts at zero, a zero-duration fade acts as `play`, and `time` seeks without
events and cancels a transition. Nonlooping playback clamps to the clip's endpoints;
looping playback wraps. `stop` preserves the visible pose. All times are finite seconds.
Negative updates seek backward without events and cancel transitions; zero updates
do nothing. Pose sampling uses reusable storage and applies the hierarchy/palettes
once after sampling, rather than once per channel.

## Events

Add ordered `AnimationClip.Event` markers when constructing a clip. They are
application metadata, not an imported glTF event extension. The controller listener
runs synchronously after pose/time commit. Forward updates deliver markers in
`(previousTime, currentTime]`, including crossed loops. End markers precede zero
markers at a loop boundary; equal-time markers retain their supplied order. Starting
or seeking to zero does not emit zero markers. Only the incoming clip emits markers
during a fade. `onLoop` aggregates crossed loops and `onComplete` fires once when
nonlooping playback first reaches its end (on a positive update for a zero-length clip).

The default limit is 256 marker callbacks per update; `maxEventsPerUpdate` accepts
1 through 65,536. Exceeding it throws before advancing playback, so subdivide a large
update or deliberately raise the limit. Markers are not silently dropped. Starting,
seeking or stopping playback, or replacing the listener from a callback, cancels the
remaining old callbacks. Recursive `update` is rejected. A throwing callback leaves
the committed pose/time intact and aborts further delivery.

## GPU and CPU geometry

The standard PBR and directional-shadow shaders share the same four-influence
affine skin transform. Positive finite weights are normalized; all-zero weights use
the unchanged bind position. The GPU palette supports up to 64 bones, subject to a
lower configured PBR maximum. Skin palettes map bind vertices to model space using
joint model transforms and inverse binds, then apply the instance transform. The
skinned mesh node transform is not applied again. Custom shaders/deformations need
their own compatible visible and shadow paths. The CPU PBR projection fallback does
not automatically deform GPU-skinned inputs or sample shadow maps.

For CPU skinning, use an explicitly owned animator:

```java
CpuSkinnedModelAnimator animation = new CpuSkinnedModelAnimator(graphics, instance);
animation.play(walkClip, true);
animation.update(deltaSeconds); // pose, then owned vertex uploads
// After the last draw using its copies, before disposing model/context:
animation.dispose();
```

Construction copies each skinned part's retained, nonindexed PBR or position/color
geometry and binds those copies to this instance. This costs separate CPU arrays
and vertex buffers per part. Other instances and shared model buffers remain intact.
glTF skin imports retain the needed source attributes. Unsupported/missing retained
data fails during construction, releasing partial copies before changing bindings.
Only one CPU animator may bind an instance. Disposal restores the model meshes,
preserves instance material overrides and releases every copy; it is idempotent.

CPU updates start from immutable bind snapshots and update both uploaded vertices
and retained positions/normals/tangents, so CPU shading sees the current deformation.
Using `controller()` or changing instance nodes directly requires `updateSkinning()`
before drawing. If a listener throws after a pose commit, the animator synchronizes
geometry before propagating the exception. The low-level `CpuSkinningMeshUpdater`
borrows an exclusively owned mesh; it is unsuitable for independently posed shared
meshes. Treat exposed mesh source arrays as read-only even when an updater changes them.

## Bounds and shadows

`ModelNodePart` prepares per-joint bind-position boxes when usable source/influence
data exists. `DefaultModelInstance` transforms their union with the current palette,
without rescanning vertices each frame. With normalized nonnegative weights, this
conservatively encloses all blended vertices, including a bind-position bucket for
zero-weight vertices. These instance-owned bounds drive sorting, optional frustum
culling and shadow fitting/fade. They are in model space before the instance transform.

Skinned renderables without prepared bounds remain uncullable by default. Directly
constructed `Renderable3D` objects still require the caller to maintain bounds.
Use `nodeCullingBounds` for borrowed expanded bounds or `null` to disable frustum
rejection, and `resetNodeCullingBounds` to restore the automatic choice. Custom
displacement requires appropriate bounds and matching shadow geometry; an override
does not change transparent sorting bounds. Reprepare after changing source geometry
or influences. Update poses before recording any scene/shadow draws, and increment
the caster revision when using [cached directional shadows](SHADOWS.md).

The `animation-integration` scenario compares CPU and GPU deformation and shadows,
two poses sharing one model, crossfade interruption, event delivery, zero weights,
non-unit weight sums and motion outside bind bounds. It also checks CPU vertices
against independent matrix/influence evaluation. The [glTF fixtures](GLTF.md) cover
imported interpolation separately.
