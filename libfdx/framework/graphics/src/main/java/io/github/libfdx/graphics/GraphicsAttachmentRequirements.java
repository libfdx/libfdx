package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

/**
 * Represents a graphics attachment requirements.
 *
 * @author xpenatan
 */
public final class GraphicsAttachmentRequirements {
    private final GraphicsClientApi clientApi;
    private final int majorVersion;
    private final int minorVersion;
    private final GraphicsContextProfile profile;
    private final boolean forwardCompatible;

    private GraphicsAttachmentRequirements(GraphicsClientApi clientApi, int majorVersion, int minorVersion,
            GraphicsContextProfile profile, boolean forwardCompatible) {
        if (clientApi == null) {
            throw new FdxException("Graphics client API cannot be null");
        }
        if (profile == null) {
            throw new FdxException("Graphics context profile cannot be null");
        }
        this.clientApi = clientApi;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.profile = profile;
        this.forwardCompatible = forwardCompatible;
    }

    /**
     * Creates a graphics attachment requirements.
     *
     * @return a new graphics attachment requirements
     */
    public static GraphicsAttachmentRequirements noApi() {
        return new GraphicsAttachmentRequirements(GraphicsClientApi.NO_API, 0, 0,
                GraphicsContextProfile.ANY, false);
    }

    /**
     * Creates a graphics attachment requirements.
     *
     * @param majorVersion the major version
     * @param minorVersion the minor version
     * @param profile the profile
     * @param forwardCompatible the forward compatible
     * @return a new graphics attachment requirements
     */
    public static GraphicsAttachmentRequirements openGL(int majorVersion, int minorVersion,
            GraphicsContextProfile profile, boolean forwardCompatible) {
        if (majorVersion <= 0 || minorVersion < 0) {
            throw new FdxException("OpenGL version must be positive");
        }
        return new GraphicsAttachmentRequirements(GraphicsClientApi.OPENGL, majorVersion, minorVersion,
                profile != null ? profile : GraphicsContextProfile.ANY, forwardCompatible);
    }

    /**
     * Creates a graphics attachment requirements.
     *
     * @return a new graphics attachment requirements
     */
    public static GraphicsAttachmentRequirements vulkan() {
        return new GraphicsAttachmentRequirements(GraphicsClientApi.VULKAN, 1, 0,
                GraphicsContextProfile.ANY, false);
    }

    /**
     * Returns the client API.
     *
     * @return the client API
     */
    public GraphicsClientApi clientApi() {
        return clientApi;
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
     * Returns the profile.
     *
     * @return the profile
     */
    public GraphicsContextProfile profile() {
        return profile;
    }

    /**
     * Returns the forward compatible.
     *
     * @return true if forward compatible succeeds or is active; false otherwise
     */
    public boolean forwardCompatible() {
        return forwardCompatible;
    }
}
