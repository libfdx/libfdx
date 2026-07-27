package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable physical layout of one host-shareable shader buffer.
 *
 * <p>Fixed-array element handles are derived and cached on demand. A valid reflection containing
 * a very large array therefore cannot cause unbounded eager allocation.</p>
 */
public final class ShaderParameterLayout {
    private static final AtomicLong NEXT_IDENTITY = new AtomicLong(1);
    private static final ShaderParameter[] EMPTY_PARAMETERS = new ShaderParameter[0];

    private final long identity;
    private final long minimumBindingSize;
    private final long alignment;
    private final ShaderParameter[] parameters;
    private final Map<String, ShaderParameter> parameterTemplates;
    private final ConcurrentHashMap<String, ShaderParameterHandle> handles;
    private final String physicalHash;

    private ShaderParameterLayout(long minimumBindingSize, long alignment, ShaderParameter[] parameters) {
        if (minimumBindingSize < 0) {
            throw new FdxException("Shader parameter layout minimum binding size cannot be negative");
        }
        if (!isPowerOfTwo(alignment)) {
            throw new FdxException("Shader parameter layout alignment must be a positive power of two");
        }
        identity = nextIdentity();
        this.minimumBindingSize = minimumBindingSize;
        this.alignment = alignment;
        this.parameters = parameters != null ? parameters.clone() : EMPTY_PARAMETERS;
        parameterTemplates = new LinkedHashMap<>();
        handles = new ConcurrentHashMap<>();
        validateAndRegister();
        physicalHash = computePhysicalHash();
    }

    public static ShaderParameterLayout of(long minimumBindingSize, long alignment, ShaderParameter... parameters) {
        return new ShaderParameterLayout(minimumBindingSize, alignment, parameters);
    }

    public long identity() {
        return identity;
    }

    public long minimumBindingSize() {
        return minimumBindingSize;
    }

    public long alignment() {
        return alignment;
    }

    public ShaderParameter[] parameters() {
        return parameters.clone();
    }

    public int parameterCount() {
        return parameters.length;
    }

    public ShaderParameter parameter(int index) {
        return parameters[index];
    }

    public String physicalHash() {
        return physicalHash;
    }

    public ShaderParameterHandle findHandle(String path) {
        if (path == null) {
            return null;
        }
        ShaderParameterHandle direct = handles.get(path);
        if (direct != null) {
            return direct;
        }
        ShaderParameterHandle derived = deriveHandle(path);
        if (derived == null) {
            return null;
        }
        ShaderParameterHandle raced = handles.putIfAbsent(path, derived);
        return raced != null ? raced : derived;
    }

    public ShaderParameterHandle requireHandle(String path) {
        ShaderParameterHandle handle = findHandle(path);
        if (handle == null) {
            throw new FdxException("Unknown shader parameter path: " + path);
        }
        return handle;
    }

    public ShaderParameterHandle requireArrayElementHandle(String path, int index) {
        ShaderParameterHandle array = requireHandle(path);
        if (array.valueType().kind() != ShaderValueKind.ARRAY) {
            throw new FdxException("Shader parameter is not an array: " + path);
        }
        validateArrayIndex(array.valueType(), index, path);
        String elementPath = path + '[' + index + ']';
        ShaderParameterHandle existing = handles.get(elementPath);
        if (existing != null) {
            return existing;
        }
        ShaderValueType elementType = array.valueType().elementType();
        long elementOffset = checkedAdd(array.byteOffset(), checkedMultiply(index, array.valueType().arrayStride(),
                "Shader array byte offset"), "Shader array byte offset");
        ShaderParameterHandle derived = new ShaderParameterHandle(this, elementPath, elementType, elementOffset,
                physicalOccupiedSize(elementType, array.valueType().arrayStride()), array.alignment(),
                elementType.kind() == ShaderValueKind.ARRAY ? elementType.arrayStride() : 0,
                findMatrixStride(elementType));
        ShaderParameterHandle raced = handles.putIfAbsent(elementPath, derived);
        return raced != null ? raced : derived;
    }

    public ShaderParameterHandle requireComponentHandle(String path, int index) {
        ShaderParameterHandle vector = requireHandle(path);
        if (vector.valueType().kind() != ShaderValueKind.VECTOR) {
            throw new FdxException("Shader parameter is not a vector: " + path);
        }
        if (index < 0 || index >= vector.valueType().rows()) {
            throw new FdxException("Shader vector component is out of range: " + path + '[' + index + ']');
        }
        return requireHandle(path + '.' + componentName(index));
    }

    public boolean owns(ShaderParameterHandle handle) {
        return handle != null && handle.belongsTo(this);
    }

    public boolean physicallyEquivalent(ShaderParameterLayout other) {
        return other != null && minimumBindingSize == other.minimumBindingSize && alignment == other.alignment
                && physicalParametersEqual(parameters, other.parameters);
    }

    private void validateAndRegister() {
        for (int i = 0; i < parameters.length; i++) {
            ShaderParameter parameter = parameters[i];
            if (parameter == null) {
                throw new FdxException("Shader parameter layout cannot contain null parameters");
            }
            long end = checkedAdd(parameter.byteOffset(), parameter.occupiedSize(),
                    "Shader parameter byte range");
            if (end > minimumBindingSize && !containsRuntimeArray(parameter.valueType())) {
                throw new FdxException("Shader parameter exceeds minimum binding size: " + parameter.name());
            }
            for (int j = 0; j < i; j++) {
                if (parameters[j].name().equals(parameter.name())
                        || parameters[j].stableId().equals(parameter.stableId())) {
                    throw new FdxException("Duplicate top-level shader parameter: " + parameter.name());
                }
            }
            registerTemplate(parameter, parameter.sourcePath(), "");
        }
    }

    private void registerTemplate(ShaderParameter parameter, String declaredPath, String parentPath) {
        String path = canonicalPath(parentPath, declaredPath, parameter.name());
        if (parameterTemplates.putIfAbsent(path, parameter) != null) {
            throw new FdxException("Duplicate shader parameter source path: " + path);
        }
        if (!path.contains("[]")) {
            ShaderParameterHandle handle = handle(parameter, path, parameter.byteOffset(), parameter.valueType(),
                    parameter.occupiedSize());
            put(path, handle);
            if (!parameter.stableId().equals(parameter.name())) {
                alias(parameter.stableId(), handle);
            }
            if (parameter.valueType().kind() == ShaderValueKind.VECTOR) {
                registerDirectComponents(path, handle);
            }
        }
        for (int i = 0; i < parameter.memberCount(); i++) {
            registerTemplate(parameter.member(i), parameter.member(i).sourcePath(), path);
        }
    }

    private ShaderParameterHandle deriveHandle(String requestedPath) {
        int component = componentIndex(requestedPath);
        String valuePath = component >= 0 ? requestedPath.substring(0, requestedPath.length() - 2) : requestedPath;
        ShaderParameterHandle nestedElement = deriveTrailingArrayElement(valuePath);
        if (nestedElement != null) {
            if (component < 0) {
                return nestedElement;
            }
            if (nestedElement.valueType().kind() != ShaderValueKind.VECTOR
                    || component >= nestedElement.valueType().rows()) {
                return null;
            }
            long scalarSize = scalarByteSize(nestedElement.valueType().scalarType());
            return new ShaderParameterHandle(this, requestedPath,
                    ShaderValueType.scalar(nestedElement.valueType().scalarType()),
                    checkedAdd(nestedElement.byteOffset(), component * scalarSize,
                            "Shader vector component byte offset"),
                    scalarSize, scalarSize, 0, 0);
        }
        NormalizedPath normalized = normalize(valuePath);
        if (normalized == null || normalized.indices.length == 0) {
            return null;
        }
        ShaderParameter parameter = parameterTemplates.get(normalized.template);
        boolean element = false;
        String parameterTemplate = normalized.template;
        if (parameter == null && normalized.template.endsWith("[]")) {
            parameterTemplate = normalized.template.substring(0, normalized.template.length() - 2);
            parameter = parameterTemplates.get(parameterTemplate);
            element = parameter != null;
        }
        if (parameter == null) {
            return null;
        }
        long delta = arrayOffsetDelta(normalized.template, normalized.indices);
        ShaderValueType type = element ? parameter.valueType().elementType() : parameter.valueType();
        if (element && parameter.valueType().kind() != ShaderValueKind.ARRAY) {
            return null;
        }
        long occupied = element
                ? physicalOccupiedSize(type, parameter.valueType().arrayStride()) : parameter.occupiedSize();
        long offset = checkedAdd(parameter.byteOffset(), delta, "Shader parameter element byte offset");
        ShaderParameterHandle result = handle(parameter, valuePath, offset, type, occupied);
        if (component >= 0) {
            if (type.kind() != ShaderValueKind.VECTOR || component >= type.rows()) {
                return null;
            }
            long scalarSize = scalarByteSize(type.scalarType());
            result = new ShaderParameterHandle(this, requestedPath, ShaderValueType.scalar(type.scalarType()),
                    checkedAdd(offset, component * scalarSize, "Shader vector component byte offset"), scalarSize,
                    scalarSize, 0, 0);
        }
        return result;
    }

    private ShaderParameterHandle deriveTrailingArrayElement(String path) {
        if (!path.endsWith("]")) {
            return null;
        }
        int open = path.lastIndexOf('[');
        if (open <= 0 || open == path.length() - 2) {
            return null;
        }
        long parsed = 0;
        for (int i = open + 1; i < path.length() - 1; i++) {
            char digit = path.charAt(i);
            if (digit < '0' || digit > '9') {
                return null;
            }
            parsed = parsed * 10 + digit - '0';
            if (parsed > Integer.MAX_VALUE) {
                throw new FdxException("Shader array index exceeds Java index range: " + path);
            }
        }
        String parent = path.substring(0, open);
        ShaderParameterHandle array = findHandle(parent);
        if (array == null || array.valueType().kind() != ShaderValueKind.ARRAY) {
            return null;
        }
        return requireArrayElementHandle(parent, (int) parsed);
    }

    private long arrayOffsetDelta(String normalizedPath, int[] indices) {
        long delta = 0;
        int search = 0;
        int indexCursor = 0;
        while (true) {
            int wildcard = normalizedPath.indexOf("[]", search);
            if (wildcard < 0) {
                break;
            }
            String arrayPath = normalizedPath.substring(0, wildcard);
            ShaderParameter array = parameterTemplates.get(arrayPath);
            if (array == null || array.valueType().kind() != ShaderValueKind.ARRAY) {
                throw new FdxException("Invalid shader array path: " + normalizedPath);
            }
            int index = indices[indexCursor++];
            validateArrayIndex(array.valueType(), index, arrayPath);
            delta = checkedAdd(delta, checkedMultiply(index, array.valueType().arrayStride(),
                    "Shader array byte offset"), "Shader array byte offset");
            search = wildcard + 2;
        }
        if (indexCursor != indices.length) {
            throw new FdxException("Invalid shader indexed path: " + normalizedPath);
        }
        return delta;
    }

    private ShaderParameterHandle handle(ShaderParameter parameter, String path, long offset, ShaderValueType type,
            long occupiedSize) {
        return new ShaderParameterHandle(this, path, type, offset, occupiedSize, parameter.alignment(),
                type.kind() == ShaderValueKind.ARRAY ? type.arrayStride() : 0, findMatrixStride(type));
    }

    private void registerDirectComponents(String path, ShaderParameterHandle vector) {
        long scalarSize = scalarByteSize(vector.valueType().scalarType());
        for (int i = 0; i < vector.valueType().rows(); i++) {
            String componentPath = path + '.' + componentName(i);
            ShaderParameterHandle component =
                    new ShaderParameterHandle(this, componentPath,
                    ShaderValueType.scalar(vector.valueType().scalarType()),
                    checkedAdd(vector.byteOffset(), i * scalarSize, "Shader vector component byte offset"),
                    scalarSize, scalarSize, 0, 0);
            put(componentPath, component);
            vector.cacheComponent(i, component);
        }
    }

    private void put(String path, ShaderParameterHandle handle) {
        if (handles.putIfAbsent(path, handle) != null) {
            throw new FdxException("Duplicate shader parameter path: " + path);
        }
    }

    private void alias(String path, ShaderParameterHandle handle) {
        if (handle == null || handles.putIfAbsent(path, handle) != null) {
            throw new FdxException("Duplicate or unresolved shader parameter stable path: " + path);
        }
    }

    private static String canonicalPath(String parentPath, String sourcePath, String localName) {
        String candidate = sourcePath != null && !sourcePath.trim().isEmpty() ? sourcePath : localName;
        if (parentPath.isEmpty() || candidate.startsWith(parentPath)
                || candidate.indexOf('.') >= 0 || candidate.contains("[]")) {
            return candidate;
        }
        return parentPath + '.' + candidate;
    }

    private static NormalizedPath normalize(String path) {
        StringBuilder template = new StringBuilder(path.length());
        int[] scratch = new int[Math.min(16, path.length() / 3 + 1)];
        int count = 0;
        for (int i = 0; i < path.length();) {
            char value = path.charAt(i);
            if (value != '[') {
                template.append(value);
                i++;
                continue;
            }
            int close = path.indexOf(']', i + 1);
            if (close < 0 || close == i + 1) {
                return null;
            }
            long index = 0;
            for (int cursor = i + 1; cursor < close; cursor++) {
                char digit = path.charAt(cursor);
                if (digit < '0' || digit > '9') {
                    return null;
                }
                index = index * 10 + digit - '0';
                if (index > Integer.MAX_VALUE) {
                    throw new FdxException("Shader array index exceeds Java index range: " + path);
                }
            }
            if (count == scratch.length) {
                scratch = Arrays.copyOf(scratch, scratch.length * 2);
            }
            scratch[count++] = (int) index;
            template.append("[]");
            i = close + 1;
        }
        return new NormalizedPath(template.toString(), Arrays.copyOf(scratch, count));
    }

    private static int componentIndex(String path) {
        if (path.length() < 2 || path.charAt(path.length() - 2) != '.') {
            return -1;
        }
        return switch (path.charAt(path.length() - 1)) {
            case 'x' -> 0;
            case 'y' -> 1;
            case 'z' -> 2;
            case 'w' -> 3;
            default -> -1;
        };
    }

    private static String componentName(int index) {
        return switch (index) {
            case 0 -> "x";
            case 1 -> "y";
            case 2 -> "z";
            case 3 -> "w";
            default -> throw new FdxException("Shader vector component is out of range: " + index);
        };
    }

    private static void validateArrayIndex(ShaderValueType array, int index, String path) {
        if (index < 0 || (array.arrayCount() >= 0 && index >= array.arrayCount())) {
            throw new FdxException("Shader array element is out of range: " + path + '[' + index + ']');
        }
    }

    static long scalarByteSize(ShaderScalarType type) {
        if (type == ShaderScalarType.F16) {
            return 2;
        }
        if (type == ShaderScalarType.I8 || type == ShaderScalarType.U8) {
            return 1;
        }
        return 4;
    }

    static long physicalOccupiedSize(ShaderValueType type, long aggregateFallback) {
        return switch (type.kind()) {
            case SCALAR, ATOMIC -> scalarByteSize(type.scalarType());
            case VECTOR -> checkedMultiply(type.rows(), scalarByteSize(type.scalarType()),
                    "Shader vector occupied size");
            case MATRIX -> checkedAdd(checkedMultiply(type.columns() - 1L, type.matrixStride(),
                    "Shader matrix occupied size"), checkedMultiply(type.rows(), scalarByteSize(type.scalarType()),
                    "Shader matrix occupied size"), "Shader matrix occupied size");
            case ARRAY -> type.arrayCount() < 0 ? 0
                    : checkedAdd(checkedMultiply(type.arrayCount() - 1L, type.arrayStride(),
                    "Shader array occupied size"), physicalOccupiedSize(type.elementType(), type.arrayStride()),
                    "Shader array occupied size");
            case STRUCT, BUFFER -> aggregateFallback;
            case UNKNOWN -> 0;
        };
    }

    private String computePhysicalHash() {
        PortableSha256 digest = new PortableSha256();
        updateLong(digest, minimumBindingSize);
        updateLong(digest, alignment);
        updateInt(digest, parameters.length);
        for (ShaderParameter parameter : parameters) {
            updatePhysical(digest, parameter);
        }
        return digest.digestHex();
    }

    private static void updatePhysical(PortableSha256 digest, ShaderParameter parameter) {
        updateType(digest, parameter.valueType());
        updateLong(digest, parameter.byteOffset());
        updateLong(digest, parameter.occupiedSize());
        updateLong(digest, parameter.minimumRequiredSize());
        updateLong(digest, parameter.alignment());
        updateInt(digest, parameter.memberCount());
        for (int i = 0; i < parameter.memberCount(); i++) {
            updatePhysical(digest, parameter.member(i));
        }
    }

    static void updateType(PortableSha256 digest, ShaderValueType type) {
        updateString(digest, type.kind().name());
        updateString(digest, type.scalarType().name());
        updateInt(digest, type.columns());
        updateInt(digest, type.rows());
        updateLong(digest, type.arrayCount());
        updateString(digest, type.typeName());
        updateLong(digest, type.arrayStride());
        updateLong(digest, type.matrixStride());
        if (type.elementType() != null) {
            updateInt(digest, 1);
            updateType(digest, type.elementType());
        } else {
            updateInt(digest, 0);
        }
    }

    static void updateInt(PortableSha256 digest, int value) {
        digest.updateInt(value);
    }

    static void updateLong(PortableSha256 digest, long value) {
        digest.updateLong(value);
    }

    static void updateString(PortableSha256 digest, String value) {
        digest.updateSizedUtf8(value);
    }

    private static boolean physicalParametersEqual(ShaderParameter[] first, ShaderParameter[] second) {
        if (first.length != second.length) {
            return false;
        }
        for (int i = 0; i < first.length; i++) {
            ShaderParameter a = first[i];
            ShaderParameter b = second[i];
            if (!a.valueType().equals(b.valueType()) || a.byteOffset() != b.byteOffset()
                    || a.occupiedSize() != b.occupiedSize()
                    || a.minimumRequiredSize() != b.minimumRequiredSize() || a.alignment() != b.alignment()
                    || !physicalParametersEqual(a.members(), b.members())) {
                return false;
            }
        }
        return true;
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

    private static boolean containsRuntimeArray(ShaderValueType type) {
        return type.kind() == ShaderValueKind.ARRAY
                && (type.arrayCount() < 0 || containsRuntimeArray(type.elementType()));
    }

    private static boolean isPowerOfTwo(long value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    private static long checkedAdd(long first, long second, String label) {
        if (first < 0 || second < 0 || first > Long.MAX_VALUE - second) {
            throw new FdxException(label + " overflows");
        }
        return first + second;
    }

    private static long checkedMultiply(long first, long second, String label) {
        if (first < 0 || second < 0 || (first != 0 && second > Long.MAX_VALUE / first)) {
            throw new FdxException(label + " overflows");
        }
        return first * second;
    }

    private static long nextIdentity() {
        long value = NEXT_IDENTITY.getAndIncrement();
        if (value <= 0) {
            throw new FdxException("Shader parameter layout identity space is exhausted");
        }
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ShaderParameterLayout other)) {
            return false;
        }
        return minimumBindingSize == other.minimumBindingSize && alignment == other.alignment
                && Arrays.equals(parameters, other.parameters);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(minimumBindingSize, alignment) + Arrays.hashCode(parameters);
    }

    private record NormalizedPath(String template, int[] indices) {
    }
}
