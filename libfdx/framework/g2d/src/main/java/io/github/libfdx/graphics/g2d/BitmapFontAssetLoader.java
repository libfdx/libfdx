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
        return context.completeOnUpdate(new FdxTask<BitmapFont>() {
            @Override
            public BitmapFont run() {
                Object size = descriptor.options().get("size");
                if (isFreeType(descriptor.path()) && size instanceof Number) {
                    FreeTypeFontOptions options = FreeTypeFontOptions.defaults(((Number) size).floatValue());
                    Object characters = descriptor.options().get("characters");
                    if (characters instanceof String) {
                        options = options.characters((String) characters);
                    }
                    return BitmapFontFiles.loadFreeType(graphics, context.files(), descriptor.path(), options);
                }
                return BitmapFontFiles.load(graphics, context.files(), descriptor.path());
            }
        });
    }

    private boolean isFreeType(String path) {
        String value = path != null ? path.toLowerCase() : "";
        return value.endsWith(".ttf") || value.endsWith(".otf");
    }
}
