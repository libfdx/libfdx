package io.github.libfdx.graphics.g2d;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.collections.Array;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.GraphicsContext;
import java.nio.charset.StandardCharsets;

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
        if (!descriptor.path().toLowerCase().endsWith(".fnt")) {
            return FdxFuture.failed(new FdxException("Unsupported font file extension: " + descriptor.path()));
        }
        FdxFuture<BitmapFont> result = FdxFuture.pending();
        context.readBytes(context.files().internal(descriptor.path())).onSuccess(bytes ->
                context.async(() -> BitmapFontFiles.BitmapFontDefinition.parse(new String(bytes, StandardCharsets.UTF_8)))
                        .onSuccess(definition -> {
                            try {
                                if (definition.pageFiles.size() == 0 || definition.glyphs.size() == 0) {
                                    throw new FdxException("Bitmap font has no pages or glyphs: " + descriptor.path());
                                }
                                Array<FdxFuture<ImageData>> pages = new Array<FdxFuture<ImageData>>();
                                for (int i = 0; i < definition.pageFiles.size(); i++) {
                                    String page = definition.pageFiles.get(i);
                                    if (page == null || page.isEmpty()) {
                                        throw new FdxException("Missing bitmap font page " + i + ": " + descriptor.path());
                                    }
                                    pages.add(context.dependency(AssetDescriptor.of(
                                            BitmapFontFiles.resolveSibling(descriptor.path(), page), ImageData.class)));
                                }
                                context.completeOnUpdate(() -> BitmapFontFiles.createBitmap(
                                        graphics, descriptor.path(), definition, i -> pages.get(i).get()))
                                        .onSuccess(result::complete).onFailure(result::completeExceptionally);
                            } catch (RuntimeException | Error error) {
                                result.completeExceptionally(error);
                            }
                        }).onFailure(result::completeExceptionally))
                .onFailure(result::completeExceptionally);
        return result;
    }

    private boolean isFreeType(String path) {
        String value = path != null ? path.toLowerCase() : "";
        return value.endsWith(".ttf") || value.endsWith(".otf");
    }
}
