# Particle effects

`ParticleEmitter2D` (G2D) and `ParticleEmitter3D` (G3D) keep particles in fixed-capacity
CPU arrays. Applications own the emitters and supply their textures and rendering
batches explicitly. Emitters hold no GPU resources and need no disposal. Confine
configuration, simulation, and rendering to the application thread.

## Density volumes and solid particles

For fire and smoke with thickness from every view, use `ParticleVolume` and
`ParticleVolumeRenderer`. The emitters deposit world-space spherical density kernels;
a ray integrates emission and absorption through those kernels. Overlapping kernels
form a continuous medium. Looking down sees a cross-section of the plume, and moving
the camera changes the path through the medium. This is a particle-driven volume,
not a camera-facing flame texture, and not a full fluid-dynamics solver.

```java
ParticleEmitter3D fire = ParticlePresets3D.volumetricFire(800, 1.0f)
        .position(0, 0, 0).turbulence(0.8f, 5.0f);
ParticleVolume volume = new ParticleVolume(96, 128, 64)
        .bounds(-0.9f, -0.2f, -0.9f, 1.8f, 2.5f, 1.8f);
ParticleVolumeRenderer renderer = new ParticleVolumeRenderer(graphics, volume)
        .steps(128).density(5.0f);
Matrix4 inverseViewProjection = new Matrix4();

// Each frame, after opaque scene geometry, in a caller-owned active render pass:
fire.update(deltaSeconds);
volume.clear();
fire.deposit(volume, ParticleVolume.Medium.FIRE);
inverseViewProjection.setToMul(camera.inverseViewMatrix(), camera.inverseProjectionMatrix());
renderer.draw(pass, camera.combined(), inverseViewProjection, camera.clipDepthRange(), elapsedSeconds);
```

For 2D, use `ParticlePresets2D.volumetricFire` and `fire.deposit(volume, medium, worldZ)`.
The particles move in XY with spherical thickness around the supplied Z. An
orthographic camera preserves pixel proportions without deforming rotating sprites.
Use the same frame delta for simulation and effect time; elapsedSeconds is accumulated
simulation time, not frame count. Long pauses should be bounded by the application;
the demos clamp pauses to 100 ms and divide normal elapsed time into steps no larger
than 1/120 second. They never advance one fixed simulation tick per displayed frame.

Keep bounds tight around each plume: resolution is per axis, so doubling a bound's
world size halves its spatial detail. The dimensions allocate fixed storage, and
clear/packing visit only occupied cells. A 96×128×64 fire grid has a 768×1024 RGBA8
upload atlas (3 MiB). The atlas is an internal storage layout for a 3D field; it is
not particle artwork. Upload bandwidth and per-pixel ray samples are real costs.
`steps(32..192)` controls integration quality, and `density` controls optical thickness. `flameColors` changes the warm/hot thermal palette;
volume fire uses this palette and particle age rather than the sprite RGB endpoints.
Use a smaller grid and fewer steps for distant effects. Create resources during setup;
reuse them and dispose the renderer at shutdown. Renderer and volume are thread-confined.

`ParticleSolidRenderer` batches world-space octahedra for small embers, snow and debris.
Call `begin(pass, camera.combined(), camera.clipDepthRange())`, append particles with
`add(...)`, then `end()`. These solids have geometry in all three dimensions and do
not face the camera. Keep them small on screen; use application-owned detailed meshes
for close-up debris. The particle scenarios use density volumes for fire/smoke and
these solids for sparks/snow. The emitter getters also allow application-specific
renderers and entirely different particle alternatives.

To composite solids with fire and smoke, pass the same volume renderer at setup:

```java
ParticleSolidRenderer solids = new ParticleSolidRenderer(graphics, 512, renderer);
// Per frame: deposit all media and draw renderer first, using the same camera/pass.
solids.begin(pass, camera.combined(), camera.clipDepthRange());
// Append sparks, snow, debris, or custom colors from any number of emitters.
solids.add(x, y, z, radius, red, green, blue, alpha);
solids.end();
```

Every solid fragment integrates the same medium from the camera to its world-space
surface. Foreground particles retain their color; embedded or background particles
are attenuated according to the material in front of them. The composition preserves
foreground fire/smoke radiance while replacing what lies behind each solid surface.
There are no preset-specific visibility rules. Both particle scenarios use this path.
The two-argument constructor selects standalone solids without a participating medium;
arbitrary external mesh/billboard renderers are not automatically integrated.

Submit all solid emitters within one begin/end scope: center-depth sorting covers the
entire batch, including GPU upload boundaries, and only outward faces contribute.
The initial capacity sizes the upload buffer; CPU storage grows only when a new peak
particle count exceeds its allocation. Steady-state submission reuses storage.
Keep grids and renderer settings unchanged between the medium draw and solid end.
The solid renderer borrows its medium; dispose solids before disposing that medium.

Volume rendering depth-tests the first significant density sample against opaque
geometry and does not write depth. It does not sample scene depth to clip the rest
of the ray: embedded opaque geometry needs split volume bounds or an application
rendering solution with scene-depth integration.

For overlapping fire and smoke in independently resolved grids, construct one renderer
with `new ParticleVolumeRenderer(graphics, fireVolume, smokeVolume)` and call `draw`
once. Both grids are sampled along the same ray with shared transmittance. Smoke
between the camera and fire absorbs the fire's emission; smoke behind it does not
incorrectly cover foreground flames. Grid argument order does not affect visibility,
including where their bounds interpenetrate. Deposit multiple emitters into either
grid. Drawing separate volume renderers in a fixed order cannot provide this behavior.
Solid particles are alpha-blended back to front and do not write scene depth.
Intersecting translucent solids retain the usual center-sorting limitation; this
does not affect the per-fragment integration through fire and smoke.

## Lightweight textured effects

```java
Texture flame = ParticleSprite.FLAME.createTexture(graphics.device(), 128);
TextureRegion region = new TextureRegion(flame);
ParticleEmitter2D fire = ParticlePresets2D.fire(256, 1.0f)
        .position(0.0f, -0.6f)
        .emissionRate(80.0f)
        .spawnArea(0.3f, 0.05f);

// Each frame, inside the application's normal rendering callback:
fire.update(deltaSeconds);
batch.begin();
fire.render(region, batch);
batch.end();

// Dispose flame and batch when their owner shuts down.
```

For 3D, use `ParticlePresets3D.fire(256, 1.0f)`, a three-coordinate position,
and `fire.render(flame, camera, billboards)` inside the billboard renderer's
begin/end scope. `ParticleSprite` and `ParticleCurve` are in
`io.github.libfdx.graphics.particles`; emitters and presets belong to their G2D/G3D
packages. The textured path remains available for inexpensive stylized effects and custom artwork.
The executable particle scenarios use the volume/solid path described above.

Preset scale converts effect units to application units; all presets use Y up.
For pixel-coordinate rendering, a scale such as `100.0f` makes one effect unit
100 pixels. SpriteBatch itself consumes clip coordinates; use an application
coordinate adapter for pixel worlds. Keep width/height proportional to the target
viewport when drawing directly in clip space.

## Customize and extend

Presets are starting configurations of ordinary emitters. Chain their lifetime,
speed, direction/spread, gravity, size ranges, color endpoints, and rotation setters.
Use floating-point literals for the one-argument lifetime setter (`lifetime(2.0f)`):
`lifetime(int)` reads an existing particle's lifetime.

`spawnArea` takes full rectangular extents centered at the emitter position (a box
in 3D). Zero extents retain point emission. `drag` damps velocity exponentially per
second; zero disables damping. `aspectRatio` sets width divided by height, where
size is the height. Size and color curves replace linear lifetime interpolation:

```java
fire.curves(
    new ParticleCurve(0, 0.4f, 0.2f, 0, 1, 1), // grow briefly, then shrink
    ParticleCurve.LINEAR,
    new ParticleCurve(0, 0, 0.1f, 1, 0.7f, 1, 1, 0)); // fade in/hold/fade out
```

Keys alternate normalized time/value pairs. Times strictly increase from 0 to 1;
values remain within 0 to 1. Curves copy their keys once and can be shared. Size
and color curves select an interpolation fraction between the particle's sampled
start/end values. The opacity curve multiplies interpolated alpha; pass null to
disable that multiplier. Curves, drag, gravity, and aspect ratio affect live
particles immediately. Position and spawn ranges affect future particles. Changing
presets is easiest by creating a separate emitter; stop emission with
`emissionRate(0)` to let existing particles expire, or use `clear()` to reset.

Use any caller-owned straight-alpha texture instead of procedural artwork; the 2D
renderer accepts a `TextureRegion`, including atlas regions. Custom effects do not
need to modify an enum or the simulation. For a layered campfire, place separate
fire, smoke, and spark emitters at the same source and render smoke behind flames.
For bursts, set emission rate to zero and call `emit(count)`. Capacity bounds both
continuous emission and bursts; excess requested particles are dropped.

Procedural artwork is generated only during setup. A resolution of 128 is a useful
starting point; larger on-screen sprites may need more. DISC and RING have narrow
antialiased edges, while SMOKE deliberately has a feathered edge and internal
variation. Small snow/spark sprites still need enough screen pixels to resolve
their silhouette. Applications choose texture filtering for their own artwork.

## Textured-path transparency and limits

The 3D emitter sorts reusable indices back-to-front by camera depth without changing
simulation indices or allocating each frame. Sorting is O(n log n) and is local to
one emitter; overlapping separate emitters must be ordered by the application.
BillboardRenderer3D tests depth but does not write it, so opaque scene geometry
occludes particles without particles blocking later transparent draws. These are
alpha-blended billboard effects, without volumetric simulation, collision, or
scene-depth soft intersections. Caller-provided textures remain borrowed and must
outlive submission; the emitter never disposes them.
