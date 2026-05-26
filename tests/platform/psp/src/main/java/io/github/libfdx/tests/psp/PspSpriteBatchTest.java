package io.github.libfdx.tests.psp;

import io.github.libfdx.backend.psp.PspGraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.g2d.SpriteBatch;

final class PspSpriteBatchTest implements PspTest {
    private PspGraphicsContext graphics;
    private SpriteBatch spriteBatch;
    private Texture checkerTexture;

    @Override
    public void create() {
        graphics = new PspGraphicsContext();
        spriteBatch = new SpriteBatch(graphics, 6);
        checkerTexture = graphics.device().createTexture(TextureDescriptor.rgba8("psp spritebatch checker", 128, 128));
        graphics.device().writeTexture(checkerTexture, PspCheckerTexture.pixels(128, 16, 4));
    }

    @Override
    public void render() {
        spriteBatch.begin(LoadOp.clear(1.0f, 1.0f, 1.0f, 1.0f));
        spriteBatch.viewport(PspGraphicsContext.SCREEN_WIDTH, PspGraphicsContext.SCREEN_HEIGHT);
        spriteBatch.color(1.0f, 1.0f, 1.0f, 1.0f);
        spriteBatch.draw(checkerTexture, -0.72f, -0.72f, 1.44f, 1.44f);
        spriteBatch.end();
    }
}
