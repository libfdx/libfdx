package io.github.libfdx.graphics.particles;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import java.nio.ByteBuffer;

/** Optional procedural artwork usable by either particle emitter or any sprite renderer. */
public enum ParticleSprite {
    DISC, FLAME, SMOKE, SPARK, SNOW, RING;

    /** Creates and uploads a caller-owned texture. Call only during setup, not each frame. */
    public Texture createTexture(GraphicsDevice device, int size) {
        if (device == null) throw new FdxException("Particle texture device cannot be null");
        ByteBuffer pixels = pixels(size);
        Texture texture = device.createTexture(TextureDescriptor.rgba8("particle " + name(), size, size));
        try {
            device.writeTexture(texture, pixels);
            return texture;
        } catch (RuntimeException error) {
            texture.dispose();
            throw error;
        }
    }

    /** Allocates straight-alpha RGBA8 artwork with a narrow antialiased edge, at 16 2048 pixels. */
    public ByteBuffer pixels(int size) {
        if (size < 16 || size > 2048) throw new FdxException("Particle sprite size must be 16�2048");
        ByteBuffer pixels = ByteBuffer.allocateDirect(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float px = (x + 0.5f) * 2 / size - 1;
                float py = (y + 0.5f) * 2 / size - 1;
                float r = (float)Math.sqrt(px * px + py * py);
                float edge = 3f / size;
                float a;
                float shade = 1;
                switch (this) {
                    case FLAME -> {
                        float height = (1 - py) * 0.5f;
                        float width = 0.06f + 0.59f * (float)Math.sin(Math.PI * Math.pow(height, 0.65));
                        float bend = 0.12f * (float)Math.sin(height * 8) * height;
                        a = smooth((width - Math.abs(px - bend)) / 0.10f)
                                * smooth((height - 0.04f) / 0.1f) * smooth((0.96f - height) / 0.14f);
                        shade = 0.7f + 0.3f * smooth((width - Math.abs(px - bend)) / width);
                    }
                    case SMOKE -> {
                        float lobes = 0.70f + 0.08f * (float)Math.sin(Math.atan2(py, px) * 5)
                                + 0.045f * (float)Math.cos(Math.atan2(py, px) * 9);
                        a = smooth((lobes - r) / 0.20f);
                        shade = 0.65f + 0.20f * (float)Math.sin(px * 9 + py * 4)
                                * (float)Math.cos(py * 11 - px * 3) + 0.15f * (1 - r);
                    }
                    case SPARK -> a = smooth((0.83f - Math.abs(px) / 0.23f - Math.abs(py)) / edge);
                    case SNOW -> {
                        float angle = (float)Math.atan2(py, px);
                        float arm = Math.abs((float)Math.sin(angle * 3)) * r;
                        a = smooth((0.12f - arm) / edge) * smooth((0.8f - r) / edge);
                    }
                    case RING -> a = smooth((0.075f - Math.abs(r - 0.7f)) / edge);
                    default -> a = smooth((0.78f - r) / edge);
                }
                int c = Math.round(Math.max(0, Math.min(1, shade)) * 255);
                pixels.put((byte)c).put((byte)c).put((byte)c).put((byte)Math.round(a * 255));
            }
        }
        pixels.flip();
        return pixels;
    }

    private static float smooth(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }
}
