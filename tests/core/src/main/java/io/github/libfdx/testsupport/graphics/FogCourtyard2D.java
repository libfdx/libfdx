package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import java.nio.ByteBuffer;

/** Crisp, opaque top-down artwork. Static geometry is retained independently of the fog. */
public final class FogCourtyard2D implements Disposable {
    private static final int DARK = 0x080e16ff;
    private final GraphicsContext graphics;
    private final ShapeRenderer scenery, actors;
    private final SpriteBatch overlay;
    private final Texture fog;
    private final ByteBuffer pixels = ByteBuffer.allocateDirect(FogExploration.COLUMNS * FogExploration.ROWS * 4);
    private int uploadedRevision = -1;
    private boolean disposed;

    public FogCourtyard2D(GraphicsContext graphics) {
        this.graphics = graphics;
        scenery = new ShapeRenderer(graphics, 24000);
        actors = new ShapeRenderer(graphics, 256);
        overlay = new SpriteBatch(graphics, 1);
        fog = graphics.device().createTexture(TextureDescriptor.rgba8("2D persistent exploration",
                FogExploration.COLUMNS, FogExploration.ROWS).filter(TextureFilter.LINEAR));
        ground();
        // The same full footprints used in 3D also keep 2D canopies and stones apart.
        FogSceneLayout.populate((model, x, y, z, scale, halfWidth, halfDepth, radius) -> {
            switch (model) {
                case 1 -> tree(x, -z, scale);
                case 2 -> masonry(x, -z, halfWidth, halfDepth, false);
                case 3 -> masonry(x, -z, halfWidth, halfDepth, true);
                case 5 -> rock(x, -z, scale);
                default -> throw new IllegalArgumentException("Unknown courtyard prop: " + model);
            }
        });
    }

    private void ground() {
        rect(-24, -20, 48, 40, 0x283e37ff);
        // Low-contrast grass clusters break up the ground without a checkerboard.
        for (int row = 0; row < 50; row++) for (int col = 0; col < 58; col++) {
            float x = -23.7f + col * .82f + (float)Math.sin(col * 17 + row * 3) * .26f;
            float y = -19.7f + row * .79f + (float)Math.cos(col * 5 + row * 11) * .25f;
            int color = (col + row) % 3 == 0 ? 0x354d40ff : 0x2f453bff;
            tri(x - .12f, y - .08f, x - .02f, y + .18f, x + .04f, y - .08f, color);
            tri(x, y - .1f, x + .15f, y + .1f, x + .13f, y - .1f, color);
        }
        rect(-1.8f, -20, 3.6f, 40, 0x465344ff);
        rect(-24, 1.2f, 48, 3.6f, 0x465344ff);
        rect(-1.58f, -20, 3.16f, 40, 0x716f56ff);
        rect(-24, 1.42f, 48, 3.16f, 0x716f56ff);
        for (int row = 0; row < 40; row++) for (int col = 0; col < 3; col++)
            paver(-1.46f + col * .98f, -19.93f + row, .9f, .9f, row + col);
        for (int col = 0; col < 48; col++) {
            float x = -23.96f + col;
            if (x > -2.4f && x < 1.6f) continue;
            for (int row = 0; row < 3; row++) paver(x, 1.54f + row * .98f, .9f, .9f, row + col);
        }
        // An inset border gives the finite courtyard an intentional edge.
        rect(-24, -20, 48, .2f, 0x182c29ff); rect(-24, 19.8f, 48, .2f, 0x182c29ff);
        rect(-24, -20, .2f, 40, 0x182c29ff); rect(23.8f, -20, .2f, 40, 0x182c29ff);
    }

    private void paver(float x, float y, float width, float height, int variant) {
        rect(x, y, width, height, variant % 3 == 0 ? 0x929077ff : 0x868970ff);
        tri(x + width - .16f, y, x + width, y + .13f, x + width, y, 0x6f785fff);
        if (variant % 5 == 0) rect(x + .17f, y + .23f, .24f, .035f, 0x7a8067ff);
    }

    private void tree(float x, float y, float scale) {
        // An illustrated trunk and irregular leaf clusters, with no cast shadow.
        // The complete silhouette remains inside the shared obstacle footprint.
        float variation = (float)Math.sin(x * 7 + y * 11);
        rect(x - .15f * scale, y - 1.08f * scale, .3f * scale, .9f * scale, 0x91633fff);
        tri(x - .15f * scale, y - .69f * scale, x - .42f * scale, y - .43f * scale,
                x - .11f * scale, y - .51f * scale, 0x91633fff);
        tri(x + .15f * scale, y - .62f * scale, x + .43f * scale, y - .34f * scale,
                x + .11f * scale, y - .4f * scale, 0x91633fff);
        rect(x - .03f * scale, y - .93f * scale, .04f * scale, .2f * scale, 0xb28653ff);

        foliage(x, y + .17f * scale, 1.08f * scale, .91f * scale, variation, 0x28583bff);
        foliage(x, y + .19f * scale, 1.01f * scale, .83f * scale, variation, 0x4a884cff);
        foliage(x - .47f * scale, y + .04f * scale, .48f * scale, .45f * scale, variation + 1, 0x599852ff);
        foliage(x + .43f * scale, y + .16f * scale, .49f * scale, .47f * scale, variation + 2, 0x609e54ff);
        foliage(x - .18f * scale, y + .59f * scale, .53f * scale, .43f * scale, variation + 3, 0x69a65aff);
        foliage(x + .02f * scale, y - .22f * scale, .55f * scale, .38f * scale, variation + 4, 0x54964eff);
        foliage(x - .04f * scale, y + .2f * scale, .44f * scale, .42f * scale, variation + 5, 0x72ad60ff);
        sprig(x - .52f * scale, y + .13f * scale, scale, -.3f);
        sprig(x + .48f * scale, y + .25f * scale, scale, .6f);
        sprig(x - .17f * scale, y + .64f * scale, scale, -.65f);
        sprig(x + .02f * scale, y - .22f * scale, scale, .25f);
    }

    private void foliage(float x, float y, float width, float height, float phase, int color) {
        // Smooth, asymmetric lobes avoid repeating a geometric star on every tree.
        for (int i = 0; i < 32; i++) {
            double a = Math.PI * 2 * i / 32, b = Math.PI * 2 * (i + 1) / 32;
            float ra = leafContour(a, phase), rb = leafContour(b, phase);
            tri(x, y, x + (float)Math.cos(a) * width * ra, y + (float)Math.sin(a) * height * ra,
                    x + (float)Math.cos(b) * width * rb, y + (float)Math.sin(b) * height * rb, color);
        }
    }

    private static float leafContour(double angle, float phase) {
        return .91f + .055f * (float)Math.sin(angle * 5 + phase)
                + .035f * (float)Math.cos(angle * 8 - phase);
    }

    private void sprig(float x, float y, float scale, float rotation) {
        for (int side = -1; side <= 1; side += 2) {
            float dx = (float)Math.cos(rotation + side * .75f) * .18f * scale;
            float dy = (float)Math.sin(rotation + side * .75f) * .18f * scale;
            tri(x, y, x + dx * .5f - dy * .24f, y + dy * .5f + dx * .24f,
                    x + dx, y + dy, 0x93be74ff);
            tri(x, y, x + dx, y + dy, x + dx * .5f + dy * .24f,
                    y + dy * .5f - dx * .24f, 0x93be74ff);
        }
    }

    private void rock(float x, float y, float scale) {
        disc(x, y, .78f * scale, 7, 0x596b67ff);
        tri(x - .7f * scale, y + .24f * scale, x - .2f * scale, y + .75f * scale,
                x + .25f * scale, y + .23f * scale, 0x9eada0ff);
        tri(x - .7f * scale, y + .24f * scale, x + .25f * scale, y + .23f * scale,
                x - .23f * scale, y - .47f * scale, 0x7f9389ff);
        tri(x + .25f * scale, y + .23f * scale, x + .7f * scale, y + .3f * scale,
                x + .45f * scale, y - .53f * scale, 0x6e827aff);
    }

    private void masonry(float x, float y, float w, float h, boolean pillar) {
        rect(x - w, y - h, w * 2, h * 2, 0x394e50ff);
        rect(x - w + .06f, y - h + .06f, w * 2 - .12f, h * 2 - .12f, 0x788a84ff);
        if (pillar) {
            rect(x - .37f, y - .27f, .74f, .74f, 0x4e6764ff);
            rect(x - .29f, y - .19f, .58f, .58f, 0x91a397ff);
        } else {
            rect(x - w + .1f, y - .03f, w * 2 - .2f, .045f, 0x4f6461ff);
            rect(x - .035f, y + .01f, .045f, h - .17f, 0x4f6461ff);
        }
    }

    public void render(Camera camera, FogExploration exploration, float x, float z,
            boolean showTarget, float targetX, float targetZ) {
        renderWorld(camera, x, z, showTarget, targetX, targetZ,
                LoadOp.clear(8 / 255f, 14 / 255f, 22 / 255f, 1));
        renderExploration(camera, exploration);
    }

    /** Opaque scenery shared by the atmospheric and exploration demonstrations. */
    public void renderWorld(Camera camera, float x, float z, boolean showTarget,
            float targetX, float targetZ, LoadOp clear) {
        scenery.setProjectionMatrix(camera.combined());
        scenery.begin(ShapeRenderer.ShapeType.Filled, clear);
        scenery.end(false);
        actors.setProjectionMatrix(camera.combined());
        actors.begin(ShapeRenderer.ShapeType.Filled);
        if (showTarget) {
            actors.setColor(0x8ad5c7ff);
            actors.rect(targetX - .18f, -targetZ - .18f, .36f, .36f);
        }
        actors.setColor(0x123d50ff); actors.filledRect(x - .45f, -z - .45f, .9f, .9f);
        actors.setColor(0x36bbd4ff); actors.filledRect(x - .36f, -z - .36f, .72f, .72f);
        actors.end();
    }

    private void renderExploration(Camera camera, FogExploration exploration) {
        if (uploadedRevision != exploration.revision) {
            pixels.clear();
            for (float opacity : exploration.opacity)
                pixels.put((byte)(DARK >>> 24)).put((byte)(DARK >>> 16)).put((byte)(DARK >>> 8))
                        .put((byte)Math.round(opacity * 255));
            pixels.flip(); graphics.device().writeTexture(fog, pixels);
            uploadedRevision = exploration.revision;
        }
        float sx = 2 / (camera.viewportWidth() * camera.zoom());
        float sy = 2 / (camera.viewportHeight() * camera.zoom());
        // Samples represent world grid points. Extend by half a texel so linear
        // filtering puts each sample at its exact world position, including edges.
        float width = FogExploration.WIDTH + 1f / FogExploration.DENSITY;
        float height = FogExploration.DEPTH + 1f / FogExploration.DENSITY;
        overlay.begin(LoadOp.load());
        overlay.draw(fog, (-width * .5f - camera.position().x()) * sx,
                (-height * .5f - camera.position().y()) * sy, width * sx, height * sy);
        overlay.end();
    }

    private void rect(float x, float y, float w, float h, int color) {
        scenery.setColor(color); scenery.filledRect(x, y, w, h);
    }
    private void tri(float ax, float ay, float bx, float by, float cx, float cy, int color) {
        scenery.setColor(color); scenery.filledTriangle(ax, ay, bx, by, cx, cy);
    }
    private void disc(float x, float y, float radius, int sides, int color) {
        scenery.filledCircle(x, y, radius, sides, (color >>> 24) / 255f,
                (color >>> 16 & 255) / 255f, (color >>> 8 & 255) / 255f, 1);
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        scenery.dispose(); actors.dispose(); overlay.dispose(); fog.dispose();
    }
    @Override public boolean isDisposed() { return disposed; }
}
