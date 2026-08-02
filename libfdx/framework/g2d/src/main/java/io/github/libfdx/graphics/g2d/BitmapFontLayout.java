package io.github.libfdx.graphics.g2d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.collections.FloatArray;

/**
 * Represents a bitmap font layout.
 *
 * @author xpenatan
 */
public final class BitmapFontLayout {
    private final Array<String> lines;
    private final ArrayView<String> lineView;
    private final FloatArray lineWidths;
    private final float width;
    private final float height;
    private final float lineHeight;

    BitmapFontLayout(ArrayView<String> lines, FloatArray lineWidths, float width, float height, float lineHeight) {
        this.lines = new Array<String>(lines);
        this.lineView = this.lines.view();
        this.lineWidths = new FloatArray(lineWidths.size());
        for (int i = 0; i < lineWidths.size(); i++) {
            this.lineWidths.add(lineWidths.get(i));
        }
        this.width = width;
        this.height = height;
        this.lineHeight = lineHeight;
    }

    /**
     * Returns the lines.
     *
     * @return the lines
     */
    public ArrayView<String> lines() {
        return lineView;
    }

    /**
     * Runs the line width step.
     *
     * @param index the index
     * @return the line width
     */
    public float lineWidth(int index) {
        return lineWidths.get(index);
    }

    /**
     * Returns the width.
     *
     * @return the width
     */
    public float width() {
        return width;
    }

    /**
     * Returns the height.
     *
     * @return the height
     */
    public float height() {
        return height;
    }

    /**
     * Returns the line height.
     *
     * @return the line height
     */
    public float lineHeight() {
        return lineHeight;
    }
}
