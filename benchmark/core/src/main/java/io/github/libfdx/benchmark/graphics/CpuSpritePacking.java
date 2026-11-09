package io.github.libfdx.benchmark.graphics;

final class CpuSpritePacking {
    private final SpriteVertexPacker packer;
    final float[][] batches;

    CpuSpritePacking(int spriteCount, int batchCapacity, SpriteVertexPacker nativePacker) {
        packer = nativePacker == null ? CpuSpritePacking::packJava : nativePacker;
        batches = new float[(spriteCount + batchCapacity - 1) / batchCapacity][];
        for (int i = 0; i < batches.length; i++) {
            int firstSprite = i * batchCapacity;
            float[] batch = new float[Math.min(batchCapacity, spriteCount - firstSprite) * 3];
            for (int offset = 0; offset < batch.length; offset += 3) {
                batch[offset + 2] = 360f * (firstSprite + offset / 3) / spriteCount;
            }
            batches[i] = batch;
        }
    }

    void pack(float[] sprites, float[] vertices, float rotationStep, float scale) {
        packer.pack(sprites, vertices, CpuSpriteMath.sineTable(), rotationStep, scale);
    }

    private static void packJava(float[] sprites, float[] vertices, float[] sineTable,
            float rotationStep, float scale) {
        for (int input = 0, output = 0; input < sprites.length; input += 3, output += 20) {
            float rotation = sprites[input + 2] + rotationStep;
            if (rotation >= 360f) rotation -= 360f;
            sprites[input + 2] = rotation;
            CpuSprite.writePositions(vertices, output, sprites[input], sprites[input + 1], rotation, scale, scale);
        }
    }

    // Run before warm-up using copies so the rendered scene starts from identical state.
    void verify() {
        for (float[] batch : batches) {
            float[] expectedSprites = batch.clone();
            float[] actualSprites = batch.clone();
            float[] expectedVertices = new float[batch.length / 3 * 20];
            float[] actualVertices = new float[expectedVertices.length];
            for (int offset = 0; offset < expectedVertices.length; offset += 20) {
                CpuSprite.initializeVertices(expectedVertices, offset);
                CpuSprite.initializeVertices(actualVertices, offset);
            }
            float[] table = CpuSpriteMath.sineTable();
            packJava(expectedSprites, expectedVertices, table, 0f, 1f);
            pack(actualSprites, actualVertices, 0f, 1f);
            checkBits(expectedSprites, actualSprites);
            checkBits(expectedVertices, actualVertices);
            packJava(expectedSprites, expectedVertices, table, 20f / 60f, 0.625f);
            pack(actualSprites, actualVertices, 20f / 60f, 0.625f);
            checkBits(expectedSprites, actualSprites);
            checkBits(expectedVertices, actualVertices);
        }
    }

    private static void checkBits(float[] expected, float[] actual) {
        for (int i = 0; i < expected.length; i++) {
            if (Float.floatToRawIntBits(expected[i]) != Float.floatToRawIntBits(actual[i])) {
                throw new IllegalStateException("Sprite packing differs from the Java reference at float " + i
                        + " (expected " + expected[i] + ", got " + actual[i] + ")");
            }
        }
    }
}
