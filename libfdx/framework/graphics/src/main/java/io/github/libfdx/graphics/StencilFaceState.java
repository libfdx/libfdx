package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable stencil compare/update state for one face orientation.
 */
public final class StencilFaceState {
    private final CompareFunction compare;
    private final StencilOperation fail;
    private final StencilOperation depthFail;
    private final StencilOperation pass;

    private StencilFaceState(CompareFunction compare, StencilOperation fail,
            StencilOperation depthFail, StencilOperation pass) {
        if (compare == null || fail == null || depthFail == null || pass == null) {
            throw new FdxException("Stencil face state values cannot be null");
        }
        this.compare = compare;
        this.fail = fail;
        this.depthFail = depthFail;
        this.pass = pass;
    }

    public static StencilFaceState of(CompareFunction compare, StencilOperation fail,
            StencilOperation depthFail, StencilOperation pass) {
        return new StencilFaceState(compare, fail, depthFail, pass);
    }

    public static StencilFaceState disabled() {
        return of(CompareFunction.ALWAYS, StencilOperation.KEEP,
                StencilOperation.KEEP, StencilOperation.KEEP);
    }

    public CompareFunction compare() {
        return compare;
    }

    public StencilOperation fail() {
        return fail;
    }

    public StencilOperation depthFail() {
        return depthFail;
    }

    public StencilOperation pass() {
        return pass;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof StencilFaceState other
                && compare == other.compare && fail == other.fail
                && depthFail == other.depthFail && pass == other.pass;
    }

    @Override
    public int hashCode() {
        return Objects.hash(compare, fail, depthFail, pass);
    }
}
