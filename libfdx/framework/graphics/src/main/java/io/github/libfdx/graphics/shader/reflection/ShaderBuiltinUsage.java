package io.github.libfdx.graphics.shader.reflection;

/**
 * Stable FDXI bit constants for built-ins used by a reflected entry point.
 */
public final class ShaderBuiltinUsage {
    public static final long CULL_DISTANCE = 1L;
    public static final long POINT_SIZE = 1L << 1;
    public static final long BARYCENTRIC_COORD = 1L << 2;
    public static final long CLIP_DISTANCES = 1L << 3;
    public static final long FRAG_DEPTH = 1L << 4;
    public static final long FRONT_FACING = 1L << 5;
    public static final long GLOBAL_INVOCATION_ID = 1L << 6;
    public static final long GLOBAL_INVOCATION_INDEX = 1L << 7;
    public static final long INSTANCE_INDEX = 1L << 8;
    public static final long LOCAL_INVOCATION_ID = 1L << 9;
    public static final long LOCAL_INVOCATION_INDEX = 1L << 10;
    public static final long NUM_SUBGROUPS = 1L << 11;
    public static final long NUM_WORKGROUPS = 1L << 12;
    public static final long POSITION = 1L << 13;
    public static final long PRIMITIVE_INDEX = 1L << 14;
    public static final long SAMPLE_INDEX = 1L << 15;
    public static final long INPUT_SAMPLE_MASK = 1L << 16;
    public static final long OUTPUT_SAMPLE_MASK = 1L << 17;
    public static final long SUBGROUP_ID = 1L << 18;
    public static final long SUBGROUP_INVOCATION_ID = 1L << 19;
    public static final long SUBGROUP_SIZE = 1L << 20;
    public static final long VERTEX_INDEX = 1L << 21;
    public static final long WORKGROUP_ID = 1L << 22;
    public static final long WORKGROUP_INDEX = 1L << 23;
    public static final long ALL = (1L << 24) - 1;

    private ShaderBuiltinUsage() {
    }
}
