# Portable map data

This module owns canonical CPU tile grids, atlas metadata, image layers, object shapes, and
typed properties. It has no graphics, backend, or file-loader dependency.
Textures and rendering remain in g2d; the optional
[Tiled importer](../../extensions/maps/tiled/README.md) produces this model.

Application-created maps and their mutable layers/properties are application
owned. A map returned by an asset manager is borrowed and may be shared by
multiple scopes; do not mutate it as private level state. Keep runtime entity
state separately. CPU map data has no GPU resources to dispose.

`TileMap.layer(index)` and `layerCount()` enumerate top-level dense tile layers.
`mapLayer(index)` and `mapLayerCount()` preserve the top-level order of tile,
object, image and group layers. `GroupLayer.layer(index)` preserves child order; fixed
membership prevents cycles and limits nesting to 32 groups. These accessors refer to the same layer objects. Cells use positive
IDs plus compact primitive transform storage; zero is empty. No per-cell objects
are required during traversal.

Objects preserve stable IDs and immutable local vertices. `findObject(id)`
searches nested groups and returns null when absent. `MapObject.worldX/worldY` rotate a vertex around its
anchor; add its layer and ancestor offsets separately. Coordinate conventions for imported
content are owned by the importing format's documentation.

Existing `io.github.libfdx.graphics.g2d.TileMap` and `TileLayer` remain deprecated
compatibility subclasses using this storage. Existing constructors, typed layer
access, and fluent calls continue to work. New callers should import
`io.github.libfdx.maps.TileMap` and `TileLayer`. A compatibility map accepts
compatibility tile layers; use the canonical map when mixing new layer types.
`TileMapRenderer` accepts either entry point, while texture-region `TileSet`
continues to belong to g2d.

Atlas `TileAnimation` definitions copy their frame IDs and millisecond durations.
They refer to base images without recursively animating frames. Runtime clocks
and GPU texture bindings belong to g2d, keeping CPU metadata independent of views.

`TileAtlas` describes a regular atlas or a sparse `imageCollection` of immutable
`TileImage` records. `tileCount` counts existing tiles; `localId(index)` enumerates
their ascending IDs, and `localIdLimit` defines the exclusive range bound. Use
`contains` to check holes. Per-ID image/crop accessors work for both kinds;
no-argument shared-image accessors fail for collections. Collection membership is
copied at creation and storage is proportional to tile count, not the ID range.
`TileMap.findAtlas` rejects holes, and subsequent tileset ranges must not overlap.

`ImageLayer` stores a borrowed image path, dimensions (both zero when unspecified),
top-left anchor, and repeat flags. It owns no decoded pixels. `MapLayer.tintRgba`
stores packed RGBA; rendering composes its components and opacity through groups.

## Sparse worlds

`TileMap.infinite(tileWidth, tileHeight)` accepts `ChunkedTileLayer` nodes, including
inside groups. Its width/height and world extents are zero because it has no finite
extent. Dense layers belong to finite maps. Use `mapLayer` to enumerate sparse nodes.

A `TileChunk` owns dense IDs/transforms at an immutable signed tile origin. Its
cell accessors use local coordinates; `ChunkedTileLayer.tile` uses world tile
coordinates. Layers borrow nonoverlapping chunks, enforce explicit resident
chunk/cell limits, and index only occupied coordinate buckets. Chunk dimensions
may differ. Empty gaps consume no cell storage. Missing cells read as zero; edits
to nonresident cells fail. Replacement at the same origin is explicit.

`TileChunkQuery` is caller-owned, fixed-capacity result storage. Queries use
half-open bounds, including `Integer.MAX_VALUE + 1L` as an exclusive edge. Results
borrow chunks until cleared; retaining them also retains those chunks after
eviction. Large empty ranges scan the resident set. Ordinary nearby queries use
the spatial index. Neither queries nor cell edits allocate frame storage.

`TileLayer.revision` and `TileChunk.revision` change when cell data changes;
`ChunkedTileLayer.revision` changes for structural edits. These are invalidation
tokens, not persistence/version control. Payload byte estimates exclude Java
objects, index capacity, source files and other owners. Structural mutations may
allocate; all access is confined to one thread. The optional
[residency owner](../../extensions/maps/streaming/README.md) manages external chunk
leases without adding assets or graphics dependencies to this module.
