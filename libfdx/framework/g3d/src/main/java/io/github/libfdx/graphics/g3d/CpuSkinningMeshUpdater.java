package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.math.Matrix4;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Updates retained mesh vertex data using CPU skinning.
 *
 * @author xpenatan
 */
public final class CpuSkinningMeshUpdater {
    private static final int INFLUENCES_PER_VERTEX = 4;

    private final GraphicsContext graphics;
    private final Mesh mesh;
    private final int[] joints;
    private final float[] weights;
    private final int vertexCount;
    private final boolean pbrLayout;
    private final boolean skinnedPbrLayout;
    private final ByteBuffer vertexBytes;
    private final FloatBuffer vertexFloats;
    private float[] paletteValues = new float[0];

    /**
     * Creates a CPU skinning mesh updater.
     *
     * @param graphics the graphics context
     * @param mesh the mesh
     * @param joints four joint indices per vertex
     * @param weights four joint weights per vertex
     */
    public CpuSkinningMeshUpdater(GraphicsContext graphics, Mesh mesh, int[] joints, float[] weights) {
        if (graphics == null) {
            throw new FdxException("CpuSkinningMeshUpdater graphics cannot be null");
        }
        if (mesh == null) {
            throw new FdxException("CpuSkinningMeshUpdater mesh cannot be null");
        }
        this.graphics = graphics;
        this.mesh = mesh;
        vertexCount = mesh.vertexCount();
        validateInfluences(joints, weights, vertexCount);
        this.joints = joints.clone();
        this.weights = weights.clone();
        VertexLayout layout = mesh.vertexLayout();
        skinnedPbrLayout = layout == Mesh.PBR_SKINNED_LAYOUT;
        pbrLayout = layout == Mesh.PBR_LAYOUT || skinnedPbrLayout;
        if (!pbrLayout && layout != Mesh.POSITION_COLOR_LAYOUT) {
            throw new FdxException("CpuSkinningMeshUpdater requires Mesh.PBR_LAYOUT, Mesh.PBR_SKINNED_LAYOUT, "
                    + "or Mesh.POSITION_COLOR_LAYOUT");
        }
        validateSourceData(mesh, pbrLayout, vertexCount);
        vertexBytes = ByteBuffer.allocateDirect(vertexCount * layout.arrayStride()).order(ByteOrder.nativeOrder());
        vertexFloats = vertexBytes.asFloatBuffer();
    }

    /**
     * Updates the mesh vertex buffer.
     *
     * @param palette the skinning palette
     * @return this CPU skinning mesh updater for chaining
     */
    public CpuSkinningMeshUpdater update(SkinningPalette palette) {
        if (palette == null) {
            throw new FdxException("CpuSkinningMeshUpdater palette cannot be null");
        }
        int paletteFloatCount = palette.size() * Matrix4.VALUE_COUNT;
        if (paletteValues.length != paletteFloatCount) {
            paletteValues = new float[paletteFloatCount];
        }
        palette.copyValues(paletteValues);
        writeVertices(palette);
        vertexBytes.position(0);
        vertexBytes.limit(vertexCount * mesh.vertexLayout().arrayStride());
        graphics.device().writeBuffer(mesh.vertexBuffer(), vertexBytes);
        return this;
    }

    private void writeVertices(SkinningPalette palette) {
        float[] positions = mesh.sourcePositions();
        float[] colors = mesh.sourceColors();
        float[] normals = mesh.sourceNormals();
        float[] texCoords = mesh.sourceTexCoords();
        float[] pbr = mesh.sourcePbr();
        float[] emissive = mesh.sourceEmissive();
        vertexFloats.clear();
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int positionOffset = vertex * 3;
            int colorOffset = vertex * 4;
            float sourceX = positions[positionOffset];
            float sourceY = positions[positionOffset + 1];
            float sourceZ = positions[positionOffset + 2];
            float skinnedX = 0.0f;
            float skinnedY = 0.0f;
            float skinnedZ = 0.0f;
            float normalX = 0.0f;
            float normalY = 0.0f;
            float normalZ = 0.0f;
            float totalWeight = 0.0f;
            for (int influence = 0; influence < INFLUENCES_PER_VERTEX; influence++) {
                int influenceOffset = vertex * INFLUENCES_PER_VERTEX + influence;
                float weight = weights[influenceOffset];
                if (weight == 0.0f) {
                    continue;
                }
                int joint = joints[influenceOffset];
                if (joint < 0 || joint >= palette.size()) {
                    throw new FdxException("CpuSkinningMeshUpdater joint index out of range: " + joint);
                }
                int matrixOffset = joint * Matrix4.VALUE_COUNT;
                skinnedX += weight * transformPositionX(matrixOffset, sourceX, sourceY, sourceZ);
                skinnedY += weight * transformPositionY(matrixOffset, sourceX, sourceY, sourceZ);
                skinnedZ += weight * transformPositionZ(matrixOffset, sourceX, sourceY, sourceZ);
                if (pbrLayout) {
                    int normalOffset = vertex * 3;
                    float sourceNormalX = normals[normalOffset];
                    float sourceNormalY = normals[normalOffset + 1];
                    float sourceNormalZ = normals[normalOffset + 2];
                    normalX += weight * transformDirectionX(matrixOffset, sourceNormalX, sourceNormalY,
                            sourceNormalZ);
                    normalY += weight * transformDirectionY(matrixOffset, sourceNormalX, sourceNormalY,
                            sourceNormalZ);
                    normalZ += weight * transformDirectionZ(matrixOffset, sourceNormalX, sourceNormalY,
                            sourceNormalZ);
                }
                totalWeight += weight;
            }
            if (totalWeight == 0.0f) {
                skinnedX = sourceX;
                skinnedY = sourceY;
                skinnedZ = sourceZ;
                if (pbrLayout) {
                    int normalOffset = vertex * 3;
                    normalX = normals[normalOffset];
                    normalY = normals[normalOffset + 1];
                    normalZ = normals[normalOffset + 2];
                }
            }
            vertexFloats.put(skinnedX);
            vertexFloats.put(skinnedY);
            vertexFloats.put(skinnedZ);
            if (pbrLayout) {
                float normalLength = (float)Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
                if (normalLength > 0.000001f) {
                    float invNormalLength = 1.0f / normalLength;
                    normalX *= invNormalLength;
                    normalY *= invNormalLength;
                    normalZ *= invNormalLength;
                }
                int texCoordOffset = vertex * 2;
                vertexFloats.put(normalX);
                vertexFloats.put(normalY);
                vertexFloats.put(normalZ);
                vertexFloats.put(texCoords[texCoordOffset]);
                vertexFloats.put(texCoords[texCoordOffset + 1]);
            }
            vertexFloats.put(colors[colorOffset]);
            vertexFloats.put(colors[colorOffset + 1]);
            vertexFloats.put(colors[colorOffset + 2]);
            vertexFloats.put(colors[colorOffset + 3]);
            if (pbrLayout) {
                int pbrOffset = vertex * 3;
                int emissiveOffset = vertex * 3;
                vertexFloats.put(pbr[pbrOffset]);
                vertexFloats.put(pbr[pbrOffset + 1]);
                vertexFloats.put(pbr[pbrOffset + 2]);
                vertexFloats.put(emissive[emissiveOffset]);
                vertexFloats.put(emissive[emissiveOffset + 1]);
                vertexFloats.put(emissive[emissiveOffset + 2]);
            }
            if (skinnedPbrLayout) {
                int influenceOffset = vertex * INFLUENCES_PER_VERTEX;
                vertexFloats.put(joints[influenceOffset]);
                vertexFloats.put(joints[influenceOffset + 1]);
                vertexFloats.put(joints[influenceOffset + 2]);
                vertexFloats.put(joints[influenceOffset + 3]);
                vertexFloats.put(weights[influenceOffset]);
                vertexFloats.put(weights[influenceOffset + 1]);
                vertexFloats.put(weights[influenceOffset + 2]);
                vertexFloats.put(weights[influenceOffset + 3]);
            }
        }
    }

    private float transformPositionX(int matrixOffset, float x, float y, float z) {
        return paletteValues[matrixOffset] * x + paletteValues[matrixOffset + 4] * y
                + paletteValues[matrixOffset + 8] * z + paletteValues[matrixOffset + 12];
    }

    private float transformPositionY(int matrixOffset, float x, float y, float z) {
        return paletteValues[matrixOffset + 1] * x + paletteValues[matrixOffset + 5] * y
                + paletteValues[matrixOffset + 9] * z + paletteValues[matrixOffset + 13];
    }

    private float transformPositionZ(int matrixOffset, float x, float y, float z) {
        return paletteValues[matrixOffset + 2] * x + paletteValues[matrixOffset + 6] * y
                + paletteValues[matrixOffset + 10] * z + paletteValues[matrixOffset + 14];
    }

    private float transformDirectionX(int matrixOffset, float x, float y, float z) {
        return paletteValues[matrixOffset] * x + paletteValues[matrixOffset + 4] * y
                + paletteValues[matrixOffset + 8] * z;
    }

    private float transformDirectionY(int matrixOffset, float x, float y, float z) {
        return paletteValues[matrixOffset + 1] * x + paletteValues[matrixOffset + 5] * y
                + paletteValues[matrixOffset + 9] * z;
    }

    private float transformDirectionZ(int matrixOffset, float x, float y, float z) {
        return paletteValues[matrixOffset + 2] * x + paletteValues[matrixOffset + 6] * y
                + paletteValues[matrixOffset + 10] * z;
    }

    private static void validateInfluences(int[] joints, float[] weights, int vertexCount) {
        int expectedLength = vertexCount * INFLUENCES_PER_VERTEX;
        if (joints == null || joints.length != expectedLength) {
            throw new FdxException("CpuSkinningMeshUpdater requires four joint indices per vertex");
        }
        if (weights == null || weights.length != expectedLength) {
            throw new FdxException("CpuSkinningMeshUpdater requires four joint weights per vertex");
        }
    }

    private static void validateSourceData(Mesh mesh, boolean pbrLayout, int vertexCount) {
        if (mesh.sourcePositions() == null || mesh.sourcePositions().length != vertexCount * 3
                || mesh.sourceColors() == null || mesh.sourceColors().length != vertexCount * 4) {
            throw new FdxException("CpuSkinningMeshUpdater requires retained position/color mesh source data");
        }
        if (!pbrLayout) {
            return;
        }
        if (mesh.sourceNormals() == null || mesh.sourceNormals().length != vertexCount * 3
                || mesh.sourceTexCoords() == null || mesh.sourceTexCoords().length != vertexCount * 2
                || mesh.sourcePbr() == null || mesh.sourcePbr().length != vertexCount * 3
                || mesh.sourceEmissive() == null || mesh.sourceEmissive().length != vertexCount * 3) {
            throw new FdxException("CpuSkinningMeshUpdater requires retained PBR mesh source data");
        }
    }
}
