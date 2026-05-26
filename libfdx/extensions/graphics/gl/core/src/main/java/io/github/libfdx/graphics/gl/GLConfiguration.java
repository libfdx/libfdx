package io.github.libfdx.graphics.gl;

import io.github.libfdx.graphics.GraphicsContextProfile;

public final class GLConfiguration {
    private int majorVersion = 3;
    private int minorVersion = 3;
    private GraphicsContextProfile profile = GraphicsContextProfile.CORE;
    private boolean forwardCompatible;

    public int majorVersion() {
        return majorVersion;
    }

    public int minorVersion() {
        return minorVersion;
    }

    public GLConfiguration version(int majorVersion, int minorVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        return this;
    }

    public GraphicsContextProfile profile() {
        return profile;
    }

    public GLConfiguration profile(GraphicsContextProfile profile) {
        this.profile = profile != null ? profile : GraphicsContextProfile.CORE;
        return this;
    }

    public boolean forwardCompatible() {
        return forwardCompatible;
    }

    public GLConfiguration forwardCompatible(boolean forwardCompatible) {
        this.forwardCompatible = forwardCompatible;
        return this;
    }
}
