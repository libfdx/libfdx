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
    NEGATIVE_ONE_TO_ONE,
    /**
     * Reversed depth on a zero-to-one API: near maps to 1, far maps to 0.
     *
     * <p>Paired with a floating point depth buffer this is the standard fix for
     * large depth ranges. A conventional projection crowds almost all of its
     * precision into the first few percent of the range; reversing it lines
     * float's exponent density up with the hyperbolic depth distribution, so
     * relative precision stays roughly even from the near plane outward. The
     * far plane can then be pushed to infinity, which removes the far/near
     * ratio - and the frustum degeneracy that comes with it - entirely.</p>
     *
     * <p>Requires the depth test to be GREATER rather than LESS_EQUAL and the
     * depth buffer to be cleared to 0 rather than 1. Not available on the
     * OpenGL family, which has no way to clip depth to 0..w.</p>
     */
    ZERO_TO_ONE_REVERSED;

    /**
     * Returns whether this range clips depth to {@code 0 <= z <= w}, which
     * both zero-to-one variants do.
     *
     * @return true when the API clips depth to zero..w
     */
    public boolean isZeroToOne() {
        return this != NEGATIVE_ONE_TO_ONE;
    }

    /**
     * Returns whether the near plane sits at the far end of the depth range.
     *
     * @return true for {@link #ZERO_TO_ONE_REVERSED}
     */
    public boolean isReversed() {
        return this == ZERO_TO_ONE_REVERSED;
    }

    /**
     * Returns the clip-space depth of the near plane: 1 when reversed, -1 for
     * OpenGL, 0 otherwise.
     *
     * @return the near plane's normalized depth
     */
    public float nearPlaneDepth() {
        if(this == ZERO_TO_ONE_REVERSED) {
            return 1.0f;
        }
        return this == NEGATIVE_ONE_TO_ONE ? -1.0f : 0.0f;
    }

    /**
     * Returns the clip-space depth of the far plane: 0 when reversed, 1
     * otherwise.
     *
     * @return the far plane's normalized depth
     */
    public float farPlaneDepth() {
        return this == ZERO_TO_ONE_REVERSED ? 0.0f : 1.0f;
    }

    /**
     * Returns the near plane's WINDOW depth, which is always within 0..1 and is
     * not the same as its clip-space depth.
     *
     * <p>OpenGL's clip range -1..1 and the zero-to-one range both map their
     * near plane to window 0; only reversed depth puts it at window 1. Code
     * that unprojects a screen coordinate works in window depth, so it needs
     * this rather than {@link #nearPlaneDepth()}.</p>
     *
     * @return 1 when reversed, 0 otherwise
     */
    public float nearPlaneWindowDepth() {
        return this == ZERO_TO_ONE_REVERSED ? 1.0f : 0.0f;
    }

    /**
     * Returns the value a depth attachment must be cleared to so that
     * everything drawn is nearer than the cleared background.
     *
     * <p>This is the far plane's window depth: 0 when reversed, 1 otherwise.
     * It has to agree with the depth compare function and with the projection.
     * Clearing to 1 while testing GREATER passes nothing and renders a black
     * screen; clearing to 0 while testing LESS_EQUAL passes everything and
     * nothing occludes. Read it from here rather than hardcoding, so the three
     * cannot drift apart.</p>
     *
     * <p>A pass that owns a private depth attachment - a shadow map, for
     * instance - only has to be self-consistent and may ignore this.</p>
     *
     * @return the depth clear value
     */
    public float depthClearValue() {
        return farPlaneDepth();
    }

    private static volatile ClipDepthRange defaultRange = ZERO_TO_ONE;
    private static volatile boolean reversedRequested;

    /**
     * Asks for reversed depth, honoured once a device that can express it comes
     * up.
     *
     * <p>Call before the graphics backend starts - from {@code main}, not from
     * an application callback. The depth convention cannot safely be changed
     * once rendering has begun: pipelines bake their depth compare function
     * into immutable state when they are created and cached, so flipping the
     * convention afterwards leaves already-built pipelines testing the old way
     * against a buffer cleared the new way, and nothing draws at all.</p>
     *
     * <p>Ignored on an API that cannot clip depth to 0..w.</p>
     *
     * @param requested whether reversed depth is wanted
     */
    public static void requestReversed(boolean requested) {
        reversedRequested = requested;
    }

    /**
     * Returns whether reversed depth has been requested for this process.
     *
     * @return true when requested
     */
    public static boolean isReversedRequested() {
        return reversedRequested;
    }

    /**
     * Resolves what a device should publish: reversed when it was asked for and
     * the device can express it, otherwise the device's own range.
     *
     * @param deviceRange the range the active API clips against
     * @return the range to publish as the default
     */
    public static ClipDepthRange resolveFor(ClipDepthRange deviceRange) {
        if (deviceRange == null) {
            throw new IllegalArgumentException("deviceRange cannot be null");
        }
        return reversedRequested && deviceRange.isZeroToOne()
                ? ZERO_TO_ONE_REVERSED : deviceRange;
    }

    /**
     * Returns the range projections should be built for when nothing more
     * specific is known. {@link #ZERO_TO_ONE} unless a backend has said
     * otherwise.
     *
     * @return the process-wide default clip depth range
     */
    public static ClipDepthRange getDefault() {
        // The request is applied on read rather than at publish time so it does
        // not depend on a backend remembering to route through one particular
        // Graphics implementation - the desktop backend does not use the same
        // one as the others, and a device that never publishes would silently
        // ignore the request.
        return reversedRequested && defaultRange.isZeroToOne()
                ? ZERO_TO_ONE_REVERSED : defaultRange;
    }

    /**
     * Returns the range the active device actually clips against, ignoring any
     * reversal request. This is the hardware fact; {@link #getDefault()} is
     * what rendering should follow.
     *
     * @return the device's own clip depth range
     */
    public static ClipDepthRange deviceDefault() {
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
