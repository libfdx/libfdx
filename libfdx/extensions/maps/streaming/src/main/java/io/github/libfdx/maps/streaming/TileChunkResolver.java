package io.github.libfdx.maps.streaming;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.maps.TileChunk;

/** Maps signed fixed-grid chunk indices to descriptors in the borrowed manager.
 * Called on its application thread only when starting a new request, never for resident cells.
 * Return a non-null descriptor. The loaded chunk must have the requested origin/dimensions.
 * Resolving may allocate; file reads/CPU preparation belong in the asset loader. */
@FunctionalInterface
public interface TileChunkResolver {
    AssetDescriptor<TileChunk> resolve(int chunkX,int chunkY);
}

