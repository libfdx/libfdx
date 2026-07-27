package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.core.FdxException;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable physical and semantic description of one buffer parameter.
 */
public final class ShaderParameter {
    private static final ShaderParameter[] EMPTY_MEMBERS = new ShaderParameter[0];

    private final String stableId;
    private final String name;
    private final String sourcePath;
    private final ShaderValueType valueType;
    private final long byteOffset;
    private final long occupiedSize;
    private final long minimumRequiredSize;
    private final long alignment;
    private final ShaderParameterDomain domain;
    private final ShaderUpdateFrequency updateFrequency;
    private final ShaderParameter[] members;

    private ShaderParameter(Builder builder) {
        stableId = requireName(builder.stableId, "Shader parameter stable ID");
        name = requireName(builder.name, "Shader parameter name");
        sourcePath = builder.sourcePath != null && !builder.sourcePath.trim().isEmpty()
                ? builder.sourcePath : name;
        if (builder.valueType == null || builder.valueType.kind() == ShaderValueKind.UNKNOWN) {
            throw new FdxException("Shader parameter type cannot be unknown");
        }
        if (builder.byteOffset < 0) {
            throw new FdxException("Shader parameter byte offset cannot be negative");
        }
        if (builder.occupiedSize < 0) {
            throw new FdxException("Shader parameter occupied size cannot be negative");
        }
        if (builder.minimumRequiredSize < -1) {
            throw new FdxException("Shader parameter minimum required size cannot be negative");
        }
        if (!isPowerOfTwo(builder.alignment)) {
            throw new FdxException("Shader parameter alignment must be a positive power of two");
        }
        long resolvedArrayStride = builder.arrayStride != 0
                ? builder.arrayStride : builder.valueType.arrayStride();
        long resolvedMatrixStride = builder.matrixStride != 0
                ? builder.matrixStride : findMatrixStride(builder.valueType);
        if (builder.valueType.kind() == ShaderValueKind.ARRAY && resolvedArrayStride <= 0) {
            throw new FdxException("Shader array parameter stride must be positive");
        }
        if (builder.valueType.kind() != ShaderValueKind.ARRAY && builder.arrayStride != 0) {
            throw new FdxException("Only shader array parameters can have an array stride");
        }
        if (containsMatrix(builder.valueType) && resolvedMatrixStride <= 0) {
            throw new FdxException("Shader matrix parameter stride must be positive");
        }
        if (!containsMatrix(builder.valueType) && builder.matrixStride != 0) {
            throw new FdxException("Only shader matrix parameters can have a matrix stride");
        }
        valueType = applyRootStrides(builder.valueType, resolvedArrayStride, resolvedMatrixStride);
        byteOffset = builder.byteOffset;
        occupiedSize = builder.occupiedSize;
        minimumRequiredSize = builder.minimumRequiredSize >= 0
                ? builder.minimumRequiredSize : builder.occupiedSize;
        alignment = builder.alignment;
        domain = builder.domain != null ? builder.domain : ShaderParameterDomain.UNSPECIFIED;
        updateFrequency = builder.updateFrequency != null
                ? builder.updateFrequency : ShaderUpdateFrequency.UNSPECIFIED;
        members = builder.members != null ? builder.members.clone() : EMPTY_MEMBERS;
        validateMembers();
    }

    /**
     * Creates a parameter builder.
     *
     * @param stableId the stable parameter identifier
     * @param name the source/display name
     * @param valueType the value type
     * @param byteOffset the absolute byte offset in the buffer
     * @param occupiedSize the occupied byte size
     * @param alignment the required byte alignment
     * @return the builder
     */
    public static Builder builder(String stableId, String name, ShaderValueType valueType, long byteOffset,
            long occupiedSize, long alignment) {
        return new Builder(stableId, name, valueType, byteOffset, occupiedSize, alignment);
    }

    /**
     * Creates a parameter whose stable ID and name are the same.
     *
     * @param name the stable source name
     * @param valueType the value type
     * @param byteOffset the byte offset
     * @param occupiedSize the occupied size
     * @param alignment the alignment
     * @return the parameter
     */
    public static ShaderParameter of(String name, ShaderValueType valueType, long byteOffset, long occupiedSize,
            long alignment) {
        return builder(name, name, valueType, byteOffset, occupiedSize, alignment).build();
    }

    public String stableId() {
        return stableId;
    }

    public String name() {
        return name;
    }

    /**
     * Returns the reflected source-path template. Array element prototypes use {@code []}.
     *
     * @return the source path
     */
    public String sourcePath() {
        return sourcePath;
    }

    public ShaderValueType valueType() {
        return valueType;
    }

    public long byteOffset() {
        return byteOffset;
    }

    public long occupiedSize() {
        return occupiedSize;
    }

    public long minimumRequiredSize() {
        return minimumRequiredSize;
    }

    public long alignment() {
        return alignment;
    }

    public long arrayStride() {
        return valueType.kind() == ShaderValueKind.ARRAY ? valueType.arrayStride() : 0;
    }

    public long matrixStride() {
        return findMatrixStride(valueType);
    }

    public ShaderParameterDomain domain() {
        return domain;
    }

    public ShaderUpdateFrequency updateFrequency() {
        return updateFrequency;
    }

    public ShaderParameter[] members() {
        return members.clone();
    }

    public int memberCount() {
        return members.length;
    }

    public ShaderParameter member(int index) {
        return members[index];
    }

    ShaderParameter withMetadata(String newStableId, ShaderParameterDomain newDomain,
            ShaderUpdateFrequency newFrequency, ShaderParameter[] newMembers) {
        return builder(newStableId, name, valueType, byteOffset, occupiedSize, alignment)
                .minimumRequiredSize(minimumRequiredSize)
                .sourcePath(sourcePath)
                .arrayStride(arrayStride())
                .matrixStride(matrixStride())
                .semantics(newDomain, newFrequency)
                .members(newMembers)
                .build();
    }

    private void validateMembers() {
        if (valueType.kind() == ShaderValueKind.STRUCT && members.length == 0) {
            throw new FdxException("Shader structure parameter must contain members: " + name);
        }
        if (valueType.kind() != ShaderValueKind.STRUCT && valueType.kind() != ShaderValueKind.ARRAY
                && valueType.kind() != ShaderValueKind.BUFFER
                && members.length != 0) {
            throw new FdxException("Only shader structure or array parameters can contain members: " + name);
        }
        for (int i = 0; i < members.length; i++) {
            ShaderParameter member = members[i];
            if (member == null) {
                throw new FdxException("Shader structure member cannot be null: " + name);
            }
            if (!(valueType.kind() == ShaderValueKind.ARRAY && valueType.arrayCount() < 0)) {
                long end = checkedAdd(member.byteOffset, member.occupiedSize, "Shader structure member range");
                long parentEnd = checkedAdd(byteOffset, occupiedSize, "Shader structure parameter range");
                if (member.byteOffset < byteOffset || end > parentEnd) {
                    throw new FdxException("Shader structure member exceeds its parent: " + name + '.'
                            + member.name);
                }
            }
            for (int j = 0; j < i; j++) {
                if (members[j].name.equals(member.name) || members[j].stableId.equals(member.stableId)) {
                    throw new FdxException("Duplicate shader structure member: " + name + '.' + member.name);
                }
            }
        }
    }

    private static boolean containsMatrix(ShaderValueType type) {
        return type.kind() == ShaderValueKind.MATRIX
                || (type.kind() == ShaderValueKind.ARRAY && containsMatrix(type.elementType()));
    }

    private static long findMatrixStride(ShaderValueType type) {
        if (type.kind() == ShaderValueKind.MATRIX) {
            return type.matrixStride();
        }
        if (type.kind() == ShaderValueKind.ARRAY) {
            return findMatrixStride(type.elementType());
        }
        return 0;
    }

    private static ShaderValueType applyRootStrides(ShaderValueType type, long arrayStride, long matrixStride) {
        if (type.kind() == ShaderValueKind.MATRIX) {
            if (type.matrixStride() != 0 && type.matrixStride() != matrixStride) {
                throw new FdxException("Shader matrix type stride does not match its parameter stride");
            }
            return type.withRootStrides(0, matrixStride).named(type.typeName());
        }
        if (type.kind() == ShaderValueKind.ARRAY) {
            if (type.arrayStride() != 0 && type.arrayStride() != arrayStride) {
                throw new FdxException("Shader array type stride does not match its parameter stride");
            }
            ShaderValueType element = applyNestedMatrixStride(type.elementType(), matrixStride);
            return (type.arrayCount() < 0 ? ShaderValueType.runtimeArray(element, arrayStride)
                    : ShaderValueType.array(element, type.arrayCount(), arrayStride)).named(type.typeName());
        }
        return type;
    }

    private static ShaderValueType applyNestedMatrixStride(ShaderValueType type, long matrixStride) {
        if (type.kind() == ShaderValueKind.MATRIX) {
            if (type.matrixStride() != 0 && type.matrixStride() != matrixStride) {
                throw new FdxException("Nested shader matrix stride does not match its parameter stride");
            }
            return type.withRootStrides(0, matrixStride).named(type.typeName());
        }
        if (type.kind() == ShaderValueKind.ARRAY) {
            ShaderValueType element = applyNestedMatrixStride(type.elementType(), matrixStride);
            return (type.arrayCount() < 0 ? ShaderValueType.runtimeArray(element, type.arrayStride())
                    : ShaderValueType.array(element, type.arrayCount(), type.arrayStride())).named(type.typeName());
        }
        return type;
    }

    private static boolean isPowerOfTwo(long value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    private static long checkedAdd(long first, long second, String label) {
        if (first > Long.MAX_VALUE - second) {
            throw new FdxException(label + " overflows");
        }
        return first + second;
    }

    private static String requireName(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new FdxException(label + " cannot be empty");
        }
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ShaderParameter other)) {
            return false;
        }
        return byteOffset == other.byteOffset && occupiedSize == other.occupiedSize
                && alignment == other.alignment && minimumRequiredSize == other.minimumRequiredSize
                && stableId.equals(other.stableId) && name.equals(other.name)
                && sourcePath.equals(other.sourcePath) && valueType.equals(other.valueType) && domain == other.domain
                && updateFrequency == other.updateFrequency && Arrays.equals(members, other.members);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(stableId, name, sourcePath, valueType, byteOffset, occupiedSize, minimumRequiredSize,
                alignment, domain, updateFrequency);
        return 31 * result + Arrays.hashCode(members);
    }

    /**
     * Builds immutable shader parameters.
     */
    public static final class Builder {
        private final String stableId;
        private final String name;
        private final ShaderValueType valueType;
        private final long byteOffset;
        private final long occupiedSize;
        private final long alignment;
        private long minimumRequiredSize = -1;
        private long arrayStride;
        private long matrixStride;
        private String sourcePath;
        private ShaderParameterDomain domain = ShaderParameterDomain.UNSPECIFIED;
        private ShaderUpdateFrequency updateFrequency = ShaderUpdateFrequency.UNSPECIFIED;
        private ShaderParameter[] members = EMPTY_MEMBERS;

        private Builder(String stableId, String name, ShaderValueType valueType, long byteOffset, long occupiedSize,
                long alignment) {
            this.stableId = stableId;
            this.name = name;
            this.valueType = valueType;
            this.byteOffset = byteOffset;
            this.occupiedSize = occupiedSize;
            this.alignment = alignment;
        }

        public Builder minimumRequiredSize(long minimumRequiredSize) {
            this.minimumRequiredSize = minimumRequiredSize;
            return this;
        }

        public Builder sourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
            return this;
        }

        public Builder arrayStride(long arrayStride) {
            this.arrayStride = arrayStride;
            return this;
        }

        public Builder matrixStride(long matrixStride) {
            this.matrixStride = matrixStride;
            return this;
        }

        public Builder semantics(ShaderParameterDomain domain, ShaderUpdateFrequency updateFrequency) {
            this.domain = domain != null ? domain : ShaderParameterDomain.UNSPECIFIED;
            this.updateFrequency = updateFrequency != null ? updateFrequency : ShaderUpdateFrequency.UNSPECIFIED;
            return this;
        }

        public Builder members(ShaderParameter... members) {
            this.members = members != null ? members.clone() : EMPTY_MEMBERS;
            return this;
        }

        public ShaderParameter build() {
            return new ShaderParameter(this);
        }
    }
}
