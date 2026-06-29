package io.github.libfdx.graphics.g2d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a bitmap font layout.
 *
 * @author xpenatan
 */
public final class BitmapFontLayout {
    private final List<String> lines;
    private final List<Float> lineWidths;
    private final float width;
    private final float height;
    private final float lineHeight;

    BitmapFontLayout(List<String> lines, List<Float> lineWidths, float width, float height, float lineHeight) {
        this.lines = Collections.unmodifiableList(new ArrayList<String>(lines));
        this.lineWidths = Collections.unmodifiableList(new ArrayList<Float>(lineWidths));
        this.width = width;
        this.height = height;
        this.lineHeight = lineHeight;
    }

    /**
     * Returns the lines.
     *
     * @return the lines
     */
    public List<String> lines() {
        return lines;
    }

    /**
     * Runs the line width step.
     *
     * @param index the index
     * @return the line width
     */
    public float lineWidth(int index) {
        return lineWidths.get(index).floatValue();
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
