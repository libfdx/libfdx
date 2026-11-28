package io.github.libfdx.samples.g2d.platformer;

import io.github.libfdx.assets.*;
import io.github.libfdx.audio.Music;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.g2d.AtlasRegion;
import io.github.libfdx.graphics.g2d.SpriteAtlas;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.maps.TileMap;

/** The level scope retains all of the borrowed artwork and the optional soundtrack. */
public record PlatformerLevelAsset(Trail trail, TileMap map, Texture panorama, Texture ground,
        SpriteAtlas sharedAtlas, TextureRegion[] props, TextureRegion[] instruments, TextureRegion[] places, TextureRegion landmark,
        TextureRegion[] actors, TextureRegion[] shots, Music music) {

    private static final String[] ACTORS = {
            "idle-0", "idle-1", "idle-2", "idle-3",
            "run-0", "run-1", "run-2", "run-3", "run-4", "run-5", "run-6", "run-7",
            "jump", "rise", "fall", "land", "crawler-0", "crawler-1", "crawler-2", "crawler-3",
            "moth-0", "moth-1", "moth-2", "moth-3", "hopper-0", "hopper-1", "hopper-2", "hopper-3",
            "gear", "sign", "glow", "spikes", "shadow", "dust", "beam"
    };
    private static final String[] PROPS = {
            "wheel", "cog", "mill", "portal", "timber-deck", "timber-post", "trestle", "lantern",
            "ferns", "stones", "tree", "workshop-detail", "iron-deck", "iron-post", "grille",
            "root-deck", "root-post", "vines", "stone-deck", "stone-pier", "rail"
    };
    private static final String[] INSTRUMENTS = {"dial-1", "dial-2", "dial-3", "bell", "lamp-off", "lamp-on"};
    private static final String[] PLACES = {"site-1", "site-2", "site-3"};

    public static void register(AssetManager assets, boolean musicAvailable) {
        assets.registerLoader(PlatformerLevelAsset.class, new AssetLoader<PlatformerLevelAsset>() {
            @Override
            public Class<PlatformerLevelAsset> type() {
                return PlatformerLevelAsset.class;
            }

            @Override
            public FdxFuture<PlatformerLevelAsset> load(AssetLoadContext context, AssetDescriptor<PlatformerLevelAsset> descriptor) {
                Trail trail = Trail.forMap(descriptor.path());
                var map = context.dependency(AssetDescriptor.of(descriptor.path(), TileMap.class));
                String prefix = "levels/art/" + trail.id + "-";
                var panorama = texture(context, prefix + "sky.jpg");
                var ground = texture(context, prefix + "ground.png");
                var shared = context.dependency(AssetDescriptor.of("levels/atlases/shared.atlas.json", SpriteAtlas.class));
                var scenery = context.dependency(AssetDescriptor.of("levels/atlases/" + trail.id + ".atlas.json", SpriteAtlas.class));
                var music = musicAvailable ? context.dependency(AssetDescriptor.of(
                        "audio/" + (trail.ordinal() % 2 == 0 ? "meadow" : "moonrise") + ".wav", Music.class)) : null;
                return context.completeOnUpdate(() -> new PlatformerLevelAsset(trail, map.get(), panorama.get(),
                        ground.get(), shared.get(), regions(shared.get(), "props/", PROPS), regions(shared.get(), "instruments/", INSTRUMENTS),
                        regions(scenery.get(), "", PLACES), region(scenery.get(), "landmark"),
                        actors(shared.get(), trail), shots(shared.get()), music == null ? null : music.get()));
            }

        });
    }

    private static TextureRegion[] actors(SpriteAtlas atlas, Trail trail) {
        TextureRegion[] frames = new TextureRegion[ACTORS.length];
        for (int i = 0; i < frames.length; i++) {
            String prefix = i >= 16 && i < 28 && trail != Trail.RAVINE ? "actors/" + trail.id + "/" : "actors/";
            frames[i] = region(atlas, prefix + ACTORS[i]);
        }
        return frames;
    }

    private static TextureRegion[] shots(SpriteAtlas atlas) {
        var kinds = RangedAttack.Kind.values();
        TextureRegion[] frames = new TextureRegion[kinds.length];
        for (var kind : kinds) {
            frames[kind.ordinal()] = region(atlas, "shots/" + kind.name().toLowerCase(java.util.Locale.ROOT));
        }
        return frames;
    }

    private static FdxFuture<Texture> texture(AssetLoadContext context, String path) {
        return context.dependency(AssetDescriptor.of(path, Texture.class));
    }

    private static TextureRegion[] regions(SpriteAtlas atlas, String prefix, String[] names) {
        TextureRegion[] frames = new TextureRegion[names.length];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = region(atlas, prefix + names[i]);
        }
        return frames;
    }

    private static TextureRegion region(SpriteAtlas atlas, String name) {
        AtlasRegion sprite = atlas.find(name);
        if (sprite == null) throw new FdxException("Missing platformer sprite: " + name);
        return sprite.region();
    }

}
