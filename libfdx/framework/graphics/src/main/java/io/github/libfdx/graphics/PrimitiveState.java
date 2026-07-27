package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable primitive assembly and raster face state.
 */
public final class PrimitiveState {
    private final PrimitiveTopology topology;
    private final FrontFace frontFace;
    private final CullMode cullMode;

    private PrimitiveState(PrimitiveTopology topology, FrontFace frontFace, CullMode cullMode) {
        if (topology == null || frontFace == null || cullMode == null) {
            throw new FdxException("Primitive state values cannot be null");
        }
        this.topology = topology;
        this.frontFace = frontFace;
        this.cullMode = cullMode;
    }

    public static PrimitiveState of(PrimitiveTopology topology,
            FrontFace frontFace, CullMode cullMode) {
        return new PrimitiveState(topology, frontFace, cullMode);
    }

    public static PrimitiveState triangles() {
        return of(PrimitiveTopology.TRIANGLE_LIST,
                FrontFace.COUNTER_CLOCKWISE, CullMode.NONE);
    }

    public PrimitiveTopology topology() {
        return topology;
    }

    public FrontFace frontFace() {
        return frontFace;
    }

    public CullMode cullMode() {
        return cullMode;
    }

    public PrimitiveState withTopology(PrimitiveTopology value) {
        return of(value, frontFace, cullMode);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof PrimitiveState other
                && topology == other.topology && frontFace == other.frontFace
                && cullMode == other.cullMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(topology, frontFace, cullMode);
    }
}
