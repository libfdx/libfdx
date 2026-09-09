package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.*;
import java.nio.ByteBuffer;

/** Opaque material study shared by all effect panels. Generated only when their dimensions change. */
public final class EffectsMaterialScene implements Disposable {
    private Texture albedo, normals;
    private boolean disposed;

    public Texture albedo() { return albedo; }
    public Texture normals() { return normals; }

    public void resize(GraphicsDevice device, int width, int height) {
        // Two source samples per presentation pixel keep curved silhouettes smooth, including LOW.
        int w = Math.min(2048, width * 2), h = Math.min(2048, height * 2);
        ByteBuffer colors = ByteBuffer.allocateDirect(w * h * 4);
        ByteBuffer vectors = ByteBuffer.allocateDirect(w * h * 4);
        float aspect = (float) width / height;
        float span = Math.max(1.45f, 2.35f / aspect);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float px = ((x + .5f) / w - .5f) * span * aspect;
            float py = (.5f - (y + .5f) / h) * span;
            float nx = 0, ny = 0, nz = 1;
            float r = 27, g = 36, b = 49;
            // Recessed wall seams provide fine edges for the AA comparison.
            if (Math.abs(px * 4 - Math.round(px * 4)) < .012f
                    || Math.abs(py * 4 - Math.round(py * 4)) < .012f) {
                r = 19; g = 26; b = 37;
            }
            // A warm ceramic sphere, a cool torus, and a small polished bead.
            float dx = (px + .37f) / .43f, dy = (py - .04f) / .43f;
            float rr = dx * dx + dy * dy;
            if (rr < 1) {
                nx = dx; ny = dy; nz = (float) Math.sqrt(1 - rr);
                r = 197; g = 176; b = 145;
            }
            float tx = px - .52f, ty = py - .02f;
            float distance = (float) Math.sqrt(tx * tx + ty * ty);
            float tube = (distance - .245f) / .095f;
            if (Math.abs(tube) < 1) {
                nx = tube * tx / distance; ny = tube * ty / distance;
                nz = (float) Math.sqrt(1 - tube * tube);
                r = 126; g = 181; b = 197;
            }
            dx = (px - .86f) / .105f; dy = (py + .31f) / .105f;
            rr = dx * dx + dy * dy;
            if (rr < 1) {
                nx = dx; ny = dy; nz = (float) Math.sqrt(1 - rr);
                r = 220; g = 226; b = 234;
            }
            // Bright inlays and small ticks expose bloom spread and clipped LDR highlights.
            if (Math.abs(px) < .94f && Math.abs(py + .53f) < .015f) {
                r = 208; g = 237; b = 246;
            }
            if (px > -.91f && px < -.35f && Math.abs(py - .55f) < .014f) {
                r = 255; g = 157; b = 75;
            }
            if (py < -.59f && py > -.64f && Math.abs(px) < .9f
                    && Math.abs(px * 20 - Math.round(px * 20)) < .10f) {
                r = 100; g = 121; b = 143;
            }
            colors.put((byte) r).put((byte) g).put((byte) b).put((byte) 255);
            vectors.put((byte) Math.round((nx + 1) * 127.5f))
                    .put((byte) Math.round((ny + 1) * 127.5f))
                    .put((byte) Math.round((nz + 1) * 127.5f)).put((byte) 255);
        }
        colors.flip(); vectors.flip();
        Texture nextAlbedo = null, nextNormals = null;
        try {
            nextAlbedo = device.createTexture(TextureDescriptor.rgba8("effects material colors", w, h).filter(TextureFilter.LINEAR));
            nextNormals = device.createTexture(TextureDescriptor.rgba8("effects material normals", w, h).filter(TextureFilter.LINEAR));
            device.writeTexture(nextAlbedo, colors);
            device.writeTexture(nextNormals, vectors);
        } catch (RuntimeException | Error failure) {
            if (nextAlbedo != null) nextAlbedo.dispose();
            if (nextNormals != null) nextNormals.dispose();
            throw failure;
        }
        if (albedo != null) albedo.dispose();
        if (normals != null) normals.dispose();
        albedo = nextAlbedo; normals = nextNormals;
    }

    @Override public boolean isDisposed() { return disposed; }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        try { if (normals != null) normals.dispose(); }
        finally { if (albedo != null) albedo.dispose(); }
    }
}
