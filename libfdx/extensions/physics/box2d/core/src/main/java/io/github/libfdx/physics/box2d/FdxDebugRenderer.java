package io.github.libfdx.physics.box2d;

import com.github.xpenatan.box2d.B2DebugDrawEm;
import com.github.xpenatan.box2d.B2DebugPolygon;
import com.github.xpenatan.box2d.B2Rot;
import com.github.xpenatan.box2d.B2Transform;
import com.github.xpenatan.box2d.B2Vec2;
import com.github.xpenatan.box2d.B2World;
import io.github.libfdx.collections.Array;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.ImmediateModeRenderer;
import io.github.libfdx.graphics.camera.Camera;

/** Collects Box2D debug-draw callbacks and renders them through libfdx. */
public class FdxDebugRenderer extends B2DebugDrawEm {
    private static final int CIRCLE_SEGMENTS = 24;
    private static final int MAX_DIAGNOSTIC_CHARS = 4096;

    private final ImmediateModeRenderer lines;
    private final float[] viewProjection = new float[16];
    private final Array<PointCommand> points = new Array<PointCommand>();
    private final StringBuilder diagnostics = new StringBuilder();
    private int pointCount;
    private boolean graphicsDisposed;

    public FdxDebugRenderer(GraphicsContext graphics) {
        lines = new ImmediateModeRenderer(graphics);
        SetDrawShapes(true);
        SetDrawJoints(true);
    }

    public void beginFrame() {
        lines.clear3D();
        pointCount = 0;
        diagnostics.setLength(0);
    }

    public void render(Camera camera) {
        float worldPerPixel = camera.zoom();
        for(int i = 0; i < pointCount; i++) {
            PointCommand point = points.get(i);
            float radius = Math.max(1.5f, point.size * 0.5f) * worldPerPixel;
            line(point.x - radius, point.y, point.x + radius, point.y, point.color, 1.0f);
            line(point.x, point.y - radius, point.x, point.y + radius, point.color, 1.0f);
        }
        camera.combined().copyValues(viewProjection, 0);
        lines.render3D(viewProjection);
        lines.clear3D();
    }

    /** Collects and renders one world's debug geometry in a single call. */
    public void render(B2World world, Camera camera) {
        if(world == null) throw new IllegalArgumentException("world cannot be null");
        beginFrame();
        DrawWorld(world);
        render(camera);
    }

    public String diagnostics() {
        return diagnostics.toString();
    }

    @Override
    protected void DrawPolygon(B2DebugPolygon polygon, int hexColor) {
        int count = polygon.GetVertexCount();
        if(count < 2) return;
        B2Vec2 first = polygon.GetVertex(0);
        float firstX = first.GetX();
        float firstY = first.GetY();
        float previousX = firstX;
        float previousY = firstY;
        for(int i = 1; i < count; i++) {
            B2Vec2 vertex = polygon.GetVertex(i);
            float x = vertex.GetX();
            float y = vertex.GetY();
            line(previousX, previousY, x, y, hexColor, 1.0f);
            previousX = x;
            previousY = y;
        }
        line(previousX, previousY, firstX, firstY, hexColor, 1.0f);
    }

    @Override
    protected void DrawSolidPolygon(B2Transform transform, B2DebugPolygon polygon, float radius, int hexColor) {
        int count = polygon.GetVertexCount();
        if(count < 2) return;
        B2Vec2 position = transform.GetPosition();
        B2Rot rotation = transform.GetRotation();
        float px = position.GetX();
        float py = position.GetY();
        float cosine = rotation.GetCosine();
        float sine = rotation.GetSine();
        B2Vec2 first = polygon.GetVertex(0);
        float firstX = px + cosine * first.GetX() - sine * first.GetY();
        float firstY = py + sine * first.GetX() + cosine * first.GetY();
        float previousX = firstX;
        float previousY = firstY;
        for(int i = 1; i < count; i++) {
            B2Vec2 vertex = polygon.GetVertex(i);
            float x = px + cosine * vertex.GetX() - sine * vertex.GetY();
            float y = py + sine * vertex.GetX() + cosine * vertex.GetY();
            line(previousX, previousY, x, y, hexColor, 1.0f);
            previousX = x;
            previousY = y;
        }
        line(previousX, previousY, firstX, firstY, hexColor, 1.0f);
    }

    @Override
    protected void DrawCircle(B2Vec2 center, float radius, int hexColor) {
        drawCircle(center.GetX(), center.GetY(), radius, hexColor);
    }

    @Override
    protected void DrawSolidCircle(B2Transform transform, float radius, int hexColor) {
        B2Vec2 position = transform.GetPosition();
        B2Rot rotation = transform.GetRotation();
        float x = position.GetX();
        float y = position.GetY();
        drawCircle(x, y, radius, hexColor);
        line(x, y, x + radius * rotation.GetCosine(), y + radius * rotation.GetSine(), hexColor, 1.0f);
    }

    @Override
    protected void DrawSolidCapsule(B2Vec2 p1, B2Vec2 p2, float radius, int hexColor) {
        float x1 = p1.GetX();
        float y1 = p1.GetY();
        float x2 = p2.GetX();
        float y2 = p2.GetY();
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if(length < 0.0001f) {
            drawCircle(x1, y1, radius, hexColor);
            return;
        }
        float nx = -dy * radius / length;
        float ny = dx * radius / length;
        line(x1 + nx, y1 + ny, x2 + nx, y2 + ny, hexColor, 1.0f);
        line(x1 - nx, y1 - ny, x2 - nx, y2 - ny, hexColor, 1.0f);
        drawCircle(x1, y1, radius, hexColor);
        drawCircle(x2, y2, radius, hexColor);
    }

    @Override
    protected void DrawSegment(B2Vec2 p1, B2Vec2 p2, int hexColor) {
        line(p1.GetX(), p1.GetY(), p2.GetX(), p2.GetY(), hexColor, 1.0f);
    }

    @Override
    protected void DrawTransform(B2Transform transform) {
        B2Vec2 position = transform.GetPosition();
        B2Rot rotation = transform.GetRotation();
        float x = position.GetX();
        float y = position.GetY();
        float cosine = rotation.GetCosine();
        float sine = rotation.GetSine();
        line(x, y, x + 0.4f * cosine, y + 0.4f * sine, 0xFF3030, 1.0f);
        line(x, y, x - 0.4f * sine, y + 0.4f * cosine, 0x30FF30, 1.0f);
    }

    @Override
    protected void DrawPoint(B2Vec2 point, float size, int hexColor) {
        addPoint(point.GetX(), point.GetY(), size, hexColor);
    }

    public void segment(float x1, float y1, float x2, float y2, int hexColor) {
        line(x1, y1, x2, y2, hexColor, 1.0f);
    }

    public void point(float x, float y, float size, int hexColor) {
        addPoint(x, y, size, hexColor);
    }

    public void circle(float x, float y, float radius, int hexColor) {
        drawCircle(x, y, radius, hexColor);
    }

    public void worldText(float x, float y, String text, int hexColor) {
        appendDiagnostic(text);
    }

    public void screenText(float x, float y, String text, int hexColor) {
        appendDiagnostic(text);
    }

    public void disposeGraphics() {
        if(graphicsDisposed) return;
        graphicsDisposed = true;
        lines.dispose();
    }

    @Override
    protected void onNativeDispose() {
        disposeGraphics();
    }

    private void drawCircle(float x, float y, float radius, int hexColor) {
        if(radius <= 0.0f) return;
        float previousX = x + radius;
        float previousY = y;
        for(int i = 1; i <= CIRCLE_SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / CIRCLE_SEGMENTS;
            float nextX = x + (float)Math.cos(angle) * radius;
            float nextY = y + (float)Math.sin(angle) * radius;
            line(previousX, previousY, nextX, nextY, hexColor, 1.0f);
            previousX = nextX;
            previousY = nextY;
        }
    }

    private void addPoint(float x, float y, float size, int hexColor) {
        if(pointCount == points.size()) points.add(new PointCommand());
        PointCommand point = points.get(pointCount++);
        point.x = x;
        point.y = y;
        point.size = size;
        point.color = hexColor;
    }

    private void appendDiagnostic(String text) {
        if(text == null || text.length() == 0 || diagnostics.length() >= MAX_DIAGNOSTIC_CHARS) return;
        if(diagnostics.length() > 0) diagnostics.append('\n');
        int remaining = MAX_DIAGNOSTIC_CHARS - diagnostics.length();
        diagnostics.append(text, 0, Math.min(text.length(), remaining));
    }

    private void line(float x1, float y1, float x2, float y2, int hexColor, float alpha) {
        lines.line3D(x1, y1, 0.0f, x2, y2, 0.0f,
                ((hexColor >>> 16) & 0xFF) / 255.0f,
                ((hexColor >>> 8) & 0xFF) / 255.0f,
                (hexColor & 0xFF) / 255.0f,
                Math.max(0.0f, Math.min(1.0f, alpha)));
    }

    private static final class PointCommand {
        float x;
        float y;
        float size;
        int color;
    }
}
