package io.github.libfdx.graphics.g2d;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxTask;
import io.github.libfdx.graphics.GraphicsContext;

/**
 * Loads bitmap font asset data.
 *
 * @author xpenatan
 */
final class BitmapFontAssetLoader implements AssetLoader<BitmapFont> {
    private final GraphicsContext graphics;

    BitmapFontAssetLoader(GraphicsContext graphics) {
        this.graphics = graphics;
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    @Override
    public Class<BitmapFont> type() {
        return BitmapFont.class;
    }

    /**
     * Loads the requested resource.
     *
     * @param context the context
     * @param descriptor the descriptor
     * @return the created value
     */
    @Override
    public FdxFuture<BitmapFont> load(final AssetLoadContext context, final AssetDescriptor<BitmapFont> descriptor) {
        if (isFreeType(descriptor.path())) {
            Object size = descriptor.options().get("size");
            FreeTypeFontOptions options = FreeTypeFontOptions.defaults(size instanceof Number
                    ? ((Number) size).floatValue() : 16.0f);
            Object characters = descriptor.options().get("characters");
            final FreeTypeFontOptions actual = characters instanceof String
                    ? options.characters((String) characters) : options;
            FdxFuture<BitmapFont> result = FdxFuture.pending();
            context.readBytes(context.files().internal(descriptor.path())).onSuccess(bytes ->
                    context.async(() -> BitmapFontFiles.rasterize(bytes, actual)).onSuccess(rasterized ->
                            context.completeOnUpdate(() -> BitmapFontFiles.createFont(graphics, descriptor.path(), rasterized))
                                    .onSuccess(result::complete).onFailure(result::completeExceptionally))
                            .onFailure(result::completeExceptionally))
                    .onFailure(result::completeExceptionally);
            return result;
        }
        return context.completeOnUpdate(new FdxTask<BitmapFont>() {
            @Override
            public BitmapFont run() {
                return BitmapFontFiles.load(graphics, context.files(), descriptor.path());
            }
        });
    }

    private boolean isFreeType(String path) {
        String value = path != null ? path.toLowerCase() : "";
        return value.endsWith(".ttf") || value.endsWith(".otf");
    }
}
