package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorData;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphWireRoute;
import io.github.libfdx.ui.UiDrawContext;
import io.github.libfdx.ui.UiPath;
import io.github.libfdx.ui.UiRect;

/** One visible wire. Drawing and picking share canvas-space endpoints, waypoints and curve controls. */
final class ShaderGraphCanvasWire {
    static final float PICK_RADIUS = 7;
    final ShaderGraphWireRoute.Wire key;
    final ShaderGraphWireRoute route;
    final MaterialOutputChannel channel;
    final String label;
    final float[] x, y;
    private final float zoom;
    private static final float[] SOCKET_TANGENTS = {1, 0, 1, 0};
    private final float[] tangents;
    private float c1x, c1y, c2x, c2y;

    ShaderGraphCanvasWire(ShaderGraphWireRoute.Wire key, MaterialOutputChannel channel, String label,
            ShaderGraphEditorData layout, float startX, float startY, float endX, float endY) {
        this.key = key;
        this.channel = channel;
        this.label = label;
        this.route = layout.routes().getOrDefault(key, ShaderGraphWireRoute.DEFAULT);
        zoom = layout.zoom();
        int end = route.pins().size() + 1;
        x = new float[end + 1];
        y = new float[end + 1];
        tangents = end == 1 ? SOCKET_TANGENTS : new float[2 * (end + 1)];
        if (end > 1) {
            tangents[0] = 1;
            tangents[2 * end] = 1;
        }
        x[0] = startX;
        y[0] = startY;
        for (int i = 1; i < end; i++) {
            var pin = route.pins().get(i - 1);
            double radians = Math.toRadians(pin.rotationDegrees());
            tangents[2 * i] = (float) Math.cos(radians);
            tangents[2 * i + 1] = (float) Math.sin(radians);
            x[i] = layout.panX() + pin.x() * zoom;
            y[i] = layout.panY() + pin.y() * zoom;
        }
        x[end] = endX;
        y[end] = endY;
    }

    static float control(float dx, float zoom) {
        return Math.max(36 * zoom, Math.abs(dx) * 0.45f);
    }

    // Pin positions can change during a drag. Draw and hit test the same live control polygon.
    private void setCurveControls(int i) {
        int end = i + 1;
        float startX = tangents[2 * i], startY = tangents[2 * i + 1];
        float endX = tangents[2 * end], endY = tangents[2 * end + 1];
        float dx = x[end] - x[i], dy = y[end] - y[i];
        float startLength = control(dx * startX + dy * startY, zoom);
        float endLength = control(dx * endX + dy * endY, zoom);
        c1x = x[i] + startX * startLength;
        c1y = y[i] + startY * startLength;
        c2x = x[end] - endX * endLength;
        c2y = y[end] - endY * endLength;
    }

    void draw(UiDrawContext draw, UiPath path, UiRect bounds, boolean selected, boolean dimmed, int selectedPin) {
        float ox = bounds.x(), oy = bounds.y();
        path.clear().moveTo(ox + x[0], oy + y[0]);
        for (int i = 0; i < x.length - 1; i++) {
            float ex = ox + x[i + 1], ey = oy + y[i + 1];
            if (route.straight(i)) path.lineTo(ex, ey);
            else {
                setCurveControls(i);
                path.cubicTo(ox + c1x, oy + c1y, ox + c2x, oy + c2y, ex, ey);
            }
        }
        draw.path(path, selected ? Math.max(2.8f, 3 * zoom) : Math.max(1, 1.75f * zoom),
                selected ? ShaderGraphEditorTheme.TEXT : dimmed ? ShaderGraphEditorTheme.EDGE : ShaderGraphEditorTheme.WIRE);
        for (int i = 1; i < x.length - 1; i++) {
            float px = ox + x[i], py = oy + y[i];
            boolean active = selected && i - 1 == selectedPin;
            float radius = active ? 7 : selected ? 5 : 3.5f;
            draw.rect(px - radius - 1, py - radius - 1, 2 * radius + 2, 2 * radius + 2, ShaderGraphEditorTheme.CANVAS);
            draw.rect(px - radius, py - radius, 2 * radius, 2 * radius,
                    selected ? ShaderGraphEditorTheme.TEXT : ShaderGraphEditorTheme.WIRE);
            if (active) {
                float dx = tangents[2 * i], dy = tangents[2 * i + 1];
                draw.line(px - dx * 4, py - dy * 4, px + dx * 4, py + dy * 4, 2, ShaderGraphEditorTheme.NODE);
                draw.line(px + dx * 4, py + dy * 4, px + dy * 3, py - dx * 3, 2, ShaderGraphEditorTheme.NODE);
                draw.line(px + dx * 4, py + dy * 4, px - dy * 3, py + dx * 3, 2, ShaderGraphEditorTheme.NODE);
            } else if (selected) draw.rect(px - 2, py - 2, 4, 4, ShaderGraphEditorTheme.NODE);
        }
    }

    float segmentDistanceSquared(int i, float px, float py) {
        float ax = x[i], ay = y[i], bx = x[i + 1], by = y[i + 1];
        if (route.straight(i)) return lineDistanceSquared(px, py, ax, ay, bx, by);
        setCurveControls(i);
        if (px < Math.min(Math.min(ax, bx), Math.min(c1x, c2x)) - PICK_RADIUS
                || px > Math.max(Math.max(ax, bx), Math.max(c1x, c2x)) + PICK_RADIUS
                || py < Math.min(Math.min(ay, by), Math.min(c1y, c2y)) - PICK_RADIUS
                || py > Math.max(Math.max(ay, by), Math.max(c1y, c2y)) + PICK_RADIUS) {
            return Float.POSITIVE_INFINITY;
        }
        float length = Math.abs(c1x - ax) + Math.abs(c1y - ay) + Math.abs(c2x - c1x)
                + Math.abs(c2y - c1y) + Math.abs(bx - c2x) + Math.abs(by - c2y);
        int steps = Math.clamp((int)(length / 10), 12, 128);
        float lastX = ax, lastY = ay, best = Float.POSITIVE_INFINITY;
        for (int j = 1; j <= steps; j++) {
            float t = (float)j / steps, s = 1 - t;
            float w0 = s * s * s, w1 = 3 * s * s * t, w2 = 3 * s * t * t, w3 = t * t * t;
            float nextX = w0 * ax + w1 * c1x + w2 * c2x + w3 * bx;
            float nextY = w0 * ay + w1 * c1y + w2 * c2y + w3 * by;
            best = Math.min(best, lineDistanceSquared(px, py, lastX, lastY, nextX, nextY));
            lastX = nextX;
            lastY = nextY;
        }
        return best;
    }

    private static float lineDistanceSquared(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay, length2 = dx * dx + dy * dy;
        float t = length2 == 0 ? 0 : Math.clamp(((px - ax) * dx + (py - ay) * dy) / length2, 0, 1);
        float ex = px - ax - t * dx, ey = py - ay - t * dy;
        return ex * ex + ey * ey;
    }

}
