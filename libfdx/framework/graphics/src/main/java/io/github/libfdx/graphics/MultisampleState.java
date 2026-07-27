package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable render-pipeline multisample state.
 */
public final class MultisampleState {
    private final int count;
    private final int mask;
    private final boolean alphaToCoverageEnabled;

    private MultisampleState(int count, int mask, boolean alphaToCoverageEnabled) {
        if (count <= 0 || (count & (count - 1)) != 0) {
            throw new FdxException("Multisample count must be a positive power of two");
        }
        this.count = count;
        this.mask = mask;
        this.alphaToCoverageEnabled = alphaToCoverageEnabled;
    }

    public static MultisampleState of(int count, int mask,
            boolean alphaToCoverageEnabled) {
        return new MultisampleState(count, mask, alphaToCoverageEnabled);
    }

    public static MultisampleState singleSample() {
        return of(1, -1, false);
    }

    public int count() {
        return count;
    }

    public int mask() {
        return mask;
    }

    public boolean alphaToCoverageEnabled() {
        return alphaToCoverageEnabled;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof MultisampleState other
                && count == other.count && mask == other.mask
                && alphaToCoverageEnabled == other.alphaToCoverageEnabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(count, mask, alphaToCoverageEnabled);
    }
}
