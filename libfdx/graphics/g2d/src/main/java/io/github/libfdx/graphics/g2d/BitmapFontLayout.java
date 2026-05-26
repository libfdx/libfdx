package io.github.libfdx.graphics.g2d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public List<String> lines() {
        return lines;
    }

    public float lineWidth(int index) {
        return lineWidths.get(index).floatValue();
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float lineHeight() {
        return lineHeight;
    }
}
