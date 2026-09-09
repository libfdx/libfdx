package io.github.libfdx.graphics.shader.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Consistent immutable export snapshot. Platform adapters encode and write both artifacts from
 * this snapshot. It owns no native resources and can be handed to an export worker. */
public record ShaderPreloadExport(String label, List<ShaderPreloadDiscovery> discoveries,
        long droppedDemands, long forgottenPreloadDeclarations) {
    public ShaderPreloadExport { discoveries = List.copyOf(discoveries); }

    public ShaderPreloadManifest manifest() {
        List<ShaderPreloadRecipe> recipes = new ArrayList<>();
        for (ShaderPreloadDiscovery discovery : discoveries) {
            for (ShaderPreparationOrigin origin : discovery.origins()) {
                if (origin.recipe() != null) recipes.add(origin.recipe());
                else recipes.add(new ShaderPreloadRecipe("libfdx.requires-input", 1, "unresolved", Map.of(
                        "renderer", origin.renderer(), "content", origin.content(),
                        "material", origin.material(), "group", origin.group(),
                        "pass", discovery.request().passId().value(), "variant", discovery.request().variantKey(),
                        "profile", discovery.request().profile().name(), "topology", discovery.request().topology().name(),
                        "vertexLayouts", ShaderPreloadVertexLayouts.encode(discovery.request().vertexLayouts()),
                        "missing", "Stable recipe factory, immutable procedural inputs and logical target role"),
                        Map.of("observedProvider", discovery.provider())));
            }
        }
        return new ShaderPreloadManifest(List.of(new ShaderPreloadManifest.Segment(label, recipes,
                droppedDemands, forgottenPreloadDeclarations)));
    }

    public String markdown() {
        StringBuilder out = new StringBuilder("# Shader preload capture: ").append(escape(label)).append("\n\n");
        out.append(discoveries.size()).append(" unique requirements; ").append(droppedDemands)
                .append(" demands dropped at requirement/origin limits; ").append(forgottenPreloadDeclarations)
                .append(" old preload declarations discarded from history. Nonzero counts are preserved as import diagnostics.\n\n")
                .append("Durations are wall-clock latency, not CPU time or render-thread stalls. Phase residence includes waits; cache timings overlap phases and may overlap each other. Late cache writes appear only in later snapshots. First draw means a successfully recorded nonempty command, not GPU completion/presentation. Unavailable means uninstrumented or not yet observed.\n\n")
                .append("| # | Renderer / content | Pass / variant | Group | Provider | Cause | Outcome | Skipped draws | Preparation ms | Action |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- | ---: | ---: | --- |\n");
        List<ShaderPreloadDiscovery> ranked = new ArrayList<>(discoveries);
        ranked.sort(Comparator.comparingLong(ShaderPreloadDiscovery::skippedDraws).reversed()
                .thenComparing(Comparator.comparingLong(ShaderPreloadDiscovery::preparationNanos).reversed())
                .thenComparingLong(ShaderPreloadDiscovery::id));
        for (ShaderPreloadDiscovery d : ranked) {
            out.append("| ").append(d.id()).append(" | ").append(escape(d.origin().renderer())).append(" / ")
                    .append(escape(d.origin().content())).append(" | ").append(escape(d.request().passId().toString()))
                    .append(" / ").append(escape(d.request().variantKey())).append(" | ").append(escape(d.origin().group()))
                    .append(" | ").append(escape(d.provider()))
                    .append(" | ").append(d.cause()).append(" | ").append(d.state()).append(" | ").append(d.skippedDraws())
                    .append(" | ").append(d.preparationNanos() / 1_000_000.0).append(" | ").append(action(d)).append(" |\n");
        }
        appendTimings(out, ranked);
        out.append("\nObserved origins (several content items can share one prepared pipeline):\n\n")
                .append("| Requirement | Renderer | Content | Material | Group | Target role | Recipe factory |\n")
                .append("| ---: | --- | --- | --- | --- | --- | --- |\n");
        for (ShaderPreloadDiscovery discovery : ranked) {
            for (ShaderPreparationOrigin origin : discovery.origins()) {
                out.append("| ").append(discovery.id()).append(" | ").append(escape(origin.renderer()))
                        .append(" | ").append(escape(origin.content())).append(" | ").append(escape(origin.material()))
                        .append(" | ").append(escape(origin.group())).append(" | ")
                        .append(origin.recipe() == null ? "REQUIRES_INPUT" : escape(origin.recipe().targetRole()))
                        .append(" | ").append(origin.recipe() == null ? "REQUIRES_INPUT" : escape(origin.recipe().factory()))
                        .append(" |\n");
            }
        }
        out.append("\nLoad the exported shader-preload.json as application content. Register each recipe factory and map its logical target role to the current graphics setup. Import it into the scope for this segment; await a successful report before activating its required content. Retain that scope for the content lifetime.\n\n")
                .append("Integration template (names below are API placeholders, not generated game variables):\n\n```java\n")
                .append("ShaderPreloadManifest manifest = ShaderPreloadManifest.fromJson(loadedManifestText);\n")
                .append("ShaderPreparationScope scope = shaders.createScope(loadingSegment);\n")
                .append("ShaderPreloadImportReport imported = scope.include(manifest, gameRecipeResolver);\n")
                .append("shaders.prepareAsync(scope.seal()).onSuccess(report -> {\n")
                .append("    // Activate required content only if !imported.hasUnresolvedEntries() && report.allReady().\n")
                .append("    // Otherwise present import/preparation diagnostics.\n});\n```\n\n")
                .append("Replay the same path with an empty compiler cache and check for new misses and skipped content. One capture does not prove complete game/platform coverage.\n");
        for (ShaderPreloadDiscovery d : discoveries) {
            if (!d.failure().isEmpty()) out.append("\n- Requirement ").append(d.id()).append(": ").append(escape(d.failure())).append('\n');
        }
        return out.toString();
    }

    private static void appendTimings(StringBuilder out, List<ShaderPreloadDiscovery> ranked) {
        out.append("\n| Requirement | Service queue ms | First draw since enqueue ms | Ready to draw ms | Draw update | Phase residence ms |\n")
                .append("| ---: | ---: | ---: | ---: | ---: | --- |\n");
        for (ShaderPreloadDiscovery d : ranked) {
            ShaderPreparationTimings t = d.timings();
            out.append("| ").append(d.id()).append(" | ").append(ms(t.queueNanos()))
                    .append(" | ").append(ms(t.firstDrawNanos())).append(" | ").append(ms(t.readyToFirstDrawNanos()))
                    .append(" | ").append(t.firstDrawUpdate() < 0 ? "unavailable" : t.firstDrawUpdate()).append(" | ");
            if (!t.phasesAvailable()) out.append("unavailable");
            else for (ShaderPreparationPhase p : ShaderPreparationPhase.values())
                if (p != ShaderPreparationPhase.COMPLETE) out.append(p).append('=').append(ms(t.phaseNanos(p))).append(' ');
            out.append(" |\n");
        }
        out.append("\nCache counters belong to the operation that initiated shared work. Pipeline cache data supplied is not proof of a driver cache hit. Aggregate merges include lookup counters and their full transaction is timed as a write.\n\n")
                .append("| Requirement | Layer | Read ms | Write ms | Hits / misses / writes | Rejected / storage failures | Compiler / pipeline calls | Driver feedbacks / hits |\n")
                .append("| ---: | --- | ---: | ---: | --- | --- | --- | --- |\n");
        for (ShaderPreloadDiscovery d : ranked) for (ShaderCacheLayer layer : ShaderCacheLayer.values()) {
            ShaderPreparationTimings t = d.timings();
            ShaderArtifactCache.Metrics m = t.cacheMetrics(layer);
            out.append("| ").append(d.id()).append(" | ").append(layer).append(" | ").append(ms(t.cacheReadNanos(layer)))
                    .append(" | ").append(ms(t.cacheWriteNanos(layer))).append(" | ");
            if (m == null) out.append("unavailable | unavailable | unavailable | unavailable");
            else out.append(m.hits()).append(" / ").append(m.misses()).append(" / ").append(m.writes())
                    .append(" | ").append(m.invalidEntries()).append(" / ").append(m.storageFailures())
                    .append(" | ").append(m.compilerInvocations()).append(" / ").append(m.pipelineCreations())
                    .append(" | ").append(m.pipelineFeedbacks()).append(" / ").append(m.pipelineCacheHits());
            out.append(" |\n");
        }
    }
    private static String ms(long nanos) { return nanos < 0 ? "unavailable" : Double.toString(nanos / 1_000_000.0); }

    private static String action(ShaderPreloadDiscovery d) {
        if (d.state() == ShaderPreparationState.FAILED) return "Correct the shader failure before preloading.";
        if (d.state() == ShaderPreparationState.UNSUPPORTED) return "Resolve the provider/capability limitation.";
        for (ShaderPreparationOrigin origin : d.origins()) {
            if (origin.recipe() == null) return "REQUIRES_INPUT: register a stable recipe factory and immutable source/configuration inputs.";
        }
        return switch (d.cause()) {
            case NONE -> "Already preloaded; retain the scope.";
            case PRELOAD_TOO_LATE -> "Start loading earlier or await the existing scope.";
            case RESIDENCY_LOST -> "Retain the level scope through gameplay.";
            case CONFIGURATION_CHANGED -> "Import the recipe for this structural configuration before its transition.";
            case NOT_PRELOADED -> "Import this recipe during segment preloading.";
            case FAILED -> "Correct the shader failure.";
            case UNSUPPORTED -> "Resolve the capability limitation.";
        };
    }
    private static String escape(String value) {
        return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ").replace("<", "&lt;").replace(">", "&gt;");
    }
}
