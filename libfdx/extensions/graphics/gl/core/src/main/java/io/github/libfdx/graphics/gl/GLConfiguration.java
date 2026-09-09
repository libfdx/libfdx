package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContextProfile;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;

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
    private int preparationWorkerLimit;
    private ShaderArtifactCache shaderCache;

    /** Optional borrowed cache for source/reflection, GLSL and supported native program binaries.
     * Native binary import/export is restricted to explicit updateLoading calls and may block;
     * an operation that starts native binary work there must finish through loading updates.
     * Ordinary runtime preparation uses source compilation/polling without binary import/export.
     * The application owns its store and keeps it available until preparation drains. */
    public ShaderArtifactCache shaderCache() { return shaderCache; }
    public GLConfiguration shaderCache(ShaderArtifactCache value) { shaderCache = value; return this; }

    /** Upper bound for platform CPU preparation workers and a hint to parallel GL compilers.
     * Actual execution support is reported by the created graphics device. */
    public int preparationWorkerLimit() {
        // Only worker-capable providers query this default; TeaVM C cannot query processor counts.
        return preparationWorkerLimit != 0 ? preparationWorkerLimit
                : Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
    }
    public GLConfiguration preparationWorkerLimit(int value) {
        if (value < 1 || value > 64) throw new FdxException("GL preparation workers must be between 1 and 64");
        preparationWorkerLimit = value; return this;
    }

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
