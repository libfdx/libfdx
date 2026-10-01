package io.github.libfdx.benchmark.graphics;

/**
 * Opt-in CPU isolation for the real benchmark objects. Native launchers accept
 * {@code --cpuDiagnostic=full|transform|cached|batch|append|copy|staging} with {@code --benchmark=sprite_batch_libfdx}.
 * Warmup/sample passes: 2000 in sprite modes; 20000 in staging; five samples, 8191 sprites.
 * Setup, checksums, GPU upload and presentation are outside the measurements.
 * Modes are independent experiments, not additive components of a frame.
 */
final class CpuSpriteDiagnostic {
    private static final int PASSES = 2000;
    private static final float ROTATION = 20f * (1f / 60f);
    private static final float SCALE = 0.875f;

    static void run(String mode, CpuSprite[] sprites, CpuSpriteBatch batch) {
        if (!mode.equals("full") && !mode.equals("transform") && !mode.equals("cached")
                && !mode.equals("batch") && !mode.equals("append") && !mode.equals("copy")
                && !mode.equals("staging")) {
            throw new IllegalArgumentException("Unknown cpuDiagnostic: " + mode);
        }
        if (sprites.length != 8191 || batch.diagnosticVertices().length != sprites.length * 20) {
            throw new IllegalArgumentException("cpuDiagnostic requires 8191 sprites in one batch");
        }
        batch.begin();
        // Resolve the initial dirty vertices and texture switch before measuring.
        for (CpuSprite sprite : sprites) sprite.draw(batch);
        if (mode.equals("staging")) {
            CpuSpriteStagingDiagnostic.run(batch.diagnosticVertices());
            batch.end();
            System.out.println("CPU_PHASE_DONE mode=" + mode + " draws=" + batch.renderCalls()
                    + " bytes=" + batch.uploadedBytes());
            return;
        }
        passes(mode, sprites, batch);
        state(mode, -1, sprites, batch);
        for (int sample = 0; sample < 5; sample++) {
            long start = System.nanoTime();
            passes(mode, sprites, batch);
            long elapsed = System.nanoTime() - start;
            System.out.println("CPU_PHASE mode=" + mode + " sample=" + sample
                    + " sprites=" + sprites.length + " passes=" + PASSES + " nanos=" + elapsed);
            state(mode, sample, sprites, batch);
        }
        // Submit once after timing so the normal render-pass lifecycle is exercised.
        batch.diagnosticReset();
        for (CpuSprite sprite : sprites) sprite.draw(batch);
        batch.end();
        System.out.println("CPU_PHASE_DONE mode=" + mode + " draws=" + batch.renderCalls()
                + " bytes=" + batch.uploadedBytes());
    }

    private static void passes(String mode, CpuSprite[] sprites, CpuSpriteBatch batch) {
        // Dispatch outside both loops. Do not add a per-sprite mode branch or timer.
        if (mode.equals("full")) full(sprites, batch);
        else if (mode.equals("transform")) transform(sprites);
        else if (mode.equals("cached")) cached(sprites, batch);
        else if (mode.equals("batch")) batch(sprites, batch);
        else if (mode.equals("append")) append(sprites, batch);
        else copy(sprites, batch.diagnosticVertices());
    }

    private static void full(CpuSprite[] sprites, CpuSpriteBatch batch) {
        for (int pass = 0; pass < PASSES; pass++) {
            batch.diagnosticReset();
            for (int i = 0; i < sprites.length; i++) {
                CpuSprite current = sprites[i];
                current.rotate(ROTATION);
                current.setScale(SCALE);
                current.draw(batch);
            }
        }
    }

    private static void transform(CpuSprite[] sprites) {
        for (int pass = 0; pass < PASSES; pass++) {
            for (int i = 0; i < sprites.length; i++) {
                CpuSprite current = sprites[i];
                current.rotate(ROTATION);
                current.setScale(SCALE);
                current.getVertices();
            }
        }
    }

    private static void cached(CpuSprite[] sprites, CpuSpriteBatch batch) {
        for (int pass = 0; pass < PASSES; pass++) {
            batch.diagnosticReset();
            for (int i = 0; i < sprites.length; i++) sprites[i].draw(batch);
        }
    }

    private static void copy(CpuSprite[] sprites, float[] vertices) {
        for (int pass = 0; pass < PASSES; pass++) {
            for (int i = 0; i < sprites.length; i++) {
                System.arraycopy(sprites[i].vertices, 0, vertices, i * 20, 20);
            }
        }
    }

    private static void batch(CpuSprite[] sprites, CpuSpriteBatch batch) {
        for (int pass = 0; pass < PASSES; pass++) {
            batch.diagnosticReset();
            for (int i = 0; i < sprites.length; i++) {
                CpuSprite current = sprites[i];
                batch.draw(current.diagnosticTexture(), current.vertices, 0, 20);
            }
        }
    }

    private static void append(CpuSprite[] sprites, CpuSpriteBatch batch) {
        for (int pass = 0; pass < PASSES; pass++) {
            batch.diagnosticReset();
            for (int i = 0; i < sprites.length; i++) batch.diagnosticAppend(sprites[i].vertices);
        }
    }

    private static void state(String mode, int sample, CpuSprite[] sprites, CpuSpriteBatch batch) {
        if (batch.diagnosticCursor() != sprites.length * 20 || batch.renderCalls() != 0) {
            throw new IllegalStateException("Unexpected flush or incomplete batch in CPU diagnostic");
        }
        long spriteHash = 0xcbf29ce484222325L;
        for (CpuSprite sprite : sprites) spriteHash = hash(spriteHash, sprite.vertices);
        long batchHash = hash(0xcbf29ce484222325L, batch.diagnosticVertices());
        if (!mode.equals("transform") && spriteHash != batchHash) {
            throw new IllegalStateException("Copied vertices differ from sprite vertices");
        }
        System.out.println("CPU_PHASE_STATE mode=" + mode + " sample=" + sample
                + " spriteHash=" + spriteHash + " batchHash=" + batchHash);
    }

    private static long hash(long value, float[] vertices) {
        for (float vertex : vertices) {
            value ^= Float.floatToRawIntBits(vertex) & 0xffffffffL;
            value *= 0x100000001b3L;
        }
        return value;
    }
}
