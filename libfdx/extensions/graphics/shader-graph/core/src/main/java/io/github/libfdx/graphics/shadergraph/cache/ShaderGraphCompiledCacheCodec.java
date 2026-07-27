package io.github.libfdx.graphics.shadergraph.cache;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Deterministic JSON codec and defensive decoder for an embedded cache block.
 */
public final class ShaderGraphCompiledCacheCodec {
    public static final int CURRENT_VERSION = 1;

    private ShaderGraphCompiledCacheCodec() {
    }

    public static String write(ShaderGraphCompiledCache cache) {
        if (cache == null) {
            throw new FdxException(
                    "Shader graph compiled cache cannot be null");
        }
        JsonValue entries = JsonValue.array();
        for (ShaderGraphCompiledCacheEntry entry : cache.entries()) {
            entries.add(entry(entry));
        }
        return JsonWriter.compact(JsonValue.object()
                .put("format", CURRENT_VERSION)
                .put("entries", entries));
    }

    /**
     * Decodes a standalone cache block without a document semantic check.
     */
    public static DecodeResult read(String source) {
        return read(source, "");
    }

    /**
     * Decodes entries independently. Invalid, duplicate, corrupt, or
     * semantic-mismatched entries are rejected while valid entries remain
     * available.
     */
    public static DecodeResult read(String source,
            String expectedSemanticHash) {
        List<Rejection> rejections = new ArrayList<Rejection>();
        JsonValue root;
        try {
            root = new JsonReader().parse(source);
            if (root == null || !root.isObject()
                    || root.require("format").intValue()
                            != CURRENT_VERSION
                    || !root.require("entries").isArray()) {
                throw new FdxException(
                        "Unsupported shader graph compiled-cache format");
            }
        } catch (RuntimeException error) {
            rejections.add(new Rejection(-1,
                    "FDXG_CACHE_BLOCK_INVALID", message(error)));
            return new DecodeResult(
                    ShaderGraphCompiledCache.empty(), rejections);
        }

        TreeMap<ShaderGraphCacheKey, ShaderGraphCompiledCacheEntry>
                decoded = new TreeMap<ShaderGraphCacheKey,
                        ShaderGraphCompiledCacheEntry>();
        JsonValue entries = root.require("entries");
        int count = Math.min(entries.size(),
                ShaderGraphCompiledCache.MAX_ENTRIES);
        if (entries.size() > ShaderGraphCompiledCache.MAX_ENTRIES) {
            rejections.add(new Rejection(-1,
                    "FDXG_CACHE_ENTRY_LIMIT",
                    "Compiled cache exceeds "
                            + ShaderGraphCompiledCache.MAX_ENTRIES
                            + " entries"));
        }
        for (int index = 0; index < count; index++) {
            try {
                ShaderGraphCompiledCacheEntry entry =
                        readEntry(entries.require(index));
                if (expectedSemanticHash != null
                        && !expectedSemanticHash.isBlank()
                        && !entry.key().semanticHash().equals(
                                expectedSemanticHash)) {
                    rejections.add(new Rejection(index,
                            "FDXG_CACHE_SEMANTIC_MISMATCH",
                            "Cache entry semantic hash does not "
                                    + "match the document"));
                    continue;
                }
                if (decoded.putIfAbsent(entry.key(), entry) != null) {
                    rejections.add(new Rejection(index,
                            "FDXG_CACHE_DUPLICATE",
                            "Cache entry duplicates an earlier key"));
                }
            } catch (RuntimeException error) {
                rejections.add(new Rejection(index,
                        "FDXG_CACHE_ENTRY_INVALID", message(error)));
            }
        }
        return new DecodeResult(ShaderGraphCompiledCache.of(
                decoded.values().toArray(
                        ShaderGraphCompiledCacheEntry[]::new)),
                rejections);
    }

    private static JsonValue entry(
            ShaderGraphCompiledCacheEntry entry) {
        return JsonValue.object()
                .put("key", key(entry.key()))
                .put("artifact", artifact(entry.artifact()))
                .put("interface",
                        shaderInterface(entry.shaderInterface()));
    }

    private static ShaderGraphCompiledCacheEntry readEntry(
            JsonValue value) {
        if (value == null || !value.isObject()) {
            throw new FdxException(
                    "Compiled cache entry must be an object");
        }
        return ShaderGraphCompiledCacheEntry.of(
                readKey(value.require("key")),
                readArtifact(value.require("artifact")),
                readInterface(value.require("interface")));
    }

    private static JsonValue key(ShaderGraphCacheKey key) {
        return JsonValue.object()
                .put("hash", key.hash())
                .put("documentFormat", key.documentFormatVersion())
                .put("semanticHash", key.semanticHash())
                .put("dependencyHash", key.dependencyHash())
                .put("compilerId", key.compilerId())
                .put("compilerVersion", key.compilerVersion())
                .put("nodeLibraryVersion",
                        key.nodeLibraryVersion())
                .put("standardLibraryVersion",
                        key.standardLibraryVersion())
                .put("profile", key.profileId())
                .put("capabilitiesHash", key.capabilitiesHash())
                .put("target", key.targetId())
                .put("artifactFormat", key.artifactFormat())
                .put("consumerEnvironment",
                        key.consumerEnvironment())
                .put("verifierId", key.verifierId())
                .put("verifierVersion", key.verifierVersion())
                .put("optionsHash", key.optionsHash())
                .put("interfaceAbiVersion",
                        key.interfaceAbiVersion())
                .put("compilationUnit", key.compilationUnit())
                .put("pass", key.passId())
                .put("variant", key.variantKey())
                .put("entryPointsHash", key.entryPointsHash());
    }

    private static ShaderGraphCacheKey readKey(JsonValue value) {
        if (value == null || !value.isObject()) {
            throw new FdxException(
                    "Compiled cache key must be an object");
        }
        ShaderGraphCacheKey key = ShaderGraphCacheKey.builder(
                        value.requireString("semanticHash"))
                .documentFormatVersion(
                        value.require("documentFormat").intValue())
                .dependencyHash(
                        value.requireString("dependencyHash"))
                .compiler(value.requireString("compilerId"),
                        value.requireString("compilerVersion"))
                .libraries(
                        value.requireString("nodeLibraryVersion"),
                        value.requireString(
                                "standardLibraryVersion"))
                .profile(value.requireString("profile"),
                        value.requireString("capabilitiesHash"))
                .target(value.requireString("target"),
                        value.requireString("artifactFormat"),
                        value.requireString(
                                "consumerEnvironment"))
                .verifier(value.requireString("verifierId"),
                        value.requireString("verifierVersion"))
                .optionsHash(value.requireString("optionsHash"))
                .interfaceAbiVersion(value.requireString(
                        "interfaceAbiVersion"))
                .compilationUnit(
                        value.requireString("compilationUnit"))
                .pass(value.requireString("pass"))
                .variant(value.requireString("variant"))
                .entryPointsHash(
                        value.requireString("entryPointsHash"))
                .build();
        requireHash(value.requireString("hash"),
                key.hash(), "cache key");
        return key;
    }

    private static JsonValue artifact(
            ShaderGraphCompiledArtifact artifact) {
        JsonValue result = JsonValue.object()
                .put("format", artifact.format())
                .put("encoding", artifact.encoding().id())
                .put("hash", artifact.contentHash());
        return artifact.encoding()
                == ShaderGraphCompiledArtifact.Encoding.TEXT
                ? result.put("source", artifact.encodedData())
                : result.put("data", artifact.encodedData());
    }

    private static ShaderGraphCompiledArtifact readArtifact(
            JsonValue value) {
        if (value == null || !value.isObject()) {
            throw new FdxException(
                    "Compiled cache artifact must be an object");
        }
        ShaderGraphCompiledArtifact.Encoding encoding =
                ShaderGraphCompiledArtifact.Encoding.fromId(
                        value.requireString("encoding"));
        ShaderGraphCompiledArtifact artifact =
                ShaderGraphCompiledArtifact.encoded(
                        value.requireString("format"), encoding,
                        value.requireString(
                                encoding
                                        == ShaderGraphCompiledArtifact
                                                .Encoding.TEXT
                                        ? "source" : "data"));
        requireHash(value.requireString("hash"),
                artifact.contentHash(), "artifact");
        return artifact;
    }

    private static JsonValue shaderInterface(
            ShaderGraphCompiledInterface shaderInterface) {
        JsonValue entryPoints = JsonValue.array();
        for (ShaderGraphCompiledInterface.EntryPoint entryPoint
                : shaderInterface.entryPoints()) {
            entryPoints.add(JsonValue.object()
                    .put("stage", entryPoint.stage().name()
                            .toLowerCase(Locale.ROOT))
                    .put("name", entryPoint.name()));
        }
        JsonValue bindings = JsonValue.array();
        for (ShaderGraphCompiledInterface.Binding binding
                : shaderInterface.bindings()) {
            bindings.add(JsonValue.object()
                    .put("group", binding.group())
                    .put("binding", binding.binding())
                    .put("name", binding.name())
                    .put("kind", binding.kind())
                    .put("type", binding.type())
                    .put("access", binding.access()));
        }
        JsonValue parameters = JsonValue.array();
        for (ShaderGraphCompiledInterface.Parameter parameter
                : shaderInterface.parameters()) {
            parameters.add(JsonValue.object()
                    .put("id", parameter.id())
                    .put("kind", parameter.kind())
                    .put("type", parameter.type())
                    .put("semantic", parameter.semantic())
                    .put("offset", parameter.offset())
                    .put("size", parameter.size()));
        }
        return JsonValue.object()
                .put("abiVersion", shaderInterface.abiVersion())
                .put("hash", shaderInterface.hash())
                .put("entryPoints", entryPoints)
                .put("bindings", bindings)
                .put("parameters", parameters);
    }

    private static ShaderGraphCompiledInterface readInterface(
            JsonValue value) {
        if (value == null || !value.isObject()) {
            throw new FdxException(
                    "Compiled cache interface must be an object");
        }
        JsonValue entryPoints = requireArray(
                value, "entryPoints");
        ShaderGraphCompiledInterface.EntryPoint[] decodedEntryPoints =
                new ShaderGraphCompiledInterface.EntryPoint[
                        entryPoints.size()];
        for (int i = 0; i < decodedEntryPoints.length; i++) {
            JsonValue item = entryPoints.require(i);
            try {
                decodedEntryPoints[i] =
                        ShaderGraphCompiledInterface.EntryPoint.of(
                                ShaderStage.valueOf(
                                        item.requireString("stage")
                                                .toUpperCase(
                                                        Locale.ROOT)),
                                item.requireString("name"));
            } catch (IllegalArgumentException error) {
                throw new FdxException(
                        "Unknown compiled-cache shader stage", error);
            }
        }
        JsonValue bindings = requireArray(value, "bindings");
        ShaderGraphCompiledInterface.Binding[] decodedBindings =
                new ShaderGraphCompiledInterface.Binding[
                        bindings.size()];
        for (int i = 0; i < decodedBindings.length; i++) {
            JsonValue item = bindings.require(i);
            decodedBindings[i] =
                    ShaderGraphCompiledInterface.Binding.of(
                            item.require("group").intValue(),
                            item.require("binding").intValue(),
                            item.requireString("name"),
                            item.requireString("kind"),
                            item.requireString("type"),
                            item.requireString("access"));
        }
        JsonValue parameters = requireArray(value, "parameters");
        ShaderGraphCompiledInterface.Parameter[] decodedParameters =
                new ShaderGraphCompiledInterface.Parameter[
                        parameters.size()];
        for (int i = 0; i < decodedParameters.length; i++) {
            JsonValue item = parameters.require(i);
            decodedParameters[i] =
                    ShaderGraphCompiledInterface.Parameter.of(
                            item.requireString("id"),
                            item.requireString("kind"),
                            item.requireString("type"),
                            item.requireString("semantic"),
                            item.require("offset").longValue(),
                            item.require("size").longValue());
        }
        ShaderGraphCompiledInterface shaderInterface =
                ShaderGraphCompiledInterface.of(
                        value.requireString("abiVersion"),
                        decodedEntryPoints, decodedBindings,
                        decodedParameters);
        requireHash(value.requireString("hash"),
                shaderInterface.hash(), "interface");
        return shaderInterface;
    }

    private static JsonValue requireArray(
            JsonValue object, String name) {
        JsonValue value = object.require(name);
        if (!value.isArray()) {
            throw new FdxException(
                    "Compiled cache " + name + " must be an array");
        }
        return value;
    }

    private static void requireHash(String actual,
            String expected, String label) {
        if (!expected.equals(actual)) {
            throw new FdxException(
                    "Shader graph " + label + " hash is corrupt");
        }
    }

    private static String message(Throwable error) {
        return error.getMessage() != null
                ? error.getMessage()
                : error.getClass().getSimpleName();
    }

    public static final class DecodeResult {
        private final ShaderGraphCompiledCache cache;
        private final Rejection[] rejections;

        DecodeResult(ShaderGraphCompiledCache cache,
                List<Rejection> rejections) {
            this.cache = cache;
            this.rejections =
                    rejections.toArray(Rejection[]::new);
        }

        public ShaderGraphCompiledCache cache() {
            return cache;
        }

        public Rejection[] rejections() {
            return rejections.clone();
        }

        public boolean acceptedAll() {
            return rejections.length == 0;
        }
    }

    public static final class Rejection {
        private final int entryIndex;
        private final String code;
        private final String message;

        Rejection(int entryIndex, String code, String message) {
            this.entryIndex = entryIndex;
            this.code = code;
            this.message = message;
        }

        public int entryIndex() {
            return entryIndex;
        }

        public String code() {
            return code;
        }

        public String message() {
            return message;
        }
    }
}
