package io.github.libfdx.graphics.g3d;

import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.loaders.ImageAssetLoader;
import io.github.libfdx.assets.loaders.ImageDecoder;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.graphics.TextureMipmapPreparer;
import io.github.libfdx.assets.loaders.BinaryAssetLoader;
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
     * Registers standard model, image, buffer and lighting loaders. Default image and glTF mipmap
     * preparation uses manager-owned platform strategies (a shared worker on web).
     * Mesh interleaving runs as bounded CPU preparation; graphics finalization yields between
     * textures, nodes and primitives under the asset manager's update budget. A single graphics
     * upload cannot be interrupted and may exceed that cooperative budget.
     *
     * @param assets the assets
     * @param graphics the graphics context
     */
    public static void register(AssetManager assets, GraphicsContext graphics) {
        if (assets == null || graphics == null) throw new FdxException("AssetManager and GraphicsContext are required");
        ImageAssetLoader.register(assets);
        BinaryAssetLoader.register(assets);
        assets.registerLoader(Model.class, new GltfModelLoader(graphics));
        assets.registerLoader(ImageBasedLighting3D.class, new ImageBasedLightingAssetLoader(graphics));
    }

    /** Web compilation binds this hook to the same manager-owned worker as image decoding. */
    static TextureMipmapPreparer defaultMipmaps(AssetLoadContext context) {
        return null;
    }

    /** Overrides platform defaults with borrowed CPU strategies; null mipmaps uses the loading executor.
     * The application must keep strategies alive until its asset manager is disposed. */
    public static void register(AssetManager assets, GraphicsContext graphics, ImageDecoder decoder,
            TextureMipmapPreparer mipmaps) {
        if (assets == null) {
            throw new FdxException("AssetManager cannot be null");
        }
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        assets.registerLoader(ImageData.class, new ImageAssetLoader(decoder));
        BinaryAssetLoader.register(assets);
        assets.registerLoader(Model.class, new GltfModelLoader(graphics, decoder, mipmaps));
        assets.registerLoader(ImageBasedLighting3D.class, new ImageBasedLightingAssetLoader(graphics));
    }

    /**
     * Creates the standard glTF/GLB model loader without registering it.
     *
     * <p>This is useful for asset managers that dispatch between a default
     * loader and more-specific suffix loaders. Register BinaryAssetLoader and
     * ImageAssetLoader in that manager for external buffer/image dependencies.</p>
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
