package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.graphics.shader.ShaderOverride;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.core.FdxException;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable complete reflection of one shader entry point.
 */
public final class ShaderEntryPoint {
    private static final ShaderStageVariable[] EMPTY_VARIABLES = new ShaderStageVariable[0];
    private static final ShaderOverride[] EMPTY_OVERRIDES = new ShaderOverride[0];
    private static final ShaderResourceUse[] EMPTY_RESOURCES = new ShaderResourceUse[0];

    private final String name;
    private final ShaderStage stage;
    private final ShaderWorkgroupSizeKind workgroupSizeKind;
    private final int workgroupX;
    private final int workgroupY;
    private final int workgroupZ;
    private final long builtinMask;
    private final int clipDistanceSize;
    private final ShaderStageVariable[] inputs;
    private final ShaderStageVariable[] outputs;
    private final ShaderOverride[] overrides;
    private final ShaderResourceUse[] resources;

    private ShaderEntryPoint(Builder builder) {
        if (builder.name == null || builder.name.trim().isEmpty()) {
            throw new FdxException("Shader entry-point name cannot be empty");
        }
        if (builder.stage == null) {
            throw new FdxException("Shader entry-point stage cannot be null");
        }
        name = builder.name;
        stage = builder.stage;
        workgroupSizeKind = builder.workgroupSizeKind != null
                ? builder.workgroupSizeKind : ShaderWorkgroupSizeKind.NOT_APPLICABLE;
        workgroupX = builder.workgroupX;
        workgroupY = builder.workgroupY;
        workgroupZ = builder.workgroupZ;
        validateWorkgroup();
        builtinMask = builder.builtinMask;
        clipDistanceSize = builder.clipDistanceSize;
        if (clipDistanceSize < -1) {
            throw new FdxException("Shader clip-distance size cannot be less than -1");
        }
        inputs = cloneAndRequire(builder.inputs, EMPTY_VARIABLES, "input");
        outputs = cloneAndRequire(builder.outputs, EMPTY_VARIABLES, "output");
        overrides = cloneAndRequire(builder.overrides, EMPTY_OVERRIDES, "override");
        resources = cloneAndRequire(builder.resources, EMPTY_RESOURCES, "resource use");
        validateDuplicates();
    }

    public static Builder builder(String name, ShaderStage stage) {
        return new Builder(name, stage);
    }

    public String name() {
        return name;
    }

    public ShaderStage stage() {
        return stage;
    }

    public ShaderWorkgroupSizeKind workgroupSizeKind() {
        return workgroupSizeKind;
    }

    public int workgroupX() {
        return workgroupX;
    }

    public int workgroupY() {
        return workgroupY;
    }

    public int workgroupZ() {
        return workgroupZ;
    }

    public long builtinMask() {
        return builtinMask;
    }

    public boolean usesBuiltin(long bit) {
        return (builtinMask & bit) != 0;
    }

    public int clipDistanceSize() {
        return clipDistanceSize;
    }

    public ShaderStageVariable[] inputs() {
        return inputs.clone();
    }

    public ShaderStageVariable[] outputs() {
        return outputs.clone();
    }

    public ShaderOverride[] overrides() {
        return overrides.clone();
    }

    public ShaderResourceUse[] resources() {
        return resources.clone();
    }

    public int inputCount() {
        return inputs.length;
    }

    public ShaderStageVariable input(int index) {
        return inputs[index];
    }

    public int outputCount() {
        return outputs.length;
    }

    public ShaderStageVariable output(int index) {
        return outputs[index];
    }

    public int overrideCount() {
        return overrides.length;
    }

    public ShaderOverride override(int index) {
        return overrides[index];
    }

    public int resourceCount() {
        return resources.length;
    }

    public ShaderResourceUse resource(int index) {
        return resources[index];
    }

    private void validateWorkgroup() {
        if (stage != ShaderStage.COMPUTE && workgroupSizeKind != ShaderWorkgroupSizeKind.NOT_APPLICABLE) {
            throw new FdxException("Only compute entry points can have a workgroup size");
        }
        if (workgroupSizeKind == ShaderWorkgroupSizeKind.FIXED) {
            if (workgroupX <= 0 || workgroupY <= 0 || workgroupZ <= 0) {
                throw new FdxException("Fixed shader workgroup dimensions must be positive");
            }
        } else if (workgroupX != 0 || workgroupY != 0 || workgroupZ != 0) {
            throw new FdxException("Non-fixed shader workgroup dimensions must be zero");
        }
    }

    private void validateDuplicates() {
        for (int i = 0; i < resources.length; i++) {
            for (int j = 0; j < i; j++) {
                if (resources[i].group() == resources[j].group()
                        && resources[i].binding() == resources[j].binding()) {
                    throw new FdxException("Duplicate shader entry-point resource use: " + name + " group "
                            + resources[i].group() + " binding " + resources[i].binding());
                }
            }
        }
        for (int i = 0; i < overrides.length; i++) {
            for (int j = 0; j < i; j++) {
                if (overrides[i].id() == overrides[j].id() || overrides[i].name().equals(overrides[j].name())) {
                    throw new FdxException("Duplicate shader entry-point override: " + name + ' '
                            + overrides[i].name());
                }
            }
        }
    }

    private static <T> T[] cloneAndRequire(T[] values, T[] empty, String label) {
        T[] result = values != null ? values.clone() : empty;
        for (T value : result) {
            if (value == null) {
                throw new FdxException("Shader entry-point " + label + " cannot be null");
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ShaderEntryPoint other)) {
            return false;
        }
        return workgroupX == other.workgroupX && workgroupY == other.workgroupY && workgroupZ == other.workgroupZ
                && builtinMask == other.builtinMask && clipDistanceSize == other.clipDistanceSize
                && name.equals(other.name) && stage == other.stage && workgroupSizeKind == other.workgroupSizeKind
                && Arrays.equals(inputs, other.inputs) && Arrays.equals(outputs, other.outputs)
                && Arrays.equals(overrides, other.overrides) && Arrays.equals(resources, other.resources);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, stage, workgroupSizeKind, workgroupX, workgroupY, workgroupZ, builtinMask,
                clipDistanceSize);
        result = 31 * result + Arrays.hashCode(inputs);
        result = 31 * result + Arrays.hashCode(outputs);
        result = 31 * result + Arrays.hashCode(overrides);
        return 31 * result + Arrays.hashCode(resources);
    }

    /**
     * Builds entry-point descriptions.
     */
    public static final class Builder {
        private final String name;
        private final ShaderStage stage;
        private ShaderWorkgroupSizeKind workgroupSizeKind = ShaderWorkgroupSizeKind.NOT_APPLICABLE;
        private int workgroupX;
        private int workgroupY;
        private int workgroupZ;
        private long builtinMask;
        private int clipDistanceSize = -1;
        private ShaderStageVariable[] inputs = EMPTY_VARIABLES;
        private ShaderStageVariable[] outputs = EMPTY_VARIABLES;
        private ShaderOverride[] overrides = EMPTY_OVERRIDES;
        private ShaderResourceUse[] resources = EMPTY_RESOURCES;

        private Builder(String name, ShaderStage stage) {
            this.name = name;
            this.stage = stage;
        }

        public Builder fixedWorkgroupSize(int x, int y, int z) {
            workgroupSizeKind = ShaderWorkgroupSizeKind.FIXED;
            workgroupX = x;
            workgroupY = y;
            workgroupZ = z;
            return this;
        }

        public Builder overrideDependentWorkgroupSize() {
            workgroupSizeKind = ShaderWorkgroupSizeKind.OVERRIDE_DEPENDENT;
            workgroupX = 0;
            workgroupY = 0;
            workgroupZ = 0;
            return this;
        }

        public Builder builtins(long builtinMask, int clipDistanceSize) {
            this.builtinMask = builtinMask;
            this.clipDistanceSize = clipDistanceSize;
            return this;
        }

        public Builder inputs(ShaderStageVariable... inputs) {
            this.inputs = inputs;
            return this;
        }

        public Builder outputs(ShaderStageVariable... outputs) {
            this.outputs = outputs;
            return this;
        }

        public Builder overrides(ShaderOverride... overrides) {
            this.overrides = overrides;
            return this;
        }

        public Builder resources(ShaderResourceUse... resources) {
            this.resources = resources;
            return this;
        }

        public ShaderEntryPoint build() {
            return new ShaderEntryPoint(this);
        }
    }
}
