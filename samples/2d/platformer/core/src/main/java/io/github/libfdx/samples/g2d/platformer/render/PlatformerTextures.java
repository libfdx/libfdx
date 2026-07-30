package io.github.libfdx.samples.g2d.platformer.render;

import io.github.libfdx.assets.loaders.ImageAssetLoader;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.samples.g2d.platformer.PlatformerConstants;

public final class PlatformerTextures {
    private static final String ASSET_ROOT = "kenney/pixel-platformer/";

    private final Texture[] textures;
    private final TextureRegion[] regions;

    private PlatformerTextures(Texture[] textures, TextureRegion[] regions) {
        this.textures = textures;
        this.regions = regions;
    }

    public static PlatformerTextures load(FileSystem files, GraphicsContext graphics) {
        if (files == null) {
            throw new FdxException("File system cannot be null");
        }
        if (graphics == null) {
            throw new FdxException("Graphics context cannot be null");
        }
        Texture[] textures = new Texture[3];
        try {
            textures[0] = loadTexture(files, graphics, "Tilemap/tilemap_packed.png");
            textures[1] = loadTexture(files, graphics, "Tilemap/tilemap-backgrounds_packed.png");
            textures[2] = loadTexture(files, graphics, "Tilemap/tilemap-characters_packed.png");

            TextureRegion[] regions = new TextureRegion[PlatformerConstants.REGION_COUNT];
            copyRegions(regions, PlatformerConstants.REGION_TILES_START,
                    TextureRegion.split(textures[0], 18, 18));
            copyRegions(regions, PlatformerConstants.REGION_BACKGROUNDS_START,
                    TextureRegion.split(textures[1], 24, 24));
            copyRegions(regions, PlatformerConstants.REGION_CHARACTERS_START,
                    TextureRegion.split(textures[2], 24, 24));
            return new PlatformerTextures(textures, regions);
        } catch (RuntimeException | Error failure) {
            disposeTextures(textures, failure);
            throw failure;
        }
    }

    public TextureRegion[] regions() {
        return regions;
    }

    public void dispose() {
        Throwable failure = disposeTextures(textures, null);
        rethrowFailure(failure);
    }

    public static boolean validRegionId(int regionId) {
        return regionId >= 0 && regionId < PlatformerConstants.REGION_COUNT;
    }

    private static void copyRegions(TextureRegion[] target, int start, TextureRegion[][] source) {
        int index = start;
        for (int row = 0; row < source.length; row++) {
            TextureRegion[] sourceRow = source[row];
            for (int column = 0; column < sourceRow.length; column++) {
                target[index++] = sourceRow[column];
            }
        }
    }

    private static Texture loadTexture(FileSystem files, GraphicsContext graphics, String relativePath) {
        String path = ASSET_ROOT + relativePath;
        ImageData image = ImageAssetLoader.decode(path, files.internal(path).readBytes().get());
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8(path, image.width(), image.height())
                .filter(TextureFilter.NEAREST));
        try {
            graphics.device().writeTexture(texture, image.rgba());
            return texture;
        } catch (RuntimeException | Error failure) {
            try {
                texture.dispose();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure != cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    static Throwable disposeTextures(Texture[] textures, Throwable failure) {
        if (textures == null) {
            return failure;
        }
        Throwable result = failure;
        for (int i = 0; i < textures.length; i++) {
            Texture texture = textures[i];
            textures[i] = null;
            if (texture == null) {
                continue;
            }
            try {
                texture.dispose();
            } catch (RuntimeException | Error disposeFailure) {
                if (result == null) {
                    result = disposeFailure;
                } else if (result != disposeFailure) {
                    result.addSuppressed(disposeFailure);
                }
            }
        }
        return result;
    }

    private static void rethrowFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
