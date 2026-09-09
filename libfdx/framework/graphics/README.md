# Offscreen targets

## Resize and context loss

A surface resize preserves the graphics resource domain. Applications rebuild
size-dependent targets while keeping compatible assets, as demonstrated by the
[kinetic gallery](../../../tests/core/src/test/fixtures/showcase/README.md).
Device/context loss is a separate failure: old textures, buffers, pipelines and
recorded commands cannot be made valid by calling `resize`.

The WebGL adapter checks native context loss at event/frame boundaries and before
cleanup. Detection permanently invalidates its GL resource domain and raises
`GraphicsContextLostException` with the provider identity. Commands and uploads
using that domain then fail before native calls. Dispose application-owned
resources normally; cleanup skips native deletion in a lost domain. Resources
are not automatically marked disposed, and loaded asset handles do not imply
their graphics domain is still valid.

The browser backend reports the frame failure, disposes application resources,
and stops its loop. The supported reconstruction boundary is a fresh backend/
provider context with newly created batches, targets and managed graphics assets;
reloading the hosting page is one way to establish it. A native
`webglcontextrestored` event does not revive the old application or its handles.
This follows WebGL's [resource invalidation rule](https://registry.khronos.org/webgl/specs/1.0.3/#5.15.2).

Other GL adapters retain the default `GLApi.isContextLost()` behavior until they
implement reset detection. No automatic recovery, state replay, WGPU device-loss
handling, or mobile context-loss parity is implied by the exception type.

## Target ownership

`OffscreenTarget` owns persistent color and optional depth textures. Construct it
with a borrowed graphics device, then call `resize(width, height)` on the graphics
thread. Equal sizes reuse the same textures. A failed allocation keeps the previous
target usable; successful resize invalidates its old borrowed texture/view handles.
Defer resizing while a window is minimized to zero dimensions.

```java
OffscreenTarget scene = new OffscreenTarget(graphics.device(), true);
scene.resize(width, height);
RenderPass pass = scene.begin(graphics.currentFrame(), true);
// Render into pass, then end it before another pass or sampling scene.color().
pass.end();
```

`begin(frame, true)` clears color and depth; `false` preserves both. Each pass
stores its results and resolves multisample color before sampling. `color()` is
the borrowed single-sample result, `renderColor()` is the possibly multisampled
attachment, and `depth()` is borrowed or null. No frame, pass or encoder is retained.
End all passes before resizing or disposing the target. A provider rejects sampling
a texture attached to the active pass. Recorded providers retain native allocations
until submitted work no longer needs them; this does not make disposed public
handles usable.

The extended constructor requests exact color/depth formats, sample count and
filter. It checks renderability, color filtering, explicit depth, sample counts and resolve support;
it does not silently reduce the request. Other usage-specific restrictions still
belong to the provider. `estimatedBytes()` reports unpadded texture storage, excluding
provider alignment/metadata. `revision()` changes after successful resize and can
invalidate application caches of borrowed views.

`GraphicsCapabilities.renderedTextureOrigin()` describes the location of v=0 in a
rendered image. Uploaded image row order is a separate convention. GL declares
bottom-left, WGPU top-left; a provider that has not declared it returns `UNKNOWN`.
Automatic composition rejects unknown origins. Use the [2D compositor](../g2d/README.md#offscreen-composition)
or pass an explicitly known origin when working with external images.

GL adapters with explicit-depth support accept `DEPTH32_FLOAT` with render-attachment
usage and a texture-backed color attachment. They reject depth uploads/sampling,
MRT and multisample resolve. Older custom GL adapters report that explicit depth is
unavailable until they implement the corresponding `GLApi` methods. WGPU supports
explicit color/depth/resolve layouts subject to its declared format/sample limits.
Check the actual device instead of selecting behavior from a provider-name string.

Color-format filtering and blending are separate capabilities from renderability.
`supportsColorFiltering(format)` gates linear texture filters;
`supportsColorBlending(format)` gates blending into color attachments. Floating-point
formats require explicit provider declarations; omitted builder lists include only
supported normalized eight-bit formats. Texture and pipeline descriptors reject
unsupported requests before allocation. An opaque pass does not require blending.
The optional [effects module](../../extensions/graphics/effects/README.md) uses these
contracts when selecting scene and bloom storage.

For lower-level passes, `RenderPassDepthStencilAttachment` owns the clear/load/store
description. The red component of its depth clear operation is the depth value in
[0,1], and overrides the legacy descriptor clear value. `LoadOp.load()` preserves
depth; a clear should match the camera's clip-depth convention when clearing the
whole scene. [Forward 3D composition](../g3d/README.md) builds on this owner.

## Explicit mip levels

`TextureDescriptor` defaults to one level, linear minification/magnification and
no mip sampling. `mipLevelCount(n)` allocates exactly that many floor-halved levels;
it never generates image data. `filters(min, mag, mipmap)` selects independent min/mag
filters and NONE, NEAREST or LINEAR mip selection. The older `filter(value)` sets both
filters and disables mip selection, leaving the allocation count unchanged.
Check `TEXTURE_MIP_LEVELS`, `TEXTURE_MIN_MAG_FILTERS` and the format's filtering
capability before requesting them. Unsupported requests fail before allocation.
Mip chains require single-sample color textures.

`writeTexture(texture, bytes)` replaces a one-level image.
`writeTextureMipLevels(texture, levels)` requires one complete, tightly packed buffer
per allocated level. All ranges are validated before upload; caller positions and
limits are preserved. The provider borrows bytes only for the call. Whole-chain
replacement preserves previously recorded draws on delayed-submission providers.
Native failures can leave partially written contents; publish replacement assets
only after successful completion. For uploads, texel layout follows the texture
format; floating-point components use little-endian IEEE half/single values.

GL-family adapters opt into `RGBA16_FLOAT` allocation, typed half uploads, filtering
and color attachments together. Older custom adapters keep their previous format
capabilities until they implement the corresponding `GLApi` hooks. Browser support
requires the float color-buffer extension; use device format/filter/blend queries
instead of assuming support from the provider name. Byte-buffer positions are
respected even when a half-float upload starts at an odd byte offset.

`texture.view(level)` returns a cached, borrowed single-level attachment view with
that level's dimensions. `view()` is level zero. Rendering into a mip does not
generate other levels. The views live until texture disposal; a view is not an
owner. Sampled bindings see the chain through the chosen sampler. Sampling a
texture attached to the active pass is rejected, including different mip levels.

For asset preparation, `TextureMipmaps.rgba8` returns a copied complete RGBA8 chain.
Its area filter includes odd-size edges. Choose sRGB decoding/encoding for color
maps, linear averaging for data, and alpha weighting for straight-alpha color
whose transparent RGB should not bleed. Opaque slots that ignore source alpha
must disable alpha weighting. The helper does not preserve alpha-test coverage or
normalize tangent-space normals. It allocates CPU buffers and belongs in loading or
build tooling, outside frame loops.
