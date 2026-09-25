# Model LOD optimizer

Generate reduced, indexed meshes from a loaded `Model`, then select them
with the [model LOD runtime](../../../framework/g3d/LOD.md). Generation is an
explicit preparation operation; it never runs inside `ModelBatch` or LOD
selection. No GLB is written and the original model is not modified.

`meshoptimizer_core` owns the portable quadric optimizer, API, snapshots, packing
and GPU upload. `new ModelLodOptimizer()` uses `PortableMeshLodSimplifier` and
needs no JNI library, external executable, threads, or gltfpack. The same Java
implementation is used on desktop, Android and TeaVM, independent of GL, Vulkan,
Direct3D 12 or WGPU. There is no platform-specific optimizer module.

## Load, prepare, publish

Load editing data explicitly. Ordinary model loading continues to omit CPU
attributes that its renderer no longer needs; morph meshes retain their deformation inputs.

```java
G3DAssetLoaders.register(assets, graphics);
assets.registerLoader(Model.class, G3DAssetLoaders.modelLoader(graphics, true));
AssetLease<Model> source = assets.acquire(AssetDescriptor.of("models/car.glb", Model.class));
// Poll assets.update(...) until source.isLoaded(); retain the lease.
```

After loading, capture on the model's owning thread while its geometry, hierarchy
and materials are stable:

```java
ModelLodInput input = ModelLodInput.capture(source.asset());
ModelLodSettings settings = ModelLodSettings.balanced();
ModelLodOptimizer optimizer = new ModelLodOptimizer();
// On an application-owned CPU worker:
PreparedModelLods prepared = optimizer.prepare(input, settings);
// Safely publish the completed result back to the graphics thread.
```

For asynchronous runtime generation use an application-owned executor:

```java
ModelLodExecutor workers = ModelLodExecutors.create(2); // default platform adapter
// Check capacity before capturing another large source snapshot.
ModelLodJob job = workers.trySubmit(input, settings); // null means temporarily full
// In later owner-thread updates:
workers.update();
if (job != null && job.isDone()) {
    PreparedModelLods prepared = job.take(); // never blocks; propagates failure
    // Begin the graphics-thread upload once, then release the job.
}
// On cancellation: job.cancel(); on owner shutdown: workers.dispose().
```

Desktop and Android use a bounded JVM pool; TeaVM's web backend binds the same
factory to real Web Workers. The optimizer and packing algorithms are identical.
Workers receive detached geometry and integer metadata, never model/material/GPU
handles. Completion is safely published and polled on the owner thread; there are
no worker-thread application callbacks. The executor admits at most `workers()`
jobs. A cancelled JVM job keeps its slot until CPU cancellation is acknowledged;
browser cancellation terminates the corresponding worker. Neither disposal nor
cancellation waits for CPU completion. After cancellation the source may be
released because the worker only retains detached CPU data. Successful generated
models still borrow their source as described below.

The browser uses a separately compiled Java worker embedded only when needed,
with transferable message buffers and completed vertex staging. It requires
blob-worker support/permission; unavailable or crashed workers fail explicitly.
There is no synchronous/caller-runs fallback. Snapshot capture and pose sampling,
message serialization and restoring transferred staging still run on the owner;
asynchronous optimization is not a hard frame-time guarantee. Dispose generated
models/uploads before source leases and cancel jobs before disposing the executor.

The lower-level cooperative API remains available for custom schedulers and tools
that explicitly choose event-loop preparation:

```java
ModelLodPreparation preparation = optimizer.begin(input, settings);
// In subsequent loading updates, drain cheap stages for roughly 2 ms:
long started = System.nanoTime();
do {
    if (preparation.step(512)) {
        PreparedModelLods prepared = preparation.result();
        // Begin the graphics-thread upload once, then release preparation.
        break;
    }
} while (System.nanoTime() - started < 2_000_000L);
```

The budget counts vertices, faces, or candidate collapses; it is not a hard
millisecond limit. Exact input compaction, ordering, shared-boundary preparation,
channel compaction and vertex packing are resumable. Snapshot copies, individual
buffer allocations, high-valence topology checks and garbage collection can still
stall a step. Large assets should use a loading phase or
a worker where available. Cancellation is checked between preparation steps;
discard a cancelled CPU job, and dispose any unfinished GPU upload.

Upload between draw submissions on the graphics thread:

```java
ModelLodUpload upload = prepared.beginUpload(graphics); // single use
// Each update: upload.step(1024 * 1024); continue on later frames until true.
GeneratedModelLods generated = upload.take();          // only after completion
upload.dispose();                                    // ownership was transferred
DefaultModelInstance base = new DefaultModelInstance(source.asset());
ModelLodBinding binding = generated.bind(base);
// Use binding.select(view, projection, sourceBounds) with ordinary ModelBatch.
```

`step` budgets vertex upload bytes for one mesh per call. Buffer allocation and
index upload are indivisible. On failure or cancellation, dispose the upload;
partially uploaded meshes are released. Publish/swap a complete generated chain
between submissions, then detach and dispose the previous chain. The permanent
`ModelLodOptimizerTest` demonstrates this lifecycle, worker dispatch and UI error
handling end to end.

`GeneratedModelLods` owns its reduced GPU meshes. Unchanged primitives borrow
the source mesh and range; identical reduced results across levels share one
uploaded mesh owned by the chain. Requested level numbers and thresholds remain
stable even when a level needs no new mesh. It **borrows** the source's
meshes/materials/textures, so retain the source asset lease until after all generated
models are disposed. A binding borrows generated models and must not outlive
them. CPU snapshots and prepared results are garbage collected; discard them
after publication. Snapshotting temporarily copies all CPU channels, so run it
as a deliberate loading/editing operation rather than during steady rendering.
Generated results can be bound to multiple independent instances;
retain one owning generated chain until its last consumer has finished drawing.

## Settings

Configure one to eight reduced levels, independently generated from the original
snapshot, with strictly decreasing triangle ratios and screen-pixel thresholds.

```java
ModelLodSettings settings = new ModelLodSettings(new ModelLodTarget[] {
    new ModelLodTarget(.50f, .01f, 240), // keep 50%; error limit .01; select below 240 px
    new ModelLodTarget(.20f, .03f, 100),
    new ModelLodTarget(.07f, .08f, 40)
}, .1f, false, .5f, 10, 1, true, true);
// hysteresis, lockBorders, normalWeight, uvWeight, colorWeight,
// optimizeCache, optimizeFetch
```

| Option | Meaning |
| --- | --- |
| Triangle ratio | Requested fraction of each source primitive's triangles; never zero. |
| Error limit | Relative simplification error, including weighted attributes. Lower limits protect appearance but can stop reduction early. |
| Screen pixels | Runtime switch threshold using the original bounds' projected diameter. |
| Hysteresis | Stabilizes runtime transitions near thresholds. |
| Lock borders | Keeps open mesh boundaries fixed; useful for adjoining surfaces. |
| Normal / UV / color weights | Larger values penalize changes to those attributes more strongly. UV weight applies to both UV sets. |
| Cache / fetch optimization | Reorders triangles for vertex reuse and compacts/orders surviving vertices for fetch locality. Unused vertices are removed even when fetch reordering is disabled. |

Each source primitive is simplified independently. Matching positions shared by
different primitives on the same model node are locked automatically, including
partitions created at the unsigned-16 vertex limit. This preserves material and
partition joins without locking every outer border. Different nodes have separate
coordinate frames: use `lockBorders` for independently generated adjoining surfaces,
or prepare their CPU meshes in a common rigid frame with
`MeshLodGeometry.protectSharedVertices`. Locking does not stitch mismatched input
boundaries or repair nonmanifold source data. Custom simplifiers must honor the
snapshot's `vertexLocked` constraints.

The balanced preset uses normal/UV/color weights and protects attribute seams.
Two-sided seams may simplify along their shared edge only when both sides collapse together and pass
the topology, attribute-error and orientation checks. The link condition is checked
across physical positions as well as attribute wedges. Junctions with three or more
distinct attribute wedges remain fixed. Exact welding compares every retained
channel, including tangents and baked values: no unsafe position-only welding is
used. High-seam inputs can remain almost unchanged in conservative mode.

Reduced models and `report(level)` are numbered from 1; the original stays in its
source lease. Reports give actual triangles, vertices, draw parts, maximum relative
error across primitives, and whether the ratio was achieved. Counts include
repeated node draws. A requested triangle count is a target, not a guarantee;
topology, seams and the error bound take precedence. The portable error is the
maximum accepted normalized, area-weighted quadric RMS (geometry plus attributes).
It is not a Hausdorff bound or a metric that can be compared directly with another
optimizer's error setting. Error is local to each primitive, not a guarantee
of a particular screen-space appearance. Inspect the
result with its real lighting/materials before choosing thresholds.

## Supported models

Static, skinned and morph standard PBR triangle models with retained CPU attributes are supported.
Node hierarchy, local transforms/pivots, material references, normals, UV0/UV1,
tangents/handedness and all retained color/PBR/emissive channels are preserved.
Parts stay separate across material boundaries. Large results are split at the
renderer's unsigned-16 vertex limit without dropping triangles. Non-triangle
topology, missing channels and ambiguous node names are rejected explicitly.
Skinning retains four joint influences, skeleton mappings and inverse bind
matrices. Morph POSITION/NORMAL/TANGENT deltas, target names/defaults, node weight
overrides and animation clips survive every remap and mesh partition. This is
not an animated subscene merger or an animation retargeter.

The portable implementation uses [Garland/Heckbert quadric error metrics and
attribute gradients](https://www.cs.cmu.edu/~garland/quadrics/). It collapses to
existing endpoints, protects split-attribute seams and boundaries, checks the
manifold link condition, rejects duplicate/inverted/degenerate faces and UV
orientation changes, and uses a deterministic priority queue. Its cache-aware
triangle traversal and first-use vertex packing run after reduction. All
surviving channels retain their source values. This is a conservative
optimizer, not a claim of quality/performance parity with gltfpack.

These collapse guards do not certify or repair an input mesh: pre-existing
duplicate faces, overlapping surfaces and nonmanifold geometry can remain.
Inspect both the source and generated meshes when checking topology. Repeated
generation is deterministic within one runtime; floating-point evaluation across
different Java targets can produce different collapse choices near equal costs.

Retained glTF loading generates angle-weighted indexed tangents when the source
omits them. This avoids false per-triangle tangent seams; authored tangents are
unchanged, and ambiguous mirrored charts retain the per-corner fallback. This
generator does not claim MikkTSpace equivalence.

`MeshLodGeometry.transform` and `combine` support CPU preparation of static rigid
groups. Callers must group compatible materials and leave independently moving
parts separate. The helpers preserve layouts, inverse-transpose normals and
mirrored winding/handedness. They do not flatten gameplay/animation hierarchies.

The indexing, cache and fetch processing order follows the
[meshoptimizer documentation](https://github.com/zeux/meshoptimizer) as an algorithm
reference; that library is not a dependency.

## Animation and morph generation

Capture explicitly chooses the deformation coverage before CPU preparation:

```java
ModelLodInput input = ModelLodInput.capture(source.asset(),
        new ModelLodAnimationSettings(5, 32, -1, 1));
// samples per clip, maximum total pose samples, minimum/maximum morph weights
```

The default is five evenly spaced samples per clip, including both endpoints,
plus the default pose and each morph target at both configured extremes. The
sample cap fails explicitly if this coverage does not fit. Capture is synchronous
loading work: keep it outside latency-sensitive frames. Each sampled pose adds
three position floats per original vertex and eleven quadric doubles per working
vertex during simplification. Increase coverage deliberately for large models.
`input.maxDeformationSamples()` reports the largest per-primitive sample count.

Welding compares skin influences, morph deltas and sampled trajectories. The
optimizer evaluates the **worst** geometric quadric error across the sampled
poses and rejects surviving triangles that flip or collapse in those poses.
Retained endpoint attributes preserve the deformation function of each surviving
vertex. Pose sampling does not certify intermediate frames, cubic overshoot,
arbitrary procedural bone motion, simultaneous morph combinations or weights
outside the configured range. Normal/UV/color penalties use the authored bind
attributes; the geometric pose metric is not a posed-normal error bound. Preview
your real clips and choose a quality-limited result when necessary.

Skin-only generated chains can use `generated.bind(base)` and ordinary GPU
skinning. That binding synchronizes node poses and material overrides. Morph
chains need explicitly owned instance geometry:

```java
DefaultModelInstance base = new DefaultModelInstance(source.asset());
AnimationController controller = new AnimationController(base);
controller.play(source.asset().animations().get(0), true);
AnimatedModelLods animated = generated.bindAnimated(graphics, base);
ModelLodBinding binding = animated.binding();

// Before queuing draws, on the graphics thread:
controller.update(deltaSeconds);
ModelInstance selected = animated.select(view, projection);
// Submit selected with ordinary ModelBatch.

// After all queued draws finish:
animated.dispose();  // owned per-instance deformation meshes
generated.dispose(); // shared generated models
source.dispose();    // original asset lease, last
```

Only the base controller advances time or emits events. Selection copies its
current pose/weights and updates only the selected reduced instance. Independent
bindings have independent mutable geometry. Rebuilding can borrow a live base
`CpuMorphModelAnimator` through the three-argument `bindAnimated` overload; dispose
the binding before that borrowed animator. The framework animator also works
without the optimizer, for an ordinary morphed model.

Use `animated.select` for the efficient path: it evaluates conservative per-joint
and per-target interval bounds without updating full-detail vertices, then deforms
only the selected level. The bounds support current negative and positive morph
weights and remain conservative between the optimizer's sampled poses. Explicit
`selectLevel` also updates just its resolved level. `updateBase()` is available
when drawing the full-detail comparison or inspecting its deformed CPU vertices;
calling it every frame adds full-detail morph work. Keep authored custom culling
bounds conservative, or reset them to automatic bounds.

Morph evaluation is a portable CPU path with explicit GPU buffer updates,
including morph-before-skinning for combined models. Skin-only meshes retain GPU
skinning. This version does not provide GPU morph blending, skeletal reduction,
blendshape retargeting, compressed morph streams or a GLB file writer. CPU morph
cost scales with selected vertices and target count. Authored tangent deltas are
preserved; missing tangent deltas keep the base tangent basis, so normal-mapped
deforming assets should author tangent targets for accurate shading. Loading
weight tracks supports LINEAR, STEP and CUBICSPLINE with ordinary controller
seeking and crossfades, following the
[glTF morph/skin semantics](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html).

Static `MeshLodGeometry.transform/combine` still reject deformation-bearing input.
Consumers that bake/export static scenes must keep their animated-content guards;
using this generation API does not make static baking preserve animation.

### File adapter sampling

`ModelLodAnimationSampler` also accepts a CPU-only `Model` containing hierarchy,
rest transforms/default morph weights, skins and clips. A file adapter can call
`capture(nodeId, positions, skin, joints, weights, targets)` without constructing
GPU meshes. The sampler borrows metadata, evaluates the same bounded pose coverage
as runtime generation, and returns `MeshLodDeformation` (or null for rigid input).
Keep borrowed metadata immutable until capture finishes.

Deformation streams remain in the **original vertex domain** after simplification
and vertex-fetch optimization. A writer must read skin and morph attributes using
`output.sourceVertex(vertex)`, not the reordered output vertex index. Preserve
the original hierarchy, clips, inverse binds and defaults when writing animated
files. `MeshLodGeometry.transform/combine` cannot flatten these animated domains.
For separately loaded levels with preserved correspondence, use framework
`ModelLodAnimatedLevel`; consumers own file encoding and asset loading.

## Interactive example

Open **ModelLodOptimizerTest** in the platform test chooser, or run on desktop:

```powershell
.\gradlew.bat :tests:platform:desktop:libfdx_desktop_jvm_tests_gl_run '-Dlibfdx.test.name=ModelLodOptimizerTest'
```

The desktop example defaults to the portable optimizer on a CPU worker. Set
`-Dlibfdx.test.cooperativeLod=true` to exercise the event-loop flow.
Web/mobile use the portable cooperative flow by default.

Choose Helmet, Duck, Dragon or the animated Bend sample, or enter a path resolvable by the asset manager.
Edit per-level triangle percentages, error percentages and switch pixels; use
Quality, Balanced or Small presets and the scrollable advanced settings. Click
Generate, then preview any level beside the original or choose Auto and vary
distance. Preview buttons restore a close view. Settings take effect when the
next Generate completes. Invalid settings or rejected loads keep the current
working model/chain. The source file is never saved or overwritten.
