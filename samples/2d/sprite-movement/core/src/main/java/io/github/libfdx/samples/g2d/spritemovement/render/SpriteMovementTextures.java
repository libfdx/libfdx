package io.github.libfdx.samples.g2d.spritemovement.render;

import io.github.libfdx.assets.loaders.AtlasData;
import io.github.libfdx.assets.loaders.ImageAssetLoader;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.*;

/** Owns the harbor and the single atlas page. All sprite regions borrow that page. */
public final class SpriteMovementTextures implements io.github.libfdx.core.Disposable {
    public static final String CHART = "harbor.jpg", ATLAS = "sprites/post.atlas.json";
    public final Texture harbor;
    public final TextureRegion launch, wake, ripple, mail, bearing;
    private Texture page;
    private SpriteAtlas atlas;

    public SpriteMovementTextures(FileSystem files, GraphicsContext graphics) {
        AtlasData data = AtlasData.parse(files.internal(ATLAS).readBytes().get());
        harbor = load(files, graphics, CHART);
        try {
            page = load(files, graphics, "sprites/" + data.page(0).image());
            atlas = new SpriteAtlas(data, new Texture[]{page});
            launch = atlas.find("launch").region();
            wake = atlas.find("wake").region();
            ripple = atlas.find("ripple").region();
            mail = atlas.find("mail").region();
            bearing = atlas.find("bearing").region();
        } catch (RuntimeException | Error failed) {
            try { dispose(); } catch (RuntimeException | Error cleanup) { failed.addSuppressed(cleanup); }
            throw failed;
        }
    }

    private static Texture load(FileSystem files, GraphicsContext graphics, String path) {
        ImageData image = ImageAssetLoader.decode(path, files.internal(path).readBytes().get());
        Texture texture = graphics.device().createTexture(
                TextureDescriptor.rgba8(path, image.width(), image.height()).filter(TextureFilter.LINEAR));
        try {
            graphics.device().writeTexture(texture, image.rgba());
            return texture;
        } catch (RuntimeException | Error failed) {
            try { texture.dispose(); } catch (RuntimeException | Error cleanup) { failed.addSuppressed(cleanup); }
            throw failed;
        }
    }

    @Override
    public void dispose() {
        try {
            if (atlas != null) atlas.dispose();
        } finally {
            try { if (page != null) page.dispose(); }
            finally { harbor.dispose(); }
        }
    }

    @Override
    public boolean isDisposed() {
        return harbor.isDisposed();
    }

}
