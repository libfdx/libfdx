package io.github.libfdx.samples.g2d.platformer;

import io.github.libfdx.assets.*;
import io.github.libfdx.audio.Music;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.g2d.TextureLoadOptions;
import io.github.libfdx.graphics.g2d.TileMapAsset;
import io.github.libfdx.maps.MapProperties;

/** Borrowed level bundle. The manager retains its map/images/music until the last level lease is released. */
public record PlatformerLevelAsset(TileMapAsset map, String title, String next, Music music) {
    /** Registers the application-specific bundle loader; a silent launcher omits music dependencies explicitly. */
    public static void register(AssetManager assets, boolean musicAvailable) {
        assets.registerLoader(PlatformerLevelAsset.class, new AssetLoader<PlatformerLevelAsset>() {
            @Override
            public Class<PlatformerLevelAsset> type() { return PlatformerLevelAsset.class; }
            @Override
            public FdxFuture<PlatformerLevelAsset> load(AssetLoadContext context, AssetDescriptor<PlatformerLevelAsset> descriptor) {
                FdxFuture<PlatformerLevelAsset> result = FdxFuture.pending();
                context.dependency(TextureLoadOptions.PIXEL_ART.descriptor(descriptor.path(), TileMapAsset.class)).onSuccess(map -> {
                    try {
                        MapProperties properties = map.map().properties();
                        String title = properties.get("title").stringValue(), next = properties.get("next").stringValue();
                        if (title.isEmpty() || title.length() > 24 || next.isEmpty()) { throw new FdxException("Invalid level title/next"); }
                        FdxFuture<Music> music = musicAvailable
                                ? context.dependency(AssetDescriptor.of(properties.get("music").stringValue(), Music.class)) : null;
                        context.completeOnUpdate(() -> new PlatformerLevelAsset(map, title, next, music == null ? null : music.get()))
                                .onSuccess(result::complete).onFailure(result::completeExceptionally);
                    } catch (RuntimeException | Error failure) { result.completeExceptionally(failure); }
                }).onFailure(result::completeExceptionally);
                return result;
            }
        });
    }
}
