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

/**
 * Loads texture asset data.
 *
 * @author xpenatan
 */
final class TextureAssetLoader implements AssetLoader<Texture> {
    private final GraphicsContext graphics;

    TextureAssetLoader(GraphicsContext graphics) {
        this.graphics = graphics;
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    @Override
    public Class<Texture> type() {
        return Texture.class;
    }

    /**
     * Loads the requested resource.
     *
     * @param context the context
     * @param descriptor the descriptor
     * @return the created value
     */
    @Override
    public FdxFuture<Texture> load(AssetLoadContext context, AssetDescriptor<Texture> descriptor) {
        TextureLoadOptions options = TextureLoadOptions.from(descriptor);
        FdxFuture<ImageData> dependency = context.dependency(AssetDescriptor.of(descriptor.path(), ImageData.class));
        return context.completeOnUpdate(new FdxTask<Texture>() {
            @Override
            public Texture run() {
                // The manager admits this finalizer only once dependencies are ready.
                ImageData image = dependency.get();
                Texture texture = graphics.device().createTexture(TextureDescriptor
                        .rgba8(descriptor.path(), image.width(), image.height())
                        .format(options.format()).filter(options.filter()).wrap(options.wrap()));
                try {
                    graphics.device().writeTexture(texture, image.rgba());
                    return texture;
                } catch (RuntimeException | Error error) {
                    try {
                        texture.dispose();
                    } catch (RuntimeException | Error cleanupError) {
                        if (cleanupError != error) {
                            error.addSuppressed(cleanupError);
                        }
                    }
                    throw error;
                }
            }
        });
    }
}
