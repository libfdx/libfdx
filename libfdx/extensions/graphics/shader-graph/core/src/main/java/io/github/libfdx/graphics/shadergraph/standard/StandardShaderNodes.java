package io.github.libfdx.graphics.shadergraph.standard;

/**
 * Stable IDs of built-in graph operations.
 */
public final class StandardShaderNodes {
    public static final String CONSTANT = "value.constant";
    public static final String PARAMETER = "value.parameter";
    public static final String RESOURCE = "value.resource";
    public static final String ADD = "math.add";
    public static final String SUBTRACT = "math.subtract";
    public static final String MULTIPLY = "math.multiply";
    public static final String DIVIDE = "math.divide";
    public static final String MINIMUM = "math.minimum";
    public static final String MAXIMUM = "math.maximum";
    public static final String NEGATE = "math.negate";
    public static final String ABSOLUTE = "math.absolute";
    public static final String NORMALIZE = "math.normalize";
    public static final String DOT = "math.dot";
    public static final String CROSS = "math.cross";
    public static final String CLAMP = "math.clamp";
    public static final String LERP = "math.lerp";
    public static final String CONSTRUCT = "value.construct";
    public static final String CONVERT = "value.convert";
    public static final String MEMBER = "value.member";
    public static final String BRANCH = "control.branch";
    public static final String SWITCH = "control.switch";
    public static final String LOOP = "control.loop";
    public static final String TEXTURE_SAMPLE = "texture.sample";
    public static final String FUNCTION_CALL = "function.call";
    public static final String DERIVATIVE_X = "fragment.derivative-x";
    public static final String DERIVATIVE_Y = "fragment.derivative-y";
    public static final String DISCARD = "fragment.discard";
    public static final String CUSTOM_FUNCTION = "function.custom-wgsl";
    public static final String ATOMIC_ADD = "compute.atomic-add";
    public static final String STORAGE_LOAD = "compute.storage-load";
    public static final String STORAGE_STORE = "compute.storage-store";
    public static final String BARRIER = "compute.barrier";

    private StandardShaderNodes() {
    }
}
