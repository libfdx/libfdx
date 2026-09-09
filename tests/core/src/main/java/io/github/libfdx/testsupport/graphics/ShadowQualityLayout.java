package io.github.libfdx.testsupport.graphics;

/** Shared logical UI and framebuffer bounds for the two equal-sized comparison views. */
public final class ShadowQualityLayout {
    public static final int MARGIN = 12;
    public static final int HEADER = 144;
    public static final int FOOTER = 44;
    public static final int LABEL = 56;
    public boolean stacked;
    public int contentHeight, panelWidth, panelHeight, viewWidth, viewHeight;
    public final int[] x = new int[2];
    public final int[] y = new int[2];

    public void update(int width, int height, int framebufferWidth, int framebufferHeight) {
        stacked = width < 800 && height >= 640;
        contentHeight = Math.max(1, height - 4 * MARGIN - HEADER - FOOTER);
        panelWidth = Math.max(1, stacked ? width - 2 * MARGIN : (width - 3 * MARGIN) / 2);
        panelHeight = stacked ? (contentHeight - MARGIN) / 2 : contentHeight;
        float sx = framebufferWidth / (float)Math.max(1, width);
        float sy = framebufferHeight / (float)Math.max(1, height);
        viewWidth = Math.max(1, Math.min(framebufferWidth, Math.round(panelWidth * sx)));
        viewHeight = Math.max(1, Math.min(framebufferHeight, Math.round((panelHeight - LABEL) * sy)));
        for (int i = 0; i < 2; i++) {
            x[i] = Math.round((MARGIN + (stacked ? 0 : i * (panelWidth + MARGIN))) * sx);
            int top = 2 * MARGIN + HEADER + LABEL + (stacked ? i * (panelHeight + MARGIN) : 0);
            y[i] = framebufferHeight - Math.round(top * sy) - viewHeight;
            x[i] = Math.max(0, Math.min(framebufferWidth - viewWidth, x[i]));
            y[i] = Math.max(0, Math.min(framebufferHeight - viewHeight, y[i]));
        }
    }
}
