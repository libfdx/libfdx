package io.github.libfdx.graphics.g2d;

import io.github.libfdx.assets.*;
import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.maps.TileAtlas;
import io.github.libfdx.maps.TileMap;
import io.github.libfdx.maps.MapLayer;
import io.github.libfdx.maps.GroupLayer;
import io.github.libfdx.maps.ImageLayer;

/** Format-neutral graphics binding; the application separately registers its CPU TileMap loader. */
final class TileMapAssetLoader implements AssetLoader<TileMapAsset> {
    @Override public Class<TileMapAsset> type() { return TileMapAsset.class; }
    @Override public FdxFuture<TileMapAsset> load(AssetLoadContext context, AssetDescriptor<TileMapAsset> descriptor) {
        FdxFuture<TileMapAsset> result = FdxFuture.pending();
        TextureLoadOptions options = TextureLoadOptions.from(descriptor);
        context.dependency(AssetDescriptor.of(descriptor.path(), TileMap.class)).onSuccess(map -> {
            try {
                ObjectMap<String, FdxFuture<Texture>> textures = new ObjectMap<>();
                for (int i = 0; i < map.atlasCount(); i++) {
                    TileAtlas atlas = map.atlas(i);
                    if (atlas.isImageCollection()) {
                        for (int tile = 0; tile < atlas.tileCount(); tile++) {
                            request(context, options, textures, atlas.imagePath(atlas.localId(tile)));
                        }
                    } else { request(context, options, textures, atlas.imagePath()); }
                }
                Array<ImageLayer> imageLayers=new Array<>(0);
                for(int i=0;i<map.mapLayerCount();i++) collectImages(map.mapLayer(i),imageLayers);
                for(int i=0;i<imageLayers.size();i++) {
                    request(context, options, textures, imageLayers.get(i).imagePath());
                }
                context.completeOnUpdate(() -> {
                    TileSet tiles = new TileSet();
                    for(int i=0;i<imageLayers.size();i++) {
                        ImageLayer layer=imageLayers.get(i); Texture texture=textures.get(layer.imagePath()).get();
                        if(layer.imageWidth()!=0 && (texture.width()!=layer.imageWidth() || texture.height()!=layer.imageHeight())) {
                            throw new FdxException(descriptor.path()+": image size disagrees with layer "+layer.id()+" "+layer.imagePath());
                        }
                        tiles.imageRegion(layer.imagePath(),new TextureRegion(texture));
                    }
                    for (int i = 0; i < map.atlasCount(); i++) {
                        TileAtlas atlas = map.atlas(i);
                        for (int tile = 0; tile < atlas.tileCount(); tile++) {
                            int local = atlas.localId(tile);
                            Texture texture = textures.get(atlas.imagePath(local)).get();
                            if (texture.width() != atlas.imageWidth(local) || texture.height() != atlas.imageHeight(local)) {
                                throw new FdxException(descriptor.path() + ": image size disagrees with tileset "
                                        + atlas.name() + " tile " + local + " " + atlas.imagePath(local));
                            }
                            tiles.atlasRegion(map.firstId(i) + local, new TextureRegion(texture, atlas.sourceX(local),
                                    atlas.sourceY(local), atlas.regionWidth(local), atlas.regionHeight(local)), atlas.offsetX(), atlas.offsetY());
                        }
                        for (int tile = 0; tile < atlas.tileCount(); tile++) {
                            int local = atlas.localId(tile);
                            if (atlas.animation(local) != null) {
                                tiles.animation(map.firstId(i) + local, atlas.animation(local), map.firstId(i));
                            }
                        }
                    }
                    return new TileMapAsset(map, tiles);
                }).onSuccess(result::complete).onFailure(result::completeExceptionally);
            } catch (RuntimeException | Error failure) { result.completeExceptionally(failure); }
        }).onFailure(result::completeExceptionally);
        return result;
    }
    private static void request(AssetLoadContext context, TextureLoadOptions options,
            ObjectMap<String, FdxFuture<Texture>> textures, String path) {
        if (!textures.containsKey(path)) { textures.put(path, context.dependency(options.descriptor(path, Texture.class))); }
    }
    private static void collectImages(MapLayer layer,Array<ImageLayer> images) {
        if(layer instanceof ImageLayer image) images.add(image);
        else if(layer instanceof GroupLayer group) for(int i=0;i<group.layerCount();i++) collectImages(group.layer(i),images);
    }
}
