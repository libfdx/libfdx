package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeProperty;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodePropertyKind;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceAccess;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderStorageTextureFormat;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureDimension;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureSampleType;
import io.github.libfdx.graphics.shader.reflection.ShaderValueKind;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;

/**
 * Deterministic semantic JSON codec for {@code .fdxgraph} assets.
 */
public final class ShaderGraphCodec {
    private ShaderGraphCodec() {
    }

    public static String write(ShaderGraph graph) {
        if (graph == null) {
            throw new FdxException("Shader graph cannot be null");
        }
        JsonValue root = JsonValue.object()
                .put("format", graph.formatVersion())
                .put("id", graph.id().value())
                .put("kind", graph.kind().name().toLowerCase())
                .put("parameters", parameters(graph.parameters()))
                .put("resources", resources(graph.resources()))
                .put("nodes", nodes(graph.nodes()))
                .put("edges", edges(graph.edges()))
                .put("outputs", outputs(graph.outputs()))
                .put("dependencies", dependencies(graph.dependencies()));
        return JsonWriter.compact(root);
    }

    public static ShaderGraph read(String source) {
        JsonValue root = new JsonReader().parse(source);
        int format = root.require("format").intValue();
        if (format <= 0) {
            throw new FdxException("Shader graph format version must be positive");
        }
        ShaderGraph.Builder builder = ShaderGraph.builder(
                root.requireString("id"), enumValue(ShaderGraphKind.class,
                        root.requireString("kind"), "graph kind"))
                .formatVersion(format);
        builder.parameters(readParameters(root.require("parameters")));
        builder.resources(readResources(root.require("resources")));
        builder.nodes(readNodes(root.require("nodes")));
        builder.edges(readEdges(root.require("edges")));
        builder.outputs(readOutputs(root.require("outputs")));
        builder.dependencies(readDependencies(root.require("dependencies")));
        return builder.build();
    }

    private static JsonValue parameters(ShaderGraphParameter[] values) {
        JsonValue result = JsonValue.array();
        for (ShaderGraphParameter value : values) {
            JsonValue item = JsonValue.object()
                    .put("id", value.id().value())
                    .put("type", type(value.type()))
                    .put("kind", value.kind().name().toLowerCase())
                    .put("semantic", value.semantic());
            if (value.defaultValue() != null) {
                item.put("default", literal(value.defaultValue()));
            }
            result.add(item);
        }
        return result;
    }

    private static ShaderGraphParameter[] readParameters(JsonValue array) {
        requireArray(array, "parameters");
        ShaderGraphParameter[] result = new ShaderGraphParameter[array.size()];
        for (int i = 0; i < result.length; i++) {
            JsonValue item = array.require(i);
            ShaderGraphLiteral defaultValue = item.get("default") != null
                    ? readLiteral(item.require("default")) : null;
            result[i] = ShaderGraphParameter.semantic(item.requireString("id"),
                    readType(item.require("type")),
                    enumValue(ShaderGraphParameterKind.class,
                            item.requireString("kind"), "parameter kind"),
                    defaultValue, item.stringValue("semantic", ""));
        }
        return result;
    }

    private static JsonValue resources(ShaderGraphResource[] values) {
        JsonValue result = JsonValue.array();
        for (ShaderGraphResource value : values) {
            result.add(JsonValue.object()
                    .put("id", value.id().value())
                    .put("type", type(value.type()))
                    .put("group", value.group())
                    .put("binding", value.binding()));
        }
        return result;
    }

    private static ShaderGraphResource[] readResources(JsonValue array) {
        requireArray(array, "resources");
        ShaderGraphResource[] result = new ShaderGraphResource[array.size()];
        for (int i = 0; i < result.length; i++) {
            JsonValue item = array.require(i);
            result[i] = ShaderGraphResource.of(item.requireString("id"),
                    readType(item.require("type")),
                    item.require("group").intValue(),
                    item.require("binding").intValue());
        }
        return result;
    }

    private static JsonValue nodes(ShaderNode[] values) {
        JsonValue result = JsonValue.array();
        for (ShaderNode value : values) {
            JsonValue properties = JsonValue.array();
            for (ShaderNodeProperty property : value.properties()) {
                properties.add(property(property));
            }
            result.add(JsonValue.object()
                    .put("id", value.id().value())
                    .put("definition", value.definitionId().value())
                    .put("version", value.definitionVersion())
                    .put("inputs", ports(value.inputs()))
                    .put("outputs", ports(value.outputs()))
                    .put("properties", properties));
        }
        return result;
    }

    private static ShaderNode[] readNodes(JsonValue array) {
        requireArray(array, "nodes");
        ShaderNode[] result = new ShaderNode[array.size()];
        for (int i = 0; i < result.length; i++) {
            JsonValue item = array.require(i);
            JsonValue properties = item.require("properties");
            requireArray(properties, "node properties");
            ShaderNodeProperty[] decoded = new ShaderNodeProperty[properties.size()];
            for (int j = 0; j < decoded.length; j++) {
                decoded[j] = readProperty(properties.require(j));
            }
            result[i] = ShaderNode.of(item.requireString("id"),
                    item.requireString("definition"),
                    item.require("version").intValue(),
                    readPorts(item.require("inputs")),
                    readPorts(item.require("outputs")), decoded);
        }
        return result;
    }

    private static JsonValue ports(ShaderGraphPort[] values) {
        JsonValue result = JsonValue.array();
        for (ShaderGraphPort value : values) {
            JsonValue item = JsonValue.object()
                    .put("id", value.id().value())
                    .put("type", type(value.type()))
                    .put("required", value.required());
            if (value.defaultValue() != null) {
                item.put("default", literal(value.defaultValue()));
            }
            result.add(item);
        }
        return result;
    }

    private static ShaderGraphPort[] readPorts(JsonValue array) {
        requireArray(array, "ports");
        ShaderGraphPort[] result = new ShaderGraphPort[array.size()];
        for (int i = 0; i < result.length; i++) {
            JsonValue item = array.require(i);
            String id = item.requireString("id");
            ShaderGraphType valueType = readType(item.require("type"));
            ShaderGraphLiteral defaultValue = item.get("default") != null
                    ? readLiteral(item.require("default")) : null;
            result[i] = item.require("required").booleanValue()
                    ? ShaderGraphPort.required(id, valueType)
                    : ShaderGraphPort.optional(id, valueType, defaultValue);
        }
        return result;
    }

    private static JsonValue edges(ShaderEdge[] values) {
        JsonValue result = JsonValue.array();
        for (ShaderEdge value : values) {
            result.add(JsonValue.object()
                    .put("source", endpoint(value.source()))
                    .put("target", endpoint(value.target())));
        }
        return result;
    }

    private static ShaderEdge[] readEdges(JsonValue array) {
        requireArray(array, "edges");
        ShaderEdge[] result = new ShaderEdge[array.size()];
        for (int i = 0; i < result.length; i++) {
            JsonValue item = array.require(i);
            result[i] = ShaderEdge.of(readEndpoint(item.require("source")),
                    readEndpoint(item.require("target")));
        }
        return result;
    }

    private static JsonValue outputs(ShaderGraphOutput[] values) {
        JsonValue result = JsonValue.array();
        for (ShaderGraphOutput value : values) {
            result.add(JsonValue.object()
                    .put("id", value.id().value())
                    .put("type", type(value.type()))
                    .put("source", endpoint(value.source()))
                    .put("semantic", value.semantic()));
        }
        return result;
    }

    private static ShaderGraphOutput[] readOutputs(JsonValue array) {
        requireArray(array, "outputs");
        ShaderGraphOutput[] result = new ShaderGraphOutput[array.size()];
        for (int i = 0; i < result.length; i++) {
            JsonValue item = array.require(i);
            result[i] = ShaderGraphOutput.semantic(item.requireString("id"),
                    readType(item.require("type")),
                    readEndpoint(item.require("source")),
                    item.stringValue("semantic", ""));
        }
        return result;
    }

    private static JsonValue dependencies(ShaderGraphDependency[] values) {
        JsonValue result = JsonValue.array();
        for (ShaderGraphDependency value : values) {
            result.add(JsonValue.object()
                    .put("id", value.graphId().value())
                    .put("hash", value.semanticHash()));
        }
        return result;
    }

    private static ShaderGraphDependency[] readDependencies(JsonValue array) {
        requireArray(array, "dependencies");
        ShaderGraphDependency[] result = new ShaderGraphDependency[array.size()];
        for (int i = 0; i < result.length; i++) {
            JsonValue item = array.require(i);
            result[i] = ShaderGraphDependency.of(item.requireString("id"),
                    item.requireString("hash"));
        }
        return result;
    }

    private static JsonValue property(ShaderNodeProperty value) {
        JsonValue result = JsonValue.object()
                .put("id", value.id().value())
                .put("kind", value.kind().name().toLowerCase());
        switch (value.kind()) {
            case STRING -> result.put("value", value.stringValue());
            case INTEGER -> result.put("value", value.integerValue());
            case BOOLEAN -> result.put("value", value.booleanValue());
            case TYPE -> result.put("value", type(value.typeValue()));
            case LITERAL -> result.put("value", literal(value.literalValue()));
            case ID_LIST -> {
                JsonValue ids = JsonValue.array();
                for (ShaderGraphId id : value.idValues()) {
                    ids.add(id.value());
                }
                result.put("value", ids);
            }
            case INTEGER_LIST -> {
                JsonValue integers = JsonValue.array();
                for (long integer : value.integerValues()) {
                    integers.add(integer);
                }
                result.put("value", integers);
            }
        }
        return result;
    }

    private static ShaderNodeProperty readProperty(JsonValue value) {
        String id = value.requireString("id");
        ShaderNodePropertyKind kind = enumValue(ShaderNodePropertyKind.class,
                value.requireString("kind"), "node property kind");
        JsonValue encoded = value.require("value");
        return switch (kind) {
            case STRING -> ShaderNodeProperty.string(id, encoded.stringValue());
            case INTEGER -> ShaderNodeProperty.integer(id, encoded.longValue());
            case BOOLEAN -> ShaderNodeProperty.bool(id, encoded.booleanValue());
            case TYPE -> ShaderNodeProperty.type(id, readType(encoded));
            case LITERAL -> ShaderNodeProperty.literal(id, readLiteral(encoded));
            case ID_LIST -> {
                requireArray(encoded, "ID property");
                ShaderGraphId[] ids = new ShaderGraphId[encoded.size()];
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = ShaderGraphId.of(encoded.require(i).stringValue());
                }
                yield ShaderNodeProperty.ids(id, ids);
            }
            case INTEGER_LIST -> {
                requireArray(encoded, "integer property");
                long[] integers = new long[encoded.size()];
                for (int i = 0; i < integers.length; i++) {
                    integers[i] = encoded.require(i).longValue();
                }
                yield ShaderNodeProperty.integers(id, integers);
            }
        };
    }

    private static JsonValue endpoint(ShaderEndpoint value) {
        return JsonValue.object()
                .put("node", value.nodeId().value())
                .put("port", value.portId().value());
    }

    private static ShaderEndpoint readEndpoint(JsonValue value) {
        return ShaderEndpoint.of(value.requireString("node"),
                value.requireString("port"));
    }

    static JsonValue type(ShaderGraphType value) {
        JsonValue result = JsonValue.object()
                .put("kind", value.kind().name().toLowerCase());
        switch (value.kind()) {
            case VALUE -> result.put("value", valueType(value.valueType()));
            case STRUCT -> {
                JsonValue fields = JsonValue.array();
                for (ShaderStructField field : value.structType().fields()) {
                    fields.add(JsonValue.object()
                            .put("id", field.id().value())
                            .put("type", type(field.type())));
                }
                result.put("id", value.structType().id().value())
                        .put("fields", fields);
            }
            case TEXTURE -> result
                    .put("dimension", value.textureDimension().name().toLowerCase())
                    .put("sample", value.textureSampleType().name().toLowerCase())
                    .put("multisampled", value.multisampled());
            case SAMPLER -> result.put("sampler",
                    value.samplerKind().name().toLowerCase());
            case STORAGE_BUFFER -> result
                    .put("element", type(value.elementType()))
                    .put("access", value.resourceAccess().name().toLowerCase());
            case STORAGE_TEXTURE -> result
                    .put("dimension",
                            value.textureDimension().name().toLowerCase())
                    .put("format",
                            value.storageFormat().name().toLowerCase())
                    .put("access",
                            value.resourceAccess().name().toLowerCase());
            case WORKGROUP_ARRAY -> result
                    .put("element", type(value.elementType()))
                    .put("count", value.elementCount());
        }
        return result;
    }

    static ShaderGraphType readType(JsonValue value) {
        ShaderGraphTypeKind kind = enumValue(ShaderGraphTypeKind.class,
                value.requireString("kind"), "graph type kind");
        return switch (kind) {
            case VALUE -> ShaderGraphType.value(readValueType(value.require("value")));
            case STRUCT -> {
                JsonValue fields = value.require("fields");
                requireArray(fields, "structure fields");
                ShaderStructField[] decoded = new ShaderStructField[fields.size()];
                for (int i = 0; i < decoded.length; i++) {
                    JsonValue field = fields.require(i);
                    decoded[i] = ShaderStructField.of(field.requireString("id"),
                            readType(field.require("type")));
                }
                yield ShaderGraphType.structure(ShaderStructType.of(
                        value.requireString("id"), decoded));
            }
            case TEXTURE -> ShaderGraphType.texture(
                    enumValue(ShaderTextureDimension.class,
                            value.requireString("dimension"), "texture dimension"),
                    enumValue(ShaderTextureSampleType.class,
                            value.requireString("sample"), "texture sample type"),
                    value.require("multisampled").booleanValue());
            case SAMPLER -> ShaderGraphType.sampler(
                    enumValue(ShaderSamplerKind.class,
                            value.requireString("sampler"), "sampler kind"));
            case STORAGE_BUFFER -> ShaderGraphType.storageBuffer(
                    readType(value.require("element")),
                    enumValue(ShaderResourceAccess.class,
                            value.requireString("access"),
                            "storage-buffer access"));
            case STORAGE_TEXTURE -> {
                ShaderTextureDimension dimension = enumValue(
                        ShaderTextureDimension.class,
                        value.requireString("dimension"),
                        "storage-texture dimension");
                if (dimension != ShaderTextureDimension.D2) {
                    throw new FdxException(
                            "Only 2D graph storage textures are supported");
                }
                yield ShaderGraphType.storageTexture2D(
                        enumValue(ShaderStorageTextureFormat.class,
                                value.requireString("format"),
                                "storage-texture format"),
                        enumValue(ShaderResourceAccess.class,
                                value.requireString("access"),
                                "storage-texture access"));
            }
            case WORKGROUP_ARRAY -> ShaderGraphType.workgroupArray(
                    readType(value.require("element")),
                    value.require("count").intValue());
        };
    }

    private static JsonValue valueType(ShaderValueType value) {
        JsonValue result = JsonValue.object()
                .put("kind", value.kind().name().toLowerCase())
                .put("scalar", value.scalarType().name().toLowerCase())
                .put("columns", value.columns())
                .put("rows", value.rows())
                .put("count", value.arrayCount())
                .put("arrayStride", value.arrayStride())
                .put("matrixStride", value.matrixStride())
                .put("name", value.typeName());
        if (value.elementType() != null) {
            result.put("element", valueType(value.elementType()));
        }
        return result;
    }

    private static ShaderValueType readValueType(JsonValue value) {
        ShaderValueKind kind = enumValue(ShaderValueKind.class,
                value.requireString("kind"), "shader value kind");
        ShaderScalarType scalar = enumValue(ShaderScalarType.class,
                value.requireString("scalar"), "shader scalar type");
        return switch (kind) {
            case SCALAR -> ShaderValueType.scalar(scalar);
            case ATOMIC -> ShaderValueType.atomic(scalar);
            case VECTOR -> ShaderValueType.vector(scalar,
                    value.require("rows").intValue());
            case MATRIX -> ShaderValueType.matrix(scalar,
                    value.require("columns").intValue(),
                    value.require("rows").intValue(),
                    value.require("matrixStride").longValue());
            case ARRAY -> {
                ShaderValueType element = readValueType(value.require("element"));
                long count = value.require("count").longValue();
                long stride = value.require("arrayStride").longValue();
                yield count < 0 ? ShaderValueType.runtimeArray(element, stride)
                        : ShaderValueType.array(element, count, stride);
            }
            case STRUCT -> ShaderValueType.structure(value.requireString("name"));
            case BUFFER -> ShaderValueType.buffer(value.requireString("name"));
            case UNKNOWN -> ShaderValueType.unknown();
        };
    }

    static JsonValue literal(ShaderGraphLiteral value) {
        JsonValue elements = JsonValue.array();
        for (ShaderGraphLiteral element : value.elements()) {
            elements.add(literal(element));
        }
        return JsonValue.object()
                .put("type", type(value.type()))
                .put("bits", value.bits())
                .put("elements", elements);
    }

    static ShaderGraphLiteral readLiteral(JsonValue value) {
        ShaderGraphType type = readType(value.require("type"));
        JsonValue elements = value.require("elements");
        requireArray(elements, "literal elements");
        if (elements.size() == 0) {
            return ShaderGraphLiteral.scalar(type, value.require("bits").longValue());
        }
        ShaderGraphLiteral[] decoded = new ShaderGraphLiteral[elements.size()];
        for (int i = 0; i < decoded.length; i++) {
            decoded[i] = readLiteral(elements.require(i));
        }
        return ShaderGraphLiteral.composite(type, decoded);
    }

    private static void requireArray(JsonValue value, String kind) {
        if (value == null || !value.isArray()) {
            throw new FdxException("Shader graph " + kind + " must be a JSON array");
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type,
            String value, String kind) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new FdxException("Unknown shader " + kind + ": " + value);
        }
    }
}
