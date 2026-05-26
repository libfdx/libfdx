package io.github.libfdx.graphics.g3d;

import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.loaders.ImageAssetLoader;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;

public final class G3DAssetLoaders {
    private G3DAssetLoaders() {
    }

    public static void register(AssetManager assets, GraphicsContext graphics) {
        if (assets == null) {
            throw new FdxException("AssetManager cannot be null");
        }
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        ImageAssetLoader.register(assets);
        assets.registerLoader(Model.class, new GltfModelLoader(graphics));
    }
}
