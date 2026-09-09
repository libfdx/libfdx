# glTF model loading

`G3DAssetLoaders.register(assets, graphics)` installs the model, binary and image
loaders. Request a `Model` descriptor for glTF JSON or GLB 2.0. External buffers and
images are managed dependencies: they finish before model completion, share their
leases, and remain alive while a loaded model needs them. Data URIs and embedded GLB
buffers/images are also accepted. The loaded model owns its uploaded meshes and
textures; consumers borrow it from their asset scope or manager.

CPU document, accessor, animation and mip preparation uses the asset executor. GPU
creation runs through the manager's update boundary on the graphics thread. A
failed upload disposes partial resources. A single model's final upload is one
completion task; a time budget cannot preempt a native upload already in progress.

## Supported subset

| Area | Behavior |
| --- | --- |
| Document | glTF/GLB 2.0; required extensions checked before dependency discovery or GPU creation |
| Geometry | Triangle primitives, indexed or unindexed; node hierarchy, matrix or TRS transforms; positions, normals, vertex colors, UV0/UV1, tangent XYZW, one four-weight joint set |
| Accessors | Strided base data, normalized small integer attributes, sparse replacements over a base or implicit zeros; padded matrix columns, including omitted final padding |
| Materials | Metallic/roughness factors and maps, base color, emissive, occlusion and normal maps; opaque/mask/blend alpha and double-sided state |
| Extensions | `KHR_materials_unlit`, `KHR_texture_transform`; other required extensions fail explicitly; unknown optional extensions are ignored |
| Animation | Independent translation, quaternion rotation and scale tracks with LINEAR, STEP or CUBICSPLINE; hierarchy and skin palettes use sampled node transforms |
| Textures | Repeat, mirrored repeat and clamp; authored min/mag/mip filters; per-slot UV selection and transforms, normal scale and occlusion strength |

This is a model loader, not a complete glTF scene implementation. Morph targets,
animation weights, UV sets beyond UV1, camera import and light extensions are outside this subset. Advanced material and
compression extensions are unsupported. GPU PBR and CPU projection paths have
different texture fidelity; CPU fallback bakes level-zero texture values at vertices
using the magnification filter, per-slot coordinates, wrapping and linear color interpolation.

Node hierarchies are validated as forests before dependency discovery: cycles,
duplicate/multiple parents, invalid scene roots and indices, malformed/nonfinite
transforms, matrix/TRS mixtures and sheared matrices fail explicitly. This loader
limits documents to 65536 nodes and hierarchy depth to 256. Without a scene list it
imports the declared root nodes; an explicitly empty scene does not load unrelated
meshes. Imported node IDs are trimmed and disambiguated, including generated-name
collisions. Skin joints must be distinct, share a root, and belong to the selected
scene when used there. An explicit skeleton must be an ancestor of every joint.

During CPU preparation, skin influences are checked against the referenced skin.
Weights must be finite and nonnegative, with a positive sum; imported sums are
renormalized in separate storage, preserving an accessor reused for another role.
Repeating a nonzero weighted joint, extra joint sets and morph targets are rejected.
Inverse binds require affine FLOAT MAT4 data with at least one entry per joint.
These rules follow the [glTF skin and hierarchy contracts](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#skins).

Accessor ranges use declared buffer lengths, checked alignment and overflow-safe
arithmetic. Sparse indices must increase strictly and remain within the accessor.
Each accessor is limited to 16,777,216 decoded component values. This is a per-accessor
bound, not a total document memory budget or a full glTF schema validator.

## Texture sampling and tangent space

Missing sampler filters default to linear minification/magnification with one level.
Authored mip filters produce a full CPU-prepared chain and require the provider's
explicit mip capability. Independent min/mag filtering also requires its capability;
unsupported requests fail instead of silently changing the authored sampler.
Sampler enums, image/texture references and transforms are checked before dependency
discovery. Attribute counts, selected UV availability, unit normals and tangent XYZW
are checked during CPU preparation before GPU upload.

Base-color and emissive slots use sRGB texture storage where supported, with shader
decoding as the UNORM fallback. Mips average color in linear space. Data maps average
linear channels. Base-color mips for mask/blend materials use straight-alpha weighting;
opaque base color and emissive ignore alpha during RGB averaging. One source image can
produce separate color/data/alpha variants, shared by compatible slots in the model.
Unreferenced texture entries have no GPU allocation. Alpha-test coverage and normal
variance/roughness compensation are not preserved by the mip generator.

Each `TextureMaterialAttribute` carries immutable `TextureCoordinates`: UV0 or UV1,
then scale, rotation in radians and offset. `KHR_texture_transform.texCoord` overrides
the texture info's UV selection. Every material slot has its own transform. The
legacy shared material UV offset is applied afterwards. `PbrAttributes.normalScale`
scales tangent-space X/Y; `occlusionStrength` blends the sampled occlusion toward one.

Supplied tangents retain their handedness. When a normal map has no tangent attribute,
the importer generates an orthogonal tangent per triangle corner from the selected
untransformed normal-map UV set, with a stable fallback for degenerate UVs. This is
not a MikkTSpace implementation; export tangents when exact agreement with a normal-map
baker matters. Texture transforms change sampling coordinates without rotating supplied
or generated tangent frames. GPU and CPU skinning transform normals with the inverse
transpose and tangent directions with the linear transform. Mirrored transforms preserve
handedness; double-sided PBR back faces reverse the shaded normal.

The [material fixtures](../../../tests/assets/gltf-materials/README.md) include independent
CPU-baked UV/normal references and pinned, individually attributed Khronos assets.

## Animation sampling

Imported node channels expose `hasSamplers()` and immutable `translationSampler()`,
`rotationSampler()` and `scaleSampler()` tracks. Missing components use the node's
authored TRS defaults. Sample times are seconds and each track clamps to its own
endpoints. LINEAR quaternion tracks use shortest-arc spherical interpolation.
CUBICSPLINE retains authored quaternion signs and per-second tangents, scales
tangents by the interval duration and normalizes rotation output. A curve passing
through a zero quaternion fails explicitly.

`AnimationController` samples these channels without per-frame track allocation.
Applications may construct tracks with `AnimationSampler` and
`AnimationClip.sampledTransform`; constructor arrays are copied, output storage
belongs to the caller, and immutable tracks can be shared between instances.
The legacy `AnimationClip.nodeTransform` constructors retain combined linear
keyframes. `keyframes()` applies only to those legacy channels and throws for
independent tracks, which cannot in general be represented by combined linear keys.

Animation input must be finite FLOAT SCALAR data with nonnegative strictly increasing
times. Duplicate node/path targets, invalid target/sampler indices, unsupported
interpolation, incorrect output counts, invalid default rotations and animated
matrix nodes fail during CPU preparation. Channels without a target node are ignored.

The original [animation fixtures](../../../tests/assets/gltf-animation/README.md)
exercise STEP boundaries, independent timelines, cubic translation/rotation,
GPU skinning, sparse positions, shared external buffers and GLB. The
`gltf-animation` scenario renders an independent geometry/analytic-transform
reference beside imported models.

Skin palettes produce model-space vertices from joint transforms and inverse binds.
`DefaultModelInstance` applies the instance transform to skinned draws, so the mesh
node is not applied a second time. Unskinned parts retain their node world transform.
For playback transitions, application events, CPU geometry ownership and current-pose
bounds, see [animation and skinning](ANIMATION.md).
