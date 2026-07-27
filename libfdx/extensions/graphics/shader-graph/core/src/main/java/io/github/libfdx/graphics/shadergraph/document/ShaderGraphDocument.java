package io.github.libfdx.graphics.shadergraph.document;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCache;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphCodec;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeProgram;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeProgramCodec;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechnique;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechniqueCodec;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgramCodec;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechnique;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniqueCodec;

import java.util.Objects;

/**
 * Immutable, headless representation of one self-contained {@code .fdxgraph}.
 *
 * <p>The semantic value is required. Editor JSON remains opaque so graph core
 * does not depend on UI Kit; the optional compiled cache is provider-neutral
 * typed data. Neither participates in {@link #semanticHash()}.</p>
 */
public final class ShaderGraphDocument {
    private final ShaderGraphDocumentKind kind;
    private final Object semantic;
    private final String semanticSource;
    private final String semanticHash;
    private final String id;
    private final String editorJson;
    private final ShaderGraphCompiledCache compiledCache;

    private ShaderGraphDocument(ShaderGraphDocumentKind kind, Object semantic,
            String semanticSource, String semanticHash, String id,
            String editorJson,
            ShaderGraphCompiledCache compiledCache) {
        if (kind == null || semantic == null || semanticSource == null
                || semanticHash == null || semanticHash.isEmpty()
                || id == null || id.isEmpty()) {
            throw new FdxException("Shader graph document is incomplete");
        }
        this.kind = kind;
        this.semantic = semantic;
        this.semanticSource = semanticSource;
        this.semanticHash = semanticHash;
        this.id = id;
        this.editorJson = editorJson;
        this.compiledCache = compiledCache;
    }

    public static ShaderGraphDocument of(ShaderGraph graph) {
        require(graph, "graph");
        return new ShaderGraphDocument(ShaderGraphDocumentKind.GRAPH, graph,
                ShaderGraphCodec.write(graph), graph.semanticHash(),
                graph.id().value(), null, null);
    }

    public static ShaderGraphDocument of(ShaderGraphProgram program) {
        require(program, "program");
        return new ShaderGraphDocument(ShaderGraphDocumentKind.PROGRAM, program,
                ShaderGraphProgramCodec.write(program), program.semanticHash(),
                program.id().value(), null, null);
    }

    public static ShaderGraphDocument of(ShaderGraphComputeProgram program) {
        require(program, "compute program");
        return new ShaderGraphDocument(ShaderGraphDocumentKind.COMPUTE_PROGRAM,
                program, ShaderGraphComputeProgramCodec.write(program),
                program.semanticHash(), program.id().value(), null, null);
    }

    public static ShaderGraphDocument of(ShaderGraphTechnique technique) {
        require(technique, "technique");
        return new ShaderGraphDocument(ShaderGraphDocumentKind.TECHNIQUE,
                technique, ShaderGraphTechniqueCodec.write(technique),
                technique.semanticHash(), technique.id(), null, null);
    }

    public static ShaderGraphDocument of(
            ShaderGraphComputeTechnique technique) {
        require(technique, "compute technique");
        return new ShaderGraphDocument(
                ShaderGraphDocumentKind.COMPUTE_TECHNIQUE, technique,
                ShaderGraphComputeTechniqueCodec.write(technique),
                technique.semanticHash(), technique.id(), null, null);
    }

    public ShaderGraphDocumentKind kind() {
        return kind;
    }

    public String id() {
        return id;
    }

    public String semanticHash() {
        return semanticHash;
    }

    /**
     * Returns deterministic semantic JSON without the document envelope.
     */
    public String semanticSource() {
        return semanticSource;
    }

    public ShaderGraph graph() {
        return kind == ShaderGraphDocumentKind.GRAPH
                ? (ShaderGraph)semantic : null;
    }

    public ShaderGraphProgram program() {
        return kind == ShaderGraphDocumentKind.PROGRAM
                ? (ShaderGraphProgram)semantic : null;
    }

    public ShaderGraphComputeProgram computeProgram() {
        return kind == ShaderGraphDocumentKind.COMPUTE_PROGRAM
                ? (ShaderGraphComputeProgram)semantic : null;
    }

    public ShaderGraphTechnique technique() {
        return kind == ShaderGraphDocumentKind.TECHNIQUE
                ? (ShaderGraphTechnique)semantic : null;
    }

    public ShaderGraphComputeTechnique computeTechnique() {
        return kind == ShaderGraphDocumentKind.COMPUTE_TECHNIQUE
                ? (ShaderGraphComputeTechnique)semantic : null;
    }

    public boolean hasEditor() {
        return editorJson != null;
    }

    /**
     * Returns canonical editor JSON, or {@code null} when absent.
     */
    public String editorJson() {
        return editorJson;
    }

    public boolean hasCompiled() {
        return compiledCache != null;
    }

    /**
     * Returns the typed compiled cache, or {@code null} when absent.
     */
    public ShaderGraphCompiledCache compiledCache() {
        return compiledCache;
    }

    /**
     * Returns a copy with an opaque editor block. A null or blank value removes
     * the block.
     */
    public ShaderGraphDocument withEditorJson(String source) {
        return sections(ShaderGraphDocumentCodec.normalizeOptional(
                source, "editor"), compiledCache);
    }

    public ShaderGraphDocument withoutEditor() {
        return sections(null, compiledCache);
    }

    /**
     * Returns a copy with a typed compiled cache. A null value removes it.
     */
    public ShaderGraphDocument withCompiledCache(
            ShaderGraphCompiledCache cache) {
        return sections(editorJson, cache);
    }

    public ShaderGraphDocument withoutCompiled() {
        return sections(editorJson, null);
    }

    ShaderGraphDocument sections(String editorJson,
            ShaderGraphCompiledCache compiledCache) {
        if (Objects.equals(this.editorJson, editorJson)
                && Objects.equals(this.compiledCache, compiledCache)) {
            return this;
        }
        return new ShaderGraphDocument(kind, semantic, semanticSource,
                semanticHash, id, editorJson, compiledCache);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphDocument other
                && kind == other.kind
                && semanticSource.equals(other.semanticSource)
                && Objects.equals(editorJson, other.editorJson)
                && Objects.equals(compiledCache, other.compiledCache);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, semanticSource, editorJson, compiledCache);
    }

    private static void require(Object value, String label) {
        if (value == null) {
            throw new FdxException("Shader graph document " + label
                    + " cannot be null");
        }
    }
}
