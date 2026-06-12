package io.github.libfdx.graphics.gl;

import io.github.libfdx.graphics.GraphicsContextProfile;

/**
 * Stores configuration values for a GL.
 *
 * @author xpenatan
 */
public final class GLConfiguration {
    private int majorVersion = 3;
    private int minorVersion = 3;
    private GraphicsContextProfile profile = GraphicsContextProfile.CORE;
    private boolean forwardCompatible;

    /**
     * Returns the major version.
     *
     * @return the major version
     */
    public int majorVersion() {
        return majorVersion;
    }

    /**
     * Returns the minor version.
     *
     * @return the minor version
     */
    public int minorVersion() {
        return minorVersion;
    }

    /**
     * Sets the version and returns this GL configuration.
     *
     * @param majorVersion the major version
     * @param minorVersion the minor version
     * @return this GL configuration for chaining
     */
    public GLConfiguration version(int majorVersion, int minorVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        return this;
    }

    /**
     * Returns the profile.
     *
     * @return the profile
     */
    public GraphicsContextProfile profile() {
        return profile;
    }

    /**
     * Sets the profile and returns this GL configuration.
     *
     * @param profile the profile
     * @return this GL configuration for chaining
     */
    public GLConfiguration profile(GraphicsContextProfile profile) {
        this.profile = profile != null ? profile : GraphicsContextProfile.CORE;
        return this;
    }

    /**
     * Returns the forward compatible.
     *
     * @return true if forward compatible succeeds or is active; false otherwise
     */
    public boolean forwardCompatible() {
        return forwardCompatible;
    }

    /**
     * Sets the forward compatible and returns this GL configuration.
     *
     * @param forwardCompatible the forward compatible
     * @return this GL configuration for chaining
     */
    public GLConfiguration forwardCompatible(boolean forwardCompatible) {
        this.forwardCompatible = forwardCompatible;
        return this;
    }
}
