package io.github.libfdx.graphics.g2d;

import io.github.libfdx.assets.*;
import io.github.libfdx.assets.loaders.AtlasData;
import io.github.libfdx.collections.Array;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.Texture;

/** Metadata discovery and page dependencies precede budgeted application-thread binding. */
final class SpriteAtlasLoader implements AssetLoader<SpriteAtlas> {
    @Override public Class<SpriteAtlas> type() { return SpriteAtlas.class; }
    @Override public FdxFuture<SpriteAtlas> load(AssetLoadContext context,AssetDescriptor<SpriteAtlas> descriptor) {
        FdxFuture<SpriteAtlas> result=FdxFuture.pending();
        TextureLoadOptions options=TextureLoadOptions.from(descriptor);
        String path=descriptor.path().replace('\\','/');
        String parent=path.substring(0,path.lastIndexOf('/')+1);
        context.dependency(AssetDescriptor.of(path,AtlasData.class)).onSuccess(data -> {
            try {
                Array<FdxFuture<Texture>> textures=new Array<>(data.pageCount());
                for(int i=0;i<data.pageCount();i++) {
                    textures.add(context.dependency(options.descriptor(parent+data.page(i).image(),Texture.class)));
                }
                context.completeOnUpdate(() -> {
                    Texture[] pages=new Texture[data.pageCount()];
                    for(int i=0;i<pages.length;i++) pages[i]=textures.get(i).get();
                    return new SpriteAtlas(data,pages);
                }).onSuccess(result::complete).onFailure(result::completeExceptionally);
            } catch(RuntimeException | Error failure) { result.completeExceptionally(failure); }
        }).onFailure(result::completeExceptionally);
        return result;
    }
}
