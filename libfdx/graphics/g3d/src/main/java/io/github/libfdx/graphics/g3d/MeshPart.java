package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.PrimitiveTopology;

/**
 * Represents a mesh part.
 *
 * @author xpenatan
 */
public final class MeshPart {
    private final String id;
    private final Mesh mesh;
    private final PrimitiveTopology primitiveTopology;
    private final int firstVertex;
    private final int vertexCount;
    private final int firstIndex;
    private final int indexCount;

    /**
     * Creates a mesh part.
     *
     * @param mesh the mesh
     * @param firstVertex the first vertex
     * @param vertexCount the vertex count
     */
    public MeshPart(Mesh mesh, int firstVertex, int vertexCount) {
        this("", mesh, PrimitiveTopology.TRIANGLE_LIST, firstVertex, vertexCount, 0, 0);
    }

    /**
     * Creates a mesh part.
     *
     * @param id the identifier
     * @param mesh the mesh
     * @param primitiveTopology the primitive topology
     * @param firstVertex the first vertex
     * @param vertexCount the vertex count
     */
    public MeshPart(String id, Mesh mesh, PrimitiveTopology primitiveTopology, int firstVertex, int vertexCount) {
        this(id, mesh, primitiveTopology, firstVertex, vertexCount, 0, 0);
    }

    /**
     * Creates a mesh part.
     *
     * @param id the identifier
     * @param mesh the mesh
     * @param primitiveTopology the primitive topology
     * @param firstVertex the first vertex
     * @param vertexCount the vertex count
     * @param firstIndex the first index
     * @param indexCount the index count
     */
    public MeshPart(String id, Mesh mesh, PrimitiveTopology primitiveTopology, int firstVertex, int vertexCount,
            int firstIndex, int indexCount) {
        if (mesh == null) {
            throw new FdxException("MeshPart mesh cannot be null");
        }
        if (firstVertex < 0 || vertexCount < 0 || firstIndex < 0 || indexCount < 0) {
            throw new FdxException("MeshPart ranges cannot be negative");
        }
        this.id = id != null ? id : "";
        this.mesh = mesh;
        this.primitiveTopology = primitiveTopology != null ? primitiveTopology : PrimitiveTopology.TRIANGLE_LIST;
        this.firstVertex = firstVertex;
        this.vertexCount = vertexCount;
        this.firstIndex = firstIndex;
        this.indexCount = indexCount;
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
     * Returns the mesh.
     *
     * @return the mesh
     */
    public Mesh mesh() {
        return mesh;
    }

    /**
     * Returns the primitive topology.
     *
     * @return the primitive topology
     */
    public PrimitiveTopology primitiveTopology() {
        return primitiveTopology;
    }

    /**
     * Returns the first vertex.
     *
     * @return the first vertex
     */
    public int firstVertex() {
        return firstVertex;
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
     * Returns the first index.
     *
     * @return the first index
     */
    public int firstIndex() {
        return firstIndex;
    }

    /**
     * Returns the index count.
     *
     * @return the index count
     */
    public int indexCount() {
        return indexCount;
    }
}
