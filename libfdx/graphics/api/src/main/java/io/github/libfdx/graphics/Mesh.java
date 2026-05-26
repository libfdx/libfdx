package io.github.libfdx.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Vector3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class Mesh implements Disposable {
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
    private Buffer vertexBuffer;
    private Buffer indexBuffer;
    private boolean disposed;

    public Mesh(GraphicsContext graphics, String id, VertexLayout vertexLayout, float[] vertices, int vertexCount) {
        this(graphics, id, vertexLayout, vertices, vertexCount, null, 0, BoundingBox.empty());
    }

    public Mesh(GraphicsContext graphics, String id, VertexLayout vertexLayout, float[] vertices, int vertexCount,
            BoundingBox bounds) {
        this(graphics, id, vertexLayout, vertices, vertexCount, null, 0, bounds);
    }

    public Mesh(GraphicsContext graphics, String id, VertexLayout vertexLayout, float[] vertices, int vertexCount,
            short[] indices, int indexCount, BoundingBox bounds) {
        this(graphics, id, vertexLayout, vertices, vertexCount, indices, indexCount, bounds, null, null, null,
                null, null, null, null, null, null, false);
    }

    private Mesh(GraphicsContext graphics, String id, VertexLayout vertexLayout, float[] vertices, int vertexCount,
            short[] indices, int indexCount, BoundingBox bounds, float[] sourcePositions, float[] sourceColors,
            float[] sourceBakedColors, float[] sourceNormals, float[] sourceTexCoords, float[] sourcePbr,
            float[] sourceBakedPbr, float[] sourceEmissive, float[] sourceBakedEmissive, boolean retainSourceData) {
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

    public static Mesh coloredTriangle(GraphicsContext graphics, String id) {
        float[] vertices = {
                0.0f, 0.65f, 0.0f, 0.95f, 0.33f, 0.28f, 1.0f,
                -0.65f, -0.55f, 0.0f, 0.18f, 0.67f, 0.95f, 1.0f,
                0.65f, -0.55f, 0.0f, 0.26f, 0.81f, 0.43f, 1.0f
        };
        return new Mesh(graphics, id, POSITION_COLOR_LAYOUT, vertices, 3,
                BoundingBox.of(new Vector3(-0.65f, -0.55f, 0.0f), new Vector3(0.65f, 0.65f, 0.0f)));
    }

    public static Mesh positionColor3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, BoundingBox bounds) {
        return positionColor3D(graphics, id, sourcePositions, sourceColors, null, null, null, null, bounds);
    }

    public static Mesh positionColor3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, float[] sourceNormals, float[] sourceTexCoords, float[] sourcePbr,
            float[] sourceEmissive, BoundingBox bounds) {
        return positionColor3D(graphics, id, sourcePositions, sourceColors, null, sourceNormals, sourceTexCoords,
                sourcePbr, null, sourceEmissive, null, bounds);
    }

    public static Mesh positionColor3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, float[] sourceBakedColors, float[] sourceNormals, float[] sourceTexCoords,
            float[] sourcePbr, float[] sourceBakedPbr, float[] sourceEmissive, float[] sourceBakedEmissive,
            BoundingBox bounds) {
        return positionColor3D(graphics, id, sourcePositions, sourceColors, sourceBakedColors, sourceNormals,
                sourceTexCoords, sourcePbr, sourceBakedPbr, sourceEmissive, sourceBakedEmissive, bounds, true);
    }

    public static Mesh positionColor3D(GraphicsContext graphics, String id, float[] sourcePositions,
            float[] sourceColors, float[] sourceBakedColors, float[] sourceNormals, float[] sourceTexCoords,
            float[] sourcePbr, float[] sourceBakedPbr, float[] sourceEmissive, float[] sourceBakedEmissive,
            BoundingBox bounds, boolean retainSourceData) {
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
        float[] vertices = new float[vertexCount * (pbrLayout ? PBR_FLOATS_PER_VERTEX
                : POSITION_COLOR_FLOATS_PER_VERTEX)];
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
        }
        return new Mesh(graphics, id, pbrLayout ? PBR_LAYOUT : POSITION_COLOR_LAYOUT, vertices, vertexCount,
                null, 0, bounds, sourcePositions, sourceColors, sourceBakedColors, sourceNormals, sourceTexCoords,
                sourcePbr, sourceBakedPbr, sourceEmissive, sourceBakedEmissive, retainSourceData);
    }

    public String id() {
        return id;
    }

    public Buffer vertexBuffer() {
        return vertexBuffer;
    }

    public Buffer indexBuffer() {
        return indexBuffer;
    }

    public VertexLayout vertexLayout() {
        return vertexLayout;
    }

    public int vertexCount() {
        return vertexCount;
    }

    public int indexCount() {
        return indexCount;
    }

    public BoundingBox bounds() {
        return bounds;
    }

    public boolean hasPositionColor3DSource() {
        return sourcePositions != null && sourceColors != null;
    }

    public float[] sourcePositions() {
        return sourcePositions;
    }

    public float[] sourceColors() {
        return sourceColors;
    }

    public float[] sourceBakedColors() {
        return sourceBakedColors;
    }

    public float[] sourceNormals() {
        return sourceNormals;
    }

    public float[] sourceTexCoords() {
        return sourceTexCoords;
    }

    public float[] sourcePbr() {
        return sourcePbr;
    }

    public float[] sourceBakedPbr() {
        return sourceBakedPbr;
    }

    public float[] sourceEmissive() {
        return sourceEmissive;
    }

    public float[] sourceBakedEmissive() {
        return sourceBakedEmissive;
    }

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
