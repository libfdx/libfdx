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

    /** PBR layouts with secondary UV (location 8) and tangent XYZW (location 9) appended. */
    public static final VertexLayout PBR_TEXTURED_LAYOUT = texturedLayout(PBR_LAYOUT);
    public static final VertexLayout PBR_TEXTURED_SKINNED_LAYOUT = texturedLayout(PBR_SKINNED_LAYOUT);

    private static VertexLayout texturedLayout(VertexLayout base) {
        VertexAttribute[] attributes = new VertexAttribute[base.attributeCount() + 2];
        for (int i = 0; i < base.attributeCount(); i++) attributes[i] = base.attribute(i);
        attributes[base.attributeCount()] = VertexAttribute.of(8, VertexFormat.FLOAT32X2, base.arrayStride());
        attributes[base.attributeCount()+1] = VertexAttribute.of(9, VertexFormat.FLOAT32X4, base.arrayStride()+8);
        return VertexLayout.of(base.arrayStride()+24, attributes);
    }

    public boolean hasPbrTextureCoordinates() {
        return vertexLayout == PBR_TEXTURED_LAYOUT || vertexLayout == PBR_TEXTURED_SKINNED_LAYOUT;
    }
    public boolean hasPbrSkinning() {
        return vertexLayout == PBR_SKINNED_LAYOUT || vertexLayout == PBR_TEXTURED_SKINNED_LAYOUT;
    }

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
    private final float[] sourceTexCoords1;
    private final float[] sourceTangents;
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
        this(graphics, id, vertexLayout, vertices, vertexCount, indices, indexCount, bounds,
                sourcePositions, sourceColors, sourceBakedColors, sourceNormals, sourceTexCoords,
                sourcePbr, sourceBakedPbr, sourceEmissive, sourceBakedEmissive, sourceJoints,
                sourceWeights, retainSourceData, null, null);
    }

    private Mesh(GraphicsContext graphics, String id, VertexLayout vertexLayout, float[] vertices, int vertexCount,
            short[] indices, int indexCount, BoundingBox bounds, float[] sourcePositions, float[] sourceColors,
            float[] sourceBakedColors, float[] sourceNormals, float[] sourceTexCoords, float[] sourcePbr,
            float[] sourceBakedPbr, float[] sourceEmissive, float[] sourceBakedEmissive, int[] sourceJoints,
            float[] sourceWeights, boolean retainSourceData, float[] sourceTexCoords1, float[] sourceTangents) {
        this(graphics, id, vertexLayout, vertices, vertexCount, indices, indexCount, bounds,
                sourcePositions, sourceColors, sourceBakedColors, sourceNormals, sourceTexCoords, sourcePbr,
                sourceBakedPbr, sourceEmissive, sourceBakedEmissive, sourceJoints, sourceWeights, retainSourceData,
                sourceTexCoords1, sourceTangents, null, false);
    }

    private Mesh(GraphicsContext graphics, String id, VertexLayout vertexLayout, float[] vertices, int vertexCount,
            short[] indices, int indexCount, BoundingBox bounds, float[] sourcePositions, float[] sourceColors,
            float[] sourceBakedColors, float[] sourceNormals, float[] sourceTexCoords, float[] sourcePbr,
            float[] sourceBakedPbr, float[] sourceEmissive, float[] sourceBakedEmissive, int[] sourceJoints,
            float[] sourceWeights, boolean retainSourceData, float[] sourceTexCoords1, float[] sourceTangents,
            PositionColor3DPreparation preparation, boolean deferUpload) {
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
        if (indexCount > 0 && (indices == null || indices.length < indexCount)) {
            throw new FdxException("Mesh indices cannot be empty when index count is greater than zero");
        }
        int vertexByteCount = vertexCount * vertexLayout.arrayStride();
        if (preparation == null) validateFloatVertexData(vertices, vertexByteCount);
        else if (preparation.uploadBytes.capacity() != vertexByteCount)
            throw new FdxException("Prepared vertex bytes do not match the mesh layout");
        this.id = id != null ? id : "";
        this.vertexLayout = vertexLayout;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.bounds = bounds != null ? bounds : BoundingBox.empty();
        // Geometry queries (for example editor picking) are independent of
        // optional CPU shading attributes. Keep one mesh-owned position copy
        // even when rendering uses only the uploaded GPU attributes.
        this.sourcePositions = sourcePositions != null ? copySource(preparation, 0, sourcePositions) : null;
        this.sourceColors = retainSourceData && sourceColors != null ? copySource(preparation, 1, sourceColors) : null;
        this.sourceBakedColors = retainSourceData && sourceBakedColors != null ? copySource(preparation, 2, sourceBakedColors) : null;
        this.sourceNormals = retainSourceData && sourceNormals != null ? copySource(preparation, 3, sourceNormals) : null;
        this.sourceTexCoords = retainSourceData && sourceTexCoords != null ? copySource(preparation, 4, sourceTexCoords) : null;
        this.sourceTexCoords1 = retainSourceData && sourceTexCoords1 != null ? copySource(preparation, 5, sourceTexCoords1) : null;
        this.sourceTangents = retainSourceData && sourceTangents != null ? copySource(preparation, 6, sourceTangents) : null;
        this.sourcePbr = retainSourceData && sourcePbr != null ? copySource(preparation, 7, sourcePbr) : null;
        this.sourceBakedPbr = retainSourceData && sourceBakedPbr != null ? copySource(preparation, 8, sourceBakedPbr) : null;
        this.sourceEmissive = retainSourceData && sourceEmissive != null ? copySource(preparation, 9, sourceEmissive) : null;
        this.sourceBakedEmissive = retainSourceData && sourceBakedEmissive != null ? copySource(preparation, 10, sourceBakedEmissive)
                : null;
        this.sourceJoints = retainSourceData && sourceJoints != null ? (preparation != null && !preparation.uploaded ? preparation.jointCopy : sourceJoints.clone()) : null;
        this.sourceWeights = retainSourceData && sourceWeights != null ? copySource(preparation, 11, sourceWeights) : null;
        try {
            vertexBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(this.id + " vertices",
                    vertexByteCount));
            if (!deferUpload) graphics.device().writeBuffer(vertexBuffer,
                    preparation != null ? preparation.uploadBytes.duplicate() : floats(vertices, vertexByteCount));
            if (indexCount > 0) {
                int indexByteCount = indexCount * 2;
                indexBuffer = graphics.device().createBuffer(BufferDescriptor.staticIndex(this.id + " indices",
                        indexByteCount));
                graphics.device().writeBuffer(indexBuffer, shorts(indices, indexByteCount));
            }
        } catch (RuntimeException | Error error) {
            releaseFailedBuffer(indexBuffer, error);
            releaseFailedBuffer(vertexBuffer, error);
            throw error;
        }
    }

    private static float[] copySource(PositionColor3DPreparation preparation, int index, float[] source) {
        return preparation != null && !preparation.uploaded ? preparation.copies[index] : source.clone();
    }
    private static void releaseFailedBuffer(Buffer buffer, Throwable error) {
        if (buffer != null) {
            try {
                buffer.dispose();
            } catch (RuntimeException | Error cleanupError) {
                if (cleanupError != error) {
                    error.addSuppressed(cleanupError);
                }
            }
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
     * @param retainSourceData whether to retain CPU shading attributes;
     *                         source positions are always retained
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
     * @param retainSourceData whether to retain CPU shading attributes;
     *                         source positions are always retained
     * @return a new mesh
     */
    public static Mesh positionColor3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, float[] sourceBakedColors, float[] sourceNormals, float[] sourceTexCoords,
            float[] sourcePbr, float[] sourceBakedPbr, float[] sourceEmissive, float[] sourceBakedEmissive,
            int[] sourceJoints, float[] sourceWeights, BoundingBox bounds, boolean retainSourceData) {
        return positionColor3D(graphics, id, sourcePositions, sourceColors, sourceBakedColors, sourceNormals,
                sourceTexCoords, sourcePbr, sourceBakedPbr, sourceEmissive, sourceBakedEmissive,
                sourceJoints, sourceWeights, bounds, retainSourceData, null, null);
    }

    /**
     * Creates a PBR mesh with optional UV1 and tangent XYZW attributes. Arrays are copied on creation;
     * retained shading arrays follow retainSourceData. Null extra attributes use zero values; tangent W
     * zero requests a derivative basis. Existing overloads keep their compact layouts.
     */
    public static Mesh positionColor3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, float[] sourceBakedColors, float[] sourceNormals, float[] sourceTexCoords,
            float[] sourcePbr, float[] sourceBakedPbr, float[] sourceEmissive, float[] sourceBakedEmissive,
            int[] sourceJoints, float[] sourceWeights, BoundingBox bounds, boolean retainSourceData,
            float[] sourceTexCoords1, float[] sourceTangents) {
        PositionColor3DPreparation preparation = preparePositionColor3D(sourcePositions, sourceColors, sourceBakedColors,
                sourceNormals, sourceTexCoords, sourcePbr, sourceBakedPbr, sourceEmissive, sourceBakedEmissive,
                sourceJoints, sourceWeights, bounds, retainSourceData, sourceTexCoords1, sourceTangents);
        while (!preparation.step(4096)) { }
        return preparation.upload(graphics, id);
    }

    /** Begins CPU-only vertex packing. Input arrays and bounds are borrowed and must remain unchanged
     * until upload completes. Call step on one preparation thread; publish completed state before
     * upload on the graphics thread. Staging is GC-managed and may be abandoned on cancellation. */
    public static PositionColor3DPreparation preparePositionColor3D(float[] sourcePositions, float[] sourceColors, float[] sourceBakedColors,
            float[] sourceNormals, float[] sourceTexCoords, float[] sourcePbr, float[] sourceBakedPbr,
            float[] sourceEmissive, float[] sourceBakedEmissive, int[] sourceJoints, float[] sourceWeights,
            BoundingBox bounds, boolean retainSourceData, float[] sourceTexCoords1, float[] sourceTangents) {
        return new PositionColor3DPreparation(sourcePositions, sourceColors, sourceBakedColors,
                sourceNormals, sourceTexCoords, sourcePbr, sourceBakedPbr, sourceEmissive, sourceBakedEmissive,
                sourceJoints, sourceWeights, bounds, retainSourceData, sourceTexCoords1, sourceTangents);
    }

    /** CPU staging with bounded vertex work. No graphics calls occur before upload. */
    public static final class PositionColor3DPreparation {
        private final float[] sourcePositions, sourceColors, sourceBakedColors, sourceNormals, sourceTexCoords,
                sourcePbr, sourceBakedPbr, sourceEmissive, sourceBakedEmissive, sourceWeights, sourceTexCoords1, sourceTangents;
        private final int[] sourceJoints;
        private final BoundingBox bounds;
        private final boolean retainSourceData;
        private boolean pbrLayout, hasSkinning, textured;
        private int vertexCount, floatsPerVertex, cursor;
        private float[] vertices;
        private ByteBuffer uploadBytes;
        private java.nio.FloatBuffer uploadFloats;
        private final float[][] sources, copies;
        private int[] jointCopy;
        private int allocatedCopies;
        private boolean uploaded;

        private PositionColor3DPreparation(float[] sourcePositions, float[] sourceColors, float[] sourceBakedColors,
            float[] sourceNormals, float[] sourceTexCoords, float[] sourcePbr, float[] sourceBakedPbr,
            float[] sourceEmissive, float[] sourceBakedEmissive, int[] sourceJoints, float[] sourceWeights,
            BoundingBox bounds, boolean retainSourceData, float[] sourceTexCoords1, float[] sourceTangents) {
            this.sourcePositions = sourcePositions;
            this.sourceColors = sourceColors;
            this.sourceBakedColors = sourceBakedColors;
            this.sourceNormals = sourceNormals;
            this.sourceTexCoords = sourceTexCoords;
            this.sourcePbr = sourcePbr;
            this.sourceBakedPbr = sourceBakedPbr;
            this.sourceEmissive = sourceEmissive;
            this.sourceBakedEmissive = sourceBakedEmissive;
            this.sourceJoints = sourceJoints;
            this.sourceWeights = sourceWeights;
            this.bounds = bounds;
            this.retainSourceData = retainSourceData;
            this.sourceTexCoords1 = sourceTexCoords1;
            this.sourceTangents = sourceTangents;
            if (sourcePositions == null || sourcePositions.length == 0 || sourcePositions.length % 3 != 0) {
                throw new FdxException("3D position/color meshes require xyz source positions");
            }
            vertexCount = sourcePositions.length / 3;
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
            pbrLayout = sourceNormals != null && sourceTexCoords != null && sourcePbr != null
                    && sourceEmissive != null;
            hasSkinning = sourceJoints != null || sourceWeights != null;
            textured = sourceTexCoords1 != null || sourceTangents != null;
            if (textured && !pbrLayout) throw new FdxException("Extended texture attributes require a PBR mesh");
            if (sourceTexCoords1 != null && sourceTexCoords1.length != vertexCount * 2
                    || sourceTangents != null && sourceTangents.length != vertexCount * 4)
                throw new FdxException("Extended texture attribute count must match positions");
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
                for (int i=0;i<sourceWeights.length;i++) {
                    if (!Float.isFinite(sourceWeights[i]) || sourceWeights[i] < 0 || sourceJoints[i] < 0
                            || sourceJoints[i] > 16_777_216)
                        throw new FdxException("Skinning requires finite nonnegative weights and exactly representable joint indices");
                }
            }
            floatsPerVertex = hasSkinning ? PBR_SKINNED_FLOATS_PER_VERTEX
                    : pbrLayout ? PBR_FLOATS_PER_VERTEX : POSITION_COLOR_FLOATS_PER_VERTEX;
            if (textured) floatsPerVertex += 6;

            Math.multiplyExact(Math.multiplyExact(vertexCount, floatsPerVertex), Float.BYTES);
            sources = new float[][] {sourcePositions, sourceColors, sourceBakedColors, sourceNormals,
                    sourceTexCoords, sourceTexCoords1, sourceTangents, sourcePbr, sourceBakedPbr,
                    sourceEmissive, sourceBakedEmissive, sourceWeights};
            copies = new float[sources.length][];
        }

        /** Allocates one staging array or packs at most maxVertices vertices; returns true when
         * upload can begin. Positive budgets only. A single allocation cannot be interrupted. */
        public boolean step(int maxVertices) {
            if (maxVertices <= 0) throw new FdxException("Mesh preparation vertex budget must be positive");
            // Separate large allocations: a cooperative caller can yield before each one.
            if (vertices == null) { vertices = new float[Math.min(vertexCount, 1024) * floatsPerVertex]; return false; }
            if (uploadBytes == null) {
                uploadBytes = ByteBuffer.allocateDirect(vertexCount * floatsPerVertex * Float.BYTES).order(ByteOrder.nativeOrder());
                uploadFloats = uploadBytes.asFloatBuffer();
                return false;
            }
            while (allocatedCopies < sources.length) {
                int i = allocatedCopies++;
                if ((i == 0 || retainSourceData) && sources[i] != null) {
                    copies[i] = new float[sources[i].length]; return false;
                }
            }
            if (retainSourceData && sourceJoints != null && jointCopy == null) {
                jointCopy = new int[sourceJoints.length]; return false;
            }
            int end = cursor + Math.min(Math.min(maxVertices, 1024), vertexCount - cursor);
            int out = 0;
            for (int i = cursor; i < end; i++) {
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
                if (textured) {
                    vertices[out++] = sourceTexCoords1 == null ? 0 : sourceTexCoords1[i*2];
                    vertices[out++] = sourceTexCoords1 == null ? 0 : sourceTexCoords1[i*2+1];
                    for (int c = 0; c < 4; c++) vertices[out++] = sourceTangents == null ? 0 : sourceTangents[i*4+c];
                }
            }

            uploadFloats.put(vertices, 0, out);
            for (int i = 0; i < sources.length; i++) if (copies[i] != null) {
                int components = sources[i].length / vertexCount;
                System.arraycopy(sources[i], cursor * components, copies[i], cursor * components, (end - cursor) * components);
            }
            if (jointCopy != null) System.arraycopy(sourceJoints, cursor * 4, jointCopy, cursor * 4, (end - cursor) * 4);
            cursor = end;
            return cursor == vertexCount;
        }

        /** Creates an owned mesh on the graphics thread once prepared. Source data is copied as in
         * positionColor3D. The first upload transfers CPU-prepared source copies; subsequent uploads
         * clone them on the caller thread. Upload failure releases partial buffers. Individual driver calls cannot
         * be preempted. Reuse is allowed while the borrowed inputs remain unchanged. */
        public Mesh upload(GraphicsContext graphics, String id) {
            PositionColor3DUpload upload = beginUpload(graphics, id);
            try {
                while (!upload.step(Integer.MAX_VALUE)) { }
                return upload.take();
            } finally { upload.dispose(); }
        }

        /** Creates an owned, unpublished upload on the graphics thread. Step it there, then take
         * the completed mesh. Dispose the upload on cancellation/failure or when no longer needed.
         * Providers without range initialization use one complete write on the first step. */
        public PositionColor3DUpload beginUpload(GraphicsContext graphics, String id) {
            if (cursor != vertexCount) throw new FdxException("Mesh preparation is not complete");
            Mesh mesh = new Mesh(graphics, id, textured ? (hasSkinning ? PBR_TEXTURED_SKINNED_LAYOUT : PBR_TEXTURED_LAYOUT)
                    : hasSkinning ? PBR_SKINNED_LAYOUT : pbrLayout ? PBR_LAYOUT
                    : POSITION_COLOR_LAYOUT, vertices, vertexCount,
                    null, 0, bounds, sourcePositions, sourceColors, sourceBakedColors, sourceNormals, sourceTexCoords,
                    sourcePbr, sourceBakedPbr, sourceEmissive, sourceBakedEmissive, sourceJoints, sourceWeights,
                    retainSourceData, sourceTexCoords1, sourceTangents, this, true);
            uploaded = true;
            return new PositionColor3DUpload(graphics.device(), mesh, uploadBytes.duplicate());
        }
    }

    /** Graphics-thread initial upload. Owns the partial mesh until {@link #take()} succeeds. */
    public static final class PositionColor3DUpload implements Disposable {
        private final GraphicsDevice device;
        private final ByteBuffer bytes;
        private Mesh mesh;
        private int offset;
        private boolean disposed;

        private PositionColor3DUpload(GraphicsDevice device, Mesh mesh, ByteBuffer bytes) {
            this.device = device; this.mesh = mesh; this.bytes = bytes;
        }

        /** Writes at most maxBytes (rounded down to a multiple of four) when the provider supports
         * ranges. Requires at least four bytes. An individual allocation/upload can exceed a time budget. */
        public boolean step(int maxBytes) {
            if (disposed || mesh == null) throw new FdxException("Mesh upload is no longer owned");
            if (maxBytes < 4) throw new FdxException("Mesh upload byte budget must be at least four");
            if (offset == bytes.capacity()) return true;
            try {
                if (device.supportsBufferRangeInitialization()) {
                    int count = Math.min(maxBytes & ~3, bytes.capacity() - offset);
                    bytes.position(offset).limit(offset + count);
                    device.initializeBufferRange(mesh.vertexBuffer, offset, bytes);
                    offset += count;
                } else {
                    bytes.clear();
                    device.writeBuffer(mesh.vertexBuffer, bytes);
                    offset = bytes.capacity();
                }
                return offset == bytes.capacity();
            } catch (RuntimeException | Error failure) {
                releaseFailedBuffer(mesh.vertexBuffer, failure);
                mesh = null; disposed = true;
                throw failure;
            }
        }

        /** Transfers the complete mesh to the caller exactly once. */
        public Mesh take() {
            if (disposed || mesh == null || offset != bytes.capacity()) throw new FdxException("Mesh upload is not complete or owned");
            Mesh result = mesh; mesh = null;
            return result;
        }

        @Override public void dispose() {
            if (disposed) return;
            disposed = true;
            if (mesh != null) { Mesh owned = mesh; mesh = null; owned.dispose(); }
        }

        @Override public boolean isDisposed() { return disposed; }
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
     * Returns the mesh-owned source positions, including when CPU shading
     * attributes were not retained. Treat this array as read-only. Generic
     * vertex-layout constructors without explicit source positions return null.
     *
     * @return the borrowed source positions, or null when not supplied
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

    /** Borrowed retained UV1 data, or null when absent/not retained. Do not mutate. */
    public float[] sourceTexCoords1() { return sourceTexCoords1; }
    /** Borrowed retained tangent XYZW data, or null when absent/not retained. Do not mutate. */
    public float[] sourceTangents() { return sourceTangents; }

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
