package io.github.libfdx.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Vector3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Represents a mesh.
 *
 * @author xpenatan
 */
public final class Mesh implements Disposable {
    public static final int POSITION_FLOATS_PER_VERTEX = 3;
    public static final int POSITION_BYTES_PER_VERTEX = POSITION_FLOATS_PER_VERTEX * 4;
    public static final VertexLayout POSITION_LAYOUT = VertexLayout.of(
            POSITION_BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0));
    public static final int POSITION_NORMAL_FLOATS_PER_VERTEX = 6;
    public static final int POSITION_NORMAL_BYTES_PER_VERTEX = POSITION_NORMAL_FLOATS_PER_VERTEX * 4;
    public static final VertexLayout POSITION_NORMAL_LAYOUT = VertexLayout.of(
            POSITION_NORMAL_BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X3, 12));
    public static final int POSITION_NORMAL_COLOR_FLOATS_PER_VERTEX = 10;
    public static final int POSITION_NORMAL_COLOR_BYTES_PER_VERTEX = POSITION_NORMAL_COLOR_FLOATS_PER_VERTEX * 4;
    public static final VertexLayout POSITION_NORMAL_COLOR_LAYOUT = VertexLayout.of(
            POSITION_NORMAL_COLOR_BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X3, 12),
            VertexAttribute.of(3, VertexFormat.FLOAT32X4, 24));
    public static final int POSITION_COLOR_FLOATS_PER_VERTEX = 7;
    public static final int POSITION_COLOR_BYTES_PER_VERTEX = POSITION_COLOR_FLOATS_PER_VERTEX * 4;
    public static final VertexLayout POSITION_COLOR_LAYOUT = VertexLayout.of(
            POSITION_COLOR_BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X4, 12));
    public static final int PBR_FLOATS_PER_VERTEX = 18;
    public static final int PBR_BYTES_PER_VERTEX = PBR_FLOATS_PER_VERTEX * 4;
    public static final VertexLayout PBR_LAYOUT = VertexLayout.of(
            PBR_BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X3, 12),
            VertexAttribute.of(2, VertexFormat.FLOAT32X2, 24),
            VertexAttribute.of(3, VertexFormat.FLOAT32X4, 32),
            VertexAttribute.of(4, VertexFormat.FLOAT32X3, 48),
            VertexAttribute.of(5, VertexFormat.FLOAT32X3, 60));
    public static final int PBR_SKINNED_FLOATS_PER_VERTEX = PBR_FLOATS_PER_VERTEX + 8;
    public static final int PBR_SKINNED_BYTES_PER_VERTEX = PBR_SKINNED_FLOATS_PER_VERTEX * 4;
    public static final VertexLayout PBR_SKINNED_LAYOUT = VertexLayout.of(
            PBR_SKINNED_BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X3, 12),
            VertexAttribute.of(2, VertexFormat.FLOAT32X2, 24),
            VertexAttribute.of(3, VertexFormat.FLOAT32X4, 32),
            VertexAttribute.of(4, VertexFormat.FLOAT32X3, 48),
            VertexAttribute.of(5, VertexFormat.FLOAT32X3, 60),
            VertexAttribute.of(6, VertexFormat.FLOAT32X4, 72),
            VertexAttribute.of(7, VertexFormat.FLOAT32X4, 88));

    private final String id;
    private final VertexLayout vertexLayout;
    private final int vertexCount;
    private final int indexCount;
    private final BoundingBox bounds;
    private final float[] sourcePositions;
    private final float[] sourceColors;
    private final float[] sourceBakedColors;
    private final float[] sourceNormals;
    private final float[] sourceTexCoords;
    private final float[] sourcePbr;
    private final float[] sourceBakedPbr;
    private final float[] sourceEmissive;
    private final float[] sourceBakedEmissive;
    private final int[] sourceJoints;
    private final float[] sourceWeights;
    private Buffer vertexBuffer;
    private Buffer indexBuffer;
    private boolean disposed;

    /**
     * Creates a mesh.
     *
     * @param graphics the graphics context
     * @param id the identifier
     * @param vertexLayout the vertex layout
     * @param vertices the vertices
     * @param vertexCount the vertex count
     */
    public Mesh(GraphicsContext graphics, String id, VertexLayout vertexLayout, float[] vertices, int vertexCount) {
        this(graphics, id, vertexLayout, vertices, vertexCount, null, 0, BoundingBox.empty());
    }

    /**
     * Creates a mesh.
     *
     * @param graphics the graphics context
     * @param id the identifier
     * @param vertexLayout the vertex layout
     * @param vertices the vertices
     * @param vertexCount the vertex count
     * @param bounds the bounds
     */
    public Mesh(GraphicsContext graphics, String id, VertexLayout vertexLayout, float[] vertices, int vertexCount,
            BoundingBox bounds) {
        this(graphics, id, vertexLayout, vertices, vertexCount, null, 0, bounds);
    }

    /**
     * Creates a mesh.
     *
     * @param graphics the graphics context
     * @param id the identifier
     * @param vertexLayout the vertex layout
     * @param vertices the vertices
     * @param vertexCount the vertex count
     * @param indices the indices
     * @param indexCount the index count
     * @param bounds the bounds
     */
    public Mesh(GraphicsContext graphics, String id, VertexLayout vertexLayout, float[] vertices, int vertexCount,
            short[] indices, int indexCount, BoundingBox bounds) {
        this(graphics, id, vertexLayout, vertices, vertexCount, indices, indexCount, bounds, null, null, null,
                null, null, null, null, null, null, null, null, false);
    }

    private Mesh(GraphicsContext graphics, String id, VertexLayout vertexLayout, float[] vertices, int vertexCount,
            short[] indices, int indexCount, BoundingBox bounds, float[] sourcePositions, float[] sourceColors,
            float[] sourceBakedColors, float[] sourceNormals, float[] sourceTexCoords, float[] sourcePbr,
            float[] sourceBakedPbr, float[] sourceEmissive, float[] sourceBakedEmissive, int[] sourceJoints,
            float[] sourceWeights, boolean retainSourceData) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        if (vertexLayout == null) {
            throw new FdxException("Mesh vertex layout cannot be null");
        }
        if (vertices == null || vertices.length == 0) {
            throw new FdxException("Mesh vertices cannot be empty");
        }
        if (vertexCount <= 0) {
            throw new FdxException("Mesh vertex count must be greater than zero");
        }
        if (indexCount < 0) {
            throw new FdxException("Mesh index count cannot be negative");
        }
        int vertexByteCount = vertexCount * vertexLayout.arrayStride();
        validateFloatVertexData(vertices, vertexByteCount);
        this.id = id != null ? id : "";
        this.vertexLayout = vertexLayout;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.bounds = bounds != null ? bounds : BoundingBox.empty();
        this.sourcePositions = retainSourceData && sourcePositions != null ? sourcePositions.clone() : null;
        this.sourceColors = retainSourceData && sourceColors != null ? sourceColors.clone() : null;
        this.sourceBakedColors = retainSourceData && sourceBakedColors != null ? sourceBakedColors.clone() : null;
        this.sourceNormals = retainSourceData && sourceNormals != null ? sourceNormals.clone() : null;
        this.sourceTexCoords = retainSourceData && sourceTexCoords != null ? sourceTexCoords.clone() : null;
        this.sourcePbr = retainSourceData && sourcePbr != null ? sourcePbr.clone() : null;
        this.sourceBakedPbr = retainSourceData && sourceBakedPbr != null ? sourceBakedPbr.clone() : null;
        this.sourceEmissive = retainSourceData && sourceEmissive != null ? sourceEmissive.clone() : null;
        this.sourceBakedEmissive = retainSourceData && sourceBakedEmissive != null ? sourceBakedEmissive.clone()
                : null;
        this.sourceJoints = retainSourceData && sourceJoints != null ? sourceJoints.clone() : null;
        this.sourceWeights = retainSourceData && sourceWeights != null ? sourceWeights.clone() : null;
        vertexBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(this.id + " vertices",
                vertexByteCount));
        graphics.device().writeBuffer(vertexBuffer, floats(vertices, vertexByteCount));
        if (indexCount > 0) {
            if (indices == null || indices.length < indexCount) {
                throw new FdxException("Mesh indices cannot be empty when index count is greater than zero");
            }
            int indexByteCount = indexCount * 2;
            indexBuffer = graphics.device().createBuffer(BufferDescriptor.staticIndex(this.id + " indices",
                    indexByteCount));
            graphics.device().writeBuffer(indexBuffer, shorts(indices, indexByteCount));
        }
    }

    /**
     * Creates a mesh.
     *
     * @param graphics the graphics context
     * @param id the identifier
     * @return a new mesh
     */
    public static Mesh coloredTriangle(GraphicsContext graphics, String id) {
        float[] vertices = {
                0.0f, 0.65f, 0.0f, 0.95f, 0.33f, 0.28f, 1.0f,
                -0.65f, -0.55f, 0.0f, 0.18f, 0.67f, 0.95f, 1.0f,
                0.65f, -0.55f, 0.0f, 0.26f, 0.81f, 0.43f, 1.0f
        };
        return new Mesh(graphics, id, POSITION_COLOR_LAYOUT, vertices, 3,
                BoundingBox.of(new Vector3(-0.65f, -0.55f, 0.0f), new Vector3(0.65f, 0.65f, 0.0f)));
    }

    /**
     * Creates a position-only 3D mesh while retaining its source positions and colors.
     *
     * @param graphics the graphics context
     * @param id the identifier
     * @param sourcePositions the source positions
     * @param sourceColors the source colors retained for CPU fallback rendering
     * @param bounds the bounds
     * @return a new mesh
     */
    public static Mesh position3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, BoundingBox bounds) {
        return packedStatic3D(graphics, id, POSITION_LAYOUT, sourcePositions, sourceColors, null, bounds);
    }

    /**
     * Creates a position/normal 3D mesh while retaining its source positions, colors, and normals.
     *
     * @param graphics the graphics context
     * @param id the identifier
     * @param sourcePositions the source positions
     * @param sourceColors the source colors retained for CPU fallback rendering
     * @param sourceNormals the source normals
     * @param bounds the bounds
     * @return a new mesh
     */
    public static Mesh positionNormal3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, float[] sourceNormals, BoundingBox bounds) {
        return packedStatic3D(graphics, id, POSITION_NORMAL_LAYOUT, sourcePositions, sourceColors, sourceNormals,
                bounds);
    }

    /**
     * Creates a position/normal/color 3D mesh while retaining all supplied source attributes.
     *
     * @param graphics the graphics context
     * @param id the identifier
     * @param sourcePositions the source positions
     * @param sourceColors the source colors
     * @param sourceNormals the source normals
     * @param bounds the bounds
     * @return a new mesh
     */
    public static Mesh positionNormalColor3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, float[] sourceNormals, BoundingBox bounds) {
        return packedStatic3D(graphics, id, POSITION_NORMAL_COLOR_LAYOUT, sourcePositions, sourceColors,
                sourceNormals, bounds);
    }

    /**
     * Creates a mesh.
     *
     * @param graphics the graphics context
     * @param id the identifier
     * @param sourcePositions the source positions
     * @param sourceColors the source colors
     * @param bounds the bounds
     * @return a new mesh
     */
    public static Mesh positionColor3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, BoundingBox bounds) {
        return positionColor3D(graphics, id, sourcePositions, sourceColors, null, null, null, null, bounds);
    }

    /**
     * Creates a mesh.
     *
     * @param graphics the graphics context
     * @param id the identifier
     * @param sourcePositions the source positions
     * @param sourceColors the source colors
     * @param sourceNormals the source normals
     * @param sourceTexCoords the source tex coords
     * @param sourcePbr the source PBR
     * @param sourceEmissive the source emissive
     * @param bounds the bounds
     * @return a new mesh
     */
    public static Mesh positionColor3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, float[] sourceNormals, float[] sourceTexCoords, float[] sourcePbr,
            float[] sourceEmissive, BoundingBox bounds) {
        return positionColor3D(graphics, id, sourcePositions, sourceColors, null, sourceNormals, sourceTexCoords,
                sourcePbr, null, sourceEmissive, null, bounds);
    }

    /**
     * Creates a mesh.
     *
     * @param graphics the graphics context
     * @param id the identifier
     * @param sourcePositions the source positions
     * @param sourceColors the source colors
     * @param sourceBakedColors the source baked colors
     * @param sourceNormals the source normals
     * @param sourceTexCoords the source tex coords
     * @param sourcePbr the source PBR
     * @param sourceBakedPbr the source baked PBR
     * @param sourceEmissive the source emissive
     * @param sourceBakedEmissive the source baked emissive
     * @param bounds the bounds
     * @return a new mesh
     */
    public static Mesh positionColor3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, float[] sourceBakedColors, float[] sourceNormals, float[] sourceTexCoords,
            float[] sourcePbr, float[] sourceBakedPbr, float[] sourceEmissive, float[] sourceBakedEmissive,
            BoundingBox bounds) {
        return positionColor3D(graphics, id, sourcePositions, sourceColors, sourceBakedColors, sourceNormals,
                sourceTexCoords, sourcePbr, sourceBakedPbr, sourceEmissive, sourceBakedEmissive, null, null, bounds,
                true);
    }

    /**
     * Creates a mesh.
     *
     * @param graphics the graphics context
     * @param id the identifier
     * @param sourcePositions the source positions
     * @param sourceColors the source colors
     * @param sourceBakedColors the source baked colors
     * @param sourceNormals the source normals
     * @param sourceTexCoords the source tex coords
     * @param sourcePbr the source PBR
     * @param sourceBakedPbr the source baked PBR
     * @param sourceEmissive the source emissive
     * @param sourceBakedEmissive the source baked emissive
     * @param bounds the bounds
     * @param retainSourceData the retain source data
     * @return a new mesh
     */
    public static Mesh positionColor3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, float[] sourceBakedColors, float[] sourceNormals, float[] sourceTexCoords,
            float[] sourcePbr, float[] sourceBakedPbr, float[] sourceEmissive, float[] sourceBakedEmissive,
            BoundingBox bounds, boolean retainSourceData) {
        return positionColor3D(graphics, id, sourcePositions, sourceColors, sourceBakedColors, sourceNormals,
                sourceTexCoords, sourcePbr, sourceBakedPbr, sourceEmissive, sourceBakedEmissive, null, null, bounds,
                retainSourceData);
    }

    private static Mesh packedStatic3D(GraphicsContext graphics, String id, VertexLayout layout,
            float[] sourcePositions, float[] sourceColors, float[] sourceNormals, BoundingBox bounds) {
        if (sourcePositions == null || sourcePositions.length == 0 || sourcePositions.length % 3 != 0) {
            throw new FdxException("Static 3D meshes require xyz source positions");
        }
        int vertexCount = sourcePositions.length / 3;
        boolean includeNormals = layout == POSITION_NORMAL_LAYOUT || layout == POSITION_NORMAL_COLOR_LAYOUT;
        boolean includeColors = layout == POSITION_NORMAL_COLOR_LAYOUT;
        if (includeNormals && (sourceNormals == null || sourceNormals.length != vertexCount * 3)) {
            throw new FdxException("Static 3D meshes with normals require xyz source normals");
        }
        if (sourceColors == null || sourceColors.length != vertexCount * 4) {
            throw new FdxException("Static 3D meshes require rgba source colors");
        }
        float[] vertices = packStatic3D(sourcePositions, sourceNormals, sourceColors, vertexCount,
                includeNormals, includeColors);
        return new Mesh(graphics, id, layout, vertices, vertexCount, null, 0, bounds,
                sourcePositions, sourceColors, null, sourceNormals, null, null, null, null, null, null, null, true);
    }

    private static float[] packStatic3D(float[] sourcePositions, float[] sourceNormals, float[] sourceColors,
            int vertexCount, boolean includeNormals, boolean includeColors) {
        int floatsPerVertex = POSITION_FLOATS_PER_VERTEX
                + (includeNormals ? 3 : 0)
                + (includeColors ? 4 : 0);
        float[] vertices = new float[vertexCount * floatsPerVertex];
        int out = 0;
        for (int i = 0; i < vertexCount; i++) {
            int positionOffset = i * 3;
            vertices[out++] = sourcePositions[positionOffset];
            vertices[out++] = sourcePositions[positionOffset + 1];
            vertices[out++] = sourcePositions[positionOffset + 2];
            if (includeNormals) {
                int normalOffset = i * 3;
                vertices[out++] = sourceNormals[normalOffset];
                vertices[out++] = sourceNormals[normalOffset + 1];
                vertices[out++] = sourceNormals[normalOffset + 2];
            }
            if (includeColors) {
                int colorOffset = i * 4;
                vertices[out++] = sourceColors[colorOffset];
                vertices[out++] = sourceColors[colorOffset + 1];
                vertices[out++] = sourceColors[colorOffset + 2];
                vertices[out++] = sourceColors[colorOffset + 3];
            }
        }
        return vertices;
    }

    /**
     * Creates a mesh.
     *
     * @param graphics the graphics context
     * @param id the identifier
     * @param sourcePositions the source positions
     * @param sourceColors the source colors
     * @param sourceBakedColors the source baked colors
     * @param sourceNormals the source normals
     * @param sourceTexCoords the source tex coords
     * @param sourcePbr the source PBR
     * @param sourceBakedPbr the source baked PBR
     * @param sourceEmissive the source emissive
     * @param sourceBakedEmissive the source baked emissive
     * @param sourceJoints four joint indices per vertex
     * @param sourceWeights four joint weights per vertex
     * @param bounds the bounds
     * @param retainSourceData the retain source data
     * @return a new mesh
     */
    public static Mesh positionColor3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, float[] sourceBakedColors, float[] sourceNormals, float[] sourceTexCoords,
            float[] sourcePbr, float[] sourceBakedPbr, float[] sourceEmissive, float[] sourceBakedEmissive,
            int[] sourceJoints, float[] sourceWeights, BoundingBox bounds, boolean retainSourceData) {
        if (sourcePositions == null || sourcePositions.length == 0 || sourcePositions.length % 3 != 0) {
            throw new FdxException("3D position/color meshes require xyz source positions");
        }
        int vertexCount = sourcePositions.length / 3;
        if (sourceColors == null || sourceColors.length != vertexCount * 4) {
            throw new FdxException("3D position/color meshes require rgba source colors");
        }
        if (sourceBakedColors != null && sourceBakedColors.length != vertexCount * 4) {
            throw new FdxException("3D position/color meshes require rgba baked source colors");
        }
        if (sourceNormals != null && sourceNormals.length != vertexCount * 3) {
            throw new FdxException("3D position/color meshes require xyz source normals");
        }
        if (sourceTexCoords != null && sourceTexCoords.length != vertexCount * 2) {
            throw new FdxException("3D position/color meshes require uv source texture coordinates");
        }
        if (sourcePbr != null && sourcePbr.length != vertexCount * 3) {
            throw new FdxException("3D position/color meshes require ao/metallic/roughness source values");
        }
        if (sourceBakedPbr != null && sourceBakedPbr.length != vertexCount * 3) {
            throw new FdxException("3D position/color meshes require baked ao/metallic/roughness source values");
        }
        if (sourceEmissive != null && sourceEmissive.length != vertexCount * 3) {
            throw new FdxException("3D position/color meshes require rgb source emissive values");
        }
        if (sourceBakedEmissive != null && sourceBakedEmissive.length != vertexCount * 3) {
            throw new FdxException("3D position/color meshes require baked rgb source emissive values");
        }
        boolean pbrLayout = sourceNormals != null && sourceTexCoords != null && sourcePbr != null
                && sourceEmissive != null;
        boolean hasSkinning = sourceJoints != null || sourceWeights != null;
        if (hasSkinning && !pbrLayout) {
            throw new FdxException("Skinned 3D meshes require retained PBR vertex attributes");
        }
        if (hasSkinning) {
            if (sourceJoints == null || sourceJoints.length != vertexCount * 4) {
                throw new FdxException("Skinned 3D meshes require four joint indices per vertex");
            }
            if (sourceWeights == null || sourceWeights.length != vertexCount * 4) {
                throw new FdxException("Skinned 3D meshes require four joint weights per vertex");
            }
        }
        int floatsPerVertex = hasSkinning ? PBR_SKINNED_FLOATS_PER_VERTEX
                : pbrLayout ? PBR_FLOATS_PER_VERTEX : POSITION_COLOR_FLOATS_PER_VERTEX;
        float[] vertices = new float[vertexCount * floatsPerVertex];
        int out = 0;
        for (int i = 0; i < vertexCount; i++) {
            int positionOffset = i * 3;
            int colorOffset = i * 4;
            vertices[out++] = sourcePositions[positionOffset];
            vertices[out++] = sourcePositions[positionOffset + 1];
            vertices[out++] = sourcePositions[positionOffset + 2];
            if (pbrLayout) {
                int normalOffset = i * 3;
                int texCoordOffset = i * 2;
                vertices[out++] = sourceNormals[normalOffset];
                vertices[out++] = sourceNormals[normalOffset + 1];
                vertices[out++] = sourceNormals[normalOffset + 2];
                vertices[out++] = sourceTexCoords[texCoordOffset];
                vertices[out++] = sourceTexCoords[texCoordOffset + 1];
            }
            vertices[out++] = sourceColors[colorOffset];
            vertices[out++] = sourceColors[colorOffset + 1];
            vertices[out++] = sourceColors[colorOffset + 2];
            vertices[out++] = sourceColors[colorOffset + 3];
            if (pbrLayout) {
                int pbrOffset = i * 3;
                int emissiveOffset = i * 3;
                vertices[out++] = sourcePbr[pbrOffset];
                vertices[out++] = sourcePbr[pbrOffset + 1];
                vertices[out++] = sourcePbr[pbrOffset + 2];
                vertices[out++] = sourceEmissive[emissiveOffset];
                vertices[out++] = sourceEmissive[emissiveOffset + 1];
                vertices[out++] = sourceEmissive[emissiveOffset + 2];
            }
            if (hasSkinning) {
                int influenceOffset = i * 4;
                vertices[out++] = sourceJoints[influenceOffset];
                vertices[out++] = sourceJoints[influenceOffset + 1];
                vertices[out++] = sourceJoints[influenceOffset + 2];
                vertices[out++] = sourceJoints[influenceOffset + 3];
                vertices[out++] = sourceWeights[influenceOffset];
                vertices[out++] = sourceWeights[influenceOffset + 1];
                vertices[out++] = sourceWeights[influenceOffset + 2];
                vertices[out++] = sourceWeights[influenceOffset + 3];
            }
        }
        return new Mesh(graphics, id, hasSkinning ? PBR_SKINNED_LAYOUT : pbrLayout ? PBR_LAYOUT
                : POSITION_COLOR_LAYOUT, vertices, vertexCount,
                null, 0, bounds, sourcePositions, sourceColors, sourceBakedColors, sourceNormals, sourceTexCoords,
                sourcePbr, sourceBakedPbr, sourceEmissive, sourceBakedEmissive, sourceJoints, sourceWeights,
                retainSourceData);
    }

    /**
     * Returns the ID.
     *
     * @return the ID
     */
    public String id() {
        return id;
    }

    /**
     * Returns the vertex buffer.
     *
     * @return the vertex buffer
     */
    public Buffer vertexBuffer() {
        return vertexBuffer;
    }

    /**
     * Returns the index buffer.
     *
     * @return the index buffer
     */
    public Buffer indexBuffer() {
        return indexBuffer;
    }

    /**
     * Returns the vertex layout.
     *
     * @return the vertex layout
     */
    public VertexLayout vertexLayout() {
        return vertexLayout;
    }

    /**
     * Returns the vertex count.
     *
     * @return the vertex count
     */
    public int vertexCount() {
        return vertexCount;
    }

    /**
     * Returns the index count.
     *
     * @return the index count
     */
    public int indexCount() {
        return indexCount;
    }

    /**
     * Returns the bounds.
     *
     * @return the bounds
     */
    public BoundingBox bounds() {
        return bounds;
    }

    /**
     * Returns whether this instance has position color3 d source.
     *
     * @return true if this instance has position color3 d source; false otherwise
     */
    public boolean hasPositionColor3DSource() {
        return sourcePositions != null && sourceColors != null;
    }

    /**
     * Returns the source positions.
     *
     * @return the source positions
     */
    public float[] sourcePositions() {
        return sourcePositions;
    }

    /**
     * Returns the source colors.
     *
     * @return the source colors
     */
    public float[] sourceColors() {
        return sourceColors;
    }

    /**
     * Returns the source baked colors.
     *
     * @return the source baked colors
     */
    public float[] sourceBakedColors() {
        return sourceBakedColors;
    }

    /**
     * Returns the source normals.
     *
     * @return the source normals
     */
    public float[] sourceNormals() {
        return sourceNormals;
    }

    /**
     * Returns the source tex coords.
     *
     * @return the source tex coords
     */
    public float[] sourceTexCoords() {
        return sourceTexCoords;
    }

    /**
     * Returns the source PBR.
     *
     * @return the source PBR
     */
    public float[] sourcePbr() {
        return sourcePbr;
    }

    /**
     * Returns the source baked PBR.
     *
     * @return the source baked PBR
     */
    public float[] sourceBakedPbr() {
        return sourceBakedPbr;
    }

    /**
     * Returns the source emissive.
     *
     * @return the source emissive
     */
    public float[] sourceEmissive() {
        return sourceEmissive;
    }

    /**
     * Returns the source baked emissive.
     *
     * @return the source baked emissive
     */
    public float[] sourceBakedEmissive() {
        return sourceBakedEmissive;
    }

    /**
     * Returns the source joints.
     *
     * @return the source joints
     */
    public int[] sourceJoints() {
        return sourceJoints;
    }

    /**
     * Returns the source weights.
     *
     * @return the source weights
     */
    public float[] sourceWeights() {
        return sourceWeights;
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (indexBuffer != null) {
            indexBuffer.dispose();
            indexBuffer = null;
        }
        if (vertexBuffer != null) {
            vertexBuffer.dispose();
            vertexBuffer = null;
        }
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private static void validateFloatVertexData(float[] vertices, int vertexByteCount) {
        if (vertexByteCount <= 0 || (vertexByteCount & 3) != 0) {
            throw new FdxException("Float mesh vertices require a positive vertex byte count divisible by four");
        }
        int requiredFloats = vertexByteCount / 4;
        if (vertices.length < requiredFloats) {
            throw new FdxException("Mesh vertices do not contain enough data for the vertex layout and count");
        }
    }

    private static ByteBuffer floats(float[] values, int byteCount) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
        buffer.asFloatBuffer().put(values, 0, byteCount / 4);
        buffer.limit(byteCount);
        buffer.position(0);
        return buffer;
    }

    private static ByteBuffer shorts(short[] values, int byteCount) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
        buffer.asShortBuffer().put(values, 0, byteCount / 2);
        buffer.limit(byteCount);
        buffer.position(0);
        return buffer;
    }
}
