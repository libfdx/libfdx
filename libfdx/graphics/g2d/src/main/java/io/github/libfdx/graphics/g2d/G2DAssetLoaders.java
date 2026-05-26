package io.github.libfdx.graphics.g2d;

import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.loaders.ImageAssetLoader;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;

public final class G2DAssetLoaders {
    private G2DAssetLoaders() {
    }

    public static void register(AssetManager assets, GraphicsContext graphics) {
        if (assets == null) {
            throw new FdxException("AssetManager cannot be null");
        }
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        ImageAssetLoader.register(assets);
        assets.registerLoader(io.github.libfdx.graphics.Texture.class, new TextureAssetLoader(graphics));
        assets.registerLoader(TextureRegion.class, new TextureRegionAssetLoader());
        assets.registerLoader(BitmapFont.class, new BitmapFontAssetLoader(graphics));
    }
}
