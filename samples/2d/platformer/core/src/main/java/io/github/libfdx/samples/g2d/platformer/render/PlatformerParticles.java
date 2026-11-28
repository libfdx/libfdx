package io.github.libfdx.samples.g2d.platformer.render;

import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.samples.g2d.platformer.PlatformerGame;
import static io.github.libfdx.samples.g2d.platformer.PlatformerLevel.pixel;

/** Small fixed pool for contact dust, gear sparks and stomp impacts. Updated at the simulation rate. */
final class PlatformerParticles {
    private static final int CAPACITY = 48;
    private final float[] x = new float[CAPACITY], y = new float[CAPACITY], vx = new float[CAPACITY], vy = new float[CAPACITY],
            life = new float[CAPACITY], size = new float[CAPACITY];
    private final int[] frame = new int[CAPACITY];
    private int cursor, sequence;

    void clear() {
        for (int i = 0; i < CAPACITY; i++) life[i] = 0;
        cursor = sequence = 0;
    }

    void step(PlatformerGame game, int events) {
        for (int i = 0; i < CAPACITY; i++) {
            if (life[i] <= 0) continue;
            life[i] -= 1f / 60;
            x[i] += vx[i] / 60;
            y[i] += vy[i] / 60;
            vy[i] -= 90f / 60;
        }
        int impacts = PlatformerGame.STOMP | PlatformerGame.HURT;
        boolean spark = (events & (impacts | PlatformerGame.COIN)) != 0;
        int count = (events & impacts) != 0 ? 13 : spark ? 7 : (events & (PlatformerGame.JUMP | PlatformerGame.LAND)) != 0 ? 5 : 0;
        for (int j = 0; j < count; j++) {
            int i = cursor;
            cursor = (cursor + 1) % CAPACITY;
            int seed = ++sequence % 71;
            x[i] = pixel(game.player().x()) + (seed * 7 % 9) - 4;
            y[i] = pixel(game.player().y() - game.player().halfHeight()) + (spark ? 5 : 1);
            vx[i] = (seed * 29 % 87) - 43;
            vy[i] = 13 + seed * 13 % 46;
            size[i] = (spark ? 5 : 3) + seed % 4;
            frame[i] = spark ? 30 : 33;
            life[i] = .24f + (seed % 14) / 60f;
        }
    }

    void draw(AuthoredPlatformerRenderer renderer, TextureRegion[] actors) {
        for (int i = 0; i < CAPACITY; i++) {
            if (life[i] > 0) renderer.fleck(actors[frame[i]], x[i], y[i], size[i], Math.min(1, life[i] * 4));
        }
    }

}
