package io.github.libfdx.graphics.g2d;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxTask;
import io.github.libfdx.graphics.Texture;

/**
 * Loads texture region asset data.
 *
 * @author xpenatan
 */
final class TextureRegionAssetLoader implements AssetLoader<TextureRegion> {
    /**
     * Returns the type.
     *
     * @return the type
     */
    @Override
    public Class<TextureRegion> type() {
        return TextureRegion.class;
    }

    /**
     * Loads the requested resource.
     *
     * @param context the context
     * @param descriptor the descriptor
     * @return the created value
     */
    @Override
    public FdxFuture<TextureRegion> load(AssetLoadContext context, AssetDescriptor<TextureRegion> descriptor) {
        FdxFuture<TextureRegion> future = FdxFuture.pending();
        context.dependency(AssetDescriptor.of(descriptor.path(), Texture.class))
                .onSuccess(texture -> context.completeOnUpdate(new FdxTask<TextureRegion>() {
                    @Override
                    public TextureRegion run() {
                        return new TextureRegion(texture);
                    }
                }).onSuccess(future::complete).onFailure(future::completeExceptionally))
                .onFailure(future::completeExceptionally);
        return future;
    }
}
