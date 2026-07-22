package io.github.libfdx.samples.g2d.spritemovement.render;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.loaders.ImageAssetLoader;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.samples.g2d.spritemovement.SpriteMovementProject;

public final class SpriteMovementTextures {
    private Texture player;
    private Texture wall;

    public SpriteMovementTextures(Fdx fdx, GraphicsContext graphics) {
        player = load(fdx.files(), graphics, SpriteMovementProject.PLAYER_SPRITE);
        wall = load(fdx.files(), graphics, SpriteMovementProject.WALL_TILE);
    }

    public Texture texture(String assetPath) {
        if (SpriteMovementProject.PLAYER_SPRITE.equals(assetPath)) {
            return player;
        }
        if (SpriteMovementProject.WALL_TILE.equals(assetPath)) {
            return wall;
        }
        return null;
    }

    public void dispose() {
        if (player != null) {
            player.dispose();
            player = null;
        }
        if (wall != null) {
            wall.dispose();
            wall = null;
        }
    }

    private static Texture load(FileSystem files, GraphicsContext graphics, String path) {
        ImageData image = ImageAssetLoader.decode(path, files.internal(path).readBytes().get());
        Texture texture = graphics.device().createTexture(
                TextureDescriptor.rgba8(path, image.width(), image.height()).filter(TextureFilter.NEAREST));
        graphics.device().writeTexture(texture, image.rgba());
        return texture;
    }
}
