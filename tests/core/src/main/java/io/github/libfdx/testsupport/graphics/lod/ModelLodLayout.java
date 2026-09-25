package io.github.libfdx.testsupport.graphics.lod;

/** Logical controls and framebuffer rectangles for the two LOD comparison views. */
public final class ModelLodLayout {
    public static final int MARGIN = 16, GAP = 10, HEADER = 172, FOOTER = 112, LABEL = 52;
    public int contentHeight, panelWidth, viewWidth, viewHeight;
    public final int[] x = new int[2];
    public int y;

    public void update(int width, int height, int pixelWidth, int pixelHeight) {
        contentHeight = Math.max(LABEL + 1, height - 2 * MARGIN - 2 * GAP - HEADER - FOOTER);
        panelWidth = Math.max(1, (width - 2 * MARGIN - GAP) / 2);
        float sx = pixelWidth / (float)Math.max(1, width), sy = pixelHeight / (float)Math.max(1, height);
        viewWidth = Math.max(1, Math.min(pixelWidth, Math.round(panelWidth * sx)));
        viewHeight = Math.max(1, Math.min(pixelHeight, Math.round((contentHeight - LABEL) * sy)));
        y = Math.max(0, pixelHeight - Math.round((MARGIN + HEADER + GAP + LABEL) * sy) - viewHeight);
        for (int i = 0; i < 2; i++) x[i] = Math.min(pixelWidth - viewWidth,
                Math.round((MARGIN + i * (panelWidth + GAP)) * sx));
    }
}
