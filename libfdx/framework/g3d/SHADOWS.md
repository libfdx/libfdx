# Directional shadows

`DirectionalShadowMap3D` owns a packed RGBA8 shadow texture, depth-pass shader and
batch. `CascadedShadowMap3D` owns one to four such maps, fits them to a camera and
directional light, snaps their light-space centers to texels, and extends depth
coverage for submitted casters. Dispose these resources on the graphics thread
before their context. `Environment3D` borrows them for PBR sampling.

`ShadowBudget3D` validates the cascade count, square resolution and logical byte
budget before allocation. Its low/balanced/high setup presets trade coverage and
resolution against storage; inspect their getters for the exact values. The byte
estimate includes RGBA8 packed depth and a logical 32-bit depth attachment. Provider
padding, retained frame resources, shader and batch storage are additional costs.
Allocation failure releases resources already created. Presets request their exact
configuration and propagate unsupported-device or allocation errors.

```java
CascadedShadowMap3D shadows = ShadowBudget3D.BALANCED.create(graphics);
environment.cascadedShadowMap(shadows); // borrowed
// Before the scene pass, on the graphics thread:
shadows.renderIfNeeded(sun, camera, casters, casterRevision);
// Later, after the environment stops using it:
shadows.dispose();
```

`render` always redraws every cascade. `renderIfNeeded` first collects current
renderables and fits the cascades, then compares the actual light projections and
caster-fade inputs against the last successful recording. It also compares the
identity of the supplied array and its caller-managed revision. Increment that
revision when array contents, instance/node transforms, mesh data, animation poses,
materials or shadow-writing behavior change. A revision is an application promise;
the framework cannot detect arbitrary mutations. The array remains borrowed until
invalidation or disposal. Unchanged calls skip GPU passes and reuse CPU storage.

All changed cascades are recorded before return, so receivers never mix updated
camera fitting with deliberately stale maps. Changing receiver bias or strength
does not require a depth redraw. Forced rendering and failed cached calls invalidate
reuse. Call `invalidateCache()` after external texture writes or after abandoning a
frame containing recorded shadow work. Recording success alone cannot establish
that an abandoned frame reached the GPU. No frame/pass handles are retained.
`lastRenderedCascadeCount()` reports completed passes from the last render call;
a failed call can report a partial count.

Tune `maxDistance` to the visible scene, `splitLambda` for near/far distribution,
`padding` for coverage, and `bias`/`minTexelBias` to balance acne against separated
contact shadows. Bias is in world units, with a resolution-dependent texel floor.
The PBR receiver blends cascade edges and applies PCF filtering. The final cascade
can fade complete casters near the coverage limit using `shadowFadeFraction`.
This fade depends on the view camera and may require updates even when snapped
projection coordinates remain unchanged. Test moving cameras and grazing surfaces
at the chosen resolution; no single bias is correct for every scene scale.

For a single `DirectionalShadowMap3D`, `autoBias(true)` derives the normalized
receiver bias from half a light-space texel and the map's depth span. Bounds and
resolution determine the value returned by `bias()`; PBR's receiver-plane
filtering additionally accounts for surface slope. Automatic mode is opt-in.
Disabling it restores the manually configured normalized bias. Inspect contacts
and grazing surfaces for your geometry; this avoids a fixed scene-scale offset
but cannot guarantee artifact-free shadows for every mesh.

`PbrShaderConfig.enableShadows(false)` disables receiver sampling. The IBL switch
likewise disables environment-probe sampling. Both default to enabled and are
copied when the provider is constructed. They do not dispose borrowed maps or stop
application-owned shadow passes. The CPU projection fallback does not implement
shadow-map sampling. The built-in depth shader supports static meshes and the same
four-weight, up-to-64-bone skin transform as standard PBR. CPU-skinned instance
copies use the static path. Current-pose bounds drive caster fitting and fade;
custom deformation still requires matching depth geometry and conservative bounds.
See [animation and skinning](ANIMATION.md) for ownership and update ordering.

The built-in depth shader writes complete triangles and does not sample material
alpha textures or model transmission. Exclude transparent/masked geometry from
the caster list or supply a matching custom depth shader; receiver blending alone
does not make a shadow translucent.

The `shadow-cache`, `shadow-budget-low` and `shadow-budget-high` rendered scenarios
compare forced and cached passes with original procedural geometry. They exercise
static scenes, camera movement and changed casters, reporting bounded CPU recording
samples separately from unmeasured GPU time. Run the same viewport/workload on each
provider being supported; reduced pass counts alone are not GPU timing evidence.
