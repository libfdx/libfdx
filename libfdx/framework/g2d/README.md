# 2D pixels and texture loading

`TileMapRenderer` draws [canonical maps](../maps/README.md) through a borrowed active
batch. Infinite maps require an explicit visible rectangle. `chunkLimits` allocates
reusable query/row storage and bounds visible chunks and visited cells per layer;
exceeding a limit fails before that layer draws. Sparse rows merge in global cell
painter order, including transparent overhangs across unequal chunk seams. Camera
offsets use double intermediates before narrowing final vertices; input positions
and the batch remain float APIs. Keep view coordinates near the origin when
rendering distant chunks. The renderer is confined to one nonreentrant caller.

`SpriteBatch` receives clip-space positions. `PixelArtViewport` converts a
logical pixel grid into those coordinates, using integer scale, optional camera
and vertex snapping, and centered letterboxing. When the framebuffer is smaller
than the logical image it crops a centered 1x image. Game positions remain
unchanged. Disable snapping for smooth subpixel movement; use integral sprite
dimensions and nearest filtering when exact source-pixel edges are required.

```java
PixelArtViewport viewport = new PixelArtViewport(320, 180);
viewport.update(framebufferWidth, framebufferHeight).camera(cameraX, cameraY);
viewport.apply(pass); // full framebuffer viewport + logical-image scissor
batch.viewport(framebufferWidth, framebufferHeight);
batch.begin(pass);
batch.draw(texture, viewport.clipX(x), viewport.clipY(y),
        viewport.clipWidth(width), viewport.clipHeight(height));
batch.end();
pass.end();
```

The pass is borrowed. Restore viewport/scissor before drawing another region in
that pass. `worldX/worldY` convert framebuffer pixels back to world coordinates;
convert high-DPI window coordinates and top-left input Y before calling them.
`contains` excludes letterbox margins. Rotation and fractional sprite sizes can
introduce resampling even when the camera is snapped.

`TextureLoadOptions.PIXEL_ART.descriptor(path, TextureRegion.class)` requests
nearest sampling through the asset manager. The same options work with `Texture`
and `SpriteAtlas`/`TileMapAsset`, propagating to the underlying image textures. Defaults preserve
linear filtering, clamped wrapping, and raw RGBA8_UNORM storage. Options are
immutable and compared by value. One manager admits one configuration per image
path/type: conflicting settings fail explicitly. CPU image data remains shared.

Texture format describes sample transfer behavior. RGBA8_UNORM passes stored RGB
values to the shader; RGBA8_UNORM_SRGB decodes RGB to linear light during sampling.
Alpha stays linear in both formats. Default sprite shaders multiply sampled values
by tint without additional color conversion and use straight-alpha blending.
Use raw UNORM for traditional byte-preserving sprites. SRGB requires a shader and
render-target composition that expect linear samples. Do not feed premultiplied
pixels to the default straight-alpha blend path.

See [3D color and composition](../g3d/README.md) for PBR material transfer behavior.

`pixel-color` is the rendered reference scenario for equivalent PBR encodings,
sRGB sampling/targets, nearest pixel boundaries, and uneven-size letterboxing.
Map ownership/import details remain in the [map model](../maps/README.md) and
[Tiled importer](../../extensions/maps/tiled/README.md).

## Font loading

Managed `.ttf`/`.otf` `BitmapFont` loads acquire the file asynchronously, rasterize
on the configured preparation executor (or a cooperative update step), and upload
the atlas on the application thread. These stages share the asset update budget;
pending reads are awaited through callbacks. A failed read or rasterization fails
the asset without creating a substitute font. Direct `BitmapFontFiles` helpers
require inline reads, such as disk files or preloaded browser assets.

UI Kit's opt-in substitution policy is documented in
[text and input](../../../docs/UI_KIT.md#text-and-input).

## Offscreen composition

`TextureBlitter` owns its shaders, quad buffers and cached pipelines, and borrows
its device, input images and destination pass. Set the destination viewport/scissor
before drawing. `draw(pass, target, true)` composites an `OffscreenTarget` whose
RGB is premultiplied, as produced by ordinary straight-alpha drawing onto transparent
black. It accounts for the target's declared texture origin and avoids applying
alpha twice. Use `false` for ordinary straight-alpha image data. The result in a
transparent destination remains premultiplied for the next composition pass.

The blitter performs no tone mapping or manual color transfer. Match texture/target
semantics deliberately, and draw UI after scene effects when its colors should be
independent. One-color destination layouts are supported; unsupported combinations
fail through the provider. End the writing pass before sampling a target, and dispose
the blitter before the graphics device. See [offscreen ownership](../graphics/README.md).

`TextureRegion.rendered(target)` is a lighter origin-aware borrowed view for an
opaque rendered image. Rebuild it after resize. It does not change `SpriteBatch`'s
straight-alpha blending. Ordinary `TextureRegion` construction retains uploaded
top-to-bottom row coordinates; render-attachment usage alone does not imply that
uploaded contents should be flipped.

## Sprite atlases


`G2DAssetLoaders.register(manager, graphics)` registers `.atlas.json` metadata and
`SpriteAtlas` bindings. Load an atlas through a scope or lease:

```java
AssetLease<SpriteAtlas> lease = assets.acquire(
        TextureLoadOptions.PIXEL_ART.descriptor("sprites/characters.atlas.json", SpriteAtlas.class));
// During loading, pump assets.update(maxTasks, maxNanos).
// Once lease.isLoaded(), cache the borrowed view:
AtlasRegion idle = lease.asset().find("player/idle"); // null when absent
```

The manager prepares metadata, discovers and loads its PNG pages, then binds the
atlas on the application thread after every page succeeds. Page texture options
propagate from the atlas request. Metadata is CPU-only and shared independently.
The manager retains the page dependencies until the last atlas owner releases
them; releasing one scope cannot invalidate another scope's shared atlas.

`AtlasRegion.draw(batch, pivotX, pivotY, scaleX, scaleY, degrees)` preserves the
original image's pivot across trimmed animation frames. Scales are world units
per source pixel in the batch's coordinate system; `SpriteBatch` uses clip space.
Use a pixel-coordinate adapter (as in `TexturePackerTest`) when drawing in
pixels. Negative scales mirror about the pivot. Page coordinates use image
top-left; original-image trim offsets and normalized pivots use bottom-left.

An atlas and its views borrow page textures. Manually constructed atlases require
the caller to keep pages alive. Disposing an atlas invalidates its views and never
disposes those borrowed textures; managed users release the lease instead.
Atlases do not reorder draws, select animation frames, or override batch blending.
The format is explicitly straight alpha and unrotated. See the
[atlas tool](../../tools/texturepacker/README.md) for packing and filtering constraints.

Managed image loading also supports images fetched after browser startup. Raw
8-bit non-interlaced RGB/RGBA PNG decoding preserves hidden RGB at alpha zero,
including atlas bleed texels. It checks chunk CRCs and inflated dimensions, with
limits of 64 MiB encoded input and 16M pixels per image. Color profiles are not
applied to this raw path; texture/shader transfer settings remain explicit.
Other PNG layouts and JPEG use platform decoding; browser canvas conversion can
lose hidden RGB and some precision at low alpha. The synchronous
`ImageAssetLoader.decode` needs prepared browser cache data for those fallback
formats; `decodeAsync` also prepares uncached browser images.

## Particle effects

See [particle effects](../graphics/particles.md) for presets, custom sprites,
lifetime curves, ownership, and transparency behavior.
