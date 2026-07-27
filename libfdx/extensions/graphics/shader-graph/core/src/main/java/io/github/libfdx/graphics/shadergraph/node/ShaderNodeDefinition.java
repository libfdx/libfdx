package io.github.libfdx.graphics.shadergraph.node;

import io.github.libfdx.graphics.shadergraph.ir.ShaderIrOpcode;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.shader.ShaderStage;

import java.util.Arrays;
import java.util.Objects;

/**
 * Versioned registry metadata for one typed node operation.
 */
public final class ShaderNodeDefinition implements Comparable<ShaderNodeDefinition> {
    private final ShaderGraphId id;
    private final int version;
    private final ShaderIrOpcode opcode;
    private final ShaderGraphKind[] graphKinds;
    private final ShaderStage[] stages;
    private final GraphicsFeature requiredFeature;
    private final boolean webgl2;

    private ShaderNodeDefinition(ShaderGraphId id, int version,
            ShaderIrOpcode opcode, ShaderGraphKind[] graphKinds,
            ShaderStage[] stages, GraphicsFeature requiredFeature,
            boolean webgl2) {
        if (id == null || version <= 0 || opcode == null
                || graphKinds == null || graphKinds.length == 0 || stages == null) {
            throw new FdxException("Shader node definition is incomplete");
        }
        this.id = id;
        this.version = version;
        this.opcode = opcode;
        this.graphKinds = graphKinds.clone();
        this.stages = stages.clone();
        this.requiredFeature = requiredFeature;
        this.webgl2 = webgl2;
        Arrays.sort(this.graphKinds);
        Arrays.sort(this.stages);
    }

    public static ShaderNodeDefinition of(String id, int version,
            ShaderIrOpcode opcode, boolean webgl2,
            GraphicsFeature requiredFeature, ShaderStage[] stages,
            ShaderGraphKind... graphKinds) {
        return new ShaderNodeDefinition(ShaderGraphId.of(id), version, opcode,
                graphKinds, stages != null ? stages : new ShaderStage[0],
                requiredFeature, webgl2);
    }

    public ShaderGraphId id() {
        return id;
    }

    public int version() {
        return version;
    }

    public ShaderIrOpcode opcode() {
        return opcode;
    }

    public boolean supports(ShaderGraphKind kind) {
        return kind != null && Arrays.binarySearch(graphKinds, kind) >= 0;
    }

    public boolean supports(ShaderStage stage) {
        return stage == null || stages.length == 0
                || Arrays.binarySearch(stages, stage) >= 0;
    }

    public GraphicsFeature requiredFeature() {
        return requiredFeature;
    }

    public boolean supportsWebGl2() {
        return webgl2;
    }

    @Override
    public int compareTo(ShaderNodeDefinition other) {
        int idOrder = id.compareTo(other.id);
        return idOrder != 0 ? idOrder : Integer.compare(version, other.version);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderNodeDefinition other
                && id.equals(other.id) && version == other.version
                && opcode == other.opcode
                && Arrays.equals(graphKinds, other.graphKinds)
                && Arrays.equals(stages, other.stages)
                && requiredFeature == other.requiredFeature && webgl2 == other.webgl2;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version, opcode, Arrays.hashCode(graphKinds),
                Arrays.hashCode(stages), requiredFeature, webgl2);
    }
}
