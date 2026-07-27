package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

/**
 * Stable semantic tokens used by complete stage graphs.
 */
public final class ShaderGraphStageSemantic {
    public static final String POSITION = "builtin.position";
    public static final String VERTEX_INDEX = "builtin.vertex_index";
    public static final String INSTANCE_INDEX = "builtin.instance_index";
    public static final String FRONT_FACING = "builtin.front_facing";
    public static final String SAMPLE_INDEX = "builtin.sample_index";
    public static final String SAMPLE_MASK = "builtin.sample_mask";
    public static final String FRAGMENT_DEPTH = "builtin.frag_depth";
    public static final String GLOBAL_INVOCATION_ID =
            "builtin.global_invocation_id";
    public static final String LOCAL_INVOCATION_ID =
            "builtin.local_invocation_id";
    public static final String LOCAL_INVOCATION_INDEX =
            "builtin.local_invocation_index";
    public static final String WORKGROUP_ID = "builtin.workgroup_id";
    public static final String NUM_WORKGROUPS = "builtin.num_workgroups";

    private ShaderGraphStageSemantic() {
    }

    public static String location(int index) {
        if (index < 0) {
            throw new FdxException("Shader stage location cannot be negative");
        }
        return "location." + index;
    }

    /**
     * Parses a location semantic.
     *
     * @param semantic the semantic token
     * @return the location index, or {@code -1} when the token is not a valid
     *         location semantic
     */
    public static int location(String semantic) {
        if (semantic == null) {
            return -1;
        }
        String value = semantic.trim().toLowerCase();
        if (!value.startsWith("location.")) {
            return -1;
        }
        try {
            int result = Integer.parseInt(value.substring("location.".length()));
            return result >= 0 ? result : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /**
     * Returns whether a semantic token names a shader builtin.
     *
     * @param semantic the semantic token
     * @return whether the token is a builtin semantic
     */
    public static boolean builtin(String semantic) {
        return semantic != null
                && semantic.trim().toLowerCase().startsWith("builtin.");
    }

    /**
     * Returns the backend builtin name from a builtin semantic token.
     *
     * @param semantic the builtin semantic token
     * @return the builtin name
     */
    public static String builtinName(String semantic) {
        return semantic.trim().toLowerCase().substring("builtin.".length());
    }
}
