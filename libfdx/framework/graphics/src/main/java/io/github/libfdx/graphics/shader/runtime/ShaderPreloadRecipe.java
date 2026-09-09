package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.FdxException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Portable immutable instructions for a game-owned resolver. Factory versions identify source/
 * technique schemas across launches; never use process revisions, native handles or driver blobs.
 * Parameters include structural configuration/content identity. Target roles are resolved against
 * the current graphics setup rather than replaying a captured desktop attachment format. */
public record ShaderPreloadRecipe(String factory, int version, String targetRole,
        Map<String, String> parameters, Map<String, String> conditions) {
    public ShaderPreloadRecipe {
        if (factory == null || factory.isBlank() || version <= 0 || targetRole == null || targetRole.isBlank()) {
            throw new FdxException("Preload recipe requires a factory, positive schema version and logical target role");
        }
        parameters = immutable(parameters);
        conditions = immutable(conditions);
    }
    private static Map<String, String> immutable(Map<String, String> values) {
        TreeMap<String, String> copy = new TreeMap<>();
        for (var value : Objects.requireNonNull(values).entrySet()) {
            copy.put(Objects.requireNonNull(value.getKey()), Objects.requireNonNull(value.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}
