package io.github.libfdx.ui;

import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.graphics.g2d.TextureRegion;
import java.nio.ByteBuffer;

/**
 * Represents an ui nine patch.
 *
 * @author xpenatan
 */
public final class UiNinePatch {
    private final TextureRegion region;
    private final String assetPath;
    private final UiInsets splits;
    private final UiInsets padding;
    private final UiSize minimumSize;
    private final boolean markerBorder;

    private UiNinePatch(TextureRegion region, String assetPath, UiInsets splits, UiInsets padding,
            UiSize minimumSize, boolean markerBorder) {
        this.region = region;
        this.assetPath = assetPath;
        this.splits = splits != null ? splits : UiInsets.ZERO;
        this.padding = padding != null ? padding : UiInsets.ZERO;
        this.minimumSize = minimumSize != null ? minimumSize : new UiSize(0.0f, 0.0f);
        this.markerBorder = markerBorder;
    }

    /**
     * Creates an UI nine patch.
     *
     * @param region the region
     * @param splits the splits
     * @param padding the padding
     * @return a new UI nine patch
     */
    public static UiNinePatch region(TextureRegion region, UiInsets splits, UiInsets padding) {
        return new UiNinePatch(region, null, splits, padding, null, false);
    }

    /**
     * Creates an UI nine patch.
     *
     * @param assetPath the asset path
     * @return a new UI nine patch
     */
    public static UiNinePatch asset(String assetPath) {
        return new UiNinePatch(null, assetPath, UiInsets.ZERO, UiInsets.ZERO, null, true);
    }

    /**
     * Creates an UI nine patch.
     *
     * @param region the region
     * @param image the image
     * @return a new UI nine patch
     */
    public static UiNinePatch marker(TextureRegion region, ImageData image) {
        if (region == null || image == null || image.width() < 3 || image.height() < 3) {
            return new UiNinePatch(region, null, UiInsets.ZERO, UiInsets.ZERO, null, true);
        }
        TextureRegion content = new TextureRegion(region.texture(), region.x() + 1, region.y() + 1,
                Math.max(1, region.width() - 2), Math.max(1, region.height() - 2));
        UiInsets splits = markerSplits(image);
        UiInsets padding = markerPadding(image);
        return new UiNinePatch(content, null, splits, padding, null, true);
    }

    /**
     * Sets the minimum size and returns this UI nine patch.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return this UI nine patch for chaining
     */
    public UiNinePatch minimumSize(float width, float height) {
        return new UiNinePatch(region, assetPath, splits, padding, new UiSize(width, height), markerBorder);
    }

    /**
     * Returns the region.
     *
     * @return the region
     */
    public TextureRegion region() {
        return region;
    }

    /**
     * Returns the asset path.
     *
     * @return the asset path
     */
    public String assetPath() {
        return assetPath;
    }

    /**
     * Returns the splits.
     *
     * @return the splits
     */
    public UiInsets splits() {
        return splits;
    }

    /**
     * Returns the padding.
     *
     * @return the padding
     */
    public UiInsets padding() {
        return padding;
    }

    /**
     * Returns the minimum size.
     *
     * @return the minimum size
     */
    public UiSize minimumSize() {
        return minimumSize;
    }

    /**
     * Returns the marker border.
     *
     * @return true if marker border succeeds or is active; false otherwise
     */
    public boolean markerBorder() {
        return markerBorder;
    }

    private static UiInsets markerSplits(ImageData image) {
        int left = firstBlack(image, 1, 0, image.width() - 2, true);
        int right = lastBlack(image, 1, 0, image.width() - 2, true);
        int top = firstBlack(image, 0, 1, image.height() - 2, false);
        int bottom = lastBlack(image, 0, 1, image.height() - 2, false);
        if (left < 0 || right < 0 || top < 0 || bottom < 0) {
            return UiInsets.ZERO;
        }
        int contentWidth = image.width() - 2;
        int contentHeight = image.height() - 2;
        return UiInsets.of(left, top, Math.max(0, contentWidth - right - 1), Math.max(0, contentHeight - bottom - 1));
    }

    private static UiInsets markerPadding(ImageData image) {
        int left = firstBlack(image, 1, image.height() - 1, image.width() - 2, true);
        int right = lastBlack(image, 1, image.height() - 1, image.width() - 2, true);
        int top = firstBlack(image, image.width() - 1, 1, image.height() - 2, false);
        int bottom = lastBlack(image, image.width() - 1, 1, image.height() - 2, false);
        if (left < 0 || right < 0 || top < 0 || bottom < 0) {
            return UiInsets.ZERO;
        }
        int contentWidth = image.width() - 2;
        int contentHeight = image.height() - 2;
        return UiInsets.of(left, top, Math.max(0, contentWidth - right - 1), Math.max(0, contentHeight - bottom - 1));
    }

    private static int firstBlack(ImageData image, int fixedOrStart, int fixed, int count, boolean horizontal) {
        for (int i = 0; i < count; i++) {
            int x = horizontal ? fixedOrStart + i : fixedOrStart;
            int y = horizontal ? fixed : fixed + i;
            if (isBlack(image, x, y)) {
                return i;
            }
        }
        return -1;
    }

    private static int lastBlack(ImageData image, int fixedOrStart, int fixed, int count, boolean horizontal) {
        for (int i = count - 1; i >= 0; i--) {
            int x = horizontal ? fixedOrStart + i : fixedOrStart;
            int y = horizontal ? fixed : fixed + i;
            if (isBlack(image, x, y)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isBlack(ImageData image, int x, int y) {
        ByteBuffer rgba = image.rgba();
        int index = (y * image.width() + x) * 4;
        int red = rgba.get(index) & 0xff;
        int green = rgba.get(index + 1) & 0xff;
        int blue = rgba.get(index + 2) & 0xff;
        int alpha = rgba.get(index + 3) & 0xff;
        return red < 8 && green < 8 && blue < 8 && alpha > 247;
    }
}
