package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.graphics.shader.ShaderOverride;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.runtime.core.shader.RuntimeShaderReflection;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict decoder for the versioned FDXI transport owned by runtime-core/Tint.
 */
final class ShaderReflectionDecoder {
    private static final int SCHEMA_VERSION = 1;
    private static final long OPTIONAL_U32 = 0xffff_ffffL;
    private static final int MAX_RECORD_COUNT = 1_000_000;
    private static final int MAX_STRING_BYTES = 16 * 1024 * 1024;
    private static final int MAX_TYPE_DEPTH = 64;

    private ShaderReflectionDecoder() {
    }

    static ShaderReflection decode(RuntimeShaderReflection runtimeReflection, ShaderProfile profile) {
        if (runtimeReflection == null) {
            throw new FdxException("Runtime shader reflection cannot be null");
        }
        Reader reader = new Reader(runtimeReflection.bytes());
        reader.expectMagic();
        int version = reader.u32Int("schema version");
        if (version != SCHEMA_VERSION || runtimeReflection.schemaVersion() != SCHEMA_VERSION) {
            throw new FdxException("Unsupported FDXI schema version: " + version);
        }
        ShaderEntryPoint[] entries = readEntries(reader);
        ShaderBinding[] bindings = readBindings(reader);
        String[] capabilities = readCapabilities(reader);
        if (reader.remaining() != 0) {
            throw new FdxException("FDXI payload contains trailing bytes: " + reader.remaining());
        }
        return ShaderReflection.complete(profile != null ? profile : ShaderProfile.PORTABLE_WEBGPU, entries,
                bindings, capabilities);
    }

    private static ShaderEntryPoint[] readEntries(Reader reader) {
        int count = reader.count("entry point");
        ShaderEntryPoint[] entries = new ShaderEntryPoint[count];
        int previousStage = 0;
        String previousName = "";
        for (int i = 0; i < count; i++) {
            String name = reader.string("entry-point name");
            int stageTag = reader.u32Int("entry-point stage");
            ShaderStage stage = stage(stageTag);
            if (stageTag < previousStage || (stageTag == previousStage && name.compareTo(previousName) <= 0)) {
                throw new FdxException("FDXI entry points are not strictly sorted by stage and name");
            }
            previousStage = stageTag;
            previousName = name;
            int workgroupTag = reader.u32Int("workgroup kind");
            ShaderWorkgroupSizeKind workgroupKind = switch (workgroupTag) {
                case 0 -> ShaderWorkgroupSizeKind.NOT_APPLICABLE;
                case 1 -> ShaderWorkgroupSizeKind.FIXED;
                case 2 -> ShaderWorkgroupSizeKind.OVERRIDE_DEPENDENT;
                default -> throw unknown("workgroup kind", workgroupTag);
            };
            int x = reader.u32Int("workgroup x");
            int y = reader.u32Int("workgroup y");
            int z = reader.u32Int("workgroup z");
            long builtinMask = reader.u64("builtin mask");
            if ((builtinMask & ~ShaderBuiltinUsage.ALL) != 0) {
                throw new FdxException("FDXI entry point contains unknown builtin bits: " + builtinMask);
            }
            int clipDistanceSize = reader.optionalU32Int("clip-distance size");
            ShaderStageVariable[] inputs = readVariables(reader, "input");
            ShaderStageVariable[] outputs = readVariables(reader, "output");
            ShaderOverride[] overrides = readOverrides(reader);
            ShaderResourceUse[] resources = readResourceUses(reader);
            ShaderEntryPoint.Builder builder = ShaderEntryPoint.builder(name, stage)
                    .builtins(builtinMask, clipDistanceSize)
                    .inputs(inputs)
                    .outputs(outputs)
                    .overrides(overrides)
                    .resources(resources);
            if (workgroupKind == ShaderWorkgroupSizeKind.FIXED) {
                builder.fixedWorkgroupSize(x, y, z);
            } else if (workgroupKind == ShaderWorkgroupSizeKind.OVERRIDE_DEPENDENT) {
                if (x != 0 || y != 0 || z != 0) {
                    throw new FdxException("FDXI override-dependent workgroup dimensions must be zero");
                }
                builder.overrideDependentWorkgroupSize();
            } else if (x != 0 || y != 0 || z != 0) {
                throw new FdxException("FDXI non-compute workgroup dimensions must be zero");
            }
            entries[i] = builder.build();
        }
        return entries;
    }

    private static ShaderStageVariable[] readVariables(Reader reader, String direction) {
        int count = reader.count("stage " + direction);
        ShaderStageVariable[] variables = new ShaderStageVariable[count];
        for (int i = 0; i < count; i++) {
            String name = reader.string(direction + " name");
            String variableName = reader.string(direction + " variable name");
            int location = reader.optionalU32Int(direction + " location");
            int color = reader.optionalU32Int(direction + " color");
            int blendSource = reader.optionalU32Int(direction + " blend source");
            ShaderScalarType component = stageComponent(reader.u32Int(direction + " component"));
            int composition = reader.u32Int(direction + " composition");
            ShaderValueType type = switch (composition) {
                case 1 -> ShaderValueType.scalar(component);
                case 2 -> ShaderValueType.vector(component, 2);
                case 3 -> ShaderValueType.vector(component, 3);
                case 4 -> ShaderValueType.vector(component, 4);
                default -> throw unknown(direction + " composition", composition);
            };
            int interpolationTag = reader.u32Int(direction + " interpolation");
            ShaderInterpolation interpolation = switch (interpolationTag) {
                case 0 -> ShaderInterpolation.UNKNOWN;
                case 1 -> ShaderInterpolation.PERSPECTIVE;
                case 2 -> ShaderInterpolation.LINEAR;
                case 3 -> ShaderInterpolation.FLAT;
                default -> throw unknown(direction + " interpolation", interpolationTag);
            };
            int samplingTag = reader.u32Int(direction + " sampling");
            ShaderInterpolationSampling sampling = switch (samplingTag) {
                case 0 -> ShaderInterpolationSampling.UNKNOWN;
                case 1 -> ShaderInterpolationSampling.NONE;
                case 2 -> ShaderInterpolationSampling.CENTER;
                case 3 -> ShaderInterpolationSampling.CENTROID;
                case 4 -> ShaderInterpolationSampling.SAMPLE;
                case 5 -> ShaderInterpolationSampling.FIRST;
                case 6 -> ShaderInterpolationSampling.EITHER;
                default -> throw unknown(direction + " interpolation sampling", samplingTag);
            };
            variables[i] = ShaderStageVariable.of(name, variableName, location, color, blendSource, type,
                    interpolation, sampling);
        }
        return variables;
    }

    private static ShaderOverride[] readOverrides(Reader reader) {
        int count = reader.count("override");
        ShaderOverride[] overrides = new ShaderOverride[count];
        long previousId = -1;
        String previousName = "";
        for (int i = 0; i < count; i++) {
            String name = reader.string("override name");
            long id = reader.u32("override ID");
            if (id > Integer.MAX_VALUE) {
                throw new FdxException("FDXI override ID exceeds the Java API range: " + id);
            }
            if (id < previousId || (id == previousId && name.compareTo(previousName) <= 0)) {
                throw new FdxException("FDXI overrides are not strictly sorted by ID and name");
            }
            previousId = id;
            previousName = name;
            ShaderScalarType type = switch (reader.u32Int("override type")) {
                case 1 -> ShaderScalarType.BOOL;
                case 2 -> ShaderScalarType.F32;
                case 3 -> ShaderScalarType.U32;
                case 4 -> ShaderScalarType.I32;
                case 5 -> ShaderScalarType.F16;
                default -> throw new FdxException("FDXI override has an unknown type");
            };
            boolean initialized = reader.booleanU32("override initialized");
            boolean explicitId = reader.booleanU32("override explicit ID");
            overrides[i] = ShaderOverride.of(name, (int) id, type, initialized, explicitId);
        }
        return overrides;
    }

    private static ShaderResourceUse[] readResourceUses(Reader reader) {
        int count = reader.count("entry-point resource reference");
        ShaderResourceUse[] uses = new ShaderResourceUse[count];
        long previousKey = -1;
        for (int i = 0; i < count; i++) {
            int group = reader.u32Int("resource-reference group");
            int binding = reader.u32Int("resource-reference binding");
            long key = ((long) group << 32) | (binding & 0xffff_ffffL);
            if (key <= previousKey) {
                throw new FdxException("FDXI entry-point resource references are not strictly sorted");
            }
            previousKey = key;
            uses[i] = ShaderResourceUse.of(group, binding, reader.u64("entry minimum binding size"));
        }
        return uses;
    }

    private static ShaderBinding[] readBindings(Reader reader) {
        int count = reader.count("resource");
        ShaderBinding[] bindings = new ShaderBinding[count];
        long previousKey = -1;
        for (int i = 0; i < count; i++) {
            int group = reader.u32Int("resource group");
            int bindingIndex = reader.u32Int("resource binding");
            long key = ((long) group << 32) | (bindingIndex & 0xffff_ffffL);
            if (key <= previousKey) {
                throw new FdxException("FDXI resources are not strictly sorted by group and binding");
            }
            previousKey = key;
            String name = reader.string("resource name");
            ShaderResourceKind kind = resourceKind(reader.u32Int("resource kind"));
            ShaderResourceAccess access = switch (reader.u32Int("resource access")) {
                case 0 -> ShaderResourceAccess.NONE;
                case 1 -> ShaderResourceAccess.READ;
                case 2 -> ShaderResourceAccess.WRITE;
                case 3 -> ShaderResourceAccess.READ_WRITE;
                default -> throw new FdxException("FDXI resource has unknown access");
            };
            ShaderStageVisibility visibility = ShaderStageVisibility.fromMask(
                    reader.u32Int("resource visibility"));
            long bindingArrayCount = reader.optionalU32("binding-array count");
            long minimumBindingSize = reader.u64("resource minimum binding size");
            long sizeWithoutPadding = reader.u64("resource size without padding");
            long alignment = reader.u64("resource alignment");
            ShaderTextureDimension dimension = textureDimension(reader.u32Int("texture dimension"));
            ShaderTextureSampleType sampleType = textureSampleType(reader.u32Int("texture sampled kind"));
            ShaderSamplerKind samplerKind = samplerKind(reader.u32Int("sampler kind"));
            ShaderStorageTextureFormat storageFormat = ShaderStorageTextureFormat.fromFdxiTag(
                    reader.u32Int("storage texture format"));
            long inputAttachmentIndex = reader.optionalU32("input-attachment index");
            ShaderParameterLayout layout = readParameterLayout(reader, kind, minimumBindingSize, alignment);
            bindings[i] = ShaderBinding.builder(group, bindingIndex, name, kind)
                    .visibility(visibility)
                    .access(access)
                    .bindingArrayCount(bindingArrayCount)
                    .buffer(minimumBindingSize, sizeWithoutPadding, alignment, layout)
                    .texture(dimension, sampleType)
                    .samplerKind(samplerKind)
                    .storageFormat(storageFormat)
                    .inputAttachmentIndex(inputAttachmentIndex)
                    .build();
        }
        return bindings;
    }

    private static ShaderParameterLayout readParameterLayout(Reader reader, ShaderResourceKind resourceKind,
            long minimumBindingSize, long alignment) {
        int count = reader.count("buffer member");
        if (count == 0) {
            if (resourceKind == ShaderResourceKind.UNIFORM_BUFFER
                    || resourceKind == ShaderResourceKind.STORAGE_BUFFER) {
                return ShaderParameterLayout.of(minimumBindingSize, alignment);
            }
            return null;
        }
        if (resourceKind != ShaderResourceKind.UNIFORM_BUFFER
                && resourceKind != ShaderResourceKind.STORAGE_BUFFER) {
            throw new FdxException("FDXI non-buffer resource contains buffer members");
        }
        RawMember[] raw = new RawMember[count];
        Set<String> paths = new HashSet<>(Math.min(count * 2, MAX_RECORD_COUNT));
        int[] firstChild = new int[count];
        int[] lastChild = new int[count];
        int[] nextSibling = new int[count];
        Arrays.fill(firstChild, -1);
        Arrays.fill(lastChild, -1);
        Arrays.fill(nextSibling, -1);
        for (int i = 0; i < count; i++) {
            String path = reader.string("buffer-member path");
            if (!paths.add(path)) {
                throw new FdxException("FDXI contains a duplicate buffer-member path: " + path);
            }
            long parent = reader.optionalU32("buffer-member parent");
            if (parent >= i) {
                throw new FdxException("FDXI buffer-member parent must precede its child");
            }
            int parentIndex = parent < 0 ? -1 : (int) parent;
            int depth = parentIndex < 0 ? 0 : raw[parentIndex].depth + 1;
            if (depth > MAX_TYPE_DEPTH) {
                throw new FdxException("FDXI buffer-member nesting exceeds " + MAX_TYPE_DEPTH);
            }
            long offset = reader.u64("buffer-member offset");
            long size = reader.u64("buffer-member size");
            long memberAlignment = reader.u64("buffer-member alignment");
            long minimumSize = reader.u64("buffer-member minimum size");
            ShaderValueType type = readType(reader, 0);
            raw[i] = new RawMember(path, parentIndex, depth, offset, size, memberAlignment, minimumSize, type);
            if (parentIndex >= 0) {
                if (firstChild[parentIndex] < 0) {
                    firstChild[parentIndex] = i;
                } else {
                    nextSibling[lastChild[parentIndex]] = i;
                }
                lastChild[parentIndex] = i;
            }
        }
        List<ShaderParameter> roots = new ArrayList<>();
        for (int i = 0; i < raw.length; i++) {
            if (raw[i].parent < 0) {
                roots.add(buildParameter(raw, firstChild, nextSibling, i, 0));
            }
        }
        if (roots.isEmpty()) {
            throw new FdxException("FDXI buffer-member hierarchy has no root");
        }
        return ShaderParameterLayout.of(minimumBindingSize, alignment, roots.toArray(ShaderParameter[]::new));
    }

    private static ShaderParameter buildParameter(RawMember[] raw, int[] firstChild, int[] nextSibling, int index,
            int depth) {
        if (depth > MAX_TYPE_DEPTH) {
            throw new FdxException("FDXI buffer-member nesting exceeds " + MAX_TYPE_DEPTH);
        }
        RawMember source = raw[index];
        List<ShaderParameter> children = new ArrayList<>();
        for (int child = firstChild[index]; child >= 0; child = nextSibling[child]) {
            children.add(buildParameter(raw, firstChild, nextSibling, child, depth + 1));
        }
        return ShaderParameter.builder(source.path, source.path, source.type, source.offset, source.size,
                        source.alignment)
                .sourcePath(source.path)
                .minimumRequiredSize(source.minimumSize)
                .arrayStride(source.type.kind() == ShaderValueKind.ARRAY ? source.type.arrayStride() : 0)
                .matrixStride(findMatrixStride(source.type))
                .members(children.toArray(ShaderParameter[]::new))
                .build();
    }

    private static ShaderValueType readType(Reader reader, int depth) {
        if (depth > MAX_TYPE_DEPTH) {
            throw new FdxException("FDXI shader type nesting exceeds " + MAX_TYPE_DEPTH);
        }
        int kindTag = reader.u32Int("value kind");
        ShaderScalarType scalar = scalar(reader.u32Int("scalar kind"), false);
        int rows = reader.u32Int("type rows");
        int columns = reader.u32Int("type columns");
        long rawArrayCount = reader.u32("type array count");
        long arrayCount = rawArrayCount == OPTIONAL_U32 ? -1 : rawArrayCount;
        long arrayStride = reader.u64("type array stride");
        long matrixStride = reader.u64("type matrix stride");
        String typeName = reader.string("type name");
        int childCount = reader.count("type child");
        if (kindTag == 4) {
            if (childCount != 1) {
                throw new FdxException("FDXI array type must contain exactly one element type");
            }
        } else if (childCount != 0) {
            throw new FdxException("Only FDXI array types can contain a child type");
        }
        ShaderValueType child = childCount == 1 ? readType(reader, depth + 1) : null;
        ShaderValueType decoded = switch (kindTag) {
            case 1 -> {
                requireShape(rows, columns, 1, 1, "scalar");
                requireNoLayout(arrayCount, arrayStride, matrixStride, "scalar");
                yield ShaderValueType.scalar(requireConcreteScalar(scalar, "scalar"));
            }
            case 2 -> {
                if (rows != 1 || columns < 2 || columns > 4) {
                    throw new FdxException("FDXI vector type has invalid dimensions");
                }
                requireNoLayout(arrayCount, arrayStride, matrixStride, "vector");
                yield ShaderValueType.vector(requireConcreteScalar(scalar, "vector"), columns);
            }
            case 3 -> {
                if (rows < 2 || rows > 4 || columns < 2 || columns > 4 || matrixStride == 0) {
                    throw new FdxException("FDXI matrix type has invalid dimensions or stride");
                }
                if (arrayCount != 0 || arrayStride != 0) {
                    throw new FdxException("FDXI matrix type contains array metadata");
                }
                yield ShaderValueType.matrix(requireConcreteScalar(scalar, "matrix"), columns, rows, matrixStride);
            }
            case 4 -> {
                if (arrayCount == 0 || arrayStride == 0 || scalar != ShaderScalarType.UNKNOWN
                        || rows != 0 || columns != 0 || matrixStride != 0) {
                    throw new FdxException("FDXI array type contains contradictory metadata");
                }
                yield arrayCount < 0 ? ShaderValueType.runtimeArray(child, arrayStride)
                        : ShaderValueType.array(child, arrayCount, arrayStride);
            }
            case 5 -> {
                requireAggregate(scalar, rows, columns, arrayCount, arrayStride, matrixStride, "struct");
                yield ShaderValueType.structure(requireTypeName(typeName, "struct"));
            }
            case 6 -> {
                requireShape(rows, columns, 1, 1, "atomic");
                requireNoLayout(arrayCount, arrayStride, matrixStride, "atomic");
                yield ShaderValueType.atomic(requireConcreteScalar(scalar, "atomic"));
            }
            case 7 -> {
                requireAggregate(scalar, rows, columns, arrayCount, arrayStride, matrixStride, "buffer");
                yield ShaderValueType.buffer(typeName);
            }
            default -> throw unknown("value kind", kindTag);
        };
        return decoded.named(typeName);
    }

    private static String[] readCapabilities(Reader reader) {
        int count = reader.count("used extension");
        String[] capabilities = new String[count];
        String previous = "";
        for (int i = 0; i < count; i++) {
            capabilities[i] = reader.string("used extension");
            if (i > 0 && capabilities[i].compareTo(previous) <= 0) {
                throw new FdxException("FDXI used extensions are not strictly sorted");
            }
            previous = capabilities[i];
        }
        return capabilities;
    }

    private static ShaderStage stage(int tag) {
        return switch (tag) {
            case 1 -> ShaderStage.VERTEX;
            case 2 -> ShaderStage.FRAGMENT;
            case 3 -> ShaderStage.COMPUTE;
            default -> throw unknown("shader stage", tag);
        };
    }

    private static ShaderResourceKind resourceKind(int tag) {
        return switch (tag) {
            case 1 -> ShaderResourceKind.UNIFORM_BUFFER;
            case 2 -> ShaderResourceKind.STORAGE_BUFFER;
            case 3 -> ShaderResourceKind.SAMPLER;
            case 4 -> ShaderResourceKind.SAMPLED_TEXTURE;
            case 5 -> ShaderResourceKind.MULTISAMPLED_TEXTURE;
            case 6 -> ShaderResourceKind.STORAGE_TEXTURE;
            case 7 -> ShaderResourceKind.DEPTH_TEXTURE;
            case 8 -> ShaderResourceKind.DEPTH_MULTISAMPLED_TEXTURE;
            case 9 -> ShaderResourceKind.EXTERNAL_TEXTURE;
            case 10 -> ShaderResourceKind.TEXEL_BUFFER;
            case 11 -> ShaderResourceKind.INPUT_ATTACHMENT;
            default -> throw unknown("resource kind", tag);
        };
    }

    private static ShaderTextureDimension textureDimension(int tag) {
        return switch (tag) {
            case 0 -> ShaderTextureDimension.NONE;
            case 1 -> ShaderTextureDimension.D1;
            case 2 -> ShaderTextureDimension.D2;
            case 3 -> ShaderTextureDimension.D2_ARRAY;
            case 4 -> ShaderTextureDimension.D3;
            case 5 -> ShaderTextureDimension.CUBE;
            case 6 -> ShaderTextureDimension.CUBE_ARRAY;
            default -> throw unknown("texture dimension", tag);
        };
    }

    private static ShaderTextureSampleType textureSampleType(int tag) {
        return switch (tag) {
            case 0 -> ShaderTextureSampleType.NONE;
            case 1 -> ShaderTextureSampleType.FLOAT;
            case 2 -> ShaderTextureSampleType.UINT;
            case 3 -> ShaderTextureSampleType.SINT;
            case 4 -> ShaderTextureSampleType.FILTERABLE_FLOAT;
            case 5 -> ShaderTextureSampleType.UNFILTERABLE_FLOAT;
            case 6 -> ShaderTextureSampleType.UNKNOWN_FILTERABLE;
            default -> throw unknown("texture sampled kind", tag);
        };
    }

    private static ShaderSamplerKind samplerKind(int tag) {
        return switch (tag) {
            case 0 -> ShaderSamplerKind.NONE;
            case 1 -> ShaderSamplerKind.COMPARISON;
            case 2 -> ShaderSamplerKind.FILTERING;
            case 3 -> ShaderSamplerKind.NON_FILTERING;
            case 4 -> ShaderSamplerKind.UNKNOWN_FILTERING;
            default -> throw unknown("sampler kind", tag);
        };
    }

    private static ShaderScalarType scalar(int tag, boolean requireConcrete) {
        ShaderScalarType result = switch (tag) {
            case 0 -> ShaderScalarType.UNKNOWN;
            case 1 -> ShaderScalarType.BOOL;
            case 2 -> ShaderScalarType.F16;
            case 3 -> ShaderScalarType.F32;
            case 4 -> ShaderScalarType.I32;
            case 5 -> ShaderScalarType.U32;
            case 6 -> ShaderScalarType.I8;
            case 7 -> ShaderScalarType.U8;
            default -> throw unknown("scalar kind", tag);
        };
        if (requireConcrete && result == ShaderScalarType.UNKNOWN) {
            throw new FdxException("FDXI value requires a concrete scalar kind");
        }
        return result;
    }

    private static ShaderScalarType stageComponent(int tag) {
        return switch (tag) {
            case 1 -> ShaderScalarType.F32;
            case 2 -> ShaderScalarType.U32;
            case 3 -> ShaderScalarType.I32;
            case 4 -> ShaderScalarType.F16;
            case 0 -> throw new FdxException("FDXI stage variable requires a concrete component kind");
            default -> throw unknown("stage-variable component kind", tag);
        };
    }

    private static ShaderScalarType requireConcreteScalar(ShaderScalarType type, String label) {
        if (type == ShaderScalarType.UNKNOWN) {
            throw new FdxException("FDXI " + label + " type requires a scalar kind");
        }
        return type;
    }

    private static void requireShape(int rows, int columns, int expectedRows, int expectedColumns, String label) {
        if (rows != expectedRows || columns != expectedColumns) {
            throw new FdxException("FDXI " + label + " type has invalid dimensions");
        }
    }

    private static void requireNoLayout(long arrayCount, long arrayStride, long matrixStride, String label) {
        if (arrayCount != 0 || arrayStride != 0 || matrixStride != 0) {
            throw new FdxException("FDXI " + label + " type contains layout metadata");
        }
    }

    private static void requireAggregate(ShaderScalarType scalar, int rows, int columns, long arrayCount,
            long arrayStride, long matrixStride, String label) {
        if (scalar != ShaderScalarType.UNKNOWN || rows != 0 || columns != 0 || arrayCount != 0
                || arrayStride != 0 || matrixStride != 0) {
            throw new FdxException("FDXI " + label + " type contains contradictory metadata");
        }
    }

    private static String requireTypeName(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new FdxException("FDXI " + label + " type name cannot be empty");
        }
        return value;
    }

    private static long findMatrixStride(ShaderValueType type) {
        if (type.kind() == ShaderValueKind.MATRIX) {
            return type.matrixStride();
        }
        return type.kind() == ShaderValueKind.ARRAY ? findMatrixStride(type.elementType()) : 0;
    }

    private static FdxException unknown(String label, long tag) {
        return new FdxException("FDXI contains unknown " + label + " tag: " + tag);
    }

    private record RawMember(String path, int parent, int depth, long offset, long size, long alignment,
            long minimumSize, ShaderValueType type) {
    }

    private static final class Reader {
        private final ByteBuffer buffer;

        private Reader(byte[] bytes) {
            buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        }

        private void expectMagic() {
            require(4, "magic");
            if (buffer.get() != 'F' || buffer.get() != 'D' || buffer.get() != 'X' || buffer.get() != 'I') {
                throw new FdxException("FDXI payload has invalid magic");
            }
        }

        private int count(String label) {
            long count = u32(label + " count");
            if (count > MAX_RECORD_COUNT) {
                throw new FdxException("FDXI " + label + " count exceeds the safe decoder limit: " + count);
            }
            return (int) count;
        }

        private long optionalU32(String label) {
            long value = u32(label);
            return value == OPTIONAL_U32 ? -1 : value;
        }

        private int optionalU32Int(String label) {
            long value = optionalU32(label);
            if (value > Integer.MAX_VALUE) {
                throw new FdxException("FDXI " + label + " exceeds the Java API range: " + value);
            }
            return (int) value;
        }

        private int u32Int(String label) {
            long value = u32(label);
            if (value > Integer.MAX_VALUE) {
                throw new FdxException("FDXI " + label + " exceeds the Java API range: " + value);
            }
            return (int) value;
        }

        private long u32(String label) {
            require(4, label);
            return Integer.toUnsignedLong(buffer.getInt());
        }

        private long u64(String label) {
            require(8, label);
            long value = buffer.getLong();
            if (value < 0) {
                throw new FdxException("FDXI " + label + " exceeds the supported unsigned 64-bit range");
            }
            return value;
        }

        private boolean booleanU32(String label) {
            int value = u32Int(label);
            if (value != 0 && value != 1) {
                throw new FdxException("FDXI " + label + " must be 0 or 1");
            }
            return value != 0;
        }

        private String string(String label) {
            long lengthValue = u32(label + " byte length");
            if (lengthValue > MAX_STRING_BYTES || lengthValue > remaining()) {
                throw new FdxException("FDXI " + label + " length is invalid: " + lengthValue);
            }
            int length = (int) lengthValue;
            ByteBuffer slice = buffer.slice();
            slice.limit(length);
            String value;
            try {
                value = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(slice)
                        .toString();
            } catch (CharacterCodingException exception) {
                throw new FdxException("FDXI " + label + " is not valid UTF-8", exception);
            }
            buffer.position(buffer.position() + length);
            return value;
        }

        private int remaining() {
            return buffer.remaining();
        }

        private void require(int bytes, String label) {
            if (bytes < 0 || buffer.remaining() < bytes) {
                throw new FdxException("FDXI payload is truncated while reading " + label);
            }
        }
    }
}
