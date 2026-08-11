package io.github.libfdx.physics.box3d;

import com.github.xpenatan.box3d.B3;
import com.github.xpenatan.box3d.B3AABB;
import com.github.xpenatan.box3d.B3Capsule;
import com.github.xpenatan.box3d.B3DebugDrawEm;
import com.github.xpenatan.box3d.B3DebugShape;
import com.github.xpenatan.box3d.B3Quat;
import com.github.xpenatan.box3d.B3Sphere;
import com.github.xpenatan.box3d.B3Transform;
import com.github.xpenatan.box3d.B3Vec3;
import com.github.xpenatan.box3d.B3World;
import com.github.xpenatan.jParser.api.NativeObject;
import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.FloatArray;
import io.github.libfdx.collections.IntMap;
import io.github.libfdx.collections.LongMap;
import io.github.libfdx.collections.ObjectIterator;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.ImmediateModeRenderer;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.g3d.CascadedShadowMap3D;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment3D;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.ModelBuilder;
import io.github.libfdx.graphics.g3d.ModelVertexUsage;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.MaterialAttributes;
import io.github.libfdx.graphics.g3d.PbrAttributes;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;

import java.util.Arrays;

public class FdxDebugRenderer extends B3DebugDrawEm {
    public static final float DEFAULT_SHADOW_BIAS = 0.001f;

    private static final int CIRCLE_SEGMENTS = 24;
    private static final int SPHERE_SLICES = 24;
    private static final int SPHERE_STACKS = 12;
    private static final float TRANSFORM_AXIS_LENGTH = 0.35f;
    private static final int RETIRED_MODEL_FRAME_DELAY = 3;
    private static final float DEFAULT_DRAW_DISTANCE = 100.0f;
    private static final int SHADOW_CASCADE_COUNT = 3;
    private static final int SHADOW_MAP_SIZE = 1024;
    private static final float SHADOW_DISTANCE = 50.0f;
    private static final float GRID_HALF_SIZE = 100.0f;
    private static final float GRID_SPACING = 5.0f;
    private static final int GRID_MAJOR_INTERVAL = 5;

    private final GraphicsContext graphics;
    private final ModelBatch modelBatch;
    private final ImmediateModeRenderer lineRenderer;
    private final InstancedSolidRenderer instancedSolidRenderer;
    private final InstancedWireRenderer instancedWireRenderer;
    private final Environment3D environment;
    private final DirectionalLight directionalLight;
    private final CascadedShadowMap3D shadowMap;
    private final boolean ownsModelBatch;
    private final boolean ownsLineRenderer;
    private final LongMap<SharedGeometry> geometryIdCache = new LongMap<SharedGeometry>(128, 0.7f);
    private final ObjectMap<GeometryDescriptor, SharedGeometry> geometryCache =
            new ObjectMap<GeometryDescriptor, SharedGeometry>(128, 0.7f);
    private final Array<SharedGeometry> sharedGeometries = new Array<SharedGeometry>();
    private final Array<RetiredSharedGeometry> retiredGeometries = new Array<RetiredSharedGeometry>();
    private final Array<DefaultModelInstance> visibleInstances = new Array<DefaultModelInstance>();
    private final Array<DefaultModelInstance> shadowCasterInstances = new Array<DefaultModelInstance>();
    private final Matrix4 worldTransform = new Matrix4();
    private final Matrix4 localTransform = new Matrix4();
    private final Matrix4 combinedTransform = new Matrix4();
    private final float[] solidRgba = new float[] { 1.0f, 1.0f, 1.0f, 1.0f };
    private final float[] transformedPoints = new float[24];
    private final float[] viewProjectionValues = new float[Matrix4.VALUE_COUNT];
    private final B3Vec3 drawingLowerBound = new B3Vec3();
    private final B3Vec3 drawingUpperBound = new B3Vec3();
    private final B3AABB drawingBounds = new B3AABB();
    private boolean enabled = true;
    private boolean drawSolidShapes = true;
    private boolean drawWireframe = true;
    private boolean shadowsEnabled = true;
    private boolean collectSolidShapes;
    private float shadowBias = DEFAULT_SHADOW_BIAS;
    private float drawOriginX;
    private float drawOriginY;
    private float drawOriginZ;
    private long renderedWorldId = Long.MIN_VALUE;
    private int visibleInstanceCount;
    private int solidDrawCallCount;
    private int shadowDrawCallCount;
    private int wireDrawCallCount;
    private int sharedGeometryCount;

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
        this.instancedSolidRenderer = graphics != null ? new InstancedSolidRenderer(graphics) : null;
        this.instancedWireRenderer = graphics != null ? new InstancedWireRenderer(graphics) : null;
        this.ownsModelBatch = ownsModelBatch;
        this.ownsLineRenderer = ownsLineRenderer;
        directionalLight = new DirectionalLight().direction(-0.45f, -0.55f, -0.70f).intensity(1.75f);
        shadowMap = graphics != null
                ? new CascadedShadowMap3D(graphics, SHADOW_CASCADE_COUNT,
                        SHADOW_MAP_SIZE, SHADOW_MAP_SIZE)
                        .maxDistance(SHADOW_DISTANCE)
                        .bias(0.0f)
                        .minTexelBias(shadowBias * SHADOW_MAP_SIZE)
                        .strength(0.82f)
                : null;
        this.environment = new Environment3D()
                .ambientColor(new Color(0.18f, 0.19f, 0.21f, 1.0f))
                .add(directionalLight);
        if(shadowMap != null) {
            environment.cascadedShadowMap(shadowMap);
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
        long worldId = world.GetId();
        if(worldId != renderedWorldId) {
            clearShapeCache();
            renderedWorldId = worldId;
        }
        disposeExpiredRetiredGeometries();
        clearFrame();
        if(!enabled) {
            return;
        }

        drawOriginX = camera.position().x();
        drawOriginY = camera.position().y();
        drawOriginZ = camera.position().z();
        updateDrawingBounds();
        camera.position(0.0f, 0.0f, 0.0f).update();
        try {
            collectSolidShapes = drawSolidShapes && graphics != null && modelBatch != null;
            DrawWorld(world, B3.DefaultMaskBits());
            collectSolidShapes = false;
            drawReferenceGrid();

            if(drawSolidShapes && instancedSolidRenderer != null
                    && instancedSolidRenderer.hasInstances()) {
                renderInstancedSolids(camera);
            }
            else if(drawSolidShapes && modelBatch != null && !visibleInstances.isEmpty()) {
                renderModelBatchSolids(camera);
            }

            renderLines(camera.combined());
        }
        finally {
            collectSolidShapes = false;
            camera.position(drawOriginX, drawOriginY, drawOriginZ).update();
        }
    }

    public void render(B3World world, Matrix4 viewProjection) {
        if(world == null) {
            throw new FdxException("B3World cannot be null");
        }
        if(viewProjection == null) {
            throw new FdxException("View-projection matrix cannot be null");
        }
        long worldId = world.GetId();
        if(worldId != renderedWorldId) {
            clearShapeCache();
            renderedWorldId = worldId;
        }
        disposeExpiredRetiredGeometries();
        clearFrame();
        if(!enabled) {
            return;
        }
        drawOriginX = 0.0f;
        drawOriginY = 0.0f;
        drawOriginZ = 0.0f;
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
                environment.cascadedShadowMap(shadowMap);
            }
            else {
                environment.clearCascadedShadowMap();
            }
        }
    }

    public float getShadowBias() {
        return shadowBias;
    }

    public void setShadowBias(float shadowBias) {
        this.shadowBias = Math.max(0.0f, shadowBias);
        if(shadowMap != null) {
            shadowMap.bias(0.0f).minTexelBias(this.shadowBias * SHADOW_MAP_SIZE);
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
        SetDrawIslands(enabled);
    }

    public void clearShapeCache() {
        geometryIdCache.clear();
        geometryCache.clear();
        if(sharedGeometries.isEmpty()) {
            return;
        }
        for(int i = 0; i < sharedGeometries.size(); i++) {
            retiredGeometries.add(new RetiredSharedGeometry(
                    sharedGeometries.get(i), RETIRED_MODEL_FRAME_DELAY));
        }
        sharedGeometries.clear();
        sharedGeometryCount = 0;
    }

    public void clear() {
        clearFrame();
        clearShapeCache();
    }

    /** Returns the number of solid shape instances submitted in the latest frame. */
    public int getVisibleInstanceCount() {
        return visibleInstanceCount;
    }

    /** Returns the number of unique shared geometries retained by this renderer. */
    public int getSharedGeometryCount() {
        return sharedGeometryCount;
    }

    /** Returns the number of GPU draw calls used for the solid color pass. */
    public int getSolidDrawCallCount() {
        return solidDrawCallCount;
    }

    /** Returns the number of GPU draw calls used for the solid shadow pass. */
    public int getShadowDrawCallCount() {
        return shadowDrawCallCount;
    }

    /** Returns the number of GPU draw calls used for repeated shape wireframes. */
    public int getWireDrawCallCount() {
        return wireDrawCallCount;
    }

    @Override
    protected void DrawShape(B3DebugShape shape, B3Transform transform, int color) {
        if(!enabled || shape == null || transform == null) {
            return;
        }

        SharedGeometry geometry = null;
        if(collectSolidShapes || drawWireframe) {
            geometry = getOrCreateGeometry(shape);
        }
        if(collectSolidShapes) {
            appendSolidInstances(geometry, shape, transform, color);
        }

        if(drawWireframe) {
            int wireColor = drawSolidShapes ? contrastingWireColor(color) : color;
            if(instancedWireRenderer != null && instancedWireRenderer.supported()
                    && geometry.wirePrimary != null) {
                appendWireInstances(geometry, shape, transform, wireColor);
            }
            else {
                drawShapeWire(geometry.descriptor, shape, transform, wireColor);
            }
        }
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
        disposeSharedGeometries();
        disposeRetiredGeometriesNow();
        disposeNative(drawingBounds);
        disposeNative(drawingUpperBound);
        disposeNative(drawingLowerBound);
        if(instancedSolidRenderer != null) {
            instancedSolidRenderer.dispose();
        }
        if(instancedWireRenderer != null) {
            instancedWireRenderer.dispose();
        }
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

    private void renderModelBatchSolids(Camera camera) {
        renderModelBatchShadows(camera);
        modelBatch.begin(camera);
        modelBatch.render(visibleInstances.view());
        modelBatch.end();
        solidDrawCallCount = visibleInstances.size();
        shadowDrawCallCount = shadowsEnabled
                ? shadowCasterInstances.size() * shadowMap.cascadeCount() : 0;
    }

    private void renderInstancedSolids(Camera camera) {
        updateShadowMap(camera);
        if(shadowsEnabled) {
            instancedSolidRenderer.renderShadow(shadowMap, directionalLight);
            shadowDrawCallCount = instancedSolidRenderer.shadowDrawCallCount();
        }
        camera.combined().copyValues(viewProjectionValues, 0);
        instancedSolidRenderer.render(viewProjectionValues, directionalLight,
                environment.ambientColor(), shadowMap, shadowsEnabled);
        solidDrawCallCount = instancedSolidRenderer.mainDrawCallCount();
    }

    private void renderModelBatchShadows(Camera camera) {
        updateShadowMap(camera);
        if(!shadowsEnabled) {
            return;
        }
        for(int i = 0; i < shadowMap.cascadeCount(); i++) {
            shadowMap.cascade(i).render(directionalLight, shadowCasterInstances);
        }
    }

    private void updateShadowMap(Camera camera) {
        if(!shadowsEnabled || shadowMap == null) {
            if(shadowMap != null) {
                environment.clearCascadedShadowMap();
            }
            return;
        }
        shadowMap.update(directionalLight, camera);
        environment.cascadedShadowMap(shadowMap);
    }

    private SharedGeometry getOrCreateGeometry(B3DebugShape shape) {
        long cacheKey = geometryHandleKey(shape);
        SharedGeometry geometry = geometryIdCache.get(cacheKey);
        if(geometry != null) {
            return geometry;
        }

        int type = shape.GetType();
        GeometryDescriptor descriptor;
        if(type == B3.SphereShape()) {
            descriptor = GeometryDescriptor.unitSphere();
        }
        else if(type == B3.CapsuleShape()) {
            descriptor = GeometryDescriptor.unitCapsule();
        }
        else {
            descriptor = GeometryDescriptor.read(shape);
        }
        geometry = geometryCache.get(descriptor);
        if(geometry == null) {
            geometry = createSharedGeometry(descriptor);
            geometryCache.put(descriptor, geometry);
            sharedGeometries.add(geometry);
            sharedGeometryCount = sharedGeometries.size();
        }
        geometryIdCache.put(cacheKey, geometry);
        return geometry;
    }

    private SharedGeometry createSharedGeometry(GeometryDescriptor descriptor) {
        InstancedSolidRenderer.Geometry solidPrimary = null;
        InstancedSolidRenderer.Geometry solidSecondary = null;
        InstancedWireRenderer.Geometry wirePrimary = null;
        InstancedWireRenderer.Geometry wireSecondary = null;
        String geometryId = descriptor.kind.name().toLowerCase()
                + "-" + Integer.toHexString(descriptor.hashCode());
        if(instancedSolidRenderer != null && instancedSolidRenderer.supported()) {
            String id = "box3d-debug-solid-" + geometryId;
            if(descriptor.kind == GeometryKind.UNIT_SPHERE) {
                float[] positions = sphereTriangles(1.0f, SPHERE_SLICES, SPHERE_STACKS);
                solidPrimary = instancedSolidRenderer.createGeometry(id, positions,
                        sphereNormals(positions));
            }
            else if(descriptor.kind == GeometryKind.UNIT_CAPSULE) {
                float[] cylinder = cylinderTriangles(1.0f, 1.0f, SPHERE_SLICES);
                solidPrimary = instancedSolidRenderer.createGeometry(id + "-cylinder", cylinder,
                        cylinderNormals(cylinder));
                float[] sphere = sphereTriangles(1.0f, SPHERE_SLICES, SPHERE_STACKS);
                solidSecondary = instancedSolidRenderer.createGeometry(id + "-sphere", sphere,
                        sphereNormals(sphere));
            }
            else if(descriptor.triangles.length > 0) {
                solidPrimary = instancedSolidRenderer.createGeometry(id,
                        descriptorPositions(descriptor), descriptorNormals(descriptor));
            }
        }
        if(instancedWireRenderer != null && instancedWireRenderer.supported()) {
            String id = "box3d-debug-wire-" + geometryId;
            if(descriptor.kind == GeometryKind.UNIT_SPHERE) {
                wirePrimary = instancedWireRenderer.createGeometry(id, sphereWireVertices(CIRCLE_SEGMENTS));
            }
            else if(descriptor.kind == GeometryKind.UNIT_CAPSULE) {
                wirePrimary = instancedWireRenderer.createGeometry(id + "-cylinder",
                        cylinderWireVertices(CIRCLE_SEGMENTS));
                wireSecondary = instancedWireRenderer.createGeometry(id + "-sphere",
                        sphereWireVertices(CIRCLE_SEGMENTS));
            }
            else {
                wirePrimary = instancedWireRenderer.createGeometry(id, descriptor.edges);
            }
        }
        return new SharedGeometry(descriptor, solidPrimary, solidSecondary,
                wirePrimary, wireSecondary);
    }

    private ColoredGeometry coloredGeometry(SharedGeometry geometry, int color) {
        int rgb = color & 0x00FFFFFF;
        ColoredGeometry colored = geometry.colors.get(rgb);
        if(colored == null) {
            colored = buildColoredGeometry(geometry.descriptor, rgb);
            geometry.colors.put(rgb, colored);
        }
        return colored;
    }

    private ColoredGeometry buildColoredGeometry(GeometryDescriptor descriptor, int rgb) {
        ColoredGeometry colored = new ColoredGeometry();
        ModelBuilder builder = new ModelBuilder(graphics)
                .material(solidMaterial("box3d-debug-solid-" + Integer.toHexString(rgb)));
        if(descriptor.kind == GeometryKind.UNIT_SPHERE) {
            float[] positions = sphereTriangles(1.0f, SPHERE_SLICES, SPHERE_STACKS);
            float[] normals = sphereNormals(positions);
            colored.models.add(buildPooledModel(builder, "box3d-debug-unit-sphere", positions, normals, rgb));
            return colored;
        }
        if(descriptor.kind == GeometryKind.UNIT_CAPSULE) {
            float[] cylinder = cylinderTriangles(1.0f, 1.0f, SPHERE_SLICES);
            colored.models.add(buildPooledModel(builder, "box3d-debug-unit-cylinder", cylinder, null, rgb));
            float[] sphere = sphereTriangles(1.0f, SPHERE_SLICES, SPHERE_STACKS);
            colored.models.add(buildPooledModel(builder, "box3d-debug-unit-capsule-sphere", sphere,
                    sphereNormals(sphere), rgb));
            return colored;
        }
        if(descriptor.triangles.length > 0) {
            int triangleCount = descriptor.triangles.length / GeometryDescriptor.TRIANGLE_STRIDE;
            float[] positions = new float[triangleCount * 9];
            float[] normals = new float[triangleCount * 9];
            int positionOffset = 0;
            int normalOffset = 0;
            for(int triangle = 0; triangle < triangleCount; triangle++) {
                int source = triangle * GeometryDescriptor.TRIANGLE_STRIDE;
                System.arraycopy(descriptor.triangles, source, positions, positionOffset, 9);
                positionOffset += 9;
                for(int vertex = 0; vertex < 3; vertex++) {
                    normals[normalOffset++] = descriptor.triangles[source + 9];
                    normals[normalOffset++] = descriptor.triangles[source + 10];
                    normals[normalOffset++] = descriptor.triangles[source + 11];
                }
            }
            colored.models.add(buildPooledModel(builder, "box3d-debug-geometry", positions, normals, rgb));
        }
        return colored;
    }

    private PooledModel buildPooledModel(ModelBuilder builder, String id, float[] positions,
            float[] normals, int rgb) {
        float[] colors = colorsForVertices(positions.length / 3, rgb);
        Model model = builder.triangles(id + "-" + sharedGeometryCount + "-" + Integer.toHexString(rgb),
                positions, null, colors, normals, ModelVertexUsage.STANDARD_PBR);
        return new PooledModel(model);
    }

    private void appendSolidInstances(SharedGeometry geometry, B3DebugShape shape,
            B3Transform transform, int color) {
        if(geometry.solidPrimary != null) {
            appendInstancedSolidInstances(geometry, shape, transform, color);
            return;
        }
        ColoredGeometry colored = coloredGeometry(geometry, color);
        toRelativeTransform(transform, worldTransform);
        if(geometry.descriptor.kind == GeometryKind.UNIT_SPHERE) {
            if(colored.models.isEmpty()) {
                return;
            }
            B3Sphere sphere = shape.GetSphere();
            float radius = sphere.GetRadius();
            if(radius <= 0.0f) {
                return;
            }
            B3Vec3 center = sphere.GetCenter();
            localTransform.setToTrs(center.GetX(), center.GetY(), center.GetZ(),
                    0.0f, 0.0f, 0.0f, 1.0f, radius, radius, radius);
            combinedTransform.setToMul(worldTransform, localTransform);
            appendInstance(colored.models.get(0), combinedTransform);
            return;
        }
        if(geometry.descriptor.kind == GeometryKind.UNIT_CAPSULE) {
            appendCapsuleInstances(colored, shape.GetCapsule());
            return;
        }
        for(int i = 0; i < colored.models.size(); i++) {
            appendInstance(colored.models.get(i), worldTransform);
        }
    }

    private void appendInstancedSolidInstances(SharedGeometry geometry, B3DebugShape shape,
            B3Transform transform, int color) {
        toRelativeTransform(transform, worldTransform);
        if(geometry.descriptor.kind == GeometryKind.UNIT_SPHERE) {
            B3Sphere sphere = shape.GetSphere();
            float radius = sphere.GetRadius();
            if(radius <= 0.0f) {
                return;
            }
            B3Vec3 center = sphere.GetCenter();
            localTransform.setToTrs(center.GetX(), center.GetY(), center.GetZ(),
                    0.0f, 0.0f, 0.0f, 1.0f, radius, radius, radius);
            combinedTransform.setToMul(worldTransform, localTransform);
            appendSolidInstance(geometry.solidPrimary, combinedTransform, color);
            return;
        }
        if(geometry.descriptor.kind == GeometryKind.UNIT_CAPSULE) {
            appendInstancedCapsuleInstances(geometry, shape.GetCapsule(), color);
            return;
        }
        appendSolidInstance(geometry.solidPrimary, worldTransform, color);
    }

    private void appendInstancedCapsuleInstances(SharedGeometry geometry,
            B3Capsule capsule, int color) {
        if(geometry.solidSecondary == null) {
            return;
        }
        B3Vec3 c1 = capsule.GetCenter1();
        B3Vec3 c2 = capsule.GetCenter2();
        float radius = capsule.GetRadius();
        if(radius <= 0.0f) {
            return;
        }
        float x1 = c1.GetX();
        float y1 = c1.GetY();
        float z1 = c1.GetZ();
        float x2 = c2.GetX();
        float y2 = c2.GetY();
        float z2 = c2.GetZ();
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        if(length > 0.00001f) {
            setCapsuleTransform(localTransform, x1, y1, z1, x2, y2, z2, radius, length);
            combinedTransform.setToMul(worldTransform, localTransform);
            appendSolidInstance(geometry.solidPrimary, combinedTransform, color);
        }
        localTransform.setToTrs(x1, y1, z1, 0.0f, 0.0f, 0.0f, 1.0f,
                radius, radius, radius);
        combinedTransform.setToMul(worldTransform, localTransform);
        appendSolidInstance(geometry.solidSecondary, combinedTransform, color);
        localTransform.setToTrs(x2, y2, z2, 0.0f, 0.0f, 0.0f, 1.0f,
                radius, radius, radius);
        combinedTransform.setToMul(worldTransform, localTransform);
        appendSolidInstance(geometry.solidSecondary, combinedTransform, color);
    }

    private void appendSolidInstance(InstancedSolidRenderer.Geometry geometry,
            Matrix4 transform, int color) {
        geometry.append(transform, color, solidRgba);
        visibleInstanceCount++;
    }

    private void appendCapsuleInstances(ColoredGeometry colored, B3Capsule capsule) {
        if(colored.models.size() < 2) {
            return;
        }
        B3Vec3 c1 = capsule.GetCenter1();
        B3Vec3 c2 = capsule.GetCenter2();
        float radius = capsule.GetRadius();
        if(radius <= 0.0f) {
            return;
        }
        float x1 = c1.GetX();
        float y1 = c1.GetY();
        float z1 = c1.GetZ();
        float x2 = c2.GetX();
        float y2 = c2.GetY();
        float z2 = c2.GetZ();
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        if(length > 0.00001f) {
            setCapsuleTransform(localTransform, x1, y1, z1, x2, y2, z2, radius, length);
            combinedTransform.setToMul(worldTransform, localTransform);
            appendInstance(colored.models.get(0), combinedTransform);
        }
        localTransform.setToTrs(x1, y1, z1, 0.0f, 0.0f, 0.0f, 1.0f,
                radius, radius, radius);
        combinedTransform.setToMul(worldTransform, localTransform);
        appendInstance(colored.models.get(1), combinedTransform);
        localTransform.setToTrs(x2, y2, z2, 0.0f, 0.0f, 0.0f, 1.0f,
                radius, radius, radius);
        combinedTransform.setToMul(worldTransform, localTransform);
        appendInstance(colored.models.get(1), combinedTransform);
    }

    private void appendInstance(PooledModel model, Matrix4 transform) {
        DefaultModelInstance instance = model.obtain();
        instance.transform(transform);
        visibleInstances.add(instance);
        shadowCasterInstances.add(instance);
        visibleInstanceCount++;
    }

    private void appendWireInstances(SharedGeometry geometry, B3DebugShape shape,
            B3Transform transform, int color) {
        toRelativeTransform(transform, worldTransform);
        if(geometry.descriptor.kind == GeometryKind.UNIT_SPHERE) {
            B3Sphere sphere = shape.GetSphere();
            float radius = sphere.GetRadius();
            if(radius <= 0.0f) {
                return;
            }
            B3Vec3 center = sphere.GetCenter();
            localTransform.setToTrs(center.GetX(), center.GetY(), center.GetZ(),
                    0.0f, 0.0f, 0.0f, 1.0f, radius, radius, radius);
            combinedTransform.setToMul(worldTransform, localTransform);
            geometry.wirePrimary.append(combinedTransform, color);
            return;
        }
        if(geometry.descriptor.kind == GeometryKind.UNIT_CAPSULE) {
            appendCapsuleWireInstances(geometry, shape.GetCapsule(), color);
            return;
        }
        geometry.wirePrimary.append(worldTransform, color);
    }

    private void appendCapsuleWireInstances(SharedGeometry geometry, B3Capsule capsule, int color) {
        if(geometry.wireSecondary == null) {
            return;
        }
        B3Vec3 c1 = capsule.GetCenter1();
        B3Vec3 c2 = capsule.GetCenter2();
        float radius = capsule.GetRadius();
        if(radius <= 0.0f) {
            return;
        }
        float x1 = c1.GetX();
        float y1 = c1.GetY();
        float z1 = c1.GetZ();
        float x2 = c2.GetX();
        float y2 = c2.GetY();
        float z2 = c2.GetZ();
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        if(length > 0.00001f) {
            setCapsuleTransform(localTransform, x1, y1, z1, x2, y2, z2, radius, length);
            combinedTransform.setToMul(worldTransform, localTransform);
            geometry.wirePrimary.append(combinedTransform, color);
        }
        localTransform.setToTrs(x1, y1, z1, 0.0f, 0.0f, 0.0f, 1.0f,
                radius, radius, radius);
        combinedTransform.setToMul(worldTransform, localTransform);
        geometry.wireSecondary.append(combinedTransform, color);
        localTransform.setToTrs(x2, y2, z2, 0.0f, 0.0f, 0.0f, 1.0f,
                radius, radius, radius);
        combinedTransform.setToMul(worldTransform, localTransform);
        geometry.wireSecondary.append(combinedTransform, color);
    }

    private void drawShapeWire(GeometryDescriptor descriptor, B3DebugShape shape,
            B3Transform transform, int color) {
        B3Vec3 scale = shape.GetScale();
        float scaleX = scale.GetX();
        float scaleY = scale.GetY();
        float scaleZ = scale.GetZ();
        for(int i = 0; i < shape.GetSphereCount(); i++) {
            B3Sphere sphere = shape.GetSphereAt(i);
            transformScaledPoint(transform, sphere.GetCenter(), scaleX, scaleY, scaleZ,
                    transformedPoints, 0);
            drawWireSphere(transformedPoints[0], transformedPoints[1], transformedPoints[2],
                    sphere.GetRadius(), color, 1.0f);
        }
        for(int i = 0; i < shape.GetCapsuleCount(); i++) {
            B3Capsule capsule = shape.GetCapsuleAt(i);
            transformScaledPoint(transform, capsule.GetCenter1(), scaleX, scaleY, scaleZ,
                    transformedPoints, 0);
            transformScaledPoint(transform, capsule.GetCenter2(), scaleX, scaleY, scaleZ,
                    transformedPoints, 3);
            drawWireCapsule(transformedPoints, 0, 3, capsule.GetRadius(), color, 1.0f);
        }
        for(int offset = 0; offset < descriptor.edges.length;
                offset += GeometryDescriptor.EDGE_STRIDE) {
            transformPoint(transform, descriptor.edges[offset], descriptor.edges[offset + 1],
                    descriptor.edges[offset + 2], transformedPoints, 0);
            transformPoint(transform, descriptor.edges[offset + 3], descriptor.edges[offset + 4],
                    descriptor.edges[offset + 5], transformedPoints, 3);
            line(transformedPoints, 0, 3, color, 1.0f);
        }
    }

    private static int contrastingWireColor(int color) {
        int red = Math.max(10, Math.round(((color >>> 16) & 0xFF) * 0.28f));
        int green = Math.max(10, Math.round(((color >>> 8) & 0xFF) * 0.28f));
        int blue = Math.max(10, Math.round((color & 0xFF) * 0.28f));
        return red << 16 | green << 8 | blue;
    }

    private Material solidMaterial(String id) {
        return new Material(id)
                .set(MaterialAttributes.baseColor(1.0f, 1.0f, 1.0f, 1.0f))
                .set(PbrAttributes.roughnessFactor(0.85f))
                .doubleSided(true);
    }

    private void renderLines(Matrix4 viewProjection) {
        viewProjection.copyValues(viewProjectionValues, 0);
        if(instancedWireRenderer != null) {
            instancedWireRenderer.render(viewProjectionValues);
            wireDrawCallCount = instancedWireRenderer.drawCallCount();
        }
        lineRenderer.render3D(viewProjectionValues);
        lineRenderer.clear3D();
    }

    private void clearFrame() {
        visibleInstances.clear();
        shadowCasterInstances.clear();
        lineRenderer.clear3D();
        visibleInstanceCount = 0;
        solidDrawCallCount = 0;
        shadowDrawCallCount = 0;
        wireDrawCallCount = 0;
        if(instancedSolidRenderer != null) {
            instancedSolidRenderer.beginFrame();
        }
        if(instancedWireRenderer != null) {
            instancedWireRenderer.beginFrame();
        }
        for(int i = 0; i < sharedGeometries.size(); i++) {
            sharedGeometries.get(i).beginFrame();
        }
    }

    private void disposeExpiredRetiredGeometries() {
        for(int i = retiredGeometries.size() - 1; i >= 0; i--) {
            RetiredSharedGeometry retired = retiredGeometries.get(i);
            retired.remainingFrames--;
            if(retired.remainingFrames <= 0) {
                retired.geometry.dispose();
                retiredGeometries.removeIndex(i);
            }
        }
    }

    private void disposeSharedGeometries() {
        for(int i = 0; i < sharedGeometries.size(); i++) {
            sharedGeometries.get(i).dispose();
        }
        sharedGeometries.clear();
        geometryCache.clear();
        geometryIdCache.clear();
    }

    private void disposeRetiredGeometriesNow() {
        for(int i = 0; i < retiredGeometries.size(); i++) {
            retiredGeometries.get(i).geometry.dispose();
        }
        retiredGeometries.clear();
    }

    private void updateDrawingBounds() {
        drawingLowerBound.Set(drawOriginX - DEFAULT_DRAW_DISTANCE,
                drawOriginY - DEFAULT_DRAW_DISTANCE, drawOriginZ - DEFAULT_DRAW_DISTANCE);
        drawingUpperBound.Set(drawOriginX + DEFAULT_DRAW_DISTANCE,
                drawOriginY + DEFAULT_DRAW_DISTANCE, drawOriginZ + DEFAULT_DRAW_DISTANCE);
        drawingBounds.SetLowerBound(drawingLowerBound);
        drawingBounds.SetUpperBound(drawingUpperBound);
        SetDrawingBounds(drawingBounds);
    }

    private void drawReferenceGrid() {
        float centerX = Math.round(drawOriginX / GRID_SPACING) * GRID_SPACING;
        float centerZ = Math.round(drawOriginZ / GRID_SPACING) * GRID_SPACING;
        int lineCount = Math.round(GRID_HALF_SIZE / GRID_SPACING);
        for(int i = -lineCount; i <= lineCount; i++) {
            float offset = i * GRID_SPACING;
            long color = i % GRID_MAJOR_INTERVAL == 0 ? 0x52677AL : 0x3B4C5CL;
            line(centerX + offset, 0.01f, centerZ - GRID_HALF_SIZE,
                    centerX + offset, 0.01f, centerZ + GRID_HALF_SIZE, color, 1.0f);
            line(centerX - GRID_HALF_SIZE, 0.01f, centerZ + offset,
                    centerX + GRID_HALF_SIZE, 0.01f, centerZ + offset, color, 1.0f);
        }
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
        lineRenderer.line3D(x1 - drawOriginX, y1 - drawOriginY, z1 - drawOriginZ,
                x2 - drawOriginX, y2 - drawOriginY, z2 - drawOriginZ,
                ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f,
                (rgb & 0xFF) / 255.0f,
                Math.max(0.0f, Math.min(1.0f, alpha)));
    }

    private static long geometryHandleKey(B3DebugShape shape) {
        long key = shape.GetGeometryId();
        key ^= (long)shape.GetType() * 0x9E3779B97F4A7C15L;
        if(shape.GetType() != B3.SphereShape() && shape.GetType() != B3.CapsuleShape()) {
            B3Vec3 scale = shape.GetScale();
            key = mixKey(key, Float.floatToIntBits(scale.GetX()));
            key = mixKey(key, Float.floatToIntBits(scale.GetY()));
            key = mixKey(key, Float.floatToIntBits(scale.GetZ()));
        }
        return key;
    }

    private static long mixKey(long value, int component) {
        long mixed = value ^ (component & 0xFFFFFFFFL);
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        return mixed ^ mixed >>> 31;
    }

    private void toRelativeTransform(B3Transform transform, Matrix4 out) {
        B3Vec3 position = transform.GetP();
        B3Quat rotation = transform.GetQ();
        B3Vec3 vector = rotation.GetV();
        out.setToTrs(position.GetX() - drawOriginX,
                position.GetY() - drawOriginY,
                position.GetZ() - drawOriginZ,
                vector.GetX(), vector.GetY(), vector.GetZ(), rotation.GetS(),
                1.0f, 1.0f, 1.0f);
    }

    private static void setCapsuleTransform(Matrix4 out,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float radius, float length) {
        float dx = (x2 - x1) / length;
        float dy = (y2 - y1) / length;
        float dz = (z2 - z1) / length;
        float qx = 0.0f;
        float qy = 0.0f;
        float qz = 0.0f;
        float qw = 1.0f;
        if(dy < -0.9999f) {
            qx = 1.0f;
            qw = 0.0f;
        }
        else if(dy < 0.9999f) {
            qx = dz;
            qz = -dx;
            qw = 1.0f + dy;
            float inverseLength = 1.0f / (float)Math.sqrt(qx * qx + qz * qz + qw * qw);
            qx *= inverseLength;
            qz *= inverseLength;
            qw *= inverseLength;
        }
        out.setToTrs((x1 + x2) * 0.5f, (y1 + y2) * 0.5f, (z1 + z2) * 0.5f,
                qx, qy, qz, qw, radius, length, radius);
    }

    private static void transformScaledPoint(B3Transform transform, B3Vec3 point,
            float scaleX, float scaleY, float scaleZ, float[] out, int offset) {
        transformPoint(transform, point.GetX() * scaleX, point.GetY() * scaleY,
                point.GetZ() * scaleZ, out, offset);
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

    private static float[] sphereNormals(float[] positions) {
        float[] normals = new float[positions.length];
        for(int i = 0; i < positions.length; i += 3) {
            float x = positions[i];
            float y = positions[i + 1];
            float z = positions[i + 2];
            float lengthSquared = x * x + y * y + z * z;
            if(lengthSquared > 0.00000001f) {
                float inverseLength = 1.0f / (float)Math.sqrt(lengthSquared);
                normals[i] = x * inverseLength;
                normals[i + 1] = y * inverseLength;
                normals[i + 2] = z * inverseLength;
            }
            else {
                normals[i + 1] = 1.0f;
            }
        }
        return normals;
    }

    private static float[] cylinderTriangles(float radius, float height, int slices) {
        FloatArray out = new FloatArray();
        float halfHeight = height * 0.5f;
        for(int slice = 0; slice < slices; slice++) {
            float u0 = slice / (float)slices;
            float u1 = (slice + 1) / (float)slices;
            float[] lower0 = ringPoint(radius, -halfHeight, u0);
            float[] lower1 = ringPoint(radius, -halfHeight, u1);
            float[] upper0 = ringPoint(radius, halfHeight, u0);
            float[] upper1 = ringPoint(radius, halfHeight, u1);
            addTriangle(out, lower0, upper0, lower1);
            addTriangle(out, lower1, upper0, upper1);
        }
        return out.toArray();
    }

    private static float[] cylinderNormals(float[] positions) {
        float[] normals = new float[positions.length];
        for(int i = 0; i < positions.length; i += 3) {
            float x = positions[i];
            float z = positions[i + 2];
            float lengthSquared = x * x + z * z;
            if(lengthSquared > 0.00000001f) {
                float inverseLength = 1.0f / (float)Math.sqrt(lengthSquared);
                normals[i] = x * inverseLength;
                normals[i + 2] = z * inverseLength;
            }
            else {
                normals[i + 1] = 1.0f;
            }
        }
        return normals;
    }

    private static float[] descriptorPositions(GeometryDescriptor descriptor) {
        int triangleCount = descriptor.triangles.length / GeometryDescriptor.TRIANGLE_STRIDE;
        float[] positions = new float[triangleCount * 9];
        for(int triangle = 0; triangle < triangleCount; triangle++) {
            System.arraycopy(descriptor.triangles,
                    triangle * GeometryDescriptor.TRIANGLE_STRIDE,
                    positions, triangle * 9, 9);
        }
        return positions;
    }

    private static float[] descriptorNormals(GeometryDescriptor descriptor) {
        int triangleCount = descriptor.triangles.length / GeometryDescriptor.TRIANGLE_STRIDE;
        float[] normals = new float[triangleCount * 9];
        int offset = 0;
        for(int triangle = 0; triangle < triangleCount; triangle++) {
            int source = triangle * GeometryDescriptor.TRIANGLE_STRIDE + 9;
            for(int vertex = 0; vertex < 3; vertex++) {
                normals[offset++] = descriptor.triangles[source];
                normals[offset++] = descriptor.triangles[source + 1];
                normals[offset++] = descriptor.triangles[source + 2];
            }
        }
        return normals;
    }

    private static float[] sphereWireVertices(int segments) {
        FloatArray out = new FloatArray();
        for(int axis = 0; axis < 3; axis++) {
            for(int segment = 0; segment < segments; segment++) {
                float angle0 = (float)(Math.PI * 2.0 * segment / segments);
                float angle1 = (float)(Math.PI * 2.0 * (segment + 1) / segments);
                float cos0 = (float)Math.cos(angle0);
                float sin0 = (float)Math.sin(angle0);
                float cos1 = (float)Math.cos(angle1);
                float sin1 = (float)Math.sin(angle1);
                if(axis == 0) {
                    addLine(out, 0.0f, cos0, sin0, 0.0f, cos1, sin1);
                }
                else if(axis == 1) {
                    addLine(out, cos0, 0.0f, sin0, cos1, 0.0f, sin1);
                }
                else {
                    addLine(out, cos0, sin0, 0.0f, cos1, sin1, 0.0f);
                }
            }
        }
        return out.toArray();
    }

    private static float[] cylinderWireVertices(int segments) {
        FloatArray out = new FloatArray();
        for(int segment = 0; segment < segments; segment++) {
            float angle0 = (float)(Math.PI * 2.0 * segment / segments);
            float angle1 = (float)(Math.PI * 2.0 * (segment + 1) / segments);
            float x0 = (float)Math.cos(angle0);
            float z0 = (float)Math.sin(angle0);
            float x1 = (float)Math.cos(angle1);
            float z1 = (float)Math.sin(angle1);
            addLine(out, x0, -0.5f, z0, x1, -0.5f, z1);
            addLine(out, x0, 0.5f, z0, x1, 0.5f, z1);
            if(segment % Math.max(1, segments / 8) == 0) {
                addLine(out, x0, -0.5f, z0, x0, 0.5f, z0);
            }
        }
        return out.toArray();
    }

    private static void addLine(FloatArray out, float x1, float y1, float z1,
            float x2, float y2, float z2) {
        out.add(x1);
        out.add(y1);
        out.add(z1);
        out.add(x2);
        out.add(y2);
        out.add(z2);
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

    private float[] colorsForVertices(int vertexCount, int rgb) {
        float[] colors = new float[vertexCount * 4];
        float red = srgbToLinear(((rgb >>> 16) & 0xFF) / 255.0f) * solidRgba[0];
        float green = srgbToLinear(((rgb >>> 8) & 0xFF) / 255.0f) * solidRgba[1];
        float blue = srgbToLinear((rgb & 0xFF) / 255.0f) * solidRgba[2];
        int index = 0;
        for(int i = 0; i < vertexCount; i++) {
            colors[index++] = red;
            colors[index++] = green;
            colors[index++] = blue;
            colors[index++] = solidRgba[3];
        }
        return colors;
    }

    private static float srgbToLinear(float value) {
        return value <= 0.04045f ? value / 12.92f
                : (float)Math.pow((value + 0.055f) / 1.055f, 2.4f);
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

    private enum GeometryKind {
        TRIANGLES,
        UNIT_SPHERE,
        UNIT_CAPSULE
    }

    private static final class GeometryDescriptor {
        static final int TRIANGLE_STRIDE = 12;
        static final int EDGE_STRIDE = 6;
        private static final GeometryDescriptor UNIT_SPHERE =
                new GeometryDescriptor(GeometryKind.UNIT_SPHERE, new float[0], new float[0]);
        private static final GeometryDescriptor UNIT_CAPSULE =
                new GeometryDescriptor(GeometryKind.UNIT_CAPSULE, new float[0], new float[0]);

        final GeometryKind kind;
        final float[] triangles;
        final float[] edges;
        private final int hashCode;

        private GeometryDescriptor(GeometryKind kind, float[] triangles, float[] edges) {
            this.kind = kind;
            this.triangles = triangles;
            this.edges = edges;
            hashCode = 31 * (31 * kind.hashCode() + Arrays.hashCode(triangles))
                    + Arrays.hashCode(edges);
        }

        static GeometryDescriptor unitSphere() {
            return UNIT_SPHERE;
        }

        static GeometryDescriptor unitCapsule() {
            return UNIT_CAPSULE;
        }

        static GeometryDescriptor read(B3DebugShape shape) {
            B3Vec3 scale = shape.GetScale();
            float scaleX = scale.GetX();
            float scaleY = scale.GetY();
            float scaleZ = scale.GetZ();
            int triangleCount = shape.GetTriangleCount();
            float[] triangles = new float[triangleCount * TRIANGLE_STRIDE];
            for(int triangle = 0; triangle < triangleCount; triangle++) {
                int offset = triangle * TRIANGLE_STRIDE;
                writeScaled(shape.GetTriangleVertex0(triangle), scaleX, scaleY, scaleZ,
                        triangles, offset);
                writeScaled(shape.GetTriangleVertex1(triangle), scaleX, scaleY, scaleZ,
                        triangles, offset + 3);
                writeScaled(shape.GetTriangleVertex2(triangle), scaleX, scaleY, scaleZ,
                        triangles, offset + 6);
                writeScaledNormal(shape.GetTriangleNormal(triangle), scaleX, scaleY, scaleZ,
                        triangles, offset + 9);
            }
            int edgeCount = shape.GetHullEdgeCount();
            float[] edges = new float[edgeCount * EDGE_STRIDE];
            for(int edge = 0; edge < edgeCount; edge++) {
                int offset = edge * EDGE_STRIDE;
                writeScaled(shape.GetHullEdgeVertex0(edge), scaleX, scaleY, scaleZ,
                        edges, offset);
                writeScaled(shape.GetHullEdgeVertex1(edge), scaleX, scaleY, scaleZ,
                        edges, offset + 3);
            }
            return new GeometryDescriptor(GeometryKind.TRIANGLES, triangles, edges);
        }

        private static void writeScaled(B3Vec3 value, float scaleX, float scaleY, float scaleZ,
                float[] output, int offset) {
            output[offset] = value.GetX() * scaleX;
            output[offset + 1] = value.GetY() * scaleY;
            output[offset + 2] = value.GetZ() * scaleZ;
        }

        private static void writeScaledNormal(B3Vec3 value, float scaleX, float scaleY, float scaleZ,
                float[] output, int offset) {
            float x = scaleX != 0.0f ? value.GetX() / scaleX : 0.0f;
            float y = scaleY != 0.0f ? value.GetY() / scaleY : 0.0f;
            float z = scaleZ != 0.0f ? value.GetZ() / scaleZ : 0.0f;
            float lengthSquared = x * x + y * y + z * z;
            if(lengthSquared > 0.00000001f) {
                float inverseLength = 1.0f / (float)Math.sqrt(lengthSquared);
                x *= inverseLength;
                y *= inverseLength;
                z *= inverseLength;
            }
            else {
                x = 0.0f;
                y = 1.0f;
                z = 0.0f;
            }
            output[offset] = x;
            output[offset + 1] = y;
            output[offset + 2] = z;
        }

        @Override
        public boolean equals(Object object) {
            if(this == object) {
                return true;
            }
            if(!(object instanceof GeometryDescriptor)) {
                return false;
            }
            GeometryDescriptor other = (GeometryDescriptor)object;
            return kind == other.kind && Arrays.equals(triangles, other.triangles)
                    && Arrays.equals(edges, other.edges);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class SharedGeometry implements Disposable {
        final GeometryDescriptor descriptor;
        final InstancedSolidRenderer.Geometry solidPrimary;
        final InstancedSolidRenderer.Geometry solidSecondary;
        final InstancedWireRenderer.Geometry wirePrimary;
        final InstancedWireRenderer.Geometry wireSecondary;
        final IntMap<ColoredGeometry> colors = new IntMap<ColoredGeometry>();
        private boolean disposed;

        SharedGeometry(GeometryDescriptor descriptor,
                InstancedSolidRenderer.Geometry solidPrimary,
                InstancedSolidRenderer.Geometry solidSecondary,
                InstancedWireRenderer.Geometry wirePrimary,
                InstancedWireRenderer.Geometry wireSecondary) {
            this.descriptor = descriptor;
            this.solidPrimary = solidPrimary;
            this.solidSecondary = solidSecondary;
            this.wirePrimary = wirePrimary;
            this.wireSecondary = wireSecondary;
        }

        void beginFrame() {
            ObjectIterator<ColoredGeometry> iterator = colors.values().iterator();
            while(iterator.hasNext()) {
                iterator.next().beginFrame();
            }
        }

        @Override
        public void dispose() {
            if(disposed) {
                return;
            }
            disposed = true;
            ObjectIterator<ColoredGeometry> iterator = colors.values().iterator();
            while(iterator.hasNext()) {
                iterator.next().dispose();
            }
            colors.clear();
            if(solidPrimary != null) {
                solidPrimary.dispose();
            }
            if(solidSecondary != null && solidSecondary != solidPrimary) {
                solidSecondary.dispose();
            }
            if(wirePrimary != null) {
                wirePrimary.dispose();
            }
            if(wireSecondary != null && wireSecondary != wirePrimary) {
                wireSecondary.dispose();
            }
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    private static final class ColoredGeometry implements Disposable {
        final Array<PooledModel> models = new Array<PooledModel>();
        private boolean disposed;

        void beginFrame() {
            for(int i = 0; i < models.size(); i++) {
                models.get(i).beginFrame();
            }
        }

        @Override
        public void dispose() {
            if(disposed) {
                return;
            }
            disposed = true;
            for(int i = 0; i < models.size(); i++) {
                models.get(i).dispose();
            }
            models.clear();
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    private static final class PooledModel implements Disposable {
        private Model model;
        private final Array<DefaultModelInstance> instances = new Array<DefaultModelInstance>();
        private int usedInstances;

        PooledModel(Model model) {
            this.model = model;
        }

        void beginFrame() {
            usedInstances = 0;
        }

        DefaultModelInstance obtain() {
            if(usedInstances == instances.size()) {
                instances.add(new DefaultModelInstance(model));
            }
            return instances.get(usedInstances++);
        }

        @Override
        public void dispose() {
            if(model != null) {
                model.dispose();
                model = null;
            }
            instances.clear();
            usedInstances = 0;
        }

        @Override
        public boolean isDisposed() {
            return model == null;
        }
    }

    private static final class RetiredSharedGeometry {
        final SharedGeometry geometry;
        int remainingFrames;

        RetiredSharedGeometry(SharedGeometry geometry, int remainingFrames) {
            this.geometry = geometry;
            this.remainingFrames = remainingFrames;
        }
    }

}
