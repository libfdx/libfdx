package io.github.libfdx.maps.tiled;

import io.github.libfdx.assets.*;
import io.github.libfdx.collections.Array;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.maps.TileAtlas;
import io.github.libfdx.maps.TileMap;

/**
 * Loads finite and infinite orthogonal JSON maps without graphics. Parsing uses asset CPU
 * preparation; external tilesets are retained dependencies and assembly waits
 * for them through the shared update budget. File references use internal paths.
 * Unsupported runtime features fail with source/layer/object context.
 */
public final class TiledMapLoader implements AssetLoader<TileMap> {
    public static final int DEFAULT_MAX_CELLS = 1_048_576;
    private final int maxCells;
    public TiledMapLoader() { this(DEFAULT_MAX_CELLS); }
    /** Bounds total layer cells, atlas tiles and animation frames per map; not a streaming setting. */
    public TiledMapLoader(int maxCells) {
        if (maxCells <= 0) { throw new FdxException("Tiled cell limit must be positive"); }
        this.maxCells = maxCells;
    }
    /** Registers map and external atlas loaders, without replacing image/graphics loaders. */
    public static void register(AssetManager assets) { register(assets, DEFAULT_MAX_CELLS); }
    public static void register(AssetManager assets, int maxCells) {
        TiledMapLoader loader = new TiledMapLoader(maxCells);
        assets.registerLoader(TileAtlas.class, new AtlasLoader(maxCells));
        assets.registerLoader(io.github.libfdx.maps.TileChunk.class, new TiledChunkLoader(maxCells));
        assets.registerLoader(TileMap.class, loader);
    }
    @Override public Class<TileMap> type() { return TileMap.class; }
    @Override public FdxFuture<TileMap> load(AssetLoadContext context, AssetDescriptor<TileMap> descriptor) {
        FdxFuture<TileMap> result = FdxFuture.pending();
        context.readBytes(context.files().internal(descriptor.path())).onSuccess(bytes ->
                context.async(() -> new TiledReader(descriptor.path(), maxCells).readMap(bytes))
                .onSuccess(document -> {
                    try {
                    Array<FdxFuture<TileAtlas>> atlases = new Array<FdxFuture<TileAtlas>>(0);
                    for (TiledReader.Reference reference : document.references) {
                        atlases.add(reference.source == null ? FdxFuture.completed(reference.embedded)
                                : context.dependency(AssetDescriptor.of(reference.source, TileAtlas.class)));
                    }
                    context.completeOnUpdate(() -> document.assemble(atlases)).onSuccess(result::complete)
                            .onFailure(result::completeExceptionally);
                    } catch (RuntimeException | Error failure) { result.completeExceptionally(failure); }
                }).onFailure(result::completeExceptionally)).onFailure(result::completeExceptionally);
        return result;
    }
    private static final class AtlasLoader implements AssetLoader<TileAtlas> {
        private final int maxTiles;
        AtlasLoader(int maxTiles) { this.maxTiles = maxTiles; }
        @Override public Class<TileAtlas> type() { return TileAtlas.class; }
        @Override public FdxFuture<TileAtlas> load(AssetLoadContext context, AssetDescriptor<TileAtlas> descriptor) {
            FdxFuture<TileAtlas> result = FdxFuture.pending();
            context.readBytes(context.files().internal(descriptor.path())).onSuccess(bytes ->
                    context.async(() -> new TiledReader(descriptor.path(), maxTiles).readAtlas(bytes))
                    .onSuccess(result::complete).onFailure(result::completeExceptionally))
                    .onFailure(result::completeExceptionally);
            return result;
        }
    }
}
