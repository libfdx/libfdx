package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.PrimitiveTopology;

public final class MeshPart {
    private final String id;
    private final Mesh mesh;
    private final PrimitiveTopology primitiveTopology;
    private final int firstVertex;
    private final int vertexCount;
    private final int firstIndex;
    private final int indexCount;

    public MeshPart(Mesh mesh, int firstVertex, int vertexCount) {
        this("", mesh, PrimitiveTopology.TRIANGLE_LIST, firstVertex, vertexCount, 0, 0);
    }

    public MeshPart(String id, Mesh mesh, PrimitiveTopology primitiveTopology, int firstVertex, int vertexCount) {
        this(id, mesh, primitiveTopology, firstVertex, vertexCount, 0, 0);
    }

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

    public String id() {
        return id;
    }

    public Mesh mesh() {
        return mesh;
    }

    public PrimitiveTopology primitiveTopology() {
        return primitiveTopology;
    }

    public int firstVertex() {
        return firstVertex;
    }

    public int vertexCount() {
        return vertexCount;
    }

    public int firstIndex() {
        return firstIndex;
    }

    public int indexCount() {
        return indexCount;
    }
}
