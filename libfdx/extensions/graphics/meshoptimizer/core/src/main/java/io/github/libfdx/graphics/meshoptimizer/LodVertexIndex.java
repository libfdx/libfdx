package io.github.libfdx.graphics.meshoptimizer;

/** Incremental exact vertex lookup. Signed zero compares equal; inputs are finite. */
final class LodVertexIndex {
    private final MeshLodData source;
    private final int channels;
    private final int[] table;

    LodVertexIndex(MeshLodData source, boolean positionsOnly) {
        this.source = source;
        channels = positionsOnly ? 1 : MeshLodData.CHANNEL_COUNT;
        int capacity = 16;
        while (capacity < source.vertexCount() * 2L) {
            if (capacity >= 1 << 29) throw new IllegalArgumentException("Mesh exceeds optimizer capacity");
            capacity <<= 1;
        }
        table = new int[capacity];
    }

    int add(int vertex) {
        int slot = hash(vertex) & (table.length - 1);
        while (table[slot] != 0 && !same(vertex, table[slot] - 1)) slot = (slot + 1) & (table.length - 1);
        if (table[slot] == 0) table[slot] = vertex + 1;
        return table[slot] - 1;
    }

    private int hash(int vertex) {
        int hash = 0x811c9dc5;
        for (int c = 0; c < channels; c++) {
            float[] data = source.channel(c);
            if (data == null) continue;
            int width = MeshLodData.components(c);
            for (int k = 0; k < width; k++) {
                float value = data[vertex * width + k];
                hash = (hash ^ (value == 0 ? 0 : Float.floatToIntBits(value))) * 0x01000193;
            }
        }
        if (source.deformation()!=null) hash=source.deformation().hash(source.sourceVertex(vertex),hash);
        return hash ^ (hash >>> 16);
    }

    private boolean same(int a, int b) {
        for (int c = 0; c < channels; c++) {
            float[] data = source.channel(c);
            if (data == null) continue;
            int width = MeshLodData.components(c);
            for (int k = 0; k < width; k++) if (data[a * width + k] != data[b * width + k]) return false;
        }
        return source.deformation()==null || source.deformation().same(source.sourceVertex(a),source.sourceVertex(b));
    }
}
