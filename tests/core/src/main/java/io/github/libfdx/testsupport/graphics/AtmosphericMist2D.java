package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import java.nio.ByteBuffer;

/** Top-down visibility through pale mist. Samples depend only on the current player position. */
public final class AtmosphericMist2D implements Disposable {
    public static final float RED = 122 / 255f, GREEN = 153 / 255f, BLUE = 173 / 255f;
    private static final int DENSITY = 4, WIDTH = 48, DEPTH = 40;
    private static final int COLUMNS = WIDTH * DENSITY + 1, ROWS = DEPTH * DENSITY + 1;
    private final GraphicsContext graphics;
    private final Texture texture;
    private final SpriteBatch overlay;
    private final ByteBuffer pixels = ByteBuffer.allocateDirect(COLUMNS * ROWS * 4);
    private final float[] banks = new float[COLUMNS * ROWS];
    private float lastX = Float.NaN, lastZ, lastStrength, lastRadius;
    private boolean disposed;

    public AtmosphericMist2D(GraphicsContext graphics) {
        this.graphics = graphics;
        texture = graphics.device().createTexture(TextureDescriptor.rgba8("courtyard atmospheric mist", COLUMNS, ROWS)
                .filter(TextureFilter.LINEAR));
        overlay = new SpriteBatch(graphics, 1);
        for (int row = 0; row < ROWS; row++) for (int col = 0; col < COLUMNS; col++) {
            float x = -WIDTH * .5f + col / (float)DENSITY;
            float z = row / (float)DENSITY - DEPTH * .5f;
            // Broad, fixed mist banks vary the sight distance along the path.
            banks[row * COLUMNS + col] = .5f + .25f * (float)Math.sin(x * .31f + z * .17f)
                    + .25f * (float)Math.cos(z * .37f - x * .13f);
        }
    }

    static float opacity(float dx, float dz, float bank, float strength, float radius) {
        // Strength scales optical distance: lower density expands both the clear
        // area and the falloff, while distant mist still becomes fully opaque.
        // At zero strength every sample remains clear, without dividing by zero.
        float distance = (float)Math.sqrt(dx * dx + dz * dz) * strength;
        // Radius sets the distance to opaque mist at full strength.
        // Mist banks soften the interior without leaving silhouettes outside the circle.
        float clearRadius = radius * (.25f + .15f * bank);
        float t = Math.max(0, Math.min(1, (distance - clearRadius) / (radius - clearRadius)));
        return t * t * (3 - 2 * t);
    }

    public void render(Camera camera, float playerX, float playerZ, float strength, float radius) {
        if (playerX != lastX || playerZ != lastZ || strength != lastStrength || radius != lastRadius) {
            pixels.clear();
            for (int row = 0; row < ROWS; row++) for (int col = 0; col < COLUMNS; col++) {
                float x = -WIDTH * .5f + col / (float)DENSITY;
                float z = row / (float)DENSITY - DEPTH * .5f;
                float alpha = opacity(x - playerX, z - playerZ, banks[row * COLUMNS + col], strength, radius);
                pixels.put((byte)122).put((byte)153).put((byte)173).put((byte)Math.round(alpha * 255));
            }
            pixels.flip(); graphics.device().writeTexture(texture, pixels);
            lastX = playerX; lastZ = playerZ; lastStrength = strength; lastRadius = radius;
        }
        float sx = 2 / (camera.viewportWidth() * camera.zoom());
        float sy = 2 / (camera.viewportHeight() * camera.zoom());
        // Center the linearly filtered samples on their world positions.
        float width = WIDTH + 1f / DENSITY, height = DEPTH + 1f / DENSITY;
        overlay.begin(LoadOp.load());
        overlay.draw(texture, (-width * .5f - camera.position().x()) * sx,
                (-height * .5f - camera.position().y()) * sy, width * sx, height * sy);
        overlay.end();
    }

    @Override
    public boolean isDisposed() { return disposed; }
    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        overlay.dispose(); texture.dispose();
    }
}
