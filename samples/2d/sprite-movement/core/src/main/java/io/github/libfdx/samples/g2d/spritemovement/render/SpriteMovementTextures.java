package io.github.libfdx.samples.g2d.spritemovement.render;

import io.github.libfdx.assets.loaders.ImageAssetLoader;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.samples.g2d.spritemovement.SpriteMovementApplication;

public final class SpriteMovementTextures {
    private Texture player;
    private Texture wall;

    public SpriteMovementTextures(FileSystem files, GraphicsContext graphics) {
        Texture nextPlayer = load(files, graphics, SpriteMovementApplication.PLAYER_SPRITE);
        Texture nextWall;
        try {
            nextWall = load(files, graphics, SpriteMovementApplication.WALL_TILE);
        } catch (RuntimeException | Error failure) {
            try {
                nextPlayer.dispose();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure != cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
        player = nextPlayer;
        wall = nextWall;
    }

    public Texture player() {
        return player;
    }

    public Texture wall() {
        return wall;
    }

    public void dispose() {
        Throwable failure = null;
        if (player != null) {
            Texture disposedPlayer = player;
            player = null;
            try {
                disposedPlayer.dispose();
            } catch (RuntimeException | Error disposeFailure) {
                failure = disposeFailure;
            }
        }
        if (wall != null) {
            Texture disposedWall = wall;
            wall = null;
            try {
                disposedWall.dispose();
            } catch (RuntimeException | Error disposeFailure) {
                if (failure == null) {
                    failure = disposeFailure;
                } else if (failure != disposeFailure) {
                    failure.addSuppressed(disposeFailure);
                }
            }
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    private static Texture load(FileSystem files, GraphicsContext graphics, String path) {
        ImageData image = ImageAssetLoader.decode(path, files.internal(path).readBytes().get());
        Texture texture = graphics.device().createTexture(
                TextureDescriptor.rgba8(path, image.width(), image.height()).filter(TextureFilter.NEAREST));
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
}
