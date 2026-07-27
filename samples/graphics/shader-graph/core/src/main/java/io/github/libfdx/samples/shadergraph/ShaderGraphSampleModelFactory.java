package io.github.libfdx.samples.shadergraph;

import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.g3d.DefaultModel;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.MeshPart;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Vector3;

import java.util.ArrayList;

/**
 * Creates procedural PBR-layout geometry for the sample.
 */
final class ShaderGraphSampleModelFactory {
    private ShaderGraphSampleModelFactory() {
    }

    static Model box(GraphicsContext graphics, String id,
            float width, float height, float depth, Material material) {
        float hx = width * 0.5f;
        float hy = height * 0.5f;
        float hz = depth * 0.5f;
        ArrayList<Float> positions = new ArrayList<>();
        ArrayList<Float> normals = new ArrayList<>();
        ArrayList<Float> texCoords = new ArrayList<>();
        ArrayList<Float> colors = new ArrayList<>();
        ArrayList<Float> pbr = new ArrayList<>();
        ArrayList<Float> emissive = new ArrayList<>();
        addFace(positions, normals, texCoords, colors, pbr, emissive,
                -hx, -hy, hz, hx, -hy, hz, hx, hy, hz, -hx, hy, hz,
                0.0f, 0.0f, 1.0f, 1.00f, 0.72f, 0.52f);
        addFace(positions, normals, texCoords, colors, pbr, emissive,
                hx, -hy, -hz, -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz,
                0.0f, 0.0f, -1.0f, 0.50f, 0.70f, 1.00f);
        addFace(positions, normals, texCoords, colors, pbr, emissive,
                -hx, hy, hz, hx, hy, hz, hx, hy, -hz, -hx, hy, -hz,
                0.0f, 1.0f, 0.0f, 1.00f, 0.88f, 0.56f);
        addFace(positions, normals, texCoords, colors, pbr, emissive,
                -hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz,
                0.0f, -1.0f, 0.0f, 0.46f, 0.62f, 0.78f);
        addFace(positions, normals, texCoords, colors, pbr, emissive,
                hx, -hy, hz, hx, -hy, -hz, hx, hy, -hz, hx, hy, hz,
                1.0f, 0.0f, 0.0f, 0.72f, 0.56f, 0.96f);
        addFace(positions, normals, texCoords, colors, pbr, emissive,
                -hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz,
                -1.0f, 0.0f, 0.0f, 0.48f, 0.90f, 0.84f);

        Mesh mesh = Mesh.positionColor3D(graphics, id,
                floats(positions), floats(colors), floats(normals),
                floats(texCoords), floats(pbr), floats(emissive),
                BoundingBox.of(new Vector3(-hx, -hy, -hz),
                        new Vector3(hx, hy, hz)));
        MeshPart part = new MeshPart(id + " part", mesh, null,
                0, mesh.vertexCount());
        return DefaultModel.singleNode(id, part, material);
    }

    private static void addFace(ArrayList<Float> positions,
            ArrayList<Float> normals, ArrayList<Float> texCoords,
            ArrayList<Float> colors, ArrayList<Float> pbr,
            ArrayList<Float> emissive,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float nx, float ny, float nz,
            float red, float green, float blue) {
        addVertex(positions, normals, texCoords, colors, pbr, emissive,
                x0, y0, z0, nx, ny, nz, 0.0f, 0.0f,
                red, green, blue);
        addVertex(positions, normals, texCoords, colors, pbr, emissive,
                x1, y1, z1, nx, ny, nz, 1.0f, 0.0f,
                red, green, blue);
        addVertex(positions, normals, texCoords, colors, pbr, emissive,
                x2, y2, z2, nx, ny, nz, 1.0f, 1.0f,
                red, green, blue);
        addVertex(positions, normals, texCoords, colors, pbr, emissive,
                x0, y0, z0, nx, ny, nz, 0.0f, 0.0f,
                red, green, blue);
        addVertex(positions, normals, texCoords, colors, pbr, emissive,
                x2, y2, z2, nx, ny, nz, 1.0f, 1.0f,
                red, green, blue);
        addVertex(positions, normals, texCoords, colors, pbr, emissive,
                x3, y3, z3, nx, ny, nz, 0.0f, 1.0f,
                red, green, blue);
    }

    private static void addVertex(ArrayList<Float> positions,
            ArrayList<Float> normals, ArrayList<Float> texCoords,
            ArrayList<Float> colors, ArrayList<Float> pbr,
            ArrayList<Float> emissive,
            float x, float y, float z,
            float nx, float ny, float nz,
            float u, float v,
            float red, float green, float blue) {
        positions.add(x);
        positions.add(y);
        positions.add(z);
        normals.add(nx);
        normals.add(ny);
        normals.add(nz);
        texCoords.add(u);
        texCoords.add(v);
        colors.add(red);
        colors.add(green);
        colors.add(blue);
        colors.add(1.0f);
        pbr.add(1.0f);
        pbr.add(1.0f);
        pbr.add(1.0f);
        emissive.add(0.0f);
        emissive.add(0.0f);
        emissive.add(0.0f);
    }

    private static float[] floats(ArrayList<Float> source) {
        float[] result = new float[source.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = source.get(i);
        }
        return result;
    }
}
