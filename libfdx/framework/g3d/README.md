# Forward rendering and color

For imported model behavior and ownership, see the [glTF subset](GLTF.md).
For prepared HDR environment lighting, see [image-based lighting](IBL.md).
For budgets, fitting and optional map reuse, see [directional shadows](SHADOWS.md).
For playback, crossfades, events and instance geometry ownership, see [animation and skinning](ANIMATION.md).

Material textures can carry per-slot `TextureCoordinates` through
`TextureMaterialAttribute`. The extended `Mesh.positionColor3D` overload appends UV1
and tangent XYZW to the existing compact PBR data. Existing overloads keep their
layouts. The standard PBR technique supports static/skinned variants of both layouts.
`StandardPbrVertexGraph` accepts UV1 and the post-skinning tangent; custom vertex
deformations may return a tangent alongside position and normal. Omitting that
optional output retains the incoming tangent.

`ForwardRenderGraph3D` is an application-owned scene pass with an owned `ModelBatch`
and color/depth target. It borrows the context, camera, environment, instances and
any shader provider supplied through `ModelBatchConfig`. Resize explicitly so scene
resolution may differ from the window, then render and present the resolved color:

```java
ForwardRenderGraph3D graph = new ForwardRenderGraph3D(graphics);
graph.resize(sceneWidth, sceneHeight);
graph.render(camera, environment, instances);
// In a later destination pass, with its viewport/scissor set:
blitter.draw(destinationPass, graph.color(), graph.origin(), true);
// Draw UI after scene composition; the caller ends destinationPass.
```

The extended constructor requests an exact color format/sample count and fails when
unsupported. `target("scene")` returns borrowed render/depth/resolve views, valid
until resize or disposal. `color()` is the resolved sampled image. `estimatedBytes()`
reports the target's unpadded allocation estimate. Dispose the graph before its
graphics context. Tone mapping and effects are separate composition choices.

`ForwardRenderPath3D` is the smaller alternative for a caller-owned batch and
`OffscreenTarget`. It begins/ends one scene pass, optionally preserving color/depth,
and borrows every resource. Both helpers end their pass after failure and retain no
frame handles. See [target ownership](../graphics/README.md).

`DefaultRenderTarget3D` borrows immutable arrays of colors, optional resolve views
and depth. `ModelBatch.begin(target, camera)` loads color, clears depth using the
active clip-depth convention, stores both, and resolves configured colors at end.
It forwards the entire layout; multiple color outputs require a matching shader
and provider. Use `begin(pass, camera)` for custom clear/store behavior or preserved
depth. The supplied pass remains caller-owned. A failed flush ends the active shader
and discards remaining queued work; `end()` releases batch frame references even
when rendering fails.

## Optional visibility culling

Enable `ModelBatch.frustumCulling(true)` outside `begin/end` to remove definitely
offscreen renderables at flush, before sorting, shader selection and GPU submission.
The default is disabled. It uses the current camera and transforms for that flush;
shadow passes use their own camera. Last nonempty flush counts are available through
`lastFlushCulledCount` and `lastFlushVisibleCount`. Empty flushes retain those counts.
Culling has CPU overhead, so measure the crossover for scenes with few objects.

Static `Renderable3D.cullingBounds` defaults to its local bounds. Skinned renderables
default to null (always visible), since bind-pose bounds cannot safely bound animation.
Applications may supply borrowed, conservative bounds after deformation and before
`worldTransform`, keeping them current through submission. Shader displacement needs
expanded bounds too. Null disables culling per renderable; invalid bounds or nonaffine
transforms are conservatively retained. Culling bounds do not change transparent
sorting bounds. This does not perform occlusion culling. `DefaultModelInstance`
updates prepared standard-skin bounds with its pose; see [animation](ANIMATION.md)
for CPU/GPU ownership and custom-deformation limits.

For an application-owned visibility list, reuse `FrustumCuller3D.update(camera)` and
`isVisible(renderable)`, or compact `DefaultRenderQueue3D.cull(culler)` in place.
The snapshot supports perspective/orthographic cameras, all clip-depth ranges and
infinite reversed-depth projections. Camera-relative plane tests preserve local
detail at large coordinates. Touching a plane stays visible. Updating/testing and
queue compaction reuse storage; all access is confined to one thread.

The standard PBR shader treats material factors as linear and base-color/emissive
image bytes as sRGB. It uses piecewise transfer for UNORM color textures and hardware
decoding for sRGB textures. Normal, metallic/roughness and occlusion textures require
linear storage. PBR outputs linear RGB into sRGB attachments (hardware encoding)
and floating HDR targets; its UNORM presentation path explicitly encodes RGB once.
CPU glTF baking and fallback projection use the same transfer functions.
`ColorTransfer` converts RGB; it does not tone-map HDR or change alpha.

The `pixel-color` rendered scenario exercises the forward graph with equivalent
material/texture encodings. The `offscreen-composition` scenario checks orientation,
alpha, explicit depth clear, depth preservation, resize and available resolve paths.

## Particle effects

See [particle effects](../graphics/particles.md) for presets, custom sprites,
lifetime curves, ownership, and transparency behavior.
