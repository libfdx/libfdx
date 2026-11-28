package io.github.libfdx.samples.g2d.platformer.render;

import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.samples.g2d.platformer.PlatformerGame;
import io.github.libfdx.samples.g2d.platformer.PlatformerLevel;

import static io.github.libfdx.samples.g2d.platformer.PlatformerLevel.pixel;

/** Paints the collision banks as a continuous rock face with an illustrated surface along its contour. */
final class PlatformerGround {
    // The upper atlas row holds periodic rock followed by two exposed edges. The lower row holds topsoil.
    private static final int GUTTER = 2, EDGE_TEXELS = 48;
    private static final float TILE_WIDTH = 328, ROCK_HEIGHT = 130, COVER_HEIGHT = 34;
    static final float EDGE_INSET = 3;
    private static final float OPEN = Float.NEGATIVE_INFINITY;
    private final SpriteBatch batch;
    private PlatformerGame game;
    private Bank[] banks;
    private Texture material;
    private float left, bottom, sx, sy;
    private float u0, du, rockV, coverV, dv;

    PlatformerGround(SpriteBatch batch) {
        this.batch = batch;
    }

    void prepare(PlatformerGame level) {
        if (game != level) {
            banks = banks(level);
            game = level;
        }
    }

    /** Fit the full width between the supplied edges. A span that is too narrow has no placement. */
    static float fitLeft(float desired, float width, float left, float right) {
        if (right - left < width) return Float.NaN;
        return Math.max(left, Math.min(desired, right - width));
    }

    /** Find the nearest bank below the beam that supports the entire base within its horizontal span. */
    float footing(float desired, float width, float left, float right, float beam) {
        float best = Float.NaN, distance = Float.POSITIVE_INFINITY;
        for (Bank bank : banks) {
            if (bank.top >= beam) continue;
            float x = fitLeft(desired, width, Math.max(left, bank.start + EDGE_INSET),
                    Math.min(right, bank.end - EDGE_INSET));
            float shift = Math.abs(x - desired);
            if (shift < distance) {
                best = x;
                distance = shift;
            }
        }
        return best;
    }

    float topAt(float x, float fallback) {
        for (Bank bank : banks) {
            if (x >= bank.start && x <= bank.end) return bank.top;
        }
        return fallback;
    }

    /** A fixed pier stands directly under its joint and needs room for its whole base. */
    float pierTop(float left, float width, float ceiling) {
        for (Bank bank : banks) {
            if (bank.top < ceiling && left >= bank.start + EDGE_INSET - .001f
                    && left + width <= bank.end - EDGE_INSET + .001f) return bank.top;
        }
        return Float.NaN;
    }

    float liftMast(float edge, boolean right, float ceiling) {
        return right ? footing(edge + 1, 5, edge - 10, edge + 18, ceiling)
                : footing(edge - 6, 5, edge - 18, edge + 10, ceiling);
    }

    void draw(Texture texture, float cameraLeft, float cameraBottom, float width, float height) {
        left = cameraLeft;
        bottom = cameraBottom;
        sx = 2 / width;
        sy = 2 / height;
        material = texture;
        u0 = (float) GUTTER / texture.width();
        du = (float) (texture.width() - 2 * (EDGE_TEXELS + 2 * GUTTER) - 2 * GUTTER) / texture.width();
        rockV = (float) GUTTER / texture.height();
        coverV = .5f + rockV;
        dv = .5f - 2 * rockV;

        // Finish the entire foundation before laying any turf over the contacts.
        for (Bank bank : banks) {
            if (bank.end > left && bank.start < left + width) foundation(bank, left + width);
        }
        for (Bank bank : banks) {
            if (bank.end + 12 > left && bank.start - 12 < left + width) surface(bank, left + width);
        }
        batch.color(1, 1, 1, 1);
    }

    private static Bank[] banks(PlatformerGame level) {
        int count = 0;
        for (int i = 0; i < level.solidCount(); i++) {
            if (!level.solidAt(i).isBridge()) count++;
        }
        Bank[] result = new Bank[count];
        int index = 0;
        for (int i = 0; i < level.solidCount(); i++) {
            var solid = level.solidAt(i);
            if (solid.isBridge()) continue;
            float x = pixel(solid.x()), radius = solid.halfWidth() * PlatformerLevel.PIXELS_PER_UNIT;
            result[index++] = new Bank(x - radius, x + radius, pixel(solid.y() + solid.halfHeight()));
        }
        for (Bank bank : result) {
            for (Bank other : result) {
                if (Math.abs(bank.start - other.end) < .001f) {
                    bank.start = other.end;
                    bank.before = other.top;
                }
                if (Math.abs(bank.end - other.start) < .001f) bank.after = other.top;
            }
        }
        return result;
    }

    private void foundation(Bank bank, float right) {
        float ceiling = bank.top - 2;
        for (float y = ceiling; y > bottom;) {
            float tileTop = (float) Math.ceil(y / ROCK_HEIGHT) * ROCK_HEIGHT;
            float low = tileTop - ROCK_HEIGHT;
            boolean bareLeft = y > bank.before, bareRight = y > bank.after;
            if (bareLeft) low = Math.max(low, bank.before);
            if (bareRight) low = Math.max(low, bank.after);
            float fromV = rockV + dv * (tileTop - y) / ROCK_HEIGHT;
            float toV = rockV + dv * (tileTop - low) / ROCK_HEIGHT;
            // Only an exposed face needs a silhouette. Where banks touch, their world UVs meet directly.
            // Start the opaque fill underneath the opaque part of the painted edge.
            float width = Math.min(12, (bank.end - bank.start) * .5f);
            float inset = width / 3;
            strip(bank.start + (bareLeft ? inset : 0), bank.end - (bareRight ? inset : 0),
                    low, y, fromV, toV, right, 1);
            if (bareLeft) edge(0, bank.start - width / 12, width, low, y, fromV, toV);
            if (bareRight) edge(1, bank.end - width * 11 / 12, width, low, y, fromV, toV);
            y = low;
        }
    }

    private void edge(int side, float x, float w, float low, float high, float v, float v2) {
        int stride = EDGE_TEXELS + 2 * GUTTER;
        float u = 1 - (float) ((2 - side) * stride - GUTTER) / material.width();
        batch.color(1, 1, 1, 1);
        batch.drawUv(material, u, v, u + (float) EDGE_TEXELS / material.width(), v2,
                (x - left) * sx - 1, (low - bottom) * sy - 1, w * sx, (high - low) * sy);
    }

    private void surface(Bank bank, float right) {
        float high = bank.top + 3;
        float low = high - COVER_HEIGHT;
        // At the base of an upward step, carry the turf a little way into the wall and feather the contact.
        // At a drop, taper the painted apron before its otherwise straight cut edge.
        float feather = Math.min(12, (bank.end - bank.start) * .24f);
        float a = bank.start, b = bank.end;
        if (bank.before > bank.top + .01f) a -= feather;
        if (bank.after > bank.top + .01f) b += feather;
        float coreL = Math.abs(bank.before - bank.top) < .01f ? a : a + feather;
        float coreR = Math.abs(bank.after - bank.top) < .01f ? b : b - feather;
        strip(coreL, coreR, low, high, coverV, coverV + dv, right, 1);
        if (a < coreL) capEdge(bank, a, coreL, true, low, high, right);
        if (b > coreR) capEdge(bank, coreR, b, false, low, high, right);
    }

    private void capEdge(Bank bank, float a, float b, boolean atLeft, float low, float high, float right) {
        // Keep the walking lip intact on a drop. Its hanging roots and rock fade into the shared foundation.
        boolean drop = (atLeft ? bank.before : bank.after) < bank.top;
        for (int i = 0; i < 12; i++) {
            float x1 = a + (b - a) * i / 12, x2 = a + (b - a) * (i + 1) / 12;
            float t = (atLeft ? i + .5f : 11.5f - i) / 12;
            float alpha = t * t * (3 - 2 * t);
            if (drop) {
                float lip = high - (2 + 10 * t);
                float lipV = coverV + dv * (high - lip) / COVER_HEIGHT;
                strip(x1, x2, lip, high, coverV, lipV, right, 1);
                strip(x1, x2, low, lip, lipV, coverV + dv, right, alpha);
            } else {
                strip(x1, x2, low, high, coverV, coverV + dv, right, alpha);
            }
        }
    }

    private void strip(float a, float b, float low, float high, float fromV, float toV,
            float right, float alpha) {
        a = Math.max(a, left);
        b = Math.min(b, right);
        if (a >= b) return;
        batch.color(1, 1, 1, alpha);
        while (a < b) {
            float tile = (float) Math.floor(a / TILE_WIDTH) * TILE_WIDTH;
            float end = Math.min(b, tile + TILE_WIDTH);
            batch.drawUv(material, u0 + du * (a - tile) / TILE_WIDTH, fromV,
                    u0 + du * (end - tile) / TILE_WIDTH, toV,
                    (a - left) * sx - 1, (low - bottom) * sy - 1, (end - a) * sx, (high - low) * sy);
            a = end;
        }
    }

    private static final class Bank {
        float start;
        final float end, top;
        float before = OPEN, after = OPEN;

        Bank(float start, float end, float top) {
            this.start = start;
            this.end = end;
            this.top = top;
        }

    }

}
