package io.github.libfdx.graphics.g3d;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.GraphicsContext;

/** CPU decoding uses the asset executor; GPU finalization uses the budgeted graphics update boundary. */
final class ImageBasedLightingAssetLoader implements AssetLoader<ImageBasedLighting3D> {
    private final GraphicsContext graphics;
    ImageBasedLightingAssetLoader(GraphicsContext graphics) { this.graphics = graphics; }
    @Override
    public Class<ImageBasedLighting3D> type() { return ImageBasedLighting3D.class; }
    @Override
    public FdxFuture<ImageBasedLighting3D> load(AssetLoadContext context, AssetDescriptor<ImageBasedLighting3D> descriptor) {
        FdxFuture<ImageBasedLighting3D> result = FdxFuture.pending();
        context.readBytes(context.files().internal(descriptor.path()))
                .onSuccess(bytes -> context.async(() -> ImageBasedLightingData.decode(bytes))
                        .onSuccess(data -> context.completeOnUpdate(() -> ImageBasedLighting3D.create(graphics, data))
                                .onSuccess(result::complete).onFailure(result::completeExceptionally))
                        .onFailure(result::completeExceptionally))
                .onFailure(result::completeExceptionally);
        return result;
    }
}
