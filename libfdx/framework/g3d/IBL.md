# Prepared image-based lighting

`ImageBasedLighting3D` owns a diffuse map, a specular mip chain and a BRDF lookup
in the creating graphics context's resource domain. `Environment` borrows it.
`G3DAssetLoaders.register` installs the `.fdxibl` loader by resource type:

```java
G3DAssetLoaders.register(assets, graphics);
assets.load(AssetDescriptor.of("lighting/studio.fdxibl", ImageBasedLighting3D.class));
// After budgeted assets.update(...) completes:
ImageBasedLighting3D probe = assets.get("lighting/studio.fdxibl", ImageBasedLighting3D.class);
environment.imageBasedLighting(probe).imageBasedLightingTransform(1.0f, 0.0f);
```

The loader decodes and validates on the asset executor, then uploads on the
manager's graphics update boundary. Upload failure disposes partial resources.
An upload is one completion task; a time budget cannot interrupt a native call.
Asset scopes/leases control ownership as for other managed resources. Alternatively,
`ImageBasedLighting3D.create(graphics, data)` uploads immediately and returns a
caller-owned resource. Dispose it on the graphics thread after its recorded draws.
The CPU data can then be released. Borrowed textures must not be rewritten or
disposed separately. Access after resource disposal fails.

Use `ImageBasedLighting3D.isSupported(graphics)` at setup: the standard GPU PBR path
requires filterable `RGBA16_FLOAT`, explicit mip levels and at least twelve texture/
sampler slots. Unsupported requests fail explicitly. Choose the procedural sky or
ambient lighting yourself when the device lacks these capabilities. The CPU
projection shader does not approximate IBL. Resource-domain checks still apply when
an environment is used with a different context.

Probe intensity is a nonnegative multiplier of linear radiance. Rotation is radians
about world +Y using the right-hand rule. Change it outside batch recording. Passing
null to `imageBasedLighting` disables the contribution without disposing the probe.
IBL adds to ambient color, procedural sky and direct lights; set the ambient/sky
contribution deliberately to avoid lighting the same scene twice. Fog and output
tone mapping retain the existing composition rules. Orthographic PBR uses parallel
view rays for specular lighting; perspective PBR uses camera-to-fragment rays.

## Representation and preparation

The portable representation uses 2D equirectangular textures, with +Y at v=0, +X at
u=.5 and +Z at u=.75. Longitude repeats; latitude clamps at the poles. Diffuse stores
cosine-convolved irradiance divided by pi. Specular level i represents perceptual
roughness `i / (levelCount - 1)`; runtime sampling interpolates adjacent levels.
Chains may stop before 1x1 to preserve directional illumination on rough surfaces.
The standard preparer stops at 16x8 for ordinary probes, keeping at least two levels
for smaller inputs. When a chain reaches 1x1, that level stores the whole-sphere average.
The two BRDF coefficients use
NdotV horizontally and perceptual roughness vertically. All data is scene-linear,
without sRGB decoding, and uploads use little-endian IEEE half components.

Use the [offline IBL tool](../../tools/ibl/README.md) to convolve a source HDR map.
The shader uses a single-scattering GGX split-sum approximation. Diffuse uses a
Schlick Fresnel energy split, and material occlusion multiplies indirect light.
The tool's LUT integrates height-correlated Smith visibility. This follows the
precomputation approach described in [Filament's IBL reference](https://google.github.io/filament/main/filament.html#lighting/imagebasedlights).

This is a distant, static probe. It has no local parallax correction, runtime probe
capture, multiple-probe blending or multiscattering compensation. Low-resolution
latitude/longitude levels lose directional detail, and bright small lights can need
more preparation samples. It does not render a background sky automatically.

`ImageBasedLightingData.of` copies already prepared float RGB/RG arrays and converts
them to half; it performs no convolution. `decode`/`encode` read/write version 1 FDXI
files. Dimensions, complete chains, exact payload size, finite nonnegative half
values and LUT coefficient ranges are checked before GPU work. Encoded data is bounded
to 64 MiB; widths are powers of two up to 2048 and the square LUT up to 1024. This is
a per-probe bound, not an application memory budget. See the [original fixtures](../../../tests/assets/ibl/README.md)
for numerical and rendered comparisons.
