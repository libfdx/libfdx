package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.collections.OrderedMap;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Vector3;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.ColorTransfer;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;


/**
 * Builds model instances and related output.
 *
 * @author xpenatan
 */
public final class ModelBuilder {
    private static final ArrayView<String> SHAPE_TYPES = Array.of(
            "BOX", "ROOF", "BEAM", "CYLINDER", "TRIANGLE", "QUAD").view();
    private static final ArrayView<ArrayView<String>> SHAPE_PARAMETERS = Array.<ArrayView<String>>of(
            Array.of("X", "Y", "Z", "Width", "Height", "Depth").view(),
            Array.of("Width", "Depth", "Base", "Height").view(),
            Array.of("Start X", "Start Y", "Start Z", "End X", "End Y", "End Z", "Width").view(),
            Array.of("X", "Y", "Z", "Radius", "Length").view(),
            Array.of("A X", "A Y", "A Z", "B X", "B Y", "B Z", "C X", "C Y", "C Z").view(),
            Array.of("A X", "A Y", "A Z", "B X", "B Y", "B Z", "C X", "C Y", "C Z", "D X", "D Y", "D Z").view()).view();
    private final GraphicsContext graphics;
    private Material material = new Material("default");
    private final OrderedMap<String, ModelShapeChunk> active = new OrderedMap<>();
    private final Array<ModelShapePart> completed = new Array<>();
    private final float[] color = {1, 1, 1, 1};
    private String shapeMaterial = "default";
    private boolean finished;

    /**
     * Starts CPU-only indexed shape construction, usable on any platform and on
     * a worker without a graphics context. The caller owns the builder and its
     * finished data. Upload or export that data separately; no files are written.
     * Existing box, cylinder and plane model methods use this same shape path.
     * Use append methods and finishShapes() on this builder. GPU model methods
     * require the graphics-context constructor. Confine each builder to one
     * thread; discard it after an input error. Completed arrays are borrowed
     * read-only from their result and may be published after finishShapes().
     */
    public static ModelBuilder shapes() {
        return new ModelBuilder();
    }

    /** Appends shape parameters produced by readShapes(), shape() or defaultShape().
     * Input is borrowed for this call. Colors in this representation are sRGB
     * and are converted to linear vertex colors. No graphics context is used. */
    public ModelBuilder appendShape(JsonValue shape) {
        requireOpen();
        String type = shape.require("type").stringValue();
        float[] p = floats(shape.require("parameters"));
        float[] c = floats(shape.require("color"));
        this.part(shape.require("material").stringValue()).color(
                ColorTransfer.srgbToLinear(c[0]), ColorTransfer.srgbToLinear(c[1]),
                ColorTransfer.srgbToLinear(c[2]), 1);
        switch(type) {
            case "BOX" -> this.appendBox(p[0], p[1], p[2], p[3], p[4], p[5]);
            case "ROOF" -> this.appendRoof(p[0], p[1], p[2], p[3]);
            case "BEAM" -> this.appendBeam(p[0], p[1], p[2], p[3], p[4], p[5], p[6]);
            case "CYLINDER" -> this.appendCylinderX(p[0], p[1], p[2], p[3], p[4],
                    shape.require("segments").intValue(), shape.require("smoothSides").booleanValue());
            case "TRIANGLE" -> this.appendTriangle(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8]);
            case "QUAD" -> this.appendQuad(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11]);
            default -> throw new IllegalArgumentException("Unsupported shape: " + type);
        }
        return this;
    }

    /** Reads the existing version-1 scene representation. Returns newly owned,
     * validated JSON parameters; it neither tessellates nor writes a model. */
    public static JsonValue readShapes(String text) {
        if(text == null || text.length() > 2_000_000)
            throw new IllegalArgumentException("Invalid model shape data size");
        JsonValue root = new JsonReader().parse(text);
        if(root.require("version").intValue() != 1)
            throw new IllegalArgumentException("Unsupported model shape version");
        return validateShapes(root.require("shapes"));
    }

    /** Writes validated shape parameters in the existing scene format. The
     * caller keeps its input; no geometry or generated asset path is stored. */
    public static String writeShapes(JsonValue shapes) {
        return JsonWriter.compact(JsonValue.object().put("version", 1)
                .put("shapes", validateShapes(shapes)));
    }

    /** Returns newly owned, validated JSON parameters, limited to 4,096 shapes
     * and a conservative 200,000-triangle budget before allocating geometry. */
    public static JsonValue validateShapes(JsonValue shapes) {
        if(shapes == null || !shapes.isArray() || shapes.size() == 0 || shapes.size() > 4096)
            throw new IllegalArgumentException("A model needs 1 to 4096 shapes");
        JsonValue result = JsonValue.array();
        int triangles = 0;
        for(int i = 0; i < shapes.size(); i++) {
            JsonValue shape = shapes.get(i);
            String type = shape.require("type").stringValue();
            int segments = shape.get("segments") == null ? 16 : shape.get("segments").intValue();
            result.add(shape(type, floats(shape.require("parameters")), floats(shape.require("color")),
                    shape.get("material") == null ? "surface" : shape.get("material").stringValue(),
                    segments, shape.get("smoothSides") != null && shape.get("smoothSides").booleanValue()));
            triangles += "CYLINDER".equals(type) ? segments * 4 : 12;
            if(triangles > 200_000)
                throw new IllegalArgumentException("Model exceeds 200,000 triangles; split it into independent parts");
        }
        return result;
    }

    /** Creates newly owned, validated scene parameters. Box origin is its
     * bottom center; cylinder axis is local X. Arrays are copied into JSON. */
    public static JsonValue shape(String type, float[] parameters, float[] color,
            String material, int segments, boolean smoothSides) {
        int count = shapeParameterNames(type).size();
        if(parameters == null || parameters.length != count)
            throw new IllegalArgumentException("Invalid " + type + " parameters");
        JsonValue values = JsonValue.array(), colors = JsonValue.array();
        for(float value : parameters) {
            if(!Float.isFinite(value) || Math.abs(value) > 100_000)
                throw new IllegalArgumentException("Shape parameters must be finite and within 100 km");
            values.add(value);
        }
        if(color == null || color.length != 3)
            throw new IllegalArgumentException("Shape needs three sRGB channels");
        for(float value : color) {
            if(!Float.isFinite(value) || value < 0 || value > 1)
                throw new IllegalArgumentException("sRGB channels must be between 0 and 1");
            colors.add(value);
        }
        material = material == null ? "surface" : material.trim();
        if(material.isEmpty() || material.length() > 128)
            throw new IllegalArgumentException("Material needs a name of at most 128 characters");
        if(segments < 3 || segments > 256)
            throw new IllegalArgumentException("Cylinder segments must be between 3 and 256");
        switch(type) {
            case "BOX" -> { positiveParameter(parameters[3]); positiveParameter(parameters[4]); positiveParameter(parameters[5]); }
            case "ROOF" -> { positiveParameter(parameters[0]); positiveParameter(parameters[1]); positiveParameter(parameters[3]); }
            case "BEAM" -> positiveParameter(parameters[6]);
            case "CYLINDER" -> { positiveParameter(parameters[3]); positiveParameter(parameters[4]); }
            default -> { }
        }
        return JsonValue.object().put("type", type).put("parameters", values).put("color", colors)
                .put("material", material).put("segments", segments).put("smoothSides", smoothSides);
    }

    /** Default editable arguments for one supported builder shape. */
    public static JsonValue defaultShape(String type) {
        float[] parameters = switch(type) {
            case "BOX" -> new float[]{0, 0, 0, 1, 1, 1};
            case "ROOF" -> new float[]{2, 2, 0, 1};
            case "BEAM" -> new float[]{0, 0, 0, 0, 1, 0, .1f};
            case "CYLINDER" -> new float[]{0, 0, 0, .5f, 1};
            case "TRIANGLE" -> new float[]{0, 0, 0, 0, 0, 1, 1, 0, 0};
            case "QUAD" -> new float[]{0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0};
            default -> throw new IllegalArgumentException("Unsupported shape: " + type);
        };
        return shape(type, parameters, new float[]{1, 1, 1}, "surface", 16, "CYLINDER".equals(type));
    }

    /** Stable read-only names supported by the version-1 parameter format. */
    public static ArrayView<String> shapeTypes() {
        return SHAPE_TYPES;
    }

    /** Stable read-only parameter names in the order accepted by shape(). */
    public static ArrayView<String> shapeParameterNames(String type) {
        for(int i = 0; i < SHAPE_TYPES.size(); i++)
            if(SHAPE_TYPES.get(i).equals(type)) return SHAPE_PARAMETERS.get(i);
        throw new IllegalArgumentException("Unsupported shape: " + type);
    }

    private static float[] floats(JsonValue array) {
        if(!array.isArray() || array.size() > 12)
            throw new IllegalArgumentException("Invalid numeric shape data");
        float[] values = new float[array.size()];
        for(int i = 0; i < values.length; i++) values[i] = array.get(i).floatValue();
        return values;
    }

    private static void positiveParameter(float value) {
        if(value <= 0) throw new IllegalArgumentException("Shape dimensions must be positive");
    }

    private ModelBuilder() { this.graphics = null; }

    /** Selects the material slot for subsequent shapes. */
    public ModelBuilder part(String name) {
        requireOpen();
        if(name == null || name.isBlank()) throw new FdxException("Material slot cannot be empty");
        shapeMaterial = name;
        return this;
    }

    /** Sets finite linear RGBA vertex color for subsequent shapes. */
    public ModelBuilder color(float red, float green, float blue, float alpha) {
        requireOpen(); finite(red, green, blue, alpha);
        color[0] = red; color[1] = green; color[2] = blue; color[3] = alpha;
        return this;
    }

    /** Appends a box centered at the origin. */
    public ModelBuilder appendBox(float width, float height, float depth) {
        return appendBox(0, -height / 2, 0, width, height, depth);
    }

    /** Appends a box positioned by its bottom center, with positive dimensions. */
    public ModelBuilder appendBox(float x, float bottomY, float z,
            float width, float height, float depth) {
        requireOpen(); finite(x, bottomY, z); positive(width); positive(height); positive(depth);
        float a = x - width / 2, b = x + width / 2, c = z - depth / 2,
                d = z + depth / 2, top = bottomY + height;
        face(v(a,top,d),v(b,top,d),v(b,top,c),v(a,top,c));
        face(v(a,bottomY,c),v(b,bottomY,c),v(b,bottomY,d),v(a,bottomY,d));
        face(v(a,bottomY,d),v(b,bottomY,d),v(b,top,d),v(a,top,d));
        face(v(b,bottomY,c),v(a,bottomY,c),v(a,top,c),v(b,top,c));
        face(v(b,bottomY,d),v(b,bottomY,c),v(b,top,c),v(b,top,d));
        face(v(a,bottomY,c),v(a,bottomY,d),v(a,top,d),v(a,top,c));
        return this;
    }

    /** Appends an XZ-centered gable roof: two slopes and two ends, open underneath. */
    public ModelBuilder appendRoof(float width, float depth, float baseY, float height) {
        requireOpen(); positive(width); positive(depth); positive(height); finite(baseY);
        float x = width / 2, z = depth / 2, top = baseY + height;
        face(v(-x,baseY,z),v(x,baseY,z),v(0,top,z));
        face(v(x,baseY,-z),v(-x,baseY,-z),v(0,top,-z));
        face(v(-x,baseY,-z),v(-x,baseY,z),v(0,top,z),v(0,top,-z));
        face(v(0,top,-z),v(0,top,z),v(x,baseY,z),v(x,baseY,-z));
        return this;
    }

    /** Appends a square-section beam between distinct endpoints. */
    public ModelBuilder appendBeam(float ax, float ay, float az,
            float bx, float by, float bz, float width) {
        requireOpen(); finite(ax, ay, az, bx, by, bz); positive(width);
        Vector3 direction = new Vector3(bx - ax, by - ay, bz - az);
        if(!Float.isFinite(direction.length()) || direction.length() < 1e-7f)
            throw new FdxException("Beam endpoints must differ and be within finite range");
        direction = direction.normalize();
        Vector3 u = new Vector3().set(direction).cross(Math.abs(direction.y()) > .9f
                ? new Vector3(1,0,0) : new Vector3(0,1,0)).normalize().scale(width / 2);
        Vector3 w = new Vector3().set(direction).cross(u).normalize().scale(width / 2);
        float[][] a = new float[4][], b = new float[4][];
        int[] us = {1,-1,-1,1}, vs = {1,1,-1,-1};
        for(int i = 0; i < 4; i++) {
            float x = us[i]*u.x()+vs[i]*w.x(), y = us[i]*u.y()+vs[i]*w.y(), z = us[i]*u.z()+vs[i]*w.z();
            a[i] = v(ax+x,ay+y,az+z); b[i] = v(bx+x,by+y,bz+z);
        }
        for(int i = 0; i < 4; i++) face(a[i],a[(i+1)%4],b[(i+1)%4],b[i]);
        face(a[3],a[2],a[1],a[0]); face(b[0],b[1],b[2],b[3]);
        return this;
    }

    /** Appends an arrow between distinct endpoints, with its square head
     * proportional to length. Endpoints are borrowed only during this call. */
    public ModelBuilder appendArrow(Vector3 from, Vector3 to) {
        requireOpen();
        if(from == null || to == null) throw new FdxException("Arrow endpoints cannot be null");
        finite(from.x(), from.y(), from.z(), to.x(), to.y(), to.z());
        Vector3 direction = to.subtract(from);
        float length = direction.length();
        if(!Float.isFinite(length) || length < 1e-7f)
            throw new FdxException("Arrow endpoints must differ and be within finite range");
        Vector3 forward = direction.normalize();
        Vector3 reference = Math.abs(forward.dot(Vector3.Y)) < .9f ? Vector3.Y : Vector3.X;
        Vector3 side = forward.cross(reference).normalize().scale(length * .08f);
        Vector3 up = side.cross(forward).normalize().scale(length * .08f);
        Vector3 center = to.subtract(forward.scale(length * .25f));
        Vector3[] corners = {center.add(side).add(up), center.subtract(side).add(up),
                center.subtract(side).subtract(up), center.add(side).subtract(up)};
        float[] start = v(from.x(), from.y(), from.z()), end = v(to.x(), to.y(), to.z());
        for(int i = 0; i < corners.length; i++) {
            Vector3 a = corners[i], b = corners[(i + 1) % corners.length];
            float[] av = v(a.x(), a.y(), a.z()), bv = v(b.x(), b.y(), b.z());
            face(start, av, bv);
            face(av, end, bv);
        }
        return this;
    }

    /** Creates a GPU arrow model with the current material and default vertex usages. */
    public Model arrow(Vector3 from, Vector3 to) {
        return arrow(from, to, ModelVertexUsage.DEFAULT);
    }

    /** Creates a GPU arrow model. The caller owns the returned model; this
     * operation requires a graphics-context builder on the graphics thread. */
    public Model arrow(Vector3 from, Vector3 to, long usage) {
        return shapeModel("arrow", shapes().appendArrow(from, to).finishShapes(), usage);
    }

    /** Appends a smooth Y-axis cylinder centered at the origin, with hard caps. */
    public ModelBuilder appendCylinder(float radius, float height, int divisions) {
        return appendCylinder(0, 0, 0, radius, height, divisions, true, true);
    }

    /** Appends an X-axis cylinder at the given center. Smooth sides retain hard caps. */
    public ModelBuilder appendCylinderX(float x, float y, float z, float radius,
            float length, int divisions, boolean smoothSides) {
        return appendCylinder(x, y, z, radius, length, divisions, smoothSides, false);
    }

    private ModelBuilder appendCylinder(float x, float y, float z, float radius,
            float length, int divisions, boolean smooth, boolean yAxis) {
        requireOpen(); finite(x, y, z); positive(radius); positive(length);
        if(divisions < 3 || divisions > 1_000_000)
            throw new FdxException("Cylinder divisions must be between 3 and 1,000,000");
        for(int i = 0; i < divisions; i++) {
            double a = i * Math.PI * 2 / divisions, b = ((i+1)%divisions) * Math.PI * 2 / divisions;
            float ca = (float)Math.cos(a), sa = (float)Math.sin(a), cb = (float)Math.cos(b), sb = (float)Math.sin(b);
            float lo = -length / 2, hi = length / 2;
            float[][] points = {point(x,y,z,lo,radius*ca,radius*sa,yAxis),point(x,y,z,lo,radius*cb,radius*sb,yAxis),
                    point(x,y,z,hi,radius*cb,radius*sb,yAxis),point(x,y,z,hi,radius*ca,radius*sa,yAxis)};
            float[][] normals = smooth ? new float[][]{axis(0,ca,sa,yAxis),axis(0,cb,sb,yAxis),
                    axis(0,cb,sb,yAxis),axis(0,ca,sa,yAxis)} : null;
            append(points, normals);
            float[] positive = axis(1,0,0,yAxis), negative = axis(-1,0,0,yAxis);
            append(new float[][]{point(x,y,z,hi,0,0,yAxis),points[3],points[2]},new float[][]{positive,positive,positive});
            append(new float[][]{point(x,y,z,lo,0,0,yAxis),points[1],points[0]},new float[][]{negative,negative,negative});
        }
        return this;
    }

    /** Appends an upward-facing XZ plane centered at the origin. */
    public ModelBuilder appendPlane(float width, float depth) {
        positive(width); positive(depth);
        return appendQuad(-width/2,0,-depth/2, -width/2,0,depth/2,
                width/2,0,depth/2, width/2,0,-depth/2);
    }

    /** Appends a counterclockwise triangle with a flat normal. */
    public ModelBuilder appendTriangle(float ax, float ay, float az, float bx, float by,
            float bz, float cx, float cy, float cz) {
        face(v(ax,ay,az),v(bx,by,bz),v(cx,cy,cz));
        return this;
    }

    /** Appends a counterclockwise planar convex quad, split along its first/third corners. */
    public ModelBuilder appendQuad(float ax, float ay, float az, float bx, float by,
            float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        face(v(ax,ay,az),v(bx,by,bz),v(cx,cy,cz),v(dx,dy,dz));
        return this;
    }

    /** Finishes once and returns a stable read-only view. Further shape calls are rejected. */
    public ArrayView<ModelShapePart> finishShapes() {
        if(!finished) {
            var chunks = active.values().iterator();
            while(chunks.hasNext()) completed.add(chunks.next().finish());
            active.clear(); finished = true;
        }
        return completed.view();
    }

    private void face(float[]... points) { append(points, null); }
    private void append(float[][] points, float[][] normals) {
        requireOpen();
        ModelShapeChunk chunk = active.get(shapeMaterial);
        if(chunk == null || chunk.vertices() + points.length > 65_536) {
            if(chunk != null) completed.add(chunk.finish());
            chunk = new ModelShapeChunk(shapeMaterial); active.put(shapeMaterial, chunk);
        }
        chunk.face(points, color, normals);
    }
    private static float[] v(float x, float y, float z) { return new float[]{x,y,z}; }
    private static float[] axis(float x, float y, float z, boolean yAxis) {
        return yAxis ? v(-y,x,z) : v(x,y,z);
    }
    private static float[] point(float x, float y, float z, float a, float b, float c, boolean yAxis) {
        float[] result = axis(a,b,c,yAxis);
        result[0] += x; result[1] += y; result[2] += z;
        return result;
    }
    private void requireOpen() { if(finished) throw new FdxException("Model shape builder is finished"); }
    private static void positive(float value) {
        if(!Float.isFinite(value) || value <= 0) throw new FdxException("Shape dimensions must be finite and positive");
    }
    private static void finite(float... values) {
        for(float value : values) if(!Float.isFinite(value)) throw new FdxException("Shape values must be finite");
    }

    private Model shapeModel(String id, ArrayView<ModelShapePart> parts, long usage) {
        requireGraphics();
        validateUsage(usage);
        Array<Mesh> meshes = new Array<>();
        ModelNode node = new ModelNode(id);
        try {
            for(int p = 0; p < parts.size(); p++) {
                ModelShapePart part = parts.get(p);
                int[] indices = new int[part.indices().length];
                for(int i = 0; i < indices.length; i++) indices[i] = part.indices()[i] & 0xffff;
                boolean includeNormals = hasUsage(usage, ModelVertexUsage.NORMAL);
                TriangleVertices vertices = triangleVertices(part.positions(), indices,
                        hasUsage(usage, ModelVertexUsage.COLOR) ? part.colors() : null,
                        includeNormals ? part.normals() : null, includeNormals, Color.WHITE);
                Mesh mesh = createMesh(p == 0 ? id : id + "-" + p, vertices, usage, part.bounds());
                meshes.add(mesh);
                node.addPart(new ModelNodePart(new MeshPart(p == 0 ? id + " part" : id + " part-" + p,
                        mesh, null, 0, mesh.vertexCount()), material));
            }
            Array<ModelNode> nodes = new Array<>(); nodes.add(node);
            Array<Material> materials = new Array<>(); materials.add(material);
            return new DefaultModel(nodes, materials, null, meshes);
        }
        catch(RuntimeException error) {
            for(int i = 0; i < meshes.size(); i++) meshes.get(i).dispose();
            throw error;
        }
    }

    /**
     * Creates a model builder.
     *
     * @param graphics the graphics context
     */
    public ModelBuilder(GraphicsContext graphics) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        this.graphics = graphics;
    }

    /**
     * Sets the material and returns this model builder.
     *
     * @param material the material
     * @return this model builder for chaining
     */
    public ModelBuilder material(Material material) {
        if (material == null) {
            throw new FdxException("ModelBuilder material cannot be null");
        }
        this.material = material;
        return this;
    }

    /**
     * Runs the cube step.
     *
     * @param size the size
     * @return the cube
     */
    public Model cube(float size) {
        return cube("cube", size, ModelVertexUsage.DEFAULT);
    }

    /**
     * Builds a cube with the requested vertex usages.
     *
     * @param size the size
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the cube
     */
    public Model cube(float size, long usage) {
        return cube("cube", size, usage);
    }

    /**
     * Runs the cube step.
     *
     * @param id the identifier
     * @param size the size
     * @return the cube
     */
    public Model cube(String id, float size) {
        return cube(id, size, ModelVertexUsage.DEFAULT);
    }

    /**
     * Builds a named cube with the requested vertex usages.
     *
     * @param id the identifier
     * @param size the size
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the cube
     */
    public Model cube(String id, float size, long usage) {
        return box(id, size, size, size, usage);
    }

    /**
     * Runs the box step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param depth the depth
     * @return the box
     */
    public Model box(float width, float height, float depth) {
        return box("box", width, height, depth, ModelVertexUsage.DEFAULT);
    }

    /**
     * Builds a box with the requested vertex usages.
     *
     * @param width the width
     * @param height the height
     * @param depth the depth
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the box
     */
    public Model box(float width, float height, float depth, long usage) {
        return box("box", width, height, depth, usage);
    }

    /**
     * Runs the box step.
     *
     * @param id the identifier
     * @param width the width in pixels
     * @param height the height in pixels
     * @param depth the depth
     * @return the box
     */
    public Model box(String id, float width, float height, float depth) {
        return box(id, width, height, depth, ModelVertexUsage.DEFAULT);
    }

    /**
     * Builds a named box with the requested vertex usages.
     *
     * @param id the identifier
     * @param width the width
     * @param height the height
     * @param depth the depth
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the box
     */
    public Model box(String id, float width, float height, float depth,
            long usage) {
        return shapeModel(id, shapes().appendBox(width, height, depth).finishShapes(), usage);
    }

    /**
     * Runs the sphere step.
     *
     * @param radius the radius
     * @param divisions the divisions
     * @return the sphere
     */
    public Model sphere(float radius, int divisions) {
        return sphere("sphere", radius, divisions,
                Math.max(2, divisions / 2), ModelVertexUsage.DEFAULT);
    }

    /**
     * Builds a sphere with the requested vertex usages.
     *
     * @param radius the radius
     * @param divisions the divisions
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the sphere
     */
    public Model sphere(float radius, int divisions, long usage) {
        return sphere("sphere", radius, divisions,
                Math.max(2, divisions / 2), usage);
    }

    /**
     * Runs the sphere step.
     *
     * @param id the identifier
     * @param radius the radius
     * @param slices the slices
     * @param stacks the stacks
     * @return the sphere
     */
    public Model sphere(String id, float radius, int slices, int stacks) {
        return sphere(id, radius, slices, stacks, ModelVertexUsage.DEFAULT);
    }

    /**
     * Builds a named sphere with the requested vertex usages.
     *
     * @param id the identifier
     * @param radius the radius
     * @param slices the slices
     * @param stacks the stacks
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the sphere
     */
    public Model sphere(String id, float radius, int slices, int stacks,
            long usage) {
        validateUsage(usage);
        if (radius <= 0.0f) {
            throw new FdxException("Sphere radius must be greater than zero");
        }
        if (slices < 3 || stacks < 2) {
            throw new FdxException("Sphere slices must be >= 3 and stacks must be >= 2");
        }
        int vertexColumns = slices + 1;
        int vertexRows = stacks + 1;
        float[] positions = new float[vertexColumns * vertexRows * 3];
        float[] colors = hasUsage(usage, ModelVertexUsage.COLOR)
                ? new float[vertexColumns * vertexRows * 4] : null;
        float[] normals = hasUsage(usage, ModelVertexUsage.NORMAL)
                ? new float[vertexColumns * vertexRows * 3] : null;
        int p = 0;
        int c = 0;
        int n = 0;
        for (int stack = 0; stack <= stacks; stack++) {
            float v = stack / (float) stacks;
            float theta = (float) (-Math.PI * 0.5 + Math.PI * v);
            float y = (float) Math.sin(theta) * radius;
            float ring = (float) Math.cos(theta) * radius;
            for (int slice = 0; slice <= slices; slice++) {
                float u = slice / (float) slices;
                float phi = (float) (Math.PI * 2.0 * u);
                float x = (float) Math.cos(phi) * ring;
                float z = (float) Math.sin(phi) * ring;
                positions[p++] = x;
                positions[p++] = y;
                positions[p++] = z;
                float nx = x / radius;
                float ny = y / radius;
                float nz = z / radius;
                if (normals != null) {
                    normals[n++] = nx;
                    normals[n++] = ny;
                    normals[n++] = nz;
                }
                if (colors != null) {
                    colors[c++] = 1.0f;
                    colors[c++] = 1.0f;
                    colors[c++] = 1.0f;
                    colors[c++] = 1.0f;
                }
            }
        }
        int[] indices = new int[slices * stacks * 6];
        int index = 0;
        for (int stack = 0; stack < stacks; stack++) {
            for (int slice = 0; slice < slices; slice++) {
                int a = stack * vertexColumns + slice;
                int b = a + 1;
                int c0 = a + vertexColumns;
                int d = c0 + 1;
                indices[index++] = a;
                indices[index++] = c0;
                indices[index++] = b;
                indices[index++] = b;
                indices[index++] = c0;
                indices[index++] = d;
            }
        }
        return triangles(id, positions, indices, colors, normals, usage);
    }

    /**
     * Builds a Y-axis cylinder with the default vertex usages.
     *
     * @param radius the cylinder radius
     * @param height the total cylinder height
     * @param divisions the radial divisions
     * @return the cylinder
     */
    public Model cylinder(float radius, float height, int divisions) {
        return cylinder("cylinder", radius, height, divisions,
                ModelVertexUsage.DEFAULT);
    }

    /**
     * Builds a Y-axis cylinder with the requested vertex usages.
     *
     * @param radius the cylinder radius
     * @param height the total cylinder height
     * @param divisions the radial divisions
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the cylinder
     */
    public Model cylinder(float radius, float height, int divisions,
            long usage) {
        return cylinder("cylinder", radius, height, divisions, usage);
    }

    /**
     * Builds a named Y-axis cylinder with the requested vertex usages.
     *
     * @param id the identifier
     * @param radius the cylinder radius
     * @param height the total cylinder height
     * @param divisions the radial divisions
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the cylinder
     */
    public Model cylinder(String id, float radius, float height,
            int divisions, long usage) {
        return shapeModel(id, shapes().appendCylinder(radius, height, divisions).finishShapes(), usage);
    }

    /**
     * Builds a Y-axis cone with the default vertex usages.
     *
     * @param radius the base radius
     * @param height the total cone height
     * @param divisions the radial divisions
     * @return the cone
     */
    public Model cone(float radius, float height, int divisions) {
        return cone("cone", radius, height, divisions,
                ModelVertexUsage.DEFAULT);
    }

    /**
     * Builds a Y-axis cone with the requested vertex usages.
     *
     * @param radius the base radius
     * @param height the total cone height
     * @param divisions the radial divisions
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the cone
     */
    public Model cone(float radius, float height, int divisions,
            long usage) {
        return cone("cone", radius, height, divisions, usage);
    }

    /**
     * Builds a named Y-axis cone with the requested vertex usages.
     *
     * @param id the identifier
     * @param radius the base radius
     * @param height the total cone height
     * @param divisions the radial divisions
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the cone
     */
    public Model cone(String id, float radius, float height, int divisions,
            long usage) {
        validateRoundPrimitive(radius, divisions, "Cone");
        if (height <= 0.0f) {
            throw new FdxException("Cone height must be greater than zero");
        }
        int columns = divisions + 1;
        int sideBaseStart = 0;
        int sideApexStart = columns;
        int bottomCenter = columns * 2;
        int bottomRingStart = bottomCenter + 1;
        int vertexCount = bottomRingStart + columns;
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float halfHeight = height * 0.5f;
        float inverseSlopeLength = 1.0f
                / (float) Math.sqrt(height * height + radius * radius);
        for (int slice = 0; slice <= divisions; slice++) {
            float angle = fullCircle(slice, divisions);
            float radialX = (float) Math.cos(angle);
            float radialZ = (float) Math.sin(angle);
            float x = radialX * radius;
            float z = radialZ * radius;
            float normalX = radialX * height * inverseSlopeLength;
            float normalY = radius * inverseSlopeLength;
            float normalZ = radialZ * height * inverseSlopeLength;
            putVertex(positions, normals, sideBaseStart + slice,
                    x, -halfHeight, z, normalX, normalY, normalZ);
            putVertex(positions, normals, sideApexStart + slice,
                    0.0f, halfHeight, 0.0f,
                    normalX, normalY, normalZ);
            putVertex(positions, normals, bottomRingStart + slice,
                    x, -halfHeight, z, 0.0f, -1.0f, 0.0f);
        }
        putVertex(positions, normals, bottomCenter,
                0.0f, -halfHeight, 0.0f, 0.0f, -1.0f, 0.0f);

        int[] indices = new int[divisions * 6];
        int index = 0;
        for (int slice = 0; slice < divisions; slice++) {
            indices[index++] = sideBaseStart + slice;
            indices[index++] = sideApexStart + slice;
            indices[index++] = sideBaseStart + slice + 1;
            indices[index++] = bottomCenter;
            indices[index++] = bottomRingStart + slice;
            indices[index++] = bottomRingStart + slice + 1;
        }
        return triangles(id, positions, indices, null, normals, usage);
    }

    /**
     * Builds a Y-axis capsule with the default vertex usages.
     *
     * @param radius the capsule radius
     * @param height the total capsule height, including both rounded ends
     * @param divisions the radial divisions
     * @return the capsule
     */
    public Model capsule(float radius, float height, int divisions) {
        return capsule("capsule", radius, height, divisions,
                ModelVertexUsage.DEFAULT);
    }

    /**
     * Builds a Y-axis capsule with the requested vertex usages.
     *
     * @param radius the capsule radius
     * @param height the total capsule height, including both rounded ends
     * @param divisions the radial divisions
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the capsule
     */
    public Model capsule(float radius, float height, int divisions,
            long usage) {
        return capsule("capsule", radius, height, divisions, usage);
    }

    /**
     * Builds a named Y-axis capsule with the requested vertex usages.
     *
     * @param id the identifier
     * @param radius the capsule radius
     * @param height the total capsule height, including both rounded ends
     * @param divisions the radial divisions
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the capsule
     */
    public Model capsule(String id, float radius, float height, int divisions,
            long usage) {
        validateRoundPrimitive(radius, divisions, "Capsule");
        if (height < radius * 2.0f) {
            throw new FdxException(
                    "Capsule height must be at least twice its radius");
        }
        float cylinderHalfHeight = height * 0.5f - radius;
        int hemisphereStacks = Math.max(2, divisions / 4);
        if (cylinderHalfHeight == 0.0f) {
            return sphere(id, radius, divisions, hemisphereStacks * 2,
                    usage);
        }
        int columns = divisions + 1;
        int ringCount = (hemisphereStacks + 1) * 2;
        float[] positions = new float[ringCount * columns * 3];
        float[] normals = new float[ringCount * columns * 3];
        int ring = 0;
        for (int stack = 0; stack <= hemisphereStacks; stack++) {
            float progress = stack / (float) hemisphereStacks;
            float latitude = (float) (-Math.PI * 0.5
                    + Math.PI * 0.5 * progress);
            putCapsuleRing(positions, normals, ring++, columns, divisions,
                    radius, -cylinderHalfHeight, latitude);
        }
        for (int stack = 0; stack <= hemisphereStacks; stack++) {
            float progress = stack / (float) hemisphereStacks;
            float latitude = (float) (Math.PI * 0.5 * progress);
            putCapsuleRing(positions, normals, ring++, columns, divisions,
                    radius, cylinderHalfHeight, latitude);
        }

        int[] indices = new int[(ringCount - 1) * divisions * 6];
        int index = 0;
        for (int row = 0; row < ringCount - 1; row++) {
            for (int slice = 0; slice < divisions; slice++) {
                int a = row * columns + slice;
                int b = a + 1;
                int c = a + columns;
                int d = c + 1;
                indices[index++] = a;
                indices[index++] = c;
                indices[index++] = b;
                indices[index++] = b;
                indices[index++] = c;
                indices[index++] = d;
            }
        }
        return triangles(id, positions, indices, null, normals, usage);
    }

    /**
     * Builds a horizontal XZ plane with the default vertex usages.
     *
     * @param width the size along the X axis
     * @param depth the size along the Z axis
     * @return the plane
     */
    public Model plane(float width, float depth) {
        return plane("plane", width, depth, ModelVertexUsage.DEFAULT);
    }

    /**
     * Builds a horizontal XZ plane with the requested vertex usages.
     *
     * @param width the size along the X axis
     * @param depth the size along the Z axis
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the plane
     */
    public Model plane(float width, float depth, long usage) {
        return plane("plane", width, depth, usage);
    }

    /**
     * Builds a named horizontal XZ plane with the requested vertex usages.
     *
     * @param id the identifier
     * @param width the size along the X axis
     * @param depth the size along the Z axis
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the plane
     */
    public Model plane(String id, float width, float depth, long usage) {
        return shapeModel(id, shapes().appendPlane(width, depth).finishShapes(), usage);
    }

    /**
     * Builds a Y-axis torus with the default vertex usages.
     *
     * @param majorRadius the distance from the origin to the tube center
     * @param minorRadius the tube radius
     * @param divisions the divisions around the major ring
     * @return the torus
     */
    public Model torus(float majorRadius, float minorRadius, int divisions) {
        return torus("torus", majorRadius, minorRadius, divisions,
                ModelVertexUsage.DEFAULT);
    }

    /**
     * Builds a Y-axis torus with the requested vertex usages.
     *
     * @param majorRadius the distance from the origin to the tube center
     * @param minorRadius the tube radius
     * @param divisions the divisions around the major ring
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the torus
     */
    public Model torus(float majorRadius, float minorRadius, int divisions,
            long usage) {
        return torus("torus", majorRadius, minorRadius, divisions, usage);
    }

    /**
     * Builds a named Y-axis torus with the requested vertex usages.
     *
     * @param id the identifier
     * @param majorRadius the distance from the origin to the tube center
     * @param minorRadius the tube radius
     * @param divisions the divisions around the major ring
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the torus
     */
    public Model torus(String id, float majorRadius, float minorRadius,
            int divisions, long usage) {
        validateRoundPrimitive(majorRadius, divisions, "Torus");
        if (minorRadius <= 0.0f) {
            throw new FdxException(
                    "Torus minor radius must be greater than zero");
        }
        int tubeDivisions = Math.max(3, divisions / 2);
        int columns = tubeDivisions + 1;
        int rows = divisions + 1;
        float[] positions = new float[rows * columns * 3];
        float[] normals = new float[rows * columns * 3];
        for (int ring = 0; ring <= divisions; ring++) {
            float majorAngle = fullCircle(ring, divisions);
            float majorCos = (float) Math.cos(majorAngle);
            float majorSin = (float) Math.sin(majorAngle);
            for (int tube = 0; tube <= tubeDivisions; tube++) {
                float minorAngle = fullCircle(tube, tubeDivisions);
                float minorCos = (float) Math.cos(minorAngle);
                float minorSin = (float) Math.sin(minorAngle);
                float radial = majorRadius + minorRadius * minorCos;
                putVertex(positions, normals, ring * columns + tube,
                        majorCos * radial,
                        minorRadius * minorSin,
                        majorSin * radial,
                        majorCos * minorCos,
                        minorSin,
                        majorSin * minorCos);
            }
        }

        int[] indices = new int[divisions * tubeDivisions * 6];
        int index = 0;
        for (int ring = 0; ring < divisions; ring++) {
            for (int tube = 0; tube < tubeDivisions; tube++) {
                int a = ring * columns + tube;
                int b = a + 1;
                int c = a + columns;
                int d = c + 1;
                indices[index++] = a;
                indices[index++] = b;
                indices[index++] = c;
                indices[index++] = b;
                indices[index++] = d;
                indices[index++] = c;
            }
        }
        return triangles(id, positions, indices, null, normals, usage);
    }

    /**
     * Runs the triangles step.
     *
     * @param id the identifier
     * @param positions the positions
     * @param indices the indices
     * @param colors the colors
     * @return the triangles
     */
    public Model triangles(String id, float[] positions, int[] indices, float[] colors) {
        return triangles(id, positions, indices, colors,
                ModelVertexUsage.DEFAULT);
    }

    /**
     * Builds triangles with the requested vertex usages.
     *
     * <p>When normals are requested, this overload generates one flat normal
     * from each triangle's winding.</p>
     *
     * @param id the identifier
     * @param positions the positions
     * @param indices the indices
     * @param colors the colors
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the triangles
     */
    public Model triangles(String id, float[] positions, int[] indices,
            float[] colors, long usage) {
        return triangles(id, positions, indices, colors, null, usage);
    }

    /**
     * Builds triangles with explicit source normals and the default color
     * usage.
     *
     * @param id the identifier
     * @param positions the positions
     * @param indices the indices
     * @param colors the colors
     * @param normals the source normals
     * @return the triangles
     */
    public Model triangles(String id, float[] positions, int[] indices,
            float[] colors, float[] normals) {
        return triangles(id, positions, indices, colors, normals,
                ModelVertexUsage.DEFAULT | ModelVertexUsage.NORMAL);
    }

    /**
     * Builds triangles with optional explicit source normals and the requested
     * vertex usages.
     *
     * <p>Explicit normals are expanded through {@code indices}, preserving
     * smooth normals supplied for shared source vertices. When normals are
     * requested and {@code normals} is {@code null}, one flat normal is
     * generated from each triangle's winding.</p>
     *
     * @param id the identifier
     * @param positions the positions
     * @param indices the indices
     * @param colors the colors
     * @param normals the source normals, or {@code null} to generate flat normals
     * @param usage the requested {@link ModelVertexUsage} bits
     * @return the triangles
     */
    public Model triangles(String id, float[] positions, int[] indices,
            float[] colors, float[] normals, long usage) {
        validateUsage(usage);
        if (positions == null || positions.length == 0 || positions.length % 3 != 0) {
            throw new FdxException("Triangle positions must be xyz triples");
        }
        boolean includeColors = hasUsage(usage, ModelVertexUsage.COLOR);
        boolean includeNormals = hasUsage(usage, ModelVertexUsage.NORMAL);
        TriangleVertices vertices = triangleVertices(positions, indices,
                includeColors ? colors : null,
                includeNormals ? normals : null, includeNormals, Color.WHITE);
        Mesh mesh = createMesh(id, vertices, usage, bounds(positions));
        MeshPart meshPart = new MeshPart(id + " part", mesh, null, 0, mesh.vertexCount());
        return DefaultModel.singleNode(id, meshPart, material);
    }

    static TriangleVertices triangleVertices(float[] positions, int[] indices, float[] colors, Color fallbackColor) {
        return triangleVertices(positions, indices, colors, null, false,
                fallbackColor);
    }

    private static TriangleVertices triangleVertices(float[] positions,
            int[] indices, float[] colors, float[] normals,
            boolean includeNormals, Color fallbackColor) {
        int sourceVertexCount = positions.length / 3;
        if (includeNormals && normals != null
                && normals.length != sourceVertexCount * 3) {
            throw new FdxException("Vertex normals must be xyz values per vertex");
        }
        int[] triangleIndices = indices != null ? indices.clone() : sequence(sourceVertexCount);
        if (triangleIndices.length == 0 || triangleIndices.length % 3 != 0) {
            throw new FdxException("Triangle index count must be a positive multiple of three");
        }
        float[] expandedPositions = new float[triangleIndices.length * 3];
        float[] expandedColors = new float[triangleIndices.length * 4];
        float[] expandedNormals = includeNormals
                ? new float[triangleIndices.length * 3] : null;
        int positionOut = 0;
        int colorOut = 0;
        int normalOut = 0;
        for (int i = 0; i < triangleIndices.length; i++) {
            int index = triangleIndices[i];
            validateIndex(index, sourceVertexCount);
            int positionOffset = index * 3;
            expandedPositions[positionOut++] = positions[positionOffset];
            expandedPositions[positionOut++] = positions[positionOffset + 1];
            expandedPositions[positionOut++] = positions[positionOffset + 2];
            colorOut = appendColor(expandedColors, colorOut, positions.length / 3, colors, fallbackColor, index);
            if (expandedNormals != null && normals != null) {
                int normalOffset = index * 3;
                expandedNormals[normalOut++] = normals[normalOffset];
                expandedNormals[normalOut++] = normals[normalOffset + 1];
                expandedNormals[normalOut++] = normals[normalOffset + 2];
            }
        }
        if (expandedNormals != null && normals == null) {
            generateFlatNormals(expandedPositions, expandedNormals);
        }
        return new TriangleVertices(expandedPositions, expandedColors,
                expandedNormals);
    }

    private static int appendColor(float[] expandedColors, int out, int vertexCount, float[] colors,
            Color fallbackColor, int index) {
        int colorComponents = colorComponentCount(colors, vertexCount);
        if (colorComponents > 0) {
            int colorOffset = index * colorComponents;
            expandedColors[out++] = colors[colorOffset];
            expandedColors[out++] = colors[colorOffset + 1];
            expandedColors[out++] = colors[colorOffset + 2];
            expandedColors[out++] = colorComponents > 3 ? colors[colorOffset + 3] : 1.0f;
        }
        else {
            Color color = fallbackColor != null ? fallbackColor : Color.WHITE;
            expandedColors[out++] = color.red();
            expandedColors[out++] = color.green();
            expandedColors[out++] = color.blue();
            expandedColors[out++] = color.alpha();
        }
        return out;
    }

    private static int colorComponentCount(float[] colors, int vertexCount) {
        if (colors == null || colors.length == 0) {
            return 0;
        }
        if (colors.length == vertexCount * 4) {
            return 4;
        }
        if (colors.length == vertexCount * 3) {
            return 3;
        }
        throw new FdxException("Vertex colors must be rgb or rgba values per vertex");
    }

    private Mesh createMesh(String id, TriangleVertices vertices, long usage,
            BoundingBox meshBounds) {
        requireGraphics();
        boolean includeColors = hasUsage(usage, ModelVertexUsage.COLOR);
        boolean includeNormals = hasUsage(usage, ModelVertexUsage.NORMAL);
        if (hasUsage(usage, ModelVertexUsage.PBR_LAYOUT)) {
            return createPbrMesh(id, vertices, meshBounds, includeColors);
        }
        if (includeNormals) {
            if (includeColors) {
                return Mesh.positionNormalColor3D(graphics, id,
                        vertices.positions, vertices.colors, vertices.normals,
                        meshBounds);
            }
            return Mesh.positionNormal3D(graphics, id, vertices.positions,
                    vertices.colors, vertices.normals, meshBounds);
        }
        if (includeColors) {
            return Mesh.positionColor3D(graphics, id, vertices.positions,
                    vertices.colors, meshBounds);
        }
        return Mesh.position3D(graphics, id, vertices.positions,
                vertices.colors, meshBounds);
    }

    private Mesh createPbrMesh(String id, TriangleVertices vertices,
            BoundingBox meshBounds, boolean includeColors) {
        int vertexCount = vertices.positions.length / 3;
        float[] colors = includeColors ? vertices.colors : null;
        float[] textureCoordinates = new float[vertexCount * 2];
        float[] pbr = new float[vertexCount * 3];
        float[] emissive = new float[vertexCount * 3];
        Color emissiveFactor = MaterialAttributes.emissiveColor(material);
        float metallic = clamp(PbrAttributes.metallicFactor(material),
                0.0f, 1.0f);
        float roughness = clamp(PbrAttributes.roughnessFactor(material),
                0.04f, 1.0f);
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int pbrOffset = vertex * 3;
            pbr[pbrOffset] = 1.0f;
            pbr[pbrOffset + 1] = metallic;
            pbr[pbrOffset + 2] = roughness;
            emissive[pbrOffset] = emissiveFactor.red();
            emissive[pbrOffset + 1] = emissiveFactor.green();
            emissive[pbrOffset + 2] = emissiveFactor.blue();
        }
        generateSphericalTextureCoordinates(vertices.positions,
                textureCoordinates);
        return Mesh.positionColor3D(graphics, id, vertices.positions,
                colors, colors, vertices.normals, textureCoordinates,
                pbr, pbr, emissive, emissive, null, null, meshBounds, true);
    }

    /**
     * Fills the PBR texture-coordinate channel with an object-space spherical
     * projection.
     *
     * <p>The channel has always been allocated and handed to the mesh, but
     * never written, so every vertex of a generated primitive sampled texel
     * (0, 0) and any base-colour texture came out a single flat colour. A
     * spherical projection is exact for a sphere - the shape these builders are
     * most often textured on - and remains continuous for the rounded
     * primitives.</p>
     *
     * <p>Longitude wraps, so a triangle straddling the seam would otherwise
     * interpolate u backwards from 0.99 to 0.01 and smear the whole texture
     * across it. Vertices are triangle soup rather than indexed, so the seam is
     * repaired per triangle by pushing the trailing corners past 1 instead.</p>
     */
    static void generateSphericalTextureCoordinates(float[] positions,
            float[] textureCoordinates) {
        int vertexCount = positions.length / 3;
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int p = vertex * 3;
            float x = positions[p];
            float y = positions[p + 1];
            float z = positions[p + 2];
            float length = (float)Math.sqrt(x * x + y * y + z * z);
            int t = vertex * 2;
            if (length == 0.0f) {
                textureCoordinates[t] = 0.0f;
                textureCoordinates[t + 1] = 0.0f;
                continue;
            }
            // East-positive longitude in a right-handed Y-up frame points toward -Z.
            textureCoordinates[t] = (float)(Math.atan2(-z, x)
                    / (2.0 * Math.PI)) + 0.5f;
            textureCoordinates[t + 1] = (float)(Math.acos(
                    Math.max(-1.0, Math.min(1.0, y / length))) / Math.PI);
        }
        for (int triangle = 0; triangle + 2 < vertexCount; triangle += 3) {
            int a = triangle * 2;
            int b = a + 2;
            int c = a + 4;
            float ua = textureCoordinates[a];
            float ub = textureCoordinates[b];
            float uc = textureCoordinates[c];
            float minimum = Math.min(ua, Math.min(ub, uc));
            if (Math.max(ua, Math.max(ub, uc)) - minimum <= 0.5f) {
                continue;
            }
            if (ua - minimum > 0.5f) {
                textureCoordinates[a] = ua;
            }
            else {
                textureCoordinates[a] = ua + 1.0f;
            }
            textureCoordinates[b] = ub - minimum > 0.5f ? ub : ub + 1.0f;
            textureCoordinates[c] = uc - minimum > 0.5f ? uc : uc + 1.0f;
        }
    }

    private static void generateFlatNormals(float[] positions,
            float[] normals) {
        for (int i = 0; i < positions.length; i += 9) {
            float ax = positions[i + 3] - positions[i];
            float ay = positions[i + 4] - positions[i + 1];
            float az = positions[i + 5] - positions[i + 2];
            float bx = positions[i + 6] - positions[i];
            float by = positions[i + 7] - positions[i + 1];
            float bz = positions[i + 8] - positions[i + 2];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float lengthSquared = nx * nx + ny * ny + nz * nz;
            if (lengthSquared > 0.0f && Float.isFinite(lengthSquared)) {
                float inverseLength = 1.0f / (float)Math.sqrt(lengthSquared);
                nx *= inverseLength;
                ny *= inverseLength;
                nz *= inverseLength;
            }
            else {
                nx = 0.0f;
                ny = 0.0f;
                nz = 0.0f;
            }
            for (int vertex = 0; vertex < 3; vertex++) {
                int normalOffset = i + vertex * 3;
                normals[normalOffset] = nx;
                normals[normalOffset + 1] = ny;
                normals[normalOffset + 2] = nz;
            }
        }
    }

    private void requireGraphics() {
        if(graphics == null) throw new FdxException("CPU ModelBuilder: use append methods and finishShapes; GPU models require a graphics context");
    }

    private static boolean hasUsage(long usage, long expected) {
        return (usage & expected) == expected;
    }

    private static void validateUsage(long usage) {
        long supported = ModelVertexUsage.ALL | ModelVertexUsage.PBR_LAYOUT;
        long unknown = usage & ~supported;
        if (unknown != 0L) {
            throw new FdxException("Unsupported model vertex usage bits: "
                    + unknown);
        }
        if (!hasUsage(usage, ModelVertexUsage.POSITION)) {
            throw new FdxException("Model vertex usage must include POSITION");
        }
        if (hasUsage(usage, ModelVertexUsage.PBR_LAYOUT)
                && !hasUsage(usage, ModelVertexUsage.NORMAL)) {
            throw new FdxException(
                    "PBR_LAYOUT requires NORMAL vertex usage");
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void validateRoundPrimitive(float radius, int divisions,
            String shape) {
        if (radius <= 0.0f) {
            throw new FdxException(
                    shape + " radius must be greater than zero");
        }
        if (divisions < 3) {
            throw new FdxException(shape + " divisions must be >= 3");
        }
    }

    private static float fullCircle(int division, int divisionCount) {
        return (float) (Math.PI * 2.0 * division / divisionCount);
    }

    private static void putCapsuleRing(float[] positions, float[] normals,
            int ring, int columns, int divisions, float radius,
            float centerY, float latitude) {
        float normalY = (float) Math.sin(latitude);
        float radialNormal = (float) Math.cos(latitude);
        float y = centerY + normalY * radius;
        for (int slice = 0; slice <= divisions; slice++) {
            float angle = fullCircle(slice, divisions);
            float normalX = (float) Math.cos(angle) * radialNormal;
            float normalZ = (float) Math.sin(angle) * radialNormal;
            putVertex(positions, normals, ring * columns + slice,
                    normalX * radius, y, normalZ * radius,
                    normalX, normalY, normalZ);
        }
    }

    private static void putVertex(float[] positions, float[] normals,
            int vertex, float x, float y, float z,
            float normalX, float normalY, float normalZ) {
        int offset = vertex * 3;
        positions[offset] = x;
        positions[offset + 1] = y;
        positions[offset + 2] = z;
        normals[offset] = normalX;
        normals[offset + 1] = normalY;
        normals[offset + 2] = normalZ;
    }

    private static int[] sequence(int count) {
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) {
            indices[i] = i;
        }
        return indices;
    }

    private static void validateIndex(int index, int vertexCount) {
        if (index < 0 || index >= vertexCount) {
            throw new FdxException("Triangle index out of range: " + index);
        }
    }

    private static BoundingBox bounds(float[] positions) {
        float minX = positions[0];
        float minY = positions[1];
        float minZ = positions[2];
        float maxX = minX;
        float maxY = minY;
        float maxZ = minZ;
        for (int i = 3; i < positions.length; i += 3) {
            minX = Math.min(minX, positions[i]);
            minY = Math.min(minY, positions[i + 1]);
            minZ = Math.min(minZ, positions[i + 2]);
            maxX = Math.max(maxX, positions[i]);
            maxY = Math.max(maxY, positions[i + 1]);
            maxZ = Math.max(maxZ, positions[i + 2]);
        }
        return BoundingBox.of(new Vector3(minX, minY, minZ), new Vector3(maxX, maxY, maxZ));
    }




    /**
     * Represents a triangle vertices.
     *
     * @author xpenatan
     */
    static final class TriangleVertices {
        private final float[] positions;
        private final float[] colors;
        private final float[] normals;

        TriangleVertices(float[] positions, float[] colors,
                float[] normals) {
            this.positions = positions;
            this.colors = colors;
            this.normals = normals;
        }
    }
}
