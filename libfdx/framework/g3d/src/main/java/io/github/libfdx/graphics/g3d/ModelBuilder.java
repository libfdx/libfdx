package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Vector3;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Mesh;

import java.util.ArrayList;

/**
 * Builds model instances and related output.
 *
 * @author xpenatan
 */
public final class ModelBuilder {
    private final GraphicsContext graphics;
    private Material material = new PbrMaterial("default");

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
        return cube("cube", size);
    }

    /**
     * Runs the cube step.
     *
     * @param id the identifier
     * @param size the size
     * @return the cube
     */
    public Model cube(String id, float size) {
        return box(id, size, size, size);
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
        return box("box", width, height, depth);
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
        if (width <= 0.0f || height <= 0.0f || depth <= 0.0f) {
            throw new FdxException("Box dimensions must be greater than zero");
        }
        float hx = width * 0.5f;
        float hy = height * 0.5f;
        float hz = depth * 0.5f;
        ArrayList<Float> positions = new ArrayList<Float>();
        ArrayList<Float> colors = new ArrayList<Float>();
        addFace(positions, colors,
                -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz, hx, -hy, -hz,
                0.30f, 0.42f, 0.75f, 1.0f);
        addFace(positions, colors,
                -hx, -hy, hz, hx, -hy, hz, hx, hy, hz, -hx, hy, hz,
                0.91f, 0.44f, 0.36f, 1.0f);
        addFace(positions, colors,
                -hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz,
                0.20f, 0.58f, 0.45f, 1.0f);
        addFace(positions, colors,
                -hx, hy, -hz, -hx, hy, hz, hx, hy, hz, hx, hy, -hz,
                0.95f, 0.76f, 0.28f, 1.0f);
        addFace(positions, colors,
                hx, -hy, -hz, hx, hy, -hz, hx, hy, hz, hx, -hy, hz,
                0.47f, 0.35f, 0.79f, 1.0f);
        addFace(positions, colors,
                -hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz,
                0.24f, 0.68f, 0.87f, 1.0f);
        return triangles(id, toFloatArray(positions), null, toFloatArray(colors));
    }

    /**
     * Runs the sphere step.
     *
     * @param radius the radius
     * @param divisions the divisions
     * @return the sphere
     */
    public Model sphere(float radius, int divisions) {
        return sphere("sphere", radius, divisions, Math.max(2, divisions / 2));
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
        if (radius <= 0.0f) {
            throw new FdxException("Sphere radius must be greater than zero");
        }
        if (slices < 3 || stacks < 2) {
            throw new FdxException("Sphere slices must be >= 3 and stacks must be >= 2");
        }
        int vertexColumns = slices + 1;
        int vertexRows = stacks + 1;
        float[] positions = new float[vertexColumns * vertexRows * 3];
        float[] colors = new float[vertexColumns * vertexRows * 4];
        int p = 0;
        int c = 0;
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
                colors[c++] = 0.35f + 0.45f * (nx * 0.5f + 0.5f);
                colors[c++] = 0.45f + 0.40f * (ny * 0.5f + 0.5f);
                colors[c++] = 0.55f + 0.35f * (nz * 0.5f + 0.5f);
                colors[c++] = 1.0f;
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
        return triangles(id, positions, indices, colors);
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
        if (positions == null || positions.length == 0 || positions.length % 3 != 0) {
            throw new FdxException("Triangle positions must be xyz triples");
        }
        TriangleVertices vertices = triangleVertices(positions, indices, colors, Color.WHITE);
        Mesh mesh = Mesh.positionColor3D(graphics, id, vertices.positions, vertices.colors,
                bounds(positions));
        MeshPart meshPart = new MeshPart(id + " part", mesh, null, 0, mesh.vertexCount());
        return DefaultModel.singleNode(id, meshPart, material);
    }

    static TriangleVertices triangleVertices(float[] positions, int[] indices, float[] colors, Color fallbackColor) {
        int sourceVertexCount = positions.length / 3;
        int[] triangleIndices = indices != null ? indices.clone() : sequence(sourceVertexCount);
        if (triangleIndices.length == 0 || triangleIndices.length % 3 != 0) {
            throw new FdxException("Triangle index count must be a positive multiple of three");
        }
        float[] expandedPositions = new float[triangleIndices.length * 3];
        float[] expandedColors = new float[triangleIndices.length * 4];
        int positionOut = 0;
        int colorOut = 0;
        for (int i = 0; i < triangleIndices.length; i++) {
            int index = triangleIndices[i];
            validateIndex(index, sourceVertexCount);
            int positionOffset = index * 3;
            expandedPositions[positionOut++] = positions[positionOffset];
            expandedPositions[positionOut++] = positions[positionOffset + 1];
            expandedPositions[positionOut++] = positions[positionOffset + 2];
            colorOut = appendColor(expandedColors, colorOut, positions.length / 3, colors, fallbackColor, index);
        }
        return new TriangleVertices(expandedPositions, expandedColors);
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

    private static void addFace(ArrayList<Float> positions, ArrayList<Float> colors,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3,
            float red, float green, float blue, float alpha) {
        addTriangle(positions, colors, x0, y0, z0, x1, y1, z1, x2, y2, z2, red, green, blue, alpha);
        addTriangle(positions, colors, x0, y0, z0, x2, y2, z2, x3, y3, z3, red, green, blue, alpha);
    }

    private static void addTriangle(ArrayList<Float> positions, ArrayList<Float> colors,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float red, float green, float blue, float alpha) {
        addVertex(positions, colors, x0, y0, z0, red, green, blue, alpha);
        addVertex(positions, colors, x1, y1, z1, red, green, blue, alpha);
        addVertex(positions, colors, x2, y2, z2, red, green, blue, alpha);
    }

    private static void addVertex(ArrayList<Float> positions, ArrayList<Float> colors,
            float x, float y, float z, float red, float green, float blue, float alpha) {
        positions.add(x);
        positions.add(y);
        positions.add(z);
        colors.add(red);
        colors.add(green);
        colors.add(blue);
        colors.add(alpha);
    }

    private static float[] toFloatArray(ArrayList<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    /**
     * Represents a triangle vertices.
     *
     * @author xpenatan
     */
    static final class TriangleVertices {
        private final float[] positions;
        private final float[] colors;

        TriangleVertices(float[] positions, float[] colors) {
            this.positions = positions;
            this.colors = colors;
        }
    }
}
