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
 * Updates exclusively owned mesh vertex data using CPU skinning. The mesh and graphics context
 * are borrowed and must outlive this updater. Construction snapshots the bind positions, normals,
 * tangents and influences; later updates replace both GPU vertices and retained CPU geometry.
 * Do not use this low-level updater on a mesh shared by independently posed instances; use
 * {@link CpuSkinnedModelAnimator} for instance-owned copies. Calls are confined to the graphics
 * thread before recording draws. No resources are allocated after the first palette update.
 *
 * @author xpenatan
 */
public final class CpuSkinningMeshUpdater {
    private static final int INFLUENCES_PER_VERTEX = 4;

    private final GraphicsContext graphics;
    private final Mesh mesh;
    private final int[] joints;
    private final float[] weights;
    private final float[] bindPositions, bindNormals, bindTangents;
    private final int vertexCount;
    private final boolean pbrLayout;
    private final boolean skinnedPbrLayout;
    private final boolean textured;
    private final float[] linear = new float[9];
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
        normalizeWeights(this.weights);
        VertexLayout layout = mesh.vertexLayout();
        skinnedPbrLayout = mesh.hasPbrSkinning();
        textured = mesh.hasPbrTextureCoordinates();
        pbrLayout = layout == Mesh.PBR_LAYOUT || skinnedPbrLayout || textured;
        if (!pbrLayout && layout != Mesh.POSITION_COLOR_LAYOUT) {
            throw new FdxException("CpuSkinningMeshUpdater requires Mesh.PBR_LAYOUT, Mesh.PBR_SKINNED_LAYOUT, "
                    + "or Mesh.POSITION_COLOR_LAYOUT");
        }
        validateSourceData(mesh, pbrLayout, vertexCount);
        bindPositions = mesh.sourcePositions().clone();
        bindNormals = pbrLayout ? mesh.sourceNormals().clone() : null;
        bindTangents = mesh.sourceTangents() == null ? null : mesh.sourceTangents().clone();
        vertexBytes = ByteBuffer.allocateDirect(vertexCount * layout.arrayStride()).order(ByteOrder.nativeOrder());
        vertexFloats = vertexBytes.asFloatBuffer();
    }

    /**
     * Updates the mesh vertex buffer and retained positions/normals/tangents from the bind snapshot.
     * Positive weights are normalized; zero-weight vertices keep their bind pose. Existing GPU
     * skin attributes are zeroed so the standard PBR/shadow shaders do not apply the skin twice.
     * Mesh bounds are borrowed and left unchanged; keep conservative bounds at the renderable.
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
        copyDeformedSource();
        return this;
    }

    private void writeVertices(SkinningPalette palette) {
        float[] positions = bindPositions;
        float[] colors = mesh.sourceColors();
        float[] normals = bindNormals;
        float[] texCoords = mesh.sourceTexCoords();
        float[] pbr = mesh.sourcePbr();
        float[] emissive = mesh.sourceEmissive();
        vertexFloats.clear();
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (pbrLayout) java.util.Arrays.fill(linear, 0);
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
                if (pbrLayout) for (int column = 0; column < 3; column++) for (int row = 0; row < 3; row++)
                    linear[column*3+row] += weight * paletteValues[matrixOffset+column*4+row];
                skinnedX += weight * transformPositionX(matrixOffset, sourceX, sourceY, sourceZ);
                skinnedY += weight * transformPositionY(matrixOffset, sourceX, sourceY, sourceZ);
                skinnedZ += weight * transformPositionZ(matrixOffset, sourceX, sourceY, sourceZ);
                totalWeight += weight;
            }
            if (totalWeight == 0.0f) {
                linear[0] = linear[4] = linear[8] = 1;
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
                float nx = normals[positionOffset], ny = normals[positionOffset+1], nz = normals[positionOffset+2];
                normalX = (linear[4]*linear[8]-linear[5]*linear[7])*nx
                        + (linear[7]*linear[2]-linear[8]*linear[1])*ny + (linear[1]*linear[5]-linear[2]*linear[4])*nz;
                normalY = (linear[5]*linear[6]-linear[3]*linear[8])*nx
                        + (linear[8]*linear[0]-linear[6]*linear[2])*ny + (linear[2]*linear[3]-linear[0]*linear[5])*nz;
                normalZ = (linear[3]*linear[7]-linear[4]*linear[6])*nx
                        + (linear[6]*linear[1]-linear[7]*linear[0])*ny + (linear[0]*linear[4]-linear[1]*linear[3])*nz;
                float sign = determinantSign();
                normalX *= sign; normalY *= sign; normalZ *= sign;
                if (normalX*normalX+normalY*normalY+normalZ*normalZ < 1e-20f) {
                    normalX = nx; normalY = ny; normalZ = nz;
                }
                float normalLength = (float)Math.sqrt(normalX*normalX+normalY*normalY+normalZ*normalZ);
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
                for (int i=0;i<8;i++) vertexFloats.put(0);
            }
            if (textured) {
                float[] uv1 = mesh.sourceTexCoords1(), tangents = bindTangents;
                vertexFloats.put(uv1 == null ? 0 : uv1[vertex*2]);
                vertexFloats.put(uv1 == null ? 0 : uv1[vertex*2+1]);
                float x = tangents == null ? 0 : tangents[vertex*4];
                float y = tangents == null ? 0 : tangents[vertex*4+1];
                float z = tangents == null ? 0 : tangents[vertex*4+2];
                vertexFloats.put(linear[0]*x+linear[3]*y+linear[6]*z);
                vertexFloats.put(linear[1]*x+linear[4]*y+linear[7]*z);
                vertexFloats.put(linear[2]*x+linear[5]*y+linear[8]*z);
                vertexFloats.put(tangents == null ? 0 : tangents[vertex*4+3]*determinantSign());
            }
        }
    }

    private void copyDeformedSource() {
        int stride=mesh.vertexLayout().arrayStride()/4;
        float[] positions=mesh.sourcePositions(), normals=mesh.sourceNormals(), tangents=mesh.sourceTangents();
        for (int vertex=0;vertex<vertexCount;vertex++) {
            int offset=vertex*stride;
            for (int axis=0;axis<3;axis++) positions[vertex*3+axis]=vertexFloats.get(offset+axis);
            if (pbrLayout) for (int axis=0;axis<3;axis++) normals[vertex*3+axis]=vertexFloats.get(offset+3+axis);
            if (textured && tangents != null) for (int axis=0;axis<4;axis++)
                tangents[vertex*4+axis]=vertexFloats.get(offset+stride-4+axis);
        }
    }

    private static void normalizeWeights(float[] weights) {
        for (int i=0;i<weights.length;i+=4) {
            float maximum=Math.max(Math.max(weights[i],weights[i+1]),Math.max(weights[i+2],weights[i+3]));
            if (maximum == 0) continue;
            float sum=0;
            for (int j=0;j<4;j++) { weights[i+j]/=maximum; sum+=weights[i+j]; }
            for (int j=0;j<4;j++) weights[i+j]/=sum;
        }
    }

    private float transformPositionX(int matrixOffset, float x, float y, float z) {
        return paletteValues[matrixOffset] * x + paletteValues[matrixOffset + 4] * y
                + paletteValues[matrixOffset + 8] * z + paletteValues[matrixOffset + 12];
    }

    private float determinantSign() {
        float determinant = linear[0]*(linear[4]*linear[8]-linear[5]*linear[7])
                + linear[1]*(linear[5]*linear[6]-linear[3]*linear[8])
                + linear[2]*(linear[3]*linear[7]-linear[4]*linear[6]);
        return determinant < 0 ? -1 : 1;
    }

    private float transformPositionY(int matrixOffset, float x, float y, float z) {
        return paletteValues[matrixOffset + 1] * x + paletteValues[matrixOffset + 5] * y
                + paletteValues[matrixOffset + 9] * z + paletteValues[matrixOffset + 13];
    }

    private float transformPositionZ(int matrixOffset, float x, float y, float z) {
        return paletteValues[matrixOffset + 2] * x + paletteValues[matrixOffset + 6] * y
                + paletteValues[matrixOffset + 10] * z + paletteValues[matrixOffset + 14];
    }

    private static void validateInfluences(int[] joints, float[] weights, int vertexCount) {
        int expectedLength = vertexCount * INFLUENCES_PER_VERTEX;
        if (joints == null || joints.length != expectedLength) {
            throw new FdxException("CpuSkinningMeshUpdater requires four joint indices per vertex");
        }
        if (weights == null || weights.length != expectedLength) {
            throw new FdxException("CpuSkinningMeshUpdater requires four joint weights per vertex");
        }
        for (int i=0;i<weights.length;i++) if (!Float.isFinite(weights[i]) || weights[i]<0 || weights[i]>0 && joints[i]<0)
            throw new FdxException("CPU skinning requires finite nonnegative weights and valid active joints");
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
