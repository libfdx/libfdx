package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ParticleEmitter2DTest {
    @Test
    void emitsUpdatesAndExpiresParticlesWithoutParticleObjects() {
        ParticleEmitter2D emitter = new ParticleEmitter2D(4)
                .seed(12)
                .position(1.0f, 2.0f)
                .lifetime(1.0f)
                .speed(0.0f)
                .size(0.2f, 0.1f)
                .color(1.0f, 0.25f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        assertEquals(3, emitter.emit(3));
        assertEquals(3, emitter.activeCount());
        assertEquals(1.0f, emitter.x(0));
        assertEquals(2.0f, emitter.y(0));

        emitter.update(0.5f);

        assertEquals(3, emitter.activeCount());
        assertEquals(0.5f, emitter.age(0));
        assertEquals(0.15f, emitter.size(0), 0.0001f);
        assertEquals(0.5f, emitter.red(0), 0.0001f);
        assertEquals(0.5f, emitter.blue(0), 0.0001f);
        assertEquals(0.5f, emitter.alpha(0), 0.0001f);

        emitter.update(0.51f);

        assertEquals(0, emitter.activeCount());
    }

    @Test
    void emissionRateUsesAccumulatorAndCapacity() {
        ParticleEmitter2D emitter = new ParticleEmitter2D(2)
                .seed(3)
                .emissionRate(8.0f)
                .lifetime(2.0f)
                .speed(0.0f);

        emitter.update(0.125f);
        assertEquals(1, emitter.activeCount());

        emitter.update(0.25f);
        assertEquals(2, emitter.activeCount());
        assertEquals(0, emitter.emit(1));

        emitter.clear();
        assertEquals(0, emitter.activeCount());
    }

    @Test
    void renderDrawsActiveParticlesAndRestoresWhiteColor() {
        ParticleEmitter2D emitter = new ParticleEmitter2D(2)
                .position(1.0f, 2.0f)
                .lifetime(1.0f)
                .speed(0.0f)
                .size(0.2f, 0.1f)
                .color(1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        emitter.emit(2);
        emitter.update(0.5f);
        RecordingBatch batch = new RecordingBatch();
        TextureRegion region = new TextureRegion(new TestTexture(8, 8));

        assertEquals(2, emitter.render(region, batch));

        assertEquals(2, batch.drawCount);
        assertEquals(1.0f - 0.075f, batch.x[0], 0.0001f);
        assertEquals(2.0f - 0.075f, batch.y[0], 0.0001f);
        assertEquals(0.15f, batch.width[0], 0.0001f);
        assertEquals(0.15f, batch.height[0], 0.0001f);
        assertEquals(0.5f, batch.red[0], 0.0001f);
        assertEquals(0.0f, batch.green[0], 0.0001f);
        assertEquals(0.5f, batch.blue[0], 0.0001f);
        assertEquals(0.5f, batch.alpha[0], 0.0001f);
        assertEquals(1.0f, batch.currentRed);
        assertEquals(1.0f, batch.currentGreen);
        assertEquals(1.0f, batch.currentBlue);
        assertEquals(1.0f, batch.currentAlpha);
    }

    @Test
    void rejectsInvalidConfiguration() {
        ParticleEmitter2D emitter = new ParticleEmitter2D(1);

        assertThrows(FdxException.class, () -> new ParticleEmitter2D(0));
        assertThrows(FdxException.class, () -> emitter.position(Float.NaN, 0.0f));
        assertThrows(FdxException.class, () -> emitter.emissionRate(-1.0f));
        assertThrows(FdxException.class, () -> emitter.lifetime(0.0f));
        assertThrows(FdxException.class, () -> emitter.speed(-1.0f));
        assertThrows(FdxException.class, () -> emitter.direction(0.0f, -1.0f));
        assertThrows(FdxException.class, () -> emitter.size(0.0f, 1.0f));
        assertThrows(FdxException.class, () -> emitter.color(1.1f, 0.0f, 0.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 0.0f));
        assertThrows(FdxException.class, () -> emitter.update(-0.01f));
        assertThrows(FdxException.class, () -> emitter.emit(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> emitter.x(0));
    }

    private static final class RecordingBatch implements Batch2D {
        private final float[] x = new float[4];
        private final float[] y = new float[4];
        private final float[] width = new float[4];
        private final float[] height = new float[4];
        private final float[] red = new float[4];
        private final float[] green = new float[4];
        private final float[] blue = new float[4];
        private final float[] alpha = new float[4];
        private int drawCount;
        private float currentRed = 1.0f;
        private float currentGreen = 1.0f;
        private float currentBlue = 1.0f;
        private float currentAlpha = 1.0f;

        @Override
        public void begin() {
        }

        @Override
        public void begin(LoadOp loadOp) {
        }

        @Override
        public void begin(RenderPass pass) {
        }

        @Override
        public Batch2D color(float red, float green, float blue, float alpha) {
            currentRed = red;
            currentGreen = green;
            currentBlue = blue;
            currentAlpha = alpha;
            return this;
        }

        @Override
        public Batch2D viewport(int width, int height) {
            return this;
        }

        @Override
        public void draw(Texture texture, float x, float y, float width, float height) {
        }

        @Override
        public void draw(Texture texture, float x, float y, float width, float height,
                float originX, float originY, float rotationDegrees) {
        }

        @Override
        public void draw(TextureRegion region, float x, float y, float width, float height) {
            draw(region, x, y, width, height, 0.0f, 0.0f, 0.0f);
        }

        @Override
        public void draw(TextureRegion region, float x, float y, float width, float height,
                float originX, float originY, float rotationDegrees) {
            this.x[drawCount] = x;
            this.y[drawCount] = y;
            this.width[drawCount] = width;
            this.height[drawCount] = height;
            red[drawCount] = currentRed;
            green[drawCount] = currentGreen;
            blue[drawCount] = currentBlue;
            alpha[drawCount] = currentAlpha;
            drawCount++;
        }

        @Override
        public void draw(TextureRegion region, float[] centerX, float[] centerY, int count,
                float width, float height, float originX, float originY, float rotationDegrees) {
        }

        @Override
        public void end() {
        }

        @Override
        public void dispose() {
        }

        @Override
        public boolean isDisposed() {
            return false;
        }
    }

    private static final class TestTexture implements Texture {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test");
        private final int width;
        private final int height;

        TestTexture(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public TextureFormat format() {
            return TextureFormat.RGBA8_UNORM;
        }

        @Override
        public TextureUsage usage() {
            return TextureUsage.SAMPLED;
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        public <T> T as() {
            return null;
        }

        @Override
        public void dispose() {
        }

        @Override
        public boolean isDisposed() {
            return false;
        }
    }
}
