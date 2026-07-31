package io.github.libfdx.graphics.g3d;

import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.loaders.ImageAssetLoader;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;

/**
 * Represents a G3 d asset loaders.
 *
 * @author xpenatan
 */
public final class G3DAssetLoaders {
    private G3DAssetLoaders() {
    }

    /**
     * Runs the register step.
     *
     * @param assets the assets
     * @param graphics the graphics context
     */
    public static void register(AssetManager assets, GraphicsContext graphics) {
        if (assets == null) {
            throw new FdxException("AssetManager cannot be null");
        }
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        ImageAssetLoader.register(assets);
        assets.registerLoader(Model.class, modelLoader(graphics));
    }

    /**
     * Creates the standard glTF/GLB model loader without registering it.
     *
     * <p>This is useful for asset managers that dispatch between a default
     * loader and more-specific suffix loaders.</p>
     *
     * @param graphics the graphics context used for model uploads
     * @return the standard model loader
     */
    public static AssetLoader<Model> modelLoader(GraphicsContext graphics) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        return new GltfModelLoader(graphics);
    }
}
