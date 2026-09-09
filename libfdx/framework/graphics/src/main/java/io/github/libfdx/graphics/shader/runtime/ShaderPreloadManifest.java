package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Deterministic versioned recipe catalog, distinct from compiled-code caches. Each capture
 * retains segment membership and completeness diagnostics when catalogs are merged.
 * JSON contains no native objects. Parse/encode during import/export, not in a draw loop. */
public final class ShaderPreloadManifest {
    public static final int VERSION = 1;
    private static final int MAX_RECIPES = 65536;

    /** One named loading segment. Partial captures retain known recipes but cannot establish
     * complete coverage. Lost history affects miss classification, not retained recipe inputs. */
    public record Segment(String label, List<ShaderPreloadRecipe> recipes,
            long droppedDemands, long forgottenPreloadDeclarations) {
        public Segment {
            if (label == null || label.isBlank() || droppedDemands < 0 || forgottenPreloadDeclarations < 0) {
                throw new FdxException("Invalid shader capture segment metadata");
            }
            recipes = unique(recipes);
        }
    }

    private final List<Segment> segments;
    private final List<String> captures;
    private final List<ShaderPreloadRecipe> recipes;

    /** Declares the supplied recipes in every named segment, without capture truncation. */
    public ShaderPreloadManifest(List<String> captures, List<ShaderPreloadRecipe> recipes) {
        this(declared(captures, recipes));
    }

    public ShaderPreloadManifest(List<Segment> segments) {
        if (segments.size() > MAX_RECIPES) throw new FdxException("Preload manifest exceeds segment limit");
        TreeMap<String, Segment> merged = new TreeMap<>();
        for (Segment segment : segments) {
            Segment previous = merged.get(segment.label());
            if (previous != null) {
                List<ShaderPreloadRecipe> union = new ArrayList<>(previous.recipes());
                union.addAll(segment.recipes());
                segment = new Segment(segment.label(), union,
                        Math.max(previous.droppedDemands(), segment.droppedDemands()),
                        Math.max(previous.forgottenPreloadDeclarations(), segment.forgottenPreloadDeclarations()));
            }
            merged.put(segment.label(), segment);
        }
        this.segments = List.copyOf(merged.values());
        captures = List.copyOf(merged.keySet());
        List<ShaderPreloadRecipe> all = new ArrayList<>();
        for (Segment segment : this.segments) {
            if (all.size() + segment.recipes().size() > MAX_RECIPES) throw new FdxException("Preload manifest exceeds recipe limit");
            all.addAll(segment.recipes());
        }
        recipes = unique(all);
    }

    public List<Segment> segments() { return segments; }
    public List<String> captures() { return captures; }
    public List<ShaderPreloadRecipe> recipes() { return recipes; }

    /** Selects named loading segments. Unknown names fail instead of creating an empty preload. */
    public ShaderPreloadManifest select(String... labels) {
        List<Segment> selected = new ArrayList<>();
        for (String label : labels) {
            Segment found = null;
            for (Segment segment : segments) if (segment.label().equals(label)) { found = segment; break; }
            if (found == null) throw new FdxException("Unknown shader capture segment: " + label);
            selected.add(found);
        }
        return new ShaderPreloadManifest(selected);
    }

    /** Explicit allocating diagnostics. Nonempty diagnostics do not discard valid recipes. */
    public List<String> diagnostics() {
        List<String> result = new ArrayList<>();
        for (Segment segment : segments) {
            if (segment.droppedDemands() != 0) result.add(segment.label() + ": incomplete capture; "
                    + segment.droppedDemands() + " demands exceeded requirement/origin limits. Capture a smaller segment or raise the requirement limit.");
            if (segment.forgottenPreloadDeclarations() != 0) result.add(segment.label() + ": "
                    + segment.forgottenPreloadDeclarations() + " old preload declarations were discarded; some miss causes may be unknown.");
        }
        return List.copyOf(result);
    }

    public String toJson() {
        JsonWriter writer = JsonWriter.prettyWriter().object().name("version").value(VERSION).name("captures").array();
        for (Segment segment : segments) {
            writer.object().name("label").value(segment.label()).name("droppedDemands").value(segment.droppedDemands())
                    .name("forgottenPreloadDeclarations").value(segment.forgottenPreloadDeclarations()).name("recipes").array();
            for (ShaderPreloadRecipe recipe : segment.recipes()) writeRecipe(writer, recipe);
            writer.endArray().endObject();
        }
        return writer.endArray().endObject().toString();
    }

    public static ShaderPreloadManifest fromJson(String json) {
        if (json == null || json.length() > 32 * 1024 * 1024) throw new FdxException("Invalid preload manifest size");
        JsonValue root = new JsonReader().parse(json);
        if (root.require("version").intValue() != VERSION) throw new FdxException("Unsupported preload manifest version");
        JsonValue source = root.require("captures");
        requireArray(source);
        List<Segment> segments = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < source.size(); i++) {
            JsonValue segment = source.require(i), sourceRecipes = segment.require("recipes");
            requireArray(sourceRecipes);
            total += sourceRecipes.size();
            if (total > MAX_RECIPES) throw new FdxException("Preload manifest exceeds recipe limit");
            List<ShaderPreloadRecipe> recipes = new ArrayList<>();
            for (int r = 0; r < sourceRecipes.size(); r++) {
                JsonValue recipe = sourceRecipes.require(r);
                recipes.add(new ShaderPreloadRecipe(recipe.requireString("factory"), recipe.require("version").intValue(),
                        recipe.requireString("targetRole"), readMap(recipe.require("parameters")), readMap(recipe.require("conditions"))));
            }
            segments.add(new Segment(segment.requireString("label"), recipes, segment.require("droppedDemands").longValue(),
                    segment.require("forgottenPreloadDeclarations").longValue()));
        }
        return new ShaderPreloadManifest(segments);
    }

    /** Union preserves segment membership and recipe conditions. Repeated segments retain
     * the greatest truncation count so merging copies does not multiply dropped demand. */
    public static ShaderPreloadManifest merge(List<ShaderPreloadManifest> manifests) {
        List<Segment> segments = new ArrayList<>();
        for (ShaderPreloadManifest manifest : manifests) segments.addAll(manifest.segments);
        return new ShaderPreloadManifest(segments);
    }

    private static List<Segment> declared(List<String> captures, List<ShaderPreloadRecipe> recipes) {
        List<Segment> segments = new ArrayList<>();
        for (String capture : captures) segments.add(new Segment(capture, recipes, 0, 0));
        if (segments.isEmpty() && !recipes.isEmpty()) throw new FdxException("Shader recipes require a named loading segment");
        return segments;
    }
    private static List<ShaderPreloadRecipe> unique(List<ShaderPreloadRecipe> recipes) {
        TreeMap<String, ShaderPreloadRecipe> unique = new TreeMap<>();
        for (ShaderPreloadRecipe recipe : recipes) {
            unique.put(recipeJson(recipe), recipe);
            if (unique.size() > MAX_RECIPES) throw new FdxException("Preload manifest exceeds recipe limit");
        }
        return List.copyOf(unique.values());
    }
    private static void requireArray(JsonValue value) {
        if (!value.isArray() || value.size() > MAX_RECIPES) throw new FdxException("Invalid preload manifest array");
    }
    static String recipeJson(ShaderPreloadRecipe recipe) {
        JsonWriter writer = new JsonWriter(); writeRecipe(writer, recipe); return writer.toString();
    }
    private static void writeRecipe(JsonWriter writer, ShaderPreloadRecipe recipe) {
        writer.object().name("factory").value(recipe.factory()).name("version").value(recipe.version())
                .name("targetRole").value(recipe.targetRole()).name("parameters");
        writeMap(writer, recipe.parameters()); writer.name("conditions"); writeMap(writer, recipe.conditions()); writer.endObject();
    }
    private static void writeMap(JsonWriter writer, Map<String, String> values) {
        writer.object(); for (var value : values.entrySet()) writer.name(value.getKey()).value(value.getValue()); writer.endObject();
    }
    private static Map<String, String> readMap(JsonValue values) {
        if (!values.isObject()) throw new FdxException("Preload recipe parameters/conditions must be objects");
        TreeMap<String, String> result = new TreeMap<>();
        var entries = values.objectMembers().entries().iterator();
        while (entries.hasNext()) { var entry = entries.next(); result.put(entry.key(), entry.value().stringValue()); }
        return result;
    }
}
