package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

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

    public static GraphicsAttachmentRequirements noApi() {
        return new GraphicsAttachmentRequirements(GraphicsClientApi.NO_API, 0, 0,
                GraphicsContextProfile.ANY, false);
    }

    public static GraphicsAttachmentRequirements openGL(int majorVersion, int minorVersion,
            GraphicsContextProfile profile, boolean forwardCompatible) {
        if (majorVersion <= 0 || minorVersion < 0) {
            throw new FdxException("OpenGL version must be positive");
        }
        return new GraphicsAttachmentRequirements(GraphicsClientApi.OPENGL, majorVersion, minorVersion,
                profile != null ? profile : GraphicsContextProfile.ANY, forwardCompatible);
    }

    public static GraphicsAttachmentRequirements vulkan() {
        return new GraphicsAttachmentRequirements(GraphicsClientApi.VULKAN, 1, 0,
                GraphicsContextProfile.ANY, false);
    }

    public GraphicsClientApi clientApi() {
        return clientApi;
    }

    public int majorVersion() {
        return majorVersion;
    }

    public int minorVersion() {
        return minorVersion;
    }

    public GraphicsContextProfile profile() {
        return profile;
    }

    public boolean forwardCompatible() {
        return forwardCompatible;
    }
}
