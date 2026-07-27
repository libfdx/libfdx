package io.github.libfdx.graphics.shadergraph.cache;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;
import io.github.libfdx.graphics.shader.ShaderStage;

import java.util.Arrays;
import java.util.Objects;

/**
 * Provider-neutral interface summary embedded beside a compiled artifact.
 */
public final class ShaderGraphCompiledInterface {
    private final String abiVersion;
    private final EntryPoint[] entryPoints;
    private final Binding[] bindings;
    private final Parameter[] parameters;
    private final String entryPointsHash;
    private final String hash;

    private ShaderGraphCompiledInterface(String abiVersion,
            EntryPoint[] entryPoints, Binding[] bindings,
            Parameter[] parameters) {
        this.abiVersion = require(abiVersion, "ABI version");
        this.entryPoints = copySort(entryPoints, EntryPoint[]::new,
                "entry point");
        this.bindings = copySort(bindings, Binding[]::new,
                "binding");
        this.parameters = copySort(parameters, Parameter[]::new,
                "parameter");
        rejectDuplicates(this.entryPoints, "entry point");
        rejectBindingSlots(this.bindings);
        rejectParameterIds(this.parameters);
        entryPointsHash = entryPointsHash(this.entryPoints);
        hash = PortableSha256.hashUtf8(structuralKey());
    }

    public static ShaderGraphCompiledInterface of(String abiVersion,
            EntryPoint[] entryPoints, Binding[] bindings,
            Parameter[] parameters) {
        return new ShaderGraphCompiledInterface(abiVersion,
                entryPoints, bindings, parameters);
    }

    public static ShaderGraphCompiledInterface empty(String abiVersion) {
        return of(abiVersion, new EntryPoint[0],
                new Binding[0], new Parameter[0]);
    }

    /**
     * Returns the deterministic key hash for an entry-point set without
     * constructing a complete interface.
     */
    public static String entryPointsHash(EntryPoint... entryPoints) {
        EntryPoint[] sorted = copySort(entryPoints, EntryPoint[]::new,
                "entry point");
        rejectDuplicates(sorted, "entry point");
        return PortableSha256.hashUtf8(entryPointKey(sorted));
    }

    public String abiVersion() {
        return abiVersion;
    }

    public EntryPoint[] entryPoints() {
        return entryPoints.clone();
    }

    public Binding[] bindings() {
        return bindings.clone();
    }

    public Parameter[] parameters() {
        return parameters.clone();
    }

    public String entryPointsHash() {
        return entryPointsHash;
    }

    public String hash() {
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphCompiledInterface other
                && abiVersion.equals(other.abiVersion)
                && Arrays.equals(entryPoints, other.entryPoints)
                && Arrays.equals(bindings, other.bindings)
                && Arrays.equals(parameters, other.parameters);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(abiVersion);
        result = 31 * result + Arrays.hashCode(entryPoints);
        result = 31 * result + Arrays.hashCode(bindings);
        result = 31 * result + Arrays.hashCode(parameters);
        return result;
    }

    private static String entryPointKey(EntryPoint[] entryPoints) {
        StringBuilder value = new StringBuilder();
        for (EntryPoint entryPoint : entryPoints) {
            value.append(entryPoint.stage().name()).append(':')
                    .append(entryPoint.name()).append('\n');
        }
        return value.toString();
    }

    private String structuralKey() {
        StringBuilder value = new StringBuilder(abiVersion)
                .append('\n').append(entryPointKey(entryPoints));
        for (Binding binding : bindings) {
            value.append(binding.group()).append(':')
                    .append(binding.binding()).append(':')
                    .append(binding.name()).append(':')
                    .append(binding.kind()).append(':')
                    .append(binding.type()).append(':')
                    .append(binding.access()).append('\n');
        }
        for (Parameter parameter : parameters) {
            value.append(parameter.id()).append(':')
                    .append(parameter.kind()).append(':')
                    .append(parameter.type()).append(':')
                    .append(parameter.semantic()).append(':')
                    .append(parameter.offset()).append(':')
                    .append(parameter.size()).append('\n');
        }
        return value.toString();
    }

    private static <T extends Comparable<T>> T[] copySort(
            T[] values, java.util.function.IntFunction<T[]> factory,
            String label) {
        T[] result = values != null ? values.clone() : factory.apply(0);
        for (T value : result) {
            if (value == null) {
                throw new FdxException(
                        "Shader graph interface " + label
                                + " cannot be null");
            }
        }
        Arrays.sort(result);
        return result;
    }

    private static <T extends Comparable<T>> void rejectDuplicates(
            T[] values, String label) {
        for (int i = 1; i < values.length; i++) {
            if (values[i - 1].compareTo(values[i]) == 0) {
                throw new FdxException(
                        "Duplicate shader graph interface " + label);
            }
        }
    }

    private static void rejectBindingSlots(Binding[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i - 1].group() == values[i].group()
                    && values[i - 1].binding()
                            == values[i].binding()) {
                throw new FdxException(
                        "Duplicate shader graph interface binding");
            }
        }
    }

    private static void rejectParameterIds(Parameter[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i - 1].id().equals(values[i].id())) {
                throw new FdxException(
                        "Duplicate shader graph interface parameter");
            }
        }
    }

    private static String require(String value, String label) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.isEmpty() || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\r') >= 0) {
            throw new FdxException(
                    "Shader graph interface " + label + " is invalid");
        }
        return normalized;
    }

    private static String optional(String value, String label) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.indexOf('\n') >= 0
                || normalized.indexOf('\r') >= 0) {
            throw new FdxException(
                    "Shader graph interface " + label + " is invalid");
        }
        return normalized;
    }

    public static final class EntryPoint
            implements Comparable<EntryPoint> {
        private final ShaderStage stage;
        private final String name;

        private EntryPoint(ShaderStage stage, String name) {
            if (stage == null) {
                throw new FdxException(
                        "Shader graph entry-point stage cannot be null");
            }
            this.stage = stage;
            this.name = require(name, "entry-point name");
        }

        public static EntryPoint of(ShaderStage stage, String name) {
            return new EntryPoint(stage, name);
        }

        public ShaderStage stage() {
            return stage;
        }

        public String name() {
            return name;
        }

        @Override
        public int compareTo(EntryPoint other) {
            int stageOrder = stage.compareTo(other.stage);
            return stageOrder != 0 ? stageOrder
                    : name.compareTo(other.name);
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof EntryPoint other
                    && stage == other.stage && name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stage, name);
        }
    }

    public static final class Binding implements Comparable<Binding> {
        private final int group;
        private final int binding;
        private final String name;
        private final String kind;
        private final String type;
        private final String access;

        private Binding(int group, int binding, String name,
                String kind, String type, String access) {
            if (group < 0 || binding < 0) {
                throw new FdxException(
                        "Shader graph interface binding is invalid");
            }
            this.group = group;
            this.binding = binding;
            this.name = require(name, "binding name");
            this.kind = require(kind, "binding kind");
            this.type = require(type, "binding type");
            this.access = optional(access, "binding access");
        }

        public static Binding of(int group, int binding,
                String name, String kind, String type, String access) {
            return new Binding(group, binding, name,
                    kind, type, access);
        }

        public int group() {
            return group;
        }

        public int binding() {
            return binding;
        }

        public String name() {
            return name;
        }

        public String kind() {
            return kind;
        }

        public String type() {
            return type;
        }

        public String access() {
            return access;
        }

        @Override
        public int compareTo(Binding other) {
            int groupOrder = Integer.compare(group, other.group);
            if (groupOrder != 0) {
                return groupOrder;
            }
            int bindingOrder = Integer.compare(binding, other.binding);
            return bindingOrder != 0 ? bindingOrder
                    : name.compareTo(other.name);
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof Binding other
                    && group == other.group && binding == other.binding
                    && name.equals(other.name)
                    && kind.equals(other.kind)
                    && type.equals(other.type)
                    && access.equals(other.access);
        }

        @Override
        public int hashCode() {
            return Objects.hash(group, binding, name, kind, type, access);
        }
    }

    public static final class Parameter
            implements Comparable<Parameter> {
        private final String id;
        private final String kind;
        private final String type;
        private final String semantic;
        private final long offset;
        private final long size;

        private Parameter(String id, String kind, String type,
                String semantic, long offset, long size) {
            if (offset < -1 || size < -1
                    || offset < 0 != (size < 0)) {
                throw new FdxException(
                        "Shader graph interface parameter range is invalid");
            }
            this.id = require(id, "parameter ID");
            this.kind = require(kind, "parameter kind");
            this.type = require(type, "parameter type");
            this.semantic = optional(semantic, "parameter semantic");
            this.offset = offset;
            this.size = size;
        }

        public static Parameter of(String id, String kind,
                String type, String semantic, long offset, long size) {
            return new Parameter(id, kind, type,
                    semantic, offset, size);
        }

        public String id() {
            return id;
        }

        public String kind() {
            return kind;
        }

        public String type() {
            return type;
        }

        public String semantic() {
            return semantic;
        }

        public long offset() {
            return offset;
        }

        public long size() {
            return size;
        }

        @Override
        public int compareTo(Parameter other) {
            return id.compareTo(other.id);
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof Parameter other
                    && id.equals(other.id)
                    && kind.equals(other.kind)
                    && type.equals(other.type)
                    && semantic.equals(other.semantic)
                    && offset == other.offset && size == other.size;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, kind, type,
                    semantic, offset, size);
        }
    }
}
