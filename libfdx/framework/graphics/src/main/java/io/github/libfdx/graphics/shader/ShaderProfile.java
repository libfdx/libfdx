package io.github.libfdx.graphics.shader;

import io.github.libfdx.core.FdxException;

import java.util.Locale;

/**
 * Lists the supported shader profile values.
 *
 * @author xpenatan
 */
public enum ShaderProfile {
    PORTABLE_WEBGL2("fdx-wgsl-webgl2"),
    PORTABLE_WEBGPU("fdx-wgsl-webgpu"),
    NATIVE("fdx-native");

    private final String id;

    ShaderProfile(String id) {
        this.id = id;
    }

    /**
     * Returns the ID.
     *
     * @return the ID
     */
    public String id() {
        return id;
    }

    /**
     * Creates a shader profile.
     *
     * @param id the identifier
     * @param fallback the fallback
     * @return a new shader profile
     */
    public static ShaderProfile fromId(String id, ShaderProfile fallback) {
        if (id == null || id.trim().isEmpty()) {
            return fallback != null ? fallback : PORTABLE_WEBGPU;
        }
        String value = id.trim().toLowerCase(Locale.ROOT);
        if ("webgl2".equals(value) || PORTABLE_WEBGL2.id.equals(value)) {
            return PORTABLE_WEBGL2;
        }
        if ("webgpu".equals(value) || PORTABLE_WEBGPU.id.equals(value)) {
            return PORTABLE_WEBGPU;
        }
        if ("native".equals(value) || NATIVE.id.equals(value)) {
            return NATIVE;
        }
        throw new FdxException("Unknown libFDX shader profile: " + id);
    }
}
