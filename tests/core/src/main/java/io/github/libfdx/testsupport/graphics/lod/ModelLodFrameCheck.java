package io.github.libfdx.testsupport.graphics.lod;

import io.github.libfdx.core.FdxException;
import java.nio.ByteBuffer;

/** Fixture-specific pixel guard: both cyan models must be visible and centered in their panels. */
public final class ModelLodFrameCheck {
    private ModelLodFrameCheck() {}

    public static void verify(ByteBuffer pixels, int frameWidth, ModelLodLayout layout) {
        for (int panel = 0; panel < 2; panel++) {
            int count = 0;
            long sumX = 0, sumY = 0;
            for (int y = layout.y; y < layout.y + layout.viewHeight; y++) {
                for (int x = layout.x[panel]; x < layout.x[panel] + layout.viewWidth; x++) {
                    int offset = (y * frameWidth + x) * 4;
                    int red = pixels.get(offset) & 255, green = pixels.get(offset + 1) & 255, blue = pixels.get(offset + 2) & 255;
                    if (green > 50 && blue > 50 && green > red * 1.35f && blue > red * 1.35f) {
                        count++; sumX += x; sumY += y;
                    }
                }
            }
            if (count < 8) throw new FdxException("LOD panel " + panel + " did not render its model");
            float centerX = sumX / (float)count, centerY = sumY / (float)count;
            if (Math.abs(centerX - layout.x[panel] - layout.viewWidth * .5f) > Math.max(4,layout.viewWidth * .12f)
                    || Math.abs(centerY - layout.y - layout.viewHeight * .5f) > Math.max(4,layout.viewHeight * .12f)) {
                throw new FdxException("LOD panel " + panel + " model is offset from its viewport: " + centerX + ", " + centerY);
            }
        }
    }
}
