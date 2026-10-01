package io.github.libfdx.benchmark.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Isolates the same full-array direct-buffer transfer used by batch flush. */
final class CpuSpriteStagingDiagnostic {
    private static final int PASSES = 20000;

    static void run(float[] vertices) {
        FloatBuffer target = ByteBuffer.allocateDirect(vertices.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        transfer(vertices, target);
        check(vertices, target, -1);
        for (int sample = 0; sample < 5; sample++) {
            long start = System.nanoTime();
            transfer(vertices, target);
            long elapsed = System.nanoTime() - start;
            System.out.println("STAGING_SAMPLE sample=" + sample + " floats=" + vertices.length
                    + " passes=" + PASSES + " nanos=" + elapsed);
            check(vertices, target, sample);
        }
    }

    private static void transfer(float[] vertices, FloatBuffer target) {
        for (int pass = 0; pass < PASSES; pass++) {
            target.clear();
            target.put(vertices, 0, vertices.length);
        }
    }

    private static void check(float[] vertices, FloatBuffer target, int sample) {
        if (target.position() != vertices.length) throw new IllegalStateException("Incomplete staging");
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < vertices.length; i++) {
            int bits = Float.floatToRawIntBits(target.get(i));
            if (bits != Float.floatToRawIntBits(vertices[i])) {
                throw new IllegalStateException("Staging changed vertex bits at " + i);
            }
            hash = (hash ^ (bits & 0xffffffffL)) * 0x100000001b3L;
        }
        System.out.println("STAGING_STATE sample=" + sample + " hash=" + hash);
    }
}
