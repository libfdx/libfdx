# Chunk residency

This optional extension owns fixed-grid chunk asset leases. It depends on
portable map data and the asset manager, with no graphics or backend dependency.
The application owns `ChunkResidency`, supplies a borrowed manager and resolver,
and disposes residency before the manager. Access is confined to the manager's
application thread. Reentrant mutations during resolver/update callbacks fail.

```java
ChunkResidency chunks = new ChunkResidency(assets,
        (x, y) -> AssetDescriptor.of("world/" + x + "_" + y + ".json", TileChunk.class),
        16, 16, 64, 4); // chunk dimensions, resident/request capacity, pending limit
TileMap world = TileMap.infinite(16, 16); // map units per cell
world.addChunkedLayer(chunks.layer());
chunks.window(-16, -16, 16, 16, 1); // half-open tile bounds and prefetch margin

// Each frame, use separate loading and residency budgets.
assets.update(3, 1_000_000L);
chunks.update(2, 2, 4); // starts, publications, evictions
// Draw world with an explicit visible rectangle and separately owned tile bindings.
```

The resolver receives signed **chunk indices**, and is invoked only for a new
request. It returns a descriptor for a registered `TileChunk` loader. File reads,
procedural generation and dependencies belong in that loader; use asynchronous
preparation instead of blocking the resolver. The
[Tiled extension](../tiled/README.md) provides a standalone JSON chunk loader.
Sources may instead depend on shared procedural metadata or other assets.

The entire visible plus prefetch rectangle must fit capacity and the signed
coordinate domain. Invalid windows preserve the previous selection. Visible
chunks are requested before prefetch; incomplete views may render holes, and
`visibleReady` lets the application choose when to show them. Changing the window
queues eviction. Zero update budgets skip their respective work. Pending slots
include completed assets awaiting publication. `update` never drives the manager
or blocks for I/O. Each individual publication/eviction remains indivisible.

Failures retain their cause through `failure(x,y)` until `retryFailed` or eviction.
Wrong origins/dimensions fail without replacing a valid neighbor. Eviction releases
the lease, cancelling requests that have no other owner. A late completion cannot
reinsert an evicted chunk. Other scopes may retain the asset or its dependencies.

The resident layer and its chunks are borrowed. Do not structurally modify the
layer while residency owns it, or edit shared loaded chunks as private gameplay
state. Save edits separately and supply application-owned versions through a
loader when needed. This owner does not persist edits or select GPU tile bindings.

`maxCellBytes` bounds ID/transform payload represented by resident and pending
slots. It excludes source archives, transient decoded data, Java/index overhead,
graphics bindings and external owners. File streaming and residency are distinct:
loading a whole TMJ into another scope retains that source independently.
