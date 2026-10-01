package io.github.libfdx.benchmark.graphics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CpuSpriteTest {
    @Test
    void matchesIndependentCornerTransformAndPreservesPackedAttributes() {
        for (float angle : new float[] {0, 13, 90, 180, 270, -57, 721}) {
            CpuSprite sprite = new CpuSprite(null, angle);
            sprite.setPosition(41, 73);
            sprite.setScale(0.7f);
            float[] vertices = sprite.getVertices();
            double radians = Math.toRadians(angle);
            float[] corners = {-16, -16, -16, 16, 16, 16, 16, -16};
            for (int i = 0; i < 4; i++) {
                double x = corners[i * 2] * 0.7;
                double y = corners[i * 2 + 1] * 0.7;
                assertEquals(x * Math.cos(radians) - y * Math.sin(radians) + 57, vertices[i * 5], 0.02);
                assertEquals(y * Math.cos(radians) + x * Math.sin(radians) + 89, vertices[i * 5 + 1], 0.02);
                assertEquals(0xfeffffff, Float.floatToRawIntBits(vertices[i * 5 + 2]));
            }
            assertSame(vertices, sprite.getVertices(), "The frame loop must reuse vertex storage");
            assertEquals(20, vertices.length);
            assertEquals(0, vertices[3]); assertEquals(1, vertices[4]);
            assertEquals(0, vertices[8]); assertEquals(0, vertices[9]);
            assertEquals(1, vertices[13]); assertEquals(0, vertices[14]);
            assertEquals(1, vertices[18]); assertEquals(1, vertices[19]);
            sprite.rotate(90);
            sprite.setScale(1);
            assertSame(vertices, sprite.getVertices());
        }
    }

    @Test
    void usesExactCardinalAnglesAndInvalidatesVertices() {
        CpuSprite sprite = new CpuSprite(null, 0);
        assertEquals(0, sprite.getVertices()[0]);
        sprite.setPosition(10, 20);
        assertEquals(10, sprite.getVertices()[0]);
        sprite.rotate(90);
        assertEquals(42, sprite.getVertices()[0]);
        assertEquals(20, sprite.getVertices()[1]);
        sprite.setScale(0.5f);
        assertEquals(34, sprite.getVertices()[0]);
        assertEquals(28, sprite.getVertices()[1]);
    }
}
