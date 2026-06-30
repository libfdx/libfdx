package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.g2d.Batch2D;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g2d.TextureRegion;

import java.nio.ByteBuffer;

/**
 * Runs the deterministic sprite batch stress parity test.
 *
 * @author xpenatan
 */
public final class SpriteBatchStressTest extends GraphicsParityTest {
    private static final int DEFAULT_SPRITE_COUNT = 20_000;
    private static final int TEXTURE_SIZE = 8;

    private Batch2D batch;
    private Texture texture;
    private TextureRegion region;
    private float[] centerX;
    private float[] centerY;
    private float spriteWidth;
    private float spriteHeight;
    private int spriteCount;

    /**
     * Creates a deterministic sprite batch stress parity test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public SpriteBatchStressTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "SpriteBatchStressTest");
        spriteCount = intProperty("libfdx.test.spriteCount", DEFAULT_SPRITE_COUNT, 1);
        batch = new SpriteBatch(graphics, spriteCount);
        texture = graphics.device().createTexture(TextureDescriptor.rgba8(
                "sprite batch stress checker", TEXTURE_SIZE, TEXTURE_SIZE));
        graphics.device().writeTexture(texture, checkerTexture());
        region = new TextureRegion(texture);
        centerX = new float[spriteCount];
        centerY = new float[spriteCount];
        buildGrid();
        markCreated();
        logger.info("SpriteBatchStressTest prepared " + spriteCount + " sprites");
    }

    @Override
    public void render() {
        batch.begin(LoadOp.clear(0.012f, 0.014f, 0.018f, 1.0f));
        batch.color(1.0f, 1.0f, 1.0f, 1.0f);
        batch.draw(region, centerX, centerY, spriteCount, spriteWidth, spriteHeight,
                spriteWidth * 0.5f, spriteHeight * 0.5f, 0.0f);
        batch.end();
        finishFrame();
    }

    @Override
    public void dispose() {
        dispose(batch);
        dispose(texture);
        verifyDisposed();
    }

    private void buildGrid() {
        int columns = (int)Math.ceil(Math.sqrt(spriteCount * 1.6f));
        if (columns <= 0) {
            columns = 1;
        }
        int rows = (spriteCount + columns - 1) / columns;
        spriteWidth = 1.92f / columns;
        spriteHeight = 1.92f / rows;
        float startX = -0.96f + spriteWidth * 0.5f;
        float startY = -0.96f + spriteHeight * 0.5f;
        for (int i = 0; i < spriteCount; i++) {
            int column = i % columns;
            int row = i / columns;
            centerX[i] = startX + column * spriteWidth;
            centerY[i] = startY + row * spriteHeight;
        }
    }

    private ByteBuffer checkerTexture() {
        ByteBuffer buffer = rgba8(TEXTURE_SIZE, TEXTURE_SIZE);
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                boolean bright = ((x / 2) + (y / 2)) % 2 == 0;
                buffer.put((byte)(bright ? 255 : 40));
                buffer.put((byte)(bright ? 225 : 80));
                buffer.put((byte)(bright ? 110 : 220));
                buffer.put((byte)255);
            }
        }
        buffer.flip();
        return buffer;
    }
}
