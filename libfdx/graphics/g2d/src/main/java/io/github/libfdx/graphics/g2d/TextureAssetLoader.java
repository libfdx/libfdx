package io.github.libfdx.graphics.g2d;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxTask;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;

final class TextureAssetLoader implements AssetLoader<Texture> {
    private final GraphicsContext graphics;

    TextureAssetLoader(GraphicsContext graphics) {
        this.graphics = graphics;
    }

    @Override
    public Class<Texture> type() {
        return Texture.class;
    }

    @Override
    public FdxFuture<Texture> load(AssetLoadContext context, AssetDescriptor<Texture> descriptor) {
        final FdxFuture<Texture> future = FdxFuture.pending();
        context.dependency(AssetDescriptor.of(descriptor.path(), ImageData.class))
                .onSuccess(image -> context.completeOnUpdate(new FdxTask<Texture>() {
                    @Override
                    public Texture run() {
                        Texture texture = graphics.device().createTexture(TextureDescriptor
                                .rgba8(descriptor.path(), image.width(), image.height()));
                        graphics.device().writeTexture(texture, image.rgba());
                        return texture;
                    }
                }).onSuccess(future::complete).onFailure(future::completeExceptionally))
                .onFailure(future::completeExceptionally);
        return future;
    }
}
