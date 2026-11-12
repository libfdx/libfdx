package io.github.libfdx.ui;

import io.github.libfdx.graphics.g2d.TextureRegion;

/** Reusable source and destination boundaries for the nine drawable cells. */
final class UiNinePatchLayout {
    final int[] sourceX = new int[4];
    final int[] sourceY = new int[4];
    final float[] targetX = new float[4];
    final float[] targetY = new float[4];

    void set(UiNinePatch patch, UiRect bounds) {
        TextureRegion region = patch.region();
        UiInsets splits = patch.splits();
        axis(region.x(), region.width(), splits.left(), splits.right(), patch.borderScale(),
                bounds.x(), bounds.width(), sourceX, targetX);
        axis(region.y(), region.height(), splits.top(), splits.bottom(), patch.borderScale(),
                bounds.y(), bounds.height(), sourceY, targetY);
    }

    private void axis(int origin, int length, float first, float last, float scale,
            float targetOrigin, float targetLength, int[] source, float[] target) {
        first = Math.max(0.0f, Math.min(length, first));
        last = Math.max(0.0f, Math.min(length, last));
        float sourceScale = first + last > length ? length / (first + last) : 1.0f;
        int leading = (int)(first * sourceScale);
        int trailing = (int)(last * sourceScale);
        source[0] = origin;
        source[1] = origin + leading;
        source[2] = origin + length - trailing;
        source[3] = origin + length;
        float borderLength = (leading + trailing) * scale;
        float targetScale = borderLength > targetLength ? targetLength / borderLength : 1.0f;
        target[0] = targetOrigin;
        target[1] = targetOrigin + leading * scale * targetScale;
        target[2] = targetOrigin + targetLength - trailing * scale * targetScale;
        target[3] = targetOrigin + targetLength;
    }

}
