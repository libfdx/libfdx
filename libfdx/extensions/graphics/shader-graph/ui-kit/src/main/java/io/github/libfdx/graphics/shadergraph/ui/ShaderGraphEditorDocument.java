package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocument;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocumentKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeProgram;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechnique;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeVariant;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechnique;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable editor wrapper over every headless shader graph asset kind.
 *
 * <p>The wrapper does not add editor data to the semantic object. Node layout,
 * selection, and viewport state are owned by {@link ShaderGraphEditorLayout}.
 * Embedded graphs are replaced by rebuilding their immutable parent programs,
 * variants, passes, and techniques.</p>
 */
public final class ShaderGraphEditorDocument {
    private final ShaderGraphDocument document;

    private ShaderGraphEditorDocument(ShaderGraphDocument document) {
        if (document == null) {
            throw new FdxException("Shader graph editor document is incomplete");
        }
        this.document = document;
    }

    public static ShaderGraphEditorDocument of(ShaderGraph graph) {
        require(graph, "graph");
        return new ShaderGraphEditorDocument(ShaderGraphDocument.of(graph));
    }

    public static ShaderGraphEditorDocument of(ShaderGraphProgram program) {
        require(program, "program");
        return new ShaderGraphEditorDocument(ShaderGraphDocument.of(program));
    }

    public static ShaderGraphEditorDocument of(ShaderGraphComputeProgram program) {
        require(program, "compute program");
        return new ShaderGraphEditorDocument(ShaderGraphDocument.of(program));
    }

    public static ShaderGraphEditorDocument of(ShaderGraphTechnique technique) {
        require(technique, "technique");
        return new ShaderGraphEditorDocument(ShaderGraphDocument.of(technique));
    }

    public static ShaderGraphEditorDocument of(ShaderGraphComputeTechnique technique) {
        require(technique, "compute technique");
        return new ShaderGraphEditorDocument(ShaderGraphDocument.of(technique));
    }

    public static ShaderGraphEditorDocument of(ShaderGraphDocument document) {
        return new ShaderGraphEditorDocument(document);
    }

    public ShaderGraphDocument shaderDocument() {
        return document;
    }

    public ShaderGraphDocumentKind type() {
        return document.kind();
    }

    public String id() {
        return document.id();
    }

    public String semanticHash() {
        return document.semanticHash();
    }

    public String semanticSource() {
        return document.semanticSource();
    }

    public ShaderGraph graph() {
        return document.graph();
    }

    public ShaderGraphProgram program() {
        return document.program();
    }

    public ShaderGraphComputeProgram computeProgram() {
        return document.computeProgram();
    }

    public ShaderGraphTechnique technique() {
        return document.technique();
    }

    public ShaderGraphComputeTechnique computeTechnique() {
        return document.computeTechnique();
    }

    /**
     * Returns all embedded semantic graphs in stable graph-ID order.
     *
     * @return immutable graph copies owned by the semantic document
     */
    public ShaderGraph[] graphs() {
        TreeMap<String, ShaderGraph> graphs = new TreeMap<String, ShaderGraph>();
        collectGraphs(graphs);
        return graphs.values().toArray(new ShaderGraph[graphs.size()]);
    }

    public ShaderGraph graph(String graphId) {
        if (graphId == null) {
            return null;
        }
        for (ShaderGraph graph : graphs()) {
            if (graph.id().value().equals(graphId)) {
                return graph;
            }
        }
        return null;
    }

    /**
     * Replaces every matching embedded graph while preserving all surrounding
     * program, entry-point, workgroup, pass, state, variant, profile, feature,
     * static-value, fallback, and limit data.
     *
     * @param replacement the replacement graph
     * @return the rebuilt editor document
     */
    public ShaderGraphEditorDocument withGraph(ShaderGraph replacement) {
        require(replacement, "replacement graph");
        boolean[] changed = new boolean[1];
        ShaderGraphEditorDocument result;
        switch (type()) {
            case GRAPH -> {
                ShaderGraph current = graph();
                if (!current.id().equals(replacement.id())) {
                    throw missingGraph(replacement.id());
                }
                result = of(replacement);
                changed[0] = true;
            }
            case PROGRAM -> result = of(replaceProgram(program(), replacement, changed));
            case COMPUTE_PROGRAM -> result = of(replaceComputeProgram(computeProgram(), replacement, changed));
            case TECHNIQUE -> result = of(replaceTechnique(technique(), replacement, changed));
            case COMPUTE_TECHNIQUE ->
                    result = of(replaceComputeTechnique(computeTechnique(), replacement, changed));
            default -> throw new FdxException(
                    "Unsupported shader graph editor document type " + type());
        }
        if (!changed[0]) {
            throw missingGraph(replacement.id());
        }
        if (document.hasEditor()) {
            result = new ShaderGraphEditorDocument(
                    result.document.withEditorJson(document.editorJson()));
        }
        return result;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphEditorDocument other
                && type() == other.type()
                && semanticSource().equals(other.semanticSource());
    }

    @Override
    public int hashCode() {
        return Objects.hash(type(), semanticSource());
    }

    private void collectGraphs(Map<String, ShaderGraph> graphs) {
        switch (type()) {
            case GRAPH -> addGraph(graphs, graph());
            case PROGRAM -> collectProgram(graphs, program());
            case COMPUTE_PROGRAM -> addGraph(graphs, computeProgram().graph());
            case TECHNIQUE -> {
                for (ShaderGraphTechniquePass pass : technique().passes()) {
                    for (ShaderGraphVariant variant : pass.variants()) {
                        collectProgram(graphs, variant.sourceProgram());
                    }
                }
            }
            case COMPUTE_TECHNIQUE -> {
                for (ShaderGraphComputeTechniquePass pass : computeTechnique().passes()) {
                    for (ShaderGraphComputeVariant variant : pass.variants()) {
                        addGraph(graphs, variant.sourceProgram().graph());
                    }
                }
            }
            default -> throw new FdxException(
                    "Unsupported shader graph editor document type " + type());
        }
    }

    private static void collectProgram(Map<String, ShaderGraph> graphs, ShaderGraphProgram program) {
        addGraph(graphs, program.vertex());
        addGraph(graphs, program.fragment());
    }

    private static void addGraph(Map<String, ShaderGraph> graphs, ShaderGraph graph) {
        ShaderGraph previous = graphs.putIfAbsent(graph.id().value(), graph);
        if (previous != null && !previous.semanticHash().equals(graph.semanticHash())) {
            throw new FdxException("Shader graph editor document contains different graphs with the same ID: "
                    + graph.id());
        }
    }

    private static ShaderGraphProgram replaceProgram(ShaderGraphProgram program, ShaderGraph replacement,
            boolean[] changed) {
        ShaderGraph vertex = replace(program.vertex(), replacement, changed);
        ShaderGraph fragment = replace(program.fragment(), replacement, changed);
        if (vertex == program.vertex() && fragment == program.fragment()) {
            return program;
        }
        return ShaderGraphProgram.builder(program.id().value(), vertex, fragment)
                .entryPoints(program.vertexEntryPoint(), program.fragmentEntryPoint())
                .materialBinding(program.materialGroup(), program.materialBinding())
                .build();
    }

    private static ShaderGraphComputeProgram replaceComputeProgram(ShaderGraphComputeProgram program,
            ShaderGraph replacement, boolean[] changed) {
        ShaderGraph graph = replace(program.graph(), replacement, changed);
        if (graph == program.graph()) {
            return program;
        }
        return ShaderGraphComputeProgram.builder(program.id().value(), graph)
                .entryPoint(program.entryPoint())
                .workgroupSize(program.workgroupX(), program.workgroupY(), program.workgroupZ())
                .build();
    }

    private static ShaderGraphTechnique replaceTechnique(ShaderGraphTechnique technique,
            ShaderGraph replacement, boolean[] changed) {
        ShaderGraphTechniquePass[] passes = technique.passes();
        boolean localChange = false;
        for (int passIndex = 0; passIndex < passes.length; passIndex++) {
            ShaderGraphTechniquePass pass = passes[passIndex];
            ShaderGraphVariant[] variants = pass.variants();
            boolean passChange = false;
            for (int variantIndex = 0; variantIndex < variants.length; variantIndex++) {
                ShaderGraphVariant variant = variants[variantIndex];
                ShaderGraphProgram program = replaceProgram(variant.sourceProgram(), replacement, changed);
                if (program != variant.sourceProgram()) {
                    ShaderGraphVariant.Builder builder = ShaderGraphVariant.builder(variant.key(), program)
                            .staticValues(variant.staticValues())
                            .profiles(variant.profiles())
                            .features(variant.features());
                    if (variant.fallbackKey() != null) {
                        builder.fallback(variant.fallbackKey());
                    }
                    variants[variantIndex] = builder.build();
                    passChange = true;
                }
            }
            if (passChange) {
                passes[passIndex] = ShaderGraphTechniquePass
                        .builder(pass.passId(), pass.pipelineState())
                        .variants(variants)
                        .defaultVariant(pass.defaultVariantKey())
                        .build();
                localChange = true;
            }
        }
        return localChange
                ? ShaderGraphTechnique.builder(technique.id())
                        .passes(passes).maxVariants(technique.maxVariants()).build()
                : technique;
    }

    private static ShaderGraphComputeTechnique replaceComputeTechnique(
            ShaderGraphComputeTechnique technique, ShaderGraph replacement, boolean[] changed) {
        ShaderGraphComputeTechniquePass[] passes = technique.passes();
        boolean localChange = false;
        for (int passIndex = 0; passIndex < passes.length; passIndex++) {
            ShaderGraphComputeTechniquePass pass = passes[passIndex];
            ShaderGraphComputeVariant[] variants = pass.variants();
            boolean passChange = false;
            for (int variantIndex = 0; variantIndex < variants.length; variantIndex++) {
                ShaderGraphComputeVariant variant = variants[variantIndex];
                ShaderGraphComputeProgram program = replaceComputeProgram(
                        variant.sourceProgram(), replacement, changed);
                if (program != variant.sourceProgram()) {
                    ShaderGraphComputeVariant.Builder builder =
                            ShaderGraphComputeVariant.builder(variant.key(), program)
                                    .staticValues(variant.staticValues())
                                    .profiles(variant.profiles())
                                    .features(variant.features());
                    if (variant.fallbackKey() != null) {
                        builder.fallback(variant.fallbackKey());
                    }
                    variants[variantIndex] = builder.build();
                    passChange = true;
                }
            }
            if (passChange) {
                passes[passIndex] = ShaderGraphComputeTechniquePass.builder(pass.passId())
                        .variants(variants)
                        .defaultVariant(pass.defaultVariantKey())
                        .build();
                localChange = true;
            }
        }
        return localChange
                ? ShaderGraphComputeTechnique.builder(technique.id())
                        .passes(passes).maxVariants(technique.maxVariants()).build()
                : technique;
    }

    private static ShaderGraph replace(ShaderGraph current, ShaderGraph replacement, boolean[] changed) {
        if (!current.id().equals(replacement.id())) {
            return current;
        }
        if (!current.kind().equals(replacement.kind())) {
            throw new FdxException("Replacement shader graph kind does not match " + current.id());
        }
        changed[0] = true;
        return replacement;
    }

    private static FdxException missingGraph(ShaderGraphId id) {
        return new FdxException("Shader graph editor document does not contain graph " + id);
    }

    private static void require(Object value, String label) {
        if (value == null) {
            throw new FdxException("Shader graph editor " + label + " cannot be null");
        }
    }
}
