package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.FloatArray;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Vector3;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Mesh;


/**
 * Builds model instances and related output.
 *
 * @author xpenatan
 */
public final class ModelBuilder {
    private final GraphicsContext graphics;
    private Material material = new Material("default");

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
        validateUsage(usage);
        if (width <= 0.0f || height <= 0.0f || depth <= 0.0f) {
            throw new FdxException("Box dimensions must be greater than zero");
        }
        float hx = width * 0.5f;
        float hy = height * 0.5f;
        float hz = depth * 0.5f;
        FloatArray positions = new FloatArray();
        FloatArray colors = hasUsage(usage, ModelVertexUsage.COLOR)
                ? new FloatArray() : null;
        addFace(positions, colors,
                -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz, hx, -hy, -hz,
                1.0f, 1.0f, 1.0f, 1.0f);
        addFace(positions, colors,
                -hx, -hy, hz, hx, -hy, hz, hx, hy, hz, -hx, hy, hz,
                1.0f, 1.0f, 1.0f, 1.0f);
        addFace(positions, colors,
                -hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz,
                1.0f, 1.0f, 1.0f, 1.0f);
        addFace(positions, colors,
                -hx, hy, -hz, -hx, hy, hz, hx, hy, hz, hx, hy, -hz,
                1.0f, 1.0f, 1.0f, 1.0f);
        addFace(positions, colors,
                hx, -hy, -hz, hx, hy, -hz, hx, hy, hz, hx, -hy, hz,
                1.0f, 1.0f, 1.0f, 1.0f);
        addFace(positions, colors,
                -hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz,
                1.0f, 1.0f, 1.0f, 1.0f);
        return triangles(id, positions.toArray(), null,
                colors != null ? colors.toArray() : null, usage);
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
        validateRoundPrimitive(radius, divisions, "Cylinder");
        if (height <= 0.0f) {
            throw new FdxException(
                    "Cylinder height must be greater than zero");
        }
        int columns = divisions + 1;
        int sideLowerStart = 0;
        int sideUpperStart = columns;
        int bottomCenter = columns * 2;
        int bottomRingStart = bottomCenter + 1;
        int topCenter = bottomRingStart + columns;
        int topRingStart = topCenter + 1;
        int vertexCount = topRingStart + columns;
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float halfHeight = height * 0.5f;
        for (int slice = 0; slice <= divisions; slice++) {
            float angle = fullCircle(slice, divisions);
            float normalX = (float) Math.cos(angle);
            float normalZ = (float) Math.sin(angle);
            float x = normalX * radius;
            float z = normalZ * radius;
            putVertex(positions, normals, sideLowerStart + slice,
                    x, -halfHeight, z, normalX, 0.0f, normalZ);
            putVertex(positions, normals, sideUpperStart + slice,
                    x, halfHeight, z, normalX, 0.0f, normalZ);
            putVertex(positions, normals, bottomRingStart + slice,
                    x, -halfHeight, z, 0.0f, -1.0f, 0.0f);
            putVertex(positions, normals, topRingStart + slice,
                    x, halfHeight, z, 0.0f, 1.0f, 0.0f);
        }
        putVertex(positions, normals, bottomCenter,
                0.0f, -halfHeight, 0.0f, 0.0f, -1.0f, 0.0f);
        putVertex(positions, normals, topCenter,
                0.0f, halfHeight, 0.0f, 0.0f, 1.0f, 0.0f);

        int[] indices = new int[divisions * 12];
        int index = 0;
        for (int slice = 0; slice < divisions; slice++) {
            int lower = sideLowerStart + slice;
            int nextLower = lower + 1;
            int upper = sideUpperStart + slice;
            int nextUpper = upper + 1;
            indices[index++] = lower;
            indices[index++] = upper;
            indices[index++] = nextLower;
            indices[index++] = upper;
            indices[index++] = nextUpper;
            indices[index++] = nextLower;
            indices[index++] = bottomCenter;
            indices[index++] = bottomRingStart + slice;
            indices[index++] = bottomRingStart + slice + 1;
            indices[index++] = topCenter;
            indices[index++] = topRingStart + slice + 1;
            indices[index++] = topRingStart + slice;
        }
        return triangles(id, positions, indices, null, normals, usage);
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
        if (width <= 0.0f || depth <= 0.0f) {
            throw new FdxException(
                    "Plane dimensions must be greater than zero");
        }
        float halfWidth = width * 0.5f;
        float halfDepth = depth * 0.5f;
        float[] positions = {
                -halfWidth, 0.0f, -halfDepth,
                -halfWidth, 0.0f, halfDepth,
                halfWidth, 0.0f, halfDepth,
                halfWidth, 0.0f, -halfDepth
        };
        float[] normals = {
                0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f
        };
        int[] indices = {0, 1, 2, 0, 2, 3};
        return triangles(id, positions, indices, null, normals, usage);
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
        boolean includeColors = hasUsage(usage, ModelVertexUsage.COLOR);
        boolean includeNormals = hasUsage(usage, ModelVertexUsage.NORMAL);
        if (hasUsage(usage, ModelVertexUsage.PBR_LAYOUT)) {
            return createPbrMesh(id, vertices, meshBounds);
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
            BoundingBox meshBounds) {
        int vertexCount = vertices.positions.length / 3;
        float[] colors = new float[vertexCount * 4];
        float[] textureCoordinates = new float[vertexCount * 2];
        float[] pbr = new float[vertexCount * 3];
        float[] emissive = new float[vertexCount * 3];
        Color baseColor = MaterialAttributes.baseColor(material);
        Color emissiveFactor = MaterialAttributes.emissiveColor(material);
        float metallic = clamp(PbrAttributes.metallicFactor(material),
                0.0f, 1.0f);
        float roughness = clamp(PbrAttributes.roughnessFactor(material),
                0.04f, 1.0f);
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int colorOffset = vertex * 4;
            colors[colorOffset] = vertices.colors[colorOffset]
                    * baseColor.red();
            colors[colorOffset + 1] = vertices.colors[colorOffset + 1]
                    * baseColor.green();
            colors[colorOffset + 2] = vertices.colors[colorOffset + 2]
                    * baseColor.blue();
            colors[colorOffset + 3] = vertices.colors[colorOffset + 3]
                    * baseColor.alpha();
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
            textureCoordinates[t] = (float)(Math.atan2(z, x)
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
                && (!hasUsage(usage, ModelVertexUsage.COLOR)
                || !hasUsage(usage, ModelVertexUsage.NORMAL))) {
            throw new FdxException(
                    "PBR_LAYOUT requires COLOR and NORMAL vertex usages");
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

    private static void addFace(FloatArray positions, FloatArray colors,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3,
            float red, float green, float blue, float alpha) {
        addTriangle(positions, colors, x0, y0, z0, x1, y1, z1, x2, y2, z2, red, green, blue, alpha);
        addTriangle(positions, colors, x0, y0, z0, x2, y2, z2, x3, y3, z3, red, green, blue, alpha);
    }

    private static void addTriangle(FloatArray positions, FloatArray colors,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float red, float green, float blue, float alpha) {
        addVertex(positions, colors, x0, y0, z0, red, green, blue, alpha);
        addVertex(positions, colors, x1, y1, z1, red, green, blue, alpha);
        addVertex(positions, colors, x2, y2, z2, red, green, blue, alpha);
    }

    private static void addVertex(FloatArray positions, FloatArray colors,
            float x, float y, float z, float red, float green, float blue, float alpha) {
        positions.add(x);
        positions.add(y);
        positions.add(z);
        if (colors != null) {
            colors.add(red);
            colors.add(green);
            colors.add(blue);
            colors.add(alpha);
        }
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
