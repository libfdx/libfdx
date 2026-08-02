package io.github.libfdx.graphics.shadergraph.document;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ObjectMapView;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCache;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCacheCodec;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphCodec;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeProgramCodec;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechniqueCodec;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgramCodec;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniqueCodec;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;

/**
 * Deterministic codec for one self-contained {@code .fdxgraph} document.
 */
public final class ShaderGraphDocumentCodec {
    private ShaderGraphDocumentCodec() {
    }

    public static String write(ShaderGraphDocument document) {
        if (document == null) {
            throw new FdxException("Shader graph document cannot be null");
        }
        JsonValue semantic = JsonValue.object()
                .put("kind", document.kind().id())
                .put("value", parse(document.semanticSource(), "semantic"));
        JsonValue root = JsonValue.object()
                .put("format", ShaderGraphDocumentFormat.CURRENT_VERSION)
                .put("semantic", semantic);
        if (document.hasEditor()) {
            root.put("editor", parse(document.editorJson(), "editor"));
        }
        if (document.hasCompiled()) {
            root.put("compiled", parse(
                    ShaderGraphCompiledCacheCodec.write(
                            document.compiledCache()), "compiled"));
        }
        return JsonWriter.compact(root);
    }

    public static ShaderGraphDocument read(String source) {
        return readResult(source).document();
    }

    /**
     * Reads semantic data even when optional compiled entries are rejected.
     */
    public static ShaderGraphDocumentReadResult readResult(String source) {
        JsonValue root = parse(source, "document");
        if (!root.isObject()
                || root.require("format").intValue()
                        != ShaderGraphDocumentFormat.CURRENT_VERSION) {
            throw new FdxException(
                    "Unsupported shader graph document format");
        }
        JsonValue semantic = root.require("semantic");
        if (!semantic.isObject()) {
            throw new FdxException(
                    "Shader graph document semantic value must be an object");
        }
        ShaderGraphDocumentKind kind = ShaderGraphDocumentKind.fromId(
                semantic.requireString("kind"));
        String semanticSource = JsonWriter.compact(
                canonicalize(semantic.require("value")));
        ShaderGraphDocument document = switch (kind) {
            case GRAPH -> ShaderGraphDocument.of(
                    ShaderGraphCodec.read(semanticSource));
            case PROGRAM -> ShaderGraphDocument.of(
                    ShaderGraphProgramCodec.read(semanticSource));
            case COMPUTE_PROGRAM -> ShaderGraphDocument.of(
                    ShaderGraphComputeProgramCodec.read(semanticSource));
            case TECHNIQUE -> ShaderGraphDocument.of(
                    ShaderGraphTechniqueCodec.read(semanticSource));
            case COMPUTE_TECHNIQUE -> ShaderGraphDocument.of(
                    ShaderGraphComputeTechniqueCodec.read(semanticSource));
        };
        JsonValue editor = root.get("editor");
        JsonValue compiled = root.get("compiled");
        ShaderGraphCompiledCache cache = null;
        ShaderGraphCompiledCacheCodec.Rejection[] rejections =
                new ShaderGraphCompiledCacheCodec.Rejection[0];
        if (compiled != null) {
            ShaderGraphCompiledCacheCodec.DecodeResult decoded =
                    ShaderGraphCompiledCacheCodec.read(
                            JsonWriter.compact(compiled),
                            document.semanticHash());
            if (decoded.acceptedAll()
                    || decoded.cache().size() > 0) {
                cache = decoded.cache();
            }
            rejections = decoded.rejections();
        }
        document = document.sections(optionalSource(editor), cache);
        return new ShaderGraphDocumentReadResult(document, rejections);
    }

    static String normalizeOptional(String source, String label) {
        if (source == null || source.trim().isEmpty()) {
            return null;
        }
        return JsonWriter.compact(canonicalize(parse(source, label)));
    }

    private static String optionalSource(JsonValue value) {
        return value != null
                ? JsonWriter.compact(canonicalize(value)) : null;
    }

    private static JsonValue parse(String source, String label) {
        if (source == null || source.trim().isEmpty()) {
            throw new FdxException(
                    "Shader graph " + label + " JSON cannot be empty");
        }
        return new JsonReader().parse(source);
    }

    private static JsonValue canonicalize(JsonValue value) {
        if (value == null) {
            return JsonValue.nullValue();
        }
        return switch (value.type()) {
            case OBJECT -> {
                JsonValue result = JsonValue.object();
                ObjectMapView<String, JsonValue> members = value.objectMembers();
                Array<String> sortedKeys = new Array<String>(members.size());
                for (String key : members.keys()) {
                    sortedKeys.add(key);
                }
                sortedKeys.sort();
                for (int i = 0; i < sortedKeys.size(); i++) {
                    String key = sortedKeys.get(i);
                    result.put(key, canonicalize(members.get(key)));
                }
                yield result;
            }
            case ARRAY -> {
                JsonValue result = JsonValue.array();
                for (JsonValue item : value.arrayValues()) {
                    result.add(canonicalize(item));
                }
                yield result;
            }
            case STRING -> JsonValue.value(value.stringValue());
            case NUMBER -> new JsonReader().parse(
                    JsonWriter.compact(value));
            case BOOLEAN -> JsonValue.value(value.booleanValue());
            case NULL -> JsonValue.nullValue();
        };
    }
}
