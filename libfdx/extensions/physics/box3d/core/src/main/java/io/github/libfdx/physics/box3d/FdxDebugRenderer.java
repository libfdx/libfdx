package io.github.libfdx.physics.box3d;

import com.github.xpenatan.box3d.B3;
import com.github.xpenatan.box3d.B3AABB;
import com.github.xpenatan.box3d.B3Body;
import com.github.xpenatan.box3d.B3Capsule;
import com.github.xpenatan.box3d.B3DebugDrawEm;
import com.github.xpenatan.box3d.B3DebugShape;
import com.github.xpenatan.box3d.B3Quat;
import com.github.xpenatan.box3d.B3Shape;
import com.github.xpenatan.box3d.B3Sphere;
import com.github.xpenatan.box3d.B3Transform;
import com.github.xpenatan.box3d.B3Vec3;
import com.github.xpenatan.box3d.B3World;
import com.github.xpenatan.jParser.api.NativeObject;
import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.FloatArray;
import io.github.libfdx.collections.LongMap;
import io.github.libfdx.collections.ObjectIterator;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.ImmediateModeRenderer;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.DirectionalShadowMap3D;
import io.github.libfdx.graphics.g3d.Environment3D;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.ModelBuilder;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.MaterialAttributes;
import io.github.libfdx.graphics.g3d.PbrAttributes;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;

public class FdxDebugRenderer extends B3DebugDrawEm {
    public static final float DEFAULT_SHADOW_BIAS = 0.001f;

    private static final int CIRCLE_SEGMENTS = 24;
    private static final int SPHERE_SLICES = 24;
    private static final int SPHERE_STACKS = 12;
    private static final float TRANSFORM_AXIS_LENGTH = 0.35f;
    private static final int RETIRED_MODEL_FRAME_DELAY = 3;

    private final GraphicsContext graphics;
    private final ModelBatch modelBatch;
    private final ImmediateModeRenderer lineRenderer;
    private final Environment3D environment;
    private final DirectionalLight directionalLight;
    private final DirectionalShadowMap3D shadowMap;
    private final boolean ownsModelBatch;
    private final boolean ownsLineRenderer;
    private final LongMap<DebugModel> modelCache = new LongMap<DebugModel>(128, 0.7f);
    private final Array<RetiredDebugModel> retiredModels = new Array<RetiredDebugModel>();
    private final Array<DefaultModelInstance> visibleInstances = new Array<DefaultModelInstance>();
    private final Array<DefaultModelInstance> shadowCasterInstances = new Array<DefaultModelInstance>();
    private final Matrix4 worldTransform = new Matrix4();
    private final Matrix4 combinedTransform = new Matrix4();
    private final float[] solidRgba = new float[] { 0.58f, 0.60f, 0.62f, 1.0f };
    private final float[] transformedPoints = new float[24];
    private final float[] viewProjectionValues = new float[Matrix4.VALUE_COUNT];
    private boolean enabled = true;
    private boolean drawSolidShapes = true;
    private boolean drawWireframe = true;
    private boolean shadowsEnabled = true;
    private boolean collectSolidShapes;
    private float shadowBias = DEFAULT_SHADOW_BIAS;

    public FdxDebugRenderer(GraphicsContext graphics) {
        this(graphics, new ModelBatch(requireGraphics(graphics)), new ImmediateModeRenderer(requireGraphics(graphics)),
                true, true);
    }

    public FdxDebugRenderer(GraphicsContext graphics, ModelBatch modelBatch) {
        this(graphics, modelBatch, new ImmediateModeRenderer(requireGraphics(graphics)), false, true);
    }

    public FdxDebugRenderer(GraphicsContext graphics, ModelBatch modelBatch, ImmediateModeRenderer lineRenderer) {
        this(graphics, modelBatch, lineRenderer, false, false);
    }

    public FdxDebugRenderer(ImmediateModeRenderer lineRenderer) {
        this(null, null, lineRenderer, false, false);
        drawSolidShapes = false;
    }

    private FdxDebugRenderer(GraphicsContext graphics, ModelBatch modelBatch, ImmediateModeRenderer lineRenderer,
            boolean ownsModelBatch, boolean ownsLineRenderer) {
        if(lineRenderer == null) {
            throw new FdxException("ImmediateModeRenderer cannot be null");
        }
        if(modelBatch == null && graphics != null) {
            throw new FdxException("ModelBatch cannot be null");
        }
        this.graphics = graphics;
        this.modelBatch = modelBatch;
        this.lineRenderer = lineRenderer;
        this.ownsModelBatch = ownsModelBatch;
        this.ownsLineRenderer = ownsLineRenderer;
        directionalLight = new DirectionalLight().direction(-0.45f, -0.55f, -0.70f).intensity(1.75f);
        shadowMap = graphics != null
                ? new DirectionalShadowMap3D(graphics, 2048, 2048).bounds(0.0f, 5.0f, 0.0f, 90.0f, 1.0f, 260.0f)
                        .bias(shadowBias).strength(0.82f)
                : null;
        this.environment = new Environment3D()
                .ambientColor(new Color(0.18f, 0.19f, 0.21f, 1.0f))
                .add(directionalLight);
        if(shadowMap != null) {
            environment.directionalShadowMap(shadowMap);
        }
        if(modelBatch != null) {
            modelBatch.environment(environment);
        }
    }

    public void render(B3World world, Camera camera) {
        if(world == null) {
            throw new FdxException("B3World cannot be null");
        }
        if(camera == null) {
            throw new FdxException("Camera cannot be null");
        }
        disposeExpiredRetiredModels();
        clearFrame();
        if(!enabled) {
            return;
        }

        collectSolidShapes = drawSolidShapes && graphics != null && modelBatch != null;
        DrawWorld(world, B3.DefaultMaskBits());
        collectSolidShapes = false;

        if(drawSolidShapes && modelBatch != null && !visibleInstances.isEmpty()) {
            renderShadows(camera);
            modelBatch.begin(camera);
            for(int i = 0; i < visibleInstances.size(); i++) {
                modelBatch.render(visibleInstances.get(i));
            }
            modelBatch.end();
        }

        renderLines(camera.combined());
    }

    public void render(B3World world, Matrix4 viewProjection) {
        if(world == null) {
            throw new FdxException("B3World cannot be null");
        }
        if(viewProjection == null) {
            throw new FdxException("View-projection matrix cannot be null");
        }
        disposeExpiredRetiredModels();
        clearFrame();
        if(!enabled) {
            return;
        }
        collectSolidShapes = false;
        DrawWorld(world, B3.DefaultMaskBits());
        renderLines(viewProjection);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDrawSolidShapes() {
        return drawSolidShapes;
    }

    public void setDrawSolidShapes(boolean drawSolidShapes) {
        if(drawSolidShapes && (graphics == null || modelBatch == null)) {
            throw new FdxException("Solid debug shapes require GraphicsContext and ModelBatch");
        }
        this.drawSolidShapes = drawSolidShapes;
    }

    public boolean isDrawWireframe() {
        return drawWireframe;
    }

    public void setDrawWireframe(boolean drawWireframe) {
        this.drawWireframe = drawWireframe;
    }

    public boolean isShadowsEnabled() {
        return shadowsEnabled;
    }

    public void setShadowsEnabled(boolean shadowsEnabled) {
        this.shadowsEnabled = shadowsEnabled;
        if(shadowMap != null) {
            if(shadowsEnabled) {
                environment.directionalShadowMap(shadowMap);
            }
            else {
                environment.clearDirectionalShadowMap();
            }
        }
    }

    public float getShadowBias() {
        return shadowBias;
    }

    public void setShadowBias(float shadowBias) {
        this.shadowBias = Math.max(0.0f, shadowBias);
        if(shadowMap != null) {
            shadowMap.bias(this.shadowBias);
        }
    }

    public void setSolidColor(float red, float green, float blue, float alpha) {
        solidRgba[0] = clamp(red);
        solidRgba[1] = clamp(green);
        solidRgba[2] = clamp(blue);
        solidRgba[3] = clamp(alpha);
        clearShapeCache();
    }

    public void setDrawAllModes(boolean enabled) {
        SetDrawShapes(enabled);
        SetDrawJoints(enabled);
        SetDrawJointExtras(enabled);
        SetDrawBounds(enabled);
        SetDrawMass(enabled);
        SetDrawBodyNames(enabled);
        SetDrawContacts(enabled);
        SetDrawAnchorA(enabled);
        SetDrawGraphColors(enabled);
        SetDrawContactFeatures(enabled);
        SetDrawContactNormals(enabled);
        SetDrawContactForces(enabled);
        SetDrawFrictionForces(enabled);
        SetDrawIslands(enabled);
    }

    public void clearShapeCache() {
        if(modelCache.isEmpty()) {
            return;
        }
        ObjectIterator<DebugModel> iterator = modelCache.values().iterator();
        while(iterator.hasNext()) {
            retiredModels.add(new RetiredDebugModel(iterator.next(), RETIRED_MODEL_FRAME_DELAY));
        }
        modelCache.clear();
    }

    public void clear() {
        clearFrame();
        clearShapeCache();
    }

    @Override
    protected boolean DrawShape(B3DebugShape shape, B3Transform transform, int color) {
        if(!enabled || shape == null || transform == null) {
            return true;
        }

        if(collectSolidShapes) {
            long key = shape.GetShapeId();
            DebugModel model = modelCache.get(key);
            if(model == null) {
                model = buildModel(shape);
                modelCache.put(key, model);
            }
            appendSolidInstances(model, transform);
        }

        if(drawWireframe) {
            drawShapeWire(shape, transform, color);
        }
        return true;
    }

    @Override
    protected void DrawSegment(B3Vec3 p1, B3Vec3 p2, int color) {
        if(enabled) {
            line(p1, p2, color, 1.0f);
        }
    }

    @Override
    protected void DrawTransform(B3Transform transform) {
        if(!enabled || transform == null) {
            return;
        }
        transformPoint(transform, 0.0f, 0.0f, 0.0f, transformedPoints, 0);
        transformPoint(transform, TRANSFORM_AXIS_LENGTH, 0.0f, 0.0f, transformedPoints, 3);
        line(transformedPoints, 0, 3, 0xFF0000L, 1.0f);
        transformPoint(transform, 0.0f, TRANSFORM_AXIS_LENGTH, 0.0f, transformedPoints, 3);
        line(transformedPoints, 0, 3, 0x00FF00L, 1.0f);
        transformPoint(transform, 0.0f, 0.0f, TRANSFORM_AXIS_LENGTH, transformedPoints, 3);
        line(transformedPoints, 0, 3, 0x0000FFL, 1.0f);
    }

    @Override
    protected void DrawPoint(B3Vec3 p, float size, int color) {
        if(!enabled || p == null) {
            return;
        }
        float r = Math.max(0.02f, size * 0.01f);
        float x = p.GetX();
        float y = p.GetY();
        float z = p.GetZ();
        line(x - r, y, z, x + r, y, z, color, 1.0f);
        line(x, y - r, z, x, y + r, z, color, 1.0f);
        line(x, y, z - r, x, y, z + r, color, 1.0f);
    }

    @Override
    protected void DrawSphere(B3Vec3 p, float radius, int color, float alpha) {
        if(enabled && p != null) {
            drawWireSphere(p.GetX(), p.GetY(), p.GetZ(), radius, color, alpha);
        }
    }

    @Override
    protected void DrawCapsule(B3Vec3 p1, B3Vec3 p2, float radius, int color, float alpha) {
        if(enabled && p1 != null && p2 != null) {
            writePoint(p1, transformedPoints, 0);
            writePoint(p2, transformedPoints, 3);
            drawWireCapsule(transformedPoints, 0, 3, radius, color, alpha);
        }
    }

    @Override
    protected void DrawBounds(B3AABB aabb, int color) {
        if(!enabled || aabb == null) {
            return;
        }
        drawAABB(aabb.GetLowerBound(), aabb.GetUpperBound(), color);
    }

    @Override
    protected void DrawBox(B3Vec3 extents, B3Transform transform, int color) {
        if(!enabled || extents == null || transform == null) {
            return;
        }
        drawBox(extents.GetX(), extents.GetY(), extents.GetZ(), transform, color);
    }

    @Override
    protected void onNativeDispose() {
        clearFrame();
        disposeCachedModels();
        disposeRetiredModelsNow();
        if(ownsLineRenderer) {
            lineRenderer.dispose();
        }
        if(shadowMap != null) {
            shadowMap.dispose();
        }
        if(ownsModelBatch && modelBatch != null) {
            modelBatch.dispose();
        }
    }

    private void renderShadows(Camera camera) {
        if(!shadowsEnabled || shadowMap == null) {
            if(shadowMap != null) {
                environment.clearDirectionalShadowMap();
            }
            return;
        }
        float centerX = camera.position().x() + camera.direction().x() * 42.0f;
        float centerY = camera.position().y() + camera.direction().y() * 42.0f;
        float centerZ = camera.position().z() + camera.direction().z() * 42.0f;
        shadowMap.bounds(centerX, centerY, centerZ, 90.0f, 1.0f, 260.0f);
        environment.directionalShadowMap(shadowMap);
        shadowMap.render(directionalLight, shadowCasterInstances);
    }

    private DebugModel buildModel(B3DebugShape shape) {
        DebugModel debugModel = new DebugModel();
        ModelBuilder builder = new ModelBuilder(graphics).material(solidMaterial("box3d-debug-solid"));
        int triangleCount = shape.GetTriangleCount();
        if(triangleCount > 0) {
            float[] positions = new float[triangleCount * 9];
            float[] colors = new float[triangleCount * 12];
            int p = 0;
            int c = 0;
            for(int i = 0; i < triangleCount; i++) {
                p = appendVertex(positions, p, shape.GetTriangleVertex0(i));
                p = appendVertex(positions, p, shape.GetTriangleVertex1(i));
                p = appendVertex(positions, p, shape.GetTriangleVertex2(i));
                c = appendColor(colors, c);
                c = appendColor(colors, c);
                c = appendColor(colors, c);
            }
            debugModel.meshModel = builder.triangles("box3d-debug-solid-" + shape.GetShapeId(), positions, null, colors);
            debugModel.meshInstance = new DefaultModelInstance(debugModel.meshModel);
        }

        for(int i = 0; i < shape.GetSphereCount(); i++) {
            B3Sphere sphere = shape.GetSphereAt(i);
            if(sphere.GetRadius() <= 0.0f) {
                continue;
            }
            float[] positions = sphereTriangles(sphere.GetRadius(), SPHERE_SLICES, SPHERE_STACKS);
            float[] colors = colorsForVertices(positions.length / 3);
            Model model = builder.triangles("box3d-debug-sphere-" + shape.GetShapeId() + "-" + i,
                    positions, null, colors);
            PrimitiveInstance primitive = new PrimitiveInstance(model, new DefaultModelInstance(model));
            B3Vec3 center = sphere.GetCenter();
            primitive.localTransform.setToTranslation(center.GetX(), center.GetY(), center.GetZ());
            debugModel.primitives.add(primitive);
        }

        for(int i = 0; i < shape.GetCapsuleCount(); i++) {
            B3Capsule capsule = shape.GetCapsuleAt(i);
            PrimitiveInstance primitive = buildCapsulePrimitive(builder, shape.GetShapeId(), i, capsule);
            if(primitive != null) {
                debugModel.primitives.add(primitive);
            }
        }
        cacheShadowBody(debugModel, shape.GetShapeId());
        return debugModel;
    }

    private PrimitiveInstance buildCapsulePrimitive(ModelBuilder builder, long shapeId, int index, B3Capsule capsule) {
        B3Vec3 c1 = capsule.GetCenter1();
        B3Vec3 c2 = capsule.GetCenter2();
        float radius = capsule.GetRadius();
        float dx = c2.GetX() - c1.GetX();
        float dy = c2.GetY() - c1.GetY();
        float dz = c2.GetZ() - c1.GetZ();
        float segmentLength = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        if(radius <= 0.0f) {
            return null;
        }
        float[] positions = capsuleTriangles(radius, segmentLength, SPHERE_SLICES, Math.max(3, SPHERE_STACKS / 2));
        float[] colors = colorsForVertices(positions.length / 3);
        Model model = builder.triangles("box3d-debug-capsule-" + shapeId + "-" + index, positions, null, colors);
        PrimitiveInstance primitive = new PrimitiveInstance(model, new DefaultModelInstance(model));
        float midX = (c1.GetX() + c2.GetX()) * 0.5f;
        float midY = (c1.GetY() + c2.GetY()) * 0.5f;
        float midZ = (c1.GetZ() + c2.GetZ()) * 0.5f;
        float qx = 0.0f;
        float qy = 0.0f;
        float qz = 0.0f;
        float qw = 1.0f;
        if(segmentLength > 0.00001f) {
            float ax = dx / segmentLength;
            float ay = dy / segmentLength;
            float az = dz / segmentLength;
            float dot = ay;
            if(dot < -0.9999f) {
                qx = 1.0f;
                qw = 0.0f;
            }
            else if(dot < 0.9999f) {
                qx = az;
                qz = -ax;
                qw = 1.0f + dot;
                float invLen = 1.0f / (float)Math.sqrt(qx * qx + qz * qz + qw * qw);
                qx *= invLen;
                qz *= invLen;
                qw *= invLen;
            }
        }
        primitive.localTransform.setToTrs(midX, midY, midZ, qx, qy, qz, qw, 1.0f, 1.0f, 1.0f);
        return primitive;
    }

    private void appendSolidInstances(DebugModel model, B3Transform transform) {
        FdxBox3DConverter.toFdx(transform, worldTransform);
        boolean castsShadow = model.castsShadow();
        if(model.meshInstance != null) {
            model.meshInstance.transform(worldTransform);
            visibleInstances.add(model.meshInstance);
            if(castsShadow) {
                shadowCasterInstances.add(model.meshInstance);
            }
        }
        for(int i = 0; i < model.primitives.size(); i++) {
            PrimitiveInstance primitive = model.primitives.get(i);
            combinedTransform.setToMul(worldTransform, primitive.localTransform);
            primitive.instance.transform(combinedTransform);
            visibleInstances.add(primitive.instance);
            if(castsShadow) {
                shadowCasterInstances.add(primitive.instance);
            }
        }
    }

    private static void cacheShadowBody(DebugModel model, long shapeId) {
        B3Shape shape = new B3Shape(shapeId);
        try {
            if(shape.IsValid()) {
                model.shadowBody = new B3Body(shape.GetBodyId());
            }
        }
        finally {
            disposeNative(shape);
        }
    }

    private void drawShapeWire(B3DebugShape shape, B3Transform transform, int color) {
        for(int i = 0; i < shape.GetSphereCount(); i++) {
            B3Sphere sphere = shape.GetSphereAt(i);
            transformPoint(transform, sphere.GetCenter(), transformedPoints, 0);
            drawWireSphere(transformedPoints[0], transformedPoints[1], transformedPoints[2],
                    sphere.GetRadius(), color, 1.0f);
        }
        for(int i = 0; i < shape.GetCapsuleCount(); i++) {
            B3Capsule capsule = shape.GetCapsuleAt(i);
            transformPoint(transform, capsule.GetCenter1(), transformedPoints, 0);
            transformPoint(transform, capsule.GetCenter2(), transformedPoints, 3);
            drawWireCapsule(transformedPoints, 0, 3, capsule.GetRadius(), color, 1.0f);
        }
        int edgeCount = shape.GetHullEdgeCount();
        for(int i = 0; i < edgeCount; i++) {
            transformPoint(transform, shape.GetHullEdgeVertex0(i), transformedPoints, 0);
            transformPoint(transform, shape.GetHullEdgeVertex1(i), transformedPoints, 3);
            line(transformedPoints, 0, 3, color, 1.0f);
        }
    }

    private Material solidMaterial(String id) {
        return new Material(id)
                .set(MaterialAttributes.baseColor(solidRgba[0], solidRgba[1],
                        solidRgba[2], solidRgba[3]))
                .set(PbrAttributes.roughnessFactor(0.85f))
                .doubleSided(true);
    }

    private void renderLines(Matrix4 viewProjection) {
        viewProjection.copyValues(viewProjectionValues, 0);
        lineRenderer.render3D(viewProjectionValues);
        lineRenderer.clear3D();
    }

    private void clearFrame() {
        visibleInstances.clear();
        shadowCasterInstances.clear();
        lineRenderer.clear3D();
    }

    private void disposeExpiredRetiredModels() {
        for(int i = retiredModels.size() - 1; i >= 0; i--) {
            RetiredDebugModel retired = retiredModels.get(i);
            retired.remainingFrames--;
            if(retired.remainingFrames <= 0) {
                retired.model.dispose();
                retiredModels.removeIndex(i);
            }
        }
    }

    private void disposeCachedModels() {
        ObjectIterator<DebugModel> iterator = modelCache.values().iterator();
        while(iterator.hasNext()) {
            iterator.next().dispose();
        }
        modelCache.clear();
    }

    private void disposeRetiredModelsNow() {
        for(int i = 0; i < retiredModels.size(); i++) {
            retiredModels.get(i).model.dispose();
        }
        retiredModels.clear();
    }

    private void drawAABB(B3Vec3 lower, B3Vec3 upper, long color) {
        float lx = lower.GetX();
        float ly = lower.GetY();
        float lz = lower.GetZ();
        float ux = upper.GetX();
        float uy = upper.GetY();
        float uz = upper.GetZ();
        line(lx, ly, lz, ux, ly, lz, color, 1.0f);
        line(ux, ly, lz, ux, ly, uz, color, 1.0f);
        line(ux, ly, uz, lx, ly, uz, color, 1.0f);
        line(lx, ly, uz, lx, ly, lz, color, 1.0f);
        line(lx, uy, lz, ux, uy, lz, color, 1.0f);
        line(ux, uy, lz, ux, uy, uz, color, 1.0f);
        line(ux, uy, uz, lx, uy, uz, color, 1.0f);
        line(lx, uy, uz, lx, uy, lz, color, 1.0f);
        line(lx, ly, lz, lx, uy, lz, color, 1.0f);
        line(ux, ly, lz, ux, uy, lz, color, 1.0f);
        line(ux, ly, uz, ux, uy, uz, color, 1.0f);
        line(lx, ly, uz, lx, uy, uz, color, 1.0f);
    }

    private void drawBox(float hx, float hy, float hz, B3Transform transform, long color) {
        transformPoint(transform, -hx, -hy, -hz, transformedPoints, 0);
        transformPoint(transform, hx, -hy, -hz, transformedPoints, 3);
        transformPoint(transform, hx, -hy, hz, transformedPoints, 6);
        transformPoint(transform, -hx, -hy, hz, transformedPoints, 9);
        transformPoint(transform, -hx, hy, -hz, transformedPoints, 12);
        transformPoint(transform, hx, hy, -hz, transformedPoints, 15);
        transformPoint(transform, hx, hy, hz, transformedPoints, 18);
        transformPoint(transform, -hx, hy, hz, transformedPoints, 21);
        edge(0, 1, color);
        edge(1, 2, color);
        edge(2, 3, color);
        edge(3, 0, color);
        edge(4, 5, color);
        edge(5, 6, color);
        edge(6, 7, color);
        edge(7, 4, color);
        edge(0, 4, color);
        edge(1, 5, color);
        edge(2, 6, color);
        edge(3, 7, color);
    }

    private void edge(int a, int b, long color) {
        line(transformedPoints, a * 3, b * 3, color, 1.0f);
    }

    private void drawWireSphere(float x, float y, float z, float radius, long color, float alpha) {
        if(radius <= 0.0f) {
            return;
        }
        for(int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double a0 = Math.PI * 2.0 * i / CIRCLE_SEGMENTS;
            double a1 = Math.PI * 2.0 * (i + 1) / CIRCLE_SEGMENTS;
            float c0 = (float)Math.cos(a0) * radius;
            float s0 = (float)Math.sin(a0) * radius;
            float c1 = (float)Math.cos(a1) * radius;
            float s1 = (float)Math.sin(a1) * radius;
            line(x + c0, y + s0, z, x + c1, y + s1, z, color, alpha);
            line(x + c0, y, z + s0, x + c1, y, z + s1, color, alpha);
            line(x, y + c0, z + s0, x, y + c1, z + s1, color, alpha);
        }
    }

    private void drawWireCapsule(float[] points, int p1, int p2, float radius, long color, float alpha) {
        line(points, p1, p2, color, alpha);
        drawWireSphere(points[p1], points[p1 + 1], points[p1 + 2], radius, color, alpha);
        drawWireSphere(points[p2], points[p2 + 1], points[p2 + 2], radius, color, alpha);
    }

    private void line(B3Vec3 p1, B3Vec3 p2, long color, float alpha) {
        line(p1.GetX(), p1.GetY(), p1.GetZ(), p2.GetX(), p2.GetY(), p2.GetZ(), color, alpha);
    }

    private void line(float[] points, int p1, int p2, long color, float alpha) {
        line(points[p1], points[p1 + 1], points[p1 + 2],
                points[p2], points[p2 + 1], points[p2 + 2], color, alpha);
    }

    private void line(float x1, float y1, float z1, float x2, float y2, float z2, long color, float alpha) {
        long rgb = color & 0x00FFFFFFL;
        lineRenderer.line3D(x1, y1, z1, x2, y2, z2,
                ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f,
                (rgb & 0xFF) / 255.0f,
                Math.max(0.0f, Math.min(1.0f, alpha)));
    }

    private static float[] sphereTriangles(float radius, int slices, int stacks) {
        FloatArray out = new FloatArray();
        for(int stack = 0; stack < stacks; stack++) {
            float v0 = stack / (float)stacks;
            float v1 = (stack + 1) / (float)stacks;
            float theta0 = (float)(-Math.PI * 0.5 + Math.PI * v0);
            float theta1 = (float)(-Math.PI * 0.5 + Math.PI * v1);
            for(int slice = 0; slice < slices; slice++) {
                float u0 = slice / (float)slices;
                float u1 = (slice + 1) / (float)slices;
                float[] p00 = spherePoint(radius, theta0, u0);
                float[] p01 = spherePoint(radius, theta0, u1);
                float[] p10 = spherePoint(radius, theta1, u0);
                float[] p11 = spherePoint(radius, theta1, u1);
                addTriangle(out, p00, p10, p01);
                addTriangle(out, p01, p10, p11);
            }
        }
        return out.toArray();
    }

    private static float[] capsuleTriangles(float radius, float segmentLength, int slices, int hemisphereStacks) {
        FloatArray out = new FloatArray();
        Array<float[]> rings = new Array<float[]>();
        float half = segmentLength * 0.5f;
        for(int i = 0; i <= hemisphereStacks; i++) {
            float t = i / (float)hemisphereStacks;
            float theta = (float)(-Math.PI * 0.5 + Math.PI * 0.5 * t);
            rings.add(new float[] { (float)Math.cos(theta) * radius, -half + (float)Math.sin(theta) * radius });
        }
        rings.add(new float[] { radius, half });
        for(int i = 1; i <= hemisphereStacks; i++) {
            float t = i / (float)hemisphereStacks;
            float theta = (float)(Math.PI * 0.5 * t);
            rings.add(new float[] { (float)Math.cos(theta) * radius, half + (float)Math.sin(theta) * radius });
        }

        for(int r = 0; r + 1 < rings.size(); r++) {
            float[] ring0 = rings.get(r);
            float[] ring1 = rings.get(r + 1);
            for(int slice = 0; slice < slices; slice++) {
                float u0 = slice / (float)slices;
                float u1 = (slice + 1) / (float)slices;
                float[] p00 = ringPoint(ring0[0], ring0[1], u0);
                float[] p01 = ringPoint(ring0[0], ring0[1], u1);
                float[] p10 = ringPoint(ring1[0], ring1[1], u0);
                float[] p11 = ringPoint(ring1[0], ring1[1], u1);
                addTriangle(out, p00, p10, p01);
                addTriangle(out, p01, p10, p11);
            }
        }
        return out.toArray();
    }

    private static float[] spherePoint(float radius, float theta, float u) {
        float ring = (float)Math.cos(theta) * radius;
        float y = (float)Math.sin(theta) * radius;
        return ringPoint(ring, y, u);
    }

    private static float[] ringPoint(float ringRadius, float y, float u) {
        float phi = (float)(Math.PI * 2.0 * u);
        return new float[] { (float)Math.cos(phi) * ringRadius, y, (float)Math.sin(phi) * ringRadius };
    }

    private static void addTriangle(FloatArray out, float[] p0, float[] p1, float[] p2) {
        addPoint(out, p0);
        addPoint(out, p1);
        addPoint(out, p2);
    }

    private static void addPoint(FloatArray out, float[] point) {
        out.add(point[0]);
        out.add(point[1]);
        out.add(point[2]);
    }

    private float[] colorsForVertices(int vertexCount) {
        float[] colors = new float[vertexCount * 4];
        int index = 0;
        for(int i = 0; i < vertexCount; i++) {
            colors[index++] = solidRgba[0];
            colors[index++] = solidRgba[1];
            colors[index++] = solidRgba[2];
            colors[index++] = solidRgba[3];
        }
        return colors;
    }

    private int appendVertex(float[] positions, int offset, B3Vec3 value) {
        positions[offset++] = value.GetX();
        positions[offset++] = value.GetY();
        positions[offset++] = value.GetZ();
        return offset;
    }

    private int appendColor(float[] colors, int offset) {
        colors[offset++] = solidRgba[0];
        colors[offset++] = solidRgba[1];
        colors[offset++] = solidRgba[2];
        colors[offset++] = solidRgba[3];
        return offset;
    }

    private static void transformPoint(B3Transform transform, B3Vec3 point, float[] out, int offset) {
        transformPoint(transform, point.GetX(), point.GetY(), point.GetZ(), out, offset);
    }

    private static void transformPoint(B3Transform transform, float x, float y, float z, float[] out, int offset) {
        B3Quat q = transform.GetQ();
        B3Vec3 qv = q.GetV();
        float qx = qv.GetX();
        float qy = qv.GetY();
        float qz = qv.GetZ();
        float qw = q.GetS();
        float tx = 2.0f * (qy * z - qz * y);
        float ty = 2.0f * (qz * x - qx * z);
        float tz = 2.0f * (qx * y - qy * x);
        float rx = x + qw * tx + qy * tz - qz * ty;
        float ry = y + qw * ty + qz * tx - qx * tz;
        float rz = z + qw * tz + qx * ty - qy * tx;
        B3Vec3 p = transform.GetP();
        out[offset] = p.GetX() + rx;
        out[offset + 1] = p.GetY() + ry;
        out[offset + 2] = p.GetZ() + rz;
    }

    private static void writePoint(B3Vec3 value, float[] out, int offset) {
        out[offset] = value.GetX();
        out[offset + 1] = value.GetY();
        out[offset + 2] = value.GetZ();
    }

    private static void disposeNative(NativeObject object) {
        if(object != null && object.native_hasOwnership() && !object.isDisposed()) {
            object.dispose();
        }
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static GraphicsContext requireGraphics(GraphicsContext graphics) {
        if(graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        return graphics;
    }

    private static final class DebugModel implements Disposable {
        Model meshModel;
        DefaultModelInstance meshInstance;
        B3Body shadowBody;
        final Array<PrimitiveInstance> primitives = new Array<PrimitiveInstance>();

        boolean castsShadow() {
            return shadowBody != null && shadowBody.IsValid() && shadowBody.GetType() != B3.StaticBody();
        }

        @Override
        public void dispose() {
            if(meshModel != null) {
                meshModel.dispose();
                meshModel = null;
                meshInstance = null;
            }
            for(int i = 0; i < primitives.size(); i++) {
                primitives.get(i).model.dispose();
            }
            primitives.clear();
            disposeNative(shadowBody);
            shadowBody = null;
        }

        @Override
        public boolean isDisposed() {
            return meshModel == null && primitives.isEmpty() && shadowBody == null;
        }
    }

    private static final class RetiredDebugModel {
        final DebugModel model;
        int remainingFrames;

        RetiredDebugModel(DebugModel model, int remainingFrames) {
            this.model = model;
            this.remainingFrames = remainingFrames;
        }
    }

    private static final class PrimitiveInstance {
        final Model model;
        final DefaultModelInstance instance;
        final Matrix4 localTransform = new Matrix4();

        PrimitiveInstance(Model model, DefaultModelInstance instance) {
            this.model = model;
            this.instance = instance;
        }
    }

}
