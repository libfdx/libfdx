package io.github.libfdx.maps.tiled;

import io.github.libfdx.assets.*;
import io.github.libfdx.core.*;
import io.github.libfdx.maps.TileChunk;

/** Loads a standalone Tiled JSON chunk object (x/y/width/height/data), using bounded CPU preparation.
 * Tiled Y-down coordinates become canonical Y-up coordinates around origin zero. Only unsigned GID arrays
 * are accepted. Tileset/image bindings remain the caller's separate shared assets; this loader validates
 * GID encoding but cannot validate membership without that map's tilesets. No graphics allocation occurs. */
public final class TiledChunkLoader implements AssetLoader<TileChunk> {
    private final int maxCells;
    public TiledChunkLoader(int maxCells) {
        if(maxCells<1)throw new FdxException("Chunk cell limit must be positive");
        this.maxCells=maxCells;
    }
    @Override
    public Class<TileChunk> type(){return TileChunk.class;}
    @Override
    public FdxFuture<TileChunk> load(AssetLoadContext context,AssetDescriptor<TileChunk> descriptor) {
        FdxFuture<TileChunk> result=FdxFuture.pending();
        context.readBytes(context.files().internal(descriptor.path())).onSuccess(bytes ->
                context.async(() -> new TiledReader(descriptor.path(),maxCells).readChunk(bytes))
                        .onSuccess(result::complete).onFailure(result::completeExceptionally))
                .onFailure(result::completeExceptionally);
        return result;
    }
}
