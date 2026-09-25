package io.github.libfdx.graphics.g3d.lod;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.json.Json;
import io.github.libfdx.json.JsonValue;
import java.math.BigDecimal;

/**
 * Optional portable reader for version-1 .glb.lod.json sidecars. Performs no I/O,
 * loading or GPU work. Applications resolve returned asset paths with their own
 * asset scope and publish ready instances into ModelLodModels. Reading allocates;
 * do it during loading, never during frame selection. Programmatic LOD needs no
 * sidecar. Paths are plain project-relative asset keys, not URLs or OS paths.
 */
public final class ModelLodDefinition {
    private final ArrayView<ModelLodLevel> levels;
    private final ModelLodConfig config;

    private ModelLodDefinition(Array<ModelLodLevel> levels, float[] thresholds) {
        this.levels = levels.view();
        config = new ModelLodConfig(thresholds);
    }

    /**
     * Reads at most 65,536 characters and eight reduced levels. Unknown provenance
     * fields are ignored. Rejects traversal, absolute paths, duplicate/base assets,
     * unsupported versions and invalid switching data with IllegalArgumentException.
     * Reduced assets must be GLBs inside the base model's directory or descendants.
     */
    public static ModelLodDefinition read(String text, String baseAssetPath) {
        if (text == null || text.length() > 65536)
            throw new IllegalArgumentException("LOD metadata must fit within 65,536 characters");
        try {
            return parse(text, baseAssetPath);
        } catch (FdxException error) {
            throw new IllegalArgumentException("Invalid LOD metadata: " + error.getMessage(), error);
        }
    }

    private static ModelLodDefinition parse(String text, String baseAssetPath) {
        String base = normalize(baseAssetPath);
        int slash = base.lastIndexOf('/');
        String parent = slash < 0 ? "" : base.substring(0, slash + 1);
        JsonValue root = new Json().read(text);
        if (!root.isObject() || new BigDecimal(root.require("version").numberLiteral()).compareTo(BigDecimal.ONE) != 0)
            throw new IllegalArgumentException("Unsupported LOD metadata version");
        JsonValue entries = root.require("levels");
        if (!entries.isArray() || entries.size() > ModelLodConfig.MAX_REDUCED_LEVELS)
            throw new IllegalArgumentException("LOD metadata requires at most eight reduced levels");
        Array<ModelLodLevel> levels = new Array<>();
        float[] thresholds = new float[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            JsonValue entry = entries.require(i);
            String relative = normalize(entry.requireString("asset"));
            if (!relative.toLowerCase(java.util.Locale.ROOT).endsWith(".glb"))
                throw new IllegalArgumentException("Reduced LOD assets must be GLBs");
            String asset = parent + relative;
            if (asset.equals(base)) throw new IllegalArgumentException("A reduced level cannot reference its base");
            for (int j = 0; j < levels.size(); j++) if (levels.get(j).assetPath().equals(asset))
                throw new IllegalArgumentException("Duplicate LOD asset: " + asset);
            float pixels = entry.require("maxScreenPixels").floatValue();
            long triangles = triangleCount(entry.get("triangleCount"));
            levels.add(new ModelLodLevel(asset, pixels, triangles));
            thresholds[i] = pixels;
        }
        return new ModelLodDefinition(levels, thresholds);
    }

    private static long triangleCount(JsonValue value) {
        if (value == null) return 0;
        if (value.isNumber()) {
            try {
                long count = new BigDecimal(value.numberLiteral()).longValueExact();
                if (count >= 0) return count;
            } catch (ArithmeticException | NumberFormatException ignored) {
                // Report the same contract violation for fractions and overflow.
            }
        }
        throw new IllegalArgumentException("LOD triangle count must be a nonnegative integer within long range");
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("LOD asset path cannot be empty");
        String value = path.trim().replace('\\', '/');
        if (value.startsWith("/") || value.indexOf(':') >= 0)
            throw new IllegalArgumentException("LOD asset paths must be relative");
        StringBuilder result = new StringBuilder();
        for (String part : value.split("/")) {
            if (part.equals("..")) throw new IllegalArgumentException("LOD asset traversal is not allowed");
            if (part.isEmpty() || part.equals(".")) continue;
            for (int i = 0; i < part.length(); i++) if (Character.isISOControl(part.charAt(i)))
                throw new IllegalArgumentException("Control character in LOD asset path");
            if (!result.isEmpty()) result.append('/');
            result.append(part);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("LOD asset path cannot be empty");
        return result.toString();
    }

    /** Immutable reduced levels; level zero is the caller's base model. */
    public ArrayView<ModelLodLevel> levels() { return levels; }
    /** Immutable switching configuration in the same order as levels(). */
    public ModelLodConfig config() { return config; }
}
