package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.ClipDepthRange;

/**
 * Depth/stencil comparison functions.
 */
public enum CompareFunction {
    NEVER,
    LESS,
    EQUAL,
    LESS_EQUAL,
    GREATER,
    NOT_EQUAL,
    GREATER_EQUAL,
    ALWAYS;

    /**
     * Returns the depth test that keeps the nearer fragment under the given
     * clip depth range.
     *
     * <p>Reversed depth puts the near plane at 1, so nearer means a LARGER
     * stored value and the test flips to {@link #GREATER}. Pairing the wrong
     * test with a projection is not a subtle artefact - it either discards
     * every fragment or stops depth occluding anything at all.</p>
     *
     * @param clipDepthRange the range the projection was built for
     * @return the compare function that keeps nearer fragments
     */
    public static CompareFunction depthTestFor(ClipDepthRange clipDepthRange) {
        if (clipDepthRange == null) {
            throw new FdxException("Clip depth range cannot be null");
        }
        return clipDepthRange.isReversed() ? GREATER : LESS_EQUAL;
    }
}
