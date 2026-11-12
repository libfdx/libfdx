package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.g2d.TextureRegion;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

final class UiNinePatchTest {
    private static final Texture TEXTURE = (Texture)Proxy.newProxyInstance(Texture.class.getClassLoader(),
            new Class<?>[] {Texture.class}, (proxy, method, args) -> switch (method.getName()) {
                case "width", "height" -> 4096;
                default -> throw new AssertionError(method.getName());
            });
    @Test
    void resizingKeepsSourceCutsAndHighResolutionCornersFixed() {
        UiNinePatch patch = UiNinePatch.region(new TextureRegion(TEXTURE, 17, 29, 2172, 724),
                Ui.insets(280, 250, 280, 250), Ui.insets(0)).borderScale(1.0f / 16.0f);
        UiNinePatchLayout layout = new UiNinePatchLayout();
        for (UiRect bounds : new UiRect[] {new UiRect(10, 20, 164, 44), new UiRect(10, 20, 480, 160)}) {
            layout.set(patch, bounds);
            assertArrayEquals(new int[] {17, 297, 1909, 2189}, layout.sourceX);
            assertArrayEquals(new int[] {29, 279, 503, 753}, layout.sourceY);
            assertEquals(17.5f, layout.targetX[1] - layout.targetX[0]);
            assertEquals(17.5f, layout.targetX[3] - layout.targetX[2]);
            assertEquals(15.625f, layout.targetY[1] - layout.targetY[0]);
            assertEquals(15.625f, layout.targetY[3] - layout.targetY[2]);
            assertEquals(bounds.right(), layout.targetX[3]);
            assertEquals(bounds.bottom(), layout.targetY[3]);
        }
    }

    @Test
    void undersizedDestinationsShrinkAsymmetricBordersWithoutChangingSourceCuts() {
        UiNinePatch patch = UiNinePatch.region(new TextureRegion(TEXTURE, 10, 20, 100, 80),
                Ui.insets(30, 10, 10, 30), Ui.insets(0));
        UiNinePatchLayout layout = new UiNinePatchLayout();
        layout.set(patch, new UiRect(5, 7, 20, 10));
        assertArrayEquals(new int[] {10, 40, 100, 110}, layout.sourceX);
        assertArrayEquals(new int[] {20, 30, 70, 100}, layout.sourceY);
        assertArrayEquals(new float[] {5, 20, 20, 25}, layout.targetX);
        assertArrayEquals(new float[] {7, 9.5f, 9.5f, 17}, layout.targetY);
        layout.set(patch, new UiRect(5, 7, 0, 0));
        assertArrayEquals(new float[] {5, 5, 5, 5}, layout.targetX);
        assertArrayEquals(new float[] {7, 7, 7, 7}, layout.targetY);
    }

    @Test
    void scaleIsImmutableValidatedAndPreservedWhenChangingMinimumSize() {
        UiNinePatch original = UiNinePatch.region(new TextureRegion(TEXTURE, 0, 0, 32, 32),
                Ui.insets(8), Ui.insets(3));
        UiNinePatch scaled = original.borderScale(0.5f).minimumSize(40, 20);
        assertEquals(1, original.borderScale());
        assertEquals(0.5f, scaled.borderScale());
        assertSame(original.region(), scaled.region());
        assertSame(original.padding(), scaled.padding());
        for (float scale : new float[] {0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> original.borderScale(scale));
        }
    }

}
