package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.PreparedPositionColor3DData;
import io.github.libfdx.graphics.g3d.MorphTarget;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Vector3;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;

/** Versioned, CPU-only worker transport. No source-model identity, material or graphics handle
 * crosses the boundary. Same Java optimizer and vertex packing on every platform. This is an
 * internal transport format, not a persistent model/cache format. */
public final class ModelLodWorkerCodec {
    private static final int VERSION = 0x4c4f4401;
    private ModelLodWorkerCodec() { }

    static byte[] request(ModelLodInput input, ModelLodSettings settings) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(VERSION);
            settings(out, settings);
            out.writeInt(input.geometry.size());
            for(int i = 0; i < input.geometry.size(); i++) {
                MeshLodData mesh = input.geometry.get(i);
                for(int c = 0; c < MeshLodData.CHANNEL_COUNT; c++) floats(out, mesh.channel(c));
                ints(out, mesh.indices());
                deformation(out, mesh.deformation());
                out.writeInt(input.occurrences.get(i));
                out.writeInt(input.sourceVertexCounts.get(i));
            }
            out.writeInt(input.rigidGroups.size());
            for(int[] group : input.rigidGroups) ints(out, group);
            return bytes.toByteArray();
        } catch(IOException error) { throw invalid(error); }
    }

    /** Worker entry: synchronous CPU work intended exclusively for a background worker. */
    public static byte[] execute(byte[] request) {
        try {
            DataInputStream in = input(request);
            ModelLodSettings settings = settings(in);
            ModelLodInput source = new ModelLodInput();
            int count = count(in, 4);
            if(count == 0) throw new IllegalArgumentException("Empty LOD worker input");
            for(int i = 0; i < count; i++) {
                float[][] channels = new float[MeshLodData.CHANNEL_COUNT][];
                for(int c = 0; c < channels.length; c++) channels[c] = floats(in);
                int[] indices = ints(in);
                source.geometry.add(new MeshLodData(channels, indices, deformation(in)));
                int uses = in.readInt(), vertices = in.readInt();
                if(uses < 1 || vertices < 1) throw new IllegalArgumentException("Invalid LOD worker counts");
                source.occurrences.add(uses);
                source.sourceVertexCounts.add(vertices);
            }
            int groups = count(in, 4);
            for(int i = 0; i < groups; i++) {
                int[] group = ints(in);
                if(group == null) throw new IllegalArgumentException("Missing rigid group");
                for(int index : group) if(index < 0 || index >= count) throw new IllegalArgumentException("Invalid rigid group");
                source.rigidGroups.add(group);
            }
            end(in);
            source.countGeometry();
            return response(new ModelLodOptimizer().prepare(source, settings, () -> false));
        } catch(IOException error) { throw invalid(error); }
    }

    private static byte[] response(PreparedModelLods prepared) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(VERSION);
        out.writeInt(prepared.meshes.length);
        out.writeInt(prepared.input.geometry.size());
        IdentityHashMap<LodPreparedMesh[], Integer> shared = new IdentityHashMap<>();
        for(int l = 0; l < prepared.meshes.length; l++) {
            ModelLodReport report = prepared.reports[l];
            out.writeInt(report.sourceTriangles()); out.writeInt(report.triangles());
            out.writeInt(report.vertices()); out.writeInt(report.drawParts());
            out.writeFloat(report.error()); out.writeBoolean(report.targetReached());
            for(LodPreparedMesh[] part : prepared.meshes[l]) {
                if(part == null) { out.writeInt(-1); continue; }
                Integer reference = shared.get(part);
                if(reference != null) { out.writeInt(-2 - reference); continue; }
                shared.put(part, shared.size());
                out.writeInt(part.length);
                for(LodPreparedMesh mesh : part) {
                    PreparedPositionColor3DData data = mesh.preparation().preparedData();
                    for(float[] channel : data.attributes()) floats(out, channel);
                    ints(out, data.joints());
                    vector(out, data.bounds().min()); vector(out, data.bounds().max());
                    ByteBuffer vertices = data.vertices().duplicate().order(ByteOrder.nativeOrder());
                    out.writeInt(vertices.remaining() / Float.BYTES);
                    while(vertices.hasRemaining()) out.writeFloat(vertices.getFloat());
                    out.writeInt(mesh.indices().length);
                    for(short index : mesh.indices()) out.writeShort(index);
                    targets(out, mesh.morphTargets());
                }
            }
        }
        return bytes.toByteArray();
    }

    static PreparedModelLods response(ModelLodInput source, ModelLodSettings settings, byte[] response) {
        try {
            DataInputStream in = input(response);
            int levels = in.readInt(), geometries = in.readInt();
            if(levels != settings.levelCount() || geometries != source.geometry.size())
                throw new IllegalArgumentException("LOD worker result shape mismatch");
            LodPreparedMesh[][][] meshes = new LodPreparedMesh[levels][geometries][];
            ModelLodReport[] reports = new ModelLodReport[levels];
            ArrayList<LodPreparedMesh[]> shared = new ArrayList<>();
            for(int l = 0; l < levels; l++) {
                reports[l] = new ModelLodReport(l + 1, in.readInt(), in.readInt(), in.readInt(),
                        in.readInt(), in.readFloat(), in.readBoolean());
                if(reports[l].sourceTriangles() != source.triangleCount()
                        || reports[l].triangles() < 0 || reports[l].triangles() > source.triangleCount())
                    throw new IllegalArgumentException("Invalid LOD worker report");
                for(int p = 0; p < geometries; p++) {
                    int chunks = in.readInt();
                    if(chunks == -1) continue;
                    if(chunks < -1) {
                        long reference = -(long)chunks - 2;
                        if(reference >= shared.size()) throw new IllegalArgumentException("Invalid shared mesh reference");
                        meshes[l][p] = shared.get((int)reference);
                        continue;
                    }
                    if(chunks < 1 || chunks > in.available() / 4) throw new IllegalArgumentException("Invalid mesh chunk count");
                    LodPreparedMesh[] part = new LodPreparedMesh[chunks];
                    for(int c = 0; c < chunks; c++) {
                        float[][] a = new float[12][];
                        for(int k = 0; k < a.length; k++) a[k] = floats(in);
                        int[] joints = ints(in);
                        BoundingBox bounds = new BoundingBox(vector(in), vector(in));
                        int packed = count(in, Float.BYTES);
                        ByteBuffer vertices = ByteBuffer.allocateDirect(Math.multiplyExact(packed, Float.BYTES)).order(ByteOrder.nativeOrder());
                        for(int k = 0; k < packed; k++) vertices.putFloat(in.readFloat());
                        vertices.flip();
                        int indexCount = count(in, Short.BYTES);
                        if(indexCount == 0 || indexCount % 3 != 0 || a[0] == null) throw new IllegalArgumentException("Invalid mesh triangles");
                        short[] indices = new short[indexCount];
                        int vertexCount = a[0].length / 3;
                        if(vertexCount > 65536) throw new IllegalArgumentException("Invalid mesh vertex range");
                        for(int k = 0; k < indices.length; k++) {
                            indices[k] = in.readShort();
                            if((indices[k] & 65535) >= vertexCount) throw new IllegalArgumentException("Invalid mesh index");
                        }
                        MorphTarget[] targets = targets(in);
                        for(MorphTarget target : targets) if(target.vertexCount() != 0 && target.vertexCount() != vertexCount)
                            throw new IllegalArgumentException("Invalid morph domain");
                        part[c] = new LodPreparedMesh(Mesh.PositionColor3DPreparation.fromPreparedData(
                                new PreparedPositionColor3DData(a, joints, bounds, vertices)), indices, vertexCount, targets);
                    }
                    meshes[l][p] = part;
                    shared.add(part);
                }
            }
            end(in);
            return new PreparedModelLods(source, settings, meshes, reports);
        } catch(IOException error) { throw invalid(error); }
    }

    private static void settings(DataOutputStream out, ModelLodSettings settings) throws IOException {
        out.writeInt(settings.levelCount());
        for(ModelLodTarget t : settings.targets()) { out.writeFloat(t.triangleRatio()); out.writeFloat(t.maxError()); out.writeFloat(t.maxScreenPixels()); }
        out.writeFloat(settings.hysteresis()); out.writeBoolean(settings.lockBorders());
        out.writeFloat(settings.normalWeight()); out.writeFloat(settings.uvWeight()); out.writeFloat(settings.colorWeight());
        out.writeBoolean(settings.optimizeCache()); out.writeBoolean(settings.optimizeFetch());
    }
    private static ModelLodSettings settings(DataInputStream in) throws IOException {
        int count = count(in, 12);
        if(count < 1 || count > 8) throw new IllegalArgumentException("Invalid LOD target count");
        ModelLodTarget[] targets = new ModelLodTarget[count];
        for(int i = 0; i < count; i++) targets[i] = new ModelLodTarget(in.readFloat(), in.readFloat(), in.readFloat());
        return new ModelLodSettings(targets, in.readFloat(), in.readBoolean(), in.readFloat(), in.readFloat(), in.readFloat(), in.readBoolean(), in.readBoolean());
    }
    private static void deformation(DataOutputStream out, MeshLodDeformation d) throws IOException {
        out.writeBoolean(d != null);
        if(d == null) return;
        out.writeInt(d.vertexCount()); out.writeBoolean(d.skinned());
        if(d.skinned()) for(int v = 0; v < d.vertexCount(); v++) for(int i = 0; i < 4; i++) {
            out.writeInt(d.joint(v, i)); out.writeFloat(d.weight(v, i));
        }
        MorphTarget[] targets = new MorphTarget[d.morphTargetCount()];
        for(int i = 0; i < targets.length; i++) targets[i] = d.morphTarget(i);
        targets(out, targets);
        out.writeInt(d.poseCount());
        for(int p = 0; p < d.poseCount(); p++) for(int v = 0; v < d.vertexCount(); v++)
            for(int a = 0; a < 3; a++) out.writeFloat(d.position(p, v, a));
    }
    private static MeshLodDeformation deformation(DataInputStream in) throws IOException {
        if(!in.readBoolean()) return null;
        int vertices = count(in, 4);
        if(vertices < 1) throw new IllegalArgumentException("Invalid deformation extent");
        int[] joints = null; float[] weights = null;
        if(in.readBoolean()) {
            if((long)vertices * 32 > in.available()) throw new IllegalArgumentException("Truncated influences");
            joints = new int[Math.multiplyExact(vertices, 4)]; weights = new float[joints.length];
            for(int i = 0; i < joints.length; i++) { joints[i] = in.readInt(); weights[i] = in.readFloat(); }
        }
        MorphTarget[] targets = targets(in);
        int samples = count(in, Math.multiplyExact(vertices, 12));
        float[][] poses = new float[samples][];
        for(int p = 0; p < samples; p++) {
            poses[p] = new float[Math.multiplyExact(vertices, 3)];
            for(int i = 0; i < poses[p].length; i++) poses[p][i] = in.readFloat();
        }
        return new MeshLodDeformation(vertices, joints, weights, targets, poses);
    }
    private static void targets(DataOutputStream out, MorphTarget[] targets) throws IOException {
        out.writeInt(targets.length);
        for(MorphTarget t : targets) {
            out.writeUTF(t.id()); out.writeFloat(t.weight());
            for(int a = 0; a < 3; a++) {
                out.writeInt(t.hasAttribute(a) ? t.vertexCount() * 3 : -1);
                if(t.hasAttribute(a)) for(int v = 0; v < t.vertexCount(); v++) for(int k = 0; k < 3; k++) out.writeFloat(t.delta(a, v, k));
            }
        }
    }
    private static MorphTarget[] targets(DataInputStream in) throws IOException {
        MorphTarget[] result = new MorphTarget[count(in, 4)];
        for(int i = 0; i < result.length; i++) result[i] = new MorphTarget(in.readUTF(), in.readFloat(), floats(in), floats(in), floats(in));
        return result;
    }
    private static void floats(DataOutputStream out, float[] values) throws IOException {
        out.writeInt(values == null ? -1 : values.length);
        if(values != null) for(float v : values) out.writeFloat(v);
    }
    private static float[] floats(DataInputStream in) throws IOException {
        int count = nullableCount(in, 4);
        if(count == -1) return null;
        float[] values = new float[count];
        for(int i = 0; i < count; i++) {
            values[i] = in.readFloat();
            if(!Float.isFinite(values[i])) throw new IllegalArgumentException("Non-finite worker vertex data");
        }
        return values;
    }
    private static void ints(DataOutputStream out, int[] values) throws IOException {
        out.writeInt(values == null ? -1 : values.length);
        if(values != null) for(int v : values) out.writeInt(v);
    }
    private static int[] ints(DataInputStream in) throws IOException {
        int count = nullableCount(in, 4);
        if(count == -1) return null;
        int[] values = new int[count];
        for(int i = 0; i < count; i++) values[i] = in.readInt();
        return values;
    }
    private static int nullableCount(DataInputStream in, int width) throws IOException {
        int count = in.readInt();
        if(count < -1 || (long)count * width > in.available()) throw new IllegalArgumentException("Invalid worker array extent");
        return count;
    }
    private static int count(DataInputStream in, int width) throws IOException {
        int count = nullableCount(in, width);
        if(count < 0) throw new IllegalArgumentException("Missing worker array");
        return count;
    }
    private static void vector(DataOutputStream out, Vector3 v) throws IOException { out.writeFloat(v.x()); out.writeFloat(v.y()); out.writeFloat(v.z()); }
    private static Vector3 vector(DataInputStream in) throws IOException { return new Vector3(in.readFloat(), in.readFloat(), in.readFloat()); }
    private static DataInputStream input(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if(in.readInt() != VERSION) throw new IllegalArgumentException("LOD worker protocol version mismatch");
        return in;
    }
    private static void end(DataInputStream in) throws IOException { if(in.available() != 0) throw new IllegalArgumentException("Trailing LOD worker data"); }
    private static IllegalArgumentException invalid(IOException error) { return new IllegalArgumentException("Invalid LOD worker data", error); }
}
