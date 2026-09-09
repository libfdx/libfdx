# Tiled JSON maps

The Tiled extension loads CPU map data. Graphics bindings live in g2d, so reading
spawn points, paths, or collision shapes does not require a graphics device.
See the [build file](build.gradle.kts) for dependencies and publication settings,
and the [loader declaration](src/main/java/io/github/libfdx/maps/tiled/TiledMapLoader.java)
for loading limits.

Register loaders before acquiring assets:

```java
DefaultAssetManager assets = new DefaultAssetManager(fdx.files(), executor);
TiledMapLoader.register(assets);
G2DAssetLoaders.register(assets, fdx.graphics().main());

AssetScope level = assets.createScope();
AssetLease<TileMapAsset> loaded = level.load(
        AssetDescriptor.of("levels/level.tmj", TileMapAsset.class));

// Application frame: process dependency/notification/finalization work.
assets.update(4, 1_000_000L);
if (loaded.isLoaded()) {
    TileMapAsset binding = loaded.asset();
    binding.tiles().animationTime(levelElapsedMillis);
    // pixelBatch is an active Batch2D using the map's pixel coordinates.
    renderer.render(binding.map(), binding.tiles(), pixelBatch, 0, 0,
            viewX, viewY, viewWidth, viewHeight);
}

// Level exit; other levels retain their shared images.
level.dispose();
```

The application owns the manager, scopes, executor, batch, and renderer.
`executor` may be null for cooperative preparation. Close the manager before
closing a borrowed executor. Loaded map data, bindings, atlas metadata, and
textures are borrowed: release leases/scopes instead of disposing shared resources.
Treat imported CPU metadata as immutable. The binding's animation clock is explicit
and shared by all borrowers of that binding; set it before rendering each view. A completed future does not extend an asset's lifetime.

For CPU-only loading, omit `G2DAssetLoaders.register` and acquire
`io.github.libfdx.maps.TileMap` instead of `TileMapAsset`. The CPU loader requests
external atlas metadata but never image textures. The graphics binding requests
those textures only after the CPU map is valid. Separate maps can share an
external atlas and its images through one manager; the last owner releases them.
Use a separate manager for each graphics resource domain.

Parsing runs through asset preparation. External tilesets, images, finalization,
and result notifications participate in the dependency scheduler. No pending
future is synchronously awaited by a loader. Each queue step remains indivisible:
large finite-map validation or texture uploads can exceed a time budget.

## Supported subset

| Area | Supported behavior |
| --- | --- |
| Maps | Finite and infinite orthogonal JSON, including `.tmj`; all four orthogonal cell render orders. |
| Tile layers | Uncompressed JSON arrays; arbitrary-size, nonoverlapping signed chunks for infinite maps; unsigned 32-bit GIDs; empty cells; eight orthogonal transforms. |
| Tilesets | Embedded or external JSON/`.tsj` atlases and image collections; multiple first-GID ranges, atlas margin/spacing, native image sizes/offsets, sparse local IDs/metadata, per-image crops, and duration-based animation. |
| Layers | Tile/object/image layers and nested groups (up to 32); stable IDs/names/classes, editor order, inherited visibility/opacity/tint/offsets/parallax. |
| Images | Relative image references, optional declared dimensions, native-size drawing, horizontal/vertical repetition bounded to the view. |
| Objects | Points, rectangles, polygons, polylines, and bottom-left-aligned tile objects with explicit positive dimensions; rotation, visibility, opacity, properties, and stable IDs. |
| Object order | Stored editor order plus stable `index` or `topdown` rendering of tile objects. |
| Properties | String, signed integer, finite float, boolean, color, relative file path, and object-reference ID. Colors become packed RGBA; object reference 0 means unset. |
| Drawing | Existing visible-rectangle culling and batching, including tiles extending beyond their cells and rotated tile-object bounds. Shape objects remain gameplay data; the scenario supplies an overlay. |

Gaps between atlas GID ranges are allowed; a cell referencing a gap fails.
Nonconsecutive entries use their explicit local IDs. For image collections,
`tilecount` counts existing images and can be much smaller than the highest local
ID plus one. Missing IDs fail in cells, tile objects, and animation frames. Each
image supplies positive source dimensions and may select a top-left crop; the
binding verifies those dimensions before publication. Empty collections and
per-tile overrides of a shared atlas image are rejected. Frames can use different
images and dimensions; grid tiles keep each selected image's native size.

Relative files resolve against the file containing the reference, preserving
case and normalizing separators and dot segments within the internal asset root.
Absolute paths, URLs, and references escaping that root fail. File properties
are retained as paths; they do not automatically load arbitrary gameplay assets.

The default limit is 1,048,576 total layer cells and 1,048,576 total atlas tiles
per map, with the same bound on animation frame entries (up to 65,536 per sequence).
`TiledMapLoader.register(assets, limit)` configures these bounds. Grouped layers
share the map's total layer/object/cell limits; invalid hidden content also fails.
Additional counts are checked before creating cell/object/vertex storage.
Malformed sizes, fractional or overflowing IDs, overlapping ranges, missing
tiles, and unsupported runtime features fail with source and layer/object
context. A failed import is never partially published.

## Coordinates and rendering

One source pixel is one map unit. For finite maps the origin is at the map's
bottom-left and Y points upward: `worldX = tiledX`, `worldY = mapPixelHeight - tiledY`.
Infinite maps flip around zero: `worldY = -tiledY`. Their zero width/height means
unbounded extent, not the bounds of currently resident chunks. A source chunk
with tile origin `(x,y)` and height `h` becomes `(x,-y-h)`; its rows are reversed.
Objects, images and parallax origins use the same unbounded origin.
Tile array rows are reversed into that coordinate system. Object anchors use
the same conversion, local vertex Y is negated, and clockwise source rotations
become counterclockwise rotations. Rectangle anchors remain their top-left;
tile-object anchors remain their bottom-left. Layer offsets are applied
separately, with source offset Y negated.

Use a batch that maps those units into your view. The
[runnable scenario](../../../../tests/core/src/main/java/io/github/libfdx/tests/graphics/TiledMapTest.java)
contains a small pixel-to-clip adapter and gameplay-object overlay.
`TileMapRenderer` automatically samples tile regions between their outermost
texel centers, preventing neighboring atlas colors from bleeding under nearest
or linear filtering. No tileset padding or scene-specific setup is required.
Dense and infinite layers, tile objects, animations, and flips share this behavior;
source dimensions and world geometry remain unchanged. Custom batches must use
the supplied region UVs rather than recomputing them from pixel bounds.

`TileMapRenderer` never opens/closes a caller's batch. Layer RGB/alpha tint and
opacity multiply through groups; after changing color the renderer restores white
at exit. Untinted, fully opaque legacy maps retain the caller's color.
`SpriteBatch` supports the transform overloads of `Batch2D`; custom
batches must implement those overloads for transformed maps. The defaults reject
nonzero transforms explicitly.

Animation durations use integer milliseconds. `TileSet.animationTime` samples
one timeline per animated tile definition, then cells and tile objects use the
selected base image. Frames referencing animated tiles do not recursively animate.
An application clock controls pause, seek and speed; no wall-clock service is read
by rendering. Texture storage is shared between static and animated frames.

Group membership is ordered and fixed; metadata remains explicit on each node.
`TileMap.mapLayer` enumerates top-level layers, and `GroupLayer.layer` enumerates
children. The legacy `TileMap.layer` API enumerates only top-level dense tile layers.
`findObject` searches nested groups and returns authored, layer-local coordinates.

Image-layer anchors are their image top-left, with layer/group offsets applied
after the Y-up conversion. Declared dimensions must match the loaded texture;
older exports may omit both and use its actual dimensions. Images share manager
texture dependencies with tiles. Repetition covers the visible rectangle, or the
finite map bounds for the renderer overload without a rectangle. It draws whole
quads; set a render-pass scissor when exact clipping is needed. The default
`imageRepeatLimit` is 65,536 copies per layer per render; exceeding it fails before
that layer draws. Hidden layers and zero effective alpha submit no draws.

Parallax factors multiply through groups; offsets add and opacity multiplies.
The renderer uses the visible rectangle center or an explicit `camera(x,y)` in
world units. Without either, it uses the map's parallax origin. Imported origins
use the same Y-up conversion as objects. Culling uses the resulting layer position;
zero and negative factors are accepted. See the
[Tiled parallax convention](https://doc.mapeditor.org/en/stable/manual/layers/#parallax-reference-point).

All four high GID bits are removed before lookup. The orthogonal transform
applies the diagonal axis swap before horizontal/vertical flips, including on
rectangular tiles. This follows the [Tiled GID contract](https://doc.mapeditor.org/en/stable/reference/global-tile-ids/).
The source format and object conventions are defined in the
[Tiled JSON reference](https://doc.mapeditor.org/en/stable/reference/json-map-format/)
and [object guide](https://doc.mapeditor.org/en/stable/manual/objects/).

## Infinite maps and external chunks

Infinite tile layers require `chunks`, each containing signed `x/y`, positive
`width/height` and an uncompressed `data` array. Duplicate origins, overlapping
rectangles and signed-coordinate overflow fail before publication. Imported
chunks share the map's cell limit and have an additional 65,536-chunk limit.
Rendering requires an explicit visible rectangle; native overhangs and all four
global painter orders are preserved across chunk seams.

Loading a TMJ still reads and retains its complete bounded CPU document. It does
not stream embedded JSON arrays as the camera moves. The registration also
provides a `TileChunk` asset loader for standalone chunk objects in the same
coordinate/data format. Preparation and publication use the asset scheduler;
atlas membership validation belongs to the application selecting shared tiles.
See [chunk residency](../streaming/README.md) for external file/window budgets.

## Explicit limits

The loader rejects non-orthogonal orientations,
TMX/TSX/XML, base64/compression,
tileset collision object groups, transparent-color conversion,
non-default tile sizing/alignment, non-normal blend modes,
ellipse/capsule/text/template objects, and custom class/enum property types.
Ordinary string class names are retained. Editor background color and editor-only
metadata do not configure the application's framebuffer clear operation.

The [fixtures](../../../../tests/assets/tiled/) are authored JSON with generated
diagnostic atlases. Parser and lifecycle tests share them with the runtime
scenario. They are not an export or screenshot from an installed Tiled editor.
The capture checker independently verifies the corner colors for every flip
and a non-square diagonal tile. Physics-body creation remains application-owned.
