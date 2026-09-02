package io.github.libfdx.math;

/**
 * The depth range a graphics API expects in clip space.
 *
 * <p>A projection matrix is not API-neutral: it has to agree with whatever the
 * hardware clips against. Vulkan, Direct3D 12, Metal and WebGPU all clip depth
 * to {@code 0 <= z <= w}; OpenGL, OpenGL ES and WebGL clip to
 * {@code -w <= z <= w}.</p>
 *
 * <p>{@link #ZERO_TO_ONE} is the default throughout libFDX. Feeding a
 * {@link #NEGATIVE_ONE_TO_ONE} matrix to a zero-to-one API is not a visible
 * error - it silently discards everything nearer than about twice the near
 * plane, because that is where the OpenGL mapping crosses zero.</p>
 */
public enum ClipDepthRange {
    /** Vulkan, Direct3D 12, Metal, WebGPU. Near maps to 0, far maps to 1. */
    ZERO_TO_ONE,
    /** OpenGL, OpenGL ES, WebGL. Near maps to -1, far maps to 1. */
    NEGATIVE_ONE_TO_ONE;

    private static volatile ClipDepthRange defaultRange = ZERO_TO_ONE;

    /**
     * Returns the range projections should be built for when nothing more
     * specific is known. {@link #ZERO_TO_ONE} unless a backend has said
     * otherwise.
     *
     * @return the process-wide default clip depth range
     */
    public static ClipDepthRange getDefault() {
        return defaultRange;
    }

    /**
     * Declares the range the active graphics API clips against.
     *
     * <p>Called by the graphics device during startup - the device is the only
     * thing that actually knows. Anything holding a projection built before
     * this call has to rebuild it, so call it before cameras are created.</p>
     *
     * @param clipDepthRange the range the active API clips against
     */
    public static void setDefault(ClipDepthRange clipDepthRange) {
        if (clipDepthRange == null) {
            throw new IllegalArgumentException(
                    "Clip depth range cannot be null");
        }
        defaultRange = clipDepthRange;
    }
}
